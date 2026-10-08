<script setup lang="ts">
import { computed } from 'vue'
import { Close, Paperclip, Promotion } from '@element-plus/icons-vue'
import ContextPicker from './ContextPicker.vue'
import type { AiAssistantEntityRef } from '@/types'

const props = defineProps<{
  modelValue: string
  /** 流式生成、执行中或归档会话禁用（store.canSend，交互 05 §2.3 / §2.4） */
  disabled: boolean
  attachments: AiAssistantEntityRef[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: [content: string]
  'add-attachment': [ref: AiAssistantEntityRef]
  'remove-attachment': [entityId: string]
}>()

const canSend = computed(() => !props.disabled && props.modelValue.trim().length > 0)

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
    <div class="ai-composer__row">
      <ContextPicker
        :selected="attachments"
        :disabled="disabled"
        @add="emit('add-attachment', $event)"
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
