import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiEnvironmentDetail,
  ApiEnvironmentSaveReq,
  ApiProcessor,
  ApiProcessorType,
} from '@/types'
import { createEnvironment, fetchEnvironmentDetail, updateEnvironment } from '@/services/project/api-testing/environment'
import {
  emptyEnvironmentDetail,
  resolveEnvironmentError,
  validateVariableRow,
} from '@/composables/project/api-testing/environment/environmentsModel'
import { useEnvironmentHttpConfig, useEnvironmentDatasource } from './useEnvironmentConfig'
import type { HttpConfigForm, DsForm } from './useEnvironmentConfig'
import { useEnvironmentProcessors } from './useEnvironmentProcessors'
import { fetchComponents } from '@/services/project/api-testing/component'
import {
  extractorsFromComponents,
  isRecord,
  processorFromComponent,
  type ProcessorExtractor,
} from '@/composables/project/api-testing/processorFormModel'
import { defaultProcessorConfig } from '@/composables/project/api-testing/processorFormModel'

export interface VariableRow { id: string; key: string; value: string; description: string; enabled: boolean }

export interface EnvironmentDetailProps {
  /** 详情态必填；新建态无环境 ID */
  environmentId?: string
  canEdit: boolean
  /** create：新建态面板，由 seed 预填且不发详情请求 */
  mode?: 'detail' | 'create'
  seed?: ApiEnvironmentDetail
}

export type EnvironmentDetailEmit = {
  (event: 'changed'): void
  (event: 'created', id: string): void
  (event: 'dirty-change', value: boolean): void
}

export function useEnvironmentDetailState(props: EnvironmentDetailProps, emit: EnvironmentDetailEmit) {
  const createMode = computed(() => (props.mode ?? 'detail') === 'create')
  // 连接测试接口按环境 ID 试连，环境尚未创建时只能先创建再验证
  const canTestConnections = computed(() => !createMode.value && Boolean(props.environmentId))
  const loading = ref(false)
  const loadError = ref(false)
  const detail = ref<ApiEnvironmentDetail | null>(null)
  const saving = ref(false)

  const configForms = ref<HttpConfigForm[]>([])
  const dsForms = ref<DsForm[]>([])
  const variableRows = ref<VariableRow[]>([])
  const processorRows = ref<ApiProcessor[]>([])
  const activeTab = ref<'http' | 'variables' | 'datasources' | 'preprocessors' | 'postprocessors'>('http')
  // 以聚合载荷快照判定未保存：五处编辑口径不一，逐字段比对易漏（docs34 §1.6）
  const baselinePayload = ref('')

  let idSeq = 0
  function nextLocalId(): string { idSeq += 1; return `local-${idSeq}` }
  function nextProcSortOrder(): number { return processorRows.value.reduce((max, p) => Math.max(max, p.sortOrder ?? 0), 0) + 1 }

  function cloneHeaders(source: { headers?: { key: string; value: string }[] }): { key: string; value: string }[] {
    return (source.headers ?? []).map((h) => ({ ...h }))
  }

  function hydrate(next: ApiEnvironmentDetail) {
    detail.value = next
    configForms.value = next.httpConfigs.map((config) => ({
      ...config,
      id: nextLocalId(),
      headers: cloneHeaders(config),
    })) as HttpConfigForm[]
    dsForms.value = next.dataSources.map((ds) => ({ ...ds, id: nextLocalId() }))
    variableRows.value = next.variables
      .map((row) => ({ id: nextLocalId(), key: row.name, value: row.value ?? '', description: row.description ?? '', enabled: true }))
      .sort((a, b) => a.key.localeCompare(b.key))
    processorRows.value = next.processors.map((processor) => ({
      ...processor,
      id: nextLocalId(),
      config: isRecord(processor.config) ? processor.config : {},
    }))
    baselinePayload.value = JSON.stringify(buildAggregatePayload())
  }

  const {
    activeConfigId, activeConfig, orderedConfigForms, selectConfig, addHttpConfig, removeHttpConfig,
    testingHttpId, runHttpTest, httpConnResult,
  } = useEnvironmentHttpConfig(configForms, nextLocalId)
  const {
    activeDsId, activeDs, orderedDsForms, selectDs, selectedDsDriverOption, handleDsDriverChange,
    addDataSource, removeDataSource, testingDsId, runDsTest, dsConnResult,
  } = useEnvironmentDatasource(dsForms, nextLocalId)

  // 详情面板是「左侧列表 + 右侧内联表单」，载入后须选中首项（ordered 已把默认项置顶），
  // 否则首次进入 HTTP / 数据源页签时右侧表单空置
  function selectFirstOfEach() {
    activeConfigId.value = orderedConfigForms.value[0]?.id ?? ''
    activeDsId.value = orderedDsForms.value[0]?.id ?? ''
  }

  const {
    activeProcId, procExpandedId, procDraft, procDraftMode, selectedProcessor,
    preProcCount, postProcCount, procList, procElement, removeProcessor,
    moveProcessor, copyProcessor, toggleProcDetail, startProcEdit, startProcAdd,
    cancelProcDraft, commitProcDraft, procTags, procDisplayName, procDetail, applyDefaultProcRef,
  } = useEnvironmentProcessors(processorRows, orderedConfigForms, orderedDsForms, nextLocalId, nextProcSortOrder)

  const variableCount = computed(() => variableRows.value.filter((row) => row.key.trim()).length)

  const dirty = computed(() => {
    if (!detail.value) return false
    return JSON.stringify(buildAggregatePayload()) !== baselinePayload.value
  })

  // 页面据此决定离开新建态前是否需要先确认放弃（交互设计 34 §1.6）
  watch(dirty, (value) => emit('dirty-change', value), { immediate: true })

  watch([selectedProcessor, orderedConfigForms, orderedDsForms], ([processor]) => {
    applyDefaultProcRef(processor)
  })

  // 类型切换在 ProcessorForm 内部完成，只能观察结果补默认引用（docs34 §1.3）
  watch(
    () => {
      const processor = selectedProcessor.value
      if (!processor) return ''
      const klass = procElement(processor).testclass
      return klass === 'http' || klass === 'jdbc' ? klass : ''
    },
    () => applyDefaultProcRef(selectedProcessor.value),
  )

  watch(activeTab, (tab) => {
    // 展开明细与草稿表单不跨页签残留，切走即收起（docs34 §1.3 同一时刻仅一处展开）
    procExpandedId.value = ''
    if (tab !== 'preprocessors' && tab !== 'postprocessors') {
      cancelProcDraft()
      activeProcId.value = ''
      return
    }
    const type = tab === 'preprocessors' ? 'preprocessor' : 'postprocessor'
    if (procDraft.value && procDraft.value.processorType !== type) cancelProcDraft()
    if (!procList(type).some((p) => p.id === activeProcId.value)) {
      activeProcId.value = procList(type)[0]?.id ?? ''
    }
  })

  async function load() {
    // 新建态以 seed 为初始表单，不发详情请求
    if (createMode.value) {
      hydrate(props.seed ?? emptyEnvironmentDetail(0))
      selectFirstOfEach()
      return
    }
    loading.value = true
    loadError.value = false
    try {
      hydrate(await fetchEnvironmentDetail(props.environmentId ?? ''))
      selectFirstOfEach()
    } catch (err) {
      loadError.value = true
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      loading.value = false
    }
  }

  onMounted(load)

  const extractorPickerVisible = ref(false)
  const extractorPickerLoading = ref(false)
  const extractorPickerItems = ref<ApiComponentListItem[]>([])
  const extractorPickerKeyword = ref('')

  async function loadExtractorAssets(): Promise<void> {
    extractorPickerLoading.value = true
    try {
      const result = await fetchComponents({
        type: 'extractor',
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: extractorPickerKeyword.value.trim() || undefined,
      })
      extractorPickerItems.value = result.list
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      extractorPickerLoading.value = false
    }
  }

  function openExtractorPicker() {
    extractorPickerVisible.value = true
    extractorPickerKeyword.value = ''
    void loadExtractorAssets()
  }

  function handleExtractorPicked(rows: ApiComponentListItem[]) {
    const processor = selectedProcessor.value
    if (!processor) return
    const incoming = extractorsFromComponents(rows)
    if (incoming.length === 0) return
    const element = procElement(processor)
    const existing = Array.isArray(element.extractors) ? element.extractors as ProcessorExtractor[] : []
    processor.config = { ...element, extractors: [...existing, ...incoming] }
    ElMessage.success(`已引入 ${incoming.length} 个提取器`)
  }

  const procAssetPickerVisible = ref(false)
  const procAssetPickerLoading = ref(false)
  const procAssetPickerItems = ref<ApiComponentListItem[]>([])
  const procAssetPickerKeyword = ref('')
  const procAssetPickerType = ref<ApiProcessorType>('preprocessor')

  async function loadProcAssets(): Promise<void> {
    procAssetPickerLoading.value = true
    try {
      const result = await fetchComponents({
        type: procAssetPickerType.value,
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: procAssetPickerKeyword.value.trim() || undefined,
      })
      procAssetPickerItems.value = result.list
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      procAssetPickerLoading.value = false
    }
  }

  function openProcessorAssetPicker(type: ApiProcessorType) {
    procAssetPickerType.value = type
    procAssetPickerKeyword.value = ''
    procAssetPickerVisible.value = true
    void loadProcAssets()
  }

  function handleProcessorAssetPicked(rows: ApiComponentListItem[]) {
    rows.forEach((item) => {
      const element = processorFromComponent(item, procAssetPickerType.value === 'postprocessor' ? 'post' : 'pre')
      const testclass = element.testclass === 'http' || element.testclass === 'jdbc' ? element.testclass : 'http'
      const sortOrder = nextProcSortOrder()
      processorRows.value.push({
        id: nextLocalId(),
        processorType: procAssetPickerType.value,
        name: item.name,
        config: {
          ...defaultProcessorConfig(),
          testclass,
          config: isRecord(element.config) ? element.config : {},
          extractors: Array.isArray(element.extractors) ? element.extractors : [],
          sortOrder,
        },
        enabled: true,
        sortOrder,
      })
    })
    const last = processorRows.value[processorRows.value.length - 1]
    if (last && last.id) activeProcId.value = last.id
    ElMessage.success(`已引入 ${rows.length} 个处理器`)
  }

  function validateAll(): string | null {
    const environment = detail.value
    if (!environment?.name.trim()) return '环境名称不能为空'
    for (const config of configForms.value) {
      if (!config.name.trim()) return '存在未命名的 HTTP 配置'
      if (!config.refName?.trim()) return `HTTP 配置「${config.name}」缺少引用名`
      if (!config.baseUrl?.trim()) return `HTTP 配置「${config.name}」缺少 Base URL`
    }
    for (const ds of dsForms.value) {
      if (!ds.name.trim()) return '存在未命名的数据源'
      if (!ds.refName?.trim()) return `数据源「${ds.name}」缺少引用名`
      if (!ds.driver?.trim()) return `数据源「${ds.name}」未选择驱动`
      if (!ds.url?.trim()) return `数据源「${ds.name}」缺少连接 URL`
    }
    const namedVariables = variableRows.value.filter((row) => row.key)
    for (let index = 0; index < namedVariables.length; index += 1) {
      const row = namedVariables[index]
      const others = new Set(namedVariables.map((other) => other.key))
      others.delete(row.key)
      const error = validateVariableRow({ name: row.key, value: row.value }, others)
      if (error) return `变量 ${row.key || '(未命名)'}：${error}`
    }
    for (const processor of processorRows.value) {
      if (!processor.name?.trim()) return '存在未命名的处理器'
    }
    return null
  }

  function buildAggregatePayload(): ApiEnvironmentSaveReq {
    const environment = detail.value as ApiEnvironmentDetail
    const httpConfigs = configForms.value.map((config) => ({
      name: config.name.trim(),
      refName: config.refName || undefined,
      baseUrl: config.baseUrl,
      isDefault: !!config.isDefault,
      headers: (config.headers ?? []).filter((header) => header.key.trim() || header.value.trim()),
    }))
    const variables = variableRows.value
      .filter((row) => row.key.trim())
      .map((row) => ({ name: row.key.trim(), value: row.value || undefined, description: row.description || undefined }))
    const dataSources = dsForms.value.map((ds) => ({
      name: ds.name.trim(),
      refName: ds.refName || undefined,
      driver: ds.driver,
      url: ds.url,
      connectionProperties: ds.connectionProperties,
      isDefault: !!ds.isDefault,
      maxPoolSize: ds.maxPoolSize,
    }))
    const processors = processorRows.value.map((processor) => ({
      processorType: processor.processorType,
      name: processor.name,
      config: processor.config,
      sortOrder: processor.sortOrder,
      enabled: processor.enabled,
    }))
    return {
      name: environment.name.trim(),
      description: environment.description || undefined,
      isDefault: environment.isDefault,
      sortOrder: environment.sortOrder,
      httpConfigs,
      variables,
      dataSources,
      processors,
    }
  }

  async function saveAll() {
    if (!props.canEdit) return
    if (!createMode.value && !props.environmentId) return
    const error = validateAll()
    if (error) { ElMessage.warning(error); return }
    saving.value = true
    try {
      const payload = buildAggregatePayload()
      if (createMode.value) {
        // 新建与复制共用创建接口，副本不携带源环境 ID（详细设计 1.3）
        const created = await createEnvironment(payload)
        ElMessage.success('环境已创建')
        emit('created', created.id)
      } else if (props.environmentId) {
        await updateEnvironment(props.environmentId, payload)
        baselinePayload.value = JSON.stringify(payload)
        ElMessage.success('已保存')
        emit('changed')
      }
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      saving.value = false
    }
  }

  return {
    loading, loadError, detail, saving, dirty, createMode, canTestConnections,
    configForms, dsForms, variableRows, processorRows, activeTab,
    activeConfigId, activeConfig, orderedConfigForms, selectConfig, addHttpConfig, removeHttpConfig, testingHttpId, runHttpTest, httpConnResult,
    activeDsId, activeDs, orderedDsForms, selectDs, selectedDsDriverOption, handleDsDriverChange, addDataSource, removeDataSource, testingDsId, runDsTest, dsConnResult,
    activeProcId, procExpandedId, procDraft, procDraftMode, preProcCount, postProcCount,
    procList, procElement, removeProcessor,
    moveProcessor, copyProcessor, toggleProcDetail, startProcEdit, startProcAdd,
    cancelProcDraft, commitProcDraft, procTags, procDisplayName, procDetail,
    variableCount, load, saveAll,
    extractorPickerVisible, extractorPickerLoading, extractorPickerItems, extractorPickerKeyword,
    openExtractorPicker, handleExtractorPicked, loadExtractorAssets,
    procAssetPickerVisible, procAssetPickerLoading, procAssetPickerItems, procAssetPickerKeyword,
    openProcessorAssetPicker, handleProcessorAssetPicked, loadProcAssets,
  }
}
