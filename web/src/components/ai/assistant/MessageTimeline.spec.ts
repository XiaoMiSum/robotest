// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import MessageTimeline from './MessageTimeline.vue'
import type { AiAssistantIntent, AiAssistantMessage } from '@/types'

// Markdown 渲染由 MarkdownView 既有单测覆盖，此处只断言流式缓冲透传
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
    content: '答案',
    attachments: null,
    intent: null,
    citations: [],
    execution: null,
    status: 'done',
    createdAt: '2026-10-03T00:00:00.000Z',
    ...overrides,
  }
}

function makeIntent(overrides: Partial<AiAssistantIntent> = {}): AiAssistantIntent {
  return {
    kind: 'create_review',
    targetType: 'test_review',
    targetTitle: '登录评审',
    createCount: 1,
    changes: [{ field: 'title', op: 'add', value: '登录评审' }],
    scope: { workspaceId: 'w1', projectId: 'p1' },
    expiresAt: new Date(Date.now() + 600_000).toISOString().slice(0, 19),
    ...overrides,
  }
}

interface TimelineProps {
  messages?: AiAssistantMessage[]
  loading?: boolean
  hasMore?: boolean
  streaming?: boolean
  streamText?: string
  recovering?: boolean
  executing?: boolean
  readonly?: boolean
}

function mountTimeline(props: TimelineProps = {}) {
  return mount(MessageTimeline, {
    props: {
      messages: [],
      loading: false,
      hasMore: false,
      streaming: false,
      streamText: '',
      recovering: false,
      executing: false,
      readonly: false,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
}

function findButton(wrapper: ReturnType<typeof mountTimeline>, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`未找到按钮：${text}`)
  return found
}

function fakeScroll(
  wrapper: ReturnType<typeof mountTimeline>,
  metrics: { scrollTop: number; scrollHeight: number; clientHeight: number },
): HTMLElement {
  const scroll = wrapper.get('.ai-timeline__scroll').element as HTMLElement
  Object.defineProperty(scroll, 'scrollTop', {
    value: metrics.scrollTop,
    writable: true,
    configurable: true,
  })
  Object.defineProperty(scroll, 'scrollHeight', {
    value: metrics.scrollHeight,
    configurable: true,
  })
  Object.defineProperty(scroll, 'clientHeight', {
    value: metrics.clientHeight,
    configurable: true,
  })
  return scroll
}

describe('components/ai/assistant/MessageTimeline', () => {
  it('空态展示示例指令，点击回填不直接发送', async () => {
    const wrapper = mountTimeline()

    expect(wrapper.text()).toContain('试试这些指令：')
    const examples = wrapper.findAll('.ai-timeline__example')
    expect(examples.length).toBeGreaterThan(0)

    await examples[0].trigger('click')
    expect(wrapper.emitted('pick-example')?.[0]).toEqual(['帮我建一个登录评审'])
    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('首屏加载中展示加载占位而非示例', () => {
    const wrapper = mountTimeline({ loading: true })

    expect(wrapper.text()).toContain('加载中…')
    expect(wrapper.text()).not.toContain('试试这些指令：')
  })

  it('按角色渲染用户、助手与澄清行', async () => {
    const wrapper = mountTimeline({
      messages: [
        makeMessage({ id: 'u1', role: 'user', content: '提问' }),
        makeMessage({ id: 'a1', content: '**答案**' }),
        makeMessage({
          id: 'c1',
          content: '要建哪个模块的评审？\n\n- 登录模块\n- 订单模块',
          citations: null,
        }),
      ],
    })

    expect(wrapper.text()).toContain('👤 用户')
    expect(wrapper.text()).toContain('提问')
    expect(wrapper.get('.md-stub').text()).toBe('**答案**')
    expect(wrapper.text()).toContain('要建哪个模块的评审？')

    await wrapper.findAll('.ai-clarify__option')[1].trigger('click')
    expect(wrapper.emitted('clarify-pick')?.[0]).toEqual(['订单模块'])
  })

  it('流式缓冲渲染为助手正文', () => {
    const wrapper = mountTimeline({
      messages: [makeMessage({ id: 'u1', role: 'user', content: '提问' })],
      streaming: true,
      streamText: '正在回答…',
    })

    expect(wrapper.get('.ai-timeline__stream .md-stub').text()).toBe('正在回答…')
  })

  it('恢复中标记透传到未终态消息', () => {
    const wrapper = mountTimeline({
      messages: [makeMessage({ id: 'a1', content: null, status: 'streaming' })],
      recovering: true,
    })

    expect(wrapper.text()).toContain('恢复中…')
  })

  it('引用跳转事件透传', async () => {
    const citation = { type: 'requirement', id: 'r1', title: '登录需求', quote: '摘要' }
    const wrapper = mountTimeline({
      messages: [makeMessage({ citations: [citation] })],
    })

    await wrapper.get('.ai-citation__jump').trigger('click')
    expect(wrapper.emitted('citation-jump')?.[0]?.[0]).toEqual(citation)
  })

  it('触顶时请求加载更早消息', async () => {
    const wrapper = mountTimeline({
      messages: [makeMessage()],
      hasMore: true,
    })

    const scroll = fakeScroll(wrapper, { scrollTop: 0, scrollHeight: 1000, clientHeight: 400 })
    scroll.dispatchEvent(new Event('scroll'))
    await nextTick()

    expect(wrapper.emitted('load-older')).toHaveLength(1)
  })

  it('上滑查看历史暂停跟随并可回到最新', async () => {
    const wrapper = mountTimeline({
      messages: [makeMessage()],
    })

    expect(wrapper.find('[aria-label="回到最新"]').exists()).toBe(false)

    const scroll = fakeScroll(wrapper, { scrollTop: 0, scrollHeight: 1000, clientHeight: 400 })
    scroll.dispatchEvent(new Event('scroll'))
    await nextTick()

    const back = wrapper.get('[aria-label="回到最新"]')
    await back.trigger('click')
    await nextTick()

    expect(wrapper.find('[aria-label="回到最新"]').exists()).toBe(false)
    expect((scroll as HTMLElement & { scrollTop: number }).scrollTop).toBe(1000)
  })

  it('新消息在跟随时自动滚动到底部', async () => {
    const wrapper = mountTimeline()
    const scroll = fakeScroll(wrapper, { scrollTop: 0, scrollHeight: 800, clientHeight: 400 })
    // 初始挂载的贴底写入被假度量重置，先归位再观察后续增量
    ;(scroll as HTMLElement & { scrollTop: number }).scrollTop = 400

    wrapper.setProps({ messages: [makeMessage()] })
    await flushPromises()

    expect((scroll as HTMLElement & { scrollTop: number }).scrollTop).toBe(800)
  })

  it('预览消息渲染预览卡并转发操作（含无正文）', async () => {
    const wrapper = mountTimeline({
      messages: [
        makeMessage({ id: 'u1', role: 'user', content: '帮我建一个登录评审' }),
        makeMessage({ id: 'p1', content: null, intent: makeIntent(), citations: null }),
      ],
    })

    expect(wrapper.find('.ai-preview').exists()).toBe(true)
    expect(wrapper.text()).toContain('登录评审')

    await findButton(wrapper, '确认执行').trigger('click')
    expect(wrapper.emitted('preview-execute')?.[0]).toEqual(['p1'])

    await findButton(wrapper, '取消').trigger('click')
    expect(wrapper.emitted('preview-cancel')?.[0]).toEqual(['p1'])
  })

  it('返回修改转发解析摘要，摘要为空时禁用', async () => {
    const withSummary = mountTimeline({
      messages: [
        makeMessage({ id: 'p1', content: '将创建登录评审', intent: makeIntent(), citations: null }),
      ],
    })
    await findButton(withSummary, '返回修改').trigger('click')
    expect(withSummary.emitted('preview-edit')?.[0]).toEqual(['将创建登录评审'])
    withSummary.unmount()

    const withoutSummary = mountTimeline({
      messages: [makeMessage({ id: 'p2', content: null, intent: makeIntent(), citations: null })],
    })
    const button = findButton(withoutSummary, '返回修改')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(withoutSummary.emitted('preview-edit')).toBeUndefined()
    withoutSummary.unmount()
  })

  it('预览过期后转发重新解析事件', async () => {
    const wrapper = mountTimeline({
      messages: [
        makeMessage({ id: 'u1', role: 'user', content: '帮我建一个登录评审' }),
        makeMessage({
          id: 'p1',
          content: null,
          intent: makeIntent({ expiresAt: '2020-01-01T00:00:00' }),
          citations: null,
        }),
      ],
    })

    await findButton(wrapper, '重新解析').trigger('click')
    expect(wrapper.emitted('preview-reparse')?.[0]).toEqual(['p1'])
  })

  it('回执消息渲染回执卡并转发单项重试与链接跳转', async () => {
    const wrapper = mountTimeline({
      messages: [
        makeMessage({
          id: 'r1',
          content: null,
          execution: {
            status: 'executed',
            results: [
              { action: 'create_case', success: false, errorMsg: '无权限执行该操作' },
              { action: 'create_plan', success: true, link: '/workspace/projects/plans/p1' },
            ],
          },
          citations: null,
        }),
      ],
    })

    expect(wrapper.find('.ai-receipt').exists()).toBe(true)
    expect(wrapper.text()).toContain('无权限执行该操作')

    await findButton(wrapper, '重试').trigger('click')
    expect(wrapper.emitted('receipt-retry')?.[0]).toEqual(['r1', 0])

    await findButton(wrapper, '查看').trigger('click')
    expect(wrapper.emitted('receipt-jump')?.[0]).toEqual(['/workspace/projects/plans/p1'])
  })

  it('执行中与归档只读透传给卡片禁用操作', async () => {
    const wrapper = mountTimeline({
      messages: [
        makeMessage({ id: 'p1', content: null, intent: makeIntent(), citations: null }),
      ],
      executing: true,
      readonly: true,
    })

    expect(findButton(wrapper, '确认执行').attributes('disabled')).toBeDefined()
    expect(findButton(wrapper, '取消').attributes('disabled')).toBeDefined()
  })
})
