<script setup lang="ts">
import { nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { formatDateTime } from '@/utils/format'
import AiArtifactReviewPanel from '@/components/project/ai/AiArtifactReviewPanel.vue'
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
  phaseSteps,
  artifactCounts,
  artifacts,
  isPendingOrRunning,
  isFailed,
  showReviewArea,
  showEmptyArtifacts,
  canManageTask,
  canConfirm,
  load,
  handleCancel,
  handleRetry,
  backToCenter,
} = useAiTaskDetail()

/** 从列表「审核」进入时定位审核区（交互 2.1.2）；审核区在 succeeded 后才渲染，待其出现再定位 */
const focusReview = route.query.review === '1'

watch(
  () => detail.value?.status,
  (status) => {
    if (focusReview && status === 'succeeded') {
      void nextTick(() => {
        document
          .getElementById('task-review')
          ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    }
  },
  { immediate: true },
)
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
              <template v-if="detail.retryOfTaskId"> · 重试任务</template>
            </p>
          </div>
          <div class="task-head__actions">
            <el-button
              v-if="canManageTask && isPendingOrRunning"
              type="warning"
              plain
              @click="handleCancel"
            >取消</el-button>
            <el-button v-if="canManageTask && isFailed" type="primary" @click="handleRetry">
              重试
            </el-button>
          </div>
        </header>

        <!-- 进度区：2 秒轮询，终态停止（交互 2.2） -->
        <section class="task-section">
          <div class="task-progress">
            <div class="task-progress__row">
              <el-progress
                :percentage="detail.progress ?? 0"
                :stroke-width="10"
                class="task-progress__bar"
              />
              <span class="task-progress__percent">{{ detail.progress ?? 0 }}%</span>
            </div>
            <p class="task-progress__phase">
              当前阶段：{{ detail.phase || (isPendingOrRunning ? '等待执行' : '—') }}
            </p>
            <p v-if="isFailed && detail.error" class="task-progress__error">
              失败原因：{{ detail.error.msg || '未知错误' }}
              <template v-if="detail.error.code">（错误码 {{ detail.error.code }}）</template>
            </p>
          </div>

          <!-- 阶段时间线：由任务 phase 驱动，未知阶段序列时降级为纯文本 -->
          <ol v-if="phaseSteps.length > 0" class="task-timeline">
            <li
              v-for="step in phaseSteps"
              :key="step.label"
              class="task-timeline__item"
              :class="`task-timeline__item--${step.state}`"
            >
              <span class="task-timeline__dot" aria-hidden="true">
                <el-icon v-if="step.state === 'done'"><Check /></el-icon>
                <el-icon v-else-if="step.state === 'failed'"><Close /></el-icon>
              </span>
              <span class="task-timeline__label">{{ step.label }}</span>
            </li>
          </ol>
        </section>

        <!-- 产物计数：徽标点击定位审核区（交互 2.2） -->
        <section v-if="artifactCounts.length > 0" class="task-section task-artifacts">
          <span class="task-artifacts__label">产物计数</span>
          <el-tag
            v-for="item in artifactCounts"
            :key="item.kind"
            size="small"
            effect="plain"
            class="task-artifacts__badge"
          >
            {{ item.label }} {{ item.count }}
          </el-tag>
        </section>

        <!-- 审核区状态分支（交互 2.2 / 2.4） -->
        <section
          id="task-review"
          class="task-section"
          :class="{ 'task-section--focus': focusReview }"
        >
          <template v-if="showReviewArea">
            <AiArtifactReviewPanel
              :task-id="detail.taskId"
              :task-type="detail.type"
              :artifacts="artifacts"
              :can-confirm="canConfirm"
              @confirmed="load"
              @leave="backToCenter"
            />
          </template>
          <el-empty
            v-else-if="isPendingOrRunning"
            description="任务进行中，完成后在此审核"
            :image-size="80"
          />
          <el-empty
            v-else-if="showEmptyArtifacts"
            description="任务已完成，但没有需要审核的产物"
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

.task-progress {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.task-progress__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.task-progress__bar {
  flex: 1;
}

.task-progress__percent {
  flex-shrink: 0;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.task-progress__phase {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.task-progress__error {
  margin: var(--space-sm) 0 0;
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}

// 阶段时间线：完成 success、失败 danger、当前 primary、待执行中性灰（视觉 4.1）
.task-timeline {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  margin: var(--space-md) 0 0;
  padding: 0;
  list-style: none;
}

.task-timeline__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.task-timeline__dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 2px solid currentcolor;
  border-radius: 50%;
}

.task-timeline__item--done {
  color: var(--color-success);

  .task-timeline__dot {
    background: var(--color-success);
    border-color: var(--color-success);
    color: var(--color-neutral-0);
  }
}

.task-timeline__item--current {
  color: var(--color-primary-500);

  .task-timeline__dot {
    background: var(--color-primary-500);
    border-color: var(--color-primary-500);
  }
}

.task-timeline__item--failed {
  color: var(--color-danger);

  .task-timeline__dot {
    background: var(--color-danger);
    border-color: var(--color-danger);
    color: var(--color-neutral-0);
  }
}

.task-artifacts {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.task-artifacts__label {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.task-artifacts__badge {
  cursor: default;
}
</style>
