<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import type { EChartsCoreOption, EChartsType } from 'echarts/core'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { BugDistItem, BugMetricsResp } from '@/types'

use([PieChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  metrics: BugMetricsResp | null
  loading: boolean
}>()

/** 缺陷等级色沿用视觉设计 4.3（不单靠颜色表意，环图附图例标签） */
const SEVERITY_COLOR: Record<string, string> = {
  fatal: '--color-bug-fatal',
  serious: '--color-bug-serious',
  general: '--color-bug-general',
  minor: '--color-bug-minor',
}

const SEVERITY_LABEL: Record<string, string> = {
  fatal: '致命',
  serious: '严重',
  general: '一般',
  minor: '轻微',
}

const donutEl = ref<HTMLDivElement>()
let donut: EChartsType | null = null

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function formatHours(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${value} 小时`
}

function formatRate(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${Math.round(value * 1000) / 10}%`
}

const severityItems = computed<BugDistItem[]>(() => props.metrics?.severityDist ?? [])

function buildDonutOption(): EChartsCoreOption {
  return {
    tooltip: { trigger: 'item', valueFormatter: (value: unknown) => `${Number(value)} 个` },
    legend: { bottom: 0, textStyle: { fontSize: 11, color: cssVar('--color-neutral-500', '#909399') } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '42%'],
        label: { show: false },
        data: severityItems.value.map((item) => ({
          name: SEVERITY_LABEL[item.key] ?? item.key,
          value: item.count,
          itemStyle: { color: cssVar(SEVERITY_COLOR[item.key] ?? '', cssVar('--color-neutral-400', '#c0c4cc')) },
        })),
      },
    ],
  }
}

function renderDonut(): void {
  const el = donutEl.value
  if (!el) return
  if (!donut) donut = init(el)
  donut.setOption(buildDonutOption(), { notMerge: true })
}

watch(
  severityItems,
  async () => {
    await nextTick()
    renderDonut()
  },
  { flush: 'post' },
)

function onResize(): void {
  donut?.resize()
}
window.addEventListener('resize', onResize)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  donut?.dispose()
  donut = null
})
</script>

<template>
  <div v-loading="loading" class="metric-cards">
    <el-card shadow="never" class="metric-cards__card">
      <template #header><span class="metric-cards__title">修复时长</span></template>
      <el-tooltip content="修复时长：缺陷激活到首次解决的时长，样本为修复时间落在区间内的缺陷" placement="top">
        <div class="metric-cards__body">
          <div class="metric-cards__item">
            <span class="metric-cards__label">p50</span>
            <span class="metric-cards__value">{{ formatHours(metrics?.fixDuration?.p50Hours) }}</span>
          </div>
          <div class="metric-cards__item">
            <span class="metric-cards__label">p90</span>
            <span class="metric-cards__value">{{ formatHours(metrics?.fixDuration?.p90Hours) }}</span>
          </div>
          <div class="metric-cards__item">
            <span class="metric-cards__label">均值</span>
            <span class="metric-cards__value">{{ formatHours(metrics?.fixDuration?.avgHours) }}</span>
          </div>
          <div class="metric-cards__sample">样本 {{ metrics?.fixDuration?.sample ?? 0 }} 个</div>
        </div>
      </el-tooltip>
    </el-card>

    <el-card shadow="never" class="metric-cards__card">
      <template #header><span class="metric-cards__title">比率</span></template>
      <div class="metric-cards__body">
        <el-tooltip content="重开率 = 激活次数 ≥ 1 的缺陷 / 区间内新增缺陷" placement="top">
          <div class="metric-cards__item">
            <span class="metric-cards__label">重开率</span>
            <span class="metric-cards__value">{{ formatRate(metrics?.reopenRate) }}</span>
          </div>
        </el-tooltip>
        <el-tooltip content="重复缺陷占比 = 解决方案为「重复缺陷」的缺陷 / 区间内新增缺陷" placement="top">
          <div class="metric-cards__item">
            <span class="metric-cards__label">重复占比</span>
            <span class="metric-cards__value">{{ formatRate(metrics?.duplicateRate) }}</span>
          </div>
        </el-tooltip>
      </div>
    </el-card>

    <el-card shadow="never" class="metric-cards__card metric-cards__card--donut">
      <template #header><span class="metric-cards__title">等级分布</span></template>
      <div ref="donutEl" class="metric-cards__donut" />
      <el-empty v-if="!loading && severityItems.length === 0" description="暂无数据" :image-size="60" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.metric-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-md);
}

.metric-cards__title {
  font-weight: 600;
}

.metric-cards__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.metric-cards__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.metric-cards__label {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.metric-cards__value {
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.metric-cards__sample {
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.metric-cards__donut {
  width: 100%;
  height: 200px;
}
</style>
