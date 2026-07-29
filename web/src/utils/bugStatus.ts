import { ElMessageBox } from 'element-plus'
import type { BugStatus } from '@/types'

/** 缺陷状态标签 */
export const BUG_STATUS_LABEL: Record<BugStatus, string> = {
  new: '新建',
  assigned: '已指派',
  fixing: '修复中',
  fixed: '已修复',
  verified: '已验证',
  closed: '已关闭',
}

// 与后端 BugServiceImpl.isValidTransition 保持一致，避免非法流转请求
const BUG_STATUS_TRANSITIONS: Record<BugStatus, BugStatus[]> = {
  new: ['assigned'],
  assigned: ['fixing'],
  fixing: ['fixed'],
  fixed: ['verified'],
  verified: ['closed', 'fixing'],
  closed: ['fixing'],
}

/** 当前状态允许流转到的目标状态列表 */
export function getValidTargetStatuses(current: BugStatus): BugStatus[] {
  return BUG_STATUS_TRANSITIONS[current] ?? []
}

/** 是否为重开操作（已验证/已关闭 → 修复中） */
export function isReopen(current: BugStatus, target: BugStatus): boolean {
  return target === 'fixing' && (current === 'verified' || current === 'closed')
}

/** 关闭与重开时后端强制要求说明 */
export function isCommentRequired(current: BugStatus, target: BugStatus): boolean {
  return target === 'closed' || isReopen(current, target)
}

/**
 * 弹出状态变更说明对话框。
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
