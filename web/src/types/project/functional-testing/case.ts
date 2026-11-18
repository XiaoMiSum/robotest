import type { CaseNodeType } from '../shared'
import type { RequirementRef } from '../../requirement'

/** 用例圈选节点（创建评审 / 计划的关联用例载荷） */
export interface SelectedCaseNode {
  documentId: string
  caseIds: string[]
}

/** 评审创建载荷（列表页创建共用） */
export interface TestReviewCreatePayload {
  title: string
  description?: string
  participantIds: string[]
  /** 关联需求；服务端按需求反查关联用例，未关联用例时允许创建空评审 */
  requirementIds: string[]
  /** 显式选中的用例（AI 助手链路使用，创建面板不再提交） */
  selectedNodes?: SelectedCaseNode[]
}

/** 关联需求变化后异步重规划任务的状态（idle 表示从未发起） */
export type ReplanStatus = 'idle' | 'running' | 'succeeded' | 'failed'

/** 重规划状态查询响应 */
export interface ReplanStatusResp {
  status: ReplanStatus
  message: string | null
  requestedAt: string | null
  finishedAt: string | null
}

/** 评审编辑载荷（字段可选，沿用后端部分更新语义） */
export interface TestReviewUpdatePayload {
  title?: string
  description?: string
  participantIds?: string[]
  requirementIds?: string[]
  /** 仅在关联需求集合变化且用户确认后为 true */
  replan?: boolean
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
  /** 关联需求数量（需求已删除时不计数） */
  requirementCount: number
}

/** 测试评审详情 */
export interface TestReviewDetail {
  id: string
  title: string
  description: string | null
  status: ReviewStatus
  initiator: { id: string; name: string }
  participantIds: string[]
  /** 关联需求（按创建时所选顺序，需求已删除时跳过） */
  requirements: RequirementRef[]
  /** 关联需求原始 ID（含已删除需求），供编辑表单判定集合变化 */
  requirementIds: string[]
  replanStatus: ReplanStatus
  replanMessage: string | null
  replanFinishedAt: string | null
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
