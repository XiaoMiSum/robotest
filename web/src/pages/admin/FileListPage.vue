<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFile, downloadFile, fetchFileAccessUrl, fetchFiles } from '@/services/files'
import { useAuthStore } from '@/stores/auth'
import type { FileItem } from '@/types'
import { formatDateTime, formatFileSize } from '@/utils/format'

const authStore = useAuthStore()
// 删除即回收空间且不可恢复（详设 5.3），仅 file:delete 可见
const canDelete = computed(() => authStore.hasPermission('file:delete'))

const loading = ref(false)
const files = ref<FileItem[]>([])
const total = ref(0)

const query = reactive({
  fileName: '',
  pageNo: 1,
  pageSize: 20,
})

async function loadFiles() {
  loading.value = true
  try {
    const page = await fetchFiles({
      fileName: query.fileName || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    files.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载文件列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadFiles()
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

onMounted(() => {
  loadFiles()
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
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <span class="file-list__count">共 {{ total }} 个文件</span>
      </div>

      <el-table v-loading="loading" :data="files" row-key="id">
        <el-table-column label="文件名" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="file-list__name">{{ row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">
            <span class="file-list__num">{{ formatFileSize(row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="160">
          <template #default="{ row }">
            <span class="file-list__muted">{{ row.contentType || 'application/octet-stream' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传者" width="140">
          <template #default="{ row }">
            <span>{{ row.uploaderName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">
            <span class="file-list__num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDownload(row as FileItem)">下载</el-button>
            <el-button link type="primary" @click="handleCopyLink(row as FileItem)">复制链接</el-button>
            <el-button v-if="canDelete" link type="danger" @click="handleDelete(row as FileItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <footer class="file-list__footer">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadFiles"
          @size-change="handleSearch"
        />
      </footer>
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

.file-list__count,
.file-list__muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.file-list__name {
  font-weight: 500;
}

.file-list__num {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.file-list__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
