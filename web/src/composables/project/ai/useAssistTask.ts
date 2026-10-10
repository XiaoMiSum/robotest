import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmAiArtifacts,
  fetchAiArtifact,
  fetchAiTask,
  retryAiTask,
  submitAiTask,
} from '@/services/ai'
import { isTerminalTaskStatus, useAiTaskStore } from '@/stores/aiTask'
import type {
  AiArtifactConfirmItem,
  AiArtifactConfirmResult,
  AiArtifactConfirmStatus,
  AiArtifactConfirmTarget,
  AiAssistSuggestion,
  AiAssistTaskType,
  AiTaskDetail,
} from '@/types'
import {
  aiArtifactConfirmMeta,
  aiArtifactProcessed,
  aiTaskStatusMeta,
} from '@/composables/project/ai/taskPresentation'
import { readAssistSuggestion } from '@/composables/project/ai/assistPresentation'

/** 建议面板行：产物摘要 + 规范化内容（内容未加载或结构不可解析时 suggestion 为 null） */
export interface AssistRow {
  key: string
  title: string
  confirmStatus: AiArtifactConfirmStatus
  confirmLabel: string
  confirmTagType: 'success' | 'info' | 'warning'
  suggestion: AiAssistSuggestion | null
}

export interface AssistTaskOptions {
  /** 落库目标：补全 / 级别推荐带 documentId，顺序建议带 planId 与 round；取值用 getter 以拿到最新上下文 */
  target?: () => AiArtifactConfirmTarget | undefined
  /** 采纳成功后的联动（刷新脑图 / 计划详情），与回执展示解耦 */
  onConfirmed?: () => void
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function asRecord(raw: unknown): Record<string, unknown> {
  return typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {}
}

/**
 * 辅助建议任务编排：发起 / 轮询 / 产物加载 / 采纳回执（交互 §3 主流程）。
 * 面板私有状态挂在这里，不入全局 store；组件卸载时自动停止轮询并释放本地状态。
 */
export function useAssistTask(kind: AiAssistTaskType, options: AssistTaskOptions = {}) {
  const aiTaskStore = useAiTaskStore()

  const taskId = ref('')
  const task = ref<AiTaskDetail | null>(null)
  const submitting = ref(false)
  const loading = ref(false)
  const loadError = ref('')
  const confirming = ref(false)
  const receipt = ref<AiArtifactConfirmResult[]>([])
  /** 产物内容按 key 缓存；确认后清空重取，避免残留旧建议 */
  const contents = ref<Record<string, Record<string, unknown>>>({})

  function sync(detail: AiTaskDetail): void {
    task.value = detail
    if (!isTerminalTaskStatus(detail.status)) return
    aiTaskStore.stopPolling()
    if (detail.status === 'succeeded') void loadContents()
  }

  async function loadContents(): Promise<void> {
    const summaries = task.value?.artifacts ?? []
    if (summaries.length === 0) return
    loading.value = true
    try {
      const next: Record<string, Record<string, unknown>> = {}
      // 建议产物数量受选中范围约束，串行拉取避免与面板其余请求抢占连接
      for (const summary of summaries) {
        const data = await fetchAiArtifact(taskId.value, summary.key)
        next[summary.key] = asRecord(data['content'])
      }
      contents.value = next
    } catch (err) {
      loadError.value = errorMessage(err, '加载建议内容失败')
    } finally {
      loading.value = false
    }
  }

  // 轮询写入 store 缓存，面板订阅缓存刷新（交互 5 状态管理）
  watch(
    () => aiTaskStore.detail,
    (cached) => {
      if (cached && cached.taskId === taskId.value) sync(cached)
    },
  )

  const rows = computed<AssistRow[]>(() =>
    (task.value?.artifacts ?? []).map((summary) => {
      const meta = aiArtifactConfirmMeta(summary.confirmStatus)
      return {
        key: summary.key,
        title: summary.title ?? '',
        confirmStatus: summary.confirmStatus,
        confirmLabel: meta.label,
        confirmTagType: meta.tagType,
        suggestion: readAssistSuggestion(kind, contents.value[summary.key] ?? null),
      }
    }),
  )

  const processed = computed(() => aiArtifactProcessed(rows.value))
  const statusMeta = computed(() => aiTaskStatusMeta(task.value?.status ?? 'pending'))
  const isRunning = computed(
    () =>
      task.value !== null && (task.value.status === 'pending' || task.value.status === 'running'),
  )
  const isSucceeded = computed(() => task.value?.status === 'succeeded')
  const isFailed = computed(() => task.value?.status === 'failed')
  const failedReason = computed(() => task.value?.error?.msg ?? '')
  const receiptSummary = computed(() => ({
    succeeded: receipt.value.filter((item) => item.success).length,
    failed: receipt.value.filter((item) => !item.success).length,
  }))

  async function refresh(): Promise<void> {
    if (!taskId.value) return
    loading.value = true
    loadError.value = ''
    try {
      const detail = await fetchAiTask(taskId.value)
      sync(detail)
      if (!isTerminalTaskStatus(detail.status)) {
        aiTaskStore.startPolling(detail.taskId)
      }
    } catch (err) {
      loadError.value = errorMessage(err, '加载建议任务失败')
    } finally {
      loading.value = false
    }
  }

  /** 发起建议任务：入口在脑图工具栏 / 计划详情（交互 1 入口） */
  async function launch(input: Record<string, unknown>): Promise<void> {
    submitting.value = true
    loadError.value = ''
    receipt.value = []
    contents.value = {}
    try {
      const created = await submitAiTask(kind, input)
      taskId.value = created.taskId
      await refresh()
    } catch (err) {
      loadError.value = errorMessage(err, '发起建议任务失败')
      ElMessage.error(loadError.value)
    } finally {
      submitting.value = false
    }
  }

  /** 挂到既有任务（进行中任务的进度入口，交互 2.3） */
  async function open(id: string): Promise<void> {
    taskId.value = id
    receipt.value = []
    contents.value = {}
    await refresh()
  }

  /** failed 重试生成新任务并回到排队，本面板跟随新任务继续 */
  async function retry(): Promise<void> {
    if (!taskId.value) return
    try {
      const created = await retryAiTask(taskId.value)
      ElMessage.success('已重新排队')
      taskId.value = created.taskId
      receipt.value = []
      contents.value = {}
      await refresh()
    } catch (err) {
      ElMessage.error(errorMessage(err, '重试任务失败'))
    }
  }

  function reset(): void {
    aiTaskStore.stopPolling()
    taskId.value = ''
    task.value = null
    contents.value = {}
    receipt.value = []
    loadError.value = ''
  }

  async function submitItems(
    items: AiArtifactConfirmItem[],
    target?: AiArtifactConfirmTarget,
  ): Promise<void> {
    if (!taskId.value) {
      ElMessage.warning('建议任务尚未就绪')
      return
    }
    if (items.length === 0) {
      ElMessage.warning('请先选择要处理的建议')
      return
    }
    confirming.value = true
    try {
      const resp = await confirmAiArtifacts(taskId.value, {
        items,
        target: target ?? options.target?.(),
      })
      receipt.value = resp.results
      const failed = resp.results.filter((item) => !item.success)
      if (failed.length === 0) {
        ElMessage.success(
          items.every((item) => item.action === 'rejected')
            ? '已全部驳回，未创建任何数据'
            : '采纳完成',
        )
      } else {
        ElMessage.warning(
          `已处理 ${resp.results.length - failed.length} 项，失败 ${failed.length} 项`,
        )
      }
      // 服务端确认状态覆盖本地缓存，重新拉取摘要与内容
      contents.value = {}
      await refresh()
      options.onConfirmed?.()
    } catch (err) {
      ElMessage.error(errorMessage(err, '确认建议失败'))
    } finally {
      confirming.value = false
    }
  }

  function adopt(key: string, target?: AiArtifactConfirmTarget): Promise<void> {
    return submitItems([{ key, action: 'adopted' }], target)
  }

  /** 编辑后采纳：content 仅携带人工调整过的字段（详设 4.2 覆盖口径） */
  function adoptEdited(
    key: string,
    content: Record<string, unknown>,
    target?: AiArtifactConfirmTarget,
  ): Promise<void> {
    return submitItems([{ key, action: 'adopted_edited', content }], target)
  }

  function reject(
    key: string,
    note?: string,
    target?: AiArtifactConfirmTarget,
  ): Promise<void> {
    const item: AiArtifactConfirmItem = { key, action: 'rejected' }
    const trimmed = note?.trim()
    if (trimmed) item.note = trimmed
    return submitItems([item], target)
  }

  function batchAdopt(keys: string[], target?: AiArtifactConfirmTarget): Promise<void> {
    return submitItems(
      keys.map((key) => ({ key, action: 'adopted' as const })),
      target,
    )
  }

  function clearReceipt(): void {
    receipt.value = []
  }

  onBeforeUnmount(reset)

  return {
    taskId,
    task,
    submitting,
    loading,
    loadError,
    confirming,
    receipt,
    rows,
    processed,
    statusMeta,
    isRunning,
    isSucceeded,
    isFailed,
    failedReason,
    receiptSummary,
    refresh,
    launch,
    open,
    retry,
    reset,
    submitItems,
    adopt,
    adoptEdited,
    reject,
    batchAdopt,
    clearReceipt,
  }
}
