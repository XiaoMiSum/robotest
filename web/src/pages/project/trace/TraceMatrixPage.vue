<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTraceStore } from '@/stores/trace'
import type { TraceGapType, TraceNodeType } from '@/types'
import { TRACE_MATRIX_COLUMNS } from '@/composables/project/trace/tracePresentation'
import { useTraceMatrix, type TraceMatrixRowView } from '@/composables/project/trace/useTraceMatrix'
import { useTraceGaps, type TraceGapView } from '@/composables/project/trace/useTraceGaps'
import TraceLegend from '@/components/project/trace/TraceLegend.vue'
import GapList from '@/components/project/trace/GapList.vue'
import GenerationConfigDialog from '@/components/project/ai/GenerationConfigDialog.vue'
import AiTaskEntryButton from '@/components/project/ai/AiTaskEntryButton.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const traceStore = useTraceStore()

const {
  loading,
  hasLoaded,
  loadError,
  rows,
  total,
  pageNo,
  pageSize,
  filters,
  filterCount,
  aiAvailable,
  retry,
  search,
  resetFilters,
  changePage,
  changePageSize,
  openChain,
} = useTraceMatrix()

const {
  gaps,
  total: gapTotal,
  pageNo: gapPageNo,
  pageSize: gapPageSize,
  loading: gapLoading,
  loadError: gapLoadError,
  hasLoaded: gapHasLoaded,
  gapType,
  typeOptions,
  retry: retryGaps,
  setType: setGapType,
  changePage: changeGapPage,
  changePageSize: changeGapPageSize,
  openChain: openGapChain,
  runAction: runGapAction,
  generationDialogVisible,
  generationScope,
  generationModuleTree,
} = useTraceGaps()

const view = computed({
  get: () => traceStore.view,
  set: (next: 'matrix' | 'gaps') => traceStore.setView(next),
})

const canCreateRequirement = computed(() => authStore.hasPermission('requirement:create'))

/** 首屏骨架：仅首屏加载中显示，刷新走整卡遮罩（交互 04 §2.6） */
const showSkeleton = computed(() => !hasLoaded.value && !loadError.value && loading.value)

const statusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'confirmed', label: '已确认' },
  { value: 'changed', label: '已变更' },
  { value: 'archived', label: '已归档' },
]

const coverageOptions = [
  { value: 'covered', label: '完整覆盖' },
  { value: 'partial', label: '部分覆盖' },
  { value: 'uncovered', label: '未覆盖' },
  { value: 'pending', label: '待分析' },
]

// ==================== 链路入口 ====================
function openCell(row: TraceMatrixRowView, type: TraceNodeType): void {
  openChain(row, type)
}

function handleGapAction(gap: TraceGapView): void {
  runGapAction(gap)
}

// 旧深链（?tab=trace&requirementId=xxx）重定向至链路明细页（详设 05 §5.1 兼容）
onMounted(() => {
  const requirementId = route.query.requirementId
  if (typeof requirementId === 'string' && requirementId) {
    void router.replace({
      name: 'TraceChain',
      params: { nodeType: 'requirement', nodeId: requirementId },
    })
  }
})
</script>

<template>
  <main class="trace-matrix">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">追溯矩阵</h1>
        <p class="page-head__desc">展示需求到模块、文档、用例与计划的全链路关系</p>
      </div>
      <div class="page-head__actions">
        <!-- 能力页任务中心入口（交互 07 §1）：与生成/覆盖分析共用页头动作区 -->
        <AiTaskEntryButton />
        <el-radio-group v-model="view" class="trace-matrix__switch">
          <el-radio-button value="matrix">矩阵</el-radio-button>
          <el-radio-button value="gaps">缺口</el-radio-button>
        </el-radio-group>
      </div>
    </header>

    <!-- 错误提示条保持页面级（卡片外），重试与卡片 loading 分离（交互 04 §2.6） -->
    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="list-error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="retry">重试</el-button>
      </template>
    </el-alert>

    <!-- 矩阵视图 -->
    <template v-if="view === 'matrix'">
      <!-- UI-SC-10：卡片内边距归零使表格贴边，筛选入工具栏，刷新走整卡遮罩（交互 04 §2.1） -->
      <el-card v-loading="loading && hasLoaded" shadow="never" class="trace-matrix__card">
        <!-- 筛选入卡片工具栏：状态 / 覆盖 / 关键词，角标显示条件数、右端常驻总数（交互 04 §2.1） -->
        <div class="filter-bar">
          <el-select
            v-model="filters.requirementStatus"
            clearable
            placeholder="状态"
            style="width: 140px"
            @change="search"
          >
            <el-option v-for="option in statusOptions" :key="option.value" v-bind="option" />
          </el-select>
          <el-select
            v-model="filters.coverage"
            clearable
            placeholder="覆盖"
            style="width: 140px"
            @change="search"
          >
            <el-option v-for="option in coverageOptions" :key="option.value" v-bind="option" />
          </el-select>
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="编号或标题"
            style="width: 220px"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="search">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-badge v-if="filterCount > 0" :value="filterCount" class="filter-bar__badge" />
          <span class="filter-bar__count">共 {{ total }} 条</span>
        </div>

        <el-skeleton v-if="showSkeleton" :rows="6" animated class="list-skeleton" />

        <el-table v-else :data="rows" row-key="requirementId" @row-click="(row) => openChain(row as TraceMatrixRowView)">
          <el-table-column label="需求 \ 下游" min-width="220" fixed="left">
            <template #default="{ row }">
              <div class="trace-matrix__req">
                <span class="trace-matrix__code">{{ (row as TraceMatrixRowView).code }}</span>
                <span class="trace-matrix__title">{{ (row as TraceMatrixRowView).title }}</span>
              </div>
              <div class="trace-matrix__flags">
                <el-tag
                  :type="(row as TraceMatrixRowView).statusMeta.tagType"
                  size="small"
                  effect="light"
                >
                  {{ (row as TraceMatrixRowView).statusMeta.label }}
                </el-tag>
                <el-tooltip
                  :content="(row as TraceMatrixRowView).hint"
                  :disabled="!(row as TraceMatrixRowView).hint"
                  placement="top"
                >
                  <span
                    v-if="(row as TraceMatrixRowView).hint"
                    class="trace-matrix__badge"
                    :class="{
                      'trace-matrix__badge--danger': (row as TraceMatrixRowView).highlight.danger,
                      'trace-matrix__badge--warning': (row as TraceMatrixRowView).highlight.warning,
                    }"
                  >
                    {{ (row as TraceMatrixRowView).staleCount + (row as TraceMatrixRowView).conflictCount }}
                    待处理
                  </span>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-for="column in TRACE_MATRIX_COLUMNS"
            :key="column.key"
            :label="column.label"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              <button
                type="button"
                class="trace-matrix__cell"
                :class="{
                  'trace-matrix__cell--warning':
                    !(row as TraceMatrixRowView).highlight.danger &&
                    (row as TraceMatrixRowView).highlight.warning &&
                    (row as TraceMatrixRowView).edgeCounts[column.key] > 0,
                  'trace-matrix__cell--danger':
                    (row as TraceMatrixRowView).highlight.danger &&
                    (row as TraceMatrixRowView).edgeCounts[column.key] > 0,
                }"
                @click.stop="openCell(row as TraceMatrixRowView, column.type)"
              >
                {{ (row as TraceMatrixRowView).edgeCounts[column.key] }}
              </button>
            </template>
          </el-table-column>

          <el-table-column label="覆盖状态" width="120" align="center">
            <template #default="{ row }">
              <!-- AI 未启用时降级「—」（交互 04 §2.6） -->
              <el-tag
                v-if="aiAvailable && (row as TraceMatrixRowView).coverageMeta"
                :type="(row as TraceMatrixRowView).coverageMeta?.tagType"
                size="small"
                effect="light"
              >
                {{ (row as TraceMatrixRowView).coverageMeta?.label }}
              </el-tag>
              <span v-else class="trace-matrix__muted">—</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="trace-matrix__empty">
              <p>项目暂无需求，建立需求后即可维护追溯关系</p>
              <el-button
                v-if="canCreateRequirement"
                type="primary"
                @click="router.push('/workspace/projects/requirements')"
              >
                新建需求
              </el-button>
            </div>
          </template>
        </el-table>

        <footer v-if="!showSkeleton" class="trace-matrix__foot">
          <TraceLegend />
          <div class="trace-matrix__pager">
            <span class="trace-matrix__pager-total">共 {{ total }} 条 · 每页 {{ pageSize }} 条</span>
            <el-pagination
              layout="prev, pager, next, sizes"
              :current-page="pageNo"
              :page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              @current-change="changePage"
              @size-change="changePageSize"
            />
          </div>
        </footer>
      </el-card>
    </template>

    <!-- 缺口视图 -->
    <GapList
      v-else
      :gaps="gaps"
      :type-options="typeOptions"
      :gap-type="gapType"
      :total="gapTotal"
      :page-no="gapPageNo"
      :page-size="gapPageSize"
      :loading="gapLoading"
      :load-error="gapLoadError"
      :has-loaded="gapHasLoaded"
      @set-type="(type: TraceGapType) => setGapType(type)"
      @retry="retryGaps"
      @change-page="changeGapPage"
      @change-page-size="changeGapPageSize"
      @open-chain="openGapChain"
      @run-action="handleGapAction"
    />

    <!-- 缺口「发起生成」就地打开生成配置；输入状态变化时刷新缺口清单（交互 04 §2.4） -->
    <GenerationConfigDialog
      v-model="generationDialogVisible"
      :requirements="generationScope"
      :module-tree="generationModuleTree"
      @stale="retryGaps"
    />
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
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.trace-matrix__switch {
  align-self: center;
}

.list-error {
  margin-bottom: var(--space-md);
}

/* UI-SC-10：卡片内边距归零，留白由工具栏/分页条承担 */
.trace-matrix__card {
  --el-card-padding: 0;
}

/* UI-SC-10：筛选工具栏是固定控件，独立留白并与表格以分割线分隔 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.filter-bar__badge :deep(.el-badge__content) {
  background-color: var(--color-primary-500);
}

.filter-bar__count {
  margin-left: auto;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.list-skeleton {
  padding: 14px 20px;
}

.trace-matrix__req {
  display: flex;
  gap: 6px;
  align-items: baseline;
}

.trace-matrix__code {
  font-weight: 600;
  color: var(--color-neutral-800);
}

.trace-matrix__title {
  overflow: hidden;
  color: var(--color-neutral-700);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-matrix__flags {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
}

.trace-matrix__badge {
  font-size: 12px;
}

.trace-matrix__badge--warning {
  color: var(--color-warning);
}

.trace-matrix__badge--danger {
  color: var(--color-danger);
}

.trace-matrix__cell {
  min-width: 32px;
  padding: 2px 6px;
  font: inherit;
  color: var(--color-primary-500);
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.trace-matrix__cell:hover {
  background: var(--color-primary-50);
}

.trace-matrix__cell--warning {
  background: var(--color-warning-light);
}

/* conflict 行优先用危险底色，避免与 stale 混色 */
.trace-matrix__cell--danger {
  background: var(--color-danger-light);
}

.trace-matrix__muted {
  color: var(--color-neutral-500);
}

.trace-matrix__empty {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  padding: 32px 0;
  color: var(--color-neutral-500);
}

/* UI-SC-10：分页条以分割线与表格分隔，左侧图例、右侧计数与分页控件 */
.trace-matrix__foot {
  display: flex;
  gap: var(--space-md);
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}

.trace-matrix__pager {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  align-items: center;
  justify-content: flex-end;
}

.trace-matrix__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}
</style>
