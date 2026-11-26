import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuInstance } from 'element-plus'

// 以 expose 契约替代组件类型导入，避免组合式函数反向依赖 pages
interface TestCaseExpose {
  confirmLeave: () => Promise<boolean>
}

interface MenuItem {
  key: string
  label: string
  icon: string
}

// 需求列表并入功能测试侧栏首位（交互 06 §1），四个子项均常驻可见
const menuItems: MenuItem[] = [
  { key: 'requirements', label: '需求列表', icon: 'Tickets' },
  { key: 'cases', label: '测试用例', icon: 'Document' },
  { key: 'reviews', label: '测试评审', icon: 'Checked' },
  { key: 'plans', label: '测试计划', icon: 'Calendar' },
]

export function useFunctionalTesting() {
  const route = useRoute()
  const router = useRouter()

  // ==================== Nav menu ====================

  const visibleItems = computed(() => menuItems)

  /**
   * 请求 tab 非可见子项或缺省时回退「测试用例」。
   * computed 随路由 query 重算，避免一次性 ref 解析在刷新瞬间落在错误子项
   */
  const activeMenu = computed<string>(() => {
    // 导入历史由本壳层承载但不属于菜单子项，侧栏持续高亮需求列表
    if (route.name === 'RequirementImportHistory') return 'requirements'
    const requested = String(route.query.tab ?? '')
    if (visibleItems.value.some((m) => m.key === requested)) return requested
    return 'cases'
  })
  const menuRef = ref<MenuInstance>()
  const testCaseRef = ref<TestCaseExpose>()

  async function handleMenuSelect(key: string) {
    if (key === activeMenu.value) return
    if (activeMenu.value === 'cases' && testCaseRef.value) {
      const ok = await testCaseRef.value.confirmLeave()
      if (!ok) {
        menuRef.value?.updateActiveIndex(activeMenu.value)
        return
      }
    }
    // 历史页无 tab，必须切回壳层主路由，否则只改 query 会停留在历史页
    router.replace({ name: 'FunctionalTesting', query: { ...route.query, tab: key } })
  }

  return {
    activeMenu,
    menuRef,
    testCaseRef,
    visibleItems,
    handleMenuSelect,
  }
}
