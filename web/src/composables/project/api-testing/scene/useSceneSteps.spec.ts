import { ref, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiSceneDetail, ApiSceneStepItem } from '@/types'

const mocks = vi.hoisted(() => ({
  deleteSceneStep: vi.fn<() => Promise<void>>(),
  reorderSceneSteps: vi.fn<() => Promise<void>>(),
  updateSceneStep: vi.fn<() => Promise<void>>(),
  copySceneStep: vi.fn<() => Promise<void>>(),
  sortedSteps: vi.fn<(steps: ApiSceneStepItem[]) => ApiSceneStepItem[]>(),
  emptyStepDraft: vi.fn<() => { name: string; stepType: string; requestConfig: Record<string, unknown> }>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/project/scene', () => ({
  deleteSceneStep: mocks.deleteSceneStep,
  reorderSceneSteps: mocks.reorderSceneSteps,
  updateSceneStep: mocks.updateSceneStep,
  copySceneStep: mocks.copySceneStep,
}))

vi.mock('@/pages/project/scenesModel', () => ({
  sortedSteps: mocks.sortedSteps,
  emptyStepDraft: mocks.emptyStepDraft,
}))

import { useSceneSteps, type UseSceneStepsOptions } from './useSceneSteps'

function makeStep(id: string, overrides?: Partial<ApiSceneStepItem>): ApiSceneStepItem {
  return {
    id,
    name: `步骤 ${id}`,
    stepType: 'http',
    sortOrder: 1,
    enabled: true,
    sourceType: 'manual',
    sourceId: null,
    requestConfig: { method: 'GET', url: '/api/test', headers: [], params: [], body: { type: 'none', content: null } },
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
    ...overrides,
  }
}

function makeDetail(steps: ApiSceneStepItem[] = []): ApiSceneDetail {
  return {
    id: 'scene-1',
    name: '测试场景',
    variables: [],
    processors: [],
    changeVersion: 1,
    steps,
  }
}

function createOptions(overrides?: Partial<UseSceneStepsOptions>) {
  const detail = ref<ApiSceneDetail | null>(makeDetail())
  const bumpAutosave = vi.fn()
  return {
    detail,
    bumpAutosave,
    ...overrides,
  }
}

describe('useSceneSteps', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.sortedSteps.mockImplementation((steps) => [...steps].sort((a, b) => a.sortOrder - b.sortOrder))
    mocks.emptyStepDraft.mockReturnValue({
      name: '',
      stepType: 'http',
      requestConfig: { method: 'GET', url: '', headers: [], params: [], body: { type: 'none', content: null } },
    })
    mocks.deleteSceneStep.mockResolvedValue(undefined)
    mocks.reorderSceneSteps.mockResolvedValue(undefined)
    mocks.updateSceneStep.mockResolvedValue(undefined)
    mocks.copySceneStep.mockResolvedValue(undefined)
  })

  describe('初始状态', () => {
    it('showInterfacePicker 初始为 false', () => {
      const { showInterfacePicker } = useSceneSteps(createOptions())
      expect(showInterfacePicker.value).toBe(false)
    })

    it('selectedStep 初始为 null', () => {
      const { selectedStep } = useSceneSteps(createOptions())
      expect(selectedStep.value).toBeNull()
    })

    it('draftSteps 初始为空数组', () => {
      const { draftSteps } = useSceneSteps(createOptions())
      expect(draftSteps.value).toEqual([])
    })

    it('sorted 调用 sortedSteps', () => {
      const step1 = makeStep('s1', { sortOrder: 2 })
      const step2 = makeStep('s2', { sortOrder: 1 })
      const opts = createOptions({ detail: ref(makeDetail([step1, step2])) })
      const { sorted } = useSceneSteps(opts)
      const result = sorted.value
      expect(mocks.sortedSteps).toHaveBeenCalledWith([step1, step2])
      expect(result).toEqual([step2, step1])
    })

    it('detail 为 null 时 sorted 返回空数组', () => {
      const opts = createOptions({ detail: ref(null) })
      const { sorted } = useSceneSteps(opts)
      expect(sorted.value).toEqual([])
    })
  })

  describe('createDefaultStep', () => {
    it('生成带临时 id 的步骤', () => {
      const { createDefaultStep } = useSceneSteps(createOptions())
      const step = createDefaultStep()
      expect(step.id).toMatch(/^new-/)
      expect(step.name).toBe('')
      expect(step.enabled).toBe(true)
      expect(step.sourceType).toBe('manual')
      expect(step.sourceId).toBeNull()
    })

    it('使用 emptyStepDraft 的 stepType 和 requestConfig', () => {
      mocks.emptyStepDraft.mockReturnValue({
        name: '',
        stepType: 'jdbc',
        requestConfig: { method: 'POST', url: '', headers: [], params: [], body: { type: 'none', content: null } },
      })
      const { createDefaultStep } = useSceneSteps(createOptions())
      const step = createDefaultStep()
      expect(step.stepType).toBe('jdbc')
      expect(step.requestConfig).toEqual(mocks.emptyStepDraft().requestConfig)
    })

    it('sortOrder 初始为 0', () => {
      const { createDefaultStep } = useSceneSteps(createOptions())
      expect(createDefaultStep().sortOrder).toBe(0)
    })

    it('返回的数组字段为新引用', () => {
      const { createDefaultStep } = useSceneSteps(createOptions())
      const step = createDefaultStep()
      expect(step.variables).toEqual([])
      expect(step.processors).toEqual([])
      expect(step.validators).toEqual([])
      expect(step.extractors).toEqual([])
    })
  })

  describe('handleAddStep', () => {
    it('向 detail.steps 追加新步骤', () => {
      const detail = ref(makeDetail([makeStep('s1')]))
      const { handleAddStep } = useSceneSteps(createOptions({ detail }))
      handleAddStep()
      expect(detail.value!.steps).toHaveLength(2)
      expect(detail.value!.steps[1].id).toMatch(/^new-/)
    })

    it('新步骤的 sortOrder 等于已有步骤数 +1', () => {
      const detail = ref(makeDetail([makeStep('s1'), makeStep('s2')]))
      const { handleAddStep } = useSceneSteps(createOptions({ detail }))
      handleAddStep()
      expect(detail.value!.steps[2].sortOrder).toBe(3)
    })

    it('detail.steps 为空时 sortOrder 为 1', () => {
      const detail = ref(makeDetail([]))
      const { handleAddStep } = useSceneSteps(createOptions({ detail }))
      handleAddStep()
      expect(detail.value!.steps[0].sortOrder).toBe(1)
    })

    it('新步骤自动被选中', () => {
      const detail = ref(makeDetail())
      const { handleAddStep, selectedStep } = useSceneSteps(createOptions({ detail }))
      handleAddStep()
      expect(selectedStep.value).toEqual(detail.value!.steps[0])
    })
  })

  describe('handleQuickAddStep', () => {
    it('打开接口选择器', () => {
      const { handleQuickAddStep, showInterfacePicker } = useSceneSteps(createOptions())
      handleQuickAddStep()
      expect(showInterfacePicker.value).toBe(true)
    })
  })

  describe('handleInterfaceSelected', () => {
    it('编辑态将步骤追加到 detail.steps', () => {
      const detail = ref(makeDetail([makeStep('s1')]))
      const step = makeStep('new-step')
      const { handleInterfaceSelected } = useSceneSteps(createOptions({ detail }))
      handleInterfaceSelected(step, false)
      expect(detail.value!.steps).toHaveLength(2)
      expect(detail.value!.steps[1]).toEqual(step)
    })

    it('创建态将步骤追加到 draftSteps', () => {
      const { handleInterfaceSelected, draftSteps } = useSceneSteps(createOptions())
      const step = makeStep('new-step')
      handleInterfaceSelected(step, true)
      expect(draftSteps.value).toHaveLength(1)
      expect(draftSteps.value[0]).toEqual(step)
    })

    it('设置步骤的 sortOrder', () => {
      const detail = ref(makeDetail([makeStep('s1')]))
      const step = makeStep('new-step')
      const { handleInterfaceSelected } = useSceneSteps(createOptions({ detail }))
      handleInterfaceSelected(step, false)
      expect(step.sortOrder).toBe(2)
    })

    it('创建态 draftSteps 为空时 sortOrder 为 1', () => {
      const step = makeStep('new-step')
      const { handleInterfaceSelected } = useSceneSteps(createOptions())
      handleInterfaceSelected(step, true)
      expect(step.sortOrder).toBe(1)
    })

    it('自动选中新步骤', () => {
      const step = makeStep('new-step')
      const { handleInterfaceSelected, selectedStep } = useSceneSteps(createOptions())
      handleInterfaceSelected(step, false)
      expect(selectedStep.value).toEqual(step)
    })

    it('关闭接口选择器', () => {
      const step = makeStep('new-step')
      const { handleInterfaceSelected, showInterfacePicker } = useSceneSteps(createOptions())
      showInterfacePicker.value = true
      handleInterfaceSelected(step, false)
      expect(showInterfacePicker.value).toBe(false)
    })

    it('调用 bumpAutosave', () => {
      const opts = createOptions()
      const step = makeStep('new-step')
      const { handleInterfaceSelected } = useSceneSteps(opts)
      handleInterfaceSelected(step, false)
      expect(opts.bumpAutosave).toHaveBeenCalled()
    })
  })

  describe('handleSelectStep', () => {
    it('直接选中步骤（无冲突）', () => {
      const step1 = makeStep('s1')
      const step2 = makeStep('s2')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step1
      handleSelectStep(step2)
      expect(selectedStep.value).toEqual(step2)
    })

    it('当前步骤完整时允许切换', () => {
      const step1 = makeStep('s1', { name: '完整步骤', requestConfig: { url: '/api' } })
      const step2 = makeStep('s2')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step1
      handleSelectStep(step2)
      expect(selectedStep.value).toEqual(step2)
    })

    it('当前步骤不完整时阻止切换并显示警告', () => {
      const step1 = makeStep('s1', { name: '', requestConfig: { url: '' } })
      const step2 = makeStep('s2')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step1
      handleSelectStep(step2)
      expect(selectedStep.value).toEqual(step1)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先填写当前步骤的名称 / HTTP 路径 / SQL 语句')
    })

    it('selectedStep 为 null 时直接选中', () => {
      const step = makeStep('s1')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      handleSelectStep(step)
      expect(selectedStep.value).toEqual(step)
    })

    it('选中同一步骤时不做额外校验', () => {
      const step = makeStep('s1')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step
      handleSelectStep(step)
      expect(selectedStep.value).toEqual(step)
    })

    it('名称缺失时阻止切换', () => {
      const step1 = makeStep('s1', { name: '', requestConfig: { url: '/api' } })
      const step2 = makeStep('s2')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step1
      handleSelectStep(step2)
      expect(selectedStep.value).toEqual(step1)
      expect(mocks.ElMessage.warning).toHaveBeenCalled()
    })

    it('HTTP url 缺失时阻止切换', () => {
      const step1 = makeStep('s1', { stepType: 'http', requestConfig: { url: '' } })
      const step2 = makeStep('s2')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step1
      handleSelectStep(step2)
      expect(selectedStep.value).toEqual(step1)
    })

    it('JDBC sql 缺失时阻止切换', () => {
      const step1 = makeStep('s1', { stepType: 'jdbc', requestConfig: {} })
      const step2 = makeStep('s2')
      const { handleSelectStep, selectedStep } = useSceneSteps(createOptions())
      selectedStep.value = step1
      handleSelectStep(step2)
      expect(selectedStep.value).toEqual(step1)
    })
  })

  describe('handleDeleteStep', () => {
    it('sceneId 缺失时不执行任何操作', async () => {
      const step = makeStep('s1')
      const { handleDeleteStep } = useSceneSteps(createOptions({ detail: ref(makeDetail([step])) }))
      await handleDeleteStep(step)
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('编辑态删除新建步骤直接从列表移除', async () => {
      const step = makeStep('new-123')
      const detail = ref(makeDetail([step]))
      const { handleDeleteStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      await handleDeleteStep(step)
      await nextTick()
      expect(detail.value!.steps).toHaveLength(0)
      expect(mocks.deleteSceneStep).not.toHaveBeenCalled()
    })

    it('删除已选中的新建步骤时 selectedStep 置 null', async () => {
      const step = makeStep('new-123')
      const detail = ref(makeDetail([step]))
      const { handleDeleteStep, selectedStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      selectedStep.value = step
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      await handleDeleteStep(step)
      await nextTick()
      expect(selectedStep.value).toBeNull()
    })

    it('删除未选中的新建步骤时 selectedStep 不变', async () => {
      const step1 = makeStep('new-123')
      const step2 = makeStep('s2')
      const detail = ref(makeDetail([step1, step2]))
      const { handleDeleteStep, selectedStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      selectedStep.value = step2
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      await handleDeleteStep(step1)
      await nextTick()
      expect(selectedStep.value).toEqual(step2)
    })

    it('调用 API 删除已有步骤', async () => {
      const step = makeStep('s1')
      const detail = ref(makeDetail([step]))
      const { handleDeleteStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      await handleDeleteStep(step)
      await nextTick()
      expect(mocks.deleteSceneStep).toHaveBeenCalledWith('scene-1', 's1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('步骤已删除')
    })

    it('删除已选中的已有步骤时 selectedStep 置 null', async () => {
      const step = makeStep('s1')
      const detail = ref(makeDetail([step]))
      const { handleDeleteStep, selectedStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      selectedStep.value = step
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      await handleDeleteStep(step)
      await nextTick()
      expect(selectedStep.value).toBeNull()
    })

    it('用户取消确认时不执行删除', async () => {
      const step = makeStep('s1')
      const detail = ref(makeDetail([step]))
      const { handleDeleteStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      await handleDeleteStep(step)
      expect(mocks.deleteSceneStep).not.toHaveBeenCalled()
      expect(detail.value!.steps).toHaveLength(1)
    })
  })

  describe('handleToggleStep', () => {
    it('sceneId 缺失时不执行', async () => {
      const step = makeStep('s1', { enabled: true })
      const { handleToggleStep } = useSceneSteps(createOptions())
      await handleToggleStep(step)
      expect(mocks.updateSceneStep).not.toHaveBeenCalled()
    })

    it('切换步骤 enabled 状态', async () => {
      const step = makeStep('s1', { enabled: true })
      const { handleToggleStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleToggleStep(step)
      expect(mocks.updateSceneStep).toHaveBeenCalledWith('scene-1', 's1', {
        name: step.name,
        stepType: step.stepType,
        enabled: false,
        requestConfig: step.requestConfig,
        processors: step.processors,
        validators: step.validators,
        extractors: step.extractors,
        sourceType: step.sourceType,
        sourceId: step.sourceId,
      })
      expect(step.enabled).toBe(false)
    })

    it('从禁用切换为启用', async () => {
      const step = makeStep('s1', { enabled: false })
      const { handleToggleStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleToggleStep(step)
      expect(step.enabled).toBe(true)
    })

    it('API 失败时显示错误消息', async () => {
      const step = makeStep('s1', { enabled: true })
      mocks.updateSceneStep.mockRejectedValue(new Error('网络异常'))
      const { handleToggleStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleToggleStep(step)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
      expect(step.enabled).toBe(true)
    })

    it('API 抛出非 Error 对象时显示通用错误', async () => {
      const step = makeStep('s1', { enabled: true })
      mocks.updateSceneStep.mockRejectedValue('unknown')
      const { handleToggleStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleToggleStep(step)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleReorderSteps', () => {
    it('sceneId 缺失时不执行', async () => {
      const { handleReorderSteps } = useSceneSteps(createOptions())
      await handleReorderSteps([makeStep('s1')])
      expect(mocks.reorderSceneSteps).not.toHaveBeenCalled()
    })

    it('调用 API 排序', async () => {
      const s1 = makeStep('s1')
      const s2 = makeStep('s2')
      const detail = ref(makeDetail([s1, s2]))
      const { handleReorderSteps } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      await handleReorderSteps([s2, s1])
      expect(mocks.reorderSceneSteps).toHaveBeenCalledWith('scene-1', { stepIds: ['s2', 's1'] })
      expect(detail.value!.steps).toEqual([s2, s1])
    })

    it('API 失败时显示错误消息且 detail 不变', async () => {
      const s1 = makeStep('s1')
      const s2 = makeStep('s2')
      const detail = ref(makeDetail([s1, s2]))
      mocks.reorderSceneSteps.mockRejectedValue(new Error('排序失败'))
      const { handleReorderSteps } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      await handleReorderSteps([s2, s1])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('排序失败')
      expect(detail.value!.steps).toEqual([s1, s2])
    })

    it('API 抛出非 Error 对象时显示通用错误', async () => {
      const detail = ref(makeDetail([makeStep('s1')]))
      mocks.reorderSceneSteps.mockRejectedValue(42)
      const { handleReorderSteps } = useSceneSteps(
        createOptions({ sceneId: 'scene-1', detail }),
      )
      await handleReorderSteps([makeStep('s1')])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('排序失败')
    })
  })

  describe('handleCopyStep', () => {
    it('sceneId 缺失时不执行', async () => {
      const { handleCopyStep } = useSceneSteps(createOptions())
      await handleCopyStep(makeStep('s1'))
      expect(mocks.copySceneStep).not.toHaveBeenCalled()
    })

    it('调用 API 复制步骤', async () => {
      const { handleCopyStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleCopyStep(makeStep('s1'))
      expect(mocks.copySceneStep).toHaveBeenCalledWith('scene-1', 's1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已复制步骤')
    })

    it('API 失败时显示错误消息', async () => {
      mocks.copySceneStep.mockRejectedValue(new Error('复制失败'))
      const { handleCopyStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleCopyStep(makeStep('s1'))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('复制失败')
    })

    it('API 抛出非 Error 对象时显示通用错误', async () => {
      mocks.copySceneStep.mockRejectedValue(null)
      const { handleCopyStep } = useSceneSteps(
        createOptions({ sceneId: 'scene-1' }),
      )
      await handleCopyStep(makeStep('s1'))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('复制失败')
    })
  })

  describe('handleDraftAddStep', () => {
    it('向 draftSteps 追加新步骤', () => {
      const { handleDraftAddStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftAddStep()
      expect(draftSteps.value).toHaveLength(1)
      expect(draftSteps.value[0].id).toMatch(/^new-/)
    })

    it('新步骤的 sortOrder 等于 draftSteps 长度 +1', () => {
      const { handleDraftAddStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftAddStep()
      handleDraftAddStep()
      expect(draftSteps.value[1].sortOrder).toBe(2)
    })

    it('自动选中新步骤', () => {
      const { handleDraftAddStep, selectedStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftAddStep()
      expect(selectedStep.value).toEqual(draftSteps.value[0])
    })
  })

  describe('handleDraftDeleteStep', () => {
    it('从 draftSteps 移除指定步骤', () => {
      const s1 = makeStep('d1')
      const s2 = makeStep('d2')
      const { handleDraftDeleteStep, draftSteps } = useSceneSteps(createOptions())
      draftSteps.value = [s1, s2]
      handleDraftDeleteStep(s1)
      expect(draftSteps.value).toHaveLength(1)
      expect(draftSteps.value[0]).toEqual(s2)
    })

    it('删除已选中步骤时 selectedStep 置 null', () => {
      const s1 = makeStep('d1')
      const { handleDraftDeleteStep, draftSteps, selectedStep } = useSceneSteps(createOptions())
      draftSteps.value = [s1]
      selectedStep.value = s1
      handleDraftDeleteStep(s1)
      expect(selectedStep.value).toBeNull()
    })

    it('删除非选中步骤时 selectedStep 不变', () => {
      const s1 = makeStep('d1')
      const s2 = makeStep('d2')
      const { handleDraftDeleteStep, draftSteps, selectedStep } = useSceneSteps(createOptions())
      draftSteps.value = [s1, s2]
      selectedStep.value = s2
      handleDraftDeleteStep(s1)
      expect(selectedStep.value).toEqual(s2)
    })

    it('删除不存在的步骤时不报错', () => {
      const { handleDraftDeleteStep, draftSteps } = useSceneSteps(createOptions())
      draftSteps.value = [makeStep('d1')]
      expect(() => handleDraftDeleteStep(makeStep('not-exist'))).not.toThrow()
      expect(draftSteps.value).toHaveLength(1)
    })
  })

  describe('handleDraftToggleStep', () => {
    it('切换步骤 enabled 状态', () => {
      const step = makeStep('d1', { enabled: true })
      const { handleDraftToggleStep } = useSceneSteps(createOptions())
      handleDraftToggleStep(step)
      expect(step.enabled).toBe(false)
    })

    it('从禁用切换为启用', () => {
      const step = makeStep('d1', { enabled: false })
      const { handleDraftToggleStep } = useSceneSteps(createOptions())
      handleDraftToggleStep(step)
      expect(step.enabled).toBe(true)
    })

    it('调用 bumpAutosave', () => {
      const opts = createOptions()
      const { handleDraftToggleStep } = useSceneSteps(opts)
      handleDraftToggleStep(makeStep('d1'))
      expect(opts.bumpAutosave).toHaveBeenCalled()
    })
  })

  describe('handleDraftReorderSteps', () => {
    it('重新分配 sortOrder 并替换 draftSteps', () => {
      const s1 = makeStep('d1', { sortOrder: 1 })
      const s2 = makeStep('d2', { sortOrder: 2 })
      const { handleDraftReorderSteps, draftSteps } = useSceneSteps(createOptions())
      handleDraftReorderSteps([s2, s1])
      expect(draftSteps.value).toEqual([s2, s1])
      expect(s2.sortOrder).toBe(1)
      expect(s1.sortOrder).toBe(2)
    })

    it('空数组时 draftSteps 为空', () => {
      const { handleDraftReorderSteps, draftSteps } = useSceneSteps(createOptions())
      draftSteps.value = [makeStep('d1')]
      handleDraftReorderSteps([])
      expect(draftSteps.value).toEqual([])
    })
  })

  describe('handleDraftCopyStep', () => {
    it('复制步骤追加到 draftSteps', () => {
      const step = makeStep('d1', { name: '原步骤', stepType: 'http' })
      const { handleDraftCopyStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftCopyStep(step)
      expect(draftSteps.value).toHaveLength(1)
      expect(draftSteps.value[0].name).toBe('原步骤')
      expect(draftSteps.value[0].stepType).toBe('http')
    })

    it('复制步骤生成新 id', () => {
      const step = makeStep('d1')
      const { handleDraftCopyStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftCopyStep(step)
      expect(draftSteps.value[0].id).toMatch(/^draft-/)
      expect(draftSteps.value[0].id).not.toBe('d1')
    })

    it('复制步骤的 sortOrder 递增', () => {
      const step = makeStep('d1')
      const { handleDraftCopyStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftCopyStep(step)
      expect(draftSteps.value[0].sortOrder).toBe(1)
      handleDraftCopyStep(step)
      expect(draftSteps.value[1].sortOrder).toBe(2)
    })

    it('多个复制步骤 id 唯一', () => {
      const step = makeStep('d1')
      const { handleDraftCopyStep, draftSteps } = useSceneSteps(createOptions())
      handleDraftCopyStep(step)
      handleDraftCopyStep(step)
      expect(draftSteps.value[0].id).not.toBe(draftSteps.value[1].id)
    })
  })

  describe('sorted 计算属性', () => {
    it('调用 sortedSteps 并返回结果', () => {
      const s1 = makeStep('s1', { sortOrder: 2 })
      const s2 = makeStep('s2', { sortOrder: 1 })
      mocks.sortedSteps.mockReturnValue([s2, s1])
      const opts = createOptions({ detail: ref(makeDetail([s1, s2])) })
      const { sorted } = useSceneSteps(opts)
      expect(sorted.value).toEqual([s2, s1])
    })

    it('detail 为 null 时返回空数组', () => {
      const opts = createOptions({ detail: ref(null) })
      const { sorted } = useSceneSteps(opts)
      expect(sorted.value).toEqual([])
    })
  })
})
