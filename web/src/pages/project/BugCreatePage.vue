<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'
import { changeBugStatus, createBug, fetchModuleTree, fetchPlans, getBugDetail, uploadBugAttachment } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import { useAiStore } from '@/stores/ai'
import type {
  AiBugDedupItem,
  BugPriority,
  BugSeverity,
  BugType,
  TestCaseModule,
  TestPlanListItem,
  WorkspaceMember,
} from '@/types'
import { BUG_STATUS_LABEL, BUG_STATUS_TAG_TYPE, BUG_TYPE_LABEL } from '@/utils/bugStatus'
import CaseSelector from '@/components/project/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import BugAiSuggest from '@/components/project/BugAiSuggest.vue'
import BugDedupList from '@/components/project/BugDedupList.vue'

const route = useRoute()
const router = useRouter()
const aiStore = useAiStore()
// AI 能力随工作空间开关显隐；关闭时表单回退 V1.0 形态（交互设计 1）
const aiEnabled = computed(() => aiStore.aiEnabled)
const formRef = ref<FormInstance>()
const submitting = ref(false)

// 查重命中列表由 BugDedupList 上抛维护，提交时据此决定是否拦截确认（无命中不弹层）
const dedupItems = ref<AiBugDedupItem[]>([])
const dedupConfirmVisible = ref(false)
const dedupSubmitting = ref(false)
// 确认层内「原始缺陷」的选中 id；卡片「选为原始」预选先写入，弹层以其为默认选中
const dedupTargetId = ref('')

// AI 建议仅回填表单待用户确认（交互设计 2.1），提交前可任意修改
function applyTitle(title: string): void {
  form.title = title
}
function applySeverity(severity: BugSeverity): void {
  form.severity = severity
}
function applyPriority(priority: BugPriority): void {
  form.priority = priority
}

const form = reactive({
  title: '',
  bugType: 'code_error' as BugType,
  moduleId: '' as string,
  severity: 'general' as BugSeverity,
  priority: 'medium' as BugPriority,
  dueDate: '' as string,
  keywords: '',
  reproSteps: '',
  assigneeId: '' as string,
  relatedCaseId: '' as string,
  relatedPlanId: '' as string,
})

const severityLabel: Record<BugSeverity, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<BugPriority, string> = { high: '高', medium: '中', low: '低' }

const moduleTree = ref<TestCaseModule[]>([])
async function loadModuleTree() {
  try {
    moduleTree.value = await fetchModuleTree()
  } catch { /* ignore */ }
}
loadModuleTree()

const caseSelectorVisible = ref(false)
const selectedCaseTitle = ref('')
// 单选模式下选择器只会返回一个用例
function handleCaseSelected(nodes: { documentId: string; caseIds: string[] }[]) {
  if (nodes.length && nodes[0].caseIds.length) {
    form.relatedCaseId = nodes[0].caseIds[0]
    selectedCaseTitle.value = `已选 1 个用例`
  }
}

const planOptions = ref<TestPlanListItem[]>([])
async function loadPlanOptions() {
  try {
    const page = await fetchPlans({ status: 'in_progress', pageNo: 1, pageSize: 50 })
    planOptions.value = page.list
  } catch { /* ignore */ }
}

// 列表"复制"入口经 ?copyFrom= 回填源缺陷信息，处理人不复制由提交人重新指派
async function applyCopySource() {
  const copyFrom = String(route.query.copyFrom ?? '')
  if (!copyFrom) return
  try {
    const src = await getBugDetail(copyFrom)
    form.title = src.title
    form.bugType = src.bugType
    form.moduleId = src.moduleId ?? ''
    form.severity = src.severity
    form.priority = src.priority
    form.dueDate = src.dueDate ?? ''
    form.keywords = src.keywords ?? ''
    form.reproSteps = src.reproSteps ?? ''
    form.relatedCaseId = src.relatedCaseId ?? ''
    if (form.relatedCaseId) selectedCaseTitle.value = '已选 1 个用例'
    // 计划下拉仅含进行中的计划，源计划已结束时放弃回填避免下拉显示原始 id
    const planId = src.relatedPlanId ?? ''
    form.relatedPlanId = planOptions.value.some((p) => p.id === planId) ? planId : ''
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载源缺陷信息失败')
  }
}
loadPlanOptions().then(applyCopySource)

const rules: FormRules = {
  title: [{ required: true, message: '请输入缺陷标题', trigger: 'blur' }],
  bugType: [{ required: true, message: '请选择缺陷类型', trigger: 'change' }],
  severity: [{ required: true, message: '请选择严重等级', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  assigneeId: [{ required: true, message: '请选择处理人', trigger: 'change' }],
}

const memberOptions = ref<WorkspaceMember[]>([])
async function loadMembers() {
  try {
    const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
    memberOptions.value = page.list
  } catch {
    // 加载失败不阻塞
  }
}
loadMembers()

// 附件本地暂存，创建成功拿到 bugId 后再逐个上传
const MAX_FILE_SIZE = 10 * 1024 * 1024
const attachmentFiles = ref<UploadUserFile[]>([])
function handleAttachmentChange(_file: UploadUserFile, files: UploadUserFile[]) {
  attachmentFiles.value = files.filter((f) => {
    if (f.size && f.size > MAX_FILE_SIZE) {
      ElMessage.warning(`「${f.name}」超过 10MB，已忽略`)
      return false
    }
    return true
  })
}
function handleAttachmentRemove(_file: UploadUserFile, files: UploadUserFile[]) {
  attachmentFiles.value = files
}

// 卡片「选为原始」预选 → 确认层默认选中该原始缺陷
function handleSelectDuplicate(item: AiBugDedupItem | null): void {
  dedupTargetId.value = item ? item.bugId : ''
}

// 查重列表内「放弃提交」：直接返回列表页（与底部「取消」同义，交互设计 3.3）
function handleAbandonSubmit(): void {
  router.push('/workspace/projects/bugs')
}

// 创建 + 上传附件（可选：创建后立即标记为重复缺陷，复用 V1.0 resolution 机制，需求 3.4.2）
async function runCreate(duplicateOfBugId?: string): Promise<void> {
  submitting.value = true
  try {
    const bugId = await createBug({
      title: form.title.trim(),
      severity: form.severity,
      priority: form.priority,
      bugType: form.bugType,
      reproSteps: form.reproSteps.trim() || undefined,
      moduleId: form.moduleId || undefined,
      keywords: form.keywords.trim() || undefined,
      dueDate: form.dueDate || undefined,
      assigneeId: form.assigneeId,
      relatedCaseId: form.relatedCaseId || undefined,
      relatedPlanId: form.relatedPlanId || undefined,
    })
    for (const item of attachmentFiles.value) {
      if (item.raw) {
        await uploadBugAttachment(bugId, item.raw)
      }
    }
    if (duplicateOfBugId) {
      await changeBugStatus(bugId, {
        status: 'resolved',
        resolution: 'duplicate',
        duplicateOfBugId,
        comment: '创建时标记为重复缺陷',
      })
    }
    ElMessage.success(duplicateOfBugId ? '缺陷已提交并标记为重复' : '缺陷已提交')
    router.push('/workspace/projects/bugs')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // 查重命中时先经确认层由用户决策（放弃/继续/继续并标记重复）；无命中保持原直提路径
  if (dedupItems.value.length > 0) {
    dedupConfirmVisible.value = true
    return
  }
  await runCreate()
}

function handleDedupAbandon(): void {
  if (dedupSubmitting.value) return
  dedupConfirmVisible.value = false
  router.push('/workspace/projects/bugs')
}

async function handleDedupContinue(): Promise<void> {
  if (dedupSubmitting.value) return
  dedupSubmitting.value = true
  try {
    await runCreate()
  } finally {
    dedupSubmitting.value = false
  }
}

async function handleDedupMarkDuplicate(): Promise<void> {
  if (dedupSubmitting.value) return
  if (!dedupTargetId.value) {
    ElMessage.warning('请选择要标记为重复所对应的原始缺陷')
    return
  }
  dedupSubmitting.value = true
  try {
    await runCreate(dedupTargetId.value)
  } finally {
    dedupSubmitting.value = false
  }
}
</script>

<template>
  <div class="bug-create">
    <el-page-header @back="router.push('/workspace/projects/bugs')">
      <template #content><span class="bug-create__title">提交缺陷</span></template>
    </el-page-header>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="bug-create__form">
      <div class="bug-create__layout">
        <div class="bug-create__main">
          <el-card shadow="never">
            <template #header><span class="bug-create__section">基本信息</span></template>
            <el-form-item label="标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="用一句话描述缺陷现象"
                maxlength="300"
                show-word-limit
                size="large"
              />
            </el-form-item>
            <BugDedupList
              v-if="aiEnabled"
              :title="form.title"
              :repro-steps="form.reproSteps"
              class="bug-create__dedup"
              @dedup-change="dedupItems = $event"
              @select-duplicate="handleSelectDuplicate"
              @abandon-submit="handleAbandonSubmit"
            />
            <el-form-item label="重现步骤" class="bug-create__repro">
              <MarkdownEditor v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown，可选）" />
            </el-form-item>
          </el-card>

          <el-card shadow="never">
            <template #header><span class="bug-create__section">附件</span></template>
            <el-upload
              drag
              :auto-upload="false"
              :file-list="attachmentFiles"
              multiple
              :on-change="handleAttachmentChange"
              :on-remove="handleAttachmentRemove"
            >
              <el-icon class="bug-create__upload-icon"><UploadFilled /></el-icon>
              <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
              <template #tip>
                <div class="bug-create__hint">单个文件不超过 10MB，创建后自动上传</div>
              </template>
            </el-upload>
          </el-card>
        </div>

        <div class="bug-create__side">
          <el-card shadow="never">
            <template #header><span class="bug-create__section">属性</span></template>
            <el-form-item label="缺陷类型" prop="bugType">
              <el-select v-model="form.bugType">
                <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="所属模块">
              <el-tree-select
                v-model="form.moduleId"
                :data="moduleTree"
                :props="{ label: 'name', children: 'children' }"
                node-key="id"
                check-strictly
                clearable
                placeholder="选择所属模块（可选）"
              />
            </el-form-item>
            <el-form-item label="严重等级" prop="severity">
              <el-select v-model="form.severity">
                <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key">
                  <span class="bug-create__severity-dot" :class="`bug-create__severity-dot--${key}`" />{{ label }}
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority">
                <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="截止日期">
              <el-date-picker
                v-model="form.dueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择截止日期（可选）"
                class="bug-create__date"
              />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="form.keywords" placeholder="多个关键词用空格分隔（可选）" maxlength="255" />
            </el-form-item>
          </el-card>

          <el-card shadow="never">
            <template #header><span class="bug-create__section">指派与关联</span></template>
            <el-form-item label="指派给" prop="assigneeId">
              <el-select v-model="form.assigneeId" filterable placeholder="选择处理人">
                <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
              </el-select>
            </el-form-item>
            <el-form-item label="关联用例">
              <el-button @click="caseSelectorVisible = true">选择用例</el-button>
              <span v-if="selectedCaseTitle" class="bug-create__hint">{{ selectedCaseTitle }}</span>
            </el-form-item>
            <el-form-item label="关联计划">
              <el-select v-model="form.relatedPlanId" filterable clearable placeholder="选择计划（可选）">
                <el-option v-for="p in planOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-card>

          <BugAiSuggest
            v-if="aiEnabled"
            :title="form.title"
            :repro-steps="form.reproSteps"
            @apply-title="applyTitle"
            @apply-severity="applySeverity"
            @apply-priority="applyPriority"
          />
        </div>
      </div>

      <div class="bug-create__footer">
        <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交缺陷</el-button>
      </div>
    </el-form>

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />

    <el-dialog
      v-model="dedupConfirmVisible"
      title="检测到疑似重复缺陷"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="!dedupSubmitting"
      append-to-body
    >
      <div class="bug-create__dedup-confirm-tip">
        以下缺陷与您提交的内容可能存在重复，请选择处理方式；标记重复需先选择对应的原始缺陷：
      </div>
      <el-radio-group v-model="dedupTargetId" class="bug-create__dedup-confirm-list">
        <el-radio
          v-for="item in dedupItems"
          :key="item.bugId"
          :value="item.bugId"
          class="bug-create__dedup-confirm-item"
        >
          <span v-if="item.similarity !== null" class="bug-create__dedup-confirm-sim">
            {{ Math.round(item.similarity * 100) }}%
          </span>
          <span class="bug-create__dedup-confirm-title">{{ item.title }}</span>
          <el-tag :type="BUG_STATUS_TAG_TYPE[item.status]" size="small" effect="light" round>
            {{ BUG_STATUS_LABEL[item.status] }}
          </el-tag>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button :disabled="dedupSubmitting" @click="handleDedupAbandon">放弃提交</el-button>
        <el-button :loading="dedupSubmitting" @click="handleDedupContinue">继续提交</el-button>
        <el-button type="primary" :loading="dedupSubmitting" @click="handleDedupMarkDuplicate">
          继续并标记重复
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.bug-create__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.bug-create__form {
  margin-top: var(--space-lg);
}

.bug-create__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-lg);
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.bug-create__main,
.bug-create__side {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.bug-create__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}

// 侧栏与主区表单控件统一撑满，视觉对齐
.bug-create__form :deep(.el-select),
.bug-create__form :deep(.el-tree-select) {
  width: 100%;
}

// el-date-picker 根节点是 fragment，scoped 的 data-v 属性不会落到控件上，须经 :deep 命中；宽度还受组件级 CSS 变量控制
.bug-create__form :deep(.bug-create__date) {
  width: 100%;
  --el-date-editor-width: 100%;
}

.bug-create__form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--color-neutral-600);
  margin-bottom: var(--space-xs);
}

.bug-create__form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.bug-create__repro :deep(.md-editor) {
  width: 100%;
  border-radius: var(--radius-md);
}

// 查重列表紧贴标题输入框下方，与后续字段保持间距
.bug-create__dedup {
  margin-bottom: var(--space-lg);
}

.bug-create__upload-icon {
  font-size: 40px;
  color: var(--color-neutral-300);
  margin-bottom: var(--space-sm);
}

.bug-create__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-create__dedup-confirm-tip {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-600);
  margin-bottom: var(--space-sm);
}

.bug-create__dedup-confirm-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  width: 100%;
}

// 弹层内单选行撑满并允许标签溢出省略
.bug-create__dedup-confirm-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: var(--space-xs);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);

  :deep(.el-radio__label) {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    flex: 1;
    min-width: 0;
  }
}

.bug-create__dedup-confirm-sim {
  flex-shrink: 0;
  font-size: var(--font-size-2xs);
  font-weight: 700;
  color: var(--color-warning);
}

.bug-create__dedup-confirm-title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-create__severity-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: var(--space-sm);
  vertical-align: middle;

  &--fatal { background: var(--color-bug-fatal); }
  &--serious { background: var(--color-bug-serious); }
  &--general { background: var(--color-bug-general); }
  &--minor { background: var(--color-bug-minor); }
}

// 底部粘性操作栏：透明背景居中悬浮，栏体不拦截点击，仅按钮可交互（与详情页操作栏样式一致）
.bug-create__footer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: transparent;
  pointer-events: none;

  .el-button {
    pointer-events: auto;
    box-shadow: var(--shadow-md);
  }
}
</style>
