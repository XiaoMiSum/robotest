import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginUser, ActiveWorkspace } from '@/types'
import { getAccessToken, setTokens, clearTokens } from '@/services'
import { fetchPermissions } from '@/services/auth'

const USER_KEY = 'robotest_user'
const WORKSPACE_KEY = 'robotest_active_workspace'
const WORKSPACE_NAME_KEY = 'robotest_active_workspace_name'
const WORKSPACE_ROLE_KEY = 'robotest_active_workspace_role'
const PROJECT_KEY = 'robotest_active_project'

function loadUser(): LoginUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as LoginUser) : null
  } catch {
    return null
  }
}

function loadActiveWorkspace(): ActiveWorkspace | null {
  const id = localStorage.getItem(WORKSPACE_KEY)
  if (!id) return null
  return {
    id,
    name: localStorage.getItem(WORKSPACE_NAME_KEY) ?? '',
    workspaceRole: localStorage.getItem(WORKSPACE_ROLE_KEY) ?? '',
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<LoginUser | null>(loadUser())
  const activeWorkspace = ref<ActiveWorkspace | null>(loadActiveWorkspace())
  const permissions = ref<string[]>(user.value?.permissions ?? [])

  const isLoggedIn = computed(() => !!getAccessToken() && !!user.value)
  const username = computed(() => user.value?.username ?? '')
  const avatarUrl = computed(() => user.value?.avatarUrl ?? '')
  const hasWorkspace = computed(() => user.value?.hasWorkspace ?? false)
  const hasSystemRole = computed(() => {
    if (!user.value?.roles) return false
    return user.value.roles.some((r) => r === 'system' || r === 'SYSTEM')
  })

  const hasSystemPermission = computed(() => {
    return permissions.value.some((p) => p.startsWith('user:') || p.startsWith('workspace:') || p.startsWith('role:'))
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
    setTokens(accessToken, refreshToken)
    user.value = loginUser
    permissions.value = loginUser.permissions ?? []
    localStorage.setItem(USER_KEY, JSON.stringify(loginUser))
    setActiveWorkspace(workspace)
  }

  function setActiveWorkspace(workspace: ActiveWorkspace | null) {
    activeWorkspace.value = workspace
    if (workspace) {
      localStorage.setItem(WORKSPACE_KEY, workspace.id)
      localStorage.setItem(WORKSPACE_NAME_KEY, workspace.name)
      localStorage.setItem(WORKSPACE_ROLE_KEY, workspace.workspaceRole)
      loadPermissions()
    } else {
      localStorage.removeItem(WORKSPACE_KEY)
      localStorage.removeItem(WORKSPACE_NAME_KEY)
      localStorage.removeItem(WORKSPACE_ROLE_KEY)
      permissions.value = []
    }
  }

  function setActiveProject(projectId: string | null) {
    if (projectId) {
      localStorage.setItem(PROJECT_KEY, projectId)
    } else {
      localStorage.removeItem(PROJECT_KEY)
    }
  }

  function logout() {
    user.value = null
    activeWorkspace.value = null
    permissions.value = []
    clearTokens()
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(WORKSPACE_KEY)
    localStorage.removeItem(WORKSPACE_NAME_KEY)
    localStorage.removeItem(WORKSPACE_ROLE_KEY)
    localStorage.removeItem(PROJECT_KEY)
  }

  return {
    user,
    activeWorkspace,
    permissions,
    isLoggedIn,
    username,
    avatarUrl,
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
