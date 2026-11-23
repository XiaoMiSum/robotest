// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import type { AiTaskProposalSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiProposal: vi.fn(),
  confirmAiProposals: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiProposal: mocks.fetchAiProposal,
  confirmAiProposals: mocks.confirmAiProposals,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
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
    kind: 'requirement_suggestion',
    title: '登录验证码',
    parentKey: null,
    confirmStatus: 'adopted',
    note: null,
    ...overrides,
  }
}

function mountPanel(
  proposals: AiTaskProposalSummary[],
  taskType = 'requirement_import',
): ReturnType<typeof mount> {
  return mount(AiProposalReviewPanel, {
    props: {
      taskId: 't1',
      taskType,
      proposals,
      readonly: true,
    },
    global: { plugins: [ElementPlus] },
  })
}

describe('AiProposalReviewPanel readonly', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchProjectModuleTree.mockResolvedValue([])
    mocks.fetchAiProposal.mockResolvedValue({
      content: { description: '需求描述', priority: 'high', sourceRef: 'PRD §2' },
    })
  })

  it('只读审核面板展示当前提案、状态与导航，无审核操作按钮', async () => {
    const wrapper = mountPanel([
      proposal({ confirmStatus: 'pending' }),
      proposal({ key: 'p2', title: '短信登录', confirmStatus: 'rejected', note: '本期不支持' }),
      proposal({ key: 'p3', title: '找回密码', confirmStatus: 'adopted' }),
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('导入结果审核')
    expect(wrapper.text()).toContain('登录验证码')
    expect(wrapper.text()).toContain('第 1 / 3 条')
    expect(wrapper.findAll('.proposal-review__item')).toHaveLength(1)
    expect(wrapper.find('.proposal-review__nav').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('修改后采纳')
    expect(wrapper.text()).not.toContain('驳回（可附反馈）')
    expect(wrapper.text()).not.toContain('批量采纳')
    expect(mocks.confirmAiProposals).not.toHaveBeenCalled()
  })

  it('切换上一个/下一个按需加载明细，已加载项不重复请求', async () => {
    mocks.fetchAiProposal.mockImplementation(async (_taskId: string, key: string) => ({
      content: { description: `详情-${key}`, priority: 'high', sourceRef: 'PRD §2' },
    }))
    const wrapper = mountPanel([
      proposal({ key: 'p1', title: '登录验证码' }),
      proposal({ key: 'p2', title: '短信登录', confirmStatus: 'rejected', note: '本期不支持' }),
    ])
    await flushPromises()

    expect(mocks.fetchAiProposal).toHaveBeenCalledTimes(1)
    expect(mocks.fetchAiProposal).toHaveBeenCalledWith('t1', 'p1')
    expect(wrapper.text()).toContain('详情-p1')

    const next = wrapper.findAll('.proposal-review__nav button')[1]
    await next.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('短信登录')
    expect(wrapper.text()).toContain('驳回原因：本期不支持')
    expect(wrapper.text()).toContain('详情-p2')
    expect(mocks.fetchAiProposal).toHaveBeenCalledWith('t1', 'p2')

    const prev = wrapper.findAll('.proposal-review__nav button')[0]
    await prev.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('登录验证码')
    expect(mocks.fetchAiProposal).toHaveBeenNthCalledWith(1, 't1', 'p1')
    expect(mocks.fetchAiProposal).toHaveBeenNthCalledWith(2, 't1', 'p2')
    expect(mocks.fetchAiProposal.mock.calls.filter(([, key]) => key === 'p1')).toHaveLength(1)
  })

  it('明细加载失败展示重试，重试成功后隐藏错误', async () => {
    mocks.fetchAiProposal.mockRejectedValueOnce(new Error('网络异常'))
    const wrapper = mountPanel([proposal()])
    await flushPromises()

    expect(wrapper.text()).toContain('网络异常')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeDefined()

    await retry!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('网络异常')
    expect(wrapper.text()).toContain('需求描述')
    expect(mocks.fetchAiProposal).toHaveBeenCalledTimes(2)
  })

  it('明细为空时展示暂无明细', async () => {
    mocks.fetchAiProposal.mockResolvedValue({ content: {} })
    const wrapper = mountPanel([proposal()])
    await flushPromises()

    expect(wrapper.text()).toContain('暂无明细')
  })

  it('无提案记录展示空态', () => {
    const wrapper = mountPanel([])

    expect(wrapper.text()).toContain('提案为空')
    expect(wrapper.find('.proposal-review__nav').exists()).toBe(false)
    expect(wrapper.findAll('.proposal-review__item')).toHaveLength(0)
  })
})
