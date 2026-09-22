import { get, post, del } from '@/services'
import type { AiConversation, AiConversationListResp, AiMessage } from '@/types'

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
  return del(`/workspace/ai/conversations/${conversationId}`)
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
  return post('/workspace/ai/confirmations/cancel', { confirmToken })
}
