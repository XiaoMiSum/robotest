import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/services'
import { useAuthStore } from '@/stores/auth'
import type { NavMode } from '@/types'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresAuth?: boolean
    requiresAdmin?: boolean
    title?: string
    /** 顶栏导航模式；nav store 从当前路由派生 currentMode，命令式 setMode 会造成双真相源 */
    mode?: NavMode
    /** 菜单项：缺省即页面不进任何菜单（详情/创建等页面），路由表就是菜单注册表 */
    menu?: {
      label: string
      icon: string
      /** 排序权重（小者靠前），不依赖路由注册顺序 */
      order: number
      /** 分组标题（管理端侧边栏）；缺省 = 置顶不分组 */
      section?: string
      /** 持该权限码才展示 */
      permission?: string
      /** 持任一权限码即展示 */
      permissionAny?: string[]
    }
  }
}

// 接口测试模块全部 view 权限点：菜单入口对任一模块 view 开放（与 ApiTestingPage 侧边导航口径一致）
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
]

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/auth/LoginPage.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/init',
    name: 'Init',
    component: () => import('@/pages/auth/InitPage.vue'),
    meta: { public: true, title: '初始化' },
  },
  {
    path: '/join',
    name: 'Join',
    component: () => import('@/pages/workspace/JoinPage.vue'),
    meta: { public: true, title: '加入空间' },
  },
  // === Public report share page ===
  {
    path: '/share/api-report/:id',
    name: 'ShareReport',
    component: () => import('@/pages/project/api-testing/report/ShareReportPage.vue'),
    meta: { public: true, title: '测试报告' },
  },
  // === Admin routes ===
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/pages/admin/DashboardPage.vue'),
        meta: { title: '数据概览', mode: 'admin', menu: { label: '数据概览', icon: 'Odometer', order: 1 } },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/pages/admin/UserListPage.vue'),
        meta: {
          title: '用户管理',
          mode: 'admin',
          menu: { label: '用户管理', icon: 'User', order: 10, section: '组织与权限', permission: 'user:view' },
        },
      },
      {
        path: 'users/create',
        name: 'AdminUserCreate',
        component: () => import('@/pages/admin/UserFormPage.vue'),
        meta: { title: '新建用户', mode: 'admin' },
      },
      {
        path: 'users/:id',
        name: 'AdminUserEdit',
        component: () => import('@/pages/admin/UserFormPage.vue'),
        meta: { title: '编辑用户', mode: 'admin' },
      },
      {
        path: 'workspaces',
        name: 'AdminWorkspaces',
        component: () => import('@/pages/admin/WorkspaceListPage.vue'),
        meta: {
          title: '工作空间管理',
          mode: 'admin',
          menu: { label: '空间管理', icon: 'OfficeBuilding', order: 30, section: '组织与权限', permission: 'workspace:view' },
        },
      },
      {
        path: 'workspaces/:id',
        name: 'AdminWorkspaceDetail',
        component: () => import('@/pages/admin/WorkspaceDetailPage.vue'),
        meta: { title: '工作空间详情', mode: 'admin' },
      },
      {
        path: 'roles',
        name: 'AdminRoles',
        component: () => import('@/pages/admin/RolePage.vue'),
        meta: {
          title: '角色管理',
          mode: 'admin',
          menu: { label: '角色管理', icon: 'Key', order: 20, section: '组织与权限', permission: 'role:view' },
        },
      },
      {
        path: 'ai-config',
        name: 'AdminAiConfig',
        component: () => import('@/pages/admin/AiConfigPage.vue'),
        meta: {
          title: 'AI 配置',
          mode: 'admin',
          menu: { label: 'AI 配置', icon: 'MagicStick', order: 40, section: '平台配置', permission: 'ai:view' },
        },
      },
    ],
  },
  // === Business routes ===
  {
    path: '/',
    component: () => import('@/layouts/BusinessLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/workspaces' },
      // My workspaces
      {
        path: 'workspaces',
        name: 'Workspaces',
        component: () => import('@/pages/workspace/WorkspaceListPage.vue'),
        meta: { title: '我的空间', mode: 'none' },
      },
      // Workspace context
      {
        path: 'workspace/:workspaceId',
        name: 'WorkspaceInfo',
        component: () => import('@/pages/workspace/WorkspaceInfoPage.vue'),
        meta: {
          title: '空间信息',
          mode: 'workspace',
          menu: { label: '空间信息', icon: 'InfoFilled', order: 10, permission: 'ws-info:view' },
        },
      },
      {
        path: 'workspace/members',
        name: 'WorkspaceMembers',
        component: () => import('@/pages/workspace/MemberListPage.vue'),
        meta: {
          title: '成员管理',
          mode: 'workspace',
          menu: { label: '成员管理', icon: 'UserFilled', order: 20, permission: 'ws-member:view' },
        },
      },
      {
        path: 'workspace/projects',
        name: 'WorkspaceProjects',
        component: () => import('@/pages/workspace/ProjectListPage.vue'),
        meta: {
          title: '项目列表',
          mode: 'workspace',
          menu: { label: '项目列表', icon: 'Folder', order: 30, permission: 'project:view' },
        },
      },
      // Project workspace routes
      {
        path: 'workspace/projects/dashboard',
        name: 'ProjectDashboard',
        component: () => import('@/pages/project/DashboardPage.vue'),
        meta: { title: '项目工作台', mode: 'project' },
      },
      {
        path: 'workspace/projects/functional-testing',
        name: 'FunctionalTesting',
        component: () => import('@/pages/project/functional-testing/FunctionalTestingPage.vue'),
        meta: {
          title: '功能测试',
          mode: 'project',
          menu: { label: '功能测试', icon: 'Monitor', order: 10, permission: 'case:view' },
        },
      },
      {
        path: 'workspace/projects/reviews',
        name: 'ReviewList',
        component: () => import('@/pages/project/functional-testing/ReviewListPage.vue'),
        meta: { title: '测试评审', mode: 'project' },
      },
      {
        path: 'workspace/projects/reviews/:reviewId',
        name: 'ReviewDetail',
        component: () => import('@/pages/project/functional-testing/ReviewDetailPage.vue'),
        meta: { title: '评审详情', mode: 'project' },
      },
      {
        path: 'workspace/projects/plans',
        name: 'PlanList',
        component: () => import('@/pages/project/functional-testing/PlanListPage.vue'),
        meta: { title: '测试计划', mode: 'project' },
      },
      {
        path: 'workspace/projects/plans/:planId',
        name: 'PlanDetail',
        component: () => import('@/pages/project/functional-testing/PlanDetailPage.vue'),
        meta: { title: '计划详情', mode: 'project' },
      },
      {
        path: 'workspace/projects/api-testing',
        name: 'ApiTesting',
        component: () => import('@/pages/project/api-testing/ApiTestingPage.vue'),
        meta: {
          title: '接口测试',
          mode: 'project',
          menu: { label: '接口测试', icon: 'Connection', order: 30, permissionAny: API_MODULE_VIEW_PERMISSIONS },
        },
      },
      {
        // 旧独立编辑路由：接口定义编辑器已内联于接口管理 Tab（?tab=interfaces&interfaceId= 或 &action=create），此处保持旧链接可用
        path: 'workspace/projects/interfaces/:interfaceId',
        name: 'InterfaceEditor',
        meta: { mode: 'project' },
        redirect: (to) => ({
          path: '/workspace/projects/api-testing',
          query:
            to.params.interfaceId === 'new'
              ? { tab: 'interfaces', action: 'create' }
              : { tab: 'interfaces', interfaceId: to.params.interfaceId as string },
        }),
      },
      {
        path: 'workspace/projects/requirements',
        name: 'RequirementPool',
        component: () => import('@/pages/project/functional-testing/RequirementPoolPage.vue'),
        meta: { title: '需求池', mode: 'project' },
      },
      {
        path: 'workspace/projects/bugs',
        name: 'BugList',
        component: () => import('@/pages/project/bug/BugListPage.vue'),
        meta: {
          title: '缺陷管理',
          mode: 'project',
          menu: { label: '缺陷管理', icon: 'Warning', order: 20, permission: 'bug:view' },
        },
      },
      {
        path: 'workspace/projects/bugs/create',
        name: 'BugCreate',
        component: () => import('@/pages/project/bug/BugCreatePage.vue'),
        meta: { title: '提交缺陷', mode: 'project' },
      },
      {
        path: 'workspace/projects/bugs/:bugId',
        name: 'BugDetail',
        component: () => import('@/pages/project/bug/BugDetailPage.vue'),
        meta: { title: '缺陷详情', mode: 'project' },
      },
    ],
  },
  // Catch-all redirect
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard: authentication + admin check
router.beforeEach(async (to, _from, next) => {
  const token = getAccessToken()

  // Public routes (login, invitation join page, etc.)
  if (to.meta.public) {
    if (token) {
      // Already logged in: redirect to home
      next({ path: '/' })
    } else {
      next()
    }
    return
  }

  // Protected routes: only routes that explicitly declare requiresAuth need a token
  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // Admin routes: 校验系统管理员身份；刷新后权限列表尚未从远端加载，
  // 先等待 loadPermissions 完成再判定，避免管理员刷新 /admin 被误重定向
  if (to.meta.requiresAdmin) {
    const authStore = useAuthStore()
    if (authStore.permissions.length === 0) {
      await authStore.loadPermissions()
    }
    if (!authStore.hasSystemRole && !authStore.hasSystemPermission) {
      next({ path: '/' })
      return
    }
  }

  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - RoboTest` : 'RoboTest'
})

export default router
