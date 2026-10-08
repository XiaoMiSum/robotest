<script setup lang="ts">
import { Paperclip } from '@element-plus/icons-vue'
import type { AiAssistantMessage } from '@/types'

/** 用户消息（交互 05 §2.2）：正文 + 附件引用 */
defineProps<{ message: AiAssistantMessage }>()
</script>

<template>
  <div class="ai-user-message">
    <p class="ai-user-message__role">👤 用户</p>
    <div class="ai-user-message__content">{{ message.content }}</div>
    <ul v-if="message.attachments && message.attachments.length > 0" class="ai-user-message__attachments">
      <li
        v-for="item in message.attachments"
        :key="`${item.entityType}:${item.entityId}`"
        class="ai-user-message__attachment"
      >
        <el-icon :size="12"><Paperclip /></el-icon>
        <span>{{ item.entityTitle ?? item.entityType }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.ai-user-message {
  align-self: flex-end;
  max-width: 85%;
  padding: var(--space-sm);
  border-radius: var(--radius-lg) var(--radius-lg) 2px var(--radius-lg);
  background: var(--color-primary-50);

  &__role {
    font-size: var(--font-size-xs);
    font-weight: 600;
    color: var(--color-neutral-500);
  }

  &__content {
    font-size: 14px;
    color: var(--color-neutral-900);
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__attachments {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
    margin: var(--space-xs) 0 0;
    padding: 0;
    list-style: none;
  }

  &__attachment {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--color-neutral-0);
    color: var(--color-neutral-600);
    font-size: var(--font-size-xs);
  }
}
</style>
