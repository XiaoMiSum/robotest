<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchBugs } from '@/services/project'
import type { BugListItem, BugPriority, BugSeverity, BugStatus } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const bugs = ref<BugListItem[]>([])
const total = ref(0)
const viewMode = ref<'list' | 'board'>('list')

const query = reactive({
  status: '' as BugStatus | '',
  severity: '' as BugSeverity | '',
  priority: '' as BugPriority | '',
  keyword: '',
  pageNo: 1,
  pageSize: 20,
})

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
const statusLabel: Record<string, string> = { new: '新建', assigned: '已指派', fixing: '修复中', fixed: '已修复', verified: '已验证', closed: '已关闭' }
const severityType: Record<string, 'danger' | 'warning' | 'success' | 'info'> = { fatal: 'danger', serious: 'warning', general: 'info', minor: 'success' }

async function loadBugs() {
  loading.value = true
  try {
    const page = await fetchBugs({
      status: query.status || undefined,
      severity: query.severity || undefined,
      priority: query.priority || undefined,
      keyword: query.keyword || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    bugs.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载缺陷列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadBugs()
}

const boardStatuses: BugStatus[] = ['new', 'assigned', 'fixing', 'fixed', 'verified', 'closed']

function bugsByStatus(status: BugStatus): BugListItem[] {
  return bugs.value.filter((b) => b.status === status)
}

onMounted(loadBugs)
</script>

<template>
  <div class="bug-page">
    <div class="bug-page__header">
      <h2 class="bug-page__title">缺陷管理</h2>
      <div class="bug-page__actions">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="board">看板</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="router.push('/workspace/projects/bugs/create')">
          <el-icon><Plus /></el-icon>提交缺陷
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="bug-page__filters">
      <el-form :inline="true" @submit.prevent>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
            <el-option v-for="(label, key) in statusLabel" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.severity" placeholder="严重等级" clearable style="width: 120px" @change="handleSearch">
            <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.priority" placeholder="优先级" clearable style="width: 100px" @change="handleSearch">
            <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="搜索标题" clearable style="width: 180px" @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表视图 -->
    <el-card v-if="viewMode === 'list'" shadow="never" v-loading="loading">
      <el-table :data="bugs" row-key="id">
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/workspace/projects/bugs/${row.id}`)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="严重等级" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType[row.severity]" size="small">{{ severityLabel[row.severity] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">{{ priorityLabel[row.priority] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ statusLabel[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="100">
          <template #default="{ row }">{{ row.assignee?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/workspace/projects/bugs/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="bug-page__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadBugs"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- 看板视图 -->
    <div v-else class="bug-board" v-loading="loading">
      <div v-for="status in boardStatuses" :key="status" class="bug-board__column">
        <div class="bug-board__col-header">
          {{ statusLabel[status] }} ({{ bugsByStatus(status).length }})
        </div>
        <div class="bug-board__col-body">
          <div
            v-for="bug in bugsByStatus(status)"
            :key="bug.id"
            class="bug-board__card"
            @click="router.push(`/workspace/projects/bugs/${bug.id}`)"
          >
            <div class="bug-board__card-title">{{ bug.title }}</div>
            <div class="bug-board__card-meta">
              <el-tag :type="severityType[bug.severity]" size="small">{{ severityLabel[bug.severity] }}</el-tag>
              <span v-if="bug.assignee">{{ bug.assignee.name }}</span>
            </div>
          </div>
          <el-empty v-if="!bugsByStatus(status).length" description="" :image-size="30" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.bug-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.bug-page__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.bug-page__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bug-page__filters {
  margin-bottom: 16px;
}

.bug-page__filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.bug-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 看板样式 */
.bug-board {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  min-height: 400px;
}

.bug-board__column {
  flex: 1;
  min-width: 180px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
}

.bug-board__col-header {
  padding: 10px 12px;
  font-weight: 600;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.bug-board__col-body {
  padding: 8px;
  flex: 1;
  overflow-y: auto;
}

.bug-board__card {
  background: var(--el-bg-color);
  border-radius: 4px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  box-shadow: var(--el-box-shadow-lighter);
  transition: box-shadow 0.15s;
}

.bug-board__card:hover {
  box-shadow: var(--el-box-shadow-light);
}

.bug-board__card-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-board__card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
