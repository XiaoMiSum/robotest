// @vitest-environment jsdom
// 执行级回归：AiUsageGroup 曾因 immediate watch 先于 drill 声明执行（TDZ）导致 setup 中断，
// ?raw 源码断言拦不住初始化顺序错误，必须真正 mount
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, type Pinia } from 'pinia'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import AiUsageGroup from './AiUsageGroup.vue'

const mocks = vi.hoisted(() => ({
  fetchAiUsageStatistics: vi.fn(),
  fetchAiUsageTasks: vi.fn(),
}))

// jsdom 无 canvas 2D context，真实 echarts init 会抛错；测试只关心 setup/查询逻辑，不关心绘制
vi.mock('echarts/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('echarts/core')>()
  return {
    ...actual,
    init: vi.fn(() => ({
      on: vi.fn(),
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    })),
  }
})

vi.mock('@/services/aiAdmin', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/aiAdmin')>()
  return {
    ...actual,
    fetchAiUsageStatistics: mocks.fetchAiUsageStatistics,
    fetchAiUsageTasks: mocks.fetchAiUsageTasks,
  }
})

const emptyView = { render: () => null }

let pinia: Pinia
let router: Router

function mountGroup(): VueWrapper {
  return mount(AiUsageGroup, {
    global: {
      plugins: [ElementPlus, pinia, router],
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  pinia = createPinia()
  router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: emptyView },
      { path: '/workspace/projects/ai/tasks/:taskId', name: 'AiTaskDetail', component: emptyView },
    ],
  })
  // 统计接口失败走页面捕获 + 重试（UI-PAGE-11），同时保证 statistics 为空、不触发 echarts init
  mocks.fetchAiUsageStatistics.mockRejectedValue(new Error('网络错误'))
})

describe('AI 配置中心 · 用量分析 setup 执行（TDZ 回归）', () => {
  it('immediate watch 不再命中 drill 的 TDZ，查询加载与错误重试可用', async () => {
    const wrapper = mountGroup()
    await flushPromises()

    // 若 watch 回调先于 drill 声明执行，mount 即抛 ReferenceError，此处不会到达
    expect(mocks.fetchAiUsageStatistics).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('网络错误')
    expect(wrapper.text()).toContain('重试')

    await wrapper.findAll('button').find((b) => b.text() === '重试')?.trigger('click')
    await flushPromises()
    expect(mocks.fetchAiUsageStatistics).toHaveBeenCalledTimes(2)
  })

  it('切换分组维度触发新一轮查询（watch 拆分后 query 响应保持）', async () => {
    mocks.fetchAiUsageStatistics.mockResolvedValue({
      summary: {
        totalCalls: 0,
        successRate: 1,
        totalTokens: 0,
        avgLatencyMs: 0,
        totalCost: 0,
      },
      series: [],
    })
    const wrapper = mountGroup()
    await flushPromises()
    expect(mocks.fetchAiUsageStatistics.mock.calls[0][0].groupBy).toBe('day')

    const modelButton = wrapper
      .findAll('.el-radio-button')
      .find((b) => b.text().includes('按模型'))
    expect(modelButton).toBeTruthy()
    await modelButton!.find('input').trigger('change')
    await flushPromises()
    expect(mocks.fetchAiUsageStatistics.mock.calls[1][0].groupBy).toBe('model')
  })

  it('空区间展示范围调整引导（交互 2.6），工具栏正常渲染', async () => {
    mocks.fetchAiUsageStatistics.mockResolvedValue({
      summary: {
        totalCalls: 0,
        successRate: 1,
        totalTokens: 0,
        avgLatencyMs: 0,
        totalCost: 0,
      },
      series: [],
    })
    const wrapper = mountGroup()
    await flushPromises()
    expect(wrapper.text()).toContain('所选范围暂无用量数据，可调整时间范围后重试')
    expect(wrapper.text()).toContain('调用次数')
    expect(wrapper.text()).toContain('按天')
  })
})
