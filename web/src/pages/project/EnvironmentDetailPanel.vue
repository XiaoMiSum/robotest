<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiDataSourcePayload,
  ApiDebugKeyValue,
  ApiEnvironmentDetail,
  ApiEnvironmentSaveReq,
  ApiHeaderItem,
  ApiHttpConfigPayload,
  ApiProcessor,
  ApiProcessorType,
} from '@/types'
import { fetchEnvironmentDetail, testDataSourceConfig, testHttpConfig, updateEnvironment } from '@/services/apiEnvironment'
import {
  createEmptyHttpConfig,
  DRIVER_OPTIONS,
  resolveEnvironmentError,
  validateVariableRow,
} from './environmentsModel'
import KeyValueTable from './debug/KeyValueTable.vue'
import ProcessorForm from '@/components/api-testing/ProcessorForm.vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import {
  defaultProcessorConfig,
  extractorsFromComponents,
  isRecord,
  processorFromComponent,
  processorSummaryTag,
  type ProcessorExtractor,
} from '@/components/api-testing/processorFormModel'
import { fetchComponents } from '@/services/apiComponent'

const props = defineProps<{ environmentId: string; canEdit: boolean }>()
const emit = defineEmits<{ changed: [] }>()

// ==================== 详情加载（聚合编辑：四类子资源全部就地编辑，一次「保存全部」提交） ====================

const loading = ref(false)
const loadError = ref(false)
const detail = ref<ApiEnvironmentDetail | null>(null)

interface ConfigForm extends ApiHttpConfigPayload {
  id: string
  headers: ApiHeaderItem[]
}
interface DsForm extends ApiDataSourcePayload {
  id: string
}

/** 变量编辑行：key=变量名，enabled 恒 true（数据模型无启用语义，仅适配 KeyValueTable） */
interface VariableRow extends ApiDebugKeyValue {
  id: string
}

const configForms = ref<ConfigForm[]>([])
const dsForms = ref<DsForm[]>([])
const variableRows = ref<VariableRow[]>([])
const processorRows = ref<ApiProcessor[]>([])
const activeTab = ref<'http' | 'variables' | 'datasources' | 'preprocessors' | 'postprocessors'>('http')
const activeConfigId = ref('')
const activeDsId = ref('')

let idSeq = 0

function nextLocalId(): string {
  idSeq += 1
  return `local-${idSeq}`
}

function cloneHeaders(source: ApiHttpConfigPayload): ApiHeaderItem[] {
  return (source.headers ?? []).map((header) => ({ ...header }))
}

function hydrate(next: ApiEnvironmentDetail) {
  detail.value = next
  // 行标识仅用于本地编辑定位，保存时提交的是子资源数据本身，含 id 与否无影响
  configForms.value = next.httpConfigs.map((config) => ({
    ...config,
    id: nextLocalId(),
    headers: cloneHeaders(config),
  }))
  dsForms.value = next.dataSources.map((ds) => ({ ...ds, id: nextLocalId() }))
  variableRows.value = next.variables
    .map((row) => ({ id: nextLocalId(), key: row.name, value: row.value ?? '', description: row.description ?? '', enabled: true }))
    .sort((a, b) => a.key.localeCompare(b.key))
  processorRows.value = next.processors.map((processor) => ({
    ...processor,
    id: nextLocalId(),
    // config 即 Ryze 元素，本地编辑要求恒为对象（v-model 绑定目标），缺失时按空元素处理
    config: isRecord(processor.config) ? processor.config : {},
  }))
  if (!configForms.value.some((config) => config.id === activeConfigId.value)) {
    activeConfigId.value = configForms.value[0]?.id ?? ''
  }
  if (!dsForms.value.some((form) => form.id === activeDsId.value)) {
    activeDsId.value = dsForms.value[0]?.id ?? ''
  }
  if (!processorRows.value.some((processor) => processor.id === activeProcId.value)) {
    activeProcId.value = ''
  }
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    hydrate(await fetchEnvironmentDetail(props.environmentId))
  } catch (err) {
    loadError.value = true
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ==================== HTTP 配置（就地编辑） ====================

const activeConfig = computed(() => configForms.value.find((config) => config.id === activeConfigId.value))

// 默认配置置顶，其余保持创建顺序（交互设计 2.3）
const orderedConfigForms = computed(() =>
  [...configForms.value].sort((a, b) => Number(b.isDefault ?? false) - Number(a.isDefault ?? false)),
)

function selectConfig(config: ConfigForm) {
  activeConfigId.value = config.id
}

function addHttpConfig() {
  const source = createEmptyHttpConfig(configForms.value.length + 1)
  const next: ConfigForm = {
    id: nextLocalId(),
    name: source.name,
    refName: source.refName,
    baseUrl: source.baseUrl,
    isDefault: source.isDefault,
    headers: cloneHeaders(source),
  }
  configForms.value.push(next)
  activeConfigId.value = next.id
}

function removeHttpConfig(form: ConfigForm) {
  configForms.value = configForms.value.filter((config) => config !== form)
  if (activeConfigId.value === form.id) {
    activeConfigId.value = configForms.value[0]?.id ?? ''
  }
}

const testingHttpId = ref('')

async function runHttpTest(form: ConfigForm) {
  if (!form.baseUrl?.trim()) {
    ElMessage.warning('请先填写 Base URL 再测试连接')
    return
  }
  testingHttpId.value = form.id
  try {
    // 免保存试连：直接用表单当前值，新建或未保存的修改无需先落库
    const result = await testHttpConfig(props.environmentId, { baseUrl: form.baseUrl.trim(), refName: form.refName })
    if (result.success) {
      ElMessage.success(`连接成功：状态码 ${result.statusCode ?? '-'}，耗时 ${result.durationMs ?? '-'}ms`)
    } else {
      ElMessage.error(result.message || '连接失败')
    }
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    testingHttpId.value = ''
  }
}

// ==================== 全局变量（KeyValueTable 就地编辑，聚合提交） ====================

/** tab 徽标按有效变量计数，不把 KeyValueTable 自动补的末行空行算入 */
const variableCount = computed(() => variableRows.value.filter((row) => row.key.trim()).length)

// ==================== 数据源（就地编辑，交互同 HTTP：左列表 + 右内联表单） ====================

const activeDs = computed(() => dsForms.value.find((form) => form.id === activeDsId.value))

// 默认数据源置顶，其余保持创建顺序（交互设计 2.3）
const orderedDsForms = computed(() =>
  [...dsForms.value].sort((a, b) => Number(b.isDefault ?? false) - Number(a.isDefault ?? false)),
)

function selectDs(form: DsForm) {
  activeDsId.value = form.id
}

const selectedDsDriverOption = computed(() => DRIVER_OPTIONS.find((option) => option.driver === activeDs.value?.driver))

function handleDsDriverChange(driver: string) {
  // 切换驱动时若尚未填写 URL，自动填充该驱动的示例，避免空 URL 误保存
  const option = DRIVER_OPTIONS.find((item) => item.driver === driver)
  if (option && activeDs.value && !activeDs.value.url) activeDs.value.url = option.urlExample
}

function addDataSource() {
  const next: DsForm = {
    id: nextLocalId(),
    name: '',
    refName: `db_${dsForms.value.length + 1}`,
    driver: DRIVER_OPTIONS[0]?.driver ?? '',
    url: '',
    isDefault: false,
  }
  dsForms.value.push(next)
  activeDsId.value = next.id
}

function removeDataSource(form: DsForm) {
  dsForms.value = dsForms.value.filter((item) => item !== form)
  if (activeDsId.value === form.id) {
    activeDsId.value = dsForms.value[0]?.id ?? ''
  }
}

const testingDsId = ref('')

async function runDsTest(form: DsForm) {
  if (!form.url?.trim()) {
    ElMessage.warning('请先填写 URL 再测试连接')
    return
  }
  testingDsId.value = form.id
  try {
    const result = await testDataSourceConfig(props.environmentId, {
      driver: form.driver,
      url: form.url.trim(),
      connectionProperties: form.connectionProperties,
    })
    if (result.success) {
      ElMessage.success(`连接成功${result.databaseVersion ? `：${result.databaseVersion}` : ''}`)
    } else {
      ElMessage.error(result.message || '连接失败')
    }
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    testingDsId.value = ''
  }
}

// ==================== 处理器（左列表 + 右内联明细，交互同测试场景 3.5） ====================

const activeProcId = ref('')

/** 某类型处理器列表（数组顺序即执行顺序，与 sortOrder 一致） */
function procList(type: ApiProcessorType): ApiProcessor[] {
  return processorRows.value.filter((processor) => processor.processorType === type)
}

const preProcCount = computed(() => procList('preprocessor').length)
const postProcCount = computed(() => procList('postprocessor').length)

const selectedProcessor = computed<ApiProcessor | null>(
  () => processorRows.value.find((processor) => processor.id === activeProcId.value) ?? null,
)

/** 处理器元素：config 即 Ryze 元素（testclass/config/extractors），读取统一回退空对象 */
function procElement(processor: ApiProcessor): Record<string, unknown> {
  return isRecord(processor.config) ? processor.config : {}
}

/** 修正选中：当前 pane 无效时切到该类型第一个，避免跨类型失配 */
function fixActiveProc(type: ApiProcessorType) {
  const list = procList(type)
  if (!list.some((processor) => processor.id === activeProcId.value)) {
    activeProcId.value = list[0]?.id ?? ''
  }
}

watch(activeTab, (tab) => {
  if (tab !== 'preprocessors' && tab !== 'postprocessors') {
    activeProcId.value = ''
    return
  }
  fixActiveProc(tab === 'preprocessors' ? 'preprocessor' : 'postprocessor')
})

function selectProcessor(processor: ApiProcessor) {
  activeProcId.value = processor.id ?? ''
}

/** 新建处理器的执行序号：当前最大排序号 +1，保持执行顺序递增 */
function nextProcSortOrder(): number {
  return processorRows.value.reduce((max, processor) => Math.max(max, processor.sortOrder ?? 0), 0) + 1
}

function addProcessor(type: ApiProcessorType) {
  const sortOrder = nextProcSortOrder()
  const processor: ApiProcessor = {
    id: nextLocalId(),
    processorType: type,
    name: '',
    // 默认 HTTP 类型（同测试场景），config 合入启用/排序 overlay 保持落库结构一致
    config: { ...defaultProcessorConfig(), sortOrder, testclass: 'http', config: {}, extractors: [] },
    enabled: true,
    sortOrder,
  }
  processorRows.value.push(processor)
  activeProcId.value = processor.id ?? ''
}

function removeProcessor(processor: ApiProcessor) {
  processorRows.value = processorRows.value.filter((item) => item !== processor)
  fixActiveProc(processor.processorType)
}

/** 上移/下移：数组顺序即执行顺序，position 为该类型列表内下标；同步互换 sortOrder 保持落库一致 */
function moveProcessor(type: ApiProcessorType, position: number, dir: -1 | 1) {
  const list = procList(type)
  const from = list[position]
  const to = list[position + dir]
  if (!from || !to) return
  const fromOrder = from.sortOrder ?? 0
  const toOrder = to.sortOrder ?? 0
  from.sortOrder = toOrder
  to.sortOrder = fromOrder
  const arr = processorRows.value
  const fromIdx = arr.indexOf(from)
  const toIdx = arr.indexOf(to)
  ;[arr[fromIdx], arr[toIdx]] = [arr[toIdx], arr[fromIdx]]
  activeProcId.value = to.id ?? ''
}

/** 复制：深拷贝元素并分配新行 id，紧随源行插入（同场景复制语义） */
function copyProcessor(processor: ApiProcessor) {
  const index = processorRows.value.indexOf(processor)
  if (index < 0) return
  const element = JSON.parse(JSON.stringify(procElement(processor))) as Record<string, unknown>
  const copy: ApiProcessor = {
    ...processor,
    id: nextLocalId(),
    config: element,
  }
  processorRows.value.splice(index + 1, 0, copy)
  activeProcId.value = copy.id ?? ''
}

/** 切换处理器类型（http/jdbc）：仅改元素 testclass，ProcessorForm 深监听自动重解析配置 */
const procTestclass = computed<string>({
  get: () => {
    const processor = selectedProcessor.value
    if (!processor) return ''
    const klass = procElement(processor).testclass
    return klass === 'http' || klass === 'jdbc' ? klass : ''
  },
  set: (value: string) => {
    const processor = selectedProcessor.value
    if (!processor) return
    processor.config = { ...procElement(processor), testclass: value }
    // 类型切换后未设置过引用，按新类型下拉的默认标记预选默认值
    applyDefaultProcRef(processor)
  },
})

/** 头部 ref 下拉：取本环境自身的 http 配置 / 数据源（编辑中未保存的引用名同样可选） */
const procHttpRefOptions = computed(() =>
  orderedConfigForms.value.map((form) => ({ value: form.refName ?? '', label: form.refName ? `${form.name}（${form.refName}）` : form.name })),
)
const procDsRefOptions = computed(() =>
  orderedDsForms.value.map((form) => ({ value: form.refName ?? '', label: form.refName ? `${form.name}（${form.refName}）` : form.name })),
)

/** 环境引用写回元素 config：http → config.ref，jdbc → config.datasource；ProcessorForm 深监听 modelValue 自动重解析同步 */
const procHttpRef = computed<string>({
  get: () => {
    const processor = selectedProcessor.value
    if (!processor) return ''
    const element = procElement(processor)
    if (element.testclass !== 'http' || !isRecord(element.config)) return ''
    return typeof element.config.ref === 'string' ? element.config.ref : ''
  },
  set: (value: string) => {
    const processor = selectedProcessor.value
    if (!processor) return
    const element = procElement(processor)
    processor.config = { ...element, config: { ...(isRecord(element.config) ? element.config : {}), ref: value } }
  },
})

const procDsRef = computed<string>({
  get: () => {
    const processor = selectedProcessor.value
    if (!processor) return ''
    const element = procElement(processor)
    if (element.testclass !== 'jdbc' || !isRecord(element.config)) return ''
    return typeof element.config.datasource === 'string' ? element.config.datasource : ''
  },
  set: (value: string) => {
    const processor = selectedProcessor.value
    if (!processor) return
    const element = procElement(processor)
    processor.config = { ...element, config: { ...(isRecord(element.config) ? element.config : {}), datasource: value } }
  },
})

/** 引用预填：处理器未显式设过引用时，按下拉选项中「是否默认」标记补默认值（处理器已显式设置则不动，避免覆盖用户选择） */
function applyDefaultProcRef(processor: ApiProcessor | null) {
  if (!processor) return
  const element = procElement(processor)
  const config = isRecord(element.config) ? element.config : {}
  if (element.testclass === 'http' && typeof config.ref !== 'string') {
    const def = orderedConfigForms.value.find((form) => form.isDefault)
    if (def?.refName) processor.config = { ...element, config: { ...config, ref: def.refName } }
    return
  }
  if (element.testclass === 'jdbc' && typeof config.datasource !== 'string') {
    const def = orderedDsForms.value.find((form) => form.isDefault)
    if (def?.refName) processor.config = { ...element, config: { ...config, datasource: def.refName } }
  }
}

// 切换处理器 / 环境 HTTP 配置、数据源列表变更时，为未设引用的当前处理器预填默认引用
watch([selectedProcessor, orderedConfigForms, orderedDsForms], ([processor]) => {
  applyDefaultProcRef(processor)
})

/** 左侧卡片标签：`[HTTP]/[JDBC]` 类型 + 方法 / SQL 摘要，同场景处理器卡片 */
function procTags(processor: ApiProcessor): { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] {
  const element = procElement(processor)
  const tags: { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] = []
  const klass = typeof element.testclass === 'string' ? element.testclass : ''
  if (klass === 'http' || klass === 'jdbc') tags.push({ text: klass.toUpperCase(), type: 'info' })
  const summary = processorSummaryTag(element)
  if (summary) tags.push(summary)
  return tags
}

/** 左列表展示名：未命名时回退「处理器 N」 */
function procDisplayName(processor: ApiProcessor, index: number): string {
  return processor.name.trim() ? processor.name : `处理器 ${index + 1}`
}

// ==================== 处理器 / 提取器：从公共组件引入 ====================

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
    // 资产无类型时默认 HTTP，避免引入后无从编辑；config 仅落 Ryze 键（不携带组件级 type）
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

// ==================== 聚合保存 ====================

const saving = ref(false)

/** 保存前校验：环境名 + HTTP/数据源必填 + 变量名合法且唯一 + 处理器名 */
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
    if (error) {
      return `变量 ${row.key || '(未命名)'}：${error}`
    }
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
  const error = validateAll()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    await updateEnvironment(props.environmentId, buildAggregatePayload())
    ElMessage.success('已保存')
    emit('changed')
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="env-detail">
    <div v-if="loadError" class="env-detail__empty">
      <p>环境详情加载失败</p>
      <el-button @click="load">重试</el-button>
    </div>

    <template v-else-if="detail">
      <div class="env-detail__head">
        <span class="env-detail__name">{{ detail.name }}</span>
        <el-button v-if="canEdit" type="primary" :loading="saving" @click="saveAll">保存全部</el-button>
      </div>
      <el-tabs v-model="activeTab" class="env-detail__tabs">
        <!-- ============ HTTP 默认配置 ============ -->
        <el-tab-pane :label="`HTTP (${configForms.length})`" name="http">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in orderedConfigForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeConfigId }"
                @click="selectConfig(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit" class="env-detail__config-add">
                <el-button link type="primary" @click="addHttpConfig"><el-icon><Plus /></el-icon>新增配置</el-button>
              </li>
            </ul>

            <div v-if="activeConfig" class="env-detail__config-form">
              <el-form label-width="110px" :disabled="!canEdit">
                <el-form-item label="名称" required>
                  <el-input v-model="activeConfig.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名" required>
                  <el-input v-model="activeConfig.refName" placeholder="场景中通过该名引用此配置" />
                </el-form-item>
                <el-form-item label="Base URL" required>
                  <el-input v-model="activeConfig.baseUrl" placeholder="https://api.example.com" />
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="activeConfig.isDefault" />
                  <span class="env-detail__hint">同一环境内至多一个默认 HTTP 配置</span>
                </el-form-item>
              </el-form>

              <div class="env-detail__headers">
                <div class="env-detail__section-title">请求头</div>
                <KeyValueTable v-model:entries="activeConfig.headers" placeholder-key="Header" :disabled="!canEdit" />
              </div>

              <div class="env-detail__config-footer">
                <el-button :loading="testingHttpId === activeConfig.id" @click="runHttpTest(activeConfig)">
                  连接测试
                </el-button>
                <el-button v-if="canEdit" type="danger" plain @click="removeHttpConfig(activeConfig)">
                  删除配置
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <!-- ============ 全局变量 ============ -->
        <el-tab-pane :label="`变量 (${variableCount})`" name="variables">
          <KeyValueTable
            v-model:entries="variableRows"
            placeholder-key="变量名"
            show-description
            :show-enabled="false"
            :disabled="!canEdit"
          />
          <p class="env-detail__syntax-tip">
            引用语法：<code>${变量名}</code>，如 <code>${BASE_URL}</code>
          </p>
        </el-tab-pane>

        <!-- ============ 数据源（交互同 HTTP：左列表 + 右内联表单） ============ -->
        <el-tab-pane :label="`数据源 (${dsForms.length})`" name="datasources">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in orderedDsForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeDsId }"
                @click="selectDs(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit" class="env-detail__config-add">
                <el-button link type="primary" @click="addDataSource"><el-icon><Plus /></el-icon>新增数据源</el-button>
              </li>
            </ul>

            <div v-if="activeDs" class="env-detail__config-form">
              <el-form label-width="110px" :disabled="!canEdit">
                <el-form-item label="名称" required>
                  <el-input v-model="activeDs.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名" required>
                  <el-input v-model="activeDs.refName" placeholder="场景中通过该名引用此数据源" />
                </el-form-item>
                <el-form-item label="驱动" required>
                  <el-select v-model="activeDs.driver" @change="handleDsDriverChange">
                    <el-option
                      v-for="option in DRIVER_OPTIONS"
                      :key="option.label"
                      :label="option.label"
                      :value="option.driver"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="URL" required>
                  <el-input
                    v-model="activeDs.url"
                    type="textarea"
                    :rows="2"
                    :placeholder="selectedDsDriverOption?.urlExample"
                  />
                  <span class="env-detail__hint">用户名/密码通过 URL 设置</span>
                </el-form-item>
                <el-form-item label="连接池上限">
                  <el-input-number v-model="activeDs.maxPoolSize" :min="1" :max="100" />
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="activeDs.isDefault" />
                  <span class="env-detail__hint">同一环境内至多一个默认数据源</span>
                </el-form-item>
              </el-form>

              <div class="env-detail__config-footer">
                <el-button :loading="testingDsId === activeDs.id" @click="runDsTest(activeDs)">
                  连接测试
                </el-button>
                <el-button v-if="canEdit" type="danger" plain @click="removeDataSource(activeDs)">
                  删除数据源
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 前置处理器（左列表 + 右内联明细，交互同测试场景 3.5） ============ -->
        <el-tab-pane :label="`前置处理器 (${preProcCount})`" name="preprocessors">
          <div class="env-detail__proc-split">
            <div class="env-detail__proc-left">
              <div class="env-detail__proc-head">
                <span>前置处理器</span>
                <div class="env-detail__proc-actions">
                  <el-button link type="primary" size="small" :disabled="!canEdit" @click="openProcessorAssetPicker('preprocessor')">从公共组件引入</el-button>
                  <el-button link type="primary" size="small" :disabled="!canEdit" @click="addProcessor('preprocessor')">+ 添加处理器</el-button>
                </div>
              </div>
              <template v-for="(processor, i) in procList('preprocessor')" :key="processor.id">
                <div
                  class="env-detail__proc-item"
                  :class="{ 'is-selected': processor.id === activeProcId, 'is-disabled': !processor.enabled }"
                  @click="selectProcessor(processor)"
                >
                  <div class="env-detail__proc-item-header">
                    <span class="env-detail__proc-index">{{ i + 1 }}</span>
                    <el-tag v-for="t in procTags(processor)" :key="t.text" size="small" :type="t.type">{{ t.text }}</el-tag>
                    <div class="env-detail__proc-header-spacer" />
                    <el-switch v-model="processor.enabled" size="small" :disabled="!canEdit" @click.stop />
                    <el-dropdown v-if="canEdit" trigger="click" @click.stop>
                      <el-button link size="small">操作</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item @click="selectProcessor(processor)">编辑</el-dropdown-item>
                          <el-dropdown-item :disabled="i === 0" @click="moveProcessor('preprocessor', i, -1)">上移</el-dropdown-item>
                          <el-dropdown-item :disabled="i === preProcCount - 1" @click="moveProcessor('preprocessor', i, 1)">下移</el-dropdown-item>
                          <el-dropdown-item divided @click="copyProcessor(processor)">复制</el-dropdown-item>
                          <el-dropdown-item divided style="color: var(--el-color-danger)" @click="removeProcessor(processor)">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                  <div class="env-detail__proc-item-name">{{ procDisplayName(processor, i) }}</div>
                </div>
              </template>
              <el-empty v-if="preProcCount === 0" description="暂无前置处理器" :image-size="60" />
              <el-button v-if="preProcCount === 0 && canEdit" size="small" class="env-detail__proc-add" @click="addProcessor('preprocessor')">
                <el-icon><Plus /></el-icon> 添加处理器
              </el-button>
            </div>

            <div class="env-detail__proc-right">
              <template v-if="selectedProcessor">
                <div class="env-detail__proc-inline">
                  <header class="env-detail__proc-inline-head">
                    <el-input v-model="selectedProcessor.name" placeholder="处理器名称" class="env-detail__proc-inline-name" :disabled="!canEdit" />
                    <el-switch v-model="selectedProcessor.enabled" :disabled="!canEdit" active-text="启用" />
                    <el-divider direction="vertical" />
                    <el-radio-group v-model="procTestclass" :disabled="!canEdit" size="small">
                      <el-radio-button value="http">HTTP</el-radio-button>
                      <el-radio-button value="jdbc">JDBC</el-radio-button>
                    </el-radio-group>
                    <el-select
                      v-if="procTestclass === 'http'"
                      v-model="procHttpRef"
                      placeholder="选择环境 HTTP 配置"
                      filterable
                      :disabled="!canEdit"
                      class="env-detail__proc-inline-ref"
                    >
                      <el-option v-for="opt in procHttpRefOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                    </el-select>
                    <el-select
                      v-else-if="procTestclass === 'jdbc'"
                      v-model="procDsRef"
                      placeholder="选择环境数据源"
                      filterable
                      :disabled="!canEdit"
                      class="env-detail__proc-inline-ref"
                    >
                      <el-option v-for="opt in procDsRefOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                    </el-select>
                  </header>
                  <div class="env-detail__proc-inline-body" :class="{ 'is-readonly': !canEdit }">
                    <ProcessorForm
                      v-model="selectedProcessor.config"
                      :http-options="configForms"
                      :ds-options="dsForms"
                      :show-type-select="false"
                      :show-ref-select="false"
                      @import-extractors="openExtractorPicker"
                    />
                  </div>
                </div>
              </template>
              <div v-else class="env-detail__right-empty">
                <p>选中左侧处理器后在右侧编辑</p>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 后置处理器（左列表 + 右内联明细） ============ -->
        <el-tab-pane :label="`后置处理器 (${postProcCount})`" name="postprocessors">
          <div class="env-detail__proc-split">
            <div class="env-detail__proc-left">
              <div class="env-detail__proc-head">
                <span>后置处理器</span>
                <div class="env-detail__proc-actions">
                  <el-button link type="primary" size="small" :disabled="!canEdit" @click="openProcessorAssetPicker('postprocessor')">从公共组件引入</el-button>
                  <el-button link type="primary" size="small" :disabled="!canEdit" @click="addProcessor('postprocessor')">+ 添加处理器</el-button>
                </div>
              </div>
              <template v-for="(processor, i) in procList('postprocessor')" :key="processor.id">
                <div
                  class="env-detail__proc-item"
                  :class="{ 'is-selected': processor.id === activeProcId, 'is-disabled': !processor.enabled }"
                  @click="selectProcessor(processor)"
                >
                  <div class="env-detail__proc-item-header">
                    <span class="env-detail__proc-index">{{ i + 1 }}</span>
                    <el-tag v-for="t in procTags(processor)" :key="t.text" size="small" :type="t.type">{{ t.text }}</el-tag>
                    <div class="env-detail__proc-header-spacer" />
                    <el-switch v-model="processor.enabled" size="small" :disabled="!canEdit" @click.stop />
                    <el-dropdown v-if="canEdit" trigger="click" @click.stop>
                      <el-button link size="small">操作</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item @click="selectProcessor(processor)">编辑</el-dropdown-item>
                          <el-dropdown-item :disabled="i === 0" @click="moveProcessor('postprocessor', i, -1)">上移</el-dropdown-item>
                          <el-dropdown-item :disabled="i === postProcCount - 1" @click="moveProcessor('postprocessor', i, 1)">下移</el-dropdown-item>
                          <el-dropdown-item divided @click="copyProcessor(processor)">复制</el-dropdown-item>
                          <el-dropdown-item divided style="color: var(--el-color-danger)" @click="removeProcessor(processor)">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                  <div class="env-detail__proc-item-name">{{ procDisplayName(processor, i) }}</div>
                </div>
              </template>
              <el-empty v-if="postProcCount === 0" description="暂无后置处理器" :image-size="60" />
              <el-button v-if="postProcCount === 0 && canEdit" size="small" class="env-detail__proc-add" @click="addProcessor('postprocessor')">
                <el-icon><Plus /></el-icon> 添加处理器
              </el-button>
            </div>

            <div class="env-detail__proc-right">
              <template v-if="selectedProcessor">
                <div class="env-detail__proc-inline">
                  <header class="env-detail__proc-inline-head">
                    <el-input v-model="selectedProcessor.name" placeholder="处理器名称" class="env-detail__proc-inline-name" :disabled="!canEdit" />
                    <el-switch v-model="selectedProcessor.enabled" :disabled="!canEdit" active-text="启用" />
                    <el-divider direction="vertical" />
                    <el-radio-group v-model="procTestclass" :disabled="!canEdit" size="small">
                      <el-radio-button value="http">HTTP</el-radio-button>
                      <el-radio-button value="jdbc">JDBC</el-radio-button>
                    </el-radio-group>
                    <el-select
                      v-if="procTestclass === 'http'"
                      v-model="procHttpRef"
                      placeholder="选择环境 HTTP 配置"
                      filterable
                      :disabled="!canEdit"
                      class="env-detail__proc-inline-ref"
                    >
                      <el-option v-for="opt in procHttpRefOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                    </el-select>
                    <el-select
                      v-else-if="procTestclass === 'jdbc'"
                      v-model="procDsRef"
                      placeholder="选择环境数据源"
                      filterable
                      :disabled="!canEdit"
                      class="env-detail__proc-inline-ref"
                    >
                      <el-option v-for="opt in procDsRefOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                    </el-select>
                  </header>
                  <div class="env-detail__proc-inline-body" :class="{ 'is-readonly': !canEdit }">
                    <ProcessorForm
                      v-model="selectedProcessor.config"
                      :http-options="configForms"
                      :ds-options="dsForms"
                      :show-type-select="false"
                      :show-ref-select="false"
                      @import-extractors="openExtractorPicker"
                    />
                  </div>
                </div>
              </template>
              <div v-else class="env-detail__right-empty">
                <p>选中左侧处理器后在右侧编辑</p>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 从公共组件引入处理器 -->
    <ExtractorAssetPicker
      v-model="procAssetPickerVisible"
      :loading="procAssetPickerLoading"
      :items="procAssetPickerItems"
      :keyword="procAssetPickerKeyword"
      title="从公共组件引入处理器"
      tip="仅展示启用的处理器资产；引入为复制，得到独立副本，与源资产无关联。"
      empty-text="暂无可用处理器"
      search-placeholder="搜索处理器名称..."
      @update:keyword="procAssetPickerKeyword = $event"
      @search="loadProcAssets"
      @confirm="handleProcessorAssetPicked"
    />

    <!-- 从公共组件引入提取器 -->
    <ExtractorAssetPicker
      v-model="extractorPickerVisible"
      :loading="extractorPickerLoading"
      :items="extractorPickerItems"
      :keyword="extractorPickerKeyword"
      @update:keyword="extractorPickerKeyword = $event"
      @search="loadExtractorAssets"
      @confirm="handleExtractorPicked"
    />
  </div>
</template>

<style scoped lang="scss">
.env-detail {
  background: var(--color-neutral-0, #fff);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  // 顶部无名称/描述，去掉上内边距让标签贴顶
  padding: 0 var(--space-lg) var(--space-md);
  min-height: 320px;
}

.env-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md) 0 var(--space-sm);
}

.env-detail__name {
  font-size: var(--font-size-md);
  font-weight: 600;
}

.env-detail__tabs {
  margin-top: 0;

  :deep(.el-tabs__header) {
    // 收紧标签条与内容间距，避免大段留白
    margin-bottom: var(--space-sm);
  }

  :deep(.el-tabs__content) {
    overflow: visible;
  }
}

.env-detail__split {
  display: flex;
  gap: var(--space-lg);
}

.env-detail__config-list {
  list-style: none;
  margin: 0;
  padding: var(--space-sm);
  width: 180px;
  flex-shrink: 0;
  // 与右侧配置表单一致的卡片化，去掉原中间分割线（视觉设计 2.1）
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 2px;

  li {
    position: relative;
    padding: 6px var(--space-sm);
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: var(--font-size-sm);
    display: flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    // 列表项默认中性色，避免纯白面板上缺乏层级
    color: var(--color-neutral-600);

    &:hover {
      background: var(--color-primary-50);
    }

    &.is-active {
      background: var(--color-primary-50);
      color: var(--color-primary-600);
      font-weight: 500;

      // 选中态左侧主色竖条，对齐视觉设计「左侧菜单选中项」
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 60%;
        border-radius: var(--radius-sm);
        background: var(--color-primary-500);
      }
    }
  }

  // 新增入口整行虚线标识，与内容项层次区分（视觉设计 2.1 主色系）
  li.env-detail__config-add {
    margin-top: var(--space-xs);
    border: 1px dashed var(--color-neutral-300);
    justify-content: center;
    color: var(--color-primary-500);

    &:hover {
      border-color: var(--color-primary-500);
      background: var(--color-primary-50);
    }
  }
}

.env-detail__config-form {
  flex: 1;
  min-width: 0;
  // 内联表单区置于浅灰卡片内，与白色面板分隔层次
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.env-detail__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-detail__headers {
  margin-top: var(--space-md);
}

.env-detail__section-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  margin-bottom: var(--space-sm);
}

.env-detail__config-footer {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
  justify-content: flex-end;

  .el-button + .el-button {
    margin-left: 0;
  }
}

// ==================== 处理器：左列表 + 右内联明细（对齐测试场景 3.5） ====================
.env-detail__proc-split {
  display: flex;
  align-items: flex-start;
  gap: var(--space-lg);
}

.env-detail__proc-left {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.env-detail__proc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  font-weight: 600;
}

.env-detail__proc-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

// 左侧列表卡片：尺寸/内边距/选中态完全对齐场景处理器卡片
.env-detail__proc-item {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: 88px;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: pointer;
  margin: 2px 0;
  overflow: hidden;

  &:hover {
    border-color: var(--color-primary-300);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  &.is-selected {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50, #eff6ff);
    box-shadow: 0 0 0 1px var(--color-primary-300);
  }

  &.is-disabled {
    opacity: 0.5;
  }
}

.env-detail__proc-item-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.env-detail__proc-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-neutral-100);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-600);
  flex-shrink: 0;
}

.env-detail__proc-header-spacer {
  flex: 1;
}

.env-detail__proc-item-name {
  padding: var(--space-xs) 0 0 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-detail__proc-add {
  border-style: dashed;
  width: 100%;
  margin-top: var(--space-sm);
}

.env-detail__proc-right {
  flex: 1;
  min-width: 0;
}

// 右侧明细卡片：对齐场景处理器的 inline 编辑结构
.env-detail__proc-inline {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.env-detail__proc-inline-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-100);
  background: var(--color-neutral-50);

  // 头部空间允许换行，窄屏下 ref 下拉不被挤破
  flex-wrap: wrap;
}

.env-detail__proc-inline-name {
  flex: 1;
  min-width: 160px;
  max-width: 320px;
}

// 头部环境引用选择器：定宽不收缩，与场景 inline-ref 对齐
.env-detail__proc-inline-ref {
  width: 240px;
  flex-shrink: 0;
}

.env-detail__proc-inline-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  padding: var(--space-lg);

  &::-webkit-scrollbar {
    display: none;
  }

  // 只读态整块禁用交互仅作预览，避免处理器表单逐控件加 disabled
  &.is-readonly {
    pointer-events: none;
    opacity: 0.65;
  }
}

.env-detail__right-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.env-detail__empty {
  text-align: center;
  padding: var(--space-xl) 0;
  color: var(--color-neutral-400);

  p {
    margin-bottom: var(--space-sm);
  }
}

.env-detail__syntax-tip {
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);

  code {
    font-family: var(--font-family-mono, monospace);
    background: var(--color-neutral-50);
    padding: 0 4px;
    border-radius: var(--radius-sm, 3px);
  }
}
</style>
