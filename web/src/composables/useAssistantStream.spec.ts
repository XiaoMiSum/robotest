import { describe, expect, it, vi } from 'vitest'
import type { AiStreamEvent } from './useAiStream'
import {
  parseConfirmRequiredEvent,
  parseDeltaEvent,
  parseDoneEvent,
  parseErrorEvent,
  parseMinderCommandsEvent,
  parseToolCallEvent,
  useAssistantStream,
} from './useAssistantStream'

/** 拦截 useAiStream：捕获事件分发器与透传回调，直接驱动结构化解析分支（详细设计 5.3） */
interface CapturedOptions {
  url: string
  body?: unknown
  onEvent: (event: AiStreamEvent) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

const { captured, useAiStreamMock } = vi.hoisted(() => {
  const captured: { options: CapturedOptions | null } = { options: null }
  return {
    captured,
    useAiStreamMock: vi.fn((options: CapturedOptions) => {
      captured.options = options
      return { cancel: vi.fn() }
    }),
  }
})

vi.mock('./useAiStream', () => ({ useAiStream: useAiStreamMock }))

describe('useAssistantStream 事件分发（多事件解析）', () => {
  function setup() {
    const handlers = {
      onDelta: vi.fn(),
      onToolCall: vi.fn(),
      onConfirmRequired: vi.fn(),
      onMinderCommands: vi.fn(),
      onDone: vi.fn(),
      onError: vi.fn(),
    }
    const controller = useAssistantStream({
      url: '/assistant/chat',
      body: { content: 'hi' },
      handlers,
    })
    const options = captured.options!
    return { handlers, controller, options }
  }

  it('delta 帧触发 onDelta 并透传增量文本', () => {
    const { handlers, options } = setup()
    options.onEvent({ event: 'delta', data: { content: '增量' } })
    expect(handlers.onDelta).toHaveBeenCalledWith('增量')
    expect(handlers.onToolCall).not.toHaveBeenCalled()
  })

  it('tool_call 帧触发 onToolCall', () => {
    const { handlers, options } = setup()
    options.onEvent({ event: 'tool_call', data: { toolName: 'query_reviews', summary: '5 条结果' } })
    expect(handlers.onToolCall).toHaveBeenCalledWith({ toolName: 'query_reviews', summary: '5 条结果' })
  })

  it('confirm_required 帧触发 onConfirmRequired', () => {
    const { handlers, options } = setup()
    options.onEvent({
      event: 'confirm_required',
      data: { confirmToken: 't1', toolName: 'create_bug', preview: '{}', expiresAt: '2026-08-01T00:00:00Z' },
    })
    expect(handlers.onConfirmRequired).toHaveBeenCalledWith({
      confirmToken: 't1',
      toolName: 'create_bug',
      preview: '{}',
      expiresAt: '2026-08-01T00:00:00Z',
    })
  })

  it('confirm_required 载荷不完整时不下发（解析失败静默，调用方按无确认处理）', () => {
    const { handlers, options } = setup()
    options.onEvent({ event: 'confirm_required', data: { confirmToken: 't1', toolName: 'create_bug' } })
    expect(handlers.onConfirmRequired).not.toHaveBeenCalled()
  })

  it('minder_commands 帧触发 onMinderCommands', () => {
    const { handlers, options } = setup()
    options.onEvent({
      event: 'minder_commands',
      data: { commands: [{ op: 'add_node' }], documentId: 'doc-1' },
    })
    expect(handlers.onMinderCommands).toHaveBeenCalledWith({
      commands: [{ op: 'add_node' }],
      documentId: 'doc-1',
    })
  })

  it('done 帧触发 onDone 并透传 messageId', () => {
    const { handlers, options } = setup()
    options.onEvent({ event: 'done', data: { messageId: 'm-1' } })
    expect(handlers.onDone).toHaveBeenCalledWith('m-1')
  })

  it('error 帧触发 onError', () => {
    const { handlers, options } = setup()
    options.onEvent({ event: 'error', data: { code: 6011, message: '确认令牌超时' } })
    expect(handlers.onError).toHaveBeenCalledWith({ code: 6011, message: '确认令牌超时' })
  })

  it('未识别事件类型不抛错、不下发任何 handler', () => {
    const { handlers, options } = setup()
    expect(() => options.onEvent({ event: 'statistics', data: { total: 1 } })).not.toThrow()
    for (const handler of Object.values(handlers)) {
      expect(handler).not.toHaveBeenCalled()
    }
  })

  it('url/body 与连接回调透传给 useAiStream', () => {
    const onConnectionError = vi.fn()
    const onClose = vi.fn()
    useAssistantStream({ url: '/assistant/confirm', body: { confirmToken: 't' }, handlers: {}, onConnectionError, onClose })
    expect(useAiStreamMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ url: '/assistant/confirm', body: { confirmToken: 't' }, onError: onConnectionError, onClose }),
    )
  })
})

describe('助手 SSE 事件载荷解析（异常帧容错，C8）', () => {
  it('parseDeltaEvent：content 缺失或非对象按空串', () => {
    expect(parseDeltaEvent({ content: 'x' })).toEqual({ content: 'x' })
    expect(parseDeltaEvent({})).toEqual({ content: '' })
    expect(parseDeltaEvent('纯文本')).toEqual({ content: '' })
    expect(parseDeltaEvent(null)).toEqual({ content: '' })
  })

  it('parseToolCallEvent：字段缺失降级占位，不崩溃', () => {
    expect(parseToolCallEvent({ toolName: 'query_reviews', summary: '5 条' })).toEqual({
      toolName: 'query_reviews',
      summary: '5 条',
    })
    expect(parseToolCallEvent({})).toEqual({ toolName: 'unknown_tool', summary: '' })
    expect(parseToolCallEvent(null)).toEqual({ toolName: 'unknown_tool', summary: '' })
  })

  it('parseConfirmRequiredEvent：confirmToken/toolName/expiresAt 任一缺失视为解析失败', () => {
    const valid = {
      confirmToken: 't1',
      toolName: 'create_bug',
      preview: '{"title":"x"}',
      expiresAt: '2026-08-01T00:00:00Z',
    }
    expect(parseConfirmRequiredEvent(valid)).toEqual(valid)
    expect(parseConfirmRequiredEvent({ ...valid, confirmToken: undefined })).toBeNull()
    expect(parseConfirmRequiredEvent({ ...valid, expiresAt: undefined })).toBeNull()
    expect(parseConfirmRequiredEvent({ ...valid, toolName: '' })).toBeNull()
    expect(parseConfirmRequiredEvent('str')).toBeNull()
    // preview 缺失降级为空对象 JSON，不阻断确认
    expect(parseConfirmRequiredEvent({ confirmToken: 't1', toolName: 'create_bug', expiresAt: '2026-08-01T00:00:00Z' })).toEqual({
      confirmToken: 't1',
      toolName: 'create_bug',
      preview: '{}',
      expiresAt: '2026-08-01T00:00:00Z',
    })
  })

  it('parseMinderCommandsEvent：commands 非数组或 documentId 缺失不崩溃', () => {
    expect(parseMinderCommandsEvent({ commands: [{ op: 'add_node' }], documentId: 'doc1' })).toEqual({
      commands: [{ op: 'add_node' }],
      documentId: 'doc1',
    })
    expect(parseMinderCommandsEvent({ commands: 'oops', documentId: 'doc1' })).toEqual({ commands: [], documentId: 'doc1' })
    expect(parseMinderCommandsEvent({ commands: [] })).toEqual({ commands: [], documentId: '' })
    expect(parseMinderCommandsEvent(null)).toEqual({ commands: [], documentId: '' })
  })

  it('parseDoneEvent：messageId 缺失按空串', () => {
    expect(parseDoneEvent({ messageId: 'm1' })).toEqual({ messageId: 'm1' })
    expect(parseDoneEvent({})).toEqual({ messageId: '' })
    expect(parseDoneEvent(42)).toEqual({ messageId: '' })
  })

  it('parseErrorEvent：code 非数字或 message 缺失降级通用错误', () => {
    expect(parseErrorEvent({ code: 6011, message: '令牌过期' })).toEqual({ code: 6011, message: '令牌过期' })
    expect(parseErrorEvent({ code: '6011', message: 'x' })).toEqual({ code: 0, message: 'x' })
    expect(parseErrorEvent({})).toEqual({ code: 0, message: 'AI 调用失败' })
    expect(parseErrorEvent('boom')).toEqual({ code: 0, message: 'AI 调用失败' })
  })
})
