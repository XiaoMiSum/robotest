import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export type RequirementTab = 'list'

export interface RequirementMenuItem {
  key: RequirementTab
  label: string
  icon: string
}

const menuItems: RequirementMenuItem[] = [{ key: 'list', label: '需求列表', icon: 'Tickets' }]

/** 子项与顶栏「需求管理」入口共用的权限口径（交互 06 §2.1.2） */
const permissionByTab: Record<RequirementTab, string> = {
  list: 'requirement:view',
}

export function useRequirementShell() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()

  /** 无权限时侧栏整体隐藏，避免只剩空壳的侧栏 */
  const visibleItems = computed(() =>
    menuItems.filter((item) => authStore.hasPermission(permissionByTab[item.key])),
  )

  /**
   * 请求 tab 无权或缺省时回退首个有权限子项。
   * 权限列表异步就绪，用一次性 ref 解析会在刷新瞬间卡在空态，computed 随权限到达重算
   */
  const activeTab = computed<RequirementTab | ''>(() => {
    const requested = String(route.query.tab ?? '')
    const matched = menuItems.find((item) => item.key === requested)
    if (matched && authStore.hasPermission(permissionByTab[matched.key])) return matched.key
    return visibleItems.value[0]?.key ?? ''
  })

  function handleMenuSelect(key: string): void {
    if (key === activeTab.value) return
    void router.replace({ query: { ...route.query, tab: key } })
  }

  return { visibleItems, activeTab, handleMenuSelect }
}
