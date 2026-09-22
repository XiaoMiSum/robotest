import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiSceneStepItem, ApiSceneStepVariableItem, ApiSceneStepSaveReq } from '@/types'
import type { ApiInterfaceItem } from '@/types'
import { createSceneStep, updateSceneStep, quickCreateSteps, fetchStepVariables, updateStepVariables } from '@/services/apiScene'
import { fetchInterfacePage, fetchInterfaceDetail } from '@/services/apiInterface'
import {
  parseRequestConfig,
  type ValidatorItem, type ExtractorItem,
  createValidator, createExtractor, serializeValidators, serializeExtractors,
  createStepVariable, createExecutionConfig,
} from '@/pages/project/scenesModel'

export interface UseStepEditorDrawerOptions {
  modelValue: boolean
  sceneId?: string
  step: ApiSceneStepItem | null
}

export interface UseStepEditorDrawerEmit {
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
  (e: 'commit', step: ApiSceneStepItem): void
}

export function useStepEditorDrawer(
  props: UseStepEditorDrawerOptions,
  emit: UseStepEditorDrawerEmit,
) {
  const draftMode = computed(() => !props.sceneId)

  const visible = ref(props.modelValue)
  watch(() => props.modelValue, (v) => (visible.value = v))
  watch(visible, (v) => emit('update:modelValue', v))

  const formName = ref('')
  const formStepType = ref('http')
  const formMethod = ref('GET')
  const formUrl = ref('')
  const formEnabled = ref(true)
  const activeTab = ref('basic')

  const reqHeaders = ref<{ key: string; value: string; enabled: boolean }[]>([])
  const reqParams = ref<{ key: string; value: string; enabled: boolean }[]>([])
  const reqBody = ref<{ type: string; content: unknown }>({ type: 'none', content: null })

  const validators = ref<ValidatorItem[]>([])
  const extractors = ref<ExtractorItem[]>([])

  const stepVariables = ref<ApiSceneStepVariableItem[]>([])
  const variablesLoading = ref(false)

  const executionConfig = ref(createExecutionConfig())

  const createMode = ref<'manual' | 'quick'>('manual')
  const quickInterfaceId = ref('')
  const quickMode = ref('copy')
  const interfaceOptions = ref<ApiInterfaceItem[]>([])
  const interfaceSearch = ref('')
  const interfaceLoading = ref(false)

  watch(visible, async (v) => {
    if (!v) return
    activeTab.value = 'basic'
    if (props.step) {
      formName.value = props.step.name
      formStepType.value = props.step.stepType
      formEnabled.value = props.step.enabled
      createMode.value = 'manual'
      const cfg = parseRequestConfig(props.step.requestConfig)
      formMethod.value = String(cfg.method ?? 'GET')
      formUrl.value = String(cfg.url ?? '')
      reqHeaders.value = (cfg.headers ?? []).map((h) => ({ ...h }))
      reqParams.value = (cfg.params ?? []).map((p) => ({ ...p }))
      reqBody.value = cfg.body ?? { type: 'none', content: null }
      validators.value = (props.step.validators ?? []).map((v) => v as unknown as ValidatorItem)
      extractors.value = (props.step.extractors ?? []).map((e) => e as unknown as ExtractorItem)
      const rc = props.step.requestConfig as Record<string, unknown> | undefined
      executionConfig.value = {
        ...createExecutionConfig(),
        conditionExpression: String(rc?.conditionExpression ?? ''),
      }
      await loadStepVariables()
    } else {
      formName.value = ''
      formStepType.value = 'http'
      formMethod.value = 'GET'
      formUrl.value = ''
      formEnabled.value = true
      reqHeaders.value = []
      reqParams.value = []
      reqBody.value = { type: 'none', content: null }
      validators.value = []
      extractors.value = []
      stepVariables.value = []
      executionConfig.value = createExecutionConfig()
      createMode.value = 'manual'
    }
  })

  async function loadStepVariables() {
    if (!props.sceneId || !props.step) return
    variablesLoading.value = true
    try {
      stepVariables.value = await fetchStepVariables(props.sceneId, props.step.id)
    } catch {
      stepVariables.value = []
    } finally {
      variablesLoading.value = false
    }
  }

  async function loadInterfaces() {
    interfaceLoading.value = true
    try {
      const resp = await fetchInterfacePage({ pageNo: 1, pageSize: 50, search: interfaceSearch.value || undefined })
      interfaceOptions.value = resp.list
    } catch {
      interfaceOptions.value = []
    } finally {
      interfaceLoading.value = false
    }
  }

  function handleCreateModeChange(mode: 'manual' | 'quick') {
    createMode.value = mode
    if (mode === 'quick' && interfaceOptions.value.length === 0) void loadInterfaces()
  }

  function addStepVariable() { stepVariables.value.push(createStepVariable()) }
  function removeStepVariable(i: number) { stepVariables.value.splice(i, 1) }

  function addValidator() { validators.value.push(createValidator()) }
  function removeValidator(i: number) { validators.value.splice(i, 1) }

  function addExtractor() { extractors.value.push(createExtractor()) }
  function removeExtractor(i: number) { extractors.value.splice(i, 1) }

  const saving = ref(false)

  function buildRequestConfig(): Record<string, unknown> {
    return {
      method: formMethod.value,
      url: formUrl.value,
      headers: reqHeaders.value.filter((h) => h.key.trim()),
      params: reqParams.value.filter((p) => p.key.trim()),
      body: reqBody.value,
      conditionExpression: executionConfig.value.conditionExpression,
    }
  }

  async function handleSave() {
    saving.value = true
    try {
      if (draftMode.value) {
        await handleDraftSave()
        return
      }
      if (!props.sceneId) return
      if (createMode.value === 'quick') {
        if (!quickInterfaceId.value) { ElMessage.warning('请选择接口'); return }
        await quickCreateSteps(props.sceneId, { interfaceId: quickInterfaceId.value, mode: quickMode.value })
        ElMessage.success('步骤已创建')
      } else {
        if (!formName.value.trim()) { ElMessage.warning('请填写步骤名称'); return }
        const payload: ApiSceneStepSaveReq = {
          name: formName.value.trim(),
          stepType: formStepType.value,
          enabled: formEnabled.value,
          requestConfig: buildRequestConfig(),
          validators: serializeValidators(validators.value),
          extractors: serializeExtractors(extractors.value),
        }
        if (props.step) {
          await updateSceneStep(props.sceneId, props.step.id, payload)
          ElMessage.success('步骤已更新')
          if (stepVariables.value.length > 0) {
            await updateStepVariables(props.sceneId, props.step.id, { variables: stepVariables.value.filter((v) => v.name.trim()) })
          }
        } else {
          await createSceneStep(props.sceneId, { ...payload, sourceType: 'custom' })
          ElMessage.success('步骤已创建')
        }
      }
      emit('saved')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  function draftId(): string {
    return `draft-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  }

  function buildDraftFromManual(): ApiSceneStepItem {
    return {
      id: draftId(),
      name: formName.value.trim(),
      stepType: formStepType.value,
      sortOrder: 0,
      enabled: formEnabled.value,
      sourceType: 'custom',
      requestConfig: buildRequestConfig(),
      variables: [],
      processors: [],
      validators: serializeValidators(validators.value),
      extractors: serializeExtractors(extractors.value),
    }
  }

  async function buildDraftFromInterface(): Promise<ApiSceneStepItem> {
    const detail = await fetchInterfaceDetail(quickInterfaceId.value)
    const requestConfig: Record<string, unknown> = {
      method: detail.method,
      url: detail.protocol === 'http' ? detail.path : detail.path,
      headers: detail.headers ?? [],
      params: detail.params ?? [],
      body: detail.body ?? { type: 'none', content: null },
      conditionExpression: '',
    }
    return {
      id: draftId(),
      name: detail.name,
      stepType: 'http',
      sortOrder: 0,
      enabled: true,
      sourceType: quickMode.value === 'link' ? 'link' : 'copy',
      sourceInterfaceId: detail.id,
      sourceInterfaceName: detail.name,
      requestConfig,
      variables: [],
      processors: [],
      validators: detail.validators ?? [],
      extractors: detail.extractors ?? [],
    }
  }

  async function handleDraftSave() {
    let step: ApiSceneStepItem
    if (createMode.value === 'quick') {
      if (!quickInterfaceId.value) { ElMessage.warning('请选择接口'); return }
      step = await buildDraftFromInterface()
    } else {
      if (!formName.value.trim()) { ElMessage.warning('请填写步骤名称'); return }
      step = buildDraftFromManual()
    }
    emit('commit', step)
  }

  return {
    draftMode,
    visible,
    formName,
    formStepType,
    formMethod,
    formUrl,
    formEnabled,
    activeTab,
    reqHeaders,
    reqParams,
    reqBody,
    validators,
    extractors,
    stepVariables,
    variablesLoading,
    executionConfig,
    createMode,
    quickInterfaceId,
    quickMode,
    interfaceOptions,
    interfaceSearch,
    interfaceLoading,
    saving,
    handleCreateModeChange,
    loadInterfaces,
    addStepVariable,
    removeStepVariable,
    addValidator,
    removeValidator,
    addExtractor,
    removeExtractor,
    handleSave,
  }
}
