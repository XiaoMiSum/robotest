import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  fetchProjectStatusCounts,
  fetchProjects,
} from '@/services/workspace'
import type {
  Project,
  ProjectStatus,
  ProjectStatusCounts,
} from '@/types'

const SEARCH_DEBOUNCE_MS = 300
const DEFAULT_COUNTS: ProjectStatusCounts = { active: 0, archived: 0 }

export interface ProjectListPageOptions {
  autoLoad?: boolean
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeCounts(result: ProjectStatusCounts): ProjectStatusCounts {
  return {
    active: result.active ?? 0,
    archived: result.archived ?? 0,
  }
}

export function useProjectListPage(options: ProjectListPageOptions = {}) {
  const authStore = useAuthStore()
  const router = useRouter()

  const projects = ref<Project[]>([])
  const total = ref(0)
  const counts = ref<ProjectStatusCounts>({ ...DEFAULT_COUNTS })
  const keyword = ref('')
  const status = ref<ProjectStatus>('active')
  const pageNo = ref(1)
  const pageSize = ref(12)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const hasLoadedOnce = ref(false)

  let requestSequence = 0
  let activeRequestKey: string | null = null
  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let lastImmediateSearchKey: string | null = null
  let lastImmediateSearchAt = 0
  let skipKeywordWatch = false
  let disposed = false

  const isInitialLoading = computed(() => loading.value && !hasLoadedOnce.value)
  const isRefreshing = computed(() => loading.value && hasLoadedOnce.value)
  const hasFilters = computed(() => Boolean(keyword.value.trim()) || status.value !== 'active')
  const workspaceProjectCount = computed(() => counts.value.active + counts.value.archived)
  const isWorkspaceEmpty = computed(
    () => !keyword.value.trim() && status.value === 'active' && workspaceProjectCount.value === 0,
  )
  const currentStatusLabel = computed(() => (status.value === 'active' ? '活跃' : '已归档'))

  function requestKey(): string {
    return [status.value, keyword.value.trim(), pageNo.value, pageSize.value].join('|')
  }

  function cancelDebounce(): void {
    if (debounceTimer !== null) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  function scheduleSearch(): void {
    cancelDebounce()
    debounceTimer = setTimeout(() => {
      debounceTimer = null
      lastImmediateSearchKey = requestKey()
      lastImmediateSearchAt = Date.now()
      void loadProjects()
    }, SEARCH_DEBOUNCE_MS)
  }

  async function loadProjects(loadOptions: { force?: boolean } = {}): Promise<void> {
    if (disposed) return
    const key = requestKey()
    if (!loadOptions.force && loading.value && activeRequestKey === key) return

    const sequence = ++requestSequence
    activeRequestKey = key
    loading.value = true
    error.value = null

    try {
      const requestKeyword = keyword.value.trim() || undefined
      const [page, statusCounts] = await Promise.all([
        fetchProjects({
          keyword: requestKeyword,
          status: status.value,
          pageNo: pageNo.value,
          pageSize: pageSize.value,
        }),
        fetchProjectStatusCounts({ keyword: requestKeyword }),
      ])
      if (disposed || sequence !== requestSequence) return
      projects.value = page.list
      total.value = page.total
      counts.value = normalizeCounts(statusCounts)
      hasLoadedOnce.value = true
    } catch (loadError) {
      if (disposed || sequence !== requestSequence) return
      error.value = errorMessage(loadError, '加载项目列表失败')
    } finally {
      if (!disposed && sequence === requestSequence) {
        loading.value = false
        activeRequestKey = null
      }
    }
  }

  function handleSearch(): Promise<void> {
    pageNo.value = 1
    const key = requestKey()
    if (loading.value && activeRequestKey === key) {
      cancelDebounce()
      return Promise.resolve()
    }
    const now = Date.now()
    if (key === lastImmediateSearchKey && now - lastImmediateSearchAt < SEARCH_DEBOUNCE_MS) {
      cancelDebounce()
      return Promise.resolve()
    }
    lastImmediateSearchKey = key
    lastImmediateSearchAt = now
    cancelDebounce()
    return loadProjects()
  }

  function handleClear(): Promise<void> {
    if (keyword.value) {
      skipKeywordWatch = true
      keyword.value = ''
    }
    lastImmediateSearchKey = null
    pageNo.value = 1
    cancelDebounce()
    return loadProjects()
  }

  function clearFilters(): Promise<void> {
    if (keyword.value) {
      skipKeywordWatch = true
      keyword.value = ''
    }
    lastImmediateSearchKey = null
    status.value = 'active'
    pageNo.value = 1
    cancelDebounce()
    return loadProjects()
  }

  function changeStatus(nextStatus: ProjectStatus): Promise<void> {
    if (status.value === nextStatus) return Promise.resolve()
    cancelDebounce()
    status.value = nextStatus
    pageNo.value = 1
    return loadProjects()
  }

  function changePage(nextPage: number): Promise<void> {
    cancelDebounce()
    pageNo.value = nextPage
    return loadProjects()
  }

  function changePageSize(nextSize: number): Promise<void> {
    cancelDebounce()
    pageSize.value = nextSize
    pageNo.value = 1
    return loadProjects()
  }

  function retry(): Promise<void> {
    return loadProjects({ force: true })
  }

  function enterProject(project: Project): void {
    if (project.status !== 'active') return
    authStore.setActiveProject(project.id, project.name)
    void router.push('/workspace/projects/dashboard')
  }

  watch(keyword, () => {
    if (skipKeywordWatch) {
      skipKeywordWatch = false
      return
    }
    pageNo.value = 1
    lastImmediateSearchKey = null
    scheduleSearch()
  })

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      disposed = true
      cancelDebounce()
    })

    if (options.autoLoad !== false) {
      onMounted(() => {
        void loadProjects()
      })
    }
  }

  return {
    projects,
    total,
    counts,
    keyword,
    status,
    pageNo,
    pageSize,
    loading,
    error,
    isInitialLoading,
    isRefreshing,
    hasFilters,
    isWorkspaceEmpty,
    currentStatusLabel,
    loadProjects,
    handleSearch,
    handleClear,
    clearFilters,
    changeStatus,
    changePage,
    changePageSize,
    retry,
    enterProject,
  }
}
