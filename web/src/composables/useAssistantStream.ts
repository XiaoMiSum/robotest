import { useAiStream, type AiStreamController, type AiStreamEvent } from './useAiStream'
import type {
  AiConfirmRequiredEvent,
  AiDeltaEvent,
  AiDoneEvent,
  AiErrorEvent,
  AiMinderCommandsEvent,
  AiToolCallEvent,
} from '@/types'

/**
 * 全局助手 SSE 事件解析（全局智能助手详细设计 3.2）：
 * 在基础设施统一帧格式（useAiStream）之上，把 delta/tool_call/confirm_required/
 * minder_commands/done/error 解析为结构化回调；未识别事件忽略不报错。
 * 类型守卫抽为导出纯函数，供单测覆盖异常帧容错（C8）。
 */

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key]
  return typeof value === 'string' ? value : null
}

/** delta 帧：content 缺失或非字符串时按空串处理（不中断流） */
export function parseDeltaEvent(data: unknown): AiDeltaEvent {
  return { content: isRecord(data) ? (readString(data, 'content') ?? '') : '' }
}

/** tool_call 帧：toolName/summary 缺失时降级为占位（避免卡片崩溃） */
export function parseToolCallEvent(data: unknown): AiToolCallEvent {
  if (!isRecord(data)) return { toolName: 'unknown_tool', summary: '' }
  return {
    toolName: readString(data, 'toolName') ?? 'unknown_tool',
    summary: readString(data, 'summary') ?? '',
  }
}

/** confirm_required 帧：四字段任一缺失视为解析失败（调用方据此提示重试） */
export function parseConfirmRequiredEvent(data: unknown): AiConfirmRequiredEvent | null {
  if (!isRecord(data)) return null
  const confirmToken = readString(data, 'confirmToken')
  const toolName = readString(data, 'toolName')
  const preview = readString(data, 'preview')
  const expiresAt = readString(data, 'expiresAt')
  if (!confirmToken || !toolName || !expiresAt) return null
  return { confirmToken, toolName, preview: preview ?? '{}', expiresAt }
}

/** minder_commands 帧：commands 必须为数组（dslRunner 内部再逐条校验结构），documentId 缺失时返回空数组 */
export function parseMinderCommandsEvent(data: unknown): AiMinderCommandsEvent {
  if (!isRecord(data)) return { commands: [], documentId: '' }
  const commands = data.commands
  return {
    commands: Array.isArray(commands) ? (commands as AiMinderCommandsEvent['commands']) : [],
    documentId: readString(data, 'documentId') ?? '',
  }
}

/** done 帧：messageId 缺失时返回空串（调用方可不依赖） */
export function parseDoneEvent(data: unknown): AiDoneEvent {
  return { messageId: isRecord(data) ? (readString(data, 'messageId') ?? '') : '' }
}

/** error 帧：code/message 缺失时降级为通用错误 */
export function parseErrorEvent(data: unknown): AiErrorEvent {
  if (!isRecord(data)) return { code: 0, message: 'AI 调用失败' }
  const code = typeof data.code === 'number' ? data.code : 0
  return { code, message: readString(data, 'message') ?? 'AI 调用失败' }
}

export interface AssistantStreamHandlers {
  onDelta?: (content: string) => void
  onToolCall?: (event: AiToolCallEvent) => void
  onConfirmRequired?: (event: AiConfirmRequiredEvent) => void
  onMinderCommands?: (event: AiMinderCommandsEvent) => void
  onDone?: (messageId: string) => void
  onError?: (event: AiErrorEvent) => void
}

export interface UseAssistantStreamOptions {
  /** 相对 /api 的 SSE 路径（发送消息 / 确认执行共用，全局智能助手详细设计 3.2/3.3） */
  url: string
  body?: unknown
  handlers: AssistantStreamHandlers
  /** 连接错误或非 2xx 响应（如令牌超时 6011） */
  onConnectionError?: (error: Error) => void
  /** 流正常结束或被取消 */
  onClose?: () => void
}

/**
 * 全局助手对话流（useAiStream + 结构化事件解析）：
 * 发送消息与写操作确认（approve）均经此消费，两者帧格式一致（3.3.1）。
 */
export function useAssistantStream(options: UseAssistantStreamOptions): AiStreamController {
  const { handlers } = options

  function onEvent(event: AiStreamEvent): void {
    switch (event.event) {
      case 'delta':
        handlers.onDelta?.(parseDeltaEvent(event.data).content)
        break
      case 'tool_call':
        handlers.onToolCall?.(parseToolCallEvent(event.data))
        break
      case 'confirm_required': {
        const parsed = parseConfirmRequiredEvent(event.data)
        if (parsed) handlers.onConfirmRequired?.(parsed)
        break
      }
      case 'minder_commands':
        handlers.onMinderCommands?.(parseMinderCommandsEvent(event.data))
        break
      case 'done':
        handlers.onDone?.(parseDoneEvent(event.data).messageId)
        break
      case 'error':
        handlers.onError?.(parseErrorEvent(event.data))
        break
      default:
        // 未识别事件：基础设施约定原样透传，助手链路无消费方，忽略
        break
    }
  }

  return useAiStream({
    url: options.url,
    body: options.body,
    onEvent,
    onError: options.onConnectionError,
    onClose: options.onClose,
  })
}
