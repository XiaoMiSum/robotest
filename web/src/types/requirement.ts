/** 需求状态（详设 4.2 状态机：draft → confirmed → changed → archived，unarchive 一律回 draft） */
export type RequirementStatus = 'draft' | 'confirmed' | 'changed' | 'archived'

/** 覆盖状态：追溯侧未接入时恒为 null，前端展示「—」 */
export type RequirementCoverageStatus = 'covered' | 'partial' | 'uncovered' | 'pending'

/** 优先级取值仅三档，非法值由服务端返回 1000018010 */
export type RequirementPriority = 'high' | 'medium' | 'low'

/** 需求来源：manual 手工 / import 导入（C6 导入落地后启用） */
export type RequirementSource = 'manual' | 'import'

/** 变更记录类型（详设 3.10） */
export type RequirementChangeType = 'title' | 'description' | 'module' | 'status' | 'attribute'

/** 需求列表项（详设 3.2，不含 description 全文） */
export interface RequirementListItem {
  id: string
  code: string
  title: string
  moduleId: string | null
  moduleName: string | null
  systemVersion: string | null
  status: RequirementStatus
  coverageStatus: RequirementCoverageStatus | null
  priority: RequirementPriority | null
  ownerId: string | null
  ownerName: string | null
  source: RequirementSource
  updatedAt: string
}

/** 来源附件（source = import 时；文件管理模块落地前恒为 null） */
export interface RequirementSourceFile {
  fileId: string
  name: string
}

/** 需求详情（详设 3.4–3.7 统一响应） */
export interface RequirementDetail {
  id: string
  code: string
  title: string
  description: string | null
  moduleId: string | null
  moduleName: string | null
  systemVersion: string | null
  status: RequirementStatus
  priority: RequirementPriority | null
  ownerId: string | null
  ownerName: string | null
  tags: string[] | null
  source: RequirementSource
  sourceFile: RequirementSourceFile | null
  confirmedAt: string | null
  coverageStatus: RequirementCoverageStatus | null
  createdAt: string
  updatedAt: string
}

/** 变更记录（summary 为单键 Map，键即变更字段） */
export interface RequirementChangeLog {
  id: string
  changeType: RequirementChangeType
  operatorId: string
  operatorName: string | null
  beforeSummary: Record<string, unknown> | null
  afterSummary: Record<string, unknown> | null
  createdAt: string
}

/** AI 拆分提交回执（详设 3.9） */
export interface RequirementSplitSubmit {
  taskId: string
  splitRecordId: string
  status: string
}

/** 业务端 AI 可用性（详设 3.2，GET /ai/status） */
export interface AiStatus {
  enabled: boolean
  modelReady: boolean
  available: boolean
}

/** 需求列表查询（status / moduleIds 为逗号分隔多选） */
export interface RequirementPageQuery {
  status?: string
  moduleIds?: string
  ownerId?: string
  systemVersion?: string
  coverage?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}

/** 创建需求（详设 3.3） */
export interface RequirementCreatePayload {
  title: string
  description?: string
  moduleId?: string
  systemVersion?: string
  priority?: RequirementPriority
  ownerId?: string
  tags?: string[]
}

/** 需求属性部分更新（详设 3.5，C11：只传变化字段；不接受 status） */
export interface RequirementUpdatePayload {
  title?: string
  description?: string
  moduleId?: string
  systemVersion?: string
  priority?: RequirementPriority
  ownerId?: string
  tags?: string[]
}
