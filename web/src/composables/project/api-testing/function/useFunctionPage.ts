import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiBuiltinFunctionGroup,
  ApiCustomFunctionDetail,
  ApiCustomFunctionListItem,
  ApiFunctionScope,
} from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  fetchBuiltinCatalog,
  fetchCustomFunctions,
  fetchCustomFunctionDetail,
  createCustomFunction,
  updateCustomFunction,
  toggleCustomFunction,
  deleteCustomFunction,
} from '@/services/project/api-testing/function'
import {
  buildFunctionSignature,
  filterFunctions,
  parseParamsDesc,
  resolveFunctionError,
  SCOPE_OPTIONS,
  serializeParamsDesc,
  FUNCTION_TAB_OPTIONS,
  validateParamRows,
  type FunctionParamRow,
  type FunctionTab,
} from '@/composables/project/api-testing/function/functionModel'

interface DisplayListItem {
  type: 'builtin' | 'custom'
  name: string
  description: string
  scope?: ApiFunctionScope
  id?: string
  enabled?: boolean
}

type CustomPanelMode = 'view' | 'create' | 'edit'

export function useFunctionPage() {
  const authStore = useAuthStore()

  // ==================== Permissions ====================

  const canEdit = computed(
    () =>
      authStore.hasPermission('api-func:edit') ||
      authStore.hasPermission('api-func:edit-space') ||
      authStore.hasPermission('api-func:edit-global'),
  )

  // ==================== List state ====================

  const listLoading = ref(false)
  const loadError = ref(false)
  const builtinGroups = ref<ApiBuiltinFunctionGroup[]>([])
  const customList = ref<ApiCustomFunctionListItem[]>([])
  const keyword = ref('')
  const activeTab = ref<FunctionTab>('all')

  let searchTimer: ReturnType<typeof setTimeout> | undefined
  function handleSearchInput(): void {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => void loadCustomList(), 300)
  }
  onBeforeUnmount(() => clearTimeout(searchTimer))

  const filtered = computed(() => filterFunctions(builtinGroups.value, customList.value, keyword.value))

  const displayItems = computed(() => {
    const { builtin, custom } = filtered.value
    const items: DisplayListItem[] = []
    if (activeTab.value === 'all' || activeTab.value === 'builtin') {
      for (const group of builtin) {
        for (const fn of group.functions) {
          items.push({ type: 'builtin', name: fn.name, description: fn.description })
        }
      }
    }
    if (activeTab.value === 'all' || activeTab.value === 'custom') {
      for (const fn of custom) {
        items.push({
          type: 'custom',
          name: fn.name,
          description: fn.description ?? '',
          scope: fn.scope,
          id: fn.id,
          enabled: fn.enabled,
        })
      }
    }
    return items
  })

  const selectedType = ref<'builtin' | 'custom' | null>(null)
  const selectedName = ref('')
  const selectedCustomId = ref('')

  async function loadBuiltin(): Promise<void> {
    try {
      builtinGroups.value = await fetchBuiltinCatalog()
    } catch (err) {
      loadError.value = true
      ElMessage.error(resolveFunctionError(err))
    }
  }

  async function loadCustomList(): Promise<void> {
    try {
      customList.value = await fetchCustomFunctions(
        keyword.value.trim() ? { keyword: keyword.value.trim() } : undefined,
      )
    } catch (err) {
      loadError.value = true
      ElMessage.error(resolveFunctionError(err))
    }
  }

  async function loadAll(): Promise<void> {
    listLoading.value = true
    loadError.value = false
    try {
      await Promise.all([loadBuiltin(), loadCustomList()])
      // 进入页面即选中首条，右栏直接展示详情而不是「选择函数查看详情」占位
      if (selectedType.value === null && displayItems.value.length > 0) {
        const first = displayItems.value[0]
        selectItem(first.type, first.name, first.id)
      }
    } finally {
      listLoading.value = false
    }
  }

  function selectItem(type: 'builtin' | 'custom', name: string, id?: string): void {
    selectedType.value = type
    selectedName.value = name
    selectedCustomId.value = id ?? ''
    panelMode.value = 'view'
  }

  // ==================== Detail ====================

  const selectedBuiltinFn = computed(() => {
    if (selectedType.value !== 'builtin') return null
    for (const group of builtinGroups.value) {
      const found = group.functions.find((fn) => fn.name === selectedName.value)
      if (found) return found
    }
    return null
  })

  const customDetail = ref<ApiCustomFunctionDetail | null>(null)
  const detailLoading = ref(false)

  const customParams = computed(() => parseParamsDesc(customDetail.value?.paramsDesc))

  const customSignature = computed(() =>
    buildFunctionSignature(customDetail.value?.name ?? '', customParams.value),
  )

  watch(selectedCustomId, async (id) => {
    if (!id || selectedType.value !== 'custom') {
      customDetail.value = null
      return
    }
    detailLoading.value = true
    try {
      customDetail.value = await fetchCustomFunctionDetail(id)
    } catch (err) {
      ElMessage.error(resolveFunctionError(err))
    } finally {
      detailLoading.value = false
    }
  })

  // ==================== Form ====================

  const panelMode = ref<CustomPanelMode>('view')
  const form = reactive({
    id: '',
    name: '',
    description: '',
    params: [] as FunctionParamRow[],
    script: '',
    scope: 'project' as ApiFunctionScope,
  })
  const saving = ref(false)

  // 行内错误（docs23 §1.3：参数校验红字提示），与 ElMessage 告警并行
  const formErrors = ref({ name: '', script: '' })
  const paramErrors = ref<Record<number, string>>({})

  function clearFormErrors(): void {
    formErrors.value = { name: '', script: '' }
    paramErrors.value = {}
  }

  /** 参数说明序列化结果实时预览（docs23 §1.3） */
  const serializedParams = computed(() => serializeParamsDesc(form.params))

  function addParamRow(): void {
    form.params.push({ name: '', required: true, description: '' })
    paramErrors.value = {}
  }

  function removeParamRow(index: number): void {
    form.params.splice(index, 1)
    paramErrors.value = {}
  }

  function moveParamRow(index: number, step: -1 | 1): void {
    const target = index + step
    if (target < 0 || target >= form.params.length) return
    const rows = form.params
    ;[rows[index], rows[target]] = [rows[target], rows[index]]
    paramErrors.value = {}
  }

  function resetForm(): void {
    form.id = ''
    form.name = ''
    form.description = ''
    form.params = []
    form.script = ''
    form.scope = 'project'
    clearFormErrors()
  }

  function startCreate(): void {
    selectedType.value = 'custom'
    selectedName.value = ''
    selectedCustomId.value = ''
    customDetail.value = null
    resetForm()
    panelMode.value = 'create'
  }

  function startEdit(): void {
    if (!customDetail.value) return
    form.id = customDetail.value.id
    form.name = customDetail.value.name
    form.description = customDetail.value.description ?? ''
    form.params = parseParamsDesc(customDetail.value.paramsDesc)
    form.script = customDetail.value.script
    form.scope = customDetail.value.scope
    clearFormErrors()
    panelMode.value = 'edit'
  }

  function cancelEdit(): void {
    panelMode.value = 'view'
    clearFormErrors()
    if (selectedCustomId.value === '') {
      selectedType.value = null
      selectedName.value = ''
    }
  }

  function validateForm(): boolean {
    clearFormErrors()
    if (!form.name.trim()) {
      formErrors.value.name = '请输入函数名称'
      ElMessage.warning('请填写函数名称')
      return false
    }
    if (!form.script.trim()) {
      formErrors.value.script = '请输入 Groovy 脚本'
      ElMessage.warning('请填写 Groovy 脚本')
      return false
    }
    const errors = validateParamRows(form.params)
    if (errors.length > 0) {
      paramErrors.value = Object.fromEntries(errors.map((e) => [e.index, e.message]))
      ElMessage.warning(errors[0].message)
      return false
    }
    return true
  }

  async function submitForm(): Promise<void> {
    if (!validateForm()) return
    saving.value = true
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        paramsDesc: serializedParams.value || undefined,
        script: form.script.trim(),
        scope: form.scope,
      }
      if (panelMode.value === 'create') {
        const resp = await createCustomFunction(payload)
        ElMessage.success('函数已创建')
        await loadCustomList()
        panelMode.value = 'view'
        selectItem('custom', form.name.trim(), resp.id)
      } else {
        await updateCustomFunction(form.id, payload)
        ElMessage.success('已保存')
        await loadCustomList()
        panelMode.value = 'view'
        if (selectedCustomId.value === form.id) {
          customDetail.value = await fetchCustomFunctionDetail(form.id)
        }
      }
    } catch (err) {
      ElMessage.error(resolveFunctionError(err))
    } finally {
      saving.value = false
    }
  }

  // ==================== Toggle ====================

  function handleToggleItem(item: DisplayListItem): void {
    void handleToggle(item)
  }

  // 入参只依赖 id/enabled，列表项与详情（勾选即时启停）均可直接传入
  async function handleToggle(item: { id?: string; enabled?: boolean }): Promise<void> {
    if (!item.id) return
    try {
      await toggleCustomFunction(item.id, !item.enabled)
      ElMessage.success(item.enabled ? '已停用' : '已启用')
      await loadCustomList()
      if (selectedCustomId.value === item.id && customDetail.value) {
        customDetail.value.enabled = !item.enabled
      }
    } catch (err) {
      ElMessage.error(resolveFunctionError(err))
    }
  }

  // ==================== Delete ====================

  function handleDeleteItem(item: DisplayListItem): void {
    void handleDelete(item as ApiCustomFunctionListItem)
  }

  async function handleDelete(item: ApiCustomFunctionListItem): Promise<void> {
    try {
      await ElMessageBox.confirm(`删除后函数不可恢复，确认删除「${item.name}」？`, '删除函数', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deleteCustomFunction(item.id)
      ElMessage.success('已删除')
      if (selectedCustomId.value === item.id) {
        selectedType.value = null
        selectedName.value = ''
        selectedCustomId.value = ''
        customDetail.value = null
      }
      await loadCustomList()
    } catch (err) {
      ElMessage.error(resolveFunctionError(err))
    }
  }

  onMounted(() => void loadAll())

  return {
    canEdit,
    listLoading,
    loadError,
    builtinGroups,
    customList,
    keyword,
    activeTab,
    handleSearchInput,
    filtered,
    displayItems,
    selectedType,
    selectedName,
    selectedCustomId,
    loadBuiltin,
    loadCustomList,
    loadAll,
    selectItem,
    selectedBuiltinFn,
    customDetail,
    detailLoading,
    customParams,
    customSignature,
    panelMode,
    form,
    formErrors,
    paramErrors,
    serializedParams,
    addParamRow,
    removeParamRow,
    moveParamRow,
    saving,
    resetForm,
    startCreate,
    startEdit,
    cancelEdit,
    validateForm,
    submitForm,
    handleToggleItem,
    handleToggle,
    handleDeleteItem,
    handleDelete,
    FUNCTION_TAB_OPTIONS,
    SCOPE_OPTIONS,
  }
}
