import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginUser, ActiveWorkspace } from '@/types'
import {
  getAccessToken,
  setTokens,
  clearTokens,
  getActiveContext,
  setActiveWorkspaceId,
  setActiveProjectId,
} from '@/services'
import { fetchPermissions } from '@/services/auth'

const USER_KEY = 'robotest_user'
const WORKSPACE_NAME_KEY = 'robotest_active_workspace_name'
const WORKSPACE_ROLE_KEY = 'robotest_active_workspace_role'
const PROJECT_NAME_KEY = 'robotest_active_project_name'

function loadUser(): LoginUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as LoginUser) : null
  } catch {
    return null
  }
}

function loadActiveWorkspace(): ActiveWorkspace | null {
  const { workspaceId } = getActiveContext()
  if (!workspaceId) return null
  return {
    id: workspaceId,
    name: localStorage.getItem(WORKSPACE_NAME_KEY) ?? '',
    workspaceRole: localStorage.getItem(WORKSPACE_ROLE_KEY) ?? '',
  }
}

function loadActiveProject(): string | null {
  return getActiveContext().projectId
}

function loadActiveProjectName(): string {
  return getActiveContext().projectId ? (localStorage.getItem(PROJECT_NAME_KEY) ?? '') : ''
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<LoginUser | null>(loadUser())
  const activeWorkspace = ref<ActiveWorkspace | null>(loadActiveWorkspace())
  const activeProject = ref<string | null>(loadActiveProject())
  // 当前项目名：project 模式顶栏展示用；项目 id 与请求上下文共用同一份活动状态
  const activeProjectName = ref<string>(loadActiveProjectName())
  if (!activeProject.value) {
    setActiveProjectId(null)
    localStorage.removeItem(PROJECT_NAME_KEY)
  }
  const permissions = ref<string[]>([])

  // 页面刷新时权限仅存于内存已丢失，只要用户已恢复即重新拉取；
  // 纯系统管理员无 activeWorkspace，不能以其为前置条件，否则刷新后权限为空、菜单消失
  if (user.value) {
    loadPermissions()
  }

  const isLoggedIn = computed(() => !!getAccessToken() && !!user.value)
  const username = computed(() => user.value?.username ?? '')
  const avatarUrl = computed(() => user.value?.avatarUrl ?? '')
  const activeWorkspaceId = computed(() => activeWorkspace.value?.id ?? null)
  const activeProjectId = computed(() => activeProject.value)
  const hasWorkspace = computed(() => user.value?.hasWorkspace ?? false)
  const hasSystemRole = computed(() => {
    if (!user.value?.roles) return false
    return user.value.roles.some((r) => r === 'system' || r === 'SYSTEM')
  })

  const hasSystemPermission = computed(() => {
    return permissions.value.some(
      (p) => p.startsWith('user:') || p.startsWith('workspace:') || p.startsWith('role:'),
    )
  })

  const hasWorkspaceAccess = computed(() => {
    return permissions.value.some((p) => p.startsWith('ws-'))
  })

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  async function loadPermissions(): Promise<void> {
    try {
      const list = await fetchPermissions()
      permissions.value = list
    } catch {
      permissions.value = []
    }
  }

  function setLogin(
    accessToken: string,
    refreshToken: string,
    loginUser: LoginUser,
    workspace: ActiveWorkspace | null,
  ) {
    const previousUserId = user.value?.id
    setTokens(accessToken, refreshToken)
    user.value = loginUser
    localStorage.setItem(USER_KEY, JSON.stringify(loginUser))
    // 从 permissions 初始化权限（过滤 ROLE_ 前缀的角色名）
    permissions.value = loginUser.permissions?.filter((a) => !a.startsWith('ROLE_')) ?? []
    if (previousUserId !== loginUser.id) {
      setActiveProject(null)
    }
    setActiveWorkspace(workspace)
  }

  function setActiveWorkspace(workspace: ActiveWorkspace | null) {
    const previousWorkspaceId = activeWorkspace.value?.id ?? null
    const nextWorkspaceId = workspace?.id ?? null
    if (nextWorkspaceId !== previousWorkspaceId || nextWorkspaceId === null) {
      // 项目属于工作空间，切换或退出时先清掉旧项目，避免新空间请求携带旧项目头
      setActiveProject(null)
    }
    activeWorkspace.value = workspace

    if (workspace) {
      setActiveWorkspaceId(workspace.id)
      localStorage.setItem(WORKSPACE_NAME_KEY, workspace.name)
      localStorage.setItem(WORKSPACE_ROLE_KEY, workspace.workspaceRole)
      loadPermissions()
    } else {
      setActiveWorkspaceId(null)
      localStorage.removeItem(WORKSPACE_NAME_KEY)
      localStorage.removeItem(WORKSPACE_ROLE_KEY)
      permissions.value = []
    }
  }

  function setActiveProject(projectId: string | null, projectName?: string | null) {
    const nextProjectId = activeWorkspace.value?.id ? projectId?.trim() || null : null
    activeProject.value = nextProjectId
    setActiveProjectId(nextProjectId)
    if (nextProjectId) {
      if (projectName) {
        localStorage.setItem(PROJECT_NAME_KEY, projectName)
        activeProjectName.value = projectName
      } else {
        // 仅 id 无名称（如空间默认项目）：名称置空，顶栏项目 tag 不显示
        localStorage.removeItem(PROJECT_NAME_KEY)
        activeProjectName.value = ''
      }
    } else {
      localStorage.removeItem(PROJECT_NAME_KEY)
      activeProjectName.value = ''
    }
  }

  function logout() {
    user.value = null
    activeWorkspace.value = null
    activeProject.value = null
    activeProjectName.value = ''
    permissions.value = []
    clearTokens()
    setActiveWorkspaceId(null)
    setActiveProjectId(null)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(WORKSPACE_NAME_KEY)
    localStorage.removeItem(WORKSPACE_ROLE_KEY)
    localStorage.removeItem(PROJECT_NAME_KEY)
  }

  return {
    user,
    activeWorkspace,
    activeProject,
    activeProjectName,
    permissions,
    isLoggedIn,
    username,
    avatarUrl,
    activeWorkspaceId,
    activeProjectId,
    hasWorkspace,
    hasSystemRole,
    hasSystemPermission,
    hasWorkspaceAccess,
    hasPermission,
    loadPermissions,
    setLogin,
    setActiveWorkspace,
    setActiveProject,
    logout,
  }
})
