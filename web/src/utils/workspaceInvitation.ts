import type { InvitationEffectiveStatus, InvitationListItem } from '@/types'

export interface InvitationStatusMeta {
  label: string
  tone: 'success' | 'neutral' | 'danger'
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
