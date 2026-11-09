import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { abortAiExecution, fetchAiStatus, fetchAiTasks, reExecuteAiTask } from '@/services/ai'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import { useAiTaskStore } from '@/stores/aiTask'
import type { AiTaskItem, AiTaskStatus } from '@/types'
import {
  aiExecutionSummary,
  aiTaskName,
  aiTaskStatusMeta,
  aiTaskTypeMeta,
  type AiExecutionSummary,
  type AiTaskStatusMeta,
  type AiTaskTypeMeta,
} from '@/composables/project/ai/taskPresentation'

/** 列表行视图模型：任务名按类型派生，发起人昵称映射（映射不到显示短 UUID） */
export interface AiTaskRow {
  taskId: string
  name: string
  typeMeta: AiTaskTypeMeta
  statusMeta: AiTaskStatusMeta
  status: AiTaskStatus
  /** 执行摘要：步数与最近动作 / 终止原因摘要（交互 2.1.2） */
  summary: AiExecutionSummary
  submitterText: string
  createdAt: string
  /** 最新执行 ID（终止端点路径参数），无执行为 null */
  executionId: string | null
  /** 可终止：最新执行处于 running / waiting_confirmation（详设 3.6.4） */
  canAbort: boolean
  /** 可再次执行：任务已终止（交互 2.1.2 行操作） */
  canReExecute: boolean
  /** 可进入审核：proposals_ready（跳详情定位审核区） */
  canReview: boolean
  /** 悬浮终止原因文案（aborted 展示） */
  terminateText: string
}

const PAGE_SIZES = [20, 50, 100]

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id
}

export function useAiTaskCenter() {
  const authStore = useAuthStore()
  const aiTaskStore = useAiTaskStore()

  const loading = ref(false)
  // 首屏加载成功前用骨架屏，刷新时保留旧数据避免闪烁
  const hasLoaded = ref(false)
  const loadError = ref('')
  const rows = ref<AiTaskRow[]>([])
  const total = ref(0)

  const filters = reactive({
    type: '',
    status: '',
  })

  const memberMap = ref(new Map<string, string>())

  /** AI 可用性：未启用整页降级提示（交互 2.1.3） */
  const aiAvailable = ref(true)
  const aiStatusLoaded = ref(false)

  async function loadAiStatus(): Promise<void> {
    try {
      const status = await fetchAiStatus()
      aiAvailable.value = status.available
    } catch {
      // 状态接口失败按不可用处理：降级提示优先于功能可用性猜测
      aiAvailable.value = false
    } finally {
      aiStatusLoaded.value = true
    }
  }

  async function loadMemberNames(): Promise<void> {
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      const map = new Map<string, string>()
      for (const member of page.list) {
        map.set(member.userId, member.name)
      }
      memberMap.value = map
    } catch {
      // 昵称映射失败降级为短 UUID，不阻塞列表
      memberMap.value = new Map()
    }
  }

  function toRow(item: AiTaskItem): AiTaskRow {
    const memberName = item.submittedBy ? memberMap.value.get(item.submittedBy) : undefined
    const execution = item.latestExecution
    const abortable =
      execution?.status === 'running' || execution?.status === 'waiting_confirmation'
    return {
      taskId: item.taskId,
      name: aiTaskName(item),
      typeMeta: aiTaskTypeMeta(item.type),
      statusMeta: aiTaskStatusMeta(item.status, execution?.status),
      status: item.status,
      summary: aiExecutionSummary(item),
      submitterText: item.submittedBy
        ? (memberName ?? shortId(item.submittedBy))
        : '系统',
      createdAt: item.createdAt,
      executionId: execution?.executionId ?? null,
      canAbort: abortable,
      canReExecute: item.status === 'aborted',
      canReview: item.status === 'proposals_ready',
      terminateText:
        item.terminateReason?.msg
        || execution?.terminateReason?.msg
        || (item.status === 'aborted' ? '已终止' : ''),
    }
  }

  // ==================== 列表加载 ====================
  let requestId = 0

  async function load(): Promise<void> {
    const id = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchAiTasks({
        type: filters.type || undefined,
        status: filters.status || undefined,
        pageNo: aiTaskStore.pageNo,
        pageSize: aiTaskStore.pageSize,
      })
      // 过期响应（快速翻页/重复查询）直接丢弃，避免旧数据覆盖新结果
      if (id !== requestId) return
      rows.value = page.list.map(toRow)
      total.value = page.total
      hasLoaded.value = true
    } catch (err) {
      if (id !== requestId) return
      // 页面级错误提示 + 重试入口（UI-PAGE-11）
      loadError.value = errorMessage(err, '加载任务列表失败')
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  function search(): void {
    aiTaskStore.applyFilters({ type: filters.type, status: filters.status })
    void load()
  }

  function resetFilters(): void {
    filters.type = ''
    filters.status = ''
    aiTaskStore.resetFilters()
    void load()
  }

  function refresh(): void {
    void load()
  }

  function changePage(pageNo: number): void {
    aiTaskStore.pageNo = pageNo
    void load()
  }

  function changePageSize(pageSize: number): void {
    aiTaskStore.pageSize = pageSize
    aiTaskStore.pageNo = 1
    void load()
  }

  const pageNo = computed(() => aiTaskStore.pageNo)
  const pageSize = computed(() => aiTaskStore.pageSize)

  // ==================== 行操作 ====================
  /** 终止执行（详设 3.6.4）：二次确认后中止最新执行，任务与执行置 aborted */
  async function handleAbort(row: AiTaskRow): Promise<void> {
    if (!row.executionId) return
    try {
      await ElMessageBox.confirm(
        `终止后「${row.name}」停止执行，不产生任何落库数据，可再次执行。`,
        '确认终止执行',
        { type: 'warning', confirmButtonText: '终止执行', cancelButtonText: '不终止' },
      )
    } catch {
      return
    }
    try {
      const updated = await abortAiExecution(row.taskId, row.executionId)
      const current = rows.value.find((item) => item.taskId === row.taskId)
      if (current) {
        const nextRow = toRow(updated)
        current.status = nextRow.status
        current.statusMeta = nextRow.statusMeta
        current.summary = nextRow.summary
        current.executionId = nextRow.executionId
        current.canAbort = nextRow.canAbort
        current.canReExecute = nextRow.canReExecute
        current.canReview = nextRow.canReview
        current.terminateText = nextRow.terminateText
      }
      // 终止即出队：即时刷新顶栏进行中计数，不等 15s 轮询（交互 07 §5）
      void aiTaskStore.refreshRunningCount()
      ElMessage.success('执行已终止')
    } catch (err) {
      ElMessage.error(errorMessage(err, '终止执行失败'))
    }
  }

  /** 再次执行（详设 3.6.4）：沿用同一任务新建执行实例，列表重取保持状态一致 */
  async function handleReExecute(row: AiTaskRow): Promise<void> {
    try {
      await reExecuteAiTask(row.taskId)
      ElMessage.success('已发起再次执行')
      void load()
      // 新执行入队：即时刷新顶栏进行中计数（交互 07 §5）
      void aiTaskStore.refreshRunningCount()
    } catch (err) {
      ElMessage.error(errorMessage(err, '再次执行失败'))
    }
  }

  // ==================== 权限与空态 ====================
  /** 任务中心入口按 ai:task 显隐，直达路由 403（交互 2.1.3） */
  const canViewTasks = computed(() => authStore.hasPermission('ai:task'))
  /** 终止 / 再次执行需 ai:task（后端不校验，仅入口显隐） */
  const canManageTasks = canViewTasks

  onMounted(() => {
    // 保活回填：从详情页返回时沿用上次筛选与页码
    filters.type = aiTaskStore.filters.type
    filters.status = aiTaskStore.filters.status
    void loadAiStatus()
    void loadMemberNames()
    void load()
  })

  return {
    loading,
    hasLoaded,
    loadError,
    rows,
    total,
    pageNo,
    pageSize,
    pageSizes: PAGE_SIZES,
    filters,
    memberMap,
    aiAvailable,
    aiStatusLoaded,
    canViewTasks,
    canManageTasks,
    load,
    retry,
    search,
    resetFilters,
    refresh,
    changePage,
    changePageSize,
    handleAbort,
    handleReExecute,
  }
}
