<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import {
  caseOptimizeOperationFoldable,
  caseOptimizeOperationLabel,
  caseOptimizeOperationUnchanged,
  isCaseOptimizeSuggestion,
} from '@/composables/project/ai/assistPresentation'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { ConfirmItemResult } from '@/composables/project/ai/useAiProposalReview'
import type {
  AiAssistSourceRef,
  AiCaseOptimizeOperation,
  AiCaseOptimizeOperationSuggestion,
  AiProposalConfirmResult,
} from '@/types'

const props = withDefaults(
  defineProps<{
    rows: AssistRow[]
    /** 节点只读或无脑图编辑权时置灰并说明原因（交互 2.3） */
    canAdopt: boolean
    confirming: boolean
    loading?: boolean
    receipt?: AiProposalConfirmResult[]
    /** 来源需求已变更：顶部提示，建议仅供参考但仍可采纳（交互 2.3） */
    sourceStale?: boolean
    /** 任务已执行完毕：展示「重新执行」入口（交互 2.4） */
    retryable?: boolean
  }>(),
  {
    loading: false,
    receipt: () => [],
    sourceStale: false,
    retryable: false,
  },
)

const emit = defineEmits<{
  /** 整条采纳：按提案原样执行全部操作指令 */
  adopt: [key: string]
  /** 采纳当前核对节点：仅携带人工核对后保留的操作指令 */
  adoptEdited: [key: string, content: Record<string, unknown>]
  reject: [key: string, note?: string]
  openSource: [ref: AiAssistSourceRef]
  clearReceipt: []
  retry: []
  close: []
}>()

interface OpEdit {
  suggested: string[]
  skipped: boolean
}

interface OpCard {
  id: string
  index: number
  op: AiCaseOptimizeOperation
  kindLabel: string
  label: string
  badge: string
  level?: string
  existing: string
  suggested: string[]
  unchanged: boolean
  foldable: boolean
  skipped: boolean
}

const PRIORITY_OPTIONS = ['P0', 'P1', 'P2', 'P3'].map((value) => ({ value, label: value }))

const activeKey = ref('')
const showUnchanged = ref(false)
const rejectVisible = ref(false)
const rejectNote = ref('')
const edits = ref<Record<string, OpEdit>>({})

// 内容刷新后停在首个未处理建议，便于逐条核对
watch(
  () => props.rows,
  (incoming) => {
    if (incoming.length === 0) {
      activeKey.value = ''
      showUnchanged.value = false
      return
    }
    if (!incoming.some((row) => row.key === activeKey.value)) {
      activeKey.value = (incoming.find((row) => row.confirmStatus === 'pending') ?? incoming[0]).key
      showUnchanged.value = false
    }
  },
  { immediate: true },
)

const activeRow = computed<AssistRow | null>(
  () => props.rows.find((row) => row.key === activeKey.value) ?? props.rows[0] ?? null,
)

const activeOptimize = computed<AiCaseOptimizeOperationSuggestion | null>(() => {
  const view = activeRow.value?.suggestion ?? null
  return isCaseOptimizeSuggestion(view) ? view : null
})

const activeReady = computed(() => activeRow.value?.suggestion != null)

const sourceRefs = computed<AiAssistSourceRef[]>(() => activeOptimize.value?.sourceRefs ?? [])

function displayValues(values: string[]): string {
  return values.length > 0 ? values.join('、') : '—'
}

function editId(key: string, index: number): string {
  return `${key}#${index}`
}

function defaultSuggested(op: AiCaseOptimizeOperation): string[] {
  return op.op === 'add' ? [op.title] : [...op.suggested]
}

function suggestedOf(key: string, index: number, op: AiCaseOptimizeOperation): string[] {
  return edits.value[editId(key, index)]?.suggested ?? defaultSuggested(op)
}

function setSuggested(key: string, index: number, values: string[]): void {
  const id = editId(key, index)
  edits.value[id] = { suggested: values, skipped: edits.value[id]?.skipped === true }
}

function isSkipped(key: string, index: number): boolean {
  return edits.value[editId(key, index)]?.skipped === true
}

function toggleSkipped(key: string, index: number, op: AiCaseOptimizeOperation): void {
  const id = editId(key, index)
  const previous = edits.value[id] ?? { suggested: defaultSuggested(op), skipped: false }
  edits.value[id] = { ...previous, skipped: !previous.skipped }
}

function badgeOf(op: AiCaseOptimizeOperation, unchanged: boolean): string {
  if (op.op === 'add') return '新增节点'
  if (op.field === 'priority') return unchanged ? '同级' : '调整'
  return unchanged ? (op.existing.length === 0 ? '补全' : '同级') : '变更'
}

const cards = computed<OpCard[]>(() => {
  const view = activeOptimize.value
  const key = activeRow.value?.key ?? ''
  if (!view) return []
  return view.operations.map((op, index) => {
    const unchanged = caseOptimizeOperationUnchanged(op)
    const foldable = caseOptimizeOperationFoldable(op)
    const suggested = suggestedOf(key, index, op)
    return {
      id: editId(key, index),
      index,
      op,
      kindLabel: op.op === 'update' ? '更新' : '新增',
      label: caseOptimizeOperationLabel(op),
      badge: badgeOf(op, unchanged),
      level: op.op === 'update' && op.field === 'priority'
        ? (suggested[0] ?? '').toLowerCase()
        : undefined,
      existing: op.op === 'update' ? displayValues(op.existing) : '',
      suggested,
      unchanged,
      foldable,
      skipped: isSkipped(key, index),
    }
  })
})

const visibleCards = computed(() => showUnchanged.value ? cards.value : cards.value.filter((card) => !card.foldable))
const unchangedCount = computed(() => cards.value.filter((card) => card.foldable).length)
const effectiveCount = computed(() => cards.value.filter((card) => !card.skipped).length)

const adoptedCount = computed(
  () =>
    props.rows.filter(
      (row) => row.confirmStatus === 'adopted' || row.confirmStatus === 'adopted_edited',
    ).length,
)
const totalCount = computed(() => props.rows.length)

function rowUnchanged(row: AssistRow): boolean {
  const view = row.suggestion
  if (!isCaseOptimizeSuggestion(view)) return false
  return view.operations.length === 0 || view.operations.every(caseOptimizeOperationUnchanged)
}

const activeUnchanged = computed(
  () => cards.value.length === 0 || cards.value.every((card) => card.unchanged),
)
const allUnchanged = computed(
  () => props.rows.length > 0 && props.rows.every((row) => row.suggestion != null && rowUnchanged(row)),
)

const adoptDisabledReason = computed(() => {
  if (props.rows.length === 0) return '暂无可处理的建议'
  if (!props.canAdopt) return '当前节点只读'
  if (allUnchanged.value) return '全部建议与现有内容一致，仅可关闭'
  if (props.confirming) return '正在提交采纳结果'
  if (!activeReady.value) return '建议内容暂不可用'
  if (activeRow.value?.confirmStatus !== 'pending') return '当前建议已处理'
  if (activeUnchanged.value) return '当前节点建议与现有内容一致'
  if (effectiveCount.value === 0) return '当前建议的操作已全部跳过'
  return ''
})

const canAdoptActive = computed(() => adoptDisabledReason.value === '')
/** 置灰原因可见展示（同 AiScopeCheckList 口径），不依赖悬浮才能读到 */
const disabledReason = computed(() => adoptDisabledReason.value)
const canReject = computed(
  () =>
    props.canAdopt &&
    !props.confirming &&
    !allUnchanged.value &&
    activeReady.value &&
    activeRow.value?.confirmStatus === 'pending',
)

const receiptItems = computed<ConfirmItemResult[]>(() =>
  props.receipt.map((item) => ({
    key: item.key,
    title: props.rows.find((row) => row.key === item.key)?.title ?? item.key,
    action: item.action,
    success: item.success,
    errorCode: item.errorCode,
    errorMsg: item.errorMsg ?? '',
    createdId: item.createdId,
  })),
)

const showReceipt = computed(() => receiptItems.value.length > 0)

/** 编辑载荷仅覆盖建议值，nodeId / parentId / field / nodeType 与提案保持一致 */
function buildContent(view: AiCaseOptimizeOperationSuggestion, rowKey: string): Record<string, unknown> {
  const operations: Record<string, unknown>[] = []
  view.operations.forEach((op, index) => {
    if (isSkipped(rowKey, index)) return
    const values = suggestedOf(rowKey, index, op)
    if (op.op === 'update') {
      operations.push({
        op: 'update',
        nodeId: op.nodeId,
        field: op.field,
        suggested: op.field === 'tags' ? values : (values[0] ?? ''),
      })
    } else {
      operations.push({
        op: 'add',
        parentId: op.parentId,
        nodeType: op.nodeType,
        title: values[0] ?? '',
      })
    }
  })
  return { operations }
}

/** 逐条核对推进到下一个未处理建议（失败项可经回执重试） */
function advance(): void {
  const next = props.rows.find(
    (row) => row.key !== activeRow.value?.key && row.confirmStatus === 'pending',
  )
  if (next) activeKey.value = next.key
}

// 回执重试需还原当时的动作与载荷，仅凭 key 无法区分采纳 / 驳回，故按 key 记录提交闭包
const retryOps = new Map<string, () => void>()

function adoptActive(): void {
  const row = activeRow.value
  const view = activeOptimize.value
  if (!row || !view) return
  const content = buildContent(view, row.key)
  retryOps.set(row.key, () => emit('adoptEdited', row.key, content))
  emit('adoptEdited', row.key, content)
  advance()
}

function openReject(): void {
  rejectNote.value = ''
  rejectVisible.value = true
}

function confirmReject(): void {
  const row = activeRow.value
  if (!row) return
  const note = rejectNote.value.trim() || undefined
  retryOps.set(row.key, () => emit('reject', row.key, note))
  emit('reject', row.key, note)
  rejectVisible.value = false
  advance()
}

function onReceiptRetry(item: ConfirmItemResult): void {
  const row = props.rows.find((candidate) => candidate.key === item.key)
  if (row && row.confirmStatus === 'pending') activeKey.value = row.key
  const replay = retryOps.get(item.key)
  if (replay) replay()
  else emit('adopt', item.key)
}
</script>

<template>
  <section class="assist-panel" aria-label="AI 优化建议对照面板">
    <header class="assist-panel__head">
      <div class="assist-panel__title">
        <h3>AI 优化建议</h3>
        <el-tag v-if="activeRow" size="small" :type="activeRow.confirmTagType">
          {{ activeRow.confirmLabel }}
        </el-tag>
      </div>
      <el-select
        v-if="rows.length > 1"
        v-model="activeKey"
        size="small"
        class="assist-panel__switch"
        aria-label="切换核对节点"
      >
        <el-option
          v-for="row in rows"
          :key="row.key"
          :value="row.key"
          :label="`${row.title || row.key}${row.confirmStatus === 'pending' ? '' : '（已处理）'}`"
        />
      </el-select>
      <el-button
        v-if="retryable"
        link
        type="primary"
        :disabled="confirming"
        @click="emit('retry')"
      >
        重新执行
      </el-button>
      <el-button link @click="emit('close')">关闭</el-button>
    </header>

    <el-alert
      v-if="sourceStale"
      title="来源需求已变更"
      description="建议仅供参考，仍可按需采纳"
      type="warning"
      :closable="false"
      show-icon
      class="assist-panel__alert"
    />

    <div v-if="sourceRefs.length > 0" class="assist-panel__sources">
      <span class="assist-panel__label">来源</span>
      <el-tooltip
        v-for="source in sourceRefs"
        :key="source.id"
        :content="source.quote || source.title"
        :disabled="!source.quote"
      >
        <el-link type="primary" @click="emit('openSource', source)">{{ source.title }}</el-link>
      </el-tooltip>
    </div>

    <div v-if="loading && !activeReady" class="assist-panel__placeholder">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="!activeReady" class="assist-panel__placeholder">
      建议内容暂不可用，请关闭面板后重新发起
    </div>

    <template v-else>
      <div v-if="visibleCards.length > 0" class="assist-ops">
        <article
          v-for="card in visibleCards"
          :key="card.id"
          :class="['assist-op-card', { 'assist-op-card--skipped': card.skipped }]"
        >
          <header class="assist-op-card__head">
            <span class="assist-op-card__seq">操作 {{ card.index + 1 }}</span>
            <span class="assist-op-card__path">{{ card.op.path || '—' }}</span>
            <span class="assist-op-card__field">· {{ card.label }}</span>
            <el-tag size="small" :effect="card.op.op === 'add' ? 'dark' : 'plain'">
              {{ card.kindLabel }}
            </el-tag>
            <el-tag v-if="card.badge" size="small" :effect="card.unchanged ? 'plain' : 'light'">
              {{ card.badge }}
            </el-tag>
            <el-button link size="small" @click="toggleSkipped(activeKey, card.index, card.op)">
              {{ card.skipped ? '恢复' : '跳过' }}
            </el-button>
          </header>
          <template v-if="card.op.op === 'update'">
            <p class="assist-op-card__line">
              <span class="assist-op-card__label">现有</span>
              <span>{{ card.existing }}</span>
            </p>
            <p class="assist-op-card__line">
              <span class="assist-op-card__label">建议</span>
              <span v-if="card.op.field === 'priority'" class="assist-op-card__priority">
                <span
                  v-if="card.level"
                  class="assist-op-card__level"
                  :class="`assist-op-card__level--${card.level}`"
                >{{ card.suggested[0] }}</span>
                <el-select
                  :model-value="card.suggested[0] ?? ''"
                  size="small"
                  class="assist-op-card__edit"
                  @update:model-value="setSuggested(activeKey, card.index, [$event])"
                >
                  <el-option
                    v-for="option in PRIORITY_OPTIONS"
                    :key="option.value"
                    :value="option.value"
                    :label="option.label"
                  />
                </el-select>
              </span>
              <el-select
                v-else-if="card.op.field === 'tags'"
                :model-value="card.suggested"
                size="small"
                multiple
                filterable
                allow-create
                default-first-option
                :reserve-keyword="false"
                class="assist-op-card__edit"
                @update:model-value="setSuggested(activeKey, card.index, $event)"
              />
              <el-input
                v-else
                :model-value="card.suggested[0] ?? ''"
                size="small"
                class="assist-op-card__edit"
                @update:model-value="setSuggested(activeKey, card.index, [$event])"
              />
            </p>
          </template>
          <template v-else>
            <p class="assist-op-card__line">
              <span class="assist-op-card__label">新节点</span>
              <el-input
                :model-value="card.suggested[0] ?? ''"
                size="small"
                class="assist-op-card__edit"
                @update:model-value="setSuggested(activeKey, card.index, [$event])"
              />
            </p>
          </template>
          <p v-if="card.op.reason" class="assist-op-card__reason">理由：{{ card.op.reason }}</p>
        </article>
      </div>

      <el-button
        v-if="unchangedCount > 0"
        link
        type="info"
        class="assist-panel__fold"
        @click="showUnchanged = !showUnchanged"
      >
        {{ showUnchanged ? '收起无变化项' : `展开共有 ${unchangedCount} 项与现有内容相同` }}
      </el-button>

      <p v-if="activeUnchanged" class="assist-panel__hint">建议与现有内容一致，无需变更</p>
    </template>

    <AiConfirmReceipt
      v-if="showReceipt"
      :receipt="receiptItems"
      :confirming="confirming"
      @retry="onReceiptRetry"
      @clear="emit('clearReceipt')"
    />

    <p v-if="disabledReason" class="assist-panel__reason">{{ disabledReason }}</p>

    <footer class="assist-panel__foot">
      <span class="assist-panel__stat">已采纳 {{ adoptedCount }} / 共 {{ totalCount }}</span>
      <div class="assist-panel__actions">
        <el-button
          type="primary"
          size="small"
          :disabled="!canAdoptActive"
          :loading="confirming"
          @click="adoptActive"
        >
          采纳
        </el-button>
        <el-button size="small" :disabled="!canReject" @click="openReject">驳回</el-button>
      </div>
    </footer>

    <el-dialog v-model="rejectVisible" title="驳回建议" width="420px">
      <el-input
        v-model="rejectNote"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="说明驳回原因（选填）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.assist-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.assist-panel__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.assist-panel__title {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;

  h3 {
    margin: 0;
    font-size: var(--font-size-base);
    font-weight: 600;
  }
}

.assist-panel__switch {
  max-width: 240px;
}

.assist-panel__sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.assist-panel__label {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-panel__placeholder {
  padding: var(--space-sm) 0;
}

.assist-panel__fold {
  align-self: flex-start;
}

.assist-panel__hint {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-panel__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.assist-panel__actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.assist-panel__reason {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-panel__stat {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-ops {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  max-height: 360px;
  overflow-y: auto;
}

.assist-op-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
  background: var(--color-primary-50);

  &--skipped {
    background: var(--color-neutral-50);
    opacity: 0.7;
  }
}

.assist-op-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.assist-op-card__seq {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-op-card__path {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.assist-op-card__field {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-op-card__head > .el-button {
  margin-left: auto;
}

.assist-op-card__line {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin: 0;
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.assist-op-card__label {
  flex-shrink: 0;
  color: var(--color-neutral-400);
}

.assist-op-card__edit {
  max-width: 320px;
}

.assist-op-card__priority {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.assist-op-card__reason {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.assist-op-card__level {
  padding: 0 var(--space-xs);
  border-radius: var(--radius-sm, 4px);
  color: var(--color-neutral-0);
  font-size: var(--font-size-xs);
  line-height: 20px;
}

@each $p in p0, p1, p2, p3 {
  .assist-op-card__level--#{$p} {
    background: var(--color-priority-#{$p});
  }
}
</style>
