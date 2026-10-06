import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveRequirement,
  confirmRequirement,
  fetchProjectModuleTree,
  fetchRequirements,
  unarchiveRequirement,
} from '@/services/project'
import { fetchAiStatus } from '@/services/ai'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import { useRequirementStore } from '@/stores/requirement'
import type {
  ProjectModule,
  RequirementListItem,
  RequirementStatus,
  WorkspaceMember,
} from '@/types'
import {
  type RequirementRow,
  requirementRow,
  requirementStatusMeta,
} from '@/composables/project/requirement/requirementPresentation'

const SEARCH_DEBOUNCE_MS = 1000

/** 需求挂在模块目录上，筛选树只留目录（文档不承载需求） */
function stripDocuments(nodes: ProjectModule[]): ProjectModule[] {
  return nodes
    .filter((node) => node.type === 'directory')
    .map((node) => ({ ...node, children: stripDocuments(node.children) }))
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

export function useRequirementList() {
  const authStore = useAuthStore()
  const requirementStore = useRequirementStore()

  const loading = ref(false)
  // 首屏加载成功前用骨架屏，刷新时保留旧数据避免闪烁
  const hasLoaded = ref(false)
  const loadError = ref('')
  const rows = ref<RequirementRow[]>([])
  const total = ref(0)

  const filters = reactive({
    status: [] as RequirementStatus[],
    moduleIds: [] as string[],
    ownerId: '',
    systemVersion: '',
    keyword: '',
  })

  // ==================== 筛选项数据 ====================
  const moduleTree = ref<ProjectModule[]>([])
  const memberOptions = ref<WorkspaceMember[]>([])
  /** 版本无枚举端点，选项取当前页数据去重 */
  const versionOptions = ref<string[]>([])

  async function loadFilterOptions() {
    try {
      moduleTree.value = stripDocuments(await fetchProjectModuleTree())
    } catch {
      // 筛选树失败不阻塞列表：用户仍可用其余条件检索
      moduleTree.value = []
    }
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberOptions.value = page.list
    } catch {
      memberOptions.value = []
    }
  }

  function collectVersions(list: RequirementListItem[]): void {
    const merged = new Set(versionOptions.value)
    for (const item of list) {
      if (item.systemVersion) merged.add(item.systemVersion)
    }
    versionOptions.value = Array.from(merged)
  }

  // ==================== 列表加载 ====================
  let requestId = 0
  let keywordTimer: ReturnType<typeof setTimeout> | null = null
  let searchedKeyword = ''

  function clearKeywordTimer() {
    if (keywordTimer) {
      clearTimeout(keywordTimer)
      keywordTimer = null
    }
  }

  async function load() {
    const id = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchRequirements({
        status: filters.status.length > 0 ? filters.status.join(',') : undefined,
        moduleIds: filters.moduleIds.length > 0 ? filters.moduleIds.join(',') : undefined,
        ownerId: filters.ownerId || undefined,
        systemVersion: filters.systemVersion || undefined,
        keyword: filters.keyword.trim() || undefined,
        pageNo: requirementStore.pageNo,
        pageSize: requirementStore.pageSize,
      })
      // 过期响应（快速翻页/重复搜索）直接丢弃，避免旧数据覆盖新结果
      if (id !== requestId) return
      rows.value = page.list.map(requirementRow)
      total.value = page.total
      collectVersions(page.list)
      hasLoaded.value = true
    } catch (err) {
      if (id !== requestId) return
      // 页面级错误提示 + 重试入口（UI-PUE-11）
      loadError.value = errorMessage(err, '加载需求列表失败')
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  function search(): void {
    clearKeywordTimer()
    searchedKeyword = filters.keyword.trim()
    requirementStore.applyFilters({
      status: [...filters.status],
      moduleIds: [...filters.moduleIds],
      ownerId: filters.ownerId,
      systemVersion: filters.systemVersion,
      keyword: filters.keyword,
    })
    void load()
  }

  function resetFilters(): void {
    filters.status = []
    filters.moduleIds = []
    filters.ownerId = ''
    filters.systemVersion = ''
    filters.keyword = ''
    requirementStore.resetFilters()
    searchedKeyword = ''
    void load()
  }

  watch(
    () => filters.keyword,
    () => {
      clearKeywordTimer()
      keywordTimer = setTimeout(() => {
        if (filters.keyword.trim() === searchedKeyword) return
        search()
      }, SEARCH_DEBOUNCE_MS)
    },
  )

  onBeforeUnmount(clearKeywordTimer)

  function changePage(pageNo: number): void {
    requirementStore.pageNo = pageNo
    void load()
  }

  function changePageSize(pageSize: number): void {
    requirementStore.pageSize = pageSize
    requirementStore.pageNo = 1
    void load()
  }
  const filterCount = computed(
    () =>
      (filters.status.length > 0 ? 1 : 0) +
      (filters.moduleIds.length > 0 ? 1 : 0) +
      (filters.ownerId ? 1 : 0) +
      (filters.systemVersion ? 1 : 0) +
      (filters.keyword.trim() ? 1 : 0),
  )

  const pageNo = computed(() => requirementStore.pageNo)
  const pageSize = computed(() => requirementStore.pageSize)

  // ==================== 行内状态操作 ====================
  async function runStatusAction(
    row: RequirementRow,
    action: () => Promise<{ status: RequirementStatus }>,
    successText: string,
  ): Promise<void> {
    try {
      const detail = await action()
      const target = rows.value.find((item) => item.id === row.id)
      if (target) {
        target.status = detail.status
        target.statusMeta = requirementStatusMeta(detail.status)
      }
      ElMessage.success(successText)
      // 状态影响可用操作与后续查询，统一重取保持一致
      void load()
    } catch (err) {
      ElMessage.error(errorMessage(err, '操作失败'))
    }
  }

  function handleConfirm(row: RequirementRow): Promise<void> {
    return runStatusAction(row, () => confirmRequirement(row.id), '需求已确认')
  }

  async function handleArchive(row: RequirementRow): Promise<void> {
    try {
      await ElMessageBox.confirm(
        `归档后「${row.title}」只读且不参与拆分，可在详情页取消归档。`,
        '确认归档',
        { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await runStatusAction(row, () => archiveRequirement(row.id), '需求已归档')
  }

  function handleUnarchive(row: RequirementRow): Promise<void> {
    return runStatusAction(
      row,
      () => unarchiveRequirement(row.id),
      '已取消归档，条目回到草稿状态',
    )
  }

  // ==================== 权限 ====================
  const canCreate = computed(() => authStore.hasPermission('requirement:create'))
  const canConfirm = computed(() => authStore.hasPermission('requirement:confirm'))
  const canEdit = computed(() => authStore.hasPermission('requirement:edit'))
  /** 任务中心入口按 ai:task 显隐（交互 07 §1 入口约定） */
  const canViewAiTasks = computed(() => authStore.hasPermission('ai:task'))

  // ==================== 批量选择与 AI 发起 ====================
  const selectedRows = ref<RequirementRow[]>([])
  const aiAvailable = ref(false)

  function handleSelectionChange(selection: RequirementRow[]): void {
    selectedRows.value = selection
  }

  async function loadAiStatus(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏，不阻塞列表（总册 4.5）
      aiAvailable.value = false
    }
  }

  /** 发起入口显隐：AI 可用且具备任务提交权限（后端 submit 口径 ai:task） */
  const canLaunchAi = computed(
    () => aiAvailable.value && authStore.hasPermission('ai:task'),
  )

  onMounted(() => {
    // 保活回填：从详情页返回时沿用上次筛选与页码
    filters.status = [...requirementStore.filters.status]
    filters.moduleIds = [...requirementStore.filters.moduleIds]
    filters.ownerId = requirementStore.filters.ownerId
    filters.systemVersion = requirementStore.filters.systemVersion
    filters.keyword = requirementStore.filters.keyword
    searchedKeyword = filters.keyword.trim()
    void loadFilterOptions()
    void load()
    void loadAiStatus()
  })

  return {
    loading,
    hasLoaded,
    loadError,
    rows,
    total,
    pageNo,
    pageSize,
    filters,
    filterCount,
    moduleTree,
    memberOptions,
    versionOptions,
    loadFilterOptions,
    canCreate,
    canConfirm,
    canEdit,
    canViewAiTasks,
    canLaunchAi,
    aiAvailable,
    selectedRows,
    handleSelectionChange,
    loadAiStatus,
    load,
    retry,
    search,
    resetFilters,
    changePage,
    changePageSize,
    handleConfirm,
    handleArchive,
    handleUnarchive,
  }
}
