<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFile, downloadFile, fetchFileAccessUrl, fetchFiles } from '@/services/files'
import { useAuthStore } from '@/stores/auth'
import type { FileItem } from '@/types'
import { formatDateTime, formatFileSize } from '@/utils/format'
import ListPagination from '@/components/common/ListPagination.vue'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'

const FILE_COLUMNS: TableColumnMeta[] = [
  { key: 'name', label: '文件名' },
  { key: 'size', label: '大小' },
  { key: 'type', label: '类型' },
  { key: 'uploader', label: '上传者' },
  { key: 'uploadedAt', label: '上传时间' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'file-list',
  columns: FILE_COLUMNS,
})

const authStore = useAuthStore()
// 删除即回收空间且不可恢复（详设 5.3），仅 file:delete 可见
const canDelete = computed(() => authStore.hasPermission('file:delete'))

const loading = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const files = ref<FileItem[]>([])
const total = ref(0)
let requestId = 0

const query = reactive({
  fileName: '',
  pageNo: 1,
  pageSize: 20,
})

const hasFilters = computed(() => Boolean(query.fileName))
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() => {
  if (loading.value) return '正在加载文件…'
  if (loadError.value) return '加载文件列表失败'
  if (hasFilters.value) return '无匹配结果'
  return '暂无文件'
})

async function loadFiles() {
  const current = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const page = await fetchFiles({
      fileName: query.fileName || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    if (current !== requestId) return
    files.value = page.list
    total.value = page.total
    hasLoaded.value = true
  } catch (err) {
    if (current !== requestId) return
    loadError.value = err instanceof Error ? err.message : '加载文件列表失败'
    ElMessage.error(loadError.value)
  } finally {
    if (current === requestId) loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  void loadFiles()
}

function handleReset() {
  query.fileName = ''
  handleSearch()
}

async function handleDownload(row: FileItem) {
  try {
    await downloadFile(row.id, row.fileName)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '下载失败')
  }
}

async function handleCopyLink(row: FileItem) {
  try {
    const { url, expiresIn } = await fetchFileAccessUrl(row.id)
    const minutes = Math.max(1, Math.round(expiresIn / 60))
    try {
      await navigator.clipboard.writeText(url)
      ElMessage.success(`临时链接已复制（${minutes} 分钟内有效）`)
    } catch {
      // 剪贴板不可用（非安全上下文等）：回退展示链接，验收入口仍可用
      ElMessage.warning(`临时链接（${minutes} 分钟内有效）：${url}`)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '获取临时链接失败')
  }
}

async function handleDelete(row: FileItem) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${row.fileName}」？删除后不可恢复。`, '删除文件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    // 用户取消确认
    return
  }
  try {
    await deleteFile(row.id)
    ElMessage.success('已删除')
    await loadFiles()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

onBeforeUnmount(() => {
  requestId += 1
})

onMounted(() => {
  void loadFiles()
})
</script>

<template>
  <div class="file-list">
    <div class="file-list__head">
      <div>
        <h1 class="file-list__title">文件管理</h1>
        <p class="file-list__desc">平台泛化附件资源：查看、下载与清理</p>
      </div>
    </div>

    <section class="file-list__card">
      <div class="file-list__toolbar">
        <div class="file-list__filters">
          <el-input
            v-model="query.fileName"
            placeholder="搜索文件名"
            clearable
            :prefix-icon="'Search'"
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
          <el-button @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <TableColumnSetting
            :columns="FILE_COLUMNS"
            :visible-keys="visibleKeys"
            @change="(key, visible) => setVisible(key, visible)"
            @select-all="selectAll"
            @reset="reset"
          />
        </div>
        <span class="file-list__count">共 {{ total }} 个文件</span>
      </div>

      <div v-if="hasLoaded && loadError" class="file-list__feedback">
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="loadFiles"
        />
      </div>

      <template v-if="hasLoaded || !loadError">
        <el-table v-loading="loading" :data="files" row-key="id">
          <el-table-column v-if="visibleKeys.includes('name')" label="文件名" min-width="240">
            <template #default="{ row }">
              <ListTitleLink :title="(row as FileItem).fileName" @open="() => {}" />
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('size')" label="大小" width="110">
            <template #default="{ row }">
              <span class="file-list__num">{{ formatFileSize((row as FileItem).fileSize) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('type')" label="类型" min-width="160">
            <template #default="{ row }">
              <span class="file-list__muted">{{ (row as FileItem).contentType || 'application/octet-stream' }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('uploader')" label="上传者" width="140">
            <template #default="{ row }">
              <span>{{ (row as FileItem).uploaderName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleKeys.includes('uploadedAt')" label="上传时间" width="170">
            <template #default="{ row }">
              <span class="file-list__num">{{ formatDateTime((row as FileItem).createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleDownload(row as FileItem)">下载</el-button>
              <el-button link type="primary" @click="handleCopyLink(row as FileItem)">复制链接</el-button>
              <el-button v-if="canDelete" link type="danger" size="small" @click="handleDelete(row as FileItem)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <ListLoadFeedback
              v-if="loading || !hasLoaded || !loadError"
              :mode="emptyMode"
              :title="emptyTitle"
              :description="loadError"
              @retry="loadFiles"
              @reset="handleReset"
            />
          </template>
        </el-table>

        <ListPagination
          v-model:page-no="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          @change="loadFiles"
        />
      </template>

      <div v-else class="file-list__first-error">
        <ListLoadFeedback mode="error" :title="emptyTitle" :description="loadError" :retrying="loading" @retry="loadFiles" />
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.file-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.file-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.file-list__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.file-list__desc {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.file-list__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.file-list__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.file-list__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.file-list__feedback {
  margin-top: 4px;
}

.file-list__first-error {
  padding: 8px 0;
}

.file-list__count,
.file-list__muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.file-list__num {
  font-variant-numeric: tabular-nums;
}
</style>
