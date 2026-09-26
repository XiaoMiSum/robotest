// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, type Pinia } from 'pinia'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import { Lock as LockIcon, User as UserIcon } from '@element-plus/icons-vue'
import LoginPage from './LoginPage.vue'
import { useAuthStore } from '@/stores/auth'
import type { LoginUser } from '@/types'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  fetchPermissions: vi.fn(),
  checkInitStatus: vi.fn(),
  messageWarning: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: {
      warning: mocks.messageWarning,
      success: mocks.messageSuccess,
      error: mocks.messageError,
    },
  }
})

// 登录走默认导出 api.post；命名导出（token/上下文持久化）保持真实实现，
// 以验证 setLogin 后的令牌落盘行为
vi.mock('@/services', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services')>()
  return { ...actual, default: { ...actual.default, post: mocks.post } }
})

vi.mock('@/services/init', () => ({ checkInitStatus: mocks.checkInitStatus }))
vi.mock('@/services/auth', () => ({
  fetchPermissions: mocks.fetchPermissions,
  changePassword: vi.fn(),
}))

const emptyView = { render: () => null }

function createTestRouter(): Router {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', name: 'Login', component: emptyView },
      { path: '/init', name: 'Init', component: emptyView },
      { path: '/workspaces', name: 'Workspaces', component: emptyView },
      { path: '/admin', name: 'AdminDashboard', component: emptyView },
    ],
  })
}

function makeUser(overrides: Partial<LoginUser> = {}): LoginUser {
  return {
    id: 'user-1',
    username: 'admin',
    email: 'admin@example.com',
    status: 'active',
    roles: [],
    permissions: [],
    hasWorkspace: true,
    ...overrides,
  }
}

interface PageHarness {
  wrapper: VueWrapper
  router: Router
  pinia: Pinia
}

async function mountPage(): Promise<PageHarness> {
  const pinia = createPinia()
  const router = createTestRouter()
  await router.push('/login')
  await router.isReady()
  const wrapper = mount(LoginPage, {
    global: {
      plugins: [ElementPlus, pinia, router],
      components: { User: UserIcon, Lock: LockIcon },
    },
  })
  // onMounted 的初始化检查是异步的，先等它稳定再进入断言
  await flushPromises()
  return { wrapper, router, pinia }
}

async function fillCredentials(wrapper: VueWrapper, identifier: string, password: string) {
  await wrapper.find('input[placeholder="用户名 / 邮箱"]').setValue(identifier)
  await wrapper.find('input[placeholder="请输入密码"]').setValue(password)
}

describe('LoginPage 登录流程', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    mocks.checkInitStatus.mockResolvedValue({ initialized: true })
    mocks.fetchPermissions.mockResolvedValue([])
  })

  it('空表单提交给出提示且不发起登录请求', async () => {
    const { wrapper } = await mountPage()

    await wrapper.find('.login-box__btn').trigger('click')

    expect(mocks.messageWarning).toHaveBeenCalledWith('请输入用户名/邮箱和密码')
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('凭证正确时持久化令牌、拉取权限并进入工作空间列表', async () => {
    mocks.post.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: makeUser(),
    })
    const { wrapper, router, pinia } = await mountPage()

    await fillCredentials(wrapper, 'admin', 'secret#123')
    await wrapper.find('.login-box__btn').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/auth/login', {
      identifier: 'admin',
      password: 'secret#123',
    })
    expect(mocks.fetchPermissions).toHaveBeenCalled()
    expect(mocks.messageSuccess).toHaveBeenCalledWith('登录成功')
    expect(router.currentRoute.value.name).toBe('Workspaces')
    expect(sessionStorage.getItem('robotest_access_token')).toBe('access-token')
    const authStore = useAuthStore(pinia)
    expect(authStore.user?.username).toBe('admin')
  })

  it('无工作空间但持系统权限时进入管理端', async () => {
    mocks.fetchPermissions.mockResolvedValue(['user:manage'])
    mocks.post.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: makeUser({ hasWorkspace: false, permissions: ['user:manage'] }),
    })
    const { wrapper, router } = await mountPage()

    await fillCredentials(wrapper, 'root', 'secret#123')
    await wrapper.find('.login-box__btn').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('AdminDashboard')
  })

  it('登录接口失败时提示后端消息并留在登录页', async () => {
    mocks.post.mockRejectedValue(new Error('账号或密码错误'))
    const { wrapper, router } = await mountPage()

    await fillCredentials(wrapper, 'admin', 'wrong-password')
    await wrapper.find('.login-box__btn').trigger('click')
    await flushPromises()

    expect(mocks.messageError).toHaveBeenCalledWith('账号或密码错误')
    expect(mocks.messageSuccess).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('系统未初始化时重定向到初始化页', async () => {
    mocks.checkInitStatus.mockResolvedValue({ initialized: false })

    const { router } = await mountPage()

    expect(router.currentRoute.value.name).toBe('Init')
  })
})
