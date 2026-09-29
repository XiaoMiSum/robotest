import { describe, expect, it } from 'vitest'
import pageSource from './MemberListPage.vue?raw'
import memberPanelSource from '@/components/workspace/MemberListPanel.vue?raw'
import invitationPanelSource from '@/components/workspace/InvitationListPanel.vue?raw'
import inviteDialogSource from '@/components/workspace/MemberInviteDialog.vue?raw'
import createDialogSource from '@/components/workspace/InvitationCreateDialog.vue?raw'

describe('MemberListPage demo structure', () => {
  it('保留页头操作并以并排双卡片组织内容', () => {
    expect(pageSource).toContain('复制邀请链接')
    expect(pageSource).toContain('邀请成员')
    expect(pageSource).toContain('生成链接')
    expect(pageSource).toContain('<MemberListPanel')
    expect(pageSource).toContain('<InvitationListPanel')
    expect(pageSource).toContain('class="member-page__split"')
    expect(pageSource).toContain('member-page__split--solo')
    expect(pageSource).toContain('grid-template-columns: repeat(2, minmax(0, 1fr))')
    expect(pageSource).toContain('member-card-in')
    expect(pageSource).toContain('prefers-reduced-motion')
  })

  it('使用下划线式页头按钮且生成链接操作放在页头', () => {
    expect(pageSource).toContain('class="member-page__head-actions"')
    expect(pageSource).toContain(
      '<el-button v-if="canManageMember" type="primary" @click="openAddDialog">',
    )
    expect(pageSource).toContain('<el-button v-if="canManageInvitation" @click="openCreateDialog">')
    expect(pageSource).toMatch(/link\s+:loading="invitationsLoading \|\| copyingLatestInvitation"/)
    expect(pageSource).not.toContain('type="border-card"')
    expect(pageSource).not.toContain('<el-card shadow="never" class="member-page__card"')
  })

  it('不再保留标签页结构', () => {
    expect(pageSource).not.toContain('<el-tabs')
    expect(pageSource).not.toContain('<el-tab-pane')
    expect(pageSource).not.toContain('member-page__tabs')
    expect(pageSource).not.toContain('member-page-pane-in')
  })

  it('按演示稿提供筛选、链接式行操作与统一表头高度', () => {
    expect(memberPanelSource).toContain('姓名 / 邮箱')
    expect(memberPanelSource).toContain('全部角色')
    expect(memberPanelSource).toContain('改角色')
    expect(memberPanelSource).toContain('移除')
    expect(memberPanelSource).toContain('min-height: 65px')
    expect(invitationPanelSource).toContain('失效')
    expect(invitationPanelSource).toContain('formatDateTime(row.expiresAt)')
    expect(invitationPanelSource).toContain('min-height: 65px')
  })

  it('邀请过期时间按统一时间工具提交和展示', () => {
    expect(createDialogSource).toContain('value-format="YYYY-MM-DDTHH:mm:ss"')
    expect(inviteDialogSource).toContain('输入姓名、用户名或邮箱搜索')
    expect(memberPanelSource).toContain('formatDateTime(row.joinedAt)')
  })

  it('进入页面同时拉取成员与邀请列表', () => {
    expect(pageSource).toContain('void loadMembers()')
    expect(pageSource).toContain('void loadInvitations()')
    // 权限晚到由 composable 内部等待，页面不再按权限提前跳过拉取
    expect(pageSource).not.toMatch(/if \(canManageInvitation\.value\) \{/)
    expect(pageSource).toContain(':load-error="membersLoadError"')
    expect(pageSource).toContain(':load-error="invitationsLoadError"')
    expect(pageSource).toContain('@retry="loadMembers"')
    expect(pageSource).toContain('@retry="loadInvitations"')
  })

  it('双卡片提供统一空态与失败可重试引导', () => {
    expect(memberPanelSource).toContain('<template #empty>')
    expect(memberPanelSource).toContain('成员列表加载失败')
    expect(memberPanelSource).toContain('重新加载')
    expect(memberPanelSource).toContain('点击右上角「邀请成员」邀请加入')
    expect(memberPanelSource).toContain('loadError: boolean')
    expect(memberPanelSource).not.toContain('empty-text=')

    expect(invitationPanelSource).toContain('<template #empty>')
    expect(invitationPanelSource).toContain('邀请链接加载失败')
    expect(invitationPanelSource).toContain('点击右上角「生成链接」创建第一个邀请')
    expect(invitationPanelSource).toContain('loadError: boolean')
    expect(invitationPanelSource).not.toContain('empty-text=')
  })

  it('双卡片容器按窗口取固定高度并交由表体内部滚动', () => {
    expect(pageSource).toContain('height: 100%')
    expect(pageSource).toContain('min-height: 0')
    expect(pageSource).toContain('.member-page__split :deep(.el-table__cell)')
    // 表格与卡片容器不留边距，单元格留白承担 24px 左基线
    expect(memberPanelSource).not.toContain('padding: 0 var(--space-md)')
    expect(memberPanelSource).toContain('height="100%"')
    expect(invitationPanelSource).toContain('height="100%"')
    // 状态列定宽，不再随剩余空间被拉伸
    expect(invitationPanelSource).toContain('label="状态" width="120"')
  })
})
