// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import RequirementImportDialog from '@/components/project/requirement/RequirementImportDialog.vue'
import RequirementImportHistoryPage from './RequirementImportHistoryPage.vue'

const mocks = vi.hoisted(() => ({
  useRequirementImportHistory: vi.fn(),
  useAssistantEntrance: vi.fn(),
}))

vi.mock('@/composables/project/requirement/useRequirementImportHistory', () => ({
  useRequirementImportHistory: mocks.useRequirementImportHistory,
}))
vi.mock('@/composables/ai/useAssistantEntrance', () => ({
  useAssistantEntrance: mocks.useAssistantEntrance,
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
    showRefreshError: ref(false),
    load: vi.fn(),
    changePage: vi.fn(),
    ...overrides,
  }
}

async function render() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/workspace/projects/functional-testing', component: { template: '<div />' } },
      { path: '/workspace/projects/requirements/import-review/:taskId', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const push = vi.spyOn(router, 'push')
  const wrapper = mount(RequirementImportHistoryPage, {
    global: { plugins: [ElementPlus, router], stubs: { ListPagination: true, Upload: true, UploadFilled: true } },
  })
  await flushPromises()
  return { wrapper, push }
}

describe('RequirementImportHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRequirementImportHistory.mockReturnValue(makeState())
    mocks.useAssistantEntrance.mockReturnValue({ visible: ref(true), refresh: vi.fn() })
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

  it('首屏加载失败展示危险按钮重试', async () => {
    const state = makeState({ showError: ref(true), loadError: ref('网络中断') })
    mocks.useRequirementImportHistory.mockReturnValue(state)
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('网络中断')
    const retry = wrapper.findAll('button').find((item) => item.text() === '重试')
    expect(retry!.classes()).toContain('el-button--danger')
    expect(retry!.classes()).toContain('is-plain')
    await retry!.trigger('click')
    expect(state.load).toHaveBeenCalled()
  })

  it('刷新失败保留表格并使用统一刷新反馈', async () => {
    const state = makeState({ showRefreshError: ref(true), loadError: ref('刷新失败') })
    mocks.useRequirementImportHistory.mockReturnValue(state)
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('刷新失败，当前显示上次加载的数据')
    expect(wrapper.find('.el-table').exists()).toBe(true)
    const retry = wrapper.findAll('button').find((item) => item.text() === '刷新')
    await retry!.trigger('click')
    expect(state.load).toHaveBeenCalled()
  })

  it('无面包屑，页头两个按钮均为默认按钮', async () => {
    const { wrapper } = await render()
    expect(wrapper.find('.import-history__breadcrumb').exists()).toBe(false)
    const buttons = wrapper.findAll('.import-history__actions button')
    expect(buttons.map((item) => item.text())).toEqual(['导入需求', '返回需求列表'])
    for (const button of buttons) {
      expect(button.classes()).not.toContain('el-button--primary')
    }
  })

  it('页头导入需求按钮直接打开导入对话框', async () => {
    const { wrapper } = await render()
    const button = wrapper.findAll('.import-history__actions button').find((item) => item.text() === '导入需求')
    await button!.trigger('click')
    expect(wrapper.findComponent(RequirementImportDialog).props('modelValue')).toBe(true)
  })

  it('空态导入需求按钮直接打开导入对话框', async () => {
    mocks.useRequirementImportHistory.mockReturnValue(makeState({ rows: ref([]), total: ref(0) }))
    const { wrapper } = await render()
    const button = wrapper.findAll('button').find((item) => item.text() === '导入需求')
    await button!.trigger('click')
    expect(wrapper.findComponent(RequirementImportDialog).props('modelValue')).toBe(true)
  })

  it('返回需求列表仍回到功能测试需求列表页签', async () => {
    const { wrapper, push } = await render()
    const button = wrapper.findAll('.import-history__actions button').find((item) => item.text() === '返回需求列表')
    await button!.trigger('click')
    await flushPromises()
    expect(push).toHaveBeenCalledWith('/workspace/projects/functional-testing?tab=requirements')
  })

  it('AI 不可用时隐藏导入需求按钮', async () => {
    mocks.useAssistantEntrance.mockReturnValue({ visible: ref(false), refresh: vi.fn() })
    const { wrapper } = await render()
    expect(wrapper.text()).not.toContain('导入需求')
  })
})
