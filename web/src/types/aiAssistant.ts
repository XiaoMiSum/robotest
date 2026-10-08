/** 会话状态（详设 3.2）：归档会话只读，不可发送 / 执行 */
export type AiAssistantConversationStatus = 'active' | 'archived'

/** 上下文实体引用（详设 3.4）：发送时可携带，属当前活跃工作空间 */
export interface AiAssistantEntityRef {
  entityType: string
  entityId: string
  entityTitle?: string | null
}

export interface AiAssistantConversation {
  id: string
  title: string
  status: AiAssistantConversationStatus
  /** 最近一条消息时间（无消息为 null，后端返回无时区标识的 UTC 值） */
  lastMessageAt: string | null
  messageCount: number
  /** 创建时上下文快照 { workspaceId, context, capturedAt }，不参与权限判定 */
  contextSnapshot: Record<string, unknown> | null
}

export interface AiAssistantConversationPageQuery {
  keyword?: string
  pageNo?: number
  pageSize?: number
}

export interface AiAssistantConversationCreatePayload {
  title?: string
  context?: AiAssistantEntityRef
}

export interface AiAssistantConversationUpdatePayload {
  title?: string
  status?: AiAssistantConversationStatus
}

export type AiAssistantMessageRole = 'user' | 'assistant' | 'system'

/** 消息状态（详设 3.6）：error / interrupted 为可恢复态，由前端轮询补齐到终态 */
export type AiAssistantMessageStatus = 'streaming' | 'done' | 'interrupted' | 'error'

export type AiAssistantIntentKind =
  | 'create_case'
  | 'update_case'
  | 'complete_case'
  | 'batch_tag'
  | 'create_review'
  | 'adjust_review'
  | 'view_review_progress'
  | 'create_plan'
  | 'adjust_plan'
  | 'view_plan_progress'

/** 字段级变更（详设 4.3）：新增展示完整字段，修改展示原值 → 新值 */
export interface AiAssistantIntentChange {
  field: string
  op: 'add' | 'replace'
  value: unknown
}

/** 预览时点目标作用域：展示依据，执行时以请求头比对 */
export interface AiAssistantIntentScope {
  workspaceId: string
  projectId: string
  projectName?: string
}

/** 固化意图预览（详设 4.3）：一经生成不再变化，超时需重新解析 */
export interface AiAssistantIntent {
  kind: AiAssistantIntentKind
  targetType: string
  targetTitle: string
  targetId?: string
  projectId?: string
  createCount: number
  changes: AiAssistantIntentChange[]
  scope: AiAssistantIntentScope
  /** 生成后 10 分钟时效（无时区标识的 UTC 值） */
  expiresAt: string
  /** 执行输入（params.cases 等），前端只透传展示，不解读 */
  params?: Record<string, unknown>
}

/** 来源引用（只读问答必带） */
export interface AiAssistantCitation {
  type?: string
  id?: string
  title?: string
  quote?: string
}

/** 回执单项：seq 为 retryIndexes 的重试序号基准，success=false 时携带错误码 */
export interface AiAssistantExecutionResult {
  seq?: number
  action: string
  success: boolean
  createdId?: string | null
  caseId?: string | null
  link?: string | null
  errorCode?: number | null
  errorMsg?: string | null
}

export interface AiAssistantExecution {
  status: 'executed' | 'rejected'
  executedBy?: string | null
  executedAt?: string | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  /** 跳转链接（前端相对路由，直接使用） */
  link?: string | null
  results?: AiAssistantExecutionResult[]
}

export interface AiAssistantMessage {
  id: string
  conversationId: string
  role: AiAssistantMessageRole
  /** 消息正文（Markdown），流式生成中为已落盘片段 */
  content: string | null
  attachments: AiAssistantEntityRef[] | null
  intent: AiAssistantIntent | null
  citations: AiAssistantCitation[] | null
  execution: AiAssistantExecution | null
  status: AiAssistantMessageStatus
  createdAt: string
}

export interface AiAssistantMessagePageQuery {
  pageNo?: number
  pageSize?: number
}

export interface AiAssistantSendMessagePayload {
  content: string
  attachments?: AiAssistantEntityRef[]
}

export interface AiAssistantExecutePayload {
  /** 单次重试的回执项序号（从 0 起），全量执行不传 */
  retryIndexes?: number[]
}

export interface AiAssistantExecuteResult {
  execution: AiAssistantExecution
}

/** SSE clarify 事件载荷：反问 + 快捷选项（详设 3.5） */
export interface AiAssistantClarify {
  question: string
  options: string[]
}

export interface AiAssistantSseError {
  code: number
  msg: string
}

/** SSE 事件流（delta / clarify / preview / citations / done / error，详设 3.5） */
export type AiAssistantSseEvent =
  | { type: 'delta'; data: { text: string } }
  | { type: 'clarify'; data: AiAssistantClarify }
  | { type: 'preview'; data: AiAssistantIntent }
  | { type: 'citations'; data: AiAssistantCitation[] }
  | { type: 'done'; data: { messageId: string } }
  | { type: 'error'; data: AiAssistantSseError }
