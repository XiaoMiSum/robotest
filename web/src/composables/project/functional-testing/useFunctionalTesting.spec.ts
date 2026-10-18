import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  useRoute: vi.fn(() => ({ query: {} })),
  useRouter: vi.fn(() => ({ replace: vi.fn() })),
  permissions: [] as string[],
}))

vi.mock('vue-router', () => ({
  useRoute: mocks.useRoute,
  useRouter: mocks.useRouter,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    hasPermission: (code: string) => mocks.permissions.includes(code),
  }),
}))

import { useFunctionalTesting } from './useFunctionalTesting'

describe('useFunctionalTesting', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.permissions = []
    mocks.useRoute.mockReturnValue({ query: {} } as never)
    mocks.useRouter.mockReturnValue({ replace: vi.fn() } as never)
  })

  describe('初始状态', () => {
    it('activeMenu 默认为 cases', () => {
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })

    it('activeMenu 根据 route.query.tab 派生', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'reviews' } } as never)
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('reviews')
    })

    it('route.query.tab 不在可见子项中时回退到 cases', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'invalid' } } as never)
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })

    it('无 requirement:view 时隐藏需求列表且 ?tab=requirements 回退 cases', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'requirements' } } as never)
      const { activeMenu, visibleItems } = useFunctionalTesting()
      expect(visibleItems.value.map((m) => m.key)).toEqual(['cases', 'reviews', 'plans'])
      expect(activeMenu.value).toBe('cases')
    })

    it('持 requirement:view 时需求列表居侧栏首位并可经 ?tab=requirements 落页', () => {
      mocks.permissions = ['requirement:view']
      mocks.useRoute.mockReturnValue({ query: { tab: 'requirements' } } as never)
      const { activeMenu, visibleItems } = useFunctionalTesting()
      expect(visibleItems.value.map((m) => m.key)).toEqual(['requirements', 'cases', 'reviews', 'plans'])
      expect(activeMenu.value).toBe('requirements')
    })

    it('无 ?tab 时缺省落测试用例，即使持需求查看权限', () => {
      mocks.permissions = ['requirement:view']
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })
  })

  describe('handleMenuSelect', () => {
    it('选择当前菜单时不做任何操作', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('cases')
      expect(replace).not.toHaveBeenCalled()
    })

    it('切换菜单并更新路由', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('reviews')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'reviews' } })
    })

    it('从 cases 切换时如果 testCaseRef 确认离开失败则取消切换', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => false) } as never
      await handleMenuSelect('reviews')
      expect(replace).not.toHaveBeenCalled()
      expect(activeMenu.value).toBe('cases')
    })

    it('从 cases 切换时确认离开成功则继续切换', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => true) } as never
      await handleMenuSelect('reviews')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'reviews' } })
    })

    it('非 cases 页面切换时不需要确认', async () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'reviews' } } as never)
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('plans')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'plans' } })
    })

    it('切换到需求列表时写入 requirements tab 并保留既有查询参数', async () => {
      mocks.permissions = ['requirement:view']
      mocks.useRoute.mockReturnValue({ query: { tab: 'cases', foo: 'bar' } } as never)
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => true) } as never
      await handleMenuSelect('requirements')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'requirements', foo: 'bar' } })
    })
  })
})
