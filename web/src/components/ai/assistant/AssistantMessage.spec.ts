// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AssistantMessage from './AssistantMessage.vue'
import type { AiAssistantCitation, AiAssistantMessage } from '@/types'

// Markdown 渲染由 MarkdownView 既有单测覆盖，此处只断言本组件的组装行为
vi.mock('@/components/common/MarkdownView.vue', async () => {
  const { h } = await import('vue')
  return {
    default: {
      name: 'MarkdownView',
      props: { content: { type: String, required: true } },
      setup: (props: { content: string }) => () => h('div', { class: 'md-stub' }, props.content),
    },
  }
})

function makeMessage(overrides: Partial<AiAssistantMessage> = {}): AiAssistantMessage {
  return {
    id: 'm1',
    conversationId: 'c1',
    role: 'assistant',
    content: '答案正文',
    attachments: null,
    intent: null,
    citations: null,
    execution: null,
    status: 'done',
    createdAt: '2026-10-03T00:00:00.000Z',
    ...overrides,
  }
}

const CITATIONS: AiAssistantCitation[] = [
  { type: 'requirement', id: 'r1', title: '登录需求', quote: '用户可通过账号密码登录' },
  { type: 'module', id: 'm1', title: '订单模块', quote: '订单创建流程' },
]

function mountMessage(
  message: AiAssistantMessage,
  recovering = false,
) {
  return mount(AssistantMessage, { props: { message, recovering } })
}

describe('components/ai/assistant/AssistantMessage', () => {
  it('渲染助手角色标识与 Markdown 正文', () => {
    const wrapper = mountMessage(makeMessage())

    expect(wrapper.text()).toContain('🤖 助手')
    expect(wrapper.get('.md-stub').text()).toBe('答案正文')
  })

  it('无正文时不渲染正文区', () => {
    const wrapper = mountMessage(makeMessage({ content: null, status: 'streaming' }))

    expect(wrapper.find('.md-stub').exists()).toBe(false)
  })

  it('引用默认收起，点击标题展开摘要，再次点击收起', async () => {
    const wrapper = mountMessage(makeMessage({ citations: CITATIONS }))

    expect(wrapper.find('.ai-citation__quote').exists()).toBe(false)

    const titles = wrapper.findAll('.ai-citation__title')
    expect(titles[0].text()).toBe('登录需求')
    await titles[0].trigger('click')
    expect(wrapper.text()).toContain('用户可通过账号密码登录')
    expect(titles[1].attributes('aria-expanded')).toBe('false')

    await titles[0].trigger('click')
    expect(wrapper.find('.ai-citation__quote').exists()).toBe(false)
  })

  it('可定位类型展示跳转按钮并携带引用，不可定位类型仅展开', async () => {
    const wrapper = mountMessage(makeMessage({ citations: CITATIONS }))

    const jumps = wrapper.findAll('.ai-citation__jump')
    expect(jumps).toHaveLength(1)

    await jumps[0].trigger('click')
    expect(wrapper.emitted('jump')?.[0]?.[0]).toEqual(CITATIONS[0])
  })

  it('恢复中仅标记未终态消息', () => {
    expect(
      mountMessage(makeMessage({ status: 'streaming', content: null }), true).text(),
    ).toContain('恢复中…')
    expect(mountMessage(makeMessage(), true).text()).not.toContain('恢复中…')
    expect(
      mountMessage(makeMessage({ status: 'streaming', content: null }), false).text(),
    ).not.toContain('恢复中…')
  })
})
