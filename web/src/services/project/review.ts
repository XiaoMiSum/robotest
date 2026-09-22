import { get, post, put, del } from '@/services'
import type {
  PageResult,
  PlannedCases,
  ReviewMark,
  ReviewRecord,
  ReviewStatus,
  SnapshotModule,
  TestReviewDetail,
  TestReviewListItem,
  TestReviewProgress,
  TestReviewSnapshotNode,
} from '@/types'

// ==================== 测试评审 ====================

export function fetchReviews(params: {
  status?: ReviewStatus | ''
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<TestReviewListItem>> {
  return get('/project/reviews', { ...params })
}

export function createReview(data: {
  title: string
  description?: string
  participantIds: string[]
  selectedNodes: { documentId: string; caseIds: string[] }[]
}): Promise<TestReviewDetail> {
  return post('/project/reviews', data)
}

export function getReviewDetail(id: string): Promise<TestReviewDetail> {
  return get(`/project/reviews/${id}`)
}

export function getReviewSnapshotTree(
  id: string,
  documentId?: string,
): Promise<TestReviewSnapshotNode[]> {
  return get(`/project/reviews/${id}/modules`, documentId ? { documentId } : undefined)
}

export function getReviewModuleTree(id: string): Promise<SnapshotModule[]> {
  return get(`/project/reviews/${id}/module-tree`)
}

export function getReviewPlannedCases(id: string): Promise<PlannedCases[]> {
  return get(`/project/reviews/${id}/cases`)
}

export function updateReviewCases(id: string, selectedNodes: PlannedCases[]): Promise<void> {
  return put(`/project/reviews/${id}/cases`, { selectedNodes })
}

export function submitReviewRecord(
  reviewId: string,
  data: {
    snapshotNodeId: string
    operationType: 'mark' | 'comment'
    // pending 为显式重置回待评审，后端落库 last_mark = null
    mark?: ReviewMark | 'pending'
    comment?: string
  },
): Promise<void> {
  return post(`/project/reviews/${reviewId}/records`, data)
}

export function getNodeReviewRecords(reviewId: string, nodeId: string): Promise<ReviewRecord[]> {
  return get(`/project/reviews/${reviewId}/nodes/${nodeId}/records`)
}

export function completeReview(id: string): Promise<void> {
  return post(`/project/reviews/${id}/complete`)
}

export function deleteReview(id: string): Promise<void> {
  return del(`/project/reviews/${id}`)
}

export function getReviewProgress(id: string): Promise<TestReviewProgress> {
  return get(`/project/reviews/${id}/progress`)
}

export function syncReview(id: string): Promise<void> {
  return post(`/project/reviews/${id}/sync`)
}
