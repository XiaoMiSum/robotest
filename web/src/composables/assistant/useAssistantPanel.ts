import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ScrollbarInstance } from 'element-plus'
import type { AiStreamController } from '@/composables/ai/useAiStream'
import { useAssistantStream } from '@/composables/assistant/useAssistantStream'
import { useConversationList } from '@/composables/assistant/useConversationList'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'
import { useAssistantContextStore } from '@/stores/assistantContext'
import {
  APPROVE_CONFIRMATION_URL,
  cancelConfirmation,
  createConversation,
  deleteConversation,
  fetchMessages,
} from '@/services/assistant'
import type { AiConversation, AiMessage } from '@/types'
import type { AssistantMessageItem } from '@/components/assistant/MessageItem.vue'
import type { DslPlan } from '@/minder/ai/dslRunner'

export interface UseAssistantPanelOptions {
  minimized?: boolean
  onMinimize: () => void
  onClose: () => void
}

export function useAssistantPanel(options: UseAssistantPanelOptions) {
  const { onMinimize, onClose } = options

  const authStore = useAuthStore()
  const aiStore = useAiStore()
  const assistantContext = useAssistantContextStore()

  const noWorkspace = computed(() => !authStore.activeWorkspace?.id)

  const {
    items: conversationItems,
    loading: conversationsLoading,
    loadingMore: conversationsLoadingMore,
    refresh: refreshConversations,
    loadMore: loadMoreConversations,
    prepend: prependConversation,
  } = useConversationList({
    workspaceId: () => authStore.activeWorkspace?.id ?? '',
  })

  const activeConversationId = ref<string | null>(null)
  const messages = ref<AssistantMessageItem[]>([])
  const messagesLoading = ref(false)
  const streaming = ref(false)
  const controller = ref<AiStreamController | null>(null)
  const input = ref('')

  let localSeq = 0
  function localId(): string {
    localSeq += 1
    return `local-${Date.now()}-${localSeq}`
  }

  function nowIso(): string {
    return new Date().toISOString()
  }

  function toMessageItem(msg: AiMessage): AssistantMessageItem {
    const base = { id: msg.id, content: msg.content ?? '', createdAt: msg.createdAt }
    if (msg.role === 'tool') return { ...base, role: 'tool' }
    if (msg.role === 'user') return { ...base, role: 'user' }
    return {
      ...base,
      role: 'assistant',
      toolProcesses: msg.toolCalls?.map((tc) => ({ toolName: tc.name, summary: tc.arguments, status: 'done' as const })) ?? [],
    }
  }

  const messageScroller = ref<ScrollbarInstance | null>(null)
  const sidebarScroller = ref<ScrollbarInstance | null>(null)

  async function scrollToBottom(): Promise<void> {
    await nextTick()
    const wrap = messageScroller.value?.wrapRef
    if (wrap) messageScroller.value?.setScrollTop(wrap.scrollHeight)
  }

  function finishStream(message: AssistantMessageItem): void {
    message.streaming = false
    message.toolProcesses?.forEach((p) => {
      p.status = 'done'
    })
    streaming.value = false
  }

  function pinConversation(id: string): void {
    const existing = conversationItems.value.find((c) => c.id === id)
    prependConversation({ id, title: existing?.title ?? '新会话', lastActiveAt: nowIso() })
  }

  async function handleSend(): Promise<void> {
    const content = input.value.trim()
    if (!content || streaming.value) return
    if (!authStore.activeWorkspace?.id) {
      ElMessage.warning('请先选择工作空间')
      return
    }

    let conversationId = activeConversationId.value
    if (!conversationId) {
      try {
        const conv = await createConversation()
        conversationId = conv.id
        activeConversationId.value = conversationId
        prependConversation(conv)
      } catch {
        ElMessage.error('创建会话失败，请重试')
        return
      }
    }

    input.value = ''
    messages.value.push({ id: localId(), role: 'user', content, createdAt: nowIso() })
    const assistantMsg = reactive<AssistantMessageItem>({
      id: localId(),
      role: 'assistant',
      content: '',
      createdAt: nowIso(),
      streaming: true,
      toolProcesses: [],
      confirmCard: null,
      dslCommands: null,
    })
    messages.value.push(assistantMsg)
    streaming.value = true
    void scrollToBottom()

    let firstFrameReceived = false
    const slowTimer = window.setTimeout(() => {
      if (!firstFrameReceived) assistantMsg.slowHint = true
    }, 10_000)

    controller.value?.cancel()
    controller.value = useAssistantStream({
      url: `/workspace/ai/conversations/${conversationId}/messages`,
      body: {
        content,
        pageContext: assistantContext.buildPageContext(),
        modelId: aiStore.selectedModelId,
      },
      handlers: {
        onDelta: (text) => {
          firstFrameReceived = true
          window.clearTimeout(slowTimer)
          assistantMsg.slowHint = false
          assistantMsg.content += text
          void scrollToBottom()
        },
        onToolCall: (event) => {
          firstFrameReceived = true
          window.clearTimeout(slowTimer)
          assistantMsg.toolProcesses?.push({ toolName: event.toolName, summary: event.summary, status: 'running' })
          void scrollToBottom()
        },
        onConfirmRequired: (event) => {
          firstFrameReceived = true
          window.clearTimeout(slowTimer)
          assistantMsg.confirmCard = { ...event, status: 'waiting' }
          void scrollToBottom()
        },
        onMinderCommands: (event) => {
          firstFrameReceived = true
          window.clearTimeout(slowTimer)
          assistantMsg.dslCommands = event
          void scrollToBottom()
        },
        onDone: () => {
          window.clearTimeout(slowTimer)
          finishStream(assistantMsg)
          pinConversation(conversationId)
        },
        onError: (event) => {
          window.clearTimeout(slowTimer)
          finishStream(assistantMsg)
          assistantMsg.content += `\n\n> ${event.message}`
        },
      },
      onConnectionError: (error) => {
        window.clearTimeout(slowTimer)
        finishStream(assistantMsg)
        ElMessage.error(error.message)
      },
      onClose: () => {
        window.clearTimeout(slowTimer)
        finishStream(assistantMsg)
      },
    })
  }

  function handleStop(): void {
    controller.value?.cancel()
  }

  async function switchConversation(id: string): Promise<void> {
    if (id === activeConversationId.value) return
    controller.value?.cancel()
    activeConversationId.value = id
    messagesLoading.value = true
    try {
      const history = await fetchMessages(id)
      messages.value = history.map(toMessageItem)
    } catch {
      ElMessage.error('加载消息失败')
    } finally {
      messagesLoading.value = false
    }
    void scrollToBottom()
  }

  function handleNewConversation(): void {
    controller.value?.cancel()
    streaming.value = false
    activeConversationId.value = null
    messages.value = []
  }

  async function handleDeleteConversation(conv: AiConversation): Promise<void> {
    try {
      await ElMessageBox.confirm('删除后该会话及消息将不可恢复，确认清空？', '清空会话', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deleteConversation(conv.id)
      conversationItems.value = conversationItems.value.filter((c) => c.id !== conv.id)
      if (activeConversationId.value === conv.id) {
        activeConversationId.value = null
        messages.value = []
      }
    } catch {
      ElMessage.error('删除失败')
    }
  }

  function findConfirmCard(confirmToken: string): AssistantMessageItem['confirmCard'] {
    return messages.value.find((m) => m.confirmCard?.confirmToken === confirmToken)?.confirmCard ?? null
  }

  function handleConfirm(confirmToken: string): void {
    const card = findConfirmCard(confirmToken)
    if (!card || card.status !== 'waiting') return
    card.status = 'approved'
    streaming.value = true
    const replyMsg = reactive<AssistantMessageItem>({
      id: localId(),
      role: 'assistant',
      content: '',
      createdAt: nowIso(),
      streaming: true,
      toolProcesses: [],
    })
    messages.value.push(replyMsg)
    void scrollToBottom()

    controller.value?.cancel()
    controller.value = useAssistantStream({
      url: APPROVE_CONFIRMATION_URL,
      body: { confirmToken },
      handlers: {
        onDelta: (text) => {
          replyMsg.content += text
          void scrollToBottom()
        },
        onToolCall: (event) => {
          replyMsg.toolProcesses?.push({ toolName: event.toolName, summary: event.summary, status: 'running' })
        },
        onDone: () => finishStream(replyMsg),
        onError: (event) => {
          finishStream(replyMsg)
          card.status = 'failed'
          card.error = event.message
        },
      },
      onConnectionError: (error) => {
        finishStream(replyMsg)
        card.status = 'failed'
        card.error = error.message
      },
      onClose: () => finishStream(replyMsg),
    })
  }

  async function handleCancel(confirmToken: string): Promise<void> {
    const card = findConfirmCard(confirmToken)
    if (!card || card.status !== 'waiting') return
    try {
      await cancelConfirmation(confirmToken)
      card.status = 'cancelled'
    } catch {
      ElMessage.error('取消失败，请重试')
    }
  }

  function handleConfirmDsl(plan: DslPlan): void {
    if (!authStore.hasPermission('case:edit')) {
      ElMessage.warning('无文档编辑权限，无法执行编辑操作')
      return
    }
    const host = assistantContext.dslHost
    if (!host) {
      ElMessage.warning('请回到文档后重试')
      return
    }
    const result = host.apply(plan)
    messages.value.push({
      id: localId(),
      role: 'tool',
      content: `已执行：${result.applied} 处变更${result.skipped ? `（${result.skipped} 处跳过）` : ''}`,
      createdAt: nowIso(),
    })
    void scrollToBottom()
  }

  function handleCancelDsl(): void {
    messages.value.push({ id: localId(), role: 'tool', content: '已取消，无任何变更', createdAt: nowIso() })
    void scrollToBottom()
  }

  function handleSidebarScroll({ scrollTop }: { scrollTop: number; scrollLeft: number }): void {
    const wrap = sidebarScroller.value?.wrapRef
    if (!wrap) return
    if (wrap.scrollHeight - scrollTop - wrap.clientHeight < 20) {
      void loadMoreConversations()
    }
  }

  watch(
    () => authStore.activeWorkspace?.id,
    () => {
      controller.value?.cancel()
      streaming.value = false
      activeConversationId.value = null
      messages.value = []
    },
  )

  onMounted(() => {
    void refreshConversations().then(() => {
      const first = conversationItems.value[0]
      if (first && !activeConversationId.value) void switchConversation(first.id)
    })
  })

  onBeforeUnmount(() => {
    controller.value?.cancel()
  })

  const minimized = computed(() => options.minimized === true)

  return {
    noWorkspace,
    conversationItems,
    conversationsLoading,
    conversationsLoadingMore,
    activeConversationId,
    messages,
    messagesLoading,
    streaming,
    input,
    messageScroller,
    sidebarScroller,
    minimized,
    handleSend,
    handleStop,
    switchConversation,
    handleNewConversation,
    handleDeleteConversation,
    handleConfirm,
    handleCancel,
    handleConfirmDsl,
    handleCancelDsl,
    handleSidebarScroll,
    onMinimize,
    onClose,
  }
}
