// ==================== 测试报告（3.5） ====================

/** 报告列表条目 */
export interface ApiReportPageItem {
  id: string
  /** 报告粒度：scene / suite */
  reportType: string
  /** 外部关联 ID（suite=任务 ID；scene=场景 ID） */
  externalId: string | null
  /** 报告名称（场景报告：场景名+时间戳；套件报告：任务名+时间戳） */
  name: string
  /** 场景报告时场景名称快照；套件报告为 null */
  sceneName: string | null
  executionMode: string
  status: string
  summary: ApiReportSummary
  environmentName: string | null
  createdAt: string
}

/** 报告汇总：场景报告 {total, passed, failed, skipped, durationMs}，套件报告为套件口径 */
export interface ApiReportSummary {
  total?: number
  passed?: number
  failed?: number
  skipped?: number
  totalScenes?: number
  passedScenes?: number
  failedScenes?: number
  totalSteps?: number
  passedSteps?: number
  failedSteps?: number
  skippedSteps?: number
  durationMs?: number
}

/** 报告详情 */
export interface ApiReportDetail {
  id: string
  reportType: string
  externalId: string | null
  name: string
  executionMode: string
  status: string
  summary: ApiReportSummary
  environmentName: string | null
  /** 按 reportType 构建的结果数据集（测试报告详细设计 2.3） */
  result: ApiReportSceneResult | ApiReportSuiteResult | null
  /** 未过期分享记录；无分享/已过期为 null（测试报告详细设计 4.2.3） */
  share: ApiReportShareInfo | null
  createdAt: string
}

/** 场景数据集（report_type='scene'，测试报告详细设计 2.3.1） */
export interface ApiReportSceneResult {
  sceneId: string | null
  sceneName: string | null
  status: string
  summary: ApiReportSummary
  environmentName: string | null
  executedAt: string | null
  steps: ApiReportStepResult[]
  preprocessors?: ApiReportStepResult[]
  postprocessors?: ApiReportStepResult[]
}

/** 套件数据集（report_type='suite'，测试报告详细设计 2.3.2），每项 scenes 即场景数据集 */
export interface ApiReportSuiteResult {
  taskId: string | null
  taskName: string | null
  source: string
  status: string
  summary: ApiReportSummary
  environmentName: string | null
  triggeredAt: string | null
  scenes: ApiReportSceneResult[]
  /** 环境级前置/后置处理器执行明细（形状同步骤元素） */
  preprocessors?: ApiReportStepResult[]
  postprocessors?: ApiReportStepResult[]
}

/** 步骤级结果（场景数据集 steps[]，测试报告详细设计 2.3.1 步骤元素） */
export interface ApiReportStepResult {
  stepId: string | null
  name: string | null
  /** 协议类型（HTTP / JDBC / Redis / WebSocket / …） */
  type?: string | null
  status: string
  durationMs?: number | null
  /** 请求快照（Real*Request 按协议序列化：HTTP 含 method/url/query/version/headers/body） */
  request?: {
    method: string | null
    url?: unknown
    query?: unknown
    version?: unknown
    headers?: Record<string, unknown> | null
    body?: unknown
    format?: string | null
  } | null
  /** 响应快照（Real*Response 按协议序列化） */
  response?: {
    status?: number | null
    headers?: Record<string, unknown> | null
    body?: unknown
    format?: string | null
  } | null
  assertions?: ApiReportAssertion[]
  extractors?: ApiReportExtractor[]
  errorMessage?: string | null
}

/** 断言明细（测试报告详细设计 2.3.1 assertions[]） */
export interface ApiReportAssertion {
  field?: string | null
  rule?: string | null
  expected?: unknown
  actual?: unknown
  status?: string | null
  message?: string | null
}

/** 提取器明细（测试报告详细设计 2.3.1 extractors[]） */
export interface ApiReportExtractor {
  refName: string
  field?: string | null
  value?: unknown
  defaultValue?: boolean
  message?: string | null
}

/** 分享链接响应 */
export interface ApiReportShareResp {
  shareUrl: string
  expiresAt: string
  /** 分享者 username */
  shareBy: string | null
}

/** 分享记录（报告详情 share 字段：未过期分享存在时返回，否则为 null） */
export interface ApiReportShareInfo {
  shareUrl: string
  expiresAt: string
  /** 分享者 username */
  shareBy: string | null
}

/** 分享访问响应（免登录） */
export interface ApiPublicReportResp {
  id: string
  reportType: string
  name: string
  environmentName: string | null
  status: string
  summary: ApiReportSummary
  /** 按 reportType 构建的结果数据集（测试报告详细设计 2.3） */
  result: ApiReportSceneResult | ApiReportSuiteResult | null
  createdAt: string
}

// ==================== 定时任务（3.6） ====================

/** 任务类型 */
export type ApiScheduleTaskType = 'scene_execute' | 'import_swagger'

/** Test 计划执行方式 */
export type ApiScheduleExecutionScope = 'all' | 'modules' | 'scenes'

/** 执行状态 */
export type ApiScheduleExecStatus = 'success' | 'failed' | 'skipped' | 'running'

/** 触发方式 */
export type ApiScheduleTriggerType = 'scheduled' | 'manual'

/** 定时任务列表项（定时任务详细设计 3.1.1） */
export interface ApiSchedulePageItem {
  id: string
  taskType: ApiScheduleTaskType
  name: string
  description: string | null
  /** 历史遗留字段（旧版绑定对象），V1.3 新任务为 null */
  boundObjectId: string | null
  boundObjectName: string | null
  executionScope: ApiScheduleExecutionScope | null
  moduleIds: string[] | null
  sceneIds: string[] | null
  openapiUrl: string | null
  environmentId: string | null
  environmentName: string | null
  cronExpression: string
  enabled: boolean
  lastExecutionStatus: ApiScheduleExecStatus | null
  lastExecutionAt: string | null
  nextExecutions: string[]
  createdAt: string
}

/** 创建/更新定时任务请求（定时任务详细设计 3.1.2/3.1.3） */
export interface ApiScheduleSaveReq {
  taskType: ApiScheduleTaskType
  name: string
  description?: string
  /** scene_execute 任务执行方式 */
  executionScope?: ApiScheduleExecutionScope
  /** 指定模块（多选），executionScope=modules 时必填 */
  moduleIds?: string[]
  /** 指定场景（多选），executionScope=scenes 时必填 */
  sceneIds?: string[]
  /** import_swagger 任务必填（OpenAPI/Swagger JSON 文件 URL） */
  openapiUrl?: string
  /** scene_execute 任务必填（目标环境） */
  environmentId?: string
  cronExpression: string
  enabled?: boolean
}

/** 启停定时任务请求（定时任务详细设计 3.1.4） */
export interface ApiScheduleToggleReq {
  enabled: boolean
}

/** Cron 校验请求（定时任务详细设计 3.1.8） */
export interface ApiScheduleValidateCronReq {
  cronExpression: string
}

/** 创建任务响应（定时任务详细设计 3.1.2） */
export interface ApiScheduleCreatedResp {
  id: string
  nextExecutionAt: string | null
}

/** 立即执行响应（定时任务详细设计 3.1.6） */
export interface ApiScheduleExecuteNowResp {
  executionId: string
  status: string
}

/** 执行记录列表项（定时任务详细设计 3.1.7） */
export interface ApiScheduleExecutionItem {
  id: string
  triggerType: ApiScheduleTriggerType
  status: ApiScheduleExecStatus
  errorMessage: string | null
  reportId: string | null
  importRecordId: string | null
  importSummary: Record<string, unknown> | null
  triggeredAt: string
  durationMs: number | null
}

/** Cron 校验结果（定时任务详细设计 3.1.8） */
export interface ApiScheduleValidateCronResp {
  valid: boolean
  description: string | null
  nextExecutions: string[] | null
}
