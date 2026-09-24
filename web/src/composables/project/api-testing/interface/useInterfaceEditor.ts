import { ref, computed, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CascaderOption } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiComponentType,
  ApiDebugKeyValue,
  ApiInterfaceDetail,
  ProjectModule,
} from '@/types'
import { createInterface, fetchInterfaceDetail, updateInterface } from '@/services/project/api-testing/interface'
import { fetchProjectModuleTree } from '@/services/project'
import { fetchComponents } from '@/services/project/api-testing/component'
import {
  createEditorForm,
  toCreatePayload,
  toSelectableModuleOptions,
  type InterfaceEditorForm,
} from '@/composables/project/api-testing/interface/interfacesModel'
import {
  extractorFromComponent,
  validatorFromComponent,
} from '@/composables/project/api-testing/processorFormModel'
import type { PaneValidatorItem, PaneExtractorItem } from '@/composables/project/api-testing/scene/scenesModel'

// ==================== Constants ====================

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'CONNECT']
const PROTOCOL_OPTIONS = [{ value: 'http', label: 'http' }]

type AssetKind = 'validator' | 'extractor'
const ASSET_TYPE: Record<AssetKind, ApiComponentType> = {
  validator: 'validator',
  extractor: 'extractor',
}
const ASSET_TITLE: Record<AssetKind, string> = {
  validator: '从公共组件引入验证器',
  extractor: '从公共组件引入提取器',
}
const ASSET_NAME: Record<AssetKind, string> = {
  validator: '验证器',
  extractor: '提取器',
}

// ==================== Helpers ====================

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function splitQueryFromPath(raw: string): { path: string; query: ApiDebugKeyValue[] } {
  const qIndex = raw.indexOf('?')
  if (qIndex < 0) return { path: raw, query: [] }
  const query = raw
    .slice(qIndex + 1)
    .split('&')
    .map((kv) => {
      const [k, v] = kv.split('=')
      return { key: (k ?? '').trim(), value: (v ?? '').trim(), enabled: true }
    })
    .filter((entry) => entry.key !== '')
  return { path: raw.slice(0, qIndex), query }
}

// ==================== Types ====================

export interface UseInterfaceEditorOptions {
  interfaceId?: string
  createMode?: boolean
  moduleId?: string
}

export interface UseInterfaceEditorReturn {
  form: Ref<InterfaceEditorForm>
  loading: Ref<boolean>
  detailError: Ref<string | null>
  saving: Ref<boolean>
  activeTab: Ref<string>
  moduleLoading: Ref<boolean>
  moduleError: Ref<string | null>
  moduleOptions: Ref<CascaderOption[]>
  retryModules: () => Promise<void>
  handlePathBlur: () => void
  addValidator: () => void
  addExtractor: () => void
  paneValidators: Ref<PaneValidatorItem[]>
  paneExtractors: Ref<PaneExtractorItem[]>
  handleValidatorsUpdate: (rows: PaneValidatorItem[]) => void
  handleExtractorsUpdate: (rows: PaneExtractorItem[]) => void
  assetPickerVisible: Ref<boolean>
  assetPickerLoading: Ref<boolean>
  assetPickerItems: Ref<ApiComponentListItem[]>
  assetPickerError: Ref<string | null>
  assetPickerKeyword: Ref<string>
  assetPickerKind: Ref<AssetKind>
  openAssetPicker: (kind: AssetKind) => void
  loadAssetPicker: () => Promise<void>
  retryAssetPicker: () => Promise<void>
  handleAssetPicked: (rows: ApiComponentListItem[]) => void
  retryDetail: () => Promise<void>
  save: () => Promise<void>
  containerRef: Ref<HTMLElement | undefined>
  requestHeight: Ref<number>
  onDividerMouseDown: (e: MouseEvent) => void
  METHOD_OPTIONS: string[]
  PROTOCOL_OPTIONS: { value: string; label: string }[]
  ASSET_TITLE: Record<AssetKind, string>
  mount: () => void
  unmount: () => void
}

// ==================== Composable ====================

export function useInterfaceEditor(
  options: UseInterfaceEditorOptions,
  emit: { (e: 'back'): void; (e: 'title-update', name: string): void; (e: 'dirty-change', dirty: boolean): void },
): UseInterfaceEditorReturn {
  const isNew = computed(() => (options.createMode ?? false) || !options.interfaceId)
  const interfaceId = computed(() => (isNew.value ? '' : options.interfaceId!))

  const detail = ref<ApiInterfaceDetail | null>(null)
  const form = ref<InterfaceEditorForm>(createEditorForm())
  const loading = ref(false)
  const detailError = ref<string | null>(null)
  const saving = ref(false)
  const activeTab = ref(isNew.value ? 'headers' : 'basic')
  let detailRequestId = 0

  // ==================== Module tree ====================
  const moduleTree = ref<ProjectModule[]>([])
  const moduleLoading = ref(false)
  const moduleError = ref<string | null>(null)
  let moduleRequestId = 0
  const moduleOptions = computed<CascaderOption[]>(() =>
    toSelectableModuleOptions(moduleTree.value) as CascaderOption[],
  )

  async function loadModules(): Promise<void> {
    const sequence = ++moduleRequestId
    moduleLoading.value = true
    moduleError.value = null
    try {
      const result = await fetchProjectModuleTree('interface')
      if (sequence !== moduleRequestId) return
      moduleTree.value = result
    } catch (err) {
      if (sequence !== moduleRequestId) return
      const message = errorMessage(err, '加载接口模块失败')
      moduleTree.value = []
      moduleError.value = message
      ElMessage.error(message)
    } finally {
      if (sequence === moduleRequestId) {
        moduleLoading.value = false
      }
    }
  }

  function retryModules(): Promise<void> {
    return loadModules()
  }

  // ==================== Path ====================
  function handlePathBlur() {
    const value = form.value.path.trim()
    if (value && !value.startsWith('/')) {
      ElMessage.warning('路径需以 / 开头')
    }
    const { path, query } = splitQueryFromPath(form.value.path)
    if (query.length) {
      form.value.path = path
      const existing = form.value.params.filter((p) => p.key.trim() !== '')
      form.value.params = [...existing, ...query]
      activeTab.value = 'query'
    }
  }

  // ==================== Dirty tracking ====================
  let savedStamp = ''
  function formStamp() {
    return JSON.stringify({ name: form.value.name, form: form.value, detailVersion: detail.value?.changeVersion })
  }
  const isDirty = ref(false)
  function markSaved() {
    savedStamp = formStamp()
    isDirty.value = false
  }

  // ==================== Validators / Extractors ====================
  function addValidator() {
    form.value.validators.push({ enabled: true, target: 'status_code', expression: '', condition: 'equals', expected: '' })
  }

  function addExtractor() {
    form.value.extractors.push({ enabled: true, source: 'json_field', expression: '', variableName: '' })
  }

  const paneValidators = computed<PaneValidatorItem[]>(() => form.value.validators as unknown as PaneValidatorItem[])
  const paneExtractors = computed<PaneExtractorItem[]>(() => form.value.extractors as unknown as PaneExtractorItem[])

  function handleValidatorsUpdate(rows: PaneValidatorItem[]) {
    form.value.validators = rows as unknown as Record<string, unknown>[]
  }

  function handleExtractorsUpdate(rows: PaneExtractorItem[]) {
    form.value.extractors = rows as unknown as Record<string, unknown>[]
  }

  // ==================== Asset picker ====================
  const assetPickerVisible = ref(false)
  const assetPickerLoading = ref(false)
  const assetPickerItems = ref<ApiComponentListItem[]>([])
  const assetPickerError = ref<string | null>(null)
  const assetPickerKeyword = ref('')
  const assetPickerKind = ref<AssetKind>('validator')
  let assetRequestId = 0

  async function loadAssetPicker(): Promise<void> {
    const sequence = ++assetRequestId
    assetPickerLoading.value = true
    assetPickerError.value = null
    try {
      const result = await fetchComponents({
        type: ASSET_TYPE[assetPickerKind.value],
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: assetPickerKeyword.value.trim() || undefined,
      })
      if (sequence !== assetRequestId) return
      assetPickerItems.value = result.list
    } catch (err) {
      if (sequence !== assetRequestId) return
      const message = errorMessage(err, '公共组件加载失败')
      assetPickerItems.value = []
      assetPickerError.value = message
      ElMessage.error(message)
    } finally {
      if (sequence === assetRequestId) {
        assetPickerLoading.value = false
      }
    }
  }

  function retryAssetPicker(): Promise<void> {
    return loadAssetPicker()
  }

  function openAssetPicker(kind: AssetKind) {
    assetPickerKind.value = kind
    assetPickerKeyword.value = ''
    assetPickerVisible.value = true
    void loadAssetPicker()
  }

  function handleAssetPicked(rows: ApiComponentListItem[]) {
    if (rows.length === 0) return
    const kind = assetPickerKind.value
    if (kind === 'validator') {
      rows.forEach((r) => form.value.validators.push({ ...validatorFromComponent(r), enabled: true }))
    } else {
      rows.forEach((r) => form.value.extractors.push({ ...extractorFromComponent(r), enabled: true }))
    }
    ElMessage.success(`已引入 ${rows.length} 个${ASSET_NAME[kind]}`)
  }

  // ==================== Load / Save ====================
  async function loadDetail(): Promise<void> {
    const sequence = ++detailRequestId
    if (isNew.value) {
      detailError.value = null
      loading.value = false
      form.value = createEditorForm()
      form.value.moduleId = options.moduleId ?? null
      markSaved()
      return
    }
    loading.value = true
    detailError.value = null
    try {
      const result = await fetchInterfaceDetail(interfaceId.value)
      if (sequence !== detailRequestId) return
      detail.value = result
      form.value = createEditorForm(result)
      markSaved()
      emit('title-update', form.value.name.trim() || '')
    } catch (err) {
      if (sequence !== detailRequestId) return
      const message = errorMessage(err, '接口详情加载失败')
      detailError.value = message
      ElMessage.error(message)
    } finally {
      if (sequence === detailRequestId) {
        loading.value = false
      }
    }
  }

  function retryDetail(): Promise<void> {
    return loadDetail()
  }

  async function handleSaveConflict(err: unknown) {
    const message = err instanceof Error ? err.message : ''
    if (!message.includes('7105') && !message.includes('版本')) {
      ElMessage.error(message || '保存失败')
      return
    }
    await ElMessageBox.confirm('接口已被他人修改，是否加载最新版本（将丢弃当前未保存的编辑）？', '版本冲突', { type: 'warning' })
    await loadDetail()
  }

  async function save() {
    if (saving.value) return
    if (!form.value.name.trim()) {
      ElMessage.warning('请填写接口名称')
      return
    }
    saving.value = true
    try {
      if (isNew.value) {
        const { req, error } = toCreatePayload(form.value)
        if (error) {
          ElMessage.warning(error)
          return
        }
        await createInterface(req)
        ElMessage.success('接口已创建')
        emit('back')
        return
      }
      const payload = toCreatePayload(form.value)
      if (payload.error) {
        ElMessage.warning(payload.error)
        return
      }
      await updateInterface(interfaceId.value, {
        ...payload.req,
        changeVersion: detail.value!.changeVersion,
      })
      ElMessage.success('已保存')
      await loadDetail()
    } catch (err) {
      await handleSaveConflict(err)
    } finally {
      saving.value = false
    }
  }

  function handleCtrlS(event: KeyboardEvent) {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
      event.preventDefault()
      void save()
    }
  }

  // ==================== Drag divider ====================
  const containerRef = ref<HTMLElement>()
  const requestHeight = ref(50)
  const isDragging = ref(false)

  function onDividerMouseMove(e: MouseEvent) {
    if (!isDragging.value || !containerRef.value) return
    const rect = containerRef.value.getBoundingClientRect()
    const y = e.clientY - rect.top
    const pct = (y / rect.height) * 100
    requestHeight.value = Math.min(Math.max(pct, 20), 80)
  }

  function onDividerMouseUp() {
    isDragging.value = false
    document.removeEventListener('mousemove', onDividerMouseMove)
    document.removeEventListener('mouseup', onDividerMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  function onDividerMouseDown(e: MouseEvent) {
    e.preventDefault()
    isDragging.value = true
    document.addEventListener('mousemove', onDividerMouseMove)
    document.addEventListener('mouseup', onDividerMouseUp)
    document.body.style.cursor = 'row-resize'
    document.body.style.userSelect = 'none'
  }

  // ==================== Lifecycle ====================
  function mount() {
    window.addEventListener('keydown', handleCtrlS)
    void loadDetail()
    void loadModules()
  }

  function unmount() {
    detailRequestId += 1
    moduleRequestId += 1
    assetRequestId += 1
    loading.value = false
    moduleLoading.value = false
    assetPickerLoading.value = false
    window.removeEventListener('keydown', handleCtrlS)
    document.removeEventListener('mousemove', onDividerMouseMove)
    document.removeEventListener('mouseup', onDividerMouseUp)
  }

  // ==================== Watches ====================
  watch(formStamp, () => {
    isDirty.value = formStamp() !== savedStamp
    emit('dirty-change', isDirty.value)
  })
  watch(
    () => form.value.name,
    (name) => emit('title-update', (name ?? '').trim() || (isNew.value ? '新接口' : '')),
  )

  return {
    form,
    loading,
    detailError,
    saving,
    activeTab,
    moduleLoading,
    moduleError,
    moduleOptions,
    retryModules,
    handlePathBlur,
    addValidator,
    addExtractor,
    paneValidators,
    paneExtractors,
    handleValidatorsUpdate,
    handleExtractorsUpdate,
    assetPickerVisible,
    assetPickerLoading,
    assetPickerItems,
    assetPickerError,
    assetPickerKeyword,
    assetPickerKind,
    openAssetPicker,
    loadAssetPicker,
    retryAssetPicker,
    handleAssetPicked,
    retryDetail,
    save,
    containerRef,
    requestHeight,
    onDividerMouseDown,
    METHOD_OPTIONS,
    PROTOCOL_OPTIONS,
    ASSET_TITLE,
    mount,
    unmount,
  }
}
