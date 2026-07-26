import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { NavMode } from '@/types'
import { useAuthStore } from '@/stores/auth'

export const useNavStore = defineStore('nav', () => {
  const currentMode = ref<NavMode>('none')
  const authStore = useAuthStore()

  const isAdminMode = computed(() => currentMode.value === 'admin')
  const isWorkspaceMode = computed(() => currentMode.value === 'workspace')
  const isProjectMode = computed(() => currentMode.value === 'project')

  /** Dynamic menu items based on current mode, filtered by permissions */
  const dynamicMenuItems = computed(() => {
    const has = (code: string) => authStore.hasPermission(code)
    switch (currentMode.value) {
      case 'admin': {
        const items: Array<{ label: string; path: string; icon: string }> = []
        if (has('user:view')) items.push({ label: '用户管理', path: '/admin/users', icon: 'User' })
        if (has('workspace:view')) items.push({ label: '空间管理', path: '/admin/workspaces', icon: 'OfficeBuilding' })
        if (has('role:view')) items.push({ label: '角色管理', path: '/admin/roles', icon: 'Lock' })
        return items
      }
      case 'workspace': {
        const wsId = authStore.activeWorkspace?.id ?? ''
        const items: Array<{ label: string; path: string; icon: string }> = []
        if (has('ws-info:view')) items.push({ label: '空间信息', path: `/workspace/${wsId}`, icon: 'InfoFilled' })
        if (has('ws-member:view')) items.push({ label: '成员管理', path: '/workspace/members', icon: 'UserFilled' })
        if (has('project:view')) items.push({ label: '项目列表', path: '/workspace/projects', icon: 'Folder' })
        return items
      }
      case 'project': {
        const items: Array<{ label: string; path: string; icon: string }> = []
        if (has('case:view')) items.push({ label: '功能测试', path: '/workspace/projects/cases', icon: 'Monitor' })
        if (has('bug:view')) items.push({ label: '缺陷管理', path: '/workspace/projects/bugs', icon: 'Warning' })
        return items
      }
      default:
        return []
    }
  })

  function setMode(mode: NavMode) {
    currentMode.value = mode
  }

  return {
    currentMode,
    isAdminMode,
    isWorkspaceMode,
    isProjectMode,
    dynamicMenuItems,
    setMode,
  }
})
