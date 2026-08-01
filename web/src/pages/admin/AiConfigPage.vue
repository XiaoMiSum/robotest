<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAiChatModel,
  deleteAiChatModel,
  fetchAiChatModels,
  fetchAiConfig,
  fetchAiProviders,
  fetchAiRebuildTask,
  fetchAiSettingsSchema,
  fetchAiStatistics,
  retryAiRebuildTask,
  saveAiConfig,
  setAiChatModelDefault,
  setAiChatModelEnabled,
  testAiConnectivity,
  updateAiChatModel,
} from '@/services/admin'
import type {
  AiChatModel,
  AiChatModelSavePayload,
  AiConfig,
  AiConfigSavePayload,
  AiProviderPreset,
  AiSettingSchemaGroup,
  AiSettingSchemaItem,
  AiStatistics,
  AiTask,
} from '@/types'
import {
  buildConfigPayload,
  buildDefaultUniqueParams,
  collectSettingErrors,
  getByPath,
  isSettingModified,
  mergeExtraParams,
  resolveDefaultBaseUrl,
  resolveModelHints,
  resolveUniqueParams,
  weightsSum,
} from './aiConfigForm'

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('config')
const providers = ref<AiProviderPreset[]>([])
const config = ref<AiConfig | null>(null)
const expectedUpdatedAt = ref<string | null>(null)

// Embedding 卡片默认收起，可手动展开（v-model 为 el-collapse 已展开项名集合）
const embeddingOpen = ref<string[]>([])

// 自动保存状态（总开关 + 系统配置项，防抖提交；Embedding 组手动 [保存]）
const AUTO_SAVE_DEBOUNCE_MS = 800
const CONFLICT_MSG = 'AI 配置已被他人修改'
const hydrated = ref(false)
const isApplying = ref(false)
const autoSaving = ref(false)
const saveStatus = ref<'idle' | 'pending' | 'saving' | 'saved' | 'error'>('idle')
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
let savedResetTimer: ReturnType<typeof setTimeout> | null = null
let pendingResave = false
let lastSavedSnapshot = ''

const form = reactive({
  enabled: false,
  embedding: {
    provider: '',
    baseUrl: '',
    model: '',
    dimension: null as number | null,
    apiKey: '',
    apiKeyConfigured: false,
    keySuffix: '' as string | null,
    uniqueValues: {} as Record<string, unknown>,
    customParams: '{}',
  },
})

// 对话模型列表与新建/编辑弹窗
const chatModels = ref<AiChatModel[]>([])
const modelDialogVisible = ref(false)
const modelDialogMode = ref<'create' | 'edit'>('create')
const editingModelId = ref<string | null>(null)
const modelExpectedUpdatedAt = ref<string | null>(null)
const modelForm = reactive({
  name: '',
  provider: 'custom',
  baseUrl: '',
  model: '',
  apiKey: '',
  apiKeyConfigured: false,
  keySuffix: '' as string | null,
  uniqueValues: {} as Record<string, unknown>,
  customParams: '{}',
})

// 系统配置项分组表单
const settingsSchema = ref<AiSettingSchemaGroup[]>([])
const settingsForm = reactive<Record<string, unknown>>({})

const testing = reactive({ embedding: false, modelDialog: false })
const rowTestingId = ref<string | null>(null)

const rebuildTask = ref<AiTask | null>(null)

const statistics = ref<AiStatistics | null>(null)
const statQuery = reactive({ groupBy: 'functionType' })

const chatProviderOptions = computed(() => providers.value.filter((p) => p.scopes.includes('chat')))
const embeddingProviderOptions = computed(() =>
  providers.value.filter((p) => p.scopes.includes('embedding')),
)

function presetOf(key: string): AiProviderPreset | undefined {
  return providers.value.find((p) => p.key === key)
}

const modelUniqueParams = computed(() => resolveUniqueParams(presetOf(modelForm.provider), 'chat'))
const modelModelHints = computed(() => resolveModelHints(presetOf(modelForm.provider), 'chat'))
const embeddingUniqueParams = computed(() =>
  resolveUniqueParams(presetOf(form.embedding.provider), 'embedding'),
)
const embeddingModelHints = computed(() =>
  resolveModelHints(presetOf(form.embedding.provider), 'embedding'),
)

const rebuildRetryable = computed(
  () => rebuildTask.value?.status === 'failed' || rebuildTask.value?.status === 'cancelled',
)

const settingsError = computed(() => collectSettingErrors(settingsSchema.value, settingsForm))

const footerStatusText = computed(() => {
  if (saveStatus.value === 'saving') return '保存中…'
  if (settingsError.value) return `存在校验错误：${settingsError.value}，修改未保存`
  if (saveStatus.value === 'error') return '自动保存失败，请重试'
  if (saveStatus.value === 'pending') return '修改待保存…'
  if (saveStatus.value === 'saved') return '已自动保存'
  return ''
})

const footerStatusError = computed(
  () => Boolean(settingsError.value) || saveStatus.value === 'error',
)

async function loadAll() {
  loading.value = true
  try {
    providers.value = await fetchAiProviders()
    const schema = await fetchAiSettingsSchema()
    const loaded = await fetchAiConfig()
    // 先赋值 settingsSchema 再同步填充 settingsForm（两步间无 await），
    // 避免渲染早于表单数据导致 weights 读取 undefined 崩溃
    settingsSchema.value = schema
    if (loaded) applyConfig(loaded)
    else applySettings({})
    chatModels.value = await fetchAiChatModels()
    await loadRebuildTask()
    hydrated.value = true
    lastSavedSnapshot = snapshotKey()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载 AI 配置失败')
  } finally {
    loading.value = false
  }
}

function applyConfig(loaded: AiConfig, opts: { preserveEmbedding?: boolean } = {}) {
  config.value = loaded
  expectedUpdatedAt.value = loaded.updatedAt
  form.enabled = loaded.enabled
  // 自动保存路径不覆盖 form.embedding，避免打断未保存的 Embedding 编辑
  if (loaded.embedding && !opts.preserveEmbedding) {
    form.embedding.provider = loaded.embedding.provider
    form.embedding.baseUrl = loaded.embedding.baseUrl
    form.embedding.model = loaded.embedding.model
    form.embedding.dimension = loaded.embedding.dimension
    form.embedding.apiKey = ''
    form.embedding.apiKeyConfigured = loaded.embedding.apiKey.configured
    form.embedding.keySuffix = loaded.embedding.apiKey.keySuffix
    form.embedding.uniqueValues = extractUniqueValues(
      loaded.embedding.extraParams,
      'embedding',
      loaded.embedding.provider,
    )
    form.embedding.customParams = JSON.stringify(loaded.embedding.extraParams ?? {}, null, 2)
  }
  applySettings(loaded.settings ?? {})
}

// 深拷贝内置默认值：settingsSchema 已被 Vue 响应式代理，structuredClone 无法克隆 Proxy；
// 配置项默认值均为 JSON 安全值，JSON round-trip 既深拷贝又剥离响应式
function cloneDefault(value: unknown): unknown {
  return value === null || typeof value !== 'object' ? value : JSON.parse(JSON.stringify(value))
}

// 按 schema 用「落库合并视图值 → 默认值」初始化分组表单
function applySettings(merged: Record<string, unknown>) {
  for (const group of settingsSchema.value) {
    for (const item of group.items) {
      const value = merged[item.key]
      settingsForm[item.key] = value !== undefined ? value : cloneDefault(item.defaultValue)
    }
  }
}

function extractUniqueValues(
  extraParams: Record<string, unknown>,
  scope: 'chat' | 'embedding',
  provider: string,
): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const param of resolveUniqueParams(presetOf(provider), scope)) {
    const value = param.key.includes('.')
      ? getByPath(extraParams ?? {}, param.key)
      : (extraParams ?? {})[param.key]
    values[param.key] = value !== undefined ? value : param.defaultValue
  }
  return values
}

async function loadRebuildTask() {
  try {
    rebuildTask.value = await fetchAiRebuildTask()
  } catch {
    rebuildTask.value = null
  }
}

function parseJsonObject(text: string, label: string): Record<string, unknown> {
  if (!text.trim()) return {}
  const parsed: unknown = JSON.parse(text)
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`${label}必须为 JSON 对象`)
  }
  return parsed as Record<string, unknown>
}

// ==================== 对话模型：新建/编辑弹窗 ====================

function openCreateModel() {
  modelDialogMode.value = 'create'
  editingModelId.value = null
  modelExpectedUpdatedAt.value = null
  modelForm.name = ''
  modelForm.provider = 'custom'
  modelForm.baseUrl = ''
  modelForm.model = ''
  modelForm.apiKey = ''
  modelForm.apiKeyConfigured = false
  modelForm.keySuffix = ''
  modelForm.uniqueValues = buildDefaultUniqueParams(resolveUniqueParams(presetOf('custom'), 'chat'))
  modelForm.customParams = '{}'
  modelDialogVisible.value = true
}

function openEditModel(row: AiChatModel) {
  modelDialogMode.value = 'edit'
  editingModelId.value = row.id
  modelExpectedUpdatedAt.value = row.updatedAt
  modelForm.name = row.name
  modelForm.provider = row.provider
  modelForm.baseUrl = row.baseUrl
  modelForm.model = row.model
  modelForm.apiKey = ''
  modelForm.apiKeyConfigured = row.apiKey.configured
  modelForm.keySuffix = row.apiKey.keySuffix
  modelForm.uniqueValues = extractUniqueValues(row.extraParams, 'chat', row.provider)
  modelForm.customParams = JSON.stringify(row.extraParams ?? {}, null, 2)
  modelDialogVisible.value = true
}

async function handleModelProviderChange(next: string) {
  if (modelForm.baseUrl || Object.keys(modelForm.uniqueValues).length) {
    try {
      await ElMessageBox.confirm(
        '切换供应商将重置服务地址与独有配置项（模型名与密钥保留待核对），是否继续？',
        '切换供应商',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  modelForm.baseUrl = resolveDefaultBaseUrl(presetOf(next), 'chat')
  modelForm.uniqueValues = buildDefaultUniqueParams(resolveUniqueParams(presetOf(next), 'chat'))
}

function buildModelPayload(): AiChatModelSavePayload {
  const custom = parseJsonObject(modelForm.customParams, '对话高级参数')
  return {
    name: modelForm.name,
    provider: modelForm.provider,
    baseUrl: modelForm.baseUrl,
    model: modelForm.model,
    apiKey: modelForm.apiKey || null,
    extraParams: mergeExtraParams(modelForm.uniqueValues, custom),
    expectedUpdatedAt: modelExpectedUpdatedAt.value,
  }
}

async function handleModelSave() {
  let payload: AiChatModelSavePayload
  try {
    payload = buildModelPayload()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '参数格式错误')
    return
  }
  saving.value = true
  try {
    if (modelDialogMode.value === 'create') {
      await createAiChatModel(payload)
      ElMessage.success('已新建对话模型')
    } else if (editingModelId.value) {
      await updateAiChatModel(editingModelId.value, payload)
      ElMessage.success('已保存对话模型')
    }
    modelDialogVisible.value = false
    chatModels.value = await fetchAiChatModels()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleModelDialogTest() {
  testing.modelDialog = true
  try {
    const custom = parseJsonObject(modelForm.customParams, '对话高级参数')
    const result = await testAiConnectivity({
      target: 'chat',
      chat: {
        provider: modelForm.provider,
        baseUrl: modelForm.baseUrl,
        model: modelForm.model,
        apiKey: modelForm.apiKey || null,
        extraParams: mergeExtraParams(modelForm.uniqueValues, custom),
      },
    })
    if (result.ok) {
      ElMessage.success(`连通成功（${result.latencyMs ?? '-'}ms）：${result.detail ?? ''}`)
    } else {
      ElMessage.warning(`连通失败：${result.detail ?? '未知错误'}`)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '连通性测试失败')
  } finally {
    testing.modelDialog = false
  }
}

// ==================== 对话模型：行内操作 ====================

async function handleRowTest(row: AiChatModel) {
  rowTestingId.value = row.id
  try {
    const result = await testAiConnectivity({ target: 'chat', modelId: row.id })
    if (result.ok) {
      ElMessage.success(`「${row.name}」连通成功（${result.latencyMs ?? '-'}ms）`)
    } else {
      ElMessage.warning(`「${row.name}」连通失败：${result.detail ?? '未知错误'}`)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '连通性测试失败')
  } finally {
    rowTestingId.value = null
  }
}

async function handleSetDefault(row: AiChatModel) {
  try {
    await ElMessageBox.confirm(`确认将「${row.name}」设为系统默认模型？`, '设为默认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await setAiChatModelDefault(row.id)
    ElMessage.success('已更新系统默认模型')
    chatModels.value = await fetchAiChatModels()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

async function handleToggleEnabled(row: AiChatModel) {
  const next = !row.enabled
  if (!next) {
    try {
      await ElMessageBox.confirm(
        `停用「${row.name}」后，此前选择该模型的用户将自动回退系统默认。确认停用？`,
        '停用模型',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    await setAiChatModelEnabled(row.id, next)
    ElMessage.success(next ? '已启用' : '已停用')
    chatModels.value = await fetchAiChatModels()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

async function handleDeleteModel(row: AiChatModel) {
  try {
    await ElMessageBox.confirm(`删除「${row.name}」后不可恢复，确认删除？`, '删除模型', {
      type: 'error',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  try {
    await deleteAiChatModel(row.id)
    ElMessage.success('已删除')
    chatModels.value = await fetchAiChatModels()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

// ==================== 系统配置项 ====================

function settingModified(item: AiSettingSchemaItem): boolean {
  return isSettingModified(item, settingsForm[item.key])
}

function resetSetting(item: AiSettingSchemaItem) {
  settingsForm[item.key] = cloneDefault(item.defaultValue)
}

function currentWeightsSum(item: AiSettingSchemaItem): number {
  return weightsSum(settingsForm[item.key])
}

// ==================== 保存（自动 + Embedding 手动）/ Embedding 测试 ====================

// 自动保存仅跟踪总开关与系统配置项；Embedding 组表单值不参与，须手动 [保存]
function snapshotKey(): string {
  return JSON.stringify({ enabled: form.enabled, settings: settingsForm })
}

function scheduleAutoSave() {
  saveStatus.value = 'pending'
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => {
    autoSaveTimer = null
    void runAutoSave()
  }, AUTO_SAVE_DEBOUNCE_MS)
}

function flashSaved() {
  saveStatus.value = 'saved'
  if (savedResetTimer) clearTimeout(savedResetTimer)
  savedResetTimer = setTimeout(() => {
    if (saveStatus.value === 'saved') saveStatus.value = 'idle'
  }, 2500)
}

// 统一提交入口：成功返回 null，失败返回错误文案（冲突时已刷新配置）
async function performSave(payload: AiConfigSavePayload): Promise<string | null> {
  try {
    const saved = await saveAiConfig(payload)
    isApplying.value = true
    applyConfig(saved, { preserveEmbedding: true })
    lastSavedSnapshot = snapshotKey()
    isApplying.value = false
    await loadRebuildTask()
    return null
  } catch (err) {
    const message = err instanceof Error ? err.message : '保存失败'
    if (message.includes(CONFLICT_MSG)) {
      await reloadConfig()
    }
    return message
  }
}

// 乐观锁冲突：以服务端最新配置整体重灌（放弃未保存的 Embedding 编辑）
async function reloadConfig() {
  try {
    const fresh = await fetchAiConfig()
    isApplying.value = true
    if (fresh) applyConfig(fresh)
    else applySettings({})
    lastSavedSnapshot = snapshotKey()
    isApplying.value = false
    ElMessage.warning('配置已被其他管理员修改，已刷新为最新配置')
  } catch {
    ElMessage.error('刷新配置失败，请手动刷新页面')
  }
}

// 总开关 + 系统配置项自动保存：校验失败不落库，仅提示
async function runAutoSave() {
  if (!hydrated.value || autoSaving.value || saving.value) {
    pendingResave = true
    return
  }
  if (snapshotKey() === lastSavedSnapshot) return
  const error = collectSettingErrors(settingsSchema.value, settingsForm)
  if (error) {
    saveStatus.value = 'error'
    return
  }
  autoSaving.value = true
  saveStatus.value = 'saving'
  try {
    const message = await performSave(
      buildConfigPayload({
        enabled: form.enabled,
        // Embedding 取已保存配置，避免把未保存的 Embedding 编辑一并提交
        embedding: { kind: 'saved', group: config.value?.embedding ?? null },
        settings: settingsForm,
        expectedUpdatedAt: expectedUpdatedAt.value,
      }),
    )
    if (message === null) {
      flashSaved()
    } else {
      saveStatus.value = 'error'
      if (!message.includes(CONFLICT_MSG)) {
        ElMessage.error(message)
      }
    }
  } finally {
    autoSaving.value = false
    if (pendingResave) {
      pendingResave = false
      scheduleAutoSave()
    }
  }
}

// Embedding 组手动保存（可能触发向量重建，须用户显式操作）
async function handleSaveEmbedding() {
  if (form.enabled && chatModels.value.filter((m) => m.enabled).length === 0) {
    ElMessage.warning('开启 AI 前请先新建并启用至少一个对话模型')
    return
  }
  const error = collectSettingErrors(settingsSchema.value, settingsForm)
  if (error) {
    ElMessage.error(error)
    return
  }
  let payload: AiConfigSavePayload
  try {
    payload = buildConfigPayload({
      enabled: form.enabled,
      embedding: { kind: 'form', group: form.embedding },
      settings: settingsForm,
      expectedUpdatedAt: expectedUpdatedAt.value,
    })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '参数格式错误')
    return
  }
  saving.value = true
  try {
    const message = await performSave(payload)
    if (message === null) {
      ElMessage.success('保存成功')
    } else if (!message.includes(CONFLICT_MSG)) {
      ElMessage.error(message)
    }
  } finally {
    saving.value = false
  }
}

// 总开关切换前钩子：开启需已启用对话模型，关闭需二次确认（自动保存紧随其后）
async function handleMasterBeforeChange(): Promise<boolean> {
  if (!form.enabled) {
    if (chatModels.value.filter((m) => m.enabled).length === 0) {
      ElMessage.warning('开启 AI 前请先新建并启用至少一个对话模型')
      return false
    }
    return true
  }
  try {
    await ElMessageBox.confirm(
      '关闭后将隐藏全部 AI 入口，进行中的 AI 调用与任务将被中断。确认关闭？',
      '关闭 AI 能力',
      { type: 'warning', confirmButtonText: '确认关闭' },
    )
    return true
  } catch {
    return false
  }
}

watch(
  () => snapshotKey(),
  () => {
    if (!hydrated.value || isApplying.value) return
    if (snapshotKey() === lastSavedSnapshot) return
    scheduleAutoSave()
  },
)

onBeforeUnmount(() => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
    autoSaveTimer = null
    if (hydrated.value && snapshotKey() !== lastSavedSnapshot) {
      void runAutoSave()
    }
  }
})

async function handleTestEmbedding() {
  testing.embedding = true
  try {
    const result = await testAiConnectivity({
      target: 'embedding',
      embedding: {
        provider: form.embedding.provider,
        baseUrl: form.embedding.baseUrl,
        model: form.embedding.model,
        dimension: form.embedding.dimension,
        apiKey: form.embedding.apiKey || null,
      },
    })
    if (result.ok) {
      ElMessage.success(`连通成功（${result.latencyMs ?? '-'}ms）：${result.detail ?? ''}`)
    } else {
      ElMessage.warning(`连通失败：${result.detail ?? '未知错误'}`)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '连通性测试失败')
  } finally {
    testing.embedding = false
  }
}

async function handleRetryRebuild() {
  try {
    await retryAiRebuildTask()
    ElMessage.success('已重新入队向量重建任务')
    await loadRebuildTask()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  }
}

async function loadStatistics() {
  try {
    statistics.value = await fetchAiStatistics({ groupBy: statQuery.groupBy })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载统计失败')
  }
}

function handleTabChange(tab: string | number) {
  if (tab === 'statistics' && !statistics.value) loadStatistics()
}

onMounted(loadAll)
</script>

<template>
  <div class="ai-config-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="AI 配置" name="config">
        <el-form v-loading="loading" label-width="120px" class="ai-config-form">
          <el-card shadow="never" class="ai-config-page__card">
            <div class="ai-config-page__master">
              <div class="ai-config-page__master-icon">
                <el-icon :size="22"><Cpu /></el-icon>
              </div>
              <div class="ai-config-page__master-text">
                <div class="ai-config-page__master-title">AI 能力总开关</div>
                <div class="ai-config-page__master-hint">
                  关闭后前端隐藏全部 AI 入口，进行中任务被取消；开启需已启用至少一个对话模型
                </div>
              </div>
              <el-switch v-model="form.enabled" size="large" :before-change="handleMasterBeforeChange" />
            </div>
          </el-card>

          <el-card shadow="never" class="ai-config-page__card">
            <template #header>
              <div class="ai-config-page__card-header">
                <el-icon class="ai-config-page__card-icon"><ChatDotRound /></el-icon>
                <span>对话模型</span>
                <el-button class="ai-config-page__card-extra" size="small" type="primary" @click="openCreateModel">
                  <el-icon><Plus /></el-icon>新建模型
                </el-button>
              </div>
            </template>
            <el-table :data="chatModels" size="small" class="ai-config-page__model-table">
              <el-table-column prop="name" label="名称" min-width="140" />
              <el-table-column prop="provider" label="供应商" width="120" />
              <el-table-column prop="model" label="模型名" min-width="140" />
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                    {{ row.enabled ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="默认" width="70" align="center">
                <template #default="{ row }">
                  <el-icon v-if="row.isDefault" class="ai-config-page__star"><StarFilled /></el-icon>
                </template>
              </el-table-column>
              <el-table-column label="操作" min-width="260">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openEditModel(row as AiChatModel)">编辑</el-button>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :loading="rowTestingId === row.id"
                    @click="handleRowTest(row as AiChatModel)"
                  >
                    测试
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :disabled="row.isDefault || !row.enabled"
                    @click="handleSetDefault(row as AiChatModel)"
                  >
                    设为默认
                  </el-button>
                  <el-button
                    link
                    type="warning"
                    size="small"
                    :disabled="row.isDefault && row.enabled"
                    @click="handleToggleEnabled(row as AiChatModel)"
                  >
                    {{ row.enabled ? '停用' : '启用' }}
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :disabled="row.isDefault"
                    @click="handleDeleteModel(row as AiChatModel)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <span class="ai-config-page__empty">尚无对话模型，点击右上角新建</span>
              </template>
            </el-table>
          </el-card>

          <el-card shadow="never" class="ai-config-page__card">
            <el-collapse v-model="embeddingOpen" class="ai-config-page__embedding">
              <el-collapse-item name="embedding">
                <template #title>
                  <div class="ai-config-page__card-header ai-config-page__embedding-header">
                    <el-icon class="ai-config-page__card-icon"><DataLine /></el-icon>
                    <span>Embedding 模型</span>
                  </div>
                </template>
                <el-form-item label="供应商">
                  <el-select v-model="form.embedding.provider" @change="(v: string) => (form.embedding.provider = v)">
                    <el-option
                      v-for="p in embeddingProviderOptions"
                      :key="p.key"
                      :label="p.name"
                      :value="p.key"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="服务地址">
                  <el-input v-model="form.embedding.baseUrl" />
                </el-form-item>
                <el-form-item label="模型名">
                  <el-select v-model="form.embedding.model" filterable allow-create default-first-option>
                    <el-option v-for="m in embeddingModelHints" :key="m" :label="m" :value="m" />
                  </el-select>
                </el-form-item>
                <el-form-item label="向量维度">
                  <el-input-number v-model="form.embedding.dimension" :min="1" :max="2000" />
                </el-form-item>
                <el-form-item label="API 密钥">
                  <el-input
                    v-model="form.embedding.apiKey"
                    type="password"
                    show-password
                    :placeholder="
                      form.embedding.apiKeyConfigured
                        ? `已配置（末位 ${form.embedding.keySuffix ?? '****'}），留空不修改`
                        : '请输入密钥'
                    "
                  />
                </el-form-item>
                <el-form-item v-for="param in embeddingUniqueParams" :key="param.key" :label="param.label">
                  <el-input v-model="form.embedding.uniqueValues[param.key] as string" />
                  <span class="ai-config-page__hint">{{ param.description }}</span>
                </el-form-item>
                <el-collapse class="ai-config-page__advanced">
                  <el-collapse-item title="高级自定义参数（JSON）" name="embeddingAdvanced">
                    <el-input v-model="form.embedding.customParams" type="textarea" :rows="4" />
                  </el-collapse-item>
                </el-collapse>
                <div class="ai-config-page__embedding-actions">
                  <el-button :loading="testing.embedding" @click="handleTestEmbedding">
                    <el-icon><Connection /></el-icon>连通性测试
                  </el-button>
                  <el-button type="primary" :loading="saving" @click="handleSaveEmbedding">保存</el-button>
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-card>

          <el-card shadow="never" class="ai-config-page__card">
            <template #header>
              <div class="ai-config-page__card-header">
                <el-icon class="ai-config-page__card-icon"><Setting /></el-icon>
                <span>系统配置项</span>
              </div>
            </template>
            <div v-for="group in settingsSchema" :key="group.group" class="ai-config-page__setting-group">
              <div class="ai-config-page__setting-group-title">{{ group.groupLabel }}</div>
              <el-form-item v-for="item in group.items" :key="item.key" :label="item.label">
                <div class="ai-config-page__setting-control">
                  <!-- 权重组合 -->
                  <template v-if="item.type === 'object'">
                    <div v-if="settingsForm[item.key]" class="ai-config-page__weights">
                      <el-input-number
                        v-for="sub in ['w1', 'w2', 'w3']"
                        :key="sub"
                        v-model="(settingsForm[item.key] as Record<string, number>)[sub]"
                        :min="0"
                        :max="1"
                        :step="0.1"
                        :controls="false"
                      />
                      <span
                        class="ai-config-page__weights-sum"
                        :class="{ 'is-error': Math.abs(currentWeightsSum(item) - 1) > 0.001 }"
                      >
                        Σ {{ currentWeightsSum(item).toFixed(2) }}
                      </span>
                    </div>
                  </template>
                  <!-- 多选 -->
                  <el-select
                    v-else-if="item.type === 'string[]'"
                    v-model="settingsForm[item.key] as string[]"
                    multiple
                    class="ai-config-page__setting-multi"
                  >
                    <el-option v-for="opt in item.options ?? []" :key="opt" :label="opt" :value="opt" />
                  </el-select>
                  <!-- 数字 -->
                  <el-input-number
                    v-else
                    v-model="settingsForm[item.key] as number"
                    :min="item.min ?? undefined"
                    :max="item.max ?? undefined"
                    :step="item.step ?? 1"
                  />
                  <el-tag v-if="settingModified(item)" size="small" type="warning" class="ai-config-page__modified">
                    已修改
                  </el-tag>
                  <el-button
                    v-if="settingModified(item)"
                    link
                    type="primary"
                    size="small"
                    @click="resetSetting(item)"
                  >
                    恢复默认
                  </el-button>
                </div>
                <span class="ai-config-page__hint">{{ item.description }}（默认 {{ item.defaultValue }}）</span>
              </el-form-item>
            </div>
          </el-card>

          <el-alert
            v-if="rebuildTask"
            class="ai-config-page__rebuild"
            :type="rebuildRetryable ? 'error' : 'info'"
            :closable="false"
          >
            向量重建任务状态：{{ rebuildTask.status }}（进度 {{ rebuildTask.progress }}%）
            <span v-if="rebuildTask.errorMessage">，原因：{{ rebuildTask.errorMessage }}</span>
            <el-button v-if="rebuildRetryable" size="small" type="primary" link @click="handleRetryRebuild">
              重试
            </el-button>
          </el-alert>

          <div class="ai-config-page__footer">
            <span
              v-if="footerStatusText"
              class="ai-config-page__footer-status"
              :class="{ 'is-error': footerStatusError }"
            >
              {{ footerStatusText }}
            </span>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="调用统计" name="statistics">
        <div class="ai-config-page__stat-bar">
          <el-radio-group v-model="statQuery.groupBy" @change="loadStatistics">
            <el-radio-button value="functionType">按功能</el-radio-button>
            <el-radio-button value="workspace">按空间</el-radio-button>
            <el-radio-button value="day">按日期</el-radio-button>
            <el-radio-button value="model">按模型</el-radio-button>
          </el-radio-group>
        </div>
        <template v-if="statistics">
          <div class="ai-config-page__stat-grid">
            <div class="stat-card stat-card--primary">
              <div class="stat-card__icon"><el-icon :size="22"><DataAnalysis /></el-icon></div>
              <div>
                <div class="stat-card__label">总调用次数</div>
                <div class="stat-card__value">{{ statistics.totalCalls }}</div>
              </div>
            </div>
            <div class="stat-card stat-card--teal">
              <div class="stat-card__icon"><el-icon :size="22"><Coin /></el-icon></div>
              <div>
                <div class="stat-card__label">总 Token</div>
                <div class="stat-card__value">{{ statistics.totalTokens }}</div>
              </div>
            </div>
            <div class="stat-card stat-card--danger">
              <div class="stat-card__icon"><el-icon :size="22"><WarningFilled /></el-icon></div>
              <div>
                <div class="stat-card__label">失败次数</div>
                <div class="stat-card__value">{{ statistics.failedCalls }}</div>
              </div>
            </div>
          </div>
          <el-card shadow="never">
            <el-table :data="statistics.items" stripe>
              <el-table-column prop="key" label="维度" />
              <el-table-column prop="calls" label="调用次数" width="120" align="right" />
              <el-table-column prop="tokens" label="Token" width="140" align="right" />
              <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" align="right" />
              <el-table-column prop="failed" label="失败" width="100" align="right" />
            </el-table>
          </el-card>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="modelDialogVisible"
      :title="modelDialogMode === 'create' ? '新建对话模型' : '编辑对话模型'"
      width="560px"
    >
      <el-form label-width="90px">
        <el-form-item label="显示名">
          <el-input v-model="modelForm.name" placeholder="如 GPT-4o、DeepSeek-V3" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="modelForm.provider" @change="handleModelProviderChange">
            <el-option v-for="p in chatProviderOptions" :key="p.key" :label="p.name" :value="p.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址">
          <el-input v-model="modelForm.baseUrl" placeholder="OpenAI 兼容根路径，不含 /chat/completions" />
        </el-form-item>
        <el-form-item label="模型名">
          <el-select v-model="modelForm.model" filterable allow-create default-first-option placeholder="选择或输入模型名">
            <el-option v-for="m in modelModelHints" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 密钥">
          <el-input
            v-model="modelForm.apiKey"
            type="password"
            show-password
            :placeholder="
              modelForm.apiKeyConfigured
                ? `已配置（末位 ${modelForm.keySuffix ?? '****'}），留空不修改`
                : '请输入密钥'
            "
          />
        </el-form-item>
        <el-form-item v-for="param in modelUniqueParams" :key="param.key" :label="param.label">
          <el-switch
            v-if="param.type === 'boolean'"
            v-model="modelForm.uniqueValues[param.key] as boolean"
          />
          <el-select
            v-else-if="param.type === 'enum'"
            v-model="modelForm.uniqueValues[param.key] as string"
          >
            <el-option v-for="opt in param.options" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-input-number
            v-else-if="param.type === 'number'"
            v-model="modelForm.uniqueValues[param.key] as number"
          />
          <el-input v-else v-model="modelForm.uniqueValues[param.key] as string" />
          <span class="ai-config-page__hint">{{ param.description }}</span>
        </el-form-item>
        <el-collapse class="ai-config-page__advanced">
          <el-collapse-item title="高级自定义参数（JSON）" name="modelAdvanced">
            <el-input v-model="modelForm.customParams" type="textarea" :rows="4" />
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button :loading="testing.modelDialog" @click="handleModelDialogTest">连通性测试</el-button>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleModelSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ai-config-form {
  max-width: 920px;
}

.ai-config-page__card {
  margin-bottom: var(--space-lg);
}

.ai-config-page__card :deep(.el-card__header) {
  padding: 12px 20px !important;
}

.ai-config-page__card :deep(.el-form-item:last-of-type) {
  margin-bottom: 0;
}

.ai-config-page__card-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-config-page__card-icon {
  color: var(--color-primary-500);
  font-size: 16px;
}

.ai-config-page__card-extra {
  margin-left: auto;

  .el-icon {
    margin-right: 4px;
  }
}

.ai-config-page__master {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.ai-config-page__master-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
  flex-shrink: 0;
}

.ai-config-page__master-text {
  flex: 1;
}

.ai-config-page__master-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-config-page__master-hint {
  font-size: 12px;
  color: var(--color-neutral-500);
  margin-top: 2px;
}

.ai-config-page__model-table :deep(.el-select),
.ai-config-page__model-table :deep(.el-input-number) {
  width: 100%;
}

.ai-config-page__star {
  color: var(--color-warning, #e6a23c);
}

.ai-config-page__empty {
  font-size: 13px;
  color: var(--color-neutral-400);
}

.ai-config-page__hint {
  width: 100%;
  line-height: 1.5;
  margin-top: 4px;
  color: var(--color-neutral-400);
  font-size: 12px;
}

.ai-config-page__embedding {
  border: none;

  :deep(.el-collapse-item__header) {
    height: auto;
    line-height: 1.5;
    padding: 8px 0;
    border-bottom: 1px solid var(--color-neutral-100);
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
  }
}

.ai-config-page__embedding-header {
  width: 100%;
}

.ai-config-page__embedding-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}

.ai-config-page__advanced {
  border: none;

  :deep(.el-collapse-item__header) {
    font-size: 13px;
    color: var(--color-neutral-500);
    border-bottom: none;
    height: 36px;
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
  }
}

.ai-config-page__setting-group {
  margin-bottom: var(--space-md);
}

.ai-config-page__setting-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-neutral-700);
  margin-bottom: var(--space-sm);
  padding-left: var(--space-xs);
  border-left: 3px solid var(--color-primary-400);
}

.ai-config-page__setting-control {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-config-page__weights {
  display: flex;
  align-items: center;
  gap: var(--space-sm);

  :deep(.el-input-number) {
    width: 90px;
  }
}

.ai-config-page__weights-sum {
  font-size: 12px;
  color: var(--color-neutral-500);

  &.is-error {
    color: var(--color-danger);
    font-weight: 600;
  }
}

.ai-config-page__setting-multi {
  min-width: 260px;
}

.ai-config-page__modified {
  flex-shrink: 0;
}

.ai-config-page__rebuild {
  margin-bottom: var(--space-lg);
}

.ai-config-page__footer {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-md) 0;
}

.ai-config-page__footer-status {
  font-size: 12px;
  color: var(--color-neutral-400);

  &.is-error {
    color: var(--color-danger);
  }
}

.ai-config-page__stat-bar {
  margin-bottom: var(--space-lg);
}

.ai-config-page__stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-lg) var(--space-xl);
  background: var(--color-neutral-0);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-neutral-200);
  box-shadow: var(--shadow-card);
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card--primary .stat-card__icon {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}
.stat-card--primary .stat-card__value {
  color: var(--color-primary-600);
}

.stat-card--teal .stat-card__icon {
  background: #f0fdfa;
  color: #0d9488;
}
.stat-card--teal .stat-card__value {
  color: #0d9488;
}

.stat-card--danger .stat-card__icon {
  background: var(--color-danger-light);
  color: var(--color-danger);
}
.stat-card--danger .stat-card__value {
  color: var(--color-danger);
}

.stat-card__label {
  font-size: 12px;
  color: var(--color-neutral-500);
  font-weight: 500;
  margin-bottom: 2px;
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
</style>
