<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Operation as OperationIcon } from '@element-plus/icons-vue'
import type {
  AiProposalConfirmTarget,
  AiTaskDocumentMeta,
  AiTaskProposalSummary,
  ProjectModule,
} from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import { useAiProposalReview } from '@/composables/project/ai/useAiProposalReview'
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
  selectedKeys,
  activeKey,
  activeRow,
  loadingContent,
  moduleTree,
  confirming,
  receipt,
  processed,
  selectableRows,
  allSelected,
  reviewTitle,
  editFields,
  editValuesOf,
  canEdit,
  canReject,
  canBatch,
  needSystemVersion,
  NOTE_MAX_LENGTH,
  syncRows,
  init,
  select,
  toggle,
  toggleAll,
  loadContent,
  handleAdopt,
  handleAdoptEdited,
  handleReject,
  handleBatchAdopt,
  handleBatchReject,
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

// init 首次同步提案行（proposals 由详情页轮询/确认后刷新传入）
init()

// ==================== 系统版本（详设 4.5）====================
/**
 * 未触碰时不提交 target.systemVersion：null → 服务端回退（拆分继承原条目 / 导入回落识别值）；
 * 触碰后提交当前值，空白串为显式清空（采纳后留空待手工补录）。
 */
const versionValue = ref(importVersionPrefill(props.documentMeta ?? null))
const versionTouched = ref(false)

// 识别版本随后到时补预填（缓存详情先渲染的场景），用户已改过则不覆盖
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
function flattenModules(nodes: ProjectModule[], map: Map<string, string>): void {
  for (const node of nodes) {
    map.set(node.id, node.name)
    if (node.children) flattenModules(node.children, map)
  }
}

const moduleNameMap = computed(() => {
  const map = new Map<string, string>()
  flattenModules(moduleTree.value, map)
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

const editValid = computed(() =>
  editFields.value.every((field) => !field.required || fieldText(field.key).trim().length > 0),
)

async function openEdit(): Promise<void> {
  const row = activeRow.value
  if (!row) return
  if (!row.contentLoaded) await loadContent(row)
  editRowKey.value = row.key
  editForm.value = editValuesOf(row)
  const items = editForm.value.items
  rankItems.value = Array.isArray(items)
    ? (items as ProposalRankItem[]).map((item) => ({ ...item }))
    : []
  editVisible.value = true
}

async function editRow(key: string): Promise<void> {
  select(key)
  await openEdit()
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

function adoptActive(): void {
  if (activeRow.value && canEdit.value) void handleAdopt(activeRow.value, targetVersion())
}

function batchAdopt(): void {
  void handleBatchAdopt(targetVersion())
}

function batchReject(): void {
  void handleBatchReject(targetVersion())
}

const selectedCount = computed(() => selectedKeys.value.length)
/** 提案全驳回 → 审核区空态「已全部驳回」（交互 2.4.3） */
const allRejected = computed(
  () => rows.value.length > 0 && rows.value.every((row) => row.confirmStatus === 'rejected'),
)
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
      <!-- 系统版本：导入预填识别版本并悬浮依据引语，未识别给补录提示（交互 06 §2.4） -->
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

    <!-- 拆分场景批量提示（交互 06 §2.4.2） -->
    <el-alert
      v-if="taskType === 'requirement_split'"
      type="info"
      :title="`新条目将继承原条目版本${versionTouched && versionValue.trim() ? `（当前值：${versionValue.trim()}）` : ''}、原条目将归档`"
      show-icon
      :closable="false"
      class="proposal-review__hint"
    />

    <div v-if="allRejected" class="proposal-review__body">
      <el-empty description="已全部驳回，未创建任何数据" :image-size="80">
        <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
      </el-empty>
    </div>

    <div v-else class="proposal-review__body">
      <!-- 提案清单：选择态与编辑态存组件本地，来源页往返不丢失（状态管理 5） -->
      <div class="proposal-review__list">
        <div class="proposal-review__list-head">
          <el-checkbox
            v-if="canBatch"
            :model-value="allSelected"
            :indeterminate="selectedCount > 0 && !allSelected"
            :disabled="selectableRows.length === 0"
            @change="toggleAll($event as boolean)"
          >
            全选
          </el-checkbox>
          <span class="proposal-review__count">已选 {{ selectedCount }} 条 / 共 {{ processed.total }} 条</span>
        </div>

        <div
          v-for="row in rows"
          :key="row.key"
          class="proposal-review__row"
          :class="{
            'proposal-review__row--active': activeKey === row.key,
            'proposal-review__row--handled': row.confirmStatus !== 'pending',
          }"
          @click="select(row.key)"
        >
          <el-checkbox
            v-if="canBatch && row.confirmStatus === 'pending'"
            :value="row.key"
            :disabled="row.confirmStatus !== 'pending'"
            @click.stop
            @change="toggle(row.key, $event as boolean)"
          />
          <div class="proposal-review__row-main">
            <div class="proposal-review__row-title">
              <span class="proposal-review__kind">{{ row.kindLabel }}</span>
              <span class="proposal-review__name">{{ row.title || row.key }}</span>
              <el-tag :type="row.confirmMeta.tagType" size="small" effect="light">
                {{ row.confirmMeta.label }}
              </el-tag>
            </div>
            <div class="proposal-review__row-meta">
              <span v-if="moduleNameOf(row.content.moduleId)">
                模块：{{ moduleNameOf(row.content.moduleId) }}
              </span>
              <span v-if="row.content.sourceRef">来源：{{ row.content.sourceRef }}</span>
              <span v-for="(warning, index) in row.parsed.warnings" :key="index" class="proposal-review__warning">
                {{ warning }}
              </span>
            </div>
          </div>
          <div
            v-if="row.confirmStatus === 'pending' && (canEdit || canReject)"
            class="proposal-review__row-actions"
            @click.stop
          >
            <el-button v-if="canEdit" link type="primary" @click="select(row.key); adoptActive()">采纳</el-button>
            <el-button v-if="canEdit" link type="primary" @click="editRow(row.key)">修改后采纳</el-button>
            <el-button v-if="canReject" link type="danger" @click="openReject(row.key)">驳回</el-button>
          </div>
        </div>

        <el-empty
          v-if="rows.length === 0"
          description="提案为空（执行未产出建议）"
          :image-size="80"
        >
          <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
        </el-empty>
      </div>

      <!-- 内容预览：按任务类型策略渲染结构化视图 -->
      <aside class="proposal-review__preview">
        <template v-if="activeRow">
          <div v-loading="loadingContent" class="proposal-review__preview-body">
            <h3 class="proposal-review__preview-title">{{ activeRow.title || activeRow.key }}</h3>

            <!-- 编辑后采纳的并排对比：既有值 vs AI 建议 -->
            <div v-if="activeRow.parsed.compare.length > 0" class="proposal-review__compare">
              <div
                v-for="(item, index) in activeRow.parsed.compare"
                :key="`${item.label}-${index}`"
                class="proposal-review__compare-row"
                :class="{ 'proposal-review__compare-row--changed': item.changed }"
              >
                <span class="proposal-review__compare-label">{{ item.label }}</span>
                <span>既有：{{ item.existing }}</span>
                <span>建议：{{ item.suggested }}</span>
              </div>
            </div>

            <dl v-if="activeRow.parsed.fields.length > 0" class="proposal-review__fields">
              <div v-for="(item, index) in activeRow.parsed.fields" :key="`${item.label}-${index}`" class="proposal-review__field">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
              <div v-if="moduleNameOf(activeRow.content.moduleId)" class="proposal-review__field">
                <dt>所属模块</dt>
                <dd>{{ moduleNameOf(activeRow.content.moduleId) }}</dd>
              </div>
              <div v-if="activeRow.content.priority" class="proposal-review__field">
                <dt>优先级</dt>
                <dd>{{ activeRow.content.priority }}</dd>
              </div>
            </dl>

            <ol v-if="activeRow.parsed.rankItems.length > 0" class="proposal-review__rank">
              <li v-for="item in activeRow.parsed.rankItems" :key="item.nodeId">
                <span>{{ item.rank }}. {{ item.title }}</span>
                <span v-if="item.reason" class="proposal-review__rank-reason">{{ item.reason }}</span>
              </li>
            </ol>

            <div v-if="activeRow.parsed.sourceRefs.length > 0" class="proposal-review__sources">
              <div v-for="(sourceRef, index) in activeRow.parsed.sourceRefs" :key="`${sourceRef.id}-${index}`">
                <span class="proposal-review__source-title">{{ sourceRef.title || sourceRef.id }}</span>
                <span class="proposal-review__source-quote">{{ sourceRef.quote }}</span>
              </div>
            </div>

            <div class="proposal-review__desc">
              <p
                v-if="activeRow.parsed.description || activeRow.content.description"
                class="proposal-review__desc-text"
              >{{ activeRow.parsed.description || activeRow.content.description }}</p>
              <p v-else class="proposal-review__desc-empty">该提案未提供描述</p>
            </div>
          </div>
          <div v-if="activeRow.confirmStatus === 'pending' && (canEdit || canReject)" class="proposal-review__preview-actions">
            <el-button v-if="canEdit" type="primary" :loading="confirming" @click="adoptActive">采纳</el-button>
            <el-button v-if="canEdit" :loading="confirming" @click="openEdit">修改后采纳</el-button>
            <el-button v-if="canReject" type="danger" plain :loading="confirming" @click="openReject(activeRow.key)">
              驳回（可附反馈）
            </el-button>
          </div>
          <p v-else-if="activeRow.confirmStatus !== 'pending'" class="proposal-review__readonly">
            该提案已处理，仅供回看
          </p>
        </template>
        <el-empty v-else description="选择左侧提案查看详情" :image-size="80" />
      </aside>
    </div>

    <!-- 底部批量操作 -->
    <footer v-if="rows.length > 0 && canBatch" class="proposal-review__foot">
      <div class="proposal-review__foot-actions">
        <el-button type="primary" :disabled="!canBatch || selectedCount === 0" :loading="confirming" @click="batchAdopt">
          批量采纳
        </el-button>
        <el-button v-if="canReject" type="danger" plain :disabled="selectedCount === 0" :loading="confirming" @click="batchReject">
          全部驳回
        </el-button>
      </div>
      <span class="proposal-review__count">已选 {{ selectedCount }} 条 / 共 {{ processed.total }} 条</span>
    </footer>

    <!-- 逐项回执：失败项可单项重试（交互 2.3 回执） -->
    <AiConfirmReceipt
      v-if="receipt.length > 0"
      :receipt="receipt"
      :confirming="confirming"
      @retry="retryResult"
      @clear="clearReceipt"
    />

    <!-- 编辑后采纳：字段 schema 由任务类型策略决定 -->
    <el-dialog v-model="editVisible" :title="reviewTitle" width="640px" :close-on-click-modal="false">
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
          <el-form-item v-else-if="field.type === 'select'" :label="field.label">
            <el-select
              :model-value="fieldText(field.key)"
              clearable
              placeholder="可选"
              style="width: 100%"
              @update:model-value="setField(field.key, $event)"
            >
              <el-option v-for="option in field.options" :key="option.value" :value="option.value" :label="option.label" />
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="field.type === 'tree'" :label="field.label">
            <el-tree-select
              :model-value="fieldText(field.key)"
              :data="moduleTree"
              check-strictly
              clearable
              placeholder="可选"
              style="width: 100%"
              @update:model-value="setField(field.key, $event)"
            />
          </el-form-item>
          <el-form-item v-else-if="field.type === 'textarea' || field.type === 'lines'" :label="field.label">
            <el-input
              :model-value="fieldText(field.key)"
              type="textarea"
              :rows="field.type === 'lines' ? 4 : 6"
              :placeholder="field.type === 'lines' ? '每行一条' : '可选，支持纯文本描述'"
              @update:model-value="setField(field.key, $event)"
            />
          </el-form-item>
          <el-form-item v-else :label="field.label" :required="field.required">
            <el-input
              :model-value="fieldText(field.key)"
              :maxlength="field.maxlength"
              show-word-limit
              placeholder="请输入标题"
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
    <el-dialog v-if="canReject" v-model="rejectVisible" title="驳回提案" width="480px" :close-on-click-modal="false">
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
.proposal-review__count {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.proposal-review__version {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.proposal-review__version-label {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.proposal-review__version-info {
  color: var(--color-neutral-400);
  cursor: help;
}

/* 识别依据引语过长时截断展示，全文走悬浮提示 */
.proposal-review__evidence {
  max-width: 240px;
  overflow: hidden;
  color: var(--color-neutral-500);
  cursor: help;
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proposal-review__hint {
  margin-bottom: var(--space-md);
}

.proposal-review__body {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-md);
}

.proposal-review__list {
  min-width: 0;
}

.proposal-review__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-200);
}

.proposal-review__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
  cursor: pointer;
}

.proposal-review__row--active {
  background: var(--color-primary-50, var(--color-neutral-50));
}

.proposal-review__row--handled {
  opacity: 0.75;
}

.proposal-review__row-main {
  flex: 1;
  min-width: 0;
}

.proposal-review__row-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.proposal-review__kind {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.proposal-review__name {
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 550;
}

.proposal-review__row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-xxs, 2px);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.proposal-review__warning {
  color: var(--color-warning);
}

.proposal-review__row-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-xxs, 2px);
}

.proposal-review__preview {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--space-sm);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.proposal-review__preview-body {
  flex: 1;
  min-height: 160px;
}

.proposal-review__preview-title {
  margin: 0 0 var(--space-sm);
  color: var(--color-neutral-900);
  font-size: var(--font-size-md);
  font-weight: 600;
}

.proposal-review__compare {
  display: flex;
  flex-direction: column;
  gap: var(--space-xxs, 2px);
  margin-bottom: var(--space-sm);
}

.proposal-review__compare-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  padding: var(--space-xxs, 2px) var(--space-xs);
  border-radius: var(--radius-sm, 4px);
  color: var(--color-neutral-700);
  font-size: var(--font-size-xs);
}

.proposal-review__compare-row--changed {
  background: var(--color-warning-light, var(--color-neutral-100));
}

.proposal-review__compare-label {
  min-width: 64px;
  color: var(--color-neutral-500);
}

.proposal-review__fields {
  margin: 0 0 var(--space-sm);
}

.proposal-review__field {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-xxs, 2px);
  font-size: var(--font-size-sm);
}

.proposal-review__field dt {
  flex-shrink: 0;
  color: var(--color-neutral-500);
}

.proposal-review__field dd {
  margin: 0;
  color: var(--color-neutral-700);
}

.proposal-review__rank {
  margin: 0 0 var(--space-sm);
  padding-left: var(--space-lg);
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.proposal-review__rank li {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-bottom: var(--space-xxs, 2px);
}

.proposal-review__rank-reason {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.proposal-review__sources {
  display: flex;
  flex-direction: column;
  gap: var(--space-xxs, 2px);
  margin-bottom: var(--space-sm);
  font-size: var(--font-size-xs);
}

.proposal-review__source-title {
  color: var(--color-primary-500);
}

.proposal-review__source-quote {
  color: var(--color-neutral-500);
}

.proposal-review__desc-text {
  margin: 0;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
}

.proposal-review__desc-empty {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.proposal-review__preview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.proposal-review__readonly {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.proposal-review__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-200);
}

.proposal-review__foot-actions {
  display: flex;
  gap: var(--space-sm);
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
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
  cursor: grab;
}

.proposal-review__rank-edit-row--over {
  border-color: var(--color-primary-500);
}

.proposal-review__rank-edit-row--dragging {
  opacity: 0.6;
}

.proposal-review__rank-edit-num {
  color: var(--color-primary-500);
  font-weight: 600;
}

.proposal-review__rank-edit-title {
  flex: 1;
  color: var(--color-neutral-700);
}

.proposal-review__rank-edit-handle {
  color: var(--color-neutral-400);
}
</style>
