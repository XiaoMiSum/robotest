// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import type { ProposalCompareRow } from '@/composables/project/ai/reviewProfiles'
import ProposalCompareView from './ProposalCompareView.vue'

const items: ProposalCompareRow[] = [
  { label: '优先级', existing: 'P1', suggested: 'P0', changed: true },
  { label: '标题', existing: '登录', suggested: '登录', changed: false },
]

function mountView(rows: ProposalCompareRow[]) {
  return mount(ProposalCompareView, {
    props: { items: rows },
    global: { plugins: [ElementPlus] },
  })
}

describe('ProposalCompareView', () => {
  it('三列分区呈现项目、现有内容与 AI 建议', () => {
    const wrapper = mountView(items)

    expect(wrapper.text()).toContain('项目')
    expect(wrapper.text()).toContain('现有内容')
    expect(wrapper.text()).toContain('AI 建议')
    expect(wrapper.findAll('.compare-view__row')).toHaveLength(1)
    expect(wrapper.text()).toContain('P1')
    expect(wrapper.text()).toContain('P0')
    expect(wrapper.find('.compare-view__row').classes()).toContain('compare-view__row--changed')
  })

  it('默认折叠无变化项，展开后展示全部', async () => {
    const wrapper = mountView(items)
    const fold = wrapper.find('.compare-view__fold')

    expect(fold.text()).toContain('展开其余 1 项')
    await fold.trigger('click')
    expect(wrapper.findAll('.compare-view__row')).toHaveLength(2)
  })

  it('无条目时不渲染', () => {
    expect(mountView([]).find('.compare-view').exists()).toBe(false)
  })
})
