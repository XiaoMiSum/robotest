<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { usePlanDetail } from '@/composables/project/functional-testing/plan/usePlanDetail'
import {
  canBlockPlan,
  canResumePlan,
  planStatusMeta,
  planStatusLabel,
} from '@/components/project/functional-testing/plan/planListPresentation'

const route = useRoute()
const planId = route.params.planId as string

const {
  loading,
  detail,
  progress,
  mindMapRef,
  moduleTree,
  selectedDocId,
  selectorVisible,
  plannedCases,
  canAdjustCases,
  handleComplete,
  handleBlock,
  handleResume,
  handleSync,
  openCaseSelector,
  handleCasesConfirm,
  handleCasesRemoved,
  refreshProgress,
} = usePlanDetail({ planId })

// 状态文案与阻塞/恢复入口显隐均取自展示口径模块；按钮是否真可用由后端校验负责人权限
const statusText = computed(() => planStatusLabel(detail.value?.status ?? ''))
const canBlock = computed(() => canBlockPlan(detail.value?.status ?? ''))
const canResume = computed(() => canResumePlan(detail.value?.status ?? ''))
</script>

<template>
  <div v-loading="loading" class="plan-detail">
    <div class="plan-detail__breadcrumb">
      <router-link to="/workspace/projects/functional-testing?tab=plans">测试计划</router-link>
      <el-icon :size="13"><ArrowRight /></el-icon>
      <span>{{ detail?.name ?? '计划详情' }}</span>
    </div>

    <div class="plan-detail__head">
      <div class="plan-detail__head-left">
        <span class="plan-detail__title">{{ detail?.name ?? '计划详情' }}</span>
        <el-tag
          v-if="detail"
          :type="planStatusMeta(detail.status).tagType"
          size="small"
          effect="light"
          round
        >{{ statusText }}</el-tag>
      </div>
      <div class="plan-detail__extra">
        <div v-if="progress" class="plan-detail__progress-row">
          <el-progress
            class="plan-detail__progress"
            :percentage="progress.progressPercent"
            :stroke-width="8"
          />
          <div class="plan-detail__stats">
            <span class="plan-detail__stat plan-detail__stat--pass"
              >通过 {{ progress.passed }}</span
            >
            <span class="plan-detail__stat plan-detail__stat--fail"
              >失败 {{ progress.failed }}</span
            >
            <span class="plan-detail__stat plan-detail__stat--blocked"
              >阻塞 {{ progress.blocked }}</span
            >
            <span class="plan-detail__stat">待执行 {{ progress.untested }}</span
            >
            <span class="plan-detail__stat">共 {{ progress.totalAssociated }}</span>
          </div>
        </div>
        <div v-if="detail" class="plan-detail__actions">
          <el-button v-if="canAdjustCases" size="small" plain @click="openCaseSelector">
            <el-icon><EditPen /></el-icon>调整用例
          </el-button>
          <el-button v-if="canAdjustCases" size="small" plain @click="handleSync">
            <el-icon><Refresh /></el-icon>同步用例
          </el-button>
          <el-button
            v-if="canBlock"
            size="small"
            plain
            @click="handleBlock"
          >
            <el-icon><VideoPause /></el-icon>阻塞
          </el-button>
          <el-button
            v-if="canResume"
            size="small"
            plain
            @click="handleResume"
          >
            <el-icon><VideoPlay /></el-icon>恢复
          </el-button>
          <el-button
            v-if="detail.status === 'new' || detail.status === 'in_progress'"
            size="small"
            type="primary"
            @click="handleComplete"
          >
            <el-icon><CircleCheck /></el-icon>完成执行
          </el-button>
        </div>
      </div>
    </div>

    <!-- 计划详情工作区（交互设计 5.1） -->
    <div class="plan-detail__workspace">
      <el-card shadow="never" class="plan-detail__tree-card">
        <SnapshotModuleTree
          :data="moduleTree"
          :current-doc-id="selectedDocId"
          @select-document="(id: string) => (selectedDocId = id)"
        />
      </el-card>
      <el-card shadow="never" class="plan-detail__body">
        <div v-if="!selectedDocId" class="plan-detail__placeholder">
          <el-empty description="请在左侧选择一个文档" />
        </div>
        <PlanMindMap
          v-else
          ref="mindMapRef"
          :plan-id="planId"
          :document-id="selectedDocId"
          :removable="canAdjustCases"
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
// 页面整高 flex 布局：头部固定、脑图卡片撑满剩余空间，消除底部留白（与评审详情页保持一致）
.plan-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.plan-detail__breadcrumb {
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
.plan-detail__head {
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
.plan-detail__head-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.plan-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// 标题行右侧：进度/统计 + 操作按钮同行排布
.plan-detail__extra {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.plan-detail__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  // 与左侧进度统计区用竖线分隔，避免同行内容粘连
  padding-left: var(--space-lg);
  border-left: 1px solid var(--color-neutral-200);

  // 间距由 gap 统一控制，去除 el-button 相邻默认 margin
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  :deep(.el-button .el-icon) {
    margin-right: 4px;
  }
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
  color: var(--color-blocked);
}

.plan-detail__workspace {
  margin-top: var(--space-lg);
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--space-lg);
}

.plan-detail__tree-card {
  width: 240px;
  flex-shrink: 0;

  :deep(.el-card__body) {
    padding: 0;
    overflow: auto;
    height: 100%;
  }
}

.plan-detail__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.plan-detail__body {
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