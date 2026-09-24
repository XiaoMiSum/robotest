import { describe, expect, it } from 'vitest'
import type { InvitationEffectiveStatus, InvitationListItem } from '@/types'
import {
  buildInvitationShareText,
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
  it('生成包含工作空间、链接和加入说明的复制文本', () => {
    expect(
      buildInvitationShareText('https://example.com/join?token=abc', '质量中台'),
    ).toBe(
      [
        '【RoboTest 工作空间邀请】',
        '工作空间：质量中台',
        '邀请链接：https://example.com/join?token=abc',
        '说明：打开链接后按页面提示完成身份验证并加入工作空间。',
      ].join('\n'),
    )
  })

  it('工作空间缺失时使用兜底文案，达上限时追加提醒', () => {
    const text = buildInvitationShareText('https://example.com/join?token=abc', '  ', true)

    expect(text).toContain('工作空间：当前工作空间')
    expect(text).toContain('说明：该邀请已达使用上限，加入前请先确认剩余名额。')
  })

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
