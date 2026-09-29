// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  fetchPermissions: vi.fn<() => Promise<string[]>>(),
  revokeSession: vi.fn<(access: string | null, refresh: string | null) => Promise<void>>(),
}))

vi.mock('@/services/auth', () => ({
  fetchPermissions: mocks.fetchPermissions,
  revokeSession: mocks.revokeSession,
}))

import { useAuthStore } from './auth'

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  vi.clearAllMocks()
  mocks.fetchPermissions.mockResolvedValue([])
  mocks.revokeSession.mockResolvedValue(undefined)
  setActivePinia(createPinia())
})

describe('auth store 活动上下文', () => {
  it('从统一持久化快照恢复空间和项目状态', () => {
    localStorage.setItem('robotest_active_workspace', 'workspace-1')
    localStorage.setItem('robotest_active_workspace_name', '质量空间')
    localStorage.setItem('robotest_active_workspace_role', 'member')
    localStorage.setItem('robotest_active_project', 'project-1')
    localStorage.setItem('robotest_active_project_name', '核心项目')

    const auth = useAuthStore()

    expect(auth.activeWorkspace?.id).toBe('workspace-1')
    expect(auth.activeProject).toBe('project-1')
    expect(auth.activeProjectName).toBe('核心项目')
    expect(auth.activeWorkspaceId).toBe('workspace-1')
    expect(auth.activeProjectId).toBe('project-1')
  })

  it('切换工作空间时清理旧项目及其展示名称', () => {
    const auth = useAuthStore()
    auth.setActiveWorkspace({ id: 'workspace-1', name: '空间一', workspaceRole: 'member' })
    auth.setActiveProject('project-1', '项目一')

    auth.setActiveWorkspace({ id: 'workspace-2', name: '空间二', workspaceRole: 'member' })

    expect(auth.activeProject).toBeNull()
    expect(auth.activeProjectName).toBe('')
    expect(localStorage.getItem('robotest_active_project')).toBeNull()
    expect(localStorage.getItem('robotest_active_project_name')).toBeNull()
  })

  it('同一工作空间更新信息时保留当前项目', () => {
    const auth = useAuthStore()
    auth.setActiveWorkspace({ id: 'workspace-1', name: '空间一', workspaceRole: 'member' })
    auth.setActiveProject('project-1', '项目一')

    auth.setActiveWorkspace({ id: 'workspace-1', name: '空间一（更新）', workspaceRole: 'admin' })

    expect(auth.activeProject).toBe('project-1')
    expect(auth.activeProjectName).toBe('项目一')
  })

  it('没有活动空间时不能保留项目上下文', () => {
    localStorage.setItem('robotest_active_project', 'orphan-project')

    const auth = useAuthStore()
    expect(localStorage.getItem('robotest_active_project')).toBeNull()

    auth.setActiveProject('project-2', '项目二')

    expect(auth.activeProject).toBeNull()
    expect(localStorage.getItem('robotest_active_project')).toBeNull()
  })

  it('退出登录时先撤销服务端令牌再清理状态', async () => {
    const auth = useAuthStore()
    auth.setLogin(
      'access-1',
      'refresh-1',
      {
        id: 'user-1',
        username: 'tester',
        email: 'tester@example.com',
        status: 'active',
        roles: [],
        permissions: [],
        hasWorkspace: true,
      },
      { id: 'workspace-1', name: '空间一', workspaceRole: 'member' },
    )
    auth.setActiveProject('project-1', '项目一')

    await auth.logout()

    expect(mocks.revokeSession).toHaveBeenCalledWith('access-1', 'refresh-1')
    expect(auth.user).toBeNull()
    expect(auth.activeWorkspace).toBeNull()
    expect(auth.activeProject).toBeNull()
    expect(localStorage.getItem('robotest_active_workspace')).toBeNull()
    expect(localStorage.getItem('robotest_active_project')).toBeNull()
    expect(sessionStorage.getItem('robotest_access_token')).toBeNull()
  })

  it('服务端撤销失败时仍清理本地会话', async () => {
    const auth = useAuthStore()
    auth.setLogin(
      'access-1',
      'refresh-1',
      {
        id: 'user-1',
        username: 'tester',
        email: 'tester@example.com',
        status: 'active',
        roles: [],
        permissions: [],
        hasWorkspace: true,
      },
      { id: 'workspace-1', name: '空间一', workspaceRole: 'member' },
    )
    mocks.revokeSession.mockRejectedValue(new Error('network down'))

    await auth.logout()

    expect(auth.user).toBeNull()
    expect(sessionStorage.getItem('robotest_access_token')).toBeNull()
  })
})

describe('auth store 权限就绪状态', () => {
  it('没有远端拉取可等时立即就绪', async () => {
    const auth = useAuthStore()

    expect(auth.permissionsLoaded).toBe(true)
    await expect(auth.whenPermissionsReady()).resolves.toBeUndefined()
  })

  it('whenPermissionsReady 等待在途拉取完成后回填权限', async () => {
    let release!: (value: string[]) => void
    mocks.fetchPermissions.mockReturnValue(
      new Promise<string[]>((resolve) => {
        release = resolve
      }),
    )
    const auth = useAuthStore()

    const loading = auth.loadPermissions()
    const ready = auth.whenPermissionsReady()

    expect(auth.permissionsLoaded).toBe(false)
    release(['ws-invitation:manage'])
    await Promise.all([loading, ready])

    expect(auth.permissionsLoaded).toBe(true)
    expect(auth.permissions).toEqual(['ws-invitation:manage'])
    // 已完成过一次拉取后再次等待立即返回，不再阻塞调用方
    await expect(auth.whenPermissionsReady()).resolves.toBeUndefined()
  })

  it('并发调用复用同一次在途拉取', async () => {
    const auth = useAuthStore()

    void auth.loadPermissions()
    void auth.loadPermissions()
    await auth.whenPermissionsReady()

    expect(mocks.fetchPermissions).toHaveBeenCalledTimes(1)
  })

  it('权限拉取失败同样视为已就绪', async () => {
    mocks.fetchPermissions.mockRejectedValue(new Error('network down'))
    const auth = useAuthStore()

    await auth.loadPermissions()

    expect(auth.permissionsLoaded).toBe(true)
    expect(auth.permissions).toEqual([])
  })
})
