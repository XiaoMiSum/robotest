import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAiTask,
  fetchAiStatus,
  fetchAiTask,
  retryAiTask,
} from '@/services/ai'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import { useAiTaskStore, isTerminalTaskStatus } from '@/stores/aiTask'
import type { AiTaskDetail } from '@/types'
import {
  aiArtifactKindCounts,
  aiTaskName,
  aiTaskPhaseSteps,
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
  const authStore = useAuthStore()
  const aiTaskStore = useAiTaskStore()

  const taskId = String(route.params.taskId ?? '')

  const loading = ref(true)
  const loadError = ref('')
  const notFound = ref(false)
  const detail = ref<AiTaskDetail | null>(null)

  const submitterText = ref('—')
  const aiAvailable = ref(true)

  /** 终态或加载失败均不轮询；轮询在 store 内按 terminal 停止 */
  const shouldPoll = computed(
    () => detail.value !== null && !isTerminalTaskStatus(detail.value.status),
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
      if (!isTerminalTaskStatus(task.status)) {
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

  async function handleCancel(): Promise<void> {
    try {
      await ElMessageBox.confirm('取消后本任务不产生任何落库数据，可重新发起。', '确认取消任务', {
        type: 'warning',
        confirmButtonText: '取消任务',
        cancelButtonText: '不取消',
      })
    } catch {
      return
    }
    try {
      const updated = await cancelAiTask(taskId)
      detail.value = updated
      aiTaskStore.cacheDetail(updated)
      aiTaskStore.stopPolling()
      ElMessage.success('任务已取消')
    } catch (err) {
      ElMessage.error(errorMessage(err, '取消任务失败'))
    }
  }

  async function handleRetry(): Promise<void> {
    try {
      const created = await retryAiTask(taskId)
      ElMessage.success('已重新排队')
      // 重试生成新任务，跳到新任务详情继续跟踪
      void router.replace(`/workspace/projects/ai/tasks/${created.taskId}`)
    } catch (err) {
      ElMessage.error(errorMessage(err, '重试任务失败'))
    }
  }

  function backToCenter(): void {
    void router.push('/workspace/projects/ai/tasks')
  }

  // ==================== 派生状态 ====================
  const statusMeta = computed(() =>
    aiTaskStatusMeta(detail.value?.status ?? 'pending'),
  )
  const typeMeta = computed(() => aiTaskTypeMeta(detail.value?.type ?? ''))
  const name = computed(() => (detail.value ? aiTaskName(detail.value) : ''))
  const phaseSteps = computed(() =>
    detail.value ? aiTaskPhaseSteps(detail.value) : [],
  )
  const artifactCounts = computed(() => aiArtifactKindCounts(detail.value?.artifacts))
  const artifacts = computed(() => detail.value?.artifacts ?? [])
  const isPendingOrRunning = computed(
    () => detail.value !== null && (detail.value.status === 'pending' || detail.value.status === 'running'),
  )
  const isFailed = computed(() => detail.value?.status === 'failed')
  const isSucceeded = computed(() => detail.value?.status === 'succeeded')
  /** succeeded 且有产物时页内直出审核区，无二次跳转（交互 2.2） */
  const showReviewArea = computed(() => isSucceeded.value && artifacts.value.length > 0)
  const showEmptyArtifacts = computed(() => isSucceeded.value && artifacts.value.length === 0)

  // ==================== 权限 ====================
  const canManageTask = computed(() => authStore.hasPermission('ai:task'))
  /** 确认产物需 ai:confirm；无权限隐藏审核动作，产物只读预览（交互 2.4） */
  const canConfirm = computed(() => authStore.hasPermission('ai:confirm'))
  /** 生成链落库另需 case:edit（1000018208），缺权时审核区只读 */
  const canConfirmGeneration = computed(
    () => canConfirm.value && authStore.hasPermission('case:edit'),
  )
  /** 圈选落库另需 review:create / plan:create（1000018208），缺权时审核区只读 */
  const canConfirmSelection = computed(() => {
    if (!canConfirm.value) return false
    return detail.value?.type === 'plan_selection'
      ? authStore.hasPermission('plan:create')
      : authStore.hasPermission('review:create')
  })
  /** 缺陷分析落库另需 bug:edit（详设 4.2），缺权时审核区只读；重复组确认只留痕无需 bug:edit */
  const canConfirmBug = computed(() => {
    if (!canConfirm.value) return false
    return detail.value?.type === 'bug_duplicate_scan'
      || authStore.hasPermission('bug:edit')
  })

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
    phaseSteps,
    artifactCounts,
    artifacts,
    isPendingOrRunning,
    isFailed,
    isSucceeded,
    showReviewArea,
    showEmptyArtifacts,
    canManageTask,
    canConfirm,
    canConfirmGeneration,
    canConfirmSelection,
    canConfirmBug,
    load,
    handleCancel,
    handleRetry,
    backToCenter,
  }
}
