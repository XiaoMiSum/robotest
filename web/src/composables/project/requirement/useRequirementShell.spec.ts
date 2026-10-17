import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

const mocks = vi.hoisted(() => ({
  useRoute: vi.fn(() => ({ query: {} })),
  useRouter: vi.fn(() => ({ replace: vi.fn() })),
  useAuthStore: vi.fn(() => ({ hasPermission: vi.fn(() => true) })),
}))

vi.mock('vue-router', () => ({
  useRoute: mocks.useRoute,
  useRouter: mocks.useRouter,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

import { useRequirementShell } from './useRequirementShell'

/** 按给定权限码集合构造 auth store；权限异步就绪的场景直接替换返回值即可 */
function authWith(codes: string[]) {
  return { hasPermission: (code: string) => codes.includes(code) }
}

describe('useRequirementShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRoute.mockReturnValue({ query: {} } as never)
    mocks.useRouter.mockReturnValue({ replace: vi.fn() } as never)
    mocks.useAuthStore.mockReturnValue(authWith(['requirement:view']) as never)
  })

  describe('子项权限过滤', () => {
    it('有权限时显示需求列表', () => {
      const { visibleItems } = useRequirementShell()
      expect(visibleItems.value.map((item) => item.key)).toEqual(['list'])
      expect(visibleItems.value.map((item) => item.label)).toEqual(['需求列表'])
    })

    it('无权限时侧栏空且无激活子页', () => {
      mocks.useAuthStore.mockReturnValue(authWith([]) as never)
      const { visibleItems, activeTab } = useRequirementShell()
      expect(visibleItems.value).toEqual([])
      expect(activeTab.value).toBe('')
    })
  })

  describe('activeTab 解析', () => {
    it('缺省回落到需求列表', () => {
      const { activeTab } = useRequirementShell()
      expect(activeTab.value).toBe('list')
    })

    it('请求 tab 不在菜单中时回退首个有权限子项', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'invalid' } } as never)
      const { activeTab } = useRequirementShell()
      expect(activeTab.value).toBe('list')
    })

    it('权限后到时 activeTab 随之重算，不残留空态', async () => {
      // 权限列表在真实应用里是响应式的；用 ref 承载以复现异步就绪时序
      const codes = ref<string[]>([])
      mocks.useAuthStore.mockReturnValue(
        { hasPermission: (code: string) => codes.value.includes(code) } as never,
      )
      mocks.useRoute.mockReturnValue({ query: { tab: 'list' } } as never)

      const { activeTab } = useRequirementShell()
      expect(activeTab.value).toBe('')

      codes.value = ['requirement:view']
      await nextTick()
      expect(activeTab.value).toBe('list')
    })
  })

  describe('handleMenuSelect', () => {
    it('选中当前子页时不发路由替换', () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { handleMenuSelect } = useRequirementShell()
      handleMenuSelect('list')
      expect(replace).not.toHaveBeenCalled()
    })
  })
})
