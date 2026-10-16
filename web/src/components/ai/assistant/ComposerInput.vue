<script setup lang="ts">
import { computed, ref } from 'vue'
import { Close, Loading, Paperclip, Picture, Promotion, Refresh } from '@element-plus/icons-vue'
import ContextPicker from './ContextPicker.vue'
import type { AiAssistantEntityRef, AiAssistantPendingImage } from '@/types'

const props = defineProps<{
  modelValue: string
  /** 流式生成、执行中或归档会话禁用（store.canSend，交互 05 §2.3 / §2.4） */
  disabled: boolean
  attachments: AiAssistantEntityRef[]
  /** 待发附图（交互 05 §2.1）：上传中 / 失败态阻止发送 */
  images: AiAssistantPendingImage[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: [content: string]
  'add-attachment': [ref: AiAssistantEntityRef]
  'remove-attachment': [entityId: string]
  'add-images': [files: File[]]
  'remove-image': [key: string]
  'retry-image': [key: string]
}>()

const canSend = computed(
  () =>
    !props.disabled
    && props.modelValue.trim().length > 0
    // 附图未上传完成（含失败）不可发送：与 store.send 守卫同口径，不静默丢图
    && props.images.every((item) => item.status === 'done'),
)

const imageInput = ref<HTMLInputElement | null>(null)

function pickImages(): void {
  imageInput.value?.click()
}

function onImagesPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 清空取值：同一文件重选也要触发 change
  input.value = ''
  if (files.length > 0) emit('add-images', files)
}

function submit(): void {
  if (!canSend.value) return
  emit('send', props.modelValue.trim())
}

/** Enter 发送 / Shift+Enter 换行；中文输入法组词确认不触发发送 */
function onKeydown(event: Event): void {
  if (!(event instanceof KeyboardEvent)) return
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  submit()
}
</script>

<template>
  <div class="ai-composer">
    <div v-if="attachments.length > 0" class="ai-composer__attachments">
      <span
        v-for="item in attachments"
        :key="`${item.entityType}:${item.entityId}`"
        class="ai-composer__attachment"
      >
        <el-icon :size="12"><Paperclip /></el-icon>
        <span class="ai-composer__attachment-title">{{ item.entityTitle ?? item.entityType }}</span>
        <el-button
          text
          circle
          size="small"
          :icon="Close"
          :disabled="disabled"
          :aria-label="`移除附件：${item.entityTitle ?? item.entityId}`"
          @click="emit('remove-attachment', item.entityId)"
        />
      </span>
    </div>
    <div v-if="images.length > 0" class="ai-composer__images">
      <span
        v-for="item in images"
        :key="item.key"
        class="ai-composer__image"
        :class="{ 'is-failed': item.status === 'failed' }"
      >
        <el-icon v-if="item.status === 'uploading'" class="is-loading" :size="12"><Loading /></el-icon>
        <span class="ai-composer__image-name">{{ item.name }}</span>
        <el-button
          v-if="item.status === 'failed'"
          text
          circle
          size="small"
          :icon="Refresh"
          :disabled="disabled"
          :aria-label="`重试上传：${item.name}`"
          @click="emit('retry-image', item.key)"
        />
        <el-button
          text
          circle
          size="small"
          :icon="Close"
          :disabled="disabled"
          :aria-label="`移除图片：${item.name}`"
          @click="emit('remove-image', item.key)"
        />
      </span>
    </div>
    <div class="ai-composer__row">
      <ContextPicker
        :selected="attachments"
        :disabled="disabled"
        @add="emit('add-attachment', $event)"
      />
      <el-button
        text
        circle
        :icon="Picture"
        :disabled="disabled"
        aria-label="添加图片"
        @click="pickImages"
      />
      <input
        ref="imageInput"
        class="ai-composer__file"
        type="file"
        accept="image/*"
        multiple
        @change="onImagesPicked"
      />
      <el-input
        :model-value="modelValue"
        class="ai-composer__input"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 4 }"
        maxlength="4000"
        resize="none"
        :disabled="disabled"
        placeholder="输入一句话…（Enter 发送，Shift+Enter 换行）"
        @update:model-value="emit('update:modelValue', $event)"
        @keydown="onKeydown"
      />
      <el-button
        type="primary"
        :icon="Promotion"
        :disabled="!canSend"
        aria-label="发送"
        @click="submit"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-composer {
  padding: var(--space-sm);
  border-top: 1px solid var(--color-neutral-100);

  &__attachments {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
    margin-bottom: var(--space-xs);
  }

  &__attachment {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding-left: var(--space-xs);
    border: 1px solid var(--color-neutral-200);
    border-radius: 999px;
    background: var(--color-neutral-50);
    color: var(--color-neutral-600);
    font-size: var(--font-size-xs);
  }

  &__attachment-title {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__images {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
    margin-bottom: var(--space-xs);
  }

  &__image {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding-left: var(--space-xs);
    border: 1px solid var(--color-neutral-200);
    border-radius: 999px;
    background: var(--color-neutral-50);
    color: var(--color-neutral-600);
    font-size: var(--font-size-xs);

    &.is-failed {
      border-color: var(--color-danger-border);
      background: var(--color-danger-light);
      color: var(--color-danger-strong);
    }
  }

  &__image-name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__file {
    display: none;
  }

  &__row {
    display: flex;
    align-items: flex-end;
    gap: var(--space-xs);
  }

  &__input {
    flex: 1;
    min-width: 0;
  }
}
</style>
