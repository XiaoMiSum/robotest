<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AiTaskProposalSummary } from '@/types'
import {
  aiProposalConfirmMeta,
  aiProposalKindLabel,
  aiTaskAdoptionProposals,
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

const base = useAiProposalReview(
  props.taskId,
  () => aiTaskAdoptionProposals(props.proposals),
  () => false,
  () => {},
  () => props.taskType,
)

base.syncRows()
// 详情刷新（再次执行等）后重建回看卡片，展开态按仍存在的 key 保留
watch(
  () => props.proposals,
  () => base.syncRows(),
)

const rows = computed(() => base.rows.value)
const activeKeys = ref<string[]>([])
const loadingKeys = ref<string[]>([])
const failedKeys = ref<string[]>([])

/** 首次展开才拉明细，收起后重展复用已加载内容 */
async function loadDetail(key: string): Promise<void> {
  const row = rows.value.find((item) => item.key === key)
  if (!row || row.contentLoaded || loadingKeys.value.includes(key)) return
  failedKeys.value = failedKeys.value.filter((item) => item !== key)
  loadingKeys.value = [...loadingKeys.value, key]
  await base.loadContent(row)
  loadingKeys.value = loadingKeys.value.filter((item) => item !== key)
  if (!row.contentLoaded) failedKeys.value = [...failedKeys.value, key]
}

watch(activeKeys, (keys, previous) => {
  for (const key of keys) {
    if (!previous?.includes(key)) void loadDetail(key)
  }
})
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
    <el-collapse v-else v-model="activeKeys" class="adoption-result__list">
      <el-collapse-item v-for="row in rows" :key="row.key" :name="row.key">
        <template #title>
          <div class="adoption-result__head-row">
            <span class="adoption-result__kind">{{ aiProposalKindLabel(row.kind) }}</span>
            <span class="adoption-result__name" :title="row.title ?? row.key">
              {{ row.title ?? row.key }}
            </span>
            <el-tag
              :type="aiProposalConfirmMeta(row.confirmStatus).tagType"
              size="small"
              effect="plain"
            >
              {{ aiProposalConfirmMeta(row.confirmStatus).label }}
            </el-tag>
          </div>
          <span v-if="row.note" class="adoption-result__note">
            {{ row.confirmStatus === 'rejected' ? '驳回原因' : '备注' }}：{{ row.note }}
          </span>
        </template>
        <div v-loading="loadingKeys.includes(row.key)" class="adoption-result__detail">
          <template v-if="failedKeys.includes(row.key)">
            <span class="adoption-result__error">产物明细加载失败</span>
            <el-button link type="primary" @click="loadDetail(row.key)">重试</el-button>
          </template>
          <AiProposalDetailView v-else-if="row.contentLoaded" :row="row" :task-type="taskType" />
        </div>
      </el-collapse-item>
    </el-collapse>
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

.adoption-result__head-row {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
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

.adoption-result__note {
  margin-left: auto;
}

.adoption-result__detail {
  min-height: 24px;
}

.adoption-result__error {
  margin-right: var(--space-sm);
  color: var(--color-danger);
  font-size: var(--font-size-sm);
}
</style>
