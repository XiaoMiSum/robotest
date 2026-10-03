import type {
  RequirementChangeLog,
  RequirementCoverageStatus,
  RequirementDetail,
  RequirementListItem,
  RequirementPriority,
  RequirementSource,
  RequirementStatus,
} from '@/types'

// ==================== 状态徽标 ====================

export interface RequirementStatusMeta {
  label: string
  /** draft→info、confirmed→success、changed→warning、archived→中性（UI-DS-09 组件变量） */
  tagType: 'info' | 'success' | 'warning'
  /** archived 用 el-tag type=info + 中性色变量覆盖，避免与 draft 的 info 混淆 */
  archived?: boolean
}

export const REQUIREMENT_STATUS_META: Record<RequirementStatus, RequirementStatusMeta> = {
  draft: { label: '草稿', tagType: 'info' },
  confirmed: { label: '已确认', tagType: 'success' },
  changed: { label: '已变更', tagType: 'warning' },
  archived: { label: '已归档', tagType: 'info', archived: true },
}

export function requirementStatusMeta(status: string): RequirementStatusMeta {
  return REQUIREMENT_STATUS_META[status as RequirementStatus] ?? { label: status, tagType: 'info' }
}

// ==================== 覆盖状态徽标 ====================

export interface RequirementCoverageMeta {
  label: string
  tagType: 'success' | 'warning' | 'danger' | 'info'
}

const COVERAGE_META: Record<RequirementCoverageStatus, RequirementCoverageMeta> = {
  covered: { label: '完整覆盖', tagType: 'success' },
  partial: { label: '部分覆盖', tagType: 'warning' },
  uncovered: { label: '未覆盖', tagType: 'danger' },
  pending: { label: '待分析', tagType: 'info' },
}

/** 追溯侧未接入时 coverageStatus 为 null，统一展示「—」（详设 3.2） */
export function requirementCoverageMeta(
  value: RequirementCoverageStatus | null | undefined,
): RequirementCoverageMeta | null {
  if (!value) return null
  return COVERAGE_META[value] ?? { label: value, tagType: 'info' }
}

// ==================== 操作可用性（状态机 4.2） ====================

export function canConfirmRequirement(detail: Pick<RequirementDetail, 'status'>): boolean {
  return detail.status === 'draft' || detail.status === 'changed'
}

export function canArchiveRequirement(detail: Pick<RequirementDetail, 'status'>): boolean {
  return detail.status !== 'archived'
}

export function canUnarchiveRequirement(detail: Pick<RequirementDetail, 'status'>): boolean {
  return detail.status === 'archived'
}

/** 归档条目不可拆分（1000018009），进行中任务由服务端再校验（1000018013） */
export function canSplitRequirement(detail: Pick<RequirementDetail, 'status'>): boolean {
  return detail.status !== 'archived'
}

/** 归档条目只读（1000018004）：属性面板与标题编辑整体禁用 */
export function isRequirementReadonly(detail: Pick<RequirementDetail, 'status'>): boolean {
  return detail.status === 'archived'
}

// ==================== 文案 ====================

export const REQUIREMENT_PRIORITY_LABEL: Record<RequirementPriority, string> = {
  high: '高',
  medium: '中',
  low: '低',
}

export function requirementPriorityLabel(priority: RequirementPriority | null): string {
  return priority ? REQUIREMENT_PRIORITY_LABEL[priority] ?? priority : '—'
}

const SOURCE_LABEL: Record<RequirementSource, string> = {
  manual: '手工创建',
  import: '导入',
}

export function requirementSourceLabel(source: string): string {
  return SOURCE_LABEL[source as RequirementSource] ?? source
}

export const REQUIREMENT_CHANGE_TYPE_LABEL: Record<string, string> = {
  title: '标题',
  description: '描述',
  module: '所属模块',
  status: '状态',
  attribute: '属性',
}

export function requirementChangeTypeLabel(changeType: string): string {
  return REQUIREMENT_CHANGE_TYPE_LABEL[changeType] ?? changeType
}

// ==================== 变更记录摘要 ====================

const ATTRIBUTE_FIELD_LABEL: Record<string, string> = {
  title: '标题',
  systemVersion: '版本',
  priority: '优先级',
  ownerId: '负责人',
  tags: '标签',
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '（空）'
  if (Array.isArray(value)) return value.length > 0 ? value.join('、') : '（空）'
  if (typeof value === 'string') {
    return value.length > 30 ? `${value.slice(0, 30)}…` : value
  }
  return String(value)
}

/**
 * 变更记录 before/after 单键摘要渲染为「字段：旧 → 新」文本数组。
 * description 摘要为截断正文，只标「已修改」不列旧值，避免时间线被正文淹没。
 */
export function requirementChangeLines(log: RequirementChangeLog): string[] {
  const before = log.beforeSummary ?? {}
  const after = log.afterSummary ?? {}
  const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]))
  if (keys.length === 0) return []
  if (log.changeType === 'status') {
    return keys.map((key) => {
      const next = after[key] ?? before[key]
      return `状态更新为「${requirementStatusMeta(String(next)).label}」`
    })
  }
  if (log.changeType === 'description') {
    return ['描述已修改']
  }
  return keys.map((key) => {
    const fieldLabel = ATTRIBUTE_FIELD_LABEL[key] ?? key
    return `${fieldLabel}：${displayValue(before[key])} → ${displayValue(after[key])}`
  })
}

// ==================== 列表行 ====================

export interface RequirementRow extends RequirementListItem {
  statusMeta: RequirementStatusMeta
  coverageMeta: RequirementCoverageMeta | null
  moduleText: string
  ownerText: string
  versionText: string
}

export function requirementRow(item: RequirementListItem): RequirementRow {
  return {
    ...item,
    statusMeta: requirementStatusMeta(item.status),
    coverageMeta: requirementCoverageMeta(item.coverageStatus),
    moduleText: item.moduleName ?? '—',
    ownerText: item.ownerName ?? '—',
    versionText: item.systemVersion ?? '—',
  }
}

export function requirementPagerTotal(total: number): string {
  return `共 ${total} 条`
}
