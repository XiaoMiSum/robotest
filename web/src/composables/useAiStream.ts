import { getAccessToken } from '@/services'

/** SSE 帧事件（统一帧格式 delta / done / error + 业务扩展事件，未识别事件原样透传） */
export interface AiStreamEvent {
  event: string
  data: unknown
}

export interface UseAiStreamOptions {
  /** 相对 /api 的路径，如 /project/ai/reviews/:id/summary */
  url: string
  method?: 'POST' | 'GET'
  body?: unknown
  /** 每个事件帧回调（delta/done/error 及业务扩展事件） */
  onEvent: (event: AiStreamEvent) => void
  /** 连接错误或非 2xx 响应 */
  onError?: (error: Error) => void
  /** 流正常结束（done 帧后连接关闭）或被取消 */
  onClose?: () => void
}

export interface AiStreamController {
  /** 主动取消（AbortController），触发服务端 onCompletion 取消上游调用 */
  cancel: () => void
}

const API_BASE = '/api'

/**
 * 解析单个 SSE 帧文本为事件对象。注释行（心跳 ping）与无 data 帧返回 null。
 * data 优先按 JSON 解析，失败原样返回文本。抽为纯函数以便单测。
 */
export function parseSseFrame(rawFrame: string): AiStreamEvent | null {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of rawFrame.split('\n')) {
    if (line.startsWith(':')) continue // 注释行（心跳 ping）
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return null
  const dataText = dataLines.join('\n')
  let data: unknown = dataText
  try {
    data = JSON.parse(dataText)
  } catch {
    // 非 JSON 数据原样透传
  }
  return { event: eventName, data }
}

/**
 * 基于 fetch + ReadableStream 解析 SSE 帧的公共组合式函数（3.1 统一帧格式）。
 *
 * 自动注入 Authorization 与 X-Active-Workspace / X-Active-Project 头，支持取消；
 * 对未识别事件类型原样透传给调用方处理，不丢弃、不报错。
 */
export function useAiStream(options: UseAiStreamOptions): AiStreamController {
  const controller = new AbortController()

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const workspaceId = localStorage.getItem('robotest_active_workspace')
  if (workspaceId) headers['X-Active-Workspace'] = workspaceId
  const projectId = localStorage.getItem('robotest_active_project')
  if (projectId) headers['X-Active-Project'] = projectId

  void start()

  async function start(): Promise<void> {
    try {
      const response = await fetch(`${API_BASE}${options.url}`, {
        method: options.method ?? 'POST',
        headers,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
        signal: controller.signal,
      })
      if (!response.ok || !response.body) {
        throw new Error(`AI 流式请求失败：HTTP ${response.status}`)
      }
      await readStream(response.body)
      options.onClose?.()
    } catch (error) {
      if (controller.signal.aborted) {
        // 主动取消不视为错误
        options.onClose?.()
        return
      }
      options.onError?.(error instanceof Error ? error : new Error(String(error)))
    }
  }

  async function readStream(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // SSE 帧以空行分隔
      let separatorIndex = buffer.indexOf('\n\n')
      while (separatorIndex !== -1) {
        const rawFrame = buffer.slice(0, separatorIndex)
        buffer = buffer.slice(separatorIndex + 2)
        dispatchFrame(rawFrame)
        separatorIndex = buffer.indexOf('\n\n')
      }
    }
  }

  function dispatchFrame(rawFrame: string): void {
    const parsed = parseSseFrame(rawFrame)
    if (parsed) options.onEvent(parsed)
  }

  return {
    cancel: () => controller.abort(),
  }
}
