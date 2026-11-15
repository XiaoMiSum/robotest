import { ElMessageBox } from 'element-plus'
import type { TagProps } from 'element-plus'
import type { BugPriority, BugResolution, BugSeverity, BugStatus, BugType } from '@/types'

export const BUG_SEVERITY_LABEL: Record<BugSeverity, string> = {
  fatal: '致命', serious: '严重', general: '一般', minor: '轻微',
}
export const BUG_PRIORITY_LABEL: Record<BugPriority, string> = {
  high: '高', medium: '中', low: '低',
}
export const BUG_SEVERITY_TAG_TYPE: Record<BugSeverity, TagProps['type']> = {
  fatal: 'danger', serious: 'warning', general: 'info', minor: 'success',
}
export const BUG_PRIORITY_TAG_TYPE: Record<BugPriority, 'primary' | 'warning' | 'info'> = {
  high: 'warning', medium: 'primary', low: 'info',
}

/** 缺陷状态标签 */
export const BUG_STATUS_LABEL: Record<BugStatus, string> = {
  active: '激活',
  resolved: '已修复',
  rejected: '已拒绝',
  closed: '已关闭',
}

// 蓝色保留给交互，未修复的激活状态使用警告色。
export const BUG_STATUS_TAG_TYPE: Record<BugStatus, 'primary' | 'danger' | 'success' | 'warning' | 'info'> = {
  active: 'warning',
  resolved: 'success',
  rejected: 'warning',
  closed: 'info',
}

/** 缺陷类型标签 */
export const BUG_TYPE_LABEL: Record<BugType, string> = {
  code_error: '代码错误',
  ui_improvement: '界面优化',
  design_defect: '设计缺陷',
  configuration: '配置相关',
  installation: '安装部署',
  security: '安全相关',
  performance: '性能问题',
  standard_spec: '标准规范',
  other: '其他',
}

/** 缺陷解决方案标签（名词式措辞，避免与状态标签「已解决」同屏时误读为两个状态） */
export const BUG_RESOLUTION_LABEL: Record<BugResolution, string> = {
  fixed: '已解决',
  by_design: '设计如此',
  duplicate: '重复缺陷',
  external: '外部原因',
  cannot_reproduce: '无法重现',
  deferred: '延期处理',
  wont_fix: '不予解决',
}

// 与后端 BugServiceImpl.isValidTransition 保持一致，避免非法流转请求
const BUG_STATUS_TRANSITIONS: Record<BugStatus, BugStatus[]> = {
  active: ['resolved', 'rejected'],
  resolved: ['closed', 'active'],
  rejected: ['closed', 'active'],
  closed: ['active'],
}

/** 当前状态允许流转到的目标状态列表 */
export function getValidTargetStatuses(current: BugStatus): BugStatus[] {
  return BUG_STATUS_TRANSITIONS[current] ?? []
}

/** 是否为重开（激活）操作 */
export function isReopen(current: BugStatus, target: BugStatus): boolean {
  return target === 'active' && (current === 'resolved' || current === 'rejected' || current === 'closed')
}

/** 关闭/拒绝与重开时后端强制要求说明；解决走 BugResolveDialog 单独处理 */
export function isCommentRequired(current: BugStatus, target: BugStatus): boolean {
  return target === 'closed' || target === 'rejected' || isReopen(current, target)
}

/**
 * 弹出状态变更说明对话框（用于关闭/重开）。
 *
 * @returns 确认时返回说明文本（可能为空串），取消时返回 null
 */
export async function promptStatusChangeComment(
  current: BugStatus,
  target: BugStatus,
): Promise<string | null> {
  const required = isCommentRequired(current, target)
  try {
    const { value } = await ElMessageBox.prompt(
      `确定要将状态变更为「${BUG_STATUS_LABEL[target]}」吗？`,
      '状态变更',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        inputType: 'textarea',
        inputPlaceholder: required ? '请输入变更说明（必填）' : '变更说明（选填）',
        inputValidator: (val: string) =>
          !required || (typeof val === 'string' && val.trim().length > 0) || '请填写变更说明',
      },
    )
    return (value ?? '').trim()
  } catch {
    return null
  }
}
