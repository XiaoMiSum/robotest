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

import { useBugDuplicateScan } from './useBugDuplicateScan'

describe('composables/project/bug/useBugDuplicateScan', () => {
  let push: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    push = vi.fn()
    mocks.useRouter.mockReturnValue({ push })
  })

  it('存量重复扫描提交 scope=active 并跳转详情', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 't-scan' })
    const onSubmitted = vi.fn()
    const actions = useBugDuplicateScan(onSubmitted)

    await actions.submitScan()
    expect(mocks.submitAiTask).toHaveBeenCalledWith('bug_duplicate_scan', { scope: 'active' })
    expect(onSubmitted).toHaveBeenCalledOnce()
    expect(push).toHaveBeenCalledWith({
      path: '/workspace/projects/ai/tasks/t-scan',
      query: { review: '1' },
    })
    expect(actions.scanBusy.value).toBe(false)
  })

  it('提交即启动：返回 running 也直接跳转详情审核区（不依赖旧终态）', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 't-run', status: 'running' })
    const actions = useBugDuplicateScan()

    await actions.submitScan()
    expect(push).toHaveBeenCalledWith({
      path: '/workspace/projects/ai/tasks/t-run',
      query: { review: '1' },
    })
  })

  it('提交失败提示错误且复位 busy', async () => {
    mocks.submitAiTask.mockRejectedValue(new Error('网络错误'))
    const actions = useBugDuplicateScan()

    await actions.submitScan()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
    expect(actions.scanBusy.value).toBe(false)
  })
})
