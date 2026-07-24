import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { NavMode } from '@/types'

export const useNavStore = defineStore('nav', () => {
  const currentMode = ref<NavMode>('none')

  const isAdminMode = computed(() => currentMode.value === 'admin')
  const isWorkspaceMode = computed(() => currentMode.value === 'workspace')
  const isProjectMode = computed(() => currentMode.value === 'project')

  /** Dynamic menu items based on current mode */
  const dynamicMenuItems = computed(() => {
    switch (currentMode.value) {
      case 'admin':
        return [
          { label: '用户管理', path: '/admin/users', icon: 'User' },
          { label: '角色管理', path: '/admin/roles', icon: 'Lock' },
          { label: '空间管理', path: '/admin/workspaces', icon: 'OfficeBuilding' },
        ]
      case 'workspace':
        return [
          { label: '空间信息', path: '/workspace/info', icon: 'InfoFilled' },
          { label: '成员管理', path: '/workspace/members', icon: 'UserFilled' },
          { label: '项目列表', path: '/workspace/projects', icon: 'Folder' },
        ]
      case 'project':
        return [
          { label: '功能测试', path: '/project/test', icon: 'Monitor' },
          { label: '接口测试', path: '/project/api-test', icon: 'Connection' },
          { label: '缺陷管理', path: '/project/bugs', icon: 'Warning' },
        ]
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
