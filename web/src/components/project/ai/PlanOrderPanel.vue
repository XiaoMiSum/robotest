<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Operation as OperationIcon } from '@element-plus/icons-vue'
import { isPlanOrderSuggestion } from '@/composables/project/ai/assistPresentation'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiPlanOrderItem } from '@/types'

const props = withDefaults(
  defineProps<{
    rows: AssistRow[]
    /** 无计划执行权限时置灰并说明原因（详设 4.4 / 交互 2.3） */
    canAdopt: boolean
    confirming: boolean
    loading?: boolean
  }>(),
  { loading: false },
)

const emit = defineEmits<{
  /** 应用建议：按当前（含人工拖拽调整后）顺序提交 adopted_edited */
  adoptEdited: [key: string, content: Record<string, unknown>]
  /** 放弃：关闭面板，不产生任何变更 */
  close: []
}>()

const items = ref<AiPlanOrderItem[]>([])
const beforeOrder = ref<string[]>([])
const suggestedOrder = ref<string[]>([])
const activeKey = ref('')
const readyKey = ref('')
const dragIndex = ref(-1)
const overIndex = ref(-1)

// 拖拽顺序属于面板私有状态：仅在拿到建议内容或换了任务产物时重置，采纳回执不覆盖人工调整
watch(
  () => props.rows,
  (incoming) => {
    const row = incoming.find((item) => item.confirmStatus === 'pending') ?? incoming[0] ?? null
    const view = row?.suggestion ?? null
    if (!row || !isPlanOrderSuggestion(view) || row.key === readyKey.value) return
    readyKey.value = row.key
    activeKey.value = row.key
    items.value = [...view.items].sort((left, right) => left.suggestedRank - right.suggestedRank)
    beforeOrder.value = [...view.beforeOrder]
    suggestedOrder.value = [...view.afterOrder]
  },
  { immediate: true },
)

const activeRow = computed(() => props.rows.find((row) => row.key === activeKey.value) ?? null)
const activeReady = computed(() => isPlanOrderSuggestion(activeRow.value?.suggestion ?? null))
const rowCount = computed(() => items.value.length)
const currentTitles = computed(() =>
  items.value.map((item) => item.caseTitle || item.nodeId),
)
/** 与建议顺序的偏离只作提示，仍允许按调整后的顺序应用 */
const manuallyAdjusted = computed(
  () =>
    currentTitles.value.length > 0 &&
    currentTitles.value.join('\n') !== suggestedOrder.value.join('\n'),
)

const diffRows = computed(() => {
  const total = Math.max(beforeOrder.value.length, currentTitles.value.length)
  return Array.from({ length: total }, (_, index) => {
    const before = beforeOrder.value[index] ?? '—'
    const after = currentTitles.value[index] ?? '—'
    return { index: index + 1, before, after, changed: before !== after }
  })
})
const changedCount = computed(() => diffRows.value.filter((row) => row.changed).length)

const applyDisabledReason = computed(() => {
  if (props.rows.length === 0) return '暂无可处理的建议'
  if (!props.canAdopt) return '当前账号没有计划执行权限'
  if (props.confirming) return '正在提交采纳结果'
  if (!activeReady.value) return '建议内容暂不可用'
  if (activeRow.value?.confirmStatus !== 'pending') return '建议已采纳或已失效'
  if (rowCount.value === 0) return '建议清单为空'
  return ''
})
const canApply = computed(() => applyDisabledReason.value === '')

function onDragStart(index: number): void {
  dragIndex.value = index
}

function onDragOver(index: number): void {
  overIndex.value = index
}

function onDragEnd(): void {
  dragIndex.value = -1
  overIndex.value = -1
}

function onDrop(index: number): void {
  const from = dragIndex.value
  onDragEnd()
  if (from < 0 || from === index || from >= items.value.length) return
  const next = [...items.value]
  const [moved] = next.splice(from, 1)
  if (moved) next.splice(index, 0, moved)
  items.value = next
}

function apply(): void {
  const key = activeKey.value
  if (!key || !canApply.value) return
  emit('adoptEdited', key, {
    items: items.value.map((item, index) => ({
      nodeId: item.nodeId,
      suggestedRank: index + 1,
    })),
  })
}
</script>

<template>
  <section class="plan-order" aria-label="执行顺序建议面板">
    <header class="plan-order__head">
      <h3>执行顺序建议</h3>
      <el-tag v-if="activeRow" size="small" :type="activeRow.confirmTagType">
        {{ activeRow.confirmLabel }}
      </el-tag>
      <el-tag v-if="manuallyAdjusted" size="small" type="warning" effect="plain">
        已人工调整
      </el-tag>
    </header>

    <div v-if="loading && !activeReady" class="plan-order__placeholder">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="!activeReady" class="plan-order__placeholder">
      建议内容暂不可用，请关闭面板后重新发起
    </div>

    <template v-else>
      <ol class="plan-order__list">
        <li
          v-for="(item, index) in items"
          :key="item.nodeId"
          class="plan-order__row"
          :class="{
            'plan-order__row--over': overIndex === index && dragIndex >= 0,
            'plan-order__row--dragging': dragIndex === index,
          }"
          draggable="true"
          @dragstart="onDragStart(index)"
          @dragover.prevent="onDragOver(index)"
          @drop.prevent="onDrop(index)"
          @dragend="onDragEnd"
        >
          <span class="plan-order__rank">{{ index + 1 }}</span>
          <span class="plan-order__title">{{ item.caseTitle || item.nodeId }}</span>
          <el-tooltip
            :content="item.reason ? `理由：${item.reason}` : ''"
            :disabled="!item.reason"
            placement="top"
          >
            <span class="plan-order__reason">{{ item.reason }}</span>
          </el-tooltip>
          <el-icon class="plan-order__handle" aria-label="拖拽调整顺序">
            <OperationIcon />
          </el-icon>
        </li>
      </ol>

      <div class="plan-order__diff">
        <div class="plan-order__diff-head">
          <span>采纳前 vs 采纳后 顺序对照</span>
          <span class="plan-order__diff-count">
            {{ changedCount > 0 ? `${changedCount} 项顺序变化` : '顺序与采纳前一致' }}
          </span>
        </div>
        <div class="plan-order__diff-cols">
          <ul>
            <li
              v-for="row in diffRows"
              :key="`before-${row.index}`"
              :class="{ 'plan-order__diff-item--changed': row.changed }"
            >
              <span class="plan-order__diff-idx">{{ row.index }}</span>{{ row.before }}
            </li>
          </ul>
          <ul>
            <li
              v-for="row in diffRows"
              :key="`after-${row.index}`"
              :class="{ 'plan-order__diff-item--changed': row.changed }"
            >
              <span class="plan-order__diff-idx">{{ row.index }}</span>{{ row.after }}
            </li>
          </ul>
        </div>
      </div>
    </template>

    <p v-if="activeReady && applyDisabledReason" class="plan-order__reason-text">
      {{ applyDisabledReason }}
    </p>

    <footer class="plan-order__foot">
      <el-button
        type="primary"
        size="small"
        :disabled="!canApply"
        :loading="confirming"
        @click="apply"
      >
        应用建议
      </el-button>
      <el-button size="small" @click="emit('close')">放弃</el-button>
    </footer>
  </section>
</template>

<style scoped lang="scss">
.plan-order {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.plan-order__head {
  display: flex;
  align-items: center;
  gap: var(--space-xs);

  h3 {
    flex: 1;
    margin: 0;
    font-size: var(--font-size-base);
    font-weight: 600;
  }
}

.plan-order__placeholder {
  padding: var(--space-sm) 0;
}

.plan-order__list {
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
  list-style: none;
}

.plan-order__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  cursor: grab;
}

.plan-order__row + .plan-order__row {
  border-top: 1px solid var(--color-neutral-100);
}

// 拖拽中目标位用主色浅底提示落点（视觉 4）
.plan-order__row--over {
  background: var(--color-primary-100);
}

.plan-order__row--dragging {
  opacity: 0.6;
}

.plan-order__rank {
  display: flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-primary-500);
  color: var(--color-neutral-0);
  font-size: var(--font-size-sm);
}

.plan-order__title {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
}

.plan-order__reason {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: help;
}

.plan-order__handle {
  flex-shrink: 0;
  color: var(--color-neutral-400);
}

.plan-order__diff {
  overflow: hidden;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
}

.plan-order__diff-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-xs) var(--space-sm);
  background: var(--color-neutral-50);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}

.plan-order__diff-count {
  color: var(--color-neutral-400);
}

.plan-order__diff-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;

  ul {
    margin: 0;
    padding: var(--space-xs) 0;
    list-style: none;
  }

  ul + ul {
    border-left: 1px solid var(--color-neutral-200);
  }
}

.plan-order__diff-cols li {
  display: flex;
  gap: var(--space-xs);
  padding: 2px var(--space-sm);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.plan-order__diff-item--changed {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.plan-order__diff-idx {
  color: var(--color-neutral-400);
}

.plan-order__reason-text {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.plan-order__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}
</style>
