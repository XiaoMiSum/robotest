<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import RequirementImportReviewPanel from '@/components/project/requirement/RequirementImportReviewPanel.vue'
import ExecutionPanel from '@/components/project/ai/ExecutionPanel.vue'
import { useAiTaskDetail } from '@/composables/project/ai/useAiTaskDetail'

const REQUIREMENT_LIST_ROUTE = '/workspace/projects/functional-testing?tab=requirements'

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
  proposals,
  adoptionResult,
  terminateText,
  canAbort,
  canReExecute,
  load,
  handleAbort,
  handleReExecute,
} = useAiTaskDetail()

const documentMeta = computed(() => detail.value?.documentMeta ?? null)
const showReviewPanel = computed(
  () => detail.value?.status === 'proposals_ready' && proposals.value.length > 0,
)
const showRunningPlaceholder = computed(() => detail.value?.status === 'running')
const showEmptyProposals = computed(
  () => detail.value?.status === 'proposals_ready' && proposals.value.length === 0,
)

function backToList(): void {
  void router.push(REQUIREMENT_LIST_ROUTE)
}
</script>

<template>
  <main class="import-review">
    <nav class="import-review__breadcrumb" aria-label="面包屑">
      <el-link type="primary" :underline="false" @click="backToList">需求列表</el-link>
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
        <el-button type="primary" @click="backToList">返回需求列表</el-button>
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
        <el-button size="small" type="danger" plain @click="load">重试</el-button>
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
            <el-button v-if="canAbort" type="warning" plain @click="handleAbort">终止</el-button>
            <el-button v-if="canReExecute" type="primary" @click="handleReExecute">再次执行</el-button>
            <el-button @click="backToList">返回需求列表</el-button>
          </div>
        </header>

        <section v-if="execution" class="import-review__section">
          <ExecutionPanel
            :execution="execution"
            :task-status="detail.status"
            :terminate-text="terminateText"
          />
        </section>

        <RequirementImportReviewPanel
          v-if="showReviewPanel"
          :task-id="detail.taskId"
          task-type="requirement_import"
          :proposals="proposals"
          :document-meta="documentMeta"
          @confirmed="load"
          @leave="backToList"
        />

        <el-empty
          v-else-if="showRunningPlaceholder"
          description="正在解析并拆分导入文档，完成后在此审核"
          :image-size="80"
        />

        <el-empty
          v-else-if="showEmptyProposals"
          description="任务已完成，但没有需要审核的导入建议"
          :image-size="80"
        >
          <el-button @click="backToList">返回需求列表</el-button>
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
</style>
