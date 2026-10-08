import { getContextHeaders, getAccessToken } from '@/services'
import type { AiAssistantSseEvent, AiAssistantSendMessagePayload } from '@/types'

/** SSE 非 2xx / 非流响应（如 1000018259 归档会话、1000018260 正文校验失败） */
export class AssistantStreamError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'AssistantStreamError'
    this.code = code
  }
}

/** 流中断（网络断开 / 连接被重置）：后端任务继续执行，转消息轮询补齐 */
export class AssistantStreamInterruptedError extends Error {
  constructor(message = '连接中断') {
    super(message)
    this.name = 'AssistantStreamInterruptedError'
  }
}

const EVENT_NAMES = new Set([
  'delta',
  'clarify',
  'preview',
  'citations',
  'done',
  'error',
])

/**
 * 解析单个 SSE 帧（event/data 行，冒号开头为心跳注释行）。
 * 未知事件名返回 null——后端新增事件时前端不崩溃，仅忽略。
 */
export function parseSseFrame(frame: string): AiAssistantSseEvent | null {
  let name = ''
  const dataLines: string[] = []
  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    const sep = line.indexOf(':')
    const field = sep === -1 ? line : line.slice(0, sep)
    let value = sep === -1 ? '' : line.slice(sep + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') name = value
    else if (field === 'data') dataLines.push(value)
  }
  if (!EVENT_NAMES.has(name) || dataLines.length === 0) return null
  try {
    // 后端仅序列化载荷本体，事件名需补进 type 才是前端事件联合类型
    const data = JSON.parse(dataLines.join('\n')) as unknown
    return { type: name, data } as AiAssistantSseEvent
  } catch {
    // 畸形数据按未知事件忽略，避免单帧失败中断整条流
    return null
  }
}

/** 增量帧解析器：跨 chunk 缓冲，按空行分帧（SSE 规范，兼容 \r\n） */
export function createSseFrameParser(onEvent: (event: AiAssistantSseEvent) => void): {
  push: (chunk: string) => void
  flush: () => void
} {
  let buffer = ''
  const emitBlocks = () => {
    let index: number
    for (;;) {
      const match = /\r?\n\r?\n/.exec(buffer)
      index = match ? match.index : -1
      if (index < 0) return
      const frame = buffer.slice(0, index)
      buffer = buffer.slice(index + (match?.[0].length ?? 0))
      const event = parseSseFrame(frame)
      if (event) onEvent(event)
    }
  }
  return {
    push: (chunk: string) => {
      buffer += chunk
      emitBlocks()
    },
    flush: () => {
      if (!buffer.trim()) return
      const event = parseSseFrame(buffer)
      buffer = ''
      if (event) onEvent(event)
    },
  }
}

function readResultError(body: string): AssistantStreamError | null {
  try {
    const parsed = JSON.parse(body) as { code?: number; msg?: string }
    if (typeof parsed.code === 'number') {
      return new AssistantStreamError(parsed.code, parsed.msg || '请求失败')
    }
  } catch {
    // 非 Result 包装（如网关 HTML），走下方兜底
  }
  return null
}

/**
 * 发送消息并以 fetch + ReadableStream 消费 SSE（详设 3.5）。
 *
 * 注意：fetch 路径不经过 axios 拦截器，401 不会触发刷新链，
 * 以 AssistantStreamError(code=401) 抛出，由调用方提示重新登录。
 */
export async function streamAssistantMessage(
  conversationId: string,
  payload: AiAssistantSendMessagePayload,
  onEvent: (event: AiAssistantSseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const path = `/ai/conversations/${conversationId}/messages`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    ...getContextHeaders({ url: path, method: 'post' }),
  }
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal,
    })
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error
    throw new AssistantStreamInterruptedError()
  }

  const contentType = response.headers.get('Content-Type') ?? ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const body = await response.text()
    throw (
      readResultError(body) ??
      new AssistantStreamError(response.status, `请求失败（HTTP ${response.status}）`)
    )
  }

  const reader = response.body?.getReader()
  if (!reader) throw new AssistantStreamInterruptedError()

  const decoder = new TextDecoder()
  const parser = createSseFrameParser(onEvent)
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.flush()
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error
    // 连接中断：后端解析任务继续执行并完整落盘，由调用方转消息轮询补齐
    throw new AssistantStreamInterruptedError()
  } finally {
    reader.releaseLock()
  }
}
