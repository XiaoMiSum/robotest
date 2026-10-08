import { defineStore } from 'pinia'
import {
  cancelAiAssistantMessage,
  createAiAssistantConversation,
  deleteAiAssistantConversation,
  executeAiAssistantMessage,
  fetchAiAssistantConversations,
  fetchAiAssistantMessages,
  updateAiAssistantConversation,
} from '@/services/aiAssistant'
import { AssistantStreamError, AssistantStreamInterruptedError, streamAssistantMessage } from '@/services/assistantSse'
import type {
  AiAssistantConversation,
  AiAssistantExecution,
  AiAssistantMessage,
  AiAssistantSseError,
} from '@/types'

const POLL_INTERVAL_MS = 2000
const PAGE_SIZE = 20

/** 终态：done / error 均不再轮询（interrupted / streaming 由断线恢复补齐） */
function isTerminalMessageStatus(status: AiAssistantMessage['status']): boolean {
  return status === 'done' || status === 'error'
}

function lastAssistantMessage(messages: AiAssistantMessage[]): AiAssistantMessage | null {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'assistant') return messages[i]
  }
  return null
}

/** 服务端倒序页转时间线正序，并剔除已被刷新覆盖的本地乐观消息 */
function mergeMessages(existing: AiAssistantMessage[], page: AiAssistantMessage[]): AiAssistantMessage[] {
  const newest = [...page].reverse()
  const ids = new Set(newest.map((message) => message.id))
  const older = existing.filter(
    (message) => !ids.has(message.id) && !message.id.startsWith('local-'),
  )
  return [...older, ...newest]
}

export const useAiAssistantStore = defineStore('aiAssistant', {
  state: () => ({
    /** 浮层卡片开合：不持久化，每次进入默认收起（详设 5.2） */
    open: false,
    conversations: [] as AiAssistantConversation[],
    conversationsLoading: false,
    /** 列表加载失败提示（UI-PAGE-11），由入口组件统一 toast 后清空 */
    conversationsError: '' as string,
    keyword: '',
    currentId: null as string | null,
    messages: [] as AiAssistantMessage[],
    messagesLoading: false,
    messagesError: '' as string,
    /** 已加载的倒序页数（上滑加载更早页） */
    messagePages: 1,
    messageTotal: 0,
    /** 按会话暂存未发送草稿（key 为空串表示尚无会话的新会话） */
    drafts: {} as Record<string, string>,
    // --- 流式过程态 ---
    streaming: false,
    /** delta 累积缓冲：done 后以服务端持久化内容对齐 */
    streamText: '',
    /** SSE error 事件或发送前业务失败的原因，重新发送前保留展示 */
    streamError: null as AiAssistantSseError | null,
    /** 断线恢复中（StreamIndicator「恢复中…」） */
    recovering: false,
    recoveryConversationId: null as string | null,
    pollTimer: null as ReturnType<typeof setInterval> | null,
    // 慢请求未返回时不叠加下一次轮询
    pollBusy: false,
    /** 预览确认执行中：回执生成前 ComposerInput 禁用 */
    executing: false,
  }),
  getters: {
    currentConversation(state): AiAssistantConversation | null {
      return state.conversations.find((item) => item.id === state.currentId) ?? null
    },
    /** 归档会话只读不可续写；流式或执行中禁发（详设 2.3 / 2.4） */
    canSend(state): boolean {
      if (state.streaming || state.executing) return false
      const conversation = state.conversations.find((item) => item.id === state.currentId)
      return conversation ? conversation.status === 'active' : true
    },
    draftForCurrent(state): string {
      return state.drafts[state.currentId ?? ''] ?? ''
    },
  },
  actions: {
    togglePanel() {
      this.open = !this.open
    },
    closePanel() {
      this.open = false
    },

    // ---------- 会话列表 ----------

    /** silent=true 用于发送后的静默对齐（标题回填 / 计数刷新），不闪加载态 */
    async loadConversations(keyword?: string, silent = false): Promise<void> {
      // 默认参数不能引用 this（TS2683：对象字面量方法的 this 在参数位无上下文类型）
      const query = keyword ?? this.keyword
      this.keyword = query
      if (!silent) this.conversationsLoading = true
      try {
        const page = await fetchAiAssistantConversations({ keyword: query, pageNo: 1, pageSize: PAGE_SIZE })
        this.conversations = page.list
        this.conversationsError = ''
      } catch (error) {
        // 首次打开依赖该列表，失败须可见；静默刷新失败保留旧列表即可
        if (!silent) {
          this.conversationsError = error instanceof Error ? error.message : '会话列表加载失败'
        }
      } finally {
        if (!silent) this.conversationsLoading = false
      }
    },

    /** 入口 toast 展示后清空，后续同类失败才能再次提示（UI-PAGE-11） */
    clearConversationsError(): void {
      this.conversationsError = ''
    },

    async createConversation(): Promise<AiAssistantConversation> {
      const conversation = await createAiAssistantConversation({})
      this.conversations = [conversation, ...this.conversations]
      return conversation
    },

    async renameConversation(conversationId: string, title: string): Promise<void> {
      const updated = await updateAiAssistantConversation(conversationId, { title })
      this.conversations = this.conversations.map((item) =>
        item.id === conversationId ? updated : item,
      )
    },

    async archiveConversation(conversationId: string): Promise<void> {
      const updated = await updateAiAssistantConversation(conversationId, { status: 'archived' })
      this.conversations = this.conversations.map((item) =>
        item.id === conversationId ? updated : item,
      )
    },

    async deleteConversation(conversationId: string): Promise<void> {
      await deleteAiAssistantConversation(conversationId)
      this.conversations = this.conversations.filter((item) => item.id !== conversationId)
      if (this.currentId === conversationId) this.resetSession()
    },

    // ---------- 消息 ----------

    setDraft(conversationId: string | null, text: string): void {
      this.drafts[conversationId ?? ''] = text
    },

    /** 新会话：不立即建库，首问时再创建（避免空会话污染列表） */
    resetSession(): void {
      this.stopRecovery()
      this.currentId = null
      this.messages = []
      this.messagePages = 1
      this.messageTotal = 0
      this.streamText = ''
      this.streamError = null
      this.streaming = false
    },

    async selectConversation(conversationId: string): Promise<void> {
      if (this.currentId === conversationId) return
      this.stopRecovery()
      this.currentId = conversationId
      this.messages = []
      this.messagePages = 1
      this.messageTotal = 0
      this.streamText = ''
      this.streamError = null
      await this.loadMessages()
      // 刷新或上次断线遗留的未完成消息：进入即自动补齐，无需用户干预（详设 2.4）
      const pending = lastAssistantMessage(this.messages)
      if (pending && !isTerminalMessageStatus(pending.status)) {
        this.beginRecovery(conversationId)
      }
    },

    async loadMessages(): Promise<void> {
      if (!this.currentId) return
      this.messagesLoading = true
      try {
        const page = await fetchAiAssistantMessages(this.currentId, {
          pageNo: 1,
          pageSize: PAGE_SIZE,
        })
        this.messages = mergeMessages(this.messages, page.list)
        this.messageTotal = page.total
        this.messagePages = 1
        this.messagesError = ''
      } catch (error) {
        this.messagesError = error instanceof Error ? error.message : '消息加载失败'
      } finally {
        this.messagesLoading = false
      }
    },

    /** 同 clearConversationsError：toast 后清空以便重复提示（UI-PAGE-11） */
    clearMessagesError(): void {
      this.messagesError = ''
    },

    /** 上滑加载更早页（服务端倒序，页码递增即更早） */
    async loadOlderMessages(): Promise<void> {
      if (!this.currentId || this.messagesLoading) return
      if (this.messages.length >= this.messageTotal) return
      this.messagesLoading = true
      try {
        const page = await fetchAiAssistantMessages(this.currentId, {
          pageNo: this.messagePages + 1,
          pageSize: PAGE_SIZE,
        })
        this.messagePages += 1
        const older = [...page.list].reverse()
        const known = new Set(this.messages.map((message) => message.id))
        this.messages = [...older.filter((message) => !known.has(message.id)), ...this.messages]
        this.messageTotal = page.total
      } catch (error) {
        this.messagesError = error instanceof Error ? error.message : '历史消息加载失败'
      } finally {
        this.messagesLoading = false
      }
    },

    /** 刷新第一页并对齐时间线（流式结束与断线恢复共用） */
    async refreshMessages(): Promise<void> {
      if (!this.currentId) return
      const page = await fetchAiAssistantMessages(this.currentId, {
        pageNo: 1,
        pageSize: PAGE_SIZE,
      })
      this.messages = mergeMessages(this.messages, page.list)
      this.messageTotal = page.total
    },

    // ---------- 发送与流式 ----------

    async send(content: string): Promise<void> {
      const text = content.trim()
      if (!text || !this.canSend) return
      this.stopRecovery()
      this.streamError = null
      this.streamText = ''
      this.streaming = true
      try {
        let conversationId = this.currentId
        if (!conversationId) {
          const conversation = await this.createConversation()
          conversationId = conversation.id
          this.currentId = conversation.id
        }
        // 乐观展示用户消息；done 后 refreshMessages 以服务端持久化状态整体对齐
        this.messages = [
          ...this.messages,
          {
            id: `local-${Date.now()}`,
            conversationId,
            role: 'user',
            content: text,
            attachments: null,
            intent: null,
            citations: null,
            execution: null,
            status: 'done',
            createdAt: new Date().toISOString(),
          },
        ]

        let sawTerminal = false
        await streamAssistantMessage(conversationId, { content: text }, (event) => {
          if (event.type === 'delta') {
            this.streamText += event.data.text
          } else if (event.type === 'done') {
            sawTerminal = true
          } else if (event.type === 'error') {
            sawTerminal = true
            this.streamError = event.data
          }
          // clarify / preview / citations 是已提交状态的投影（详设 3.5）：
          // done 后统一 refreshMessages 对齐，避免事件与持久化双写不一致
        })

        this.streaming = false
        if (sawTerminal) {
          await this.refreshMessages()
          // 终态以服务端持久化内容为准，清空流式缓冲避免与消息双显
          this.streamText = ''
          // 标题首问回填与 messageCount 由服务端更新，静默对齐列表
          void this.loadConversations(this.keyword, true)
        } else {
          // 连接正常关闭但未见终态（如心跳超时）：同样走恢复轮询
          this.beginRecovery(conversationId)
        }
      } catch (error) {
        this.streaming = false
        if (error instanceof AssistantStreamInterruptedError) {
          if (this.currentId) this.beginRecovery(this.currentId)
          return
        }
        if (error instanceof AssistantStreamError) {
          this.streamError = { code: error.code, msg: error.message }
          // 发送在校验阶段被拒，乐观消息未落盘，撤回避免与历史不一致
          this.messages = this.messages.filter((message) => !message.id.startsWith('local-'))
          return
        }
        throw error
      }
    },

    // ---------- 断线恢复轮询 ----------

    beginRecovery(conversationId: string): void {
      if (this.pollTimer && this.recoveryConversationId === conversationId) return
      this.stopRecovery()
      this.recovering = true
      this.recoveryConversationId = conversationId
      void this.pollRecovery(conversationId)
      this.pollTimer = setInterval(() => void this.pollRecovery(conversationId), POLL_INTERVAL_MS)
    },

    stopRecovery(): void {
      if (this.pollTimer) clearInterval(this.pollTimer)
      this.pollTimer = null
      this.recovering = false
      this.recoveryConversationId = null
      this.pollBusy = false
    },

    async pollRecovery(conversationId: string): Promise<void> {
      // 仅对发起恢复的会话生效：期间切换会话由 stopRecovery 终止
      if (this.pollBusy || this.recoveryConversationId !== conversationId) return
      if (this.currentId !== conversationId) {
        this.stopRecovery()
        return
      }
      this.pollBusy = true
      try {
        await this.refreshMessages()
        const pending = lastAssistantMessage(this.messages)
        if (!pending || isTerminalMessageStatus(pending.status)) {
          // 补齐完成：服务端内容已完整落盘，流式缓冲让位给持久化消息
          this.streamText = ''
          this.stopRecovery()
        }
      } catch {
        // 恢复轮询容忍网络抖动继续重试；列表错误提示由 loadMessages 负责
      } finally {
        this.pollBusy = false
      }
    },

    // ---------- 预览执行 ----------

    async executePreview(messageId: string, retryIndexes?: number[]): Promise<void> {
      if (!this.currentId) return
      this.executing = true
      try {
        const payload = retryIndexes && retryIndexes.length > 0 ? { retryIndexes } : {}
        const result = await executeAiAssistantMessage(this.currentId, messageId, payload)
        this.patchExecution(messageId, result.execution)
      } finally {
        this.executing = false
      }
    },

    async cancelPreview(messageId: string): Promise<void> {
      if (!this.currentId) return
      const result = await cancelAiAssistantMessage(this.currentId, messageId)
      this.patchExecution(messageId, result.execution)
    },

    patchExecution(messageId: string, execution: AiAssistantExecution): void {
      this.messages = this.messages.map((message) =>
        message.id === messageId ? { ...message, execution } : message,
      )
    },
  },
})
