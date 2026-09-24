import type { InvitationEffectiveStatus, InvitationListItem } from '@/types'

export interface InvitationStatusMeta {
  label: string
  tone: 'success' | 'neutral' | 'danger'
}

export interface InvitationShareOptions {
  workspaceName?: string | null
  inviterUsername?: string | null
  exhausted?: boolean
}

const DEFAULT_INVITATION_DESCRIPTION = '打开链接后按页面提示完成身份验证并加入工作空间。'
const EXHAUSTED_INVITATION_DESCRIPTION = '该邀请已达使用上限，加入前请先确认剩余名额。'

export function buildInvitationShareText(url: string, options: InvitationShareOptions = {}): string {
  const workspaceLabel = options.workspaceName?.trim() || '当前工作空间'
  const inviterLabel = options.inviterUsername?.trim() || '未知用户'
  const description = options.exhausted ? EXHAUSTED_INVITATION_DESCRIPTION : DEFAULT_INVITATION_DESCRIPTION
  return [
    '【RoboTest 工作空间邀请】',
    `工作空间：${workspaceLabel}`,
    `邀请人：${inviterLabel}`,
    `邀请链接：${url}`,
    `说明：${description}`,
  ].join('\n')
}

export function canCopyInvitation(invitation: InvitationListItem): boolean {
  return invitation.effectiveStatus === 'active' || invitation.effectiveStatus === 'exhausted'
}

export function canExpireInvitation(invitation: InvitationListItem): boolean {
  return canCopyInvitation(invitation)
}

export function invitationStatusMeta(status: InvitationEffectiveStatus): InvitationStatusMeta {
  switch (status) {
    case 'active':
      return { label: '有效', tone: 'success' }
    case 'exhausted':
      return { label: '已达上限', tone: 'neutral' }
    case 'expired':
      return { label: '已过期', tone: 'danger' }
    case 'revoked':
      return { label: '已失效', tone: 'neutral' }
  }
}
