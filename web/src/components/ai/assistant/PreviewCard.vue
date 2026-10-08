<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  assistantActionLabel,
  formatIntentChangeValue,
  intentFieldLabel,
  splitReplaceChange,
} from '@/utils/assistant'
import { parseDateTime } from '@/utils/format'
import type { AiAssistantIntent, AiAssistantIntentChange } from '@/types'

/** 变更预览卡（交互 05 §2.3）：字段级 diff、expiresAt 倒计时与确认 / 取消 / 返回修改 */
const props = defineProps<{
  intent: AiAssistantIntent
  /** 回执生成前禁用全部操作（交互 05 §2.3，对应 store.executing） */
  executing: boolean
  /** 归档会话只读，不产生任何执行类操作（详设 3.4） */
  readonly: boolean
  /** 解析摘要非空时才允许「返回修改」回填（交互 05 §2.3） */
  editable: boolean
}>()

const emit = defineEmits<{
  execute: []
  cancel: []
  edit: []
  reparse: []
}>()

const TICK_MS = 1000

const now = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

interface ChangeView {
  change: AiAssistantIntentChange
  pair: { before: string; after: string } | null
}

const changeViews = computed<ChangeView[]>(() =>
  props.intent.changes.map((change) => ({
    change,
    pair: change.op === 'replace' ? splitReplaceChange(change.value) : null,
  })),
)

// 解析失败按已过期处理：时效以服务端校验为准（1000018253），前端宁可置灰也不放行
const expiresAtMs = computed(() => parseDateTime(props.intent.expiresAt)?.getTime() ?? 0)

const remainingMs = computed(() => expiresAtMs.value - now.value)
const expired = computed(() => remainingMs.value <= 0)
const countdown = computed(() => {
  const totalSeconds = Math.max(0, Math.ceil(remainingMs.value / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

const locked = computed(() => props.executing || props.readonly)

onMounted(() => {
  tickTimer = setInterval(() => {
    now.value = Date.now()
  }, TICK_MS)
})

onBeforeUnmount(() => {
  if (tickTimer) clearInterval(tickTimer)
  tickTimer = null
})
</script>

<template>
  <div class="ai-preview" role="note" aria-label="变更预览">
    <p class="ai-preview__head">
      <span class="ai-preview__kind">📋 {{ assistantActionLabel(intent.kind) }}</span>
      <span v-if="intent.createCount > 0" class="ai-preview__count">
        将创建 {{ intent.createCount }} 条
      </span>
    </p>
    <p class="ai-preview__target">{{ intent.targetTitle }}</p>
    <p v-if="intent.scope.projectName" class="ai-preview__scope">
      项目：{{ intent.scope.projectName }}
    </p>
    <ul v-if="changeViews.length > 0" class="ai-preview__changes">
      <li v-for="(view, index) in changeViews" :key="index" class="ai-preview__change">
        <span class="ai-preview__field">{{ intentFieldLabel(view.change.field) }}</span>
        <template v-if="view.pair">
          <code class="ai-preview__seg ai-preview__seg--del">
            <span aria-hidden="true">−</span>{{ view.pair.before }}
          </code>
          <code class="ai-preview__seg ai-preview__seg--add">
            <span aria-hidden="true">＋</span>{{ view.pair.after }}
          </code>
        </template>
        <code
          v-else
          class="ai-preview__seg"
          :class="view.change.op === 'add' ? 'ai-preview__seg--add' : 'ai-preview__seg--del'"
        >
          <span aria-hidden="true">{{ view.change.op === 'add' ? '＋' : '±' }}</span
          >{{ formatIntentChangeValue(view.change.value) }}
        </code>
      </li>
    </ul>
    <p class="ai-preview__expiry" :class="{ 'ai-preview__expiry--expired': expired }">
      {{ expired ? '预览已过期，确认执行已停用' : `有效期剩余 ${countdown}` }}
    </p>
    <div class="ai-preview__actions">
      <el-button
        type="primary"
        :loading="executing"
        :disabled="expired || executing || readonly"
        @click="emit('execute')"
      >
        确认执行
      </el-button>
      <el-button :disabled="locked" @click="emit('cancel')">取消</el-button>
      <el-button :disabled="locked || !editable" @click="emit('edit')">返回修改</el-button>
      <el-button
        v-if="expired"
        :disabled="locked"
        @click="emit('reparse')"
      >
        重新解析
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-preview {
  padding: var(--space-sm);
  border: 1px solid var(--color-primary-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-xs);
    margin: 0;
    font-size: var(--font-size-xs);
  }

  &__kind {
    font-weight: 600;
    color: var(--color-neutral-700);
  }

  &__count {
    color: var(--color-primary-500);
  }

  &__target {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--color-neutral-900);
  }

  &__scope {
    margin: 0;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }

  &__changes {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
  }

  &__change {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: var(--space-xs);
    font-size: 13px;
  }

  &__field {
    flex-shrink: 0;
    color: var(--color-neutral-500);
  }

  // diff 底色 + +/- 图标双通道表意，不单靠颜色（交互 05 §4）
  &__seg {
    padding: 1px 6px;
    border-radius: var(--radius-sm);
    font-family: inherit;
    font-size: 13px;
    word-break: break-all;

    &--add {
      background: var(--color-success-light);
      color: var(--color-success);
    }

    &--del {
      background: var(--color-danger-light);
      color: var(--color-danger);
    }
  }

  &__expiry {
    margin: 0;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-500);

    &--expired {
      color: var(--color-neutral-400);
    }
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
  }
}
</style>
