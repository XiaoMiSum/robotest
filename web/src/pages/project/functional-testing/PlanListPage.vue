<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  completePlan,
  createPlan,
  deletePlan,
  fetchPlans,
  resumePlan,
} from '@/services/project'
import type { PlanStatus, TestPlanCreatePayload, TestPlanListItem } from '@/types'
import { formatDateTime } from '@/utils/format'
import PlanCreateDialog from '@/components/project/functional-testing/plan/PlanCreateDialog.vue'
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
const authStore = useAuthStore()
const loading = ref(false)
const plans = ref<TestPlanListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as PlanStatus | '', keyword: '', pageNo: 1, pageSize: 20 })

// 行内 [完成][恢复] 与后端 @PreAuthorize 同源：缺权限码不展示入口；[删除] 由后端按计划负责人校验
const canCompletePlans = computed(() => authStore.hasPermission('plan:execute'))
const canResumePlans = computed(() => authStore.hasPermission('plan:block'))

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
const createSubmitting = ref(false)

/** 卡片 body 转 flex 列，表体区才能 flex:1 承接剩余高度（UI-SC-06）；走组件属性而非 :deep，避免扩大 EX-DS-001 基线 */
const cardBodyStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  flex: '1 1 auto',
  minHeight: '0',
}

function openCreateDialog() {
  createDialogVisible.value = true
}

/** 创建落库仍在页面层（组件只产载荷，圈选确认复用同一载荷转 createParams） */
async function submitCreate(payload: TestPlanCreatePayload) {
  createSubmitting.value = true
  try {
    const result = await createPlan(payload)
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

    <el-card v-loading="loading" shadow="never" class="plan-list__card" :body-style="cardBodyStyle">
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

      <!-- 表体区是卡片内唯一滚动拥有者：表头固定，数据超出固定高度时表体内滚动（UI-SC-02 / UI-SC-05） -->
      <div class="plan-list__body">
        <el-table :data="rows" :row-key="rowKeyId" empty-text="暂无计划" height="100%">
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
                v-if="isActivePlan(row.plan.status) && canCompletePlans"
                link
                type="success"
                @click="handleComplete(row.plan)"
              >
                完成
              </el-button>
              <el-button
                v-if="canResumePlan(row.plan.status) && canResumePlans"
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
      </div>

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

    <PlanCreateDialog
      v-model="createDialogVisible"
      :submitting="createSubmitting"
      @submit="submitCreate"
    />
  </main>
</template>

<style scoped lang="scss">
/* 高度链路：内容白卡 → 页面 → 卡片 → 表体，滚动只发生在表体（UI-SC-01 / UI-SC-02） */
.plan-list-page {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  height: 100%;
}

.page-head {
  display: flex;
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
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

/* UI-SC-10：body 内边距归零，留白由工具栏/分页条承担；卡片本体转 flex 列承接表体区（UI-SC-06） */
.plan-list__card {
  --el-card-padding: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.plan-list__toolbar {
  display: flex;
  flex-shrink: 0;
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

/* 表体容器：flex:1 承接剩余高度并允许收缩（min-height:0），表格外层不再叠加 overflow 滚动（UI-SC-02 / UI-SC-06） */
.plan-list__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
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
  flex-shrink: 0;
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
</style>
