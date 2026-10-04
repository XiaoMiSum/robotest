import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fetchRequirements, getRequirement } from '@/services/project'
import type { RequirementDetail, RequirementListItem } from '@/types'

const SEARCH_DEBOUNCE_MS = 1000
const PAGE_SIZE = 10

/** 选取器条目摘要（跨页已选回显，不依赖列表当前页） */
export interface RequirementPick {
  id: string
  code: string
  title: string
}

function pickOf(item: RequirementListItem): RequirementPick {
  return { id: item.id, code: item.code, title: item.title }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 「选择需求」选取器（交互 06 关联需求）：关键字过滤 + 分页多选 + 已选计数，
 * 明细按需拉取需求详情展示 Markdown 正文；已选跨页保留由 Map 承载。
 */
export function useRequirementPicker() {
  const rows = ref<RequirementListItem[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const loading = ref(false)
  const hasLoaded = ref(false)
  const loadError = ref('')
  const keyword = ref('')
  /** 已选（含跨页与当前筛选外的条目），key 为需求 ID */
  const selected = ref(new Map<string, RequirementPick>())

  const detailLoading = ref(false)
  const detail = ref<RequirementDetail | null>(null)
  const detailError = ref('')

  const selectedCount = computed(() => selected.value.size)
  const selectedIds = computed(() => [...selected.value.keys()])

  let requestId = 0
  let detailRequestId = 0
  let keywordTimer: ReturnType<typeof setTimeout> | null = null
  let searchedKeyword = ''

  function clearKeywordTimer() {
    if (keywordTimer) {
      clearTimeout(keywordTimer)
      keywordTimer = null
    }
  }

  async function load() {
    const id = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchRequirements({
        keyword: keyword.value.trim() || undefined,
        pageNo: pageNo.value,
        pageSize: PAGE_SIZE,
      })
      if (id !== requestId) return
      rows.value = page.list
      total.value = page.total
      hasLoaded.value = true
    } catch (err) {
      if (id !== requestId) return
      loadError.value = errorMessage(err, '加载需求列表失败')
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  function search(): void {
    clearKeywordTimer()
    searchedKeyword = keyword.value.trim()
    pageNo.value = 1
    void load()
  }

  function reset(): void {
    keyword.value = ''
    searchedKeyword = ''
    pageNo.value = 1
    rows.value = []
    total.value = 0
    hasLoaded.value = false
    loadError.value = ''
    void load()
  }

  watch(keyword, () => {
    clearKeywordTimer()
    keywordTimer = setTimeout(() => {
      if (keyword.value.trim() === searchedKeyword) return
      search()
    }, SEARCH_DEBOUNCE_MS)
  })

  function changePage(next: number): void {
    pageNo.value = next
    void load()
  }

  function toggle(row: RequirementListItem): void {
    const next = new Map(selected.value)
    if (next.has(row.id)) next.delete(row.id)
    else next.set(row.id, pickOf(row))
    selected.value = next
  }

  function clearSelected(): void {
    selected.value = new Map()
  }

  /** 打开弹窗时回填既有关联（列表当前页之外的条目也参与已选计数） */
  function seed(items: RequirementPick[]): void {
    selected.value = new Map(items.map((item) => [item.id, item]))
  }

  async function openDetail(row: { id: string }): Promise<void> {
    const seq = ++detailRequestId
    detailLoading.value = true
    detailError.value = ''
    try {
      const data = await getRequirement(row.id)
      if (seq !== detailRequestId) return
      detail.value = data
    } catch (err) {
      if (seq !== detailRequestId) return
      detail.value = null
      detailError.value = errorMessage(err, '加载需求明细失败')
    } finally {
      if (seq === detailRequestId) detailLoading.value = false
    }
  }

  function closeDetail(): void {
    detailRequestId += 1
    detail.value = null
    detailError.value = ''
    detailLoading.value = false
  }

  onBeforeUnmount(clearKeywordTimer)

  return {
    rows,
    total,
    pageNo,
    loading,
    hasLoaded,
    loadError,
    keyword,
    selected,
    selectedCount,
    selectedIds,
    detail,
    detailLoading,
    detailError,
    load,
    retry,
    search,
    reset,
    changePage,
    toggle,
    clearSelected,
    seed,
    openDetail,
    closeDetail,
  }
}
