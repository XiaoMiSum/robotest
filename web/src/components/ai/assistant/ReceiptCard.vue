<script setup lang="ts">
import { computed } from 'vue'
import { assistantActionLabel } from '@/utils/assistant'
import { formatDateTime } from '@/utils/format'
import type { AiAssistantExecution, AiAssistantIntent } from '@/types'

/** 执行回执卡（交互 05 §2.3）：逐项成败、对象链接跳转与失败项单项重试 */
const props = defineProps<{
  execution: AiAssistantExecution
  /** 预览意图（可为空，如历史消息缺失）：仅用于展示动作标签 */
  intent: AiAssistantIntent | null
  /** 重试执行中禁用重试按钮，避免并发重复提交 */
  executing: boolean
  /** 归档会话只读，不允许重试（详设 3.4） */
  readonly: boolean
}>()

const emit = defineEmits<{
  retry: [index: number]
  jump: [path: string]
}>()

const results = computed(() => props.execution.results ?? [])
</script>

<template>
  <div class="ai-receipt" role="note" aria-label="执行回执">
    <p class="ai-receipt__head">
      <span class="ai-receipt__title">执行回执</span>
      <span v-if="intent" class="ai-receipt__kind">{{ assistantActionLabel(intent.kind) }}</span>
      <span
        class="ai-receipt__status"
        :class="`ai-receipt__status--${execution.status}`"
      >
        {{ execution.status === 'executed' ? '已执行' : '已取消' }}
      </span>
    </p>

    <p v-if="execution.status === 'rejected'" class="ai-receipt__rejected">
      已取消本次变更，未产生任何数据
      <span v-if="execution.rejectedAt" class="ai-receipt__time">
        {{ formatDateTime(execution.rejectedAt) }}
      </span>
    </p>

    <template v-else>
      <ul v-if="results.length > 0" class="ai-receipt__results">
        <li
          v-for="(item, index) in results"
          :key="index"
          class="ai-receipt__item"
          :class="item.success ? 'ai-receipt__item--ok' : 'ai-receipt__item--fail'"
        >
          <span
            class="ai-receipt__mark"
            :aria-label="item.success ? '成功' : '失败'"
          >{{ item.success ? '✓' : '✗' }}</span>
          <span class="ai-receipt__action">{{ assistantActionLabel(item.action) }}</span>
          <span v-if="!item.success" class="ai-receipt__error">
            {{ item.errorMsg ?? '执行失败'
            }}<template v-if="item.errorCode">（{{ item.errorCode }}）</template>
          </span>
          <span class="ai-receipt__item-actions">
            <el-button
              v-if="!item.success"
              size="small"
              :disabled="executing || readonly"
              @click="emit('retry', index)"
            >
              重试
            </el-button>
            <el-button
              v-if="item.link"
              size="small"
              link
              type="primary"
              @click="emit('jump', item.link)"
            >
              查看
            </el-button>
          </span>
        </li>
      </ul>
      <p v-else class="ai-receipt__empty">执行完成，无回执项</p>
      <p v-if="execution.executedAt" class="ai-receipt__time">
        执行时间：{{ formatDateTime(execution.executedAt) }}
      </p>
      <el-button
        v-if="execution.link"
        link
        type="primary"
        @click="emit('jump', execution.link)"
      >
        查看结果
      </el-button>
    </template>
  </div>
</template>

<style scoped lang="scss">
.ai-receipt {
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);

  &__head {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    margin: 0;
    font-size: var(--font-size-xs);
  }

  &__title {
    font-weight: 600;
    color: var(--color-neutral-700);
  }

  &__kind {
    color: var(--color-neutral-500);
  }

  &__status {
    margin-left: auto;
    padding: 0 6px;
    border-radius: 999px;

    &--executed {
      background: var(--color-success-light);
      color: var(--color-success);
    }

    &--rejected {
      background: var(--color-neutral-100);
      color: var(--color-neutral-400);
    }
  }

  &__rejected {
    margin: 0;
    font-size: 13px;
    color: var(--color-neutral-500);
  }

  &__time {
    margin: 0;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }

  &__results {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
  }

  // 成功 / 失败双通道表意：图标 + 文案，底色只作辅助（交互 05 §4）
  &__item {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-xs);
    padding: var(--space-xs);
    border-radius: var(--radius-sm);
    font-size: 13px;

    &--ok {
      background: var(--color-success-light);
    }

    &--fail {
      background: var(--color-danger-light);
    }
  }

  &__mark {
    font-weight: 700;
  }

  &__action {
    font-weight: 600;
    color: var(--color-neutral-700);
  }

  &__error {
    color: var(--color-danger);
    word-break: break-all;
  }

  &__item-actions {
    margin-left: auto;
    display: flex;
    gap: var(--space-xs);
  }

  &__empty {
    margin: 0;
    font-size: 13px;
    color: var(--color-neutral-500);
  }
}
</style>
