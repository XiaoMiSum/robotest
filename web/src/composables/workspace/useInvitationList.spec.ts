// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { InvitationListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchInvitations: vi.fn(),
  fetchInvitationCopyLink: vi.fn(),
  revokeInvitation: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
  authStore: {
    hasPermission: vi.fn(() => true),
    user: { id: 'user-me', username: 'qa-admin' } as { id: string; username: string } | null,
    activeWorkspace: { id: 'ws-1', name: '质量中台' } as { id: string; name: string } | null,
    // Pinia 会把 ref 解包成布尔量，这里按解包后的形态打桩
    permissionsLoaded: true,
    whenPermissionsReady: vi.fn(() => Promise.resolve()),
  },
}))

vi.mock('@/services/workspace', () => ({
  fetchInvitations: mocks.fetchInvitations,
  fetchInvitationCopyLink: mocks.fetchInvitationCopyLink,
  revokeInvitation: mocks.revokeInvitation,
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useInvitationList } from './useInvitationList'

function makeInvitation(overrides: Partial<InvitationListItem> = {}): InvitationListItem {
  return {
    id: 'inv-1',
    tokenPreview: '8f3a…d21c',
    effectiveStatus: 'active',
    expiresAt: null,
    maxUses: 20,
    useCount: 7,
    status: 'active',
    createdAt: '2026-09-01T02:00:00',
    ...overrides,
  }
}

function stubClipboard(writeText = vi.fn().mockResolvedValue(undefined)): typeof writeText {
  Object.defineProperty(window.navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  })
  return writeText
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.authStore.hasPermission.mockReturnValue(true)
  mocks.authStore.permissionsLoaded = true
  mocks.authStore.whenPermissionsReady.mockResolvedValue(undefined)
  stubClipboard()
  mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  mocks.fetchInvitations.mockResolvedValue({ list: [makeInvitation()], total: 3 })
  mocks.fetchInvitationCopyLink.mockResolvedValue({ token: 'plain-token' })
  mocks.revokeInvitation.mockResolvedValue(undefined)
})

describe('useInvitationList', () => {
  it('无邀请管理权限时不发起列表请求', async () => {
    mocks.authStore.hasPermission.mockReturnValue(false)
    const sut = useInvitationList()

    await expect(sut.loadInvitations()).resolves.toBe(false)

    expect(mocks.fetchInvitations).not.toHaveBeenCalled()
  })

  it('加载成功记录总数与已加载标记，翻页传递页码', async () => {
    const sut = useInvitationList()

    await expect(sut.loadInvitations()).resolves.toBe(true)
    expect(mocks.fetchInvitations).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20 })
    expect(sut.invitations.value).toHaveLength(1)
    expect(sut.invitationTotal.value).toBe(3)
    expect(sut.invitationsLoaded.value).toBe(true)
    expect(sut.invitationsLoadError.value).toBe(false)

    sut.handleInvitationPageChange(2)
    await vi.waitFor(() =>
      expect(mocks.fetchInvitations).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: 20 }),
    )
  })

  it('加载失败时提示并保持未加载状态', async () => {
    mocks.fetchInvitations.mockRejectedValue(new Error('邀请接口失败'))
    const sut = useInvitationList()

    await expect(sut.loadInvitations()).resolves.toBe(false)

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('邀请接口失败')
    expect(sut.invitationsLoaded.value).toBe(false)
    expect(sut.invitationsLoading.value).toBe(false)
    expect(sut.invitationsLoadError.value).toBe(true)
  })

  it('权限晚到时先等待就绪再拉列表，避免刷新进入只拉到成员', async () => {
    let releasePermissions!: () => void
    mocks.authStore.permissionsLoaded = false
    mocks.authStore.whenPermissionsReady.mockReturnValue(
      new Promise<void>((resolve) => {
        releasePermissions = resolve
      }),
    )
    const sut = useInvitationList()

    const load = sut.loadInvitations()

    // 权限未就绪期间不得抢跑列表请求，否则拿到的是旧权限下的误判
    await Promise.resolve()
    expect(mocks.fetchInvitations).not.toHaveBeenCalled()

    releasePermissions()
    await expect(load).resolves.toBe(true)
    expect(mocks.fetchInvitations).toHaveBeenCalledTimes(1)
  })

  it('等待权限就绪后仍无权限时不发起列表请求', async () => {
    mocks.authStore.permissionsLoaded = false
    mocks.authStore.whenPermissionsReady.mockResolvedValue(undefined)
    mocks.authStore.hasPermission.mockReturnValue(false)
    const sut = useInvitationList()

    await expect(sut.loadInvitations()).resolves.toBe(false)

    expect(mocks.authStore.whenPermissionsReady).toHaveBeenCalledTimes(1)
    expect(mocks.fetchInvitations).not.toHaveBeenCalled()
    expect(sut.invitationsLoadError.value).toBe(false)
  })

  it('复制行链接写入完整 URL 与邀请说明', async () => {
    const writeText = stubClipboard()
    const sut = useInvitationList()

    await sut.copyInvitation(makeInvitation())

    expect(mocks.fetchInvitationCopyLink).toHaveBeenCalledWith('inv-1')
    const copied = writeText.mock.calls[0]?.[0] as string
    expect(copied).toContain('邀请链接：')
    expect(copied).toContain('/join?token=plain-token')
    expect(copied).toContain('工作空间：质量中台')
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('邀请链接及说明已复制')
    expect(sut.copyingInvitationId.value).toBe('')
  })

  it('已过期链接不可复制', async () => {
    const sut = useInvitationList()

    await sut.copyInvitation(makeInvitation({ effectiveStatus: 'expired' }))

    expect(mocks.fetchInvitationCopyLink).not.toHaveBeenCalled()
  })

  it('复制页头快捷链接时先加载列表并只取有效邀请', async () => {
    const writeText = stubClipboard()
    const sut = useInvitationList()

    await sut.handleCopyLatestInvitation()

    expect(mocks.fetchInvitations).toHaveBeenCalledTimes(1)
    expect(writeText).toHaveBeenCalledTimes(1)
    expect(sut.copyingLatestInvitation.value).toBe(false)
  })

  it('没有有效邀请时提示且不请求复制接口', async () => {
    mocks.fetchInvitations.mockResolvedValue({ list: [], total: 0 })
    const sut = useInvitationList()

    await sut.handleCopyLatestInvitation()

    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('暂无可复制的有效邀请链接')
    expect(mocks.fetchInvitationCopyLink).not.toHaveBeenCalled()
    expect(sut.copyingLatestInvitation.value).toBe(false)
  })

  it('取消失效确认时不调用接口', async () => {
    mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
    const sut = useInvitationList()

    await sut.handleExpireInvitation(makeInvitation())

    expect(mocks.revokeInvitation).not.toHaveBeenCalled()
  })

  it('失效成功后提示并刷新列表', async () => {
    const sut = useInvitationList()
    await sut.loadInvitations()
    expect(mocks.fetchInvitations).toHaveBeenCalledTimes(1)

    await sut.handleExpireInvitation(makeInvitation())

    expect(mocks.revokeInvitation).toHaveBeenCalledWith('inv-1')
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('邀请链接已失效')
    await vi.waitFor(() => expect(mocks.fetchInvitations).toHaveBeenCalledTimes(2))
    expect(sut.revokingInvitationId.value).toBe('')
  })

  it('失效失败按后端消息提示', async () => {
    mocks.revokeInvitation.mockRejectedValue(new Error('链接状态已变更'))
    const sut = useInvitationList()

    await sut.handleExpireInvitation(makeInvitation())

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('链接状态已变更')
    expect(sut.revokingInvitationId.value).toBe('')
  })

  it('dispose 后在途响应不再写回状态', async () => {
    let resolveLoad!: (value: { list: InvitationListItem[]; total: number }) => void
    mocks.fetchInvitations.mockReturnValue(
      new Promise<{ list: InvitationListItem[]; total: number }>((resolve) => {
        resolveLoad = resolve
      }),
    )
    const sut = useInvitationList()

    const load = sut.loadInvitations()
    sut.dispose()
    resolveLoad({ list: [makeInvitation()], total: 3 })
    await load

    expect(sut.invitations.value).toEqual([])
    expect(sut.invitationsLoaded.value).toBe(false)
    expect(sut.invitationsLoading.value).toBe(false)
  })
})
