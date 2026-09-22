import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConfirmCardState } from '@/composables/assistant/assistantConfirm'

const mocks = vi.hoisted(() => ({
  filterAssistantLinks: vi.fn<(markdown: string) => string>(),
  collectRegisteredPrefixes: vi.fn<() => string[]>(() => ['/workspace', '/admin']),
  useAssistantContextStore: vi.fn(),
  useAuthStore: vi.fn(),
  resolveConfirmStatus: vi.fn<() => 'waiting' | 'expired' | 'approved' | 'cancelled' | 'failed'>(),
  formatCountdown: vi.fn<() => string>(() => '05:00'),
  remainingMs: vi.fn<() => number>(() => 300000),
  parseConfirmPreview: vi.fn<() => Record<string, unknown>>(() => ({ key: 'val' })),
  routerGetRoutes: vi.fn<() => Array<{ path: string }>>(() => []),
  ElMessage: { warning: vi.fn() },
}))

vi.mock('@/composables/assistant/assistantLinkWhitelist', () => ({
  filterAssistantLinks: mocks.filterAssistantLinks,
  collectRegisteredPrefixes: mocks.collectRegisteredPrefixes,
}))

vi.mock('@/stores/assistantContext', () => ({
  useAssistantContextStore: mocks.useAssistantContextStore,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/composables/assistant/assistantConfirm', () => ({
  resolveConfirmStatus: mocks.resolveConfirmStatus,
  formatCountdown: mocks.formatCountdown,
  remainingMs: mocks.remainingMs,
  parseConfirmPreview: mocks.parseConfirmPreview,
}))

vi.mock('@/router', () => ({
  default: { getRoutes: mocks.routerGetRoutes },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useMessageItem, type AssistantMessageItem, type UseMessageItemOptions } from './useMessageItem'

function makeMessage(overrides?: Partial<AssistantMessageItem>): AssistantMessageItem {
  return {
    id: 'msg-1',
    role: 'assistant',
    content: 'Hello',
    createdAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function setupStores(authOverrides?: { username?: string; avatarUrl?: string; permissions?: string[] }) {
  const authStore = {
    username: authOverrides?.username ?? 'TestUser',
    avatarUrl: authOverrides?.avatarUrl ?? '',
    hasPermission: vi.fn<(code: string) => boolean>((code) => (authOverrides?.permissions ?? []).includes(code)),
  }
  const assistantContext = {
    dslHost: null as null | { documentId: string; buildPlan: ReturnType<typeof vi.fn> },
    selectedNodeId: 'node-1',
  }
  mocks.useAuthStore.mockReturnValue(authStore)
  mocks.useAssistantContextStore.mockReturnValue(assistantContext)
  return { authStore, assistantContext }
}

function init(opts?: { message?: AssistantMessageItem; onConfirm?: ReturnType<typeof vi.fn>; onCancel?: ReturnType<typeof vi.fn>; onConfirmDsl?: ReturnType<typeof vi.fn>; onCancelDsl?: ReturnType<typeof vi.fn>; authOverrides?: { username?: string; avatarUrl?: string; permissions?: string[] } }) {
  const { authStore, assistantContext } = setupStores(opts?.authOverrides)
  const options: UseMessageItemOptions = {
    message: opts?.message ?? makeMessage(),
    onConfirm: opts?.onConfirm ?? vi.fn(),
    onCancel: opts?.onCancel ?? vi.fn(),
    onConfirmDsl: opts?.onConfirmDsl ?? vi.fn(),
    onCancelDsl: opts?.onCancelDsl ?? vi.fn(),
  }
  const result = useMessageItem(options)
  return { result, authStore, assistantContext, options }
}

describe('useMessageItem', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.filterAssistantLinks.mockImplementation((md) => md)
    mocks.collectRegisteredPrefixes.mockReturnValue(['/workspace', '/admin'])
    mocks.resolveConfirmStatus.mockReturnValue('waiting')
    mocks.formatCountdown.mockReturnValue('05:00')
    mocks.remainingMs.mockReturnValue(300000)
    mocks.parseConfirmPreview.mockReturnValue({ key: 'val' })
  })

  describe('isUser / isTool', () => {
    it('user message => isUser true, isTool false', () => {
      const { result } = init({ message: makeMessage({ role: 'user' }) })
      expect(result.isUser.value).toBe(true)
      expect(result.isTool.value).toBe(false)
    })

    it('tool message => isTool true, isUser false', () => {
      const { result } = init({ message: makeMessage({ role: 'tool' }) })
      expect(result.isTool.value).toBe(true)
      expect(result.isUser.value).toBe(false)
    })

    it('assistant message => both false', () => {
      const { result } = init({ message: makeMessage({ role: 'assistant' }) })
      expect(result.isUser.value).toBe(false)
      expect(result.isTool.value).toBe(false)
    })
  })

  describe('safeContent', () => {
    it('calls filterAssistantLinks and returns result', () => {
      mocks.filterAssistantLinks.mockReturnValue('filtered')
      const { result } = init({ message: makeMessage({ content: 'some content' }) })
      expect(result.safeContent.value).toBe('filtered')
      expect(mocks.filterAssistantLinks).toHaveBeenCalled()
    })

    it('defaults to empty string when content is undefined', () => {
      const { result } = init({ message: makeMessage({ content: undefined as unknown as string }) })
      expect(result.safeContent.value).toBeDefined()
    })
  })

  describe('userAvatarUrl / userAvatarChar', () => {
    it('returns avatarUrl from auth store', () => {
      const { result } = init({ authOverrides: { avatarUrl: 'https://img.test/avatar.png' } })
      expect(result.userAvatarUrl.value).toBe('https://img.test/avatar.png')
    })

    it('returns empty string when avatarUrl is blank', () => {
      const { result } = init({ authOverrides: { avatarUrl: '   ' } })
      expect(result.userAvatarUrl.value).toBe('')
    })

    it('returns first char of username uppercased', () => {
      const { result } = init({ authOverrides: { username: 'alice' } })
      expect(result.userAvatarChar.value).toBe('A')
    })

    it('returns ? when username is empty', () => {
      const { result } = init({ authOverrides: { username: '' } })
      expect(result.userAvatarChar.value).toBe('?')
    })

    it('returns ? when username is whitespace', () => {
      const { result } = init({ authOverrides: { username: '   ' } })
      expect(result.userAvatarChar.value).toBe('?')
    })
  })

  describe('confirmStatus / confirmWaiting / confirmStatusLabel', () => {
    it('returns null when no confirmCard', () => {
      const { result } = init({ message: makeMessage({ confirmCard: null }) })
      expect(result.confirmStatus.value).toBeNull()
      expect(result.confirmWaiting.value).toBe(false)
    })

    it('delegates to resolveConfirmStatus', () => {
      const card: ConfirmCardState = { confirmToken: 't1', toolName: 'write', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'waiting' }
      mocks.resolveConfirmStatus.mockReturnValue('waiting')
      const { result } = init({ message: makeMessage({ confirmCard: card }) })
      expect(result.confirmStatus.value).toBe('waiting')
      expect(result.confirmWaiting.value).toBe(true)
    })

    it('confirmStatusLabel returns 已超时 for expired', () => {
      mocks.resolveConfirmStatus.mockReturnValue('expired')
      const { result } = init({ message: makeMessage({ confirmCard: { confirmToken: 't1', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'expired' } }) })
      expect(result.confirmStatusLabel.value).toBe('已超时')
    })

    it('confirmStatusLabel returns 执行成功 for approved', () => {
      mocks.resolveConfirmStatus.mockReturnValue('approved')
      const { result } = init({ message: makeMessage({ confirmCard: { confirmToken: 't1', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'approved' } }) })
      expect(result.confirmStatusLabel.value).toBe('执行成功')
    })

    it('confirmStatusLabel returns 已取消 for cancelled', () => {
      mocks.resolveConfirmStatus.mockReturnValue('cancelled')
      const { result } = init({ message: makeMessage({ confirmCard: { confirmToken: 't1', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'cancelled' } }) })
      expect(result.confirmStatusLabel.value).toBe('已取消')
    })

    it('confirmStatusLabel returns 执行失败 for failed', () => {
      mocks.resolveConfirmStatus.mockReturnValue('failed')
      const { result } = init({ message: makeMessage({ confirmCard: { confirmToken: 't1', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'failed' } }) })
      expect(result.confirmStatusLabel.value).toBe('执行失败')
    })

    it('confirmStatusLabel returns empty for waiting', () => {
      mocks.resolveConfirmStatus.mockReturnValue('waiting')
      const { result } = init({ message: makeMessage({ confirmCard: { confirmToken: 't1', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'waiting' } }) })
      expect(result.confirmStatusLabel.value).toBe('')
    })
  })

  describe('countdownText', () => {
    it('returns empty string when no confirmCard', () => {
      const { result } = init({ message: makeMessage({ confirmCard: null }) })
      expect(result.countdownText.value).toBe('')
    })

    it('delegates to formatCountdown(remainingMs(...))', () => {
      const card: ConfirmCardState = { confirmToken: 't1', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'waiting' }
      mocks.remainingMs.mockReturnValue(120000)
      mocks.formatCountdown.mockReturnValue('02:00')
      const { result } = init({ message: makeMessage({ confirmCard: card }) })
      expect(result.countdownText.value).toBe('02:00')
      expect(mocks.remainingMs).toHaveBeenCalledWith(card, expect.any(Number))
      expect(mocks.formatCountdown).toHaveBeenCalledWith(120000)
    })
  })

  describe('previewFields', () => {
    it('returns empty array when no confirmCard', () => {
      const { result } = init({ message: makeMessage({ confirmCard: null }) })
      expect(result.previewFields.value).toEqual([])
    })

    it('returns Object.entries of parseConfirmPreview result', () => {
      mocks.parseConfirmPreview.mockReturnValue({ a: 1, b: 2 })
      const card: ConfirmCardState = { confirmToken: 't1', toolName: 'w', preview: '{"a":1,"b":2}', expiresAt: '2099-01-01T00:00:00', status: 'waiting' }
      const { result } = init({ message: makeMessage({ confirmCard: card }) })
      expect(result.previewFields.value).toEqual([['a', 1], ['b', 2]])
    })
  })

  describe('handleConfirm / handleCancel', () => {
    it('handleConfirm calls onConfirm with confirmToken', () => {
      const onConfirm = vi.fn()
      const card: ConfirmCardState = { confirmToken: 'token-abc', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'waiting' }
      const { result } = init({ onConfirm, message: makeMessage({ confirmCard: card }) })
      result.handleConfirm()
      expect(onConfirm).toHaveBeenCalledWith('token-abc')
    })

    it('handleConfirm does nothing when no confirmCard', () => {
      const onConfirm = vi.fn()
      const { result } = init({ onConfirm, message: makeMessage({ confirmCard: null }) })
      result.handleConfirm()
      expect(onConfirm).not.toHaveBeenCalled()
    })

    it('handleCancel calls onCancel with confirmToken', () => {
      const onCancel = vi.fn()
      const card: ConfirmCardState = { confirmToken: 'token-xyz', toolName: 'w', preview: '{}', expiresAt: '2099-01-01T00:00:00', status: 'waiting' }
      const { result } = init({ onCancel, message: makeMessage({ confirmCard: card }) })
      result.handleCancel()
      expect(onCancel).toHaveBeenCalledWith('token-xyz')
    })

    it('handleCancel does nothing when no confirmCard', () => {
      const onCancel = vi.fn()
      const { result } = init({ onCancel, message: makeMessage({ confirmCard: null }) })
      result.handleCancel()
      expect(onCancel).not.toHaveBeenCalled()
    })
  })

  describe('dslPreviewVisible / dslPlan', () => {
    it('dslPreviewVisible is initially false', () => {
      const { result } = init()
      expect(result.dslPreviewVisible.value).toBe(false)
    })

    it('dslPlan is initially null', () => {
      const { result } = init()
      expect(result.dslPlan.value).toBeNull()
    })
  })

  describe('openDslPreview', () => {
    it('shows warning when no permission', () => {
      const { result } = init({ authOverrides: { permissions: [] } })
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('无文档编辑权限，无法预览编辑指令')
    })

    it('shows warning when no dslCommands', () => {
      const { result } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: null }) })
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请回到文档后重试')
    })

    it('shows warning when no dslHost', () => {
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = null
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请回到文档后重试')
    })

    it('shows warning when dslHost documentId mismatch', () => {
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = { documentId: 'doc-2', buildPlan: vi.fn() }
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请回到文档后重试')
    })

    it('shows warning when buildPlan returns not ok (too-many)', () => {
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = {
        documentId: 'doc-1',
        buildPlan: vi.fn().mockReturnValue({ ok: false, reason: { kind: 'too-many' } }),
      }
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('指令数量超过上限（10 条），请分批执行')
    })

    it('shows warning when buildPlan returns not ok (no-selected)', () => {
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = {
        documentId: 'doc-1',
        buildPlan: vi.fn().mockReturnValue({ ok: false, reason: { kind: 'no-selected' } }),
      }
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('未选中对应节点，请先选中目标节点后重试')
    })

    it('shows warning when buildPlan returns not ok (ambiguous)', () => {
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = {
        documentId: 'doc-1',
        buildPlan: vi.fn().mockReturnValue({ ok: false, reason: { kind: 'ambiguous' } }),
      }
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('存在多个同名节点，请补充说明以精确定位')
    })

    it('shows warning when buildPlan returns not ok (unknown kind)', () => {
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = {
        documentId: 'doc-1',
        buildPlan: vi.fn().mockReturnValue({ ok: false, reason: { kind: 'zero-hit' } }),
      }
      result.openDslPreview()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('未找到匹配节点，请检查节点标题')
    })

    it('sets dslPlan and shows preview when buildPlan succeeds', () => {
      const plan = { commands: [], totalHits: 0, totalSkipped: 0 }
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = {
        documentId: 'doc-1',
        buildPlan: vi.fn().mockReturnValue({ ok: true, plan }),
      }
      result.openDslPreview()
      expect(result.dslPlan.value).toEqual(plan)
      expect(result.dslPreviewVisible.value).toBe(true)
    })
  })

  describe('handleConfirmDsl', () => {
    it('calls onConfirmDsl with plan and closes preview', () => {
      const onConfirmDsl = vi.fn()
      const plan = { commands: [], totalHits: 0, totalSkipped: 0 }
      const { result, assistantContext } = init({ authOverrides: { permissions: ['case:edit'] }, onConfirmDsl, message: makeMessage({ dslCommands: { documentId: 'doc-1', commands: [] } }) })
      assistantContext.dslHost = {
        documentId: 'doc-1',
        buildPlan: vi.fn().mockReturnValue({ ok: true, plan }),
      }
      result.openDslPreview()
      result.handleConfirmDsl()
      expect(onConfirmDsl).toHaveBeenCalledWith(plan)
      expect(result.dslPreviewVisible.value).toBe(false)
    })

    it('does not call onConfirmDsl when dslPlan is null', () => {
      const onConfirmDsl = vi.fn()
      const { result } = init({ onConfirmDsl })
      result.handleConfirmDsl()
      expect(onConfirmDsl).not.toHaveBeenCalled()
      expect(result.dslPreviewVisible.value).toBe(false)
    })
  })

  describe('handleCancelDsl', () => {
    it('calls onCancelDsl and closes preview', () => {
      const onCancelDsl = vi.fn()
      const { result } = init({ onCancelDsl })
      result.handleCancelDsl()
      expect(onCancelDsl).toHaveBeenCalled()
      expect(result.dslPreviewVisible.value).toBe(false)
    })
  })
})
