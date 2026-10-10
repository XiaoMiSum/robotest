/** 任务状态（详设 3.6.1）：succeeded / failed / cancelled 为终态，终态不再轮询 */
export type AiTaskStatus = 'pending' | 'running' | 'succeeded' | 'failed' | 'cancelled'

/** 任务类型（详设 3.6.1 枚举）：批次一仅注册 requirement_split / requirement_import 处理器 */
export type AiTaskType =
  | 'requirement_import'
  | 'requirement_split'
  | 'test_design_generation'
  | 'review_selection'
  | 'plan_selection'
  | 'coverage_analysis'
  | 'impact_analysis'
  | 'case_complete'
  | 'case_priority'
  | 'plan_order'
  | 'bug_classify'
  | 'bug_duplicate_scan'
  | 'bug_triage'
  | 'bug_trend_summary'
  | 'assistant_parse'
  | 'vector_reindex'

/** 失败原因（仅 status = failed 时非 null） */
export interface AiTaskError {
  code: number | null
  msg: string | null
}

/** 产物确认动作（详设 3.6.5） */
export type AiArtifactAction = 'adopted' | 'adopted_edited' | 'rejected'

/** 产物确认状态（详设 3.6.5） */
export type AiArtifactConfirmStatus = 'pending' | 'adopted' | 'adopted_edited' | 'rejected'

/** 产物清单摘要（列表 / 详情携带，不含产物内容） */
export interface AiArtifactSummary {
  key: string
  kind: string
  title: string | null
  parentKey: string | null
  confirmStatus: AiArtifactConfirmStatus
}

/** 任务响应（列表项无 result / artifacts，详情附 artifacts 摘要） */
export interface AiTaskItem {
  taskId: string
  type: string
  status: AiTaskStatus
  progress: number | null
  phase: string | null
  submittedBy: string | null
  retryOfTaskId: string | null
  tokensIn: number | null
  tokensOut: number | null
  createdAt: string
  error: AiTaskError | null
}

/** 文档级识别元数据（详设 3.6.3）：导入任务识别版本与依据引语，识别不到字段为 null */
export interface AiTaskDocumentMeta {
  detectedVersion: string | null
  versionEvidence: string | null
}

/** 任务详情（succeeded 时附产物清单摘要与 documentMeta；不返回 input / result 明细） */
export interface AiTaskDetail extends AiTaskItem {
  result: Record<string, unknown> | null
  documentMeta: AiTaskDocumentMeta | null
  artifacts: AiArtifactSummary[] | null
}

/** 任务列表查询参数（type / status 精确筛选） */
export interface AiTaskPageQuery {
  type?: string
  status?: string
  pageNo?: number
  pageSize?: number
}

/** 产物确认项（content 仅 adopted_edited 携带编辑后内容，note 仅 rejected 携带） */
export interface AiArtifactConfirmItem {
  key: string
  action: AiArtifactAction
  content?: Record<string, unknown>
  note?: string
}

/** 确认面板落库目标（systemVersion null=未设置回退、空白串=显式清空，详设 4.5） */
export interface AiArtifactConfirmTarget {
  systemVersion?: string
  moduleId?: string
  position?: string
  /** 既有评审 / 计划创建请求整包（圈选确认承接，生成链详设 3.6） */
  createParams?: Record<string, unknown>
  /** 辅助功能：承接的用例文档（补全 / 级别推荐），缺省回退任务入参 documentId */
  documentId?: string
  /** 辅助功能：补充节点落位 sibling / child，缺省 sibling */
  extraNodePosition?: string
  /** 辅助功能：承接的测试计划（执行顺序建议），缺省回退任务入参 planId */
  planId?: string
  /** 辅助功能：推荐轮次（执行顺序建议） */
  round?: number
}

export interface AiArtifactConfirmPayload {
  items: AiArtifactConfirmItem[]
  target?: AiArtifactConfirmTarget
}

/** 逐项回执（整体 200，逐项成败；请求级错误才返回错误码） */
export interface AiArtifactConfirmResult {
  key: string
  action: string
  success: boolean
  createdId: string | null
  errorCode: number | null
  errorMsg: string | null
}

export interface AiArtifactConfirmReceipt {
  results: AiArtifactConfirmResult[]
}

// ==================== 生成链产物内容（生成链详设 3.3–3.5） ====================

/** 来源需求引用；changed 由详情回读比对（详设 4.1） */
export interface AiArtifactSourceRef {
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

/** 圈选发起配置：计划按轮次分组，评审为单轮（生成链详设 3.1 输入裁决） */
export interface AiSelectionConfig {
  requirementIds: string[]
  roundCount?: number
}

// ==================== 辅助建议（辅助功能详设 3.2–3.4） ====================

/** 辅助任务类型（脑图工具栏与计划详情各自发起，产物形态见详设 3.2–3.4） */
export type AiAssistTaskType = 'case_complete' | 'case_priority' | 'plan_order'

/** 补全字段键（详设 3.2 产物形态，不建议改写 priority） */
export type AiAssistFieldName = 'precondition' | 'steps' | 'expected' | 'tags'

/** 现有值与建议值成对；字符串字段（precondition）归一为单元素列表便于统一比对渲染 */
export interface AiAssistFieldPair {
  existing: string[]
  suggested: string[]
}

/** 补充节点（详设 3.2）：仅预览，采纳后才落库 */
export interface AiAssistExtraNode {
  title: string
  /** true=用例节点，false=结构节点；预览用不同图标区分 */
  isTestCase: boolean
}

/** 来源引用（详设 3.2 / 3.3）：补全带 quote，级别推荐不带 */
export interface AiAssistSourceRef {
  id: string
  title: string
  quote: string
}

export interface AiCaseCompleteSuggestion {
  nodeId: string
  fields: Record<AiAssistFieldName, AiAssistFieldPair>
  extraNodes: AiAssistExtraNode[]
  sourceRefs: AiAssistSourceRef[]
}

export interface AiCasePrioritySuggestion {
  nodeId: string
  current: string
  suggested: string
  reason: string
  sourceRefs: AiAssistSourceRef[]
}

/** 顺序建议条目（详设 3.4）：suggestedRank 已连续化，覆盖计划全部关联用例 */
export interface AiPlanOrderItem {
  nodeId: string
  caseTitle: string
  suggestedRank: number
  reason: string
}

export interface AiPlanOrderSuggestion {
  planId: string
  items: AiPlanOrderItem[]
  beforeOrder: string[]
  afterOrder: string[]
}

export type AiAssistSuggestion =
  | AiCaseCompleteSuggestion
  | AiCasePrioritySuggestion
  | AiPlanOrderSuggestion
