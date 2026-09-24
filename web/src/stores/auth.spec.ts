// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  fetchPermissions: vi.fn<() => Promise<string[]>>(),
}))

vi.mock('@/services/auth', () => ({
  fetchPermissions: mocks.fetchPermissions,
}))

import { useAuthStore } from './auth'

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  vi.clearAllMocks()
  mocks.fetchPermissions.mockResolvedValue([])
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

  it('退出登录时清理空间、项目和会话状态', () => {
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

    auth.logout()

    expect(auth.user).toBeNull()
    expect(auth.activeWorkspace).toBeNull()
    expect(auth.activeProject).toBeNull()
    expect(localStorage.getItem('robotest_active_workspace')).toBeNull()
    expect(localStorage.getItem('robotest_active_project')).toBeNull()
    expect(sessionStorage.getItem('robotest_access_token')).toBeNull()
  })
})
