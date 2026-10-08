import { del, get, patch, post } from '@/services'
import type {
  AiAssistantConversation,
  AiAssistantConversationCreatePayload,
  AiAssistantConversationPageQuery,
  AiAssistantConversationUpdatePayload,
  AiAssistantExecutePayload,
  AiAssistantExecuteResult,
  AiAssistantMessage,
  AiAssistantMessagePageQuery,
  PageResult,
} from '@/types'

// ==================== 智能助手面板（详设 3.2–3.8）====================

export function fetchAiAssistantConversations(
  params: AiAssistantConversationPageQuery = {},
): Promise<PageResult<AiAssistantConversation>> {
  return get('/ai/conversations', { pageNo: 1, pageSize: 20, ...params })
}

export function createAiAssistantConversation(
  payload: AiAssistantConversationCreatePayload = {},
): Promise<AiAssistantConversation> {
  return post('/ai/conversations', payload)
}

/** 重命名 / 归档共用 PATCH：未传字段不参与更新（C11） */
export function updateAiAssistantConversation(
  conversationId: string,
  payload: AiAssistantConversationUpdatePayload,
): Promise<AiAssistantConversation> {
  return patch(`/ai/conversations/${conversationId}`, payload)
}

export function deleteAiAssistantConversation(conversationId: string): Promise<void> {
  return del(`/ai/conversations/${conversationId}`)
}

/** 消息历史倒序分页（默认 20），前端时间线需反转为正序展示 */
export function fetchAiAssistantMessages(
  conversationId: string,
  params: AiAssistantMessagePageQuery = {},
): Promise<PageResult<AiAssistantMessage>> {
  return get(`/ai/conversations/${conversationId}/messages`, { pageNo: 1, pageSize: 20, ...params })
}

/** 确认执行 / 单项重试（详设 4.2），整体 200、逐项成败 */
export function executeAiAssistantMessage(
  conversationId: string,
  messageId: string,
  payload: AiAssistantExecutePayload = {},
): Promise<AiAssistantExecuteResult> {
  return post(`/ai/conversations/${conversationId}/messages/${messageId}/execute`, payload)
}

/** 取消预览：intent 固化不变，execution 落 rejected 回执 */
export function cancelAiAssistantMessage(
  conversationId: string,
  messageId: string,
): Promise<AiAssistantExecuteResult> {
  return post(`/ai/conversations/${conversationId}/messages/${messageId}/cancel`)
}
