import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import type { BugListItem, WorkspaceMember } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchBugs: vi.fn(),
  assignBug: vi.fn(),
  changeBugStatus: vi.fn(),
  confirmBug: vi.fn(),
  fetchMembers: vi.fn(),
  useRouter: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
  getValidTargetStatuses: vi.fn(),
  promptStatusChangeComment: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: mocks.useRouter,
}))

vi.mock('@/services/project', () => ({
  fetchBugs: mocks.fetchBugs,
  assignBug: mocks.assignBug,
  changeBugStatus: mocks.changeBugStatus,
  confirmBug: mocks.confirmBug,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/composables/project/bug/bugStatus', () => ({
  BUG_STATUS_LABEL: { active: '激活', resolved: '已修复', rejected: '已拒绝', closed: '已关闭' },
  BUG_STATUS_TAG_TYPE: { active: 'primary', resolved: 'success', rejected: 'warning', closed: 'info' },
  BUG_TYPE_LABEL: { code_error: '代码错误' },
  BUG_RESOLUTION_LABEL: { fixed: '已解决' },
  getValidTargetStatuses: mocks.getValidTargetStatuses,
  promptStatusChangeComment: mocks.promptStatusChangeComment,
}))

import { useBugList } from './useBugList'

function makeBug(overrides: Partial<BugListItem> = {}): BugListItem {
  return {
    id: 'b1', projectId: 'p1', title: 'Bug 1',
    severity: 'serious', priority: 'high', status: 'active', bugType: 'code_error',
    reproSteps: null, moduleId: null, keywords: null, confirmed: true,
    reopenCount: 0, lastReopenedAt: null, resolution: null, duplicateOfBugId: null,
    dueDate: null, relatedCaseId: null, relatedPlanId: null,
    reporter: { id: 'u1', name: 'Reporter' },
    assignee: { id: 'u2', name: 'Assignee' },
    resolvedBy: null, resolvedAt: null, rejectedBy: null,
    closedBy: null, closedAt: null,
    createdAt: '2025-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z',
    ...overrides,
  }
}

function createRouterMock() {
  return { push: vi.fn() }
}

const authStoreMock = { user: { id: 'u1' } as { id: string; name?: string } | null }
let routerMock: ReturnType<typeof createRouterMock>

function setupMocks() {
  routerMock = createRouterMock()
  mocks.useRouter.mockReturnValue(routerMock)
  mocks.useAuthStore.mockReturnValue(authStoreMock)
  mocks.fetchBugs.mockResolvedValue({ list: [makeBug()], total: 1 })
  mocks.getValidTargetStatuses.mockReturnValue(['resolved'])
  authStoreMock.user = { id: 'u1' }
}

describe('useBugList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setupMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('initial state', () => {
    it('returns correct default state', () => {
      const s = useBugList()
      expect(s.loading.value).toBe(false)
      expect(s.bugs.value).toEqual([])
      expect(s.total.value).toBe(0)
      expect(s.viewMode.value).toBe('list')
      expect(s.clusterVisible.value).toBe(false)
      expect(s.quickFilter.value).toBe('')
      expect(s.filtersExpanded.value).toBe(false)
      expect(s.advancedFilterCount.value).toBe(0)
      expect(s.draggingBug.value).toBeNull()
      expect(s.resolveDialogVisible.value).toBe(false)
      expect(s.resolvingBug.value).toBeNull()
      expect(s.assignDialogVisible.value).toBe(false)
      expect(s.assigningBug.value).toBeNull()
      expect(s.assigneeId.value).toBe('')
      expect(s.assigning.value).toBe(false)
      expect(s.memberOptions.value).toEqual([])
    })

    it('query defaults are correct', () => {
      const s = useBugList()
      expect(s.query).toEqual({
        status: '', severity: '', priority: '', bugType: '', keyword: '', pageNo: 1, pageSize: 20,
      })
    })

    it('quickFilterOptions has 6 items', () => {
      const s = useBugList()
      expect(s.quickFilterOptions).toHaveLength(6)
      expect(s.quickFilterOptions[0]).toEqual({ value: '', label: '全部' })
    })

    it('constants are exported', () => {
      const s = useBugList()
      expect(s.BOARD_CARD_SIZE).toBe(76)
      expect(s.boardStatuses).toEqual(['active', 'resolved', 'closed'])
      expect(s.severityLabel.fatal).toBe('致命')
      expect(s.priorityLabel.high).toBe('高')
      expect(s.statusLabel.active).toBe('激活')
    })
  })

  describe('loadBugs', () => {
    it('updates bugs and total on success', async () => {
      const s = useBugList()
      await s.loadBugs()
      expect(s.bugs.value).toHaveLength(1)
      expect(s.total.value).toBe(1)
      expect(s.loading.value).toBe(false)
    })

    it('loading is true during fetch', async () => {
      let resolve: (v: unknown) => void
      mocks.fetchBugs.mockImplementation(() => new Promise((r) => { resolve = r }))
      const s = useBugList()
      const p = s.loadBugs()
      expect(s.loading.value).toBe(true)
      resolve!({ list: [], total: 0 })
      await p
      expect(s.loading.value).toBe(false)
    })

    it('shows error on failure', async () => {
      mocks.fetchBugs.mockRejectedValueOnce(new Error('network'))
      const s = useBugList()
      await s.loadBugs()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('network')
      expect(s.loading.value).toBe(false)
    })

    it('shows generic message for non-Error', async () => {
      mocks.fetchBugs.mockRejectedValueOnce('string')
      const s = useBugList()
      await s.loadBugs()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载缺陷列表失败')
    })

    it('passes query params to fetchBugs', async () => {
      const s = useBugList()
      s.query.status = 'active'
      s.query.severity = 'fatal'
      s.query.priority = 'high'
      s.query.bugType = 'code_error'
      s.query.keyword = 'test'
      await s.loadBugs()
      const lastCall = mocks.fetchBugs.mock.calls[mocks.fetchBugs.mock.calls.length - 1][0]
      expect(lastCall).toEqual({
        status: 'active', severity: 'fatal', priority: 'high',
        bugType: 'code_error', keyword: 'test', pageNo: 1, pageSize: 20,
      })
    })

    it('empty strings are omitted from params', async () => {
      const s = useBugList()
      await s.loadBugs()
      const lastCall = mocks.fetchBugs.mock.calls[mocks.fetchBugs.mock.calls.length - 1][0]
      expect(lastCall.status).toBeUndefined()
      expect(lastCall.severity).toBeUndefined()
      expect(lastCall.keyword).toBeUndefined()
    })
  })

  describe('handleSearch', () => {
    it('resets pageNo and loads in list mode', async () => {
      const s = useBugList()
      s.query.pageNo = 5
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(s.query.pageNo).toBe(1)
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('loads board in board mode', async () => {
      const s = useBugList()
      s.viewMode.value = 'board'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })
  })

  describe('handleReset', () => {
    it('resets all query fields', async () => {
      const s = useBugList()
      s.query.status = 'active'
      s.query.severity = 'fatal'
      s.query.priority = 'high'
      s.query.bugType = 'code_error'
      s.query.keyword = 'test'
      s.quickFilter.value = 'reported'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleReset()
      expect(s.query.status).toBe('')
      expect(s.query.severity).toBe('')
      expect(s.query.priority).toBe('')
      expect(s.query.bugType).toBe('')
      expect(s.query.keyword).toBe('')
      expect(s.quickFilter.value).toBe('')
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })
  })

  describe('handleKeywordSearch', () => {
    it('does not search when keyword unchanged', async () => {
      const s = useBugList()
      await s.handleSearch()
      vi.clearAllMocks()
      await s.handleKeywordSearch()
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
    })

    it('searches when keyword changed', async () => {
      const s = useBugList()
      await s.handleSearch()
      s.query.keyword = 'new'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleKeywordSearch()
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })
  })

  describe('keyword watcher', () => {
    it('debounces keyword search by 1 second', async () => {
      const s = useBugList()
      s.query.keyword = 'debounced'
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1000)
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('multiple rapid changes trigger only one search', async () => {
      const s = useBugList()
      s.query.keyword = 'a'
      await vi.advanceTimersByTimeAsync(500)
      s.query.keyword = 'ab'
      await vi.advanceTimersByTimeAsync(1000)
      expect(mocks.fetchBugs).toHaveBeenCalledTimes(1)
    })
  })

  describe('handleAdvancedSearch', () => {
    it('collapses filters and searches', async () => {
      const s = useBugList()
      s.filtersExpanded.value = true
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleAdvancedSearch()
      expect(s.filtersExpanded.value).toBe(false)
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })
  })

  describe('advancedFilterCount', () => {
    it('counts filled fields', () => {
      const s = useBugList()
      expect(s.advancedFilterCount.value).toBe(0)
      s.query.status = 'active'
      expect(s.advancedFilterCount.value).toBe(1)
      s.query.severity = 'fatal'
      expect(s.advancedFilterCount.value).toBe(2)
      s.query.priority = 'high'
      expect(s.advancedFilterCount.value).toBe(3)
      s.query.bugType = 'code_error'
      expect(s.advancedFilterCount.value).toBe(4)
    })
  })

  describe('quickFilter', () => {
    it('no filter returns empty', async () => {
      const s = useBugList()
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      const params = mocks.fetchBugs.mock.calls[0][0]
      expect(params.reporterId).toBeUndefined()
      expect(params.assigneeId).toBeUndefined()
    })

    it('unresolved sets status=active', async () => {
      const s = useBugList()
      s.quickFilter.value = 'unresolved'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs.mock.calls[0][0].status).toBe('active')
    })

    it('reported sets reporterId', async () => {
      const s = useBugList()
      s.quickFilter.value = 'reported'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs.mock.calls[0][0].reporterId).toBe('u1')
    })

    it('assigned sets assigneeId', async () => {
      const s = useBugList()
      s.quickFilter.value = 'assigned'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs.mock.calls[0][0].assigneeId).toBe('u1')
    })

    it('resolved sets resolvedBy', async () => {
      const s = useBugList()
      s.quickFilter.value = 'resolved'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs.mock.calls[0][0].resolvedBy).toBe('u1')
    })

    it('closed sets closedBy', async () => {
      const s = useBugList()
      s.quickFilter.value = 'closed'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs.mock.calls[0][0].closedBy).toBe('u1')
    })

    it('null user returns empty params', async () => {
      authStoreMock.user = null
      const s = useBugList()
      s.quickFilter.value = 'reported'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleSearch()
      expect(mocks.fetchBugs.mock.calls[0][0].reporterId).toBeUndefined()
    })
  })

  describe('board', () => {
    it('boardItemSize returns BOARD_CARD_SIZE', () => {
      const s = useBugList()
      expect(s.boardItemSize()).toBe(76)
    })

    it('boardColumns initial state', () => {
      const s = useBugList()
      expect(s.boardColumns.active.list).toEqual([])
      expect(s.boardColumns.active.total).toBe(0)
      expect(s.boardColumns.active.loading).toBe(false)
      expect(s.boardColumns.active.finished).toBe(false)
      expect(s.boardColumns.resolved.list).toEqual([])
      expect(s.boardColumns.closed.list).toEqual([])
    })

    it('loadBoardColumn success updates column', async () => {
      const bug = makeBug({ status: 'active' })
      mocks.fetchBugs.mockResolvedValueOnce({ list: [bug], total: 1 })
      const s = useBugList()
      await s.loadBoardColumn('active', true)
      expect(s.boardColumns.active.list).toEqual([bug])
      expect(s.boardColumns.active.total).toBe(1)
      expect(s.boardColumns.active.finished).toBe(true)
      expect(s.boardColumns.active.loading).toBe(false)
    })

    it('loadBoardColumn shows error on failure', async () => {
      mocks.fetchBugs.mockRejectedValueOnce(new Error('fail'))
      const s = useBugList()
      await s.loadBoardColumn('active', true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
      expect(s.boardColumns.active.loading).toBe(false)
    })

    it('loadBoardColumn non-Error shows generic message', async () => {
      mocks.fetchBugs.mockRejectedValueOnce(42)
      const s = useBugList()
      await s.loadBoardColumn('active', true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载缺陷列表失败')
    })

    it('loadBoardColumn skips finished column when not resetting', async () => {
      const s = useBugList()
      s.boardColumns.active.finished = true
      vi.clearAllMocks()
      await s.loadBoardColumn('active', false)
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
    })

    it('loadBoardColumn skips loading column when not resetting', async () => {
      const s = useBugList()
      s.boardColumns.active.loading = true
      vi.clearAllMocks()
      await s.loadBoardColumn('active', false)
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
    })

    it('loadBoardColumn reset clears list and fetches', async () => {
      mocks.fetchBugs.mockResolvedValueOnce({ list: [makeBug()], total: 1 })
      const s = useBugList()
      s.boardColumns.active.list = [makeBug()]
      s.boardColumns.active.total = 99
      await s.loadBoardColumn('active', true)
      expect(s.boardColumns.active.list).toEqual([makeBug()])
      expect(s.boardColumns.active.total).toBe(1)
      expect(s.boardColumns.active.finished).toBe(true)
    })

    it('loadBoardColumn pagination appends data', async () => {
      const bug1 = makeBug({ id: 'b1' })
      const bug2 = makeBug({ id: 'b2' })
      mocks.fetchBugs.mockResolvedValueOnce({ list: [bug1], total: 2 })
      const s = useBugList()
      s.boardBodyHeight.value = 0
      await s.loadBoardColumn('active', true)
      expect(s.boardColumns.active.list).toEqual([bug1])
      expect(s.boardColumns.active.pageNo).toBe(2)
      expect(s.boardColumns.active.finished).toBe(false)
      mocks.fetchBugs.mockResolvedValueOnce({ list: [bug2], total: 2 })
      await s.loadBoardColumn('active', false)
      expect(s.boardColumns.active.list).toEqual([bug1, bug2])
      expect(s.boardColumns.active.finished).toBe(true)
    })

    it('loadBoardColumn skips when query.status mismatch', async () => {
      const s = useBugList()
      s.query.status = 'resolved'
      vi.clearAllMocks()
      await s.loadBoardColumn('active', true)
      expect(s.boardColumns.active.finished).toBe(true)
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
    })

    it('loadBoardColumn skips when quickFilter unresolved and column is resolved', async () => {
      const s = useBugList()
      s.quickFilter.value = 'unresolved'
      vi.clearAllMocks()
      await s.loadBoardColumn('resolved', true)
      expect(s.boardColumns.resolved.finished).toBe(true)
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
    })

    it('handleBoardEndReached bottom loads column', async () => {
      const s = useBugList()
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      s.handleBoardEndReached('active', 'bottom')
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('handleBoardEndReached non-bottom does not load', async () => {
      const s = useBugList()
      vi.clearAllMocks()
      s.handleBoardEndReached('active', 'top')
      expect(mocks.fetchBugs).not.toHaveBeenCalled()
    })
  })

  describe('board auto-fill', () => {
    it('auto-loads next page when list does not fill viewport', async () => {
      const bugs = Array.from({ length: 3 }, (_, i) => makeBug({ id: `b${i}` }))
      mocks.fetchBugs.mockResolvedValue({ list: bugs, total: 20 })
      const s = useBugList()
      s.boardBodyHeight.value = 400
      await s.loadBoardColumn('active', true)
      expect(mocks.fetchBugs.mock.calls.length).toBeGreaterThan(1)
    })

    it('does not auto-load when list fills viewport', async () => {
      const bugs = Array.from({ length: 10 }, (_, i) => makeBug({ id: `b${i}` }))
      mocks.fetchBugs.mockResolvedValue({ list: bugs, total: 10 })
      const s = useBugList()
      s.boardBodyHeight.value = 400
      await s.loadBoardColumn('active', true)
      expect(mocks.fetchBugs).toHaveBeenCalledTimes(1)
    })
  })

  describe('drag', () => {
    it('handleDragStart sets draggingBug and validDropStatuses', () => {
      const s = useBugList()
      const bug = makeBug({ status: 'active' })
      s.handleDragStart(bug)
      expect(s.draggingBug.value).toEqual(bug)
      expect(mocks.getValidTargetStatuses).toHaveBeenCalledWith('active')
    })

    it('handleDragEnd clears state', () => {
      const s = useBugList()
      s.handleDragStart(makeBug())
      s.handleDragEnd()
      expect(s.draggingBug.value).toBeNull()
    })

    it('isValidDropTarget checks set', () => {
      mocks.getValidTargetStatuses.mockReturnValue(['resolved', 'rejected'])
      const s = useBugList()
      s.handleDragStart(makeBug())
      expect(s.isValidDropTarget('resolved')).toBe(true)
      expect(s.isValidDropTarget('closed')).toBe(false)
    })

    it('handleDrop same status does nothing', async () => {
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('active')
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
      expect(s.draggingBug.value).toBeNull()
    })

    it('handleDrop invalid target does nothing', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['resolved'])
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('closed')
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('handleDrop to resolved opens resolve dialog', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['resolved'])
      const bug = makeBug({ status: 'active' })
      const s = useBugList()
      s.handleDragStart(bug)
      await s.handleDrop('resolved')
      expect(s.resolveDialogVisible.value).toBe(true)
      expect(s.resolvingBug.value).toEqual(bug)
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('handleDrop to non-resolved prompts comment and changes status', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['closed'])
      mocks.promptStatusChangeComment.mockResolvedValue('关闭理由')
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('closed')
      expect(mocks.promptStatusChangeComment).toHaveBeenCalledWith('active', 'closed')
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('b1', { status: 'closed', comment: '关闭理由' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('状态已更新')
    })

    it('handleDrop comment cancel does nothing', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['closed'])
      mocks.promptStatusChangeComment.mockResolvedValue(null)
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('closed')
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('handleDrop empty comment omits comment field', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['closed'])
      mocks.promptStatusChangeComment.mockResolvedValue('')
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('closed')
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('b1', { status: 'closed' })
    })

    it('handleDrop status change error shows message', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['closed'])
      mocks.promptStatusChangeComment.mockResolvedValue('c')
      mocks.changeBugStatus.mockRejectedValueOnce(new Error('fail'))
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('closed')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
    })

    it('handleDrop non-Error shows generic message', async () => {
      mocks.getValidTargetStatuses.mockReturnValue(['closed'])
      mocks.promptStatusChangeComment.mockResolvedValue('c')
      mocks.changeBugStatus.mockRejectedValueOnce(42)
      const s = useBugList()
      s.handleDragStart(makeBug({ status: 'active' }))
      await s.handleDrop('closed')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态变更失败')
    })
  })

  describe('resolve dialog', () => {
    it('openResolveDialog opens dialog', () => {
      const s = useBugList()
      const bug = makeBug()
      s.openResolveDialog(bug)
      expect(s.resolveDialogVisible.value).toBe(true)
      expect(s.resolvingBug.value).toEqual(bug)
    })

    it('handleResolveConfirm success refreshes', async () => {
      mocks.changeBugStatus.mockResolvedValue(undefined)
      const s = useBugList()
      s.openResolveDialog(makeBug())
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleResolveConfirm({ resolution: 'fixed', comment: 'done' })
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('b1', { status: 'resolved', resolution: 'fixed', comment: 'done' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已解决')
      expect(s.resolvingBug.value).toBeNull()
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('handleResolveConfirm board mode refreshes columns', async () => {
      mocks.changeBugStatus.mockResolvedValue(undefined)
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      const s = useBugList()
      s.viewMode.value = 'board'
      s.openResolveDialog(makeBug({ status: 'active' }))
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleResolveConfirm({ resolution: 'fixed', comment: '' })
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('handleResolveConfirm error shows message', async () => {
      mocks.changeBugStatus.mockRejectedValueOnce(new Error('fail'))
      const s = useBugList()
      s.openResolveDialog(makeBug())
      await s.handleResolveConfirm({ resolution: 'fixed', comment: '' })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
    })

    it('handleResolveConfirm non-Error shows generic', async () => {
      mocks.changeBugStatus.mockRejectedValueOnce(42)
      const s = useBugList()
      s.openResolveDialog(makeBug())
      await s.handleResolveConfirm({ resolution: 'fixed', comment: '' })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('解决失败')
    })

    it('handleResolveConfirm no bug silently returns', async () => {
      const s = useBugList()
      await s.handleResolveConfirm({ resolution: 'fixed', comment: '' })
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })
  })

  describe('handleStatusAction', () => {
    it('confirms comment then changes status', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('reason')
      mocks.changeBugStatus.mockResolvedValue(undefined)
      const s = useBugList()
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleStatusAction(makeBug(), 'closed', '缺陷已关闭')
      expect(mocks.promptStatusChangeComment).toHaveBeenCalledWith('active', 'closed')
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('b1', { status: 'closed', comment: 'reason' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已关闭')
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('cancel does nothing', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue(null)
      const s = useBugList()
      vi.clearAllMocks()
      await s.handleStatusAction(makeBug(), 'closed', '缺陷已关闭')
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('error shows message', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('reason')
      mocks.changeBugStatus.mockRejectedValueOnce(new Error('fail'))
      const s = useBugList()
      await s.handleStatusAction(makeBug(), 'closed', '缺陷已关闭')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
    })

    it('non-Error shows generic message', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('reason')
      mocks.changeBugStatus.mockRejectedValueOnce(42)
      const s = useBugList()
      await s.handleStatusAction(makeBug(), 'closed', '缺陷已关闭')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态变更失败')
    })
  })

  describe('handleConfirmBug', () => {
    it('confirms and refreshes', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.confirmBug.mockResolvedValue(undefined)
      const s = useBugList()
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleConfirmBug(makeBug())
      expect(mocks.confirmBug).toHaveBeenCalledWith('b1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已确认')
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('user cancel does nothing', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const s = useBugList()
      await s.handleConfirmBug(makeBug())
      expect(mocks.confirmBug).not.toHaveBeenCalled()
    })

    it('error shows message', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.confirmBug.mockRejectedValueOnce(new Error('fail'))
      const s = useBugList()
      await s.handleConfirmBug(makeBug())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
    })

    it('non-Error shows generic', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.confirmBug.mockRejectedValueOnce(42)
      const s = useBugList()
      await s.handleConfirmBug(makeBug())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('确认失败')
    })
  })

  describe('assign dialog', () => {
    it('openAssignDialog opens and loads members', async () => {
      mocks.fetchMembers.mockResolvedValueOnce({ list: [{ userId: 'u3' } as WorkspaceMember], total: 1 })
      const s = useBugList()
      const bug = makeBug({ assignee: { id: 'u2', name: 'A' } })
      await s.openAssignDialog(bug)
      expect(s.assignDialogVisible.value).toBe(true)
      expect(s.assigningBug.value).toEqual(bug)
      expect(s.assigneeId.value).toBe('u2')
      expect(s.memberOptions.value).toHaveLength(1)
    })

    it('openAssignDialog skips fetch when members exist', async () => {
      const s = useBugList()
      s.memberOptions.value = [{ userId: 'u3' } as WorkspaceMember]
      await s.openAssignDialog(makeBug())
      expect(mocks.fetchMembers).not.toHaveBeenCalled()
    })

    it('openAssignDialog ignores fetch error', async () => {
      mocks.fetchMembers.mockRejectedValueOnce(new Error('err'))
      const s = useBugList()
      await s.openAssignDialog(makeBug())
      expect(s.assignDialogVisible.value).toBe(true)
      expect(s.memberOptions.value).toEqual([])
    })

    it('openAssignDialog null assignee sets empty id', async () => {
      const s = useBugList()
      await s.openAssignDialog(makeBug({ assignee: null }))
      expect(s.assigneeId.value).toBe('')
    })

    it('handleAssignConfirm success closes and refreshes', async () => {
      mocks.assignBug.mockResolvedValue(undefined)
      const s = useBugList()
      s.assigningBug.value = makeBug()
      s.assigneeId.value = 'u3'
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      await s.handleAssignConfirm()
      expect(mocks.assignBug).toHaveBeenCalledWith('b1', 'u3')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已指派')
      expect(s.assignDialogVisible.value).toBe(false)
      expect(mocks.fetchBugs).toHaveBeenCalled()
    })

    it('handleAssignConfirm no bug does nothing', async () => {
      const s = useBugList()
      await s.handleAssignConfirm()
      expect(mocks.assignBug).not.toHaveBeenCalled()
    })

    it('handleAssignConfirm no assignee shows warning', async () => {
      const s = useBugList()
      s.assigningBug.value = makeBug()
      s.assigneeId.value = ''
      await s.handleAssignConfirm()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择处理人')
      expect(mocks.assignBug).not.toHaveBeenCalled()
    })

    it('handleAssignConfirm toggles assigning state', async () => {
      let resolve: (v: unknown) => void
      mocks.assignBug.mockImplementation(() => new Promise((r) => { resolve = r }))
      const s = useBugList()
      s.assigningBug.value = makeBug()
      s.assigneeId.value = 'u3'
      const p = s.handleAssignConfirm()
      expect(s.assigning.value).toBe(true)
      resolve!(undefined)
      await p
      expect(s.assigning.value).toBe(false)
    })

    it('handleAssignConfirm error shows message', async () => {
      mocks.assignBug.mockRejectedValueOnce(new Error('fail'))
      const s = useBugList()
      s.assigningBug.value = makeBug()
      s.assigneeId.value = 'u3'
      await s.handleAssignConfirm()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
      expect(s.assigning.value).toBe(false)
    })

    it('handleAssignConfirm non-Error shows generic', async () => {
      mocks.assignBug.mockRejectedValueOnce(42)
      const s = useBugList()
      s.assigningBug.value = makeBug()
      s.assigneeId.value = 'u3'
      await s.handleAssignConfirm()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('指派失败')
    })
  })

  describe('handleMoreAction', () => {
    it('confirm calls handleConfirmBug', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const s = useBugList()
      s.handleMoreAction('confirm', makeBug())
      await vi.waitFor(() => {
        expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      })
    })

    it('reopen calls handleStatusAction', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue(null)
      const s = useBugList()
      s.handleMoreAction('reopen', makeBug())
      await vi.waitFor(() => {
        expect(mocks.promptStatusChangeComment).toHaveBeenCalledWith('active', 'active')
      })
    })

    it('assign opens assign dialog', () => {
      const s = useBugList()
      s.handleMoreAction('assign', makeBug())
      expect(s.assignDialogVisible.value).toBe(true)
    })

    it('copy navigates to create page', () => {
      const s = useBugList()
      s.handleMoreAction('copy', makeBug({ id: 'b99' }))
      expect(routerMock.push).toHaveBeenCalledWith({ path: '/workspace/projects/bugs/create', query: { copyFrom: 'b99' } })
    })
  })

  describe('handleCopyBug', () => {
    it('navigates with copyFrom param', () => {
      const s = useBugList()
      s.handleCopyBug(makeBug({ id: 'b42' }))
      expect(routerMock.push).toHaveBeenCalledWith({ path: '/workspace/projects/bugs/create', query: { copyFrom: 'b42' } })
    })
  })

  describe('viewMode switch', () => {
    it('switch to board loads board', async () => {
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      const s = useBugList()
      vi.clearAllMocks()
      mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
      s.viewMode.value = 'board'
      await vi.waitFor(() => {
        expect(mocks.fetchBugs).toHaveBeenCalled()
      })
    })
  })
})
