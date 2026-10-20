import type { BugPriority, BugSeverity, BugStatus, BugType } from './bug'

// ==================== 趋势与度量（详设 3.2 / 3.3） ====================

/** 分析分组维度：none 默认；module 按模块；severity / type 按枚举 */
export type BugAnalysisGroupBy = 'none' | 'module' | 'severity' | 'type'

/** 分析时间范围（日期为 yyyy-MM-dd 的 UTC 日历日，缺省最近 30 天） */
export interface BugAnalysisQuery {
  from?: string
  to?: string
  groupBy?: BugAnalysisGroupBy
}

/** 趋势分组序列：key=all（全部）/ 模块 id / 枚举值 */
export interface BugTrendSeries {
  key: string
  label: string
  created: number[]
  closed: number[]
  active: number[]
}

/** 趋势查询响应：axis 为区间逐日 UTC 日期 */
export interface BugTrendsResp {
  axis: string[]
  series: BugTrendSeries[]
  groupBy: BugAnalysisGroupBy
}

/** 修复时长度量：sample 为修复时间落在区间内的缺陷数 */
export interface BugFixDuration {
  avgHours: number | null
  p50Hours: number | null
  p90Hours: number | null
  sample: number | null
}

/** 分布项：severity / type 为枚举值；module 为模块名称（未指定为「未指定模块」） */
export interface BugDistItem {
  key: string
  count: number
}

/** 质量度量响应：比率与分布分母统一为区间内新增缺陷，分母为 0 时比率为 0 */
export interface BugMetricsResp {
  fixDuration: BugFixDuration | null
  reopenRate: number | null
  duplicateRate: number | null
  severityDist: BugDistItem[]
  moduleDist: BugDistItem[]
  typeDist: BugDistItem[]
}

// ==================== 录入时重复检测（详设 3.8） ====================
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

// ==================== 产物 content（详设 3.4–3.9） ====================

/** 建议值：value 为建议内容，reason 为理由 */
export interface BugSuggestionField<T> {
  value: T
  reason: string
}

/** 分类建议字段集（批量产物 content 与实时建议共用） */
export interface BugClassifySuggestions {
  bugType?: BugSuggestionField<BugType>
  severity?: BugSuggestionField<BugSeverity>
  priority?: BugSuggestionField<BugPriority>
  moduleId?: BugSuggestionField<string | null>
  keywords?: BugSuggestionField<string[]>
  /** 采纳承接支持的可选建议字段（模型清洗产出中不含，编辑后采纳可携带） */
  assigneeId?: BugSuggestionField<string | null>
}

/** 来源引用（草稿建议与摘要均附带） */
export interface BugSourceRef {
  type: string
  id: string
  title: string
  quote?: string
}

/** 分类建议产物 content（批量逐缺陷一条） */
export interface BugClassifyContent {
  bugId?: string
  suggestions: BugClassifySuggestions
  sourceRefs?: BugSourceRef[]
}

/** 分诊顺序产物 content（只读，不写入任何数据） */
export interface BugTriageContent {
  items: Array<{ bugId: string; rank: number; reason: string }>
  scope: string
}

/** 趋势摘要产物 content（只读建议，必附 citations） */
export interface BugSummaryContent {
  text: string
  citations: BugSourceRef[]
}

/** 重复组产物 content（分组确认仅留痕，不修改任何缺陷） */
export interface BugDuplicateGroupContent {
  canonicalBugId: string | null
  items: Array<{ bugId: string; similarity: number; reason: string }>
}

// ==================== 批量分类发起（详设 3.6） ====================

/** 批量分类输入：bugIds 显式圈定优先，其次 filter.statuses 按状态筛选 */
export interface BugClassifyInput {
  bugIds?: string[]
  filter?: { statuses: BugStatus[] }
}

// ==================== 新建缺陷实时建议（详设 3.5） ====================

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

/** 实时建议的分类字段集：在批量建议字段基础上补关联用例 */
export interface BugDraftSuggestFields extends BugClassifySuggestions {
  relatedCase?: BugRelatedCaseSuggestion
}

/** 实时建议响应：同步单跳调用一次返回，不产生任务、不落库 */
export interface BugDraftSuggestResp {
  title?: BugSuggestionField<string>
  reproSteps?: BugSuggestionField<string>
  suggestions: BugDraftSuggestFields
}
