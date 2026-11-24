<script setup lang="ts">
import { computed } from 'vue'
import type { TestPlanListItem } from '@/types'
import { formatDateTime } from '@/utils/format'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import {
  isActivePlan,
  canResumePlan,
  planEnvironment,
  planListAction,
  planNameSub,
  planPassRate,
  planProgressStatus,
  planProgressText,
  planStatusMeta,
  planTimeRange,
} from './planListPresentation'

const props = defineProps<{ plans: TestPlanListItem[]; canComplete: boolean; canResume: boolean; canEdit: boolean; visibleKeys: string[] }>()
const emit = defineEmits<{
  open: [id: string]
  edit: [plan: TestPlanListItem]
  complete: [plan: TestPlanListItem]
  resume: [plan: TestPlanListItem]
  delete: [plan: TestPlanListItem]
}>()
const rows = computed(() =>
  props.plans.map((plan) => ({
    id: plan.id,
    plan,
    meta: planStatusMeta(plan.status),
    action: planListAction(plan.status),
    passRate: planPassRate(plan.status, plan.passRate),
    progressStatus: planProgressStatus(plan.status),
    timeText: planTimeRange(plan.startTime, plan.endTime),
    environment: planEnvironment(plan.environment),
    nameSub: planNameSub(plan.totalAssociated, plan.requirementCount),
  })),
)
</script>

<template>
  <el-table class="plan-table" :data="rows" row-key="id" height="100%">
    <template #empty><slot name="empty" /></template>
    <el-table-column v-if="visibleKeys.includes('name')" label="名称" min-width="300">
      <template #default="{ row }">
        <div class="plan-table__cell">
          <ListTitleLink :title="row.plan.name" @open="emit('open', row.plan.id)" />
          <span class="plan-table__secondary">{{ row.nameSub }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('owner')" label="负责人" width="100" show-overflow-tooltip>
      <template #default="{ row }">{{ row.plan.executor?.name ?? '—' }}</template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('timeRange')" label="起止时间" width="176">
      <template #default="{ row }"
        ><span class="plan-table__time">{{ row.timeText }}</span></template
      >
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('environment')" label="环境" width="116" show-overflow-tooltip>
      <template #default="{ row }">
        <span v-if="row.environment === '—'" class="plan-table__secondary">—</span>
        <el-tag v-else type="info" size="small" round>{{ row.environment }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('status')" label="状态" width="96">
      <template #default="{ row }">
        <span class="plan-table__status" :class="`plan-table__status--${row.meta.modifier}`">
          <span class="plan-table__dot" aria-hidden="true" />{{ row.meta.label }}
        </span>
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('progress')" label="进度" min-width="170">
      <template #default="{ row }">
        <el-progress
          :percentage="row.plan.progressPercent"
          :stroke-width="6"
          :status="row.progressStatus"
          >{{ planProgressText(row.plan.progressPercent) }}</el-progress
        >
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('passRate')" label="通过率" width="92" align="right">
      <template #default="{ row }"
        ><span :class="`plan-table__rate--${row.passRate.tone}`">{{
          row.passRate.text
        }}</span></template
      >
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('createdAt')" label="创建时间" width="160">
      <template #default="{ row }"
        ><span class="plan-table__time">{{ formatDateTime(row.plan.createdAt) }}</span></template
      >
    </el-table-column>
    <el-table-column label="操作" width="280" fixed="right">
      <template #header>
        <div class="plan-table__action-header">
          <span>操作</span>
          <slot name="column-setting" />
        </div>
      </template>
      <template #default="{ row }">
        <el-space :size="8">
          <el-button link type="primary" @click="emit('open', row.plan.id)">{{
            row.action === 'enter' ? '进入执行' : '查看详情'
          }}</el-button>
          <el-button
            v-if="canEdit && isActivePlan(row.plan.status)"
            link
            type="primary"
            @click="emit('edit', row.plan)"
            >编辑</el-button
          >
          <el-button
            v-if="isActivePlan(row.plan.status) && canComplete"
            link
            type="success"
            @click="emit('complete', row.plan)"
            >完成</el-button
          >
          <el-button
            v-if="canResumePlan(row.plan.status) && canResume"
            link
            type="primary"
            size="small"
            @click="emit('resume', row.plan)"
            >恢复</el-button>
          <el-button link type="danger" size="small" @click="emit('delete', row.plan)">删除</el-button>
        </el-space>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped lang="scss">
.plan-table__cell {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-width: 0;
}
.plan-table__secondary,
.plan-table__time {
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}
.plan-table__time {
  font-variant-numeric: tabular-nums;
}
.plan-table__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  white-space: nowrap;
}
.plan-table__dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: currentColor;
}
.plan-table__status--running {
  color: var(--color-warning);
  .plan-table__dot {
    box-shadow: 0 0 0 3px var(--el-color-warning-light-8);
  }
}
.plan-table__status--success,
.plan-table__rate--success {
  color: var(--color-success);
}
.plan-table__rate--success {
  font-weight: 600;
}
.plan-table__status--blocked {
  color: var(--color-blocked);
}
.plan-table__rate--muted {
  color: var(--color-neutral-600);
}
.plan-table__action-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  width: 100%;
}
</style>
