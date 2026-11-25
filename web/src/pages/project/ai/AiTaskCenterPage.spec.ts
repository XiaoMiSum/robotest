// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive, ref } from 'vue'
import AiTaskCenterPage from './AiTaskCenterPage.vue'
import RowActions from '@/components/common/RowActions.vue'
import type { AiTaskRow } from '@/composables/project/ai/useAiTaskCenter'
import { useAiTaskCenter } from '@/composables/project/ai/useAiTaskCenter'

const mocks = vi.hoisted(() => ({ push: vi.fn(), abort: vi.fn(), reExecute: vi.fn() }))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/composables/project/ai/useAiTaskCenter', () => ({ useAiTaskCenter: vi.fn() }))

function makeRow(overrides: Partial<AiTaskRow> = {}): AiTaskRow {
  return {
    taskId: 't1',
    name: '支付回调用例优化',
    typeMeta: { label: '用例优化', domain: '辅助' },
    statusMeta: { label: '执行中', tagType: 'primary' },
    status: 'running',
    summary: { text: '3/5 步', stepPercent: 60 },
    submitterText: '张明',
    createdAt: '2026-11-24T08:00:00Z',
    executionId: 'e1',
    canAbort: true,
    canReExecute: false,
    terminateText: '',
    ...overrides,
  }
}

function render(rows: AiTaskRow[]) {
  vi.mocked(useAiTaskCenter).mockReturnValue({
    loading: ref(false),
    hasLoaded: ref(true),
    loadError: ref(''),
    rows: ref(rows),
    total: ref(rows.length),
    pageNo: ref(1),
    pageSize: ref(20),
    pageSizes: [20, 50, 100],
    filters: reactive({ type: '', status: '' }),
    aiAvailable: ref(true),
    aiStatusLoaded: ref(true),
    retry: vi.fn(),
    search: vi.fn(),
    resetFilters: vi.fn(),
    changePage: vi.fn(),
    changePageSize: vi.fn(),
    handleAbort: mocks.abort,
    handleReExecute: mocks.reExecute,
  } as unknown as ReturnType<typeof useAiTaskCenter>)

  return mount(AiTaskCenterPage, {
    global: {
      stubs: {
        Refresh: true,
        Search: true,
        ListPagination: true,
        'router-link': true,
      },
    },
  })
}

describe('AiTaskCenterPage 行操作', () => {
  it('无刷新按钮，表格固定高度由表体内部滚动', async () => {
    const wrapper = render([makeRow()])
    await flushPromises()

    expect(wrapper.find('.page-head__actions').exists()).toBe(false)
    expect(wrapper.find('.page-head__desc').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('刷新')
    expect(wrapper.find('.list-workspace__toolbar').exists()).toBe(true)
    expect(wrapper.find('.ai-task-center__count').text()).toBe('共 1 条')
    expect(wrapper.find('.ai-task-center__table').exists()).toBe(true)
    expect(wrapper.find('.el-table').attributes('style')).toContain('height: 100%')
    wrapper.unmount()
  })

  it('操作列全部收敛为 link 小按钮，并按状态显隐动作', async () => {
    const wrapper = render([
      makeRow(),
      makeRow({ taskId: 't2', status: 'aborted', canAbort: false, canReExecute: true }),
    ])
    await flushPromises()

    const actions = wrapper.findAllComponents(RowActions)
    expect(actions).toHaveLength(2)
    for (const action of actions) {
      const buttons = action.findAll('.el-button')
      expect(buttons.length).toBeGreaterThan(0)
      for (const button of buttons) {
        expect(button.classes()).toContain('el-button--small')
        expect(button.classes()).toContain('is-link')
      }
    }
    expect(actions[0]?.props('actions')).toEqual([
      { key: 'detail', label: '详情', form: 'PLsm' },
      { key: 'abort', label: '终止', form: 'DGsm', visible: true },
      { key: 'reExecute', label: '重新执行', form: 'PLsm', visible: false },
    ])
    expect(actions[1]?.props('actions')).toEqual([
      { key: 'detail', label: '详情', form: 'PLsm' },
      { key: 'abort', label: '终止', form: 'DGsm', visible: false },
      { key: 'reExecute', label: '重新执行', form: 'PLsm', visible: true },
    ])
    wrapper.unmount()
  })

  it('详情跳详情路由，终止与再次执行转发对应 handler', async () => {
    const wrapper = render([makeRow()])
    await flushPromises()
    const actions = wrapper.getComponent(RowActions)

    actions.vm.$emit('action', 'detail')
    expect(mocks.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks/t1')

    actions.vm.$emit('action', 'abort')
    expect(mocks.abort).toHaveBeenCalledWith(expect.objectContaining({ taskId: 't1' }))

    actions.vm.$emit('action', 'reExecute')
    expect(mocks.reExecute).toHaveBeenCalledWith(expect.objectContaining({ taskId: 't1' }))
    wrapper.unmount()
  })
})
