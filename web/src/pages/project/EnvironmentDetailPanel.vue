<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiEnvironmentDetail,
  ApiHttpConfigPayload,
  ApiProcessor,
  ApiProcessorType,
  ApiVariable,
} from '@/types'
import {
  batchReplaceVariables,
  createProcessor,
  deleteProcessor,
  exportVariables,
  fetchEnvironmentDetail,
  importVariables,
  revealVariable,
  setDefaultEnvironment,
  testDataSource,
  testHttpConfig,
  updateEnvironment,
  updateProcessor,
} from '@/services/apiEnvironment'
import {
  buildSavePayload,
  createEmptyHttpConfig,
  detailToForm,
  DRIVER_OPTIONS,
  formatImportResult,
  HTTP_METHOD_OPTIONS,
  parseVariablesJson,
  processorTypeLabel,
  resolveEnvironmentError,
  SENSITIVE_MASK,
  toVariablePayloads,
  VARIABLE_TYPE_OPTIONS,
  validateVariableRow,
  type EnvironmentEditForm,
} from './environmentsModel'

const props = defineProps<{ environmentId: string; canEdit: boolean; isFirst: boolean; isLast: boolean }>()
const emit = defineEmits<{ changed: []; move: [direction: -1 | 1] }>()

// ==================== 详情加载与聚合持久化 ====================

const loading = ref(false)
const loadError = ref(false)
const detail = ref<ApiEnvironmentDetail | null>(null)
/** 基础信息与 HTTP 配置的未保存标记（交互设计 2.6） */
const dirty = ref(false)
const basicForm = reactive<EnvironmentEditForm>({ name: '', description: '', isDefault: false })
const editingBasic = ref(false)
const saving = ref(false)

interface ConfigForm extends ApiHttpConfigPayload {
  id?: string
}
interface DsForm {
  id?: string
  name: string
  refName: string
  driver: string
  url: string
  maxPoolSize?: number
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
  Object.assign(basicForm, detailToForm(next))
  editingBasic.value = false
  configForms.value = next.httpConfigs.map((config) => ({ ...config, headers: cloneHeaders(config) }))
  dsForms.value = next.dataSources.map((ds) => ({ ...ds }))
  variableRows.value = next.variables.map((row) => ({ ...row }))
  if (!configForms.value.some((config) => config.id === activeConfigId.value)) {
    activeConfigId.value = configForms.value[0]?.id ?? ''
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

/** 聚合 PUT：全量替换语义，未编辑段落由 buildSavePayload 从详情回传兜底 */
async function persistAggregate(): Promise<boolean> {
  if (!detail.value || !basicForm.name.trim()) {
    ElMessage.warning('环境名称不能为空')
    return false
  }
  saving.value = true
  try {
    await updateEnvironment(
      detail.value.id,
      buildSavePayload(
        basicForm,
        detail.value,
        // 本地新增行带 local- 占位 id，剥离后仅上送 payload 字段
        configForms.value.map(({ id: _ignored, ...payload }) => payload),
        dsForms.value.map(({ id: _ignored, ...payload }) => payload),
      ),
    )
    dirty.value = false
    return true
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
    return false
  } finally {
    saving.value = false
  }
}

async function saveAll() {
  if (!props.canEdit || !dirty.value) return
  if (await persistAggregate()) {
    ElMessage.success('已保存')
    await refresh()
  }
}

async function handleSetDefault() {
  if (!detail.value || detail.value.isDefault) return
  try {
    await ElMessageBox.confirm(
      `确认将「${detail.value.name}」设为默认环境？场景执行未指定环境时将使用默认环境`,
      '设为默认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await setDefaultEnvironment(detail.value.id)
    ElMessage.success('已设为默认')
    await refresh()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

// ==================== 默认配置（HTTP 配置） ====================

const activeConfig = computed(() => configForms.value.find((config) => config.id === activeConfigId.value))

function selectConfig(config: ConfigForm) {
  activeConfigId.value = config.id ?? ''
}

function addHttpConfig() {
  const next = createEmptyHttpConfig(configForms.value.length + 1)
  next.id = `local-${Date.now()}`
  configForms.value.push(next)
  activeConfigId.value = next.id
  markDirty()
}

function removeHttpConfig(form: ConfigForm) {
  configForms.value = configForms.value.filter((config) => config !== form)
  if (activeConfigId.value === form.id) activeConfigId.value = configForms.value[0]?.id ?? ''
  markDirty()
}

function addHeader(form: ConfigForm) {
  form.headers = [...(form.headers ?? []), { key: '', value: '', enabled: true }]
  markDirty()
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

function markDirty() {
  dirty.value = true
}
// ==================== 变量（独立端点即时保存） ====================

const editingVariableId = ref('')
const revealingId = ref('')

function addVariableRow() {
  const row: ApiVariable = {
    id: `local-${Date.now()}-${variableRows.value.length}`,
    name: '',
    value: '',
    type: 'text',
    description: '',
    hasValue: false,
  }
  variableRows.value.push(row)
  editingVariableId.value = row.id
}

function editVariable(row: ApiVariable) {
  // 敏感值不回显明文：进入编辑清空输入框，留空提交由后端沿用旧密文
  if (row.type === 'sensitive') row.value = ''
  editingVariableId.value = row.id
}

function removeVariableRow(row: ApiVariable) {
  variableRows.value = variableRows.value.filter((item) => item !== row)
}

async function revealValue(row: ApiVariable) {
  if (row.type !== 'sensitive' || row.id.startsWith('local-')) return
  revealingId.value = row.id
  try {
    const resp = await revealVariable(props.environmentId, row.id)
    row.value = resp.value ?? ''
    // 交互设计 3.3：临时明文展示 3 秒后恢复脱敏，避免旁观泄露
    setTimeout(() => {
      row.value = SENSITIVE_MASK
      revealingId.value = ''
    }, 3000)
  } catch (err) {
    revealingId.value = ''
    ElMessage.error(resolveEnvironmentError(err))
  }
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

// ==================== 数据源（随聚合 PUT 持久化） ====================

const dsDialogVisible = ref(false)
const dsDialogMode = ref<'create' | 'edit'>('create')
const dsForm = reactive<DsForm>({ name: '', refName: '', driver: '', url: '' })
let dsEditTarget: DsForm | null = null

const selectedDriverOption = computed(() => DRIVER_OPTIONS.find((option) => option.driver === dsForm.driver))

function handleDriverChange(driver: string) {
  const option = DRIVER_OPTIONS.find((item) => item.driver === driver)
  if (option && !dsForm.url) dsForm.url = option.urlExample
}

function openDsCreateDialog() {
  dsDialogMode.value = 'create'
  dsEditTarget = null
  Object.assign(dsForm, { id: undefined, name: '', refName: `db_${dsForms.value.length + 1}`, driver: DRIVER_OPTIONS[0]?.driver ?? '', url: '' })
  dsDialogVisible.value = true
}

function openDsEditDialog(form: DsForm) {
  dsDialogMode.value = 'edit'
  dsEditTarget = form
  Object.assign(dsForm, { ...form })
  dsDialogVisible.value = true
}

async function submitDsDialog() {
  if (!dsForm.name.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  if (!dsForm.url.trim()) {
    ElMessage.warning('请填写 JDBC URL')
    return
  }
  const entry: DsForm = { ...dsForm, name: dsForm.name.trim(), url: dsForm.url.trim() }
  if (dsDialogMode.value === 'create') {
    entry.id = `local-${Date.now()}`
    dsForms.value.push(entry)
  } else if (dsEditTarget) {
    entry.id = dsEditTarget.id
    dsForms.value = dsForms.value.map((form) => (form === dsEditTarget ? entry : form))
  }
  dsDialogVisible.value = false
  if (await persistAggregate()) {
    ElMessage.success('数据源已保存')
    await refresh()
  }
}

async function removeDataSource(form: DsForm) {
  dsForms.value = dsForms.value.filter((item) => item !== form)
  if (await persistAggregate()) {
    ElMessage.success('数据源已删除')
    await refresh()
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
      <header class="env-detail__header">
        <div class="env-detail__title">
          <template v-if="editingBasic && canEdit">
            <el-input v-model="basicForm.name" maxlength="100" class="env-detail__name-input" />
            <el-tag v-if="detail.isDefault" size="small" type="warning" effect="light">默认</el-tag>
          </template>
          <template v-else>
            <h4>{{ detail.name }}</h4>
            <el-tag v-if="detail.isDefault" size="small" type="warning" effect="light">默认</el-tag>
          </template>
          <el-badge v-if="dirty" value="未保存" type="warning" />
        </div>
        <div v-if="canEdit" class="env-detail__actions">
          <el-button-group class="env-detail__sort">
            <el-button size="small" :disabled="isFirst" @click="emit('move', -1)">上移</el-button>
            <el-button size="small" :disabled="isLast" @click="emit('move', 1)">下移</el-button>
          </el-button-group>
          <el-button v-if="!detail.isDefault" size="small" @click="handleSetDefault">设为默认</el-button>
          <el-button v-if="!editingBasic" size="small" @click="editingBasic = true">编辑信息</el-button>
          <el-button size="small" :disabled="!dirty" type="primary" :loading="saving" @click="saveAll">
            保存{{ dirty ? ' *' : '' }}
          </el-button>
        </div>
      </header>

      <p v-if="editingBasic && canEdit" class="env-detail__desc-edit">
        <el-input v-model="basicForm.description" type="textarea" :rows="2" maxlength="500" placeholder="描述（可选）" />
        <el-checkbox v-model="basicForm.isDefault" :disabled="detail.isDefault">设为默认环境</el-checkbox>
      </p>
      <p v-else-if="detail.description" class="env-detail__desc">{{ detail.description }}</p>

      <el-tabs v-model="activeTab" class="env-detail__tabs">
        <!-- ============ 默认配置 ============ -->
        <el-tab-pane label="默认配置" name="http">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in configForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeConfigId }"
                @click="selectConfig(form)"
              >
                {{ form.name || '(未命名)' }}
                <el-tag v-if="form.isDefault" size="small" effect="plain">默认</el-tag>
              </li>
              <li v-if="canEdit">
                <el-button link type="primary" @click="addHttpConfig">+ 新增配置</el-button>
              </li>
            </ul>

            <div v-if="activeConfig" class="env-detail__config-form">
              <el-form label-width="110px" size="small" :disabled="!canEdit">
                <el-form-item label="名称">
                  <el-input v-model="activeConfig.name" maxlength="100" @input="markDirty" />
                </el-form-item>
                <el-form-item label="引用名 refName">
                  <el-input v-model="activeConfig.refName" placeholder="场景中通过该名引用此配置" @input="markDirty" />
                </el-form-item>
                <el-form-item label="Base URL">
                  <el-input v-model="activeConfig.baseUrl" placeholder="https://api.example.com" @input="markDirty" />
                </el-form-item>
                <el-form-item label="默认方法">
                  <el-select v-model="activeConfig.defaultMethod" @change="markDirty">
                    <el-option v-for="method in HTTP_METHOD_OPTIONS" :key="method" :label="method" :value="method" />
                  </el-select>
                </el-form-item>
                <el-form-item label="超时 (ms)">
                  <el-input-number v-model="activeConfig.timeoutMs" :min="0" :step="1000" @change="markDirty" />
                  <span class="env-detail__hint">响应超时</span>
                </el-form-item>
                <el-form-item label="连接超时 (ms)">
                  <el-input-number v-model="activeConfig.connectTimeoutMs" :min="0" :step="1000" @change="markDirty" />
                </el-form-item>
                <el-form-item label="跟随重定向">
                  <el-switch v-model="activeConfig.followRedirects" @change="markDirty" />
                </el-form-item>
                <el-form-item label="校验 SSL">
                  <el-switch v-model="activeConfig.verifySsl" @change="markDirty" />
                </el-form-item>
                <el-form-item label="默认配置">
                  <el-switch v-model="activeConfig.isDefault" @change="markDirty" />
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
                    @change="(value: boolean | string | number) => { header.enabled = value === true; markDirty() }"
                  />
                  <el-input v-model="header.key" placeholder="Header" :disabled="!canEdit" @input="markDirty" />
                  <el-input v-model="header.value" placeholder="Value" :disabled="!canEdit" @input="markDirty" />
                  <el-button
                    v-if="canEdit"
                    link
                    type="danger"
                    @click="activeConfig.headers?.splice(index, 1); markDirty()"
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
                <el-button
                  v-if="canEdit && configForms.length > 0"
                  size="small"
                  type="danger"
                  plain
                  @click="removeHttpConfig(activeConfig)"
                >
                  删除配置
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <!-- ============ 全局变量 ============ -->
        <el-tab-pane :label="`变量 (${variableRows.length})`" name="variables">
          <div class="env-detail__toolbar">
            <el-button size="small" type="primary" :disabled="!canEdit" @click="addVariableRow">新增变量</el-button>
            <el-button size="small" :disabled="!canEdit" @click="varImportDialogVisible = true">批量导入</el-button>
            <el-button size="small" @click="exportVariableRows">导出</el-button>
          </div>
          <el-table :data="variableRows" size="small" empty-text="暂无变量">
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
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-select v-if="row.id === editingVariableId && canEdit" v-model="row.type" size="small">
                  <el-option v-for="option in VARIABLE_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
                <span v-else>{{ VARIABLE_TYPE_OPTIONS.find((o) => o.value === row.type)?.label ?? row.type }}</span>
              </template>
            </el-table-column>
            <el-table-column label="取值">
              <template #default="{ row }">
                <el-input
                  v-if="row.id === editingVariableId && canEdit"
                  v-model="row.value"
                  size="small"
                  :placeholder="row.type === 'sensitive' ? '留空沿用旧值' : '变量取值'"
                  show-password
                />
                <span v-else-if="row.type === 'sensitive'" class="env-detail__mono">{{ row.hasValue ? SENSITIVE_MASK : '(未设置)' }}</span>
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
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <template v-if="row.id === editingVariableId && canEdit">
                  <el-button link type="primary" size="small" @click="editingVariableId = ''">完成</el-button>
                </template>
                <template v-else>
                  <el-button v-if="row.type === 'sensitive'" link size="small" @click="revealValue(row as ApiVariable)">
                    {{ revealingId === row.id ? '隐藏中' : '显示' }}
                  </el-button>
                  <el-button v-if="canEdit" link size="small" @click="editVariable(row as ApiVariable)">编辑</el-button>
                  <el-button v-if="canEdit" link type="danger" size="small" @click="removeVariableRow(row as ApiVariable)">删除</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div class="env-detail__toolbar env-detail__toolbar--right">
            <el-button size="small" type="primary" :loading="variablesSaving" :disabled="!canEdit" @click="saveVariables">
              保存变量
            </el-button>
          </div>
        </el-tab-pane>

        <!-- ============ 数据源 ============ -->
        <el-tab-pane :label="`数据源 (${dsForms.length})`" name="datasources">
          <div class="env-detail__toolbar">
            <el-button size="small" type="primary" :disabled="!canEdit" @click="openDsCreateDialog">新增数据源</el-button>
          </div>
          <el-table :data="dsForms" size="small" empty-text="暂无数据源">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="refName" label="引用名" min-width="100" />
            <el-table-column label="驱动" min-width="90">
              <template #default="{ row }">
                {{ DRIVER_OPTIONS.find((o) => o.driver === row.driver)?.label ?? row.driver }}
              </template>
            </el-table-column>
            <el-table-column prop="url" label="JDBC URL" min-width="240" show-overflow-tooltip />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" :loading="testingDsId === row.id" @click="runDsTest(row as DsForm)">测试</el-button>
                <el-button v-if="canEdit" link size="small" @click="openDsEditDialog(row as DsForm)">编辑</el-button>
                <el-button v-if="canEdit" link type="danger" size="small" @click="removeDataSource(row as DsForm)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
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

    <!-- 数据源新建/编辑 -->
    <el-dialog v-model="dsDialogVisible" :title="dsDialogMode === 'create' ? '新增数据源' : '编辑数据源'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="dsForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="引用名 refName">
          <el-input v-model="dsForm.refName" placeholder="场景中通过该名引用此数据源" />
        </el-form-item>
        <el-form-item label="驱动">
          <el-select v-model="dsForm.driver" @change="handleDriverChange">
            <el-option
              v-for="option in DRIVER_OPTIONS"
              :key="option.label"
              :label="option.label"
              :value="option.driver"
              :disabled="option.disabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" required>
          <el-input v-model="dsForm.url" type="textarea" :rows="2" :placeholder="selectedDriverOption?.urlExample" />
        </el-form-item>
        <el-form-item label="连接池上限">
          <el-input-number v-model="dsForm.maxPoolSize" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <p class="env-detail__dialog-tip">用户名与密码请拼入 URL；保存后可通过「测试」验证连通性</p>
      <template #footer>
        <el-button @click="dsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDsDialog">保存</el-button>
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
  padding: var(--space-md) var(--space-lg);
  min-height: 320px;
}

.env-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.env-detail__title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;

  h4 {
    margin: 0;
    font-size: var(--font-size-base);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.env-detail__name-input {
  width: 220px;
}

.env-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;

  .el-button + .el-button {
    margin-left: 0;
  }
}

.env-detail__sort {
  margin-right: var(--space-xs);
}

.env-detail__desc {
  margin: 6px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-detail__desc-edit {
  margin: 8px 0 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.env-detail__tabs {
  margin-top: var(--space-sm);

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
</style>




