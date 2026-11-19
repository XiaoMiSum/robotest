// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskProposalSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiProposal: vi.fn(),
  confirmAiProposals: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiProposal: mocks.fetchAiProposal,
  confirmAiProposals: mocks.confirmAiProposals,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
  }
})

import AiProposalReviewPanel from './AiProposalReviewPanel.vue'

function proposal(overrides: Partial<AiTaskProposalSummary> = {}): AiTaskProposalSummary {
  return {
    key: 'p1',
    kind: 'case_optimize_suggestion',
    title: '登录用例优化',
    parentKey: null,
    confirmStatus: 'pending',
    note: null,
    ...overrides,
  }
}

function mountPanel(taskType: string, proposals: AiTaskProposalSummary[]) {
  return mount(AiProposalReviewPanel, {
    props: { taskId: 't1', taskType, proposals },
    global: { plugins: [ElementPlus], stubs: { InfoFilled: true } },
  })
}

describe('AiProposalReviewPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocks.fetchProjectModuleTree.mockResolvedValue([])
    mocks.fetchAiProposal.mockResolvedValue({ content: {} })
    mocks.confirmAiProposals.mockResolvedValue({
      results: [
        {
          key: 'p1',
          action: 'adopted',
          success: true,
          createdId: null,
          errorCode: null,
          errorMsg: null,
        },
      ],
    })
  })

  it('用例优化按卡片只展示当前第一条待处理产物', async () => {
    const wrapper = mountPanel('case_optimize', [
      proposal(),
      proposal({ key: 'p2', title: '第二项' }),
      proposal({ key: 'p3', title: '第三项' }),
      proposal({ key: 'p4', title: '第四项' }),
      proposal({ key: 'p5', title: '第五项', confirmStatus: 'adopted' }),
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('用例优化结果')
    expect(wrapper.findAll('.proposal-review__item')).toHaveLength(1)
    expect(wrapper.text()).toContain('登录用例优化')
    expect(wrapper.text()).not.toContain('第二项')
    expect(wrapper.text()).not.toContain('第三项')
    expect(wrapper.text()).not.toContain('第四项')
    expect(wrapper.text()).not.toContain('第五项')
  })

  it('当前产物处理后按最新清单自动切换下一条', async () => {
    const wrapper = mountPanel('case_optimize', [
      proposal(),
      proposal({ key: 'p2', title: '第二项' }),
      proposal({ key: 'p3', title: '第三项' }),
    ])
    await flushPromises()

    await wrapper.setProps({
      proposals: [
        proposal({ confirmStatus: 'adopted' }),
        proposal({ key: 'p2', title: '第二项' }),
        proposal({ key: 'p3', title: '第三项' }),
      ],
    })
    await flushPromises()

    expect(wrapper.findAll('.proposal-review__item')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('登录用例优化')
    expect(wrapper.text()).toContain('第二项')
    expect(wrapper.text()).not.toContain('第三项')
  })

  it('不渲染左侧列表、勾选、全选与批量操作计数', () => {
    const wrapper = mountPanel('requirement_import', [
      proposal({ kind: 'requirement_suggestion', title: '登录需求' }),
    ])

    expect(wrapper.text()).toContain('导入结果审核')
    expect(wrapper.find('.proposal-review__version').exists()).toBe(true)
    expect(wrapper.find('.proposal-review__list').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('全选')
    expect(wrapper.text()).not.toContain('批量采纳')
    expect(wrapper.text()).not.toContain('已选')
    expect(wrapper.find('.proposal-review__foot').exists()).toBe(false)
  })

  it('只读任务卡片不渲染采纳与驳回操作', async () => {
    for (const [taskType, kind, title] of [
      ['bug_triage', 'triage_order', '缺陷分诊结果'],
      ['case_optimize', 'case_optimize_suggestion', '用例优化结果'],
    ]) {
      const wrapper = mountPanel(taskType, [proposal({ kind })])
      await flushPromises()

      expect(wrapper.text()).toContain(title)
      expect(wrapper.findAll('.proposal-review__item')).toHaveLength(1)
      expect(wrapper.text()).not.toContain('修改后采纳')
      expect(wrapper.text()).not.toContain('驳回')
    }
  })

  it('卡片内展示既有与建议三列对比', async () => {
    mocks.fetchAiProposal.mockResolvedValue({
      content: {
        nodeId: 'n1',
        operations: [
          {
            op: 'update', nodeId: 'n1', field: 'priority',
            existing: ['P1'], suggested: ['P0'], path: '登录用例', reason: '上游需求为高',
          },
        ],
      },
    })
    const wrapper = mountPanel('case_optimize', [proposal()])
    await flushPromises()

    expect(wrapper.find('.compare-view').exists()).toBe(true)
    expect(wrapper.text()).toContain('AI 建议')
    expect(wrapper.text()).toContain('P0')
  })

  it('卡片采纳按钮提交确认并触发刷新', async () => {
    const wrapper = mountPanel('requirement_import', [
      proposal({ kind: 'requirement_suggestion' }),
    ])
    await flushPromises()

    const adopt = wrapper
      .findAll('.proposal-review__card-actions button')
      .find((button) => button.text() === '采纳')
    expect(adopt).toBeDefined()
    await adopt!.trigger('click')
    await flushPromises()

    expect(mocks.confirmAiProposals).toHaveBeenCalledWith(
      't1',
      expect.objectContaining({ items: [{ key: 'p1', action: 'adopted' }] }),
    )
  })

  it('全部驳回展示空态', async () => {
    const wrapper = mountPanel('case_optimize', [
      proposal({ confirmStatus: 'rejected' }),
      proposal({ key: 'p2', confirmStatus: 'rejected' }),
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('已全部驳回')
    expect(wrapper.findAll('.proposal-review__item')).toHaveLength(0)
  })
})
