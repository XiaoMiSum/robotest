<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createReview,
  deleteReview,
  fetchReviews,
  getReviewDetail,
  getReviewReplanStatus,
  reopenReview,
  updateReview,
} from '@/services/project'
import type {
  ReplanStatusResp,
  ReviewStatus,
  TestReviewDetail,
  TestReviewListItem,
  TestReviewCreatePayload,
  TestReviewUpdatePayload,
} from '@/types'
import ReviewCreateDialog from '@/components/project/functional-testing/review/ReviewCreateDialog.vue'
import ReviewEditDialog from '@/components/project/functional-testing/review/ReviewEditDialog.vue'
import ReviewListTable from '@/components/project/functional-testing/review/ReviewListTable.vue'
import { isRequirementSetChanged, useReplanPolling } from '@/composables/project/functional-testing/replan'
import { REVIEW_STATUS_META } from '@/components/project/functional-testing/review/reviewListPresentation'
import ListPagination from '@/components/common/ListPagination.vue'
import ListWorkspace from '@/components/common/ListWorkspace.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'

const REVIEW_COLUMNS: TableColumnMeta[] = [
  { key: 'title', label: '标题' },
  { key: 'initiator', label: '发起人' },
  { key: 'status', label: '状态' },
  { key: 'progress', label: '进度' },
  { key: 'passRate', label: '通过率' },
  { key: 'participants', label: '参与者' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'review-list',
  columns: REVIEW_COLUMNS,
})

const router = useRouter()
const authStore = useAuthStore()
const currentUserId = computed(() => authStore.user?.id ?? '')
const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const reviews = ref<TestReviewListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as ReviewStatus | '', keyword: '', pageNo: 1, pageSize: 20 })
const hasFilters = computed(() => Boolean(query.keyword.trim() || query.status))
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() =>
  loading.value
    ? '正在加载评审…'
    : loadError.value
      ? '评审列表加载失败'
      : hasFilters.value
        ? '没有符合条件的评审'
        : '暂无评审',
)
const statusOptions = (Object.keys(REVIEW_STATUS_META) as ReviewStatus[]).map((value) => ({
  value,
  label: REVIEW_STATUS_META[value].label,
}))
let requestId = 0

async function loadReviews() {
  const current = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const page = await fetchReviews({
      status: query.status || undefined,
      keyword: query.keyword.trim() || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    if (current !== requestId) return
    reviews.value = page.list
    total.value = page.total
    hasLoaded.value = true
  } catch (err) {
    if (current !== requestId) return
    loadError.value = err instanceof Error ? err.message : '加载评审列表失败'
    ElMessage.error(loadError.value)
  } finally {
    if (current === requestId) loading.value = false
  }
}
function handleSearch() {
  query.pageNo = 1
  loadReviews()
}
function handleReset() {
  query.status = ''
  query.keyword = ''
  handleSearch()
}
onMounted(loadReviews)
onUnmounted(() => {
  requestId += 1
})
function openReview(id: string) {
  router.push(`/workspace/projects/reviews/${id}`)
}

async function handleReopen(row: TestReviewListItem) {
  try {
    await ElMessageBox.confirm(
      `确定重新发起评审「${row.title}」？既有标记将保留，参与者可继续评审。`,
      '重新发起评审',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await reopenReview(row.id)
    ElMessage.success('评审已重新发起')
    loadReviews()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重新发起失败')
  }
}
async function handleDelete(row: TestReviewListItem) {
  try {
    await ElMessageBox.confirm(`确定删除评审「${row.title}」？删除后不可恢复`, '删除评审', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteReview(row.id)
    ElMessage.success('评审已删除')
    loadReviews()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除评审失败')
  }
}
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
function openCreateDialog() {
  createDialogVisible.value = true
}
async function submitCreate(payload: TestReviewCreatePayload) {
  createSubmitting.value = true
  try {
    const result = await createReview(payload)
    ElMessage.success('评审已创建')
    createDialogVisible.value = false
    openReview(result.id)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建评审失败')
  } finally {
    createSubmitting.value = false
  }
}

const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editingReview = ref<TestReviewListItem | null>(null)
const editDetail = ref<TestReviewDetail | null>(null)

function handleReplanFinish(status: ReplanStatusResp) {
  if (status.status === 'succeeded') {
    ElMessage.success(status.message || '测试用例重新规划完成')
  } else {
    ElMessage.error(status.message || '测试用例重新规划失败')
  }
  loadReviews()
}
function handleReplanError(message: string) {
  ElMessage.error(message)
  loadReviews()
}
const replanPolling = useReplanPolling(getReviewReplanStatus)

async function openEditDialog(row: TestReviewListItem) {
  try {
    editDetail.value = await getReviewDetail(row.id)
    editingReview.value = row
    editDialogVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载评审详情失败')
  }
}

async function submitEdit(payload: TestReviewUpdatePayload) {
  if (!editingReview.value || !editDetail.value) return
  const id = editingReview.value.id
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
    await updateReview(id, { ...payload, replan })
    editDialogVisible.value = false
    if (replan) {
      ElMessage.success('已保存，正在后台重新规划测试用例')
      replanPolling.start(id, handleReplanFinish, handleReplanError)
      return
    }
    ElMessage.success('已保存')
    loadReviews()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存评审失败')
  } finally {
    editSubmitting.value = false
  }
}
</script>

<template>
  <main class="review-list-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">测试评审</h1>
        <p class="page-head__desc">用例评审与评审结论跟踪</p>
      </div>
      <el-button type="primary" @click="openCreateDialog"
        ><el-icon><Plus /></el-icon>发起评审</el-button
      >
    </header>
    <ListWorkspace label="评审列表" :loading="loading">
      <template #toolbar>
        <el-input
          v-model="query.keyword"
          class="review-list__search"
          aria-label="评审标题"
          placeholder="评审标题"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="query.status"
          class="review-list__status"
          aria-label="评审状态"
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
        <TableColumnSetting
          :columns="REVIEW_COLUMNS"
          :visible-keys="visibleKeys"
          @change="(key, visible) => setVisible(key, visible)"
          @select-all="selectAll"
          @reset="reset"
        />
        <span class="review-list__count">共 {{ total }} 场评审</span>
      </template>
      <template v-if="hasLoaded && loadError" #feedback>
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="loadReviews"
        />
      </template>
      <ReviewListTable
        :reviews="reviews"
        :current-user-id="currentUserId"
        :visible-keys="visibleKeys"
        @open="openReview"
        @edit="openEditDialog"
        @reopen="handleReopen"
        @delete="handleDelete"
      >
        <template #empty>
          <ListLoadFeedback
            v-if="loading || !hasLoaded || !loadError"
            :mode="emptyMode"
            :title="emptyTitle"
            :description="loadError"
            create-label="发起评审"
            @retry="loadReviews"
            @reset="handleReset"
            @create="openCreateDialog"
          />
        </template>
      </ReviewListTable>
      <template #pagination>
        <ListPagination
          v-model:page-no="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          unit="场"
          @change="loadReviews"
        />
      </template>
    </ListWorkspace>
    <ReviewCreateDialog
      v-model="createDialogVisible"
      :submitting="createSubmitting"
      @submit="submitCreate"
    />
    <ReviewEditDialog
      v-model="editDialogVisible"
      :detail="editDetail"
      :submitting="editSubmitting"
      @submit="submitEdit"
    />
  </main>
</template>

<style scoped>
.review-list-page {
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
.review-list__search {
  flex: 1 1 14rem;
  max-width: 32rem;
  min-width: 0;
}
.review-list__status {
  width: 130px;
}
.review-list__count {
  margin-left: auto;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}
</style>
