import type { BugPriority, BugSeverity, BugStatus, BugType } from './bug'

// ==================== 录入时重复检测（详设 3.6） ====================
export interface BugDuplicateCheckReq {
  title: string
  steps?: string
  limit?: number
}

/** similarity 仅作排序信号，阈值不对外承诺；basis 为向量命中的原文分块 */
export interface BugDuplicateCheckItem {
  bugId: string
  title: string
  status: BugStatus
  similarity: number
  basis: string
}

export interface BugDuplicateCheckResp {
  list: BugDuplicateCheckItem[]
}

// ==================== 建议字段（详设 3.4） ====================

/** 建议值：value 为建议内容，reason 为理由 */
export interface BugSuggestionField<T> {
  value: T
  reason: string
}

/** 缺陷分类建议字段集（新建缺陷实时建议复用） */
export interface BugSuggestionFields {
  bugType?: BugSuggestionField<BugType>
  severity?: BugSuggestionField<BugSeverity>
  priority?: BugSuggestionField<BugPriority>
  moduleId?: BugSuggestionField<string | null>
  keywords?: BugSuggestionField<string[]>
  /** 采纳承接支持的可选建议字段（模型清洗产出中不含，仅人工补录时携带） */
  assigneeId?: BugSuggestionField<string | null>
}

// ==================== 新建缺陷实时建议（详设 3.4） ====================

/** 实时建议请求：title 必填（≤300），steps / moduleId 为已填内容上下文 */
export interface BugDraftSuggestReq {
  title: string
  steps?: string
  moduleId?: string
}

/** 关联用例建议：value 为候选用例节点 id，caseTitle 取候选用例清单原值 */
export interface BugRelatedCaseSuggestion {
  value: string
  caseTitle: string
  reason: string
}

/** 实时建议的分类字段集：在通用建议字段基础上补关联用例 */
export interface BugDraftSuggestFields extends BugSuggestionFields {
  relatedCase?: BugRelatedCaseSuggestion
}

/** 实时建议响应：同步单跳调用一次返回，不产生任务、不落库 */
export interface BugDraftSuggestResp {
  title?: BugSuggestionField<string>
  reproSteps?: BugSuggestionField<string>
  suggestions: BugDraftSuggestFields
}
