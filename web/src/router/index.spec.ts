// @vitest-environment jsdom
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteComponent } from 'vue-router'

const h = vi.hoisted(() => ({
  token: null as string | null,
  permissions: [] as string[],
  hasSystemRole: false,
  hasSystemPermission: false,
  loadPermissions: vi.fn(async () => {}),
}))

vi.mock('@/services', () => ({ getAccessToken: () => h.token }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get permissions() {
      return h.permissions
    },
    get hasSystemRole() {
      return h.hasSystemRole
    },
    get hasSystemPermission() {
      return h.hasSystemPermission
    },
    loadPermissions: () => h.loadPermissions(),
  }),
}))

import router from '@/router'

// 守卫测试只验证导航决策，桩掉页面组件加载器，避免拉起整棵页面依赖树
const stubComponent = { render: () => null } as unknown as RouteComponent

beforeAll(() => {
  for (const record of router.getRoutes()) {
    const components = record.components
    if (components?.default) {
      components.default = stubComponent
    }
  }
})

beforeEach(async () => {
  vi.clearAllMocks()
  h.token = null
  h.permissions = []
  h.hasSystemRole = false
  h.hasSystemPermission = false
  // 归位到另一张公开页，避免"push('/login') 与当前路由相同"导致导航去重、守卫不触发
  await router.replace('/init')
})

describe('路由 meta 契约', () => {
  it('非公开的叶子组件路由必须声明 meta.mode（顶栏模式派生依据）', () => {
    const missing = router
      .getRoutes()
      .filter((record) => record.children.length === 0 && !record.meta.public)
      .filter((record) => Object.keys(record.components ?? {}).length > 0)
      .filter((record) => !record.meta.mode)
      .map((record) => record.path)
    expect(missing).toEqual([])
  })

  it('带 meta.menu 的路由必须声明 mode 且 label/icon/order 完整、路径可解析为具名路由', () => {
    const menuRoutes = router.getRoutes().filter((record) => record.meta.menu)
    // 5 管理端 + 3 空间 + 3 项目 = 11 项菜单注册
    expect(menuRoutes.length).toBe(11)
    for (const record of menuRoutes) {
      const menu = record.meta.menu
      expect(record.meta.mode, record.path).toBeTruthy()
      expect(menu?.label, record.path).toBeTruthy()
      expect(menu?.icon, record.path).toBeTruthy()
      expect(typeof menu?.order, record.path).toBe('number')
      // 动态段填充后必须命中具名路由；悬空路径会落进无名兜底路由（历史上的 /admin/ai-agents 类漂移即由此拦截）
      const testablePath = record.path.replace(/:[^/]+/g, 'test')
      expect(router.resolve(testablePath).name, record.path).not.toBeNull()
    }
  })

  it('meta.title 接入浏览器标签页标题', async () => {
    h.token = 'token'
    await router.push({ name: 'Workspaces' })
    expect(document.title).toBe('我的空间 - RoboTest')
  })
})

describe('导航守卫', () => {
  it('未登录访问受保护路由 → 跳登录页并带回跳地址', async () => {
    await router.push('/workspaces')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/workspaces')
  })

  it('已登录访问公开页 → 重定向首页', async () => {
    h.token = 'token'
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/workspaces')
  })

  it('无管理权限访问 /admin → 先等待权限加载再重定向首页', async () => {
    h.token = 'token'
    await router.push('/admin/dashboard')
    expect(h.loadPermissions).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/workspaces')
  })

  it('持有系统权限可进入管理端，标题同步为数据概览', async () => {
    h.token = 'token'
    h.permissions = ['user:view']
    h.hasSystemPermission = true
    await router.push('/admin/dashboard')
    expect(router.currentRoute.value.path).toBe('/admin/dashboard')
    expect(document.title).toBe('数据概览 - RoboTest')
  })

  it('未知路径 → 兜底重定向首页', async () => {
    h.token = 'token'
    await router.push('/some/unknown/path')
    expect(router.currentRoute.value.path).toBe('/workspaces')
    expect(document.title).toBe('我的空间 - RoboTest')
  })
})
