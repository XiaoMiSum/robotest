import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({
  router: { push: vi.fn() },
  submitAiTask: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useSelectionSubmit } from './useSelectionSubmit'

function codedError(code: number, message: string): Error {
  return Object.assign(new Error(message), { code })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  mocks.submitAiTask.mockResolvedValue({ taskId: 't1' })
})

describe('useSelectionSubmit', () => {
  it('评审圈选提交成功跳转任务详情页且不带轮次', async () => {
    const { submitSelection, submitting } = useSelectionSubmit()

    const outcome = await submitSelection('review_selection', { requirementIds: ['r1', 'r2'] })

    expect(outcome).toBe('submitted')
    expect(mocks.submitAiTask).toHaveBeenCalledWith('review_selection', {
      requirementIds: ['r1', 'r2'],
    })
    expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks/t1')
    expect(mocks.ElMessage.success).toHaveBeenCalled()
    expect(submitting.value).toBe(false)
  })

  it('计划圈选携带轮次，缺省回落 1', async () => {
    const { submitSelection } = useSelectionSubmit()

    await submitSelection('plan_selection', { requirementIds: ['r1'], roundCount: 3 })
    expect(mocks.submitAiTask).toHaveBeenCalledWith('plan_selection', {
      requirementIds: ['r1'],
      roundCount: 3,
    })

    await submitSelection('plan_selection', { requirementIds: ['r1'] })
    expect(mocks.submitAiTask).toHaveBeenLastCalledWith('plan_selection', {
      requirementIds: ['r1'],
      roundCount: 1,
    })
  })

  it('重复任务（1000018209）引导前往任务中心', async () => {
    mocks.submitAiTask.mockRejectedValue(codedError(1000018209, '已存在进行中的同输入圈选任务'))
    const { submitSelection } = useSelectionSubmit()

    const outcome = await submitSelection('review_selection', { requirementIds: ['r1'] })

    expect(outcome).toBe('failed')
    expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
    expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks')
  })

  it('重复任务留在本页时不跳转任务中心', async () => {
    mocks.submitAiTask.mockRejectedValue(codedError(1000018209, 'dup'))
    mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
    const { submitSelection } = useSelectionSubmit()

    const outcome = await submitSelection('review_selection', { requirementIds: ['r1'] })

    expect(outcome).toBe('failed')
    expect(mocks.router.push).not.toHaveBeenCalled()
  })

  it('输入非法（1000018206）警示范围变化且不跳转', async () => {
    mocks.submitAiTask.mockRejectedValue(codedError(1000018206, '圈选建议输入非法'))
    const { submitSelection } = useSelectionSubmit()

    const outcome = await submitSelection('review_selection', { requirementIds: ['r1'] })

    expect(outcome).toBe('failed')
    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('圈选建议输入非法')
    expect(mocks.router.push).not.toHaveBeenCalled()
  })

  it('其余错误统一提示并返回 failed', async () => {
    mocks.submitAiTask.mockRejectedValue(new Error('网络异常'))
    const { submitSelection } = useSelectionSubmit()

    const outcome = await submitSelection('plan_selection', {
      requirementIds: ['r1'],
      roundCount: 2,
    })

    expect(outcome).toBe('failed')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
    expect(mocks.router.push).not.toHaveBeenCalled()
  })
})
