<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiReportPageItem } from '@/types'
import {
  batchDeleteReports,
  deleteReport,
  fetchReportPage,
} from '@/services/project/api-testing/report'
import { formatDateTime } from '@/utils/format'
import BatchActionBar from '@/components/common/BatchActionBar.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'

const REPORT_COLUMNS: TableColumnMeta[] = [
  { key: 'selection', label: '选择', pinned: true },
  { key: 'name', label: '报告名称' },
  { key: 'type', label: '类型' },
  { key: 'status', label: '状态' },
  { key: 'passRate', label: '通过率' },
  { key: 'duration', label: '耗时' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'report-list',
  columns: REPORT_COLUMNS,
})

const emit = defineEmits<{
  (e: 'view', reportId: string): void
}>()

// ==================== 筛选 ====================
const searchText = ref('')
const statusFilter = ref('')
const dateRange = ref<[string, string] | null>(null)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
let requestId = 0

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'success', label: '通过' },
  { value: 'failed', label: '失败' },
  { value: 'partial', label: '部分通过' },
]

const hasFilters = computed(() =>
  Boolean(searchText.value.trim() || statusFilter.value || dateRange.value),
)
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() => {
  if (loading.value) return '正在加载报告…'
  if (loadError.value) return '测试报告列表加载失败'
  if (hasFilters.value) return '无匹配结果'
  return '暂无测试报告'
})

// ==================== 列表 ====================
const rows = ref<ApiReportPageItem[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
// 批量条清空需直接驱动表格勾选态，避免只清 ids 导致复选框残留
const tableRef = ref<{ clearSelection: () => void } | null>(null)

async function loadPage() {
  const current = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (statusFilter.value) params.status = statusFilter.value
    if (searchText.value) params.sceneName = searchText.value
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const page = await fetchReportPage(params as Parameters<typeof fetchReportPage>[0])
    if (current !== requestId) return
    rows.value = page.list
    total.value = page.total
    hasLoaded.value = true
  } catch (error) {
    if (current !== requestId) return
    loadError.value = error instanceof Error ? error.message : '测试报告列表加载失败'
    ElMessage.error(loadError.value)
  } finally {
    if (current === requestId) loading.value = false
  }
}

function handleFilter() {
  pageNo.value = 1
  tableRef.value?.clearSelection()
  void loadPage()
}

watch(statusFilter, handleFilter)

onBeforeUnmount(() => {
  requestId += 1
})

// ==================== 报告名称派生 ====================
function reportName(row: ApiReportPageItem): string {
  // 后端已固化报告名称（场景报告：场景名+时间戳；套件报告：任务名+时间戳）
  if (row.name) return row.name
  const date = row.createdAt ? formatDateTime(row.createdAt) : ''
  return `${row.sceneName ?? ''}-${date}`
}

// ==================== 状态样式 ====================
function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'partial') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    success: '通过',
    failed: '失败',
    partial: '部分通过',
  }
  return map[status] ?? status
}

// ==================== 通过率 ====================
function passRate(summary: ApiReportPageItem['summary']): string {
  if (!summary || !summary.total) return '-'
  const rate = ((summary.passed ?? 0) / summary.total) * 100
  return `${rate.toFixed(1)}%`
}

// ==================== 耗时 ====================
function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

// ==================== 行操作 ====================
function handleView(row: ApiReportPageItem) {
  emit('view', row.id)
}

async function handleDelete(row: ApiReportPageItem) {
  await ElMessageBox.confirm(`删除报告「${reportName(row)}」？删除后不可恢复。`, '删除报告', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  try {
    await deleteReport(row.id)
    ElMessage.success('已删除')
    if (rows.value.length <= 1 && pageNo.value > 1) pageNo.value -= 1
    void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ==================== 批量操作 ====================
function handleSelectionChange(selection: ApiReportPageItem[]) {
  selectedIds.value = selection.map((r) => r.id)
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`删除选中的 ${selectedIds.value.length} 份报告？删除后不可恢复。`, '删除报告', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  try {
    await batchDeleteReports(selectedIds.value)
    ElMessage.success('已删除')
    tableRef.value?.clearSelection()
    if (rows.value.length <= selectedIds.value.length && pageNo.value > 1) pageNo.value -= 1
    void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function clearFilters() {
  searchText.value = ''
  statusFilter.value = ''
  dateRange.value = null
  handleFilter()
}

onMounted(async () => {
  await loadPage()
})
</script>

<template>
  <div v-loading="loading" class="reports-page">
    <div class="reports-page__toolbar">
      <el-select v-model="statusFilter" style="width: 110px" @change="handleFilter">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DDT00:00:00"
        style="width: 220px"
        @change="handleFilter"
      />
      <el-input
        v-model="searchText"
        placeholder="报告名称"
        clearable
        style="width: 200px"
        @keyup.enter="handleFilter"
        @clear="handleFilter"
      />
      <TableColumnSetting
        :columns="REPORT_COLUMNS"
        :visible-keys="visibleKeys"
        @change="(key, visible) => setVisible(key, visible)"
        @select-all="selectAll"
        @reset="reset"
      />
    </div>

    <div v-if="hasLoaded && loadError" class="reports-page__feedback">
      <ListLoadFeedback
        mode="refresh-error"
        title="刷新失败，当前显示上次加载的数据"
        :description="loadError"
        :retrying="loading"
        @retry="loadPage"
      />
    </div>

    <!-- 表体区是页面内唯一滚动拥有者：表格固定高度、表头固定，超出时表体内滚动（UI-SC-02 / UI-SC-05 / UI-SC-06） -->
    <div v-if="hasLoaded || !loadError" class="reports-page__body">
      <el-table
        ref="tableRef"
        :data="rows"
        height="100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column v-if="visibleKeys.includes('name')" label="报告名称" min-width="220">
          <template #default="{ row }">
            <ListTitleLink
              :title="reportName(row as ApiReportPageItem)"
              @open="handleView(row as ApiReportPageItem)"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('type')" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(row as ApiReportPageItem).reportType === 'suite' ? 'info' : 'primary'">
              {{ (row as ApiReportPageItem).reportType === 'suite' ? '套件报告' : '场景报告' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('status')" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType((row as ApiReportPageItem).status)">
              {{ statusLabel((row as ApiReportPageItem).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('passRate')" label="通过率" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-red-500': ((row as ApiReportPageItem).summary?.failed ?? 0) > 0 }">
              {{ passRate((row as ApiReportPageItem).summary) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('duration')" label="耗时" width="90" align="center">
          <template #default="{ row }">
            {{ formatDuration((row as ApiReportPageItem).summary?.durationMs) }}
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('createdAt')" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime((row as ApiReportPageItem).createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row as ApiReportPageItem)">查看</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as ApiReportPageItem)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <ListLoadFeedback
            v-if="loading || !hasLoaded || !loadError"
            :mode="emptyMode"
            :title="emptyTitle"
            :description="loadError"
            @retry="loadPage"
            @reset="clearFilters"
          />
        </template>
      </el-table>
    </div>

    <div v-else class="reports-page__first-error">
      <ListLoadFeedback mode="error" :title="emptyTitle" :description="loadError" :retrying="loading" @retry="loadPage" />
    </div>

    <!-- 页面壳已提供左右内边距，分页用 bare 模式与工具栏对齐同一左基线 -->
    <div v-if="hasLoaded || !loadError" class="reports-page__footer">
      <ListPagination
        v-model:page-no="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        bare
        @change="loadPage"
      />
    </div>

    <BatchActionBar :count="selectedIds.length" @clear="tableRef?.clearSelection()">
      <el-button size="small" type="danger" data-test="batch-delete-btn" @click="handleBatchDelete">批量删除</el-button>
    </BatchActionBar>
  </div>
</template>

<style scoped lang="scss">
.reports-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* UI-SC-01/06：表体区承接剩余高度并裁剪，el-table 100% 高度内滚动 */
.reports-page__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.reports-page__feedback {
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-neutral-100);
}

.reports-page__first-error {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  padding: 14px 0;
}

/* UI-SC-10：工具栏与分页条各自留白并以分割线与表格分隔；左右 0 与表格贴合 */
.reports-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: nowrap;
  flex-shrink: 0;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-neutral-100);
}

.reports-page__footer {
  flex-shrink: 0;
  border-top: 1px solid var(--color-neutral-100);
}

.text-red-500 {
  color: var(--color-danger);
}
</style>
