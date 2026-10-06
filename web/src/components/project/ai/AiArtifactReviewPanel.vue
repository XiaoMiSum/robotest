<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type {
  AiArtifactConfirmTarget,
  AiArtifactSummary,
  AiTaskDocumentMeta,
  ProjectModule,
} from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import { useAiArtifactReview } from '@/composables/project/ai/useAiArtifactReview'
import {
  importVersionEvidence,
  importVersionHint,
  importVersionPrefill,
} from '@/composables/project/ai/taskPresentation'

const props = defineProps<{
  taskId: string
  taskType: string
  artifacts: AiArtifactSummary[]
  canConfirm: boolean
  /** 导入任务的文档级识别版本与依据（详设 3.6.3），其余任务为 null */
  documentMeta?: AiTaskDocumentMeta | null
}>()

const emit = defineEmits<{ confirmed: []; leave: [] }>()

/** 需求域（拆分 / 导入）展示系统版本输入与需求字段，其余域只读预览产物 */
const isRequirementDomain = computed(
  () => props.taskType === 'requirement_split' || props.taskType === 'requirement_import',
)
const reviewTitle = computed(() =>
  props.taskType === 'requirement_import' ? '导入结果审核' : '拆分结果审核',
)

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
  canConfirm,
  NOTE_MAX_LENGTH,
  syncRows,
  init,
  select,
  toggle,
  toggleAll,
  handleAdopt,
  handleAdoptEdited,
  handleReject,
  handleBatchAdopt,
  handleBatchReject,
  retryResult,
  clearReceipt,
} = useAiArtifactReview(
  props.taskId,
  () => props.artifacts,
  () => props.canConfirm,
  () => {
    syncRows()
    emit('confirmed')
  },
)

// init 首次同步产物行（artifacts 由详情页轮询/确认后刷新传入）
init()

// ==================== 系统版本（详设 4.5）====================
/**
 * 未触碰时不提交 target.systemVersion：null → 服务端回退（拆分继承原条目 / 导入回落识别值）；
 * 触碰后提交当前值，空白串为显式清空（采纳后留空待手工补录）。
 * 导入任务以识别版本预填但不置 touched：未触碰仍不提交，服务端回退同值（交互 06 §2.4）。
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

function targetVersion(): AiArtifactConfirmTarget | undefined {
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
const editForm = ref({ title: '', description: '', moduleId: '', priority: '' })
const editRowKey = ref('')

const priorityOptions = [
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
]

function openEdit(): void {
  const row = activeRow.value
  if (!row) return
  editRowKey.value = row.key
  editForm.value = {
    title: row.content.title || row.title || '',
    description: row.content.description,
    moduleId: row.content.moduleId,
    priority: row.content.priority,
  }
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const row = rows.value.find((item) => item.key === editRowKey.value)
  if (!row) return
  if (!editForm.value.title.trim()) return
  await handleAdoptEdited(row, editForm.value, targetVersion())
  editVisible.value = false
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
  await handleReject(row, rejectNote.value, targetVersion())
  rejectVisible.value = false
}

function adoptActive(): void {
  if (activeRow.value) void handleAdopt(activeRow.value, targetVersion())
}

function batchAdopt(): void {
  void handleBatchAdopt(targetVersion())
}

function batchReject(): void {
  void handleBatchReject(targetVersion())
}

const selectedCount = computed(() => selectedKeys.value.length)
const canBatch = computed(() => canConfirm.value && selectedCount.value > 0)
/** 产物全驳回 → 审核区空态「已全部驳回」（交互 2.4.3） */
const allRejected = computed(
  () => rows.value.length > 0 && rows.value.every((row) => row.confirmStatus === 'rejected'),
)
</script>

<template>
  <section class="artifact-review">
    <header class="artifact-review__head">
      <div class="artifact-review__head-main">
        <h2 class="artifact-review__title">
          {{ isRequirementDomain ? reviewTitle : '产物审核' }}
        </h2>
        <span class="artifact-review__processed">
          已处理 {{ processed.processed }} / 共 {{ processed.total }}
        </span>
      </div>
      <!-- 系统版本：导入预填识别版本并悬浮依据引语，未识别给补录提示（交互 06 §2.4） -->
      <div v-if="isRequirementDomain && canConfirm" class="artifact-review__version">
        <span class="artifact-review__version-label">版本</span>
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
          <span class="artifact-review__evidence">{{ versionEvidence }}</span>
        </el-tooltip>
        <el-tooltip :content="versionHint" placement="top">
          <el-icon class="artifact-review__version-info"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
    </header>

    <!-- 拆分场景批量提示（交互 06 §2.4.2） -->
    <el-alert
      v-if="isRequirementDomain && taskType === 'requirement_split' && canConfirm"
      type="info"
      :title="`新条目将继承原条目版本${versionTouched && versionValue.trim() ? `（当前值：${versionValue.trim()}）` : ''}、原条目将归档`"
      show-icon
      :closable="false"
      class="artifact-review__hint"
    />

    <div v-if="allRejected" class="artifact-review__body">
      <el-empty description="已全部驳回，未创建任何数据" :image-size="80">
        <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
      </el-empty>
    </div>

    <div v-else class="artifact-review__body">
      <!-- 产物清单：选择态与编辑态存组件本地，来源页往返不丢失（状态管理 5） -->
      <div class="artifact-review__list">
        <div class="artifact-review__list-head">
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="selectedCount > 0 && !allSelected"
            :disabled="!canConfirm || selectableRows.length === 0"
            @change="toggleAll($event as boolean)"
          >
            全选
          </el-checkbox>
          <span class="artifact-review__count">已选 {{ selectedCount }} 条 / 共 {{ processed.total }} 条</span>
        </div>

        <el-checkbox-group v-model="selectedKeys" :disabled="!canConfirm">
          <div
            v-for="row in rows"
            :key="row.key"
            class="artifact-review__row"
            :class="{
              'artifact-review__row--active': activeKey === row.key,
              'artifact-review__row--handled': row.confirmStatus !== 'pending',
            }"
            @click="select(row.key)"
          >
            <el-checkbox
              :value="row.key"
              :disabled="row.confirmStatus !== 'pending'"
              @click.stop
              @change="toggle(row.key, $event as boolean)"
            />
            <div class="artifact-review__row-main">
              <div class="artifact-review__row-title">
                <span class="artifact-review__kind">{{ row.kindLabel }}</span>
                <span class="artifact-review__name">{{ row.title || row.key }}</span>
                <el-tag :type="row.confirmMeta.tagType" size="small" effect="light">
                  {{ row.confirmMeta.label }}
                </el-tag>
              </div>
              <div class="artifact-review__row-meta">
                <span v-if="moduleNameOf(row.content.moduleId)">
                  模块：{{ moduleNameOf(row.content.moduleId) }}
                </span>
                <span v-if="row.content.sourceRef">来源：{{ row.content.sourceRef }}</span>
              </div>
            </div>
            <div v-if="canConfirm && row.confirmStatus === 'pending'" class="artifact-review__row-actions" @click.stop>
              <el-button link type="primary" @click="select(row.key); adoptActive()">采纳</el-button>
              <el-button link type="primary" @click="select(row.key); openEdit()">修改后采纳</el-button>
              <el-button link type="danger" @click="openReject(row.key)">驳回</el-button>
            </div>
          </div>
        </el-checkbox-group>

        <el-empty
          v-if="rows.length === 0"
          description="产物为空（解析未产出建议）"
          :image-size="80"
        >
          <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
        </el-empty>
      </div>

      <!-- 内容预览：只读渲染当前选中产物 -->
      <aside class="artifact-review__preview">
        <template v-if="activeRow">
          <div v-loading="loadingContent" class="artifact-review__preview-body">
            <h3 class="artifact-review__preview-title">{{ activeRow.title || activeRow.key }}</h3>
            <dl class="artifact-review__fields">
              <div v-if="moduleNameOf(activeRow.content.moduleId)" class="artifact-review__field">
                <dt>所属模块</dt>
                <dd>{{ moduleNameOf(activeRow.content.moduleId) }}</dd>
              </div>
              <div v-if="activeRow.content.priority" class="artifact-review__field">
                <dt>优先级</dt>
                <dd>{{ activeRow.content.priority }}</dd>
              </div>
              <div v-if="activeRow.content.sourceRef" class="artifact-review__field">
                <dt>来源引用</dt>
                <dd>{{ activeRow.content.sourceRef }}</dd>
              </div>
            </dl>
            <div class="artifact-review__desc">
              <p
                v-if="activeRow.content.description"
                class="artifact-review__desc-text"
              >{{ activeRow.content.description }}</p>
              <p v-else class="artifact-review__desc-empty">该产物未提供描述</p>
            </div>
          </div>
          <div v-if="canConfirm && activeRow.confirmStatus === 'pending'" class="artifact-review__preview-actions">
            <el-button type="primary" :loading="confirming" @click="adoptActive">采纳</el-button>
            <el-button :loading="confirming" @click="openEdit">修改后采纳</el-button>
            <el-button type="danger" plain :loading="confirming" @click="openReject(activeRow.key)">
              驳回（可附反馈）
            </el-button>
          </div>
          <p v-else-if="!canConfirm" class="artifact-review__readonly">当前账号无产物确认权限，仅可预览</p>
        </template>
        <el-empty v-else description="选择左侧产物查看详情" :image-size="80" />
      </aside>
    </div>

    <!-- 底部批量操作 -->
    <footer v-if="canConfirm && rows.length > 0" class="artifact-review__foot">
      <div class="artifact-review__foot-actions">
        <el-button type="primary" :disabled="!canBatch" :loading="confirming" @click="batchAdopt">
          批量采纳
        </el-button>
        <el-button type="danger" plain :disabled="!canBatch" :loading="confirming" @click="batchReject">
          全部驳回
        </el-button>
      </div>
      <span class="artifact-review__count">已选 {{ selectedCount }} 条 / 共 {{ processed.total }} 条</span>
    </footer>

    <!-- 逐项回执：失败项可单项重试（交互 2.3 回执） -->
    <AiConfirmReceipt
      v-if="receipt.length > 0"
      :receipt="receipt"
      :confirming="confirming"
      @retry="retryResult"
      @clear="clearReceipt"
    />

    <!-- 编辑后采纳 -->
    <el-dialog v-model="editVisible" title="修改后采纳" width="640px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="editForm.title" maxlength="300" show-word-limit placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="6"
            placeholder="可选，支持纯文本描述"
          />
        </el-form-item>
        <div class="artifact-review__edit-row">
          <el-form-item label="所属模块">
            <el-tree-select
              v-model="editForm.moduleId"
              :data="moduleTree"
              check-strictly
              clearable
              placeholder="可选"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="editForm.priority" clearable placeholder="可选" style="width: 100%">
              <el-option v-for="option in priorityOptions" :key="option.value" v-bind="option" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" :disabled="!editForm.title.trim()" @click="submitEdit">
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
.artifact-review {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.artifact-review__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.artifact-review__head-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.artifact-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.artifact-review__processed,
.artifact-review__count {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.artifact-review__version {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.artifact-review__version-label {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.artifact-review__version-info {
  color: var(--color-neutral-400);
  cursor: help;
}

/* 识别依据引语过长时截断展示，全文走悬浮提示 */
.artifact-review__evidence {
  max-width: 240px;
  overflow: hidden;
  color: var(--color-neutral-500);
  cursor: help;
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-review__hint {
  margin-bottom: var(--space-md);
}

.artifact-review__body {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-md);
}

.artifact-review__list {
  min-width: 0;
}

.artifact-review__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-200);
}

.artifact-review__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
  cursor: pointer;
}

.artifact-review__row--active {
  background: var(--color-primary-50, var(--color-neutral-50));
}

.artifact-review__row--handled {
  opacity: 0.75;
}

.artifact-review__row-main {
  flex: 1;
  min-width: 0;
}

.artifact-review__row-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.artifact-review__kind {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.artifact-review__name {
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 550;
}

.artifact-review__row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-xxs, 2px);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.artifact-review__row-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-xxs, 2px);
}

.artifact-review__preview {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--space-sm);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.artifact-review__preview-body {
  flex: 1;
  min-height: 160px;
}

.artifact-review__preview-title {
  margin: 0 0 var(--space-sm);
  color: var(--color-neutral-900);
  font-size: var(--font-size-md);
  font-weight: 600;
}

.artifact-review__fields {
  margin: 0 0 var(--space-sm);
}

.artifact-review__field {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-xxs, 2px);
  font-size: var(--font-size-sm);
}

.artifact-review__field dt {
  flex-shrink: 0;
  color: var(--color-neutral-500);
}

.artifact-review__field dd {
  margin: 0;
  color: var(--color-neutral-700);
}

.artifact-review__desc-text {
  margin: 0;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
}

.artifact-review__desc-empty {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.artifact-review__preview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.artifact-review__readonly {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.artifact-review__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-200);
}

.artifact-review__foot-actions {
  display: flex;
  gap: var(--space-sm);
}

.artifact-review__edit-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-sm);
}
</style>
