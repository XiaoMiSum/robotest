// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import AiTaskEntryButton from './AiTaskEntryButton.vue'

const mocks = vi.hoisted(() => ({
  hasPermission: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
}))

describe('components/project/ai/AiTaskEntryButton（能力页任务中心入口，交互 07 §1）', () => {
  let router: Router
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/workspace/projects/ai/tasks', component: { template: '<div />' } },
      ],
    })
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  async function mountButton(): Promise<VueWrapper> {
    wrapper = mount(AiTaskEntryButton, {
      global: { plugins: [ElementPlus, router] },
    })
    await flushPromises()
    return wrapper
  }

  it('具备 ai:task 权限时渲染任务中心按钮', async () => {
    mocks.hasPermission.mockImplementation((code: string) => code === 'ai:task')
    const root = await mountButton()
    expect(root.text()).toContain('任务中心')
  })

  it('无 ai:task 权限时整键不渲染', async () => {
    mocks.hasPermission.mockReturnValue(false)
    const root = await mountButton()
    expect(root.find('button').exists()).toBe(false)
    expect(root.text()).not.toContain('任务中心')
  })

  it('点击跳转任务中心，判权仅看 ai:task', async () => {
    mocks.hasPermission.mockImplementation((code: string) => code === 'ai:task')
    const root = await mountButton()
    await root.find('button').trigger('click')
    await flushPromises()
    expect(mocks.hasPermission).toHaveBeenCalledWith('ai:task')
    expect(router.currentRoute.value.path).toBe('/workspace/projects/ai/tasks')
  })
})
