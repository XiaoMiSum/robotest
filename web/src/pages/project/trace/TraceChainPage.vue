<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchAiStatus } from '@/services/ai'
import { useTraceStore } from '@/stores/trace'
import { useTraceChain, type TraceChainSource } from '@/composables/project/trace/useTraceChain'
import { useTraceCoverage } from '@/composables/project/trace/useTraceCoverage'
import { useTraceImpact } from '@/composables/project/trace/useTraceImpact'
import {
  isTraceNodeType,
  traceNodeTypeMeta,
} from '@/composables/project/trace/tracePresentation'
import type {
  TraceEdgeCreatePayload,
  TraceEdgePatchPayload,
  TraceImpactDisposition,
  TraceNodeType,
} from '@/types'
import type { TraceImpactItemView } from '@/composables/project/trace/useTraceImpact'
import TraceChainTree from '@/components/project/trace/TraceChainTree.vue'
import CoveragePanel from '@/components/project/trace/CoveragePanel.vue'
import ImpactDispositionPanel from '@/components/project/trace/ImpactDispositionPanel.vue'

const route = useRoute()
const router = useRouter()
const traceStore = useTraceStore()

/** URL 参数即起点：类型白名单校验，非法参数按空链路处理 */
const source = computed<TraceChainSource | null>(() => {
  const { nodeType, nodeId } = route.params
  if (!isTraceNodeType(nodeType) || typeof nodeId !== 'string' || !nodeId) return null
  const focusType = route.query.focusType
  return {
    type: nodeType,
    id: nodeId,
    focusType: isTraceNodeType(focusType) ? focusType : undefined,
  }
})

const isRequirementOrigin = computed(() => source.value?.type === 'requirement')

const {
  chain,
  loading,
  loadError,
  busy,
  revision: chainRevision,
  canEdit,
  retry: retryChain,
  confirmEdge,
  reattachEdge,
  detachEdge,
  restoreEdge,
  addEdge,
} = useTraceChain(() => source.value)

const {
  record: coverageRecord,
  loading: coverageLoading,
  loadError: coverageLoadError,
  saving: coverageSaving,
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
  items: impactItems,
  total: impactTotal,
  pageNo: impactPageNo,
  pageSize: impactPageSize,
  loading: impactLoading,
  loadError: impactLoadError,
  busy: impactBusy,
  taskRunning: impactTaskRunning,
  taskProgress: impactTaskProgress,
  taskFailed: impactTaskFailed,
  taskError: impactTaskError,
  canEdit: canEditImpact,
  openFor: openImpact,
  retry: retryImpact,
  changePage: changeImpactPage,
  changePageSize: changeImpactPageSize,
  dispose: disposeImpact,
  analyze: analyzeImpact,
  retryAnalyze: retryAnalyzeImpact,
} = useTraceImpact()

const aiAvailable = ref(false)

async function loadAiStatus(): Promise<void> {
  try {
    aiAvailable.value = (await fetchAiStatus()).available
  } catch {
    aiAvailable.value = false
  }
}

const rootTitle = computed(() => chain.value?.root.title ?? '链路明细')
const rootTypeMeta = computed(() => (source.value ? traceNodeTypeMeta(source.value.type) : null))

// 起点变化（含首次进入）时联动加载覆盖与影响；非需求起点只看链路
watch(
  source,
  (next) => {
    if (next?.type === 'requirement') {
      void openCoverage(next.id)
      void openImpact(next.id)
      return
    }
    closeCoverage()
  },
  { immediate: true },
)

// 边修正会改变下游结论，成功后刷新覆盖与影响，避免链路与结论不一致
watch(chainRevision, (value) => {
  if (!value || !isRequirementOrigin.value) return
  retryCoverage()
  retryImpact()
})

function backToMatrix(): void {
  traceStore.setView('matrix')
  void router.push({ path: '/workspace/projects/requirements', query: { tab: 'trace' } })
}

function openChainNode(node: { type: TraceNodeType; id: string }): void {
  void router.push({
    name: 'TraceChain',
    params: { nodeType: node.type, nodeId: node.id },
  })
}

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

onMounted(() => {
  void loadAiStatus()
})
</script>

<template>
  <main class="chain-detail">
    <!-- UI-BC-01：两级面包屑，回退入口唯一可点击（同需求详情页） -->
    <nav class="chain-detail__breadcrumb" aria-label="面包屑">
      <router-link :to="{ path: '/workspace/projects/requirements', query: { tab: 'trace' } }">
        追溯矩阵
      </router-link>
      <el-icon :size="13"><ArrowRight /></el-icon>
      <span>{{ rootTitle }}</span>
    </nav>

    <el-empty v-if="!source" description="链路起点无效，请从追溯矩阵进入">
      <el-button type="primary" @click="backToMatrix">返回追溯矩阵</el-button>
    </el-empty>

    <template v-else>
      <header class="chain-detail__head">
        <h1 class="chain-detail__title">{{ rootTitle }}</h1>
        <el-tag v-if="rootTypeMeta" size="small" effect="light">{{ rootTypeMeta.label }}</el-tag>
      </header>
      <p class="chain-detail__desc">
        {{ chain?.hasMore ? '链路超过展示上限，仅展示前段节点' : '按关系层级展开，点击下游节点查看该节点的链路' }}
      </p>

      <el-card shadow="never" class="chain-detail__card">
        <TraceChainTree
          :origin="source"
          :chain="chain"
          :loading="loading"
          :load-error="loadError"
          :busy="busy"
          :can-edit="canEdit"
          @retry="retryChain"
          @patch="handlePatch"
          @create-edge="(payload: TraceEdgeCreatePayload) => void addEdge(payload)"
          @open-chain="openChainNode"
        />
      </el-card>

      <template v-if="isRequirementOrigin">
        <el-card shadow="never" class="chain-detail__card">
          <CoveragePanel
            :record="coverageRecord"
            :loading="coverageLoading"
            :load-error="coverageLoadError"
            :saving="coverageSaving"
            :can-edit="canEdit"
            :ai-available="aiAvailable"
            :busy="coverageBusy"
            :task-running="coverageTaskRunning"
            :task-progress="coverageTaskProgress"
            :task-failed="coverageTaskFailed"
            :task-error="coverageTaskError"
            @retry="retryCoverage"
            @save="(payload) => void saveCoverage(payload)"
            @analyze="() => void analyzeCoverage()"
            @retry-analyze="() => void retryAnalyzeCoverage()"
          />
        </el-card>

        <el-card v-if="aiAvailable" shadow="never" class="chain-detail__card">
          <ImpactDispositionPanel
            :items="impactItems"
            :total="impactTotal"
            :page-no="impactPageNo"
            :page-size="impactPageSize"
            :loading="impactLoading"
            :load-error="impactLoadError"
            :busy="impactBusy"
            :ai-available="aiAvailable"
            :can-edit="canEditImpact"
            :task-running="impactTaskRunning"
            :task-progress="impactTaskProgress"
            :task-failed="impactTaskFailed"
            :task-error="impactTaskError"
            @dispose="handleDispose"
            @analyze="() => void analyzeImpact()"
            @retry-analyze="() => void retryAnalyzeImpact()"
            @change-page="changeImpactPage"
            @change-page-size="changeImpactPageSize"
            @retry="retryImpact"
          />
        </el-card>
      </template>
    </template>
  </main>
</template>

<style scoped lang="scss">
// UI-BC-02：面包屑容器排版基准（同需求详情页）
.chain-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  margin-bottom: var(--space-md);

  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;
    flex-shrink: 0;
  }

  a:hover {
    color: var(--color-primary-600);
  }

  .el-icon {
    color: var(--color-neutral-400);
    flex-shrink: 0;
  }

  > :last-child {
    color: var(--color-neutral-900);
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.chain-detail__head {
  display: flex;
  gap: var(--space-sm);
  align-items: baseline;
}

.chain-detail__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.chain-detail__desc {
  margin: var(--space-xs) 0 var(--block-gap);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.chain-detail__card {
  margin-bottom: var(--block-gap);
}
</style>
