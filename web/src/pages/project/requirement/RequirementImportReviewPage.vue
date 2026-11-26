<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatDateTime } from '@/utils/format'
import RequirementImportReviewPanel from '@/components/project/requirement/RequirementImportReviewPanel.vue'
import ExecutionPanel from '@/components/project/ai/ExecutionPanel.vue'
import { useAiTaskDetail } from '@/composables/project/ai/useAiTaskDetail'
import { aiExecutionStatusMeta } from '@/composables/project/ai/taskPresentation'

const REQUIREMENT_LIST_ROUTE = '/workspace/projects/functional-testing?tab=requirements'
const REQUIREMENT_HISTORY_ROUTE = '/workspace/projects/requirements/import-history'

const router = useRouter()
const {
  loading,
  loadError,
  notFound,
  detail,
  aiAvailable,
  statusMeta,
  typeMeta,
  name,
  execution,
  executions,
  selectedExecutionId,
  switchExecution,
  proposals,
  adoptionResult,
  terminateText,
  canAbort,
  canReExecute,
  reload,
  handleAbort,
  handleReExecute,
} = useAiTaskDetail()

const documentMeta = computed(() => detail.value?.documentMeta ?? null)
/** 旧执行回看时任务可能已是 closed，审核区按「当前执行有提案」判定而非任务态 */
const showReviewPanel = computed(() => proposals.value.length > 0)
const showRunningPlaceholder = computed(() => detail.value?.status === 'running')
const showEmptyProposals = computed(
  () => !showReviewPanel.value && !showRunningPlaceholder.value,
)
const emptyProposalsText = computed(() => {
  if (detail.value?.status === 'aborted') return '任务已终止，未生成可审核的导入建议'
  if (selectedExecutionId.value) return '该执行批次未生成可审核的导入建议'
  return '任务已完成，但没有需要审核的导入建议'
})
const showExecutionSwitcher = computed(() => executions.value.length > 1)
const selectedExecution = computed(
  () => selectedExecutionId.value ?? executions.value[0]?.executionId ?? '',
)

function executionLabel(item: (typeof executions.value)[number], index: number): string {
  const seq = item.seq == null ? '—' : item.seq
  const time = item.createdAt ? ` · ${formatDateTime(item.createdAt)}` : ''
  const latest = index === 0 ? '（最新）' : ''
  return `第 ${seq} 次执行${time} · ${aiExecutionStatusMeta(item.status).label}${latest}`
}

function onExecutionChange(value: unknown): void {
  if (typeof value === 'string' && value) void switchExecution(value)
}

function backToList(): void {
  void router.push(REQUIREMENT_LIST_ROUTE)
}

function backToHistory(): void {
  void router.push(REQUIREMENT_HISTORY_ROUTE)
}
</script>

<template>
  <main class="import-review">
    <nav class="import-review__breadcrumb" aria-label="面包屑">
      <router-link :to="REQUIREMENT_LIST_ROUTE">需求列表</router-link>
      <span aria-hidden="true">›</span>
      <span>导入结果审核</span>
    </nav>

    <el-result
      v-if="notFound"
      status="404"
      title="导入任务不存在或无权访问"
      sub-title="任务可能已被删除，或当前账号无权查看"
    >
      <template #extra>
        <router-link :to="REQUIREMENT_LIST_ROUTE">返回需求列表</router-link>
      </template>
    </el-result>

    <el-skeleton
      v-else-if="!detail && !loadError && loading"
      :rows="8"
      animated
      class="import-review__skeleton"
    />

    <el-alert
      v-else-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="import-review__error"
    >
      <template #default>
        <el-button type="danger" plain size="small" @click="reload">重试</el-button>
      </template>
    </el-alert>

    <template v-else-if="detail">
      <el-result
        v-if="!aiAvailable"
        status="warning"
        title="AI 能力未启用 / 未配置模型"
        sub-title="开启 AI 总开关并配置可用模型后可继续审核导入结果"
      />

      <template v-else>
        <header class="import-review__head">
          <div>
            <div class="import-review__title-row">
              <h1 class="import-review__title">{{ name }}</h1>
              <el-tag :type="statusMeta.tagType" size="small" effect="light">
                {{ statusMeta.label }}
              </el-tag>
              <el-tag v-if="adoptionResult" :type="adoptionResult.tagType" size="small" effect="plain">
                {{ adoptionResult.label }}
              </el-tag>
            </div>
            <p class="import-review__meta">
              类型 {{ typeMeta.label }} · 任务 ID {{ detail.taskId }}
            </p>
          </div>
          <div class="import-review__actions">
            <el-button v-if="canAbort" link type="danger" @click="handleAbort">终止</el-button>
            <el-button v-if="canReExecute" link type="primary" @click="handleReExecute">重新执行</el-button>
            <el-button link type="primary" @click="backToHistory">返回导入历史</el-button>
            <el-button link type="primary" @click="backToList">返回需求列表</el-button>
          </div>
        </header>

        <section v-if="execution" class="import-review__section">
          <ExecutionPanel
            :execution="execution"
            :task-status="detail.status"
            :terminate-text="terminateText"
          />
          <div v-if="showExecutionSwitcher" class="import-review__execution-switch">
            <span class="import-review__execution-label">执行批次</span>
            <el-select
              :model-value="selectedExecution"
              placeholder="选择执行批次"
              @change="onExecutionChange"
            >
              <el-option
                v-for="(item, index) in executions"
                :key="item.executionId"
                :value="item.executionId"
                :label="executionLabel(item, index)"
              />
            </el-select>
            <span class="import-review__execution-hint">
              {{ selectedExecutionId ? '当前查看旧执行的提案，可继续审核，采纳记录按该执行独立保存' : '当前为最新执行的提案，可逐条审核' }}
            </span>
          </div>
        </section>

        <RequirementImportReviewPanel
          v-if="showReviewPanel"
          :task-id="detail.taskId"
          task-type="requirement_import"
          :proposals="proposals"
          :execution-id="selectedExecutionId"
          :document-meta="documentMeta"
          @confirmed="reload"
          @leave="backToList"
        />

        <el-empty
          v-else-if="showRunningPlaceholder"
          description="正在解析并拆分导入文档，完成后在此审核"
          :image-size="80"
        />

        <el-empty
          v-else-if="showEmptyProposals"
          :description="emptyProposalsText"
          :image-size="80"
        >
          <router-link :to="REQUIREMENT_LIST_ROUTE">返回需求列表</router-link>
        </el-empty>
      </template>
    </template>
  </main>
</template>

<style scoped lang="scss">
.import-review {
  padding: var(--space-md);
}

.import-review__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-md);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);

  a {
    color: var(--color-primary-500);
    text-decoration: none;
  }
}

.el-result__extra a,
.el-empty__bottom a {
  color: var(--color-primary-500);
  text-decoration: none;
}

.import-review__skeleton,
.import-review__error {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.import-review__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.import-review__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.import-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
}

.import-review__meta {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.import-review__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.import-review__section {
  margin-bottom: var(--block-gap);
}

.import-review__execution-switch {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.import-review__execution-label,
.import-review__execution-hint {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}
</style>
