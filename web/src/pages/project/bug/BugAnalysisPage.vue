<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useBugAnalysisStore } from '@/stores/bugAnalysis'
import AnalysisRangePicker from '@/components/project/bug/analysis/AnalysisRangePicker.vue'
import MetricCards from '@/components/project/bug/analysis/MetricCards.vue'
import TriageQueueCard from '@/components/project/bug/analysis/TriageQueueCard.vue'
import TrendChart from '@/components/project/bug/analysis/TrendChart.vue'

const router = useRouter()
const store = useBugAnalysisStore()

onMounted(() => {
  void store.reload()
})

onBeforeUnmount(() => {
  store.dispose()
})

/** 口径脚注：随范围与分组展示取数定义（详设 4.1） */
const footnote = computed(() => {
  const range = store.from && store.to ? `${store.from} ~ ${store.to}` : '最近 30 天（默认）'
  const groupLabel
    = store.groupBy === 'none'
      ? '不分组'
      : store.groupBy === 'module'
        ? '按模块'
        : store.groupBy === 'severity'
          ? '按严重等级'
          : '按缺陷类型'
  return `口径：新增=创建时间落区间，关闭=关闭时间落区间，存量=区间末激活数（UTC 日期）；范围 ${range}；${groupLabel}`
})

function onRangeChange(): void {
  void store.reload()
}

/** 趋势下钻：携带缺陷列表既有筛选支持的参数（Q1 降级口径） */
function drillToBugs(filters: { severity?: string; bugType?: string }): void {
  void router.push({ path: '/workspace/projects/bugs', query: { ...filters } })
}
</script>

<template>
  <main class="bug-analysis">
    <header class="bug-analysis__head">
      <div>
        <h1 class="bug-analysis__title">AI 缺陷分析</h1>
        <p class="bug-analysis__desc">趋势与质量度量、分诊建议，范围限于当前项目</p>
      </div>
    </header>

    <el-card shadow="never" class="bug-analysis__range">
      <AnalysisRangePicker
        :from="store.from"
        :to="store.to"
        :group-by="store.groupBy"
        :footnote="footnote"
        @update:from="store.from = $event"
        @update:to="store.to = $event"
        @update:group-by="store.groupBy = $event"
        @change="onRangeChange"
      />
    </el-card>

    <TrendChart
      class="bug-analysis__block"
      :trends="store.trends"
      :loading="store.trendsLoading"
      @drill="drillToBugs"
    />

    <el-alert
      v-if="store.trendsError || store.metricsError"
      type="error"
      :title="store.trendsError || store.metricsError"
      show-icon
      :closable="false"
      class="bug-analysis__block"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="void store.reload()">重试</el-button>
      </template>
    </el-alert>

    <MetricCards
      class="bug-analysis__block"
      :metrics="store.metrics"
      :loading="store.metricsLoading"
    />

    <TriageQueueCard
      class="bug-analysis__block"
      :items="store.triageItems"
      :loading="store.triageLoading"
      :error="store.triageError"
      @refresh="void store.loadTriage()"
    />
  </main>
</template>

<style scoped lang="scss">
.bug-analysis__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.bug-analysis__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
}

.bug-analysis__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.bug-analysis__block {
  margin-bottom: var(--block-gap);
}
</style>
