<script setup lang="ts">
import type { ApiReportStepResult } from '@/types'
import { useReportProcessors } from '@/composables/useReportProcessors'
import ReportProcCard from './ReportProcCard.vue'

const props = defineProps<{
  level: 'task' | 'scene'
  pre: ApiReportStepResult[]
  post: ApiReportStepResult[]
}>()

const {
  activeTab,
  paneGroups,
  procKey,
  isCollapsed,
  toggleCollapse,
  chain,
  chainHighlightKey,
  statusLabel,
  badgeClass,
  methodChipClass,
  paneTone,
  pretty,
  displayValue,
  procUrl,
  headersEntries,
} = useReportProcessors(() => props.level, () => props.pre, () => props.post)
</script>

<template>
  <div v-if="pre.length || post.length" class="rr-procs">
    <div class="rr-procs__nav">
      <button
        v-for="group in paneGroups"
        :key="group.tab"
        class="rr-tab"
        :class="{ 'rr-tab--active': activeTab === group.tab }"
        @click="activeTab = group.tab"
      >
        {{ group.tab === 'pre' ? '前置' : '后置' }}
        <span class="rr-tab__cnt">{{ group.list.length }}</span>
        <span class="rr-dot" :class="`rr-dot--${paneTone(group.list)}`"></span>
      </button>
    </div>

    <div v-for="group in paneGroups" v-show="activeTab === group.tab" :key="group.tab" class="rr-procs__pane">
      <p class="rr-order-tip">
        <span>执行顺序</span>
        <template v-for="(item, i) in chain" :key="item.key">
          <span class="rr-order-tip__arrow">{{ i === 0 ? '›' : '→' }}</span>
          <b :class="{ 'rr-order-tip__current': item.key === chainHighlightKey }">{{ item.label }}</b>
        </template>
      </p>

      <ReportProcCard
        v-for="(step, index) in group.list"
        :key="procKey(step, group.tab, index)"
        :step="step"
        :tab="group.tab"
        :index="index"
        :proc-key="procKey"
        :is-collapsed="isCollapsed"
        :toggle-collapse="toggleCollapse"
        :status-label="statusLabel"
        :badge-class="badgeClass"
        :method-chip-class="methodChipClass"
        :display-value="displayValue"
        :pretty="pretty"
        :proc-url="procUrl"
        :headers-entries="headersEntries"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.rr-procs__nav {
  display: flex;
  gap: 2px;
  padding: 8px 14px 0;
  border-bottom: 1px solid #eef1f5;
  background: linear-gradient(180deg, #fbfdff, #fff);
  flex-wrap: wrap;
}

.rr-tab {
  appearance: none;
  border: 1px solid transparent;
  border-bottom: none;
  background: transparent;
  color: #64748b;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: 9px 9px 0 0;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  position: relative;
  top: 1px;
  transition:
    color 0.15s,
    background 0.15s;
  white-space: nowrap;

  &:hover {
    color: var(--color-success);
    background: #f0fdf4;
  }

  &--active {
    background: #fff;
    color: var(--color-success);
    border-color: #eef1f5;
    box-shadow: inset 0 2px 0 var(--color-success);
  }
}

.rr-tab__cnt {
  background: #e5e9f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  padding: 0 6px;
  border-radius: 999px;
  min-width: 18px;
  text-align: center;
  line-height: 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  .rr-tab--active & {
    background: #d1fae5;
    color: #15803d;
  }
}

.rr-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #cbd5e1;
  flex: 0 0 auto;

  &--ok {
    background: #22c55e;
    box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
  }

  &--fail {
    background: #ef4444;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
  }

  &--skip {
    background: #f59e0b;
  }

  &--empty {
    background: #e2e8f0;
  }
}

.rr-procs__pane {
  padding: 14px 14px 16px;
}

.rr-order-tip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  padding: 6px 11px;
  margin: 0 0 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  b {
    font-weight: 600;
    color: #64748b;
  }

  .rr-order-tip__current {
    color: var(--color-success);
    font-weight: 700;
  }
}

.rr-order-tip__arrow {
  color: #cbd5e1;
}

.rr-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }

  &--ok {
    background: #ecfdf3;
    color: #15803d;
  }

  &--fail {
    background: #fef2f2;
    color: #b91c1c;
  }

  &--skip {
    background: #f3f4f6;
    color: #4b5563;
  }
}

.rr-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  &--get {
    background: #e0f2fe;
    color: #0369a1;
  }

  &--post {
    background: #dcfce7;
    color: #15803d;
  }

  &--put {
    background: #fef3c7;
    color: #b45309;
  }

  &--delete {
    background: #fee2e2;
    color: #b91c1c;
  }
}

.rr-monotag {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
