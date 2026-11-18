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
      results: [{ key: 'p1', action: 'adopted', success: true, createdId: null, errorCode: null, errorMsg: null }],
    })
  })

  it('用例优化按策略渲染标题、来源与导入版本区', () => {
    const wrapper = mountPanel('case_optimize', [proposal()])
    expect(wrapper.text()).toContain('用例优化审核')
    expect(wrapper.find('.proposal-review__version').exists()).toBe(false)
    expect(wrapper.findAll('.proposal-review__row')).toHaveLength(1)
  })

  it('只读任务不渲染采纳与驳回操作', () => {
    const wrapper = mountPanel('bug_triage', [proposal({ kind: 'triage_order' })])
    expect(wrapper.text()).toContain('缺陷分诊结果')
    expect(wrapper.text()).not.toContain('修改后采纳')
    expect(wrapper.text()).not.toContain('批量采纳')
    expect(wrapper.find('.proposal-review__foot').exists()).toBe(false)
  })

  it('需求导入展示版本输入与批量操作', () => {
    const wrapper = mountPanel('requirement_import', [
      proposal({ kind: 'requirement_suggestion', title: '登录需求' }),
    ])
    expect(wrapper.text()).toContain('导入结果审核')
    expect(wrapper.find('.proposal-review__version').exists()).toBe(true)
    expect(wrapper.text()).toContain('批量采纳')
  })

  it('编辑后采纳按策略字段渲染弹窗并提交结构化载荷', async () => {
    mocks.fetchAiProposal.mockResolvedValue({
      content: {
        nodeId: 'n1',
        fields: { steps: { suggested: ['点击登录', '点击验证码'] } },
        priority: { current: 'P1', suggested: 'P0' },
      },
    })
    const wrapper = mountPanel('case_optimize', [proposal()])
    await wrapper.find('.proposal-review__row').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('建议：点击登录、点击验证码')

    const buttons = wrapper.findAll('.proposal-review__row-actions button')
    const editButton = buttons.find((button) => button.text() === '修改后采纳')
    expect(editButton).toBeDefined()
    await editButton!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('前置条件')
    expect(wrapper.text()).toContain('预期结果（每行一条）')
  })
})
