import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAiTask,
  fetchAiTask,
  fetchReviewCheckResult,
  retryAiTask,
  startReviewCheck,
} from '@/services/ai'
import type { AiReviewCheckDimension, AiReviewCheckItem, AiReviewCheckResult, AiTask } from '@/types'

export function useReviewAiCheck(getReviewId: () => string, getCanRun: () => boolean, onLocate: (snapshotNodeId: string) => void) {
  const DIMENSION_ORDER: AiReviewCheckDimension[] = [
    'missing_precondition',
    'vague_step',
    'missing_expected',
    'priority_conflict',
  ]
  const DIMENSION_LABELS: Record<AiReviewCheckDimension, string> = {
    missing_precondition: '缺少前置条件',
    vague_step: '步骤描述过于笼统',
    missing_expected: '缺少预期结果',
    priority_conflict: '优先级冲突',
  }

  const task = ref<AiTask | null>(null)
  const starting = ref(false)
  const filter = ref<'all' | AiReviewCheckDimension>('all')

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let initialLoad: Promise<void> | null = null

  const result = computed<AiReviewCheckResult | null>(() =>
    task.value?.result ? (task.value.result as unknown as AiReviewCheckResult) : null,
  )
  const running = computed(
    () => task.value?.status === 'pending' || task.value?.status === 'running',
  )

  // 按维度过滤并保持固定维度序，便于按问题类别浏览
  const filteredItems = computed<AiReviewCheckItem[]>(() =>
    (result.value?.items ?? []).filter(
      (item) => filter.value === 'all' || item.dimension === filter.value,
    ),
  )
  const groupedItems = computed(() =>
    DIMENSION_ORDER.map((dimension) => ({
      dimension,
      items: filteredItems.value.filter((item) => item.dimension === dimension),
    })).filter((group) => group.items.length > 0),
  )

  function stopPolling(): void {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = null
  }

  async function loadLatest(): Promise<void> {
    try {
      const latest = await fetchReviewCheckResult(getReviewId())
      task.value = latest
      if (latest && (latest.status === 'pending' || latest.status === 'running')) {
        startPolling(latest.id)
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载检查结果失败')
    }
  }

  function startPolling(taskId: string): void {
    stopPolling()
    pollTimer = setInterval(async () => {
      try {
        const latest = await fetchAiTask(taskId)
        task.value = latest
        if (latest.status === 'success' || latest.status === 'failed' || latest.status === 'cancelled') {
          stopPolling()
        }
      } catch {
        // 单次轮询失败不重试，保留当前进度，用户重开面板可恢复
        stopPolling()
      }
    }, 2000)
  }

  // 发起/重新发起检查：已有进行中任务时仅恢复轮询（同一评审仅允许一个任务，后端 6005 兜底）
  async function startCheck(): Promise<void> {
    // 评审已完成时仅允许查看历史结果，禁止发起/重试（后端 6012 兜底）
    if (!getCanRun()) return
    if (task.value && (task.value.status === 'pending' || task.value.status === 'running')) {
      ElMessage.info('已有检查任务在执行')
      startPolling(task.value.id)
      return
    }
    starting.value = true
    try {
      const { taskId } = await startReviewCheck(getReviewId())
      task.value = {
        id: taskId,
        type: 'review_check',
        targetId: getReviewId(),
        status: 'pending',
        progress: 0,
        result: null,
        errorMessage: null,
        createdBy: '',
        createdAt: '',
        updatedAt: '',
      }
      startPolling(taskId)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '发起检查失败')
    } finally {
      starting.value = false
    }
  }

  // 入口点击触发：等待初始读取完成后，按最新任务状态决定恢复轮询或发起新任务
  async function start(): Promise<void> {
    await (initialLoad ?? loadLatest())
    if (getCanRun()) await startCheck()
  }

  async function cancel(): Promise<void> {
    if (!task.value) return
    try {
      await ElMessageBox.confirm('取消后已产出的部分结果仍可查看，确定取消？', '取消检查', { type: 'warning' })
    } catch {
      return
    }
    try {
      await cancelAiTask(task.value.id)
      // 轮询继续，下一轮将读到 cancelled 状态与部分结果
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '取消失败')
    }
  }

  async function retry(): Promise<void> {
    if (!task.value) return
    try {
      await retryAiTask(task.value.id)
      startPolling(task.value.id)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '重试失败')
    }
  }

  function locate(snapshotNodeId: string): void {
    onLocate(snapshotNodeId)
  }

  function footerAction(): void {
    if (task.value?.status === 'failed') retry()
    else startCheck()
  }

  onMounted(() => {
    initialLoad = loadLatest()
  })

  onBeforeUnmount(stopPolling)

  return {
    DIMENSION_ORDER,
    DIMENSION_LABELS,
    task,
    starting,
    filter,
    result,
    running,
    filteredItems,
    groupedItems,
    stopPolling,
    loadLatest,
    startPolling,
    startCheck,
    start,
    cancel,
    retry,
    locate,
    footerAction,
  }
}
