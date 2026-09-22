import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiComponentSaveReq,
  ApiComponentScope,
  ApiComponentType,
  ApiDataSource,
  ApiHttpConfig,
} from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  batchDeleteComponents,
  batchToggleComponents,
  copyComponent,
  createComponent,
  deleteComponent,
  fetchComponents,
  toggleComponent,
  updateComponent,
} from '@/services/project/component'
import { fetchEnvironmentDetail, fetchEnvironments } from '@/services/project/environment'
import {
  COMPONENT_SCOPE_OPTIONS,
  COMPONENT_TYPE_OPTIONS,
  SCOPE_TAG_TYPE,
  componentScopeLabel,
  componentTypeLabel,
  resolveComponentError,
} from '@/pages/project/api-testing/component/componentModel'
import {
  createProcessorComponentConfig,
  defaultComponentConfig,
  extractorsFromComponents,
  type ProcessorExtractor,
} from '@/components/api-testing/processorFormModel'

export function useComponentPage() {
  const authStore = useAuthStore()
  const canEdit = computed(() =>
    authStore.hasPermission('api-component:edit')
    || authStore.hasPermission('api-component:edit-space')
    || authStore.hasPermission('api-component:edit-global'),
  )

  const listLoading = ref(false)
  const loadError = ref(false)
  const list = ref<ApiComponentListItem[]>([])
  const total = ref(0)
  const selectedIds = ref<string[]>([])
  const keyword = ref('')
  const keywordDraft = ref('')
  const filterType = ref<ApiComponentType | ''>('')
  const filterScope = ref<ApiComponentScope | ''>('')
  const filterEnabled = ref<boolean | ''>('')
  const pageNo = ref(1)
  const pageSize = ref(20)

  async function loadList(): Promise<void> {
    listLoading.value = true
    loadError.value = false
    try {
      const result = await fetchComponents({
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        type: filterType.value || undefined,
        scope: filterScope.value || undefined,
        enabled: filterEnabled.value !== '' ? filterEnabled.value === true : undefined,
        keyword: keyword.value.trim() || undefined,
      })
      list.value = result.list
      total.value = result.total
      selectedIds.value = []
    } catch (err) {
      loadError.value = true
      ElMessage.error(resolveComponentError(err))
    } finally {
      listLoading.value = false
    }
  }

  function handlePageChange(page: number) {
    pageNo.value = page
    void loadList()
  }

  function handleSizeChange(size: number) {
    pageSize.value = size
    pageNo.value = 1
    void loadList()
  }

  function handleSearch() {
    keyword.value = keywordDraft.value
    pageNo.value = 1
    void loadList()
  }

  function handleReset() {
    keywordDraft.value = ''
    keyword.value = ''
    filterType.value = ''
    filterScope.value = ''
    filterEnabled.value = ''
    pageNo.value = 1
    void loadList()
  }

  function handleSelectionChange(rows: ApiComponentListItem[]) {
    selectedIds.value = rows.map((r) => r.id)
  }

  const hasSelection = computed(() => selectedIds.value.length > 0)

  async function handleToggle(row: ApiComponentListItem) {
    try {
      await toggleComponent(row.id, !row.enabled)
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  async function handleBatchToggle(enabled: boolean) {
    if (!hasSelection.value) return
    const action = enabled ? '启用' : '停用'
    try {
      await ElMessageBox.confirm(`确认${action}选中的 ${selectedIds.value.length} 个组件？`, `批量${action}`, {
        type: 'warning',
        confirmButtonText: action,
      })
    } catch {
      return
    }
    try {
      await batchToggleComponents(selectedIds.value, enabled)
      ElMessage.success(`已${action}`)
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  async function handleBatchDelete() {
    if (!hasSelection.value) return
    try {
      await ElMessageBox.confirm(
        `删除后不可恢复，确认删除选中的 ${selectedIds.value.length} 个组件？已引入的副本不受影响`,
        '批量删除',
        { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await batchDeleteComponents(selectedIds.value)
      ElMessage.success('已删除')
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  async function handleDelete(row: ApiComponentListItem) {
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
      await deleteComponent(row.id)
      ElMessage.success('已删除')
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  async function handleCopy(row: ApiComponentListItem) {
    try {
      await copyComponent(row.id)
      ElMessage.success('已复制')
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    }
  }

  const drawerVisible = ref(false)
  const editingId = ref<string | null>(null)
  const saving = ref(false)
  const form = reactive<{
    type: ApiComponentType
    name: string
    description: string
    scope: ApiComponentScope
    sortOrder: number
    config: Record<string, unknown>
  }>({
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

  const basicConfigEnabled = computed<boolean>({
    get: () => form.config.enabled !== false,
    set: (value: boolean) => {
      form.config = { ...form.config, enabled: value }
    },
  })

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

  function openExtractorPicker() {
    extractorPickerVisible.value = true
    extractorPickerKeyword.value = ''
    void loadExtractorAssets()
  }

  function handleExtractorPicked(rows: ApiComponentListItem[]) {
    const incoming = extractorsFromComponents(rows)
    if (incoming.length === 0) return
    const existing = Array.isArray(form.config.extractors) ? form.config.extractors as ProcessorExtractor[] : []
    form.config = {
      ...form.config,
      extractors: [...existing, ...incoming],
    }
    ElMessage.success(`已引入 ${incoming.length} 个提取器`)
  }

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

  function openCreateDrawer() {
    editingId.value = null
    form.type = 'preprocessor'
    form.name = ''
    form.description = ''
    form.scope = 'project'
    form.sortOrder = 0
    form.config = createProcessorComponentConfig()
    void loadProcessorRefOptions()
    drawerVisible.value = true
  }

  function openEditDrawer(row: ApiComponentListItem) {
    editingId.value = row.id
    form.type = row.type
    form.name = row.name
    form.description = row.description ?? ''
    form.scope = row.scope
    form.sortOrder = typeof row.sortOrder === 'number' ? row.sortOrder : 0
    try {
      form.config = row.config ? JSON.parse(row.config) : {}
    } catch {
      form.config = {}
    }
    void loadProcessorRefOptions()
    drawerVisible.value = true
  }

  async function handleSave() {
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
      } else {
        payload.scope = form.scope
        await createComponent(payload)
        ElMessage.success('已创建')
      }
      drawerVisible.value = false
      await loadList()
    } catch (err) {
      ElMessage.error(resolveComponentError(err))
    } finally {
      saving.value = false
    }
  }

  onMounted(() => void loadList())

  return {
    canEdit,
    listLoading,
    loadError,
    list,
    total,
    selectedIds,
    keyword,
    keywordDraft,
    filterType,
    filterScope,
    filterEnabled,
    pageNo,
    pageSize,
    hasSelection,
    drawerVisible,
    editingId,
    saving,
    form,
    basicConfigEnabled,
    extractorPickerVisible,
    extractorPickerLoading,
    extractorPickerItems,
    extractorPickerKeyword,
    httpRefOptions,
    dsRefOptions,
    loadList,
    handlePageChange,
    handleSizeChange,
    handleSearch,
    handleReset,
    handleSelectionChange,
    handleToggle,
    handleBatchToggle,
    handleBatchDelete,
    handleDelete,
    handleCopy,
    openExtractorPicker,
    handleExtractorPicked,
    loadExtractorAssets,
    openCreateDrawer,
    openEditDrawer,
    handleSave,
    COMPONENT_TYPE_OPTIONS,
    COMPONENT_SCOPE_OPTIONS,
    SCOPE_TAG_TYPE,
    componentTypeLabel,
    componentScopeLabel,
  }
}
