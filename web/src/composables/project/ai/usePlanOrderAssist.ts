import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAiStatus, fetchLatestAiTask } from '@/services/ai'
import {
  resolveReExecuteRoute,
  useAssistTask,
} from '@/composables/project/ai/useAssistTask'
import type { AiTaskDetail } from '@/types'

export interface PlanOrderAssistOptions {
  planId: string
  /** 采纳成功后刷新计划详情与脑图（交互 2.2 应用建议后 toast） */
  onConfirmed?: () => void
}

/**
 * 测试计划详情「执行顺序建议」入口（详设 5.1 / 交互 2.2、2.6）：
 * 入口按计划复用最近任务，命中回显进度 / 提案 / 审核结果，按任务状态分叉重新执行。
 */
export function usePlanOrderAssist(options: PlanOrderAssistOptions) {
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

  const entryVisible = computed(() => aiAvailable.value)

  const panelVisible = ref(false)
  const task = useAssistTask('plan_order', {
    target: () => ({ planId: options.planId }),
    onConfirmed: options.onConfirmed,
  })

  // 切换计划即丢弃上一条查询与任务，避免旧任务的提案写入当前计划面板
  let queryToken = 0
  watch(
    () => options.planId,
    () => {
      queryToken++
      panelVisible.value = false
      task.reset()
    },
  )

  async function launch(): Promise<void> {
    if (!entryVisible.value) return
    const token = ++queryToken
    let latest: AiTaskDetail | null
    try {
      latest = await fetchLatestAiTask('plan_order', { planId: options.planId })
    } catch (err) {
      if (token === queryToken) {
        ElMessage.error(err instanceof Error && err.message ? err.message : '查询该计划任务失败')
      }
      return
    }
    if (token !== queryToken) return
    panelVisible.value = true
    if (latest) {
      // 命中最近任务：进行中落进度态，已产出提案 / 已处理落排序面板，终止落失败态
      await task.open(latest.taskId)
      return
    }
    await task.launch({ planId: options.planId })
  }

  /** 重新执行按状态分叉：running 仅回显、aborted 沿用原任务、其余按当前计划新建任务 */
  async function retry(): Promise<void> {
    const route = resolveReExecuteRoute(task.taskStatus.value)
    if (route === 'echo') return
    if (route === 'new-task') {
      await task.launch({ planId: options.planId })
      return
    }
    await task.retry()
  }

  // 已产出提案 / 已处理完的任务才提供「重新执行」；进行中由进度态独占
  const retryable = computed(
    () => task.rows.value.length > 0 && resolveReExecuteRoute(task.taskStatus.value) === 'new-task',
  )

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
    taskStatus: task.taskStatus,
    proposalAudit: task.proposalAudit,
    retryable,
    launch,
    retry,
    refresh,
    apply,
    closePanel,
    onPanelClose,
    clearReceipt: task.clearReceipt,
  }
}
