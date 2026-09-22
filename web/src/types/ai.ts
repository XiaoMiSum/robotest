import type { CaseNodeType } from './project'
import type { BugSeverity, BugPriority, BugStatus } from './bug'

/** AI 可用性状态（GET /api/workspace/ai/status） */
export interface AiStatus {
  enabled: boolean
  /** available / degraded / unavailable，enabled=false 时不返回 */
  semanticSearch?: 'available' | 'degraded' | 'unavailable'
  /** 已启用对话模型清单（脱敏，仅 id/显示名/是否默认），enabled=false 时不返回 */
  chatModels?: AiChatModelView[]
}

/** 对话模型选择器数据项（status 下发的脱敏视图） */
export interface AiChatModelView {
  id: string
  name: string
  isDefault: boolean
}

/** 密钥脱敏信息（永不回传明文） */
export interface AiApiKeyInfo {
  configured: boolean
  keySuffix: string | null
}

export interface AiConfigEmbeddingGroup {
  provider: string
  baseUrl: string
  model: string
  dimension: number | null
  apiKey: AiApiKeyInfo
  extraParams: Record<string, unknown>
}

/** AI 配置（GET /api/admin/ai/config，未配置为 null；对话模型独立于 chat-models 接口） */
export interface AiConfig {
  enabled: boolean
  embedding: AiConfigEmbeddingGroup | null
  settings: Record<string, unknown>
  updatedAt: string | null
}

/** 对话模型配置（GET /api/admin/ai/chat-models 列表项，脱敏） */
export interface AiChatModel {
  id: string
  name: string
  provider: string
  baseUrl: string
  model: string
  apiKey: AiApiKeyInfo
  extraParams: Record<string, unknown>
  enabled: boolean
  isDefault: boolean
  updatedBy: string | null
  updatedAt: string | null
}

/** 新建/更新对话模型载荷（apiKey 非空即更新，空表示保持原值） */
export interface AiChatModelSavePayload {
  name: string
  provider: string
  baseUrl: string
  model: string
  apiKey?: string | null
  extraParams?: Record<string, unknown>
}

/** 对话模型新建/编辑弹窗的表单编辑态（与 AiConfigEmbeddingGroup 同构的编辑视图） */
export interface AiModelFormState {
  name: string
  provider: string
  baseUrl: string
  model: string
  apiKey: string
  apiKeyConfigured: boolean
  keySuffix: string | null
  uniqueValues: Record<string, unknown>
  customParams: string
}

/** 保存 AI 配置的对话临时配置（供连通性测试临时透传） */
export interface AiConfigChatGroupPayload {
  provider: string
  baseUrl: string
  model: string
  apiKey?: string | null
  extraParams?: Record<string, unknown>
}

export interface AiConfigEmbeddingGroupPayload {
  provider?: string
  baseUrl?: string
  model?: string
  dimension?: number | null
  apiKey?: string | null
  extraParams?: Record<string, unknown>
}

export interface AiConfigSavePayload {
  enabled: boolean
  embedding?: AiConfigEmbeddingGroupPayload | null
  settings?: Record<string, unknown>
}

/** 连通性测试请求与结果 */
export interface AiConfigTestPayload {
  target: 'chat' | 'embedding'
  /** target=chat 且缺省临时配置时，指定已保存的对话模型（缺省则测系统默认） */
  modelId?: string | null
  chat?: AiConfigChatGroupPayload
  embedding?: AiConfigEmbeddingGroupPayload
}

export interface AiConnectivityTestResult {
  ok: boolean
  latencyMs: number | null
  detail: string | null
}

/** 系统配置项表单定义（GET /api/admin/ai/settings-schema） */
export interface AiSettingSchemaItem {
  key: string
  type: 'int' | 'number' | 'object' | 'string[]'
  label: string
  description: string
  defaultValue: unknown
  min: number | null
  max: number | null
  step: number | null
  options?: string[]
}

export interface AiSettingSchemaGroup {
  group: string
  groupLabel: string
  items: AiSettingSchemaItem[]
}

/** 供应商预设独有配置项模板 */
export interface AiProviderUniqueParam {
  key: string
  type: 'boolean' | 'number' | 'string' | 'enum'
  defaultValue: unknown
  options?: string[]
  label: string
  description: string
}

/** 供应商预设注册表元数据 */
export interface AiProviderPreset {
  key: string
  name: string
  scopes: string[]
  defaultBaseUrl: Record<string, string>
  modelHints: Record<string, string[]>
  uniqueParams: Record<string, AiProviderUniqueParam[]>
}

/** 智能体列表项 */
export interface AiAgent {
  functionType: string
  name: string
  customized: boolean
  formatEditable: boolean
  updatedBy: string | null
  updatedAt: string | null
}

/** 智能体详情（当前生效段，内容全部来自数据库） */
export interface AiAgentDetail {
  functionType: string
  name: string
  customized: boolean
  formatEditable: boolean
  roleInstruction: string
  formatConstraint: string
}

export interface AiAgentSavePayload {
  roleInstruction: string
  formatEditable: boolean
  formatConstraint?: string | null
}

/** 调用量统计 */
export interface AiStatisticsItem {
  key: string
  calls: number
  tokens: number
  avgDurationMs: number
  failed: number
}

export interface AiStatistics {
  totalCalls: number
  totalTokens: number
  failedCalls: number
  items: AiStatisticsItem[]
}

/** AI 异步任务状态 */
export interface AiTask {
  id: string
  type: string
  targetId: string | null
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
  progress: number
  result: Record<string, unknown> | null
  errorMessage: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** AI 生成用例树节点（生成子树/补全步骤/文本导入共用输出结构，详细设计 2.2） */
export interface AiGeneratedNode {
  type: CaseNodeType
  title: string
  /** 仅 case 节点携带，P0-P3 */
  priority?: string | null
  children?: AiGeneratedNode[]
}

/** AI 生成用例子树 done 帧载荷 */
export interface AiCaseGenerateResult {
  nodes: AiGeneratedNode[]
  /** 截断等宽容规整提示 */
  warnings: string[]
}

/** AI 评审摘要按文档不通过分布 */
export interface AiReviewSummaryFailByDocument {
  documentName: string
  failCount: number
}

/** AI 评审摘要统计（SQL 精确计算，随 statistics 帧即时返回） */
export interface AiReviewSummaryStats {
  totalCases: number
  passCount: number
  failCount: number
  pendingCount: number
  passRate: number
  failByDocument: AiReviewSummaryFailByDocument[]
}

/** AI 评审摘要（GET 查询与 done 帧共用；generatedAt 仅持久化查询返回） */
export interface AiReviewSummary {
  statistics: AiReviewSummaryStats
  summaryMarkdown: string
  generatedAt?: string
}

/** AI 评审结论判定（06 §5.2：无关联用例→INCONCLUSIVE，有 FAIL→FAIL，有 PENDING→INCONCLUSIVE，否则 PASS；后端确定性计算，LLM 不产出） */
export type AiReviewConclusionVerdict = 'PASS' | 'FAIL' | 'INCONCLUSIVE'

/** AI 评审结论（GET 查询 task.result 与 done 帧共用；generatedAt 仅持久化查询返回） */
export interface AiReviewConclusion {
  verdict: AiReviewConclusionVerdict
  reason: string
  keyFindings: string[]
  statistics: AiReviewSummaryStats
  generatedAt?: string
}

/** AI 一键检查建议维度（US-AI-005，详细设计 2.2.1 枚举） */
export type AiReviewCheckDimension =
  | 'missing_precondition'
  | 'vague_step'
  | 'missing_expected'
  | 'priority_conflict'

/** AI 一键检查单条建议（result.items 元素，2.2.1） */
export interface AiReviewCheckItem {
  snapshotNodeId: string
  dimension: AiReviewCheckDimension
  suggestion: string
}

/** AI 一键检查结果（review_check 任务 result；分批累计写入，running/cancelled 亦含部分结果） */
export interface AiReviewCheckResult {
  checkedCaseCount: number
  totalCaseCount: number
  skippedBatches: number
  items: AiReviewCheckItem[]
}

/** AI 遗漏测试点分析单条（US-AI-007，详细设计 3.3） */
export interface AiMissingPoint {
  title: string
  description: string
  /** 建议归属模块路径（「转用例生成」用于目标文档默认预选） */
  suggestedModulePath: string | null
  /** 关联候选用例标题（幻觉过滤后仅含候选清单中真实存在的标题） */
  relatedCaseTitles: string[]
}

/** AI 遗漏测试点分析响应（3.3，同步长调用） */
export interface AiMissingPointResult {
  /** 语义降级：true 时顶部提示「当前为关键词匹配结果」 */
  semanticDegraded: boolean
  points: AiMissingPoint[]
}

/** AI 用例规划智能推荐单条（US-AI-018，详细设计 3.5） */
export interface AiCasePlanRecommendItem {
  caseNodeId: string
  title: string
  /** 所属模块路径（如 登录 > 密码登录），清单展示用 */
  modulePath: string
  /** 命中方式：semantic（语义匹配；降级态关键词匹配亦归此值） */
  matchType: 'semantic'
  /** 推荐度（score 降序，结果上限 50；降级模式 0.6 仅作展示排序） */
  score: number
  /** 一句话推荐理由；生成失败时整体置空，不影响清单可用性（4.5） */
  reason: string | null
}

/** AI 用例规划智能推荐响应（3.5，同步长调用） */
export interface AiCasePlanRecommendResult {
  /** 语义降级：true 时顶部提示「当前为关键词匹配结果」 */
  semanticDegraded: boolean
  items: AiCasePlanRecommendItem[]
}

/** AI 执行顺序推荐单条因子（US-AI-017，详细设计 2.2.3 / 4.4） */
export interface AiPlanOrderFactors {
  /** bug.related_case_id = 快照节点.original_node_id 的未删除缺陷数 */
  relatedBugCount: number
  /** P0=1.0 / P1=0.75 / P2=0.5 / P3=0.25 / 无=0.25 */
  priorityWeight: number
  /** 所属文档对应模块（含子孙模块）缺陷数 ÷ 现势 case 节点数 */
  moduleBugDensity: number
}

/** AI 执行顺序推荐单条（items 元素，按 score 降序排列，order 为推荐序号） */
export interface AiPlanOrderRecommendItem {
  snapshotNodeId: string
  order: number
  score: number
  factors: AiPlanOrderFactors
  /** 按需生成后回填（3.4.3，缓存复用），未生成时为 null */
  reason: string | null
}

/** AI 执行顺序推荐结果（type=plan_order_recommend，target=计划 ID，2.2.3） */
export interface AiPlanOrderRecommendResult {
  /** 计算时刻 test_plan.snapshot_synced_at 的列值（含 NULL），用于失效判定 */
  planSyncedAt: string | null
  /** 本次计算实际使用的权重（settings 键 planOrder.weights） */
  weights: Record<string, number>
  items: AiPlanOrderRecommendItem[]
}

/** 执行顺序推荐计算响应（3.4.1）：同步计算立即返回任务标识与推荐结果 */
export interface AiPlanOrderComputeResp {
  taskId: string
  result: AiPlanOrderRecommendResult
}

/** 执行顺序推荐结果查询响应（3.4.2） */
export interface AiPlanOrderQueryResp {
  /** true 表示计划快照在计算后重新同步过，结果已失效需重算 */
  stale: boolean
  result: AiPlanOrderRecommendResult | null
}

/** 执行顺序推荐理由响应（3.4.3） */
export interface AiPlanOrderReasonResp {
  reason: string | null
}

/** 需求池条目状态：active 参与 AI 消费，archived 归档只读（需求规格 3.2.4） */
export type RequirementStatus = 'active' | 'archived'

/** 需求池条目列表项（US-AI-004） */
export interface RequirementPoolItem {
  id: string
  title: string
  sourceUrl: string | null
  status: RequirementStatus
  createdBy: string
  creatorName: string | null
  updatedAt: string
  /** AI 拆分入库标识（US-AI-019，仅展示 AI 徽标，不影响业务规则） */
  aiGenerated: boolean
}

/** 需求池条目详情 */
export interface RequirementDetail {
  id: string
  title: string
  content: string
  sourceUrl: string | null
  status: RequirementStatus
  createdBy: string
  creatorName: string | null
  updatedBy: string
  createdAt: string
  updatedAt: string
  /** AI 拆分入库标识（US-AI-019，仅展示 AI 徽标，不影响业务规则） */
  aiGenerated: boolean
}

/** 需求池条目摘要（文档关联查询与条目选取器共用） */
export interface RequirementSummary {
  id: string
  title: string
}

// --- 需求文档 AI 拆分（US-AI-019，详细设计 3.2.3） ---

/** AI 拆分预览需求点（done 帧 items 元素） */
export interface AiRequirementSplitItem {
  title: string
  content: string
}

/** AI 拆分模块分组（done 帧 modules 元素） */
export interface AiRequirementSplitModule {
  module: string
  items: AiRequirementSplitItem[]
}

/** AI 拆分 done 帧载荷：模块分组 + 警告（纯预览，不落库） */
export interface AiRequirementSplitResult {
  modules: AiRequirementSplitModule[]
  warnings: string[]
}

// --- 缺陷 AI 能力（US-AI-008/009/010，详细设计 3.1–3.3） ---

/** 缺陷表单智能建议响应（3.1） */
export interface AiBugSuggestion {
  optimizedTitle: string
  severity: BugSeverity
  priority: BugPriority
  reason: string
}

/** 语义查重单条命中（3.2） */
export interface AiBugDedupItem {
  bugId: string
  title: string
  status: BugStatus
  assigneeName: string | null
  /** 降级模式下为 null，前端据此不展示相似度徽标（3.2） */
  similarity: number | null
}

/** 语义查重响应（3.2） */
export interface AiBugDedupResult {
  semanticDegraded: boolean
  items: AiBugDedupItem[]
}

/** 聚类模块分布条目（2.3；moduleId 为 null 表示未指定模块） */
export interface AiBugClusterModule {
  moduleId: string | null
  moduleName: string
  count: number
}

/** 聚类单簇内缺陷（2.3，携带标题/严重度/状态供明细直接渲染，无需再查详情） */
export interface AiBugClusterBug {
  id: string
  title: string
  severity: BugSeverity
  status: BugStatus
}

/** 聚类单簇（2.3；labeled=false 表示 LLM 归纳失败/超限，label 为占位「未命名主题 N」，前端需明示标签生成失败） */
export interface AiBugCluster {
  label: string
  labeled: boolean
  rootCause: string | null
  bugs: AiBugClusterBug[]
  severityDist: Record<BugSeverity, number>
  moduleDist: AiBugClusterModule[]
}

/** 聚类结果快照（bug_clustering 任务 result，2.3；unclustered 仅含 ID 无标题，仅计数展示） */
export interface AiBugClusterSnapshot {
  generatedAt: string
  bugCount: number
  clusters: AiBugCluster[]
  unclustered: string[]
}

// ==================== 全局智能助手（ChatBot，US-AI-011~014） ====================

/** 助手会话列表项（后端 AiConversationItemRespDTO，全局智能助手详细设计 3.1） */
export interface AiConversation {
  id: string
  title: string
  lastActiveAt: string
}

/** 助手会话列表响应（键集分页，nextCursor 为空表示无更多，3.1） */
export interface AiConversationListResp {
  items: AiConversation[]
  nextCursor: string | null
}

/** 助手消息角色（3.1；tool 消息前端渲染为工具调用卡片） */
export type AiMessageRole = 'user' | 'assistant' | 'tool'

/** assistant 消息内单条工具调用载荷（AiMessageRespDTO.toolCalls 元素） */
export interface AiMessageToolCall {
  name: string
  /** OpenAI 工具参数 JSON 字符串 */
  arguments: string
  callId: string
}

/** 助手消息（AiMessageRespDTO，3.1；role=tool 渲染为工具调用卡片） */
export interface AiMessage {
  id: string
  role: AiMessageRole
  content: string | null
  /** assistant 消息发起的工具调用载荷，非工具消息为空 */
  toolCalls: AiMessageToolCall[] | null
  /** tool 消息对应的调用 ID */
  toolCallId: string | null
  createdAt: string
}

/** 页面上下文桥（4.4）：脑图页注入 documentId/selectedNodeId，项目内注入 projectId */
export interface AiPageContext {
  projectId?: string | null
  documentId?: string | null
  selectedNodeId?: string | null
}

/** 发送消息请求体（AiAssistantSendReqDTO，3.2） */
export interface AiAssistantSendPayload {
  content: string
  pageContext?: AiPageContext | null
  modelId?: string | null
}

/** 写操作确认/取消请求体（AiConfirmReqDTO，3.3；令牌经请求体传递不入 URL） */
export interface AiConfirmPayload {
  confirmToken: string
}

// ---- 助手 SSE 扩展事件载荷（基础设施统一帧格式，3.2） ----

/** delta 帧：回复文本增量 */
export interface AiDeltaEvent {
  content: string
}

/** tool_call 帧：只读工具执行通知（前端渲染过程卡片） */
export interface AiToolCallEvent {
  toolName: string
  summary: string
}

/** confirm_required 帧：写操作确认请求，本轮 SSE 随即以 done 结束 */
export interface AiConfirmRequiredEvent {
  confirmToken: string
  toolName: string
  /** 写工具参数 JSON（arguments），前端渲染为操作明细 */
  preview: string
  expiresAt: string
}

/** minder_commands 帧：对话式编辑翻译结果（DSL），交前端预览执行 */
export interface AiMinderCommandsEvent {
  commands: AiMinderCommand[]
  documentId: string
}

/** done 帧：本轮回复完成 */
export interface AiDoneEvent {
  messageId: string
}

/** error 帧：失败 */
export interface AiErrorEvent {
  code: number
  message: string
}

// ---- 脑图操作指令集（DSL，智能用例生成与脑图智能编辑详细设计 4.4） ----

/** 用例优先级（P0-P3，仅对 case 生效） */
export type AiPriority = 'P0' | 'P1' | 'P2' | 'P3'

/** selector 各条件为 AND 关系；@selected 为标题引用的保留值，仅允许出现在 subtreeRootTitle/targetParentTitle */
export interface AiMinderSelector {
  types?: CaseNodeType[]
  priorities?: AiPriority[]
  /** 标题包含（忽略大小写） */
  keyword?: string
  /** 以标题引用限定子树范围（默认全文档），预览阶段按标题精确匹配解析 */
  subtreeRootTitle?: string
  /** 按 AI 标识筛选 */
  aiGenerated?: boolean
}

export type AiMinderAction =
  | { type: 'mark_type'; params: { nodeType: CaseNodeType } }
  | { type: 'mark_priority'; params: { priority: AiPriority } }
  | { type: 'highlight'; params: Record<string, never> }
  | { type: 'move'; params: { targetParentTitle: string } }
  | { type: 'add_child'; params: { nodes: AiGeneratedNode[] } }

/** 脑图操作指令（4.4.1）；commands 数组按序执行，上限 10 条 */
export interface AiMinderCommand {
  selector: AiMinderSelector
  action: AiMinderAction
}
