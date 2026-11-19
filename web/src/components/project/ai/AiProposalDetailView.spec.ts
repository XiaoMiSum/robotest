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
      isTestCase: false,
      parentRef: '',
      attributes: { priority: '', precondition: '', steps: [], expected: [], tags: [] },
      sourceRefs: [],
      suspectedDuplicateOf: '',
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
  it('生成链展示用例属性、来源引用并可跳转需求原文', async () => {
    const item = row({
      content: {
        ...row().content,
        isTestCase: true,
        priority: 'medium',
        attributes: {
          priority: 'medium',
          precondition: '已注册',
          steps: ['输入账号'],
          expected: ['进入首页'],
          tags: ['登录'],
        },
        sourceRefs: [{ requirementId: 'r1', quote: '支持登录', changed: false }],
      },
    })
    const wrapper = mountDetail(item, 'test_design_generation')

    expect(wrapper.text()).toContain('P1·中')
    expect(wrapper.text()).toContain('已注册')
    expect(wrapper.text()).toContain('输入账号')
    expect(wrapper.text()).toContain('进入首页')
    await wrapper.find('.el-link').trigger('click')
    expect(push).toHaveBeenCalledWith('/workspace/projects/requirements/r1')
  })

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
