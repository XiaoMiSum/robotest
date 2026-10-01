<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useReviewDetail } from '@/composables/project/functional-testing/review/useReviewDetail'
import {
  isActiveReview,
  reviewStatusMeta,
  reviewStatusLabel,
} from '@/components/project/functional-testing/review/reviewListPresentation'
import ReviewMindMap from '@/components/project/functional-testing/review/ReviewMindMap.vue'
import SnapshotModuleTree from '@/components/project/functional-testing/review/SnapshotModuleTree.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'

const route = useRoute()
const reviewId = route.params.reviewId as string

const {
  loading,
  detail,
  progress,
  mindMapRef,
  moduleTree,
  selectedDocId,
  selectorVisible,
  plannedCases,
  canComplete,
  handleComplete,
  handleReject,
  handleReopen,
  handleSync,
  openCaseSelector,
  handleCasesConfirm,
  handleCasesRemoved,
  refreshProgress,
  authStore,
} = useReviewDetail({ reviewId })

// 状态展示口径与列表页共用（交互设计 07 §1.1）
const statusMeta = computed(() => reviewStatusMeta(detail.value?.status ?? ''))
const statusText = computed(() => reviewStatusLabel(detail.value?.status ?? ''))
</script>

<template>
  <div v-loading="loading" class="review-detail">
    <div class="review-detail__breadcrumb">
      <router-link to="/workspace/projects/functional-testing?tab=reviews">测试评审</router-link>
      <el-icon :size="13"><ArrowRight /></el-icon>
      <span>{{ detail?.title ?? '评审详情' }}</span>
    </div>

    <div class="review-detail__head">
      <div class="review-detail__head-left">
        <span class="review-detail__title">{{ detail?.title ?? '评审详情' }}</span>
        <el-tag v-if="detail" :type="statusMeta.tagType" size="small" effect="light" round>
          {{ statusText }}
        </el-tag>
      </div>
      <div class="review-detail__extra">
        <div v-if="progress" class="review-detail__progress-row">
          <el-progress
            class="review-detail__progress"
            :percentage="progress.progressPercent"
            :stroke-width="8"
          />
          <div class="review-detail__stats">
            <span class="review-detail__stat review-detail__stat--pass"
              >通过 {{ progress.passed }}</span
            >
            <span class="review-detail__stat review-detail__stat--fail"
              >不通过 {{ progress.failed }}</span
            >
            <span class="review-detail__stat review-detail__stat--pending"
              >待评审 {{ progress.pending }}</span
            >
            <span class="review-detail__stat">共 {{ progress.totalAssociated }}</span>
          </div>
        </div>
        <!-- 活跃态操作组：调整/同步/驳回/完成，终态收起（交互设计 07 §1.2） -->
        <div v-if="detail && isActiveReview(detail.status)" class="review-detail__actions">
          <el-button size="small" plain @click="openCaseSelector">
            <el-icon><EditPen /></el-icon>调整用例
          </el-button>
          <el-button size="small" plain @click="handleSync">
            <el-icon><Refresh /></el-icon>同步用例
          </el-button>
          <el-button size="small" type="danger" plain @click="handleReject">
            <el-icon><CircleClose /></el-icon>驳回
          </el-button>
          <el-tooltip
            :disabled="canComplete"
            content="全部用例评审通过后才能完成评审"
            placement="bottom"
          >
            <span>
              <el-button
                size="small"
                type="primary"
                :disabled="!canComplete"
                @click="handleComplete"
              >
                <el-icon><CircleCheck /></el-icon>完成评审
              </el-button>
            </span>
          </el-tooltip>
        </div>
        <!-- 已驳回：仅发起人可见，重新发起后回到进行中 -->
        <div
          v-else-if="detail && detail.status === 'rejected' && detail.initiator.id === authStore.user?.id"
          class="review-detail__actions"
        >
          <el-button size="small" type="primary" @click="handleReopen">
            <el-icon><RefreshLeft /></el-icon>重新发起
          </el-button>
        </div>
      </div>
    </div>

    <div class="review-detail__workspace">
      <el-card shadow="never" class="review-detail__tree-card">
        <template #header>
          <div class="review-detail__tree-header">
            <span class="review-detail__tree-title">快照文档</span>
            <span class="review-detail__tree-hint">只读</span>
          </div>
        </template>
        <SnapshotModuleTree
          :data="moduleTree"
          :current-doc-id="selectedDocId"
          @select-document="(id: string) => (selectedDocId = id)"
        />
      </el-card>
      <el-card shadow="never" class="review-detail__body">
        <div v-if="!selectedDocId" class="review-detail__placeholder">
          <el-empty description="请在左侧选择一个文档" />
        </div>
        <ReviewMindMap
          v-else
          ref="mindMapRef"
          :review-id="reviewId"
          :document-id="selectedDocId"
          :removable="isActiveReview(detail?.status ?? '')"
          @marked="refreshProgress"
          @removed="handleCasesRemoved"
        />
      </el-card>
    </div>

    <CaseSelector
      v-model="selectorVisible"
      :initial-selected="plannedCases"
      @confirm="handleCasesConfirm"
    />
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

.review-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-sm);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  flex-shrink: 0;

  // 回退入口沿用全站链接色：颜色本身就是「可点击」信号，与仅作定位的当前页项区分
  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;
  }

  a:hover {
    color: var(--color-primary-600);
  }

  .el-icon {
    color: var(--color-neutral-400);
  }

  // 当前页只加深不加粗：位置感交给深浅，强调由紧随其下的页面标题承担
  > :last-child {
    color: var(--color-neutral-900);
  }
}

// 标题行用白底卡片化横条承载，与下方脑图卡片视觉统一
.review-detail__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
}

// 左侧标题/元信息区可伸缩，窗口变窄时优先裁剪标题而非挤压右侧操作区
.review-detail__head-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.review-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// 标题行右侧：进度/统计 + 操作按钮同行排布
.review-detail__extra {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.review-detail__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  // 与左侧进度统计区用竖线分隔，避免同行内容粘连
  padding-left: var(--space-lg);
  border-left: 1px solid var(--color-neutral-200);

  // 间距由 gap 统一控制，去除 el-button 相邻默认 margin（tooltip 包裹导致间距不均）
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  :deep(.el-button .el-icon) {
    margin-right: 4px;
  }
}

.review-detail__progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.review-detail__progress {
  width: 140px;
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

.review-detail__workspace {
  margin-top: var(--space-lg);
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--space-lg);
}

// 快照文档卡片：header 固定 + body 弹性滚动，对齐示例页 rv-tree 结构
.review-detail__tree-card {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__header) {
    flex-shrink: 0;
    padding: var(--space-sm) var(--space-md);
  }

  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    padding: 0;
    overflow: auto;
  }
}

.review-detail__tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-detail__tree-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.review-detail__tree-hint {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.review-detail__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.review-detail__body {
  flex: 1;
  min-width: 0;

  :deep(.el-card__body) {
    height: 100%;
    padding: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
}
</style>
