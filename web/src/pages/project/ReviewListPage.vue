<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createReview, fetchReviews } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { ReviewStatus, TestReviewListItem, WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'
import CaseSelector from '@/components/project/CaseSelector.vue'

const router = useRouter()
const loading = ref(false)
const reviews = ref<TestReviewListItem[]>([])
const total = ref(0)
const query = reactive({ status: '' as ReviewStatus | '', keyword: '', pageNo: 1, pageSize: 20 })

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

const createDialogVisible = ref(false)
const caseSelectorVisible = ref(false)
const createSubmitting = ref(false)
const memberOptions = ref<WorkspaceMember[]>([])
const createForm = reactive({
  title: '',
  description: '',
  participantIds: [] as string[],
  selectedNodes: [] as { documentId: string; caseIds: string[] }[],
})
const createRules: FormRules = {
  title: [{ required: true, message: '请输入评审标题', trigger: 'blur' }],
}
const createFormRef = ref<FormInstance>()

function openCreateDialog() {
  createForm.title = ''
  createForm.description = ''
  createForm.participantIds = []
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
  if (!createForm.participantIds.length) {
    ElMessage.warning('请选择至少一个参与者')
    return
  }
  createSubmitting.value = true
  try {
    const result = await createReview({
      title: createForm.title.trim(),
      description: createForm.description.trim() || undefined,
      participantIds: createForm.participantIds,
      selectedNodes: createForm.selectedNodes,
    })
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
  <div class="review-list">

    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="review-list__header">
          <el-input
            v-model="query.keyword"
            placeholder="搜索标题"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
            <el-option label="评审中" value="in_progress" />
            <el-option label="已完成" value="completed" />
          </el-select>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <div class="review-list__header-spacer" />
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>发起评审
          </el-button>
        </div>
      </template>
      <el-table :data="reviews" row-key="id">
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="router.push(`/workspace/projects/reviews/${row.id}`)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="发起人" width="120">
          <template #default="{ row }">{{ row.initiator.name }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : 'warning'" size="small" effect="light" round>
              {{ row.status === 'completed' ? '已完成' : '评审中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参与者" width="80">
          <template #default="{ row }">{{ row.participantCount }} 人</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/workspace/projects/reviews/${row.id}`)">
              {{ row.status === 'in_progress' ? '进入评审' : '查看详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="review-list__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          :total="total"
          :page-size="query.pageSize"
          layout="total, prev, pager, next"
          @current-change="loadReviews"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="发起评审" width="560px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="请输入评审标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="评审描述（可选）" />
        </el-form-item>
        <el-form-item label="参与者">
          <el-select v-model="createForm.participantIds" multiple filterable placeholder="选择参与者" style="width: 100%">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联用例">
          <el-button @click="caseSelectorVisible = true">选择用例</el-button>
          <span v-if="createForm.selectedNodes.length" class="review-list__case-count">
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
.review-list__header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.review-list__header-spacer {
  flex: 1;
}

.review-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.review-list__case-count {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
