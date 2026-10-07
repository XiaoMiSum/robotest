<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTraceStore } from '@/stores/trace'
import type { TraceEdgePatchPayload, TraceGapType, TraceImpactDisposition, TraceNodeType } from '@/types'
import { TRACE_MATRIX_COLUMNS } from '@/composables/project/trace/tracePresentation'
import { useTraceMatrix, type TraceMatrixRowView } from '@/composables/project/trace/useTraceMatrix'
import { useTraceChain } from '@/composables/project/trace/useTraceChain'
import { useTraceCoverage } from '@/composables/project/trace/useTraceCoverage'
import { useTraceGaps, type TraceGapView } from '@/composables/project/trace/useTraceGaps'
import { useTraceImpact, type TraceImpactItemView } from '@/composables/project/trace/useTraceImpact'
import TraceLegend from '@/components/project/trace/TraceLegend.vue'
import TraceChainDrawer from '@/components/project/trace/TraceChainDrawer.vue'
import GapList from '@/components/project/trace/GapList.vue'
import GenerationConfigDialog from '@/components/project/ai/GenerationConfigDialog.vue'

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
  load,
  retry,
  search,
  resetFilters,
  changePage,
  changePageSize,
  openChain,
} = useTraceMatrix()

const {
  visible: chainVisible,
  origin: chainOrigin,
  chain,
  loading: chainLoading,
  loadError: chainLoadError,
  busy: chainBusy,
  revision: chainRevision,
  canEdit: canEditTrace,
  retry: retryChain,
  confirmEdge,
  reattachEdge,
  detachEdge,
  restoreEdge,
  addEdge,
} = useTraceChain()

const {
  record: coverageRecord,
  loading: coverageLoading,
  loadError: coverageLoadError,
  saving: coverageSaving,
  revision: coverageRevision,
  busy: coverageBusy,
  taskRunning: coverageTaskRunning,
  taskProgress: coverageTaskProgress,
  taskFailed: coverageTaskFailed,
  taskError: coverageTaskError,
  openFor: openCoverage,
  retry: retryCoverage,
  close: closeCoverage,
  save: saveCoverage,
  analyze: analyzeCoverage,
  retryAnalyze: retryAnalyzeCoverage,
} = useTraceCoverage()

const {
  gaps,
  total: gapTotal,
  pageNo: gapPageNo,
  pageSize: gapPageSize,
  loading: gapLoading,
  loadError: gapLoadError,
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

const {
  items: impactItems,
  total: impactTotal,
  pageNo: impactPageNo,
  pageSize: impactPageSize,
  loading: impactLoading,
  loadError: impactLoadError,
  busy: impactBusy,
  taskRunning,
  taskProgress,
  taskFailed,
  taskError,
  refreshSignal,
  canEdit: canEditImpact,
  openFor: openImpact,
  retry: retryImpact,
  changePage: changeImpactPage,
  changePageSize: changeImpactPageSize,
  dispose: disposeImpact,
  analyze,
  retryAnalyze,
} = useTraceImpact()

const view = computed({
  get: () => traceStore.view,
  set: (next: 'matrix' | 'gaps') => traceStore.setView(next),
})

const canCreateRequirement = computed(() => authStore.hasPermission('requirement:create'))

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
  traceStore.openChain({
    type: 'requirement',
    id: row.requirementId,
    title: `${row.code} ${row.title}`,
    focusType: type,
  })
}

/** 起点为需求时联动加载覆盖结论与受影响项，其余起点只看链路 */
watch(
  () => traceStore.chainOrigin,
  (origin) => {
    if (origin?.type === 'requirement') {
      void openCoverage(origin.id)
      void openImpact(origin.id)
      return
    }
    closeCoverage()
  },
)

// ==================== 写操作后的矩阵刷新 ====================
watch([chainRevision, coverageRevision, refreshSignal], () => {
  void load()
})

// ==================== 抽屉动作 ====================
function handlePatch(event: { edgeId: string; payload: TraceEdgePatchPayload; successText: string }): void {
  const { edgeId, payload } = event
  if (payload.action === 'detach') {
    void detachEdge(edgeId, payload.reason ?? '')
    return
  }
  if (payload.action === 'reattach' && payload.targetId) {
    void reattachEdge(edgeId, { type: payload.targetType ?? 'mindmap_document', id: payload.targetId })
    return
  }
  if (payload.action === 'confirm') void confirmEdge(edgeId)
  if (payload.action === 'restore') void restoreEdge(edgeId)
}

function handleDispose(event: {
  item: TraceImpactItemView
  disposition: TraceImpactDisposition
  reason: string
}): void {
  void disposeImpact(event.item, event.disposition, event.reason)
}

function handleGapAction(gap: TraceGapView): void {
  runGapAction(gap)
}

onMounted(() => {
  const requirementId = route.query.requirementId
  if (typeof requirementId === 'string' && requirementId) {
    const title = typeof route.query.title === 'string' ? route.query.title : '追溯链路'
    traceStore.openChain({ type: 'requirement', id: requirementId, title })
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
      <el-radio-group v-model="view" class="trace-matrix__switch">
        <el-radio-button value="matrix">矩阵</el-radio-button>
        <el-radio-button value="gaps">缺口</el-radio-button>
      </el-radio-group>
    </header>

    <!-- 筛选：状态 / 覆盖 / 关键词，角标显示条件数（交互 04 §2.1） -->
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
    </div>

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
      <el-skeleton v-if="!hasLoaded && !loadError && loading" :rows="6" animated class="list-skeleton" />

      <el-card v-else shadow="never" class="trace-matrix__card">
        <el-table v-loading="loading" :data="rows" row-key="requirementId" @row-click="openChain">
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

        <footer class="trace-matrix__foot">
          <TraceLegend />
          <el-pagination
            v-if="total > 0"
            :current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50]"
            @current-change="changePage"
            @size-change="changePageSize"
          />
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
      @set-type="(type: TraceGapType) => setGapType(type)"
      @retry="retryGaps"
      @change-page="changeGapPage"
      @change-page-size="changeGapPageSize"
      @open-chain="openGapChain"
      @run-action="handleGapAction"
    />

    <TraceChainDrawer
      :origin="chainOrigin"
      :chain="chain"
      :loading="chainLoading"
      :load-error="chainLoadError"
      :busy="chainBusy"
      :can-edit="canEditTrace"
      :ai-available="aiAvailable"
      :coverage="{
        record: coverageRecord,
        loading: coverageLoading,
        loadError: coverageLoadError,
        saving: coverageSaving,
        canEdit: canEditTrace,
        aiAvailable,
        busy: coverageBusy,
        taskRunning: coverageTaskRunning,
        taskProgress: coverageTaskProgress,
        taskFailed: coverageTaskFailed,
        taskError: coverageTaskError,
      }"
      :impact="{
        items: impactItems,
        total: impactTotal,
        pageNo: impactPageNo,
        pageSize: impactPageSize,
        loading: impactLoading,
        loadError: impactLoadError,
        busy: impactBusy,
        aiAvailable: aiAvailable,
        canEdit: canEditImpact,
        taskRunning,
        taskProgress,
        taskFailed,
        taskError,
      }"
      @retry="retryChain"
      @patch="handlePatch"
      @create-edge="(payload) => void addEdge(payload)"
      @close="chainVisible = false"
      @open-chain="(origin) => traceStore.openChain(origin)"
      @retry-coverage="retryCoverage"
      @save-coverage="(payload) => void saveCoverage(payload)"
      @analyze-coverage="() => void analyzeCoverage()"
      @retry-analyze-coverage="() => void retryAnalyzeCoverage()"
      @dispose-impact="handleDispose"
      @analyze-impact="() => void analyze()"
      @retry-analyze-impact="() => void retryAnalyze()"
      @change-impact-page="changeImpactPage"
      @change-impact-page-size="changeImpactPageSize"
      @retry-impact="retryImpact"
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
.trace-matrix__switch {
  align-self: center;
}

.trace-matrix__card {
  margin-top: 12px;
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

.trace-matrix__foot {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
</style>
