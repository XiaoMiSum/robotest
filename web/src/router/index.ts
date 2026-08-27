import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/services'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/auth/LoginPage.vue'),
    meta: { public: true },
  },
  {
    path: '/init',
    name: 'Init',
    component: () => import('@/pages/auth/InitPage.vue'),
    meta: { public: true },
  },
  {
    path: '/join',
    name: 'Join',
    component: () => import('@/pages/workspace/JoinPage.vue'),
    meta: { public: true },
  },
  // === Public report share page ===
  {
    path: '/share/api-report/:id',
    name: 'ShareReport',
    component: () => import('@/pages/project/ShareReportPage.vue'),
    meta: { public: true },
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
        meta: { title: '仪表盘' },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/pages/admin/UserListPage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'users/create',
        name: 'AdminUserCreate',
        component: () => import('@/pages/admin/UserFormPage.vue'),
        meta: { title: '新建用户' },
      },
      {
        path: 'users/:id',
        name: 'AdminUserEdit',
        component: () => import('@/pages/admin/UserFormPage.vue'),
        meta: { title: '编辑用户' },
      },
      {
        path: 'workspaces',
        name: 'AdminWorkspaces',
        component: () => import('@/pages/admin/WorkspaceListPage.vue'),
        meta: { title: '工作空间管理' },
      },
      {
        path: 'workspaces/:id',
        name: 'AdminWorkspaceDetail',
        component: () => import('@/pages/admin/WorkspaceDetailPage.vue'),
        meta: { title: '工作空间详情' },
      },
      {
        path: 'roles',
        name: 'AdminRoles',
        component: () => import('@/pages/admin/RolePage.vue'),
        meta: { title: '角色管理' },
      },
      {
        path: 'ai-config',
        name: 'AdminAiConfig',
        component: () => import('@/pages/admin/AiConfigPage.vue'),
        meta: { title: 'AI 配置' },
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
        meta: { title: '我的空间' },
      },
      // Workspace context
      {
        path: 'workspace/:workspaceId',
        name: 'WorkspaceInfo',
        component: () => import('@/pages/workspace/WorkspaceInfoPage.vue'),
        meta: { title: '空间信息' },
      },
      {
        path: 'workspace/members',
        name: 'WorkspaceMembers',
        component: () => import('@/pages/workspace/MemberListPage.vue'),
        meta: { title: '成员管理' },
      },
      {
        path: 'workspace/projects',
        name: 'WorkspaceProjects',
        component: () => import('@/pages/workspace/ProjectListPage.vue'),
        meta: { title: '项目列表' },
      },
      // Project workspace routes
      {
        path: 'workspace/projects/dashboard',
        name: 'ProjectDashboard',
        component: () => import('@/pages/project/DashboardPage.vue'),
        meta: { title: '项目工作台' },
      },
      {
        path: 'workspace/projects/functional-testing',
        name: 'FunctionalTesting',
        component: () => import('@/pages/project/FunctionalTestingPage.vue'),
        meta: { title: '功能测试' },
      },
      {
        path: 'workspace/projects/reviews',
        name: 'ReviewList',
        component: () => import('@/pages/project/ReviewListPage.vue'),
        meta: { title: '测试评审' },
      },
      {
        path: 'workspace/projects/reviews/:reviewId',
        name: 'ReviewDetail',
        component: () => import('@/pages/project/ReviewDetailPage.vue'),
        meta: { title: '评审详情' },
      },
      {
        path: 'workspace/projects/plans',
        name: 'PlanList',
        component: () => import('@/pages/project/PlanListPage.vue'),
        meta: { title: '测试计划' },
      },
      {
        path: 'workspace/projects/plans/:planId',
        name: 'PlanDetail',
        component: () => import('@/pages/project/PlanDetailPage.vue'),
        meta: { title: '计划详情' },
      },
      {
        path: 'workspace/projects/api-testing',
        name: 'ApiTesting',
        component: () => import('@/pages/project/ApiTestingPage.vue'),
        meta: { title: '接口测试' },
      },
      {
        // interfaceId 为 'new' 时进入新建模式（交互设计 §3 独立路由约定）
        path: 'workspace/projects/interfaces/:interfaceId',
        name: 'InterfaceEditor',
        component: () => import('@/pages/project/InterfaceEditorPage.vue'),
        meta: { title: '接口编辑' },
      },
      {
        path: 'workspace/projects/requirements',
        name: 'RequirementPool',
        component: () => import('@/pages/project/RequirementPoolPage.vue'),
        meta: { title: '需求池' },
      },
      {
        path: 'workspace/projects/bugs',
        name: 'BugList',
        component: () => import('@/pages/project/BugListPage.vue'),
        meta: { title: '缺陷管理' },
      },
      {
        path: 'workspace/projects/bugs/create',
        name: 'BugCreate',
        component: () => import('@/pages/project/BugCreatePage.vue'),
        meta: { title: '提交缺陷' },
      },
      {
        path: 'workspace/projects/bugs/:bugId',
        name: 'BugDetail',
        component: () => import('@/pages/project/BugDetailPage.vue'),
        meta: { title: '缺陷详情' },
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

  // Protected routes: require token
  if (!token) {
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

export default router
