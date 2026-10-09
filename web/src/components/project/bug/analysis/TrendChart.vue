<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import type { EChartsCoreOption, EChartsType } from 'echarts/core'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { BugTrendSeries, BugTrendsResp } from '@/types'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  trends: BugTrendsResp | null
  loading: boolean
}>()

/** 下钻载荷：仅携带缺陷列表既有筛选支持的参数（Q1 降级口径） */
const emit = defineEmits<{
  drill: [filters: { severity?: string; bugType?: string }]
}>()

const chartEl = ref<HTMLDivElement>()
let chart: EChartsType | null = null

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

const SERIES_COLOR: Record<keyof Pick<BugTrendSeries, 'created' | 'closed' | 'active'>, string> = {
  created: '--color-primary-500',
  closed: '--color-neutral-500',
  active: '--color-warning',
}

function buildOption(): EChartsCoreOption {
  const trends = props.trends
  if (!trends) return {}
  // 全部分组叠加为合计（分组对比时各组分别成线）
  const seriesDefs: Array<{ name: string; field: 'created' | 'closed' | 'active'; data: number[] }> = []
  for (const series of trends.series) {
    for (const field of ['created', 'closed', 'active'] as const) {
      seriesDefs.push({
        name: `${series.label}·${field === 'created' ? '新增' : field === 'closed' ? '关闭' : '存量'}`,
        field,
        data: series[field],
      })
    }
  }
  return {
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { fontSize: 11, color: cssVar('--color-neutral-500', '#909399') } },
    xAxis: {
      type: 'category',
      data: trends.axis,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: cssVar('--color-neutral-200', '#e4e7ed') } },
      axisLabel: {
        color: cssVar('--color-neutral-500', '#909399'),
        fontSize: 11,
        formatter: (value: string) => value.slice(5),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: cssVar('--color-neutral-500', '#909399'), fontSize: 11 },
      splitLine: { lineStyle: { color: cssVar('--color-neutral-100', '#f5f7fa') } },
    },
    series: seriesDefs.map((def) => ({
      name: def.name,
      type: 'line',
      data: def.data,
      showSymbol: trends.axis.length <= 31,
      lineStyle: { color: cssVar(SERIES_COLOR[def.field], '#409eff'), width: 2 },
      itemStyle: { color: cssVar(SERIES_COLOR[def.field], '#409eff') },
    })),
  }
}

function renderChart(): void {
  const el = chartEl.value
  if (!el || !props.trends) return
  if (!chart) {
    chart = init(el)
    chart.on('click', handleClick)
  }
  chart.setOption(buildOption(), { notMerge: true })
}

/** 数据点点击下钻：分组维度为 severity / type 时带列表既有筛选，其余维度列表不支持 → 提示（Q1） */
function handleClick(params: { seriesIndex?: number; dataIndex?: number }): void {
  const trends = props.trends
  const seriesIndex = params.seriesIndex
  const dataIndex = params.dataIndex
  if (!trends || seriesIndex === undefined || dataIndex === undefined) return
  const series = trends.series[Math.floor(seriesIndex / 3)]
  if (!series) return
  if (trends.groupBy === 'severity' && series.key !== 'all') {
    emit('drill', { severity: series.key })
  } else if (trends.groupBy === 'type' && series.key !== 'all') {
    emit('drill', { bugType: series.key })
  } else {
    ElMessage.info('缺陷列表暂不支持按该维度筛选，请在列表中手动过滤')
  }
}

watch(
  () => props.trends,
  async () => {
    await nextTick()
    renderChart()
  },
  { flush: 'post' },
)

function onResize(): void {
  chart?.resize()
}
window.addEventListener('resize', onResize)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="trend-chart">
    <template #header><span class="trend-chart__title">缺陷趋势</span></template>
    <div ref="chartEl" class="trend-chart__canvas" />
    <el-empty v-if="!loading && !trends" description="暂无趋势数据" :image-size="80" />
  </el-card>
</template>

<style scoped lang="scss">
.trend-chart__title {
  font-weight: 600;
}

.trend-chart__canvas {
  width: 100%;
  height: 320px;
}
</style>
