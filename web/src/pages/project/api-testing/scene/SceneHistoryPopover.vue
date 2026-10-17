<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/format'
import type { ApiExecutionHistoryItem } from '@/types'

// 执行历史弹层：场景编辑页头部（编辑态信息组 / gitlab_ci 只读行）共用同一份交互
defineProps<{
  history: ApiExecutionHistoryItem[]
  total: number
  page: number
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  (e: 'load'): void
  (e: 'view-report', reportId: string): void
  (e: 'update:page', page: number): void
}>()
</script>

<template>
  <el-popover placement="bottom-start" :width="360" trigger="click">
    <template #reference>
      <el-button link @click="emit('load')">
        执行历史
        <el-icon class="shp-caret"><ArrowDown /></el-icon>
      </el-button>
    </template>
    <div v-loading="loading" class="shp-panel">
      <div class="shp-head">执行历史</div>
      <div v-if="error" class="shp-error" role="alert">
        <span>{{ error }}</span>
        <el-button link type="primary" @click="emit('load')">重试</el-button>
      </div>
      <div v-for="h in history" :key="h.id" class="shp-row">
        <span class="shp-status" :class="`is-${h.status}`">{{ h.status }}</span>
        <span class="shp-time">{{ formatDateTime(h.executedAt) }}</span>
        <el-button
          v-if="h.reportId"
          link
          type="primary"
          class="shp-report"
          @click="emit('view-report', h.reportId)"
        >报告</el-button>
      </div>
      <div v-if="!history.length && !error" class="shp-empty">暂无执行记录</div>
      <el-pagination
        v-if="total > 0"
        :current-page="page"
        :page-size="20"
        :total="total"
        layout="prev, pager, next"
        class="shp-pager"
        @current-change="(p: number) => { emit('update:page', p); emit('load') }"
      />
    </div>
  </el-popover>
</template>

<style scoped lang="scss">
.shp-caret {
  font-size: 10px;
  opacity: 0.75;
}

.shp-panel {
  max-height: 280px;
  overflow-y: auto;
}

.shp-head {
  margin-bottom: 8px;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.shp-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
  color: var(--el-color-danger);
  font-size: var(--font-size-xs);
}

.shp-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 6px 0;
  font-size: var(--font-size-xs);
  border-bottom: 1px solid var(--color-neutral-100);
}

.shp-status {
  flex-shrink: 0;
  min-width: 52px;
  font-weight: 600;

  &.is-success { color: var(--el-color-success); }
  &.is-failed,
  &.is-error { color: var(--el-color-danger); }
  &.is-pending,
  &.is-running { color: var(--el-color-primary); }
}

.shp-time {
  flex: 1;
  color: var(--color-neutral-500);
}

// [报告] 贴行右端：即便时间列为空也不会被挤到中间
.shp-report {
  margin-left: auto;
}

.shp-empty {
  padding: var(--space-sm) 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.shp-pager {
  margin-top: var(--space-sm);
  justify-content: flex-end;
}
</style>
