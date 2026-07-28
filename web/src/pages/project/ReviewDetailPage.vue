<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeReview, getReviewDetail, getReviewProgress, syncReview } from '@/services/project'
import type { TestReviewDetail, TestReviewProgress } from '@/types'
import { formatDateTime } from '@/utils/format'
import ReviewMindMap from '@/components/project/ReviewMindMap.vue'

const route = useRoute()
const router = useRouter()
const reviewId = route.params.reviewId as string

const loading = ref(false)
const detail = ref<TestReviewDetail | null>(null)
const progress = ref<TestReviewProgress | null>(null)

async function load() {
  loading.value = true
  try {
    const [d, p] = await Promise.all([getReviewDetail(reviewId), getReviewProgress(reviewId)])
    detail.value = d
    progress.value = p
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载评审详情失败')
  } finally {
    loading.value = false
  }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm('确定完成该评审吗？完成后将不可再修改标记。', '完成评审', { type: 'warning' })
  } catch { return }
  try {
    await completeReview(reviewId)
    ElMessage.success('评审已完成')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

async function handleSync() {
  try {
    await ElMessageBox.confirm('同步将更新快照节点属性，已有标记不受影响。确定同步？', '同步最新用例', { type: 'info' })
  } catch { return }
  try {
    await syncReview(reviewId)
    ElMessage.success('已同步')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '同步失败')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="review-detail">
    <el-page-header @back="router.push('/workspace/projects/functional-testing')">
      <template #content><span class="review-detail__title">{{ detail?.title ?? '评审详情' }}</span></template>
    </el-page-header>

    <el-card v-if="detail" shadow="never" class="review-detail__info">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="发起人">{{ detail.initiator.name }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 'completed' ? 'success' : 'warning'" size="small" effect="light" round>
            {{ detail.status === 'completed' ? '已完成' : '评审中' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="progress" class="review-detail__progress">
        <el-progress :percentage="progress.progressPercent" :stroke-width="20" text-inside />
        <div class="review-detail__stats">
          通过 {{ progress.passed }} / 不通过 {{ progress.failed }} / 待评审 {{ progress.pending }}（共 {{ progress.totalAssociated }}）
        </div>
      </div>

      <div v-if="detail.status === 'in_progress'" class="review-detail__actions">
        <el-button @click="handleSync">同步最新用例</el-button>
        <el-button type="primary" @click="handleComplete">完成评审</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="review-detail__body">
      <ReviewMindMap :review-id="reviewId" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.review-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.review-detail__info {
  margin-top: var(--space-lg);
}

.review-detail__progress {
  margin-top: var(--space-lg);
}

.review-detail__stats {
  margin-top: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.review-detail__actions {
  margin-top: var(--space-lg);
  display: flex;
  gap: var(--space-sm);
}

.review-detail__body {
  margin-top: var(--space-lg);
  min-height: 300px;
}
</style>
