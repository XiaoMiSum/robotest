<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { AiArtifactSummary } from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import { useAiArtifactReview } from '@/composables/project/ai/useAiArtifactReview'
import {
  useGenerationArtifactTree,
  type GenerationTreeNode,
} from '@/composables/project/ai/useGenerationArtifactTree'

const props = defineProps<{
  taskId: string
  artifacts: AiArtifactSummary[]
  canConfirm: boolean
}>()

const emit = defineEmits<{ confirmed: []; leave: [] }>()

const router = useRouter()

const base = useAiArtifactReview(
  props.taskId,
  () => props.artifacts,
  () => props.canConfirm,
  () => {
    base.syncRows()
    emit('confirmed')
  },
)

const tree = useGenerationArtifactTree({
  rows: base.rows,
  selectedKeys: base.selectedKeys,
  confirming: base.confirming,
  canConfirm: base.canConfirm,
  activeKey: base.activeKey,
  select: base.select,
  submitItems: base.submitItems,
})

base.init()
// 行内属性徽标与来源警示依赖内容，打开即预取（并发受限）
void base.preloadContents()

const selectedCount = computed(() => base.selectedKeys.value.length)
const canBatch = computed(() => base.canConfirm.value && selectedCount.value > 0)
/** 产物全驳回 → 审核区空态「已全部驳回」（交互 2.4.3） */
const allRejected = computed(
  () =>
    base.rows.value.length > 0 &&
    base.rows.value.every((row) => row.confirmStatus === 'rejected'),
)

const activeNode = computed(() => tree.findNode(base.activeKey.value) ?? null)

function nodeTitle(node: GenerationTreeNode): string {
  return node.title || node.row.content.title || node.key
}

const PRIORITY_LABEL: Record<string, string> = {
  high: 'P0·高',
  medium: 'P1·中',
  low: 'P2·低',
}

function priorityLabel(priority: string): string {
  return PRIORITY_LABEL[priority] ?? priority
}

function goRequirement(requirementId: string): void {
  if (requirementId) void router.push(`/workspace/projects/requirements/${requirementId}`)
}

// ==================== 编辑后采纳 ====================
const editVisible = ref(false)
const editKey = ref('')
const editForm = ref({ name: '', priority: '', precondition: '', steps: '', expected: '' })
const editNode = computed(() => tree.findNode(editKey.value) ?? null)
const editIsCase = computed(() => editNode.value?.row.content.isTestCase === true)

function splitLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
}

function openEdit(node: GenerationTreeNode): void {
  const content = node.row.content
  editKey.value = node.key
  editForm.value = {
    name: nodeTitle(node),
    priority: content.attributes.priority || 'medium',
    precondition: content.attributes.precondition,
    steps: content.attributes.steps.join('\n'),
    expected: content.attributes.expected.join('\n'),
  }
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const node = editNode.value
  if (!node) return
  const name = editForm.value.name.trim()
  if (!name) return
  // 模块 / 文档以 name 落库、节点以 title（生成链详设 4.3）；属性整包覆盖避免半截数据
  const payload: Record<string, unknown> = editIsCase.value
    ? {
        title: name,
        attributes: {
          priority: editForm.value.priority,
          precondition: editForm.value.precondition,
          steps: splitLines(editForm.value.steps),
          expected: splitLines(editForm.value.expected),
          tags: node.row.content.attributes.tags,
        },
      }
    : node.row.kind === 'test_case_suggestion'
      ? { title: name }
      : { name }
  await tree.adoptEdited(node, payload)
  editVisible.value = false
}

// ==================== 驳回反馈 ====================
const rejectVisible = ref(false)
const rejectNote = ref('')
const rejectKey = ref('')

function openReject(node: GenerationTreeNode): void {
  rejectKey.value = node.key
  rejectNote.value = ''
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  const node = tree.findNode(rejectKey.value)
  if (!node) return
  await tree.rejectOne(node, rejectNote.value)
  rejectVisible.value = false
}

function adopt(node: GenerationTreeNode): void {
  void tree.adoptOne(node)
}

function adoptSubtree(node: GenerationTreeNode): void {
  void tree.adoptSubtree(node)
}
</script>

<template>
  <section class="gen-review">
    <header class="gen-review__head">
      <div class="gen-review__head-main">
        <h2 class="gen-review__title">测试设计产物审核</h2>
        <span class="gen-review__muted">
          已处理 {{ base.processed.value.processed }} / 共 {{ base.processed.value.total }}
        </span>
      </div>
      <span v-if="!canConfirm" class="gen-review__muted">
        当前账号无产物确认权限，仅可预览
      </span>
      <span v-else class="gen-review__muted">
        采纳按模块 → 文档 → 用例顺序落库，可整树采纳
      </span>
    </header>

    <div v-if="allRejected" class="gen-review__body gen-review__body--single">
      <el-empty description="已全部驳回，未创建任何数据" :image-size="80">
        <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
      </el-empty>
    </div>

    <div v-else class="gen-review__body">
      <div class="gen-review__list">
        <div class="gen-review__list-head">
          <el-checkbox
            :model-value="base.allSelected.value"
            :indeterminate="selectedCount > 0 && !base.allSelected.value"
            :disabled="!canConfirm || base.selectableRows.value.length === 0"
            @change="base.toggleAll($event as boolean)"
          >
            全选
          </el-checkbox>
          <span class="gen-review__muted">
            已选 {{ selectedCount }} 条 / 共 {{ base.processed.value.total }} 条
          </span>
        </div>

        <el-checkbox-group v-model="base.selectedKeys.value" :disabled="!canConfirm">
          <div
            v-for="node in tree.visibleNodes.value"
            :key="node.key"
            class="gen-review__row"
            :class="{
              'gen-review__row--active': base.activeKey.value === node.key,
              'gen-review__row--handled': node.row.confirmStatus !== 'pending',
              'gen-review__row--duplicate': tree.isDuplicate(node),
            }"
            @click="base.select(node.key)"
          >
            <span
              class="gen-review__indent"
              :style="{ marginLeft: `${tree.indentLevel(node) * 18}px` }"
            >
              <button
                v-if="node.children.length > 0"
                type="button"
                class="gen-review__toggle"
                :aria-label="tree.isCollapsed(node.key) ? '展开' : '收起'"
                @click.stop="tree.toggleCollapse(node.key)"
              >
                <el-icon><ArrowRight v-if="tree.isCollapsed(node.key)" /><ArrowDown v-else /></el-icon>
              </button>
            </span>
            <el-checkbox
              :value="node.key"
              :disabled="node.row.confirmStatus !== 'pending'"
              @click.stop
              @change="base.toggle(node.key, $event as boolean)"
            />
            <div class="gen-review__row-main">
              <div class="gen-review__row-title">
                <span class="gen-review__kind">{{ node.kindLabel }}</span>
                <span class="gen-review__name">{{ nodeTitle(node) }}</span>
                <el-tag
                  v-if="node.row.content.isTestCase && node.row.content.attributes.priority"
                  size="small"
                  effect="plain"
                >
                  {{ priorityLabel(node.row.content.attributes.priority) }}
                </el-tag>
                <el-tag :type="node.row.confirmMeta.tagType" size="small" effect="light">
                  {{ node.row.confirmMeta.label }}
                </el-tag>
              </div>
              <div class="gen-review__row-meta">
                <el-tag v-if="tree.hasChangedSource(node)" size="small" type="warning" effect="light">
                  来源需求已变更
                </el-tag>
                <el-tag v-if="tree.isDuplicate(node)" size="small" type="danger" effect="light">
                  疑似重复
                </el-tag>
                <span
                  v-if="node.row.content.isTestCase && node.row.content.attributes.steps.length > 0"
                >
                  步骤 {{ node.row.content.attributes.steps.length }} 步
                </span>
              </div>
            </div>
            <div
              v-if="canConfirm && node.row.confirmStatus === 'pending'"
              class="gen-review__row-actions"
              @click.stop
            >
              <el-button link type="primary" @click="adopt(node)">采纳</el-button>
              <el-button
                v-if="node.children.length > 0"
                link
                type="primary"
                @click="adoptSubtree(node)"
              >整树采纳</el-button>
              <el-button link type="primary" @click="openEdit(node)">修改后采纳</el-button>
              <el-button link type="danger" @click="openReject(node)">驳回</el-button>
            </div>
          </div>
        </el-checkbox-group>

        <el-empty
          v-if="base.rows.value.length === 0"
          description="产物为空（模型未产出建议）"
          :image-size="80"
        >
          <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
        </el-empty>
      </div>

      <aside class="gen-review__preview">
        <template v-if="activeNode">
          <div v-loading="base.loadingContent.value" class="gen-review__preview-body">
            <h3 class="gen-review__preview-title">{{ nodeTitle(activeNode) }}</h3>
            <div class="gen-review__preview-tags">
              <el-tag size="small" effect="plain">{{ activeNode.kindLabel }}</el-tag>
              <el-tag
                v-if="activeNode.level === 0"
                size="small"
                type="info"
                effect="plain"
              >顶级目录</el-tag>
              <el-tag
                v-else-if="activeNode.level === 1"
                size="small"
                type="info"
                effect="plain"
              >文档</el-tag>
            </div>

            <el-alert
              v-if="tree.isDuplicate(activeNode)"
              type="error"
              title="疑似与既有数据重复：修改后采纳（改名）或驳回，直接采纳将被拒绝"
              show-icon
              :closable="false"
              class="gen-review__alert"
            />
            <el-alert
              v-if="tree.hasChangedSource(activeNode)"
              type="warning"
              title="来源需求已变更，采纳前请核对需求原文"
              show-icon
              :closable="false"
              class="gen-review__alert"
            />

            <div
              v-if="activeNode.row.content.attributes.steps.length > 0"
              class="gen-review__case"
            >
              <p v-if="activeNode.row.content.attributes.precondition" class="gen-review__case-line">
                <span class="gen-review__case-label">前置条件</span>
                {{ activeNode.row.content.attributes.precondition }}
              </p>
              <ol class="gen-review__steps">
                <li
                  v-for="(step, index) in activeNode.row.content.attributes.steps"
                  :key="index"
                  class="gen-review__step"
                >
                  <span class="gen-review__case-label">步骤 {{ index + 1 }}</span>
                  <span class="gen-review__step-text">{{ step }}</span>
                  <span class="gen-review__step-expected">
                    预期：{{ activeNode.row.content.attributes.expected[index] || '—' }}
                  </span>
                </li>
              </ol>
            </div>

            <div v-if="activeNode.row.content.sourceRefs.length > 0" class="gen-review__sources">
              <p class="gen-review__case-label">来源需求</p>
              <button
                v-for="source in activeNode.row.content.sourceRefs"
                :key="source.requirementId"
                type="button"
                class="gen-review__source"
                :class="{ 'gen-review__source--changed': source.changed }"
                @click="goRequirement(source.requirementId)"
              >
                <el-icon><Link /></el-icon>
                <span class="gen-review__source-quote">{{ source.quote || '查看需求原文' }}</span>
                <el-tag v-if="source.changed" size="small" type="warning" effect="light">已变更</el-tag>
              </button>
            </div>
          </div>

          <div
            v-if="canConfirm && activeNode.row.confirmStatus === 'pending'"
            class="gen-review__preview-actions"
          >
            <el-button
              type="primary"
              :loading="base.confirming.value"
              @click="adopt(activeNode)"
            >采纳</el-button>
            <el-button
              v-if="activeNode.children.length > 0"
              :loading="base.confirming.value"
              @click="adoptSubtree(activeNode)"
            >整树采纳</el-button>
            <el-button :loading="base.confirming.value" @click="openEdit(activeNode)">
              修改后采纳
            </el-button>
            <el-button
              type="danger"
              plain
              :loading="base.confirming.value"
              @click="openReject(activeNode)"
            >驳回（可附反馈）</el-button>
          </div>
          <p v-else-if="!canConfirm" class="gen-review__readonly">
            当前账号无产物确认权限，仅可预览
          </p>
        </template>
        <el-empty v-else description="选择左侧产物查看详情" :image-size="80" />
      </aside>
    </div>

    <footer v-if="canConfirm && base.rows.value.length > 0" class="gen-review__foot">
      <div class="gen-review__foot-actions">
        <el-button
          type="primary"
          :disabled="!canBatch"
          :loading="base.confirming.value"
          @click="tree.adoptSelected()"
        >
          批量采纳
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="!canBatch"
          :loading="base.confirming.value"
          @click="tree.rejectSelected(base.selectedKeys.value)"
        >
          全部驳回
        </el-button>
      </div>
      <span class="gen-review__muted">已选 {{ selectedCount }} 条 / 共 {{ base.processed.value.total }} 条</span>
    </footer>

    <AiConfirmReceipt
      v-if="base.receipt.value.length > 0"
      :receipt="base.receipt.value"
      :confirming="base.confirming.value"
      @retry="base.retryResult"
      @clear="base.clearReceipt"
    />

    <!-- 修改后采纳：AI 原值并排可比（交互 2.4 内容对比） -->
    <el-dialog v-model="editVisible" title="修改后采纳" width="640px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item :label="editIsCase ? '用例标题' : '名称'" required>
          <el-input v-model="editForm.name" maxlength="300" show-word-limit placeholder="请输入名称" />
        </el-form-item>
        <template v-if="editIsCase">
          <div class="gen-review__edit-compare">
            <el-form-item label="优先级">
              <el-select v-model="editForm.priority" style="width: 100%">
                <el-option value="high" label="P0·高" />
                <el-option value="medium" label="P1·中" />
                <el-option value="low" label="P2·低" />
              </el-select>
            </el-form-item>
            <el-form-item label="前置条件">
              <el-input v-model="editForm.precondition" type="textarea" :rows="2" />
            </el-form-item>
          </div>
          <div class="gen-review__edit-compare">
            <el-form-item label="步骤（每行一步）">
              <el-input v-model="editForm.steps" type="textarea" :rows="5" />
            </el-form-item>
            <el-form-item label="预期（每行一条，与步骤一一对应）">
              <el-input v-model="editForm.expected" type="textarea" :rows="5" />
            </el-form-item>
          </div>
        </template>
        <p v-if="editNode" class="gen-review__ai-original">
          AI 建议原值：{{ nodeTitle(editNode) }}
          <template v-if="editIsCase">
            · {{ priorityLabel(editNode.row.content.attributes.priority) || '未定级' }}
            · {{ editNode.row.content.attributes.steps.length }} 步
          </template>
        </p>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="base.confirming.value"
          :disabled="!editForm.name.trim()"
          @click="submitEdit"
        >确认采纳</el-button>
      </template>
    </el-dialog>

    <!-- 驳回反馈 -->
    <el-dialog v-model="rejectVisible" title="驳回产物" width="480px" :close-on-click-modal="false">
      <el-input
        v-model="rejectNote"
        type="textarea"
        :rows="4"
        :maxlength="base.NOTE_MAX_LENGTH"
        show-word-limit
        placeholder="反馈可选，用于改进后续生成（不超过 500 字）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="base.confirming.value" @click="submitReject">
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.gen-review {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.gen-review__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.gen-review__head-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.gen-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.gen-review__muted {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.gen-review__body {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-md);
}

.gen-review__body--single {
  display: block;
}

.gen-review__list {
  min-width: 0;
}

.gen-review__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-200);
}

.gen-review__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
  cursor: pointer;
}

.gen-review__row--active {
  background: var(--color-primary-50, var(--color-neutral-50));
}

.gen-review__row--handled {
  opacity: 0.75;
}

.gen-review__row--duplicate {
  box-shadow: inset 3px 0 0 var(--color-danger);
}

.gen-review__indent {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  width: 24px;
}

.gen-review__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: none;
  border: none;
  color: var(--color-neutral-500);
  cursor: pointer;
}

.gen-review__row-main {
  flex: 1;
  min-width: 0;
}

.gen-review__row-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.gen-review__kind {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.gen-review__name {
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 550;
}

.gen-review__row-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-xxs, 2px);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.gen-review__row-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-xxs, 2px);
}

.gen-review__preview {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--space-sm);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.gen-review__preview-body {
  flex: 1;
  min-height: 160px;
}

.gen-review__preview-title {
  margin: 0 0 var(--space-xs);
  color: var(--color-neutral-900);
  font-size: var(--font-size-md);
  font-weight: 600;
}

.gen-review__preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-bottom: var(--space-sm);
}

.gen-review__alert {
  margin-bottom: var(--space-sm);
}

.gen-review__case-label {
  margin-right: var(--space-xs);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.gen-review__case-line {
  margin: 0 0 var(--space-xs);
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.gen-review__steps {
  margin: 0;
  padding-left: var(--space-md);
}

.gen-review__step {
  margin-bottom: var(--space-xs);
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.gen-review__step-expected {
  display: block;
  color: var(--color-neutral-500);
}

.gen-review__sources {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.gen-review__source {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  width: 100%;
  padding: var(--space-xs) 0;
  background: none;
  border: none;
  color: var(--color-primary-500);
  cursor: pointer;
  font-size: var(--font-size-sm);
  text-align: left;
}

.gen-review__source-quote {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-neutral-700);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gen-review__preview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.gen-review__readonly {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.gen-review__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-200);
}

.gen-review__foot-actions {
  display: flex;
  gap: var(--space-sm);
}

.gen-review__edit-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-sm);
}

.gen-review__ai-original {
  margin: 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}
</style>
