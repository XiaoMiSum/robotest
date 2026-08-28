<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiReportPageItem, ApiScenePageItem } from '@/types'
import {
  batchDeleteReports,
  batchExportReports,
  deleteReport,
  exportReportUrl,
  fetchReportPage,
} from '@/services/apiReport'
import { fetchScenePage } from '@/services/apiScene'
import { formatDateTime } from '@/utils/format'

const emit = defineEmits<{
  (e: 'view', reportId: string): void
}>()

// ==================== 筛选 ====================
const searchText = ref('')
const statusFilter = ref('')
const executionModeFilter = ref('')
const sceneFilter = ref<string>('')
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

const executionModeOptions = [
  { value: '', label: '全部执行方式' },
  { value: 'platform', label: '平台内执行' },
  { value: 'pipeline', label: '仓库流水线' },
]

// ==================== 场景列表（用于筛选） ====================
const sceneOptions = ref<ApiScenePageItem[]>([])

async function loadScenes() {
  try {
    const page = await fetchScenePage({ pageNo: 1, pageSize: 200 })
    sceneOptions.value = page.list
  } catch {
    sceneOptions.value = []
  }
}

// ==================== 列表 ====================
const rows = ref<ApiReportPageItem[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])

async function loadPage() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize }
    if (statusFilter.value) params.status = statusFilter.value
    if (executionModeFilter.value) params.executionMode = executionModeFilter.value
    if (sceneFilter.value) params.sceneId = sceneFilter.value
    if (searchText.value.trim()) params.keyword = searchText.value.trim()
    if (dateRange.value && dateRange.value[0]) params.startDate = dateRange.value[0]
    if (dateRange.value && dateRange.value[1]) params.endDate = dateRange.value[1]
    const page = await fetchReportPage(params as { pageNo: number; pageSize: number; status?: string; executionMode?: string; sceneId?: string; keyword?: string; startDate?: string; endDate?: string })
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

watch([statusFilter, executionModeFilter, sceneFilter], handleFilter)

// ==================== 报告名称派生 ====================
function reportName(row: ApiReportPageItem): string {
  const date = row.createdAt ? formatDateTime(row.createdAt) : ''
  return `${row.sceneName}-${date}`
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

function handleExport(row: ApiReportPageItem) {
  const url = exportReportUrl(row.id, 'json')
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportName(row)}.json`
  a.click()
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

async function handleBatchExport() {
  if (!selectedIds.value.length) return
  try {
    const blob = await batchExportReports(selectedIds.value, 'json')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `api-reports-${Date.now()}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`批量删除 ${selectedIds.value.length} 份报告？删除后不可恢复。`, '批量删除', {
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
  await Promise.all([loadScenes(), loadPage()])
})
</script>

<template>
  <div class="reports-page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="reports-page__toolbar">
          <el-select v-model="statusFilter" style="width: 140px" @change="handleFilter">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-select v-model="executionModeFilter" style="width: 160px" @change="handleFilter">
            <el-option v-for="opt in executionModeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-select v-model="sceneFilter" placeholder="全部场景" clearable filterable style="width: 180px" @change="handleFilter">
            <el-option v-for="scene in sceneOptions" :key="scene.id" :label="scene.name" :value="scene.id" />
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
            placeholder="搜索报告名称或场景名称"
            clearable
            style="width: 240px"
            @keyup.enter="handleFilter"
            @clear="handleFilter"
          />
          <div class="reports-page__spacer" />
          <template v-if="hasSelection">
            <span class="reports-page__selected-count">已选 {{ selectedIds.length }} 项</span>
            <el-button @click="handleBatchExport">批量导出</el-button>
            <el-button type="danger" @click="handleBatchDelete">批量删除</el-button>
            <el-divider direction="vertical" />
          </template>
        </div>
      </template>

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
        <el-table-column prop="sceneName" label="场景名称" width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType((row as ApiReportPageItem).status)">
              {{ statusLabel((row as ApiReportPageItem).status) }}
            </el-tag>
            <el-tag v-if="(row as ApiReportPageItem).executionMode === 'pipeline'" size="small" type="info" style="margin-left: 4px">
              流水线
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-red-500': (row as ApiReportPageItem).summary?.failed > 0 }">
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
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-dropdown
trigger="click" @command="(cmd: string) => {
              const r = row as ApiReportPageItem
              if (cmd === 'view') handleView(r)
              else if (cmd === 'export') handleExport(r)
              else if (cmd === 'delete') handleDelete(r)
            }">
              <el-button link size="small">操作</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="view">查看</el-dropdown-item>
                  <el-dropdown-item command="export">导出</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无测试报告，执行测试场景后生成报告" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="reports-page__pagination"
        @current-change="loadPage"
      />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.reports-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.reports-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.reports-page__spacer {
  flex: 1;
}

.reports-page__selected-count {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.reports-page__pagination {
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.text-red-500 {
  color: var(--color-danger);
}
</style>
