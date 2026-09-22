import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { BugAttachment, BugDetail, BugLog, ProjectModule, TestCaseNode, TestPlanListItem, WorkspaceMember } from '@/types'

const mocks = vi.hoisted(() => ({
  getBugDetail: vi.fn<() => Promise<BugDetail>>(),
  getBugLogs: vi.fn<() => Promise<BugLog[]>>(),
  fetchMembers: vi.fn<() => Promise<{ list: WorkspaceMember[] }>>(),
  fetchProjectModuleTree: vi.fn<() => Promise<ProjectModule[]>>(),
  fetchPlans: vi.fn<() => Promise<{ list: TestPlanListItem[] }>>(),
  fetchBugAttachments: vi.fn<() => Promise<BugAttachment[]>>(),
  updateBug: vi.fn<() => Promise<void>>(),
  assignBug: vi.fn<() => Promise<void>>(),
  confirmBug: vi.fn<() => Promise<void>>(),
  changeBugStatus: vi.fn<() => Promise<void>>(),
  uploadBugAttachment: vi.fn<() => Promise<BugAttachment>>(),
  downloadBugAttachment: vi.fn<() => Promise<void>>(),
  deleteBugAttachment: vi.fn<() => Promise<void>>(),
  getCaseDetail: vi.fn<() => Promise<TestCaseNode>>(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  promptStatusChangeComment: vi.fn<() => Promise<string | null>>(),
  typeBadge: vi.fn(),
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
  getBugDetail: mocks.getBugDetail,
  getBugLogs: mocks.getBugLogs,
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  fetchPlans: mocks.fetchPlans,
  fetchBugAttachments: mocks.fetchBugAttachments,
  updateBug: mocks.updateBug,
  assignBug: mocks.assignBug,
  confirmBug: mocks.confirmBug,
  changeBugStatus: mocks.changeBugStatus,
  uploadBugAttachment: mocks.uploadBugAttachment,
  downloadBugAttachment: mocks.downloadBugAttachment,
  deleteBugAttachment: mocks.deleteBugAttachment,
  getCaseDetail: mocks.getCaseDetail,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('@/composables/project/bug/bugStatus', () => ({
  BUG_RESOLUTION_LABEL: {},
  BUG_STATUS_LABEL: {},
  BUG_STATUS_TAG_TYPE: {},
  BUG_TYPE_LABEL: {},
  promptStatusChangeComment: mocks.promptStatusChangeComment,
}))

vi.mock('@/components/project/functional-testing/minder/badges', () => ({
  typeBadge: mocks.typeBadge,
}))

import { useBugDetail } from './useBugDetail'

const makeBug = (overrides?: Partial<BugDetail>): BugDetail => ({
  id: 'bug-1',
  title: '测试缺陷',
  severity: 'serious',
  priority: 'high',
  status: 'active',
  bugType: 'code_error',
  reproSteps: '复现步骤',
  moduleId: null,
  moduleName: null,
  keywords: null,
  dueDate: null,
  confirmed: false,
  reopenCount: 0,
  lastReopenedAt: null,
  resolution: null,
  duplicateOfBugId: null,
  resolvedBy: null,
  resolvedAt: null,
  closedBy: null,
  closedAt: null,
  reporter: { id: 'u1', name: '张三' },
  assignee: null,
  relatedCaseId: null,
  relatedPlanId: null,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
  recentLogs: [],
  ...overrides,
})

function makeModule(id: string, name: string, children: ProjectModule[] = []): ProjectModule {
  return { id, name, type: 'directory', children } as unknown as ProjectModule
}

function makeDocModule(id: string, name: string): ProjectModule {
  return { id, name, type: 'document', children: [] } as unknown as ProjectModule
}

function makeCase(id: string, title: string, type: string, children: TestCaseNode[] = []): TestCaseNode {
  return {
    id,
    parentId: null,
    type: type as TestCaseNode['type'],
    title,
    priority: null,
    sortOrder: 0,
    version: 1,
    children,
    documentId: 'doc-1',
  }
}

function setupMocks(overrides?: { bug?: BugDetail; logs?: BugLog[]; members?: WorkspaceMember[]; moduleTree?: ProjectModule[]; plans?: TestPlanListItem[]; attachments?: BugAttachment[] }) {
  mocks.getBugDetail.mockResolvedValue(overrides?.bug ?? makeBug())
  mocks.getBugLogs.mockResolvedValue(overrides?.logs ?? [])
  mocks.fetchMembers.mockResolvedValue({ list: overrides?.members ?? [] })
  mocks.fetchProjectModuleTree.mockResolvedValue(overrides?.moduleTree ?? [])
  mocks.fetchPlans.mockResolvedValue({ list: overrides?.plans ?? [] })
  mocks.fetchBugAttachments.mockResolvedValue(overrides?.attachments ?? [])
}

describe('useBugDetail', () => {
  let routerPush: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    routerPush = vi.fn()
    mocks.useRouter.mockReturnValue({ push: routerPush })
  })

  function init() {
    return useBugDetail({ bugId: 'bug-1' })
  }

  describe('初始状态', () => {
    it('loading 和 saving 最终均为 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.loading.value).toBe(false)
      expect(s.saving.value).toBe(false)
    })

    it('detail 为 null', () => {
      const s = init()
      expect(s.detail.value).toBeNull()
    })

    it('logs 为空数组', () => {
      const s = init()
      expect(s.logs.value).toEqual([])
    })

    it('memberOptions 为空数组', () => {
      const s = init()
      expect(s.memberOptions.value).toEqual([])
    })

    it('form 字段全部为空字符串', () => {
      const s = init()
      expect(s.form.title).toBe('')
      expect(s.form.severity).toBe('')
      expect(s.form.priority).toBe('')
      expect(s.form.bugType).toBe('')
      expect(s.form.moduleId).toBe('')
      expect(s.form.keywords).toBe('')
      expect(s.form.dueDate).toBe('')
      expect(s.form.reproSteps).toBe('')
      expect(s.form.assigneeId).toBe('')
      expect(s.form.relatedCaseId).toBe('')
      expect(s.form.relatedPlanId).toBe('')
    })

    it('resolveDialogVisible 为 false', () => {
      const s = init()
      expect(s.resolveDialogVisible.value).toBe(false)
    })

    it('attachments 为空数组', () => {
      const s = init()
      expect(s.attachments.value).toEqual([])
    })

    it('uploading 为 false', () => {
      const s = init()
      expect(s.uploading.value).toBe(false)
    })

    it('caseSelectorVisible 为 false', () => {
      const s = init()
      expect(s.caseSelectorVisible.value).toBe(false)
    })

    it('caseDetail 为 null', () => {
      const s = init()
      expect(s.caseDetail.value).toBeNull()
    })

    it('caseDetailLoading 为 false', () => {
      const s = init()
      expect(s.caseDetailLoading.value).toBe(false)
    })

    it('导出常量存在', () => {
      const s = init()
      expect(s.severityLabel).toBeDefined()
      expect(s.priorityLabel).toBeDefined()
      expect(s.severityType).toBeDefined()
      expect(s.priorityType).toBeDefined()
      expect(s.statusLabel).toBeDefined()
      expect(s.BUG_RESOLUTION_LABEL).toBeDefined()
      expect(s.BUG_STATUS_TAG_TYPE).toBeDefined()
      expect(s.BUG_TYPE_LABEL).toBeDefined()
    })
  })

  describe('computed isClosed / isActive / isResolved / isRejected', () => {
    it('status 为 active 时 isActive 为 true', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.isActive.value).toBe(true)
      expect(s.isClosed.value).toBe(false)
      expect(s.isResolved.value).toBe(false)
      expect(s.isRejected.value).toBe(false)
    })

    it('status 为 closed 时 isClosed 为 true', async () => {
      setupMocks({ bug: makeBug({ status: 'closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.isClosed.value).toBe(true)
      expect(s.isActive.value).toBe(false)
    })

    it('status 为 resolved 时 isResolved 为 true', async () => {
      setupMocks({ bug: makeBug({ status: 'resolved' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.isResolved.value).toBe(true)
    })

    it('status 为 rejected 时 isRejected 为 true', async () => {
      setupMocks({ bug: makeBug({ status: 'rejected' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.isRejected.value).toBe(true)
    })
  })

  describe('load', () => {
    it('加载成功时填充 detail、logs、memberOptions 和 form', async () => {
      const bug = makeBug({
        title: '缺陷标题',
        severity: 'fatal',
        priority: 'low',
        bugType: 'ui_improvement',
        moduleId: 'mod-1',
        keywords: '关键字',
        dueDate: '2025-06-01',
        reproSteps: '步骤详情',
        assignee: { id: 'u2', name: '李四' },
        relatedCaseId: 'case-1',
        relatedPlanId: 'plan-1',
      })
      setupMocks({
        bug,
        logs: [{ id: 'log-1' } as BugLog],
        members: [{ userId: 'u2', username: '李四', email: 'test@test.com', workspaceRole: 'member', joinedAt: '2024-01-01' } as WorkspaceMember],
      })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.detail.value).toEqual(bug)
      expect(s.logs.value).toEqual([{ id: 'log-1' }])
      expect(s.memberOptions.value).toEqual([{ userId: 'u2', username: '李四', email: 'test@test.com', workspaceRole: 'member', joinedAt: '2024-01-01' }])
      expect(s.form.title).toBe('缺陷标题')
      expect(s.form.severity).toBe('fatal')
      expect(s.form.priority).toBe('low')
      expect(s.form.bugType).toBe('ui_improvement')
      expect(s.form.moduleId).toBe('mod-1')
      expect(s.form.keywords).toBe('关键字')
      expect(s.form.dueDate).toBe('2025-06-01')
      expect(s.form.reproSteps).toBe('步骤详情')
      expect(s.form.assigneeId).toBe('u2')
      expect(s.form.relatedCaseId).toBe('case-1')
      expect(s.form.relatedPlanId).toBe('plan-1')
    })

    it('加载失败时显示错误消息', async () => {
      mocks.getBugDetail.mockRejectedValue(new Error('网络错误'))
      mocks.getBugLogs.mockResolvedValue([])
      mocks.fetchMembers.mockResolvedValue({ list: [] })
      mocks.fetchProjectModuleTree.mockResolvedValue([])
      mocks.fetchPlans.mockResolvedValue({ list: [] })
      mocks.fetchBugAttachments.mockResolvedValue([])
      const s = init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.loading.value).toBe(false)
    })

    it('加载失败非 Error 异常显示通用消息', async () => {
      mocks.getBugDetail.mockRejectedValue('string err')
      mocks.getBugLogs.mockResolvedValue([])
      mocks.fetchMembers.mockResolvedValue({ list: [] })
      mocks.fetchProjectModuleTree.mockResolvedValue([])
      mocks.fetchPlans.mockResolvedValue({ list: [] })
      mocks.fetchBugAttachments.mockResolvedValue([])
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载缺陷详情失败')
    })

    it('null 字段使用默认空字符串', async () => {
      setupMocks({
        bug: makeBug({
          moduleId: null,
          keywords: null,
          dueDate: null,
          reproSteps: null,
          assignee: null,
          relatedCaseId: null,
          relatedPlanId: null,
        }),
      })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.form.moduleId).toBe('')
      expect(s.form.keywords).toBe('')
      expect(s.form.dueDate).toBe('')
      expect(s.form.reproSteps).toBe('')
      expect(s.form.assigneeId).toBe('')
      expect(s.form.relatedCaseId).toBe('')
      expect(s.form.relatedPlanId).toBe('')
    })
  })

  describe('handleSave', () => {
    it('detail 为 null 时直接返回', async () => {
      const s = init()
      await s.handleSave()
      expect(mocks.updateBug).not.toHaveBeenCalled()
    })

    it('成功保存时调用 updateBug 并显示成功消息', async () => {
      setupMocks({ bug: makeBug({ assignee: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.title = '新标题'
      s.form.severity = 'fatal'
      s.form.priority = 'low'
      s.form.bugType = 'ui_improvement'
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.updateBug).toHaveBeenCalledWith('bug-1', expect.objectContaining({
        title: '新标题',
        severity: 'fatal',
        priority: 'low',
        bugType: 'ui_improvement',
      }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
    })

    it('assignee 变更时调用 assignBug', async () => {
      setupMocks({ bug: makeBug({ assignee: { id: 'u1', name: '张三' } }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.assigneeId = 'u2'
      mocks.updateBug.mockResolvedValue()
      mocks.assignBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.assignBug).toHaveBeenCalledWith('bug-1', 'u2')
    })

    it('assignee 未变更时不调用 assignBug', async () => {
      setupMocks({ bug: makeBug({ assignee: { id: 'u1', name: '张三' } }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.assigneeId = 'u1'
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.assignBug).not.toHaveBeenCalled()
    })

    it('保存失败时显示错误消息', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      mocks.updateBug.mockRejectedValue(new Error('保存失败'))
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
      expect(s.saving.value).toBe(false)
    })

    it('保存失败非 Error 异常显示通用消息', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      mocks.updateBug.mockRejectedValue(42)
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('trim 空白标题后提交', async () => {
      setupMocks({ bug: makeBug({ title: '原始标题', assignee: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.title = '  新标题  '
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.updateBug).toHaveBeenCalledWith('bug-1', expect.objectContaining({ title: '新标题' }))
    })

    it('空 moduleId 等字段提交 undefined', async () => {
      setupMocks({ bug: makeBug({ moduleId: 'mod-1', assignee: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.moduleId = ''
      s.form.keywords = ''
      s.form.dueDate = ''
      s.form.reproSteps = ''
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.updateBug).toHaveBeenCalledWith('bug-1', expect.objectContaining({
        moduleId: undefined,
        keywords: undefined,
        dueDate: undefined,
        reproSteps: undefined,
      }))
    })

    it('relatedCaseId/relatedPlanId 未变更时提交 undefined', async () => {
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1', relatedPlanId: 'plan-1', assignee: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.updateBug).toHaveBeenCalledWith('bug-1', expect.objectContaining({
        relatedCaseId: undefined,
        relatedPlanId: undefined,
      }))
    })

    it('relatedCaseId 变更时提交新值', async () => {
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1', assignee: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.relatedCaseId = 'case-2'
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.updateBug).toHaveBeenCalledWith('bug-1', expect.objectContaining({ relatedCaseId: 'case-2' }))
    })

    it('relatedPlanId 变更时提交新值', async () => {
      setupMocks({ bug: makeBug({ relatedPlanId: 'plan-1', assignee: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.relatedPlanId = 'plan-2'
      mocks.updateBug.mockResolvedValue()
      await s.handleSave()
      expect(mocks.updateBug).toHaveBeenCalledWith('bug-1', expect.objectContaining({ relatedPlanId: 'plan-2' }))
    })
  })

  describe('handleConfirm', () => {
    it('用户确认后调用 confirmBug 并刷新', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.confirmBug.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleConfirm()
      expect(mocks.confirmBug).toHaveBeenCalledWith('bug-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已确认')
    })

    it('用户取消时不调用 confirmBug', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleConfirm()
      expect(mocks.confirmBug).not.toHaveBeenCalled()
    })

    it('confirmBug 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.confirmBug.mockRejectedValue(new Error('确认失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleConfirm()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('确认失败')
    })

    it('confirmBug 非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.confirmBug.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleConfirm()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('确认失败')
    })
  })

  describe('handleResolve', () => {
    it('成功解决时调用 changeBugStatus', async () => {
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleResolve({ resolution: 'fixed' })
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', { status: 'resolved', resolution: 'fixed' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已解决')
    })

    it('解决失败时显示错误', async () => {
      mocks.changeBugStatus.mockRejectedValue(new Error('解决失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleResolve({ resolution: 'fixed' })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('解决失败')
    })

    it('解决失败非 Error 异常显示通用消息', async () => {
      mocks.changeBugStatus.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleResolve({ resolution: 'fixed' })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('解决失败')
    })

    it('带 comment 和 duplicateOfBugId 参数时传递', async () => {
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleResolve({ resolution: 'duplicate', duplicateOfBugId: 'bug-2', comment: '重复' })
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', {
        status: 'resolved',
        resolution: 'duplicate',
        duplicateOfBugId: 'bug-2',
        comment: '重复',
      })
    })
  })

  describe('handleReject', () => {
    it('detail 为 null 时直接返回', async () => {
      const s = init()
      await s.handleReject()
      expect(mocks.promptStatusChangeComment).not.toHaveBeenCalled()
    })

    it('用户确认后调用 changeBugStatus', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('拒绝原因')
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', { status: 'rejected', comment: '拒绝原因' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已拒绝')
    })

    it('用户取消时不调用 changeBugStatus', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue(null)
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('comment 为空字符串时提交 undefined', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('')
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', { status: 'rejected', comment: undefined })
    })

    it('失败时显示错误', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('原因')
      mocks.changeBugStatus.mockRejectedValue(new Error('拒绝失败'))
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('拒绝失败')
    })

    it('失败非 Error 异常显示通用消息', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('原因')
      mocks.changeBugStatus.mockRejectedValue(42)
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReject()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('拒绝失败')
    })
  })

  describe('handleClose', () => {
    it('detail 为 null 时直接返回', async () => {
      const s = init()
      await s.handleClose()
      expect(mocks.promptStatusChangeComment).not.toHaveBeenCalled()
    })

    it('用户确认后调用 changeBugStatus', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('关闭原因')
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleClose()
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', { status: 'closed', comment: '关闭原因' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已关闭')
    })

    it('用户取消时不调用 changeBugStatus', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue(null)
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleClose()
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('comment 为空字符串时提交 undefined', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('')
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleClose()
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', { status: 'closed', comment: undefined })
    })

    it('失败时显示错误', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('原因')
      mocks.changeBugStatus.mockRejectedValue(new Error('关闭失败'))
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleClose()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('关闭失败')
    })

    it('失败非 Error 异常显示通用消息', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('原因')
      mocks.changeBugStatus.mockRejectedValue(42)
      setupMocks({ bug: makeBug({ status: 'active' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleClose()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('关闭失败')
    })
  })

  describe('handleReopen', () => {
    it('detail 为 null 时直接返回', async () => {
      const s = init()
      await s.handleReopen()
      expect(mocks.promptStatusChangeComment).not.toHaveBeenCalled()
    })

    it('用户确认后调用 changeBugStatus', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('重新打开')
      mocks.changeBugStatus.mockResolvedValue()
      setupMocks({ bug: makeBug({ status: 'closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('bug-1', { status: 'active', comment: '重新打开' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已激活')
    })

    it('用户取消时不调用 changeBugStatus', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue(null)
      setupMocks({ bug: makeBug({ status: 'closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.changeBugStatus).not.toHaveBeenCalled()
    })

    it('失败时显示错误', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('原因')
      mocks.changeBugStatus.mockRejectedValue(new Error('激活失败'))
      setupMocks({ bug: makeBug({ status: 'closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('激活失败')
    })

    it('失败非 Error 异常显示通用消息', async () => {
      mocks.promptStatusChangeComment.mockResolvedValue('原因')
      mocks.changeBugStatus.mockRejectedValue(42)
      setupMocks({ bug: makeBug({ status: 'closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleReopen()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('激活失败')
    })
  })

  describe('dirTree', () => {
    it('过滤掉 document 类型节点', async () => {
      const tree: ProjectModule[] = [
        makeModule('d1', '目录', [makeDocModule('doc-1', '文档'), makeModule('d2', '子目录')]),
        makeDocModule('doc-2', '文档2'),
      ]
      setupMocks({ moduleTree: tree })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.dirTree.value).toHaveLength(1)
      expect(s.dirTree.value[0]).toHaveProperty('id', 'd1')
      const children = (s.dirTree.value[0] as unknown as { children: ProjectModule[] }).children
      expect(children).toHaveLength(1)
      expect(children[0]).toHaveProperty('id', 'd2')
    })

    it('空模块树返回空数组', async () => {
      setupMocks({ moduleTree: [] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.dirTree.value).toEqual([])
    })
  })

  describe('relatedPlanName', () => {
    it('无 relatedPlanId 时返回 -', async () => {
      setupMocks({ bug: makeBug({ relatedPlanId: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.relatedPlanName.value).toBe('-')
    })

    it('有 relatedPlanId 但 planOptions 中找不到时返回 id', async () => {
      setupMocks({ bug: makeBug({ relatedPlanId: 'plan-x' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.relatedPlanName.value).toBe('plan-x')
    })

    it('有 relatedPlanId 且 planOptions 中找到时返回 name', async () => {
      setupMocks({
        bug: makeBug({ relatedPlanId: 'plan-1' }),
        plans: [{ id: 'plan-1', name: '计划A' } as TestPlanListItem],
      })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.relatedPlanName.value).toBe('计划A')
    })
  })

  describe('handleCaseSelected', () => {
    it('有选中用例时设置 relatedCaseId', () => {
      const s = init()
      s.handleCaseSelected([{ documentId: 'doc-1', caseIds: ['case-2'] }])
      expect(s.form.relatedCaseId).toBe('case-2')
    })

    it('空数组时 relatedCaseId 不变', () => {
      const s = init()
      s.form.relatedCaseId = 'case-old'
      s.handleCaseSelected([])
      expect(s.form.relatedCaseId).toBe('case-old')
    })

    it('caseIds 为空数组时 relatedCaseId 不变', () => {
      const s = init()
      s.form.relatedCaseId = 'case-old'
      s.handleCaseSelected([{ documentId: 'doc-1', caseIds: [] }])
      expect(s.form.relatedCaseId).toBe('case-old')
    })
  })

  describe('loadCaseDetail', () => {
    it('currentRelatedCaseId 为空时直接返回', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(mocks.getCaseDetail).not.toHaveBeenCalled()
    })

    it('成功加载用例详情', async () => {
      const caseNode = makeCase('case-1', '用例1', 'case')
      mocks.getCaseDetail.mockResolvedValue(caseNode)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDetail.value).toEqual(caseNode)
      expect(s.caseDetailLoading.value).toBe(false)
    })

    it('加载失败时 caseDetail 为 null', async () => {
      mocks.getCaseDetail.mockRejectedValue(new Error('用例不存在'))
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDetail.value).toBeNull()
      expect(s.caseDetailLoading.value).toBe(false)
    })

    it('已加载相同 id 时不重复请求', async () => {
      const caseNode = makeCase('case-1', '用例1', 'case')
      mocks.getCaseDetail.mockResolvedValue(caseNode)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      await s.loadCaseDetail()
      expect(mocks.getCaseDetail).toHaveBeenCalledTimes(1)
    })

    it('loading 中不重复请求', async () => {
      let resolve!: (v: TestCaseNode) => void
      mocks.getCaseDetail.mockImplementation(() => new Promise<TestCaseNode>((r) => { resolve = r }))
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      const p1 = s.loadCaseDetail()
      await s.loadCaseDetail()
      resolve(makeCase('case-1', '用例1', 'case'))
      await p1
      expect(mocks.getCaseDetail).toHaveBeenCalledTimes(1)
    })
  })

  describe('currentRelatedCaseId', () => {
    it('isClosed 时使用 detail.relatedCaseId', async () => {
      setupMocks({ bug: makeBug({ status: 'closed', relatedCaseId: 'case-closed' }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.currentRelatedCaseId.value).toBe('case-closed')
    })

    it('非 isClosed 时使用 form.relatedCaseId', async () => {
      setupMocks({ bug: makeBug({ status: 'active', relatedCaseId: 'case-detail' }) })
      const s = init()
      await vi.dynamicImportSettled()
      s.form.relatedCaseId = 'case-form'
      expect(s.currentRelatedCaseId.value).toBe('case-form')
    })

    it('isClosed 且 relatedCaseId 为空时返回空字符串', async () => {
      setupMocks({ bug: makeBug({ status: 'closed', relatedCaseId: null }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.currentRelatedCaseId.value).toBe('')
    })
  })

  describe('caseDetailRows', () => {
    it('caseDetail 为空时返回空数组', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.caseDetailRows.value).toEqual([])
    })

    it('遍历 caseDetail 中 MARKED_TYPES 类型的节点生成行', async () => {
      const stepChild = makeCase('c1', '步骤1', 'step')
      const expectedChild = makeCase('c2', '预期1', 'expected')
      const preconditionChild = makeCase('c3', '前置条件', 'precondition')
      const caseChild = makeCase('c4', '用例子节点', 'case')
      const node = makeCase('root', '根', 'case', [stepChild, expectedChild, preconditionChild, caseChild])
      mocks.getCaseDetail.mockResolvedValue(node)
      mocks.typeBadge.mockImplementation((type: string) => {
        const map: Record<string, { label: string; color: string }> = {
          step: { label: '步骤', color: '#67C23A' },
          expected: { label: '预期', color: '#E6A23C' },
          precondition: { label: '前置', color: '#409EFF' },
        }
        return map[type] ?? null
      })
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDetailRows.value.length).toBe(3)
    })

    it('非 MARKED_TYPES 类型的节点不添加 badge', async () => {
      const child = makeCase('c1', '用例1', 'case')
      const node = makeCase('root', '根', 'case', [child])
      mocks.getCaseDetail.mockResolvedValue(node)
      mocks.typeBadge.mockReturnValue(null)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDetailRows.value).toHaveLength(0)
    })

    it('空 children 时返回空数组', async () => {
      const node = makeCase('root', '根', 'case', [])
      mocks.getCaseDetail.mockResolvedValue(node)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDetailRows.value).toHaveLength(0)
    })

    it('嵌套层级正确计算 depth', async () => {
      const grandchild = makeCase('gc1', '孙节点', 'step')
      const child = makeCase('c1', '子节点', 'case', [grandchild])
      const node = makeCase('root', '根', 'case', [child])
      mocks.getCaseDetail.mockResolvedValue(node)
      mocks.typeBadge.mockImplementation((type: string) => {
        if (type === 'step') return { label: '步骤', color: '#67C23A' }
        return null
      })
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDetailRows.value[0].depth).toBe(0)
    })
  })

  describe('caseDocName', () => {
    it('caseDetail 无 documentId 时返回空字符串', async () => {
      const node = makeCase('c1', '用例', 'case')
      delete node.documentId
      mocks.getCaseDetail.mockResolvedValue(node)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDocName.value).toBe('')
    })

    it('moduleTree 中找到文档名称', async () => {
      const node = makeCase('c1', '用例', 'case')
      node.documentId = 'doc-1'
      mocks.getCaseDetail.mockResolvedValue(node)
      const tree: ProjectModule[] = [makeModule('d1', '目录', [makeDocModule('doc-1', '测试文档')])]
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }), moduleTree: tree })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDocName.value).toBe('测试文档')
    })

    it('moduleTree 中找不到时返回空字符串', async () => {
      const node = makeCase('c1', '用例', 'case')
      node.documentId = 'doc-missing'
      mocks.getCaseDetail.mockResolvedValue(node)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      expect(s.caseDocName.value).toBe('')
    })
  })

  describe('openCaseDocument', () => {
    it('有 documentId 时跳转到用例文档页', async () => {
      const node = makeCase('c1', '用例', 'case')
      node.documentId = 'doc-1'
      mocks.getCaseDetail.mockResolvedValue(node)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      s.openCaseDocument()
      expect(routerPush).toHaveBeenCalledWith({
        path: '/workspace/projects/functional-testing',
        query: { tab: 'cases', documentId: 'doc-1' },
      })
    })

    it('无 documentId 时不跳转', async () => {
      const node = makeCase('c1', '用例', 'case')
      delete node.documentId
      mocks.getCaseDetail.mockResolvedValue(node)
      setupMocks({ bug: makeBug({ relatedCaseId: 'case-1' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.loadCaseDetail()
      s.openCaseDocument()
      expect(routerPush).not.toHaveBeenCalled()
    })
  })

  describe('loadModuleTree / loadPlanOptions / loadAttachments 失败不阻塞', () => {
    it('moduleTree 加载失败不阻塞', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.dirTree.value).toEqual([])
    })

    it('planOptions 加载失败不阻塞', async () => {
      mocks.fetchPlans.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.planOptions.value).toEqual([])
    })

    it('attachments 加载失败不阻塞', async () => {
      mocks.fetchBugAttachments.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.attachments.value).toEqual([])
    })
  })

  describe('附件操作', () => {
    const makeAttachment = (overrides?: Partial<BugAttachment>): BugAttachment => ({
      id: 'att-1',
      fileName: 'test.png',
      fileSize: 1024,
      contentType: 'image/png',
      uploaderId: 'u1',
      uploaderName: '张三',
      createdAt: '2025-01-01T00:00:00',
      ...overrides,
    })

    describe('handleAttachmentUpload', () => {
      it('成功上传后刷新附件列表', async () => {
        mocks.uploadBugAttachment.mockResolvedValue(makeAttachment())
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        const file = new File(['a'], 'test.png', { type: 'image/png' })
        await s.handleAttachmentUpload({ file } as never)
        expect(mocks.uploadBugAttachment).toHaveBeenCalledWith('bug-1', file)
        expect(mocks.ElMessage.success).toHaveBeenCalledWith('附件已上传')
      })

      it('文件超过 10MB 时显示警告', async () => {
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        const bigFile = new File([new ArrayBuffer(11 * 1024 * 1024)], 'big.png', { type: 'image/png' })
        Object.defineProperty(bigFile, 'size', { value: 11 * 1024 * 1024 })
        await s.handleAttachmentUpload({ file: bigFile } as never)
        expect(mocks.ElMessage.warning).toHaveBeenCalledWith('「big.png」超过 10MB，无法上传')
        expect(mocks.uploadBugAttachment).not.toHaveBeenCalled()
      })

      it('上传失败时显示错误', async () => {
        mocks.uploadBugAttachment.mockRejectedValue(new Error('上传失败'))
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        const file = new File(['a'], 'test.png', { type: 'image/png' })
        await s.handleAttachmentUpload({ file } as never)
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('上传失败')
        expect(s.uploading.value).toBe(false)
      })

      it('上传失败非 Error 异常显示通用消息', async () => {
        mocks.uploadBugAttachment.mockRejectedValue(42)
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        const file = new File(['a'], 'test.png', { type: 'image/png' })
        await s.handleAttachmentUpload({ file } as never)
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('附件上传失败')
      })
    })

    describe('handleAttachmentDownload', () => {
      it('成功下载', async () => {
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDownload(makeAttachment())
        expect(mocks.downloadBugAttachment).toHaveBeenCalledWith('att-1', 'test.png')
      })

      it('下载失败时显示错误', async () => {
        mocks.downloadBugAttachment.mockRejectedValue(new Error('下载失败'))
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDownload(makeAttachment())
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('下载失败')
      })

      it('下载失败非 Error 异常显示通用消息', async () => {
        mocks.downloadBugAttachment.mockRejectedValue(42)
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDownload(makeAttachment())
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('附件下载失败')
      })
    })

    describe('handleAttachmentDelete', () => {
      it('用户确认后删除并刷新列表', async () => {
        mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
        mocks.deleteBugAttachment.mockResolvedValue()
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDownload(makeAttachment())
        await s.handleAttachmentDelete(makeAttachment())
        expect(mocks.deleteBugAttachment).toHaveBeenCalledWith('att-1')
        expect(mocks.ElMessage.success).toHaveBeenCalledWith('附件已删除')
      })

      it('用户取消时不删除', async () => {
        mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDelete(makeAttachment())
        expect(mocks.deleteBugAttachment).not.toHaveBeenCalled()
      })

      it('删除失败时显示错误', async () => {
        mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
        mocks.deleteBugAttachment.mockRejectedValue(new Error('删除失败'))
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDelete(makeAttachment())
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
      })

      it('删除失败非 Error 异常显示通用消息', async () => {
        mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
        mocks.deleteBugAttachment.mockRejectedValue(42)
        setupMocks()
        const s = init()
        await vi.dynamicImportSettled()
        await s.handleAttachmentDelete(makeAttachment())
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('附件删除失败')
      })
    })
  })
})
