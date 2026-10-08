<script setup lang="ts">
/** 澄清反问卡（交互 05 §2.2）：点击快捷选项回填输入框并发送，亦可自行输入 */
defineProps<{
  question: string
  options: string[]
}>()

const emit = defineEmits<{ pick: [option: string] }>()
</script>

<template>
  <div class="ai-clarify" role="note" aria-label="澄清反问">
    <p class="ai-clarify__question">{{ question }}</p>
    <div v-if="options.length > 0" class="ai-clarify__options">
      <button
        v-for="option in options"
        :key="option"
        type="button"
        class="ai-clarify__option"
        @click="emit('pick', option)"
      >
        {{ option }}
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-clarify {
  padding: var(--space-sm);
  border: 1px dashed var(--color-primary-500);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);

  &__question {
    font-size: 14px;
    color: var(--color-neutral-900);
    white-space: pre-wrap;
  }

  &__options {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
    margin-top: var(--space-xs);
  }

  &__option {
    padding: 4px 10px;
    border: 1px solid var(--color-primary-500);
    border-radius: 999px;
    background: var(--color-neutral-0);
    color: var(--color-primary-500);
    font-size: 13px;
    cursor: pointer;
    transition: background-color var(--transition-fast);

    &:hover {
      background: var(--color-primary-50);
    }

    &:disabled {
      border-color: var(--color-neutral-300);
      color: var(--color-neutral-400);
      cursor: not-allowed;
    }
  }
}
</style>
