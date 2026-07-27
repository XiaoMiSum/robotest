import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/services'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/auth/LoginPage.vue'),
    meta: { public: true },
  },
  {
    path: '/join',
    name: 'Join',
    component: () => import('@/pages/workspace/JoinPage.vue'),
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
        path: 'workspace/projects/api-test',
        name: 'ApiTest',
        component: () => import('@/pages/project/ApiTestPage.vue'),
        meta: { title: '接口测试' },
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
router.beforeEach((to, _from, next) => {
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

  // Admin routes: check system role (simplified — in real app check store)
  if (to.meta.requiresAdmin) {
    // TODO: check user has system role from store
    // For now, allow access
  }

  next()
})

export default router
