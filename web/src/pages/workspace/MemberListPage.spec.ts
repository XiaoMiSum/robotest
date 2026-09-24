import { describe, expect, it } from 'vitest'
import pageSource from './MemberListPage.vue?raw'

describe('MemberListPage demo structure', () => {
  it('保留成员与邀请链接标签页模式', () => {
    expect(pageSource).toContain('成员列表')
    expect(pageSource).toContain('邀请链接')
    expect(pageSource).toContain('<el-tabs')
    expect(pageSource).toContain('name="members"')
    expect(pageSource).toContain('name="invitations"')
  })

  it('按演示稿提供页头操作、角色筛选和链接式行操作', () => {
    expect(pageSource).toContain('复制邀请链接')
    expect(pageSource).toContain('邀请成员')
    expect(pageSource).toContain('姓名 / 邮箱')
    expect(pageSource).toContain('全部角色')
    expect(pageSource).toContain('改角色')
    expect(pageSource).toContain('移除')
    expect(pageSource).toContain('生成链接')
  })

  it('邀请过期时间按统一时间工具提交和展示', () => {
    expect(pageSource).toContain('value-format="YYYY-MM-DDTHH:mm:ss"')
    expect(pageSource).toContain('formatDateTime(row.expiresAt)')
  })

  it('使用 border-card 标签页并将生成链接操作放在页头', () => {
    expect(pageSource).toContain('type="border-card"')
    expect(pageSource).toContain('class="member-page__tabs"')
    expect(pageSource).toContain('.member-page__tabs :deep(.el-tabs__content)')
    expect(pageSource).toContain('生成链接')
    expect(pageSource).toContain('member-page__head-actions')
    expect(pageSource).toContain('<el-button v-if="canManageMember" type="primary" @click="openAddDialog">')
    expect(pageSource).toContain('<el-button v-if="canManageInvitation" @click="openCreateDialog">')
    expect(pageSource).toContain('link\n          :loading="invitationsLoading || copyingLatestInvitation"')
    expect(pageSource).not.toContain('<el-card shadow="never" class="member-page__card"')
  })

  it('邀请链接使用脱敏预览和按需复制接口', () => {
    expect(pageSource).toContain('row.tokenPreview')
    expect(pageSource).toContain('fetchInvitationCopyLink')
    expect(pageSource).toContain('buildInvitationCopyText')
    expect(pageSource).toContain('inviterUsername: authStore.user?.username')
    expect(pageSource).toContain('邀请链接及说明已复制')
    expect(pageSource).toContain('invitationStatusMeta')
    expect(pageSource).not.toContain('invitation.token }}')
  })
})
