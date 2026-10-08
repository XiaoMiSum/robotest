<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import type { AiAssistantSseError } from '@/types'

/** 流式指示（交互 05 §2.1）：打字动画 / 断线「恢复中…」/ 发送失败原因 */
defineProps<{
  streaming: boolean
  recovering: boolean
  error: AiAssistantSseError | null
}>()
</script>

<template>
  <div v-if="error || recovering || streaming" class="ai-stream-indicator" aria-live="polite">
    <p v-if="error" class="ai-stream-indicator__error">
      发送失败（{{ error.code }}）：{{ error.msg }}
    </p>
    <p v-else-if="recovering" class="ai-stream-indicator__recovering">
      <el-icon class="ai-stream-indicator__spin" :size="12"><Loading /></el-icon>
      恢复中…
    </p>
    <p v-else class="ai-stream-indicator__typing" aria-label="正在生成">
      <span /><span /><span />
    </p>
  </div>
</template>

<style scoped lang="scss">
.ai-stream-indicator {
  display: flex;
  align-items: center;
  min-height: 24px;
  padding: 0 var(--space-sm);

  &__error {
    color: var(--color-danger);
    font-size: var(--font-size-xs);
  }

  &__recovering {
    display: inline-flex;
    align-items: center;
    gap: var(--space-xs);
    color: var(--color-neutral-500);
    font-size: var(--font-size-xs);
  }

  &__spin {
    animation: ai-spin 1s linear infinite;
    color: var(--color-primary-500);
  }

  &__typing {
    display: inline-flex;
    align-items: center;
    gap: 4px;

    span {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--color-primary-500);
      animation: ai-blink 1.2s infinite both;

      &:nth-child(2) {
        animation-delay: 0.2s;
      }

      &:nth-child(3) {
        animation-delay: 0.4s;
      }
    }
  }
}

@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes ai-blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
  }

  40% {
    opacity: 1;
  }
}
</style>
