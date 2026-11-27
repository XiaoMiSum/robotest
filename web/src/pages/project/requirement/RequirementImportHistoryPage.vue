<script setup lang="ts">
import { ref, type CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
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

/** 卡片 body 转 flex 列，表格根节点才能 flex:1 承接剩余高度并随窗口动态计算（UI-SC-01/06），走组件属性避免 :deep */
const cardBodyStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  flex: '1 1 auto',
  minHeight: '0',
  padding: '0',
}

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
    <!-- 页头收拢为单条无边框横条：面包屑与操作同行，超宽横向滚动（评审明细页口径） -->
    <header class="import-history__head">
      <nav class="import-history__breadcrumb" aria-label="面包屑">
        <router-link :to="REQUIREMENT_LIST_ROUTE">需求管理</router-link>
        <el-icon :size="13"><ArrowRight /></el-icon>
        <span>导入历史</span>
      </nav>
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

      <el-card v-else shadow="never" class="import-history__card" :body-style="cardBodyStyle">
        <!-- 表格根节点作 flex 伸缩子项承接剩余高度：表头固定、数据超出时表体内滚动（UI-SC-05/06） -->
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
/* 高度链路：页面 → 卡片 → 表格，滚动只发生在表体（UI-SC-01/02） */
.import-history {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.import-history__error {
  flex-shrink: 0;
  margin-bottom: var(--space-md);
}

.import-history__refresh-error {
  flex-shrink: 0;
  margin-bottom: var(--space-md);
}

/* 单条无边框横条：面包屑 + 操作同行；超宽整行横向滚动而非换行（评审明细页口径） */
.import-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  flex-shrink: 0;
  flex-wrap: nowrap;
  overflow-x: auto;
  margin-bottom: var(--block-gap);
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    width: 0;
    height: 0;
    display: none;
  }
}

.import-history__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-shrink: 0;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);

  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;

    &:hover {
      color: var(--color-primary-600);
    }
  }

  :deep(.el-icon) {
    color: var(--color-neutral-400);
  }

  > :last-child {
    color: var(--color-neutral-900);
  }
}

.import-history__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

/* 卡片去悬浮：无阴影无圆角、body 内边距归零，表格贴边（同需求列表） */
.import-history__card {
  --el-card-padding: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  border-radius: 0;
  box-shadow: none;
}

/* 表格根节点作 flex 伸缩子项：随窗口承接卡片剩余高度，滚动由组件内表体承担（UI-SC-05/06） */
.import-history__table {
  flex: 1 1 auto;
  min-height: 0;
}
</style>
