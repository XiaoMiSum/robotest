<script setup lang="ts">
import { computed } from 'vue'
import type { BugAnalysisGroupBy } from '@/types'

const props = defineProps<{
  from: string
  to: string
  groupBy: BugAnalysisGroupBy
  /** 口径脚注：随范围与分组展示取数定义（交互 2.1.2） */
  footnote: string
}>()

const emit = defineEmits<{
  'update:from': [value: string]
  'update:to': [value: string]
  'update:groupBy': [value: BugAnalysisGroupBy]
  change: []
}>()

const GROUP_OPTIONS: Array<{ value: BugAnalysisGroupBy; label: string }> = [
  { value: 'none', label: '不分组' },
  { value: 'module', label: '按模块' },
  { value: 'severity', label: '按严重等级' },
  { value: 'type', label: '按缺陷类型' },
]

/** el-date-picker 使用 Date 交互、字符串回传（value-format 已锁定 yyyy-MM-dd） */
const rangeValue = computed<[string, string] | null>({
  get() {
    return props.from && props.to ? [props.from, props.to] : null
  },
  set(value) {
    if (value && value[0] && value[1]) {
      emit('update:from', value[0])
      emit('update:to', value[1])
      emit('change')
    }
  },
})

function onGroupChange(value: BugAnalysisGroupBy): void {
  emit('update:groupBy', value)
  emit('change')
}
</script>

<template>
  <div class="analysis-range">
    <el-date-picker
      v-model="rangeValue"
      type="daterange"
      range-separator="至"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      value-format="YYYY-MM-DD"
      :clearable="false"
      class="analysis-range__picker"
    />
    <el-select
      :model-value="groupBy"
      class="analysis-range__group"
      @change="onGroupChange($event as BugAnalysisGroupBy)"
    >
      <el-option v-for="item in GROUP_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>
    <!-- 口径脚注：每张图表标注口径与时间范围（详设 4.1） -->
    <el-tooltip :content="footnote" placement="bottom">
      <span class="analysis-range__footnote" aria-label="统计口径">
        <el-icon><InfoFilled /></el-icon>
        <span class="analysis-range__footnote-text">{{ footnote }}</span>
      </span>
    </el-tooltip>
  </div>
</template>

<style scoped lang="scss">
.analysis-range {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.analysis-range__group {
  width: 140px;
}

.analysis-range__footnote {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  margin-left: auto;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  cursor: help;
}

.analysis-range__footnote-text {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
