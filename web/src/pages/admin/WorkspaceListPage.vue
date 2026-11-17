<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchWorkspaces } from '@/services/admin'
import { useAuthStore } from '@/stores/auth'
import WorkspaceCreateDialog from '@/components/workspace/WorkspaceCreateDialog.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'
import type { AdminWorkspace } from '@/types'

const WORKSPACE_COLUMNS: TableColumnMeta[] = [
  { key: 'name', label: '空间名称' },
  { key: 'description', label: '描述' },
  { key: 'status', label: '状态' },
  { key: 'members', label: '成员' },
  { key: 'projects', label: '项目' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'workspace-list',
  columns: WORKSPACE_COLUMNS,
})
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()
const canCreate = computed(() => authStore.hasPermission('workspace:create'))

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
const hasLoaded = ref(false)
const loadError = ref('')
const workspaces = ref<AdminWorkspace[]>([])
const total = ref(0)
let requestId = 0

const query = reactive({
  keyword: '',
  status: 'active' as string,
  pageNo: 1,
  pageSize: 20,
})

const hasFilters = computed(() => Boolean(query.keyword || query.status !== 'active'))
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() => {
  if (loading.value) return '正在加载工作空间…'
  if (loadError.value) return '加载工作空间列表失败'
  if (hasFilters.value) return '无匹配结果'
  return '暂无工作空间'
})

async function loadWorkspaces() {
  const current = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const page = await fetchWorkspaces({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    if (current !== requestId) return
    workspaces.value = page.list
    total.value = page.total
    hasLoaded.value = true
  } catch (err) {
    if (current !== requestId) return
    loadError.value = err instanceof Error ? err.message : '加载工作空间列表失败'
    ElMessage.error(loadError.value)
  } finally {
    if (current === requestId) loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  void loadWorkspaces()
}

function handleReset() {
  query.keyword = ''
  query.status = 'active'
  query.pageNo = 1
  void loadWorkspaces()
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

function openCreateDialog(): void {
  if (!canCreate.value) return
  createDialogVisible.value = true
}

function handleCreated(id: string): void {
  createDialogVisible.value = false
  goDetail(id)
}

onBeforeUnmount(() => {
  requestId += 1
})

onMounted(() => {
  void loadWorkspaces()
})
</script>

<template>
  <div class="workspace-list">
    <div class="workspace-list__head">
      <div>
        <h1 class="workspace-list__title">空间管理</h1>
        <p class="workspace-list__desc">平台内全部工作空间及其规模</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="openCreateDialog">
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
          <TableColumnSetting
            :columns="WORKSPACE_COLUMNS"
            :visible-keys="visibleKeys"
            @change="(key, visible) => setVisible(key, visible)"
            @select-all="selectAll"
            @reset="reset"
          />
        </div>
        <span class="workspace-list__sort-note">按创建时间倒序</span>
      </div>

      <div v-if="hasLoaded && loadError" class="workspace-list__feedback">
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="loadWorkspaces"
        />
      </div>

      <template v-if="hasLoaded || !loadError">
        <el-table
          v-loading="loading"
          :data="workspaces"
          row-key="id"
          class="workspace-list__table"
          @row-click="handleRowClick"
        >
          <el-table-column v-if="visibleKeys.includes('name')" label="空间名称" min-width="160">
            <template #default="{ row }">
              <div @click.stop>
                <ListTitleLink :title="row.name" @open="goDetail(row.id)" />
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('description')" label="描述" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="workspace-list__desc-cell">{{ row.description || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('status')" label="状态" width="100">
            <template #default="{ row }">
              <span class="workspace-list__status" :class="statusMeta(row.status).dot">
                <span class="workspace-list__dot" />{{ statusMeta(row.status).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('members')" label="成员" width="80">
            <template #default="{ row }">
              <span class="workspace-list__num">{{ row.memberCount }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('projects')" label="项目" width="80">
            <template #default="{ row }">
              <span class="workspace-list__num">{{ row.projectCount }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('createdAt')" label="创建时间" width="170">
            <template #default="{ row }">
              <span class="workspace-list__num">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="goDetail(row.id)">查看详情</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <ListLoadFeedback
              v-if="loading || !hasLoaded || !loadError"
              :mode="emptyMode"
              :title="emptyTitle"
              :description="loadError"
              @retry="loadWorkspaces"
              @reset="handleReset"
            />
          </template>
        </el-table>

        <ListPagination
          v-model:page-no="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          @change="loadWorkspaces"
        />
      </template>

      <div v-else class="workspace-list__first-error">
        <ListLoadFeedback mode="error" :title="emptyTitle" :description="loadError" :retrying="loading" @retry="loadWorkspaces" />
      </div>
    </section>

    <WorkspaceCreateDialog
      v-if="canCreate"
      v-model="createDialogVisible"
      @created="handleCreated"
    />
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

.workspace-list__segment {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  gap: 2px;
  background: var(--color-neutral-100);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.workspace-list__seg-item {
  padding: 5px 12px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-600);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    color: var(--color-neutral-800);
  }

  &.is-active {
    background: var(--color-neutral-0);
    color: var(--color-neutral-900);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }
}

.workspace-list__sort-note {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  white-space: nowrap;
}

.workspace-list__feedback {
  padding: 12px 20px 0;
}

.workspace-list__first-error {
  padding: 20px;
}

.workspace-list__table :deep(.el-table__row) {
  cursor: pointer;
}

.workspace-list__desc-cell {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

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

.workspace-list__status--neutral .workspace-list__dot {
  background: var(--color-neutral-400);
}

.workspace-list__num {
  font-variant-numeric: tabular-nums;
}
</style>
