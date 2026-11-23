<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AiTaskProposalSummary } from '@/types'
import {
  aiProposalConfirmMeta,
  aiProposalKindLabel,
  type AiTaskAdoptionResultMeta,
} from '@/composables/project/ai/taskPresentation'
import { useAiProposalReview } from '@/composables/project/ai/useAiProposalReview'
import AiProposalDetailView from './AiProposalDetailView.vue'

const props = defineProps<{
  taskId: string
  taskType: string
  /** 最新一次执行的提案清单摘要 */
  proposals: AiTaskProposalSummary[]
  /** 任务级采纳结果（按提案确认状态聚合） */
  result: AiTaskAdoptionResultMeta | null
}>()

const review = useAiProposalReview(
  props.taskId,
  () => props.proposals ?? [],
  () => false,
  () => {},
  () => props.taskType,
)

review.init()

const pendingKeys = ref(new Set<string>())

async function preloadActive(key?: string): Promise<void> {
  const row = review.activeRow.value
  if (!row || row.contentLoaded || pendingKeys.value.has(row.key)) return
  if (key !== undefined && row.key !== key) return
  pendingKeys.value.add(row.key)
  try {
    await review.loadContent(row)
  } finally {
    pendingKeys.value.delete(row.key)
  }
}

watch(
  () => props.proposals,
  () => {
    review.syncRows()
  },
  { immediate: true },
)

watch(
  () => review.activeRow.value?.key,
  (key) => {
    void preloadActive(key)
  },
  { immediate: true },
)

const rows = computed(() => review.rows.value)
const activeRow = computed(() => review.activeRow.value)
const activeIndex = computed(() => review.activeIndex.value)
</script>

<template>
  <section class="proposal-review proposal-review--readonly">
    <header class="proposal-review__head">
      <div class="proposal-review__head-main">
        <h2 class="proposal-review__title">提案与审核结果</h2>
        <span class="proposal-review__processed">
          共 {{ rows.length }} 条
        </span>
        <el-tag v-if="result" :type="result.tagType" size="small" effect="light">
          {{ result.label }}
        </el-tag>
      </div>
    </header>

    <p v-if="rows.length === 0" class="proposal-review__empty">本次执行没有提案记录</p>

    <div v-else class="proposal-review__items">
      <div v-if="rows.length >= 2" class="proposal-review__nav">
        <el-button link :disabled="!review.canGoPrev.value" @click="review.movePrev()">上一个</el-button>
        <span class="proposal-review__nav-pos">第 {{ activeIndex + 1 }} / {{ rows.length }} 条</span>
        <el-button link :disabled="!review.canGoNext.value" @click="review.moveNext()">下一个</el-button>
      </div>

      <article v-if="activeRow" :key="activeRow.key" class="proposal-review__item">
        <div class="proposal-review__item-head">
          <span class="proposal-review__kind">{{ aiProposalKindLabel(activeRow.kind) }}</span>
          <span class="proposal-review__name" :title="activeRow.title ?? activeRow.key">
            {{ activeRow.title || activeRow.key }}
          </span>
          <el-tag
            :type="aiProposalConfirmMeta(activeRow.confirmStatus).tagType"
            size="small"
            effect="plain"
          >
            {{ aiProposalConfirmMeta(activeRow.confirmStatus).label }}
          </el-tag>
        </div>

        <div
          v-loading="!activeRow.contentLoaded && !activeRow.contentError"
          class="proposal-review__item-body"
        >
          <el-alert
            v-if="activeRow.contentError"
            type="error"
            :title="activeRow.contentError"
            show-icon
            :closable="false"
            class="proposal-review__alert"
          >
            <template #default>
              <el-button link type="primary" @click="review.loadContent(activeRow)">重试</el-button>
            </template>
          </el-alert>

          <p v-if="activeRow.note" class="proposal-review__note">
            {{ activeRow.confirmStatus === 'rejected' ? '驳回原因' : '备注' }}：{{ activeRow.note }}
          </p>

          <AiProposalDetailView
            v-if="activeRow.contentLoaded"
            :row="activeRow"
            :task-type="taskType"
          />
        </div>
      </article>
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
  flex-wrap: wrap;
  align-items: center;
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
.proposal-review__nav-pos,
.proposal-review__note {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__empty {
  margin: 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__nav {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.proposal-review__item {
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm);
}

.proposal-review__item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-neutral-200);
  background: var(--color-neutral-50);
}

.proposal-review__name {
  flex: 1;
  min-width: 160px;
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proposal-review__item-body {
  min-height: 72px;
  padding: var(--space-md);
}

.proposal-review__alert,
.proposal-review__note {
  margin: 0 0 var(--space-sm);
}
</style>
