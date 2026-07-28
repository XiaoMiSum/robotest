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
      <template #content>
        <div class="review-detail__header">
          <span class="review-detail__title">{{ detail?.title ?? '评审详情' }}</span>
          <template v-if="detail">
            <el-tag :type="detail.status === 'completed' ? 'success' : 'warning'" size="small" effect="light" round>
              {{ detail.status === 'completed' ? '已完成' : '评审中' }}
            </el-tag>
            <span class="review-detail__meta-item">发起人：{{ detail.initiator.name }}</span>
            <el-divider direction="vertical" />
            <span class="review-detail__meta-item">参与者：{{ detail.participantIds.length }} 人</span>
            <el-divider direction="vertical" />
            <span class="review-detail__meta-item">创建于 {{ formatDateTime(detail.createdAt) }}</span>
          </template>
        </div>
      </template>
      <template #extra>
        <div v-if="detail?.status === 'in_progress'" class="review-detail__actions">
          <el-button size="small" @click="handleSync">同步最新用例</el-button>
          <el-button size="small" type="primary" @click="handleComplete">完成评审</el-button>
        </div>
      </template>
    </el-page-header>

    <el-card v-if="progress" shadow="never" class="review-detail__info">
      <div class="review-detail__progress-row">
        <el-progress class="review-detail__progress" :percentage="progress.progressPercent" :stroke-width="8" />
        <div class="review-detail__stats">
          <span class="review-detail__stat review-detail__stat--pass">通过 {{ progress.passed }}</span>
          <span class="review-detail__stat review-detail__stat--fail">不通过 {{ progress.failed }}</span>
          <span class="review-detail__stat review-detail__stat--pending">待评审 {{ progress.pending }}</span>
          <span class="review-detail__stat">共 {{ progress.totalAssociated }}</span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="review-detail__body">
      <ReviewMindMap :review-id="reviewId" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
// 页面整高 flex 布局：头部固定、脑图卡片撑满剩余空间，消除底部留白
.review-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.review-detail__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.review-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.review-detail__meta-item {
  font-size: var(--font-size-sm);
  font-weight: 400;
  color: var(--color-neutral-600);
  white-space: nowrap;
}

.review-detail__info {
  margin-top: var(--space-lg);
  flex-shrink: 0;

  :deep(.el-card__body) {
    padding: var(--space-md) var(--space-lg);
  }
}

.review-detail__actions {
  flex-shrink: 0;
}

.review-detail__progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.review-detail__progress {
  flex: 1;
}

.review-detail__stats {
  flex-shrink: 0;
  display: flex;
  gap: var(--space-md);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.review-detail__stat--pass {
  color: var(--color-success);
}

.review-detail__stat--fail {
  color: var(--color-danger);
}

.review-detail__stat--pending {
  color: var(--color-warning);
}

.review-detail__body {
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
