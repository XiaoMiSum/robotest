/**
 * AI 任务类型层（总册 3.6 任务 · 执行 · 提案三资源）。
 * 状态机与字段以 server 端 Constants / AiTask*DTO 为唯一事实源。
 */

/** 任务状态（Constants.AiTaskFlowStatus）：running → proposals_ready → closed；中止为 aborted */
export type AiTaskStatus = 'running' | 'proposals_ready' | 'closed' | 'aborted'

/** 执行状态（Constants.AiTaskExecutionStatus）：running →（waiting_confirmation）→ succeeded / aborted */
export type AiTaskExecutionStatus = 'running' | 'waiting_confirmation' | 'succeeded' | 'aborted'

/** 轨迹事件类型（Constants.AiTraceEventType，只增不改） */
export type AiTraceEventType =
  | 'plan'
  | 'ai_decision'
  | 'tool_call'
  | 'tool_result'
  | 'proposal_emitted'
  | 'status_change'

/**
 * 提案确认状态（Constants.AiProposalConfirmStatus + 总册 3.6.3 回填）：
 * pending / not_applicable 为提案侧状态，adopted 等为已有采纳记录回填的动作。
 */
export type AiProposalConfirmStatus =
  | 'pending'
  | 'not_applicable'
  | 'adopted'
  | 'adopted_edited'
  | 'rejected'

/** 提案采纳动作（Constants.AiProposalAction） */
export type AiProposalAction = 'adopted' | 'adopted_edited' | 'rejected'

/** 任务类型（Constants.AiTaskType，11 类单一事实源） */
export type AiTaskType =
  | 'requirement_import'
  | 'test_design_generation'
  | 'case_optimize'
  | 'mindmap_case_generation'
  | 'plan_order'
  | 'bug_classify'
  | 'bug_duplicate_scan'
  | 'bug_triage'
  | 'bug_trend_summary'
  | 'assistant_parse'
  | 'vector_reindex'

/** 终止原因（terminate_code / terminate_msg 响应投影，10 位错误码） */
export interface AiTaskTerminateReason {
  code: number | null
  msg: string | null
}

/** 预算快照（总册 2.6.2）：类型默认 + 请求下调，执行期不可调高 */
export interface AiTaskBudget {
  maxSteps?: number | null
  maxDurationSeconds?: number | null
  maxTokens?: number | null
  maxCost?: number | null
}

/** 执行消耗（详设 3.6.3 consumed） */
export interface AiTaskConsumed {
  durationMs: number | null
  tokensIn: number | null
  tokensOut: number | null
  cost: number | null
}

/** 计划步骤快照（plan，AI 自主规划的展示参考，不作为执行依据） */
export interface AiTaskPlanSnapshot {
  steps?: string[]
  [key: string]: unknown
}

/** 任务执行（AiTaskExecutionRespDTO）：步数、预算、消耗、计划与执行级终止原因 */
export interface AiTaskExecutionItem {
  executionId: string
  /** 同任务内第几次执行（首次 1，再次执行自增） */
  seq: number | null
  status: AiTaskExecutionStatus
  steps: number | null
  budget: AiTaskBudget | null
  consumed: AiTaskConsumed | null
  plan: AiTaskPlanSnapshot | null
  terminateReason: AiTaskTerminateReason | null
}

/** 文档级识别元数据（详设 3.6.3 documentMeta）：导入任务识别版本与依据引语 */
export interface AiTaskDocumentMeta {
  detectedVersion: string | null
  versionEvidence: string | null
}

/** 提案清单摘要（详设 3.6.3 proposals，不含 content） */
export interface AiTaskProposalSummary {
  /** 提案在本次执行产出中的定位键 */
  key: string
  kind: string
  title: string | null
  /** 树形提案的父提案键，非树形为 null */
  parentKey: string | null
  confirmStatus: AiProposalConfirmStatus
  /** 采纳记录 note（驳回原因），无采纳记录为 null */
  note: string | null
}

/** 提案详情（详设 3.6.3 提案详情端点）：结构化产物 + 证据链 */
export interface AiTaskProposalPayload extends AiTaskProposalSummary {
  content: Record<string, unknown>
}

/** 任务响应（AiTaskRespDTO）：列表项以 latestExecution 摘要呈现 */
export interface AiTaskItem {
  taskId: string
  /** 提交 / 再次执行响应携带本次执行 ID，其余场景为 null */
  executionId: string | null
  type: string
  /** 执行产出提案后回填的主提案标题，空串表示尚未回填（列表按类型名兜底） */
  title: string
  status: AiTaskStatus
  /** 提交 / 再次执行响应携带本次签发的预算快照，其余场景为 null */
  budget: AiTaskBudget | null
  /** 列表与详情的最新一次执行，提交响应为 null */
  latestExecution: AiTaskExecutionItem | null
  submittedBy: string | null
  /** 任务级 token 汇总（跨全部执行累加） */
  tokensIn: number | null
  tokensOut: number | null
  createdAt: string
  /** 任务级终止原因（aborted 时携带），其余状态为 null */
  terminateReason: AiTaskTerminateReason | null
}

/** 任务详情（详设 3.6.3）：列表项外另附 documentMeta、resultMeta 与最新执行的提案清单 */
export interface AiTaskDetail extends AiTaskItem {
  /** result_meta 原始值（确认面板预填数据源） */
  resultMeta: Record<string, unknown> | null
  documentMeta: AiTaskDocumentMeta | null
  /** 最新一次执行的提案清单摘要，非完成态为 null */
  proposals: AiTaskProposalSummary[] | null
}

/** 轨迹事件行（详设 3.6.4 events，只增不改） */
export interface AiTaskTraceEvent {
  seq: number
  eventType: AiTraceEventType
  summary: string | null
  detail: Record<string, unknown> | null
  createdAt: string
}

/** 轨迹查询响应：items 按 seq 升序，nextSeq 为下次请求的 afterSeq 游标 */
export interface AiTaskEventsResult {
  items: AiTaskTraceEvent[]
  nextSeq: number | null
}

/** 任务列表查询参数（type / status 精确筛选） */
export interface AiTaskPageQuery {
  type?: string
  status?: string
  pageNo?: number
  pageSize?: number
}

/** 按节点查询最近一次任务的参数（详设 3.6.3）：用例节点 AI 优化入口复用 */
export interface AiTaskLatestQuery {
  documentId: string
  nodeId: string
}

/** 轨迹查询参数（afterSeq 游标，默认 0；pageSize 默认 200 上限 500） */
export interface AiTaskEventsQuery {
  afterSeq?: number
  pageSize?: number
}

/** 提案确认项（content 仅 adopted_edited 携带编辑后内容，note 仅 rejected 携带） */
export interface AiProposalConfirmItem {
  key: string
  action: AiProposalAction
  content?: Record<string, unknown>
  note?: string
}

/** 确认面板落库目标（systemVersion null=未设置回退、空白串=显式清空，详设 4.5） */
export interface AiProposalConfirmTarget {
  systemVersion?: string
  moduleId?: string
  position?: string
  /** 承接的用例文档（用例优化），缺省回退任务入参 documentId */
  documentId?: string
  /** 承接的测试计划（执行顺序建议），缺省回退任务入参 planId */
  planId?: string
  /** 推荐轮次（执行顺序建议） */
  round?: number
}

export interface AiProposalConfirmPayload {
  items: AiProposalConfirmItem[]
  target?: AiProposalConfirmTarget
}

/** 逐项回执（整体 200，逐项成败；请求级错误才返回错误码） */
export interface AiProposalConfirmResult {
  key: string
  action: string
  success: boolean
  createdId: string | null
  errorCode: number | null
  errorMsg: string | null
}

export interface AiProposalConfirmReceipt {
  results: AiProposalConfirmResult[]
}

// ==================== 提案内容（生成链详设 3.3–3.5） ====================

/** 来源需求引用；changed 由详情回读比对（详设 4.1） */
export interface AiProposalSourceRef {
  requirementId: string
  quote: string
  changed: boolean
}

/** 用例属性（mindmap 详设 4.5 口径，steps 与 expected 成对） */
export interface AiCaseAttributes {
  priority: string
  precondition: string
  steps: string[]
  expected: string[]
  tags: string[]
}

// ==================== 生成链（生成链详设 3.2 / 3.6） ====================

/** 目标模块落位：新建顶级目录（默认）/ 挂到既有模块节点（须带 targetModuleId） */
export type AiGenerationPlacement = 'new_top_level' | 'attach'

/** 用例粒度偏好（默认 standard） */
export type AiGenerationGranularity = 'concise' | 'standard' | 'detailed'

export interface AiGenerationConfig {
  placement: AiGenerationPlacement
  targetModuleId?: string
  granularity: AiGenerationGranularity
}

/** 发起对话框的生成范围条目（列表行 / 详情条目归一） */
export interface AiGenerationScopeItem {
  id: string
  code: string
  title: string
  status: string
}

// ==================== 辅助建议（辅助功能详设 3.2–3.4） ====================

/** 辅助任务类型（脑图工具栏与计划详情各自发起，提案形态见详设 3.2–3.4） */
export type AiAssistTaskType = 'case_optimize' | 'mindmap_case_generation' | 'plan_order'

/** 来源引用（详设 3.2 / 3.3）：用例优化带 quote，执行顺序建议不带 */
export interface AiAssistSourceRef {
  id: string
  title: string
  quote: string
}

/** 节点级操作指令（辅助功能详设 3.2）：更新按 nodeId 原地改写，新增按 parentId 挂子节点 */
export type AiCaseOptimizeField = 'title' | 'priority' | 'tags'
export type AiCaseOptimizeNodeType = 'precondition' | 'step' | 'expected' | 'normal'

export interface AiCaseOptimizeUpdateOperation {
  op: 'update'
  nodeId: string
  field: AiCaseOptimizeField
  /** 归一为列表：title / priority 单元素，tags 多元素 */
  existing: string[]
  suggested: string[]
  path: string
  reason: string
}

export interface AiCaseOptimizeAddOperation {
  op: 'add'
  parentId: string
  nodeType: AiCaseOptimizeNodeType
  title: string
  path: string
  reason: string
}

export type AiCaseOptimizeOperation = AiCaseOptimizeUpdateOperation | AiCaseOptimizeAddOperation

export interface AiCaseOptimizeOperationSuggestion {
  nodeId: string
  operations: AiCaseOptimizeOperation[]
  sourceRefs: AiAssistSourceRef[]
}

/** 顺序建议条目（详设 3.4）：suggestedRank 已连续化，覆盖计划全部关联用例 */
export interface AiPlanOrderItem {
  nodeId: string
  caseTitle: string
  suggestedRank: number
  reason: string
}

/** 脑图生成用例提案（详设 3.4）：逐用例一条，含完整属性与来源引用 */
export interface AiCaseGenerationSuggestion {
  priority: string
  precondition: string[]
  steps: string[]
  expected: string[]
  tags: string[]
  sourceRefs: AiAssistSourceRef[]
}

export interface AiPlanOrderSuggestion {
  planId: string
  items: AiPlanOrderItem[]
  beforeOrder: string[]
  afterOrder: string[]
}

export type AiAssistSuggestion =
  | AiCaseOptimizeOperationSuggestion
  | AiCaseGenerationSuggestion
  | AiPlanOrderSuggestion
