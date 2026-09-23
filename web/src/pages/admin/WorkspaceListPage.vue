<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createWorkspace, fetchWorkspaces } from '@/services/admin'
import type { AdminWorkspace } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

// 后端状态仅 active/dissolved 两态，且无按状态计数接口，故 segment 不展示计数
const STATUS_OPTIONS = [
  { value: 'active', label: '活跃' },
  { value: 'dissolved', label: '归档' },
] as const

const STATUS_META: Record<string, { label: string; dot: string }> = {
  active: { label: '活跃', dot: 'workspace-list__status--success' },
  dissolved: { label: '归档', dot: 'workspace-list__status--neutral' },
}

function statusMeta(status: string) {
  return STATUS_META[status] ?? { label: status, dot: 'workspace-list__status--neutral' }
}

const loading = ref(false)
const workspaces = ref<AdminWorkspace[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  status: 'active' as string,
  pageNo: 1,
  pageSize: 20,
})

async function loadWorkspaces() {
  loading.value = true
  try {
    const page = await fetchWorkspaces({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    workspaces.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载工作空间列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadWorkspaces()
}

function handleReset() {
  query.keyword = ''
  query.status = 'active'
  query.pageNo = 1
  loadWorkspaces()
}

function handleStatusChange(value: string) {
  if (query.status === value) return
  query.status = value
  handleSearch()
}

function goDetail(id: string) {
  router.push(`/admin/workspaces/${id}`)
}

function handleRowClick(row: AdminWorkspace) {
  goDetail(row.id)
}

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createSubmitting = ref(false)
const createForm = reactive({
  name: '',
  description: '',
})
const createRules: FormRules = {
  name: [
    { required: true, message: '请输入工作空间名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度需在 2-50 字符之间', trigger: 'blur' },
  ],
}

function openCreateDialog() {
  createForm.name = ''
  createForm.description = ''
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  createSubmitting.value = true
  try {
    const id = await createWorkspace({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
    })
    ElMessage.success('工作空间已创建')
    createDialogVisible.value = false
    goDetail(id)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建工作空间失败')
  } finally {
    createSubmitting.value = false
  }
}

onMounted(loadWorkspaces)
</script>

<template>
  <div class="workspace-list">
    <div class="workspace-list__head">
      <div>
        <h1 class="workspace-list__title">空间管理</h1>
        <p class="workspace-list__desc">平台内全部工作空间及其规模</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>新建空间
      </el-button>
    </div>

    <section class="workspace-list__card">
      <div class="workspace-list__toolbar">
        <div class="workspace-list__filters">
          <div class="workspace-list__segment">
            <button
              v-for="opt in STATUS_OPTIONS"
              :key="opt.value"
              type="button"
              class="workspace-list__seg-item"
              :class="{ 'is-active': query.status === opt.value }"
              @click="handleStatusChange(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
          <el-input
            v-model="query.keyword"
            placeholder="搜索空间名称"
            clearable
            :prefix-icon="'Search'"
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <span class="workspace-list__sort-note">按创建时间倒序</span>
      </div>

      <el-table
        v-loading="loading"
        :data="workspaces"
        row-key="id"
        class="workspace-list__table"
        @row-click="handleRowClick"
      >
        <el-table-column label="空间名称" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click.stop="goDetail(row.id)">
              {{ row.name }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="workspace-list__desc-cell">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="workspace-list__status" :class="statusMeta(row.status).dot">
              <span class="workspace-list__dot" />{{ statusMeta(row.status).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="成员" width="80">
          <template #default="{ row }">
            <span class="workspace-list__num">{{ row.memberCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="项目" width="80">
          <template #default="{ row }">
            <span class="workspace-list__num">{{ row.projectCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="workspace-list__num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="workspace-list__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadWorkspaces"
          @size-change="handleSearch"
        />
      </div>
    </section>

    <el-dialog v-model="createDialogVisible" title="新建工作空间" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="createForm.name"
            placeholder="请输入工作空间名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入工作空间描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.workspace-list__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.workspace-list__title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-neutral-900);
}

.workspace-list__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

/* 单卡片承载 toolbar/表格/分页，对齐演示稿 workspaces.html 结构 */
.workspace-list__card {
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.workspace-list__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-neutral-100);
  flex-wrap: wrap;
}

.workspace-list__filters {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

/* 分段控件对齐演示稿 segment，替代原状态下拉 */
.workspace-list__segment {
  display: inline-flex;
  background: var(--color-neutral-100);
  border-radius: var(--radius-md);
  padding: 3px;
  gap: 2px;
}

.workspace-list__seg-item {
  padding: 4px 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-neutral-600);
  font-family: inherit;
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 1.4;
  cursor: pointer;
}

.workspace-list__seg-item.is-active {
  background: var(--color-neutral-0);
  color: var(--color-neutral-900);
  box-shadow: var(--shadow-sm);
}

.workspace-list__sort-note {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  white-space: nowrap;
}

/* 状态点标对齐演示稿 status（圆点 + 文案），色义：活跃绿 / 归档灰 */
.workspace-list__status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  white-space: nowrap;
}

.workspace-list__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-neutral-400);
}

.workspace-list__status--success .workspace-list__dot {
  background: var(--color-success);
}

.workspace-list__table :deep(.el-table__row) {
  cursor: pointer;
}

.workspace-list__desc-cell {
  color: var(--color-neutral-500);
}

.workspace-list__num {
  font-variant-numeric: tabular-nums;
}

.workspace-list__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}
</style>
