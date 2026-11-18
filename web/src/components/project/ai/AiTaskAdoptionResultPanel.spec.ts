// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import type { AiTaskProposalSummary } from '@/types'
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

function mountPanel(proposals: AiTaskProposalSummary[]): ReturnType<typeof mount> {
  return mount(AiTaskAdoptionResultPanel, {
    props: {
      proposals,
      result: { label: '部分采纳', tagType: 'warning' },
    },
    global: { plugins: [ElementPlus] },
  })
}

describe('AiTaskAdoptionResultPanel', () => {
  it('只读展示提案标题、结果标签与驳回原因，不提供操作按钮', () => {
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
    expect(wrapper.findAll('.adoption-result__row')).toHaveLength(2)
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('无采纳记录展示空态', () => {
    const wrapper = mountPanel([
      proposal({ confirmStatus: 'pending' }),
      proposal({ key: 'p4', confirmStatus: 'not_applicable' }),
    ])

    expect(wrapper.text()).toContain('本次执行没有采纳或驳回记录')
    expect(wrapper.findAll('.adoption-result__row')).toHaveLength(0)
  })
})
