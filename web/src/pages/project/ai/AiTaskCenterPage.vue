<script setup lang="ts">
import { useRouter } from 'vue-router'
import ListPagination from '@/components/common/ListPagination.vue'
import { formatDateTime } from '@/utils/format'
import { aiTaskTypeOptions } from '@/composables/project/ai/taskPresentation'
import type { AiTaskRow } from '@/composables/project/ai/useAiTaskCenter'
import { useAiTaskCenter } from '@/composables/project/ai/useAiTaskCenter'

const router = useRouter()

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
  refresh,
  changePage,
  changePageSize,
  handleAbort,
  handleReExecute,
} = useAiTaskCenter()

const typeOptions = aiTaskTypeOptions()

/** 状态筛选四态（交互 2.1.2，取值与总册 2.6.1 一致） */
const statusOptions = [
  { value: 'running', label: '执行中' },
  { value: 'proposals_ready', label: '待采纳' },
  { value: 'closed', label: '已结束' },
  { value: 'aborted', label: '已终止' },
]

function openDetail(taskId: string): void {
  void router.push(`/workspace/projects/ai/tasks/${taskId}`)
}

/** el-table 插槽行是宽松类型，此处收窄为行视图模型（no-unsafe 后端未知键本就不读取） */
function asRow(row: unknown): AiTaskRow {
  return row as AiTaskRow
}
</script>

<template>
  <main class="ai-task-center">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">AI 任务中心</h1>
        <p class="page-head__desc">项目内 AI 任务的统一入口，跟踪执行过程与采纳结果</p>
      </div>
      <div class="page-head__actions">
        <el-button size="small" @click="refresh">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
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
        <el-button type="primary" @click="search">
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
        <el-table v-loading="loading" :data="rows" row-key="taskId">
          <el-table-column label="任务名" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click.stop="openDetail(row.taskId)">
                {{ row.name }}
              </el-link>
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
              <el-button link type="primary" @click="openDetail(asRow(row).taskId)">详情</el-button>
              <el-button
                v-if="asRow(row).canAbort"
                link
                type="warning"
                @click="handleAbort(asRow(row))"
              >终止</el-button>
              <el-button
                v-if="asRow(row).canReExecute"
                link
                type="primary"
                size="small"
                @click="handleReExecute(asRow(row))"
              >重新执行</el-button>
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
              <el-button
                type="primary"
                @click="router.push('/workspace/projects/functional-testing?tab=requirements')"
              >
                去需求列表发起 AI 任务
              </el-button>
            </el-empty>
          </template>
        </el-table>

        <!-- store 分页状态为只读 computed，细分事件直连原 handler（切条数已由组件先置页码为 1） -->
        <ListPagination
          bare
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
.page-head {
  display: flex;
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

.page-head__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.list-error {
  margin-bottom: var(--space-md);
}

.list-skeleton {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.ai-task-center__card {
  margin-bottom: var(--space-md);
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
