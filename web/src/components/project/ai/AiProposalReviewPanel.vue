<script setup lang="ts">
import { computed, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { AiTaskDocumentMeta, AiTaskProposalSummary } from '@/types'
import ProposalCompareView from '@/components/project/ai/ProposalCompareView.vue'
import { useAiProposalReview } from '@/composables/project/ai/useAiProposalReview'
import {
  importVersionEvidence,
  importVersionHint,
  importVersionPrefill,
} from '@/composables/project/ai/taskPresentation'

const props = defineProps<{
  taskId: string
  taskType: string
  proposals: AiTaskProposalSummary[]
  /** 导入任务的文档级识别版本与依据（详设 3.6.3），其余任务为 null */
  documentMeta?: AiTaskDocumentMeta | null
  /** 只读模式：仅展示提案产物与审核结果，不提供审核操作 */
  readonly?: boolean
}>()

const emit = defineEmits<{ confirmed: []; leave: [] }>()

const {
  rows,
  activeRow,
  activeIndex,
  canGoPrev,
  canGoNext,
  movePrev,
  moveNext,
  moduleTree,
  processed,
  reviewTitle,
  needSystemVersion,
  syncRows,
  init,
  loadContent,
} = useAiProposalReview(
  props.taskId,
  () => props.proposals,
  () => !props.readonly,
  () => {
    syncRows()
    emit('confirmed')
  },
  () => props.taskType,
)

init()

const HANDLED_CONFIRM_STATUSES = new Set(['adopted', 'adopted_edited', 'rejected'])

const handledHint = computed(() => {
  if (props.readonly) return ''
  if (rows.value.length === 0) return ''
  if (!rows.value.every((row) => HANDLED_CONFIRM_STATUSES.has(row.confirmStatus))) return ''
  return rows.value.every((row) => row.confirmStatus === 'rejected')
    ? '已全部驳回，未创建任何数据'
    : '待处理提案已全部审核完成'
})

async function preloadVisible(): Promise<void> {
  if (activeRow.value) await loadContent(activeRow.value)
}
void preloadVisible()

watch(
  () => props.proposals,
  () => {
    syncRows()
    void preloadVisible()
  },
)

const versionHint = computed(() =>
  importVersionHint({
    taskType: props.taskType,
    documentMeta: props.documentMeta ?? null,
    touched: false,
    value: importVersionPrefill(props.documentMeta ?? null),
  }),
)

const versionEvidence = computed(() => importVersionEvidence(props.documentMeta ?? null))
const versionDisplay = computed(() => importVersionPrefill(props.documentMeta ?? null))

const moduleNameMap = computed(() => {
  const map = new Map<string, string>()
  const walk = (nodes: typeof moduleTree.value): void => {
    for (const node of nodes) {
      map.set(node.id, node.name)
      if (node.children) walk(node.children)
    }
  }
  walk(moduleTree.value)
  return map
})

function moduleNameOf(moduleId: string): string {
  return moduleNameMap.value.get(moduleId) ?? ''
}
</script>

<template>
  <section class="proposal-review">
    <header class="proposal-review__head">
      <div class="proposal-review__head-main">
        <h2 class="proposal-review__title">{{ reviewTitle }}</h2>
        <span class="proposal-review__processed">
          已处理 {{ processed.processed }} / 共 {{ processed.total }}
        </span>
      </div>
      <div v-if="needSystemVersion && !readonly" class="proposal-review__version">
        <span class="proposal-review__version-label">版本</span>
        <span class="proposal-review__evidence">{{ versionDisplay || '留空按规则回填' }}</span>
        <el-tooltip v-if="versionEvidence" :content="versionEvidence" placement="top">
          <span class="proposal-review__evidence">{{ versionEvidence }}</span>
        </el-tooltip>
        <el-tooltip :content="versionHint" placement="top">
          <el-icon class="proposal-review__version-info"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <div v-else-if="needSystemVersion && readonly" class="proposal-review__version">
        <span class="proposal-review__version-label">版本</span>
        <span class="proposal-review__evidence">{{ versionDisplay || '未识别' }}</span>
      </div>
    </header>

    <el-alert
      v-if="!readonly && taskType === 'requirement_split'"
      type="info"
      title="新条目将继承原条目版本、原条目将归档"
      show-icon
      :closable="false"
      class="proposal-review__hint"
    />

    <el-alert
      v-if="handledHint && !readonly"
      type="info"
      :title="handledHint"
      show-icon
      :closable="false"
      class="proposal-review__hint"
    >
      <template #default>
        <el-button link type="primary" @click="emit('leave')">返回任务中心</el-button>
      </template>
    </el-alert>

    <div v-if="rows.length === 0" class="proposal-review__empty">
      <el-empty description="提案为空（执行未产出建议）" :image-size="80">
        <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
      </el-empty>
    </div>

    <div v-else class="proposal-review__items">
      <div v-if="rows.length >= 2" class="proposal-review__nav">
        <el-button link :disabled="!canGoPrev" @click="movePrev">上一个</el-button>
        <span class="proposal-review__nav-pos">第 {{ activeIndex + 1 }} / {{ rows.length }} 条</span>
        <el-button link :disabled="!canGoNext" @click="moveNext">下一个</el-button>
      </div>
      <div v-for="row in activeRow ? [activeRow] : []" :key="row.key" class="proposal-review__item">
        <div class="proposal-review__item-head">
          <span class="proposal-review__kind">{{ row.kindLabel }}</span>
          <span class="proposal-review__name" :title="row.title ?? row.key">
            {{ row.title || row.key }}
          </span>
          <el-tag :type="row.confirmMeta.tagType" size="small" effect="plain">
            {{ row.confirmMeta.label }}
          </el-tag>
        </div>

        <div v-loading="!row.contentLoaded && !row.contentError" class="proposal-review__item-body">
          <el-alert
            v-if="row.contentError"
            type="error"
            :title="row.contentError"
            show-icon
            :closable="false"
            class="proposal-review__alert"
          >
            <template #default>
              <el-button type="danger" plain size="small" @click="loadContent(row)">重试</el-button>
            </template>
          </el-alert>
          <p v-if="row.note" class="proposal-review__note">驳回原因：{{ row.note }}</p>
          <span v-if="moduleNameOf(row.content.moduleId)" class="proposal-review__meta">
            模块：{{ moduleNameOf(row.content.moduleId) }}
          </span>
          <span v-if="row.content.sourceRef" class="proposal-review__meta">
            来源：{{ row.content.sourceRef }}
          </span>
          <el-alert
            v-for="(warning, index) in row.parsed.warnings"
            :key="index"
            type="warning"
            :title="warning"
            show-icon
            :closable="false"
            class="proposal-review__alert"
          />

          <p
            v-if="row.contentLoaded && !row.parsed.description && !row.content.description && row.parsed.fields.length === 0 && row.parsed.compare.length === 0 && row.parsed.rankItems.length === 0 && row.parsed.sourceRefs.length === 0 && row.parsed.warnings.length === 0 && !row.content.moduleId && !row.content.sourceRef && !row.content.priority"
            class="proposal-review__empty-detail"
          >暂无明细</p>

          <p
            v-if="row.parsed.description || row.content.description"
            class="proposal-review__desc-text"
          >
            {{ row.parsed.description || row.content.description }}
          </p>

          <ProposalCompareView :items="row.parsed.compare" />

          <dl v-if="row.parsed.fields.length > 0" class="proposal-review__fields">
            <div
              v-for="(item, index) in row.parsed.fields"
              :key="`${item.label}-${index}`"
              class="proposal-review__field"
            >
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
            <div v-if="row.content.priority" class="proposal-review__field">
              <dt>优先级</dt>
              <dd>{{ row.content.priority }}</dd>
            </div>
          </dl>

          <ol v-if="row.parsed.rankItems.length > 0" class="proposal-review__rank">
            <li v-for="item in row.parsed.rankItems" :key="item.nodeId">
              <span>{{ item.rank }}. {{ item.title }}</span>
              <span v-if="item.reason" class="proposal-review__rank-reason">{{ item.reason }}</span>
            </li>
          </ol>

          <div v-if="row.parsed.sourceRefs.length > 0" class="proposal-review__sources">
            <div
              v-for="(sourceRef, index) in row.parsed.sourceRefs"
              :key="`${sourceRef.id}-${index}`"
            >
              <span class="proposal-review__source-title">{{
                sourceRef.title || sourceRef.id
              }}</span>
              <span class="proposal-review__source-quote">{{ sourceRef.quote }}</span>
            </div>
          </div>
        </div>

      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.proposal-review {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.proposal-review__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.proposal-review__head-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.proposal-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.proposal-review__processed,
.proposal-review__kind,
.proposal-review__meta {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__version {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.proposal-review__version-label,
.proposal-review__evidence {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__evidence {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proposal-review__version-info {
  color: var(--color-neutral-500);
}

.proposal-review__hint,
.proposal-review__empty,
.proposal-review__alert {
  margin-bottom: var(--space-sm);
}

.proposal-review__empty-detail {
  margin: 0 0 var(--space-sm);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  padding-bottom: var(--space-sm);
}

.proposal-review__nav-pos {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__item {
  border-top: 1px solid var(--color-neutral-200);
}

.proposal-review__note {
  margin: 0 0 var(--space-sm);
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}

.proposal-review__item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
  margin: var(--space-sm) 0;
}

.proposal-review__name {
  flex: 1;
  min-width: 160px;
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proposal-review__item-body {
  min-height: 72px;
}

.proposal-review__meta {
  display: inline-block;
  margin-right: var(--space-sm);
}

.proposal-review__desc-text {
  margin: var(--space-sm) 0;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.proposal-review__fields {
  margin: 0 0 var(--space-sm);
}

.proposal-review__field {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;

  dt {
    flex: 0 0 96px;
    color: var(--color-neutral-500);
  }

  dd {
    flex: 1;
    margin: 0;
  }
}

.proposal-review__rank {
  margin: 0 0 var(--space-sm);
  padding-left: var(--space-md);

  li {
    margin-bottom: var(--space-xs);
  }
}

.proposal-review__rank-reason {
  display: block;
  color: var(--color-neutral-500);
}

.proposal-review__sources {
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.proposal-review__source-title {
  margin-right: var(--space-xs);
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
}

.proposal-review__source-quote {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.proposal-review__rank-edit {
  margin: 0;
  padding: 0;
  list-style: none;
}

.proposal-review__rank-edit-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  margin-bottom: var(--space-xs);
  background: var(--color-neutral-100);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm);
  cursor: grab;

  &--dragging {
    opacity: 0.5;
  }

  &--over {
    border-color: var(--color-primary-500);
  }
}

.proposal-review__rank-edit-num {
  color: var(--color-primary-500);
  font-weight: 600;
}

.proposal-review__rank-edit-title {
  flex: 1;
}

.proposal-review__rank-edit-handle {
  color: var(--color-neutral-500);
}
</style>
