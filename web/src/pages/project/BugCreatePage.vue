<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'
import { createBug, fetchModuleTree, fetchPlans, uploadBugAttachment } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type {
  BugPriority,
  BugSeverity,
  BugType,
  TestCaseModule,
  TestPlanListItem,
  WorkspaceMember,
} from '@/types'
import { BUG_TYPE_LABEL } from '@/utils/bugStatus'
import CaseSelector from '@/components/project/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

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
loadPlanOptions()

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

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
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
    ElMessage.success('缺陷已提交')
    router.push('/workspace/projects/bugs')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交失败')
  } finally {
    submitting.value = false
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
        </div>
      </div>

      <div class="bug-create__footer">
        <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交缺陷</el-button>
      </div>
    </el-form>

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />
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

// 底部粘性操作栏，长表单滚动时提交按钮始终可见
.bug-create__footer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}
</style>
