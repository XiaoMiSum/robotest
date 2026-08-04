import api from '@/services'
import type { AiConversation, AiConversationListResp, AiMessage } from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言（C1：unknown + 断言）
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string): Promise<T> {
  return api.post(url) as unknown as Promise<T>
}
function postData<T>(url: string, data: Record<string, unknown>): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}

// ==================== 会话管理（全局智能助手详细设计 3.1） ====================

/** 会话列表（键集分页：cursor 不透明游标可空表示首页，size 默认 20 上限 50） */
export function fetchConversations(cursor?: string, size?: number): Promise<AiConversationListResp> {
  return get('/workspace/ai/conversations', {
    ...(cursor ? { cursor } : {}),
    ...(size ? { size } : {}),
  })
}

/** 新建空会话（title = 新会话，首条消息后自动更名） */
export function createConversation(): Promise<AiConversation> {
  return post('/workspace/ai/conversations')
}

/** 删除会话（逻辑删除，级联逻辑删除消息） */
export function deleteConversation(conversationId: string): Promise<void> {
  return api.delete(`/workspace/ai/conversations/${conversationId}`) as unknown as Promise<void>
}

/** 清空当前用户当前空间全部会话 */
export function clearConversations(): Promise<void> {
  return api.delete('/workspace/ai/conversations') as unknown as Promise<void>
}

/** 会话消息历史（按时间升序全量；role=tool 前端渲染为工具调用卡片） */
export function fetchMessages(conversationId: string): Promise<AiMessage[]> {
  return get(`/workspace/ai/conversations/${conversationId}/messages`)
}

// ==================== 写操作确认（3.3） ====================

/** 确认执行写操作（SSE 端点：approve 经 useAiStream 消费，与发送消息同链路，此处仅暴露路径约定） */
export const APPROVE_CONFIRMATION_URL = '/workspace/ai/confirmations/approve'

/** 取消写操作（即时落库 tool 消息，返回 200） */
export function cancelConfirmation(confirmToken: string): Promise<void> {
  return postData('/workspace/ai/confirmations/cancel', { confirmToken })
}
