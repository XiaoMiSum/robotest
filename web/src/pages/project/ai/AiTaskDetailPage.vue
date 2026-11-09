<script setup lang="ts">
import { computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { formatDateTime } from '@/utils/format'
import AiProposalReviewPanel from '@/components/project/ai/AiProposalReviewPanel.vue'
import ExecutionPanel from '@/components/project/ai/ExecutionPanel.vue'
import GenerationArtifactTreeReview from '@/components/project/ai/GenerationArtifactTreeReview.vue'
import PlanTimeline from '@/components/project/ai/PlanTimeline.vue'
import SelectionReviewPanel from '@/components/project/ai/SelectionReviewPanel.vue'
import TraceEventList from '@/components/project/ai/TraceEventList.vue'
import BugClassifyReviewPanel from '@/components/project/bug/BugClassifyReviewPanel.vue'
import { useAiTaskDetail } from '@/composables/project/ai/useAiTaskDetail'

const route = useRoute()

const {
  loading,
  loadError,
  notFound,
  detail,
  submitterText,
  aiAvailable,
  statusMeta,
  typeMeta,
  name,
  execution,
  planSteps,
  proposalCounts,
  proposals,
  terminateText,
  canAbort,
  canReExecute,
  showReviewArea,
  showRunningPlaceholder,
  showEmptyProposals,
  canManageTask,
  canConfirm,
  canConfirmGeneration,
  canConfirmSelection,
  canConfirmBug,
  taskId,
  load,
  handleAbort,
  handleReExecute,
  backToCenter,
} = useAiTaskDetail()

/** 圈选任务走专属审核面板；类型联合由 computed 收窄供子组件 props（模板不跨节点收窄） */
const isSelectionTask = computed(
  () => detail.value?.type === 'review_selection' || detail.value?.type === 'plan_selection',
)
const selectionTaskType = computed(() =>
  detail.value?.type === 'plan_selection' ? ('plan_selection' as const) : ('review_selection' as const),
)
/** 缺陷批量分类与存量重复扫描走缺陷审核面板；摘要 / 分诊提案只读，留通用面板预览 */
const isBugReviewTask = computed(
  () => detail.value?.type === 'bug_classify' || detail.value?.type === 'bug_duplicate_scan',
)

/** 从列表「审核」进入时定位审核区（交互 2.1.2）；审核区在 proposals_ready 后才渲染，待其出现再定位 */
const focusReview = route.query.review === '1'

function scrollReview(): void {
  void nextTick(() => {
    document
      .getElementById('task-review')
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

watch(
  () => detail.value?.status,
  (status) => {
    if (focusReview && status === 'proposals_ready') scrollReview()
  },
  { immediate: true },
)

/** 提案计数徽标点击定位审核区（交互 2.2） */
function focusReviewArea(): void {
  if (showReviewArea.value) scrollReview()
}
</script>

<template>
  <main class="ai-task-detail">
    <!-- 面包屑：内容区顶部回退入口（UI-BC-01） -->
    <nav class="task-breadcrumb" aria-label="面包屑">
      <el-link type="primary" :underline="false" @click="backToCenter">AI 任务中心</el-link>
      <span class="task-breadcrumb__sep" aria-hidden="true">›</span>
      <span class="task-breadcrumb__current">任务详情</span>
    </nav>

    <el-result
      v-if="notFound"
      status="404"
      title="任务不存在或无权访问"
      sub-title="任务可能已被删除，或当前账号无权查看"
    >
      <template #extra>
        <el-button type="primary" @click="backToCenter">返回任务中心</el-button>
      </template>
    </el-result>

    <el-skeleton
      v-else-if="!detail && !loadError && loading"
      :rows="8"
      animated
      class="detail-skeleton"
    />

    <el-alert
      v-else-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="detail-error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="load">重试</el-button>
      </template>
    </el-alert>

    <template v-else-if="detail">
      <!-- AI 未启用 / 未配置模型：整页降级提示（交互 2.1.3） -->
      <el-result
        v-if="!aiAvailable"
        status="warning"
        title="AI 能力未启用 / 未配置模型"
        sub-title="开启 AI 总开关并配置可用模型后可继续使用"
      />

      <template v-else>
        <header class="task-head">
          <div class="task-head__main">
            <div class="task-head__title-row">
              <h1 class="task-head__title">{{ name }}</h1>
              <el-tag :type="statusMeta.tagType" size="small" effect="light">
                {{ statusMeta.label }}
              </el-tag>
            </div>
            <p class="task-head__meta">
              类型 {{ typeMeta.label }} · 发起人 {{ submitterText }} · 发起时间
              {{ formatDateTime(detail.createdAt) }}
              <template v-if="execution && (execution.seq ?? 0) > 1">
                · 第 {{ execution.seq }} 次执行
              </template>
            </p>
          </div>
          <div class="task-head__actions">
            <el-button
              v-if="canManageTask && canAbort"
              type="warning"
              plain
              @click="handleAbort"
            >终止</el-button>
            <el-button
              v-if="canManageTask && canReExecute"
              type="primary"
              @click="handleReExecute"
            >再次执行</el-button>
          </div>
        </header>

        <!-- 执行区：步数与预算消耗、终止原因，2 秒轮询至 aborted / closed（交互 2.2） -->
        <section class="task-section">
          <ExecutionPanel
            :execution="execution"
            :task-status="detail.status"
            :terminate-text="terminateText"
          />
        </section>

        <!-- 计划步骤：plan 快照驱动，AI 自主规划不承诺固定序列（交互 2.2） -->
        <section v-if="planSteps.length > 0" class="task-section">
          <h2 class="task-section__title">计划步骤</h2>
          <PlanTimeline :steps="planSteps" />
        </section>

        <!-- 提案计数：徽标点击定位审核区（交互 2.2） -->
        <section v-if="proposalCounts.length > 0" class="task-section task-proposals">
          <span class="task-proposals__label">提案计数</span>
          <el-tag
            v-for="item in proposalCounts"
            :key="item.kind"
            size="small"
            effect="plain"
            class="task-proposals__badge"
            :class="{ 'task-proposals__badge--clickable': showReviewArea }"
            @click="focusReviewArea"
          >
            {{ item.label }} {{ item.count }}
          </el-tag>
        </section>

        <!-- 轨迹区：游标增量拉取，实时通道接入前以展开刷新兜底（交互 2.2） -->
        <section v-if="execution" class="task-section">
          <TraceEventList :task-id="taskId" :execution-id="execution.executionId" />
        </section>

        <!-- 审核区状态分支（交互 2.2 / 2.4）：仅 proposals_ready 渲染，aborted / closed 不渲染 -->
        <section
          v-if="showReviewArea || showRunningPlaceholder || showEmptyProposals"
          id="task-review"
          class="task-section"
          :class="{ 'task-section--focus': focusReview }"
        >
          <template v-if="showReviewArea">
            <!-- 生成链走三层树审核，其余任务沿用通用审核面板 -->
            <GenerationArtifactTreeReview
              v-if="detail.type === 'test_design_generation'"
              :task-id="detail.taskId"
              :proposals="proposals"
              :can-confirm="canConfirmGeneration"
              @confirmed="load"
              @leave="backToCenter"
            />
            <!-- 圈选任务（评审 / 计划）走圈选审核面板（交互 2.4） -->
            <SelectionReviewPanel
              v-else-if="isSelectionTask"
              :task-id="detail.taskId"
              :task-type="selectionTaskType"
              :proposals="proposals"
              :can-confirm="canConfirmSelection"
              @confirmed="load"
              @leave="backToCenter"
            />
            <!-- 缺陷批量分类与存量重复扫描走缺陷审核面板（详设 3.6 / 3.9） -->
            <BugClassifyReviewPanel
              v-else-if="isBugReviewTask"
              :task-id="detail.taskId"
              :task-type="detail.type"
              :proposals="proposals"
              :can-confirm="canConfirmBug"
              @confirmed="load"
              @leave="backToCenter"
            />
            <AiProposalReviewPanel
              v-else
              :task-id="detail.taskId"
              :task-type="detail.type"
              :proposals="proposals"
              :can-confirm="canConfirm"
              :document-meta="detail.documentMeta ?? null"
              @confirmed="load"
              @leave="backToCenter"
            />
          </template>
          <el-empty
            v-else-if="showRunningPlaceholder"
            description="执行进行中，完成后在此审核"
            :image-size="80"
          />
          <el-empty
            v-else
            description="任务完成，但没有需要审核的提案"
            :image-size="80"
          >
            <el-button @click="backToCenter">返回任务中心</el-button>
          </el-empty>
        </section>
      </template>
    </template>
  </main>
</template>

<style scoped lang="scss">
.task-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-md);
  font-size: var(--font-size-sm);
}

.task-breadcrumb__sep {
  color: var(--color-neutral-400);
}

.task-breadcrumb__current {
  color: var(--color-neutral-500);
}

.detail-skeleton,
.detail-error {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.task-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.task-head__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.task-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.task-head__meta {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.task-head__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.task-section {
  margin-bottom: var(--block-gap);
}

.task-section__title {
  margin: 0 0 var(--space-sm);
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.task-proposals {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.task-proposals__label {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.task-proposals__badge {
  cursor: default;
}

.task-proposals__badge--clickable {
  cursor: pointer;
}
</style>
