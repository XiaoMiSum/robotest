import { ref, computed } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiEnvironmentDetail, ApiComponentListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchEnvironmentDetail: vi.fn<(id: string) => Promise<ApiEnvironmentDetail>>(),
  updateEnvironment: vi.fn<(id: string, data: unknown) => Promise<boolean>>(),
  resolveEnvironmentError: vi.fn<(err: unknown) => string>(),
  validateVariableRow: vi.fn<(row: { name: string; value: string }, others: Set<string>) => string | null>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  fetchComponents: vi.fn<(params?: Record<string, unknown>) => Promise<{ list: ApiComponentListItem[] }>>(),
  extractorsFromComponents: vi.fn<(items: ApiComponentListItem[]) => { testclass: string }[]>(),
  isRecord: vi.fn<(value: unknown) => boolean>(),
  processorFromComponent: vi.fn<(item: ApiComponentListItem, type: 'pre' | 'post') => Record<string, unknown>>(),
  defaultProcessorConfig: vi.fn<() => Record<string, unknown>>(() => ({})),
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))
vi.mock('@/services/project/environment', () => ({
  fetchEnvironmentDetail: mocks.fetchEnvironmentDetail,
  updateEnvironment: mocks.updateEnvironment,
}))
vi.mock('@/pages/project/api-testing/environment/environmentsModel', () => ({
  resolveEnvironmentError: mocks.resolveEnvironmentError,
  validateVariableRow: mocks.validateVariableRow,
}))
vi.mock('@/services/project/component', () => ({ fetchComponents: mocks.fetchComponents }))
vi.mock('@/components/project/api-testing/processorFormModel', () => ({
  extractorsFromComponents: mocks.extractorsFromComponents,
  isRecord: mocks.isRecord,
  processorFromComponent: mocks.processorFromComponent,
  defaultProcessorConfig: mocks.defaultProcessorConfig,
}))

const mockSelectConfig = vi.fn()
const mockAddHttpConfig = vi.fn()
const mockRemoveHttpConfig = vi.fn()
const mockRunHttpTest = vi.fn()
const mockSelectDs = vi.fn()
const mockHandleDsDriverChange = vi.fn()
const mockAddDataSource = vi.fn()
const mockRemoveDataSource = vi.fn()
const mockRunDsTest = vi.fn()
const mockSelectProcessor = vi.fn()
const mockAddProcessor = vi.fn()
const mockRemoveProcessor = vi.fn()
const mockMoveProcessor = vi.fn()
const mockCopyProcessor = vi.fn()
const mockApplyDefaultProcRef = vi.fn()
const mockProcList = vi.fn(() => [])
const mockProcElement = vi.fn(() => ({}))
const mockProcTags = vi.fn(() => [])
const mockProcDisplayName = vi.fn(() => '')

vi.mock('./useEnvironmentConfig', () => ({
  useEnvironmentHttpConfig: () => ({
    activeConfigId: ref(''),
    activeConfig: computed(() => undefined),
    orderedConfigForms: computed(() => []),
    selectConfig: mockSelectConfig,
    addHttpConfig: mockAddHttpConfig,
    removeHttpConfig: mockRemoveHttpConfig,
    testingHttpId: ref(''),
    runHttpTest: mockRunHttpTest,
  }),
  useEnvironmentDatasource: () => ({
    activeDsId: ref(''),
    activeDs: computed(() => undefined),
    orderedDsForms: computed(() => []),
    selectDs: mockSelectDs,
    selectedDsDriverOption: computed(() => undefined),
    handleDsDriverChange: mockHandleDsDriverChange,
    addDataSource: mockAddDataSource,
    removeDataSource: mockRemoveDataSource,
    testingDsId: ref(''),
    runDsTest: mockRunDsTest,
  }),
}))

vi.mock('./useEnvironmentProcessors', () => ({
  useEnvironmentProcessors: () => ({
    activeProcId: ref(''),
    selectedProcessor: computed(() => null),
    preProcCount: computed(() => 0),
    postProcCount: computed(() => 0),
    procList: mockProcList,
    procElement: mockProcElement,
    selectProcessor: mockSelectProcessor,
    addProcessor: mockAddProcessor,
    removeProcessor: mockRemoveProcessor,
    moveProcessor: mockMoveProcessor,
    copyProcessor: mockCopyProcessor,
    procTestclass: ref(''),
    procHttpRefOptions: computed(() => []),
    procDsRefOptions: computed(() => []),
    procHttpRef: ref(''),
    procDsRef: ref(''),
    procTags: mockProcTags,
    procDisplayName: mockProcDisplayName,
    applyDefaultProcRef: mockApplyDefaultProcRef,
  }),
}))

import { useEnvironmentDetailState } from './useEnvironmentDetailState'

function makeDetail(overrides?: Partial<ApiEnvironmentDetail>): ApiEnvironmentDetail {
  return {
    id: 'env-1', name: '测试环境', description: '描述', scope: 'project',
    isDefault: false, sortOrder: 1, httpConfigs: [], variables: [],
    dataSources: [], processors: [], ...overrides,
  }
}

describe('useEnvironmentDetailState', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.resolveEnvironmentError.mockReturnValue('操作失败')
    mocks.validateVariableRow.mockReturnValue(null)
    mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
  })

  describe('初始状态', () => {
    it('loading 初始为 false', () => {
      const { loading } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(loading.value).toBe(false)
    })
    it('loadError 初始为 false', () => {
      const { loadError } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(loadError.value).toBe(false)
    })
    it('detail 初始为 null', () => {
      const { detail } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(detail.value).toBeNull()
    })
    it('saving 初始为 false', () => {
      const { saving } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(saving.value).toBe(false)
    })
    it('configForms 初始为空数组', () => {
      const { configForms } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(configForms.value).toEqual([])
    })
    it('dsForms 初始为空数组', () => {
      const { dsForms } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(dsForms.value).toEqual([])
    })
    it('variableRows 初始为空数组', () => {
      const { variableRows } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(variableRows.value).toEqual([])
    })
    it('processorRows 初始为空数组', () => {
      const { processorRows } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(processorRows.value).toEqual([])
    })
    it('activeTab 初始为 http', () => {
      const { activeTab } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(activeTab.value).toBe('http')
    })
    it('extractorPickerVisible 初始为 false', () => {
      const { extractorPickerVisible } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(extractorPickerVisible.value).toBe(false)
    })
    it('procAssetPickerVisible 初始为 false', () => {
      const { procAssetPickerVisible } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(procAssetPickerVisible.value).toBe(false)
    })
  })

  describe('load', () => {
    it('成功加载时设置 detail', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.detail.value).toEqual(expect.objectContaining({ id: 'env-1', name: '测试环境' }))
      expect(state.loadError.value).toBe(false)
    })
    it('成功加载时 loading 先 true 后 false', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const promise = state.load()
      expect(state.loading.value).toBe(true)
      await promise
      expect(state.loading.value).toBe(false)
    })
    it('加载失败时 loadError 为 true', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue(new Error('网络异常'))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.loadError.value).toBe(true)
      expect(state.loading.value).toBe(false)
    })
    it('加载失败时显示 ElMessage.error', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue(new Error('fail'))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
    it('hydrate configForms', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        httpConfigs: [{ name: 'cfg1', refName: 'ref1', baseUrl: 'http://a.com', headers: [{ key: 'k', value: 'v', enabled: true }], isDefault: true }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.configForms.value).toHaveLength(1)
      expect(state.configForms.value[0].name).toBe('cfg1')
      expect(state.configForms.value[0].id).toBeDefined()
    })
    it('hydrate dsForms', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        dataSources: [{ name: 'ds1', refName: 'db1', driver: 'mysql', url: 'jdbc:mysql://localhost' }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.dsForms.value).toHaveLength(1)
      expect(state.dsForms.value[0].name).toBe('ds1')
    })
    it('hydrate variableRows 并按 key 排序', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        variables: [
          { name: 'B', value: '2', description: '', hasValue: true },
          { name: 'A', value: '1', description: '', hasValue: true },
        ],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.variableRows.value.map((r) => r.key)).toEqual(['A', 'B'])
    })
    it('hydrate processorRows', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        processors: [{ id: 'p1', processorType: 'preprocessor', name: 'proc1', config: { testclass: 'http' }, enabled: true, sortOrder: 1 }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.processorRows.value).toHaveLength(1)
      expect(state.processorRows.value[0].name).toBe('proc1')
    })
    it('processorRows config 非 Record 时替换为空对象', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        processors: [{ id: 'p1', processorType: 'preprocessor', name: 'proc1', config: 'invalid' as unknown as Record<string, unknown>, enabled: true, sortOrder: 1 }],
      }))
      mocks.isRecord.mockReturnValue(false)
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.processorRows.value[0].config).toEqual({})
    })
    it('variableRows 中 value/description 缺失时使用默认值', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        variables: [{ name: 'A', value: undefined, description: undefined, hasValue: false }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.variableRows.value[0].value).toBe('')
      expect(state.variableRows.value[0].description).toBe('')
    })
  })

  describe('variableCount', () => {
    it('无变量时为 0', () => {
      const { variableCount } = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      expect(variableCount.value).toBe(0)
    })
    it('计算 key 非空的行数', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        variables: [
          { name: 'A', value: '1', hasValue: true },
          { name: 'B', value: '2', hasValue: true },
          { name: '', value: '3', hasValue: true },
        ],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.variableCount.value).toBe(2)
    })
    it('key 为空白时不算入', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        variables: [
          { name: ' ', value: '1', hasValue: true },
          { name: 'A', value: '2', hasValue: true },
        ],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      expect(state.variableCount.value).toBe(1)
    })
  })

  describe('activeTab watcher', () => {
    it('切换到 http 时 activeProcId 被清空', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.activeTab.value = 'http'
      expect(state.activeProcId.value).toBe('')
    })
    it('切换到 variables 时 activeProcId 被清空', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.activeTab.value = 'variables'
      expect(state.activeProcId.value).toBe('')
    })
    it('切换到 datasources 时 activeProcId 被清空', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.activeTab.value = 'datasources'
      expect(state.activeProcId.value).toBe('')
    })
  })

  describe('saveAll', () => {
    it('canEdit 为 false 时不执行保存', async () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: false }, vi.fn())
      await state.saveAll()
      expect(mocks.updateEnvironment).not.toHaveBeenCalled()
    })
    it('环境名称为空时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: ' ' }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('环境名称不能为空')
      expect(mocks.updateEnvironment).not.toHaveBeenCalled()
    })
    it('HTTP 配置名称为空时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', httpConfigs: [{ name: ' ', refName: 'r', baseUrl: 'http://x.com', headers: [] }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('存在未命名的 HTTP 配置')
    })
    it('HTTP 配置缺少 refName 时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', httpConfigs: [{ name: 'cfg', refName: undefined, baseUrl: 'http://x.com', headers: [] }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('HTTP 配置「cfg」缺少引用名')
    })
    it('HTTP 配置缺少 baseUrl 时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', httpConfigs: [{ name: 'cfg', refName: 'r', baseUrl: undefined, headers: [] }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('HTTP 配置「cfg」缺少 Base URL')
    })
    it('数据源名称为空时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', dataSources: [{ name: ' ', refName: 'r', driver: 'mysql', url: 'jdbc:mysql://x' }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('存在未命名的数据源')
    })
    it('数据源缺少 refName 时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', dataSources: [{ name: 'ds', refName: undefined, driver: 'mysql', url: 'jdbc:mysql://x' }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('数据源「ds」缺少引用名')
    })
    it('数据源缺少 driver 时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', dataSources: [{ name: 'ds', refName: 'r', driver: undefined, url: 'jdbc:mysql://x' }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('数据源「ds」未选择驱动')
    })
    it('数据源缺少 url 时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', dataSources: [{ name: 'ds', refName: 'r', driver: 'mysql', url: undefined }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('数据源「ds」缺少连接 URL')
    })
    it('变量校验失败时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', variables: [{ name: 'V', value: '', hasValue: true }],
      }))
      mocks.validateVariableRow.mockReturnValue('变量名已存在')
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('变量 V：变量名已存在')
    })
    it('处理器名称为空时显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境', processors: [{ processorType: 'preprocessor', name: '', enabled: true }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('存在未命名的处理器')
    })
    it('验证通过时不显示警告', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: '环境' }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.warning).not.toHaveBeenCalled()
    })
    it('保存成功时调用 updateEnvironment 并 emit changed', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: '环境' }))
      mocks.updateEnvironment.mockResolvedValue(true)
      const emit = vi.fn()
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, emit)
      await state.load()
      await state.saveAll()
      expect(mocks.updateEnvironment).toHaveBeenCalledWith('env-1', expect.objectContaining({ name: '环境' }))
      expect(emit).toHaveBeenCalledWith('changed')
    })
    it('保存成功时显示 ElMessage.success', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: '环境' }))
      mocks.updateEnvironment.mockResolvedValue(true)
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
    })
    it('保存失败时显示 ElMessage.error', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: '环境' }))
      mocks.updateEnvironment.mockRejectedValue(new Error('fail'))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
    it('保存期间 saving 先 true 后 false', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: '环境' }))
      mocks.updateEnvironment.mockResolvedValue(true)
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      const promise = state.saveAll()
      expect(state.saving.value).toBe(true)
      await promise
      expect(state.saving.value).toBe(false)
    })
    it('保存异常时 saving 也被重置', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: '环境' }))
      mocks.updateEnvironment.mockRejectedValue(new Error('fail'))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      expect(state.saving.value).toBe(false)
    })
  })


  describe('buildAggregatePayload', () => {
    it('正确构建包含所有字段的 payload', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: '环境A', description: 'desc', isDefault: true, sortOrder: 5,
        httpConfigs: [{ name: 'cfg1', refName: 'r1', baseUrl: 'http://a.com', headers: [{ key: 'h', value: 'v', enabled: true }], isDefault: true }],
        variables: [{ name: 'VAR', value: 'val', description: 'desc', hasValue: true }],
        dataSources: [{ name: 'ds1', refName: 'dr1', driver: 'mysql', url: 'jdbc:mysql://x', isDefault: false, maxPoolSize: 10 }],
        processors: [{ processorType: 'preprocessor', name: 'p1', config: { testclass: 'http' }, sortOrder: 1, enabled: true }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      const payload = mocks.updateEnvironment.mock.calls[0][1] as Record<string, unknown>
      expect(payload.name).toBe('环境A')
      expect(payload.description).toBe('desc')
      expect(payload.isDefault).toBe(true)
      expect(payload.sortOrder).toBe(5)
      expect(payload.httpConfigs).toHaveLength(1)
      expect(payload.variables).toHaveLength(1)
      expect(payload.dataSources).toHaveLength(1)
      expect(payload.processors).toHaveLength(1)
    })
    it('description 为 undefined 时传 undefined', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({ name: 'env', description: undefined }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      const payload = mocks.updateEnvironment.mock.calls[0][1] as Record<string, unknown>
      expect(payload.description).toBeUndefined()
    })
    it('过滤掉空 key 的变量', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: 'env', variables: [{ name: 'A', value: '1', hasValue: true }, { name: '', value: '2', hasValue: true }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      const payload = mocks.updateEnvironment.mock.calls[0][1] as Record<string, unknown>
      expect(payload.variables).toHaveLength(1)
    })
    it('过滤掉空 header 行', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: 'env', httpConfigs: [{ name: 'cfg', refName: 'r', baseUrl: 'http://x.com', headers: [{ key: 'h', value: 'v', enabled: true }, { key: '', value: '', enabled: true }] }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      const payload = mocks.updateEnvironment.mock.calls[0][1] as Record<string, unknown>
      expect((payload.httpConfigs as Record<string, unknown>[])[0].headers).toHaveLength(1)
    })
    it('variables value 为 undefined 时传 undefined', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: 'env', variables: [{ name: 'A', value: undefined, hasValue: false }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      const payload = mocks.updateEnvironment.mock.calls[0][1] as Record<string, unknown>
      expect((payload.variables as Record<string, unknown>[])[0].value).toBeUndefined()
    })
    it('variables description 为 undefined 时传 undefined', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail({
        name: 'env', variables: [{ name: 'A', value: 'v', description: undefined, hasValue: true }],
      }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      await state.saveAll()
      const payload = mocks.updateEnvironment.mock.calls[0][1] as Record<string, unknown>
      expect((payload.variables as Record<string, unknown>[])[0].description).toBeUndefined()
    })
  })

  describe('extractor picker', () => {
    it('openExtractorPicker 打开弹窗并重置 keyword', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.extractorPickerKeyword.value = 'old'
      state.openExtractorPicker()
      expect(state.extractorPickerVisible.value).toBe(true)
      expect(state.extractorPickerKeyword.value).toBe('')
    })
    it('openExtractorPicker 调用 loadExtractorAssets', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openExtractorPicker()
      await vi.waitFor(() => { expect(mocks.fetchComponents).toHaveBeenCalled() })
    })
    it('loadExtractorAssets 成功时更新 extractorPickerItems', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const items = [{ id: 'c1', name: 'ext1' }] as unknown as ApiComponentListItem[]
      mocks.fetchComponents.mockResolvedValue({ list: items })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openExtractorPicker()
      await vi.waitFor(() => { expect(state.extractorPickerItems.value).toEqual(items) })
    })
    it('loadExtractorAssets 失败时显示错误', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockRejectedValue(new Error('fail'))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openExtractorPicker()
      await vi.waitFor(() => { expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败') })
    })
    it('loadExtractorAssets 期间 loading 状态', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      let resolve!: (v: { list: ApiComponentListItem[] }) => void
      mocks.fetchComponents.mockReturnValue(new Promise((r) => { resolve = r }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openExtractorPicker()
      expect(state.extractorPickerLoading.value).toBe(true)
      resolve({ list: [] })
      await vi.waitFor(() => { expect(state.extractorPickerLoading.value).toBe(false) })
    })
    it('handleExtractorPicked 无选中处理器时不操作', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.handleExtractorPicked([{ id: 'c1', name: 'ext1' }] as unknown as ApiComponentListItem[])
      expect(mocks.extractorsFromComponents).not.toHaveBeenCalled()
    })
    it('loadExtractorAssets 使用当前 keyword', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openExtractorPicker()
      await vi.waitFor(() => { expect(mocks.fetchComponents).toHaveBeenCalled() })
      vi.clearAllMocks()
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      state.extractorPickerKeyword.value = 'myKeyword'
      await state.loadExtractorAssets()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'myKeyword' }))
    })
    it('keyword 为空白时不传 keyword 参数', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.extractorPickerKeyword.value = '   '
      await state.loadExtractorAssets()
      const call = mocks.fetchComponents.mock.calls[0][0] as Record<string, unknown>
      expect(call.keyword).toBeUndefined()
    })
  })

  describe('processor asset picker', () => {
    it('openProcessorAssetPicker 打开弹窗并重置 keyword', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.procAssetPickerKeyword.value = 'old'
      state.openProcessorAssetPicker('preprocessor')
      expect(state.procAssetPickerVisible.value).toBe(true)
      expect(state.procAssetPickerKeyword.value).toBe('')
    })
    it('openProcessorAssetPicker 设置类型', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('postprocessor')
      await vi.waitFor(() => {
        expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ type: 'postprocessor' }))
      })
    })
    it('loadProcAssets 成功时更新 items', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      mocks.fetchComponents.mockResolvedValue({ list: items })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerItems.value).toEqual(items) })
    })
    it('loadProcAssets 失败时显示错误', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockRejectedValue(new Error('fail'))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败') })
    })
    it('loadProcAssets 期间 loading 状态', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      let resolve!: (v: { list: ApiComponentListItem[] }) => void
      mocks.fetchComponents.mockReturnValue(new Promise((r) => { resolve = r }))
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      expect(state.procAssetPickerLoading.value).toBe(true)
      resolve({ list: [] })
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
    })
    it('loadProcAssets 使用当前 keyword', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(mocks.fetchComponents).toHaveBeenCalled() })
      vi.clearAllMocks()
      mocks.fetchComponents.mockResolvedValue({ list: [] })
      state.procAssetPickerKeyword.value = 'search'
      await state.loadProcAssets()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'search' }))
    })
    it('handleProcessorAssetPicked 空数组不添加', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.handleProcessorAssetPicked([])
      expect(state.processorRows.value).toHaveLength(0)
    })
    it('handleProcessorAssetPicked 添加处理器并选中最后一个', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'http', config: {} })
      mocks.defaultProcessorConfig.mockReturnValue({ testclass: 'http', config: {}, extractors: [] })
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(state.processorRows.value).toHaveLength(1)
      expect(state.processorRows.value[0].name).toBe('proc1')
      expect(state.activeProcId.value).toBe(state.processorRows.value[0].id)
    })
    it('handleProcessorAssetPicked 显示成功消息', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'http', config: {} })
      mocks.defaultProcessorConfig.mockReturnValue({ testclass: 'http', config: {}, extractors: [] })
      const items = [{ id: 'c1', name: 'proc1' }, { id: 'c2', name: 'proc2' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已引入 2 个处理器')
    })
    it('postprocessor 传 post 到 processorFromComponent', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'http', config: {} })
      mocks.defaultProcessorConfig.mockReturnValue({ testclass: 'http', config: {}, extractors: [] })
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('postprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(mocks.processorFromComponent).toHaveBeenCalledWith(items[0], 'post')
    })
    it('preprocessor 传 pre 到 processorFromComponent', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'http', config: {} })
      mocks.defaultProcessorConfig.mockReturnValue({ testclass: 'http', config: {}, extractors: [] })
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(mocks.processorFromComponent).toHaveBeenCalledWith(items[0], 'pre')
    })
    it('testclass 非 http/jdbc 时默认为 http', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'other', config: {} })
      mocks.defaultProcessorConfig.mockReturnValue({})
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(state.processorRows.value[0].config).toEqual(expect.objectContaining({ testclass: 'http' }))
    })
    it('config 非 Record 时用空对象', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'http', config: 'bad' })
      mocks.isRecord.mockReturnValue(false)
      mocks.defaultProcessorConfig.mockReturnValue({})
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(state.processorRows.value[0].config).toEqual(expect.objectContaining({ config: {} }))
    })
    it('extractors 非数组时用空数组', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.processorFromComponent.mockReturnValue({ testclass: 'http', extractors: 'bad' })
      mocks.defaultProcessorConfig.mockReturnValue({})
      const items = [{ id: 'c1', name: 'proc1' }] as unknown as ApiComponentListItem[]
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      await state.load()
      state.openProcessorAssetPicker('preprocessor')
      await vi.waitFor(() => { expect(state.procAssetPickerLoading.value).toBe(false) })
      state.handleProcessorAssetPicked(items)
      expect(state.processorRows.value[0].config).toEqual(expect.objectContaining({ extractors: [] }))
    })
  })

  describe('委托方法透传', () => {
    it('selectConfig 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const cfg = { id: '1' } as Parameters<typeof mockSelectConfig>[0]
      state.selectConfig(cfg)
      expect(mockSelectConfig).toHaveBeenCalledWith(cfg)
    })
    it('addHttpConfig 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      state.addHttpConfig()
      expect(mockAddHttpConfig).toHaveBeenCalled()
    })
    it('removeHttpConfig 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const cfg = { id: '1' } as Parameters<typeof mockRemoveHttpConfig>[0]
      state.removeHttpConfig(cfg)
      expect(mockRemoveHttpConfig).toHaveBeenCalledWith(cfg)
    })
    it('selectDs 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const ds = { id: '1' } as Parameters<typeof mockSelectDs>[0]
      state.selectDs(ds)
      expect(mockSelectDs).toHaveBeenCalledWith(ds)
    })
    it('addDataSource 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      state.addDataSource()
      expect(mockAddDataSource).toHaveBeenCalled()
    })
    it('removeDataSource 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const ds = { id: '1' } as Parameters<typeof mockRemoveDataSource>[0]
      state.removeDataSource(ds)
      expect(mockRemoveDataSource).toHaveBeenCalledWith(ds)
    })
    it('handleDsDriverChange 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      state.handleDsDriverChange('mysql')
      expect(mockHandleDsDriverChange).toHaveBeenCalledWith('mysql')
    })
    it('runHttpTest 透传', async () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const cfg = { id: '1' } as Parameters<typeof mockRunHttpTest>[0]
      await state.runHttpTest(cfg, 'env-1')
      expect(mockRunHttpTest).toHaveBeenCalledWith(cfg, 'env-1')
    })
    it('runDsTest 透传', async () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const ds = { id: '1' } as Parameters<typeof mockRunDsTest>[0]
      await state.runDsTest(ds, 'env-1')
      expect(mockRunDsTest).toHaveBeenCalledWith(ds, 'env-1')
    })
    it('selectProcessor 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const proc = { id: 'p1' } as Parameters<typeof mockSelectProcessor>[0]
      state.selectProcessor(proc)
      expect(mockSelectProcessor).toHaveBeenCalledWith(proc)
    })
    it('addProcessor 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      state.addProcessor('preprocessor')
      expect(mockAddProcessor).toHaveBeenCalledWith('preprocessor')
    })
    it('removeProcessor 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const proc = { id: 'p1' } as Parameters<typeof mockRemoveProcessor>[0]
      state.removeProcessor(proc)
      expect(mockRemoveProcessor).toHaveBeenCalledWith(proc)
    })
    it('moveProcessor 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      state.moveProcessor('preprocessor', 0, 1)
      expect(mockMoveProcessor).toHaveBeenCalledWith('preprocessor', 0, 1)
    })
    it('copyProcessor 透传', () => {
      const state = useEnvironmentDetailState({ environmentId: 'env-1', canEdit: true }, vi.fn())
      const proc = { id: 'p1' } as Parameters<typeof mockCopyProcessor>[0]
      state.copyProcessor(proc)
      expect(mockCopyProcessor).toHaveBeenCalledWith(proc)
    })
  })
})
