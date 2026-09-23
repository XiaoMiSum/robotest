import { defineStore } from 'pinia'
import { computed } from 'vue'
import type { RouteRecordNormalized } from 'vue-router'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { NavMode } from '@/types'

export interface NavMenuItem {
  label: string
  path: string
  icon: string
}

export interface NavMenuSection {
  title?: string
  items: NavMenuItem[]
}

export const useNavStore = defineStore('nav', () => {
  const authStore = useAuthStore()

  // 模式从当前路由 meta 派生：此前 watch 路径推导与各页面命令式 setMode 并存，
  // 登录页等处只能打补丁纠正残留模式，双真相源导致状态漂移
  const currentMode = computed<NavMode>(() => router.currentRoute.value.meta.mode ?? 'none')

  const isAdminMode = computed(() => currentMode.value === 'admin')
  const isWorkspaceMode = computed(() => currentMode.value === 'workspace')
  const isProjectMode = computed(() => currentMode.value === 'project')

  // 空间信息菜单路径含 :workspaceId 动态段：注册表存模板，渲染时用活跃空间填充
  function resolvePath(path: string): string {
    return path.replace(':workspaceId', authStore.activeWorkspace?.id ?? '')
  }

  function toMenuItem(record: RouteRecordNormalized): NavMenuItem | null {
    const menu = record.meta.menu
    if (!menu) return null
    if (menu.permission && !authStore.hasPermission(menu.permission)) return null
    if (menu.permissionAny && !menu.permissionAny.some((code) => authStore.hasPermission(code))) return null
    return { label: menu.label, icon: menu.icon, path: resolvePath(record.path) }
  }

  /** 按模式从路由注册表取菜单记录（meta.menu 缺省即不进菜单），按 order 升序 */
  function menuRecords(mode: NavMode): RouteRecordNormalized[] {
    return router
      .getRoutes()
      .filter((record) => record.meta.mode === mode && record.meta.menu)
      .sort((a, b) => (a.meta.menu?.order ?? 0) - (b.meta.menu?.order ?? 0))
  }

  /** 顶部动态菜单（交互设计 3.2/3.3）：当前模式的注册表菜单项再按权限过滤 */
  const dynamicMenuItems = computed<NavMenuItem[]>(() =>
    menuRecords(currentMode.value)
      .map(toMenuItem)
      .filter((item): item is NavMenuItem => item !== null),
  )

  /** 管理端侧边栏（AdminLayout）：同一路由注册表按 section 连续分组，权限过滤后为空的组不产生 */
  const adminSidebarSections = computed<NavMenuSection[]>(() => {
    const sections: NavMenuSection[] = []
    for (const record of menuRecords('admin')) {
      const item = toMenuItem(record)
      if (!item) continue
      const title = record.meta.menu?.section
      const last = sections[sections.length - 1]
      if (last && last.title === title) {
        last.items.push(item)
      } else {
        sections.push({ title, items: [item] })
      }
    }
    return sections
  })

  return {
    currentMode,
    isAdminMode,
    isWorkspaceMode,
    isProjectMode,
    dynamicMenuItems,
    adminSidebarSections,
  }
})
