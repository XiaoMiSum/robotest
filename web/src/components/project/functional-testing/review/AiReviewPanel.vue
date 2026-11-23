<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import { isReviewSuggestion } from '@/composables/project/ai/assistPresentation'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { ConfirmItemResult } from '@/types'
import type {
  AiAssistSourceRef,
  AiProposalConfirmResult,
  AiReviewIssue,
  AiReviewSuggestion,
} from '@/types'

const props = withDefaults(
  defineProps<{
    rows: AssistRow[]
    /** 评审终态时置灰处置动作，仍可只读回看 */
    canAdopt: boolean
    confirming: boolean
    loading?: boolean
    receipt?: AiProposalConfirmResult[]
    /** 来源需求已变更：建议仅供参考但仍可采纳 */
    sourceStale?: boolean
    /** 提案产出完毕：提供再次执行入口 */
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
  adopt: [key: string]
  reject: [key: string, note?: string]
  openSource: [ref: AiAssistSourceRef]
  clearReceipt: []
  retry: []
  close: []
}>()

const SEVERITY_LABEL: Record<AiReviewIssue['severity'], string> = {
  high: '高',
  medium: '中',
  low: '低',
}
const SEVERITY_TYPE: Record<AiReviewIssue['severity'], 'danger' | 'warning' | 'info'> = {
  high: 'danger',
  medium: 'warning',
  low: 'info',
}

const activeKey = ref('')
const rejectVisible = ref(false)
const rejectKey = ref('')
const rejectNote = ref('')

// 内容刷新后停在首个待处置建议，逐条确认时不必重新定位
watch(
  () => props.rows,
  (incoming) => {
    const active = incoming.find((row) => row.key === activeKey.value)
    if (!active || active.confirmStatus !== 'pending') {
      activeKey.value =
        (incoming.find((row) => row.confirmStatus === 'pending') ?? incoming[0])?.key ?? ''
    }
  },
  { immediate: true },
)

const pendingRows = computed(() => props.rows.filter((row) => row.confirmStatus === 'pending'))
const processedRows = computed(() => props.rows.filter((row) => row.confirmStatus !== 'pending'))

function viewOf(row: AssistRow): AiReviewSuggestion | null {
  const view = row.suggestion
  return isReviewSuggestion(view) ? view : null
}

function canOperate(row: AssistRow): boolean {
  return props.canAdopt && !props.confirming && row.confirmStatus === 'pending' && viewOf(row) !== null
}

const disabledReason = computed(() => (props.canAdopt ? '' : '当前评审不可处置建议'))

// 回执重试需还原当时的动作与载荷，仅凭 key 无法区分采纳 / 驳回，故按 key 记录提交闭包
const retryOps = new Map<string, () => void>()

function adopt(row: AssistRow): void {
  retryOps.set(row.key, () => emit('adopt', row.key))
  emit('adopt', row.key)
}

function openReject(key: string): void {
  rejectKey.value = key
  rejectNote.value = ''
  rejectVisible.value = true
}

function confirmReject(): void {
  if (!rejectKey.value) return
  const key = rejectKey.value
  const note = rejectNote.value.trim() || undefined
  retryOps.set(key, () => emit('reject', key, note))
  emit('reject', key, note)
  rejectVisible.value = false
}

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

function onReceiptRetry(item: ConfirmItemResult): void {
  const row = props.rows.find((candidate) => candidate.key === item.key)
  if (row) activeKey.value = row.key
  const replay = retryOps.get(item.key)
  if (replay) replay()
  else emit('adopt', item.key)
}
</script>

<template>
  <section class="review-panel" aria-label="AI 评审建议面板">
    <header class="review-panel__head">
      <div class="review-panel__title">
        <h3>逐用例评审建议</h3>
        <el-tag size="small" type="info" effect="light">待处理 {{ pendingRows.length }}</el-tag>
      </div>
      <el-button
        v-if="retryable"
        link
        type="primary"
        :disabled="confirming"
        @click="emit('retry')"
      >重新执行</el-button>
      <el-button link @click="emit('close')">关闭</el-button>
    </header>

    <el-alert
      v-if="sourceStale"
      title="来源需求已变更"
      description="建议按发起时快照生成，仍可按需采纳"
      type="warning"
      :closable="false"
      show-icon
    />

    <div
      v-if="loading && (rows.length === 0 || rows.some((row) => row.suggestion === null))"
      class="review-panel__placeholder"
    >
      <el-skeleton :rows="3" animated />
    </div>
    <div v-else-if="rows.length === 0" class="review-panel__placeholder">暂无评审建议</div>

    <template v-else>
      <div v-if="pendingRows.length > 0" class="review-panel__section">
        <h4 class="review-panel__section-title">待处理建议（{{ pendingRows.length }}）</h4>
        <article
          v-for="row in pendingRows"
          :key="row.key"
          class="review-card"
          :class="{ 'review-card--active': row.key === activeKey }"
        >
          <header class="review-card__head">
            <span class="review-card__title">{{ row.title || viewOf(row)?.caseTitle || row.key }}</span>
            <el-tag
              v-if="viewOf(row)"
              size="small"
              effect="light"
              :type="viewOf(row)?.suggestedMark === 'pass' ? 'success' : 'danger'"
            >{{ viewOf(row)?.suggestedMark === 'pass' ? '建议通过' : '建议不通过' }}</el-tag>
            <el-tag size="small" :type="row.confirmTagType">{{ row.confirmLabel }}</el-tag>
          </header>
          <p class="review-card__reason">理由：{{ viewOf(row)?.reason || '—' }}</p>

          <div v-if="viewOf(row)?.issues.length" class="review-card__issues">
            <p v-for="(issue, index) in viewOf(row)?.issues" :key="index" class="review-card__issue">
              <el-tag size="small" :type="SEVERITY_TYPE[issue.severity]">
                {{ SEVERITY_LABEL[issue.severity] }}
              </el-tag>
              <span class="review-card__issue-category">{{ issue.category }}</span>
              <span class="review-card__issue-path">{{ issue.path || '—' }}</span>
              <span class="review-card__issue-desc">{{ issue.description }}</span>
            </p>
          </div>

          <div v-if="viewOf(row)?.sourceRefs.length" class="review-card__sources">
            <span class="review-panel__label">来源</span>
            <el-tooltip
              v-for="source in viewOf(row)?.sourceRefs"
              :key="source.id"
              :content="source.quote || source.title"
              :disabled="!source.quote"
            >
              <el-link type="primary" @click="emit('openSource', source)">
                {{ source.title || source.id }}
              </el-link>
            </el-tooltip>
          </div>

          <div class="review-card__actions">
            <el-button
              link
              type="danger"
              size="small"
              :disabled="!canOperate(row)"
              @click="openReject(row.key)"
            >驳回</el-button>
            <el-button
              link
              type="success"
              size="small"
              :disabled="!canOperate(row)"
              :loading="confirming && activeKey === row.key"
              @click="adopt(row)"
            >采纳</el-button>
          </div>
        </article>
      </div>

      <div v-if="processedRows.length > 0" class="review-panel__section">
        <h4 class="review-panel__section-title">已处理（{{ processedRows.length }}）</h4>
        <article v-for="row in processedRows" :key="row.key" class="review-card review-card--readonly">
          <header class="review-card__head">
            <span class="review-card__title">{{ row.title || viewOf(row)?.caseTitle || row.key }}</span>
            <el-tag
              v-if="viewOf(row)"
              size="small"
              effect="light"
              :type="viewOf(row)?.suggestedMark === 'pass' ? 'success' : 'danger'"
            >{{ viewOf(row)?.suggestedMark === 'pass' ? '建议通过' : '建议不通过' }}</el-tag>
            <el-tag size="small" :type="row.confirmTagType">{{ row.confirmLabel }}</el-tag>
          </header>
          <p class="review-card__reason">理由：{{ viewOf(row)?.reason || '—' }}</p>
        </article>
      </div>
    </template>

    <AiConfirmReceipt
      v-if="showReceipt"
      :receipt="receiptItems"
      :confirming="confirming"
      @retry="onReceiptRetry"
      @clear="emit('clearReceipt')"
    />

    <p v-if="disabledReason" class="review-panel__reason">{{ disabledReason }}</p>

    <footer class="review-panel__foot">
      <span class="review-panel__stat">已处置 {{ processedRows.length }} / 共 {{ rows.length }}</span>
    </footer>

    <el-dialog v-model="rejectVisible" title="驳回评审建议" width="420px">
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
.review-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.review-panel__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.review-panel__title {
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

.review-panel__label {
  flex-shrink: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.review-panel__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.review-panel__section-title {
  margin: 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.review-panel__placeholder {
  padding: var(--space-sm) 0;
}

.review-panel__foot {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.review-panel__stat,
.review-panel__reason {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.review-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
  background: var(--color-neutral-0);

  &--active {
    border-color: var(--color-primary-300);
    background: var(--color-primary-50);
  }

  &--readonly {
    opacity: 0.75;
  }
}

.review-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.review-card__title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.review-card__reason {
  margin: 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.review-card__issues {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.review-card__issue {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
  margin: 0;
  font-size: var(--font-size-sm);
}

.review-card__issue-category {
  font-weight: 600;
}

.review-card__issue-path {
  color: var(--color-neutral-400);
}

.review-card__issue-desc {
  flex-basis: 100%;
  overflow-wrap: anywhere;
}

.review-card__sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.review-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
}
</style>
