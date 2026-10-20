<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBugAnalysisStore } from '@/stores/bugAnalysis'
import { fetchAiStatus } from '@/services/ai'
import AnalysisRangePicker from '@/components/project/bug/analysis/AnalysisRangePicker.vue'
import AiSummaryCard from '@/components/project/bug/analysis/AiSummaryCard.vue'
import BatchActions from '@/components/project/bug/analysis/BatchActions.vue'
import MetricCards from '@/components/project/bug/analysis/MetricCards.vue'
import TriageQueueCard from '@/components/project/bug/analysis/TriageQueueCard.vue'
import TrendChart from '@/components/project/bug/analysis/TrendChart.vue'

const router = useRouter()
const authStore = useAuthStore()
const store = useBugAnalysisStore()

/** 无缺陷查看权限不显示分析入口，直达路由 403（交互 2.4） */
const canView = computed(() => authStore.hasPermission('bug:view'))

const aiAvailable = ref(false)
onMounted(async () => {
  if (!canView.value) return
  try {
    aiAvailable.value = (await fetchAiStatus()).available
  } catch {
    // 状态查询失败按 AI 不可用降级，分析图表不受影响
    aiAvailable.value = false
  }
  await store.reload()
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
    <!-- 无权限：入口不展示，直达路由给 403 提示（交互 2.4） -->
    <el-result
      v-if="!canView"
      status="403"
      title="无权访问缺陷分析"
      sub-title="当前账号没有缺陷查看权限（bug:view），请联系空间管理员"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/workspace/projects/dashboard')">
          返回项目工作台
        </el-button>
      </template>
    </el-result>

    <template v-else>
      <header class="bug-analysis__head">
        <div>
          <h1 class="bug-analysis__title">AI 缺陷分析</h1>
          <p class="bug-analysis__desc">趋势与质量度量、AI 摘要、分诊建议与存量扫描，范围限于当前项目</p>
        </div>
        <div class="bug-analysis__actions">
          <BatchActions :ai-available="aiAvailable" @submitted="void 0" />
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

      <div class="bug-analysis__block bug-analysis__split">
        <AiSummaryCard
          class="bug-analysis__summary"
          :loading="store.summaryLoading"
          :running="store.summaryRunning"
          :text="store.summaryText"
          :citations="store.summaryCitations"
          :error="store.summaryError"
          :stale="store.summaryStale"
          @generate="void store.generateSummary()"
        />
        <TriageQueueCard
          class="bug-analysis__triage"
          :items="store.triageItems"
          :loading="store.triageLoading"
          :error="store.triageError"
          @refresh="void store.loadTriage()"
        />
      </div>
    </template>
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

.bug-analysis__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.bug-analysis__block {
  margin-bottom: var(--block-gap);
}

.bug-analysis__split {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: var(--space-md);
}

.bug-analysis__summary,
.bug-analysis__triage {
  min-width: 0;
}
</style>
