import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import type { PageResult, WorkspaceItem, WorkspaceScopeCounts } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchMyWorkspaces: vi.fn(),
  fetchMyWorkspaceCounts: vi.fn(),
  setActiveWorkspacePreference: vi.fn(),
  ElMessage: { error: vi.fn() },
  authState: {
    activeWorkspace: null as { id: string; name: string; workspaceRole: string } | null,
  },
  authStore: {
    hasPermission: vi.fn(() => true),
    setActiveWorkspace: vi.fn(),
    setActiveProject: vi.fn(),
  },
  router: { push: vi.fn() },
}))

vi.mock('@/services/workspace', () => ({
  fetchMyWorkspaces: mocks.fetchMyWorkspaces,
  fetchMyWorkspaceCounts: mocks.fetchMyWorkspaceCounts,
  setActiveWorkspacePreference: mocks.setActiveWorkspacePreference,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useWorkspaceListPage } from './useWorkspaceListPage'

type WorkspaceListResult = PageResult<WorkspaceItem>

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function makeWorkspace(overrides: Partial<WorkspaceItem> = {}): WorkspaceItem {
  return {
    id: 'workspace-1',
    name: '质量中台',
    description: '质量保障',
    workspaceRole: 'c0000000-0000-0000-0000-000000000001',
    workspaceRoleName: '管理员',
    defaultProjectId: 'project-1',
    defaultProjectName: '默认项目',
    memberCount: 24,
    projectCount: 6,
    testCaseCount: 1024,
    status: 'active',
    createdAt: '2026-09-18T02:24:00',
    lastAccessedAt: null,
    ...overrides,
  }
}

function result(overrides: Partial<WorkspaceListResult> = {}): WorkspaceListResult {
  return {
    list: [makeWorkspace()],
    total: 1,
    ...overrides,
  }
}

function counts(overrides: Partial<WorkspaceScopeCounts> = {}): WorkspaceScopeCounts {
  return {
    all: 6,
    managed: 2,
    archived: 1,
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.authState.activeWorkspace = null
  mocks.authStore.hasPermission.mockReturnValue(true)
  mocks.authStore.setActiveWorkspace.mockImplementation(
    (workspace: typeof mocks.authState.activeWorkspace) => {
      mocks.authState.activeWorkspace = workspace
    },
  )
  mocks.router.push.mockResolvedValue(undefined)
  mocks.fetchMyWorkspaces.mockResolvedValue(result())
  mocks.fetchMyWorkspaceCounts.mockResolvedValue(counts())
  mocks.setActiveWorkspacePreference.mockResolvedValue(undefined)
})

describe('useWorkspaceListPage', () => {
  it('加载列表时传递关键词、范围、分页并采用接口计数', async () => {
    const sut = useWorkspaceListPage({ autoLoad: false })
    sut.keyword.value = '质量'
    sut.scope.value = 'managed'

    await sut.loadWorkspaces()

    expect(mocks.fetchMyWorkspaces).toHaveBeenCalledWith({
      keyword: '质量',
      scope: 'managed',
      pageNo: 1,
      pageSize: 12,
    })
    expect(mocks.fetchMyWorkspaceCounts).toHaveBeenCalledWith({ keyword: '质量' })
    expect(sut.workspaces.value[0]?.name).toBe('质量中台')
    expect(sut.total.value).toBe(1)
    expect(sut.counts.value).toEqual({ all: 6, managed: 2, archived: 1 })
  })

  it('关键词输入在 300ms 后查询，Enter 可立即查询', async () => {
    vi.useFakeTimers()
    try {
      let resolveList!: (value: WorkspaceListResult) => void
      mocks.fetchMyWorkspaces.mockReturnValueOnce(
        new Promise<WorkspaceListResult>((resolve) => {
          resolveList = resolve
        }),
      )
      const sut = useWorkspaceListPage({ autoLoad: false })
      sut.keyword.value = '质量'
      await nextTick()

      await vi.advanceTimersByTimeAsync(299)
      expect(mocks.fetchMyWorkspaces).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      expect(mocks.fetchMyWorkspaces).toHaveBeenCalledTimes(1)

      await sut.handleSearch()
      expect(mocks.fetchMyWorkspaces).toHaveBeenCalledTimes(1)
      resolveList(result())
      await Promise.resolve()
    } finally {
      vi.useRealTimers()
    }
  })

  it('忽略晚到的旧列表响应', async () => {
    const first = deferred<WorkspaceListResult>()
    const second = deferred<WorkspaceListResult>()
    mocks.fetchMyWorkspaces.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const sut = useWorkspaceListPage({ autoLoad: false })

    const firstLoad = sut.loadWorkspaces()
    sut.keyword.value = '新空间'
    const secondLoad = sut.handleSearch()

    second.resolve(result({ list: [makeWorkspace({ id: 'new', name: '新空间' })], total: 1 }))
    await secondLoad
    first.resolve(result({ list: [makeWorkspace({ id: 'old', name: '旧空间' })], total: 1 }))
    await firstLoad

    expect(sut.workspaces.value[0]?.id).toBe('new')
  })

  it('忽略晚到的旧列表错误，不覆盖当前结果或重复提示', async () => {
    const first = deferred<WorkspaceListResult>()
    const second = deferred<WorkspaceListResult>()
    mocks.fetchMyWorkspaces.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const sut = useWorkspaceListPage({ autoLoad: false })
    const firstLoad = sut.loadWorkspaces()
    sut.keyword.value = '新空间'
    const secondLoad = sut.handleSearch()
    second.resolve(result({ list: [makeWorkspace({ id: 'new', name: '新空间' })] }))
    await secondLoad
    first.reject(new Error('旧请求失败'))
    await firstLoad
    expect(sut.workspaces.value[0]?.id).toBe('new')
    expect(sut.error.value).toBeNull()
    expect(mocks.ElMessage.error).not.toHaveBeenCalled()
  })

  it('列表失败时优先展示后端 Error.message 并结束 loading', async () => {
    mocks.fetchMyWorkspaces.mockRejectedValue(new Error('空间接口失败'))
    const sut = useWorkspaceListPage({ autoLoad: false })
    await sut.loadWorkspaces()
    expect(sut.error.value).toBe('空间接口失败')
    expect(sut.loading.value).toBe(false)
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('空间接口失败')
  })

  it('偏好接口失败时不更新本地空间也不导航', async () => {
    mocks.setActiveWorkspacePreference.mockRejectedValue(new Error('偏好保存失败'))
    const sut = useWorkspaceListPage({ autoLoad: false })

    await sut.enterWorkspace(makeWorkspace())

    expect(mocks.setActiveWorkspacePreference).toHaveBeenCalledWith('workspace-1')
    expect(mocks.authStore.setActiveWorkspace).not.toHaveBeenCalled()
    expect(mocks.authStore.setActiveProject).not.toHaveBeenCalled()
    expect(mocks.router.push).not.toHaveBeenCalled()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('偏好保存失败')
  })

  it('连续进入同一空间只发送一次偏好请求，并在成功后按默认项目路由', async () => {
    const preference = deferred<void>()
    mocks.setActiveWorkspacePreference.mockReturnValue(preference.promise)
    const sut = useWorkspaceListPage({ autoLoad: false })

    const first = sut.enterWorkspace(makeWorkspace())
    const second = sut.enterWorkspace(makeWorkspace())
    expect(mocks.setActiveWorkspacePreference).toHaveBeenCalledTimes(1)

    preference.resolve()
    await Promise.all([first, second])

    expect(mocks.authStore.setActiveWorkspace).toHaveBeenCalledWith({
      id: 'workspace-1',
      name: '质量中台',
      workspaceRole: 'c0000000-0000-0000-0000-000000000001',
    })
    expect(mocks.authStore.setActiveProject).toHaveBeenCalledWith('project-1', '默认项目')
    expect(mocks.router.push).toHaveBeenCalledWith({ name: 'ProjectDashboard' })
  })

  it('分页与每页数量变化都会重新请求并重置到有效页', async () => {
    const sut = useWorkspaceListPage({ autoLoad: false })
    sut.pageNo.value = 3

    await sut.changePage(3)
    expect(mocks.fetchMyWorkspaces).toHaveBeenLastCalledWith({
      keyword: undefined,
      scope: 'all',
      pageNo: 3,
      pageSize: 12,
    })

    sut.pageSize.value = 24
    await sut.changePageSize(24)
    expect(mocks.fetchMyWorkspaces).toHaveBeenLastCalledWith({
      keyword: undefined,
      scope: 'all',
      pageNo: 1,
      pageSize: 24,
    })
  })

  it('归档空间不可进入', async () => {
    const sut = useWorkspaceListPage({ autoLoad: false })

    await sut.enterWorkspace(makeWorkspace({ status: 'dissolved' }))

    expect(mocks.setActiveWorkspacePreference).not.toHaveBeenCalled()
    expect(mocks.router.push).not.toHaveBeenCalled()
  })
})
