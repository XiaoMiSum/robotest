import { ref, nextTick } from 'vue'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import type { ApiSceneDetail, ApiSceneStepItem } from '@/types'
import type { SceneProcessorElement } from './useSceneProcessors'

const mocks = vi.hoisted(() => ({
  fetchSceneDetail: vi.fn(),
  createScene: vi.fn(),
  updateScene: vi.fn(),
  deleteScene: vi.fn(),
  executeScene: vi.fn(),
  executeDraftScene: vi.fn(),
  fetchEnvironments: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  toSelectableModuleOptions: vi.fn<(modules: unknown[]) => unknown[]>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/project/api-testing/scene', () => ({
  fetchSceneDetail: mocks.fetchSceneDetail,
  createScene: mocks.createScene,
  updateScene: mocks.updateScene,
  deleteScene: mocks.deleteScene,
  executeScene: mocks.executeScene,
  executeDraftScene: mocks.executeDraftScene,
}))

vi.mock('@/services/project/api-testing/environment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('@/composables/project/api-testing/interface/interfacesModel', () => ({
  toSelectableModuleOptions: mocks.toSelectableModuleOptions,
}))

import { useSceneEditor } from './useSceneEditor'

function makeSceneDetail(overrides?: Partial<ApiSceneDetail>): ApiSceneDetail {
  return {
    id: 'scene-1',
    name: '测试场景',
    moduleId: 'mod-1',
    description: '场景描述',
    environmentId: 'env-1',
    priority: 'P1',
    status: 'draft',
    followed: false,
    variables: [],
    processors: [],
    changeVersion: 1,
    steps: [],
    ...overrides,
  }
}

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

function makeVariable() {
  return [{ key: 'var1', value: 'val1', description: '', enabled: true }]
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers()
  mocks.toSelectableModuleOptions.mockImplementation((m: unknown[]) => m)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useSceneEditor', () => {
  describe('初始状态', () => {
    it('loading 初始为 false', () => {
      const { loading } = useSceneEditor({}, vi.fn())
      expect(loading.value).toBe(false)
    })

    it('saving 初始为 false', () => {
      const { saving } = useSceneEditor({}, vi.fn())
      expect(saving.value).toBe(false)
    })

    it('running 初始为 false', () => {
      const { running } = useSceneEditor({}, vi.fn())
      expect(running.value).toBe(false)
    })

    it('detail 初始为 null', () => {
      const { detail } = useSceneEditor({}, vi.fn())
      expect(detail.value).toBeNull()
    })

    it('dirty 初始为 false', () => {
      const { dirty } = useSceneEditor({}, vi.fn())
      expect(dirty.value).toBe(false)
    })

    it('sceneSection 初始为 steps', () => {
      const { sceneSection } = useSceneEditor({}, vi.fn())
      expect(sceneSection.value).toBe('steps')
    })

    it('editName 初始为空字符串', () => {
      const { editName } = useSceneEditor({}, vi.fn())
      expect(editName.value).toBe('')
    })

    it('showDescription 初始为 false', () => {
      const { showDescription } = useSceneEditor({}, vi.fn())
      expect(showDescription.value).toBe(false)
    })

    it('editDescription 初始为空字符串', () => {
      const { editDescription } = useSceneEditor({}, vi.fn())
      expect(editDescription.value).toBe('')
    })

    it('editModuleId 初始为 null', () => {
      const { editModuleId } = useSceneEditor({}, vi.fn())
      expect(editModuleId.value).toBeNull()
    })

    it('editEnvironmentId 初始为 null', () => {
      const { editEnvironmentId } = useSceneEditor({}, vi.fn())
      expect(editEnvironmentId.value).toBeNull()
    })

    it('editPriority 初始为 P2', () => {
      const { editPriority } = useSceneEditor({}, vi.fn())
      expect(editPriority.value).toBe('P2')
    })

    it('editStatus 初始为 draft', () => {
      const { editStatus } = useSceneEditor({}, vi.fn())
      expect(editStatus.value).toBe('draft')
    })

    it('moduleTree 初始为空数组', () => {
      const { moduleTree } = useSceneEditor({}, vi.fn())
      expect(moduleTree.value).toEqual([])
    })

    it('environmentOptions 初始为空数组', () => {
      const { environmentOptions } = useSceneEditor({}, vi.fn())
      expect(environmentOptions.value).toEqual([])
    })

    it('autoSaveLabel 初始为空字符串', () => {
      const { autoSaveLabel } = useSceneEditor({}, vi.fn())
      expect(autoSaveLabel.value).toBe('')
    })

    it('SCENE_PRIORITY_OPTIONS 包含 P0-P3', () => {
      const { SCENE_PRIORITY_OPTIONS } = useSceneEditor({}, vi.fn())
      expect(SCENE_PRIORITY_OPTIONS).toHaveLength(4)
      expect(SCENE_PRIORITY_OPTIONS.map((o) => o.value)).toEqual(['P0', 'P1', 'P2', 'P3'])
    })
  })

  describe('isCreateMode', () => {
    it('createMode 为 true 时返回 true', () => {
      const { isCreateMode } = useSceneEditor({ createMode: true }, vi.fn())
      expect(isCreateMode.value).toBe(true)
    })

    it('sceneId 存在且 createMode 未设置时返回 false', () => {
      const { isCreateMode } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      expect(isCreateMode.value).toBe(false)
    })

    it('createMode 和 sceneId 都未设置时返回 true', () => {
      const { isCreateMode } = useSceneEditor({}, vi.fn())
      expect(isCreateMode.value).toBe(true)
    })
  })

  describe('currentPriorityColor', () => {
    it('返回当前优先级对应的颜色', () => {
      const { currentPriorityColor, editPriority } = useSceneEditor({}, vi.fn())
      editPriority.value = 'P1'
      expect(currentPriorityColor.value).toBe('var(--color-priority-p1)')
    })

    it('优先级不在选项中时返回 undefined', () => {
      const { currentPriorityColor, editPriority } = useSceneEditor({}, vi.fn())
      editPriority.value = 'P99'
      expect(currentPriorityColor.value).toBeUndefined()
    })

    it('优先级为 null 时返回 undefined', () => {
      const { currentPriorityColor, editPriority } = useSceneEditor({}, vi.fn())
      editPriority.value = null
      expect(currentPriorityColor.value).toBeUndefined()
    })
  })

  describe('moduleOptions', () => {
    it('委托 toSelectableModuleOptions 转换 moduleTree', () => {
      const { moduleOptions, moduleTree } = useSceneEditor({}, vi.fn())
      moduleTree.value = [{ id: '1', name: 'mod1' }] as never[]
      void moduleOptions.value
      expect(mocks.toSelectableModuleOptions).toHaveBeenCalledWith([{ id: '1', name: 'mod1' }])
      expect(moduleOptions.value).toEqual([{ id: '1', name: 'mod1' }])
    })
  })

  describe('watchers', () => {
    it('editName 变更触发 dirty-change', async () => {
      const emit = vi.fn()
      const { editName } = useSceneEditor({}, emit)
      editName.value = 'new name'
      await nextTick()
      expect(emit).toHaveBeenCalledWith('dirty-change', true)
    })

    it('editDescription 变更触发 dirty-change', async () => {
      const emit = vi.fn()
      const { editDescription } = useSceneEditor({}, emit)
      editDescription.value = 'new desc'
      await nextTick()
      expect(emit).toHaveBeenCalledWith('dirty-change', true)
    })

    it('editEnvironmentId 变更触发 dirty-change', async () => {
      const emit = vi.fn()
      const { editEnvironmentId } = useSceneEditor({}, emit)
      editEnvironmentId.value = 'env-2'
      await nextTick()
      expect(emit).toHaveBeenCalledWith('dirty-change', true)
    })

    it('editPriority 变更触发 dirty-change', async () => {
      const emit = vi.fn()
      const { editPriority } = useSceneEditor({}, emit)
      editPriority.value = 'P0'
      await nextTick()
      expect(emit).toHaveBeenCalledWith('dirty-change', true)
    })

    it('editName 变更触发 title-update', async () => {
      const emit = vi.fn()
      const { editName } = useSceneEditor({}, emit)
      editName.value = '新名称'
      await nextTick()
      expect(emit).toHaveBeenCalledWith('title-update', '新名称')
    })

    it('editName 为空时 title-update 发送默认文案', async () => {
      const emit = vi.fn()
      const { editName } = useSceneEditor({}, emit)
      editName.value = 'something'
      await nextTick()
      editName.value = ''
      await nextTick()
      expect(emit).toHaveBeenCalledWith('title-update', '新场景')
    })

    it('editName 纯空格时 title-update 发送默认文案', async () => {
      const emit = vi.fn()
      const { editName } = useSceneEditor({}, emit)
      editName.value = 'something'
      await nextTick()
      editName.value = '   '
      await nextTick()
      expect(emit).toHaveBeenCalledWith('title-update', '新场景')
    })
  })

  describe('bumpAutosave', () => {
    it('设置 autoSaveLabel 为包含时间的字符串', () => {
      const { bumpAutosave, autoSaveLabel } = useSceneEditor({}, vi.fn())
      bumpAutosave()
      expect(autoSaveLabel.value).toMatch(/^自动保存已开启 \d{2}:\d{2}:\d{2}$/)
    })

    it('5秒后自动清除 autoSaveLabel', () => {
      const { bumpAutosave, autoSaveLabel } = useSceneEditor({}, vi.fn())
      bumpAutosave()
      expect(autoSaveLabel.value).not.toBe('')
      vi.advanceTimersByTime(5000)
      expect(autoSaveLabel.value).toBe('')
    })

    it('连续调用只保留最后一次定时器', () => {
      const { bumpAutosave, autoSaveLabel } = useSceneEditor({}, vi.fn())
      bumpAutosave()
      vi.advanceTimersByTime(3000)
      bumpAutosave()
      vi.advanceTimersByTime(3000)
      expect(autoSaveLabel.value).not.toBe('')
      vi.advanceTimersByTime(2000)
      expect(autoSaveLabel.value).toBe('')
    })
  })

  describe('loadModules', () => {
    it('成功时填充 moduleTree', async () => {
      const tree = [{ id: 'mod-1', name: '模块1' }]
      mocks.fetchProjectModuleTree.mockResolvedValue(tree)
      const { loadModules, moduleTree } = useSceneEditor({}, vi.fn())
      await loadModules()
      expect(mocks.fetchProjectModuleTree).toHaveBeenCalledWith('scene')
      expect(moduleTree.value).toStrictEqual(tree)
    })

    it('失败时 moduleTree 保持空数组', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('fail'))
      const { loadModules, moduleTree } = useSceneEditor({}, vi.fn())
      await loadModules()
      expect(moduleTree.value).toEqual([])
    })
  })

  describe('loadEnvironments', () => {
    it('成功时填充 environmentOptions', async () => {
      const envs = [{ id: 'env-1', name: '环境1', isDefault: false, sortOrder: 0, httpConfigCount: 0, variableCount: 0, dataSourceCount: 0, processorCount: 0 }]
      mocks.fetchEnvironments.mockResolvedValue(envs)
      const { loadEnvironments, environmentOptions } = useSceneEditor({}, vi.fn())
      await loadEnvironments()
      expect(environmentOptions.value).toStrictEqual(envs)
    })

    it('失败时 environmentOptions 保持空数组', async () => {
      mocks.fetchEnvironments.mockRejectedValue(new Error('fail'))
      const { loadEnvironments, environmentOptions } = useSceneEditor({}, vi.fn())
      await loadEnvironments()
      expect(environmentOptions.value).toEqual([])
    })

    it('创建模式且未选环境时自动选中默认环境', async () => {
      const envs = [
        { id: 'env-1', name: '环境1', isDefault: false, sortOrder: 0, httpConfigCount: 0, variableCount: 0, dataSourceCount: 0, processorCount: 0 },
        { id: 'env-2', name: '默认环境', isDefault: true, sortOrder: 1, httpConfigCount: 0, variableCount: 0, dataSourceCount: 0, processorCount: 0 },
      ]
      mocks.fetchEnvironments.mockResolvedValue(envs)
      const { loadEnvironments, editEnvironmentId } = useSceneEditor({ createMode: true }, vi.fn())
      await loadEnvironments()
      expect(editEnvironmentId.value).toBe('env-2')
    })

    it('创建模式但已有环境时不覆盖', async () => {
      const envs = [{ id: 'env-2', name: '默认环境', isDefault: true, sortOrder: 0, httpConfigCount: 0, variableCount: 0, dataSourceCount: 0, processorCount: 0 }]
      mocks.fetchEnvironments.mockResolvedValue(envs)
      const { loadEnvironments, editEnvironmentId } = useSceneEditor({ createMode: true }, vi.fn())
      editEnvironmentId.value = 'env-1'
      await loadEnvironments()
      expect(editEnvironmentId.value).toBe('env-1')
    })

    it('非创建模式时即使有默认环境也不自动选中', async () => {
      const envs = [{ id: 'env-2', name: '默认环境', isDefault: true, sortOrder: 0, httpConfigCount: 0, variableCount: 0, dataSourceCount: 0, processorCount: 0 }]
      mocks.fetchEnvironments.mockResolvedValue(envs)
      const { loadEnvironments, editEnvironmentId } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await loadEnvironments()
      expect(editEnvironmentId.value).toBeNull()
    })
  })

  describe('loadDetail', () => {
    it('sceneId 不存在时直接返回', async () => {
      const { loadDetail, loading } = useSceneEditor({}, vi.fn())
      await loadDetail()
      expect(loading.value).toBe(false)
      expect(mocks.fetchSceneDetail).not.toHaveBeenCalled()
    })

    it('成功加载场景详情', async () => {
      const detail = makeSceneDetail()
      mocks.fetchSceneDetail.mockResolvedValue(detail)
      const emit = vi.fn()
      const { loadDetail, loading, detail: detailRef, editName, editDescription, editModuleId, editEnvironmentId, editPriority, editStatus } = useSceneEditor({ sceneId: 'scene-1' }, emit)
      await loadDetail()
      expect(loading.value).toBe(false)
      expect(detailRef.value).toStrictEqual(detail)
      expect(editName.value).toBe('测试场景')
      expect(editDescription.value).toBe('场景描述')
      expect(editModuleId.value).toBe('mod-1')
      expect(editEnvironmentId.value).toBe('env-1')
      expect(editPriority.value).toBe('P1')
      expect(editStatus.value).toBe('draft')
      expect(emit).toHaveBeenCalledWith('title-update', '测试场景')
    })

    it('加载失败时显示错误消息', async () => {
      mocks.fetchSceneDetail.mockRejectedValue(new Error('网络错误'))
      const { loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await loadDetail()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
    })

    it('加载失败且非 Error 实例时显示通用错误', async () => {
      mocks.fetchSceneDetail.mockRejectedValue('string error')
      const { loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await loadDetail()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('场景加载失败')
    })

    it('加载完成后 loading 恢复为 false', async () => {
      mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail())
      const { loadDetail, loading } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      const p = loadDetail()
      expect(loading.value).toBe(true)
      await p
      expect(loading.value).toBe(false)
    })

    it('场景 description 为 null 时 editDescription 设为空字符串', async () => {
      mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail({ description: null }))
      const { loadDetail, editDescription } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await loadDetail()
      expect(editDescription.value).toBe('')
    })

    it('场景 moduleId 为 null 时 editModuleId 设为 null', async () => {
      mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail({ moduleId: null }))
      const { loadDetail, editModuleId } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await loadDetail()
      expect(editModuleId.value).toBeNull()
    })
  })

  describe('prefillFromCopy', () => {
    it('copyFromId 不存在时直接返回', async () => {
      const vars = ref(makeVariable())
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const { prefillFromCopy, loading } = useSceneEditor({}, vi.fn())
      await prefillFromCopy(vars, processors, steps, (s) => s)
      expect(loading.value).toBe(false)
      expect(mocks.fetchSceneDetail).not.toHaveBeenCalled()
    })

    it('成功复制预填基础信息', async () => {
      const src = makeSceneDetail({
        name: '源场景',
        description: '源描述',
        moduleId: 'mod-2',
        environmentId: 'env-3',
        priority: 'P0',
        variables: [{ name: 'k1', value: 'v1', description: 'desc1' }],
        processors: [{ name: 'proc1', enabled: true }] as Record<string, unknown>[],
        steps: [makeStep({ id: 's1' })],
      })
      mocks.fetchSceneDetail.mockResolvedValue(src)
      const vars = ref(makeVariable())
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const prefillFn = vi.fn((s: ApiSceneStepItem[]) => [...s, makeStep({ id: 'prefilled' })])
      const { prefillFromCopy, editName, editDescription, editModuleId, editEnvironmentId, editPriority, loading } = useSceneEditor({ copyFromId: 'copy-1' }, vi.fn())
      await prefillFromCopy(vars, processors, steps, prefillFn)
      expect(editName.value).toBe('源场景（副本）')
      expect(editDescription.value).toBe('源描述')
      expect(editModuleId.value).toBe('mod-2')
      expect(editEnvironmentId.value).toBe('env-3')
      expect(editPriority.value).toBe('P0')
      expect(loading.value).toBe(false)
    })

    it('成功复制预填变量', async () => {
      const src = makeSceneDetail({
        variables: [
          { name: 'k1', value: 'v1', description: 'd1' },
          { name: 'k2', value: 'v2', description: 'd2' },
        ],
      })
      mocks.fetchSceneDetail.mockResolvedValue(src)
      const vars = ref<{ key: string; value: string; description: string; enabled: boolean }[]>([])
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const { prefillFromCopy } = useSceneEditor({ copyFromId: 'copy-1' }, vi.fn())
      await prefillFromCopy(vars, processors, steps, (s) => s)
      expect(vars.value).toEqual([
        { key: 'k1', value: 'v1', description: 'd1', enabled: true },
        { key: 'k2', value: 'v2', description: 'd2', enabled: true },
      ])
    })

    it('成功复制预填步骤', async () => {
      const srcSteps = [makeStep({ id: 'src-step-1', name: '源步骤' })]
      mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail({ steps: srcSteps }))
      const vars = ref(makeVariable())
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const prefillFn = vi.fn((s: ApiSceneStepItem[]) => s)
      const { prefillFromCopy } = useSceneEditor({ copyFromId: 'copy-1' }, vi.fn())
      await prefillFromCopy(vars, processors, steps, prefillFn)
      expect(prefillFn).toHaveBeenCalledWith(srcSteps)
    })

    it('复制失败时显示错误消息', async () => {
      mocks.fetchSceneDetail.mockRejectedValue(new Error('复制失败'))
      const vars = ref(makeVariable())
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const { prefillFromCopy } = useSceneEditor({ copyFromId: 'copy-1' }, vi.fn())
      await prefillFromCopy(vars, processors, steps, (s) => s)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('复制失败')
    })

    it('复制失败且非 Error 实例时显示通用错误', async () => {
      mocks.fetchSceneDetail.mockRejectedValue('fail')
      const vars = ref(makeVariable())
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const { prefillFromCopy } = useSceneEditor({ copyFromId: 'copy-1' }, vi.fn())
      await prefillFromCopy(vars, processors, steps, (s) => s)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('复制预填失败')
    })

    it('场景 description 为 null 时预填空字符串', async () => {
      mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail({ description: null }))
      const vars = ref(makeVariable())
      const processors = ref<SceneProcessorElement[]>([])
      const steps = ref<ApiSceneStepItem[]>([])
      const { prefillFromCopy, editDescription } = useSceneEditor({ copyFromId: 'copy-1' }, vi.fn())
      await prefillFromCopy(vars, processors, steps, (s) => s)
      expect(editDescription.value).toBe('')
    })
  })

  describe('handleSave', () => {
    describe('创建模式', () => {
      it('名称为空时显示警告', async () => {
        const { handleSave, editName } = useSceneEditor({ createMode: true }, vi.fn())
        editName.value = '  '
        const result = await handleSave({})
        expect(result).toBe(false)
        expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写场景名称')
        expect(mocks.createScene).not.toHaveBeenCalled()
      })

      it('模块 ID 缺失时显示警告', async () => {
        const { handleSave, editName } = useSceneEditor({ createMode: true }, vi.fn())
        editName.value = '场景'
        const result = await handleSave({})
        expect(result).toBe(false)
        expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择所属模块')
        expect(mocks.createScene).not.toHaveBeenCalled()
      })

      it('props.moduleId 作为备选模块 ID', async () => {
        mocks.createScene.mockResolvedValue('new-id')
        const { handleSave, editName } = useSceneEditor({ createMode: true, moduleId: 'mod-prop' }, vi.fn())
        editName.value = '场景'
        await handleSave({})
        expect(mocks.createScene).toHaveBeenCalledWith(expect.objectContaining({ moduleId: 'mod-prop' }))
      })

      it('成功创建场景', async () => {
        mocks.createScene.mockResolvedValue('new-id')
        const emit = vi.fn()
        const { handleSave, editName } = useSceneEditor({ createMode: true, moduleId: 'mod-1' }, emit)
        editName.value = '新场景'
        const result = await handleSave({ status: 'published' })
        expect(result).toBe(true)
        expect(mocks.createScene).toHaveBeenCalledWith(expect.objectContaining({
          name: '新场景',
          status: 'published',
        }))
        expect(mocks.ElMessage.success).toHaveBeenCalledWith('已创建')
        expect(emit).toHaveBeenCalledWith('back')
      })

      it('成功创建时调用 sceneVariablePayload', async () => {
        mocks.createScene.mockResolvedValue('new-id')
        const payloadFn = vi.fn(() => [{ name: 'k', value: 'v' }])
        const { handleSave, editName } = useSceneEditor({ createMode: true, moduleId: 'mod-1' }, vi.fn())
        editName.value = '场景'
        await handleSave({ sceneVariablePayload: payloadFn })
        expect(payloadFn).toHaveBeenCalled()
        expect(mocks.createScene).toHaveBeenCalledWith(expect.objectContaining({ variables: [{ name: 'k', value: 'v' }] }))
      })

      it('传递 editProcessors 和 draftSteps', async () => {
        mocks.createScene.mockResolvedValue('new-id')
        const procs = ref([{ name: 'proc1' }] as SceneProcessorElement[])
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const { handleSave, editName } = useSceneEditor({ createMode: true, moduleId: 'mod-1' }, vi.fn())
        editName.value = '场景'
        await handleSave({ editProcessors: procs, draftSteps })
        expect(mocks.createScene).toHaveBeenCalledWith(expect.objectContaining({
          processors: [{ name: 'proc1' }],
          steps: [expect.objectContaining({ name: '步骤1' })],
        }))
      })

      it('创建失败时显示错误消息', async () => {
        mocks.createScene.mockRejectedValue(new Error('创建失败'))
        const { handleSave, editName } = useSceneEditor({ createMode: true, moduleId: 'mod-1' }, vi.fn())
        editName.value = '场景'
        const result = await handleSave({})
        expect(result).toBe(false)
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('创建失败')
      })

      it('创建失败且非 Error 实例时显示通用错误', async () => {
        mocks.createScene.mockRejectedValue('fail')
        const { handleSave, editName } = useSceneEditor({ createMode: true, moduleId: 'mod-1' }, vi.fn())
        editName.value = '场景'
        const result = await handleSave({})
        expect(result).toBe(false)
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
      })
    })

    describe('更新模式', () => {
      it('成功更新场景', async () => {
        const sceneDetail = makeSceneDetail()
        mocks.updateScene.mockResolvedValue(true)
        mocks.fetchSceneDetail.mockResolvedValue(sceneDetail)
        const { handleSave, editName, loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await loadDetail()
        editName.value = '更新名称'
        const result = await handleSave({})
        expect(result).toBe(true)
        expect(mocks.updateScene).toHaveBeenCalledWith('scene-1', expect.objectContaining({
          name: '更新名称',
          changeVersion: 1,
        }))
        expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
      })

      it('更新时调用 loadDetail 刷新数据', async () => {
        const sceneDetail = makeSceneDetail()
        mocks.updateScene.mockResolvedValue(true)
        mocks.fetchSceneDetail.mockResolvedValue(sceneDetail)
        const { handleSave, editName, loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await loadDetail()
        mocks.fetchSceneDetail.mockClear()
        mocks.fetchSceneDetail.mockResolvedValue(sceneDetail)
        editName.value = '更新'
        await handleSave({})
        expect(mocks.fetchSceneDetail).toHaveBeenCalledWith('scene-1')
      })

      it('description 为空白时传 undefined', async () => {
        const sceneDetail = makeSceneDetail()
        mocks.updateScene.mockResolvedValue(true)
        mocks.fetchSceneDetail.mockResolvedValue(sceneDetail)
        const { handleSave, editName, editDescription, loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await loadDetail()
        editName.value = '更新'
        editDescription.value = '   '
        await handleSave({})
        expect(mocks.updateScene).toHaveBeenCalledWith('scene-1', expect.objectContaining({ description: undefined }))
      })

      it('steps 按 sortOrder 排序', async () => {
        const steps = [
          makeStep({ id: 's2', sortOrder: 2 }),
          makeStep({ id: 's1', sortOrder: 1 }),
          makeStep({ id: 's3', sortOrder: 3 }),
        ]
        mocks.updateScene.mockResolvedValue(true)
        mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail({ steps }))
        const { handleSave, editName, loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await loadDetail()
        editName.value = '更新'
        await handleSave({})
        const callArgs = mocks.updateScene.mock.calls[0]!
        const payload = callArgs[1] as { steps: { sortOrder: number }[] }
        expect(payload.steps.map((s: { sortOrder: number }) => s.sortOrder)).toEqual([1, 2, 3])
      })

      it('new- 开头的步骤 id 传 undefined', async () => {
        const steps = [makeStep({ id: 'new-123', sortOrder: 0 })]
        mocks.updateScene.mockResolvedValue(true)
        mocks.fetchSceneDetail.mockResolvedValue(makeSceneDetail({ steps }))
        const { handleSave, editName, loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await loadDetail()
        editName.value = '更新'
        await handleSave({})
        const callArgs = mocks.updateScene.mock.calls[0]!
        const payload = callArgs[1] as { steps: { id?: string }[] }
        expect(payload.steps[0].id).toBeUndefined()
      })

      it('更新失败时显示错误消息', async () => {
        const sceneDetail = makeSceneDetail()
        mocks.updateScene.mockRejectedValue(new Error('更新失败'))
        mocks.fetchSceneDetail.mockResolvedValue(sceneDetail)
        const { handleSave, editName, loadDetail } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await loadDetail()
        editName.value = '更新'
        const result = await handleSave({})
        expect(result).toBe(false)
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新失败')
      })
    })

    it('成功后 saving 恢复为 false', async () => {
      mocks.createScene.mockResolvedValue('new-id')
      const { handleSave, editName, saving } = useSceneEditor({ createMode: true, moduleId: 'mod-1' }, vi.fn())
      editName.value = '场景'
      await handleSave({})
      expect(saving.value).toBe(false)
    })
  })

  describe('handleDeleteScene', () => {
    it('sceneId 不存在时直接返回', async () => {
      const { handleDeleteScene } = useSceneEditor({}, vi.fn())
      await handleDeleteScene()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('确认删除后调用 deleteScene', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteScene.mockResolvedValue(true)
      const emit = vi.fn()
      const { handleDeleteScene } = useSceneEditor({ sceneId: 'scene-1' }, emit)
      await handleDeleteScene()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith('删除场景后不可恢复，确定删除？', '删除场景', { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' })
      expect(mocks.deleteScene).toHaveBeenCalledWith('scene-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
      expect(emit).toHaveBeenCalledWith('back')
    })

    it('取消删除时不调用 deleteScene', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const { handleDeleteScene } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await handleDeleteScene()
      expect(mocks.deleteScene).not.toHaveBeenCalled()
    })

    it('删除失败时显示错误消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteScene.mockRejectedValue(new Error('删除失败'))
      const { handleDeleteScene } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await handleDeleteScene()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
    })

    it('删除失败且非 Error 实例时显示通用错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteScene.mockRejectedValue('fail')
      const { handleDeleteScene } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
      await handleDeleteScene()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
    })
  })

  describe('handleRun', () => {
    describe('创建模式', () => {
      it('draftSteps 为空时显示警告', async () => {
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({})
        expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先添加步骤')
        expect(mocks.executeDraftScene).not.toHaveBeenCalled()
      })

      it('draftSteps 存在但为空数组时显示警告', async () => {
        const draftSteps = ref<ApiSceneStepItem[]>([])
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({ draftSteps })
        expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先添加步骤')
      })

      it('草稿执行成功', async () => {
        mocks.executeDraftScene.mockResolvedValue({ status: 'passed', passed: 3, failed: 0, skipped: 0, durationMs: 100, steps: [] })
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({ draftSteps })
        expect(mocks.executeDraftScene).toHaveBeenCalled()
        expect(mocks.ElMessage.success).toHaveBeenCalledWith('草稿运行完成：通过 3 · 失败 0 · 跳过 0')
      })

      it('草稿执行失败时显示错误', async () => {
        mocks.executeDraftScene.mockRejectedValue(new Error('运行失败'))
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({ draftSteps })
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('运行失败')
      })

      it('草稿执行失败且非 Error 实例时显示通用错误', async () => {
        mocks.executeDraftScene.mockRejectedValue('fail')
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({ draftSteps })
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('运行失败')
      })

      it('草稿执行时 running 先 true 后 false', async () => {
        mocks.executeDraftScene.mockResolvedValue({ status: 'passed', passed: 1, failed: 0, skipped: 0, durationMs: 50, steps: [] })
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const { handleRun, running } = useSceneEditor({ createMode: true }, vi.fn())
        const p = handleRun({ draftSteps })
        expect(running.value).toBe(true)
        await p
        expect(running.value).toBe(false)
      })

      it('草稿执行传递 sceneVariablePayload', async () => {
        mocks.executeDraftScene.mockResolvedValue({ status: 'passed', passed: 1, failed: 0, skipped: 0, durationMs: 50, steps: [] })
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const payloadFn = vi.fn(() => [{ name: 'k', value: 'v' }])
        const { handleRun, editName } = useSceneEditor({ createMode: true }, vi.fn())
        editName.value = '草稿场景'
        await handleRun({ draftSteps, sceneVariablePayload: payloadFn })
        expect(mocks.executeDraftScene).toHaveBeenCalledWith(expect.objectContaining({
          name: '草稿场景',
          sceneVariables: [{ name: 'k', value: 'v' }],
        }))
      })

      it('草稿执行时 editName 为空则 name 传 undefined', async () => {
        mocks.executeDraftScene.mockResolvedValue({ status: 'passed', passed: 1, failed: 0, skipped: 0, durationMs: 50, steps: [] })
        const draftSteps = ref<ApiSceneStepItem[]>([makeStep()])
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({ draftSteps })
        expect(mocks.executeDraftScene).toHaveBeenCalledWith(expect.objectContaining({ name: undefined }))
      })

      it('草稿执行传递步骤的 stepVariables', async () => {
        mocks.executeDraftScene.mockResolvedValue({ status: 'passed', passed: 1, failed: 0, skipped: 0, durationMs: 50, steps: [] })
        const stepWithVars = makeStep({ variables: [{ id: 'v1', name: 'var1', value: 'val1', source: 'user', sortOrder: 0 }] })
        const draftSteps = ref<ApiSceneStepItem[]>([stepWithVars])
        const { handleRun } = useSceneEditor({ createMode: true }, vi.fn())
        await handleRun({ draftSteps })
        const callArgs = mocks.executeDraftScene.mock.calls[0]!
        const payload = callArgs[0] as { steps: { stepVariables: unknown[] }[] }
        expect(payload.steps[0].stepVariables).toEqual([{ id: 'v1', name: 'var1', value: 'val1', source: 'user', sortOrder: 0 }])
      })
    })

    describe('更新模式', () => {
      it('sceneId 不存在时直接返回', async () => {
        const { handleRun } = useSceneEditor({}, vi.fn())
        await handleRun({})
        expect(mocks.executeScene).not.toHaveBeenCalled()
      })

      it('dirty 时先调用 saveFn', async () => {
        mocks.executeScene.mockResolvedValue({ executionId: 'exec-1', status: 'running' })
        const saveFn = vi.fn().mockResolvedValue(true)
        const { handleRun, dirty } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        dirty.value = true
        await handleRun({ saveFn })
        expect(saveFn).toHaveBeenCalled()
        expect(mocks.executeScene).toHaveBeenCalled()
      })

      it('saveFn 返回 false 时不执行', async () => {
        const saveFn = vi.fn().mockResolvedValue(false)
        const { handleRun, dirty } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        dirty.value = true
        await handleRun({ saveFn })
        expect(saveFn).toHaveBeenCalled()
        expect(mocks.executeScene).not.toHaveBeenCalled()
      })

      it('非 dirty 时不调用 saveFn', async () => {
        mocks.executeScene.mockResolvedValue({ executionId: 'exec-1', status: 'running' })
        const saveFn = vi.fn().mockResolvedValue(true)
        const { handleRun } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await handleRun({ saveFn })
        expect(saveFn).not.toHaveBeenCalled()
        expect(mocks.executeScene).toHaveBeenCalled()
      })

      it('执行成功', async () => {
        mocks.executeScene.mockResolvedValue({ executionId: 'exec-1', status: 'running' })
        const { handleRun } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await handleRun({})
        expect(mocks.executeScene).toHaveBeenCalledWith('scene-1', { environmentId: null })
        expect(mocks.ElMessage.success).toHaveBeenCalledWith('场景已触发执行（exec-1）')
      })

      it('执行失败时显示错误', async () => {
        mocks.executeScene.mockRejectedValue(new Error('执行失败'))
        const { handleRun } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await handleRun({})
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行失败')
      })

      it('执行失败且非 Error 实例时显示通用错误', async () => {
        mocks.executeScene.mockRejectedValue('fail')
        const { handleRun } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        await handleRun({})
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('运行失败')
      })

      it('running 先 true 后 false', async () => {
        mocks.executeScene.mockResolvedValue({ executionId: 'exec-1', status: 'running' })
        const { handleRun, running } = useSceneEditor({ sceneId: 'scene-1' }, vi.fn())
        const p = handleRun({})
        expect(running.value).toBe(true)
        await p
        expect(running.value).toBe(false)
      })
    })
  })
})
