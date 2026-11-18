<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskProposalSummary } from '@/types'
import {
  aiProposalConfirmMeta,
  aiProposalKindLabel,
  aiTaskAdoptionProposals,
  type AiTaskAdoptionResultMeta,
} from '@/composables/project/ai/taskPresentation'

const props = defineProps<{
  /** 最新一次执行的提案清单摘要 */
  proposals: AiTaskProposalSummary[]
  /** 任务级采纳结果（按提案确认状态聚合） */
  result: AiTaskAdoptionResultMeta | null
}>()

const rows = computed(() => aiTaskAdoptionProposals(props.proposals))
</script>

<template>
  <section class="adoption-result">
    <div class="adoption-result__head">
      <h2 class="adoption-result__title">采纳结果</h2>
      <el-tag v-if="result" :type="result.tagType" size="small" effect="light">
        {{ result.label }}
      </el-tag>
    </div>
    <p v-if="rows.length === 0" class="adoption-result__empty">本次执行没有采纳或驳回记录</p>
    <ul v-else class="adoption-result__list">
      <li v-for="row in rows" :key="row.key" class="adoption-result__row">
        <span class="adoption-result__name" :title="row.title ?? row.key">
          {{ row.title ?? row.key }}
        </span>
        <span class="adoption-result__kind">{{ aiProposalKindLabel(row.kind) }}</span>
        <el-tag :type="aiProposalConfirmMeta(row.confirmStatus).tagType" size="small" effect="plain">
          {{ aiProposalConfirmMeta(row.confirmStatus).label }}
        </el-tag>
        <span v-if="row.note" class="adoption-result__note">
          {{ row.confirmStatus === 'rejected' ? '驳回原因' : '备注' }}：{{ row.note }}
        </span>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.adoption-result {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.adoption-result__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.adoption-result__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.adoption-result__empty {
  margin: 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.adoption-result__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.adoption-result__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--color-neutral-200);

  &:last-child {
    border-bottom: none;
  }
}

.adoption-result__kind,
.adoption-result__note {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.adoption-result__name {
  flex: 1;
  min-width: 160px;
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
