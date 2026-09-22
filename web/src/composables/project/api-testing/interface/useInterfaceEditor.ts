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
import { createInterface, fetchInterfaceDetail, updateInterface } from '@/services/project/interface'
import { fetchProjectModuleTree } from '@/services/project'
import { fetchComponents } from '@/services/project/component'
import {
  createEditorForm,
  toCreatePayload,
  toSelectableModuleOptions,
  type InterfaceEditorForm,
} from '@/pages/project/api-testing/interface/interfacesModel'
import {
  extractorFromComponent,
  validatorFromComponent,
} from '@/components/project/api-testing/processorFormModel'
import type { PaneValidatorItem, PaneExtractorItem } from '@/pages/project/api-testing/scene/scenesModel'

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
  saving: Ref<boolean>
  activeTab: Ref<string>
  moduleOptions: Ref<CascaderOption[]>
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
  assetPickerKeyword: Ref<string>
  assetPickerKind: Ref<AssetKind>
  openAssetPicker: (kind: AssetKind) => void
  loadAssetPicker: () => Promise<void>
  handleAssetPicked: (rows: ApiComponentListItem[]) => void
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
  const saving = ref(false)
  const activeTab = ref(isNew.value ? 'headers' : 'basic')

  // ==================== Module tree ====================
  const moduleTree = ref<ProjectModule[]>([])
  const moduleOptions = computed<CascaderOption[]>(() =>
    toSelectableModuleOptions(moduleTree.value) as CascaderOption[],
  )

  async function loadModules() {
    try {
      moduleTree.value = await fetchProjectModuleTree('interface')
    } catch {
      moduleTree.value = []
    }
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
  const assetPickerKeyword = ref('')
  const assetPickerKind = ref<AssetKind>('validator')

  async function loadAssetPicker(): Promise<void> {
    assetPickerLoading.value = true
    try {
      const result = await fetchComponents({
        type: ASSET_TYPE[assetPickerKind.value],
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: assetPickerKeyword.value.trim() || undefined,
      })
      assetPickerItems.value = result.list
    } catch {
      ElMessage.error('公共组件加载失败')
    } finally {
      assetPickerLoading.value = false
    }
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
  async function loadDetail() {
    if (isNew.value) {
      form.value = createEditorForm()
      form.value.moduleId = options.moduleId ?? null
      markSaved()
      return
    }
    loading.value = true
    try {
      detail.value = await fetchInterfaceDetail(interfaceId.value)
      form.value = createEditorForm(detail.value)
      markSaved()
      emit('title-update', form.value.name.trim() || '')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '接口详情加载失败')
    } finally {
      loading.value = false
    }
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
    saving,
    activeTab,
    moduleOptions,
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
    assetPickerKeyword,
    assetPickerKind,
    openAssetPicker,
    loadAssetPicker,
    handleAssetPicked,
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
