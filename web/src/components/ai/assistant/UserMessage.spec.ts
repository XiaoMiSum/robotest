// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import UserMessage from './UserMessage.vue'
import type { AiAssistantMessage } from '@/types'

function makeMessage(overrides: Partial<AiAssistantMessage> = {}): AiAssistantMessage {
  return {
    id: 'm1',
    conversationId: 'c1',
    role: 'user',
    content: '帮我建一个登录评审',
    attachments: null,
    intent: null,
    citations: null,
    execution: null,
    status: 'done',
    createdAt: '2026-10-03T00:00:00.000Z',
    ...overrides,
  }
}

describe('components/ai/assistant/UserMessage', () => {
  it('渲染用户角色标识与正文', () => {
    const wrapper = mount(UserMessage, {
      props: { message: makeMessage() },
      global: { plugins: [ElementPlus] },
    })

    expect(wrapper.text()).toContain('👤 用户')
    expect(wrapper.text()).toContain('帮我建一个登录评审')
  })

  it('携带附件时逐项渲染引用标签', () => {
    const wrapper = mount(UserMessage, {
      props: {
        message: makeMessage({
          attachments: [
            { entityType: 'requirement', entityId: 'r1', entityTitle: '登录需求' },
            { entityType: 'module', entityId: 'm1', entityTitle: null },
          ],
        }),
      },
      global: { plugins: [ElementPlus] },
    })

    const items = wrapper.findAll('.ai-user-message__attachment')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('登录需求')
    // 标题缺失时回退展示实体类型
    expect(items[1].text()).toContain('module')
  })

  it('无附件时不渲染引用区', () => {
    const wrapper = mount(UserMessage, {
      props: { message: makeMessage() },
      global: { plugins: [ElementPlus] },
    })

    expect(wrapper.find('.ai-user-message__attachments').exists()).toBe(false)
  })
})
