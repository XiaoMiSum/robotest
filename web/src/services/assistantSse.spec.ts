import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AiAssistantSseEvent } from '@/types'

// node 测试环境无 sessionStorage；SSE 客户端只需上下文头与 token 两个纯函数
vi.mock('@/services', () => ({
  getContextHeaders: vi.fn(() => ({ 'X-Active-Workspace': 'w1' })),
  getAccessToken: vi.fn(() => 'token'),
}))

import {
  AssistantStreamError,
  AssistantStreamInterruptedError,
  createSseFrameParser,
  parseSseFrame,
  streamAssistantMessage,
} from './assistantSse'

describe('services/assistantSse 帧解析', () => {
  it('parseSseFrame 解析事件名与 JSON 载荷，忽略心跳注释行', () => {
    const frame = ': ping\nevent: delta\ndata: {"text":"你好"}'
    expect(parseSseFrame(frame)).toEqual({ type: 'delta', data: { text: '你好' } })
  })

  it('未知事件名返回 null（后端新增事件不致前端崩溃）', () => {
    expect(parseSseFrame('event: revalidate\ndata: {}')).toBeNull()
    expect(parseSseFrame(': ping')).toBeNull()
  })

  it('畸形 JSON 按未知事件忽略，不抛异常', () => {
    expect(parseSseFrame('event: delta\ndata: {broken')).toBeNull()
  })

  it('跨 chunk 缓冲：帧被拆分到两个 chunk 仍完整解析', () => {
    const events: AiAssistantSseEvent[] = []
    const parser = createSseFrameParser((event) => events.push(event))

    parser.push('event: delta\ndata: {"te')
    parser.push('xt":"流式"}\n\nevent: done\ndata: {"messageId":"m1"}\n\n')

    expect(events).toEqual([
      { type: 'delta', data: { text: '流式' } },
      { type: 'done', data: { messageId: 'm1' } },
    ])
  })

  it('flush 处理结尾缺少空行的残留帧', () => {
    const events: AiAssistantSseEvent[] = []
    const parser = createSseFrameParser((event) => events.push(event))

    parser.push('event: citations\ndata: []')
    parser.flush()

    expect(events).toEqual([{ type: 'citations', data: [] }])
  })
})

describe('services/assistantSse 发送', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  function jsonResponse(body: string, status = 200): Response {
    return new Response(body, {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  it('业务校验失败（Result 包装）抛出带错误码的 AssistantStreamError', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(JSON.stringify({ code: 1000018260, msg: '消息内容不合法' })),
    ) as unknown as typeof fetch

    await expect(
      streamAssistantMessage('c1', { content: 'x' }, () => {}),
    ).rejects.toMatchObject({ name: 'AssistantStreamError', code: 1000018260 })
  })

  it('连接建立前网络失败按中断抛出（调用方转轮询补齐）', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) as unknown as typeof fetch

    await expect(
      streamAssistantMessage('c1', { content: 'x' }, () => {}),
    ).rejects.toBeInstanceOf(AssistantStreamInterruptedError)
  })

  it('流式响应逐事件回调，正常关闭即返回', async () => {
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: delta\ndata: {"text":"你"}\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: {"messageId":"m1"}\n\n'))
        controller.close()
      },
    })
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }),
    ) as unknown as typeof fetch

    const events: AiAssistantSseEvent[] = []
    await streamAssistantMessage('c1', { content: 'hi' }, (event) => events.push(event))

    expect(events).toEqual([
      { type: 'delta', data: { text: '你' } },
      { type: 'done', data: { messageId: 'm1' } },
    ])
  })

  it('流中途断开抛出中断错误（已受理，转轮询）', async () => {
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: delta\ndata: {"text":"你"}\n\n'))
        controller.error(new TypeError('network reset'))
      },
    })
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }),
    ) as unknown as typeof fetch

    await expect(
      streamAssistantMessage('c1', { content: 'hi' }, () => {}),
    ).rejects.toBeInstanceOf(AssistantStreamInterruptedError)
  })

  it('非 Result 包装的错误响应按 HTTP 状态抛出', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse('<html>gateway</html>', 502),
    ) as unknown as typeof fetch

    const error = await streamAssistantMessage('c1', { content: 'x' }, () => {}).catch(
      (e: unknown) => e,
    )
    expect(error).toBeInstanceOf(AssistantStreamError)
    expect((error as AssistantStreamError).code).toBe(502)
  })
})
