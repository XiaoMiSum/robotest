<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Operation as OperationIcon } from '@element-plus/icons-vue'
import type { AiProposalConfirmTarget, AiTaskDocumentMeta, AiTaskProposalSummary } from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import ProposalCompareView from '@/components/project/ai/ProposalCompareView.vue'
import { useAiProposalReview, type ProposalRow } from '@/composables/project/ai/useAiProposalReview'
import type { ProposalRankItem } from '@/composables/project/ai/reviewProfiles'
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
  confirming,
  receipt,
  processed,
  reviewTitle,
  editFields,
  editValuesOf,
  canEdit,
  canReject,
  needSystemVersion,
  NOTE_MAX_LENGTH,
  syncRows,
  init,
  loadContent,
  handleAdopt,
  handleAdoptEdited,
  handleReject,
  retryResult,
  clearReceipt,
} = useAiProposalReview(
  props.taskId,
  () => props.proposals,
  () => true,
  () => {
    syncRows()
    emit('confirmed')
  },
  () => props.taskType,
)

init()

const HANDLED_CONFIRM_STATUSES = new Set(['adopted', 'adopted_edited', 'rejected'])

/** 全部处置完成后保留卡片供导航回看，仅在头部提示并给出返回入口 */
const handledHint = computed(() => {
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

// ==================== 系统版本（详设 4.5）====================
/**
 * 未触碰时不提交 target.systemVersion：null → 服务端回退（拆分继承原条目 / 导入回落识别值）；
 * 触碰后提交当前值，空白串为显式清空（采纳后留空待手工补录）。
 */
const versionValue = ref(importVersionPrefill(props.documentMeta ?? null))
const versionTouched = ref(false)

watch(
  () => props.documentMeta,
  (meta) => {
    if (!versionTouched.value) versionValue.value = importVersionPrefill(meta ?? null)
  },
)

function onVersionInput(value: string): void {
  versionValue.value = value
  versionTouched.value = true
}

const versionHint = computed(() =>
  importVersionHint({
    taskType: props.taskType,
    documentMeta: props.documentMeta ?? null,
    touched: versionTouched.value,
    value: versionValue.value,
  }),
)

const versionEvidence = computed(() => importVersionEvidence(props.documentMeta ?? null))

function targetVersion(): AiProposalConfirmTarget | undefined {
  return versionTouched.value ? { systemVersion: versionValue.value } : undefined
}

// ==================== 模块名映射 ====================
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

// ==================== 编辑后采纳 ====================
const editVisible = ref(false)
const editRowKey = ref('')
const editForm = ref<Record<string, unknown>>({})
const rankItems = ref<ProposalRankItem[]>([])
const dragIndex = ref(-1)
const overIndex = ref(-1)

function fieldText(key: string): string {
  const value = editForm.value[key]
  if (Array.isArray(value)) return value.filter((item) => typeof item === 'string').join('\n')
  return typeof value === 'string' ? value : ''
}

function setField(key: string, value: unknown): void {
  editForm.value = { ...editForm.value, [key]: value }
}

function scalarField(key: string): string {
  const value = editForm.value[key]
  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}

const editValid = computed(() =>
  editFields.value.every((field) => !field.required || fieldText(field.key).trim().length > 0),
)

async function openEdit(row: ProposalRow): Promise<void> {
  if (!row.contentLoaded) await loadContent(row)
  editRowKey.value = row.key
  editForm.value = editValuesOf(row)
  const items = editForm.value.items
  rankItems.value = Array.isArray(items)
    ? (items as ProposalRankItem[]).map((item) => ({ ...item }))
    : []
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const row = rows.value.find((item) => item.key === editRowKey.value)
  if (!row) return
  if (rankItems.value.length > 0) {
    editForm.value = {
      ...editForm.value,
      items: rankItems.value.map((item, index) => ({ ...item, rank: index + 1 })),
    }
  }
  const ok = await handleAdoptEdited(row, editForm.value, targetVersion())
  if (ok) editVisible.value = false
}

function onRankDragStart(index: number): void {
  dragIndex.value = index
}

function onRankDragOver(index: number): void {
  overIndex.value = index
}

function onRankDragEnd(): void {
  dragIndex.value = -1
  overIndex.value = -1
}

function onRankDrop(index: number): void {
  const from = dragIndex.value
  onRankDragEnd()
  if (from < 0 || from === index || from >= rankItems.value.length) return
  const next = [...rankItems.value]
  const [moved] = next.splice(from, 1)
  if (moved) next.splice(index, 0, moved)
  rankItems.value = next
}

// ==================== 逐卡动作 ====================
function adopt(row: ProposalRow): void {
  if (canEdit.value) void handleAdopt(row, targetVersion())
}

// ==================== 驳回反馈 ====================
const rejectVisible = ref(false)
const rejectNote = ref('')
const rejectRowKey = ref('')

function openReject(key: string): void {
  rejectRowKey.value = key
  rejectNote.value = ''
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  const row = rows.value.find((item) => item.key === rejectRowKey.value)
  if (!row) return
  const ok = await handleReject(row, rejectNote.value, targetVersion())
  if (ok) rejectVisible.value = false
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
      <div v-if="needSystemVersion" class="proposal-review__version">
        <span class="proposal-review__version-label">版本</span>
        <el-input
          :model-value="versionValue"
          maxlength="50"
          show-word-limit
          clearable
          placeholder="留空按规则回填"
          style="width: 220px"
          @update:model-value="onVersionInput"
        />
        <el-tooltip v-if="versionEvidence" :content="versionEvidence" placement="top">
          <span class="proposal-review__evidence">{{ versionEvidence }}</span>
        </el-tooltip>
        <el-tooltip :content="versionHint" placement="top">
          <el-icon class="proposal-review__version-info"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
    </header>

    <el-alert
      v-if="taskType === 'requirement_split'"
      type="info"
      :title="`新条目将继承原条目版本${versionTouched && versionValue.trim() ? `（当前值：${versionValue.trim()}）` : ''}、原条目将归档`"
      show-icon
      :closable="false"
      class="proposal-review__hint"
    />

    <el-alert
      v-if="handledHint"
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
              <el-button link type="primary" @click="loadContent(row)">重试</el-button>
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

        <div
          v-if="row.confirmStatus === 'pending' && (canEdit || canReject)"
          class="proposal-review__card-actions"
        >
          <el-button link type="primary" :loading="confirming" @click="adopt(row)">
            采纳
          </el-button>
          <el-button link type="primary" :loading="confirming" @click="openEdit(row)">
            修改后采纳
          </el-button>
          <el-button
            v-if="canReject"
            link
            type="danger"
            :loading="confirming"
            @click="openReject(row.key)"
          >
            驳回（可附反馈）
          </el-button>
        </div>
      </div>
    </div>

    <!-- 逐项回执：失败项可单项重试（交互 2.3 回执） -->
    <AiConfirmReceipt
      v-if="receipt.length > 0"
      :receipt="receipt"
      :confirming="confirming"
      @retry="retryResult"
      @clear="clearReceipt"
    />

    <!-- 编辑后采纳：字段 schema 由任务类型策略决定 -->
    <el-dialog
      v-model="editVisible"
      :title="reviewTitle"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <template v-for="field in editFields" :key="field.key">
          <el-form-item v-if="field.type === 'ranks'" :label="field.label">
            <ol class="proposal-review__rank-edit">
              <li
                v-for="(item, index) in rankItems"
                :key="item.nodeId"
                class="proposal-review__rank-edit-row"
                :class="{
                  'proposal-review__rank-edit-row--over': overIndex === index && dragIndex >= 0,
                  'proposal-review__rank-edit-row--dragging': dragIndex === index,
                }"
                draggable="true"
                @dragstart="onRankDragStart(index)"
                @dragover.prevent="onRankDragOver(index)"
                @drop.prevent="onRankDrop(index)"
                @dragend="onRankDragEnd"
              >
                <span class="proposal-review__rank-edit-num">{{ index + 1 }}</span>
                <span class="proposal-review__rank-edit-title">{{ item.title }}</span>
                <el-icon class="proposal-review__rank-edit-handle"><OperationIcon /></el-icon>
              </li>
            </ol>
          </el-form-item>
          <el-form-item v-else-if="field.type === 'tree'" :label="field.label">
            <el-tree-select
              :model-value="scalarField(field.key)"
              :data="moduleTree"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              check-strictly
              style="width: 100%"
              @update:model-value="setField(field.key, $event)"
            />
          </el-form-item>
          <el-form-item v-else-if="field.type === 'select'" :label="field.label">
            <el-select
              :model-value="scalarField(field.key)"
              style="width: 100%"
              @update:model-value="setField(field.key, $event)"
            >
              <el-option
                v-for="option in field.options"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="field.type === 'lines'" :label="field.label">
            <el-input
              :model-value="fieldText(field.key)"
              type="textarea"
              :rows="4"
              @update:model-value="setField(field.key, $event)"
            />
          </el-form-item>
          <el-form-item v-else :label="field.label" :required="field.required">
            <el-input
              :model-value="fieldText(field.key)"
              :type="field.type === 'textarea' ? 'textarea' : 'text'"
              :rows="field.type === 'textarea' ? 3 : undefined"
              :maxlength="field.maxlength"
              :placeholder="field.placeholder"
              @update:model-value="setField(field.key, $event)"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" :disabled="!editValid" @click="submitEdit">
          确认采纳
        </el-button>
      </template>
    </el-dialog>

    <!-- 驳回反馈 -->
    <el-dialog v-model="rejectVisible" title="驳回产物" width="480px" :close-on-click-modal="false">
      <el-input
        v-model="rejectNote"
        type="textarea"
        :rows="4"
        :maxlength="NOTE_MAX_LENGTH"
        show-word-limit
        placeholder="反馈可选，用于改进后续生成（不超过 500 字）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="confirming" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
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
