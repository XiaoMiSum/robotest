<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { completePlan, createPlan, deletePlan, fetchPlans } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { PlanStatus, TestPlanListItem, WorkspaceMember } from '@/types'
import { formatDateTime, formatDate } from '@/utils/format'
import CaseSelector from '@/components/project/CaseSelector.vue'

const router = useRouter()
const loading = ref(false)
const plans = ref<TestPlanListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as PlanStatus | '', keyword: '', pageNo: 1, pageSize: 20 })

const statusLabel: Record<string, string> = { new: '待开始', in_progress: '进行中', completed: '已完成', closed: '已关闭' }
const statusType: Record<string, 'info' | 'warning' | 'success' | 'danger'> = { new: 'info', in_progress: 'warning', completed: 'success', closed: 'danger' }

async function loadPlans() {
  loading.value = true
  try {
    const page = await fetchPlans({
      status: query.status || undefined,
      keyword: query.keyword.trim() || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    plans.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载计划列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadPlans()
}

function handleReset() {
  query.status = ''
  query.keyword = ''
  query.pageNo = 1
  loadPlans()
}

function timeRange(plan: TestPlanListItem): string {
  const s = plan.startTime ? formatDate(plan.startTime) : ''
  const e = plan.endTime ? formatDate(plan.endTime) : ''
  if (!s && !e) return '-'
  return `${s || '?'} ~ ${e || '?'}`
}

onMounted(loadPlans)

async function handleComplete(row: TestPlanListItem) {
  try {
    await ElMessageBox.confirm(`确定完成计划「${row.name}」？`, '完成计划', { type: 'warning' })
  } catch { return }
  try {
    await completePlan(row.id)
    ElMessage.success('计划已完成')
    loadPlans()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '完成计划失败')
  }
}

async function handleDelete(row: TestPlanListItem) {
  try {
    await ElMessageBox.confirm(`确定删除计划「${row.name}」？删除后不可恢复`, '删除计划', { type: 'warning' })
  } catch { return }
  try {
    await deletePlan(row.id)
    ElMessage.success('计划已删除')
    loadPlans()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除计划失败')
  }
}

const createDialogVisible = ref(false)
const caseSelectorVisible = ref(false)
const createSubmitting = ref(false)
const memberOptions = ref<WorkspaceMember[]>([])
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  name: '',
  description: '',
  executorId: '',
  startTime: '' as string,
  endTime: '' as string,
  environment: '',
  selectedNodes: [] as { documentId: string; caseIds: string[] }[],
})
const createRules: FormRules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
}

function openCreateDialog() {
  createForm.name = ''
  createForm.description = ''
  createForm.executorId = ''
  createForm.startTime = ''
  createForm.endTime = ''
  createForm.environment = ''
  createForm.selectedNodes = []
  createDialogVisible.value = true
  loadMemberOptions()
}

async function loadMemberOptions() {
  try {
    const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
    memberOptions.value = page.list
  } catch { /* ignore */ }
}

function handleCaseSelected(nodes: { documentId: string; caseIds: string[] }[]) {
  createForm.selectedNodes = nodes
}

async function submitCreate() {
  if (!createFormRef.value) return
  try { await createFormRef.value.validate() } catch { return }
  if (!createForm.selectedNodes.length) {
    ElMessage.warning('请关联至少一个用例')
    return
  }
  createSubmitting.value = true
  try {
    const result = await createPlan({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
      executorId: createForm.executorId || undefined,
      startTime: createForm.startTime || null,
      endTime: createForm.endTime || null,
      environment: createForm.environment.trim() || undefined,
      selectedNodes: createForm.selectedNodes,
    })
    ElMessage.success('计划已创建')
    createDialogVisible.value = false
    router.push(`/workspace/projects/plans/${result.id}`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建计划失败')
  } finally {
    createSubmitting.value = false
  }
}
</script>

<template>
  <div class="plan-list">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="plan-list__header">
          <el-input
            v-model="query.keyword"
            placeholder="搜索名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in statusLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <div class="plan-list__header-spacer" />
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>创建计划
          </el-button>
        </div>
      </template>
      <el-table :data="plans" row-key="id">
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="router.push(`/workspace/projects/plans/${row.id}`)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100">
          <template #default="{ row }">{{ row.executor?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="起止时间" width="200">
          <template #default="{ row }">{{ timeRange(row as TestPlanListItem) }}</template>
        </el-table-column>
        <el-table-column label="环境" width="120">
          <template #default="{ row }">{{ row.environment || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status] ?? 'info'" size="small" effect="light" round>{{ statusLabel[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent" :stroke-width="6" />
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="100">
          <template #default="{ row }">
            <span :class="row.passRate === 100 ? 'plan-list__rate--full' : 'plan-list__rate--partial'">
              {{ row.passRate }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/workspace/projects/plans/${row.id}`)">
              {{ row.status === 'in_progress' ? '进入执行' : '查看详情' }}
            </el-button>
            <el-button
              v-if="row.status === 'new' || row.status === 'in_progress'"
              link
              type="success"
              @click="handleComplete(row as TestPlanListItem)"
            >
              完成
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as TestPlanListItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="plan-list__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          :total="total"
          :page-size="query.pageSize"
          layout="total, prev, pager, next"
          @current-change="loadPlans"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建计划" width="600px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入计划名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="计划描述（可选）" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="createForm.executorId" filterable clearable placeholder="选择负责人" style="width: 100%">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="createForm.startTime" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="createForm.endTime" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="执行环境">
          <el-input v-model="createForm.environment" placeholder="如：staging / production" />
        </el-form-item>
        <el-form-item label="关联用例">
          <el-button @click="caseSelectorVisible = true">选择用例</el-button>
          <span v-if="createForm.selectedNodes.length" class="plan-list__case-count">
            已选 {{ createForm.selectedNodes.reduce((sum, n) => sum + n.caseIds.length, 0) }} 个用例
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <CaseSelector v-model="caseSelectorVisible" @confirm="handleCaseSelected" />
  </div>
</template>

<style scoped lang="scss">
.plan-list__header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.plan-list__header-spacer {
  flex: 1;
}

.plan-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.plan-list__rate--full {
  color: var(--color-success);
}

.plan-list__rate--partial {
  color: var(--color-danger);
}

/* 用于创建对话框，非布局样式 */
.plan-list__case-count {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
