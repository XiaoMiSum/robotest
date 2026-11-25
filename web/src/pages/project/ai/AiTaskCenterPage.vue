<script setup lang="ts">
import { useRouter } from 'vue-router'
import ListPagination from '@/components/common/ListPagination.vue'
import RowActions from '@/components/common/RowActions.vue'
import { formatDateTime } from '@/utils/format'
import { aiTaskTypeOptions } from '@/composables/project/ai/taskPresentation'
import type { AiTaskRow } from '@/composables/project/ai/useAiTaskCenter'
import { useAiTaskCenter } from '@/composables/project/ai/useAiTaskCenter'

const {
  loading,
  hasLoaded,
  loadError,
  rows,
  total,
  pageNo,
  pageSize,
  pageSizes,
  filters,
  aiAvailable,
  aiStatusLoaded,
  retry,
  search,
  resetFilters,
  changePage,
  changePageSize,
  handleAbort,
  handleReExecute,
} = useAiTaskCenter()

const router = useRouter()
const typeOptions = aiTaskTypeOptions()

/** 状态筛选四态（交互 2.1.2，取值与总册 2.6.1 一致） */
const statusOptions = [
  { value: 'running', label: '执行中' },
  { value: 'proposals_ready', label: '待采纳' },
  { value: 'closed', label: '已结束' },
  { value: 'aborted', label: '已终止' },
]

/** el-table 插槽行是宽松类型，此处收窄为行视图模型（no-unsafe 后端未知键本就不读取） */
function asRow(row: unknown): AiTaskRow {
  return row as AiTaskRow
}

function actionsOf(row: unknown) {
  const task = asRow(row)
  return [
    { key: 'detail', label: '详情', form: 'PLsm' as const },
    { key: 'abort', label: '终止', form: 'DGsm' as const, visible: task.canAbort },
    { key: 'reExecute', label: '重新执行', form: 'PLsm' as const, visible: task.canReExecute },
  ]
}

function onRowAction(key: string, row: unknown): void {
  const task = asRow(row)
  if (key === 'detail') void router.push(`/workspace/projects/ai/tasks/${task.taskId}`)
  else if (key === 'abort') void handleAbort(task)
  else if (key === 'reExecute') void handleReExecute(task)
}
</script>

<template>
  <main class="ai-task-center">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">AI 任务中心</h1>
        <p class="page-head__desc">项目内 AI 任务的统一入口，跟踪执行过程与采纳结果</p>
      </div>
    </header>

    <!-- AI 未启用 / 未配置模型：整页降级提示（交互 2.1.3） -->
    <el-result
      v-if="aiStatusLoaded && !aiAvailable"
      status="warning"
      title="AI 能力未启用 / 未配置模型"
      sub-title="开启 AI 总开关并配置可用模型后，任务将在此展示"
    />

    <template v-else>
      <div class="filter-bar">
        <el-select
          v-model="filters.type"
          clearable
          filterable
          placeholder="任务类型"
          style="width: 220px"
          @change="search"
        >
          <el-option v-for="option in typeOptions" :key="option.value" v-bind="option" />
        </el-select>
        <el-select
          v-model="filters.status"
          clearable
          placeholder="任务状态"
          style="width: 150px"
          @change="search"
        >
          <el-option v-for="option in statusOptions" :key="option.value" v-bind="option" />
        </el-select>
        <el-button @click="search">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <!-- 列表错误：提示条 + 重试，不展示空态以免误导（UI-PAGE-11） -->
      <el-alert
        v-if="loadError"
        type="error"
        :title="loadError"
        show-icon
        :closable="false"
        class="list-error"
      >
        <template #default>
          <el-button type="danger" plain size="small" @click="retry">重试</el-button>
        </template>
      </el-alert>

      <el-skeleton
        v-if="!hasLoaded && !loadError && loading"
        :rows="6"
        animated
        class="list-skeleton"
      />

      <el-card v-else shadow="never" class="ai-task-center__card">
        <div class="ai-task-center__table">
          <el-table v-loading="loading" :data="rows" row-key="taskId" height="100%">
            <el-table-column label="任务名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <router-link :to="`/workspace/projects/ai/tasks/${row.taskId}`">
                  {{ row.name }}
                </router-link>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ row.typeMeta.domain }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <!-- 已终止悬浮终止原因（交互 2.1.2），标签同时带文字不单靠颜色表意 -->
                <el-tooltip
                  v-if="row.status === 'aborted' && row.terminateText"
                  :content="row.terminateText"
                  placement="top"
                >
                  <el-tag :type="row.statusMeta.tagType" size="small" effect="light">
                    {{ row.statusMeta.label }}
                  </el-tag>
                </el-tooltip>
                <el-tag v-else :type="row.statusMeta.tagType" size="small" effect="light">
                  {{ row.statusMeta.label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="执行摘要" min-width="200">
              <template #default="{ row }">
                <!-- 执行中：步数进度条 + 步数与最近动作；其余态给摘要文案（交互 2.1.2） -->
                <div v-if="row.summary.stepPercent !== null" class="task-summary">
                  <el-progress
                    :percentage="row.summary.stepPercent"
                    :stroke-width="8"
                    :show-text="false"
                    class="task-summary__bar"
                  />
                  <span class="task-summary__text">{{ row.summary.text }}</span>
                </div>
                <span v-else class="task-summary__muted">{{ row.summary.text }}</span>
              </template>
            </el-table-column>
            <el-table-column label="发起人" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.submitterText }}</template>
            </el-table-column>
            <el-table-column label="发起时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <RowActions :actions="actionsOf(row)" @action="(key) => onRowAction(key, row)" />
              </template>
            </el-table-column>
            <template #empty>
              <el-empty
                v-if="filters.type || filters.status"
                description="无匹配结果"
                :image-size="80"
              >
                <el-button @click="resetFilters">重置筛选</el-button>
              </el-empty>
              <el-empty v-else description="暂无 AI 任务" :image-size="80">
                <router-link to="/workspace/projects/functional-testing?tab=requirements">
                  去需求列表发起 AI 任务
                </router-link>
              </el-empty>
            </template>
          </el-table>
        </div>

        <!-- store 分页状态为只读 computed，细分事件直连原 handler（切条数已由组件先置页码为 1） -->
        <ListPagination
          :page-no="pageNo"
          :page-size="pageSize"
          :total="total"
          :page-sizes="pageSizes"
          @page-change="({ pageNo: page }) => changePage(page)"
          @size-change="({ pageSize: size }) => changePageSize(size)"
        />
      </el-card>
    </template>
  </main>
</template>

<style scoped lang="scss">
.ai-task-center {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.page-head {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.filter-bar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.list-error {
  flex-shrink: 0;
  margin-bottom: var(--space-md);
}

.list-skeleton {
  flex-shrink: 0;
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.ai-task-center__card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  --el-card-padding: 0;

  :deep(.el-card__body) {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
  }

  a {
    color: var(--color-primary-500);
    text-decoration: none;
  }
}

.ai-task-center__table {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.task-summary {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.task-summary__bar {
  flex: 1;
  min-width: 90px;
}

.task-summary__text {
  flex-shrink: 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.task-summary__muted {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}
</style>
