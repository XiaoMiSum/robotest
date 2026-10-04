import { get, patch, post, put } from '@/services'
import type {
  PageResult,
  RequirementSummary,
  TraceChain,
  TraceChainQuery,
  TraceCoverage,
  TraceCoveragePatchPayload,
  TraceCoverageQuery,
  TraceEdge,
  TraceEdgeCreatePayload,
  TraceEdgePatchPayload,
  TraceEdgeQuery,
  TraceGap,
  TraceGapQuery,
  TraceImpactItem,
  TraceImpactPatchPayload,
  TraceImpactQuery,
  TraceMatrixQuery,
  TraceMatrixRow,
} from '@/types'

// ==================== 追溯矩阵（详设 3.2–3.10） ====================

export function fetchTraceMatrix(params: TraceMatrixQuery = {}): Promise<PageResult<TraceMatrixRow>> {
  return get('/project/trace/matrix', { ...params })
}

export function fetchTraceChain(query: TraceChainQuery): Promise<TraceChain> {
  return get('/project/trace/chain', { ...query })
}

export function fetchTraceEdges(params: TraceEdgeQuery = {}): Promise<PageResult<TraceEdge>> {
  return get('/project/trace/edges', { ...params })
}

export function createTraceEdge(data: TraceEdgeCreatePayload): Promise<TraceEdge> {
  return post('/project/trace/edges', data)
}

export function patchTraceEdge(edgeId: string, data: TraceEdgePatchPayload): Promise<TraceEdge> {
  return patch(`/project/trace/edges/${edgeId}`, data)
}

export function fetchTraceCoverage(params: TraceCoverageQuery = {}): Promise<PageResult<TraceCoverage>> {
  return get('/project/trace/coverage', { ...params })
}

export function patchTraceCoverage(
  requirementId: string,
  data: TraceCoveragePatchPayload,
): Promise<TraceCoverage> {
  return patch(`/project/trace/coverage/${requirementId}`, data)
}

export function fetchTraceGaps(params: TraceGapQuery): Promise<PageResult<TraceGap>> {
  return get('/project/trace/gaps', { ...params })
}

export function fetchTraceImpactItems(
  params: TraceImpactQuery,
): Promise<PageResult<TraceImpactItem>> {
  return get('/project/trace/impact-items', { ...params })
}

export function patchTraceImpactItem(
  edgeId: string,
  data: TraceImpactPatchPayload,
): Promise<TraceImpactItem> {
  return patch(`/project/trace/impact-items/${edgeId}`, data)
}

// ==================== 文档关联需求（脑图详设 4.3，底层经追溯边承载） ====================

export function fetchDocumentRequirements(docId: string): Promise<RequirementSummary[]> {
  return get(`/project/documents/${docId}/requirements`)
}

/** 全量覆盖保存，返回保存后的完整关联列表 */
export function setDocumentRequirements(
  docId: string,
  requirementIds: string[],
): Promise<RequirementSummary[]> {
  return put(`/project/documents/${docId}/requirements`, { requirementIds })
}
