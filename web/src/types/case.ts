import type { CaseNodeType } from './project'

/** 评审状态 */
export type ReviewStatus = 'new' | 'in_progress' | 'completed'

/** 评审标记 */
export type ReviewMark = 'pass' | 'fail'

/** 测试评审列表项 */
export interface TestReviewListItem {
  id: string
  title: string
  status: ReviewStatus
  initiator: { id: string; name: string }
  participantCount: number
  createdAt: string
  totalAssociated: number
  passed: number
  progressPercent: number
  passRate: number
}

/** 测试评审详情 */
export interface TestReviewDetail {
  id: string
  title: string
  description: string | null
  status: ReviewStatus
  initiator: { id: string; name: string }
  participantIds: string[]
  createdAt: string
}

/** 评审快照节点 */
export interface TestReviewSnapshotNode {
  id: string
  originalNodeId: string | null
  parentId: string | null
  title: string
  type: CaseNodeType
  priority: string | null
  isAssociated: boolean
  lastMark: ReviewMark | null
  lastReviewerId: string | null
  lastReviewedAt: string | null
  sortOrder: number
  aiGenerated?: boolean
  children: TestReviewSnapshotNode[]
}

/** 评审进度 */
export interface TestReviewProgress {
  totalAssociated: number
  passed: number
  failed: number
  pending: number
  progressPercent: number
}

/** 评审记录 */
export interface ReviewRecord {
  id: string
  snapshotNodeId: string
  reviewerId: string
  reviewerName: string
  operationType: 'mark' | 'comment'
  mark: ReviewMark | 'pending' | null
  comment: string | null
  createdAt: string
}
