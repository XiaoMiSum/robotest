<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closePlan, getPlanDetail, getPlanProgress, startPlan, syncPlan } from '@/services/project'
import type { TestPlanDetail, TestPlanProgress } from '@/types'
import { formatDateTime, formatDate } from '@/utils/format'
import MindMapEditor from '@/components/project/MindMapEditor.vue'

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
      <template #content><span class="plan-detail__title">{{ detail?.name ?? '计划详情' }}</span></template>
    </el-page-header>

    <el-card v-if="detail" shadow="never" class="plan-detail__info">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="负责人">{{ detail.executor?.name ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" effect="light" round>{{ statusLabel[detail.status] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="起止时间">
          {{ detail.startTime ? formatDate(detail.startTime) : '?' }} ~ {{ detail.endTime ? formatDate(detail.endTime) : '?' }}
        </el-descriptions-item>
        <el-descriptions-item label="环境">{{ detail.environment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="progress" class="plan-detail__progress">
        <el-progress :percentage="progress.progressPercent" :stroke-width="20" text-inside />
        <div class="plan-detail__stats">
          通过 {{ progress.passed }} / 失败 {{ progress.failed }} / 阻塞 {{ progress.blocked }} / 未执行 {{ progress.untested }}（共 {{ progress.totalAssociated }}）
        </div>
      </div>

      <div class="plan-detail__actions">
        <el-button v-if="detail.status === 'new'" type="primary" @click="handleStart">开始执行</el-button>
        <el-button v-if="detail.status === 'in_progress'" @click="handleSync">同步最新用例</el-button>
        <el-button v-if="detail.status === 'in_progress'" type="danger" @click="handleClose">关闭计划</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="plan-detail__body">
      <MindMapEditor :plan-id="planId" mode="plan" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.plan-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.plan-detail__info {
  margin-top: var(--space-lg);
}

.plan-detail__progress {
  margin-top: var(--space-lg);
}

.plan-detail__stats {
  margin-top: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.plan-detail__actions {
  margin-top: var(--space-lg);
  display: flex;
  gap: var(--space-sm);
}

.plan-detail__body {
  margin-top: var(--space-lg);
  min-height: 300px;
}
</style>
