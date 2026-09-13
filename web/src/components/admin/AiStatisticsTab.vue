<script setup lang="ts">
import type { AiStatistics } from '@/types'

defineProps<{
  statistics: AiStatistics | null
}>()

const groupBy = defineModel<string>('groupBy', { required: true })

const emit = defineEmits<{
  (e: 'change'): void
}>()
</script>

<template>
  <div class="ai-statistics-tab">
    <div class="ai-statistics-tab__bar">
      <el-radio-group v-model="groupBy" @change="emit('change')">
        <el-radio-button value="functionType">按功能</el-radio-button>
        <el-radio-button value="workspace">按空间</el-radio-button>
        <el-radio-button value="day">按日期</el-radio-button>
        <el-radio-button value="model">按模型</el-radio-button>
        <el-radio-button value="user">按用户</el-radio-button>
      </el-radio-group>
    </div>
    <template v-if="statistics">
      <div class="ai-statistics-tab__grid">
        <div class="stat-card stat-card--primary">
          <div class="stat-card__icon"><el-icon :size="22"><DataAnalysis /></el-icon></div>
          <div>
            <div class="stat-card__label">总调用次数</div>
            <div class="stat-card__value">{{ statistics.totalCalls }}</div>
          </div>
        </div>
        <div class="stat-card stat-card--teal">
          <div class="stat-card__icon"><el-icon :size="22"><Coin /></el-icon></div>
          <div>
            <div class="stat-card__label">总 Token</div>
            <div class="stat-card__value">{{ statistics.totalTokens }}</div>
          </div>
        </div>
        <div class="stat-card stat-card--danger">
          <div class="stat-card__icon"><el-icon :size="22"><WarningFilled /></el-icon></div>
          <div>
            <div class="stat-card__label">失败次数</div>
            <div class="stat-card__value">{{ statistics.failedCalls }}</div>
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

  :deep(.stat-card--primary) {
    background: var(--color-primary-50);

    .stat-card__icon {
      background: var(--color-primary-100);
      color: var(--color-primary-600);
    }
  }

  :deep(.stat-card--teal) {
    background: var(--color-teal-50, #f0f9f9);

    .stat-card__icon {
      background: var(--color-teal-100, #d3f2f2);
      color: var(--color-teal-600, #0a8f8f);
    }
  }

  :deep(.stat-card--danger) {
    background: var(--color-danger-50, #fef0f0);

    .stat-card__icon {
      background: var(--color-danger-100, #fde2e2);
      color: var(--color-danger-600, #d03050);
    }
  }
}
</style>