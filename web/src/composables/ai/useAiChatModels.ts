import { computed, reactive, ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAiChatModel,
  deleteAiChatModel,
  fetchAiChatModels,
  setAiChatModelDefault,
  setAiChatModelEnabled,
  testAiConnectivity,
  updateAiChatModel,
} from '@/services/admin'
import type { AiChatModel, AiChatModelSavePayload, AiModelFormState, AiProviderPreset } from '@/types'
import {
  buildDefaultUniqueParams,
  extractUniqueValuesForScope,
  mergeExtraParams,
  resolveDefaultBaseUrl,
  resolveModelHints,
  resolveUniqueParams,
} from '@/pages/admin/aiConfigForm'

/**
 * AI 配置页对话模型管理（01 §3.1.3 拆分）：列表 + 新建/编辑弹窗 + 行内操作。
 * 纯逻辑层，不渲染 UI；弹窗可见性与保存态由页面组合到 AiModelFormDialog。
 */
export function useAiChatModels(deps: {
  presetOf: (key: string) => AiProviderPreset | undefined
  parsing: { parseJsonObject: (text: string, label: string) => Record<string, unknown> }
  saving: { saving: Ref<boolean> }
}) {
  const chatModels = ref<AiChatModel[]>([])
  const modelDialogVisible = ref(false)
  const modelDialogMode = ref<'create' | 'edit'>('create')
  const editingModelId = ref<string | null>(null)
  const modelForm = reactive<AiModelFormState>({
    name: '',
    provider: 'custom',
    baseUrl: '',
    model: '',
    apiKey: '',
    apiKeyConfigured: false,
    keySuffix: '',
    uniqueValues: {},
    customParams: '{}',
  })
  const testing = reactive({ modelDialog: false })
  const rowTestingId = ref<string | null>(null)

  const modelUniqueParams = computed(() =>
    resolveUniqueParams(deps.presetOf(modelForm.provider), 'chat'),
  )
  const modelModelHints = computed(() => resolveModelHints(deps.presetOf(modelForm.provider), 'chat'))
  const enabledCount = computed(() => chatModels.value.filter((m) => m.enabled).length)

  async function refresh() {
    chatModels.value = await fetchAiChatModels()
  }

  function openCreateModel() {
    modelDialogMode.value = 'create'
    editingModelId.value = null
    modelForm.name = ''
    modelForm.provider = 'custom'
    modelForm.baseUrl = ''
    modelForm.model = ''
    modelForm.apiKey = ''
    modelForm.apiKeyConfigured = false
    modelForm.keySuffix = ''
    modelForm.uniqueValues = buildDefaultUniqueParams(resolveUniqueParams(deps.presetOf('custom'), 'chat'))
    modelForm.customParams = '{}'
    modelDialogVisible.value = true
  }

  function openEditModel(row: AiChatModel) {
    modelDialogMode.value = 'edit'
    editingModelId.value = row.id
    modelForm.name = row.name
    modelForm.provider = row.provider
    modelForm.baseUrl = row.baseUrl
    modelForm.model = row.model
    modelForm.apiKey = ''
    modelForm.apiKeyConfigured = row.apiKey.configured
    modelForm.keySuffix = row.apiKey.keySuffix
    modelForm.uniqueValues = extractUniqueValuesForScope(
      row.extraParams,
      'chat',
      deps.presetOf(row.provider),
    )
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
    modelForm.baseUrl = resolveDefaultBaseUrl(deps.presetOf(next), 'chat')
    modelForm.uniqueValues = buildDefaultUniqueParams(resolveUniqueParams(deps.presetOf(next), 'chat'))
  }

  function buildModelPayload(): AiChatModelSavePayload {
    const custom = deps.parsing.parseJsonObject(modelForm.customParams, '对话高级参数')
    return {
      name: modelForm.name,
      provider: modelForm.provider,
      baseUrl: modelForm.baseUrl,
      model: modelForm.model,
      apiKey: modelForm.apiKey || null,
      extraParams: mergeExtraParams(modelForm.uniqueValues, custom),
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
    deps.saving.saving.value = true
    try {
      if (modelDialogMode.value === 'create') {
        await createAiChatModel(payload)
        ElMessage.success('已新建对话模型')
      } else if (editingModelId.value) {
        await updateAiChatModel(editingModelId.value, payload)
        ElMessage.success('已保存对话模型')
      }
      modelDialogVisible.value = false
      await refresh()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '保存失败')
    } finally {
      deps.saving.saving.value = false
    }
  }

  async function handleModelDialogTest() {
    testing.modelDialog = true
    try {
      const custom = deps.parsing.parseJsonObject(modelForm.customParams, '对话高级参数')
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
      await refresh()
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
      await refresh()
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
      await refresh()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '删除失败')
    }
  }

  return {
    chatModels,
    enabledCount,
    modelDialogVisible,
    modelDialogMode,
    editingModelId,
    modelForm,
    testing,
    rowTestingId,
    modelUniqueParams,
    modelModelHints,
    refresh,
    openCreateModel,
    openEditModel,
    handleModelProviderChange,
    handleModelSave,
    handleModelDialogTest,
    handleRowTest,
    handleSetDefault,
    handleToggleEnabled,
    handleDeleteModel,
  }
}