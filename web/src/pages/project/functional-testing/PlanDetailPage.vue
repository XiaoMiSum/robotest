<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { usePlanDetail } from '@/composables/project/functional-testing/plan/usePlanDetail'
import { usePlanOrderAssist } from '@/composables/project/ai/usePlanOrderAssist'
import PlanOrderPanel from '@/components/project/ai/PlanOrderPanel.vue'
import { planStatusMeta, planStatusLabel } from '@/components/project/functional-testing/plan/planListPresentation'

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
  canOperateActive,
  canBlock,
  canResume,
  handleComplete,
  handleBlock,
  handleResume,
  handleSync,
  openCaseSelector,
  handleCasesConfirm,
  handleCasesRemoved,
  refreshProgress,
  load,
} = usePlanDetail({ planId })

// 状态文案取自展示口径模块；操作按钮按计划状态显隐，服务端授权兜底
const statusText = computed(() => planStatusLabel(detail.value?.status ?? ''))

// ---------- 执行顺序建议（详设 5.1 / 交互 2.2） ----------
const {
  entryVisible: orderEntryVisible,
  panelVisible: orderPanelVisible,
  rows: orderRows,
  loading: orderLoading,
  confirming: orderConfirming,
  running: orderRunning,
  failed: orderFailed,
  failedReason: orderFailedReason,
  progressText: orderProgressText,
  loadError: orderLoadError,
  launch: launchOrderAssist,
  retry: retryOrderAssist,
  refresh: refreshOrderAssist,
  apply: applyOrderAssist,
  closePanel: closeOrderAssistPanel,
  onPanelClose: onOrderPanelClose,
} = usePlanOrderAssist({
  planId,
  onConfirmed: () => {
    void load()
    mindMapRef.value?.reload()
  },
})

/** 计划关联用例为空时后端 1000018304，入口置灰并说明原因（详设 4.1） */
const orderEmptyReason = computed(() =>
  (progress.value?.totalAssociated ?? 0) === 0 ? '计划关联用例为空，无法推荐执行顺序' : '',
)
</script>

<template>
  <div v-loading="loading" class="plan-detail">
    <!-- 页头收拢为单条无边框横条：面包屑保持纯净，右侧同行承载需求/状态/进度/操作（交互 06 §1.2） -->
    <div class="plan-detail__header">
      <div class="plan-detail__breadcrumb">
        <router-link to="/workspace/projects/functional-testing?tab=plans">测试计划</router-link>
        <el-icon :size="13"><ArrowRight /></el-icon>
        <span>{{ detail?.name ?? '计划详情' }}</span>
      </div>
      <div class="plan-detail__extra">
        <div v-if="detail?.requirements?.length" class="plan-detail__requirements">
          <span class="plan-detail__requirements-label">需求</span>
          <el-tooltip
            v-for="item in detail.requirements"
            :key="item.id"
            :content="item.title"
            placement="top"
          >
            <el-tag size="small" effect="plain">{{ item.code }}</el-tag>
          </el-tooltip>
        </div>
        <el-tag
          v-if="detail"
          :type="planStatusMeta(detail.status).tagType"
          size="small"
          effect="light"
          round
        >{{ statusText }}</el-tag>
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
          <el-tooltip
            v-if="orderEntryVisible && canOperateActive"
            :content="orderEmptyReason || '按 AI 建议调整计划执行顺序'"
            placement="top"
          >
            <span>
              <el-button size="small" plain :disabled="!!orderEmptyReason" @click="launchOrderAssist">
                <el-icon><Sort /></el-icon>执行顺序建议
              </el-button>
            </span>
          </el-tooltip>
          <el-button v-if="canOperateActive" size="small" plain @click="handleSync">
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
            v-if="canOperateActive"
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
        <template #header>
          <div class="plan-detail__tree-header">
            <span class="plan-detail__tree-title">快照文档</span>
            <span class="plan-detail__tree-hint">只读</span>
          </div>
        </template>
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

    <!-- 执行顺序建议：进行中展示进度入口，完成后切换为排序面板（交互 2.2 / 2.3） -->
    <el-dialog
      v-model="orderPanelVisible"
      title="执行顺序建议"
      width="720px"
      :close-on-click-modal="false"
      @closed="closeOrderAssistPanel"
    >
      <div v-if="orderRunning" class="plan-detail__ai-progress">
        <el-icon class="is-loading"><Loading /></el-icon>
        <div class="plan-detail__ai-progress-text">
          <span>AI 正在生成执行顺序建议…</span>
          <span v-if="orderProgressText" class="plan-detail__ai-progress-detail">
            {{ orderProgressText }}
          </span>
        </div>
        <el-button link @click="closeOrderAssistPanel">后台运行，关闭</el-button>
      </div>
      <el-alert
        v-else-if="orderFailed"
        type="error"
        :closable="false"
        show-icon
        :title="orderFailedReason || '执行顺序建议生成失败'"
      >
        <el-button size="small" type="danger" plain @click="retryOrderAssist">再次执行</el-button>
      </el-alert>
      <el-alert
        v-else-if="orderLoadError"
        type="error"
        :closable="false"
        show-icon
        :title="orderLoadError"
      >
        <el-button size="small" plain @click="refreshOrderAssist">重试</el-button>
        <el-button size="small" plain @click="closeOrderAssistPanel">关闭</el-button>
      </el-alert>
      <PlanOrderPanel
        v-else
        :rows="orderRows"
        :can-adopt="canOperateActive"
        :confirming="orderConfirming"
        :loading="orderLoading"
        @adopt-edited="applyOrderAssist"
        @close="onOrderPanelClose"
      />
    </el-dialog>
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

// 单条无边框横条：左侧面包屑 + 右侧信息/操作同行；超宽时整行横向滚动而非换行（交互 06 §1.2）
.plan-detail__header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-sm);
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.plan-detail__header::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.plan-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);

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

  // 当前页只加深不加粗：位置感交给深浅（UI-BC-04）
  > :last-child {
    color: var(--color-neutral-900);
  }
}

.plan-detail__requirements {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: var(--space-xs);
}

.plan-detail__requirements-label {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  white-space: nowrap;
}

// 右侧信息/操作区：需求、状态、进度统计、操作按钮同行不换行
.plan-detail__extra {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  flex-shrink: 0;
  white-space: nowrap;
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

// 快照文档卡片：header 固定 + body 弹性滚动，与评审明细一致（交互 06 §1.2）
.plan-detail__tree-card {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    padding: 0;
    overflow: auto;
    // 只隐藏视觉滚动条：卡片 body 仍是滚动拥有者（UI-SC-03）
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  :deep(.el-card__body)::-webkit-scrollbar {
    width: 0;
    height: 0;
    display: none;
  }
}

.plan-detail__tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plan-detail__tree-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.plan-detail__tree-hint {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
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

.plan-detail__ai-progress {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) 0;
}

.plan-detail__ai-progress-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  font-size: var(--font-size-sm);
}

.plan-detail__ai-progress-detail {
  color: var(--color-neutral-400);
}
</style>