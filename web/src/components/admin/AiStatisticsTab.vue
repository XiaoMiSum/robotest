<script setup lang="ts">
import { computed } from 'vue'
import type { AiStatistics } from '@/types'

const props = defineProps<{
  statistics: AiStatistics | null
}>()

const groupBy = defineModel<string>('groupBy', { required: true })
// 未选区间时为 null，后端缺省回看近 30 天（AiStatisticsService minusDays(30)）
const dateRange = defineModel<[string, string] | null>('dateRange', { default: null })

const emit = defineEmits<{
  (e: 'change'): void
}>()

const GROUP_OPTIONS = [
  { label: '按功能', value: 'functionType' },
  { label: '按空间', value: 'workspace' },
  { label: '按日期', value: 'day' },
  { label: '按模型', value: 'model' },
  { label: '按用户', value: 'user' },
]

// 汇总卡副行按 demo 口径派生：平均值四舍五入、失败率保留两位
const windowText = computed(() =>
  dateRange.value ? `统计窗口 ${dateRange.value[0]} ~ ${dateRange.value[1]}` : '统计窗口近 30 天',
)
const avgTokensText = computed(() => {
  const calls = props.statistics?.totalCalls ?? 0
  const tokens = props.statistics?.totalTokens ?? 0
  return `单次平均约 ${calls > 0 ? Math.round(tokens / calls) : 0} token`
})
const failRateText = computed(() => {
  const calls = props.statistics?.totalCalls ?? 0
  const failed = props.statistics?.failedCalls ?? 0
  return `失败率 ${calls > 0 ? ((failed / calls) * 100).toFixed(2) : '0.00'}%`
})
</script>

<template>
  <div class="ai-statistics-tab">
    <div class="ai-statistics-tab__bar">
      <el-segmented v-model="groupBy" :options="GROUP_OPTIONS" @change="emit('change')" />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        @change="emit('change')"
      />
    </div>
    <template v-if="statistics">
      <div class="ai-statistics-tab__grid">
        <div class="stat-card stat-card--primary">
          <div class="stat-card__icon"><el-icon :size="22"><DataAnalysis /></el-icon></div>
          <div>
            <div class="stat-card__label">总调用次数</div>
            <div class="stat-card__value">{{ statistics.totalCalls }}</div>
            <div class="stat-card__foot">{{ windowText }}</div>
          </div>
        </div>
        <div class="stat-card stat-card--info">
          <div class="stat-card__icon"><el-icon :size="22"><Coin /></el-icon></div>
          <div>
            <div class="stat-card__label">总 Token</div>
            <div class="stat-card__value">{{ statistics.totalTokens }}</div>
            <div class="stat-card__foot">{{ avgTokensText }}</div>
          </div>
        </div>
        <div class="stat-card stat-card--danger">
          <div class="stat-card__icon"><el-icon :size="22"><WarningFilled /></el-icon></div>
          <div>
            <div class="stat-card__label">失败次数</div>
            <div class="stat-card__value">{{ statistics.failedCalls }}</div>
            <div class="stat-card__foot">{{ failRateText }}</div>
          </div>
        </div>
      </div>
      <el-card shadow="never">
        <el-table :data="statistics.items" stripe>
          <el-table-column prop="key" label="维度" />
          <el-table-column prop="calls" label="调用次数" width="120" align="right" />
          <el-table-column prop="tokens" label="Token" width="140" align="right" />
          <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" align="right" />
          <el-table-column prop="failed" label="失败" width="100" align="right" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped lang="scss">
.ai-statistics-tab {
  padding-top: var(--space-sm);
}

.ai-statistics-tab__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.ai-statistics-tab__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);

  :deep(.stat-card) {
    display: flex;
    align-items: center;
    gap: var(--space-md);
    padding: var(--space-lg);
    border-radius: var(--radius-lg);
  }

  :deep(.stat-card__icon) {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-md);
  }

  :deep(.stat-card__label) {
    font-size: 12px;
    color: var(--color-neutral-500);
  }

  :deep(.stat-card__value) {
    font-size: 22px;
    font-weight: 600;
    color: var(--color-neutral-800);
  }

  :deep(.stat-card__foot) {
    margin-top: 2px;
    font-size: var(--font-size-2xs);
    color: var(--color-neutral-400);
  }

  :deep(.stat-card--primary) {
    background: var(--color-primary-50);

    .stat-card__icon {
      background: var(--color-primary-100);
      color: var(--color-primary-600);
    }
  }

  /* 总 Token 为中性消耗量，取信息灰（视觉设计 4.1 中性信息→灰） */
  :deep(.stat-card--info) {
    background: var(--color-info-light);

    .stat-card__icon {
      background: var(--color-neutral-200);
      color: var(--color-info);
    }
  }

  :deep(.stat-card--danger) {
    background: var(--color-danger-light);

    .stat-card__icon {
      background: var(--color-danger-border);
      color: var(--color-danger-strong);
    }
  }
}

/* demo .stats-table：悬停 n-50、偶数行 n-25，较通用表（视觉设计 §8.3）更强一档的行反馈 */
.ai-statistics-tab :deep(.el-table) {
  --el-table-row-hover-bg-color: var(--color-neutral-50);
}

.ai-statistics-tab
  :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: var(--color-neutral-25) !important;
}

.ai-statistics-tab :deep(.el-table td.el-table__cell .cell) {
  font-variant-numeric: tabular-nums;
}

/* 首列为主维度名称（demo .cell-main：n-900 / 500） */
.ai-statistics-tab :deep(.el-table td.el-table__cell:first-child .cell) {
  color: var(--color-neutral-900);
  font-weight: 500;
}
</style>
