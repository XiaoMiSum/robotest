<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import AssistantMessage from './AssistantMessage.vue'
import ClarifyCard from './ClarifyCard.vue'
import PreviewCard from './PreviewCard.vue'
import ReceiptCard from './ReceiptCard.vue'
import UserMessage from './UserMessage.vue'
import { ASSISTANT_EXAMPLES, isClarifyMessage, parseClarifyContent } from '@/utils/assistant'
import type {
  AiAssistantCitation,
  AiAssistantClarify,
  AiAssistantExecution,
  AiAssistantIntent,
  AiAssistantMessage,
} from '@/types'

/** 距底部阈值内视为跟随；触顶阈值内触发加载更早页（交互 05 §2.1 MessageTimeline） */
const FOLLOW_THRESHOLD = 40
const LOAD_THRESHOLD = 8

const props = defineProps<{
  messages: AiAssistantMessage[]
  loading: boolean
  hasMore: boolean
  streaming: boolean
  streamText: string
  recovering: boolean
  /** 预览确认执行 / 回执重试进行中：禁用卡片操作（交互 05 §2.3） */
  executing: boolean
  /** 归档会话只读（详设 3.4） */
  readonly: boolean
}>()

const emit = defineEmits<{
  'load-older': []
  'pick-example': [text: string]
  'clarify-pick': [option: string]
  'citation-jump': [citation: AiAssistantCitation]
  'preview-execute': [messageId: string]
  'preview-cancel': [messageId: string]
  'preview-edit': [text: string]
  'preview-reparse': [messageId: string]
  'receipt-retry': [messageId: string, index: number]
  'receipt-jump': [path: string]
}>()

interface TimelineRow {
  kind: 'user' | 'clarify' | 'assistant' | 'preview' | 'receipt'
  message: AiAssistantMessage
  clarify: AiAssistantClarify | null
  intent: AiAssistantIntent | null
  execution: AiAssistantExecution | null
}

const scrollEl = ref<HTMLElement | null>(null)
const follow = ref(true)

/** 预览 / 回执消息可无正文（只承载 intent / execution），不渲染即丢失操作入口 */
const rows = computed<TimelineRow[]>(() => {
  const result: TimelineRow[] = []
  for (const message of props.messages) {
    const visible =
      message.intent !== null ||
      message.execution !== null ||
      Boolean(message.content) ||
      (props.recovering && message.status !== 'done')
    if (!visible) continue
    if (message.role === 'user') {
      result.push({ kind: 'user', message, clarify: null, intent: null, execution: null })
    } else if (message.execution !== null) {
      // 已执行 / 已取消后预览卡让位回执卡（交互 05 §2.3）
      result.push({ kind: 'receipt', message, clarify: null, intent: message.intent, execution: message.execution })
    } else if (message.intent !== null) {
      result.push({ kind: 'preview', message, clarify: null, intent: message.intent, execution: null })
    } else if (isClarifyMessage(message)) {
      result.push({
        kind: 'clarify',
        message,
        clarify: parseClarifyContent(message.content),
        intent: null,
        execution: null,
      })
    } else {
      result.push({ kind: 'assistant', message, clarify: null, intent: null, execution: null })
    }
  }
  return result
})

const empty = computed(
  () =>
    props.messages.length === 0 && !props.streaming && props.streamText === '',
)

function scrollToBottom(): void {
  const el = scrollEl.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function onScroll(): void {
  const el = scrollEl.value
  if (!el) return
  if (el.scrollTop <= LOAD_THRESHOLD && props.hasMore && !props.loading) {
    emit('load-older')
  }
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  follow.value = distance <= FOLLOW_THRESHOLD
}

function backToLatest(): void {
  follow.value = true
  scrollToBottom()
}

// 消息集合与流式增量均触发：刷新替换消息时引用变化亦进入回调，跟随时自动贴底
watch(
  () => [props.messages, props.streamText],
  async () => {
    if (!follow.value) return
    await nextTick()
    scrollToBottom()
  },
)

onMounted(() => {
  scrollToBottom()
})
</script>

<template>
  <div class="ai-timeline">
    <div ref="scrollEl" class="ai-timeline__scroll" @scroll="onScroll">
      <p v-if="loading && messages.length === 0" class="ai-timeline__hint">加载中…</p>
      <div v-else-if="empty" class="ai-timeline__empty">
        <p class="ai-timeline__hint">试试这些指令：</p>
        <button
          v-for="example in ASSISTANT_EXAMPLES"
          :key="example"
          type="button"
          class="ai-timeline__example"
          @click="emit('pick-example', example)"
        >
          {{ example }}
        </button>
      </div>
      <template v-else>
        <p v-if="loading" class="ai-timeline__hint">加载更早…</p>
        <template v-for="row in rows" :key="row.message.id">
          <UserMessage v-if="row.kind === 'user'" :message="row.message" />
          <div v-else-if="row.kind === 'clarify'" class="ai-timeline__clarify">
            <p class="ai-timeline__role">🤖 助手</p>
            <ClarifyCard
              :question="row.clarify?.question ?? ''"
              :options="row.clarify?.options ?? []"
              @pick="emit('clarify-pick', $event)"
            />
          </div>
          <div v-else-if="row.kind === 'preview'" class="ai-timeline__card">
            <p class="ai-timeline__role">🤖 助手</p>
            <MarkdownView
              v-if="row.message.content"
              :content="row.message.content"
              class="ai-timeline__card-content"
            />
            <PreviewCard
              v-if="row.intent"
              :intent="row.intent"
              :executing="executing"
              :readonly="readonly"
              :editable="Boolean(row.message.content)"
              @execute="emit('preview-execute', row.message.id)"
              @cancel="emit('preview-cancel', row.message.id)"
              @edit="emit('preview-edit', row.message.content ?? '')"
              @reparse="emit('preview-reparse', row.message.id)"
            />
          </div>
          <div v-else-if="row.kind === 'receipt'" class="ai-timeline__card">
            <p class="ai-timeline__role">🤖 助手</p>
            <ReceiptCard
              v-if="row.execution"
              :execution="row.execution"
              :intent="row.intent"
              :executing="executing"
              :readonly="readonly"
              @retry="emit('receipt-retry', row.message.id, $event)"
              @jump="emit('receipt-jump', $event)"
            />
          </div>
          <AssistantMessage
            v-else
            :message="row.message"
            :recovering="recovering"
            @jump="emit('citation-jump', $event)"
          />
        </template>
        <div v-if="streaming && streamText" class="ai-timeline__stream">
          <p class="ai-timeline__role">🤖 助手</p>
          <MarkdownView :content="streamText" class="ai-timeline__stream-content" />
        </div>
      </template>
    </div>
    <button
      v-if="!follow"
      type="button"
      class="ai-timeline__latest"
      aria-label="回到最新"
      @click="backToLatest"
    >
      回到最新
    </button>
  </div>
</template>

<style scoped lang="scss">
.ai-timeline {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;

  &__scroll {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: var(--space-sm);
    display: flex;
    flex-direction: column;
    gap: var(--space-md);
  }

  &__hint {
    margin: 0;
    color: var(--color-neutral-400);
    font-size: var(--font-size-xs);
    text-align: center;
  }

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-xs);
    margin-top: var(--space-xl);
  }

  &__example {
    padding: 6px var(--space-md);
    border: 1px solid var(--color-neutral-200);
    border-radius: 999px;
    background: var(--color-neutral-0);
    color: var(--color-primary-500);
    font-size: 13px;
    cursor: pointer;
    transition: background-color var(--transition-fast);

    &:hover {
      background: var(--color-primary-50);
      border-color: var(--color-primary-200);
    }
  }

  &__role {
    margin: 0;
    font-size: var(--font-size-xs);
    font-weight: 600;
    color: var(--color-neutral-500);
  }

  &__clarify,
  &__card,
  &__stream {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
    max-width: 92%;
    align-self: flex-start;
  }

  &__stream-content,
  &__card-content {
    font-size: 14px;
  }

  &__latest {
    position: absolute;
    right: var(--space-md);
    bottom: var(--space-md);
    z-index: 1;
    padding: 4px var(--space-sm);
    border: 1px solid var(--color-primary-500);
    border-radius: 999px;
    background: var(--color-neutral-0);
    color: var(--color-primary-500);
    font-size: var(--font-size-xs);
    cursor: pointer;
    box-shadow: var(--shadow-sm);

    &:hover {
      background: var(--color-primary-50);
    }
  }
}
</style>
