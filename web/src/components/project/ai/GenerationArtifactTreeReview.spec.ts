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

import GenerationArtifactTreeReview from './GenerationArtifactTreeReview.vue'

function proposals(count: number): AiTaskProposalSummary[] {
  return Array.from({ length: count }, (_, index) => ({
    key: `p${index + 1}`,
    kind: 'mindmap_document_suggestion',
    title: `产物${index + 1}`,
    parentKey: null,
    confirmStatus: 'pending',
    note: null,
  }))
}

function mountReview(items: AiTaskProposalSummary[]) {
  return mount(GenerationArtifactTreeReview, {
    props: { taskId: 't1', proposals: items },
    global: { plugins: [ElementPlus, createPinia()], stubs: { Link: true } },
  })
}

describe('GenerationArtifactTreeReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocks.fetchProjectModuleTree.mockResolvedValue([])
    mocks.fetchAiProposal.mockResolvedValue({ content: {} })
    mocks.confirmAiProposals.mockResolvedValue({ results: [] })
  })

  it('默认以卡片只展示当前第一条待处理产物，且无列表勾选与批量计数', () => {
    const wrapper = mountReview(proposals(5))
    expect(wrapper.findAll('.gen-review__item')).toHaveLength(1)
    expect(wrapper.text()).toContain('产物1')
    expect(wrapper.text()).not.toContain('产物2')
    expect(wrapper.text()).not.toContain('产物3')
    expect(wrapper.text()).not.toContain('已选')
    expect(wrapper.text()).not.toContain('全选')
    expect(wrapper.text()).not.toContain('批量采纳')
    expect(wrapper.text()).toMatch(/已处理\s*0\s*\/\s*共\s*5/)
  })

  it('提案刷新后按最新待处理清单自动切换下一条', async () => {
    const wrapper = mountReview(proposals(5))
    const updated = proposals(5).map((item, index) =>
      index === 0 ? { ...item, confirmStatus: 'adopted' as const } : item,
    )
    await wrapper.setProps({ proposals: updated })
    await flushPromises()
    expect(wrapper.findAll('.gen-review__item')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('产物1')
    expect(wrapper.text()).toContain('产物2')
    expect(wrapper.text()).not.toContain('产物3')
    expect(wrapper.text()).toMatch(/已处理\s*1\s*\/\s*共\s*5/)
  })

  it('全部驳回与全部处理完成分别展示对应空态', () => {
    const rejected = mountReview(
      proposals(2).map((item) => ({ ...item, confirmStatus: 'rejected' as const })),
    )
    expect(rejected.findAll('.gen-review__item')).toHaveLength(0)
    expect(rejected.text()).toContain('已全部驳回，未创建任何数据')

    const adopted = mountReview(
      proposals(2).map((item) => ({ ...item, confirmStatus: 'adopted' as const })),
    )
    expect(adopted.findAll('.gen-review__item')).toHaveLength(0)
    expect(adopted.text()).toContain('待处理产物已全部审核完成')
  })

  it('卡片内采纳调用确认接口并通知父级刷新', async () => {
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
    const wrapper = mountReview(proposals(1))
    const adoptButton = wrapper
      .findAll('.gen-review__card-actions button')
      .find((button) => button.text() === '采纳')
    expect(adoptButton).toBeDefined()
    await adoptButton!.trigger('click')
    await flushPromises()
    expect(mocks.confirmAiProposals).toHaveBeenCalledWith('t1', {
      items: [{ key: 'p1', action: 'adopted' }],
    })
    expect(wrapper.emitted('confirmed')).toHaveLength(1)
  })
})
