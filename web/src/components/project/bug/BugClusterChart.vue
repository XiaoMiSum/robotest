<script setup lang="ts">
import type { ModuleBar, SeveritySegment } from './bugClusterChart'

defineProps<{ moduleBars: ModuleBar[]; severitySegments: SeveritySegment[] }>()

const SEVERITY_LABEL: Record<string, string> = {
  fatal: '致命',
  serious: '严重',
  general: '一般',
  minor: '轻微',
}
</script>

<template>
  <div class="bug-cluster__chart">
    <div class="bug-cluster__chart-title">按模块</div>
    <div v-for="bar in moduleBars" :key="bar.moduleName" class="bug-cluster__bar-row">
      <span class="bug-cluster__bar-name">{{ bar.moduleName }}</span>
      <div class="bug-cluster__bar-track">
        <div class="bug-cluster__bar-fill" :style="{ width: `${bar.widthPercent}%` }" />
      </div>
      <span class="bug-cluster__bar-count">{{ bar.count }}</span>
      <span class="bug-cluster__bar-percent">{{ bar.widthPercent }}%</span>
    </div>

    <div class="bug-cluster__chart-title">按等级</div>
    <div class="bug-cluster__seg-track">
      <div
        v-for="seg in severitySegments"
        :key="seg.severity"
        class="bug-cluster__seg-fill"
        :class="`bug-cluster__seg-fill--${seg.severity}`"
        :style="{ width: `${seg.widthPercent}%` }"
      />
    </div>
    <div class="bug-cluster__seg-legend">
      <span
        v-for="seg in severitySegments"
        :key="seg.severity"
        class="bug-cluster__seg-legend-item"
      >
        <span class="bug-cluster__seg-dot" :class="`bug-cluster__seg-dot--${seg.severity}`" />
        {{ SEVERITY_LABEL[seg.severity] ?? seg.severity }}
        {{ seg.count }}
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.bug-cluster__chart {
  border-top: 1px solid var(--color-neutral-100);
  padding-top: var(--space-md);
}

.bug-cluster__chart-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
  margin-bottom: var(--space-sm);
}

.bug-cluster__bar-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: 8px;
}

.bug-cluster__bar-name {
  flex-shrink: 0;
  width: 96px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-cluster__bar-track {
  flex: 1;
  height: 10px;
  background: var(--color-neutral-100);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.bug-cluster__bar-fill {
  height: 100%;
  background: var(--color-primary-500);
  border-radius: var(--radius-full);
}

.bug-cluster__bar-count {
  flex-shrink: 0;
  width: 24px;
  text-align: right;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.bug-cluster__bar-percent {
  flex-shrink: 0;
  width: 36px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.bug-cluster__seg-track {
  display: flex;
  height: 14px;
  border-radius: var(--radius-full);
  overflow: hidden;
  margin-bottom: var(--space-sm);
}

.bug-cluster__seg-fill--fatal { background: var(--color-bug-fatal); }
.bug-cluster__seg-fill--serious { background: var(--color-bug-serious); }
.bug-cluster__seg-fill--general { background: var(--color-bug-general); }
.bug-cluster__seg-fill--minor { background: var(--color-bug-minor); }

.bug-cluster__seg-legend {
  display: flex;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.bug-cluster__seg-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.bug-cluster__seg-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.bug-cluster__seg-dot--fatal { background: var(--color-bug-fatal); }
.bug-cluster__seg-dot--serious { background: var(--color-bug-serious); }
.bug-cluster__seg-dot--general { background: var(--color-bug-general); }
.bug-cluster__seg-dot--minor { background: var(--color-bug-minor); }
</style>
