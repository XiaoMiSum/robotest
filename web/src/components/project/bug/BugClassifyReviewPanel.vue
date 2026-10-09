<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type {
  AiArtifactSummary,
  BugPriority,
  BugSeverity,
} from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import { BUG_TYPE_LABEL } from '@/composables/project/bug/bugStatus'
import { formatShortId } from '@/utils/format'
import {
  useBugClassifyReview,
  type BugClassifyEdit,
  type BugClassifyRow,
} from '@/composables/project/bug/useBugClassifyReview'

const props = defineProps<{
  taskId: string
  taskType: string
  artifacts: AiArtifactSummary[]
  canConfirm: boolean
}>()

const emit = defineEmits<{ confirmed: []; leave: [] }>()

const router = useRouter()

const review = useBugClassifyReview(
  props.taskId,
  () => props.artifacts,
  () => props.canConfirm,
  () => {
    review.syncRows()
    emit('confirmed')
  },
)
review.init()

const isDuplicateTask = computed(() => props.taskType === 'bug_duplicate_scan')

const SEVERITY_LABEL: Record<BugSeverity, string> = {
  fatal: '致命',
  serious: '严重',
  general: '一般',
  minor: '轻微',
}
const PRIORITY_LABEL: Record<BugPriority, string> = { high: '高', medium: '中', low: '低' }

// ==================== 修改后采纳对话框 ====================

const editVisible = ref(false)
const editBusy = ref(false)
const editingRow = ref<BugClassifyRow | null>(null)
const editForm = ref<BugClassifyEdit>({
  bugType: 'other',
  severity: 'general',
  priority: 'medium',
  moduleId: '',
  keywords: '',
  assigneeId: '',
})

function openEdit(row: BugClassifyRow): void {
  const suggestions = row.content?.suggestions
  editForm.value = {
    bugType: suggestions?.bugType?.value ?? 'other',
    severity: suggestions?.severity?.value ?? 'general',
    priority: suggestions?.priority?.value ?? 'medium',
    moduleId: suggestions?.moduleId?.value ?? '',
    keywords: suggestions?.keywords?.value.join(' ') ?? '',
    // 批量产物不带指派建议，空值表示不变更指派人
    assigneeId: '',
  }
  editingRow.value = row
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editingRow.value) return
  editBusy.value = true
  try {
    await review.handleAdoptEdited(editingRow.value, editForm.value)
    editVisible.value = false
    editingRow.value = null
  } finally {
    editBusy.value = false
  }
}

// ==================== 驳回（附反馈） ====================

const rejectVisible = ref(false)
const rejectNote = ref('')
const rejectingRow = ref<BugClassifyRow | null>(null)

function openReject(row: BugClassifyRow): void {
  rejectNote.value = ''
  rejectingRow.value = row
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  if (!rejectingRow.value) return
  await review.handleReject(rejectingRow.value, rejectNote.value)
  rejectVisible.value = false
  rejectingRow.value = null
}

// ==================== 重复组确认 ====================

const groupVisible = ref(false)
const groupAction = ref<'adopted' | 'rejected'>('adopted')
const groupNote = ref('')
const groupingRow = ref<BugClassifyRow | null>(null)

function openGroup(row: BugClassifyRow, action: 'adopted' | 'rejected'): void {
  groupNote.value = ''
  groupAction.value = action
  groupingRow.value = row
  groupVisible.value = true
}

async function submitGroup(): Promise<void> {
  if (!groupingRow.value) return
  await review.handleGroupConfirm(groupingRow.value, groupAction.value, groupNote.value)
  groupVisible.value = false
  groupingRow.value = null
}

function openBug(bugId: string): void {
  void router.push(`/workspace/projects/bugs/${bugId}`)
}

function similarityText(value: number): string {
  return `${Math.round(value * 100)}%`
}
</script>

<template>
  <section class="bug-review">
    <header class="bug-review__head">
      <div class="bug-review__head-main">
        <h2 class="bug-review__title">
          {{ isDuplicateTask ? '存量重复分组审核' : '批量分类建议审核' }}
        </h2>
        <span class="bug-review__muted">
          已处理 {{ review.processed.value.processed }} / 共 {{ review.processed.value.total }}
        </span>
      </div>
      <!-- 确认需 ai:confirm；分类采纳落库另需 bug:edit（缺权时只读预览） -->
      <span class="bug-review__muted">
        {{ canConfirm
          ? (isDuplicateTask
              ? '确认仅留痕，不修改任何缺陷，后续按既有「重复缺陷」流程处理'
              : '采纳经既有缺陷服务更新字段；驳回不更新任何缺陷')
          : '当前账号无产物确认权限，仅可预览' }}
      </span>
    </header>

    <!-- 重复扫描任务：分组卡片 + 确认 / 排除 -->
    <template v-if="isDuplicateTask">
      <ul v-for="row in review.duplicateRows.value" :key="row.key" class="bug-review__groups">
        <li v-loading="!row.loaded" class="bug-review__group">
          <div class="bug-review__group-head">
            <span class="bug-review__group-title">
              疑似重复组 · 主缺陷
              <el-link
                v-if="row.group?.canonicalBugId"
                type="primary"
                :underline="false"
                @click="openBug(row.group.canonicalBugId)"
              >
                {{ formatShortId(row.group.canonicalBugId) }}
              </el-link>
              <span v-else class="bug-review__muted">未指定</span>
            </span>
            <el-tag
              v-if="row.confirmStatus !== 'pending'"
              size="small"
              effect="plain"
              :type="row.confirmStatus === 'rejected' ? 'info' : 'success'"
            >
              {{ row.confirmStatus === 'rejected' ? '已排除' : '已确认' }}
            </el-tag>
          </div>
          <ul class="bug-review__dup-items">
            <li v-for="item in row.group?.items ?? []" :key="item.bugId" class="bug-review__dup-item">
              <el-link type="primary" :underline="false" @click="openBug(item.bugId)">
                {{ formatShortId(item.bugId) }}
              </el-link>
              <span class="bug-review__dup-title">{{ item.reason }}</span>
              <span class="bug-review__muted">相似度 {{ similarityText(item.similarity) }}</span>
            </li>
          </ul>
          <div v-if="canConfirm && row.confirmStatus === 'pending'" class="bug-review__group-actions">
            <el-button size="small" type="primary" :loading="review.confirming.value" @click="openGroup(row, 'adopted')">
              确认为重复组
            </el-button>
            <el-button size="small" :loading="review.confirming.value" @click="openGroup(row, 'rejected')">
              排除误报
            </el-button>
          </div>
        </li>
      </ul>
    </template>

    <!-- 批量分类任务：当前 vs 建议对照 + 逐条 / 批量动作 -->
    <template v-else>
      <div v-if="canConfirm" class="bug-review__toolbar">
        <el-checkbox
          :model-value="review.allSelected.value"
          :disabled="review.selectableRows.value.length === 0"
          @change="review.toggleAll($event as boolean)"
        >
          全选待审（{{ review.selectableRows.value.length }}）
        </el-checkbox>
        <div class="bug-review__toolbar-actions">
          <el-button
            size="small"
            type="primary"
            :loading="review.confirming.value"
            :disabled="review.selectedKeys.value.length === 0"
            @click="review.handleBatchAdopt()"
          >
            批量采纳（{{ review.selectedKeys.value.length }}）
          </el-button>
          <el-button
            size="small"
            type="danger"
            plain
            :loading="review.confirming.value"
            :disabled="review.selectedKeys.value.length === 0"
            @click="review.handleBatchReject()"
          >
            批量驳回
          </el-button>
        </div>
      </div>

      <ul class="bug-review__list">
        <li
          v-for="row in review.classifyRows.value"
          :key="row.key"
          v-loading="!row.loaded"
          class="bug-review__row"
        >
          <el-checkbox
            v-if="canConfirm && row.confirmStatus === 'pending'"
            :model-value="review.selectedKeys.value.includes(row.key)"
            @change="review.toggle(row.key, $event as boolean)"
          />
          <div class="bug-review__row-main">
            <div class="bug-review__row-head">
              <el-link type="primary" :underline="false" @click="openBug(row.content?.bugId ?? '')">
                {{ row.content?.bugId ? formatShortId(row.content.bugId) : row.title }}
              </el-link>
              <el-tag
                v-if="row.confirmStatus !== 'pending'"
                size="small"
                effect="plain"
                :type="row.confirmStatus === 'rejected' ? 'info' : 'success'"
              >
                {{ row.confirmStatus === 'rejected' ? '已驳回' : '已采纳' }}
              </el-tag>
            </div>

            <!-- 当前 vs 建议：字段级对照，值不一致时建议列高亮（交互 2.3） -->
            <table v-if="row.content" class="bug-review__compare">
              <tbody>
                <tr>
                  <th>类型</th>
                  <td>{{ row.current ? BUG_TYPE_LABEL[row.current.bugType] : '—' }}</td>
                  <td :class="{ 'bug-review__diff': row.content.suggestions.bugType && row.current && row.content.suggestions.bugType.value !== row.current.bugType }">
                    {{ row.content.suggestions.bugType
                      ? BUG_TYPE_LABEL[row.content.suggestions.bugType.value]
                      : '保持不变' }}
                  </td>
                </tr>
                <tr>
                  <th>严重等级</th>
                  <td>{{ row.current ? SEVERITY_LABEL[row.current.severity] : '—' }}</td>
                  <td :class="{ 'bug-review__diff': row.content.suggestions.severity && row.current && row.content.suggestions.severity.value !== row.current.severity }">
                    {{ row.content.suggestions.severity
                      ? SEVERITY_LABEL[row.content.suggestions.severity.value]
                      : '保持不变' }}
                  </td>
                </tr>
                <tr>
                  <th>优先级</th>
                  <td>{{ row.current ? PRIORITY_LABEL[row.current.priority] : '—' }}</td>
                  <td :class="{ 'bug-review__diff': row.content.suggestions.priority && row.current && row.content.suggestions.priority.value !== row.current.priority }">
                    {{ row.content.suggestions.priority
                      ? PRIORITY_LABEL[row.content.suggestions.priority.value]
                      : '保持不变' }}
                  </td>
                </tr>
                <tr>
                  <th>所属模块</th>
                  <td>{{ row.current ? review.moduleNameOf(row.current.moduleId) : '—' }}</td>
                  <td :class="{ 'bug-review__diff': row.content.suggestions.moduleId && row.current && (row.content.suggestions.moduleId.value ?? '') !== (row.current.moduleId ?? '') }">
                    {{ row.content.suggestions.moduleId
                      ? review.moduleNameOf(row.content.suggestions.moduleId.value ?? null)
                      : '保持不变' }}
                  </td>
                </tr>
                <tr>
                  <th>关键词</th>
                  <td>{{ row.current?.keywords || '—' }}</td>
                  <td :class="{ 'bug-review__diff': !!row.content.suggestions.keywords?.value.length }">
                    {{ row.content.suggestions.keywords?.value.join('、') || '保持不变' }}
                  </td>
                </tr>
                <tr>
                  <th>处理人</th>
                  <td>{{ row.current?.assigneeName || '未指派' }}</td>
                  <td class="bug-review__muted">保持不变</td>
                </tr>
              </tbody>
            </table>

            <p v-if="row.content?.suggestions.severity?.reason" class="bug-review__reason">
              {{ row.content.suggestions.severity.reason }}
            </p>

            <div v-if="canConfirm && row.confirmStatus === 'pending'" class="bug-review__row-actions">
              <el-button
                size="small"
                type="primary"
                plain
                :loading="review.confirming.value"
                @click="review.handleAdopt(row)"
              >
                采纳
              </el-button>
              <el-button size="small" :loading="review.confirming.value" @click="openEdit(row)">
                修改后采纳
              </el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :loading="review.confirming.value"
                @click="openReject(row)"
              >
                驳回（可附反馈）
              </el-button>
            </div>
          </div>
        </li>
      </ul>
    </template>

    <AiConfirmReceipt
      v-if="review.receipt.value.length > 0"
      :receipt="review.receipt.value"
      :confirming="review.confirming.value"
      @retry="review.retryResult"
      @clear="review.clearReceipt"
    />

    <!-- 修改后采纳：编辑值覆盖产物内容，服务端按部分更新落库（详设 3.6.5） -->
    <el-dialog v-model="editVisible" title="修改后采纳" width="480px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="缺陷类型">
          <el-select v-model="editForm.bugType">
            <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select v-model="editForm.severity">
            <el-option v-for="(label, key) in SEVERITY_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="editForm.priority">
            <el-option v-for="(label, key) in PRIORITY_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属模块">
          <el-tree-select
            v-model="editForm.moduleId"
            :data="review.moduleTree.value"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="选择所属模块（可选）"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="editForm.keywords" placeholder="多个关键词用空格分隔（不超过 5 个）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editBusy" @click="submitEdit">采纳</el-button>
      </template>
    </el-dialog>

    <!-- 单条驳回反馈 -->
    <el-dialog v-model="rejectVisible" title="驳回分类建议" width="480px" :close-on-click-modal="false">
      <el-input
        v-model="rejectNote"
        type="textarea"
        :rows="4"
        :maxlength="500"
        show-word-limit
        placeholder="反馈可选，用于改进后续建议（不超过 500 字）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="review.confirming.value" @click="submitReject">
          确认驳回
        </el-button>
      </template>
    </el-dialog>

    <!-- 重复组确认（留痕） -->
    <el-dialog
      v-model="groupVisible"
      :title="groupAction === 'adopted' ? '确认疑似重复组' : '排除误报'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-alert
        :type="groupAction === 'adopted' ? 'warning' : 'info'"
        :title="groupAction === 'adopted'
          ? '确认仅记录结论留痕，不会合并或修改任何缺陷；请在缺陷中按既有「重复缺陷」流程逐条处理'
          : '排除仅记录结论留痕，不会修改任何缺陷'"
        show-icon
        :closable="false"
        class="bug-review__dialog-alert"
      />
      <el-input
        v-model="groupNote"
        type="textarea"
        :rows="3"
        :maxlength="500"
        show-word-limit
        placeholder="结论说明可选（不超过 500 字）"
      />
      <template #footer>
        <el-button @click="groupVisible = false">取消</el-button>
        <el-button
          :type="groupAction === 'adopted' ? 'primary' : 'default'"
          :loading="review.confirming.value"
          @click="submitGroup"
        >
          {{ groupAction === 'adopted' ? '确认' : '排除' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.bug-review {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.bug-review__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.bug-review__head-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.bug-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.bug-review__muted {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.bug-review__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-200);
}

.bug-review__toolbar-actions {
  display: flex;
  gap: var(--space-sm);
}

.bug-review__list,
.bug-review__groups {
  margin: 0;
  padding: 0;
  list-style: none;
}

.bug-review__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
}

.bug-review__row-main {
  min-width: 0;
  flex: 1;
}

.bug-review__row-head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.bug-review__compare {
  width: 100%;
  margin-top: var(--space-xs);
  border-collapse: collapse;
  font-size: var(--font-size-sm);

  th,
  td {
    padding: var(--space-xxs, 2px) var(--space-sm);
    text-align: left;
    border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
  }

  th {
    width: 88px;
    color: var(--color-neutral-500);
    font-weight: 400;
  }

  td:first-of-type {
    color: var(--color-neutral-600);
  }
}

// 建议值与当前值不一致：差异列标记，不单靠颜色（有「保持不变」文本语义）
.bug-review__diff {
  color: var(--color-primary-600, var(--color-primary-500));
  font-weight: 600;
}

.bug-review__reason {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.bug-review__row-actions,
.bug-review__group-actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
}

.bug-review__group {
  margin-top: var(--space-sm);
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.bug-review__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.bug-review__group-title {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.bug-review__dup-items {
  margin: var(--space-xs) 0 0;
  padding: 0;
  list-style: none;
}

.bug-review__dup-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xxs, 2px) 0;
}

.bug-review__dup-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.bug-review__dialog-alert {
  margin-bottom: var(--space-md);
}
</style>
