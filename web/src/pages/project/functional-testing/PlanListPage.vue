<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  completePlan,
  createPlan,
  deletePlan,
  fetchPlans,
  resumePlan,
} from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { PlanStatus, TestPlanListItem, WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import {
  PLAN_STATUS_META,
  isActivePlan,
  planCountText,
  planEnvironment,
  planListAction,
  planNameSub,
  planPagerTotalText,
  planPassRate,
  planProgressStatus,
  planProgressText,
  planStatusMeta,
  planTimeRange,
  canResumePlan,
} from '@/components/project/functional-testing/plan/planListPresentation'

const router = useRouter()
const loading = ref(false)
const plans = ref<TestPlanListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as PlanStatus | '', keyword: '', pageNo: 1, pageSize: 20 })

const statusOptions = (Object.keys(PLAN_STATUS_META) as PlanStatus[]).map((value) => ({
  value,
  label: PLAN_STATUS_META[value].label,
}))

// 展示口径在行级预计算，模板内不再散落条件分支
interface PlanRowView {
  plan: TestPlanListItem
  meta: ReturnType<typeof planStatusMeta>
  action: ReturnType<typeof planListAction>
  passRate: ReturnType<typeof planPassRate>
  progressStatus: ReturnType<typeof planProgressStatus>
  timeText: string
  environment: string
  nameSub: string
}

const rows = computed<PlanRowView[]>(() =>
  plans.value.map((plan) => ({
    plan,
    meta: planStatusMeta(plan.status),
    action: planListAction(plan.status),
    passRate: planPassRate(plan.status, plan.passRate),
    progressStatus: planProgressStatus(plan.status),
    timeText: planTimeRange(plan.startTime, plan.endTime),
    environment: planEnvironment(plan.environment),
    nameSub: planNameSub(plan.totalAssociated),
  })),
)

// 视图模型无原始 id 字段，行 key 由内层计划对象提供
function rowKeyId(row: PlanRowView): string {
  return row.plan.id
}

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

async function handleResume(row: TestPlanListItem) {
  try {
    await ElMessageBox.confirm(`确定恢复计划「${row.name}」？恢复后回到执行中。`, '恢复计划', { type: 'warning' })
  } catch { return }
  try {
    await resumePlan(row.id)
    ElMessage.success('计划已恢复')
    loadPlans()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '恢复计划失败')
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
  <main class="plan-list-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">测试计划</h1>
        <p class="page-head__desc">计划维度组织用例执行与通过率统计</p>
      </div>
      <div class="page-head__actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>新建计划
        </el-button>
      </div>
    </header>

    <el-card v-loading="loading" shadow="never" class="plan-list__card">
      <div class="plan-list__toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="计划名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="plan-list__count">{{ planCountText(total) }}</span>
      </div>

      <el-table :data="rows" :row-key="rowKeyId" empty-text="暂无计划">
        <el-table-column label="名称" min-width="220">
          <template #default="{ row }">
            <div class="plan-list__cell">
              <el-link type="primary" underline="never" @click="router.push(`/workspace/projects/plans/${row.plan.id}`)">
                {{ row.plan.name }}
              </el-link>
              <span class="plan-list__cell-sub">{{ row.nameSub }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100">
          <template #default="{ row }">{{ row.plan.executor?.name ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="起止时间" width="176">
          <template #default="{ row }">{{ row.timeText }}</template>
        </el-table-column>
        <el-table-column label="环境" width="116">
          <template #default="{ row }">
            <span v-if="row.environment === '—'" class="plan-list__muted">—</span>
            <span v-else class="plan-list__chip">{{ row.environment }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="plan-list__status" :class="`plan-list__status--${row.meta.modifier}`">
              <span class="plan-list__status-dot" />
              {{ row.meta.label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="170">
          <template #default="{ row }">
            <el-progress
              :percentage="row.plan.progressPercent"
              :stroke-width="6"
              :status="row.progressStatus"
            >
              {{ planProgressText(row.plan.progressPercent) }}
            </el-progress>
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="92" align="right">
          <template #default="{ row }">
            <span class="plan-list__rate" :class="`plan-list__rate--${row.passRate.tone}`">
              {{ row.passRate.text }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.plan.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/workspace/projects/plans/${row.plan.id}`)">
              {{ row.action === 'enter' ? '进入执行' : '查看详情' }}
            </el-button>
            <el-button
              v-if="isActivePlan(row.plan.status)"
              link
              type="success"
              @click="handleComplete(row.plan)"
            >
              完成
            </el-button>
            <el-button
              v-if="canResumePlan(row.plan.status)"
              link
              type="primary"
              @click="handleResume(row.plan)"
            >
              恢复
            </el-button>
            <el-button link type="danger" @click="handleDelete(row.plan)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="plan-list__pager">
        <span class="plan-list__pager-total">{{ planPagerTotalText(total, query.pageSize) }}</span>
        <el-pagination
          v-model:current-page="query.pageNo"
          :total="total"
          :page-size="query.pageSize"
          layout="prev, pager, next"
          @current-change="loadPlans"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建计划" width="600px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入计划名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="计划描述（可选）" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="createForm.executorId" filterable clearable placeholder="选择负责人" style="width: 100%">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name || m.username" :value="m.userId" />
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
  </main>
</template>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.plan-list__card {
  /* UI-SC-10：body 内边距归零，留白由工具栏/分页条承担 */
  --el-card-padding: 0;
}

.plan-list__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.plan-list__count {
  margin-left: auto;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.plan-list__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  /* el-link 默认 justify-content:center，被容器拉伸后名称会居中，收缩至内容宽以与副行同左对齐 */
  align-items: flex-start;
}

.plan-list__cell-sub {
  color: var(--color-neutral-400);
  font-size: var(--font-size-2xs);
}

.plan-list__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.plan-list__status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: var(--color-neutral-400);
}

.plan-list__status--running {
  color: var(--color-warning);

  .plan-list__status-dot {
    background: var(--color-warning);
    box-shadow: 0 0 0 3px rgb(230 162 60 / 18%);
  }
}

.plan-list__status--success {
  color: var(--color-success);

  .plan-list__status-dot {
    background: var(--color-success);
  }
}

.plan-list__status--blocked {
  color: var(--color-blocked);

  .plan-list__status-dot {
    background: var(--color-blocked);
  }
}

.plan-list__chip {
  display: inline-flex;
  align-items: center;
  padding: 1px var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-full);
  background: var(--color-neutral-50);
  color: var(--color-neutral-600);
  font-size: var(--font-size-2xs);
}

.plan-list__rate {
  font-size: var(--font-size-sm);
}

.plan-list__rate--muted {
  color: var(--color-neutral-400);
}

.plan-list__rate--success {
  color: var(--color-success);
  font-weight: 600;
}

.plan-list__muted {
  color: var(--color-neutral-400);
}

.plan-list__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}

.plan-list__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

/* 用于创建对话框，非布局样式 */
.plan-list__case-count {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
