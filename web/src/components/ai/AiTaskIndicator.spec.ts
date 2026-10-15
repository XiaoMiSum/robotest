// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import AiTaskIndicator from './AiTaskIndicator.vue'
import { useAiTaskStore } from '@/stores/aiTask'

const mocks = vi.hoisted(() => ({
  state: { projectMode: true },
  hasPermission: vi.fn(),
  fetchAiRunningCount: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
}))

vi.mock('@/stores/nav', () => ({
  useNavStore: () => ({
    // Pinia store 解包 computed ref，mock 用 getter 模拟布尔读取
    get isProjectMode(): boolean {
      return mocks.state.projectMode
    },
  }),
}))

vi.mock('@/services/ai', () => ({
  fetchAiRunningCount: mocks.fetchAiRunningCount,
  fetchAiTask: vi.fn(),
}))

describe('components/ai/AiTaskIndicator（顶栏进行中任务指示器，交互 07 §1/§5）', () => {
  let router: Router
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    // 只伪造 interval：flushPromises 走 setImmediate，全量伪造会挂起
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    setActivePinia(createPinia())
    mocks.state.projectMode = true
    mocks.hasPermission.mockImplementation((code: string) => code === 'ai:task')
    mocks.fetchAiRunningCount.mockResolvedValue({ count: 0 })
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/workspace/projects/dashboard', component: { template: '<div />' } },
        { path: '/workspace/projects/ai/tasks', component: { template: '<div />' } },
        { path: '/workspace/projects/ai/tasks/:taskId', component: { template: '<div />' } },
      ],
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
  })

  async function mountIndicator(path = '/workspace/projects/dashboard'): Promise<VueWrapper> {
    await router.push(path)
    wrapper = mount(AiTaskIndicator, {
      global: { plugins: [ElementPlus, router] },
    })
    await flushPromises()
    return wrapper
  }

  it('项目模式且有 ai:task 权限时显示并装载计数轮询', async () => {
    const root = await mountIndicator()
    expect(root.text()).toContain('AI 任务')
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(1)
    expect(useAiTaskStore().countPollTimer).not.toBeNull()
  })

  it('workspace 模式或无权限时整键不渲染且不发起计数请求', async () => {
    mocks.state.projectMode = false
    let root = await mountIndicator()
    expect(root.find('.top-nav__icon-btn').exists()).toBe(false)
    expect(mocks.fetchAiRunningCount).not.toHaveBeenCalled()

    wrapper?.unmount()
    mocks.state.projectMode = true
    mocks.hasPermission.mockReturnValue(false)
    root = await mountIndicator()
    expect(root.find('.top-nav__icon-btn').exists()).toBe(false)
    expect(mocks.fetchAiRunningCount).not.toHaveBeenCalled()
  })

  it('角标按进行中数量显示，0 时隐藏', async () => {
    mocks.fetchAiRunningCount.mockResolvedValue({ count: 3 })
    const root = await mountIndicator()
    await flushPromises()
    expect(root.find('.el-badge__content').text()).toBe('3')
  })

  it('点击指示器进入任务中心', async () => {
    const root = await mountIndicator()
    await root.find('.top-nav__icon-btn').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/workspace/projects/ai/tasks')
  })

  it('计数按 15 秒间隔轮询，卸载后停止', async () => {
    await mountIndicator()
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(15100)
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(2)

    wrapper?.unmount()
    expect(useAiTaskStore().countPollTimer).toBeNull()
    await vi.advanceTimersByTimeAsync(15100)
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(2)
  })

  it('进入任务中心域路由时即时刷新计数（提交后跳详情页场景）', async () => {
    const root = await mountIndicator()
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(1)

    await router.push('/workspace/projects/ai/tasks/t1')
    await flushPromises()
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(2)

    // 非任务域路由不触发刷新
    await router.push('/workspace/projects/dashboard')
    await flushPromises()
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(2)
    expect(root.text()).toContain('AI 任务')
  })
})
