<script setup lang="ts">
import type { ConfirmItemResult } from '@/composables/project/ai/useAiArtifactReview'

defineProps<{
  receipt: ConfirmItemResult[]
  confirming: boolean
}>()

const emit = defineEmits<{
  retry: [item: ConfirmItemResult]
  clear: []
}>()

const hasFailures = (items: ConfirmItemResult[]): boolean => items.some((item) => !item.success)
</script>

<template>
  <!-- 逐项回执：失败项可单项重试（交互 2.3 回执） -->
  <div class="ai-receipt">
    <div class="ai-receipt__head">
      <span :class="hasFailures(receipt) ? 'ai-receipt__head--fail' : 'ai-receipt__head--ok'">
        {{ hasFailures(receipt) ? '部分确认失败' : '确认完成' }}
      </span>
      <el-button link @click="emit('clear')">收起回执</el-button>
    </div>
    <ul class="ai-receipt__list">
      <li
        v-for="item in receipt"
        :key="`${item.key}-${item.action}`"
        class="ai-receipt__item"
        :class="item.success ? 'ai-receipt__item--ok' : 'ai-receipt__item--fail'"
      >
        <span class="ai-receipt__title">{{ item.title }}</span>
        <span class="ai-receipt__msg">
          {{ item.success ? '成功' : `${item.errorMsg || '失败'}（${item.errorCode ?? '—'}）` }}
        </span>
        <el-button
          v-if="!item.success"
          link
          type="primary"
          :loading="confirming"
          @click="emit('retry', item)"
        >重试</el-button>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
// 回执：成功项 success、失败项 danger-light 底（视觉 4）
.ai-receipt {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-200);
}

.ai-receipt__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
  font-size: var(--font-size-sm);
}

.ai-receipt__head--ok {
  color: var(--color-success);
}

.ai-receipt__head--fail {
  color: var(--color-danger);
}

.ai-receipt__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-receipt__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm, 4px);
  font-size: var(--font-size-sm);
}

.ai-receipt__item--ok {
  background: var(--color-success-light, var(--color-neutral-50));
  color: var(--color-success);
}

.ai-receipt__item--fail {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.ai-receipt__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-receipt__msg {
  flex-shrink: 0;
}
</style>
