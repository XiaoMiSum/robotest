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

import AiTaskAdoptionResultPanel from './AiTaskAdoptionResultPanel.vue'

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
  return mount(AiTaskAdoptionResultPanel, {
    props: {
      taskId: 't1',
      taskType,
      proposals,
      result: { label: '部分采纳', tagType: 'warning' },
    },
    global: { plugins: [ElementPlus] },
  })
}

describe('AiTaskAdoptionResultPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchProjectModuleTree.mockResolvedValue([])
    mocks.fetchAiProposal.mockResolvedValue({
      content: { description: '需求描述', priority: 'high', sourceRef: 'PRD §2' },
    })
  })

  it('默认收起展示只读卡片，无采纳 / 驳回操作按钮', async () => {
    const wrapper = mountPanel([
      proposal(),
      proposal({ key: 'p2', title: '短信登录', confirmStatus: 'rejected', note: '本期不支持' }),
      proposal({ key: 'p3', confirmStatus: 'pending' }),
    ])

    expect(wrapper.text()).toContain('采纳结果')
    expect(wrapper.text()).toContain('部分采纳')
    expect(wrapper.text()).toContain('登录验证码')
    expect(wrapper.text()).toContain('短信登录')
    expect(wrapper.text()).toContain('驳回原因：本期不支持')
    expect(wrapper.findAll('.el-collapse-item')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('需求描述')
    expect(wrapper.findAll('button')).toHaveLength(0)
    await flushPromises()
    expect(mocks.fetchAiProposal).not.toHaveBeenCalled()
  })

  it('首次展开按需加载明细，收起重展不重复请求', async () => {
    const wrapper = mountPanel([proposal()])
    const header = wrapper.find('.el-collapse-item__header')

    await header.trigger('click')
    await flushPromises()
    expect(mocks.fetchAiProposal).toHaveBeenCalledTimes(1)
    expect(mocks.fetchAiProposal).toHaveBeenCalledWith('t1', 'p1')
    expect(wrapper.text()).toContain('需求描述')
    expect(wrapper.text()).toContain('来源引用')

    await header.trigger('click')
    await flushPromises()
    await header.trigger('click')
    await flushPromises()
    expect(mocks.fetchAiProposal).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('需求描述')
  })

  it('明细加载失败展示重试，重试成功后隐藏错误', async () => {
    mocks.fetchAiProposal.mockRejectedValueOnce(new Error('网络异常'))
    const wrapper = mountPanel([proposal()])
    const header = wrapper.find('.el-collapse-item__header')

    await header.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('产物明细加载失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeDefined()

    await retry!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('产物明细加载失败')
    expect(wrapper.text()).toContain('需求描述')
    expect(mocks.fetchAiProposal).toHaveBeenCalledTimes(2)
  })

  it('明细为空时展示暂无明细', async () => {
    mocks.fetchAiProposal.mockResolvedValue({ content: {} })
    const wrapper = mountPanel([proposal()])

    await wrapper.find('.el-collapse-item__header').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无明细')
  })

  it('无采纳记录展示空态', () => {
    const wrapper = mountPanel([
      proposal({ confirmStatus: 'pending' }),
      proposal({ key: 'p4', confirmStatus: 'not_applicable' }),
    ])

    expect(wrapper.text()).toContain('本次执行没有采纳或驳回记录')
    expect(wrapper.findAll('.el-collapse-item')).toHaveLength(0)
  })
})
