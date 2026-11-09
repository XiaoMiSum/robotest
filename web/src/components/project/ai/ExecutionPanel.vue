<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskExecutionItem, AiTaskStatus } from '@/types'
import {
  aiExecutionStatusMeta,
  aiTaskStatusMeta,
} from '@/composables/project/ai/taskPresentation'

const props = defineProps<{
  /** 最新一次执行（无执行记录时为 null） */
  execution: AiTaskExecutionItem | null
  /** 任务状态（终态徽标与终止原因展示） */
  taskStatus: AiTaskStatus
  /** 终止原因文案（含错误码，任务级或执行级） */
  terminateText: string
}>()

const taskMeta = computed(() => aiTaskStatusMeta(props.taskStatus, props.execution?.status))
/** 执行态徽标：执行中与任务徽标重复，仅挂起 / 成功 / 终止时补充展示 */
const executionBadge = computed(() => {
  const execution = props.execution
  if (!execution || execution.status === 'running') return null
  const meta = aiExecutionStatusMeta(execution.status)
  return { label: meta.label, tagType: meta.tagType }
})

const seqText = computed(() => {
  const seq = props.execution?.seq
  return seq && seq > 1 ? `第 ${seq} 次执行` : ''
})

const stepsText = computed(() => {
  const execution = props.execution
  if (!execution) return '—'
  const steps = execution.steps ?? 0
  const maxSteps = execution.budget?.maxSteps
  return maxSteps && maxSteps > 0 ? `${steps}/${maxSteps} 步` : `${steps} 步`
})

/** 时长（秒）：不足 1 秒按 0 展示，不做亚秒精度猜测 */
const durationText = computed(() => {
  const durationMs = props.execution?.consumed?.durationMs
  if (durationMs === null || durationMs === undefined) return '—'
  return `${Math.round(durationMs / 1000)}s`
})

const maxDurationText = computed(() => {
  const maxSeconds = props.execution?.budget?.maxDurationSeconds
  return maxSeconds && maxSeconds > 0 ? `${maxSeconds}s` : '—'
})

const tokensText = computed(() => {
  const consumed = props.execution?.consumed
  if (!consumed) return '—'
  const used = (consumed.tokensIn ?? 0) + (consumed.tokensOut ?? 0)
  const maxTokens = props.execution?.budget?.maxTokens
  return maxTokens && maxTokens > 0 ? `${used}/${maxTokens}` : `${used}`
})

const costText = computed(() => {
  const consumed = props.execution?.consumed
  if (!consumed || consumed.cost === null) return '—'
  const maxCost = props.execution?.budget?.maxCost
  return maxCost !== null && maxCost !== undefined ? `${consumed.cost}/${maxCost}` : `${consumed.cost}`
})
</script>

<template>
  <section class="execution-panel">
    <div class="execution-panel__head">
      <el-tag :type="taskMeta.tagType" size="small" effect="light">{{ taskMeta.label }}</el-tag>
      <el-tag
        v-if="executionBadge"
        :type="executionBadge.tagType"
        size="small"
        effect="plain"
      >{{ executionBadge.label }}</el-tag>
      <span v-if="seqText" class="execution-panel__seq">{{ seqText }}</span>
      <span class="execution-panel__metrics">
        步数 {{ stepsText }} · 时长 {{ durationText }}/{{ maxDurationText }} ·
        tokens {{ tokensText }} · 成本 {{ costText }}
      </span>
    </div>
    <p v-if="terminateText" class="execution-panel__terminate">终止原因：{{ terminateText }}</p>
  </section>
</template>

<style scoped lang="scss">
.execution-panel {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.execution-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}

.execution-panel__seq {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.execution-panel__metrics {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.execution-panel__terminate {
  margin: var(--space-sm) 0 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
</style>
