import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  submitAiTask: vi.fn(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('vue-router', () => ({
  useRouter: mocks.useRouter,
}))

import { useBugBatchActions } from './useBugBatchActions'

describe('composables/project/bug/useBugBatchActions', () => {
  let push: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    push = vi.fn()
    mocks.useRouter.mockReturnValue({ push })
  })

  it('打开批量分类对话框默认选中激活缺陷', () => {
    const actions = useBugBatchActions()
    actions.openClassify()
    expect(actions.classifyVisible.value).toBe(true)
    expect(actions.classifyStatuses.value).toEqual(['active'])
  })

  it('未选状态时摘要提示且拒绝提交', async () => {
    const actions = useBugBatchActions()
    actions.classifyStatuses.value = []
    expect(actions.classifySummary.value).toContain('至少选择')
    await actions.submitClassify()
    expect(mocks.submitAiTask).not.toHaveBeenCalled()
  })

  it('批量分类提交 filter.statuses 并跳转任务详情定位审核区', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 't-cls' })
    const onSubmitted = vi.fn()
    const actions = useBugBatchActions(onSubmitted)
    actions.classifyStatuses.value = ['active', 'resolved']

    await actions.submitClassify()
    expect(mocks.submitAiTask).toHaveBeenCalledWith('bug_classify', {
      filter: { statuses: ['active', 'resolved'] },
    })
    expect(onSubmitted).toHaveBeenCalledOnce()
    expect(push).toHaveBeenCalledWith({
      path: '/workspace/projects/ai/tasks/t-cls',
      query: { review: '1' },
    })
    expect(actions.classifyVisible.value).toBe(false)
  })

  it('批量分类失败：提示错误且对话框保持打开', async () => {
    mocks.submitAiTask.mockRejectedValue(new Error('输入为空'))
    const actions = useBugBatchActions()
    actions.openClassify()
    actions.classifyStatuses.value = ['active']

    await actions.submitClassify()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('输入为空')
    expect(actions.classifyVisible.value).toBe(true)
  })

  it('存量重复扫描提交 scope=active 并跳转详情', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 't-scan' })
    const actions = useBugBatchActions()

    await actions.submitScan()
    expect(mocks.submitAiTask).toHaveBeenCalledWith('bug_duplicate_scan', { scope: 'active' })
    expect(push).toHaveBeenCalledWith({
      path: '/workspace/projects/ai/tasks/t-scan',
      query: { review: '1' },
    })
    expect(actions.scanBusy.value).toBe(false)
  })

  it('状态选项覆盖四态', () => {
    const actions = useBugBatchActions()
    expect(actions.STATUS_OPTIONS).toEqual(['active', 'resolved', 'rejected', 'closed'])
  })
})
