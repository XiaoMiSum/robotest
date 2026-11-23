import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAiStatus, fetchLatestAiTask } from '@/services/ai'
import { resolveReExecuteRoute, useAssistTask } from '@/composables/project/ai/useAssistTask'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiAssistSourceRef, AiProposalConfirmResult, AiTaskDetail } from '@/types'

export interface ReviewTaskOptions {
  reviewId: string
  /** 采纳写回后刷新评审进度与脑图徽标 */
  onConfirmed?: () => void
}

/**
 * 评审详情「AI 评审」入口（AI 评审详设 5.1 / 交互 05 §1.4）：
 * 按评审 id 复用最近一次任务，命中回显进度或提案，未命中才发起；
 * 切换评审时丢弃过期查询，避免把上一评审的提案写进当前面板。
 */
export function useReviewTask(options: ReviewTaskOptions) {
  const router = useRouter()

  const aiAvailable = ref(false)

  async function loadAvailability(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏，不阻塞评审既有操作（总册 4.5）
      aiAvailable.value = false
    }
  }

  onMounted(loadAvailability)

  const entryVisible = computed(() => aiAvailable.value)

  const task = useAssistTask('review_case_review', {
    onConfirmed: options.onConfirmed,
  })

  const panelVisible = ref(false)
  let queryToken = 0

  const rows = computed<AssistRow[]>(() => task.rows.value)
  const loading = computed(() => task.loading.value)
  const confirming = computed(() => task.confirming.value)
  const receipt = computed<AiProposalConfirmResult[]>(() => task.receipt.value)
  const running = computed(() => task.submitting.value || task.isRunning.value)
  const failed = computed(() => task.isFailed.value)
  const failedReason = computed(() => task.failedReason.value)
  const progressText = computed(() => task.executionSummary.value?.text ?? '')
  /** 已有提案且非执行中：提供重新执行入口（交互 05 §1.4） */
  const retryable = computed(
    () => rows.value.length > 0 && resolveReExecuteRoute(task.task.value?.status) !== 'echo',
  )
  const loadError = computed(() => task.loadError.value)

  async function open(): Promise<void> {
    if (!entryVisible.value) return
    const token = ++queryToken
    let latest: AiTaskDetail | null
    try {
      latest = await fetchLatestAiTask('review_case_review', { reviewId: options.reviewId })
    } catch (err) {
      if (token !== queryToken) return
      // 查询失败不自动发起，避免误耗额度
      ElMessage.error(err instanceof Error && err.message ? err.message : '查询该评审任务失败')
      return
    }
    if (token !== queryToken) return
    panelVisible.value = true
    if (latest) {
      await task.open(latest.taskId)
      return
    }
    await task.launch({ reviewId: options.reviewId })
  }

  async function retry(): Promise<void> {
    const route = resolveReExecuteRoute(task.task.value?.status)
    if (route === 'echo') return
    if (route === 'new-task') {
      await task.launch({ reviewId: options.reviewId })
      return
    }
    await task.retry()
  }

  function refresh(): Promise<void> {
    return task.refresh()
  }

  /** 写回由后端承接，前端只转发采纳动作，不在本地改脑图或进度 */
  function adopt(key: string): Promise<void> {
    return task.adopt(key)
  }

  function adoptEdited(key: string, content: Record<string, unknown>): Promise<void> {
    return task.adoptEdited(key, content)
  }

  function reject(key: string, note?: string): Promise<void> {
    return task.reject(key, note)
  }

  function clearReceipt(): void {
    task.clearReceipt()
  }

  function openSource(source: AiAssistSourceRef): void {
    if (!source.id) return
    void router.push({
      name: 'RequirementDetail',
      params: { requirementId: source.id },
    })
  }

  function closePanel(): void {
    queryToken++
    task.reset()
    panelVisible.value = false
  }

  watch(
    () => options.reviewId,
    () => {
      closePanel()
    },
  )

  onBeforeUnmount(closePanel)

  return {
    entryVisible,
    panelVisible,
    rows,
    loading,
    confirming,
    receipt,
    running,
    failed,
    failedReason,
    progressText,
    retryable,
    loadError,
    open,
    retry,
    refresh,
    adopt,
    adoptEdited,
    reject,
    clearReceipt,
    openSource,
    closePanel,
  }
}
