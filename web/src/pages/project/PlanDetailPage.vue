<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closePlan, getPlanDetail, getPlanProgress, startPlan, syncPlan } from '@/services/project'
import type { TestPlanDetail, TestPlanProgress } from '@/types'
import { formatDateTime, formatDate } from '@/utils/format'
import PlanMindMap from '@/components/project/PlanMindMap.vue'

const route = useRoute()
const router = useRouter()
const planId = route.params.planId as string

const loading = ref(false)
const detail = ref<TestPlanDetail | null>(null)
const progress = ref<TestPlanProgress | null>(null)

const statusLabel: Record<string, string> = { new: '待开始', in_progress: '进行中', completed: '已完成', closed: '已关闭' }

async function load() {
  loading.value = true
  try {
    const [d, p] = await Promise.all([getPlanDetail(planId), getPlanProgress(planId)])
    detail.value = d
    progress.value = p
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载计划详情失败')
  } finally {
    loading.value = false
  }
}

async function handleStart() {
  try {
    await startPlan(planId)
    ElMessage.success('计划已开始')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

async function handleClose() {
  try {
    await ElMessageBox.confirm('确定关闭该计划吗？', '关闭计划', { type: 'warning' })
  } catch { return }
  try {
    await closePlan(planId)
    ElMessage.success('计划已关闭')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '关闭失败')
  }
}

async function handleSync() {
  try {
    await ElMessageBox.confirm('同步将更新快照节点属性，已有执行记录不受影响。确定同步？', '同步最新用例', { type: 'info' })
  } catch { return }
  try {
    await syncPlan(planId)
    ElMessage.success('已同步')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '同步失败')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="plan-detail">
    <el-page-header @back="router.push('/workspace/projects/functional-testing')">
      <template #content>
        <div class="plan-detail__header">
          <span class="plan-detail__title">{{ detail?.name ?? '计划详情' }}</span>
          <template v-if="detail">
            <el-tag size="small" effect="light" round>{{ statusLabel[detail.status] }}</el-tag>
            <span class="plan-detail__meta-item">负责人：{{ detail.executor?.name ?? '-' }}</span>
            <el-divider direction="vertical" />
            <span class="plan-detail__meta-item">
              起止：{{ detail.startTime ? formatDate(detail.startTime) : '?' }} ~ {{ detail.endTime ? formatDate(detail.endTime) : '?' }}
            </span>
            <el-divider direction="vertical" />
            <span class="plan-detail__meta-item">环境：{{ detail.environment || '-' }}</span>
            <el-divider direction="vertical" />
            <span class="plan-detail__meta-item">创建于 {{ formatDateTime(detail.createdAt) }}</span>
          </template>
        </div>
      </template>
      <template #extra>
        <div class="plan-detail__extra">
          <div v-if="progress" class="plan-detail__progress-row">
            <el-progress class="plan-detail__progress" :percentage="progress.progressPercent" :stroke-width="8" />
            <div class="plan-detail__stats">
              <span class="plan-detail__stat plan-detail__stat--pass">通过 {{ progress.passed }}</span>
              <span class="plan-detail__stat plan-detail__stat--fail">失败 {{ progress.failed }}</span>
              <span class="plan-detail__stat plan-detail__stat--blocked">阻塞 {{ progress.blocked }}</span>
              <span class="plan-detail__stat">未执行 {{ progress.untested }}</span>
              <span class="plan-detail__stat">共 {{ progress.totalAssociated }}</span>
            </div>
          </div>
          <div v-if="detail" class="plan-detail__actions">
            <el-button v-if="detail.status === 'new'" size="small" type="primary" @click="handleStart">开始执行</el-button>
            <el-button v-if="detail.status === 'in_progress'" size="small" @click="handleSync">同步最新用例</el-button>
            <el-button v-if="detail.status === 'in_progress'" size="small" type="danger" @click="handleClose">关闭计划</el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <el-card shadow="never" class="plan-detail__body">
      <PlanMindMap :plan-id="planId" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
// 页面整高 flex 布局：头部固定、脑图卡片撑满剩余空间，消除底部留白（与评审详情页保持一致）
.plan-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.plan-detail__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.plan-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.plan-detail__meta-item {
  font-size: var(--font-size-sm);
  font-weight: 400;
  color: var(--color-neutral-600);
  white-space: nowrap;
}

// 标题行右侧：进度/统计 + 操作按钮同行排布
.plan-detail__extra {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.plan-detail__actions {
  flex-shrink: 0;
}

.plan-detail__progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.plan-detail__progress {
  width: 140px;
}

.plan-detail__stats {
  flex-shrink: 0;
  display: flex;
  gap: var(--space-md);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.plan-detail__stat--pass {
  color: var(--color-success);
}

.plan-detail__stat--fail {
  color: var(--color-danger);
}

.plan-detail__stat--blocked {
  color: var(--color-warning);
}

.plan-detail__body {
  margin-top: var(--space-lg);
  flex: 1;
  min-height: 0;

  :deep(.el-card__body) {
    height: 100%;
    padding: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
}
</style>
