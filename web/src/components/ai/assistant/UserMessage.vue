<script setup lang="ts">
import { ref, watch } from 'vue'
import { Loading, Paperclip } from '@element-plus/icons-vue'
import { signedUrlFor } from '@/composables/common/markdownImage'
import type { AiAssistantMessage } from '@/types'

/** 用户消息（交互 05 §2.2）：正文 + 附件引用 + 附图缩略图（经换签加载，失败占位） */
const props = defineProps<{ message: AiAssistantMessage }>()

const urls = ref<Record<string, string>>({})
const failed = ref<Record<string, boolean>>({})

watch(
  () => props.message.images ?? [],
  async (images) => {
    for (const image of images) {
      if (urls.value[image.fileId] || failed.value[image.fileId]) continue
      try {
        urls.value = { ...urls.value, [image.fileId]: await signedUrlFor(image.fileId) }
      } catch {
        // 占位后不再重试，避免失效引用被反复请求
        failed.value = { ...failed.value, [image.fileId]: true }
      }
    }
  },
  { immediate: true },
)

/** 签名地址仍不可达（对象被治理等）时降级为文字占位，不重试循环请求 */
function markFailed(fileId: string): void {
  failed.value = { ...failed.value, [fileId]: true }
}
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
    <ul v-if="message.images && message.images.length > 0" class="ai-user-message__images">
      <li v-for="image in message.images" :key="image.fileId" class="ai-user-message__image">
        <img
          v-if="urls[image.fileId]"
          :src="urls[image.fileId]"
          :alt="image.name ?? '附图'"
          @error="markFailed(image.fileId)"
        />
        <span v-else-if="failed[image.fileId]" class="ai-user-message__image-failed">图片加载失败</span>
        <el-icon v-else class="is-loading" :size="16"><Loading /></el-icon>
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

  &__images {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
    margin: var(--space-xs) 0 0;
    padding: 0;
    list-style: none;
  }

  &__image {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 96px;
    height: 96px;
    overflow: hidden;
    border: 1px solid var(--color-neutral-200);
    border-radius: var(--radius-md);
    background: var(--color-neutral-0);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__image-failed {
    color: var(--color-neutral-500);
    font-size: var(--font-size-xs);
  }
}
</style>
