import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiAssistantConversation, AiAssistantMessage, PageResult } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiAssistantConversations: vi.fn(),
  createAiAssistantConversation: vi.fn(),
  updateAiAssistantConversation: vi.fn(),
  deleteAiAssistantConversation: vi.fn(),
  fetchAiAssistantMessages: vi.fn(),
  executeAiAssistantMessage: vi.fn(),
  cancelAiAssistantMessage: vi.fn(),
  streamAssistantMessage: vi.fn(),
  AssistantStreamError: class extends Error {
    code: number

    constructor(code: number, message: string) {
      super(message)
      this.code = code
    }
  },
  AssistantStreamInterruptedError: class extends Error {},
}))

vi.mock('@/services/aiAssistant', () => ({
  fetchAiAssistantConversations: mocks.fetchAiAssistantConversations,
  createAiAssistantConversation: mocks.createAiAssistantConversation,
  updateAiAssistantConversation: mocks.updateAiAssistantConversation,
  deleteAiAssistantConversation: mocks.deleteAiAssistantConversation,
  fetchAiAssistantMessages: mocks.fetchAiAssistantMessages,
  executeAiAssistantMessage: mocks.executeAiAssistantMessage,
  cancelAiAssistantMessage: mocks.cancelAiAssistantMessage,
}))

vi.mock('@/services/assistantSse', () => ({
  streamAssistantMessage: mocks.streamAssistantMessage,
  AssistantStreamError: mocks.AssistantStreamError,
  AssistantStreamInterruptedError: mocks.AssistantStreamInterruptedError,
}))

import { useAiAssistantStore } from './aiAssistant'

function makeConversation(overrides: Partial<AiAssistantConversation> = {}): AiAssistantConversation {
  return {
    id: 'c1',
    title: '新会话',
    status: 'active',
    lastMessageAt: null,
    messageCount: 0,
    contextSnapshot: null,
    ...overrides,
  }
}

function makeMessage(overrides: Partial<AiAssistantMessage> = {}): AiAssistantMessage {
  return {
    id: 'm1',
    conversationId: 'c1',
    role: 'assistant',
    content: '回答',
    attachments: null,
    intent: null,
    citations: null,
    execution: null,
    status: 'done',
    createdAt: '2026-10-08T00:00:00',
    ...overrides,
  }
}

function page(list: AiAssistantMessage[], total = list.length): PageResult<AiAssistantMessage> {
  return { list, total }
}

describe('stores/aiAssistant 会话管理', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loadConversations 失败时记录错误供页面提示（UI-PAGE-11）', async () => {
    mocks.fetchAiAssistantConversations.mockRejectedValue(new Error('网络错误'))
    const store = useAiAssistantStore()

    await store.loadConversations()

    expect(store.conversationsError).toBe('网络错误')
    expect(store.conversationsLoading).toBe(false)
  })

  it('静默刷新失败保留旧列表且不置错误', async () => {
    const store = useAiAssistantStore()
    store.conversations = [makeConversation()]
    mocks.fetchAiAssistantConversations.mockRejectedValue(new Error('boom'))

    await store.loadConversations('k', true)

    expect(store.conversations).toHaveLength(1)
    expect(store.conversationsError).toBe('')
  })

  it('删除当前会话即重置会话上下文', async () => {
    const store = useAiAssistantStore()
    store.conversations = [makeConversation()]
    store.currentId = 'c1'
    store.messages = [makeMessage()]
    mocks.deleteAiAssistantConversation.mockResolvedValue(undefined)

    await store.deleteConversation('c1')

    expect(store.currentId).toBeNull()
    expect(store.messages).toHaveLength(0)
    expect(store.conversations).toHaveLength(0)
  })

  it('归档更新列表项状态，canSend 随之禁用', async () => {
    const store = useAiAssistantStore()
    store.conversations = [makeConversation()]
    store.currentId = 'c1'
    mocks.updateAiAssistantConversation.mockResolvedValue(
      makeConversation({ status: 'archived' }),
    )

    await store.archiveConversation('c1')

    expect(store.currentConversation?.status).toBe('archived')
    expect(store.canSend).toBe(false)
  })

  it('切换会话保留各会话草稿', () => {
    const store = useAiAssistantStore()

    store.setDraft('c1', '草稿一')
    store.setDraft(null, '新会话草稿')

    expect(store.drafts).toEqual({ c1: '草稿一', '': '新会话草稿' })
  })

  it('待发附件按会话暂存，类型 + ID 去重并支持移除', () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    const ref = { entityType: 'requirement', entityId: 'r1', entityTitle: '登录需求' }

    store.addAttachment(ref)
    store.addAttachment({ ...ref })
    store.addAttachment({ entityType: 'module', entityId: 'm1' })

    expect(store.attachmentsForCurrent).toHaveLength(2)

    store.removeAttachment('r1')
    expect(store.attachmentsForCurrent.map((item) => item.entityId)).toEqual(['m1'])

    store.currentId = null
    expect(store.attachmentsForCurrent).toEqual([])
  })

  it('setAttachments 整体替换当前会话待发附件（重新解析还原原始输入）', () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.attachments['c1'] = [{ entityType: 'test_plan', entityId: 'p9' }]
    const refs = [{ entityType: 'requirement', entityId: 'r9' }]

    store.setAttachments(refs)
    refs.push({ entityType: 'module', entityId: 'm1' })

    expect(store.attachmentsForCurrent).toEqual([{ entityType: 'requirement', entityId: 'r9' }])
  })

  it('清除错误后同类加载失败仍可再次提示（UI-PAGE-11）', () => {
    const store = useAiAssistantStore()
    store.conversationsError = '会话列表加载失败'
    store.messagesError = '消息加载失败'

    store.clearConversationsError()
    store.clearMessagesError()

    expect(store.conversationsError).toBe('')
    expect(store.messagesError).toBe('')
  })
})

describe('stores/aiAssistant 发送与流式', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('无会话时首问先创建会话，乐观追加用户消息并在 done 后对齐服务端', async () => {
    const store = useAiAssistantStore()
    mocks.createAiAssistantConversation.mockResolvedValue(makeConversation())
    mocks.streamAssistantMessage.mockImplementation(
      async (_id: string, _payload: unknown, onEvent: (e: unknown) => void) => {
        onEvent({ type: 'delta', data: { text: '你' } })
        onEvent({ type: 'delta', data: { text: '好' } })
        onEvent({ type: 'done', data: { messageId: 'm2' } })
      },
    )
    mocks.fetchAiAssistantMessages.mockResolvedValue(
      // 服务端倒序返回（最新在前）
      page([
        makeMessage({ id: 'm2', content: '你好' }),
        makeMessage({ id: 'm1', role: 'user', content: '提问' }),
      ]),
    )
    mocks.fetchAiAssistantConversations.mockResolvedValue({ list: [], total: 0 })

    await store.send('提问')

    expect(store.currentId).toBe('c1')
    expect(mocks.streamAssistantMessage).toHaveBeenCalledWith(
      'c1',
      { content: '提问' },
      expect.any(Function),
    )
    // 刷新后不应残留乐观占位消息
    expect(store.messages.map((m) => m.id)).toEqual(['m1', 'm2'])
    expect(store.streaming).toBe(false)
    expect(store.streamText).toBe('')
  })

  it('发送前业务失败记录错误码，不抛出到页面', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.conversations = [makeConversation()]
    mocks.streamAssistantMessage.mockRejectedValue(
      new mocks.AssistantStreamError(1000018260, '消息内容不合法'),
    )

    await store.send('x')

    expect(store.streamError).toEqual({ code: 1000018260, msg: '消息内容不合法' })
    expect(store.streaming).toBe(false)
    expect(store.recovering).toBe(false)
    // 乐观消息已撤回
    expect(store.messages).toHaveLength(0)
  })

  it('SSE 中断转入恢复轮询，补齐到 done 自动停止', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.conversations = [makeConversation()]
    mocks.streamAssistantMessage.mockRejectedValue(new mocks.AssistantStreamInterruptedError())
    mocks.fetchAiAssistantMessages
      .mockResolvedValueOnce(
        page([makeMessage({ id: 'm2', status: 'interrupted', content: null })]),
      )
      .mockResolvedValue(page([makeMessage({ id: 'm2', status: 'done' })]))

    await store.send('x')

    expect(store.recovering).toBe(true)
    // 首次同步轮询 + 定时轮询推进到终态
    await vi.advanceTimersByTimeAsync(2000)

    expect(store.recovering).toBe(false)
    expect(store.messages[0].status).toBe('done')
    expect(store.pollTimer).toBeNull()
  })

  it('选择含未完成消息的会话时自动开始补齐', async () => {
    const store = useAiAssistantStore()
    store.conversations = [makeConversation()]
    mocks.fetchAiAssistantMessages.mockResolvedValue(
      page([makeMessage({ status: 'interrupted' })]),
    )

    await store.selectConversation('c1')

    expect(store.recovering).toBe(true)
    store.stopRecovery()
  })

  it('切换会话终止上一个会话的恢复轮询', async () => {
    const store = useAiAssistantStore()
    store.conversations = [makeConversation(), makeConversation({ id: 'c2' })]
    store.currentId = 'c1'
    store.recovering = true
    store.recoveryConversationId = 'c1'
    store.pollTimer = setInterval(() => {}, 2000)
    mocks.fetchAiAssistantMessages.mockResolvedValue(page([]))

    await store.selectConversation('c2')

    expect(store.recovering).toBe(false)
    expect(store.pollTimer).toBeNull()
  })

  it('发送携带待发附件并回显，发送后清空草稿与附件', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.conversations = [makeConversation()]
    const ref = { entityType: 'requirement', entityId: 'r1', entityTitle: '登录需求' }
    store.drafts.c1 = '问题'
    store.attachments.c1 = [ref]
    mocks.fetchAiAssistantConversations.mockResolvedValue({ list: [], total: 0 })
    mocks.fetchAiAssistantMessages.mockResolvedValue(
      page([makeMessage({ id: 'm2' }), makeMessage({ id: 'm1', role: 'user', content: '问题' })]),
    )
    let optimistic: AiAssistantMessage | undefined
    mocks.streamAssistantMessage.mockImplementation(
      async (_id: string, _payload: unknown, onEvent: (e: unknown) => void) => {
        optimistic = store.messages[0]
        onEvent({ type: 'done', data: { messageId: 'm2' } })
      },
    )

    await store.send('问题')

    expect(mocks.streamAssistantMessage).toHaveBeenCalledWith(
      'c1',
      { content: '问题', attachments: [ref] },
      expect.any(Function),
    )
    expect(optimistic?.attachments).toEqual([ref])
    expect(store.drafts.c1).toBe('')
    expect(store.attachments.c1).toEqual([])
  })

  it('校验阶段被拒回填草稿与附件并撤回乐观消息', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.conversations = [makeConversation()]
    const ref = { entityType: 'module', entityId: 'm1' }
    store.drafts.c1 = '问题'
    store.attachments.c1 = [ref]
    mocks.streamAssistantMessage.mockRejectedValue(
      new mocks.AssistantStreamError(1000018260, '消息内容不合法'),
    )

    await store.send('问题')

    expect(store.streamError).toEqual({ code: 1000018260, msg: '消息内容不合法' })
    expect(store.drafts.c1).toBe('问题')
    expect(store.attachments.c1).toEqual([ref])
    expect(store.messages).toHaveLength(0)
  })

  it('新会话校验失败回填到新会话键，输入保持可见', async () => {
    const store = useAiAssistantStore()
    const ref = { entityType: 'test_plan', entityId: 'p1' }
    store.drafts[''] = '首问'
    store.attachments[''] = [ref]
    mocks.createAiAssistantConversation.mockResolvedValue(makeConversation())
    mocks.streamAssistantMessage.mockRejectedValue(
      new mocks.AssistantStreamError(1000018260, '消息内容不合法'),
    )

    await store.send('首问')

    expect(store.currentId).toBe('c1')
    expect(store.drafts.c1).toBe('首问')
    expect(store.drafts['']).toBe('')
    expect(store.attachments.c1).toEqual([ref])
    expect(store.attachments['']).toEqual([])
  })
})

describe('stores/aiAssistant 预览执行', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('执行回执回填到对应消息并复位 executing', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.messages = [makeMessage({ id: 'm1', intent: null })]
    mocks.executeAiAssistantMessage.mockResolvedValue({
      execution: { status: 'executed', results: [{ action: 'create_review', success: true }] },
    })

    await store.executePreview('m1')

    expect(mocks.executeAiAssistantMessage).toHaveBeenCalledWith('c1', 'm1', {})
    expect(store.messages[0].execution?.status).toBe('executed')
    expect(store.executing).toBe(false)
  })

  it('单项重试携带 retryIndexes', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.messages = [makeMessage({ id: 'm1' })]
    mocks.executeAiAssistantMessage.mockResolvedValue({
      execution: { status: 'executed', results: [] },
    })

    await store.executePreview('m1', [2])

    expect(mocks.executeAiAssistantMessage).toHaveBeenCalledWith('c1', 'm1', {
      retryIndexes: [2],
    })
  })

  it('取消预览落 rejected 回执', async () => {
    const store = useAiAssistantStore()
    store.currentId = 'c1'
    store.messages = [makeMessage({ id: 'm1' })]
    mocks.cancelAiAssistantMessage.mockResolvedValue({
      execution: { status: 'rejected' },
    })

    await store.cancelPreview('m1')

    expect(store.messages[0].execution?.status).toBe('rejected')
  })
})
