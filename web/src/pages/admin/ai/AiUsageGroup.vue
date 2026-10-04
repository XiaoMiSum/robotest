<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import type { EChartsCoreOption, EChartsType } from 'echarts/core'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { aiTaskTypeMeta } from '@/composables/project/ai/taskPresentation'
import { fetchAiUsageTasks } from '@/services/aiAdmin'
import { usageCacheKey, useAiAdminStore } from '@/stores/aiAdmin'
import type {
  AiUsageGroupBy,
  AiUsageQuery,
  AiUsageSeriesItem,
  AiUsageTask,
  AiUsageTaskQuery,
} from '@/types'

use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const store = useAiAdminStore()

/** 指标页签（交互 2.5：切换保留时间范围） */
type AiUsageMetric = 'calls' | 'tokens' | 'latency' | 'cost'

const METRICS: Array<{
  key: AiUsageMetric
  label: string
  field: 'calls' | 'tokens' | 'avgLatencyMs' | 'cost'
  format: (value: number) => string
}> = [
  { key: 'calls', label: '调用次数', field: 'calls', format: (v) => `${v} 次` },
  {
    key: 'tokens',
    label: 'Tokens',
    field: 'tokens',
    format: (v) => `${v.toLocaleString()} tokens`,
  },
  { key: 'latency', label: '耗时', field: 'avgLatencyMs', format: (v) => `${v} ms` },
  { key: 'cost', label: '成本', field: 'cost', format: (v) => `¥${v.toFixed(4)}` },
]

const GROUP_OPTIONS: Array<{ value: AiUsageGroupBy; label: string }> = [
  { value: 'day', label: '按天' },
  { value: 'model', label: '按模型' },
  { value: 'scene', label: '按场景' },
  { value: 'callType', label: '按调用类型' },
]

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

// ---------- 范围与查询 ----------

const preset = ref<'7' | '30' | 'custom'>('30')
const customRange = ref<string[] | null>(null)
const groupBy = ref<AiUsageGroupBy>('day')
const metric = ref<AiUsageMetric>('calls')

// UTC 日期与后端按天聚合口径一致（后端对 from/to 按 UTC LocalDate 解释）
function isoUtcDate(timestamp: number): string {
  return new Date(timestamp).toISOString().slice(0, 10)
}

const query = computed<AiUsageQuery | null>(() => {
  if (preset.value !== 'custom') {
    const days = preset.value === '7' ? 7 : 30
    const now = Date.now()
    return { from: isoUtcDate(now - (days - 1) * 86400000), to: isoUtcDate(now), groupBy: groupBy.value }
  }
  const [from, to] = customRange.value ?? []
  if (!from || !to) return null
  return { from, to, groupBy: groupBy.value }
})

const statistics = computed(() => {
  const q = query.value
  return q ? store.usage[usageCacheKey(q)] : undefined
})

watch(
  query,
  (q) => {
    drill.value = null // 范围/维度变化后下钻区间随之失效
    if (q) void store.loadUsage(q)
  },
  { immediate: true },
)

function resetRange() {
  preset.value = '30'
}

function retryUsage() {
  const q = query.value
  if (q) void store.loadUsage(q)
}

// ---------- 汇总条 ----------

const summaryItems = computed(() => {
  const summary = statistics.value?.summary
  if (!summary) return []
  return [
    { label: '总调用', value: summary.totalCalls.toLocaleString() },
    { label: '成功率', value: `${(summary.successRate * 100).toFixed(1)}%` },
    { label: 'Tokens', value: summary.totalTokens.toLocaleString() },
    { label: '平均耗时', value: `${summary.avgLatencyMs} ms` },
    { label: '总成本', value: `¥${summary.totalCost.toFixed(2)}` },
  ]
})

// ---------- 图表 ----------

const chartEl = ref<HTMLElement | null>(null)
let chart: EChartsType | null = null

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function callTypeLabel(key: string): string {
  if (key === 'chat') return '对话调用'
  if (key === 'embedding') return '嵌入调用'
  return key
}

function categoryLabel(item: AiUsageSeriesItem): string {
  if (groupBy.value === 'day') return item.key
  if (groupBy.value === 'callType') return callTypeLabel(item.key)
  if (groupBy.value === 'scene' && !item.key) return '未归因场景'
  return item.keyName ?? item.key
}

function buildOption(): EChartsCoreOption {
  const series = statistics.value?.series ?? []
  const metricDef = METRICS.find((m) => m.key === metric.value) ?? METRICS[0]
  const primary = cssVar('--color-primary-500', '#409eff')
  const neutral100 = cssVar('--color-neutral-100', '#f5f7fa')
  const neutral200 = cssVar('--color-neutral-200', '#e4e7ed')
  const neutral500 = cssVar('--color-neutral-500', '#909399')
  const isDay = groupBy.value === 'day'

  const categories = series.map(categoryLabel)
  const data = series.map((item) => item[metricDef.field])

  return {
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => metricDef.format(Number(value)),
    },
    xAxis: {
      type: 'category',
      data: categories,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: neutral200 } },
      axisLabel: {
        color: neutral500,
        fontSize: 11,
        interval: 0,
        formatter: (value: string) => {
          const label = isDay ? value.slice(5) : value
          return label.length > 10 ? `${label.slice(0, 10)}…` : label
        },
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: neutral500, fontSize: 11 },
      splitLine: { lineStyle: { color: neutral100 } },
    },
    series: isDay
      ? [
          {
            type: 'line',
            data,
            showSymbol: series.length <= 31,
            lineStyle: { color: primary, width: 2 },
            itemStyle: { color: primary },
          },
        ]
      : [
          {
            type: 'bar',
            data,
            barMaxWidth: 40,
            itemStyle: { color: primary, borderRadius: [4, 4, 0, 0] },
          },
        ],
  }
}

function renderChart() {
  const el = chartEl.value
  if (!el || !statistics.value) return
  if (!chart) {
    chart = init(el)
    chart.on('click', handlePointClick)
  }
  chart.setOption(buildOption(), { notMerge: true })
}

watch(
  [statistics, metric, groupBy],
  async () => {
    await nextTick()
    renderChart()
  },
  { flush: 'post' },
)

function onResize() {
  chart?.resize()
}
window.addEventListener('resize', onResize)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})

// ---------- 下钻（详设 3.7） ----------

type DrillFilters = Omit<AiUsageTaskQuery, 'pageNo' | 'pageSize'>
const DRILL_PAGE_SIZE = 20

const drill = ref<{ title: string; filters: DrillFilters } | null>(null)
const drillRows = ref<AiUsageTask[]>([])
const drillTotal = ref(0)
const drillPageNo = ref(1)
const drillLoading = ref(false)
const drillError = ref('')

async function loadDrill() {
  if (!drill.value) return
  drillLoading.value = true
  drillError.value = ''
  try {
    const page = await fetchAiUsageTasks({
      pageNo: drillPageNo.value,
      pageSize: DRILL_PAGE_SIZE,
      ...drill.value.filters,
    })
    drillRows.value = page.list
    drillTotal.value = page.total
  } catch (error) {
    drillError.value = errorMessage(error, '加载下钻明细失败')
  } finally {
    drillLoading.value = false
  }
}

function openDrill(item: AiUsageSeriesItem) {
  const q = query.value
  if (!q) return
  const filters: DrillFilters = { from: q.from, to: q.to }
  let title: string
  if (groupBy.value === 'day') {
    filters.from = item.key
    filters.to = item.key
    title = item.key
  } else if (groupBy.value === 'model') {
    filters.modelId = item.key
    title = item.keyName ?? item.key
  } else if (groupBy.value === 'scene') {
    if (!item.key) return // 无场景归因行无法按场景筛选，不开放下钻
    filters.scene = item.key
    title = item.keyName ?? item.key
  } else {
    filters.callType = item.key
    title = callTypeLabel(item.key)
  }
  drill.value = { title, filters }
  drillPageNo.value = 1
  void loadDrill()
}

function handlePointClick(params: unknown) {
  const point = params as { dataIndex?: number }
  const series = statistics.value?.series
  if (point.dataIndex === undefined || !series) return
  const item = series[point.dataIndex]
  if (item) openDrill(item)
}

function handleDrillPageChange(page: number) {
  drillPageNo.value = page
  void loadDrill()
}

function usageStatusMeta(status: string): { label: string; tagType: 'success' | 'danger' | 'info' } {
  if (status === 'success') return { label: '成功', tagType: 'success' }
  if (status === 'failed') return { label: '失败', tagType: 'danger' }
  return { label: status, tagType: 'info' }
}

function typeLabel(row: AiUsageTask): string {
  return row.type ? aiTaskTypeMeta(row.type).label : '—'
}

function openTask(row: AiUsageTask) {
  if (!row.taskId) return // 连通性测试等无任务调用不可跳转（详设 3.7）
  void router.push({ name: 'AiTaskDetail', params: { taskId: row.taskId } })
}
</script>

<template>
  <div class="usage-group">
    <!-- 范围选择（交互 2.5）：预设 + 自定义起止 -->
    <div class="usage-group__toolbar">
      <div class="usage-group__toolbar-left">
        <el-radio-group v-model="preset" size="small">
          <el-radio-button value="7">近 7 天</el-radio-button>
          <el-radio-button value="30">近 30 天</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="customRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="usage-group__range"
          @change="preset = 'custom'"
        />
      </div>
      <el-radio-group v-model="groupBy" size="small">
        <el-radio-button
          v-for="option in GROUP_OPTIONS"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 汇总条：区间总量一眼可见 -->
    <div v-if="statistics" class="usage-group__summary">
      <div v-for="item in summaryItems" :key="item.label" class="usage-group__summary-item">
        <span class="usage-group__summary-label">{{ item.label }}</span>
        <span class="usage-group__summary-value">{{ item.value }}</span>
      </div>
    </div>

    <!-- 指标页签（切换保留时间范围，复用缓存） -->
    <el-tabs v-model="metric" class="usage-group__metric-tabs">
      <el-tab-pane v-for="item in METRICS" :key="item.key" :name="item.key" :label="item.label" />
    </el-tabs>

    <div v-loading="store.usageLoading" class="usage-group__chart-card">
      <!-- 列表错误（UI-PAGE-11：页面捕获 + 重试） -->
      <div v-if="store.usageError" class="usage-group__error">
        <span>{{ store.usageError }}</span>
        <el-button size="small" @click="retryUsage">重试</el-button>
      </div>

      <!-- 自定义范围未选全：提示选择起止日期 -->
      <div v-else-if="query === null" class="usage-group__hint">请选择起止日期</div>

      <!-- 空区间：空态 + 范围调整引导（交互 2.6） -->
      <el-empty
        v-else-if="statistics && statistics.series.length === 0"
        description="所选范围暂无用量数据，可调整时间范围后重试"
      >
        <el-button type="primary" @click="resetRange">重置为近 30 天</el-button>
      </el-empty>

      <div v-show="statistics" ref="chartEl" class="usage-group__chart" />
    </div>

    <!-- 下钻任务明细（趋势图数据点点击进入） -->
    <div v-if="drill" class="usage-group__drill">
      <div class="usage-group__drill-head">
        <span class="usage-group__drill-title">下钻明细 · {{ drill.title }}</span>
        <el-button link type="primary" size="small" @click="drill = null">收起</el-button>
      </div>

      <div v-if="drillError" class="usage-group__error">
        <span>{{ drillError }}</span>
        <el-button size="small" @click="loadDrill">重试</el-button>
      </div>

      <el-table
        v-else
        v-loading="drillLoading"
        :data="drillRows"
        class="usage-group__drill-table"
        @row-click="openTask"
      >
        <el-table-column label="类型" min-width="140">
          <template #default="{ row }">
            <span :class="{ 'usage-group__task-link': row.taskId }">{{ typeLabel(row as AiUsageTask) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="usageStatusMeta(row.status).tagType">
              {{ usageStatusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" prop="latencyMs" width="110">
          <template #default="{ row }">{{ row.latencyMs }} ms</template>
        </el-table-column>
        <el-table-column label="Tokens" prop="totalTokens" width="110" align="right" />
      </el-table>

      <el-pagination
        v-if="drillTotal > DRILL_PAGE_SIZE"
        v-model:current-page="drillPageNo"
        :page-size="DRILL_PAGE_SIZE"
        :total="drillTotal"
        layout="total, prev, pager, next"
        class="usage-group__drill-pager"
        @current-change="handleDrillPageChange"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.usage-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 0;
}

.usage-group__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.usage-group__toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.usage-group__range {
  width: 248px;
}

.usage-group__summary {
  display: flex;
  gap: var(--space-xl);
  flex-wrap: wrap;
  padding: var(--space-md);
  background: var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.usage-group__summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 96px;
}

.usage-group__summary-label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.usage-group__summary-value {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.usage-group__metric-tabs {
  flex-shrink: 0;
}

.usage-group__chart-card {
  min-height: 300px;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.usage-group__chart {
  height: 260px;
}

.usage-group__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-md);
}

.usage-group__hint {
  padding: var(--space-xl) 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.usage-group__drill {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.usage-group__drill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.usage-group__drill-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.usage-group__task-link {
  color: var(--color-primary-500);
  cursor: pointer;
}

.usage-group__drill-pager {
  align-self: flex-end;
}
</style>
