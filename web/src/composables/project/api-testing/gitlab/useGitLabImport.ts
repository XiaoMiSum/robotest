import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  fetchGitImportStatus,
  previewGitImport,
  startGitImport,
} from '@/services/project/api-testing/gitRepo'
import { formatGitLabError, toGitLabError } from './useGitLabRepo'
import type {
  ApiGitImportPreview,
  ApiGitImportPreviewDetail,
  ApiGitImportStage,
  ApiGitImportStatus,
  ApiGitRepoConfig,
} from '@/types'

/** 轮询间隔：进度为服务端内存态，导入通常秒级完成（交互稿 27 §5 轮询态） */
const POLL_INTERVAL_MS = 1000

const RUNNING_STAGES: ApiGitImportStage[] = ['fetch', 'scan', 'parse', 'merge', 'persist']

/** 幂等闸门拦截的 10 位错误码（详设 2.3）：与预览 gate.blocked 同一判定的两种表达 */
const GATE_BLOCKED_CODE = 1000017709

const FALLBACK_GATE_REASON = '目标分支提交与上次导入一致且无遗留错误，本次导入已跳过'

/** 进度阶段中文（交互稿 27 §3.2：进度条展示阶段与计数） */
export const STAGE_LABELS: Record<ApiGitImportStage, string> = {
  idle: '未导入',
  fetch: '拉取源码',
  scan: '扫描测试类',
  parse: '解析用例',
  merge: '合并环境',
  persist: '回写结果',
  done: '已完成',
  failed: '失败',
}

export function isRunningStage(stage: ApiGitImportStage): boolean {
  return RUNNING_STAGES.includes(stage)
}

export type ImportDetailFilter = 'all' | ApiGitImportPreviewDetail['type']

/** 明细类型中文标签：未知取值原样透出，避免把服务端异常值误标成预期内类别 */
export function detailTypeLabel(type: string): string {
  if (type === 'parseFailed') return '解析失败'
  if (type === 'errorFailed') return '真实错误'
  return type
}

function escapeCsvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

/** 明细导出 CSV（SRS F1.2 功能点 4）：列与页面明细表一致，出错文件路径由原因文本承载 */
export function buildDetailsCsv(rows: readonly ApiGitImportPreviewDetail[]): string {
  const lines = rows.map((row) =>
    [detailTypeLabel(row.type), row.sceneKey ?? '', row.reason].map(escapeCsvCell).join(','),
  )
  return ['类型,类或方法,原因', ...lines].join('\r\n')
}

/** BOM 让 Excel 正确识别 UTF-8 中文列头；纯前端生成，不经服务端 */
function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

/** 二次确认文案随「重复导入覆盖」配置切换（交互稿 27 §3.2） */
export function buildImportConfirmText(config: ApiGitRepoConfig): string {
  return config.reimportOverwrite
    ? '导入将按归属标识合并，并以仓库内容覆盖已修改的平台侧场景内容，是否继续？'
    : '导入将按归属标识合并，已修改的平台侧内容不被覆盖，是否继续？'
}

/**
 * 导入页签状态机（交互稿 27 §2.2 / §3.2）：
 * 幂等拦截统一收口为 info 提示态而非错误 Toast；轮询随组件卸载停止，不落任何本地存储。
 *
 * @param repo 当前仓库配置读取器（页面在导入完成后会静默刷新该配置）
 * @param onImported 导入完成回调：用于重拉仓库配置（上次导入信息条、源有更新徽标数据）
 */
export function useGitLabImport(repo: () => ApiGitRepoConfig | null, onImported: () => void) {
  const authStore = useAuthStore()
  /** 预览与进度查询为 api-scene:view，执行导入为 api-scene:edit（详设 3.2 权限口径） */
  const canPreview = computed(() => authStore.hasPermission('api-scene:view'))
  const canExecute = computed(() => authStore.hasPermission('api-scene:edit'))

  const previewing = ref(false)
  const preview = ref<ApiGitImportPreview | null>(null)
  /** 幂等拦截提示态（交互稿 27 §4：info 色信息条而非错误条，附 [查看上次结果]） */
  const gateBlocked = ref(false)
  const gateReason = ref('')
  const lastResultVisible = ref(false)

  const submitting = ref(false)
  const progress = ref<ApiGitImportStatus | null>(null)
  const importing = computed(() => {
    const stage = progress.value?.stage
    return stage !== undefined && stage !== null && isRunningStage(stage)
  })

  const detailFilter = ref<ImportDetailFilter>('all')
  const filteredDetails = computed(() => {
    const details = preview.value?.details ?? []
    return detailFilter.value === 'all'
      ? details
      : details.filter((detail) => detail.type === detailFilter.value)
  })

  let timer: ReturnType<typeof setInterval> | null = null
  // 慢请求未返回时不叠加下一轮；单次网络抖动由下一轮自愈，终态由后续轮判定
  let polling = false

  function stopPolling(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  async function pollOnce(): Promise<void> {
    const current = repo()
    if (polling || !current) return
    polling = true
    try {
      const status = await fetchGitImportStatus(current.id)
      progress.value = status
      if (status.stage === 'done') {
        stopPolling()
        // 预览计数已过期：清空后须重新扫描才能再次导入
        preview.value = null
        detailFilter.value = 'all'
        ElMessage.success('导入完成')
        onImported()
      } else if (status.stage === 'failed') {
        stopPolling()
        ElMessage.error(status.error || '导入失败，请稍后重试')
      }
    } catch {
      // 轮询失败不终止：进度只在服务端，下一轮继续判定
    } finally {
      polling = false
    }
  }

  function startPolling(): void {
    if (timer !== null) return
    timer = setInterval(() => void pollOnce(), POLL_INTERVAL_MS)
  }

  /** 进入页签即重查：进度不落本地存储，重进重新查询（交互稿 27 §5） */
  async function restoreProgress(): Promise<void> {
    const current = repo()
    if (!current) return
    try {
      const status = await fetchGitImportStatus(current.id)
      progress.value = status
      if (isRunningStage(status.stage)) startPolling()
    } catch {
      // 挂载即查失败不阻塞动作行，错误由下一次提交类操作统一暴露
    }
  }

  async function runPreview(): Promise<void> {
    const current = repo()
    if (!current || !canPreview.value || previewing.value) return
    // 重新扫描先清上一次结果，loading 期间既不展示旧明细也不闪现空态引导（交互稿 27 §3.2）
    preview.value = null
    detailFilter.value = 'all'
    previewing.value = true
    try {
      const result = await previewGitImport(current.id)
      if (result.gate.blocked) {
        // 拦截是非错误提示：清预览（执行导入不可用）并转 info 态
        preview.value = null
        gateBlocked.value = true
        gateReason.value = result.gate.reason || FALLBACK_GATE_REASON
      } else {
        preview.value = result
        gateBlocked.value = false
        gateReason.value = ''
      }
    } catch (err) {
      ElMessage.error(formatGitLabError(toGitLabError(err)))
    } finally {
      previewing.value = false
    }
  }

  async function runImport(): Promise<void> {
    const current = repo()
    // 执行导入以预览通过为前置（交互稿 27 §3.2）：无预览或闸门拦截态（preview 已清空）直接不可执行
    if (!current || !canExecute.value || !preview.value || submitting.value || importing.value) return
    try {
      await ElMessageBox.confirm(buildImportConfirmText(current), '执行导入', {
        type: 'warning',
        confirmButtonText: '执行导入',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    submitting.value = true
    try {
      progress.value = await startGitImport(current.id)
      startPolling()
    } catch (err) {
      const info = toGitLabError(err)
      if (info.code === GATE_BLOCKED_CODE) {
        // 导入被闸门拦截与预览拦截同一收口：info 提示而非错误 Toast（交互稿 27 §2.2）
        preview.value = null
        gateBlocked.value = true
        gateReason.value = info.message
      } else {
        ElMessage.error(formatGitLabError(info))
      }
    } finally {
      submitting.value = false
    }
  }

  function exportDetailsCsv(): void {
    const current = repo()
    if (!current || !filteredDetails.value.length) return
    const commit = preview.value?.headCommit.slice(0, 7) || 'preview'
    downloadCsv(`git-import-details-${commit}.csv`, buildDetailsCsv(filteredDetails.value))
  }

  onMounted(() => void restoreProgress())
  onBeforeUnmount(stopPolling)

  return {
    canPreview,
    canExecute,
    previewing,
    preview,
    gateBlocked,
    gateReason,
    lastResultVisible,
    submitting,
    progress,
    importing,
    detailFilter,
    filteredDetails,
    runPreview,
    runImport,
    exportDetailsCsv,
  }
}
