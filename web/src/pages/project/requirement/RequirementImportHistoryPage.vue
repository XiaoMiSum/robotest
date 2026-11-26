<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { formatDateTime } from '@/utils/format'
import { useAssistantEntrance } from '@/composables/ai/useAssistantEntrance'
import RequirementImportDialog from '@/components/project/requirement/RequirementImportDialog.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import {
  useRequirementImportHistory,
  type RequirementImportHistoryRow,
} from '@/composables/project/requirement/useRequirementImportHistory'

const REQUIREMENT_LIST_ROUTE = '/workspace/projects/functional-testing?tab=requirements'

const router = useRouter()
const {
  loading,
  hasLoaded,
  loadError,
  rows,
  total,
  pageNo,
  pageSize,
  showSkeleton,
  showError,
  showRefreshError,
  load,
  changePage,
} = useRequirementImportHistory()

const { visible: aiAvailable, refresh: refreshAiStatus } = useAssistantEntrance()
void refreshAiStatus()

const importDialogVisible = ref(false)

function openImport(): void {
  importDialogVisible.value = true
}

/** el-table 插槽行是宽松类型，此处收窄为历史行视图（no-unsafe 后端未知键本就不读取） */
function asRow(row: unknown): RequirementImportHistoryRow {
  return row as RequirementImportHistoryRow
}

function openDetail(id: string): void {
  void router.push(`/workspace/projects/requirements/import-review/${id}`)
}

function outputText(row: RequirementImportHistoryRow): string {
  if (row.taskStatus === 'running' && !row.latestExecution) return '解析中'
  if (row.proposalCount == null) return '—'
  return `${row.proposalCount} 条建议 · ${row.adoptedCount ?? 0} 已采纳`
}

function goBackToList(): void {
  void router.push(REQUIREMENT_LIST_ROUTE)
}
</script>

<template>
  <main class="import-history">
    <header class="import-history__head">
      <div>
        <h1 class="import-history__title">导入历史</h1>
        <p class="import-history__desc">按时间倒序查看项目内历次需求导入任务，点击任务查看执行状态并审核提案</p>
      </div>
      <div class="import-history__actions">
        <el-button v-if="aiAvailable" @click="openImport">
          <el-icon><Upload /></el-icon>导入需求
        </el-button>
        <el-button @click="goBackToList">返回需求列表</el-button>
      </div>
    </header>

    <el-alert
      v-if="showError"
      type="error"
      title="加载导入历史失败"
      :description="loadError"
      show-icon
      :closable="false"
      class="import-history__error"
    >
      <template #default>
        <span>{{ loadError }}</span>
        <el-button type="danger" plain size="small" :loading="loading" @click="load">重试</el-button>
      </template>
    </el-alert>

    <el-skeleton v-else-if="showSkeleton" :rows="8" animated />

    <template v-else>
      <div v-if="showRefreshError" class="import-history__refresh-error">
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="load"
        />
      </div>

      <ListLoadFeedback
        v-if="hasLoaded && rows.length === 0 && !loading && !showRefreshError"
        mode="empty"
        title="暂无导入记录"
        create-label="导入需求"
        @create="openImport"
      />

      <el-card v-else shadow="never">
        <el-table
          v-loading="loading && hasLoaded"
          :data="rows"
          row-key="id"
          class="import-history__table"
        >
          <el-table-column label="导入时间" width="170">
            <template #default="{ row }">{{ formatDateTime(asRow(row).createdAt) }}</template>
          </el-table-column>
          <el-table-column label="源文件" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ asRow(row).sourceFileName ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="任务状态" width="110">
            <template #default="{ row }">
              <el-tag :type="asRow(row).taskStatusMeta.tagType" size="small" effect="light">
                {{ asRow(row).taskStatusMeta.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发起人" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ asRow(row).submitterName }}</template>
          </el-table-column>
          <el-table-column label="产出概览" width="170">
            <template #default="{ row }">{{ outputText(asRow(row)) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="!asRow(row).aiTaskId"
                @click="openDetail(asRow(row).aiTaskId ?? '')"
              >查看明细</el-button>
            </template>
          </el-table-column>
        </el-table>
        <ListPagination
          :total="total"
          :page-no="pageNo"
          :page-size="pageSize"
          @change="changePage"
        />
      </el-card>
    </template>

    <RequirementImportDialog v-model="importDialogVisible" />
  </main>
</template>

<style scoped lang="scss">
.import-history__error {
  flex-shrink: 0;
  margin-bottom: var(--space-md);
}

.import-history__refresh-error {
  flex-shrink: 0;
  margin-bottom: var(--space-md);
}

.import-history__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.import-history__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
}

.import-history__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.import-history__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}
</style>
