import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignBug, changeBugStatus, confirmBug, fetchBugs } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import type { BugListItem, BugPriority, BugResolution, BugSeverity, BugStatus, BugType, WorkspaceMember } from '@/types'
import {
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  getValidTargetStatuses,
  promptStatusChangeComment,
} from '@/composables/project/bug/bugStatus'

type QuickFilter = '' | 'unresolved' | 'reported' | 'assigned' | 'resolved' | 'closed'
type BoardStatus = Extract<BugStatus, 'active' | 'resolved' | 'closed'>

interface BoardColumn {
  list: BugListItem[]
  total: number
  pageNo: number
  loading: boolean
  finished: boolean
  requestId: number
}

function createBoardColumn(): BoardColumn {
  return { list: [], total: 0, pageNo: 1, loading: false, finished: false, requestId: 0 }
}

const BOARD_CARD_SIZE = 76
const boardStatuses: BoardStatus[] = ['active', 'resolved', 'closed']

export function useBugList() {
  const router = useRouter()
  const authStore = useAuthStore()

  const loading = ref(false)
  const bugs = ref<BugListItem[]>([])
  const total = ref(0)
  const viewMode = ref<'list' | 'board'>('list')
  const clusterVisible = ref(false)

  const query = reactive({
    status: '' as BugStatus | '',
    severity: '' as BugSeverity | '',
    priority: '' as BugPriority | '',
    bugType: '' as BugType | '',
    keyword: '',
    pageNo: 1,
    pageSize: 20,
  })

  // ==================== Quick filter ====================
  const quickFilter = ref<QuickFilter>('')
  const quickFilterOptions: { value: QuickFilter; label: string }[] = [
    { value: '', label: '全部' },
    { value: 'unresolved', label: '未修复的' },
    { value: 'reported', label: '由我创建' },
    { value: 'assigned', label: '指派给我' },
    { value: 'resolved', label: '由我修复' },
    { value: 'closed', label: '由我关闭' },
  ]

  function quickFilterParams(): {
    status?: BugStatus
    reporterId?: string
    assigneeId?: string
    resolvedBy?: string
    closedBy?: string
  } {
    if (!quickFilter.value) return {}
    if (quickFilter.value === 'unresolved') return { status: 'active' }
    const uid = authStore.user?.id
    if (!uid) return {}
    switch (quickFilter.value) {
      case 'reported': return { reporterId: uid }
      case 'assigned': return { assigneeId: uid }
      case 'resolved': return { resolvedBy: uid }
      default: return { closedBy: uid }
    }
  }

  // ==================== Labels ====================
  const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
  const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
  const statusLabel = BUG_STATUS_LABEL
  const severityType: Record<string, 'primary' | 'danger' | 'warning' | 'info'> = { fatal: 'danger', serious: 'warning', general: 'primary', minor: 'info' }
  const priorityType: Record<string, 'primary' | 'warning' | 'info'> = { high: 'warning', medium: 'primary', low: 'info' }

  // ==================== Load bugs ====================
  async function loadBugs() {
    loading.value = true
    try {
      const page = await fetchBugs({
        status: query.status || undefined,
        severity: query.severity || undefined,
        priority: query.priority || undefined,
        bugType: query.bugType || undefined,
        keyword: query.keyword || undefined,
        ...quickFilterParams(),
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })
      bugs.value = page.list
      total.value = page.total
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载缺陷列表失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== Search / Reset ====================
  let keywordTimer: ReturnType<typeof setTimeout> | null = null
  let searchedKeyword = ''

  function clearKeywordTimer() {
    if (keywordTimer) {
      clearTimeout(keywordTimer)
      keywordTimer = null
    }
  }

  function handleSearch() {
    clearKeywordTimer()
    searchedKeyword = query.keyword
    if (viewMode.value === 'board') {
      loadBoard()
      return
    }
    query.pageNo = 1
    loadBugs()
  }

  function handleReset() {
    query.status = ''
    query.severity = ''
    query.priority = ''
    query.bugType = ''
    query.keyword = ''
    quickFilter.value = ''
    handleSearch()
  }

  function handleKeywordSearch() {
    clearKeywordTimer()
    if (query.keyword === searchedKeyword) return
    handleSearch()
  }

  watch(() => query.keyword, () => {
    clearKeywordTimer()
    keywordTimer = setTimeout(handleKeywordSearch, 1000)
  })

  onUnmounted(clearKeywordTimer)

  // ==================== Advanced filters ====================
  const filtersExpanded = ref(false)
  const advancedFilterCount = computed(
    () => [query.status, query.bugType, query.severity, query.priority].filter(Boolean).length,
  )

  function handleAdvancedSearch() {
    filtersExpanded.value = false
    handleSearch()
  }

  // ==================== Board ====================
  const boardColumns = reactive<Record<BoardStatus, BoardColumn>>({
    active: createBoardColumn(),
    resolved: createBoardColumn(),
    closed: createBoardColumn(),
  })

  const boardItemSize = () => BOARD_CARD_SIZE
  const boardBodyHeight = ref(400)
  const boardRef = ref<HTMLElement>()

  async function loadBoardColumn(status: BoardStatus, reset = false) {
    const col = boardColumns[status]
    if (!reset && (col.loading || col.finished)) return
    if (reset) {
      col.requestId += 1
      col.list = []
      col.total = 0
      col.pageNo = 1
      col.finished = false
    }
    const { status: quickStatus, ...quickPersonParams } = quickFilterParams()
    if ((query.status && query.status !== status) || (quickStatus && quickStatus !== status)) {
      col.finished = true
      col.loading = false
      return
    }
    const requestId = col.requestId
    const pageNo = col.pageNo
    col.loading = true
    try {
      const page = await fetchBugs({
        status,
        severity: query.severity || undefined,
        priority: query.priority || undefined,
        bugType: query.bugType || undefined,
        keyword: query.keyword || undefined,
        ...quickPersonParams,
        pageNo,
        pageSize: query.pageSize,
      })
      if (requestId !== col.requestId) return
      col.list = pageNo === 1 ? page.list : [...col.list, ...page.list]
      col.total = page.total
      col.pageNo = pageNo + 1
      col.finished = col.list.length >= page.total
    } catch (err) {
      if (requestId !== col.requestId) return
      ElMessage.error(err instanceof Error ? err.message : '加载缺陷列表失败')
    } finally {
      if (requestId === col.requestId) {
        col.loading = false
        if (!col.finished && col.list.length * BOARD_CARD_SIZE < boardBodyHeight.value) {
          loadBoardColumn(status)
        }
      }
    }
  }

  function loadBoard() {
    boardStatuses.forEach((status) => loadBoardColumn(status, true))
  }

  function handleBoardEndReached(status: BoardStatus, direction: string) {
    if (direction === 'bottom') loadBoardColumn(status)
  }

  let boardResizeObserver: ResizeObserver | null = null
  let boardMeasureRaf = 0

  function measureBoardBody() {
    const body = boardRef.value?.querySelector('.bug-board__col-body') as HTMLElement | null
    if (body) boardBodyHeight.value = body.clientHeight
  }

  function scheduleBoardMeasure() {
    cancelAnimationFrame(boardMeasureRaf)
    boardMeasureRaf = requestAnimationFrame(measureBoardBody)
  }

  function setupBoardResize() {
    if (boardResizeObserver || !boardRef.value) return
    boardResizeObserver = new ResizeObserver(scheduleBoardMeasure)
    boardResizeObserver.observe(boardRef.value)
    measureBoardBody()
  }

  function teardownBoardResize() {
    boardResizeObserver?.disconnect()
    boardResizeObserver = null
    cancelAnimationFrame(boardMeasureRaf)
  }

  onUnmounted(teardownBoardResize)

  // ==================== Board drag ====================
  const draggingBug = ref<BugListItem | null>(null)
  const validDropStatuses = ref<Set<BugStatus>>(new Set())

  function handleDragStart(bug: BugListItem) {
    draggingBug.value = bug
    validDropStatuses.value = new Set(getValidTargetStatuses(bug.status as BugStatus))
  }

  function handleDragEnd() {
    draggingBug.value = null
    validDropStatuses.value = new Set()
  }

  function isValidDropTarget(status: BugStatus): boolean {
    return validDropStatuses.value.has(status)
  }

  function isValidDropTargetFor(bug: BugListItem, targetStatus: BugStatus): boolean {
    return getValidTargetStatuses(bug.status as BugStatus).includes(targetStatus)
  }

  // ==================== Resolve dialog ====================
  const resolveDialogVisible = ref(false)
  const resolvingBug = ref<BugListItem | null>(null)

  function openResolveDialog(bug: BugListItem) {
    resolvingBug.value = bug
    resolveDialogVisible.value = true
  }

  async function handleResolveConfirm(payload: {
    resolution: BugResolution
    duplicateOfBugId?: string
    comment: string
  }) {
    const bug = resolvingBug.value
    resolvingBug.value = null
    if (!bug) return
    try {
      await changeBugStatus(bug.id, { status: 'resolved', ...payload })
      ElMessage.success('缺陷已解决')
      if (viewMode.value === 'board') {
        loadBoardColumn(bug.status as BoardStatus, true)
        loadBoardColumn('resolved', true)
      } else {
        loadBugs()
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '解决失败')
    }
  }

  async function handleDrop(targetStatus: BoardStatus) {
    const bug = draggingBug.value
    handleDragEnd()
    if (!bug || bug.status === targetStatus) return
    if (!isValidDropTargetFor(bug, targetStatus)) return
    if (targetStatus === 'resolved') {
      openResolveDialog(bug)
      return
    }
    const comment = await promptStatusChangeComment(bug.status as BugStatus, targetStatus)
    if (comment === null) return
    try {
      await changeBugStatus(bug.id, { status: targetStatus, comment: comment || undefined })
      ElMessage.success('状态已更新')
      loadBoardColumn(bug.status as BoardStatus, true)
      loadBoardColumn(targetStatus, true)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '状态变更失败')
    }
  }

  // ==================== Row actions ====================
  async function handleStatusAction(bug: BugListItem, targetStatus: BugStatus, successMsg: string) {
    const comment = await promptStatusChangeComment(bug.status as BugStatus, targetStatus)
    if (comment === null) return
    try {
      await changeBugStatus(bug.id, { status: targetStatus, comment: comment || undefined })
      ElMessage.success(successMsg)
      loadBugs()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '状态变更失败')
    }
  }

  async function handleConfirmBug(bug: BugListItem) {
    try {
      await ElMessageBox.confirm('确认该缺陷有效并需要处理吗？', '确认缺陷', { type: 'info' })
    } catch {
      return
    }
    try {
      await confirmBug(bug.id)
      ElMessage.success('缺陷已确认')
      loadBugs()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '确认失败')
    }
  }

  // ==================== Assign dialog ====================
  const assignDialogVisible = ref(false)
  const assigningBug = ref<BugListItem | null>(null)
  const assigneeId = ref('')
  const assigning = ref(false)
  const memberOptions = ref<WorkspaceMember[]>([])

  async function openAssignDialog(bug: BugListItem) {
    assigningBug.value = bug
    assigneeId.value = bug.assignee?.id ?? ''
    assignDialogVisible.value = true
    if (!memberOptions.value.length) {
      try {
        const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
        memberOptions.value = page.list
      } catch { /* ignore */ }
    }
  }

  async function handleAssignConfirm() {
    const bug = assigningBug.value
    if (!bug) return
    if (!assigneeId.value) {
      ElMessage.warning('请选择处理人')
      return
    }
    assigning.value = true
    try {
      await assignBug(bug.id, assigneeId.value)
      ElMessage.success('已指派')
      assignDialogVisible.value = false
      loadBugs()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '指派失败')
    } finally {
      assigning.value = false
    }
  }

  function handleMoreAction(command: string, bug: BugListItem) {
    if (command === 'confirm') handleConfirmBug(bug)
    else if (command === 'reopen') handleStatusAction(bug, 'active', '缺陷已激活')
    else if (command === 'assign') openAssignDialog(bug)
    else if (command === 'copy') handleCopyBug(bug)
  }

  function handleCopyBug(bug: BugListItem) {
    router.push({ path: '/workspace/projects/bugs/create', query: { copyFrom: bug.id } })
  }

  // ==================== Watch / Lifecycle ====================
  watch(viewMode, (mode) => {
    if (mode === 'board') {
      loadBoard()
      nextTick(setupBoardResize)
    } else {
      teardownBoardResize()
      loadBugs()
    }
  })

  onMounted(loadBugs)

  return {
    loading,
    bugs,
    total,
    viewMode,
    clusterVisible,
    query,
    quickFilter,
    quickFilterOptions,
    filtersExpanded,
    advancedFilterCount,
    boardColumns,
    boardItemSize,
    boardBodyHeight,
    boardRef,
    draggingBug,
    resolveDialogVisible,
    resolvingBug,
    assignDialogVisible,
    assigningBug,
    assigneeId,
    assigning,
    memberOptions,
    // Methods
    handleSearch,
    handleReset,
    handleKeywordSearch,
    handleAdvancedSearch,
    loadBoardColumn,
    handleBoardEndReached,
    handleDragStart,
    handleDragEnd,
    isValidDropTarget,
    handleDrop,
    openResolveDialog,
    handleResolveConfirm,
    handleStatusAction,
    handleConfirmBug,
    openAssignDialog,
    handleAssignConfirm,
    handleMoreAction,
    handleCopyBug,
    loadBugs,
    // Router
    router,
    // Constants
    severityLabel,
    priorityLabel,
    statusLabel,
    severityType,
    priorityType,
    BUG_RESOLUTION_LABEL,
    BUG_STATUS_TAG_TYPE,
    BUG_TYPE_LABEL,
    boardStatuses,
    BOARD_CARD_SIZE,
  }
}
