// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import type { AiTaskProposalSummary } from '@/types'
import type { ProposalRow } from '@/composables/project/ai/useAiProposalReview'
import AiProposalDetailView from './AiProposalDetailView.vue'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

function row(overrides: Partial<ProposalRow> = {}): ProposalRow {
  const summary: AiTaskProposalSummary = {
    key: 'p1',
    kind: 'requirement_suggestion',
    title: '登录',
    parentKey: null,
    confirmStatus: 'adopted',
    note: null,
  }
  return {
    ...summary,
    kindLabel: '需求建议',
    confirmMeta: { label: '已采纳', tagType: 'success' },
    contentLoaded: true,
    contentError: '',
    rawContent: {},
    parsed: {
      description: '',
      fields: [],
      compare: [],
      rankItems: [],
      sourceRefs: [],
      warnings: [],
      editValues: {},
    },
    content: {
      title: '登录',
      description: '',
      moduleId: '',
      priority: '',
      sourceRef: '',
    },
    ...overrides,
  }
}

function mountDetail(item: ProposalRow, taskType: string): ReturnType<typeof mount> {
  return mount(AiProposalDetailView, {
    props: { row: item, taskType },
    global: { plugins: [ElementPlus] },
  })
}

describe('AiProposalDetailView', () => {
  it('通用任务展示解析字段并渲染警示', () => {
    const item = row({
      parsed: {
        description: '导入描述',
        fields: [{ label: '优先级', value: 'high' }],
        compare: [],
        rankItems: [],
        sourceRefs: [{ id: 'r2', title: '来源需求', quote: '原文', changed: true }],
        warnings: ['来源已变更'],
        editValues: {},
      },
    })
    const wrapper = mountDetail(item, 'requirement_import')

    expect(wrapper.text()).toContain('导入描述')
    expect(wrapper.text()).toContain('来源需求')
    expect(wrapper.text()).toContain('来源已变更')
  })

  it('无可展示内容时展示暂无明细', () => {
    expect(mountDetail(row(), 'requirement_import').text()).toContain('暂无明细')
  })
})
