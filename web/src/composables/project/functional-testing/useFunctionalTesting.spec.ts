import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  useRoute: vi.fn(() => ({ query: {} })),
  useRouter: vi.fn(() => ({ replace: vi.fn() })),
}))

vi.mock('vue-router', () => ({
  useRoute: mocks.useRoute,
  useRouter: mocks.useRouter,
}))

import { useFunctionalTesting } from './useFunctionalTesting'

describe('useFunctionalTesting', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRoute.mockReturnValue({ query: {} } as never)
    mocks.useRouter.mockReturnValue({ replace: vi.fn() } as never)
  })

  describe('初始状态', () => {
    it('activeMenu 默认为 cases', () => {
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })

    it('activeMenu 根据 route.query.tab 初始化', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'reviews' } } as never)
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('reviews')
    })

    it('route.query.tab 不在 menuItems 中时回退到 cases', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'invalid' } } as never)
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })

    it('menuItems 包含预期项', () => {
      const { menuItems } = useFunctionalTesting()
      expect(menuItems).toHaveLength(3)
      expect(menuItems.map((m) => m.key)).toEqual(['cases', 'reviews', 'plans'])
    })
  })

  describe('handleMenuSelect', () => {
    it('选择当前菜单时不做任何操作', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('cases')
      expect(activeMenu.value).toBe('cases')
      expect(replace).not.toHaveBeenCalled()
    })

    it('切换菜单并更新路由', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('reviews')
      expect(activeMenu.value).toBe('reviews')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'reviews' } })
    })

    it('从 cases 切换时如果 testCaseRef 确认离开失败则取消切换', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => false) } as never
      await handleMenuSelect('reviews')
      expect(activeMenu.value).toBe('cases')
      expect(replace).not.toHaveBeenCalled()
    })

    it('从 cases 切换时确认离开成功则继续切换', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => true) } as never
      await handleMenuSelect('reviews')
      expect(activeMenu.value).toBe('reviews')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'reviews' } })
    })

    it('非 cases 页面切换时不需要确认', async () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'reviews' } } as never)
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('plans')
      expect(activeMenu.value).toBe('plans')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'plans' } })
    })
  })
})
