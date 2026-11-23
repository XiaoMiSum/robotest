<script setup lang="ts">
import { formatDateTime } from '@/utils/format'
import AiTaskAdoptionResultPanel from '@/components/project/ai/AiTaskAdoptionResultPanel.vue'
import ExecutionPanel from '@/components/project/ai/ExecutionPanel.vue'
import PlanTimeline from '@/components/project/ai/PlanTimeline.vue'
import TraceEventList from '@/components/project/ai/TraceEventList.vue'
import { useAiTaskDetail } from '@/composables/project/ai/useAiTaskDetail'

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
  proposals,
  adoptionResult,
  showProposalPanel,
  showEmptyProposals,
  terminateText,
  canAbort,
  canReExecute,
  taskId,
  load,
  handleAbort,
  handleReExecute,
  backToCenter,
} = useAiTaskDetail()
</script>

<template>
  <main class="ai-task-detail">
    <!-- 面包屑：内容区顶部回退入口（UI-BC-01） -->
    <nav class="task-breadcrumb" aria-label="面包屑">
      <el-link type="primary" :underline="false" @click="backToCenter">AI 任务中心</el-link>
      <span class="task-breadcrumb__sep" aria-hidden="true">›</span>
      <span class="task-breadcrumb__current" :title="name">{{ name || '任务详情' }}</span>
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
              <h1 class="task-head__title" :title="name">{{ name }}</h1>
              <el-tag :type="statusMeta.tagType" size="small" effect="light">
                {{ statusMeta.label }}
              </el-tag>
              <el-tag v-if="adoptionResult" :type="adoptionResult.tagType" size="small" effect="plain">
                {{ adoptionResult.label }}
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
              v-if="canAbort"
              type="warning"
              plain
              @click="handleAbort"
            >终止</el-button>
            <el-button
              v-if="canReExecute"
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

        <!-- 轨迹区：游标增量拉取，实时通道接入前以展开刷新兜底（交互 2.2） -->
        <section v-if="execution" class="task-section">
          <TraceEventList :task-id="taskId" :execution-id="execution.executionId" />
        </section>

        <!-- 提案与审核结果：只读查看产物与确认状态，不提供审核动作（交互 2.2/2.3） -->
        <section v-if="showProposalPanel" class="task-section">
          <AiTaskAdoptionResultPanel
            :task-id="detail.taskId"
            :task-type="detail.type"
            :proposals="proposals"
            :result="adoptionResult"
          />
        </section>

        <section v-else-if="showEmptyProposals" class="task-section">
          <el-empty
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
</style>
