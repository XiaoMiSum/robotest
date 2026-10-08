<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import { citationRoute } from '@/utils/assistant'
import type { AiAssistantCitation, AiAssistantMessage } from '@/types'

/** 助手消息（交互 05 §2.2）：Markdown 正文 + citations 来源引用展开与跳转 */
const props = defineProps<{
  message: AiAssistantMessage
  /** 断线恢复轮询中且消息未到终态：标记「恢复中…」（交互 05 §2.4 断线分支） */
  recovering: boolean
}>()

const emit = defineEmits<{ jump: [citation: AiAssistantCitation] }>()

const expanded = ref<number | null>(null)

function toggle(index: number): void {
  expanded.value = expanded.value === index ? null : index
}

/** 恢复标记仅挂在未终态消息上，终态内容以服务端落盘为准 */
const showRecovering = computed(
  () => props.recovering && props.message.status !== 'done',
)
</script>

<template>
  <div class="ai-assistant-message">
    <p class="ai-assistant-message__role">🤖 助手</p>
    <MarkdownView
      v-if="message.content"
      :content="message.content"
      class="ai-assistant-message__content"
    />
    <p v-if="showRecovering" class="ai-assistant-message__recovering">恢复中…</p>
    <ul
      v-if="message.citations && message.citations.length > 0"
      class="ai-assistant-message__citations"
    >
      <li
        v-for="(citation, index) in message.citations"
        :key="`${citation.type ?? ''}:${citation.id ?? ''}:${index}`"
        class="ai-citation"
      >
        <button
          type="button"
          class="ai-citation__title"
          :aria-expanded="expanded === index"
          @click="toggle(index)"
        >
          {{ citation.title ?? citation.id ?? '来源引用' }}
        </button>
        <button
          v-if="citationRoute(citation.type, citation.id)"
          type="button"
          class="ai-citation__jump"
          @click="emit('jump', citation)"
        >
          跳转
        </button>
        <p v-if="expanded === index && citation.quote" class="ai-citation__quote">
          {{ citation.quote }}
        </p>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.ai-assistant-message {
  align-self: flex-start;
  max-width: 92%;

  &__role {
    font-size: var(--font-size-xs);
    font-weight: 600;
    color: var(--color-neutral-500);
  }

  &__content {
    font-size: 14px;
    color: var(--color-neutral-900);
  }

  &__recovering {
    display: inline-block;
    margin-top: var(--space-xs);
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--color-neutral-100);
    color: var(--color-neutral-500);
    font-size: var(--font-size-xs);
  }

  &__citations {
    margin: var(--space-xs) 0 0;
    padding: 0;
    list-style: none;
  }
}

.ai-citation {
  padding: var(--space-xs) 0;
  border-top: 1px solid var(--color-neutral-100);

  &__title,
  &__jump {
    padding: 0;
    border: none;
    background: none;
    color: var(--color-primary-500);
    font-size: 13px;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }

  &__jump {
    margin-left: var(--space-sm);
    color: var(--color-neutral-500);
  }

  &__quote {
    margin: var(--space-xs) 0 0;
    padding: var(--space-xs) var(--space-sm);
    border-left: 2px solid var(--color-primary-200);
    background: var(--color-neutral-50);
    color: var(--color-neutral-600);
    font-size: 13px;
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
