import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { retryAiTask, submitAiTask } from '@/services/ai'
import { fetchTraceImpactItems, patchTraceImpactItem } from '@/services/project'
import { isTerminalTaskStatus, useAiTaskStore } from '@/stores/aiTask'
import { useAuthStore } from '@/stores/auth'
import type { AiTaskDetail, TraceImpactDisposition, TraceImpactItem } from '@/types'
import { traceDispositionMeta, type TraceDispositionMeta } from '@/composables/project/trace/tracePresentation'

export interface TraceImpactItemView extends TraceImpactItem {
  dispositionMeta: TraceDispositionMeta
}

function itemView(item: TraceImpactItem): TraceImpactItemView {
  return { ...item, dispositionMeta: traceDispositionMeta(item.disposition) }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 影响处置面板（交互 04 §2.5 / 3.2）：发起影响分析走通用任务口径，
 * 任务 succeeded 后刷新受影响项；failed 展示原因并可重试。
 */
export function useTraceImpact() {
  const authStore = useAuthStore()
  const aiTaskStore = useAiTaskStore()

  const requirementId = ref('')
  const items = ref<TraceImpactItemView[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const loadError = ref('')
  const busy = ref(false)

  /** 本面板发起的任务 ID：轮询与终态回执只认自己的任务，避免与其他任务中心任务串扰 */
  const runningTaskId = ref('')
  const taskFailed = ref(false)
  const taskError = ref('')
  /** 任务终态 / 处置成功后自增，页面据此重载矩阵与链路 */
  const refreshSignal = ref(0)

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

  let requestId = 0

  async function load() {
    const id = requirementId.value
    if (!id) return
    const seq = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchTraceImpactItems({
        requirementId: id,
        pageNo: pageNo.value,
        pageSize: pageSize.value,
      })
      if (seq !== requestId) return
      items.value = page.list.map(itemView)
      total.value = page.total
    } catch (err) {
      if (seq !== requestId) return
      items.value = []
      loadError.value = errorMessage(err, '加载受影响项失败')
    } finally {
      if (seq === requestId) loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  async function openFor(id: string): Promise<void> {
    if (requirementId.value !== id) {
      requirementId.value = id
      items.value = []
      total.value = 0
      pageNo.value = 1
    }
    await load()
  }

  function changePage(next: number): void {
    pageNo.value = next
    void load()
  }

  function changePageSize(next: number): void {
    pageSize.value = next
    pageNo.value = 1
    void load()
  }

  async function dispose(
    item: TraceImpactItemView,
    disposition: TraceImpactDisposition,
    reason: string,
  ): Promise<boolean> {
    busy.value = true
    try {
      const updated = await patchTraceImpactItem(item.edgeId, { disposition, reason: reason.trim() })
      const target = items.value.find((row) => row.edgeId === item.edgeId)
      if (target) Object.assign(target, itemView(updated))
      ElMessage.success('处置已记录')
      refreshSignal.value += 1
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '处置失败'))
      return false
    } finally {
      busy.value = false
    }
  }

  async function analyze(): Promise<boolean> {
    if (!requirementId.value) return false
    busy.value = true
    taskFailed.value = false
    taskError.value = ''
    try {
      const task = await submitAiTask('impact_analysis', { requirementId: requirementId.value })
      runningTaskId.value = task.taskId
      aiTaskStore.startPolling(task.taskId)
      ElMessage.success('影响分析已发起，完成后自动刷新受影响项')
      return true
    } catch (err) {
      taskFailed.value = true
      taskError.value = errorMessage(err, '发起影响分析失败')
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
      ElMessage.error(errorMessage(err, '重试影响分析失败'))
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
        refreshSignal.value += 1
        void load()
        return
      }
      if (detail.status === 'failed') {
        taskFailed.value = true
        taskError.value = detail.error?.msg ?? '影响分析执行失败'
        refreshSignal.value += 1
      }
    },
  )

  const canEdit = computed(() => authStore.hasPermission('trace:edit'))

  return {
    requirementId,
    items,
    total,
    pageNo,
    pageSize,
    loading,
    loadError,
    busy,
    taskRunning,
    taskProgress,
    taskFailed,
    taskError,
    refreshSignal,
    canEdit,
    openFor,
    load,
    retry,
    changePage,
    changePageSize,
    dispose,
    analyze,
    retryAnalyze,
  }
}
