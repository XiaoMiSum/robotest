<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
import AssistantIcon from './AssistantIcons.vue'
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

// 未选择工作空间（如 /workspaces 列表页）：面板显示引导态，会话/消息/输入均不可用（交互设计 1.1/2）
const noWorkspace = computed(() => !authStore.activeWorkspace?.id)

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
  // reactive 包装：回调中直接改 assistantMsg 需触发模板更新，普通对象 push 后与响应式代理分离导致视图冻结
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
  // 需求 3.5.3：仅编辑权限用户可编辑，无权限时连变更也不触发（最终防线，预览入口已拦一次）
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
function handleSidebarScroll({ scrollTop }: { scrollTop: number; scrollLeft: number }): void {
  const wrap = sidebarScroller.value?.wrapRef
  if (!wrap) return
  if (wrap.scrollHeight - scrollTop - wrap.clientHeight < 20) {
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
        <div class="assistant-panel__heading">
          <span class="assistant-panel__logo">
            <AssistantIcon name="sparkles" :size="14" />
          </span>
          <div class="assistant-panel__titles">
            <span class="assistant-panel__title">智能助手</span>
            <span class="assistant-panel__status">
              <span class="assistant-panel__status-dot" aria-hidden="true"></span>
              在线
            </span>
          </div>
        </div>
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
            <el-button size="small" type="primary" plain :disabled="noWorkspace" @click="handleNewConversation">
              <el-icon><Plus /></el-icon>新会话
            </el-button>
          </div>
          <el-scrollbar ref="sidebarScroller" class="assistant-panel__conv-scroll" @scroll="handleSidebarScroll">
            <div v-if="noWorkspace" class="assistant-panel__empty">请先选择工作空间</div>
            <template v-else>
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
            </template>
          </el-scrollbar>
        </aside>

        <!-- 消息流 + 输入区（交互设计 1.2 右侧） -->
        <section class="assistant-panel__chat">
          <el-scrollbar ref="messageScroller" class="assistant-panel__messages">
            <div v-if="noWorkspace" class="assistant-panel__empty">请先选择工作空间后使用智能助手</div>
            <template v-else>
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
            </template>
          </el-scrollbar>

          <footer class="assistant-panel__input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 3 }"
              resize="none"
              :disabled="noWorkspace || streaming"
              placeholder="输入消息…"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="assistant-panel__toolbar">
              <AiModelSelect />
              <div class="assistant-panel__toolbar-actions">
                <span class="assistant-panel__hint">Enter 发送</span>
                <el-button
                  v-if="!streaming"
                  class="assistant-panel__send"
                  type="primary"
                  size="small"
                  :disabled="noWorkspace || !input.trim()"
                  @click="handleSend"
                >
                  发送
                </el-button>
                <el-button v-else type="danger" size="small" @click="handleStop">停止</el-button>
              </div>
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
  background: var(--color-neutral-0);
  border-left: 1px solid var(--color-neutral-200);
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
  height: 52px;
  padding: 0 var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-100);
  flex-shrink: 0;
}

.assistant-panel__heading {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.assistant-panel__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-700));
  color: var(--color-neutral-0);
}

.assistant-panel__titles {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.assistant-panel__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.assistant-panel__status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-neutral-400);
}

.assistant-panel__status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-success);
}

.assistant-panel__actions {
  display: flex;
  align-items: center;
}

.assistant-panel__actions :deep(.el-button) {
  border-radius: var(--radius-md);
}

.assistant-panel__actions :deep(.el-button:hover) {
  background: var(--color-neutral-100);
}

.assistant-panel__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.assistant-panel__sidebar {
  width: 220px;
  border-right: 1px solid var(--color-neutral-100);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.assistant-panel__new {
  // 按钮右对齐，贴近会话列右缘（与消息区对齐）
  display: flex;
  justify-content: flex-end;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-neutral-100);
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
  color: var(--color-neutral-400);
}

.conv-item {
  position: relative;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);

  // 激活态左缘圆角指示条（伪元素避免影响布局）
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 3px;
    height: 16px;
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    background: transparent;
    transition: background-color var(--transition-fast);
  }

  &:hover {
    background: var(--color-neutral-100);

    .conv-item__clear {
      opacity: 1;
    }
  }

  &--active {
    background: var(--color-primary-50);

    &::before {
      background: var(--color-primary-500);
    }
  }
}

.conv-item__title {
  font-size: 13px;
  color: var(--color-neutral-700);
  margin-bottom: 2px;
}

.conv-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conv-item__time {
  font-size: 11px;
  color: var(--color-neutral-400);
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
  padding: var(--space-lg);
}

.assistant-panel__loading {
  padding: 16px;
}

.assistant-panel__input {
  border-top: 1px solid var(--color-neutral-100);
  padding: var(--space-md) var(--space-lg);
  flex-shrink: 0;
}

.assistant-panel__input :deep(.el-textarea__inner) {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-neutral-200);
  box-shadow: none;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  // 达到 autosize 3 行上限后内部滚动，避免撑破面板
  overflow-y: auto;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.assistant-panel__input :deep(.el-textarea__inner:focus) {
  border-color: var(--color-primary-500);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.assistant-panel__input :deep(.el-textarea__inner::placeholder) {
  color: var(--color-neutral-400);
}

.assistant-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-sm);
}

.assistant-panel__toolbar-actions {
  // 恒靠右：AiModelSelect 单模型时仅渲染只读标签（交互设计 2.8），space-between 下仍保证操作区贴右
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-left: auto;
}

.assistant-panel__hint {
  font-size: 11px;
  color: var(--color-neutral-400);
}

.assistant-panel__send {
  border: none;
  background: linear-gradient(135deg, var(--color-primary-500), var(--color-primary-600));
  color: var(--color-neutral-0);
  transition: background var(--transition-fast), box-shadow var(--transition-fast);

  &:hover {
    background: var(--color-primary-600);
  }

  &.is-disabled {
    background: var(--color-neutral-200);
    color: var(--color-neutral-400);
  }
}
</style>
