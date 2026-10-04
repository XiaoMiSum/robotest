import type {
  RequirementCoverageStatus,
  TraceEdgeAction,
  TraceEdgeStatus,
  TraceGapAction,
  TraceGapType,
  TraceImpactDisposition,
  TraceMatrixRow,
  TraceNodeType,
} from '@/types'
import {
  type RequirementCoverageMeta,
  requirementCoverageMeta,
  requirementStatusMeta,
  type RequirementStatusMeta,
} from '@/composables/project/requirement/requirementPresentation'

// ==================== 边状态（交互 04 §4：图例常驻，图标 + 文字不单靠颜色表意） ====================

export interface TraceEdgeStatusMeta {
  /** 图例与徽标按状态精确着色（视觉设计 §4 边状态色） */
  status: TraceEdgeStatus
  label: string
  /** 图例符号：○ 有效 / ◐ 待确认 / ⚠ stale / ⚠⚠ conflict / ⋯ 已断开 */
  symbol: string
  tagType: 'success' | 'info' | 'warning' | 'danger'
  hint: string
}

const EDGE_STATUS_META: Record<TraceEdgeStatus, TraceEdgeStatusMeta> = {
  confirmed: { status: 'confirmed', label: '有效', symbol: '○', tagType: 'success', hint: '链路有效' },
  ai_created: { status: 'ai_created', label: '待确认', symbol: '◐', tagType: 'info', hint: 'AI 建边待人工确认' },
  stale: { status: 'stale', label: 'stale', symbol: '⚠', tagType: 'warning', hint: '目标版本已变更，需重新确认' },
  conflict: {
    status: 'conflict',
    label: 'conflict',
    symbol: '⚠⚠',
    tagType: 'danger',
    hint: '人工与 AI 结论冲突，需采纳其一',
  },
  detached: {
    status: 'detached',
    label: '已断开',
    symbol: '⋯',
    tagType: 'info',
    hint: '人工断开，不计入覆盖统计',
  },
}

export function traceEdgeStatusMeta(status: string): TraceEdgeStatusMeta {
  return EDGE_STATUS_META[status as TraceEdgeStatus] ?? {
    status: 'confirmed',
    label: status,
    symbol: '○',
    tagType: 'info',
    hint: status,
  }
}

/** 图例常驻项，顺序与交互设计 2.1 图例一致 */
export const TRACE_EDGE_LEGEND: TraceEdgeStatusMeta[] = [
  EDGE_STATUS_META.confirmed,
  EDGE_STATUS_META.ai_created,
  EDGE_STATUS_META.stale,
  EDGE_STATUS_META.conflict,
  EDGE_STATUS_META.detached,
]

// ==================== 节点类型 ====================

export interface TraceNodeTypeMeta {
  label: string
  /** Element Plus 图标名（自动导入），矩阵列与链路树节点区分用 */
  icon: string
}

const NODE_TYPE_META: Record<TraceNodeType, TraceNodeTypeMeta> = {
  requirement: { label: '需求', icon: 'Tickets' },
  module: { label: '模块', icon: 'Folder' },
  mindmap_document: { label: '脑图文档', icon: 'Notebook' },
  test_case: { label: '测试用例', icon: 'List' },
  test_review: { label: '评审', icon: 'View' },
  test_plan: { label: '测试计划', icon: 'Calendar' },
}

export function traceNodeTypeMeta(type: string): TraceNodeTypeMeta {
  return NODE_TYPE_META[type as TraceNodeType] ?? { label: type, icon: 'Tickets' }
}

/** 矩阵下游列（交互 04 §2.1 表头），key 对齐后端 edgeCounts 字段 */
export const TRACE_MATRIX_COLUMNS: { key: keyof TraceMatrixRow['edgeCounts']; label: string; type: TraceNodeType }[] = [
  { key: 'module', label: '模块', type: 'module' },
  { key: 'document', label: '脑图文档', type: 'mindmap_document' },
  { key: 'testCase', label: '测试用例', type: 'test_case' },
  { key: 'review', label: '评审', type: 'test_review' },
  { key: 'plan', label: '测试计划', type: 'test_plan' },
]

// ==================== 覆盖与需求状态 ====================

export type TraceCoverageMeta = RequirementCoverageMeta

/** 追溯侧未接入或 AI 关闭时矩阵覆盖列展示「—」（交互 04 §2.6） */
export function traceCoverageMeta(value: RequirementCoverageStatus | null | undefined): TraceCoverageMeta | null {
  return requirementCoverageMeta(value)
}

export type TraceRequirementStatusMeta = RequirementStatusMeta

export function traceRequirementStatusMeta(status: string): RequirementStatusMeta {
  return requirementStatusMeta(status)
}

// ==================== 矩阵行高亮 ====================

export interface TraceRowHighlight {
  /** 单元格警示底色：stale 警示 / conflict 危险（交互 04 §4） */
  warning: boolean
  danger: boolean
}

export function traceRowHighlight(row: Pick<TraceMatrixRow, 'staleCount' | 'conflictCount'>): TraceRowHighlight {
  return { warning: row.staleCount > 0, danger: row.conflictCount > 0 }
}

/** stale 成因悬浮解释（版本比对失效），conflict 为人工与 AI 结论冲突 */
export function traceRowHint(row: Pick<TraceMatrixRow, 'staleCount' | 'conflictCount'>): string {
  const parts: string[] = []
  if (row.staleCount > 0) parts.push(`${row.staleCount} 条边目标版本已变更，需重新确认`)
  if (row.conflictCount > 0) parts.push(`${row.conflictCount} 条边人工与 AI 结论冲突`)
  return parts.join('；')
}

// ==================== 缺口 ====================

export interface TraceGapTypeMeta {
  label: string
  /** 分组说明，缺口视图子标签页标题 */
  desc: string
}

const GAP_TYPE_META: Record<TraceGapType, TraceGapTypeMeta> = {
  uncovered_requirement: { label: '未生成用例', desc: '需求尚无派生用例' },
  orphan_case: { label: '未关联需求', desc: '用例未回溯到任何需求' },
  unreviewed_case: { label: '未评审', desc: '用例未进入评审' },
  unscheduled_case: { label: '未进计划', desc: '用例未纳入测试计划' },
}

export function traceGapTypeMeta(type: string): TraceGapTypeMeta {
  return GAP_TYPE_META[type as TraceGapType] ?? { label: type, desc: type }
}

export const TRACE_GAP_TYPES: TraceGapType[] = [
  'uncovered_requirement',
  'orphan_case',
  'unreviewed_case',
  'unscheduled_case',
]

export interface TraceGapActionMeta {
  label: string
  /** 批次二生成配置未落地，本包置灰并以悬浮提示说明（方案裁决） */
  disabled: boolean
  disabledHint: string
}

const GAP_ACTION_META: Record<TraceGapAction, TraceGapActionMeta> = {
  generate: { label: '发起生成', disabled: true, disabledHint: '生成配置随批次二开放' },
  review: { label: '发起评审', disabled: false, disabledHint: '' },
  schedule: { label: '加入计划', disabled: false, disabledHint: '' },
}

export function traceGapActionMeta(action: string): TraceGapActionMeta {
  return GAP_ACTION_META[action as TraceGapAction] ?? { label: action, disabled: true, disabledHint: '' }
}

/** 缺口引导的落地页：generate 尚无生成配置入口，由调用方按 disabled 提示 */
export const TRACE_GAP_ROUTE: Record<'review' | 'schedule', string> = {
  review: '/workspace/projects/reviews',
  schedule: '/workspace/projects/plans',
}

// ==================== 影响处置 ====================

export interface TraceDispositionMeta {
  label: string
  tagType: 'info' | 'warning' | 'success' | 'danger'
  /** 跟进与已处理须填理由（交互 04 §2.5） */
  requireReason: boolean
}

const DISPOSITION_META: Record<TraceImpactDisposition, TraceDispositionMeta> = {
  pending: { label: '待处置', tagType: 'info', requireReason: false },
  no_impact: { label: '确认无影响', tagType: 'success', requireReason: false },
  regenerate: { label: '需要跟进', tagType: 'warning', requireReason: true },
  re_review: { label: '已处理', tagType: 'success', requireReason: true },
}

export function traceDispositionMeta(disposition: string): TraceDispositionMeta {
  return DISPOSITION_META[disposition as TraceImpactDisposition] ?? { label: disposition, tagType: 'info', requireReason: false }
}

export const TRACE_DISPOSITIONS: TraceImpactDisposition[] = ['no_impact', 'regenerate', 're_review']

// ==================== 修正动作可用性 ====================

export interface TraceActionAvailability {
  confirm: boolean
  reattach: boolean
  detach: boolean
  restore: boolean
}

/**
 * 修正动作可用性（详设 3.6 状态机）：detached 只能恢复，其余状态可确认 / 改挂 / 断开。
 * 冲突边保留两个动作入口，由调用方按状态提示「采纳人工或保留 AI」。
 */
export function traceActionAvailability(status: string): TraceActionAvailability {
  const detached = status === 'detached'
  return {
    confirm: !detached,
    reattach: !detached,
    detach: !detached,
    restore: detached,
  }
}

/** 边修正动作的中文名（操作确认与回执文案） */
export const TRACE_ACTION_LABEL: Record<TraceEdgeAction, string> = {
  confirm: '确认',
  reattach: '改挂',
  detach: '断开',
  restore: '恢复',
}
