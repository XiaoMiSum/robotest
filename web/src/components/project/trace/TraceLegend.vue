<script setup lang="ts">
import { TRACE_EDGE_LEGEND } from '@/composables/project/trace/tracePresentation'
</script>

<template>
  <!-- 图例常驻：图标 + 文字，不单靠颜色表意（交互 04 §4） -->
  <div class="trace-legend" aria-label="边状态图例">
    <el-tooltip v-for="item in TRACE_EDGE_LEGEND" :key="item.label" :content="item.hint" placement="top">
      <span class="trace-legend__item" :class="`trace-legend__item--${item.status}`">
        <span class="trace-legend__symbol">{{ item.symbol }}</span>
        <span>{{ item.label }}</span>
      </span>
    </el-tooltip>
  </div>
</template>

<style scoped lang="scss">
.trace-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  color: var(--color-neutral-600);
}

.trace-legend__item {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.trace-legend__symbol {
  font-weight: 600;
}

.trace-legend__item--confirmed .trace-legend__symbol {
  color: var(--color-success);
}

.trace-legend__item--ai_created .trace-legend__symbol {
  color: var(--color-info);
}

.trace-legend__item--stale .trace-legend__symbol {
  color: var(--color-warning);
}

.trace-legend__item--conflict .trace-legend__symbol {
  color: var(--color-danger);
}

/* 已断开按视觉设计 §4 用中性色阶，区别于在途状态 */
.trace-legend__item--detached .trace-legend__symbol {
  color: var(--color-neutral-400);
}
</style>
