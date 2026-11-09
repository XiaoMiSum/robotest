<script setup lang="ts">
import type { AiPlanStep } from '@/composables/project/ai/taskPresentation'

defineProps<{
  /** 计划步骤视图模型（plan 快照驱动），空数组时父级不渲染本组件 */
  steps: AiPlanStep[]
}>()
</script>

<template>
  <!-- 计划步骤（交互 2.2）：完成打勾、当前高亮、终止标红、其后待执行 -->
  <ol class="plan-timeline">
    <li
      v-for="step in steps"
      :key="step.label"
      class="plan-timeline__item"
      :class="`plan-timeline__item--${step.state}`"
    >
      <span class="plan-timeline__dot" aria-hidden="true">
        <el-icon v-if="step.state === 'done'"><Check /></el-icon>
        <el-icon v-else-if="step.state === 'aborted'"><Close /></el-icon>
      </span>
      <span class="plan-timeline__label">{{ step.label }}</span>
    </li>
  </ol>
</template>

<style scoped lang="scss">
// 计划步骤：完成 success、终止 danger、当前 primary、挂起 info、待执行中性灰（视觉 4.1）
.plan-timeline {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  margin: 0;
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  list-style: none;
}

.plan-timeline__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.plan-timeline__dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 2px solid currentcolor;
  border-radius: 50%;
}

.plan-timeline__item--done {
  color: var(--color-success);

  .plan-timeline__dot {
    background: var(--color-success);
    border-color: var(--color-success);
    color: var(--color-neutral-0);
  }
}

.plan-timeline__item--current {
  color: var(--color-primary-500);

  .plan-timeline__dot {
    background: var(--color-primary-500);
    border-color: var(--color-primary-500);
  }
}

.plan-timeline__item--aborted {
  color: var(--color-danger);

  .plan-timeline__dot {
    background: var(--color-danger);
    border-color: var(--color-danger);
    color: var(--color-neutral-0);
  }
}
</style>
