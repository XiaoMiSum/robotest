<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiReportPageItem } from '@/types'
import {
  batchDeleteReports,
  deleteReport,
  fetchReportPage,
} from '@/services/project/api-testing/report'
import { formatDateTime } from '@/utils/format'

const emit = defineEmits<{
  (e: 'view', reportId: string): void
}>()

// ==================== 筛选 ====================
const searchText = ref('')
const statusFilter = ref('')
const dateRange = ref<[string, string] | null>(null)
const pageNo = ref(1)
const pageSize = 20
const loading = ref(false)

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'success', label: '通过' },
  { value: 'failed', label: '失败' },
  { value: 'partial', label: '部分通过' },
]

// ==================== 列表 ====================
const rows = ref<ApiReportPageItem[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])

async function loadPage() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize }
    if (statusFilter.value) params.status = statusFilter.value
    if (searchText.value.trim()) params.keyword = searchText.value.trim()
    if (dateRange.value && dateRange.value[0]) params.startDate = dateRange.value[0]
    if (dateRange.value && dateRange.value[1]) params.endDate = dateRange.value[1]
    const page = await fetchReportPage(params as { pageNo: number; pageSize: number; status?: string; keyword?: string; startDate?: string; endDate?: string })
    rows.value = page.list
    total.value = page.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  pageNo.value = 1
  selectedIds.value = []
  void loadPage()
}

watch(statusFilter, handleFilter)

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
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ==================== 批量操作 ====================
const hasSelection = computed(() => selectedIds.value.length > 0)

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
    selectedIds.value = []
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(async () => {
  await loadPage()
})
</script>

<template>
  <div v-loading="loading" class="reports-page">
    <div class="reports-page__toolbar">
      <el-select v-model="statusFilter" style="width: 140px" @change="handleFilter">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DDT00:00:00"
        style="width: 280px"
        @change="handleFilter"
      />
      <el-input
        v-model="searchText"
        placeholder="报告名称"
        clearable
        style="width: 240px"
        @keyup.enter="handleFilter"
        @clear="handleFilter"
      />
    </div>

    <el-table
      :data="rows"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column label="报告名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="handleView(row as ApiReportPageItem)">
            {{ reportName(row as ApiReportPageItem) }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="(row as ApiReportPageItem).reportType === 'suite' ? 'info' : 'primary'">
            {{ (row as ApiReportPageItem).reportType === 'suite' ? '套件报告' : '场景报告' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType((row as ApiReportPageItem).status)">
            {{ statusLabel((row as ApiReportPageItem).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通过率" width="90" align="center">
        <template #default="{ row }">
          <span :class="{ 'text-red-500': ((row as ApiReportPageItem).summary?.failed ?? 0) > 0 }">
            {{ passRate((row as ApiReportPageItem).summary) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">
          {{ formatDuration((row as ApiReportPageItem).summary?.durationMs) }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime((row as ApiReportPageItem).createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleView(row as ApiReportPageItem)">查看</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row as ApiReportPageItem)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无测试报告，定时任务执行后生成；场景页运行报告可在场景执行历史中查看" />
      </template>
    </el-table>

    <div class="reports-page__footer">
      <el-button v-if="hasSelection" link type="danger" data-test="batch-delete-btn" @click="handleBatchDelete">删除</el-button>
      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="reports-page__pagination"
        @current-change="loadPage"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.reports-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* UI-SC-10：工具栏与分页条各自留白并以分割线与表格分隔；左右 0 与表格贴合 */
.reports-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
  flex-shrink: 0;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-neutral-100);
}

.reports-page__footer {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
  padding: 14px 0;
  border-top: 1px solid var(--color-neutral-100);
}

.reports-page__pagination {
  justify-content: flex-end;
  margin-left: auto;
}

.text-red-500 {
  color: var(--color-danger);
}
</style>
