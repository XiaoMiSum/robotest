<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiDataSourcePayload,
  ApiEnvironmentDetail,
  ApiEnvironmentSaveReq,
  ApiHeaderItem,
  ApiHttpConfigPayload,
  ApiProcessor,
  ApiProcessorType,
  ApiVariable,
} from '@/types'
import { fetchEnvironmentDetail, testDataSourceConfig, testHttpConfig, updateEnvironment } from '@/services/apiEnvironment'
import {
  createEmptyHttpConfig,
  DRIVER_OPTIONS,
  processorTypeLabel,
  resolveEnvironmentError,
  validateVariableRow,
} from './environmentsModel'
import KeyValueTable from './debug/KeyValueTable.vue'
import ProcessorForm from '@/components/api-testing/ProcessorForm.vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import {
  defaultProcessorConfig,
  extractorsFromComponents,
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

const configForms = ref<ConfigForm[]>([])
const dsForms = ref<DsForm[]>([])
const variableRows = ref<ApiVariable[]>([])
const processorRows = ref<ApiProcessor[]>([])
const activeTab = ref<'http' | 'variables' | 'datasources' | 'processors'>('http')
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
    .map((row) => ({ ...row, id: nextLocalId() }))
    .sort((a, b) => a.name.localeCompare(b.name))
  processorRows.value = next.processors.map((processor) => ({ ...processor, id: nextLocalId() }))
  variablePage.value = 1
  if (!configForms.value.some((config) => config.id === activeConfigId.value)) {
    activeConfigId.value = configForms.value[0]?.id ?? ''
  }
  if (!dsForms.value.some((form) => form.id === activeDsId.value)) {
    activeDsId.value = dsForms.value[0]?.id ?? ''
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

// ==================== 全局变量（就地编辑，聚合提交） ====================

const editingVariableId = ref('')
/** 行数超过 10 条时分页展示（交互设计 3.5） */
const VARIABLE_PAGE_SIZE = 10
const variablePage = ref(1)
const pagedVariableRows = computed(() => {
  const start = (variablePage.value - 1) * VARIABLE_PAGE_SIZE
  return variableRows.value.slice(start, start + VARIABLE_PAGE_SIZE)
})

function addVariableRow() {
  const row: ApiVariable = {
    id: nextLocalId(),
    name: '',
    value: '',
    description: '',
    hasValue: false,
  }
  variableRows.value.push(row)
  editingVariableId.value = row.id ?? ''
}

function removeVariableRow(row: ApiVariable) {
  variableRows.value = variableRows.value.filter((item) => item !== row)
  if (editingVariableId.value === row.id) editingVariableId.value = ''
}

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

// ==================== 处理器（就地编辑） ====================

const procDialogVisible = ref(false)
const procDialogMode = ref<'create' | 'edit'>('create')
const procForm = reactive<{ id?: string; processorType: ApiProcessorType; name: string; config: Record<string, unknown> }>({
  processorType: 'preprocessor',
  name: '',
  config: {},
})

function openProcCreateDialog(processorType: ApiProcessorType) {
  procDialogMode.value = 'create'
  Object.assign(procForm, { id: undefined, processorType, name: '', config: {} })
  procDialogVisible.value = true
}

function openProcEditDialog(processor: ApiProcessor) {
  procDialogMode.value = 'edit'
  Object.assign(procForm, {
    id: processor.id,
    processorType: processor.processorType,
    name: processor.name,
    config: processor.config ?? {},
  })
  procDialogVisible.value = true
}

const basicProcEnabled = computed<boolean>({
  get: () => procForm.config.enabled !== false,
  set: (value: boolean) => {
    procForm.config = { ...procForm.config, enabled: value }
  },
})

const basicProcSortOrder = computed<number>({
  get: () => (typeof procForm.config.sortOrder === 'number' ? procForm.config.sortOrder as number : 0),
  set: (value: number) => {
    procForm.config = { ...procForm.config, sortOrder: value }
  },
})

function toggleProcessor(processor: ApiProcessor) {
  processor.enabled = !processor.enabled
}

function removeProcessor(processor: ApiProcessor) {
  processorRows.value = processorRows.value.filter((item) => item !== processor)
}

// ==================== 提取器：从公共组件获取 ====================

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
  const incoming = extractorsFromComponents(rows)
  if (incoming.length === 0) return
  const existing = Array.isArray(procForm.config.extractors) ? procForm.config.extractors as ProcessorExtractor[] : []
  procForm.config = {
    ...procForm.config,
    extractors: [...existing, ...incoming],
  }
  ElMessage.success(`已引入 ${incoming.length} 个提取器`)
}

function submitProcDialog() {
  if (!procForm.name.trim()) {
    ElMessage.warning('请填写处理器名称')
    return
  }
  const config = { ...defaultProcessorConfig(), ...(procForm.config ?? {}) }
  const body: ApiProcessor = {
    id: procForm.id ?? nextLocalId(),
    processorType: procForm.processorType,
    name: procForm.name.trim(),
    config: Object.keys(config).length > 0 ? config : undefined,
    enabled: config.enabled !== false,
    sortOrder: typeof config.sortOrder === 'number' ? config.sortOrder : 0,
  }
  if (procDialogMode.value === 'create') {
    processorRows.value.push(body)
  } else {
    const index = processorRows.value.findIndex((item) => item.id === body.id)
    if (index >= 0) processorRows.value[index] = body
  }
  procDialogVisible.value = false
  ElMessage.success('处理器已保存')
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
  const namedVariables = variableRows.value.filter((row) => row.name)
  for (let index = 0; index < namedVariables.length; index += 1) {
    const row = namedVariables[index]
    const others = new Set(namedVariables.map((other) => other.name))
    others.delete(row.name)
    const error = validateVariableRow(row, others)
    if (error) {
      return `变量 ${row.name || '(未命名)'}：${error}`
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
    headers: (config.headers ?? []).filter((header) => header.key.trim() || header.value.trim()),
  }))
  const variables = variableRows.value
    .filter((row) => row.name.trim())
    .map((row) => ({ name: row.name.trim(), value: row.value || undefined, description: row.description || undefined }))
  const dataSources = dsForms.value.map((ds) => ({
    name: ds.name.trim(),
    refName: ds.refName || undefined,
    driver: ds.driver,
    url: ds.url,
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
        <el-tab-pane :label="`变量 (${variableRows.length})`" name="variables">
          <div class="env-detail__toolbar env-detail__toolbar--right">
            <el-button type="primary" :disabled="!canEdit" @click="addVariableRow">
              <el-icon><Plus /></el-icon>新增变量
            </el-button>
          </div>
          <el-table :data="pagedVariableRows" size="small" empty-text="暂无变量，点击右上角添加">
            <el-table-column label="变量名" width="200">
              <template #default="{ row }">
                <el-input
                  v-if="row.id === editingVariableId && canEdit"
                  v-model="row.name"
                  placeholder="仅字母/数字/下划线"
                  size="small"
                />
                <span v-else class="env-detail__mono">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column label="取值">
              <template #default="{ row }">
                <el-input
                  v-if="row.id === editingVariableId && canEdit"
                  v-model="row.value"
                  placeholder="变量取值"
                  size="small"
                />
                <span v-else class="env-detail__mono">{{ row.value || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="描述" min-width="140">
              <template #default="{ row }">
                <el-input
                  v-if="row.id === editingVariableId && canEdit"
                  v-model="row.description"
                  size="small"
                />
                <span v-else>{{ row.description || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEdit" link @click="editingVariableId = row.id">编辑</el-button>
                <el-button v-if="canEdit" link type="danger" @click="removeVariableRow(row as ApiVariable)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="variableRows.length > VARIABLE_PAGE_SIZE"
            v-model:current-page="variablePage"
            :page-size="VARIABLE_PAGE_SIZE"
            :total="variableRows.length"
            layout="prev, pager, next"
            size="small"
            class="env-detail__pager"
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

        <!-- ============ 处理器 ============ -->
        <el-tab-pane :label="`处理器 (${processorRows.length})`" name="processors">
          <div class="env-detail__toolbar">
            <el-button type="primary" :disabled="!canEdit" @click="openProcCreateDialog('preprocessor')">
              <el-icon><Plus /></el-icon>新增前置
            </el-button>
            <el-button type="primary" plain :disabled="!canEdit" @click="openProcCreateDialog('postprocessor')">
              <el-icon><Plus /></el-icon>新增后置
            </el-button>
          </div>
          <el-table :data="processorRows" size="small" empty-text="暂无处理器">
            <el-table-column label="类别" width="80">
              <template #default="{ row }">{{ processorTypeLabel(row.processorType) }}</template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled"
                  size="small"
                  :disabled="!canEdit"
                  @change="() => toggleProcessor(row as ApiProcessor)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEdit" link @click="openProcEditDialog(row as ApiProcessor)">编辑</el-button>
                <el-button v-if="canEdit" link type="danger" @click="removeProcessor(row as ApiProcessor)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 处理器新建/编辑 -->
    <el-dialog v-model="procDialogVisible" :title="procDialogMode === 'create' ? '新增处理器' : '编辑处理器'" width="640px">
      <el-form label-width="110px">
        <el-form-item label="类别">
          <el-radio-group v-model="procForm.processorType" :disabled="procDialogMode === 'edit'">
            <el-radio value="preprocessor">前置</el-radio>
            <el-radio value="postprocessor">后置</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="procForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="basicProcEnabled" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="basicProcSortOrder" :min="0" :max="9999" />
        </el-form-item>
        <ProcessorForm v-model="procForm.config" @import-extractors="openExtractorPicker" />
      </el-form>
      <template #footer>
        <el-button @click="procDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProcDialog">保存</el-button>
      </template>
    </el-dialog>

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

.env-detail__toolbar {
  margin-bottom: var(--space-md);
  display: flex;
  gap: var(--space-sm);

  &--right {
    justify-content: flex-end;
    margin-top: var(--space-md);
  }

  .el-button + .el-button {
    margin-left: 0;
  }
}

.env-detail__mono {
  font-family: var(--font-family-mono, monospace);
  font-size: var(--font-size-xs);
  word-break: break-all;
}

.env-detail__empty {
  text-align: center;
  padding: var(--space-xl) 0;
  color: var(--color-neutral-400);

  p {
    margin-bottom: var(--space-sm);
  }
}

.env-detail__pager {
  margin-top: var(--space-sm);
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
