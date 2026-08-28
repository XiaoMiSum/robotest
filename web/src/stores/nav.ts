import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { NavMode } from '@/types'
import { useAuthStore } from '@/stores/auth'

export const useNavStore = defineStore('nav', () => {
  const currentMode = ref<NavMode>('none')
  const authStore = useAuthStore()

  // 接口测试模块的全部 view 权限点，与 ApiTestingPage 侧边导航保持一致
  const API_MODULE_VIEW_PERMISSIONS = [
    'api-debug:view',
    'api-interface:view',
    'api-mock:view',
    'api-scene:view',
    'api-report:view',
    'api-timer:view',
    'api-env:view',
    'api-func:view',
    'api-component:view',
    'api-gitlab:view',
    'api-setting:view',
  ]

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
        if (has('ai:view')) items.push({ label: 'AI 配置', path: '/admin/ai-config', icon: 'MagicStick' })
        if (has('ai:view')) items.push({ label: '智能体', path: '/admin/ai-agents', icon: 'ChatDotRound' })
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
        if (has('case:view')) items.push({ label: '功能测试', path: '/workspace/projects/functional-testing', icon: 'Monitor' })
        if (has('bug:view')) items.push({ label: '缺陷管理', path: '/workspace/projects/bugs', icon: 'Warning' })
        // 菜单顺序对齐交互设计 3.3：接口测试位于缺陷管理之后；入口对任一模块 view 权限开放
        // （环境/函数/等仅持有项目设置类权限的用户也可进入，侧边导航内再按各自 view 过滤）
        if (API_MODULE_VIEW_PERMISSIONS.some(has)) {
          items.push({ label: '接口测试', path: '/workspace/projects/api-testing', icon: 'Connection' })
        }
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
