// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import MarkdownView from '@/components/common/MarkdownView.vue'
import ProposalDescriptionView from './ProposalDescriptionView.vue'

describe('ProposalDescriptionView', () => {
  it('把提案描述原文交给 MarkdownView 只读渲染', () => {
    const content = '## 叠加限制\n\n- 同订单仅可使用一张券'
    const wrapper = shallowMount(ProposalDescriptionView, { props: { content } })

    const markdown = wrapper.findComponent(MarkdownView)
    expect(markdown.exists()).toBe(true)
    expect(markdown.props('content')).toBe(content)
  })
})
