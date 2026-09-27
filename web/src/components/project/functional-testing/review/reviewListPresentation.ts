import type { ReviewStatus, TestReviewListItem } from '@/types'

/** 状态元信息：标签与语义色（列表与详情共用，基准 web/demos/project/reviews.html） */
export interface ReviewStatusMeta {
  label: string
  tagType: 'info' | 'warning' | 'success' | 'danger'
  /** 状态点配色修饰，与 tagType 同源，避免两处口径漂移 */
  modifier: 'neutral' | 'running' | 'success' | 'danger'
}

export const REVIEW_STATUS_META: Record<ReviewStatus, ReviewStatusMeta> = {
  new: { label: '待评审', tagType: 'info', modifier: 'neutral' },
  in_progress: { label: '进行中', tagType: 'warning', modifier: 'running' },
  completed: { label: '已通过', tagType: 'success', modifier: 'success' },
  rejected: { label: '已驳回', tagType: 'danger', modifier: 'danger' },
}

const UNKNOWN_STATUS_META: ReviewStatusMeta = {
  label: '—',
  tagType: 'info',
  modifier: 'neutral',
}

/** 未知状态兜底展示，避免后端新增状态时前端出现空白 */
export function reviewStatusMeta(status: string): ReviewStatusMeta {
  return REVIEW_STATUS_META[status as ReviewStatus] ?? UNKNOWN_STATUS_META
}

export function reviewStatusLabel(status: string): string {
  return reviewStatusMeta(status).label
}

/** 活跃态：可标记、调整用例、同步、完成与驳回 */
export function isActiveReview(status: string): boolean {
  return status === 'new' || status === 'in_progress'
}

export function canRejectReview(status: string): boolean {
  return isActiveReview(status)
}

export function canReopenReview(status: string): boolean {
  return status === 'rejected'
}

/** 列表主操作：活跃态进入评审，终态只读查看 */
export function reviewListAction(status: string): 'enter' | 'view' {
  return isActiveReview(status) ? 'enter' : 'view'
}

export interface ReviewPassRate {
  text: string
  tone: 'success' | 'danger' | 'muted'
}

/** 通过率展示：未到终态一律 —；已通过绿、已驳回红（演示稿 62.9% / 100% 口径） */
export function reviewPassRate(status: string, passRate: number): ReviewPassRate {
  if (status === 'completed') return { text: `${trimPercent(passRate)}`, tone: 'success' }
  if (status === 'rejected') return { text: `${trimPercent(passRate)}`, tone: 'danger' }
  return { text: '—', tone: 'muted' }
}

/** 进度文案：已评审/关联总数（18/30） */
export function reviewProgressText(reviewed: number, totalAssociated: number): string {
  return `${Math.max(0, Math.min(reviewed, totalAssociated))}/${totalAssociated}`
}

/** 进度条填充色：进行中橙（warning）、已驳回红（exception），其余走 Element Plus 默认色 */
export function reviewProgressStatus(status: string): 'warning' | 'exception' | undefined {
  if (status === 'in_progress') return 'warning'
  if (status === 'rejected') return 'exception'
  return undefined
}

export interface ReviewAvatar {
  key: string
  label: string
  avatarUrl: string | null
  /** 发起人置顶并用品牌色区分 */
  brand: boolean
}

export interface ReviewAvatarStack {
  visible: ReviewAvatar[]
  overflow: number
}

/** 头像堆：发起人 + 参与者去重，最多展示 max 个，其余折算为 +N */
export function reviewAvatars(
  row: Pick<TestReviewListItem, 'initiator' | 'participants'>,
  max = 3,
): ReviewAvatarStack {
  const all: ReviewAvatar[] = []
  const seen = new Set<string>()
  const push = (id: string, name: string, avatarUrl: string | null, brand: boolean): void => {
    if (seen.has(id)) return
    seen.add(id)
    all.push({ key: id, label: name.trim().charAt(0) || '?', avatarUrl, brand })
  }
  push(row.initiator.id, row.initiator.name, null, true)
  for (const participant of row.participants) {
    push(participant.id, participant.name, participant.avatarUrl, false)
  }
  return { visible: all.slice(0, max), overflow: Math.max(0, all.length - max) }
}

function trimPercent(value: number): string {
  return `${Math.round(value * 10) / 10}%`
}
