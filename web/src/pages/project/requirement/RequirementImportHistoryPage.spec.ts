// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import RequirementImportHistoryPage from './RequirementImportHistoryPage.vue'

const mocks = vi.hoisted(() => ({ useRequirementImportHistory: vi.fn() }))

vi.mock('@/composables/project/requirement/useRequirementImportHistory', () => ({
  useRequirementImportHistory: mocks.useRequirementImportHistory,
}))

function makeState(overrides: Record<string, unknown> = {}) {
  return {
    loading: ref(false),
    hasLoaded: ref(true),
    loadError: ref(''),
    rows: ref([
      {
        id: 's1',
        aiTaskId: 't1',
        sourceFileName: 'prd.md',
        createdAt: '2026-11-26T06:20:00Z',
        taskStatus: 'proposals_ready',
        taskStatusMeta: { label: '待采纳', tagType: 'warning' },
        submitterName: '张三',
        proposalCount: 3,
        adoptedCount: 1,
      },
    ]),
    total: ref(1),
    pageNo: ref(1),
    pageSize: ref(20),
    showSkeleton: ref(false),
    showError: ref(false),
    load: vi.fn(),
    changePage: vi.fn(),
    ...overrides,
  }
}

async function render() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  await router.push('/')
  await router.isReady()
  const push = vi.spyOn(router, 'push')
  const wrapper = mount(RequirementImportHistoryPage, {
    global: { plugins: [ElementPlus, router], stubs: { ListPagination: true } },
  })
  await flushPromises()
  return { wrapper, push }
}

describe('RequirementImportHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRequirementImportHistory.mockReturnValue(makeState())
  })

  it('查看明细按任务 ID 下钻到现有导入结果审核页', async () => {
    const { wrapper, push } = await render()
    const button = wrapper.findAll('button').find((item) => item.text() === '查看明细')
    expect(button).toBeDefined()
    await button!.trigger('click')
    await flushPromises()
    expect(push).toHaveBeenCalledWith('/workspace/projects/requirements/import-review/t1')
  })

  it('无任务 ID 的历史行禁止下钻', async () => {
    const state = makeState()
    mocks.useRequirementImportHistory.mockReturnValue({
      ...state,
      rows: ref([{ ...state.rows.value[0], aiTaskId: null }]),
    })
    const { wrapper, push } = await render()
    const button = wrapper.findAll('button').find((item) => item.text() === '查看明细')
    expect((button!.element as HTMLButtonElement).disabled).toBe(true)
    await button!.trigger('click')
    await flushPromises()
    expect(push).not.toHaveBeenCalled()
  })

  it('空列表展示空状态而非空表格', async () => {
    mocks.useRequirementImportHistory.mockReturnValue(makeState({ rows: ref([]), total: ref(0) }))
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('暂无导入记录')
    expect(wrapper.find('.el-table').exists()).toBe(false)
  })

  it('加载失败展示统一错误反馈并提供重试', async () => {
    const state = makeState({ showError: ref(true), loadError: ref('网络中断') })
    mocks.useRequirementImportHistory.mockReturnValue(state)
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('网络中断')
    const retry = wrapper.findAll('button').find((item) => item.text() === '刷新')
    await retry!.trigger('click')
    expect(state.load).toHaveBeenCalled()
  })
})
