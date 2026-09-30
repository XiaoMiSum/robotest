import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuInstance } from 'element-plus'

// 以 expose 契约替代组件类型导入，避免组合式函数反向依赖 pages
interface TestCaseExpose {
  confirmLeave: () => Promise<boolean>
}

const menuItems = [
  { key: 'cases', label: '测试用例', icon: 'Document' },
  { key: 'reviews', label: '测试评审', icon: 'Checked' },
  { key: 'plans', label: '测试计划', icon: 'Calendar' },
  { key: 'requirements', label: '需求池', icon: 'Tickets' },
]

export function useFunctionalTesting() {
  const route = useRoute()
  const router = useRouter()

  // ==================== Nav menu ====================

  const initialTab = String(route.query.tab ?? '')
  const activeMenu = ref(menuItems.some((m) => m.key === initialTab) ? initialTab : 'cases')
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
    activeMenu.value = key
    router.replace({ query: { ...route.query, tab: key } })
  }

  return {
    activeMenu,
    menuRef,
    testCaseRef,
    menuItems,
    handleMenuSelect,
  }
}
