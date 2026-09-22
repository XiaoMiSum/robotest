import { describe, it, expect, vi, beforeEach } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchProjectModuleTree: vi.fn(),
  fetchPlans: vi.fn(),
  fetchMembers: vi.fn(),
  createBug: vi.fn(),
  getBugDetail: vi.fn(),
  uploadBugAttachment: vi.fn(),
  changeBugStatus: vi.fn(),
  useAiStore: vi.fn(),
  useRoute: vi.fn(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  fetchPlans: mocks.fetchPlans,
  fetchMembers: mocks.fetchMembers,
  createBug: mocks.createBug,
  getBugDetail: mocks.getBugDetail,
  uploadBugAttachment: mocks.uploadBugAttachment,
  changeBugStatus: mocks.changeBugStatus,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('@/stores/ai', () => ({
  useAiStore: mocks.useAiStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('vue-router', () => ({
  useRoute: mocks.useRoute,
  useRouter: mocks.useRouter,
}))

vi.mock('@/components/project/BugAiSuggest.vue', () => ({
  default: { name: 'BugAiSuggest' },
}))

vi.mock('@/utils/bugStatus', () => ({
  BUG_STATUS_LABEL: { active: '激活', resolved: '已修复', rejected: '已拒绝', closed: '已关闭' },
  BUG_STATUS_TAG_TYPE: { active: 'primary', resolved: 'success', rejected: 'warning', closed: 'info' },
  BUG_TYPE_LABEL: { code_error: '代码缺陷', function_error: '功能缺陷', performance: '性能问题', security: '安全问题' },
}))

import { useBugCreate } from './useBugCreate'

function setupDefaultMocks() {
  const push = vi.fn()
  mocks.useRoute.mockReturnValue({ query: {} })
  mocks.useRouter.mockReturnValue({ push })
  mocks.useAiStore.mockReturnValue({ aiEnabled: true })
  mocks.fetchProjectModuleTree.mockResolvedValue([])
  mocks.fetchPlans.mockResolvedValue({ list: [] })
  mocks.fetchMembers.mockResolvedValue({ list: [] })
  mocks.createBug.mockResolvedValue('new-bug-id')
  mocks.getBugDetail.mockResolvedValue(null)
  mocks.uploadBugAttachment.mockResolvedValue({})
  mocks.changeBugStatus.mockResolvedValue(undefined)
  return { push }
}

describe('useBugCreate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupDefaultMocks()
  })

  describe('initial state', () => {
    it('form has correct defaults', () => {
      const { form } = useBugCreate()
      expect(form.title).toBe('')
      expect(form.bugType).toBe('code_error')
      expect(form.moduleId).toBe('')
      expect(form.severity).toBe('general')
      expect(form.priority).toBe('medium')
      expect(form.dueDate).toBe('')
      expect(form.keywords).toBe('')
      expect(form.reproSteps).toBe('')
      expect(form.assigneeId).toBe('')
      expect(form.relatedCaseId).toBe('')
      expect(form.relatedPlanId).toBe('')
    })

    it('submitting is false', () => {
      const { submitting } = useBugCreate()
      expect(submitting.value).toBe(false)
    })

    it('dedupItems is empty', () => {
      const { dedupItems } = useBugCreate()
      expect(dedupItems.value).toEqual([])
    })

    it('dedupConfirmVisible is false', () => {
      const { dedupConfirmVisible } = useBugCreate()
      expect(dedupConfirmVisible.value).toBe(false)
    })

    it('dedupSubmitting is false', () => {
      const { dedupSubmitting } = useBugCreate()
      expect(dedupSubmitting.value).toBe(false)
    })

    it('dedupTargetId is empty string', () => {
      const { dedupTargetId } = useBugCreate()
      expect(dedupTargetId.value).toBe('')
    })

    it('moduleTree is empty initially', () => {
      const { moduleTree } = useBugCreate()
      expect(moduleTree.value).toEqual([])
    })

    it('caseSelectorVisible is false', () => {
      const { caseSelectorVisible } = useBugCreate()
      expect(caseSelectorVisible.value).toBe(false)
    })

    it('selectedCaseTitle is empty', () => {
      const { selectedCaseTitle } = useBugCreate()
      expect(selectedCaseTitle.value).toBe('')
    })

    it('planOptions is empty', () => {
      const { planOptions } = useBugCreate()
      expect(planOptions.value).toEqual([])
    })

    it('memberOptions is empty', () => {
      const { memberOptions } = useBugCreate()
      expect(memberOptions.value).toEqual([])
    })

    it('attachmentFiles is empty', () => {
      const { attachmentFiles } = useBugCreate()
      expect(attachmentFiles.value).toEqual([])
    })

    it('aiEnabled from store', () => {
      const { aiEnabled } = useBugCreate()
      expect(aiEnabled).toBe(true)
    })
  })

  describe('rules', () => {
    it('contains required rules for title, bugType, severity, priority, assigneeId', () => {
      const { rules } = useBugCreate()
      expect(rules.title).toBeDefined()
      expect(rules.bugType).toBeDefined()
      expect(rules.severity).toBeDefined()
      expect(rules.priority).toBeDefined()
      expect(rules.assigneeId).toBeDefined()
    })

    it('title rule requires value', () => {
      const { rules } = useBugCreate()
      const titleRule = (rules.title as Array<{ required: boolean }>)[0]
      expect(titleRule.required).toBe(true)
    })
  })

  describe('severityLabel / priorityLabel / bugStatusLabels', () => {
    it('returns correct labels', () => {
      const { severityLabel, priorityLabel, BUG_STATUS_LABEL, BUG_STATUS_TAG_TYPE, BUG_TYPE_LABEL } = useBugCreate()
      expect(severityLabel.fatal).toBe('致命')
      expect(severityLabel.serious).toBe('严重')
      expect(severityLabel.general).toBe('一般')
      expect(severityLabel.minor).toBe('轻微')
      expect(priorityLabel.high).toBe('高')
      expect(priorityLabel.medium).toBe('中')
      expect(priorityLabel.low).toBe('低')
      expect(BUG_STATUS_LABEL.active).toBe('激活')
      expect(BUG_STATUS_TAG_TYPE.active).toBe('primary')
      expect(BUG_TYPE_LABEL.code_error).toBe('代码缺陷')
    })
  })

  describe('applyTitle / applySeverity / applyPriority', () => {
    it('applyTitle sets form.title', () => {
      const { form, applyTitle } = useBugCreate()
      applyTitle('新标题')
      expect(form.title).toBe('新标题')
    })

    it('applySeverity sets form.severity', () => {
      const { form, applySeverity } = useBugCreate()
      applySeverity('fatal')
      expect(form.severity).toBe('fatal')
    })

    it('applyPriority sets form.priority', () => {
      const { form, applyPriority } = useBugCreate()
      applyPriority('high')
      expect(form.priority).toBe('high')
    })
  })

  describe('handleCaseSelected', () => {
    it('sets relatedCaseId and selectedCaseTitle from first node', () => {
      const { form, selectedCaseTitle, handleCaseSelected } = useBugCreate()
      handleCaseSelected([{ documentId: 'doc1', caseIds: ['case1', 'case2'] }])
      expect(form.relatedCaseId).toBe('case1')
      expect(selectedCaseTitle.value).toBe('已选 1 个用例')
    })

    it('does nothing when nodes is empty', () => {
      const { form, handleCaseSelected } = useBugCreate()
      handleCaseSelected([])
      expect(form.relatedCaseId).toBe('')
    })

    it('does nothing when caseIds is empty', () => {
      const { form, handleCaseSelected } = useBugCreate()
      handleCaseSelected([{ documentId: 'doc1', caseIds: [] }])
      expect(form.relatedCaseId).toBe('')
    })
  })

  describe('handleAttachmentChange', () => {
    it('filters out files exceeding 10MB', () => {
      const { attachmentFiles, handleAttachmentChange } = useBugCreate()
      const smallFile = { name: 'small.txt', size: 1000 } as unknown as import('element-plus').UploadUserFile
      const bigFile = { name: 'big.zip', size: 11 * 1024 * 1024 } as unknown as import('element-plus').UploadUserFile
      handleAttachmentChange(smallFile, [smallFile, bigFile])
      expect(attachmentFiles.value).toHaveLength(1)
      expect(attachmentFiles.value[0].name).toBe('small.txt')
    })

    it('shows warning for oversized file', () => {
      const { handleAttachmentChange } = useBugCreate()
      const bigFile = { name: 'big.zip', size: 11 * 1024 * 1024 } as unknown as import('element-plus').UploadUserFile
      handleAttachmentChange(bigFile, [bigFile])
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('「big.zip」超过 10MB，已忽略')
    })

    it('keeps files under limit', () => {
      const { attachmentFiles, handleAttachmentChange } = useBugCreate()
      const file1 = { name: 'a.txt', size: 500 } as unknown as import('element-plus').UploadUserFile
      const file2 = { name: 'b.txt', size: 1000 } as unknown as import('element-plus').UploadUserFile
      handleAttachmentChange(file1, [file1, file2])
      expect(attachmentFiles.value).toHaveLength(2)
    })

    it('keeps files with no size property', () => {
      const { attachmentFiles, handleAttachmentChange } = useBugCreate()
      const file = { name: 'no-size.txt' } as unknown as import('element-plus').UploadUserFile
      handleAttachmentChange(file, [file])
      expect(attachmentFiles.value).toHaveLength(1)
    })
  })

  describe('handleAttachmentRemove', () => {
    it('updates attachmentFiles with remaining files', () => {
      const { attachmentFiles, handleAttachmentRemove, handleAttachmentChange } = useBugCreate()
      const f1 = { name: 'a.txt', size: 100 } as unknown as import('element-plus').UploadUserFile
      const f2 = { name: 'b.txt', size: 200 } as unknown as import('element-plus').UploadUserFile
      handleAttachmentChange(f1, [f1, f2])
      expect(attachmentFiles.value).toHaveLength(2)
      handleAttachmentRemove(f1, [f2])
      expect(attachmentFiles.value).toHaveLength(1)
      expect(attachmentFiles.value[0].name).toBe('b.txt')
    })
  })

  describe('handleSelectDuplicate', () => {
    it('sets dedupTargetId from item', () => {
      const { dedupTargetId, handleSelectDuplicate } = useBugCreate()
      handleSelectDuplicate({ bugId: 'bug-123' } as never)
      expect(dedupTargetId.value).toBe('bug-123')
    })

    it('clears dedupTargetId when null', () => {
      const { dedupTargetId, handleSelectDuplicate } = useBugCreate()
      dedupTargetId.value = 'bug-123'
      handleSelectDuplicate(null)
      expect(dedupTargetId.value).toBe('')
    })
  })

  describe('handleAbandonSubmit', () => {
    it('navigates to bugs list', () => {
      const { handleAbandonSubmit } = useBugCreate()
      handleAbandonSubmit()
      expect(mocks.useRouter().push).toHaveBeenCalledWith('/workspace/projects/bugs')
    })
  })

  describe('loadModuleTree', () => {
    it('populates moduleTree on success', async () => {
      const tree = [{ id: 'm1', name: 'Module 1' }]
      mocks.fetchProjectModuleTree.mockResolvedValue(tree)
      const { moduleTree } = useBugCreate()
      await vi.waitFor(() => {
        expect(moduleTree.value).toEqual(tree)
      })
    })

    it('ignores error on failure', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('fail'))
      const { moduleTree } = useBugCreate()
      await vi.waitFor(() => {
        expect(moduleTree.value).toEqual([])
      })
    })
  })

  describe('loadPlanOptions', () => {
    it('populates planOptions on success', async () => {
      const plans = [{ id: 'p1', name: 'Plan 1' }]
      mocks.fetchPlans.mockResolvedValue({ list: plans })
      const { planOptions } = useBugCreate()
      await vi.waitFor(() => {
        expect(planOptions.value).toEqual(plans)
      })
    })

    it('ignores error on failure', async () => {
      mocks.fetchPlans.mockRejectedValue(new Error('fail'))
      const { planOptions } = useBugCreate()
      await vi.waitFor(() => {
        expect(planOptions.value).toEqual([])
      })
    })
  })

  describe('loadMembers', () => {
    it('populates memberOptions on success', async () => {
      const members = [{ userId: 'u1', name: 'Alice' }]
      mocks.fetchMembers.mockResolvedValue({ list: members })
      const { memberOptions } = useBugCreate()
      await vi.waitFor(() => {
        expect(memberOptions.value).toEqual(members)
      })
    })

    it('ignores error on failure', async () => {
      mocks.fetchMembers.mockRejectedValue(new Error('fail'))
      const { memberOptions } = useBugCreate()
      await vi.waitFor(() => {
        expect(memberOptions.value).toEqual([])
      })
    })
  })

  describe('applyCopySource', () => {
    it('copies fields from source bug when copyFrom query exists', async () => {
      const plans = [{ id: 'plan-1' }]
      const srcBug = {
        title: 'Source Bug',
        bugType: 'function_error',
        moduleId: 'mod-1',
        severity: 'fatal',
        priority: 'high',
        dueDate: '2026-01-01',
        keywords: 'k1,k2',
        reproSteps: 'Step 1',
        relatedCaseId: 'case-1',
        relatedPlanId: 'plan-1',
      }
      mocks.fetchPlans.mockResolvedValue({ list: plans })
      mocks.getBugDetail.mockResolvedValue(srcBug)
      mocks.useRoute.mockReturnValue({ query: { copyFrom: 'src-bug-id' } })

      const { form, selectedCaseTitle } = useBugCreate()
      await vi.waitFor(() => {
        expect(form.title).toBe('Source Bug')
      })
      expect(form.bugType).toBe('function_error')
      expect(form.moduleId).toBe('mod-1')
      expect(form.severity).toBe('fatal')
      expect(form.priority).toBe('high')
      expect(form.dueDate).toBe('2026-01-01')
      expect(form.keywords).toBe('k1,k2')
      expect(form.reproSteps).toBe('Step 1')
      expect(form.relatedCaseId).toBe('case-1')
      expect(selectedCaseTitle.value).toBe('已选 1 个用例')
    })

    it('clears relatedPlanId when plan not in planOptions', async () => {
      mocks.fetchPlans.mockResolvedValue({ list: [{ id: 'other-plan' }] })
      mocks.getBugDetail.mockResolvedValue({
        title: 'X',
        bugType: 'code_error',
        severity: 'general',
        priority: 'medium',
        relatedPlanId: 'unknown-plan',
      })
      mocks.useRoute.mockReturnValue({ query: { copyFrom: 'src-id' } })

      const { form } = useBugCreate()
      await vi.waitFor(() => {
        expect(form.title).toBe('X')
      })
      expect(form.relatedPlanId).toBe('')
    })

    it('shows error message on failure', async () => {
      mocks.fetchPlans.mockResolvedValue({ list: [] })
      mocks.getBugDetail.mockRejectedValue(new Error('Not found'))
      mocks.useRoute.mockReturnValue({ query: { copyFrom: 'bad-id' } })

      useBugCreate()
      await vi.waitFor(() => {
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('Not found')
      })
    })

    it('shows fallback error when non-Error thrown', async () => {
      mocks.fetchPlans.mockResolvedValue({ list: [] })
      mocks.getBugDetail.mockRejectedValue('string error')
      mocks.useRoute.mockReturnValue({ query: { copyFrom: 'bad-id' } })

      useBugCreate()
      await vi.waitFor(() => {
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载源缺陷信息失败')
      })
    })

    it('does nothing when copyFrom is empty string', async () => {
      mocks.useRoute.mockReturnValue({ query: { copyFrom: '' } })
      useBugCreate()
      await vi.waitFor(() => {
        expect(mocks.fetchPlans).toHaveBeenCalled()
      })
      expect(mocks.getBugDetail).not.toHaveBeenCalled()
    })

    it('does nothing when copyFrom query is absent', async () => {
      mocks.useRoute.mockReturnValue({ query: {} })
      useBugCreate()
      await vi.waitFor(() => {
        expect(mocks.fetchPlans).toHaveBeenCalled()
      })
      expect(mocks.getBugDetail).not.toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('does nothing when formRef is not set', async () => {
      const { handleSubmit } = useBugCreate()
      await handleSubmit()
      expect(mocks.createBug).not.toHaveBeenCalled()
    })

    it('validates form and calls createBug when no dedup items', async () => {
      const { formRef, handleSubmit } = useBugCreate()
      const validate = vi.fn().mockResolvedValue(true)
      formRef.value = { validate } as never
      await handleSubmit()
      expect(validate).toHaveBeenCalled()
      expect(mocks.createBug).toHaveBeenCalled()
    })

    it('shows dedup confirm when dedupItems exist', async () => {
      const { formRef, dedupItems, dedupConfirmVisible, handleSubmit } = useBugCreate()
      const validate = vi.fn().mockResolvedValue(true)
      formRef.value = { validate } as never
      dedupItems.value = [{ bugId: 'dup-1' } as never]
      await handleSubmit()
      expect(dedupConfirmVisible.value).toBe(true)
      expect(mocks.createBug).not.toHaveBeenCalled()
    })

    it('does not call createBug when validation fails', async () => {
      const { formRef, handleSubmit } = useBugCreate()
      const validate = vi.fn().mockRejectedValue(false)
      formRef.value = { validate } as never
      await handleSubmit()
      expect(mocks.createBug).not.toHaveBeenCalled()
    })

    it('creates bug with trimmed form data', async () => {
      const { form, formRef, handleSubmit } = useBugCreate()
      form.title = '  Bug Title  '
      form.severity = 'fatal'
      form.priority = 'high'
      form.bugType = 'code_error'
      form.assigneeId = 'user-1'
      form.reproSteps = '  Steps  '
      form.keywords = '  kw  '
      form.moduleId = 'mod-1'
      form.dueDate = '2026-01-01'
      form.relatedCaseId = 'case-1'
      form.relatedPlanId = 'plan-1'
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never

      await handleSubmit()
      expect(mocks.createBug).toHaveBeenCalledWith({
        title: 'Bug Title',
        severity: 'fatal',
        priority: 'high',
        bugType: 'code_error',
        reproSteps: 'Steps',
        moduleId: 'mod-1',
        keywords: 'kw',
        dueDate: '2026-01-01',
        assigneeId: 'user-1',
        relatedCaseId: 'case-1',
        relatedPlanId: 'plan-1',
      })
    })

    it('sends undefined for empty optional fields', async () => {
      const { formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      const callArg = mocks.createBug.mock.calls[0][0]
      expect(callArg.reproSteps).toBeUndefined()
      expect(callArg.moduleId).toBeUndefined()
      expect(callArg.keywords).toBeUndefined()
      expect(callArg.dueDate).toBeUndefined()
      expect(callArg.relatedCaseId).toBeUndefined()
      expect(callArg.relatedPlanId).toBeUndefined()
    })

    it('uploads attachments that have raw file', async () => {
      const { attachmentFiles, formRef, handleSubmit } = useBugCreate()
      attachmentFiles.value = [
        { name: 'a.txt', raw: new File(['content'], 'a.txt') } as unknown as import('element-plus').UploadUserFile,
        { name: 'b.txt' } as unknown as import('element-plus').UploadUserFile,
      ]
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(mocks.uploadBugAttachment).toHaveBeenCalledTimes(1)
      expect(mocks.uploadBugAttachment).toHaveBeenCalledWith('new-bug-id', expect.any(File))
    })

    it('shows success message', async () => {
      const { formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已提交')
    })

    it('navigates to bugs list on success', async () => {
      const { formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(mocks.useRouter().push).toHaveBeenCalledWith('/workspace/projects/bugs')
    })

    it('shows error on createBug failure', async () => {
      mocks.createBug.mockRejectedValue(new Error('Submit failed'))
      const { formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('Submit failed')
    })

    it('shows fallback error for non-Error rejection', async () => {
      mocks.createBug.mockRejectedValue(42)
      const { formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('提交失败')
    })

    it('resets submitting after successful creation', async () => {
      const { submitting, formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(undefined) } as never
      await handleSubmit()
      expect(submitting.value).toBe(false)
    })

    it('resets submitting even on error', async () => {
      mocks.createBug.mockRejectedValue(new Error('fail'))
      const { submitting, formRef, handleSubmit } = useBugCreate()
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(submitting.value).toBe(false)
    })
  })

  describe('handleDedupAbandon', () => {
    it('closes dedup modal and navigates away', () => {
      const { dedupConfirmVisible, handleDedupAbandon } = useBugCreate()
      dedupConfirmVisible.value = true
      handleDedupAbandon()
      expect(dedupConfirmVisible.value).toBe(false)
      expect(mocks.useRouter().push).toHaveBeenCalledWith('/workspace/projects/bugs')
    })

    it('does nothing when dedupSubmitting is true', () => {
      const { dedupConfirmVisible, dedupSubmitting, handleDedupAbandon } = useBugCreate()
      dedupSubmitting.value = true
      dedupConfirmVisible.value = true
      handleDedupAbandon()
      expect(dedupConfirmVisible.value).toBe(true)
      expect(mocks.useRouter().push).not.toHaveBeenCalled()
    })
  })

  describe('handleDedupContinue', () => {
    it('calls createBug and resets dedupSubmitting', async () => {
      const { dedupSubmitting, handleDedupContinue } = useBugCreate()
      await handleDedupContinue()
      expect(mocks.createBug).toHaveBeenCalled()
      expect(dedupSubmitting.value).toBe(false)
    })

    it('does nothing when dedupSubmitting is already true', async () => {
      const { dedupSubmitting, handleDedupContinue } = useBugCreate()
      dedupSubmitting.value = true
      await handleDedupContinue()
      expect(mocks.createBug).not.toHaveBeenCalled()
    })

    it('resets dedupSubmitting on error', async () => {
      mocks.createBug.mockRejectedValue(new Error('fail'))
      const { dedupSubmitting, handleDedupContinue } = useBugCreate()
      await handleDedupContinue()
      expect(dedupSubmitting.value).toBe(false)
    })
  })

  describe('handleDedupMarkDuplicate', () => {
    it('shows warning when dedupTargetId is empty', async () => {
      const { handleDedupMarkDuplicate } = useBugCreate()
      await handleDedupMarkDuplicate()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择要标记为重复所对应的原始缺陷')
      expect(mocks.createBug).not.toHaveBeenCalled()
    })

    it('calls createBug with dedupTargetId and marks duplicate', async () => {
      const { dedupTargetId, handleDedupMarkDuplicate } = useBugCreate()
      dedupTargetId.value = 'target-bug'
      await handleDedupMarkDuplicate()
      expect(mocks.createBug).toHaveBeenCalled()
      expect(mocks.changeBugStatus).toHaveBeenCalledWith('new-bug-id', {
        status: 'resolved',
        resolution: 'duplicate',
        duplicateOfBugId: 'target-bug',
        comment: '创建时标记为重复缺陷',
      })
    })

    it('shows success message for duplicate', async () => {
      const { dedupTargetId, handleDedupMarkDuplicate } = useBugCreate()
      dedupTargetId.value = 'target-bug'
      await handleDedupMarkDuplicate()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('缺陷已提交并标记为重复')
    })

    it('does nothing when dedupSubmitting is already true', async () => {
      const { dedupSubmitting, dedupTargetId, handleDedupMarkDuplicate } = useBugCreate()
      dedupSubmitting.value = true
      dedupTargetId.value = 'target-bug'
      await handleDedupMarkDuplicate()
      expect(mocks.createBug).not.toHaveBeenCalled()
    })

    it('resets dedupSubmitting on error', async () => {
      mocks.createBug.mockRejectedValue(new Error('fail'))
      const { dedupSubmitting, dedupTargetId, handleDedupMarkDuplicate } = useBugCreate()
      dedupTargetId.value = 'target-bug'
      await handleDedupMarkDuplicate()
      expect(dedupSubmitting.value).toBe(false)
    })
  })
})
