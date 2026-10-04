import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelAiTask, fetchAiStatus, fetchAiTasks, retryAiTask } from '@/services/ai'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import { useAiTaskStore } from '@/stores/aiTask'
import type { AiTaskItem, AiTaskStatus } from '@/types'
import {
  aiTaskName,
  aiTaskStatusMeta,
  aiTaskTypeMeta,
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
  progress: number | null
  phase: string | null
  submitterText: string
  createdAt: string
  errorMessage: string
  retryOfTaskId: string | null
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
    return {
      taskId: item.taskId,
      name: aiTaskName(item),
      typeMeta: aiTaskTypeMeta(item.type),
      statusMeta: aiTaskStatusMeta(item.status),
      status: item.status,
      progress: item.progress,
      phase: item.phase,
      submitterText: item.submittedBy
        ? (memberName ?? shortId(item.submittedBy))
        : '系统',
      createdAt: item.createdAt,
      errorMessage: item.error?.msg ?? '',
      retryOfTaskId: item.retryOfTaskId,
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
  function isPending(row: AiTaskRow): boolean {
    return row.status === 'pending' || row.status === 'running'
  }

  async function handleCancel(row: AiTaskRow): Promise<void> {
    try {
      await ElMessageBox.confirm(
        `取消后「${row.name}」不产生任何落库数据，可重新发起。`,
        '确认取消任务',
        { type: 'warning', confirmButtonText: '取消任务', cancelButtonText: '不取消' },
      )
    } catch {
      return
    }
    try {
      const updated = await cancelAiTask(row.taskId)
      const target = rows.value.find((item) => item.taskId === row.taskId)
      if (target) {
        target.status = updated.status
        target.statusMeta = aiTaskStatusMeta(updated.status)
        target.progress = updated.progress
        target.phase = updated.phase
        target.errorMessage = updated.error?.msg ?? ''
      }
      ElMessage.success('任务已取消')
    } catch (err) {
      ElMessage.error(errorMessage(err, '取消任务失败'))
    }
  }

  async function handleRetry(row: AiTaskRow): Promise<void> {
    try {
      await retryAiTask(row.taskId)
      ElMessage.success('已重新排队')
      // 重试生成新任务并回到排队，列表重取保持状态一致
      void load()
    } catch (err) {
      ElMessage.error(errorMessage(err, '重试任务失败'))
    }
  }

  // ==================== 权限与空态 ====================
  /** 任务中心入口按 ai:task 显隐，直达路由 403（交互 2.1.3） */
  const canViewTasks = computed(() => authStore.hasPermission('ai:task'))
  /** 取消 / 重试需 ai:task（详设 3.6.6） */
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
    isPending,
    load,
    retry,
    search,
    resetFilters,
    refresh,
    changePage,
    changePageSize,
    handleCancel,
    handleRetry,
  }
}
