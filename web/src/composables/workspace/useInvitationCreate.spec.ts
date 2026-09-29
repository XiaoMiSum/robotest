// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  createInvitation: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  authStore: {
    user: { id: 'user-me', username: 'qa-admin' } as { id: string; username: string } | null,
    activeWorkspace: { id: 'ws-1', name: '质量中台' } as { id: string; name: string } | null,
  },
}))

vi.mock('@/services/workspace', () => ({ createInvitation: mocks.createInvitation }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))

import { useInvitationCreate } from './useInvitationCreate'

beforeEach(() => {
  vi.clearAllMocks()
  mocks.createInvitation.mockResolvedValue({ token: 'plain-token' })
})

describe('useInvitationCreate', () => {
  it('打开弹窗会重置过期时间与最大次数', () => {
    const sut = useInvitationCreate(() => {})
    sut.setExpiresAt('2026-10-01T00:00:00')
    sut.setMaxUses(50)

    sut.openCreateDialog()

    expect(sut.createDialogVisible.value).toBe(true)
    expect(sut.createForm.expiresAt).toBe('')
    expect(sut.createForm.maxUses).toBeNull()
  })

  it('提交成功生成完整链接并打开结果弹窗', async () => {
    const onCreated = vi.fn()
    const sut = useInvitationCreate(onCreated)
    sut.createForm.expiresAt = '2026-10-01T00:00:00'
    sut.createForm.maxUses = 20

    await sut.submitCreateInvitation()

    expect(mocks.createInvitation).toHaveBeenCalledWith({
      expiresAt: '2026-10-01T00:00:00',
      maxUses: 20,
    })
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('邀请链接已创建')
    expect(sut.createdInviteLink.value).toContain('/join?token=plain-token')
    expect(sut.createDialogVisible.value).toBe(false)
    expect(sut.linkDialogVisible.value).toBe(true)
    expect(onCreated).toHaveBeenCalledTimes(1)
    expect(sut.createSubmitting.value).toBe(false)
  })

  it('过期时间留空时提交 null', async () => {
    const sut = useInvitationCreate(() => {})

    await sut.submitCreateInvitation()

    expect(mocks.createInvitation).toHaveBeenCalledWith({ expiresAt: null, maxUses: null })
  })

  it('提交失败按后端消息提示并保持弹窗打开', async () => {
    mocks.createInvitation.mockRejectedValue(new Error('参数不合法'))
    const sut = useInvitationCreate(() => {})
    sut.openCreateDialog()

    await sut.submitCreateInvitation()

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('参数不合法')
    expect(sut.createDialogVisible.value).toBe(true)
    expect(sut.linkDialogVisible.value).toBe(false)
    expect(sut.createSubmitting.value).toBe(false)
  })

  it('复制链接写入含工作空间与邀请人的说明文案', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    const sut = useInvitationCreate(() => {})

    await sut.handleCopyLink('https://example.com/join?token=abc')

    const copied = writeText.mock.calls[0]?.[0] as string
    expect(copied).toContain('【RoboTest 工作空间邀请】')
    expect(copied).toContain('工作空间：质量中台')
    expect(copied).toContain('邀请人：qa-admin')
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('邀请链接及说明已复制')
  })

  it('无剪贴板权限时提示手动复制', async () => {
    Object.defineProperty(window.navigator, 'clipboard', {
      value: undefined,
      configurable: true,
    })
    const sut = useInvitationCreate(() => {})

    await sut.handleCopyLink('https://example.com/join?token=abc')

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('当前浏览器不支持自动复制')
  })
})
