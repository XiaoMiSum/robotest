<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createReview, deleteReview, fetchReviews, reopenReview } from '@/services/project'
import type { ReviewStatus, TestReviewListItem, TestReviewCreatePayload } from '@/types'
import { formatDateTime } from '@/utils/format'
import ReviewCreateDialog from '@/components/project/functional-testing/review/ReviewCreateDialog.vue'
import {
  REVIEW_STATUS_META,
  reviewAvatars,
  reviewListAction,
  reviewPassRate,
  reviewProgressStatus,
  reviewProgressText,
  reviewStatusMeta,
} from '@/components/project/functional-testing/review/reviewListPresentation'

const router = useRouter()
const loading = ref(false)
const reviews = ref<TestReviewListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as ReviewStatus | '', keyword: '', pageNo: 1, pageSize: 20 })

// 发起人在头像堆中置顶并高亮，通过组件 CSS 变量定制，避免重写 el-avatar 内部结构
const brandAvatarStyle: Record<string, string> = {
  '--el-avatar-background-color': 'var(--color-primary-500)',
  '--el-avatar-color': 'var(--color-neutral-0)',
}

const statusOptions = (Object.keys(REVIEW_STATUS_META) as ReviewStatus[]).map((value) => ({
  value,
  label: REVIEW_STATUS_META[value].label,
}))

// 展示口径在行级预计算，模板内不再散落条件分支
interface ReviewRowView {
  review: TestReviewListItem
  meta: ReturnType<typeof reviewStatusMeta>
  action: ReturnType<typeof reviewListAction>
  passRate: ReturnType<typeof reviewPassRate>
  progressText: string
  progressStatus: ReturnType<typeof reviewProgressStatus>
  avatars: ReturnType<typeof reviewAvatars>
}

const rows = computed<ReviewRowView[]>(() =>
  reviews.value.map((review) => ({
    review,
    meta: reviewStatusMeta(review.status),
    action: reviewListAction(review.status),
    passRate: reviewPassRate(review.status, review.passRate),
    progressText: reviewProgressText(review.reviewed, review.totalAssociated),
    progressStatus: reviewProgressStatus(review.status),
    avatars: reviewAvatars(review),
  })),
)

// 视图模型无原始 id 字段，行 key 由内层评审对象提供
function rowKeyId(row: ReviewRowView): string {
  return row.review.id
}

async function loadReviews() {
  loading.value = true
  try {
    const page = await fetchReviews({
      status: query.status || undefined,
      keyword: query.keyword.trim() || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    reviews.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载评审列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadReviews()
}

function handleReset() {
  query.status = ''
  query.keyword = ''
  query.pageNo = 1
  loadReviews()
}

onMounted(loadReviews)

function openReview(id: string) {
  router.push(`/workspace/projects/reviews/${id}`)
}

async function handleReopen(row: TestReviewListItem) {
  try {
    await ElMessageBox.confirm(`确定重新发起评审「${row.title}」？既有标记将保留，参与者可继续评审。`, '重新发起评审', { type: 'warning' })
  } catch { return }
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
    await ElMessageBox.confirm(`确定删除评审「${row.title}」？删除后不可恢复`, '删除评审', { type: 'warning' })
  } catch { return }
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

/** 创建落库仍在页面层（组件只产载荷，圈选确认复用同一载荷转 createParams） */
async function submitCreate(payload: TestReviewCreatePayload) {
  createSubmitting.value = true
  try {
    const result = await createReview(payload)
    ElMessage.success('评审已创建')
    createDialogVisible.value = false
    router.push(`/workspace/projects/reviews/${result.id}`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建评审失败')
  } finally {
    createSubmitting.value = false
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
      <div class="page-head__actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>发起评审
        </el-button>
      </div>
    </header>

    <el-card v-loading="loading" shadow="never" class="review-list__card">
      <div class="review-list__toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="评审标题"
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
        <span class="review-list__count">共 {{ total }} 场评审</span>
      </div>

      <el-table :data="rows" :row-key="rowKeyId" empty-text="暂无评审">
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <div class="review-list__cell">
              <el-link type="primary" underline="never" @click="openReview(row.review.id)">
                {{ row.review.title }}
              </el-link>
              <span class="review-list__cell-sub">关联用例 {{ row.review.totalAssociated }} 条</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发起人" width="100">
          <template #default="{ row }">{{ row.review.initiator.name }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="review-list__status" :class="`review-list__status--${row.meta.modifier}`">
              <span class="review-list__status-dot" />
              {{ row.meta.label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="170">
          <template #default="{ row }">
            <div class="review-list__progress">
              <el-progress
                class="review-list__progress-bar"
                :percentage="row.review.progressPercent"
                :stroke-width="6"
                :status="row.progressStatus"
              >
                {{ row.progressText }}
              </el-progress>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="92" align="right">
          <template #default="{ row }">
            <span class="review-list__rate" :class="`review-list__rate--${row.passRate.tone}`">
              {{ row.passRate.text }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="参与者" width="124">
          <template #default="{ row }">
            <span v-if="!row.avatars.visible.length" class="review-list__muted">—</span>
            <span v-else class="review-list__avatars">
              <el-avatar
                v-for="avatar in row.avatars.visible"
                :key="avatar.key"
                :size="24"
                :src="avatar.avatarUrl ?? undefined"
                :style="avatar.brand ? brandAvatarStyle : undefined"
              >{{ avatar.label }}</el-avatar>
              <span v-if="row.avatars.overflow" class="review-list__avatar-more">
                +{{ row.avatars.overflow }}
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.review.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReview(row.review.id)">
              {{ row.action === 'enter' ? '进入' : '查看' }}
            </el-button>
            <el-button
              v-if="row.review.status === 'rejected'"
              link
              type="primary"
              @click="handleReopen(row.review)"
            >
              重新发起
            </el-button>
            <el-button link type="danger" @click="handleDelete(row.review)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="review-list__pager">
        <span class="review-list__pager-total">共 {{ total }} 场 · 每页 {{ query.pageSize }} 条</span>
        <el-pagination
          v-model:current-page="query.pageNo"
          :total="total"
          :page-size="query.pageSize"
          layout="prev, pager, next"
          @current-change="loadReviews"
        />
      </div>
    </el-card>

    <ReviewCreateDialog
      v-model="createDialogVisible"
      :submitting="createSubmitting"
      @submit="submitCreate"
    />
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

.review-list__card {
  /* UI-SC-10：body 内边距归零，留白由工具栏/分页条承担 */
  --el-card-padding: 0;
}

.review-list__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.review-list__count {
  margin-left: auto;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.review-list__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  /* el-link 默认 justify-content:center，被容器拉伸后主标题会居中，收缩至内容宽以与副行同左对齐 */
  align-items: flex-start;
}

.review-list__cell-sub {
  color: var(--color-neutral-400);
  font-size: var(--font-size-2xs);
}

.review-list__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.review-list__status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: var(--color-neutral-400);
}

.review-list__status--running {
  color: var(--color-warning);

  .review-list__status-dot {
    background: var(--color-warning);
    box-shadow: 0 0 0 3px rgb(230 162 60 / 18%);
  }
}

.review-list__status--success {
  color: var(--color-success);

  .review-list__status-dot {
    background: var(--color-success);
  }
}

.review-list__status--danger {
  color: var(--color-danger);

  .review-list__status-dot {
    background: var(--color-danger);
  }
}

.review-list__progress {
  display: flex;
  align-items: center;
}

.review-list__progress-bar {
  flex: 1;
  min-width: 0;
}

.review-list__rate {
  font-size: var(--font-size-sm);
}

.review-list__rate--muted {
  color: var(--color-neutral-400);
}

.review-list__rate--success {
  color: var(--color-success);
  font-weight: 600;
}

.review-list__rate--danger {
  color: var(--color-danger);
}

.review-list__muted {
  color: var(--color-neutral-400);
}

.review-list__avatars {
  display: inline-flex;
  align-items: center;

  :deep(.el-avatar + .el-avatar) {
    margin-left: -6px;
    box-shadow: 0 0 0 2px var(--color-neutral-0);
  }
}

.review-list__avatar-more {
  margin-left: 4px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-2xs);
}

.review-list__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}

.review-list__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}
</style>
