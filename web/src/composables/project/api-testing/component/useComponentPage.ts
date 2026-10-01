import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiComponentListItem,
  ApiComponentSaveReq,
  ApiDataSource,
  ApiHttpConfig,
} from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
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

/** 交互设计：每页 20 条服务端分页，滚动加载上下页，窗口最多保留 5 页（`docs/05-interaction-design/05-api-testing/22-global-asset-ui.md` 2.1） */
const PAGE_SIZE = 20
const MAX_WINDOW_PAGES = 5
const SEARCH_DEBOUNCE_MS = 300

/** 窗口内的单页切片：items 为去重后保留的条目，rawLen 为服务端原样条数（判定是否还有下一页） */
interface ComponentPageSlice {
  no: number
  items: ApiComponentListItem[]
  rawLen: number
}

/** 前插 / 丢首部会移动视口内容，由页面在变更落 DOM 前后捕获与回放 scrollTop 补偿 */
export interface ComponentPageScrollHooks {
  beforeShift(): void
  afterShift(): void
}

export interface UseComponentPageOptions {
  scroll?: ComponentPageScrollHooks
}

export function useComponentPage(options: UseComponentPageOptions = {}) {
  const scroll = options.scroll
  const authStore = useAuthStore()
  const canEdit = computed(() =>
    authStore.hasPermission('api-component:edit')
    || authStore.hasPermission('api-component:edit-space')
    || authStore.hasPermission('api-component:edit-global'),
  )

  // ==================== 列表 ====================

  const listLoading = ref(false)
  const loadError = ref(false)
  /** 窗口内的页切片（firstPage…lastPage 连续），list 为切片按序摊平 */
  const pages = ref<ComponentPageSlice[]>([])
  const total = ref(0)
  const loadingMore = ref(false)
  const loadingPrev = ref(false)
  /** 滚动加载失败的行内错误：保留已加载数据并提供行内重试 */
  const moreError = ref(false)
  const prevError = ref(false)
  const keyword = ref('')
  const filterType = ref<ComponentTab>('all')
  const selectedId = ref<string | null>(null)
  const panelMode = ref<ComponentPanelMode>('view')
  /** 选中项被滑出窗口后维持右栏详情的缓存 */
  const selectedCache = ref<ApiComponentListItem | null>(null)
  /** 删除后按原位置回选下一项；null 表示按默认策略（首项）回选 */
  let pendingSelectIndex: number | null = null
  /** 递增以丢弃过期请求：筛选重置会使在途滚动加载失效 */
  let requestSequence = 0

  const list = computed(() => pages.value.flatMap((slice) => slice.items))
  const firstPage = computed(() => pages.value[0]?.no ?? 1)
  const lastPage = computed(() => pages.value[pages.value.length - 1]?.no ?? 1)
  /** 末页取满整页才可能存在下一页，避免服务端条数漂移导致空转 */
  const hasMoreDown = computed(() => {
    const last = pages.value[pages.value.length - 1]
    return !!last && last.rawLen >= PAGE_SIZE && last.no * PAGE_SIZE < total.value
  })
  const hasMoreUp = computed(() => pages.value.length > 0 && firstPage.value > 1)

  function restoreSelection(): void {
    if (pendingSelectIndex !== null) {
      const index = Math.min(pendingSelectIndex, list.value.length - 1)
      pendingSelectIndex = null
      selectedId.value = index >= 0 ? list.value[index]?.id ?? null : null
      return
    }
    // 选中项不在当前窗口（筛选变化或页被丢弃）时保留 id，右栏由缓存继续展示
    if (selectedId.value && (list.value.some((item) => item.id === selectedId.value)
      || selectedCache.value?.id === selectedId.value)) return
    selectedId.value = list.value[0]?.id ?? null
  }

  function fetchPage(pageNo: number) {
    return fetchComponents({
      pageNo,
      pageSize: PAGE_SIZE,
      type: filterType.value === 'all' ? undefined : filterType.value,
      keyword: keyword.value.trim() || undefined,
    })
  }

  async function loadList(): Promise<void> {
    const seq = ++requestSequence
    listLoading.value = true
    loadError.value = false
    moreError.value = false
    prevError.value = false
    loadingMore.value = false
    loadingPrev.value = false
    try {
      const result = await fetchPage(1)
      if (seq !== requestSequence) return
      pages.value = [{ no: 1, items: result.list, rawLen: result.list.length }]
      total.value = result.total
      restoreSelection()
    } catch (err) {
      if (seq !== requestSequence) return
      loadError.value = true
      ElMessage.error(resolveComponentError(err))
    } finally {
      if (seq === requestSequence) listLoading.value = false
    }
  }

  async function trimTop(): Promise<void> {
    while (pages.value.length > MAX_WINDOW_PAGES) {
      scroll?.beforeShift()
      pages.value = pages.value.slice(1)
      await nextTick()
      scroll?.afterShift()
    }
  }

  /** 滚动至底部附近：加载下一页追加，超出窗口丢弃最前页 */
  async function loadMore(): Promise<void> {
    if (listLoading.value || loadingMore.value || loadingPrev.value || !hasMoreDown.value) return
    const seq = requestSequence
    const targetNo = lastPage.value + 1
    loadingMore.value = true
    moreError.value = false
    try {
      const result = await fetchPage(targetNo)
      if (seq !== requestSequence) return
      const known = new Set(list.value.map((item) => item.id))
      const items = result.list.filter((item) => !known.has(item.id))
      pages.value = [...pages.value, { no: targetNo, items, rawLen: result.list.length }]
      total.value = result.total
      // 先让追加落 DOM，再捕获基线丢首部，保证补偿量只含被丢弃的条目
      await nextTick()
      await trimTop()
    } catch (err) {
      if (seq !== requestSequence) return
      moreError.value = true
      ElMessage.error(resolveComponentError(err))
    } finally {
      if (seq === requestSequence) loadingMore.value = false
    }
  }

  /** 滚动至顶部附近：加载上一页前插，超出窗口丢弃最远端（末页位于视口下方，无需补偿） */
  async function loadPrev(): Promise<void> {
    if (listLoading.value || loadingMore.value || loadingPrev.value || !hasMoreUp.value) return
    const seq = requestSequence
    const targetNo = firstPage.value - 1
    loadingPrev.value = true
    prevError.value = false
    try {
      const result = await fetchPage(targetNo)
      if (seq !== requestSequence) return
      const known = new Set(list.value.map((item) => item.id))
      const items = result.list.filter((item) => !known.has(item.id))
      scroll?.beforeShift()
      pages.value = [{ no: targetNo, items, rawLen: result.list.length }, ...pages.value]
      await nextTick()
      scroll?.afterShift()
      if (pages.value.length > MAX_WINDOW_PAGES) pages.value = pages.value.slice(0, MAX_WINDOW_PAGES)
      total.value = result.total
    } catch (err) {
      if (seq !== requestSequence) return
      prevError.value = true
      ElMessage.error(resolveComponentError(err))
    } finally {
      if (seq === requestSequence) loadingPrev.value = false
    }
  }

  /** 变更操作后按当前窗口范围顺序重取：保持滚动位置与右栏选中，按 id 去重防御排序漂移 */
  async function reloadWindow(): Promise<void> {
    const seq = ++requestSequence
    loadingMore.value = false
    loadingPrev.value = false
    const wanted = pages.value.map((slice) => slice.no)
    const merged: ComponentPageSlice[] = []
    try {
      for (const no of wanted) {
        const result = await fetchPage(no)
        if (seq !== requestSequence) return
        const items: ApiComponentListItem[] = []
        for (const item of result.list) {
          if (merged.some((slice) => slice.items.some((existed) => existed.id === item.id))) continue
          items.push(item)
        }
        merged.push({ no, items, rawLen: result.list.length })
        total.value = result.total
      }
      pages.value = merged
      restoreSelection()
    } catch (err) {
      // 刷新失败保留旧列表仅提示：旧数据仍可操作，避免整列表塌陷为空态
      ElMessage.error(resolveComponentError(err))
    }
  }

  /** 选中项不在当前窗口时就地补丁缓存；在窗口内则由列表数据自动刷新，无需补丁 */
  function refreshCache(id: string, patch: Partial<ApiComponentListItem>): void {
    if (selectedCache.value?.id !== id) return
    if (list.value.some((item) => item.id === id)) return
    selectedCache.value = { ...selectedCache.value, ...patch }
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

  /** 列表命中优先（实时同步服务端状态），否则回落到选中时缓存的详情 */
  const selectedItem = computed(() => {
    const id = selectedId.value
    if (!id) return null
    const inList = list.value.find((item) => item.id === id)
    if (inList) return inList
    return selectedCache.value?.id === id ? selectedCache.value : null
  })

  watch(selectedItem, (item) => {
    if (item) selectedCache.value = item
  })

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

  // flush:sync —— 保证「复制」等程序化赋值先设类型再设配置时不被异步回调覆盖
  watch(() => form.type, () => {
    if (!editingId.value) {
      form.config = form.type === 'preprocessor' || form.type === 'postprocessor' ? createProcessorComponentConfig() : {}
    }
  }, { flush: 'sync' })

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

  /** 复制：以源组件配置回填新建面板（editingId 置空使保存走创建接口），名称追加「 (副本)」 */
  function startCopy(item: ApiComponentListItem | null): void {
    if (!item) return
    editingId.value = null
    form.type = item.type
    form.name = `${item.name} (副本)`
    form.description = item.description ?? ''
    form.scope = item.scope
    form.sortOrder = typeof item.sortOrder === 'number' ? item.sortOrder : 0
    form.config = parseComponentConfig(item.config)
    void loadProcessorRefOptions()
    panelMode.value = 'create'
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
        const id = editingId.value
        await updateComponent(id, payload)
        ElMessage.success('已更新')
        await reloadWindow()
        selectedId.value = id
        refreshCache(id, {
          name: payload.name,
          description: payload.description ?? null,
          sortOrder: payload.sortOrder ?? 0,
          config: payload.config ? JSON.stringify(payload.config) : null,
        })
      } else {
        payload.scope = form.scope
        const resp = await createComponent(payload)
        ElMessage.success('已创建')
        await reloadWindow()
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
      await reloadWindow()
      refreshCache(item.id, { enabled })
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
      await reloadWindow()
    } catch (err) {
      pendingSelectIndex = null
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
    total,
    loadingMore,
    loadingPrev,
    moreError,
    prevError,
    hasMoreDown,
    hasMoreUp,
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
    loadMore,
    loadPrev,
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
    startCopy,
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
