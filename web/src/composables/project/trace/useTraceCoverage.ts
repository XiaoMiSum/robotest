import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { retryAiTask, submitAiTask } from '@/services/ai'
import { fetchTraceCoverage, patchTraceCoverage } from '@/services/project'
import { isTerminalTaskStatus, useAiTaskStore } from '@/stores/aiTask'
import type { AiTaskDetail, TraceCoverage, TraceCoveragePatchPayload } from '@/types'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 覆盖修正面板（交互 04 §2.3）：按需求取覆盖结论，人工修正即写即回，
 * 无结论时发起覆盖分析走通用任务口径，任务 succeeded 后刷新结论。
 */
export function useTraceCoverage() {
  const aiTaskStore = useAiTaskStore()
  const requirementId = ref('')
  const record = ref<TraceCoverage | null>(null)
  const loading = ref(false)
  const loadError = ref('')
  const saving = ref(false)
  const revision = ref(0)
  const busy = ref(false)

  /** 本面板发起的任务 ID：回执只认自己的任务，避免与其他任务中心任务串扰 */
  const runningTaskId = ref('')
  const taskFailed = ref(false)
  const taskError = ref('')

  const taskRunning = computed(() => {
    const detail = aiTaskStore.detail
    if (!detail || detail.taskId !== runningTaskId.value) return false
    return !isTerminalTaskStatus(detail.status)
  })
  const taskProgress = computed(() => {
    const detail = aiTaskStore.detail
    if (!detail || detail.taskId !== runningTaskId.value) return null
    return detail.progress
  })

  async function load() {
    const id = requirementId.value
    if (!id) return
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchTraceCoverage({ requirementIds: id, pageNo: 1, pageSize: 1 })
      record.value = page.list[0] ?? null
    } catch (err) {
      record.value = null
      loadError.value = errorMessage(err, '加载覆盖结论失败')
    } finally {
      loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  /** 任务态跟随当前需求：切需求或关闭后不再把上一个需求的回执当成本面板状态 */
  function resetTaskState(): void {
    runningTaskId.value = ''
    taskFailed.value = false
    taskError.value = ''
  }

  async function openFor(id: string): Promise<void> {
    if (requirementId.value !== id) {
      requirementId.value = id
      record.value = null
      resetTaskState()
    }
    await load()
  }

  async function save(payload: TraceCoveragePatchPayload): Promise<boolean> {
    const id = requirementId.value
    if (!id) return false
    saving.value = true
    try {
      record.value = await patchTraceCoverage(id, payload)
      ElMessage.success('覆盖结论已更新，后续 AI 分析不再覆盖人工判定')
      revision.value += 1
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '覆盖修正失败'))
      return false
    } finally {
      saving.value = false
    }
  }

  async function analyze(): Promise<boolean> {
    const id = requirementId.value
    if (!id) return false
    busy.value = true
    taskFailed.value = false
    taskError.value = ''
    try {
      const task = await submitAiTask('coverage_analysis', { requirementIds: [id] })
      runningTaskId.value = task.taskId
      aiTaskStore.startPolling(task.taskId)
      ElMessage.success('覆盖分析已发起，完成后自动刷新覆盖结论')
      return true
    } catch (err) {
      taskFailed.value = true
      taskError.value = errorMessage(err, '发起覆盖分析失败')
      ElMessage.error(taskError.value)
      return false
    } finally {
      busy.value = false
    }
  }

  /** 失败重试沿用任务重试口径（生成新任务并回到排队），不重复提交入参 */
  async function retryAnalyze(): Promise<boolean> {
    if (!runningTaskId.value) return analyze()
    busy.value = true
    try {
      const next: AiTaskDetail = await retryAiTask(runningTaskId.value)
      runningTaskId.value = next.taskId
      taskFailed.value = false
      taskError.value = ''
      aiTaskStore.startPolling(next.taskId)
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '重试覆盖分析失败'))
      return false
    } finally {
      busy.value = false
    }
  }

  watch(
    () => aiTaskStore.detail,
    (detail) => {
      if (!detail || detail.taskId !== runningTaskId.value) return
      if (!isTerminalTaskStatus(detail.status)) return
      if (detail.status === 'succeeded') {
        revision.value += 1
        void load()
        return
      }
      if (detail.status === 'failed') {
        taskFailed.value = true
        taskError.value = detail.error?.msg ?? '覆盖分析执行失败'
        // 分析逐需求落库，失败也可能已写入部分结论，通知页面刷新矩阵
        revision.value += 1
      }
    },
  )

  function close(): void {
    requirementId.value = ''
    record.value = null
    loadError.value = ''
    resetTaskState()
  }

  return {
    requirementId,
    record,
    loading,
    loadError,
    saving,
    revision,
    busy,
    taskRunning,
    taskProgress,
    taskFailed,
    taskError,
    openFor,
    load,
    retry,
    save,
    analyze,
    retryAnalyze,
    close,
  }
}
