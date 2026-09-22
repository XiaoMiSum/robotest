import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import type { ApiSceneStepItem, ApiSceneStepVariableItem, ApiInterfaceItem } from '@/types'
import type { ApiInterfaceDetail } from '@/types/apitest'

const mocks = vi.hoisted(() => ({
  createSceneStep: vi.fn<() => Promise<string>>(),
  updateSceneStep: vi.fn<() => Promise<void>>(),
  quickCreateSteps: vi.fn<() => Promise<void>>(),
  fetchStepVariables: vi.fn<() => Promise<ApiSceneStepVariableItem[]>>(),
  updateStepVariables: vi.fn<() => Promise<void>>(),
  fetchInterfacePage: vi.fn<() => Promise<{ list: ApiInterfaceItem[] }>>(),
  fetchInterfaceDetail: vi.fn<() => Promise<ApiInterfaceDetail>>(),
  parseRequestConfig: vi.fn(),
  createValidator: vi.fn(),
  createExtractor: vi.fn(),
  serializeValidators: vi.fn(),
  serializeExtractors: vi.fn(),
  createStepVariable: vi.fn(),
  createExecutionConfig: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/project/scene', () => ({
  createSceneStep: mocks.createSceneStep,
  updateSceneStep: mocks.updateSceneStep,
  quickCreateSteps: mocks.quickCreateSteps,
  fetchStepVariables: mocks.fetchStepVariables,
  updateStepVariables: mocks.updateStepVariables,
}))

vi.mock('@/services/project/interface', () => ({
  fetchInterfacePage: mocks.fetchInterfacePage,
  fetchInterfaceDetail: mocks.fetchInterfaceDetail,
}))

vi.mock('@/pages/project/api-testing/scene/scenesModel', () => ({
  parseRequestConfig: mocks.parseRequestConfig,
  createValidator: mocks.createValidator,
  createExtractor: mocks.createExtractor,
  serializeValidators: mocks.serializeValidators,
  serializeExtractors: mocks.serializeExtractors,
  createStepVariable: mocks.createStepVariable,
  createExecutionConfig: mocks.createExecutionConfig,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useStepEditorDrawer } from './useStepEditorDrawer'

function makeStep(overrides?: Partial<ApiSceneStepItem>): ApiSceneStepItem {
  return {
    id: 'step-1',
    name: '测试步骤',
    stepType: 'http',
    sortOrder: 0,
    enabled: true,
    sourceType: 'custom',
    requestConfig: {},
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
    ...overrides,
  }
}

function makeVariable(overrides?: Partial<ApiSceneStepVariableItem>): ApiSceneStepVariableItem {
  return {
    id: 'var-1',
    name: 'varName',
    value: 'val',
    source: 'custom',
    sortOrder: 0,
    ...overrides,
  }
}

function makeInterfaceDetail(overrides?: Partial<ApiInterfaceDetail>): ApiInterfaceDetail {
  return {
    id: 'if-1',
    name: '接口1',
    protocol: 'http',
    method: 'POST',
    path: '/api/test',
    headers: [{ key: 'Content-Type', value: 'application/json', enabled: true }],
    params: [],
    body: { type: 'json', content: '{}' },
    restParams: null,
    status: 'enabled',
    changeVersion: 1,
    referenceCount: 0,
    followed: false,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function setupMocks(overrides?: {
  variables?: ApiSceneStepVariableItem[]
  interfaces?: ApiInterfaceItem[]
  interfaceDetail?: ApiInterfaceDetail
}) {
  mocks.parseRequestConfig.mockReturnValue({})
  mocks.createValidator.mockReturnValue({ id: 'v-1', name: '', enabled: true, target: 'status_code', condition: 'equals', expected: '', expression: '' })
  mocks.createExtractor.mockReturnValue({ id: 'e-1', name: '', enabled: true, source: 'json_field', expression: '', variableName: '' })
  mocks.serializeValidators.mockReturnValue([])
  mocks.serializeExtractors.mockReturnValue([])
  mocks.createStepVariable.mockReturnValue(makeVariable())
  mocks.createExecutionConfig.mockReturnValue({ timeout: 30000, retryCount: 0, conditionExpression: '' })
  mocks.fetchStepVariables.mockResolvedValue(overrides?.variables ?? [])
  mocks.fetchInterfacePage.mockResolvedValue({ list: overrides?.interfaces ?? [] })
  mocks.fetchInterfaceDetail.mockResolvedValue(overrides?.interfaceDetail ?? makeInterfaceDetail())
}

describe('useStepEditorDrawer', () => {
  let emit: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    emit = vi.fn()
    setupMocks()
  })

  function create(propsOverrides?: Partial<{ modelValue: boolean; sceneId: string; step: ApiSceneStepItem | null }>, emitFn?: ReturnType<typeof vi.fn>) {
    return useStepEditorDrawer(
      {
        modelValue: false,
        sceneId: 'scene-1',
        step: null,
        ...propsOverrides,
      },
      emitFn ?? emit,
    )
  }

  describe('初始状态', () => {
    it('draftMode 为 false（有 sceneId）', () => {
      const s = create({ sceneId: 'scene-1' })
      expect(s.draftMode.value).toBe(false)
    })

    it('draftMode 为 true（无 sceneId）', () => {
      const s = create({ sceneId: undefined })
      expect(s.draftMode.value).toBe(true)
    })

    it('visible 为 false（初始 modelValue）', () => {
      const s = create({ modelValue: false })
      expect(s.visible.value).toBe(false)
    })

    it('visible 跟随 modelValue 变化', async () => {
      const props = reactive({ modelValue: false, sceneId: 'scene-1', step: null })
      const s = useStepEditorDrawer(props, emit)
      expect(s.visible.value).toBe(false)
      props.modelValue = true
      await vi.waitFor(() => expect(s.visible.value).toBe(true))
    })

    it('visible 变化时 emit update:modelValue', async () => {
      const props = reactive({ modelValue: false, sceneId: 'scene-1', step: null })
      const s = useStepEditorDrawer(props, emit)
      s.visible.value = true
      await vi.waitFor(() => expect(emit).toHaveBeenCalledWith('update:modelValue', true))
    })

    it('form 字段默认值正确', () => {
      const s = create()
      expect(s.formName.value).toBe('')
      expect(s.formStepType.value).toBe('http')
      expect(s.formMethod.value).toBe('GET')
      expect(s.formUrl.value).toBe('')
      expect(s.formEnabled.value).toBe(true)
      expect(s.activeTab.value).toBe('basic')
      expect(s.createMode.value).toBe('manual')
      expect(s.quickInterfaceId.value).toBe('')
      expect(s.quickMode.value).toBe('copy')
      expect(s.interfaceOptions.value).toEqual([])
      expect(s.interfaceSearch.value).toBe('')
      expect(s.interfaceLoading.value).toBe(false)
      expect(s.saving.value).toBe(false)
      expect(s.variablesLoading.value).toBe(false)
    })

    it('请求相关字段默认为空', () => {
      const s = create()
      expect(s.reqHeaders.value).toEqual([])
      expect(s.reqParams.value).toEqual([])
      expect(s.reqBody.value).toEqual({ type: 'none', content: null })
      expect(s.validators.value).toEqual([])
      expect(s.extractors.value).toEqual([])
      expect(s.stepVariables.value).toEqual([])
    })

    it('executionConfig 使用默认值', () => {
      const s = create()
      expect(s.executionConfig.value).toEqual({ timeout: 30000, retryCount: 0, conditionExpression: '' })
    })
  })

  describe('visible 打开时填充表单（有 step）', () => {
    it('填充 formName、formStepType、formEnabled、createMode', async () => {
      const step = makeStep({ name: '步骤A', stepType: 'ws', enabled: false })
      mocks.parseRequestConfig.mockReturnValue({ method: 'POST', url: '/api', headers: [{ key: 'h', value: 'v', enabled: true }], params: [], body: { type: 'json', content: '{}' } })
      const s = create({ step })
      s.visible.value = true
      await vi.waitFor(() => expect(s.formName.value).toBe('步骤A'))
      expect(s.formStepType.value).toBe('ws')
      expect(s.formEnabled.value).toBe(false)
      expect(s.formMethod.value).toBe('POST')
      expect(s.formUrl.value).toBe('/api')
      expect(s.reqHeaders.value).toEqual([{ key: 'h', value: 'v', enabled: true }])
      expect(s.reqBody.value).toEqual({ type: 'json', content: '{}' })
      expect(s.createMode.value).toBe('manual')
      expect(s.activeTab.value).toBe('basic')
    })

    it('加载 stepVariables', async () => {
      const step = makeStep()
      const vars = [makeVariable()]
      setupMocks({ variables: vars })
      const s = create({ step })
      s.visible.value = true
      await vi.waitFor(() => expect(s.stepVariables.value).toEqual(vars))
    })

    it('无 step 时重置所有表单字段', async () => {
      const s = create({ step: null })
      s.visible.value = true
      await vi.waitFor(() => expect(s.formName.value).toBe(''))
      expect(s.formStepType.value).toBe('http')
      expect(s.formMethod.value).toBe('GET')
      expect(s.formUrl.value).toBe('')
      expect(s.formEnabled.value).toBe(true)
      expect(s.reqHeaders.value).toEqual([])
      expect(s.reqParams.value).toEqual([])
      expect(s.reqBody.value).toEqual({ type: 'none', content: null })
      expect(s.validators.value).toEqual([])
      expect(s.extractors.value).toEqual([])
      expect(s.stepVariables.value).toEqual([])
    })
  })

  describe('loadStepVariables', () => {
    it('成功加载', async () => {
      const step = makeStep()
      const vars = [makeVariable()]
      setupMocks({ variables: vars })
      const s = create({ step })
      s.visible.value = true
      await vi.waitFor(() => expect(mocks.fetchStepVariables).toHaveBeenCalledWith('scene-1', 'step-1'))
      expect(s.stepVariables.value).toEqual(vars)
      expect(s.variablesLoading.value).toBe(false)
    })

    it('加载失败时 stepVariables 为空', async () => {
      mocks.fetchStepVariables.mockRejectedValue(new Error('fail'))
      const step = makeStep()
      const s = create({ step })
      s.visible.value = true
      await vi.waitFor(() => expect(mocks.fetchStepVariables).toHaveBeenCalled())
      await vi.waitFor(() => expect(s.variablesLoading.value).toBe(false))
      expect(s.stepVariables.value).toEqual([])
    })

    it('无 sceneId 时不调用', async () => {
      const step = makeStep()
      const s = create({ sceneId: undefined, step })
      s.visible.value = true
      await vi.waitFor(() => expect(mocks.fetchStepVariables).not.toHaveBeenCalled())
    })

    it('无 step 时不调用', async () => {
      const s = create({ step: null })
      s.visible.value = true
      await vi.waitFor(() => expect(mocks.fetchStepVariables).not.toHaveBeenCalled())
    })
  })

  describe('loadInterfaces', () => {
    it('成功加载接口列表', async () => {
      const s = create()
      await s.loadInterfaces()
      expect(mocks.fetchInterfacePage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 50, search: undefined })
      expect(s.interfaceLoading.value).toBe(false)
    })

    it('带搜索参数加载', async () => {
      const s = create()
      s.interfaceSearch.value = '测试'
      await s.loadInterfaces()
      expect(mocks.fetchInterfacePage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 50, search: '测试' })
    })

    it('加载失败时接口列表为空', async () => {
      mocks.fetchInterfacePage.mockRejectedValue(new Error('fail'))
      const s = create()
      await s.loadInterfaces()
      expect(s.interfaceOptions.value).toEqual([])
      expect(s.interfaceLoading.value).toBe(false)
    })
  })

  describe('handleCreateModeChange', () => {
    it('切换到 manual 模式', () => {
      const s = create()
      s.handleCreateModeChange('manual')
      expect(s.createMode.value).toBe('manual')
    })

    it('切换到 quick 模式并加载接口列表', () => {
      const s = create()
      s.handleCreateModeChange('quick')
      expect(s.createMode.value).toBe('quick')
      expect(mocks.fetchInterfacePage).toHaveBeenCalled()
    })

    it('切换到 quick 模式时接口列表非空则不重复加载', () => {
      const s = create()
      s.interfaceOptions.value = [{ id: '1' } as ApiInterfaceItem]
      s.handleCreateModeChange('quick')
      expect(s.createMode.value).toBe('quick')
      expect(mocks.fetchInterfacePage).not.toHaveBeenCalled()
    })
  })

  describe('addStepVariable / removeStepVariable', () => {
    it('添加步骤变量', () => {
      const s = create()
      expect(s.stepVariables.value).toHaveLength(0)
      s.addStepVariable()
      expect(s.stepVariables.value).toHaveLength(1)
      expect(mocks.createStepVariable).toHaveBeenCalled()
    })

    it('移除步骤变量', () => {
      const s = create()
      s.stepVariables.value = [makeVariable({ id: 'v1' }), makeVariable({ id: 'v2' })]
      s.removeStepVariable(0)
      expect(s.stepVariables.value).toHaveLength(1)
      expect(s.stepVariables.value[0].id).toBe('v2')
    })
  })

  describe('addValidator / removeValidator', () => {
    it('添加验证器', () => {
      const s = create()
      s.addValidator()
      expect(s.validators.value).toHaveLength(1)
      expect(mocks.createValidator).toHaveBeenCalled()
    })

    it('移除验证器', () => {
      const s = create()
      s.validators.value = [{ id: 'v1' } as never, { id: 'v2' } as never]
      s.removeValidator(0)
      expect(s.validators.value).toHaveLength(1)
    })
  })

  describe('addExtractor / removeExtractor', () => {
    it('添加提取器', () => {
      const s = create()
      s.addExtractor()
      expect(s.extractors.value).toHaveLength(1)
      expect(mocks.createExtractor).toHaveBeenCalled()
    })

    it('移除提取器', () => {
      const s = create()
      s.extractors.value = [{ id: 'e1' } as never, { id: 'e2' } as never]
      s.removeExtractor(0)
      expect(s.extractors.value).toHaveLength(1)
    })
  })

  describe('handleSave — draft mode', () => {
    it('draftMode 时调用 handleDraftSave', async () => {
      const s = create({ sceneId: undefined })
      s.formName.value = '草稿步骤'
      await s.handleSave()
      expect(emit).toHaveBeenCalledWith('commit', expect.objectContaining({ name: '草稿步骤' }))
    })

    it('draftMode quick 模式无 quickInterfaceId 时 warning', async () => {
      const s = create({ sceneId: undefined })
      s.createMode.value = 'quick'
      s.quickInterfaceId.value = ''
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择接口')
      expect(emit).not.toHaveBeenCalledWith('commit', expect.anything())
    })

    it('draftMode quick 模式有 quickInterfaceId 时获取接口详情并 commit', async () => {
      const detail = makeInterfaceDetail()
      mocks.fetchInterfaceDetail.mockResolvedValue(detail)
      const s = create({ sceneId: undefined })
      s.createMode.value = 'quick'
      s.quickInterfaceId.value = 'if-1'
      await s.handleSave()
      expect(mocks.fetchInterfaceDetail).toHaveBeenCalledWith('if-1')
      expect(emit).toHaveBeenCalledWith('commit', expect.objectContaining({
        name: detail.name,
        stepType: 'http',
        sourceType: 'copy',
        sourceInterfaceId: detail.id,
        sourceInterfaceName: detail.name,
      }))
    })

    it('draftMode quick 模式 link mode 设置 sourceType 为 link', async () => {
      mocks.fetchInterfaceDetail.mockResolvedValue(makeInterfaceDetail())
      const s = create({ sceneId: undefined })
      s.createMode.value = 'quick'
      s.quickMode.value = 'link'
      s.quickInterfaceId.value = 'if-1'
      await s.handleSave()
      expect(emit).toHaveBeenCalledWith('commit', expect.objectContaining({ sourceType: 'link' }))
    })

    it('draftMode quick 模式接口详情请求失败时抛出错误', async () => {
      mocks.fetchInterfaceDetail.mockRejectedValue(new Error('接口不存在'))
      const s = create({ sceneId: undefined })
      s.createMode.value = 'quick'
      s.quickInterfaceId.value = 'if-1'
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('接口不存在')
    })

    it('draftMode quick 模式接口详情非 Error 异常显示通用消息', async () => {
      mocks.fetchInterfaceDetail.mockRejectedValue(42)
      const s = create({ sceneId: undefined })
      s.createMode.value = 'quick'
      s.quickInterfaceId.value = 'if-1'
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('draftMode manual 模式无名称时 warning', async () => {
      const s = create({ sceneId: undefined })
      s.formName.value = '   '
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写步骤名称')
      expect(emit).not.toHaveBeenCalledWith('commit', expect.anything())
    })

    it('draftMode manual 模式构建完整 draft', async () => {
      mocks.serializeValidators.mockReturnValue([{ target: 'status_code' }])
      mocks.serializeExtractors.mockReturnValue([{ source: 'json_field' }])
      const s = create({ sceneId: undefined })
      s.formName.value = '草稿'
      s.formStepType.value = 'ws'
      s.formMethod.value = 'POST'
      s.formUrl.value = '/test'
      s.formEnabled.value = false
      s.reqHeaders.value = [{ key: 'h1', value: 'v1', enabled: true }]
      s.reqParams.value = [{ key: 'p1', value: 'v2', enabled: true }]
      s.reqBody.value = { type: 'json', content: '{}' }
      s.executionConfig.value.conditionExpression = 'expr'
      await s.handleSave()
      expect(emit).toHaveBeenCalledWith('commit', expect.objectContaining({
        name: '草稿',
        stepType: 'ws',
        enabled: false,
        sourceType: 'custom',
        requestConfig: expect.objectContaining({ method: 'POST', url: '/test' }),
      }))
    })
  })

  describe('handleSave — non-draft mode', () => {
    it('无 sceneId 时直接返回', async () => {
      const s = create({ sceneId: undefined })
      await s.handleSave()
      expect(mocks.createSceneStep).not.toHaveBeenCalled()
      expect(mocks.updateSceneStep).not.toHaveBeenCalled()
    })

    it('quick 模式无 quickInterfaceId 时 warning', async () => {
      const s = create({ sceneId: 'scene-1' })
      s.createMode.value = 'quick'
      s.quickInterfaceId.value = ''
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择接口')
    })

    it('quick 模式有 quickInterfaceId 时调用 quickCreateSteps', async () => {
      const s = create({ sceneId: 'scene-1' })
      s.createMode.value = 'quick'
      s.quickInterfaceId.value = 'if-1'
      s.quickMode.value = 'copy'
      await s.handleSave()
      expect(mocks.quickCreateSteps).toHaveBeenCalledWith('scene-1', { interfaceId: 'if-1', mode: 'copy' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('步骤已创建')
      expect(emit).toHaveBeenCalledWith('saved')
    })

    it('manual 模式无名称时 warning', async () => {
      const s = create({ sceneId: 'scene-1' })
      s.formName.value = '   '
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写步骤名称')
    })

    it('manual 模式更新已有步骤', async () => {
      const step = makeStep()
      const s = create({ sceneId: 'scene-1', step })
      s.visible.value = true
      await vi.waitFor(() => expect(s.formName.value).toBe('测试步骤'))
      s.formName.value = '更新名称'
      await s.handleSave()
      expect(mocks.updateSceneStep).toHaveBeenCalledWith('scene-1', 'step-1', expect.objectContaining({ name: '更新名称' }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('步骤已更新')
      expect(emit).toHaveBeenCalledWith('saved')
    })

    it('manual 模式更新时有 stepVariables 则调用 updateStepVariables', async () => {
      const step = makeStep()
      const s = create({ sceneId: 'scene-1', step })
      s.visible.value = true
      await vi.waitFor(() => expect(s.formName.value).toBe('测试步骤'))
      s.stepVariables.value = [makeVariable({ name: 'myVar' })]
      await s.handleSave()
      expect(mocks.updateStepVariables).toHaveBeenCalledWith('scene-1', 'step-1', { variables: expect.arrayContaining([expect.objectContaining({ name: 'myVar' })]) })
    })

    it('manual 模式更新时 stepVariables 为空则不调用 updateStepVariables', async () => {
      const step = makeStep()
      const s = create({ sceneId: 'scene-1', step })
      s.visible.value = true
      await vi.waitFor(() => expect(s.formName.value).toBe('测试步骤'))
      s.stepVariables.value = []
      await s.handleSave()
      expect(mocks.updateStepVariables).not.toHaveBeenCalled()
    })

    it('manual 模式创建新步骤', async () => {
      const s = create({ sceneId: 'scene-1', step: null })
      s.formName.value = '新步骤'
      await s.handleSave()
      expect(mocks.createSceneStep).toHaveBeenCalledWith('scene-1', expect.objectContaining({ name: '新步骤', sourceType: 'custom' }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('步骤已创建')
      expect(emit).toHaveBeenCalledWith('saved')
    })

    it('保存失败时显示错误消息', async () => {
      const s = create({ sceneId: 'scene-1', step: null })
      s.formName.value = '新步骤'
      mocks.createSceneStep.mockRejectedValue(new Error('网络错误'))
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.saving.value).toBe(false)
    })

    it('保存失败非 Error 异常显示通用消息', async () => {
      const s = create({ sceneId: 'scene-1', step: null })
      s.formName.value = '新步骤'
      mocks.createSceneStep.mockRejectedValue(42)
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('trim 后的名称提交', async () => {
      const s = create({ sceneId: 'scene-1', step: null })
      s.formName.value = '  名称  '
      await s.handleSave()
      expect(mocks.createSceneStep).toHaveBeenCalledWith('scene-1', expect.objectContaining({ name: '名称' }))
    })
  })

  describe('buildRequestConfig — header/param 过滤', () => {
    it('过滤空 key 的 header 和 param', async () => {
      const s = create({ sceneId: 'scene-1', step: null })
      s.formMethod.value = 'POST'
      s.formUrl.value = '/test'
      s.reqHeaders.value = [
        { key: 'Content-Type', value: 'json', enabled: true },
        { key: '   ', value: 'skip', enabled: true },
        { key: '', value: 'skip', enabled: true },
      ]
      s.reqParams.value = [
        { key: 'page', value: '1', enabled: true },
        { key: '  ', value: 'skip', enabled: true },
      ]
      s.formName.value = '测试'
      await s.handleSave()
      const callArgs = mocks.createSceneStep.mock.calls[0] as unknown[]
      const payload = callArgs[1] as Record<string, unknown>
      const cfg = payload.requestConfig as Record<string, unknown>
      expect(cfg.headers).toHaveLength(1)
      expect(cfg.params).toHaveLength(1)
    })
  })

  describe('stepVariables 保存时过滤空名称', () => {
    it('只保存 name 非空的变量', async () => {
      const step = makeStep()
      const s = create({ sceneId: 'scene-1', step })
      s.visible.value = true
      await vi.waitFor(() => expect(s.formName.value).toBe('测试步骤'))
      s.stepVariables.value = [
        makeVariable({ name: 'valid', id: 'v1' }),
        makeVariable({ name: '', id: 'v2' }),
        makeVariable({ name: '   ', id: 'v3' }),
      ]
      await s.handleSave()
      expect(mocks.updateStepVariables).toHaveBeenCalledWith('scene-1', 'step-1', {
        variables: [expect.objectContaining({ name: 'valid' })],
      })
    })
  })

  describe('saving 状态', () => {
    it('handleSave 期间 saving 为 true，完成后为 false', async () => {
      const s = create({ sceneId: 'scene-1', step: null })
      s.formName.value = '测试'
      expect(s.saving.value).toBe(false)
      const p = s.handleSave()
      expect(s.saving.value).toBe(true)
      await p
      expect(s.saving.value).toBe(false)
    })
  })
})
