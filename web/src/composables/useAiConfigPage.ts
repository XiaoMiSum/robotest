import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAiConfig,
  fetchAiProviders,
  fetchAiRebuildTask,
  fetchAiSettingsSchema,
  fetchAiStatistics,
  retryAiRebuildTask,
  saveAiConfig,
  testAiConnectivity,
} from '@/services/admin'
import type {
  AiConfig,
  AiConfigSavePayload,
  AiProviderPreset,
  AiSettingSchemaGroup,
  AiStatistics,
  AiTask,
} from '@/types'
import {
  buildConfigPayload,
  collectSettingErrors,
  isEmbeddingGroupEmpty,
  resolveModelHints,
  resolveUniqueParams,
} from '@/pages/admin/aiConfigForm'
import { extractUniqueValuesForScope } from '@/pages/admin/aiConfigForm'

const AUTO_SAVE_DEBOUNCE_MS = 800

/**
 * AI 配置页核心编排（01 §3.1.3 拆分）：配置加载、Embedding 保存/测试、总开关、自动保存。
 * 自动保存与配置状态共用 applyConfig/snapshotKey/定时器，故与 Embedding 同簇于本文件。
 */
export function useAiConfigPage() {
  const loading = ref(false)
  const saving = ref(false)
  const activeTab = ref('config')
  const providers = ref<AiProviderPreset[]>([])
  const config = ref<AiConfig | null>(null)

  const embeddingOpen = ref<string[]>([])
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

  const settingsSchema = ref<AiSettingSchemaGroup[]>([])
  const settingsForm = reactive<Record<string, unknown>>({})

  const testing = reactive({ embedding: false })
  const rebuildTask = ref<AiTask | null>(null)
  const statistics = ref<AiStatistics | null>(null)
  const statQuery = reactive({ groupBy: 'functionType' })

  const hydrated = ref(false)
  const isApplying = ref(false)
  const autoSaving = ref(false)
  const saveStatus = ref<'idle' | 'pending' | 'saving' | 'saved' | 'error'>('idle')
  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
  let savedResetTimer: ReturnType<typeof setTimeout> | null = null
  let pendingResave = false
  let lastSavedSnapshot = ''

  function presetOf(key: string): AiProviderPreset | undefined {
    return providers.value.find((p) => p.key === key)
  }

  const chatProviderOptions = computed(() => providers.value.filter((p) => p.scopes.includes('chat')))
  const embeddingProviderOptions = computed(() =>
    providers.value.filter((p) => p.scopes.includes('embedding')),
  )

  const embeddingUniqueParams = computed(() =>
    resolveUniqueParams(presetOf(form.embedding.provider), 'embedding'),
  )
  const embeddingModelHints = computed(() =>
    resolveModelHints(presetOf(form.embedding.provider), 'embedding'),
  )
  const embeddingConfigured = computed(() => !isEmbeddingGroupEmpty(form.embedding))
  const rebuildRetryable = computed(
    () => rebuildTask.value?.status === 'failed' || rebuildTask.value?.status === 'cancelled',
  )

  // 由 useAiChatModels 注入：开启总开关需至少一个已启用对话模型
  const chatModelsEnabledCount = ref(0)

  function cloneDefault(value: unknown): unknown {
    return value === null || typeof value !== 'object' ? value : JSON.parse(JSON.stringify(value))
  }

  function resetSetting(item: { key: string; defaultValue: unknown }) {
    settingsForm[item.key] = cloneDefault(item.defaultValue)
  }

  function applySettings(merged: Record<string, unknown>) {
    for (const group of settingsSchema.value) {
      for (const item of group.items) {
        const value = merged[item.key]
        settingsForm[item.key] = value !== undefined ? value : cloneDefault(item.defaultValue)
      }
    }
  }

  async function loadRebuildTask() {
    try {
      rebuildTask.value = await fetchAiRebuildTask()
    } catch {
      rebuildTask.value = null
    }
  }

  function applyConfig(loaded: AiConfig, opts: { preserveEmbedding?: boolean } = {}) {
    config.value = loaded
    form.enabled = loaded.enabled
    if (loaded.embedding && !opts.preserveEmbedding) {
      form.embedding.provider = loaded.embedding.provider
      form.embedding.baseUrl = loaded.embedding.baseUrl
      form.embedding.model = loaded.embedding.model
      form.embedding.dimension = loaded.embedding.dimension
      form.embedding.apiKey = ''
      form.embedding.apiKeyConfigured = loaded.embedding.apiKey.configured
      form.embedding.keySuffix = loaded.embedding.apiKey.keySuffix
      form.embedding.uniqueValues = extractUniqueValuesForScope(
        loaded.embedding.extraParams,
        'embedding',
        presetOf(loaded.embedding.provider),
      )
      form.embedding.customParams = JSON.stringify(loaded.embedding.extraParams ?? {}, null, 2)
    }
    applySettings(loaded.settings ?? {})
  }

  async function loadAll() {
    loading.value = true
    try {
      providers.value = await fetchAiProviders()
      const schema = await fetchAiSettingsSchema()
      const loaded = await fetchAiConfig()
      // 先赋值 settingsSchema 再同步填充 settingsForm：schema 先到才可渲染分组表单
      settingsSchema.value = schema
      if (loaded) applyConfig(loaded)
      else applySettings({})
      hydrated.value = true
      lastSavedSnapshot = snapshotKey()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载 AI 配置失败')
    } finally {
      loading.value = false
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

  // ==================== 自动保存 ====================

  function snapshotKey(): string {
    return JSON.stringify({ enabled: form.enabled, settings: settingsForm })
  }

  function flashSaved() {
    saveStatus.value = 'saved'
    if (savedResetTimer) clearTimeout(savedResetTimer)
    savedResetTimer = setTimeout(() => {
      if (saveStatus.value === 'saved') saveStatus.value = 'idle'
    }, 2500)
  }

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
      return err instanceof Error ? err.message : '保存失败'
    }
  }

  function scheduleAutoSave() {
    saveStatus.value = 'pending'
    if (autoSaveTimer) clearTimeout(autoSaveTimer)
    autoSaveTimer = setTimeout(() => {
      autoSaveTimer = null
      void runAutoSave()
    }, AUTO_SAVE_DEBOUNCE_MS)
  }

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
          embedding: { kind: 'saved', group: config.value?.embedding ?? null },
          settings: settingsForm,
        }),
      )
      if (message === null) {
        flashSaved()
      } else {
        saveStatus.value = 'error'
        ElMessage.error(message)
      }
    } finally {
      autoSaving.value = false
      if (pendingResave) {
        pendingResave = false
        scheduleAutoSave()
      }
    }
  }

  // ==================== Embedding 保存/测试 ====================

  async function handleSaveEmbedding() {
    if (form.enabled && chatModelsEnabledCount.value === 0) {
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
      } else {
        ElMessage.error(message)
      }
    } finally {
      saving.value = false
    }
  }

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

  // 总开关切换前钩子：开启需已启用对话模型，关闭需二次确认
  async function handleMasterBeforeChange(): Promise<boolean> {
    if (!form.enabled) {
      if (chatModelsEnabledCount.value === 0) {
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
    if (tab === 'statistics' && !statistics.value) void loadStatistics()
  }

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

  return {
    loading,
    saving,
    activeTab,
    providers,
    config,
    embeddingOpen,
    form,
    settingsSchema,
    settingsForm,
    testing,
    rebuildTask,
    statistics,
    statQuery,
    hydrated,
    chatModelsEnabledCount,
    presetOf,
    chatProviderOptions,
    embeddingProviderOptions,
    embeddingUniqueParams,
    embeddingModelHints,
    embeddingConfigured,
    rebuildRetryable,
    footerStatusText,
    footerStatusError,
    loadAll,
    applyConfig,
    resetSetting,
    loadRebuildTask,
    parseJsonObject,
    handleSaveEmbedding,
    handleTestEmbedding,
    handleMasterBeforeChange,
    handleRetryRebuild,
    loadStatistics,
    handleTabChange,
  }
}