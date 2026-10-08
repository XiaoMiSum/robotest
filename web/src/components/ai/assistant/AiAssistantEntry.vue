<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import ComposerInput from './ComposerInput.vue'
import MessageTimeline from './MessageTimeline.vue'
import PanelHeader from './PanelHeader.vue'
import SessionList from './SessionList.vue'
import StreamIndicator from './StreamIndicator.vue'
import { useAssistantEntrance } from '@/composables/ai/useAssistantEntrance'
import { useAiAssistantStore } from '@/stores/aiAssistant'
import { citationRoute } from '@/utils/assistant'
import type { AiAssistantCitation } from '@/types'

/** 宽屏卡片固定尺寸；小屏由媒体查询接管且禁用拖动，夹取直接用常量避免拖动中读布局 */
const CARD_WIDTH = 400
const CARD_HEIGHT = 560
const SEARCH_DEBOUNCE_MS = 300

const store = useAiAssistantStore()
const router = useRouter()
const { visible, refresh } = useAssistantEntrance()

const listOpen = ref(true)
const narrow = ref(false)
const pos = ref<{ x: number; y: number } | null>(null)
const cardEl = ref<HTMLElement | null>(null)

const panelTitle = computed(() => store.currentConversation?.title ?? '助手')
const hasConversation = computed(() => store.currentConversation !== null)
const archived = computed(() => store.currentConversation?.status === 'archived')

void refresh()

// 打开即刷新会话列表；已有数据时静默对齐，避免重复闪加载态
watch(
  () => store.open,
  (open) => {
    if (!open) {
      stopDrag()
      return
    }
    void store.loadConversations(store.keyword, store.conversations.length > 0)
  },
)

// UI-PAGE-11：列表与消息加载失败由入口统一 toast，清空后同类失败可再次提示
watch(
  () => store.conversationsError,
  (error) => {
    if (!error) return
    ElMessage.error(error)
    store.clearConversationsError()
  },
)
watch(
  () => store.messagesError,
  (error) => {
    if (!error) return
    ElMessage.error(error)
    store.clearMessagesError()
  },
)

// ---------- 检索 ----------

let searchTimer: ReturnType<typeof setTimeout> | null = null

/** 防抖后按关键词重查，计时器由组件卸载时兜底清理 */
function handleSearch(keyword: string): void {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchTimer = null
    void store.loadConversations(keyword)
  }, SEARCH_DEBOUNCE_MS)
}

// ---------- 会话操作 ----------

function handleSelect(conversationId: string): void {
  listOpen.value = false
  void store.selectConversation(conversationId)
}

function handleNewSession(): void {
  store.resetSession()
}

async function handleRename(): Promise<void> {
  const conversation = store.currentConversation
  if (!conversation) return
  try {
    const { value } = await ElMessageBox.prompt('请输入会话名称', '重命名会话', {
      inputValue: conversation.title,
      inputValidator: (input: string) => {
        const trimmed = input.trim()
        if (!trimmed) return '会话名称不能为空'
        if (trimmed.length > 100) return '会话名称不能超过 100 字'
        return true
      },
    })
    await store.renameConversation(conversation.id, value.trim())
  } catch (error) {
    handleActionError(error, '重命名失败')
  }
}

async function handleArchive(): Promise<void> {
  const conversation = store.currentConversation
  if (!conversation) return
  try {
    await ElMessageBox.confirm(
      `确定归档会话「${conversation.title}」吗？归档后转为只读。`,
      '归档会话',
      { type: 'warning' },
    )
    await store.archiveConversation(conversation.id)
  } catch (error) {
    handleActionError(error, '归档失败')
  }
}

async function handleDelete(): Promise<void> {
  const conversation = store.currentConversation
  if (!conversation) return
  try {
    await ElMessageBox.confirm(
      `确定删除会话「${conversation.title}」吗？删除后不可恢复。`,
      '删除会话',
      { type: 'warning' },
    )
    await store.deleteConversation(conversation.id)
  } catch (error) {
    handleActionError(error, '删除失败')
  }
}

/** 取消 / 关闭弹窗是用户主动行为不提示，仅真实失败走页面级 toast（UI-PAGE-11） */
function handleActionError(error: unknown, fallback: string): void {
  if (error === 'cancel' || error === 'close') return
  ElMessage.error(error instanceof Error ? error.message : fallback)
}

// ---------- 消息与输入 ----------

function handleSend(content: string): void {
  void store.send(content)
}

function handleLoadOlder(): void {
  void store.loadOlderMessages()
}

/** 空会话示例只回填输入框，由用户确认后发送（交互 05 §2.4） */
function handlePickExample(text: string): void {
  store.setDraft(store.currentId ?? '', text)
}

/** 澄清快捷选项回填并立即发送，等价于用户自行输入后发送（交互 05 §2.2） */
function handleClarifyPick(option: string): void {
  store.setDraft(store.currentId ?? '', option)
  void store.send(option)
}

/** 引用跳转沿用回执链接口径：跳转后收起面板，会话保留可回看 */
function handleCitationJump(citation: AiAssistantCitation): void {
  const path = citationRoute(citation.type, citation.id)
  if (!path) return
  void router.push(path)
  store.closePanel()
}

// ---------- 预览确认执行与回执 ----------

/** 执行 / 取消 / 重试失败由入口统一 toast，成功态由回执卡就地渲染（UI-PAGE-11） */
async function runExclusive(action: () => Promise<void>, fallback: string): Promise<void> {
  try {
    await action()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : fallback)
  }
}

function handlePreviewExecute(messageId: string): void {
  void runExclusive(() => store.executePreview(messageId), '执行失败')
}

function handlePreviewCancel(messageId: string): void {
  void runExclusive(() => store.cancelPreview(messageId), '取消失败')
}

function handleReceiptRetry(messageId: string, index: number): void {
  void runExclusive(() => store.executePreview(messageId, [index]), '重试失败')
}

/** 返回修改：解析摘要回填输入框，由用户修改后重发（交互 05 §2.3） */
function handlePreviewEdit(text: string): void {
  if (!text.trim()) return
  store.setDraft(store.currentId ?? '', text)
}

/** 重新解析：重发预览前的原始指令并还原其附件，过期后重新生成预览（交互 05 §2.3） */
function handlePreviewReparse(messageId: string): void {
  const index = store.messages.findIndex((message) => message.id === messageId)
  if (index < 0) return
  const source = store.messages
    .slice(0, index)
    .reverse()
    .find((message) => message.role === 'user')
  if (!source?.content?.trim()) return
  if (source.attachments) store.setAttachments(source.attachments)
  // send 会同步清空当前会话草稿；重新解析不消费用户未发送的输入，调用后立即回填
  const draft = store.draftForCurrent
  const sending = store.send(source.content)
  if (draft) store.setDraft(store.currentId ?? '', draft)
  void sending
}

/** 回执链接为前端相对路由（详设 4.2⑦）：跳转并收起面板，会话保留可回看 */
function handleReceiptJump(path: string): void {
  void router.push(path)
  store.closePanel()
}

// ---------- 拖动 ----------

let dragOffset = { x: 0, y: 0 }

function handleDragStart(event: MouseEvent): void {
  if (narrow.value || !cardEl.value) return
  const rect = cardEl.value.getBoundingClientRect()
  // 首次拖动先把右下锚定换算成左上坐标，避免定位基准切换时跳位
  if (!pos.value) pos.value = { x: rect.left, y: rect.top }
  dragOffset = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  window.addEventListener('mousemove', handleDragMove)
  window.addEventListener('mouseup', stopDrag)
}

function handleDragMove(event: MouseEvent): void {
  if (!pos.value) return
  const maxX = window.innerWidth - CARD_WIDTH
  const maxY = window.innerHeight - CARD_HEIGHT
  pos.value = {
    x: Math.min(Math.max(event.clientX - dragOffset.x, 0), Math.max(maxX, 0)),
    y: Math.min(Math.max(event.clientY - dragOffset.y, 0), Math.max(maxY, 0)),
  }
}

function stopDrag(): void {
  window.removeEventListener('mousemove', handleDragMove)
  window.removeEventListener('mouseup', stopDrag)
}

/** 小屏或未拖动过时不下发内联定位，交由媒体查询决定贴底布局 */
const cardStyle = computed<CSSProperties | undefined>(() => {
  if (narrow.value || !pos.value) return undefined
  return { left: `${pos.value.x}px`, top: `${pos.value.y}px`, right: 'auto', bottom: 'auto' }
})

// ---------- 生命周期 ----------

function handleResize(): void {
  narrow.value = window.innerWidth < 768
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  stopDrag()
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<template>
  <div v-if="visible" class="ai-entry">
    <button
      type="button"
      class="ai-entry__ball"
      :class="{ 'ai-entry__ball--active': store.open }"
      aria-label="智能助手"
      :aria-expanded="store.open"
      @click="store.togglePanel()"
    >
      <el-icon :size="22"><MagicStick /></el-icon>
    </button>

    <section
      v-if="store.open"
      ref="cardEl"
      class="ai-entry__card"
      :style="cardStyle"
      aria-label="智能助手面板"
    >
      <PanelHeader
        :title="panelTitle"
        :has-conversation="hasConversation"
        :archived="archived"
        :list-open="listOpen"
        @toggle-list="listOpen = !listOpen"
        @rename="handleRename"
        @archive="handleArchive"
        @delete="handleDelete"
        @new-session="handleNewSession"
        @close="store.closePanel()"
        @drag-start="handleDragStart"
      />
      <SessionList
        v-if="listOpen"
        :conversations="store.conversations"
        :current-id="store.currentId"
        :loading="store.conversationsLoading"
        :keyword="store.keyword"
        @select="handleSelect"
        @search="handleSearch"
      />
      <MessageTimeline
        :messages="store.messages"
        :loading="store.messagesLoading"
        :has-more="store.messages.length < store.messageTotal"
        :streaming="store.streaming"
        :stream-text="store.streamText"
        :recovering="store.recovering"
        :executing="store.executing"
        :readonly="archived"
        @load-older="handleLoadOlder"
        @pick-example="handlePickExample"
        @clarify-pick="handleClarifyPick"
        @citation-jump="handleCitationJump"
        @preview-execute="handlePreviewExecute"
        @preview-cancel="handlePreviewCancel"
        @preview-edit="handlePreviewEdit"
        @preview-reparse="handlePreviewReparse"
        @receipt-retry="handleReceiptRetry"
        @receipt-jump="handleReceiptJump"
      />
      <StreamIndicator
        :streaming="store.streaming"
        :recovering="store.recovering"
        :error="store.streamError"
      />
      <ComposerInput
        :model-value="store.draftForCurrent"
        :disabled="!store.canSend"
        :attachments="store.attachmentsForCurrent"
        @update:model-value="store.setDraft(store.currentId ?? '', $event)"
        @send="handleSend"
        @add-attachment="store.addAttachment($event)"
        @remove-attachment="store.removeAttachment($event)"
      />
    </section>
  </div>
</template>

<style scoped lang="scss">
.ai-entry__ball {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border: 1px solid var(--color-neutral-200);
  border-radius: 50%;
  background: var(--color-neutral-0);
  color: var(--color-neutral-700);
  cursor: pointer;
  box-shadow: var(--shadow-float);
  transition:
    background-color var(--transition-fast),
    color var(--transition-fast),
    border-color var(--transition-fast);

  &:hover {
    background: var(--color-neutral-50);
    border-color: var(--color-neutral-300);
  }

  &--active {
    background: var(--color-primary-500);
    border-color: var(--color-primary-500);
    color: var(--color-neutral-0);
  }
}

.ai-entry__card {
  position: fixed;
  right: 24px;
  bottom: 84px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  width: 400px;
  height: 560px;
  overflow: hidden;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

@media (max-width: 767px) {
  .ai-entry__card {
    right: 8px;
    bottom: 0;
    left: 8px;
    width: auto;
    height: 70vh;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  }
}
</style>
