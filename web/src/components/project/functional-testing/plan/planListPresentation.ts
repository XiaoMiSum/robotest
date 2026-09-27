import type { PlanStatus } from '@/types'
import { formatDate, formatShortDateTime } from '@/utils/format'

/** 状态元信息：标签与语义色（列表、详情页与工作台共用，基准 web/demos/project/plans.html） */
export interface PlanStatusMeta {
  label: string
  tagType: 'info' | 'warning' | 'success' | 'danger'
  /** 状态点配色修饰，与 tagType 同源，避免两处口径漂移 */
  modifier: 'neutral' | 'running' | 'success' | 'blocked'
}

export const PLAN_STATUS_META: Record<PlanStatus, PlanStatusMeta> = {
  new: { label: '未开始', tagType: 'info', modifier: 'neutral' },
  in_progress: { label: '执行中', tagType: 'warning', modifier: 'running' },
  completed: { label: '已完成', tagType: 'success', modifier: 'success' },
  blocked: { label: '已阻塞', tagType: 'danger', modifier: 'blocked' },
  closed: { label: '已关闭', tagType: 'info', modifier: 'neutral' },
}

const UNKNOWN_STATUS_META: PlanStatusMeta = {
  label: '—',
  tagType: 'info',
  modifier: 'neutral',
}

/** 未知状态兜底展示，避免后端新增状态时前端出现空白 */
export function planStatusMeta(status: string): PlanStatusMeta {
  return PLAN_STATUS_META[status as PlanStatus] ?? UNKNOWN_STATUS_META
}

export function planStatusLabel(status: string): string {
  return planStatusMeta(status).label
}

/** 活跃态：可标记执行结果、调整用例、同步、完成与阻塞 */
export function isActivePlan(status: string): boolean {
  return status === 'new' || status === 'in_progress'
}

export function canBlockPlan(status: string): boolean {
  return isActivePlan(status)
}

export function canResumePlan(status: string): boolean {
  return status === 'blocked'
}

/** 列表主操作：执行中进入执行，其余查看详情（已阻塞另有恢复入口） */
export function planListAction(status: string): 'enter' | 'view' {
  return status === 'in_progress' ? 'enter' : 'view'
}

export interface PlanPassRate {
  text: string
  tone: 'default' | 'success' | 'muted'
}

/** 通过率展示：未开始无数据为 —，已完成绿色加粗，其余常规展示 */
export function planPassRate(status: string, passRate: number): PlanPassRate {
  if (status === 'new') return { text: '—', tone: 'muted' }
  if (status === 'completed') return { text: trimPercent(passRate), tone: 'success' }
  return { text: trimPercent(passRate), tone: 'default' }
}

/** 进度条填充色：执行中橙（warning）、已阻塞红（exception），其余走 Element Plus 默认色 */
export function planProgressStatus(status: string): 'warning' | 'exception' | undefined {
  if (status === 'in_progress') return 'warning'
  if (status === 'blocked') return 'exception'
  return undefined
}

/** 进度文案：整数百分比（基准 58% / 100%） */
export function planProgressText(percentage: number): string {
  return `${Math.round(percentage)}%`
}

/** 起止时间：双值 `MM-DD ~ MM-DD`，单值带时刻，均无显示 -（基准 09-20 ~ 09-24 / 09-22 09:00） */
export function planTimeRange(startTime?: string | null, endTime?: string | null): string {
  const start = shortDate(startTime)
  const end = shortDate(endTime)
  if (start && end) return `${start} ~ ${end}`
  if (!start && !end) return '-'
  return formatShortDateTime(startTime ?? endTime)
}

export function planEnvironment(environment: string | null): string {
  return environment?.trim() || '—'
}

export function planNameSub(totalAssociated: number): string {
  return `关联用例 ${totalAssociated} 条`
}

export function planCountText(total: number): string {
  return `共 ${total} 个计划`
}

export function planPagerTotalText(total: number, pageSize: number): string {
  return `${planCountText(total)} · 每页 ${pageSize} 条`
}

/** 返回 `MM-DD`；空值或非法时间返回空串，交由调用方按缺失处理 */
function shortDate(value?: string | null): string {
  const formatted = value ? formatDate(value) : '-'
  return formatted === '-' ? '' : formatted.slice(5)
}

function trimPercent(value: number): string {
  return `${Math.round(value * 10) / 10}%`
}
