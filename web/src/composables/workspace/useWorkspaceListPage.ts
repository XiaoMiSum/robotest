import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchMyWorkspaceCounts, fetchMyWorkspaces, setActiveWorkspacePreference } from '@/services/workspace'
import type {
  WorkspaceItem,
  WorkspaceScope,
  WorkspaceScopeCounts,
} from '@/types'
import { isWorkspaceArchived } from '@/utils/workspaceRole'

const SEARCH_DEBOUNCE_MS = 300
const DEFAULT_COUNTS: WorkspaceScopeCounts = { all: 0, managed: 0, archived: 0 }

export interface WorkspaceListPageOptions {
  autoLoad?: boolean
}

function emptyCounts(): WorkspaceScopeCounts {
  return { ...DEFAULT_COUNTS }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeCounts(result: WorkspaceScopeCounts): WorkspaceScopeCounts {
  return {
    all: result.all ?? 0,
    managed: result.managed ?? 0,
    archived: result.archived ?? 0,
  }
}

export function useWorkspaceListPage(options: WorkspaceListPageOptions = {}) {
  const authStore = useAuthStore()
  const router = useRouter()

  const workspaces = ref<WorkspaceItem[]>([])
  const total = ref(0)
  const counts = ref<WorkspaceScopeCounts>(emptyCounts())
  const keyword = ref('')
  const scope = ref<WorkspaceScope>('all')
  const pageNo = ref(1)
  const pageSize = ref(12)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const hasLoadedOnce = ref(false)
  const switchingId = ref<string | null>(null)

  let requestSequence = 0
  let activeRequestKey: string | null = null
  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let lastImmediateSearchKey: string | null = null
  let lastImmediateSearchAt = 0
  let skipKeywordWatch = false
  let disposed = false

  const canCreate = computed(() => authStore.hasPermission('workspace:create'))
  const activeWorkspaceId = computed(() => authStore.activeWorkspace?.id ?? null)
  const isInitialLoading = computed(() => loading.value && !hasLoadedOnce.value)
  const isRefreshing = computed(() => loading.value && hasLoadedOnce.value)
  const hasFilters = computed(() => Boolean(keyword.value.trim()) || scope.value !== 'all')
  const isSwitching = computed(() => switchingId.value !== null)

  function requestKey(): string {
    return [scope.value, keyword.value.trim(), pageNo.value, pageSize.value].join('|')
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
      void loadWorkspaces()
    }, SEARCH_DEBOUNCE_MS)
  }

  async function loadWorkspaces(loadOptions: { force?: boolean } = {}): Promise<void> {
    if (disposed) return
    const key = requestKey()
    if (!loadOptions.force && loading.value && activeRequestKey === key) return

    const sequence = ++requestSequence
    activeRequestKey = key
    loading.value = true
    error.value = null

    try {
      const requestKeyword = keyword.value.trim() || undefined
      const [page, countsResult] = await Promise.all([
        fetchMyWorkspaces({
          keyword: requestKeyword,
          scope: scope.value,
          pageNo: pageNo.value,
          pageSize: pageSize.value,
        }),
        fetchMyWorkspaceCounts({ keyword: requestKeyword }),
      ])
      if (disposed || sequence !== requestSequence) return
      workspaces.value = page.list
      total.value = page.total
      counts.value = normalizeCounts(countsResult)
      hasLoadedOnce.value = true
    } catch (loadError) {
      if (disposed || sequence !== requestSequence) return
      const message = errorMessage(loadError, '加载工作空间列表失败')
      error.value = message
      ElMessage.error(message)
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
    const now = Date.now()
    if (key === lastImmediateSearchKey && now - lastImmediateSearchAt < SEARCH_DEBOUNCE_MS) {
      cancelDebounce()
      return Promise.resolve()
    }
    lastImmediateSearchKey = key
    lastImmediateSearchAt = now
    cancelDebounce()
    return loadWorkspaces()
  }

  function handleClear(): Promise<void> {
    if (keyword.value) {
      skipKeywordWatch = true
      keyword.value = ''
    }
    lastImmediateSearchKey = null
    pageNo.value = 1
    cancelDebounce()
    return loadWorkspaces()
  }

  function clearFilters(): Promise<void> {
    if (keyword.value) {
      skipKeywordWatch = true
      keyword.value = ''
    }
    lastImmediateSearchKey = null
    scope.value = 'all'
    pageNo.value = 1
    cancelDebounce()
    return loadWorkspaces()
  }

  function changeScope(nextScope: WorkspaceScope): Promise<void> {
    if (scope.value === nextScope) return Promise.resolve()
    cancelDebounce()
    scope.value = nextScope
    pageNo.value = 1
    return loadWorkspaces()
  }

  function changePage(nextPage: number): Promise<void> {
    cancelDebounce()
    pageNo.value = nextPage
    return loadWorkspaces()
  }

  function changePageSize(nextSize: number): Promise<void> {
    cancelDebounce()
    pageSize.value = nextSize
    pageNo.value = 1
    return loadWorkspaces()
  }

  function retry(): Promise<void> {
    cancelDebounce()
    return loadWorkspaces({ force: true })
  }

  async function enterWorkspace(workspace: WorkspaceItem): Promise<void> {
    if (isWorkspaceArchived(workspace.status) || switchingId.value !== null) return
    switchingId.value = workspace.id
    try {
      await setActiveWorkspacePreference(workspace.id)
    } catch (switchError) {
      ElMessage.error(errorMessage(switchError, '切换工作空间失败'))
      switchingId.value = null
      return
    }

    try {
      authStore.setActiveWorkspace({
        id: workspace.id,
        name: workspace.name,
        workspaceRole: workspace.workspaceRole,
      })
      if (workspace.defaultProjectId) {
        authStore.setActiveProject(workspace.defaultProjectId, workspace.defaultProjectName)
        await router.push({ name: 'ProjectDashboard' })
      } else {
        authStore.setActiveProject(null)
        await router.push({ name: 'WorkspaceProjects' })
      }
    } finally {
      switchingId.value = null
    }
  }

  watch(keyword, () => {
    if (skipKeywordWatch) {
      skipKeywordWatch = false
      return
    }
    pageNo.value = 1
    if (loading.value && !hasLoadedOnce.value) return
    scheduleSearch()
  })

  if (options.autoLoad !== false && getCurrentInstance()) {
    onMounted(() => {
      void loadWorkspaces()
    })
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      disposed = true
      cancelDebounce()
      requestSequence += 1
    })
  }

  return {
    workspaces,
    total,
    counts,
    keyword,
    scope,
    pageNo,
    pageSize,
    loading,
    error,
    switchingId,
    canCreate,
    activeWorkspaceId,
    isInitialLoading,
    isRefreshing,
    hasFilters,
    isSwitching,
    loadWorkspaces,
    handleSearch,
    handleClear,
    clearFilters,
    changeScope,
    changePage,
    changePageSize,
    retry,
    enterWorkspace,
  }
}
