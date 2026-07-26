<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createBug, fetchPlans } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { BugPriority, BugSeverity, TestPlanListItem, WorkspaceMember } from '@/types'
import CaseSelector from '@/components/project/CaseSelector.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  title: '',
  severity: 'general' as BugSeverity,
  priority: 'medium' as BugPriority,
  description: '',
  assigneeId: '' as string,
  relatedCaseId: '' as string,
  relatedPlanId: '' as string,
})

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

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createBug({
      title: form.title.trim(),
      severity: form.severity,
      priority: form.priority,
      description: form.description.trim() || undefined,
      assigneeId: form.assigneeId || undefined,
      relatedCaseId: form.relatedCaseId || undefined,
      relatedPlanId: form.relatedPlanId || undefined,
    })
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
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="5" placeholder="详细描述缺陷（可选）" />
        </el-form-item>
        <el-form-item label="处理人">
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
