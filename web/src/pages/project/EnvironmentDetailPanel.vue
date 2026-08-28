<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiDataSourcePayload,
  ApiEnvironmentDetail,
  ApiHttpConfigPayload,
  ApiProcessor,
  ApiProcessorType,
  ApiVariable,
} from '@/types'
import {
  batchReplaceVariables,
  createDataSource,
  createHttpConfig,
  createProcessor,
  deleteDataSource,
  deleteHttpConfig,
  deleteProcessor,
  exportVariables,
  fetchEnvironmentDetail,
  importVariables,
  testDataSource,
  testHttpConfig,
  updateDataSource,
  updateHttpConfig,
  updateProcessor,
} from '@/services/apiEnvironment'
import {
  createEmptyHttpConfig,
  DRIVER_OPTIONS,
  formatImportResult,
  parseVariablesJson,
  processorTypeLabel,
  resolveEnvironmentError,
  toVariablePayloads,
  validateVariableRow,
} from './environmentsModel'

const props = defineProps<{ environmentId: string; canEdit: boolean }>()
const emit = defineEmits<{ changed: [] }>()

// ==================== 详情加载与持久化（HTTP/数据源逐条即时保存，处理器/变量独立端点） ====================

const loading = ref(false)
const loadError = ref(false)
const detail = ref<ApiEnvironmentDetail | null>(null)

interface ConfigForm extends ApiHttpConfigPayload {
  id?: string
}
interface DsForm extends ApiDataSourcePayload {
  id?: string
}

const configForms = ref<ConfigForm[]>([])
const dsForms = ref<DsForm[]>([])
const variableRows = ref<ApiVariable[]>([])
const activeTab = ref<'http' | 'variables' | 'datasources' | 'processors'>('http')
const activeConfigId = ref('')

function cloneHeaders(source: ApiHttpConfigPayload): ApiHttpConfigPayload['headers'] {
  return (source.headers ?? []).map((header) => ({ ...header }))
}

function hydrate(next: ApiEnvironmentDetail) {
  detail.value = next
  configForms.value = next.httpConfigs.map((config) => ({ ...config, headers: cloneHeaders(config) }))
  dsForms.value = next.dataSources.map((ds) => ({ ...ds }))
  // 变量名按字母序排列（交互设计 3.5）
  variableRows.value = next.variables.map((row) => ({ ...row })).sort((a, b) => a.name.localeCompare(b.name))
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

async function refresh() {
  try {
    hydrate(await fetchEnvironmentDetail(props.environmentId))
    emit('changed')
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

// ==================== HTTP 配置（独立新增/编辑端点，3.4） ====================

const activeConfig = computed(() => configForms.value.find((config) => config.id === activeConfigId.value))

function selectConfig(config: ConfigForm) {
  activeConfigId.value = config.id ?? ''
}

function addHttpConfig() {
  const next = createEmptyHttpConfig(configForms.value.length + 1)
  next.id = `local-${Date.now()}`
  configForms.value.push(next)
  activeConfigId.value = next.id
}

/** 新建后本地占位（local- 前缀）未落库，取消即丢弃；已落库走删除端点 */
function cancelNewHttpConfig(form: ConfigForm) {
  if (form.id?.startsWith('local-')) form.id = undefined
  configForms.value = configForms.value.filter((config) => config !== form)
  if (activeConfigId.value === form.id || activeConfigId.value === undefined) {
    activeConfigId.value = configForms.value[0]?.id ?? ''
  }
}

const savingHttpId = ref('')

async function saveHttpConfig(form: ConfigForm) {
  if (!form.name.trim()) {
    ElMessage.warning('请填写配置名称')
    return
  }
  const isNew = !form.id || form.id.startsWith('local-')
  savingHttpId.value = form.id ?? ''
  try {
    const saved = isNew
      ? await createHttpConfig(props.environmentId, toHttpConfigPayload(form))
      : await updateHttpConfig(props.environmentId, form.id as string, toHttpConfigPayload(form))
    ElMessage.success(isNew ? '配置已创建' : '已保存')
    await refresh()
    activeConfigId.value = saved.id
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    savingHttpId.value = ''
  }
}

function toHttpConfigPayload(form: ConfigForm): ApiHttpConfigPayload {
  return {
    name: form.name.trim(),
    refName: form.refName || undefined,
    baseUrl: form.baseUrl || undefined,
    headers: (form.headers ?? []).filter((header) => header.key.trim() || header.value.trim()),
  }
}

async function deleteHttpConfigRow(form: ConfigForm) {
  if (!form.id || form.id.startsWith('local-')) return
  try {
    await ElMessageBox.confirm(`确认删除 HTTP 配置「${form.name || '(未命名)'}」？删除后不可恢复。`, '删除配置', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteHttpConfig(props.environmentId, form.id)
    ElMessage.success('已删除')
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

function addHeader(form: ConfigForm) {
  form.headers = [...(form.headers ?? []), { key: '', value: '', enabled: true }]
}

const testingHttpId = ref('')

async function runHttpTest(form: ConfigForm) {
  if (!form.id || form.id.startsWith('local-')) {
    ElMessage.warning('新配置请先保存后再测试连接')
    return
  }
  testingHttpId.value = form.id
  try {
    const result = await testHttpConfig(props.environmentId, form.id)
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
// ==================== 变量（独立端点即时保存） ====================

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
    id: `local-${Date.now()}-${variableRows.value.length}`,
    name: '',
    value: '',
    description: '',
    hasValue: false,
  }
  variableRows.value.push(row)
  editingVariableId.value = row.id
}

function removeVariableRow(row: ApiVariable) {
  variableRows.value = variableRows.value.filter((item) => item !== row)
}

const variablesSaving = ref(false)

async function saveVariables() {
  for (const row of variableRows.value) {
    if (!row.name && !row.value) continue
    const others = new Set(variableRows.value.filter((item) => item !== row).map((item) => item.name))
    const error = validateVariableRow(row, others)
    if (error) {
      ElMessage.warning(`${row.name || '(未命名)'}：${error}`)
      return
    }
  }
  variablesSaving.value = true
  try {
    const saved = await batchReplaceVariables(
      props.environmentId,
      toVariablePayloads(variableRows.value.filter((row) => row.name)),
    )
    variableRows.value = saved.map((row) => ({ ...row }))
    ElMessage.success('变量已保存')
    emit('changed')
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    variablesSaving.value = false
  }
}

const varImportDialogVisible = ref(false)
const varImportRaw = ref('')
const varImportOverwrite = ref(false)
const varImporting = ref(false)

/** 变量导出为 JSON 文件（详细设计 3.3.4），可直接用于批量导入回灌 */
async function exportVariableRows() {
  try {
    const rows = await exportVariables(props.environmentId)
    const blob = new Blob([JSON.stringify(rows, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'variables.json'
    link.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

async function submitVarImport() {
  const parsed = parseVariablesJson(varImportRaw.value)
  if (!parsed.ok) {
    ElMessage.error(parsed.error)
    return
  }
  varImporting.value = true
  try {
    const result = await importVariables(props.environmentId, parsed.rows, varImportOverwrite.value)
    varImportDialogVisible.value = false
    await ElMessageBox.alert(formatImportResult(result), '导入结果', { confirmButtonText: '知道了' })
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    varImporting.value = false
  }
}

// ==================== 数据源（独立新增/编辑端点，3.4） ====================

const activeDsId = ref('')
const activeDs = computed(() => dsForms.value.find((form) => form.id === activeDsId.value))

function selectDs(form: DsForm) {
  activeDsId.value = form.id ?? ''
}

const selectedDsDriverOption = computed(() => DRIVER_OPTIONS.find((option) => option.driver === activeDs.value?.driver))

function handleDsDriverChange(driver: string) {
  // 切换驱动时若尚未填写 URL，自动填充该驱动的示例，避免空 URL 误保存
  const option = DRIVER_OPTIONS.find((item) => item.driver === driver)
  if (option && activeDs.value && !activeDs.value.url) activeDs.value.url = option.urlExample
}

function addDataSource() {
  const id = `local-${Date.now()}`
  const next: DsForm = {
    id,
    name: '',
    refName: `db_${dsForms.value.length + 1}`,
    driver: DRIVER_OPTIONS[0]?.driver ?? '',
    url: '',
  }
  dsForms.value.push(next)
  activeDsId.value = id
}

/** 新建后本地占位（local- 前缀）未落库，取消即丢弃；已落库走删除端点 */
function cancelNewDs(form: DsForm) {
  if (form.id?.startsWith('local-')) form.id = undefined
  dsForms.value = dsForms.value.filter((item) => item !== form)
  if (activeDsId.value === form.id || activeDsId.value === undefined) {
    activeDsId.value = dsForms.value[0]?.id ?? ''
  }
}

const savingDsId = ref('')

async function saveDs(form: DsForm) {
  if (!form.name.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  if (!form.url?.trim()) {
    ElMessage.warning('请填写连接 URL')
    return
  }
  const isNew = !form.id || form.id.startsWith('local-')
  savingDsId.value = form.id ?? ''
  try {
    const saved = isNew
      ? await createDataSource(props.environmentId, toDsPayload(form))
      : await updateDataSource(props.environmentId, form.id as string, toDsPayload(form))
    ElMessage.success(isNew ? '数据源已创建' : '已保存')
    await refresh()
    activeDsId.value = saved.id
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    savingDsId.value = ''
  }
}

function toDsPayload(form: DsForm): ApiDataSourcePayload {
  return {
    name: form.name.trim(),
    refName: form.refName || undefined,
    driver: form.driver,
    url: form.url?.trim(),
    maxPoolSize: form.maxPoolSize,
  }
}

async function deleteDsRow(form: DsForm) {
  if (!form.id || form.id.startsWith('local-')) return
  try {
    await ElMessageBox.confirm(`确认删除数据源「${form.name || '(未命名)'}」？删除后不可恢复。`, '删除数据源', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteDataSource(props.environmentId, form.id)
    ElMessage.success('已删除')
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

const testingDsId = ref('')

async function runDsTest(form: DsForm) {
  if (!form.id || form.id.startsWith('local-')) {
    ElMessage.warning('新数据源请先保存后再测试连接')
    return
  }
  testingDsId.value = form.id
  try {
    const result = await testDataSource(props.environmentId, form.id)
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

// ==================== 处理器（独立端点即时保存） ====================

const procDialogVisible = ref(false)
const procDialogMode = ref<'create' | 'edit'>('create')
const procForm = reactive<{ id?: string; processorType: ApiProcessorType; name: string; configRaw: string; enabled: boolean }>({
  processorType: 'preprocessor',
  name: '',
  configRaw: '',
  enabled: true,
})

function openProcCreateDialog(processorType: ApiProcessorType) {
  procDialogMode.value = 'create'
  Object.assign(procForm, { id: undefined, processorType, name: '', configRaw: '', enabled: true })
  procDialogVisible.value = true
}

function openProcEditDialog(processor: ApiProcessor) {
  procDialogMode.value = 'edit'
  Object.assign(procForm, {
    id: processor.id,
    processorType: processor.processorType,
    name: processor.name,
    configRaw: processor.config ? JSON.stringify(processor.config, null, 2) : '',
    enabled: processor.enabled,
  })
  procDialogVisible.value = true
}

async function submitProcDialog() {
  if (!procForm.name.trim()) {
    ElMessage.warning('请填写处理器名称')
    return
  }
  let config: Record<string, unknown> | undefined
  if (procForm.configRaw.trim()) {
    try {
      const parsed: unknown = JSON.parse(procForm.configRaw)
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new Error('not object')
      config = parsed as Record<string, unknown>
    } catch {
      ElMessage.error('config 须为合法 JSON 对象')
      return
    }
  }
  const body = {
    processorType: procForm.processorType,
    name: procForm.name.trim(),
    config,
    enabled: procForm.enabled,
  }
  try {
    if (procDialogMode.value === 'create') {
      await createProcessor(props.environmentId, body)
    } else if (procForm.id) {
      await updateProcessor(props.environmentId, procForm.id, body)
    }
    procDialogVisible.value = false
    ElMessage.success('处理器已保存')
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

async function toggleProcessor(processor: ApiProcessor) {
  try {
    await updateProcessor(props.environmentId, processor.id, {
      processorType: processor.processorType,
      name: processor.name,
      config: processor.config,
      sortOrder: processor.sortOrder,
      enabled: !processor.enabled,
    })
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

async function removeProcessor(processor: ApiProcessor) {
  try {
    await ElMessageBox.confirm(`确认删除处理器「${processor.name}」？`, '删除处理器', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteProcessor(props.environmentId, processor.id)
    ElMessage.success('已删除')
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}
</script>

<template>
  <div v-loading="loading" class="env-detail">
    <div v-if="loadError" class="env-detail__empty">
      <p>环境详情加载失败</p>
      <el-button size="small" @click="load">重试</el-button>
    </div>

    <template v-else-if="detail">
      <el-tabs v-model="activeTab" class="env-detail__tabs">
        <!-- ============ HTTP 默认配置 ============ -->
        <el-tab-pane :label="`HTTP (${configForms.length})`" name="http">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in configForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeConfigId }"
                @click="selectConfig(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit">
                <el-button link type="primary" @click="addHttpConfig">+ 新增配置</el-button>
              </li>
            </ul>

            <div v-if="activeConfig" class="env-detail__config-form">
              <el-form label-width="110px" size="small" :disabled="!canEdit">
                <el-form-item label="名称" required>
                  <el-input v-model="activeConfig.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名 refName">
                  <el-input v-model="activeConfig.refName" placeholder="场景中通过该名引用此配置" />
                </el-form-item>
                <el-form-item label="Base URL">
                  <el-input v-model="activeConfig.baseUrl" placeholder="https://api.example.com" />
                </el-form-item>
              </el-form>

              <div class="env-detail__headers">
                <div class="env-detail__section-title">
                  请求头
                  <el-button v-if="canEdit" link type="primary" size="small" @click="addHeader(activeConfig)">
                    + 添加
                  </el-button>
                </div>
                <div v-for="(header, index) in activeConfig.headers ?? []" :key="index" class="env-detail__header-row">
                  <el-checkbox
                    :model-value="header.enabled"
                    :disabled="!canEdit"
                    @change="(value: boolean | string | number) => { header.enabled = value === true }"
                  />
                  <el-input v-model="header.key" placeholder="Header" :disabled="!canEdit" />
                  <el-input v-model="header.value" placeholder="Value" :disabled="!canEdit" />
                  <el-button
                    v-if="canEdit"
                    link
                    type="danger"
                    @click="activeConfig.headers?.splice(index, 1)"
                  >
                    删除
                  </el-button>
                </div>
              </div>

              <div class="env-detail__config-footer">
                <el-button
                  size="small"
                  :loading="testingHttpId === activeConfig.id"
                  @click="runHttpTest(activeConfig)"
                >
                  连接测试
                </el-button>
                <template v-if="canEdit">
                  <el-button v-if="!activeConfig.id || activeConfig.id.startsWith('local-')" size="small" @click="cancelNewHttpConfig(activeConfig)">
                    取消
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="savingHttpId === activeConfig.id"
                    @click="saveHttpConfig(activeConfig)"
                  >
                    {{ activeConfig.id && !activeConfig.id.startsWith('local-') ? '保存修改' : '保存' }}
                  </el-button>
                  <el-button
                    v-if="activeConfig.id && !activeConfig.id.startsWith('local-')"
                    size="small"
                    type="danger"
                    plain
                    @click="deleteHttpConfigRow(activeConfig)"
                  >
                    删除配置
                  </el-button>
                </template>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <!-- ============ 全局变量 ============ -->
        <el-tab-pane :label="`变量 (${variableRows.length})`" name="variables">
          <div class="env-detail__toolbar env-detail__toolbar--right">
            <el-button size="small" :disabled="!canEdit" @click="varImportDialogVisible = true">导入</el-button>
            <el-button size="small" @click="exportVariableRows">导出</el-button>
            <el-button size="small" type="primary" :disabled="!canEdit" @click="addVariableRow">新增变量</el-button>
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
                <template v-if="row.id === editingVariableId && canEdit">
                  <el-button link type="primary" size="small" @click="editingVariableId = ''">完成</el-button>
                </template>
                <template v-else>
                  <el-button v-if="canEdit" link size="small" @click="editingVariableId = row.id">编辑</el-button>
                  <el-button v-if="canEdit" link type="danger" size="small" @click="removeVariableRow(row as ApiVariable)">删除</el-button>
                </template>
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
            引用语法：<code>${'{'}变量名{'}'}</code>，如 <code>${'{'}BASE_URL{'}'}</code>
          </p>
          <div class="env-detail__toolbar env-detail__toolbar--right">
            <el-button size="small" type="primary" :loading="variablesSaving" :disabled="!canEdit" @click="saveVariables">
              保存变量
            </el-button>
          </div>
        </el-tab-pane>

        <!-- ============ 数据源（交互同 HTTP：左列表 + 右内联表单） ============ -->
        <el-tab-pane :label="`数据源 (${dsForms.length})`" name="datasources">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in dsForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeDsId }"
                @click="selectDs(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit">
                <el-button link type="primary" @click="addDataSource">+ 新增数据源</el-button>
              </li>
            </ul>

            <div v-if="activeDs" class="env-detail__config-form">
              <el-form label-width="110px" size="small" :disabled="!canEdit">
                <el-form-item label="名称" required>
                  <el-input v-model="activeDs.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名 refName">
                  <el-input v-model="activeDs.refName" placeholder="场景中通过该名引用此数据源" />
                </el-form-item>
                <el-form-item label="驱动">
                  <el-select v-model="activeDs.driver" @change="handleDsDriverChange">
                    <el-option
                      v-for="option in DRIVER_OPTIONS"
                      :key="option.label"
                      :label="option.label"
                      :value="option.driver"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="JDBC URL" required>
                  <el-input
                    v-model="activeDs.url"
                    type="textarea"
                    :rows="2"
                    :placeholder="selectedDsDriverOption?.urlExample"
                  />
                  <span v-if="activeDs.driver === '-'" class="env-detail__hint">Redis 无需驱动，按 redis:// 协议识别</span>
                </el-form-item>
                <el-form-item label="连接池上限">
                  <el-input-number v-model="activeDs.maxPoolSize" :min="1" :max="100" />
                </el-form-item>
              </el-form>

              <p class="env-detail__dialog-tip">用户名与密码请拼入 URL；保存后可通过「连接测试」验证连通性</p>

              <div class="env-detail__config-footer">
                <el-button
                  size="small"
                  :loading="testingDsId === activeDs.id"
                  @click="runDsTest(activeDs)"
                >
                  连接测试
                </el-button>
                <template v-if="canEdit">
                  <el-button v-if="!activeDs.id || activeDs.id.startsWith('local-')" size="small" @click="cancelNewDs(activeDs)">
                    取消
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="savingDsId === activeDs.id"
                    @click="saveDs(activeDs)"
                  >
                    {{ activeDs.id && !activeDs.id.startsWith('local-') ? '保存修改' : '保存' }}
                  </el-button>
                  <el-button
                    v-if="activeDs.id && !activeDs.id.startsWith('local-')"
                    size="small"
                    type="danger"
                    plain
                    @click="deleteDsRow(activeDs)"
                  >
                    删除数据源
                  </el-button>
                </template>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 处理器 ============ -->
        <el-tab-pane :label="`处理器 (${detail.processors.length})`" name="processors">
          <div class="env-detail__toolbar">
            <el-button size="small" type="primary" :disabled="!canEdit" @click="openProcCreateDialog('preprocessor')">
              新增前置
            </el-button>
            <el-button size="small" type="primary" plain :disabled="!canEdit" @click="openProcCreateDialog('postprocessor')">
              新增后置
            </el-button>
          </div>
          <el-table :data="detail.processors" size="small" empty-text="暂无处理器">
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
                <el-button v-if="canEdit" link size="small" @click="openProcEditDialog(row as ApiProcessor)">编辑</el-button>
                <el-button v-if="canEdit" link type="danger" size="small" @click="removeProcessor(row as ApiProcessor)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 变量批量导入 -->
    <el-dialog v-model="varImportDialogVisible" title="批量导入变量" width="520px">
      <p class="env-detail__dialog-tip">粘贴 JSON 数组，如：[{"name":"BASE_URL","value":"https://api.example.com"}]</p>
      <el-input v-model="varImportRaw" type="textarea" :rows="8" placeholder="[...]" />
      <div class="env-detail__import-overwrite">
        <el-switch v-model="varImportOverwrite" />
        <span>同名变量覆盖（关闭则跳过）</span>
      </div>
      <template #footer>
        <el-button @click="varImportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="varImporting" @click="submitVarImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 处理器新建/编辑 -->
    <el-dialog v-model="procDialogVisible" :title="procDialogMode === 'create' ? '新增处理器' : '编辑处理器'" width="560px">
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
        <el-form-item label="config (JSON)">
          <el-input v-model="procForm.configRaw" type="textarea" :rows="6" placeholder='{"script":"..."}' />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="procForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="procDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProcDialog">保存</el-button>
      </template>
    </el-dialog>
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
  padding: 0;
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-neutral-100);
  display: flex;
  flex-direction: column;
  gap: 2px;

  li {
    padding: 6px var(--space-sm);
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: var(--font-size-sm);
    display: flex;
    align-items: center;
    gap: 6px;

    &:hover {
      background: var(--color-neutral-50);
    }

    &.is-active {
      background: rgba(59, 130, 246, 0.08);
    }
  }
}

.env-detail__config-form {
  flex: 1;
  min-width: 0;
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

.env-detail__header-row {
  display: grid;
  grid-template-columns: auto 1fr 1fr auto;
  gap: var(--space-sm);
  align-items: center;
  margin-bottom: var(--space-sm);
}

.env-detail__config-footer {
  margin-top: var(--space-md);
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

.env-detail__dialog-tip {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-detail__import-overwrite {
  margin-top: var(--space-sm);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
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




