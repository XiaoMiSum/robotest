<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completePlan,
  createPlan,
  deletePlan,
  fetchPlans,
  getPlanDetail,
  resumePlan,
  updatePlan,
} from '@/services/project'
import type {
  PlanStatus,
  TestPlanCreatePayload,
  TestPlanDetail,
  TestPlanListItem,
  TestPlanUpdatePayload,
} from '@/types'
import PlanCreateDialog from '@/components/project/functional-testing/plan/PlanCreateDialog.vue'
import PlanEditDialog from '@/components/project/functional-testing/plan/PlanEditDialog.vue'
import PlanListTable from '@/components/project/functional-testing/plan/PlanListTable.vue'
import { isRequirementSetChanged } from '@/composables/project/functional-testing/replan'
import {
  PLAN_STATUS_META,
  planCountText,
} from '@/components/project/functional-testing/plan/planListPresentation'
import ListPagination from '@/components/common/ListPagination.vue'
import ListWorkspace from '@/components/common/ListWorkspace.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'

const PLAN_COLUMNS: TableColumnMeta[] = [
  { key: 'name', label: '名称' },
  { key: 'owner', label: '负责人' },
  { key: 'timeRange', label: '起止时间' },
  { key: 'environment', label: '环境' },
  { key: 'status', label: '状态' },
  { key: 'progress', label: '进度' },
  { key: 'passRate', label: '通过率' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'plan-list',
  columns: PLAN_COLUMNS,
})

const router = useRouter()
const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const plans = ref<TestPlanListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as PlanStatus | '', keyword: '', pageNo: 1, pageSize: 20 })
const hasFilters = computed(() => Boolean(query.keyword.trim() || query.status))
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() =>
  loading.value
    ? '正在加载计划…'
    : loadError.value
      ? '计划列表加载失败'
      : hasFilters.value
        ? '没有符合条件的计划'
        : '暂无计划',
)
// 前端不再按权限码隐藏计划操作，状态条件由 PlanListTable 收口，服务端授权兜底
const canCompletePlans = true
const canResumePlans = true
const canEditPlans = true
const statusOptions = (Object.keys(PLAN_STATUS_META) as PlanStatus[]).map((value) => ({
  value,
  label: PLAN_STATUS_META[value].label,
}))
let requestId = 0

async function loadPlans() {
  const current = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const page = await fetchPlans({
      status: query.status || undefined,
      keyword: query.keyword.trim() || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    if (current !== requestId) return
    plans.value = page.list
    total.value = page.total
    hasLoaded.value = true
  } catch (err) {
    if (current !== requestId) return
    loadError.value = err instanceof Error ? err.message : '加载计划列表失败'
    ElMessage.error(loadError.value)
  } finally {
    if (current === requestId) loading.value = false
  }
}
function handleSearch() {
  query.pageNo = 1
  loadPlans()
}
function handleReset() {
  query.status = ''
  query.keyword = ''
  handleSearch()
}
onMounted(loadPlans)
onUnmounted(() => {
  requestId += 1
})
function openPlan(id: string) {
  router.push(`/workspace/projects/plans/${id}`)
}

async function handleComplete(row: TestPlanListItem) {
  try {
    await ElMessageBox.confirm(`确定完成计划「${row.name}」？`, '完成计划', { type: 'warning' })
  } catch {
    return
  }
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
    await ElMessageBox.confirm(`确定恢复计划「${row.name}」？恢复后回到执行中。`, '恢复计划', {
      type: 'warning',
    })
  } catch {
    return
  }
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
    await ElMessageBox.confirm(`确定删除计划「${row.name}」？删除后不可恢复`, '删除计划', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deletePlan(row.id)
    ElMessage.success('计划已删除')
    loadPlans()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除计划失败')
  }
}
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
function openCreateDialog() {
  createDialogVisible.value = true
}
async function submitCreate(payload: TestPlanCreatePayload) {
  createSubmitting.value = true
  try {
    const result = await createPlan(payload)
    ElMessage.success('计划已创建')
    createDialogVisible.value = false
    openPlan(result.id)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建计划失败')
  } finally {
    createSubmitting.value = false
  }
}

const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editingPlan = ref<TestPlanListItem | null>(null)
const editDetail = ref<TestPlanDetail | null>(null)

async function openEditDialog(row: TestPlanListItem) {
  try {
    editDetail.value = await getPlanDetail(row.id)
    editingPlan.value = row
    editDialogVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载计划详情失败')
  }
}

async function submitEdit(payload: TestPlanUpdatePayload) {
  if (!editingPlan.value || !editDetail.value) return
  const id = editingPlan.value.id
  const changed = isRequirementSetChanged(editDetail.value.requirementIds, payload.requirementIds ?? [])
  let replan = false
  if (changed) {
    try {
      await ElMessageBox.confirm('检测到关联需求发生变化，是否重新规划测试用例？', '关联需求变化', {
        type: 'warning',
      })
      replan = true
    } catch {
      replan = false
    }
  }
  editSubmitting.value = true
  try {
    await updatePlan(id, { ...payload, replan })
    editDialogVisible.value = false
    if (replan) {
      ElMessage.success('已保存，正在后台重新规划测试用例')
      return
    }
    ElMessage.success('已保存')
    loadPlans()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存计划失败')
  } finally {
    editSubmitting.value = false
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
      <el-button type="primary" @click="openCreateDialog"
        ><el-icon><Plus /></el-icon>新建计划</el-button
      >
    </header>
    <ListWorkspace label="计划列表" :loading="loading">
      <template #toolbar>
        <el-input
          v-model="query.keyword"
          class="plan-list__search"
          aria-label="计划名称"
          placeholder="计划名称"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="query.status"
          class="plan-list__status"
          aria-label="计划状态"
          placeholder="全部状态"
          clearable
        >
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-space :size="8"
          ><el-button type="primary" @click="handleSearch"
            ><el-icon><Search /></el-icon>查询</el-button
          ><el-button @click="handleReset">重置</el-button></el-space
        >
        <span class="plan-list__count">{{ planCountText(total) }}</span>
      </template>
      <template v-if="hasLoaded && loadError" #feedback>
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="loadPlans"
        />
      </template>
      <PlanListTable
        :plans="plans"
        :can-complete="canCompletePlans"
        :can-resume="canResumePlans"
        :can-edit="canEditPlans"
        :visible-keys="visibleKeys"
        @open="openPlan"
        @edit="openEditDialog"
        @complete="handleComplete"
        @resume="handleResume"
        @delete="handleDelete"
      >
        <template #column-setting>
          <TableColumnSetting
            icon-only
            :columns="PLAN_COLUMNS"
            :visible-keys="visibleKeys"
            @change="(key, visible) => setVisible(key, visible)"
            @select-all="selectAll"
            @reset="reset"
          />
        </template>
        <template #empty>
          <ListLoadFeedback
            v-if="loading || !hasLoaded || !loadError"
            :mode="emptyMode"
            :title="emptyTitle"
            :description="loadError"
            create-label="新建计划"
            @retry="loadPlans"
            @reset="handleReset"
            @create="openCreateDialog"
          />
        </template>
      </PlanListTable>
      <template #pagination>
        <ListPagination
          v-model:page-no="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          unit="个计划"
          @change="loadPlans"
        />
      </template>
    </ListWorkspace>
    <PlanCreateDialog
      v-model="createDialogVisible"
      :submitting="createSubmitting"
      @submit="submitCreate"
    />
    <PlanEditDialog
      v-model="editDialogVisible"
      :detail="editDetail"
      :submitting="editSubmitting"
      @submit="submitEdit"
    />
  </main>
</template>

<style scoped>
.plan-list-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  min-height: 400px;
}
.page-head {
  display: flex;
  flex-wrap: wrap;
  flex-shrink: 0;
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
}
.page-head__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}
.plan-list__search {
  flex: 1 1 14rem;
  max-width: 32rem;
  min-width: 0;
}
.plan-list__status {
  width: 130px;
}
.plan-list__count {
  margin-left: auto;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}
</style>
