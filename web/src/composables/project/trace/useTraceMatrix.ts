import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTraceMatrix } from '@/services/project'
import { fetchAiStatus } from '@/services/ai'
import { useAuthStore } from '@/stores/auth'
import { useTraceStore } from '@/stores/trace'
import type { TraceMatrixRow } from '@/types'
import {
  traceCoverageMeta,
  traceRowHighlight,
  traceRowHint,
  traceRequirementStatusMeta,
  type TraceCoverageMeta,
  type TraceRequirementStatusMeta,
} from '@/composables/project/trace/tracePresentation'

const SEARCH_DEBOUNCE_MS = 1000

export interface TraceMatrixRowView extends TraceMatrixRow {
  coverageMeta: TraceCoverageMeta | null
  statusMeta: TraceRequirementStatusMeta
  highlight: ReturnType<typeof traceRowHighlight>
  hint: string
}

function rowView(item: TraceMatrixRow): TraceMatrixRowView {
  return {
    ...item,
    coverageMeta: traceCoverageMeta(item.coverageStatus),
    statusMeta: traceRequirementStatusMeta(item.status),
    highlight: traceRowHighlight(item),
    hint: traceRowHint(item),
  }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

export function useTraceMatrix() {
  const authStore = useAuthStore()
  const traceStore = useTraceStore()

  const loading = ref(false)
  const hasLoaded = ref(false)
  const loadError = ref('')
  const rows = ref<TraceMatrixRowView[]>([])
  const total = ref(0)

  /** AI 未启用时覆盖列降级「—」、影响分析入口隐藏（交互 04 §2.6） */
  const aiAvailable = ref(false)
  const aiStatusLoaded = ref(false)

  const filters = reactive({
    requirementStatus: '',
    coverage: '',
    keyword: '',
  })

  let requestId = 0
  let keywordTimer: ReturnType<typeof setTimeout> | null = null
  let searchedKeyword = ''

  function clearKeywordTimer() {
    if (keywordTimer) {
      clearTimeout(keywordTimer)
      keywordTimer = null
    }
  }

  async function loadAiStatus() {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      aiAvailable.value = false
    } finally {
      aiStatusLoaded.value = true
    }
  }

  async function load() {
    const id = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchTraceMatrix({
        requirementStatus: filters.requirementStatus || undefined,
        coverage: filters.coverage || undefined,
        keyword: filters.keyword.trim() || undefined,
        pageNo: traceStore.pageNo,
        pageSize: traceStore.pageSize,
      })
      // 过期响应（快速翻页 / 重复搜索）直接丢弃，避免旧数据覆盖新结果
      if (id !== requestId) return
      rows.value = page.list.map(rowView)
      total.value = page.total
      hasLoaded.value = true
    } catch (err) {
      if (id !== requestId) return
      loadError.value = errorMessage(err, '加载追溯矩阵失败')
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  function search(): void {
    clearKeywordTimer()
    searchedKeyword = filters.keyword.trim()
    traceStore.applyFilters({
      requirementStatus: filters.requirementStatus,
      coverage: filters.coverage,
      keyword: filters.keyword,
    })
    void load()
  }

  function resetFilters(): void {
    filters.requirementStatus = ''
    filters.coverage = ''
    filters.keyword = ''
    traceStore.resetFilters()
    searchedKeyword = ''
    void load()
  }

  watch(
    () => filters.keyword,
    () => {
      clearKeywordTimer()
      keywordTimer = setTimeout(() => {
        if (filters.keyword.trim() === searchedKeyword) return
        search()
      }, SEARCH_DEBOUNCE_MS)
    },
  )

  const filterCount = computed(() => traceStore.filterCount)
  const pageNo = computed(() => traceStore.pageNo)
  const pageSize = computed(() => traceStore.pageSize)

  function changePage(next: number): void {
    traceStore.pageNo = next
    void load()
  }

  function changePageSize(next: number): void {
    traceStore.pageSize = next
    traceStore.pageNo = 1
    void load()
  }

  // ==================== 链路入口 ====================
  function openChain(row: TraceMatrixRow): void {
    traceStore.openChain({ type: 'requirement', id: row.requirementId, title: `${row.code} ${row.title}` })
  }

  const canEdit = computed(() => authStore.hasPermission('trace:edit'))

  onMounted(() => {
    // 保活回填：从详情或链路返回时沿用上次筛选与页码
    filters.requirementStatus = traceStore.filters.requirementStatus
    filters.coverage = traceStore.filters.coverage
    filters.keyword = traceStore.filters.keyword
    searchedKeyword = filters.keyword.trim()
    void loadAiStatus()
    void load()
  })

  onBeforeUnmount(clearKeywordTimer)

  return {
    loading,
    hasLoaded,
    loadError,
    rows,
    total,
    pageNo,
    pageSize,
    filters,
    filterCount,
    aiAvailable,
    aiStatusLoaded,
    canEdit,
    load,
    retry,
    search,
    resetFilters,
    changePage,
    changePageSize,
    openChain,
    notifyError: (error: unknown, fallback: string) => ElMessage.error(errorMessage(error, fallback)),
  }
}
