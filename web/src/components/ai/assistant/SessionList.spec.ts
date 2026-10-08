// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import SessionList from './SessionList.vue'
import type { AiAssistantConversation } from '@/types'

interface ListProps {
  conversations?: AiAssistantConversation[]
  currentId?: string | null
  loading?: boolean
  keyword?: string
}

function makeConversation(overrides: Partial<AiAssistantConversation> = {}): AiAssistantConversation {
  return {
    id: 'c1',
    title: '登录评审',
    status: 'active',
    lastMessageAt: null,
    messageCount: 0,
    contextSnapshot: null,
    ...overrides,
  }
}

function mountList(props: ListProps = {}) {
  return mount(SessionList, {
    props: {
      conversations: [],
      currentId: null,
      loading: false,
      keyword: '',
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
}

describe('components/ai/assistant/SessionList', () => {
  it('加载中且无数据时展示加载占位', () => {
    const wrapper = mountList({ loading: true })

    expect(wrapper.text()).toContain('加载中…')
  })

  it('空列表按关键词区分「无匹配会话」与「暂无会话」', () => {
    expect(mountList().text()).toContain('暂无会话')
    expect(mountList({ keyword: '登录' }).text()).toContain('无匹配会话')
  })

  it('渲染会话行、归档标记与当前会话标识', () => {
    const wrapper = mountList({
      conversations: [
        makeConversation(),
        makeConversation({ id: 'c2', title: '接口冒烟', status: 'archived' }),
      ],
      currentId: 'c1',
    })

    const rows = wrapper.findAll('button[role="listitem"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('登录评审')
    expect(rows[0].text()).toContain('-')
    expect(rows[0].attributes('aria-current')).toBe('true')
    expect(rows[1].text()).toContain('接口冒烟')
    expect(rows[1].text()).toContain('已归档')
    expect(rows[1].attributes('aria-current')).toBeUndefined()
  })

  it('点击会话行触发 select 并携带会话 ID', async () => {
    const wrapper = mountList({ conversations: [makeConversation()] })

    await wrapper.get('button[role="listitem"]').trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual(['c1'])
  })

  it('输入关键词立即回传 search，清空同样回传空串', async () => {
    const wrapper = mountList()
    const input = wrapper.get('input')

    await input.setValue('登录')
    expect(wrapper.emitted('search')?.[0]).toEqual(['登录'])

    await input.setValue('')
    expect(wrapper.emitted('search')?.[1]).toEqual([''])
  })
})
