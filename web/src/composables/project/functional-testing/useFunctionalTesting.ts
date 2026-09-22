import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuInstance } from 'element-plus'
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
} from '@/services/project/function'
import {
  filterFunctions,
  resolveFunctionError,
  SCOPE_OPTIONS,
  FUNCTION_TAB_OPTIONS,
  type FunctionTab,
} from '@/pages/project/api-testing/function/functionModel'
import type TestCasePage from '@/pages/project/functional-testing/TestCasePage.vue'

interface DisplayListItem {
  type: 'builtin' | 'custom'
  name: string
  description: string
  scope?: ApiFunctionScope
  id?: string
  enabled?: boolean
}

type CustomPanelMode = 'view' | 'create' | 'edit'

const menuItems = [
  { key: 'cases', label: '测试用例', icon: 'Document' },
  { key: 'reviews', label: '测试评审', icon: 'Checked' },
  { key: 'plans', label: '测试计划', icon: 'Calendar' },
  { key: 'requirements', label: '需求池', icon: 'Tickets' },
]

export function useFunctionalTesting() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()

  // ==================== Nav menu ====================

  const initialTab = String(route.query.tab ?? '')
  const activeMenu = ref(menuItems.some((m) => m.key === initialTab) ? initialTab : 'cases')
  const menuRef = ref<MenuInstance>()
  const testCaseRef = ref<InstanceType<typeof TestCasePage>>()

  async function handleMenuSelect(key: string) {
    if (key === activeMenu.value) return
    if (activeMenu.value === 'cases' && testCaseRef.value) {
      const ok = await testCaseRef.value.confirmLeave()
      if (!ok) {
        menuRef.value?.updateActiveIndex(activeMenu.value)
        return
      }
    }
    activeMenu.value = key
    router.replace({ query: { ...route.query, tab: key } })
  }

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

  const customParams = computed(() => {
    const desc = customDetail.value?.paramsDesc
    if (!desc) return []
    return desc.split(',').map((p) => {
      const seg = p.trim()
      return {
        name: seg.split(':')[0]?.trim() ?? '',
        required: true,
        description: seg,
      }
    })
  })

  const customSignature = computed(() => {
    const name = customDetail.value?.name ?? ''
    const args = customParams.value.map((p) => p.name).join(', ')
    return `\${${name}${args ? `(${args})` : '()'}}`
  })

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
    paramsDesc: '',
    script: '',
    scope: 'project' as ApiFunctionScope,
  })
  const saving = ref(false)

  function resetForm(): void {
    form.id = ''
    form.name = ''
    form.description = ''
    form.paramsDesc = ''
    form.script = ''
    form.scope = 'project'
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
    form.paramsDesc = customDetail.value.paramsDesc ?? ''
    form.script = customDetail.value.script
    form.scope = customDetail.value.scope
    panelMode.value = 'edit'
  }

  function cancelEdit(): void {
    panelMode.value = 'view'
    if (selectedCustomId.value === '') {
      selectedType.value = null
      selectedName.value = ''
    }
  }

  function validateForm(): boolean {
    if (!form.name.trim()) {
      ElMessage.warning('请填写函数名称')
      return false
    }
    if (!form.script.trim()) {
      ElMessage.warning('请填写 Groovy 脚本')
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
        paramsDesc: form.paramsDesc.trim() || undefined,
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

  async function handleToggle(item: DisplayListItem): Promise<void> {
    if (!item.id) return
    try {
      await toggleCustomFunction(item.id, !item.enabled)
      ElMessage.success(item.enabled ? '已禁用' : '已启用')
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
    activeMenu,
    menuRef,
    testCaseRef,
    menuItems,
    handleMenuSelect,
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
