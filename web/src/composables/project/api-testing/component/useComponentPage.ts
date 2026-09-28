import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiComponentSaveReq,
  ApiDataSource,
  ApiHttpConfig,
} from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  copyComponent,
  createComponent,
  deleteComponent,
  fetchComponents,
  toggleComponent,
  updateComponent,
} from '@/services/project/api-testing/component'
import { fetchEnvironmentDetail, fetchEnvironments } from '@/services/project/api-testing/environment'
import {
  COMPONENT_SCOPE_OPTIONS,
  COMPONENT_TAB_OPTIONS,
  COMPONENT_TYPE_OPTIONS,
  SCOPE_TAG_TYPE,
  componentScopeLabel,
  componentTypeLabel,
  resolveComponentError,
  type ComponentFormData,
  type ComponentPanelMode,
  type ComponentTab,
} from '@/composables/project/api-testing/component/componentModel'
import {
  createProcessorComponentConfig,
  defaultComponentConfig,
  extractorsFromComponents,
  parseComponentConfig,
  type ProcessorExtractor,
} from '@/composables/project/api-testing/processorFormModel'

/** 交互设计已移除分页，一次拉满当前筛选下的全部组件（超出即列表截断，量级远低于该阈值） */
const LIST_PAGE_SIZE = 1000
const SEARCH_DEBOUNCE_MS = 300

export function useComponentPage() {
  const authStore = useAuthStore()
  const canEdit = computed(() =>
    authStore.hasPermission('api-component:edit')
    || authStore.hasPermission('api-component:edit-space')
    || authStore.hasPermission('api-component:edit-global'),
  )

  // ==================== 列表 ====================

  const listLoading = ref(false)
  const loadError = ref(false)
  const list = ref<ApiComponentListItem[]>([])
  const keyword = ref('')
  const filterType = ref<ComponentTab>('all')
  const selectedId = ref<string | null>(null)
  const panelMode = ref<ComponentPanelMode>('view')
  /** 删除后按原位置回选下一项；null 表示按默认策略（首项）回选 */
  let pendingSelectIndex: number | null = null

  function restoreSelection(): void {
    if (pendingSelectIndex !== null) {
      const index = Math.min(pendingSelectIndex, list.value.length - 1)
      pendingSelectIndex = null
      selectedId.value = index >= 0 ? list.value[index]?.id ?? null : null
      return
    }
    if (selectedId.value && list.value.some((item) => item.id === selectedId.value)) return
    selectedId.value = list.value[0]?.id ?? null
  }

  async function loadList(): Promise<void> {
    listLoading.value = true
    loadError.value = false
    try {
      const result = await fetchComponents({
        pageNo: 1,
        pageSize: LIST_PAGE_SIZE,
        type: filterType.value === 'all' ? undefined : filterType.value,
        keyword: keyword.value.trim() || undefined,
      })
      list.value = result.list
      restoreSelection()
    } catch (err) {
      loadError.value = true
      ElMessage.error(resolveComponentError(err))
    } finally {
      listLoading.value = false
    }
  }

  let searchTimer: ReturnType<typeof setTimeout> | undefined
  function handleSearchInput(): void {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => void loadList(), SEARCH_DEBOUNCE_MS)
  }
  onBeforeUnmount(() => clearTimeout(searchTimer))

  function handleTabChange(tab: ComponentTab): void {
    filterType.value = tab
    clearTimeout(searchTimer)
    void loadList()
  }

  /** 清除筛选同时清空关键词与类型页签（对齐空态 [清除筛选] 口径） */
  function clearFilters(): void {
    keyword.value = ''
    filterType.value = 'all'
    clearTimeout(searchTimer)
    void loadList()
  }

  const hasFilter = computed(() => keyword.value.trim() !== '' || filterType.value !== 'all')

  const selectedItem = computed(() => list.value.find((item) => item.id === selectedId.value) ?? null)

  function selectComponent(id: string): void {
    selectedId.value = id
    panelMode.value = 'view'
  }

  // ==================== 表单 ====================

  const saving = ref(false)
  const editingId = ref<string | null>(null)
  const form = reactive<ComponentFormData>({
    type: 'preprocessor',
    name: '',
    description: '',
    scope: 'project',
    sortOrder: 0,
    config: {},
  })

  watch(() => form.type, () => {
    if (!editingId.value) {
      form.config = form.type === 'preprocessor' || form.type === 'postprocessor' ? createProcessorComponentConfig() : {}
    }
  })

  const httpRefOptions = ref<ApiHttpConfig[]>([])
  const dsRefOptions = ref<ApiDataSource[]>([])

  async function loadProcessorRefOptions(): Promise<void> {
    try {
      const envs = await fetchEnvironments()
      const def = envs.find((e) => e.isDefault)
      if (!def) {
        httpRefOptions.value = []
        dsRefOptions.value = []
        return
      }
      const detail = await fetchEnvironmentDetail(def.id)
      httpRefOptions.value = detail.httpConfigs
      dsRefOptions.value = detail.dataSources
    } catch (err) {
      httpRefOptions.value = []
      dsRefOptions.value = []
      ElMessage.error(resolveComponentError(err))
    }
  }

  function startCreate(): void {
    editingId.value = null
    form.type = 'preprocessor'
    form.name = ''
    form.description = ''
    form.scope = 'project'
    form.sortOrder = 0
    form.config = createProcessorComponentConfig()
    void loadProcessorRefOptions()
    panelMode.value = 'create'
  }

  function startEdit(item: ApiComponentListItem | null = selectedItem.value): void {
    if (!item) return
    editingId.value = item.id
    form.type = item.type
    form.name = item.name
    form.description = item.description ?? ''
    form.scope = item.scope
    form.sortOrder = typeof item.sortOrder === 'number' ? item.sortOrder : 0
    form.config = parseComponentConfig(item.config)
    void loadProcessorRefOptions()
    panelMode.value = 'edit'
  }

  /** 取消回到查看态：保留当前选中项，未选中时右栏落回空态 */
  function cancelEdit(): void {
    panelMode.value = 'view'
  }

  async function handleSave(): Promise<void> {
    if (!form.name.trim()) {
      ElMessage.warning('请填写组件名称')
      return
    }
    saving.value = true
    try {
      const config = { ...defaultComponentConfig(), ...(form.config ?? {}) }
      const payload: ApiComponentSaveReq = {
        type: form.type,
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        sortOrder: form.sortOrder,
        config: Object.keys(config).length > 0 ? config : undefined,
      }
      if (editingId.value) {
        await updateComponent(editingId.value, payload)
        ElMessage.success('已更新')
        await loadList()
        selectedId.value = editingId.value
      } else {
        payload.scope = form.scope
        const resp = await createComponent(payload)
        ElMessage.success('已创建')
        await loadList()
        selectedId.value = resp.id
      }
      panelMode.value = 'view'
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    } finally {
      saving.value = false
    }
  }

  // ==================== 查看态操作 ====================

  /** 详情头部启停：成功后刷新列表同步左栏状态标签，失败仅提示（列表即服务端状态，无需回滚） */
  async function handleEnableToggle(enabled: boolean): Promise<void> {
    const item = selectedItem.value
    if (!item) return
    try {
      await toggleComponent(item.id, enabled)
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  async function handleDelete(row: ApiComponentListItem | null): Promise<void> {
    if (!row) return
    try {
      await ElMessageBox.confirm(
        `删除后不可恢复，确认删除「${row.name}」？已引入的副本不受影响`,
        '删除组件',
        { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      pendingSelectIndex = list.value.findIndex((item) => item.id === row.id)
      await deleteComponent(row.id)
      selectedId.value = null
      panelMode.value = 'view'
      ElMessage.success('已删除')
      await loadList()
    } catch (err) {
      pendingSelectIndex = null
      ElMessage.error(resolveComponentError(err))
    }
  }

  async function handleCopy(row: ApiComponentListItem | null): Promise<void> {
    if (!row) return
    try {
      const resp = await copyComponent(row.id)
      ElMessage.success('已复制')
      await loadList()
      if (resp.id && list.value.some((item) => item.id === resp.id)) {
        selectedId.value = resp.id
        panelMode.value = 'view'
      }
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  // ==================== 提取器引入 ====================

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
      ElMessage.error(resolveComponentError(err))
    } finally {
      extractorPickerLoading.value = false
    }
  }

  function openExtractorPicker(): void {
    extractorPickerVisible.value = true
    extractorPickerKeyword.value = ''
    void loadExtractorAssets()
  }

  function handleExtractorPicked(rows: ApiComponentListItem[]): void {
    const incoming = extractorsFromComponents(rows)
    if (incoming.length === 0) return
    const existing = Array.isArray(form.config.extractors) ? form.config.extractors as ProcessorExtractor[] : []
    form.config = {
      ...form.config,
      extractors: [...existing, ...incoming],
    }
    ElMessage.success(`已引入 ${incoming.length} 个提取器`)
  }

  onMounted(() => void loadList())

  return {
    canEdit,
    listLoading,
    loadError,
    list,
    keyword,
    filterType,
    hasFilter,
    selectedId,
    selectedItem,
    panelMode,
    saving,
    editingId,
    form,
    httpRefOptions,
    dsRefOptions,
    extractorPickerVisible,
    extractorPickerLoading,
    extractorPickerItems,
    extractorPickerKeyword,
    loadList,
    handleSearchInput,
    handleTabChange,
    clearFilters,
    selectComponent,
    startCreate,
    startEdit,
    cancelEdit,
    handleSave,
    handleEnableToggle,
    handleDelete,
    handleCopy,
    openExtractorPicker,
    handleExtractorPicked,
    loadExtractorAssets,
    COMPONENT_TYPE_OPTIONS,
    COMPONENT_SCOPE_OPTIONS,
    COMPONENT_TAB_OPTIONS,
    SCOPE_TAG_TYPE,
    componentTypeLabel,
    componentScopeLabel,
  }
}
