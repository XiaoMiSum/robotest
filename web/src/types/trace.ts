import type { RequirementCoverageStatus, RequirementStatus } from './requirement'

// ==================== 追溯矩阵（详设 3.2–3.10） ====================

/** 节点类型：矩阵列、链路树与人工建边的目标枚举来源 */
export type TraceNodeType =
  | 'requirement'
  | 'module'
  | 'mindmap_document'
  | 'test_case'
  | 'test_review'
  | 'test_plan'

export type TraceEdgeType = 'derivation' | 'case_snapshot'

/** ai_created 待确认 / confirmed 有效 / conflict 冲突 / stale 版本失效 / detached 已断开 */
export type TraceEdgeStatus = 'ai_created' | 'confirmed' | 'conflict' | 'stale' | 'detached'

/** 修正动作：confirm 确认 / reattach 改挂 / detach 断开 / restore 恢复 */
export type TraceEdgeAction = 'confirm' | 'reattach' | 'detach' | 'restore'

export type TraceGapType =
  | 'uncovered_requirement'
  | 'orphan_case'
  | 'unreviewed_case'
  | 'unscheduled_case'

/** 缺口引导动作：generate 发起生成 / review 发起评审 / schedule 加入计划 */
export type TraceGapAction = 'generate' | 'review' | 'schedule'

export type TraceImpactDisposition = 'pending' | 'regenerate' | 're_review' | 'no_impact'

/** 追溯节点引用（3.4 / 3.10）：title 解析不到时为占位，version 无版本概念时为 null */
export interface TraceNodeRef {
  type: TraceNodeType
  id: string
  title: string
  version: string | null
}

/** 矩阵行的按目标类型有效边计数（排除 detached） */
export interface TraceEdgeCounts {
  module: number
  document: number
  testCase: number
  review: number
  plan: number
}

export interface TraceMatrixRow {
  requirementId: string
  code: string
  title: string
  status: RequirementStatus
  coverageStatus: RequirementCoverageStatus
  edgeCounts: TraceEdgeCounts
  /** stale 边数 + 查询侧实时版本比对命中的边数 */
  staleCount: number
  conflictCount: number
}

export interface TraceMatrixQuery {
  pageNo?: number
  pageSize?: number
  requirementStatus?: string
  coverage?: string
  keyword?: string
}

export interface TraceChainQuery {
  sourceType: TraceNodeType
  sourceId: string
  /** down 向下游（默认）/ up 向上游回溯 / both */
  direction?: 'down' | 'up' | 'both'
}

export interface TraceChainNode {
  id: string
  type: TraceNodeType
  title: string
  version: string | null
  /** 相对起点的层数，起点为 0 */
  level: number
}

export interface TraceChainEdge {
  edgeId: string
  sourceId: string
  targetId: string
  edgeType: TraceEdgeType
  status: TraceEdgeStatus
  targetVersion: string | null
  /** 目标无版本概念时为 null，不参与判断 */
  versionMatched: boolean | null
}

/** 链路视图（3.3）：节点与边分离，前端按边渲染连线 */
export interface TraceChain {
  root: TraceNodeRef
  nodes: TraceChainNode[]
  edges: TraceChainEdge[]
  /** 超出深度（6 层）或节点数（2000）上限 */
  hasMore: boolean
}

export interface TraceEdge {
  edgeId: string
  edgeType: TraceEdgeType
  source: TraceNodeRef
  target: TraceNodeRef
  targetVersion: string | null
  status: TraceEdgeStatus
  establishedBy: 'ai' | 'manual'
  confirmedBy: string | null
  confirmedAt: string | null
}

export interface TraceEdgeQuery {
  pageNo?: number
  pageSize?: number
  edgeType?: TraceEdgeType
  status?: TraceEdgeStatus
  sourceType?: TraceNodeType
  sourceId?: string
  targetType?: TraceNodeType
  targetId?: string
}

export interface TraceEdgeCreatePayload {
  edgeType: TraceEdgeType
  sourceType: TraceNodeType
  sourceId: string
  targetType: TraceNodeType
  targetId: string
  targetVersion?: string
}

export interface TraceEdgePatchPayload {
  action: TraceEdgeAction
  targetType?: TraceNodeType
  targetId?: string
  targetVersion?: string
  /** detach 必填，留痕随审计落库 */
  reason?: string
}

/** 覆盖结论（3.7 查询 / 3.8 修正同构）：evidence 为 AI 判定依据 */
export interface TraceCoverage {
  requirementId: string
  coverageStatus: Exclude<RequirementCoverageStatus, 'pending'>
  evidence: Record<string, unknown> | null
  aiAnalyzedAt: string | null
  /** 非空即人工判定优先，后续 AI 分析不再覆盖 */
  reviewedBy: string | null
  reviewedNote: string | null
  reviewedAt: string | null
  analyzedTaskId: string | null
}

export interface TraceCoverageQuery {
  pageNo?: number
  pageSize?: number
  /** uuid 逗号分隔，最多 100 个 */
  requirementIds?: string
}

export interface TraceCoveragePatchPayload {
  coverageStatus: Exclude<RequirementCoverageStatus, 'pending'>
  note?: string
}

export interface TraceGap {
  targetType: 'requirement' | 'test_case'
  targetId: string
  /** 需求为「REQ-001 标题」，用例为节点标题 */
  title: string
  /** 仅需求缺口回填 */
  coverageStatus: RequirementCoverageStatus | null
  suggestedAction: TraceGapAction
}

export interface TraceGapQuery {
  pageNo?: number
  pageSize?: number
  type: TraceGapType
}

export interface TraceImpactItem {
  edgeId: string
  target: TraceNodeRef
  impactType: TraceEdgeType
  disposition: TraceImpactDisposition
  reason: string | null
  disposedBy: string | null
}

export interface TraceImpactQuery {
  pageNo?: number
  pageSize?: number
  requirementId: string
  disposition?: TraceImpactDisposition
}

export interface TraceImpactPatchPayload {
  disposition: TraceImpactDisposition
  /** no_impact 必填 */
  reason?: string
}

/** 需求摘要（文档关联需求与需求选取器共用） */
export interface RequirementSummary {
  id: string
  code: string
  title: string
}
