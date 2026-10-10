<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Document as DocumentIcon, FolderOpened as FolderIcon } from '@element-plus/icons-vue'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import {
  caseCompleteChangedFields,
  caseCompleteCompareRows,
  casePriorityChanged,
  isCaseCompleteSuggestion,
  isCasePrioritySuggestion,
} from '@/composables/project/ai/assistPresentation'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { ConfirmItemResult } from '@/composables/project/ai/useAiArtifactReview'
import type {
  AiAssistFieldName,
  AiAssistSourceRef,
  AiAssistTaskType,
  AiArtifactConfirmResult,
  AiCaseCompleteSuggestion,
  AiCasePrioritySuggestion,
} from '@/types'

const props = withDefaults(
  defineProps<{
    /** 对照面板在补全与级别推荐间复用（交互 2.1） */
    kind: Extract<AiAssistTaskType, 'case_complete' | 'case_priority'>
    rows: AssistRow[]
    /** 节点只读或无脑图编辑权时置灰并说明原因（交互 2.3） */
    canAdopt: boolean
    confirming: boolean
    loading?: boolean
    receipt?: AiArtifactConfirmResult[]
    /** 来源需求已变更：顶部提示，建议仅供参考但仍可采纳（交互 2.3） */
    sourceStale?: boolean
  }>(),
  {
    loading: false,
    receipt: () => [],
    sourceStale: false,
  },
)

const emit = defineEmits<{
  /** 整条采纳：补全走 adopted（只补空缺），级别推荐按建议级别落库 */
  adopt: [key: string]
  /** 逐字段采纳：仅携带人工确认的字段，命中详设 4.2「人工显式采纳」口径 */
  adoptEdited: [key: string, content: Record<string, unknown>]
  batchAdopt: [keys: string[]]
  reject: [key: string, note?: string]
  openSource: [ref: AiAssistSourceRef]
  clearReceipt: []
  close: []
}>()

const PRIORITY_LABEL: Record<string, string> = {
  high: 'P0·高',
  medium: 'P1·中',
  low: 'P2·低',
}

interface CompareItem {
  id: string
  label: string
  existing: string
  suggested: string
  changed: boolean
  /** 新增 / 变更徽标（视觉 4：浅底 + 小徽标，不单靠颜色表意） */
  badge: string
}

const activeKey = ref('')
const showUnchanged = ref(false)
const rejectVisible = ref(false)
const rejectNote = ref('')

// 切换 kind 或内容刷新后停在首个未处理建议，便于逐条核对
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

const title = computed(() => (props.kind === 'case_complete' ? 'AI 补全建议' : 'AI 级别推荐'))

const activeRow = computed<AssistRow | null>(
  () => props.rows.find((row) => row.key === activeKey.value) ?? props.rows[0] ?? null,
)

const activeComplete = computed<AiCaseCompleteSuggestion | null>(() => {
  const view = activeRow.value?.suggestion ?? null
  return props.kind === 'case_complete' && isCaseCompleteSuggestion(view) ? view : null
})

const activePriority = computed<AiCasePrioritySuggestion | null>(() => {
  const view = activeRow.value?.suggestion ?? null
  return props.kind === 'case_priority' && isCasePrioritySuggestion(view) ? view : null
})

const activeReady = computed(() => activeRow.value?.suggestion != null)

const extraNodes = computed(() => activeComplete.value?.extraNodes ?? [])

const sourceRefs = computed<AiAssistSourceRef[]>(() => {
  const view = activeRow.value?.suggestion ?? null
  if (isCaseCompleteSuggestion(view)) return view.sourceRefs
  if (isCasePrioritySuggestion(view)) return view.sourceRefs
  return []
})

function displayValues(values: string[]): string {
  return values.length > 0 ? values.join('、') : '—'
}

function priorityLabel(priority: string): string {
  return PRIORITY_LABEL[priority] ?? priority ?? '—'
}

const compareItems = computed<CompareItem[]>(() => {
  const priority = activePriority.value
  if (props.kind === 'case_priority') {
    if (!priority) return []
    const changed = casePriorityChanged(priority)
    return [
      {
        id: 'priority',
        label: '用例级别',
        existing: priorityLabel(priority.current),
        suggested: priorityLabel(priority.suggested),
        changed,
        badge: '调整',
      },
    ]
  }
  const view = activeComplete.value
  if (!view) return []
  return caseCompleteCompareRows(view.fields).map((row) => ({
    id: row.name,
    label: row.label,
    existing: displayValues(row.existing),
    suggested: displayValues(row.suggested),
    changed: row.changed,
    badge: row.existing.length === 0 ? '补全' : '变更',
  }))
})

const changedItems = computed(() => compareItems.value.filter((item) => item.changed))
const unchangedCount = computed(() => compareItems.value.length - changedItems.value.length)
const visibleItems = computed(() => (showUnchanged.value ? compareItems.value : changedItems.value))
const activeUnchanged = computed(() => changedItems.value.length === 0 && extraNodes.value.length === 0)

const pendingKeys = computed(() =>
  props.rows.filter((row) => row.confirmStatus === 'pending').map((row) => row.key),
)
const adoptedCount = computed(
  () =>
    props.rows.filter(
      (row) => row.confirmStatus === 'adopted' || row.confirmStatus === 'adopted_edited',
    ).length,
)
const totalCount = computed(() => props.rows.length)

/** 无变化判定只用于置灰：内容缺失时不判「无变化」，避免误伤可采纳项 */
function rowUnchanged(row: AssistRow): boolean {
  const view = row.suggestion
  if (isCaseCompleteSuggestion(view)) {
    return caseCompleteChangedFields(view.fields).length === 0 && view.extraNodes.length === 0
  }
  if (isCasePrioritySuggestion(view)) return !casePriorityChanged(view)
  return false
}

const allUnchanged = computed(
  () => props.rows.length > 0 && props.rows.every((row) => row.suggestion != null && rowUnchanged(row)),
)

const adoptDisabledReason = computed(() => {
  if (props.rows.length === 0) return '暂无可处理的建议'
  if (!props.canAdopt) return '当前节点只读或没有脑图编辑权限'
  if (allUnchanged.value) return '全部建议与现有内容一致，仅可关闭'
  if (props.confirming) return '正在提交采纳结果'
  if (!activeReady.value) return '建议内容暂不可用'
  if (activeRow.value?.confirmStatus !== 'pending') return '当前建议已处理'
  if (activeUnchanged.value) return '当前节点建议与现有内容一致'
  return ''
})

const batchDisabledReason = computed(() => {
  if (props.rows.length === 0) return '暂无可处理的建议'
  if (!props.canAdopt) return '当前节点只读或没有脑图编辑权限'
  if (allUnchanged.value) return '全部建议与现有内容一致，仅可关闭'
  if (props.confirming) return '正在提交采纳结果'
  if (pendingKeys.value.length === 0) return '全部建议已处理'
  return ''
})

const canAdoptActive = computed(() => adoptDisabledReason.value === '')
const canBatch = computed(() => batchDisabledReason.value === '')
/** 置灰原因可见展示（同 AiScopeCheckList 口径），不依赖悬浮才能读到 */
const disabledReason = computed(() => adoptDisabledReason.value || batchDisabledReason.value)
const canReject = computed(
  () =>
    props.canAdopt &&
    !props.confirming &&
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

/** 单值字段（precondition）回写字符串，列表字段回写数组，与产物清洗口径一致 */
function wireValue(name: AiAssistFieldName, values: string[]): string | string[] {
  return name === 'precondition' ? (values[0] ?? '') : values
}

function changedFieldsContent(view: AiCaseCompleteSuggestion): Record<string, unknown> {
  const fields: Record<string, unknown> = {}
  for (const name of caseCompleteChangedFields(view.fields)) {
    fields[name] = wireValue(name, view.fields[name].suggested)
  }
  return { fields }
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
  if (!row) return
  const view = row.suggestion
  if (props.kind === 'case_complete' && isCaseCompleteSuggestion(view)) {
    const content = changedFieldsContent(view)
    retryOps.set(row.key, () => emit('adoptEdited', row.key, content))
    emit('adoptEdited', row.key, content)
  } else {
    retryOps.set(row.key, () => emit('adopt', row.key))
    emit('adopt', row.key)
  }
  advance()
}

function onBatch(): void {
  for (const key of pendingKeys.value) {
    retryOps.set(key, () => emit('adopt', key))
  }
  emit('batchAdopt', pendingKeys.value)
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
  <section class="assist-panel" aria-label="AI 建议对照面板">
    <header class="assist-panel__head">
      <div class="assist-panel__title">
        <h3>{{ title }}</h3>
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
      <div v-if="visibleItems.length > 0" class="assist-compare">
        <div class="assist-compare__head">
          <span>项目</span>
          <span>现有内容</span>
          <span>AI 建议</span>
        </div>
        <div
          v-for="item in visibleItems"
          :key="item.id"
          class="assist-compare__row"
          :class="{ 'assist-compare__row--changed': item.changed }"
        >
          <span class="assist-compare__label">{{ item.label }}</span>
          <span class="assist-compare__existing">{{ item.existing }}</span>
          <span class="assist-compare__suggested">
            {{ item.suggested }}
            <el-tag v-if="item.changed" size="small" effect="light">{{ item.badge }}</el-tag>
          </span>
        </div>
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

      <div v-if="extraNodes.length > 0" class="assist-extra">
        <div class="assist-extra__head">
          补充节点树预览
          <span>采纳后新增，不直接写入脑图</span>
        </div>
        <ul class="assist-extra__list">
          <li v-for="(node, index) in extraNodes" :key="`${node.title}-${index}`">
            <el-icon>
              <DocumentIcon v-if="node.isTestCase" />
              <FolderIcon v-else />
            </el-icon>
            <span class="assist-extra__title">{{ node.title }}</span>
            <el-tag size="small" :type="node.isTestCase ? 'primary' : 'info'" effect="plain">
              {{ node.isTestCase ? '用例' : '结构' }}
            </el-tag>
          </li>
        </ul>
      </div>
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
          逐条采纳
        </el-button>
        <el-button size="small" :disabled="!canBatch" :loading="confirming" @click="onBatch">
          批量采纳
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

.assist-compare {
  display: grid;
  grid-template-columns: minmax(88px, 1fr) minmax(0, 2fr) minmax(0, 2fr);
  overflow: hidden;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
}

.assist-compare__head {
  display: contents;

  span {
    padding: var(--space-xs) var(--space-sm);
    background: var(--color-neutral-50);
    color: var(--color-neutral-400);
    font-size: var(--font-size-sm);
  }
}

.assist-compare__row {
  display: contents;
}

.assist-compare__row > span {
  padding: var(--space-xs) var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.assist-compare__row--changed > span {
  background: var(--color-primary-100);
}

.assist-compare__label {
  color: var(--color-neutral-400);
}

.assist-compare__existing {
  color: var(--color-neutral-400);
}

.assist-compare__suggested {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.assist-extra__head {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  font-size: var(--font-size-sm);

  span {
    color: var(--color-neutral-400);
  }
}

.assist-extra__list {
  margin: var(--space-xs) 0 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    padding: var(--space-xs) var(--space-sm);
    border-radius: var(--radius-sm, 4px);
    background: var(--color-neutral-50);
    font-size: var(--font-size-sm);
  }
}

.assist-extra__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
