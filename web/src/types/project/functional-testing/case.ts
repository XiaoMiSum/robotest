import type { CaseNodeType } from '../shared'

/** 用例圈选节点（创建评审 / 计划的关联用例载荷） */
export interface SelectedCaseNode {
  documentId: string
  caseIds: string[]
}

/** 评审创建载荷（列表页创建与圈选确认 target.createParams 共用） */
export interface TestReviewCreatePayload {
  title: string
  description?: string
  participantIds: string[]
  selectedNodes: SelectedCaseNode[]
}

/** 评审状态（new 待评审 / in_progress 进行中 / completed 已通过 / rejected 已驳回） */
export type ReviewStatus = 'new' | 'in_progress' | 'completed' | 'rejected'

/** 评审标记 */
export type ReviewMark = 'pass' | 'fail'

/** 评审参与者（列表头像堆展示用） */
export interface ReviewParticipant {
  id: string
  name: string
  avatarUrl: string | null
}

/** 测试评审列表项 */
export interface TestReviewListItem {
  id: string
  title: string
  status: ReviewStatus
  initiator: { id: string; name: string }
  participantCount: number
  participants: ReviewParticipant[]
  createdAt: string
  totalAssociated: number
  /** 已评审数（总数 − 待评审数），进度列展示 n/total */
  reviewed: number
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
