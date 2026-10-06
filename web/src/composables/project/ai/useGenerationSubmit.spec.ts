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

import { useGenerationSubmit } from './useGenerationSubmit'
import type { AiGenerationConfig } from '@/types'

const DEFAULT_CONFIG: AiGenerationConfig = {
  placement: 'new_top_level',
  granularity: 'standard',
}

function codedError(code: number, message: string): Error {
  return Object.assign(new Error(message), { code })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  mocks.submitAiTask.mockResolvedValue({ taskId: 't1' })
})

describe('useGenerationSubmit', () => {
  it('提交成功跳转任务详情页', async () => {
    const { submitGeneration, submitting } = useGenerationSubmit()

    const result = await submitGeneration(['r1', 'r2'], DEFAULT_CONFIG)

    expect(result).toBe('submitted')
    expect(mocks.submitAiTask).toHaveBeenCalledWith('test_design_generation', {
      requirementIds: ['r1', 'r2'],
      placement: 'new_top_level',
      granularity: 'standard',
    })
    expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks/t1')
    expect(mocks.ElMessage.success).toHaveBeenCalled()
    expect(submitting.value).toBe(false)
  })

  it('指定模块落位携带 targetModuleId', async () => {
    const { submitGeneration } = useGenerationSubmit()

    await submitGeneration(['r1'], {
      placement: 'attach',
      targetModuleId: 'm1',
      granularity: 'detailed',
    })

    expect(mocks.submitAiTask).toHaveBeenCalledWith('test_design_generation', {
      requirementIds: ['r1'],
      placement: 'attach',
      granularity: 'detailed',
      targetModuleId: 'm1',
    })
  })

  it('重复任务（1000018209）引导前往任务中心', async () => {
    mocks.submitAiTask.mockRejectedValue(codedError(1000018209, '已存在进行中的同输入生成任务'))
    const { submitGeneration } = useGenerationSubmit()

    const result = await submitGeneration(['r1'], DEFAULT_CONFIG)

    expect(result).toBe('failed')
    expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
    expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks')
  })

  it('重复任务留在本页时不跳转任务中心', async () => {
    mocks.submitAiTask.mockRejectedValue(codedError(1000018209, 'dup'))
    mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
    const { submitGeneration } = useGenerationSubmit()

    const result = await submitGeneration(['r1'], DEFAULT_CONFIG)

    expect(result).toBe('failed')
    expect(mocks.router.push).not.toHaveBeenCalled()
  })

  it('输入状态变化（1000018202）回传 stale 供刷新范围', async () => {
    mocks.submitAiTask.mockRejectedValue(codedError(1000018202, '存在非已确认需求'))
    const { submitGeneration } = useGenerationSubmit()

    const result = await submitGeneration(['r1'], DEFAULT_CONFIG)

    expect(result).toBe('stale')
    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('存在非已确认需求')
  })

  it('其余错误统一提示并返回 failed', async () => {
    mocks.submitAiTask.mockRejectedValue(new Error('网络异常'))
    const { submitGeneration } = useGenerationSubmit()

    const result = await submitGeneration(['r1'], DEFAULT_CONFIG)

    expect(result).toBe('failed')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
    expect(mocks.router.push).not.toHaveBeenCalled()
  })
})
