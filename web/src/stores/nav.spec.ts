import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

interface MenuMeta {
  label: string
  icon: string
  order: number
  section?: string
  permission?: string
  permissionAny?: string[]
}

interface TestRecord {
  path: string
  meta: { mode?: string; title?: string; menu?: MenuMeta }
}

const h = vi.hoisted(() => ({
  currentRouteMeta: {} as Record<string, unknown>,
  records: [] as TestRecord[],
  permissions: [] as string[],
  activeWorkspace: null as { id: string; name: string; workspaceRole: string } | null,
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: {
      get value() {
        return { meta: h.currentRouteMeta }
      },
    },
    getRoutes: () => h.records,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    hasPermission: (code: string) => h.permissions.includes(code),
    get activeWorkspace() {
      return h.activeWorkspace
    },
  }),
}))

import { useNavStore } from '@/stores/nav'

function addRecord(path: string, meta: TestRecord['meta']) {
  h.records.push({ path, meta })
}

beforeEach(() => {
  h.currentRouteMeta = {}
  h.records = []
  h.permissions = []
  h.activeWorkspace = null
  setActivePinia(createPinia())
})

describe('currentMode 派生', () => {
  it('路由 meta 无 mode 时回退为 none', () => {
    const nav = useNavStore()
    expect(nav.currentMode).toBe('none')
    expect(nav.isAdminMode).toBe(false)
    expect(nav.isWorkspaceMode).toBe(false)
    expect(nav.isProjectMode).toBe(false)
  })

  it('从当前路由 meta.mode 派生对应模式', () => {
    h.currentRouteMeta = { mode: 'project', title: '缺陷管理' }
    const nav = useNavStore()
    expect(nav.currentMode).toBe('project')
    expect(nav.isProjectMode).toBe(true)
    expect(nav.isAdminMode).toBe(false)
  })
})

describe('dynamicMenuItems', () => {
  it('按当前模式过滤 + 权限过滤 + order 升序', () => {
    h.currentRouteMeta = { mode: 'project' }
    // 故意乱序注册，验证排序依赖 menu.order 而非注册顺序
    addRecord('/workspace/projects/api-testing', {
      mode: 'project',
      menu: { label: '接口测试', icon: 'Connection', order: 30, permissionAny: ['api-env:view', 'api-debug:view'] },
    })
    addRecord('/workspace/projects/bugs', {
      mode: 'project',
      menu: { label: '缺陷管理', icon: 'Warning', order: 20, permission: 'bug:view' },
    })
    addRecord('/workspace/projects/functional-testing', {
      mode: 'project',
      menu: { label: '功能测试', icon: 'Monitor', order: 10, permission: 'case:view' },
    })
    // 其他模式的菜单项不得串入
    addRecord('/admin/users', { mode: 'admin', menu: { label: '用户管理', icon: 'User', order: 10 } })
    h.permissions = ['case:view', 'api-debug:view']

    const nav = useNavStore()
    expect(nav.dynamicMenuItems.map((item) => item.label)).toEqual(['功能测试', '接口测试'])
  })

  it('permissionAny 任一命中即展示，全部未命中则隐藏', () => {
    h.currentRouteMeta = { mode: 'project' }
    addRecord('/workspace/projects/api-testing', {
      mode: 'project',
      menu: { label: '接口测试', icon: 'Connection', order: 30, permissionAny: ['api-env:view', 'api-debug:view'] },
    })

    h.permissions = ['api-env:view']
    expect(useNavStore().dynamicMenuItems).toHaveLength(1)

    // 换全新 store 实例，规避 computed 缓存（mock 非响应式）
    setActivePinia(createPinia())
    h.permissions = ['bug:view']
    expect(useNavStore().dynamicMenuItems).toHaveLength(0)
  })

  it('无 meta.menu 的页面（详情/创建等）不进菜单', () => {
    h.currentRouteMeta = { mode: 'project' }
    addRecord('/workspace/projects/bugs/create', { mode: 'project', title: '提交缺陷' })
    addRecord('/workspace/projects/bugs/:bugId', { mode: 'project', title: '缺陷详情' })
    h.permissions = ['bug:view']

    const nav = useNavStore()
    expect(nav.dynamicMenuItems).toEqual([])
  })

  it('空间信息菜单路径用活跃空间填充动态段', () => {
    h.currentRouteMeta = { mode: 'workspace' }
    addRecord('/workspace/:workspaceId', {
      mode: 'workspace',
      menu: { label: '空间信息', icon: 'InfoFilled', order: 10, permission: 'ws-info:view' },
    })
    h.permissions = ['ws-info:view']
    h.activeWorkspace = { id: 'ws-42', name: '测试空间', workspaceRole: 'admin' }

    const nav = useNavStore()
    expect(nav.dynamicMenuItems[0]?.path).toBe('/workspace/ws-42')
  })

  it('无活跃空间时动态段退化为空串（与旧实现 /workspace/ 一致）', () => {
    h.currentRouteMeta = { mode: 'workspace' }
    addRecord('/workspace/:workspaceId', {
      mode: 'workspace',
      menu: { label: '空间信息', icon: 'InfoFilled', order: 10 },
    })
    h.activeWorkspace = null

    const nav = useNavStore()
    expect(nav.dynamicMenuItems[0]?.path).toBe('/workspace/')
  })
})

describe('adminSidebarSections', () => {
  function addAdminRecords() {
    addRecord('/admin/dashboard', { mode: 'admin', menu: { label: '数据概览', icon: 'Odometer', order: 1 } })
    addRecord('/admin/users', {
      mode: 'admin',
      menu: { label: '用户管理', icon: 'User', order: 10, section: '组织与权限', permission: 'user:view' },
    })
    addRecord('/admin/roles', {
      mode: 'admin',
      menu: { label: '角色管理', icon: 'Key', order: 20, section: '组织与权限', permission: 'role:view' },
    })
    addRecord('/admin/ai-config', {
      mode: 'admin',
      menu: { label: 'AI 配置', icon: 'MagicStick', order: 40, section: '平台配置', permission: 'ai:view' },
    })
  }

  it('无 section 的置顶项独立成组，同 section 连续归组', () => {
    addAdminRecords()
    h.permissions = ['user:view', 'role:view', 'ai:view']

    const nav = useNavStore()
    expect(nav.adminSidebarSections).toHaveLength(3)
    expect(nav.adminSidebarSections[0]?.title).toBeUndefined()
    expect(nav.adminSidebarSections[0]?.items.map((item) => item.label)).toEqual(['数据概览'])
    expect(nav.adminSidebarSections[1]?.title).toBe('组织与权限')
    expect(nav.adminSidebarSections[1]?.items.map((item) => item.label)).toEqual(['用户管理', '角色管理'])
    expect(nav.adminSidebarSections[2]?.title).toBe('平台配置')
    expect(nav.adminSidebarSections[2]?.items.map((item) => item.label)).toEqual(['AI 配置'])
  })

  it('权限过滤后为空的分组整体不产生', () => {
    addAdminRecords()
    h.permissions = [] // 仅无权限要求的数据概览可见

    const nav = useNavStore()
    expect(nav.adminSidebarSections).toHaveLength(1)
    expect(nav.adminSidebarSections[0]?.title).toBeUndefined()
    expect(nav.adminSidebarSections[0]?.items.map((item) => item.label)).toEqual(['数据概览'])
  })
})
