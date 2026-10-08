<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import AssistantMessage from './AssistantMessage.vue'
import ClarifyCard from './ClarifyCard.vue'
import UserMessage from './UserMessage.vue'
import { ASSISTANT_EXAMPLES, isClarifyMessage, parseClarifyContent } from '@/utils/assistant'
import type { AiAssistantCitation, AiAssistantClarify, AiAssistantMessage } from '@/types'

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
}>()

const emit = defineEmits<{
  'load-older': []
  'pick-example': [text: string]
  'clarify-pick': [option: string]
  'citation-jump': [citation: AiAssistantCitation]
}>()

interface TimelineRow {
  kind: 'user' | 'clarify' | 'assistant'
  message: AiAssistantMessage
  clarify: AiAssistantClarify | null
}

const scrollEl = ref<HTMLElement | null>(null)
const follow = ref(true)

/** 无正文且非恢复中标记的流式壳不渲染；澄清按落盘格式解析为选项卡 */
const rows = computed<TimelineRow[]>(() => {
  const result: TimelineRow[] = []
  for (const message of props.messages) {
    const visible =
      Boolean(message.content) || (props.recovering && message.status !== 'done')
    if (!visible) continue
    if (message.role === 'user') {
      result.push({ kind: 'user', message, clarify: null })
    } else if (isClarifyMessage(message)) {
      result.push({ kind: 'clarify', message, clarify: parseClarifyContent(message.content) })
    } else {
      result.push({ kind: 'assistant', message, clarify: null })
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
  &__stream {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
    max-width: 92%;
    align-self: flex-start;
  }

  &__stream-content {
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
