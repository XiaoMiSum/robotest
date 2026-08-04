<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ScrollbarInstance } from 'element-plus'
import type { AiStreamController } from '@/composables/useAiStream'
import { useAssistantStream } from '@/composables/useAssistantStream'
import { useConversationList } from '@/composables/useConversationList'
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
import { formatDateTime, truncateText } from '@/utils/format'
import MessageItem, { type AssistantMessageItem } from './MessageItem.vue'
import AiModelSelect from '@/components/common/AiModelSelect.vue'
import type { DslPlan } from '@/components/project/minder/ai/dslRunner'

/**
 * 助手对话面板（全局智能助手交互设计 1.2/2/3/4）：
 * 左侧会话列表（游标分页滚动加载 + 本地置顶），右侧消息流（SSE 流式逐字渲染），
 * 底部输入区（模型选择器 + 发送/停止）。流式期间输入禁用、[发送] 变 [停止]，
 * 已渲染内容在停止后保留为最终消息。确认卡片 approve 经独立 SSE 流消费收尾答复。
 *
 * 最小化语义：minimized 为 true 时仅 v-show 隐藏（组件保持挂载），
 * 进行中的流式回复不被中断；关闭（emit close）由父组件销毁本组件，重开会话列表重载。
 */
const props = defineProps<{ minimized?: boolean }>()
const emit = defineEmits<{
  minimize: []
  close: []
}>()

const authStore = useAuthStore()
const aiStore = useAiStore()
const assistantContext = useAssistantContextStore()

// ==================== 会话列表（交互设计 3：触底加载 + 本地置顶 + 空间隔离） ====================

// 解构为顶层 ref 以在模板中自动解包（嵌套 ref 不会被模板自动 unwrap）
const { items: conversationItems, loading: conversationsLoading, loadingMore: conversationsLoadingMore, refresh: refreshConversations, loadMore: loadMoreConversations, prepend: prependConversation } = useConversationList({
  workspaceId: () => authStore.activeWorkspace?.id ?? '',
})

// ==================== 当前会话消息流 ====================

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

/** 历史 AiMessage → 消息视图模型：assistant 的工具调用载荷渲染为已完成过程卡片 */
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

/** 流收尾：关闭光标、过程卡片全部置完成（4.3）、解除流式锁（幂等，取消/完成/错误共用） */
function finishStream(message: AssistantMessageItem): void {
  message.streaming = false
  message.toolProcesses?.forEach((p) => {
    p.status = 'done'
  })
  streaming.value = false
}

/** 发送消息后本地置顶（交互设计 3：不重拉列表；标题沿用列表现值，服务端首条后自动更名） */
function pinConversation(id: string): void {
  const existing = conversationItems.value.find((c) => c.id === id)
  prependConversation({ id, title: existing?.title ?? '新会话', lastActiveAt: nowIso() })
}

// ==================== 发送 / 停止（交互设计 4.1） ====================

async function handleSend(): Promise<void> {
  const content = input.value.trim()
  if (!content || streaming.value) return
  if (!authStore.activeWorkspace?.id) {
    ElMessage.warning('请先选择工作空间')
    return
  }

  // 无活跃会话时先落库空会话，首条消息后会话入列并本地置顶（交互设计 3）
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
  const assistantMsg: AssistantMessageItem = {
    id: localId(),
    role: 'assistant',
    content: '',
    createdAt: nowIso(),
    streaming: true,
    toolProcesses: [],
    confirmCard: null,
    dslCommands: null,
  }
  messages.value.push(assistantMsg)
  streaming.value = true
  void scrollToBottom()

  // 首帧超 10 秒提示（交互设计 4.1：响应较慢，可停止重试），收到任一事件即取消
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
        // 确认请求后本轮 SSE 随即以 done 结束，卡片由前端维持「等待确认」（详细设计 3.3/4.2）
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

/** 停止回复：断开 SSE，已渲染内容保留为最终消息（交互设计 4.1） */
function handleStop(): void {
  controller.value?.cancel()
}

// ==================== 会话切换 / 新建 / 删除（交互设计 3） ====================

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

/** 新建会话：消息流清空，会话在首条消息发送后创建（交互设计 3） */
function handleNewConversation(): void {
  controller.value?.cancel()
  streaming.value = false
  activeConversationId.value = null
  messages.value = []
}

/** 清空会话：二次确认后逻辑删除，当前会话则一并关闭（交互设计 3） */
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

// ==================== 确认卡片（交互设计 5：approve 走 SSE / 取消走 REST） ====================

function findConfirmCard(confirmToken: string): AssistantMessageItem['confirmCard'] {
  return messages.value.find((m) => m.confirmCard?.confirmToken === confirmToken)?.confirmCard ?? null
}

/** 确认执行：卡片置执行成功，approve SSE 流式生成收尾答复（详细设计 3.3.1）；失败置 failed 展示原因 */
function handleConfirm(confirmToken: string): void {
  const card = findConfirmCard(confirmToken)
  if (!card || card.status !== 'waiting') return
  card.status = 'approved'
  streaming.value = true
  const replyMsg: AssistantMessageItem = {
    id: localId(),
    role: 'assistant',
    content: '',
    createdAt: nowIso(),
    streaming: true,
    toolProcesses: [],
  }
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

/** 取消：即时落库 tool 消息并返回 200（详细设计 3.3.2），卡片置已取消 */
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

// ==================== DSL 执行（详细设计 4.3：本地执行 + 本地提示消息，不回传后端） ====================

/** 确认 DSL 预览：经上下文桥宿主执行（单撤销组）；宿主缺失按已离开文档页处理 */
function handleConfirmDsl(plan: DslPlan): void {
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

/** 取消 DSL 预览：无任何变更，仅追加本地提示（交互设计 6.2） */
function handleCancelDsl(): void {
  messages.value.push({ id: localId(), role: 'tool', content: '已取消，无任何变更', createdAt: nowIso() })
  void scrollToBottom()
}

// ==================== 滚动与生命周期 ====================

const messageScroller = ref<ScrollbarInstance | null>(null)
const sidebarScroller = ref<ScrollbarInstance | null>(null)

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const wrap = messageScroller.value?.wrapRef
  if (wrap) messageScroller.value?.setScrollTop(wrap.scrollHeight)
}

/** 会话列表触底加载下一页（交互设计 3：加载中显示骨架条，无更多不再触发） */
function handleSidebarScroll(event: Event): void {
  const target = event.target as HTMLElement
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 20) {
    void loadMoreConversations()
  }
}

/** 空间切换：会话列表由 useConversationList 重置，当前会话一并关闭（交互设计 2/8.3） */
watch(
  () => authStore.activeWorkspace?.id,
  () => {
    controller.value?.cancel()
    streaming.value = false
    activeConversationId.value = null
    messages.value = []
  },
)

/** 打开面板：重载会话列表并恢复最近一次会话（交互设计 2） */
onMounted(() => {
  void refreshConversations().then(() => {
    const first = conversationItems.value[0]
    if (first && !activeConversationId.value) void switchConversation(first.id)
  })
})

// 组件销毁（关闭面板 / AI 开关关闭）时中断进行中的流式回复（8.3）
onBeforeUnmount(() => {
  controller.value?.cancel()
})

const minimized = computed(() => props.minimized === true)
</script>

<template>
  <transition name="assistant-slide">
    <aside v-show="!minimized" class="assistant-panel">
      <header class="assistant-panel__header">
        <span class="assistant-panel__title">✨ 智能助手</span>
        <div class="assistant-panel__actions">
          <el-button text title="最小化" @click="emit('minimize')">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button text title="关闭" @click="emit('close')">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="assistant-panel__body">
        <!-- 会话列表（交互设计 1.2 左侧） -->
        <aside class="assistant-panel__sidebar">
          <div class="assistant-panel__new">
            <el-button size="small" type="primary" plain @click="handleNewConversation">
              <el-icon><Plus /></el-icon>新会话
            </el-button>
          </div>
          <el-scrollbar ref="sidebarScroller" class="assistant-panel__conv-scroll" @scroll="handleSidebarScroll">
            <div v-if="conversationsLoading" class="assistant-panel__skeleton">
              <el-skeleton :rows="6" animated />
            </div>
            <div
              v-for="conv in conversationItems"
              :key="conv.id"
              class="conv-item"
              :class="{ 'conv-item--active': conv.id === activeConversationId }"
              @click="switchConversation(conv.id)"
            >
              <div class="conv-item__title" :title="conv.title">{{ truncateText(conv.title, 14) }}</div>
              <div class="conv-item__meta">
                <span class="conv-item__time">{{ formatDateTime(conv.lastActiveAt) }}</span>
                <el-button text size="small" class="conv-item__clear" @click.stop="handleDeleteConversation(conv)">
                  清空
                </el-button>
              </div>
            </div>
            <div v-if="conversationsLoadingMore" class="assistant-panel__skeleton">
              <el-skeleton :rows="2" animated />
            </div>
            <div v-if="!conversationItems.length && !conversationsLoading" class="assistant-panel__empty">
              暂无会话
            </div>
          </el-scrollbar>
        </aside>

        <!-- 消息流 + 输入区（交互设计 1.2 右侧） -->
        <section class="assistant-panel__chat">
          <el-scrollbar ref="messageScroller" class="assistant-panel__messages">
            <div v-if="messagesLoading" class="assistant-panel__loading">
              <el-skeleton :rows="4" animated />
            </div>
            <div v-else-if="!messages.length" class="assistant-panel__empty">开始与智能助手对话</div>
            <MessageItem
              v-for="msg in messages"
              :key="msg.id"
              :message="msg"
              @confirm="handleConfirm"
              @cancel="handleCancel"
              @confirm-dsl="handleConfirmDsl"
              @cancel-dsl="handleCancelDsl"
            />
          </el-scrollbar>

          <footer class="assistant-panel__input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="1"
              resize="none"
              :disabled="streaming"
              placeholder="输入消息…"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="assistant-panel__toolbar">
              <AiModelSelect />
              <el-button
                v-if="!streaming"
                type="primary"
                size="small"
                :disabled="!input.trim()"
                @click="handleSend"
              >
                发送
              </el-button>
              <el-button v-else type="danger" size="small" @click="handleStop">停止</el-button>
            </div>
          </footer>
        </section>
      </div>
    </aside>
  </transition>
</template>

<style scoped lang="scss">
.assistant-panel {
  position: fixed;
  top: var(--header-height);
  right: 0;
  bottom: 0;
  width: 780px;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-left: 1px solid var(--el-border-color-light);
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.08);
  z-index: 90;
}

.assistant-slide-enter-active,
.assistant-slide-leave-active {
  transition: transform 0.25s ease;
}

.assistant-slide-enter-from,
.assistant-slide-leave-to {
  transform: translateX(100%);
}

.assistant-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 8px 0 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.assistant-panel__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.assistant-panel__actions {
  display: flex;
  align-items: center;
}

.assistant-panel__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.assistant-panel__sidebar {
  width: 220px;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.assistant-panel__new {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.assistant-panel__conv-scroll {
  flex: 1;
  min-height: 0;
}

.assistant-panel__skeleton {
  padding: 12px;
}

.assistant-panel__empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.conv-item {
  padding: 8px 12px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background-color var(--transition-fast);

  &:hover {
    background: var(--el-fill-color-light);

    .conv-item__clear {
      opacity: 1;
    }
  }

  &--active {
    background: var(--el-color-primary-light-9);
    border-left-color: var(--el-color-primary);
  }
}

.conv-item__title {
  font-size: 13px;
  color: var(--el-text-color-primary);
  margin-bottom: 2px;
}

.conv-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conv-item__time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.conv-item__clear {
  opacity: 0;
  padding: 0 2px;
  font-size: 12px;
  transition: opacity var(--transition-fast);
}

.assistant-panel__chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.assistant-panel__messages {
  flex: 1;
  min-height: 0;
  padding: 16px;
}

.assistant-panel__loading {
  padding: 16px;
}

.assistant-panel__input {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 10px 12px;
  flex-shrink: 0;
}

.assistant-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
</style>
