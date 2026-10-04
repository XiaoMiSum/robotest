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

/** 任务详情（succeeded 时附产物清单摘要；不返回 input / result 明细） */
export interface AiTaskDetail extends AiTaskItem {
  result: Record<string, unknown> | null
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
