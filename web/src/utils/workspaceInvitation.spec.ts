import { describe, expect, it } from 'vitest'
import type { InvitationEffectiveStatus, InvitationListItem } from '@/types'
import {
  canCopyInvitation,
  canExpireInvitation,
  invitationStatusMeta,
} from './workspaceInvitation'

function invitation(effectiveStatus: InvitationEffectiveStatus): InvitationListItem {
  return {
    id: 'invitation-1',
    tokenPreview: '1234…cdef',
    effectiveStatus,
    expiresAt: null,
    maxUses: null,
    useCount: 0,
    status: 'active',
    createdAt: '2026-09-24T00:00:00',
  }
}

describe('workspace invitation presentation', () => {
  it('有效和已达上限状态允许复制及失效', () => {
    expect(canCopyInvitation(invitation('active'))).toBe(true)
    expect(canCopyInvitation(invitation('exhausted'))).toBe(true)
    expect(canExpireInvitation(invitation('active'))).toBe(true)
  })

  it('过期和已失效状态禁止复制及失效', () => {
    expect(canCopyInvitation(invitation('expired'))).toBe(false)
    expect(canCopyInvitation(invitation('revoked'))).toBe(false)
    expect(canExpireInvitation(invitation('expired'))).toBe(false)
  })

  it.each([
    ['active', '有效', 'success'],
    ['exhausted', '已达上限', 'neutral'],
    ['expired', '已过期', 'danger'],
    ['revoked', '已失效', 'neutral'],
  ] as const)('%s 映射为 %s', (status, label, tone) => {
    expect(invitationStatusMeta(status)).toEqual({ label, tone })
  })
})
