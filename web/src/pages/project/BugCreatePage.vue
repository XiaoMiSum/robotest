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

const moduleTree = ref<TestCaseModule[]>([])
async function loadModuleTree() {
  try {
    moduleTree.value = await fetchModuleTree()
  } catch { /* ignore */ }
}
loadModuleTree()

const caseSelectorVisible = ref(false)
const selectedCaseTitle = ref('')
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
      assigneeId: form.assigneeId || undefined,
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

    <el-card shadow="never" class="bug-create__card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="bug-create__form">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入缺陷标题" maxlength="300" show-word-limit />
        </el-form-item>
        <el-form-item label="缺陷类型" prop="bugType">
          <el-select v-model="form.bugType" style="width: 160px">
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
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="严重等级" prop="severity">
          <el-select v-model="form.severity" style="width: 160px">
            <el-option label="致命" value="fatal" />
            <el-option label="严重" value="serious" />
            <el-option label="一般" value="general" />
            <el-option label="轻微" value="minor" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" style="width: 160px">
            <el-option label="高" value="high" />
            <el-option label="中" value="medium" />
            <el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="选择截止日期（可选）" style="width: 160px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="多个关键词用空格分隔（可选）" maxlength="255" />
        </el-form-item>
        <el-form-item label="重现步骤">
          <el-input v-model="form.reproSteps" type="textarea" :rows="6" placeholder="重现步骤（支持 Markdown，可选）" />
        </el-form-item>
        <el-form-item label="指派给">
          <el-select v-model="form.assigneeId" filterable clearable placeholder="选择处理人（可选）" style="width: 240px">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联用例">
          <el-button @click="caseSelectorVisible = true">选择用例</el-button>
          <span v-if="selectedCaseTitle" class="bug-create__case-hint">{{ selectedCaseTitle }}</span>
        </el-form-item>
        <el-form-item label="关联计划">
          <el-select v-model="form.relatedPlanId" filterable clearable placeholder="选择计划（可选）" style="width: 240px">
            <el-option v-for="p in planOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            :auto-upload="false"
            :file-list="attachmentFiles"
            multiple
            :on-change="handleAttachmentChange"
            :on-remove="handleAttachmentRemove"
          >
            <el-button>选择文件</el-button>
            <template #tip>
              <div class="bug-create__case-hint">单个文件不超过 10MB，创建后自动上传</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
          <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <CaseSelector v-model="caseSelectorVisible" @confirm="handleCaseSelected" />
  </div>
</template>

<style scoped lang="scss">
.bug-create__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.bug-create__card {
  margin-top: var(--space-lg);
}

.bug-create__form {
  max-width: 640px;
}

.bug-create__case-hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
