import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  AiPlanOrderRecommendItem,
  PlannedCases,
  SnapshotModule,
  TestPlanDetail,
  TestPlanProgress,
} from '@/types'

const mocks = vi.hoisted(() => ({
  getPlanDetail: vi.fn<() => Promise<TestPlanDetail>>(),
  getPlanProgress: vi.fn<() => Promise<TestPlanProgress>>(),
  getPlanModuleTree: vi.fn<() => Promise<SnapshotModule[]>>(),
  getPlanPlannedCases: vi.fn<() => Promise<PlannedCases[]>>(),
  updatePlanCases: vi.fn<() => Promise<void>>(),
  completePlan: vi.fn<() => Promise<void>>(),
  syncPlan: vi.fn<() => Promise<void>>(),
  getCaseDetail: vi.fn<(id: string) => Promise<{ id: string; documentId?: string | null }>>(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  useAuthStore: vi.fn(),
  useAiStore: vi.fn(),
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => { cb() },
  }
})

vi.mock('vue-router', () => ({
  useRouter: mocks.useRouter,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/project', () => ({
  getPlanDetail: mocks.getPlanDetail,
  getPlanProgress: mocks.getPlanProgress,
  getPlanModuleTree: mocks.getPlanModuleTree,
  getPlanPlannedCases: mocks.getPlanPlannedCases,
  updatePlanCases: mocks.updatePlanCases,
  completePlan: mocks.completePlan,
  syncPlan: mocks.syncPlan,
  getCaseDetail: mocks.getCaseDetail,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/stores/ai', () => ({
  useAiStore: mocks.useAiStore,
}))

vi.mock('@/components/project/PlanMindMap.vue', () => ({ default: {} }))
vi.mock('@/components/project/PlanOrderRecommend.vue', () => ({ default: {} }))

import { usePlanDetail } from './usePlanDetail'

function makePlan(overrides?: Partial<TestPlanDetail>): TestPlanDetail {
  return {
    id: 'plan-1',
    name: '测试计划',
    description: null,
    status: 'new',
    environment: null,
    startTime: null,
    endTime: null,
    executor: { id: 'user-1', name: '执行者' },
    createdAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function makeProgress(overrides?: Partial<TestPlanProgress>): TestPlanProgress {
  return {
    totalAssociated: 10,
    passed: 3,
    failed: 2,
    blocked: 1,
    untested: 4,
    progressPercent: 30,
    ...overrides,
  }
}

function makeModule(id: string, name: string, type: 'directory' | 'document' = 'directory', children: SnapshotModule[] = []): SnapshotModule {
  return { id, parentId: null, name, type, sortOrder: 0, children }
}

function setupMocks(overrides?: {
  plan?: TestPlanDetail
  progress?: TestPlanProgress
  tree?: SnapshotModule[]
  plannedCases?: PlannedCases[]
  userId?: string
  aiEnabled?: boolean
}) {
  mocks.getPlanDetail.mockResolvedValue(overrides?.plan ?? makePlan())
  mocks.getPlanProgress.mockResolvedValue(overrides?.progress ?? makeProgress())
  mocks.getPlanModuleTree.mockResolvedValue(overrides?.tree ?? [])
  mocks.useAuthStore.mockReturnValue({ user: { id: overrides?.userId ?? 'user-1' } })
  mocks.useAiStore.mockReturnValue({ aiEnabled: overrides?.aiEnabled ?? false })
}

describe('usePlanDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRouter.mockReturnValue({ push: vi.fn() })
  })

  function init() {
    return usePlanDetail({ planId: 'plan-1' })
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

    it('activeTab 初始为 records', () => {
      const s = init()
      expect(s.activeTab.value).toBe('records')
    })

    it('selectorVisible 初始为 false', () => {
      const s = init()
      expect(s.selectorVisible.value).toBe(false)
    })

    it('plannedCases 初始为空数组', () => {
      const s = init()
      expect(s.plannedCases.value).toEqual([])
    })

    it('recommendVisible 初始为 false', () => {
      const s = init()
      expect(s.recommendVisible.value).toBe(false)
    })

    it('recommendExcludeIds 初始为空数组', () => {
      const s = init()
      expect(s.recommendExcludeIds.value).toEqual([])
    })

    it('statusLabel 常量包含四种状态', () => {
      const s = init()
      expect(s.statusLabel.new).toBe('待开始')
      expect(s.statusLabel.in_progress).toBe('进行中')
      expect(s.statusLabel.completed).toBe('已完成')
      expect(s.statusLabel.closed).toBe('已关闭')
    })

    it('loading 最终为 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('computed canShowOrder', () => {
    it('aiEnabled 为 false 时返回 false', async () => {
      setupMocks({ aiEnabled: false })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canShowOrder.value).toBe(false)
    })

    it('aiEnabled 为 true 但 executor 与当前用户不同时返回 false', async () => {
      setupMocks({ aiEnabled: true, userId: 'user-1', plan: makePlan({ executor: { id: 'user-2', name: '其他' } }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canShowOrder.value).toBe(false)
    })

    it('aiEnabled 为 true 且 executor 为当前用户时返回 true', async () => {
      setupMocks({ aiEnabled: true, userId: 'user-1', plan: makePlan({ executor: { id: 'user-1', name: '执行者' } }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canShowOrder.value).toBe(true)
    })

    it('executor 为 null 时返回 false', async () => {
      setupMocks({ aiEnabled: true, plan: makePlan({ executor: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canShowOrder.value).toBe(false)
    })
  })

  describe('computed canAdjustCases', () => {
    it('status 为 new 时返回 true', async () => {
      setupMocks({ plan: makePlan({ status: 'new' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canAdjustCases.value).toBe(true)
    })

    it('status 为 in_progress 时返回 true', async () => {
      setupMocks({ plan: makePlan({ status: 'in_progress' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canAdjustCases.value).toBe(true)
    })

    it('status 为 completed 时返回 false', async () => {
      setupMocks({ plan: makePlan({ status: 'completed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canAdjustCases.value).toBe(false)
    })

    it('status 为 closed 时返回 false', async () => {
      setupMocks({ plan: makePlan({ status: 'closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.canAdjustCases.value).toBe(false)
    })
  })

  describe('load', () => {
    it('成功加载时填充 detail、progress 和 moduleTree', async () => {
      const plan = makePlan()
      const prog = makeProgress()
      const tree = [makeModule('d1', '目录')]
      setupMocks({ plan, progress: prog, tree })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.detail.value).toEqual(plan)
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
      mocks.getPlanDetail.mockRejectedValue(new Error('网络错误'))
      mocks.getPlanProgress.mockResolvedValue(makeProgress())
      mocks.getPlanModuleTree.mockResolvedValue([])
      mocks.useAuthStore.mockReturnValue({ user: { id: 'user-1' } })
      mocks.useAiStore.mockReturnValue({ aiEnabled: false })
      const s = init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.loading.value).toBe(false)
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      mocks.getPlanDetail.mockRejectedValue('string err')
      mocks.getPlanProgress.mockResolvedValue(makeProgress())
      mocks.getPlanModuleTree.mockResolvedValue([])
      mocks.useAuthStore.mockReturnValue({ user: { id: 'user-1' } })
      mocks.useAiStore.mockReturnValue({ aiEnabled: false })
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载计划详情失败')
    })

    it('无论成功失败 loading 最终均为 false', async () => {
      mocks.getPlanDetail.mockRejectedValue(new Error('fail'))
      mocks.getPlanProgress.mockResolvedValue(makeProgress())
      mocks.getPlanModuleTree.mockResolvedValue([])
      mocks.useAuthStore.mockReturnValue({ user: { id: 'user-1' } })
      mocks.useAiStore.mockReturnValue({ aiEnabled: false })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('handleComplete', () => {
    it('用户确认后调用 completePlan 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.completePlan.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.completePlan).toHaveBeenCalledWith('plan-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('计划已完成')
    })

    it('用户取消时不调用 completePlan', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.completePlan).not.toHaveBeenCalled()
    })

    it('completePlan 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.completePlan.mockRejectedValue(new Error('完成失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('完成失败')
    })

    it('completePlan 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.completePlan.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleComplete()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleSync', () => {
    it('用户确认后调用 syncPlan 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncPlan.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.syncPlan).toHaveBeenCalledWith('plan-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已同步')
    })

    it('用户取消时不调用 syncPlan', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.syncPlan).not.toHaveBeenCalled()
    })

    it('syncPlan 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncPlan.mockRejectedValue(new Error('同步失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('同步失败')
    })

    it('syncPlan 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncPlan.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleSync()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('同步失败')
    })

    it('同步成功后调用 mindMapRef.reload', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncPlan.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockReload = vi.fn()
      s.mindMapRef.value = { reload: mockReload } as never
      await s.handleSync()
      expect(mockReload).toHaveBeenCalled()
    })

    it('同步成功后调用 orderPanelRef.load', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.syncPlan.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockLoad = vi.fn()
      s.orderPanelRef.value = { load: mockLoad } as never
      await s.handleSync()
      expect(mockLoad).toHaveBeenCalled()
    })
  })

  describe('openCaseSelector', () => {
    it('成功加载后设置 plannedCases 并显示选择器', async () => {
      const cases = [{ documentId: 'doc-1', caseIds: ['c1', 'c2'] }]
      mocks.getPlanPlannedCases.mockResolvedValue(cases as PlannedCases[])
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openCaseSelector()
      expect(s.plannedCases.value).toEqual(cases)
      expect(s.selectorVisible.value).toBe(true)
    })

    it('加载失败时显示错误消息', async () => {
      mocks.getPlanPlannedCases.mockRejectedValue(new Error('加载失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openCaseSelector()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
      expect(s.selectorVisible.value).toBe(false)
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      mocks.getPlanPlannedCases.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openCaseSelector()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载规划用例失败')
    })
  })

  describe('handleCasesConfirm', () => {
    it('成功更新后显示成功消息并刷新', async () => {
      mocks.updatePlanCases.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const nodes = [{ documentId: 'doc-1', caseIds: ['c1'] }] as PlannedCases[]
      await s.handleCasesConfirm(nodes)
      expect(mocks.updatePlanCases).toHaveBeenCalledWith('plan-1', nodes)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('规划用例已更新')
    })

    it('更新成功后调用 mindMapRef.reload', async () => {
      mocks.updatePlanCases.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockReload = vi.fn()
      s.mindMapRef.value = { reload: mockReload } as never
      await s.handleCasesConfirm([])
      expect(mockReload).toHaveBeenCalled()
    })

    it('更新失败时显示错误', async () => {
      mocks.updatePlanCases.mockRejectedValue(new Error('更新失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleCasesConfirm([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新失败')
    })

    it('更新失败非 Error 异常显示通用消息', async () => {
      mocks.updatePlanCases.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleCasesConfirm([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新规划用例失败')
    })
  })

  describe('handleCasesRemoved', () => {
    it('调用 load 并刷新 orderPanelRef', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockLoad = vi.fn()
      s.orderPanelRef.value = { load: mockLoad } as never
      await s.handleCasesRemoved()
      expect(mockLoad).toHaveBeenCalled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('openRecommend', () => {
    it('成功加载后设置 recommendExcludeIds 并显示推荐面板', async () => {
      mocks.getPlanPlannedCases.mockResolvedValue([
        { documentId: 'doc-1', caseIds: ['c1', 'c2'] },
        { documentId: 'doc-2', caseIds: ['c3'] },
      ])
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openRecommend()
      expect(s.recommendExcludeIds.value).toEqual(['c1', 'c2', 'c3'])
      expect(s.recommendVisible.value).toBe(true)
    })

    it('加载失败时显示错误', async () => {
      mocks.getPlanPlannedCases.mockRejectedValue(new Error('加载失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openRecommend()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
      expect(s.recommendVisible.value).toBe(false)
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      mocks.getPlanPlannedCases.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openRecommend()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载规划用例失败')
    })

    it('plannedCases 为空数组时 recommendExcludeIds 为空', async () => {
      mocks.getPlanPlannedCases.mockResolvedValue([])
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.openRecommend()
      expect(s.recommendExcludeIds.value).toEqual([])
    })
  })

  describe('handleBringIn', () => {
    it('合并现有用例与推荐用例后显示选择器', async () => {
      mocks.getPlanPlannedCases.mockResolvedValue([
        { documentId: 'doc-1', caseIds: ['c1'] },
      ])
      mocks.getCaseDetail.mockImplementation(async (id: string) => ({
        id,
        documentId: 'doc-1',
      }))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleBringIn(['c2', 'c3'])
      expect(s.plannedCases.value).toEqual([
        { documentId: 'doc-1', caseIds: ['c1', 'c2', 'c3'] },
      ])
      expect(s.selectorVisible.value).toBe(true)
    })

    it('推荐用例属于不同文档时正确分组', async () => {
      mocks.getPlanPlannedCases.mockResolvedValue([])
      mocks.getCaseDetail.mockImplementation(async (id: string) => ({
        id,
        documentId: id === 'c1' ? 'doc-1' : 'doc-2',
      }))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleBringIn(['c1', 'c2'])
      expect(s.plannedCases.value).toHaveLength(2)
    })

    it('推荐用例 documentId 为 null 时跳过合并', async () => {
      mocks.getPlanPlannedCases.mockResolvedValue([])
      mocks.getCaseDetail.mockImplementation(async (id: string) => ({
        id,
        documentId: null,
      }))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleBringIn(['c1'])
      expect(s.plannedCases.value).toEqual([])
      expect(s.selectorVisible.value).toBe(true)
    })

    it('加载失败时显示错误', async () => {
      mocks.getPlanPlannedCases.mockRejectedValue(new Error('加载失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleBringIn(['c1'])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      mocks.getPlanPlannedCases.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleBringIn(['c1'])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载推荐用例失败')
    })

    it('与现有用例去重（同一 documentId 下重复 caseId 不重复添加）', async () => {
      mocks.getPlanPlannedCases.mockResolvedValue([
        { documentId: 'doc-1', caseIds: ['c1'] },
      ])
      mocks.getCaseDetail.mockImplementation(async (id: string) => ({
        id,
        documentId: 'doc-1',
      }))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleBringIn(['c1'])
      expect(s.plannedCases.value).toEqual([
        { documentId: 'doc-1', caseIds: ['c1'] },
      ])
    })
  })

  describe('refreshProgress', () => {
    it('成功刷新 progress 和 detail', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const newProgress = makeProgress({ passed: 5 })
      const newPlan = makePlan({ name: '更新计划' })
      mocks.getPlanProgress.mockResolvedValue(newProgress)
      mocks.getPlanDetail.mockResolvedValue(newPlan)
      await s.refreshProgress()
      expect(s.progress.value).toEqual(newProgress)
      expect(s.detail.value).toEqual(newPlan)
    })

    it('刷新失败时不抛出异常', async () => {
      mocks.getPlanProgress.mockRejectedValue(new Error('fail'))
      mocks.getPlanDetail.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await expect(s.refreshProgress()).resolves.toBeUndefined()
    })
  })

  describe('handleOrderLocate', () => {
    it('切换到 records 标签并定位节点', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockLocate = vi.fn().mockReturnValue(true)
      s.mindMapRef.value = { locateNode: mockLocate } as never
      await s.handleOrderLocate('snap-1')
      expect(s.activeTab.value).toBe('records')
      expect(mockLocate).toHaveBeenCalledWith('snap-1')
    })

    it('节点未找到时显示提示消息', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockLocate = vi.fn().mockReturnValue(false)
      s.mindMapRef.value = { locateNode: mockLocate } as never
      await s.handleOrderLocate('snap-missing')
      expect(mocks.ElMessage.info).toHaveBeenCalledWith('该建议指向的用例不在当前文档，请切换左侧文档后重试')
    })

    it('节点找到时不显示提示', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockLocate = vi.fn().mockReturnValue(true)
      s.mindMapRef.value = { locateNode: mockLocate } as never
      await s.handleOrderLocate('snap-1')
      expect(mocks.ElMessage.info).not.toHaveBeenCalled()
    })
  })

  describe('handleOrderResult', () => {
    it('调用 mindMapRef.setOrderBadges', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockSetBadges = vi.fn()
      s.mindMapRef.value = { setOrderBadges: mockSetBadges } as never
      const items = [{ snapshotNodeId: 's1', order: 1 }] as AiPlanOrderRecommendItem[]
      s.handleOrderResult(items)
      expect(mockSetBadges).toHaveBeenCalledWith(items)
    })
  })

  describe('handleOrderSelect', () => {
    it('切换到 order 标签并滚动到指定位置', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockScrollToOrder = vi.fn()
      s.orderPanelRef.value = { scrollToOrder: mockScrollToOrder } as never
      await s.handleOrderSelect(3)
      expect(s.activeTab.value).toBe('order')
      expect(mockScrollToOrder).toHaveBeenCalledWith(3)
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
