import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  abortAiExecution,
  fetchAiStatus,
  fetchAiTask,
  reExecuteAiTask,
} from '@/services/ai'
import { fetchMembers } from '@/services/workspace'
import { useAiTaskStore, shouldStopTaskDetailPolling } from '@/stores/aiTask'
import type { AiTaskDetail } from '@/types'
import {
  aiExecutionStatusMeta,
  aiPlanSteps,
  aiTaskAdoptionResult,
  aiTaskName,
  aiTaskStatusMeta,
  aiTaskTypeMeta,
} from '@/composables/project/ai/taskPresentation'

/** 任务不存在 / 越权（详设 3.6.3，交互 2.4）：404 提示 + 返回任务中心 */
const TASK_NOT_FOUND = 1000018110

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function errorCode(error: unknown): number | undefined {
  if (error instanceof Error && 'code' in error) {
    const code = (error as Error & { code?: number }).code
    return typeof code === 'number' ? code : undefined
  }
  return undefined
}

export function useAiTaskDetail() {
  const route = useRoute()
  const router = useRouter()
  const aiTaskStore = useAiTaskStore()

  const taskId = String(route.params.taskId ?? '')

  const loading = ref(true)
  const loadError = ref('')
  const notFound = ref(false)
  const detail = ref<AiTaskDetail | null>(null)

  const submitterText = ref('—')
  const aiAvailable = ref(true)

  /** 仅 running（进行中）轮询；加载失败或非进行中不轮询，轮询在 store 内按非 running 停止 */
  const shouldPoll = computed(
    () => detail.value !== null && !shouldStopTaskDetailPolling(detail.value.status),
  )

  async function loadMemberName(submittedBy: string | null): Promise<void> {
    if (!submittedBy) {
      submitterText.value = '系统'
      return
    }
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      const member = page.list.find((item) => item.userId === submittedBy)
      submitterText.value = member?.name ?? (submittedBy.length > 8 ? `${submittedBy.slice(0, 8)}…` : submittedBy)
    } catch {
      submitterText.value = submittedBy.length > 8 ? `${submittedBy.slice(0, 8)}…` : submittedBy
    }
  }

  async function loadAiStatus(): Promise<void> {
    try {
      const status = await fetchAiStatus()
      aiAvailable.value = status.available
    } catch {
      // 状态接口失败按不可用处理：降级提示优先于功能可用性猜测
      aiAvailable.value = false
    }
  }

  async function load(): Promise<void> {
    // 已有缓存先展示旧值，仅首载走骨架屏
    loading.value = detail.value === null
    loadError.value = ''
    notFound.value = false
    try {
      const task = await fetchAiTask(taskId)
      detail.value = task
      aiTaskStore.cacheDetail(task)
      void loadMemberName(task.submittedBy)
      if (!shouldStopTaskDetailPolling(task.status)) {
        aiTaskStore.startPolling(taskId)
      }
    } catch (err) {
      if (errorCode(err) === TASK_NOT_FOUND) {
        notFound.value = true
        return
      }
      loadError.value = errorMessage(err, '加载任务详情失败')
    } finally {
      loading.value = false
    }
  }

  /** 轮询写入 store 缓存，详情页订阅缓存刷新展示值 */
  watch(
    () => aiTaskStore.detail,
    (cached) => {
      if (cached && cached.taskId === taskId) {
        detail.value = cached
      }
    },
  )

  /** 终止执行（详设 3.6.4）：仅 running / waiting_confirmation 可终止，成功后停止轮询 */
  async function handleAbort(): Promise<void> {
    const executionId = detail.value?.latestExecution?.executionId
    if (!executionId) return
    try {
      await ElMessageBox.confirm('终止后本任务停止执行，不产生任何落库数据，可再次执行。', '确认终止执行', {
        type: 'warning',
        confirmButtonText: '终止执行',
        cancelButtonText: '不终止',
      })
    } catch {
      return
    }
    try {
      const updated = await abortAiExecution(taskId, executionId)
      detail.value = updated
      aiTaskStore.cacheDetail(updated)
      aiTaskStore.stopPolling()
      // 终止即出队：即时刷新顶栏进行中计数（交互 07 §5）
      void aiTaskStore.refreshRunningCount()
      ElMessage.success('执行已终止')
    } catch (err) {
      ElMessage.error(errorMessage(err, '终止执行失败'))
    }
  }

  /** 再次执行（详设 3.6.4）：沿用同一任务新建执行实例，刷新详情继续跟踪 */
  async function handleReExecute(): Promise<void> {
    try {
      await reExecuteAiTask(taskId)
      ElMessage.success('已发起再次执行')
      await load()
      // 新执行入队：即时刷新顶栏进行中计数（交互 07 §5）
      void aiTaskStore.refreshRunningCount()
    } catch (err) {
      ElMessage.error(errorMessage(err, '再次执行失败'))
    }
  }

  function backToCenter(): void {
    void router.push('/workspace/projects/ai/tasks')
  }

  // ==================== 派生状态 ====================
  const statusMeta = computed(() =>
    aiTaskStatusMeta(detail.value?.status ?? 'running', detail.value?.latestExecution?.status),
  )
  const typeMeta = computed(() => aiTaskTypeMeta(detail.value?.type ?? ''))
  const name = computed(() => (detail.value ? aiTaskName(detail.value) : ''))
  const execution = computed(() => detail.value?.latestExecution ?? null)
  const executionStatusMeta = computed(() =>
    execution.value ? aiExecutionStatusMeta(execution.value.status) : null,
  )
  /** 计划步骤时间线（plan 快照驱动，交互 2.2） */
  const planSteps = computed(() => aiPlanSteps(execution.value))
  const proposals = computed(() => detail.value?.proposals ?? [])
  /** 任务级结果：由提案确认状态聚合派生（详设 3.6.3） */
  const adoptionResult = computed(() => aiTaskAdoptionResult(proposals.value))
  /** 有提案时渲染只读查看区：可查看产物与审核结果，不提供审核动作 */
  const showProposalPanel = computed(
    () =>
      (detail.value?.status === 'proposals_ready' || detail.value?.status === 'closed') &&
      proposals.value.length > 0,
  )
  const showEmptyProposals = computed(
    () => detail.value?.status === 'proposals_ready' && proposals.value.length === 0,
  )
  const terminateText = computed(() => {
    const detailReason = detail.value?.terminateReason
    if (detailReason?.msg) {
      return detailReason.code ? `${detailReason.msg}（错误码 ${detailReason.code}）` : detailReason.msg
    }
    const execReason = execution.value?.terminateReason
    if (execReason?.msg) {
      return execReason.code ? `${execReason.msg}（错误码 ${execReason.code}）` : execReason.msg
    }
    return ''
  })
  /** 最新执行可终止（详设 3.6.4） */
  const canAbort = computed(
    () =>
      execution.value?.status === 'running' || execution.value?.status === 'waiting_confirmation',
  )
  /** 任务已终止时可再次执行（交互 2.2 操作） */
  const canReExecute = computed(() => detail.value?.status === 'aborted')
  const isRunning = computed(() => detail.value?.status === 'running')

  onMounted(() => {
    void loadAiStatus()
    // 保活缓存先行展示，减少切回详情页的空白
    const cached = aiTaskStore.takeCachedDetail(taskId)
    if (cached) {
      detail.value = cached
      loading.value = false
    }
    void load()
  })

  onBeforeUnmount(() => {
    // 路由离开停止轮询（状态管理：随详情页路由进入/离开启停）
    aiTaskStore.stopPolling()
  })

  return {
    taskId,
    loading,
    loadError,
    notFound,
    detail,
    submitterText,
    aiAvailable,
    shouldPoll,
    statusMeta,
    typeMeta,
    name,
    execution,
    executionStatusMeta,
    planSteps,
    proposals,
    adoptionResult,
    showProposalPanel,
    showEmptyProposals,
    terminateText,
    canAbort,
    canReExecute,
    isRunning,
    load,
    handleAbort,
    handleReExecute,
    backToCenter,
  }
}
