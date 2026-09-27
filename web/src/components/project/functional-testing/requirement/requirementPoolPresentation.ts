import type { RequirementStatus } from '@/types'

/** 状态元信息：标签与圆点配色（基准 web/demos/project/requirements.html） */
export interface RequirementStatusMeta {
  label: string
  modifier: 'active' | 'archived'
}

export const REQUIREMENT_STATUS_META: Record<RequirementStatus, RequirementStatusMeta> = {
  active: { label: '启用', modifier: 'active' },
  archived: { label: '已归档', modifier: 'archived' },
}

const UNKNOWN_STATUS_META: RequirementStatusMeta = {
  label: '—',
  modifier: 'active',
}

/** 未知状态兜底展示，避免后端新增状态时前端出现空白 */
export function requirementStatusMeta(status: string): RequirementStatusMeta {
  return REQUIREMENT_STATUS_META[status as RequirementStatus] ?? UNKNOWN_STATUS_META
}

export function requirementStatusLabel(status: string): string {
  return requirementStatusMeta(status).label
}

export interface RequirementSegmentOption {
  label: string
  value: RequirementStatus
}

/** 分段切换项：两态互斥即全集，故无「全部」（基准口径，默认启用） */
export const REQUIREMENT_SEGMENT_OPTIONS: RequirementSegmentOption[] = [
  { label: '启用', value: 'active' },
  { label: '已归档', value: 'archived' },
]

/** 归档条目只读：隐藏编辑入口（后端同时强校验拒绝） */
export function canEditRequirement(status: string): boolean {
  return status !== 'archived'
}

export interface RequirementSourceSub {
  /** 短链接（host + path）或「内部提出」 */
  text: string
  /** 可点击来源，内部提出时为空 */
  href: string | null
}

/** 副行来源：无来源 URL 展示「内部提出」，有则截取 host + path（基准口径，替代无数据的分类副行） */
export function requirementSourceSub(sourceUrl: string | null): RequirementSourceSub {
  if (!sourceUrl) return { text: '内部提出', href: null }
  return { text: shortUrl(sourceUrl), href: sourceUrl }
}

export function requirementPagerTotal(total: number, pageSize: number): string {
  return `共 ${total} 条 · 每页 ${pageSize} 条`
}

function shortUrl(url: string): string {
  try {
    const parsed = new URL(url)
    return `${parsed.host}${parsed.pathname}`.replace(/\/+$/, '')
  } catch {
    // 非法 URL 原样展示，避免整列空白
    return url
  }
}
