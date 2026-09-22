import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { AiConversation, AiMessage } from '@/types'

const mocks = vi.hoisted(() => ({
  createConversation: vi.fn<() => Promise<AiConversation>>(),
  deleteConversation: vi.fn<() => Promise<void>>(),
  fetchMessages: vi.fn<() => Promise<AiMessage[]>>(),
  cancelConfirmation: vi.fn<() => Promise<void>>(),
  APPROVE_CONFIRMATION_URL: '/workspace/ai/confirmations/approve',
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  useAuthStore: vi.fn(),
  useAiStore: vi.fn(),
  useAssistantContextStore: vi.fn(),
  useAssistantStream: vi.fn(),
  useConversationList: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/stores/ai', () => ({
  useAiStore: mocks.useAiStore,
}))

vi.mock('@/stores/assistantContext', () => ({
  useAssistantContextStore: mocks.useAssistantContextStore,
}))

vi.mock('@/composables/assistant/useAssistantStream', () => ({
  useAssistantStream: mocks.useAssistantStream,
}))

vi.mock('@/composables/assistant/useConversationList', () => ({
  useConversationList: mocks.useConversationList,
}))

vi.mock('@/services/assistant', () => ({
  createConversation: mocks.createConversation,
  deleteConversation: mocks.deleteConversation,
  fetchMessages: mocks.fetchMessages,
  cancelConfirmation: mocks.cancelConfirmation,
  APPROVE_CONFIRMATION_URL: mocks.APPROVE_CONFIRMATION_URL,
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => { cb() },
    onBeforeUnmount: () => {},
  }
})

import { useAssistantPanel } from './useAssistantPanel'
import type { UseAssistantPanelOptions } from './useAssistantPanel'

const fakeWindow = { setTimeout: vi.fn((..._args: unknown[]) => 1 as never), clearTimeout: vi.fn() }

let conversationItems: ReturnType<typeof ref<AiConversation[]>>
let refreshConversations: ReturnType<typeof vi.fn>
let loadMoreConversations: ReturnType<typeof vi.fn>
let prependConversation: ReturnType<typeof vi.fn>

function setupMocks(overrides?: {
  workspaceId?: string | null
  hasPermission?: (code: string) => boolean
  dslHost?: unknown
}) {
  conversationItems = ref<AiConversation[]>([])
  refreshConversations = vi.fn().mockResolvedValue(undefined)
  loadMoreConversations = vi.fn().mockResolvedValue(undefined)
  prependConversation = vi.fn()

  mocks.useConversationList.mockReturnValue({
    items: conversationItems,
    loading: { value: false },
    loadingMore: { value: false },
    refresh: refreshConversations,
    loadMore: loadMoreConversations,
    prepend: prependConversation,
  })

  mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })

  const ws = overrides?.workspaceId === undefined ? 'ws-1' : overrides?.workspaceId
  mocks.useAuthStore.mockReturnValue({
    activeWorkspace: ws ? { id: ws, name: '测试空间' } : null,
    hasPermission: overrides?.hasPermission ?? (() => true),
  })
  mocks.useAiStore.mockReturnValue({ selectedModelId: 'model-1' })
  mocks.useAssistantContextStore.mockReturnValue({
    buildPageContext: vi.fn().mockReturnValue({ projectId: 'p1' }),
    dslHost: overrides?.dslHost ?? null,
  })
}

function defaultOptions(): UseAssistantPanelOptions {
  return { onMinimize: vi.fn(), onClose: vi.fn() }
}

describe('useAssistantPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('window', fakeWindow)
  })

  describe('minimized', () => {
    it('options.minimized 为 true 时返回 true', () => {
      setupMocks()
      const panel = useAssistantPanel({ ...defaultOptions(), minimized: true })
      expect(panel.minimized.value).toBe(true)
    })

    it('options.minimized 为 false 时返回 false', () => {
      setupMocks()
      const panel = useAssistantPanel({ ...defaultOptions(), minimized: false })
      expect(panel.minimized.value).toBe(false)
    })

    it('options.minimized 未定义时返回 false', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.minimized.value).toBe(false)
    })
  })

  describe('noWorkspace', () => {
    it('有工作空间时返回 false', () => {
      setupMocks({ workspaceId: 'ws-1' })
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.noWorkspace.value).toBe(false)
    })

    it('无工作空间时返回 true', () => {
      setupMocks({ workspaceId: null })
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.noWorkspace.value).toBe(true)
    })
  })

  describe('初始状态', () => {
    it('activeConversationId 初始为 null', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.activeConversationId.value).toBeNull()
    })

    it('messages 初始为空数组', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.messages.value).toEqual([])
    })

    it('messagesLoading 初始为 false', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.messagesLoading.value).toBe(false)
    })

    it('streaming 初始为 false', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.streaming.value).toBe(false)
    })

    it('input 初始为空字符串', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(panel.input.value).toBe('')
    })
  })

  describe('onMounted', () => {
    it('初始化时调用 refreshConversations', () => {
      setupMocks()
      useAssistantPanel(defaultOptions())
      expect(refreshConversations).toHaveBeenCalled()
    })

    it('refresh 成功后有会话时自动切换到第一个', async () => {
      setupMocks()
      conversationItems.value = [{ id: 'c1', title: '会话1', lastActiveAt: '2025-01-01T00:00:00' }]
      const panel = useAssistantPanel(defaultOptions())
      await refreshConversations.mock.results[0].value
      expect(panel.activeConversationId.value).toBe('c1')
    })

    it('refresh 成功后无会话时不切换', async () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      await refreshConversations.mock.results[0].value
      expect(panel.activeConversationId.value).toBeNull()
    })

    it('refresh 成功后 activeConversationId 已存在时不切换', async () => {
      setupMocks()
      conversationItems.value = [{ id: 'c1', title: '会话1', lastActiveAt: '2025-01-01T00:00:00' }]
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'existing'
      await refreshConversations.mock.results[0].value
      expect(panel.activeConversationId.value).toBe('existing')
    })
  })

  describe('handleSend', () => {
    it('content 为空时不发送', async () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = '   '
      await panel.handleSend()
      expect(mocks.useAssistantStream).not.toHaveBeenCalled()
    })

    it('streaming 为 true 时不发送', async () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.streaming.value = true
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(mocks.useAssistantStream).not.toHaveBeenCalled()
    })

    it('无工作空间时提示警告', async () => {
      setupMocks({ workspaceId: null })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先选择工作空间')
    })

    it('无活跃会话时先创建会话', async () => {
      setupMocks()
      const newConv: AiConversation = { id: 'new-conv', title: '新会话', lastActiveAt: '2025-01-01T00:00:00' }
      mocks.createConversation.mockResolvedValue(newConv)
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(mocks.createConversation).toHaveBeenCalled()
      expect(panel.activeConversationId.value).toBe('new-conv')
      expect(prependConversation).toHaveBeenCalledWith(newConv)
    })

    it('创建会话失败时提示错误', async () => {
      setupMocks()
      mocks.createConversation.mockRejectedValue(new Error('fail'))
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('创建会话失败，请重试')
    })

    it('发送消息后 input 清空，消息列表包含 user 和 assistant 消息', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(panel.input.value).toBe('')
      expect(panel.messages.value).toHaveLength(2)
      expect(panel.messages.value[0].role).toBe('user')
      expect(panel.messages.value[0].content).toBe('hello')
      expect(panel.messages.value[1].role).toBe('assistant')
      expect(panel.messages.value[1].streaming).toBe(true)
    })

    it('有活跃会话时不创建新会话', async () => {
      setupMocks()
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'existing-conv'
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(mocks.createConversation).not.toHaveBeenCalled()
    })

    it('使用 useAssistantStream 发起流式请求', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      expect(mocks.useAssistantStream).toHaveBeenCalledWith(
        expect.objectContaining({
          url: '/workspace/ai/conversations/conv-1/messages',
          body: expect.objectContaining({ content: 'hello', modelId: 'model-1' }),
        }),
      )
    })

    it('发送前取消之前的 controller', async () => {
      setupMocks()
      const prevCancel = vi.fn()
      mocks.useAssistantStream.mockReturnValue({ cancel: prevCancel })
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'existing'
      panel.input.value = 'first'
      await panel.handleSend()
      expect(prevCancel).not.toHaveBeenCalled()
      panel.streaming.value = false
      const newCancel = vi.fn()
      mocks.useAssistantStream.mockReturnValue({ cancel: newCancel })
      panel.input.value = 'second'
      await panel.handleSend()
      expect(prevCancel).toHaveBeenCalledTimes(1)
      expect(newCancel).not.toHaveBeenCalled()
    })

    it('onDelta 回调追加内容到 assistant 消息', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let deltaFn: ((text: string) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        deltaFn = opts.handlers.onDelta
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      deltaFn?.('chunk1')
      deltaFn?.('chunk2')
      expect(panel.messages.value[1].content).toBe('chunk1chunk2')
    })

    it('onToolCall 回调添加 toolProcesses', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let toolCallFn: ((event: { toolName: string; summary: string }) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        toolCallFn = opts.handlers.onToolCall
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      toolCallFn?.({ toolName: 'search', summary: 'query' })
      expect(panel.messages.value[1].toolProcesses).toHaveLength(1)
      expect(panel.messages.value[1].toolProcesses?.[0].toolName).toBe('search')
      expect(panel.messages.value[1].toolProcesses?.[0].status).toBe('running')
    })

    it('onConfirmRequired 回调设置 confirmCard', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let confirmFn: ((event: { confirmToken: string; toolName: string; preview: string; expiresAt: string }) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        confirmFn = opts.handlers.onConfirmRequired
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      confirmFn?.({ confirmToken: 'tk1', toolName: 'write', preview: 'p', expiresAt: '2025-12-31' })
      const assistantMsg = panel.messages.value[1]
      expect(assistantMsg.confirmCard).not.toBeNull()
      expect(assistantMsg.confirmCard?.confirmToken).toBe('tk1')
      expect(assistantMsg.confirmCard?.status).toBe('waiting')
    })

    it('onMinderCommands 回调设置 dslCommands', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let minderFn: ((event: { commands: unknown[]; documentId: string }) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        minderFn = opts.handlers.onMinderCommands
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      minderFn?.({ commands: [{ type: 'add' }], documentId: 'doc-1' })
      expect(panel.messages.value[1].dslCommands).not.toBeNull()
      expect(panel.messages.value[1].dslCommands?.commands).toHaveLength(1)
    })

    it('onDone 回调结束流并置顶会话', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let doneFn: (() => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        doneFn = opts.handlers.onDone
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      doneFn?.()
      expect(panel.streaming.value).toBe(false)
      expect(panel.messages.value[1].streaming).toBe(false)
      expect(prependConversation).toHaveBeenCalled()
    })

    it('onError 回调结束流并追加错误信息', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let errorFn: ((event: { message: string }) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        errorFn = opts.handlers.onError
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      errorFn?.({ message: 'something went wrong' })
      expect(panel.streaming.value).toBe(false)
      expect(panel.messages.value[1].content).toContain('something went wrong')
    })

    it('onConnectionError 回调结束流并提示错误', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let connErrFn: ((error: Error) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        connErrFn = opts.onConnectionError
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      connErrFn?.(new Error('connection failed'))
      expect(panel.streaming.value).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('connection failed')
    })

    it('onClose 回调结束流', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let closeFn: (() => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        closeFn = opts.onClose
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      closeFn?.()
      expect(panel.streaming.value).toBe(false)
    })

    it('slowHint 在 10 秒后设置为 true', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      const timerCallback = fakeWindow.setTimeout.mock.calls.find(
        (c: unknown[]) => c[1] === 10_000,
      )?.[0] as (() => void) | undefined
      timerCallback?.()
      expect(panel.messages.value[1].slowHint).toBe(true)
    })

    it('onDelta 清除 slowTimer 并设置 slowHint 为 false', async () => {
      setupMocks()
      mocks.createConversation.mockResolvedValue({ id: 'conv-1', title: '新会话', lastActiveAt: '' })
      let deltaFn: ((text: string) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        deltaFn = opts.handlers.onDelta
        return { cancel: vi.fn() }
      })
      const panel = useAssistantPanel(defaultOptions())
      panel.input.value = 'hello'
      await panel.handleSend()
      panel.messages.value[1].slowHint = true
      deltaFn?.('text')
      expect(panel.messages.value[1].slowHint).toBe(false)
    })
  })

  describe('handleStop', () => {
    it('取消当前 controller', async () => {
      setupMocks()
      const cancelFn = vi.fn()
      mocks.useAssistantStream.mockReturnValue({ cancel: cancelFn })
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'conv-1'
      panel.input.value = 'test'
      await panel.handleSend()
      panel.handleStop()
      expect(cancelFn).toHaveBeenCalled()
    })

    it('无 controller 时不报错', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(() => panel.handleStop()).not.toThrow()
    })
  })

  describe('switchConversation', () => {
    it('切换到新会话并加载消息', async () => {
      setupMocks()
      const history: AiMessage[] = [
        { id: 'm1', role: 'user', content: 'hi', toolCalls: null, toolCallId: null, createdAt: '2025-01-01T00:00:00' },
      ]
      mocks.fetchMessages.mockResolvedValue(history)
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(panel.activeConversationId.value).toBe('c1')
      expect(panel.messages.value).toHaveLength(1)
      expect(panel.messages.value[0].role).toBe('user')
    })

    it('加载失败时提示错误', async () => {
      setupMocks()
      mocks.fetchMessages.mockRejectedValue(new Error('fail'))
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载消息失败')
    })

    it('切换到已激活的会话时不重复加载', async () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'c1'
      await panel.switchConversation('c1')
      expect(mocks.fetchMessages).not.toHaveBeenCalled()
    })

    it('切换前取消当前 controller', async () => {
      setupMocks()
      const cancelFn = vi.fn()
      mocks.useAssistantStream.mockReturnValue({ cancel: cancelFn })
      mocks.fetchMessages.mockResolvedValue([])
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'old-conv'
      panel.input.value = 'test'
      await panel.handleSend()
      await panel.switchConversation('new-conv')
      expect(cancelFn).toHaveBeenCalled()
    })

    it('finally 中 messagesLoading 恢复为 false', async () => {
      setupMocks()
      mocks.fetchMessages.mockRejectedValue(new Error('fail'))
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(panel.messagesLoading.value).toBe(false)
    })

    it('加载成功时 messagesLoading 恢复为 false', async () => {
      setupMocks()
      mocks.fetchMessages.mockResolvedValue([])
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(panel.messagesLoading.value).toBe(false)
    })

    it('消息 role 为 tool 时正确映射', async () => {
      setupMocks()
      mocks.fetchMessages.mockResolvedValue([
        { id: 'm1', role: 'tool', content: 'result', toolCalls: null, toolCallId: 'tc1', createdAt: '2025-01-01T00:00:00' },
      ])
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(panel.messages.value[0].role).toBe('tool')
    })

    it('assistant 消息包含 toolCalls 时正确映射 toolProcesses', async () => {
      setupMocks()
      mocks.fetchMessages.mockResolvedValue([
        { id: 'm1', role: 'assistant', content: 'ok', toolCalls: [{ name: 'search', arguments: '{}', callId: 'c1' }], toolCallId: null, createdAt: '2025-01-01T00:00:00' },
      ])
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(panel.messages.value[0].toolProcesses).toHaveLength(1)
      expect(panel.messages.value[0].toolProcesses?.[0].toolName).toBe('search')
    })

    it('assistant 消息无 toolCalls 时 toolProcesses 为空数组', async () => {
      setupMocks()
      mocks.fetchMessages.mockResolvedValue([
        { id: 'm1', role: 'assistant', content: 'ok', toolCalls: null, toolCallId: null, createdAt: '2025-01-01T00:00:00' },
      ])
      const panel = useAssistantPanel(defaultOptions())
      await panel.switchConversation('c1')
      expect(panel.messages.value[0].toolProcesses).toEqual([])
    })
  })

  describe('handleNewConversation', () => {
    it('取消当前流并重置状态', async () => {
      setupMocks()
      const cancelFn = vi.fn()
      mocks.useAssistantStream.mockReturnValue({ cancel: cancelFn })
      const panel = useAssistantPanel(defaultOptions())
      panel.activeConversationId.value = 'conv-1'
      panel.input.value = 'test'
      await panel.handleSend()
      expect(panel.streaming.value).toBe(true)
      panel.handleNewConversation()
      expect(cancelFn).toHaveBeenCalled()
      expect(panel.streaming.value).toBe(false)
      expect(panel.activeConversationId.value).toBeNull()
      expect(panel.messages.value).toEqual([])
    })

    it('无 controller 时不报错', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      expect(() => panel.handleNewConversation()).not.toThrow()
    })
  })

  describe('handleDeleteConversation', () => {
    it('用户确认后删除会话', async () => {
      setupMocks()
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteConversation.mockResolvedValue()
      const panel = useAssistantPanel(defaultOptions())
      conversationItems.value = [{ id: 'c1', title: '会话1', lastActiveAt: '' }]
      await panel.handleDeleteConversation({ id: 'c1', title: '会话1', lastActiveAt: '' })
      expect(mocks.deleteConversation).toHaveBeenCalledWith('c1')
      expect(conversationItems.value).toEqual([])
    })

    it('用户取消时不删除', async () => {
      setupMocks()
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const panel = useAssistantPanel(defaultOptions())
      conversationItems.value = [{ id: 'c1', title: '会话1', lastActiveAt: '' }]
      await panel.handleDeleteConversation({ id: 'c1', title: '会话1', lastActiveAt: '' })
      expect(mocks.deleteConversation).not.toHaveBeenCalled()
    })

    it('删除活跃会话时重置 activeConversationId 和 messages', async () => {
      setupMocks()
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteConversation.mockResolvedValue()
      const panel = useAssistantPanel(defaultOptions())
      conversationItems.value = [{ id: 'c1', title: '会话1', lastActiveAt: '' }]
      panel.activeConversationId.value = 'c1'
      panel.messages.value = [{ id: 'm1', role: 'user', content: 'hi', createdAt: '' }]
      await panel.handleDeleteConversation({ id: 'c1', title: '会话1', lastActiveAt: '' })
      expect(panel.activeConversationId.value).toBeNull()
      expect(panel.messages.value).toEqual([])
    })

    it('删除失败时提示错误', async () => {
      setupMocks()
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteConversation.mockRejectedValue(new Error('fail'))
      const panel = useAssistantPanel(defaultOptions())
      conversationItems.value = [{ id: 'c1', title: '会话1', lastActiveAt: '' }]
      await panel.handleDeleteConversation({ id: 'c1', title: '会话1', lastActiveAt: '' })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
    })

    it('删除非活跃会话时不重置 activeConversationId', async () => {
      setupMocks()
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteConversation.mockResolvedValue()
      const panel = useAssistantPanel(defaultOptions())
      conversationItems.value = [
        { id: 'c1', title: '会话1', lastActiveAt: '' },
        { id: 'c2', title: '会话2', lastActiveAt: '' },
      ]
      panel.activeConversationId.value = 'c2'
      await panel.handleDeleteConversation({ id: 'c1', title: '会话1', lastActiveAt: '' })
      expect(panel.activeConversationId.value).toBe('c2')
    })
  })

  describe('handleConfirm', () => {
    it('找到 waiting 状态的卡片并设为 approved，开始新流', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      panel.handleConfirm('tk1')
      expect(panel.messages.value[0].confirmCard?.status).toBe('approved')
      expect(panel.streaming.value).toBe(true)
      expect(panel.messages.value).toHaveLength(2)
      expect(panel.messages.value[1].role).toBe('assistant')
    })

    it('卡片不存在时不做任何操作', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.handleConfirm('nonexistent')
      expect(mocks.useAssistantStream).not.toHaveBeenCalled()
    })

    it('卡片状态非 waiting 时不操作', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'approved' } as never },
      ]
      panel.handleConfirm('tk1')
      expect(mocks.useAssistantStream).not.toHaveBeenCalled()
    })

    it('使用 APPROVE_CONFIRMATION_URL 发起请求', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      mocks.useAssistantStream.mockReturnValue({ cancel: vi.fn() })
      panel.handleConfirm('tk1')
      expect(mocks.useAssistantStream).toHaveBeenCalledWith(
        expect.objectContaining({
          url: '/workspace/ai/confirmations/approve',
          body: { confirmToken: 'tk1' },
        }),
      )
    })

    it('onDelta 追加内容到 replyMsg', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      let deltaFn: ((text: string) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        deltaFn = opts.handlers.onDelta
        return { cancel: vi.fn() }
      })
      panel.handleConfirm('tk1')
      deltaFn?.('ok')
      expect(panel.messages.value[1].content).toBe('ok')
    })

    it('onDone 结束流', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      let doneFn: (() => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        doneFn = opts.handlers.onDone
        return { cancel: vi.fn() }
      })
      panel.handleConfirm('tk1')
      doneFn?.()
      expect(panel.streaming.value).toBe(false)
    })

    it('onError 设置卡片为 failed', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      let errorFn: ((event: { message: string }) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        errorFn = opts.handlers.onError
        return { cancel: vi.fn() }
      })
      panel.handleConfirm('tk1')
      errorFn?.({ message: 'exec failed' })
      expect(panel.messages.value[0].confirmCard?.status).toBe('failed')
      expect(panel.messages.value[0].confirmCard?.error).toBe('exec failed')
    })

    it('onConnectionError 设置卡片为 failed', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      let connErrFn: ((error: Error) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        connErrFn = opts.onConnectionError
        return { cancel: vi.fn() }
      })
      panel.handleConfirm('tk1')
      connErrFn?.(new Error('conn err'))
      expect(panel.messages.value[0].confirmCard?.status).toBe('failed')
      expect(panel.messages.value[0].confirmCard?.error).toBe('conn err')
    })

    it('onClose 结束流', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      let closeFn: (() => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        closeFn = opts.onClose
        return { cancel: vi.fn() }
      })
      panel.handleConfirm('tk1')
      closeFn?.()
      expect(panel.streaming.value).toBe(false)
    })

    it('onToolCall 添加 toolProcesses 到 replyMsg', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      let toolCallFn: ((event: { toolName: string; summary: string }) => void) | undefined
      mocks.useAssistantStream.mockImplementation((opts: Parameters<typeof mocks.useAssistantStream>[0]) => {
        toolCallFn = opts.handlers.onToolCall
        return { cancel: vi.fn() }
      })
      panel.handleConfirm('tk1')
      toolCallFn?.({ toolName: 'exec', summary: 'run' })
      expect(panel.messages.value[1].toolProcesses).toHaveLength(1)
      expect(panel.messages.value[1].toolProcesses?.[0].toolName).toBe('exec')
    })
  })

  describe('handleCancel', () => {
    it('找到 waiting 卡片后取消确认', async () => {
      setupMocks()
      mocks.cancelConfirmation.mockResolvedValue()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      await panel.handleCancel('tk1')
      expect(mocks.cancelConfirmation).toHaveBeenCalledWith('tk1')
      expect(panel.messages.value[0].confirmCard?.status).toBe('cancelled')
    })

    it('卡片不存在时不操作', async () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      await panel.handleCancel('nonexistent')
      expect(mocks.cancelConfirmation).not.toHaveBeenCalled()
    })

    it('卡片状态非 waiting 时不操作', async () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'approved' } as never },
      ]
      await panel.handleCancel('tk1')
      expect(mocks.cancelConfirmation).not.toHaveBeenCalled()
    })

    it('取消失败时提示错误', async () => {
      setupMocks()
      mocks.cancelConfirmation.mockRejectedValue(new Error('fail'))
      const panel = useAssistantPanel(defaultOptions())
      panel.messages.value = [
        { id: 'm1', role: 'assistant', content: '', createdAt: '', confirmCard: { confirmToken: 'tk1', status: 'waiting' } as never },
      ]
      await panel.handleCancel('tk1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('取消失败，请重试')
    })
  })

  describe('handleConfirmDsl', () => {
    it('无权限时提示警告', () => {
      setupMocks({ hasPermission: () => false })
      const panel = useAssistantPanel(defaultOptions())
      panel.handleConfirmDsl({ commands: [], documentId: 'doc-1' } as never)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('无文档编辑权限，无法执行编辑操作')
    })

    it('dslHost 为空时提示警告', () => {
      setupMocks({ dslHost: null })
      const panel = useAssistantPanel(defaultOptions())
      panel.handleConfirmDsl({ commands: [], documentId: 'doc-1' } as never)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请回到文档后重试')
    })

    it('dslHost 存在时执行 plan 并添加 tool 消息', () => {
      const mockApply = vi.fn().mockReturnValue({ applied: 3, skipped: 1 })
      setupMocks({ dslHost: { apply: mockApply } })
      const panel = useAssistantPanel(defaultOptions())
      const plan = { commands: [], documentId: 'doc-1' } as never
      panel.handleConfirmDsl(plan)
      expect(mockApply).toHaveBeenCalledWith(plan)
      expect(panel.messages.value).toHaveLength(1)
      expect(panel.messages.value[0].role).toBe('tool')
      expect(panel.messages.value[0].content).toContain('3')
      expect(panel.messages.value[0].content).toContain('1')
    })

    it('skipped 为 0 时不显示跳过数', () => {
      const mockApply = vi.fn().mockReturnValue({ applied: 2, skipped: 0 })
      setupMocks({ dslHost: { apply: mockApply } })
      const panel = useAssistantPanel(defaultOptions())
      panel.handleConfirmDsl({ commands: [], documentId: 'doc-1' } as never)
      expect(panel.messages.value[0].content).toContain('2')
      expect(panel.messages.value[0].content).not.toContain('跳过')
    })
  })

  describe('handleCancelDsl', () => {
    it('添加取消提示的 tool 消息', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.handleCancelDsl()
      expect(panel.messages.value).toHaveLength(1)
      expect(panel.messages.value[0].role).toBe('tool')
      expect(panel.messages.value[0].content).toBe('已取消，无任何变更')
    })
  })

  describe('handleSidebarScroll', () => {
    it('距离底部小于 20px 时触发 loadMoreConversations', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.sidebarScroller.value = { wrapRef: { scrollHeight: 1000, clientHeight: 800 } } as never
      panel.handleSidebarScroll({ scrollTop: 985, scrollLeft: 0 })
      expect(loadMoreConversations).toHaveBeenCalled()
    })

    it('距离底部大于等于 20px 时不触发 loadMore', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.sidebarScroller.value = { wrapRef: { scrollHeight: 1000, clientHeight: 800 } } as never
      loadMoreConversations.mockClear()
      panel.handleSidebarScroll({ scrollTop: 0, scrollLeft: 0 })
      expect(loadMoreConversations).not.toHaveBeenCalled()
    })

    it('wrapRef 为空时不报错', () => {
      setupMocks()
      const panel = useAssistantPanel(defaultOptions())
      panel.sidebarScroller.value = { wrapRef: null } as never
      loadMoreConversations.mockClear()
      expect(() => panel.handleSidebarScroll({ scrollTop: 0, scrollLeft: 0 })).not.toThrow()
      expect(loadMoreConversations).not.toHaveBeenCalled()
    })
  })

  describe('onMinimize / onClose', () => {
    it('返回 options 中的回调', () => {
      setupMocks()
      const onMinimize = vi.fn()
      const onClose = vi.fn()
      const panel = useAssistantPanel({ onMinimize, onClose })
      panel.onMinimize()
      panel.onClose()
      expect(onMinimize).toHaveBeenCalled()
      expect(onClose).toHaveBeenCalled()
    })
  })
})
