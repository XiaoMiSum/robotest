<script setup lang="ts">
import { useRoute } from 'vue-router'
import { usePlanDetail } from '@/composables/usePlanDetail'
import { MagicStick } from '@element-plus/icons-vue'

const route = useRoute()
const planId = route.params.planId as string

const {
  loading,
  detail,
  progress,
  mindMapRef,
  moduleTree,
  selectedDocId,
  activeTab,
  orderPanelRef,
  selectorVisible,
  plannedCases,
  recommendVisible,
  recommendExcludeIds,
  canShowOrder,
  canAdjustCases,
  handleComplete,
  handleSync,
  openCaseSelector,
  handleCasesConfirm,
  handleCasesRemoved,
  openRecommend,
  handleBringIn,
  refreshProgress,
  handleOrderLocate,
  handleOrderResult,
  handleOrderSelect,
  statusLabel,
  router,
  aiStore,
} = usePlanDetail({ planId })
</script>

<template>
  <div v-loading="loading" class="plan-detail">
    <el-page-header
      class="plan-detail__page-header"
      @back="router.push('/workspace/projects/functional-testing?tab=plans')"
    >
      <template #content>
        <div class="plan-detail__header">
          <span class="plan-detail__title">{{ detail?.name ?? '计划详情' }}</span>
          <el-tag v-if="detail" size="small" effect="light" round>{{
            statusLabel[detail.status]
          }}</el-tag>
        </div>
      </template>
      <template #extra>
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
              <span class="plan-detail__stat">待执行 {{ progress.untested }}</span>
              <span class="plan-detail__stat">共 {{ progress.totalAssociated }}</span>
            </div>
          </div>
          <div v-if="detail" class="plan-detail__actions">
            <el-button
              v-if="aiStore.aiEnabled && canAdjustCases"
              size="small"
              plain
              @click="openRecommend"
            >
              <el-icon><MagicStick /></el-icon>AI 推荐用例
            </el-button>
            <el-button v-if="canAdjustCases" size="small" plain @click="openCaseSelector">
              <el-icon><EditPen /></el-icon>调整用例
            </el-button>
            <el-button v-if="canAdjustCases" size="small" plain @click="handleSync">
              <el-icon><Refresh /></el-icon>同步用例
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
      </template>
    </el-page-header>

    <!-- 计划详情标签：执行记录（默认）与执行顺序推荐（交互设计 5.1）；推荐标签仅计划负责人/执行人可见 -->
    <!-- el-tabs 无 extra 插槽（Element Plus 已移除该插槽），计算按钮改由外层容器绝对定位于标签栏右侧同行（交互设计 5.1 布局） -->
    <div class="plan-detail__tabs-wrap">
      <el-tabs v-model="activeTab" class="plan-detail__tabs">
        <el-tab-pane label="执行记录" name="records">
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
                @order-select="handleOrderSelect"
                @removed="handleCasesRemoved"
              />
            </el-card>
          </div>
        </el-tab-pane>
        <el-tab-pane v-if="canShowOrder" label="执行顺序推荐✨" name="order">
          <PlanOrderRecommend
            ref="orderPanelRef"
            :plan-id="planId"
            @locate="handleOrderLocate"
            @result="handleOrderResult"
          />
        </el-tab-pane>
      </el-tabs>
      <el-button
        v-if="activeTab === 'order'"
        class="plan-detail__order-btn"
        size="small"
        link
        :loading="orderPanelRef?.computing"
        @click="orderPanelRef?.compute()"
      >
        <el-icon><MagicStick /></el-icon>
        {{ orderPanelRef?.hasResult ? '重新计算' : '开始计算' }}
      </el-button>
    </div>

    <CaseSelector
      v-model="selectorVisible"
      :initial-selected="plannedCases"
      @confirm="handleCasesConfirm"
    />
    <CasePlanRecommendDialog
      v-model="recommendVisible"
      :exclude-case-node-ids="recommendExcludeIds"
      target="plan"
      @bring-in="handleBringIn"
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

// 标题行用白底卡片化横条承载，与下方脑图卡片视觉统一
.plan-detail__page-header {
  flex-shrink: 0;
  padding: var(--space-sm) var(--space-md);
  background: #fff;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);

  // 左侧标题/元信息区可伸缩，窗口变窄时优先裁剪标题而非挤压右侧操作区
  :deep(.el-page-header__left) {
    flex: 1;
    min-width: 0;
    margin-right: var(--space-lg);
  }

  :deep(.el-page-header__content) {
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }
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
  color: var(--color-warning);
}

// 标签容器撑满剩余高度：header 固定、内容区弹性占满，脑图/推荐面板在其中整高布局
.plan-detail__tabs-wrap {
  position: relative;
  margin-top: var(--space-lg);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.plan-detail__tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin-bottom: 0;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
  }

  :deep(.el-tab-pane) {
    height: 100%;
  }
}

// 执行顺序计算按钮：绝对定位于标签栏右侧同行（标签高 40px、small 按钮高 24px，top 8px 垂直居中）
// 采用 link 样式：与标签栏视觉层级保持一致，避免实心主色按钮在页头区喧宾夺主
.plan-detail__order-btn {
  position: absolute;
  top: 8px;
  right: 0;
  z-index: 1;

  // 图标与文字间距由按钮内部 gap 兜底，此处确保 loading 态下图标不额外占位
  :deep(.el-icon) {
    margin-right: 4px;
  }
}

.plan-detail__workspace {
  height: 100%;
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