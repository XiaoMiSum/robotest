import { computed, onMounted, ref } from 'vue'
import { fetchAiStatus } from '@/services/ai'
import { useAuthStore } from '@/stores/auth'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'

export interface PlanOrderAssistOptions {
  planId: string
  /** 采纳成功后刷新计划详情与脑图（交互 2.2 应用建议后 toast） */
  onConfirmed?: () => void
}

/**
 * 测试计划详情「执行顺序建议」入口（详设 5.1 / 交互 2.2）：
 * 入口显隐由 AI 可用性与 ai:task 决定，计划权限由页面按钮条件收口。
 */
export function usePlanOrderAssist(options: PlanOrderAssistOptions) {
  const authStore = useAuthStore()
  const aiAvailable = ref(false)

  async function loadAvailability(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏（总册 4.5）
      aiAvailable.value = false
    }
  }

  onMounted(loadAvailability)

  const entryVisible = computed(
    () => aiAvailable.value && authStore.hasPermission('ai:task'),
  )

  const panelVisible = ref(false)
  const task = useAssistTask('plan_order', {
    target: () => ({ planId: options.planId }),
    onConfirmed: options.onConfirmed,
  })

  async function launch(): Promise<void> {
    if (!entryVisible.value) return
    panelVisible.value = true
    await task.launch({ planId: options.planId })
  }

  async function retry(): Promise<void> {
    await task.retry()
  }

  async function refresh(): Promise<void> {
    await task.refresh()
  }

  function apply(key: string, content: Record<string, unknown>): Promise<void> {
    return task.adoptEdited(key, content)
  }

  function closePanel(): void {
    task.reset()
    panelVisible.value = false
  }

  function onPanelClose(): void {
    // 「放弃」只关闭面板释放本地状态，不产生任何变更（交互 2.2）
    closePanel()
  }

  return {
    entryVisible,
    panelVisible,
    rows: task.rows,
    loading: task.loading,
    confirming: task.confirming,
    submitting: task.submitting,
    running: computed(() => task.submitting.value || task.isRunning.value),
    failed: task.isFailed,
    failedReason: task.failedReason,
    progressText: computed(() => task.executionSummary.value?.text ?? ''),
    loadError: task.loadError,
    launch,
    retry,
    refresh,
    apply,
    closePanel,
    onPanelClose,
    clearReceipt: task.clearReceipt,
  }
}
