// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import TraceEventList from './TraceEventList.vue'
import type { AiTaskTraceEvent } from '@/types'

const mocks = vi.hoisted(() => ({
  loadEvents: vi.fn(),
  resetEvents: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  clipboardWriteText: vi.fn<(text: string) => Promise<void>>(),
  storeState: {
    events: [] as AiTaskTraceEvent[],
    eventsLoading: false,
    eventsError: false,
    eventsHasMore: false,
    eventsExecutionId: 'exec-1',
  },
}))

vi.mock('@/stores/aiTask', () => ({
  useAiTaskStore: () => ({
    ...mocks.storeState,
    loadEvents: mocks.loadEvents,
    resetEvents: mocks.resetEvents,
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: mocks.success, warning: mocks.warning, error: vi.fn() },
  }
})

Object.defineProperty(globalThis, 'navigator', {
  value: { clipboard: { writeText: mocks.clipboardWriteText } },
  writable: true,
  configurable: true,
})

function decisionEvent(overrides: Partial<AiTaskTraceEvent['detail']> = {}): AiTaskTraceEvent {
  return {
    seq: 1,
    eventType: 'ai_decision',
    summary: '模型选择调用工具',
    createdAt: '2026-11-23T02:00:00',
    detail: {
      toolCalls: ['query_x'],
      ...overrides,
    },
  }
}

async function mountList(events: AiTaskTraceEvent[]) {
  mocks.storeState.events = events
  const wrapper = mount(TraceEventList, {
    props: { taskId: 'task-1', executionId: 'exec-1' },
    global: { plugins: [ElementPlus] },
  })
  await nextTick()
  return wrapper
}

async function expandTrace(wrapper: ReturnType<typeof mount>): Promise<void> {
  const buttons = wrapper.findAll('button')
  const toggle = buttons.find((b) => b.text().includes('展开轨迹'))
  expect(toggle).toBeDefined()
  await toggle!.trigger('click')
  await flushPromises()
}

describe('TraceEventList AI 决策原始详情', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('非决策事件不展示查看详情入口', async () => {
    const wrapper = await mountList([
      { seq: 2, eventType: 'tool_call', summary: '调用工具 x', detail: null, createdAt: '2026-11-23T02:00:00' },
    ])
    await expandTrace(wrapper)

    expect(wrapper.find('.trace-events__detail-toggle').exists()).toBe(false)
  })

  it('决策事件展开后展示请求摘要与模型响应并复制', async () => {
    mocks.clipboardWriteText.mockResolvedValue(undefined)
    const wrapper = await mountList([
      decisionEvent({
        rawRequest: {
          model: 'gpt-x',
          promptScene: 'requirement_import',
          context: { taskType: 'requirement_import', input: { a: 1 }, feedback: null },
          tools: ['query_x'],
        },
        rawResponse: {
          content: '结果',
          toolCalls: [{ id: 'c1', name: 'query_x', arguments: '{}' }],
          finishReason: 'TOOL_CALLS',
          usage: { promptTokens: 10, completionTokens: 5, totalTokens: 15 },
        },
      }),
    ])
    await expandTrace(wrapper)

    await wrapper.find('.trace-events__detail-toggle').trigger('click')
    await flushPromises()

    const sections = wrapper.findAll('.trace-events__section')
    expect(sections.length).toBe(2)
    expect(sections[0].text()).toContain('请求摘要')
    expect(sections[1].text()).toContain('模型响应')

    const copyButtons = wrapper.findAll('.trace-events__section button')
    expect(copyButtons.length).toBe(2)
    await copyButtons[1].trigger('click')
    await flushPromises()

    expect(mocks.clipboardWriteText).toHaveBeenCalledTimes(1)
    const copied = mocks.clipboardWriteText.mock.calls[0][0] as string
    expect(copied).toContain('"content": "结果"')
    expect(mocks.success).toHaveBeenCalled()
  })

  it('content 为 JSON 字符串时按结构化节点展示并复制解析结果', async () => {
    mocks.clipboardWriteText.mockResolvedValue(undefined)
    const wrapper = await mountList([
      decisionEvent({ rawResponse: { content: '{"title":"登录","steps":[1,2]}' } }),
    ])
    await expandTrace(wrapper)

    await wrapper.find('.trace-events__detail-toggle').trigger('click')
    await flushPromises()

    await wrapper.find('.trace-events__json .jn-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.find('.trace-events__json').text()).toContain('title')

    const copyButtons = wrapper.findAll('.trace-events__section button')
    await copyButtons[0].trigger('click')
    await flushPromises()

    const copied = mocks.clipboardWriteText.mock.calls[0][0] as string
    expect(copied).toContain('"title": "登录"')
    expect(copied).not.toContain('\\"title\\"')
  })

  it('content 为 Markdown 代码围栏包裹的 JSON 时剥离围栏后解析', async () => {
    mocks.clipboardWriteText.mockResolvedValue(undefined)
    const wrapper = await mountList([
      decisionEvent({ rawResponse: { content: '```json\n{"code":0}\n```' } }),
    ])
    await expandTrace(wrapper)

    await wrapper.find('.trace-events__detail-toggle').trigger('click')
    await flushPromises()
    const copyButtons = wrapper.findAll('.trace-events__section button')
    await copyButtons[0].trigger('click')
    await flushPromises()

    const copied = mocks.clipboardWriteText.mock.calls[0][0] as string
    expect(copied).toContain('"code": 0')
    expect(copied).not.toContain('```json')
  })

  it('代码围栏内非 JSON 时保留原文', async () => {
    mocks.clipboardWriteText.mockResolvedValue(undefined)
    const wrapper = await mountList([
      decisionEvent({ rawResponse: { content: '```json\n这不是 JSON\n```' } }),
    ])
    await expandTrace(wrapper)

    await wrapper.find('.trace-events__detail-toggle').trigger('click')
    await flushPromises()
    const copyButtons = wrapper.findAll('.trace-events__section button')
    await copyButtons[0].trigger('click')
    await flushPromises()

    const copied = mocks.clipboardWriteText.mock.calls[0][0] as string
    expect(copied).toContain('```json')
    expect(copied).toContain('这不是 JSON')
  })

  it('缺少原始数据键时展示空态', async () => {
    const wrapper = await mountList([decisionEvent()])
    await expandTrace(wrapper)

    await wrapper.find('.trace-events__detail-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.trace-events__empty').length).toBeGreaterThanOrEqual(1)
    expect(wrapper.text()).toContain('暂无原始数据')
    expect(wrapper.find('.trace-events__json').exists()).toBe(false)
  })

  it('手风琴：再次点击同一事件收起详情', async () => {
    const wrapper = await mountList([decisionEvent({ rawResponse: { content: '结果' } })])
    await expandTrace(wrapper)

    const toggle = wrapper.find('.trace-events__detail-toggle')
    await toggle.trigger('click')
    await flushPromises()
    expect(wrapper.find('.trace-events__detail').exists()).toBe(true)

    await toggle.trigger('click')
    await flushPromises()
    expect(wrapper.find('.trace-events__detail').exists()).toBe(false)
  })

  it('复制失败时给出警告', async () => {
    mocks.clipboardWriteText.mockRejectedValue(new Error('denied'))
    const wrapper = await mountList([decisionEvent({ rawRequest: { context: {} } })])
    await expandTrace(wrapper)

    await wrapper.find('.trace-events__detail-toggle').trigger('click')
    await flushPromises()
    const copyButton = wrapper.find('.trace-events__section button')
    await copyButton.trigger('click')
    await flushPromises()

    expect(mocks.warning).toHaveBeenCalled()
  })
})
