import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { ApiSceneStepItem } from '@/types'

const mocks = vi.hoisted(() => ({
  debugStep: vi.fn(),
  ElMessage: { error: vi.fn(), info: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/project/api-testing/scene', () => ({
  debugStep: mocks.debugStep,
}))

import { useSceneDebug } from './useSceneDebug'

function makeStep(overrides?: Partial<ApiSceneStepItem>): ApiSceneStepItem {
  return {
    id: 'step-1',
    name: '步骤1',
    stepType: 'http',
    sortOrder: 0,
    enabled: true,
    sourceType: 'interface',
    sourceId: 'if-1',
    requestConfig: { method: 'GET', url: '/test' },
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useSceneDebug', () => {
  describe('初始状态', () => {
    it('debugResult 初始为 null', () => {
      const { debugResult } = useSceneDebug(vi.fn())
      expect(debugResult.value).toBeNull()
    })

    it('showDebugResult 初始为 false', () => {
      const { showDebugResult } = useSceneDebug(vi.fn())
      expect(showDebugResult.value).toBe(false)
    })

    it('debugStepId 初始为 null', () => {
      const { debugStepId } = useSceneDebug(vi.fn())
      expect(debugStepId.value).toBeNull()
    })
  })

  describe('handleDebugStep', () => {
    it('sceneId 不存在时直接返回，不调用 debugStep', async () => {
      const { handleDebugStep } = useSceneDebug(() => undefined)
      await handleDebugStep(makeStep())
      expect(mocks.debugStep).not.toHaveBeenCalled()
    })

    it('sceneId 返回空字符串时直接返回', async () => {
      const { handleDebugStep } = useSceneDebug(() => '')
      await handleDebugStep(makeStep())
      expect(mocks.debugStep).not.toHaveBeenCalled()
    })

    it('成功时设置 debugResult 并显示结果', async () => {
      const resp = { stepResult: { stepId: 'step-1', status: 'passed', durationMs: 100, request: {}, response: {}, validatorResults: [], extractedVariables: {} } }
      mocks.debugStep.mockResolvedValue(resp)
      const { handleDebugStep, debugResult, showDebugResult } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(mocks.debugStep).toHaveBeenCalledWith('scene-1', 'step-1')
      expect(debugResult.value).toStrictEqual(resp)
      expect(showDebugResult.value).toBe(true)
    })

    it('设置 debugStepId 为步骤 id', async () => {
      mocks.debugStep.mockResolvedValue({ stepResult: {} })
      const { handleDebugStep, debugStepId } = useSceneDebug(() => 'scene-1')
      const p = handleDebugStep(makeStep({ id: 'step-42' }))
      expect(debugStepId.value).toBe('step-42')
      await p
    })

    it('完成后 debugStepId 恢复为 null', async () => {
      mocks.debugStep.mockResolvedValue({ stepResult: {} })
      const { handleDebugStep, debugStepId } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(debugStepId.value).toBeNull()
    })

    it('请求失败时显示错误消息', async () => {
      mocks.debugStep.mockRejectedValue(new Error('调试失败'))
      const { handleDebugStep } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('调试失败')
    })

    it('请求失败且非 Error 实例时显示通用错误', async () => {
      mocks.debugStep.mockRejectedValue('string error')
      const { handleDebugStep } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('调试失败')
    })

    it('失败后 debugStepId 恢复为 null', async () => {
      mocks.debugStep.mockRejectedValue(new Error('fail'))
      const { handleDebugStep, debugStepId } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(debugStepId.value).toBeNull()
    })

    it('失败后 debugResult 保持 null', async () => {
      mocks.debugStep.mockRejectedValue(new Error('fail'))
      const { handleDebugStep, debugResult } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(debugResult.value).toBeNull()
    })

    it('失败后 showDebugResult 保持 false', async () => {
      mocks.debugStep.mockRejectedValue(new Error('fail'))
      const { handleDebugStep, showDebugResult } = useSceneDebug(() => 'scene-1')
      await handleDebugStep(makeStep())
      expect(showDebugResult.value).toBe(false)
    })
  })

  describe('handleDraftDebugDisabled', () => {
    it('显示提示消息', () => {
      const { handleDraftDebugDisabled } = useSceneDebug(vi.fn())
      handleDraftDebugDisabled(makeStep())
      expect(mocks.ElMessage.info).toHaveBeenCalledWith('创建成功后可在编辑页单步调试')
    })
  })
})
