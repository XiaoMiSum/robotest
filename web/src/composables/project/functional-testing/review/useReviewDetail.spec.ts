import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  PlannedCases,
  SnapshotModule,
  TestReviewDetail,
  TestReviewProgress,
} from '@/types'

const mocks = vi.hoisted(() => ({
  getReviewDetail: vi.fn<() => Promise<TestReviewDetail>>(),
  getReviewProgress: vi.fn<() => Promise<TestReviewProgress>>(),
  getReviewModuleTree: vi.fn<() => Promise<SnapshotModule[]>>(),
  getReviewPlannedCases: vi.fn<() => Promise<PlannedCases[]>>(),
  updateReviewCases: vi.fn<() => Promise<void>>(),
  completeReview: vi.fn<() => Promise<void>>(),
  rejectReview: vi.fn<() => Promise<void>>(),
  reopenReview: vi.fn<() => Promise<void>>(),
  syncReview: vi.fn<() => Promise<void>>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  useAuthStore: vi.fn(),
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => { cb() },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/project', () => ({
  getReviewDetail: mocks.getReviewDetail,
  getReviewProgress: mocks.getReviewProgress,
  getReviewModuleTree: mocks.getReviewModuleTree,
  getReviewPlannedCases: mocks.getReviewPlannedCases,
  updateReviewCases: mocks.updateReviewCases,
  completeReview: mocks.completeReview,
  rejectReview: mocks.rejectReview,
  reopenReview: mocks.reopenReview,
  syncReview: mocks.syncReview,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

import { useReviewDetail } from './useReviewDetail'

function makeReview(overrides?: Partial<TestReviewDetail>): TestReviewDetail {
  return {
    id: 'review-1',
    title: '测试评审',
    description: null,
    status: 'in_progress',
    initiator: { id: 'user-1', name: '发起人' },
    participantIds: [],
    createdAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function makeProgress(overrides?: Partial<TestReviewProgress>): TestReviewProgress {
  return {
    totalAssociated: 10,
    passed: 3,
    failed: 2,
    pending: 5,
    progressPercent: 30,
    ...overrides,
  }
}

function makeModule(
  id: string,
  name: string,
  type: 'directory' | 'document' = 'directory',
  children: SnapshotModule[] = [],
): SnapshotModule {
  return { id, parentId: null, name, type, sortOrder: 0, children }
}

function setupMocks(overrides?: {
  review?: TestReviewDetail
  progress?: TestReviewProgress
  tree?: SnapshotModule[]
  plannedCases?: PlannedCases[]
  userId?: string
}) {
  mocks.getReviewDetail.mockResolvedValue(overrides?.review ?? makeReview())
  mocks.getReviewProgress.mockResolvedValue(overrides?.progress ?? makeProgress())
  mocks.getReviewModuleTree.mockResolvedValue(overrides?.tree ?? [])
  mocks.getReviewPlannedCases.mockResolvedValue(overrides?.plannedCases ?? [])
  mocks.useAuthStore.mockReturnValue({ user: { id: overrides?.userId ?? 'user-1' } })
}

describe('useReviewDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function init() {
    return useReviewDetail({ reviewId: 'review-1' })
  }

  describe('初始状态', () => {
    it('detail 和 progress 初始为 null', () => {
      const s = init()
      expect(s.detail.value).toBeNull()
      expect(s.progress.value).toBeNull()
    })

    it('moduleTree 初始为空数组', () => {
      const s = init()
      expect(s.moduleTree.value).toEqual([])
    })

    it('selectedDocId 初始为空字符串', () => {
      const s = init()
      expect(s.selectedDocId.value).toBe('')
    })

    it('selectorVisible 初始为 false', () => {
      const s = init()
      expect(s.selectorVisible.value).toBe(false)
    })

    it('plannedCases 初始为空数组', () => {
      const s = init()
      expect(s.plannedCases.value).toEqual([])
    })

    it('loading 最终为 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('computed canComplete', () => {
    it('progress 为 null 时返回 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.progress.value = null
      expect(s.canComplete.value).toBe(false)
    })

    it('pending 和 failed 均为 0 时返回 true', async () => {
      setupMocks({ progress: makeProgress({ pending: 0, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canComplete.value).toBe(true)
    })

    it('pending 大于 0 时返回 false', async () => {
      setupMocks({ progress: makeProgress({ pending: 1, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canComplete.value).toBe(false)
    })

    it('failed 大于 0 时返回 false', async () => {
      setupMocks({ progress: makeProgress({ pending: 0, failed: 1 }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canComplete.value).toBe(false)
    })
  })

  describe('load', () => {
    it('成功加载时填充 detail、progress 和 moduleTree', async () => {
      const review = makeReview()
      const prog = makeProgress()
      const tree = [makeModule('d1', '目录')]
      setupMocks({ review, progress: prog, tree })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.detail.value).toEqual(review)
      expect(s.progress.value).toEqual(prog)
      expect(s.moduleTree.value).toEqual(tree)
    })

    it('模块树无 document 节点时 selectedDocId 保持空字符串', async () => {
      setupMocks({ tree: [makeModule('d1', '目录', 'directory')] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.selectedDocId.value).toBe('')
    })

    it('模块树含 document 节点时 selectedDocId 设为首个 document id', async () => {
      const doc = makeModule('doc-1', '文档1', 'document')
      setupMocks({ tree: [doc] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.selectedDocId.value).toBe('doc-1')
    })

    it('嵌套 document 节点时 selectedDocId 正确选取', async () => {
      const doc = makeModule('doc-2', '深层文档', 'document')
      const dir = makeModule('d1', '目录', 'directory', [doc])
      setupMocks({ tree: [dir] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.selectedDocId.value).toBe('doc-2')
    })

    it('selectedDocId 已存在且仍在模块树中时保留', async () => {
      const doc = makeModule('doc-1', '文档1', 'document')
      setupMocks({ tree: [doc] })
      const s = init()
      s.selectedDocId.value = 'doc-1'
      await s.load()
      expect(s.selectedDocId.value).toBe('doc-1')
    })

    it('selectedDocId 已存在但不在模块树中时重新选取', async () => {
      const doc = makeModule('doc-2', '文档2', 'document')
      setupMocks({ tree: [doc] })
      const s = init()
      s.selectedDocId.value = 'doc-missing'
      await s.load()
      expect(s.selectedDocId.value).toBe('doc-2')
    })

    it('加载失败时显示错误消息', async () => {
      mocks.getReviewDetail.mockRejectedValue(new Error('网络错误'))
      mocks.getReviewProgress.mockResolvedValue(makeProgress())
      mocks.getReviewModuleTree.mockResolvedValue([])
      mocks.useAuthStore.mockReturnValue({ user: { id: 'user-1' } })
      const s = init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.loading.value).toBe(false)
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      mocks.getReviewDetail.mockRejectedValue('string err')
      mocks.getReviewProgress.mockResolvedValue(makeProgress())
      mocks.getReviewModuleTree.mockResolvedValue([])
      mocks.useAuthStore.mockReturnValue({ user: { id: 'user-1' } })
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载评审详情失败')
    })

    it('无论成功失败 loading 最终均为 false', async () => {
      mocks.getReviewDetail.mockRejectedValue(new Error('fail'))
      mocks.getReviewProgress.mockResolvedValue(makeProgress())
      mocks.getReviewModuleTree.mockResolvedValue([])
      mocks.useAuthStore.mockReturnValue({ user: { id: 'user-1' } })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('handleComplete', () => {
    it('canComplete 为 false 时显示警告且不调用接口', async () => {
      setupMocks({ progress: makeProgress({ pending: 1, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith(
        '仍有待评审或不通过的用例，全部通过后才能完成评审',
      )
      expect(mocks.completeReview).not.toHaveBeenCalled()
    })

    it('用户确认后调用 completeReview 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.completeReview.mockResolvedValue()
      setupMocks({ progress: makeProgress({ pending: 0, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.completeReview).toHaveBeenCalledWith('review-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('评审已完成')
    })

    it('用户取消时不调用 completeReview', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks({ progress: makeProgress({ pending: 0, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.completeReview).not.toHaveBeenCalled()
    })

    it('completeReview 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.completeReview.mockRejectedValue(new Error('完成失败'))
      setupMocks({ progress: makeProgress({ pending: 0, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('完成失败')
    })

    it('completeReview 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.completeReview.mockRejectedValue(42)
      setupMocks({ progress: makeProgress({ pending: 0, failed: 0 }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleReject', () => {
    it('用户确认后调用 rejectReview 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.rejectReview.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.rejectReview).toHaveBeenCalledWith('review-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('评审已驳回')
    })

    it('用户取消时不调用 rejectReview', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.rejectReview).not.toHaveBeenCalled()
    })

    it('rejectReview 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.rejectReview.mockRejectedValue(new Error('驳回失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('驳回失败')
    })

    it('rejectReview 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.rejectReview.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('驳回评审失败')
    })
  })

  describe('handleReopen', () => {
    it('用户确认后调用 reopenReview 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.reopenReview.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.reopenReview).toHaveBeenCalledWith('review-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('评审已重新发起')
    })

    it('用户取消时不调用 reopenReview', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.reopenReview).not.toHaveBeenCalled()
    })

    it('reopenReview 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.reopenReview.mockRejectedValue(new Error('重新发起失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('重新发起失败')
    })

    it('reopenReview 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.reopenReview.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('重新发起失败')
    })
  })

  describe('handleSync', () => {
    it('用户确认后调用 syncReview 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncReview.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.syncReview).toHaveBeenCalledWith('review-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已同步')
    })

    it('用户取消时不调用 syncReview', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.syncReview).not.toHaveBeenCalled()
    })

    it('syncReview 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncReview.mockRejectedValue(new Error('同步失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('同步失败')
    })

    it('syncReview 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncReview.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('同步失败')
    })

    it('同步成功后调用 mindMapRef.reload', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncReview.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockReload = vi.fn()
      s.mindMapRef.value = { reload: mockReload } as never
      await s.handleSync()
      expect(mockReload).toHaveBeenCalled()
    })
  })

  describe('openCaseSelector', () => {
    it('成功加载后设置 plannedCases 并显示选择器', async () => {
      const cases = [{ documentId: 'doc-1', caseIds: ['c1', 'c2'] }]
      setupMocks({ plannedCases: cases as PlannedCases[] })
      const s = init()
      await vi.dynamicImportSettled()
      await s.openCaseSelector()
      expect(s.plannedCases.value).toEqual(cases)
      expect(s.selectorVisible.value).toBe(true)
    })

    it('加载失败时显示错误消息', async () => {
      setupMocks()
      mocks.getReviewPlannedCases.mockRejectedValue(new Error('加载失败'))
      const s = init()
      await vi.dynamicImportSettled()
      await s.openCaseSelector()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
      expect(s.selectorVisible.value).toBe(false)
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      setupMocks()
      mocks.getReviewPlannedCases.mockRejectedValue(42)
      const s = init()
      await vi.dynamicImportSettled()
      await s.openCaseSelector()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载规划用例失败')
    })
  })

  describe('handleCasesConfirm', () => {
    it('成功更新后显示成功消息并刷新', async () => {
      mocks.updateReviewCases.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const nodes = [{ documentId: 'doc-1', caseIds: ['c1'] }] as PlannedCases[]
      await s.handleCasesConfirm(nodes)
      expect(mocks.updateReviewCases).toHaveBeenCalledWith('review-1', nodes)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('规划用例已更新')
    })

    it('更新成功后调用 mindMapRef.reload', async () => {
      mocks.updateReviewCases.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockReload = vi.fn()
      s.mindMapRef.value = { reload: mockReload } as never
      await s.handleCasesConfirm([])
      expect(mockReload).toHaveBeenCalled()
    })

    it('更新失败时显示错误', async () => {
      mocks.updateReviewCases.mockRejectedValue(new Error('更新失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleCasesConfirm([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新失败')
    })

    it('更新失败非 Error 异常显示通用消息', async () => {
      mocks.updateReviewCases.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleCasesConfirm([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新规划用例失败')
    })
  })

  describe('handleCasesRemoved', () => {
    it('调用 load 刷新进度与快照树', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleCasesRemoved()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('refreshProgress', () => {
    it('成功刷新 progress 和 detail', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const newProgress = makeProgress({ passed: 5 })
      const newReview = makeReview({ title: '更新评审' })
      mocks.getReviewProgress.mockResolvedValue(newProgress)
      mocks.getReviewDetail.mockResolvedValue(newReview)
      await s.refreshProgress()
      expect(s.progress.value).toEqual(newProgress)
      expect(s.detail.value).toEqual(newReview)
    })

    it('刷新失败时不抛出异常', async () => {
      mocks.getReviewProgress.mockRejectedValue(new Error('fail'))
      mocks.getReviewDetail.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await expect(s.refreshProgress()).resolves.toBeUndefined()
    })
  })

  describe('firstDocument / findDoc 边界', () => {
    it('模块树为空时 load 后 selectedDocId 为空', async () => {
      setupMocks({ tree: [] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.selectedDocId.value).toBe('')
    })

    it('多层嵌套 directory 后的 document 仍能被选取', async () => {
      const doc = makeModule('doc-deep', '深层文档', 'document')
      const d3 = makeModule('d3', '三级', 'directory', [doc])
      const d2 = makeModule('d2', '二级', 'directory', [d3])
      const d1 = makeModule('d1', '一级', 'directory', [d2])
      setupMocks({ tree: [d1] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.selectedDocId.value).toBe('doc-deep')
    })
  })
})
