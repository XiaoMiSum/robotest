<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useDashboard } from '@/composables/admin/useDashboard'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const {
  loading,
  subtitle,
  kpiCards,
  lineChart,
  donutSegments,
  donutTotal,
  workspaceList,
  workspaceTotal,
  workspacePage,
  workspacePages,
  refresh,
  gotoWorkspacePage,
} = useDashboard()
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="dashboard__head">
      <div class="dashboard__head-text">
        <h1 class="dashboard__title">数据概览</h1>
        <p class="dashboard__desc">{{ subtitle }}</p>
      </div>
      <el-button :loading="loading" @click="refresh">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <div class="dashboard__kpis">
      <div v-for="card in kpiCards" :key="card.key" class="kpi">
        <div class="kpi__label">
          <el-icon><component :is="card.icon" /></el-icon>{{ card.label }}
        </div>
        <div class="kpi__value">
          {{ card.value }}<span class="kpi__unit">{{ card.unit }}</span>
        </div>
        <div v-if="card.foot.length" class="kpi__foot">
          <template v-for="(part, index) in card.foot" :key="index">
            <span
              v-if="part.delta"
              class="kpi__delta"
              :class="{ 'kpi__delta--up': part.text !== '+0' }"
              >{{ part.text }}</span
            >
            <template v-else>{{ part.text }}</template>
          </template>
        </div>
      </div>
    </div>

    <div class="dashboard__charts">
      <section class="panel">
        <header class="panel__head">
          <h2 class="panel__title">
            近 14 日活跃用户<span class="panel__subtitle">按日去重登录用户</span>
          </h2>
          <span class="legend"><span class="legend__dot" />活跃用户</span>
        </header>
        <svg
          class="chart"
          viewBox="0 0 640 210"
          height="210"
          role="img"
          aria-label="近 14 日活跃用户趋势"
        >
          <line class="chart__grid" x1="0" y1="20" x2="640" y2="20" />
          <line class="chart__grid" x1="0" y1="70" x2="640" y2="70" />
          <line class="chart__grid" x1="0" y1="120" x2="640" y2="120" />
          <line class="chart__grid chart__grid--base" x1="0" y1="170" x2="640" y2="170" />
          <path class="chart__area" :d="lineChart.areaPath" />
          <polyline class="chart__line" :points="lineChart.points" />
          <circle
            class="chart__dot"
            :cx="lineChart.lastX"
            :cy="lineChart.lastY"
            r="4"
          />
          <text
            class="chart__last"
            :x="lineChart.lastX"
            :y="lineChart.lastLabelY"
            text-anchor="end"
            >{{ lineChart.lastCount }}</text
          >
          <text class="chart__axis" x="20" y="196" text-anchor="start">{{
            lineChart.firstLabel
          }}</text>
          <text
            class="chart__axis"
            :x="lineChart.middleX"
            y="196"
            text-anchor="middle"
            >{{ lineChart.middleLabel }}</text
          >
          <text class="chart__axis" x="605" y="196" text-anchor="end">{{
            lineChart.lastLabel
          }}</text>
        </svg>
      </section>

      <section class="panel">
        <header class="panel__head">
          <h2 class="panel__title">用户状态分布</h2>
          <span class="legend">按用户数</span>
        </header>
        <div class="donut">
          <svg
            class="donut__svg"
            width="132"
            height="132"
            viewBox="0 0 120 120"
            role="img"
            aria-label="用户状态分布"
          >
            <circle class="donut__track" cx="60" cy="60" r="46" />
            <circle
              v-for="seg in donutSegments"
              :key="seg.key"
              class="donut__seg"
              cx="60"
              cy="60"
              r="46"
              transform="rotate(-90 60 60)"
              :style="{
                stroke: seg.color,
                strokeDasharray: seg.dashArray,
                strokeDashoffset: seg.dashOffset,
              }"
            />
            <text class="donut__total" x="60" y="58" text-anchor="middle">{{
              donutTotal
            }}</text>
            <text class="donut__caption" x="60" y="76" text-anchor="middle">全部用户</text>
          </svg>
          <ul class="donut__legend">
            <li v-for="seg in donutSegments" :key="seg.key" class="donut__legend-item">
              <span class="donut__swatch" :style="{ background: seg.color }" />
              {{ seg.label }} {{ seg.value }}
            </li>
          </ul>
        </div>
      </section>
    </div>

    <section class="panel">
      <header class="panel__head">
        <h2 class="panel__title">最近创建的空间</h2>
        <el-link type="primary" underline="never" @click="router.push('/admin/workspaces')"
          >查看全部 →</el-link
        >
      </header>
      <el-table :data="workspaceList" row-key="id" empty-text="暂无工作空间">
        <el-table-column prop="name" label="空间名称" min-width="160" />
        <el-table-column label="创建人" width="120">
          <template #default="{ row }">{{ row.createdByName ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="成员" width="80" align="center">
          <template #default="{ row }">{{ row.memberCount }}</template>
        </el-table-column>
        <el-table-column label="项目" width="80" align="center">
          <template #default="{ row }">{{ row.projectCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span
              class="dot"
              :class="row.status === 'active' ? 'dot--success' : 'dot--neutral'"
            />{{ row.status === 'active' ? '活跃' : '已解散' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <footer class="dashboard__table-foot">
        <span class="dashboard__total">共 {{ workspaceTotal }} 个空间</span>
        <el-pagination
          v-if="workspacePages > 1"
          layout="prev, pager, next"
          :page-size="8"
          :total="workspaceTotal"
          :current-page="workspacePage"
          @current-change="gotoWorkspacePage"
        />
      </footer>
    </section>
  </div>
</template>

<style scoped lang="scss">
/* 面积填充为示例指定的低透明主色，非新增色值，故以局部变量收敛 */
.dashboard {
  --dash-area-fill: rgba(51, 112, 255, 0.08);
}

.dashboard__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.dashboard__title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.dashboard__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.dashboard__kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

@media (max-width: 1200px) {
  .dashboard__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .dashboard__kpis {
    grid-template-columns: 1fr;
  }
}

.kpi {
  padding: var(--space-lg) var(--space-xl);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.kpi__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.kpi__value {
  margin-top: 6px;
  font-size: var(--font-size-3xl);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: var(--color-neutral-900);
}

.kpi__unit {
  margin-left: 4px;
  font-size: var(--font-size-sm);
  font-weight: 400;
  color: var(--color-neutral-500);
}

.kpi__foot {
  margin-top: 6px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.kpi__delta {
  font-weight: 600;
  color: var(--color-neutral-500);
}

.kpi__delta--up {
  color: var(--color-success-strong);
}

.dashboard__charts {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

@media (max-width: 1100px) {
  .dashboard__charts {
    grid-template-columns: 1fr;
  }
}

.panel {
  padding: var(--space-xl);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.panel__title {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.panel__subtitle {
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-400);
}

.legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary-500);
}

.chart {
  display: block;
  width: 100%;
  height: 210px;
}

.chart__grid {
  stroke: var(--color-neutral-100);
  stroke-width: 1;
}

.chart__grid--base {
  stroke: var(--color-neutral-300);
}

.chart__area {
  fill: var(--dash-area-fill);
  stroke: none;
}

.chart__line {
  fill: none;
  stroke: var(--color-primary-500);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.chart__dot {
  fill: var(--color-primary-500);
  stroke: var(--color-neutral-0);
  stroke-width: 2;
}

.chart__last {
  font-size: 12px;
  font-weight: 600;
  fill: var(--color-neutral-800);
}

.chart__axis {
  font-size: 11px;
  fill: var(--color-neutral-400);
}

.donut {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.donut__svg {
  flex-shrink: 0;
}

.donut__track {
  fill: none;
  stroke: var(--color-neutral-100);
  stroke-width: 14;
}

.donut__seg {
  fill: none;
  stroke-width: 14;
}

.donut__total {
  font-size: 22px;
  font-weight: 700;
  fill: var(--color-neutral-900);
}

.donut__caption {
  font-size: 11px;
  fill: var(--color-neutral-400);
}

.donut__legend {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
}

.donut__swatch {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 2px;
}

.dashboard__table-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.dashboard__total {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  vertical-align: middle;
}

.dot--success {
  background: var(--color-success);
}

.dot--neutral {
  background: var(--color-neutral-400);
}
</style>
