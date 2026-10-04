<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import type {
  RequirementCoverageStatus,
  TraceChain,
  TraceChainEdge,
  TraceChainNode,
  TraceEdgeAction,
  TraceEdgeCreatePayload,
  TraceEdgePatchPayload,
  TraceImpactDisposition,
  TraceNodeType,
} from '@/types'
import type { TraceCoverage } from '@/types'
import type { TraceImpactItemView } from '@/composables/project/trace/useTraceImpact'
import type { TraceNodeOption } from '@/composables/project/trace/useTraceNodePicker'
import {
  traceActionAvailability,
  TRACE_ACTION_LABEL,
  traceEdgeStatusMeta,
  traceNodeTypeMeta,
} from '@/composables/project/trace/tracePresentation'
import { useTraceNodePicker } from '@/composables/project/trace/useTraceNodePicker'
import TraceLegend from '@/components/project/trace/TraceLegend.vue'
import TraceEdgeCreateDialog from '@/components/project/trace/TraceEdgeCreateDialog.vue'
import CoveragePanel from '@/components/project/trace/CoveragePanel.vue'
import ImpactDispositionPanel from '@/components/project/trace/ImpactDispositionPanel.vue'

/**
 * 链路视图抽屉（交互 04 §2.2）：节点树按层展示、边状态徽标与修正动作；
 * 起点为需求时附带覆盖修正面板与影响处置面板，形成需求维度的完整下钻。
 */
const props = defineProps<{
  origin: { type: TraceNodeType; id: string; title: string; focusType?: TraceNodeType } | null
  chain: TraceChain | null
  loading: boolean
  loadError: string
  busy: boolean
  canEdit: boolean
  aiAvailable: boolean
  coverage: {
    record: TraceCoverage | null
    loading: boolean
    loadError: string
    saving: boolean
    canEdit: boolean
  }
  impact: {
    items: TraceImpactItemView[]
    total: number
    pageNo: number
    pageSize: number
    loading: boolean
    loadError: string
    busy: boolean
    aiAvailable: boolean
    canEdit: boolean
    taskRunning: boolean
    taskProgress: number | null
    taskFailed: boolean
    taskError: string
  }
}>()

const emit = defineEmits<{
  retry: []
  patch: [payload: { edgeId: string; payload: TraceEdgePatchPayload; successText: string }]
  createEdge: [payload: TraceEdgeCreatePayload]
  close: []
  openChain: [origin: { type: TraceNodeType; id: string; title: string }]
  retryCoverage: []
  saveCoverage: [payload: { coverageStatus: Exclude<RequirementCoverageStatus, 'pending'>; note?: string }]
  disposeImpact: [payload: { item: TraceImpactItemView; disposition: TraceImpactDisposition; reason: string }]
  analyzeImpact: []
  retryAnalyzeImpact: []
  changeImpactPage: [pageNo: number]
  changeImpactPageSize: [pageSize: number]
  retryImpact: []
}>()

const visible = computed({
  get: () => props.origin !== null,
  set: (open: boolean) => {
    if (!open) emit('close')
  },
})

const isRequirementOrigin = computed(() => props.origin?.type === 'requirement')

/** 节点入边：树中除根节点外每条连线对应一条边，按 targetId 索引 */
const edgeByTarget = computed(() => {
  const map = new Map<string, TraceChainEdge>()
  for (const edge of props.chain?.edges ?? []) map.set(edge.targetId, edge)
  return map
})

interface TraceChainRow {
  node: TraceChainNode
  edge: TraceChainEdge | null
  meta: ReturnType<typeof traceNodeTypeMeta>
  statusMeta: ReturnType<typeof traceEdgeStatusMeta> | null
  availability: ReturnType<typeof traceActionAvailability> | null
  focused: boolean
}

const rows = computed<TraceChainRow[]>(() => {
  const nodes = props.chain?.nodes ?? []
  const focusType = props.origin?.focusType
  return nodes.map((node) => {
    const edge = edgeByTarget.value.get(node.id) ?? null
    const statusMeta = edge ? traceEdgeStatusMeta(edge.status) : null
    return {
      node,
      edge,
      meta: traceNodeTypeMeta(node.type),
      statusMeta,
      availability: edge ? traceActionAvailability(edge.status) : null,
      focused: Boolean(focusType) && node.type === focusType && edge !== null,
    }
  })
})

const createVisible = ref(false)

// ==================== 改挂：重选目标 ====================
const reattachVisible = ref(false)
const reattachEdgeId = ref('')
const reattachEdgeType = ref<'derivation' | 'case_snapshot'>('derivation')
const reattachTargetType = ref<TraceNodeType>('mindmap_document')
const reattachTargetId = ref('')
const reattachPicker = useTraceNodePicker()

const REATTACH_TARGETS: Record<'derivation' | 'case_snapshot', TraceNodeType[]> = {
  derivation: ['module', 'mindmap_document', 'test_case'],
  case_snapshot: ['test_review', 'test_plan'],
}

const reattachOptions = computed(() =>
  reattachTargetType.value === 'test_case'
    ? (caseOptions.value ?? [])
    : reattachPicker.options.value,
)

const caseOptions = computed<TraceNodeOption[]>(() =>
  (props.chain?.nodes ?? [])
    .filter((node) => node.type === 'test_case')
    .map((node) => ({ id: node.id, label: node.title })),
)

function openReattach(edge: TraceChainEdge): void {
  reattachEdgeId.value = edge.edgeId
  reattachEdgeType.value = edge.edgeType
  reattachTargetType.value = REATTACH_TARGETS[edge.edgeType][0]
  reattachTargetId.value = ''
  reattachVisible.value = true
}

watch(reattachVisible, (open) => {
  if (!open) return
  reattachTargetId.value = ''
  void reattachPicker.load(reattachTargetType.value)
})

watch(
  () => reattachTargetType.value,
  (type) => {
    reattachTargetId.value = ''
    void reattachPicker.load(type)
  },
)

function confirmReattach(): void {
  if (!reattachTargetId.value) return
  const payload: TraceEdgePatchPayload = {
    action: 'reattach',
    targetType: reattachTargetType.value,
    targetId: reattachTargetId.value,
  }
  emit('patch', { edgeId: reattachEdgeId.value, payload, successText: '边已改挂' })
  reattachVisible.value = false
}

// ==================== 修正动作 ====================
function runAction(row: TraceChainRow, action: TraceEdgeAction): void {
  if (!row.edge) return
  if (action === 'detach') {
    void detach(row.edge)
    return
  }
  emit('patch', {
    edgeId: row.edge.edgeId,
    payload: { action },
    successText: `边已${TRACE_ACTION_LABEL[action]}`,
  })
}

async function detach(edge: TraceChainEdge): Promise<void> {
  let reason: string
  try {
    const result = await ElMessageBox.prompt('断开理由将随审计留痕', '断开追溯边', {
      inputType: 'textarea',
      inputPlaceholder: '必填，说明为何断开该关联',
      inputValidator: (value: string) => (value.trim() ? true : '断开必须填写理由'),
      confirmButtonText: '断开',
      cancelButtonText: '取消',
      type: 'warning',
    })
    reason = result.value.trim()
  } catch {
    return
  }
  emit('patch', {
    edgeId: edge.edgeId,
    payload: { action: 'detach', reason },
    successText: '边已断开',
  })
}

function submitCreate(payload: TraceEdgeCreatePayload): void {
  emit('createEdge', payload)
  createVisible.value = false
}
</script>

<template>
  <el-drawer v-model="visible" size="min(760px, 92vw)" :with-header="false" class="trace-chain-drawer">
    <header class="trace-chain__head">
      <div>
        <h2 class="trace-chain__title">
          链路视图：{{ origin?.title ?? '' }}
        </h2>
        <p class="trace-chain__subtitle">
          {{ chain?.hasMore ? '链路超过展示上限，仅展示前段节点' : '按关系层级展开，点击下游节点查看详情' }}
        </p>
      </div>
      <el-button text @click="visible = false">关闭</el-button>
    </header>

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="trace-chain__error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="emit('retry')">重试</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="trace-chain__tree">
      <div
        v-for="row in rows"
        :key="row.node.id"
        class="trace-chain__row"
        :class="{ 'trace-chain__row--focus': row.focused }"
        :style="{ paddingLeft: `${row.node.level * 24}px` }"
      >
        <el-tooltip :content="row.meta.label" placement="top">
          <el-icon class="trace-chain__icon"><component :is="row.meta.icon" /></el-icon>
        </el-tooltip>
        <button type="button" class="trace-chain__node" @click="emit('openChain', { type: row.node.type, id: row.node.id, title: row.node.title })">
          {{ row.node.title }}
        </button>

        <template v-if="row.edge">
          <el-tag
            :type="row.statusMeta?.tagType ?? 'info'"
            size="small"
            effect="light"
            class="trace-chain__status"
          >
            {{ row.statusMeta?.symbol }} {{ row.statusMeta?.label }}
          </el-tag>
          <span v-if="row.edge.versionMatched === false" class="trace-chain__stale">
            目标版本 {{ row.edge.targetVersion }}
          </span>
        </template>

        <div v-if="canEdit && row.edge && row.availability" class="trace-chain__actions">
          <el-button
            v-if="row.availability.confirm"
            size="small"
            text
            type="primary"
            :loading="busy"
            @click="runAction(row, 'confirm')"
          >
            确认
          </el-button>
          <el-button
            v-if="row.availability.reattach"
            size="small"
            text
            type="primary"
            :loading="busy"
            @click="openReattach(row.edge)"
          >
            改挂
          </el-button>
          <el-button
            v-if="row.availability.detach"
            size="small"
            text
            type="danger"
            :loading="busy"
            @click="runAction(row, 'detach')"
          >
            断开
          </el-button>
          <el-button
            v-if="row.availability.restore"
            size="small"
            text
            type="warning"
            :loading="busy"
            @click="runAction(row, 'restore')"
          >
            恢复
          </el-button>
        </div>
      </div>

      <div v-if="!loading && !loadError && rows.length === 0" class="trace-chain__empty">
        <p>该起点暂无下游关系</p>
        <!-- 生成配置随批次二开放（WP-3.2 方案裁决 ②/④）：入口置灰并以悬浮提示说明 -->
        <el-tooltip content="生成配置随批次二开放" placement="bottom">
          <span>
            <el-button size="small" disabled>发起 AI 生成</el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <footer class="trace-chain__foot">
      <TraceLegend />
      <el-button v-if="canEdit" size="small" @click="createVisible = true">+ 人工建边</el-button>
    </footer>

    <!-- 起点为需求时的覆盖修正与影响处置（交互 04 §2.3 / §2.5） -->
    <template v-if="isRequirementOrigin">
      <el-divider content-position="left">覆盖判定</el-divider>
      <CoveragePanel
        :record="coverage.record"
        :loading="coverage.loading"
        :load-error="coverage.loadError"
        :saving="coverage.saving"
        :can-edit="coverage.canEdit"
        @retry="emit('retryCoverage')"
        @save="emit('saveCoverage', $event)"
      />

      <template v-if="aiAvailable">
        <el-divider content-position="left">受影响项</el-divider>
        <ImpactDispositionPanel
          :items="impact.items"
          :total="impact.total"
          :page-no="impact.pageNo"
          :page-size="impact.pageSize"
          :loading="impact.loading"
          :load-error="impact.loadError"
          :busy="impact.busy"
          :ai-available="impact.aiAvailable"
          :can-edit="impact.canEdit"
          :task-running="impact.taskRunning"
          :task-progress="impact.taskProgress"
          :task-failed="impact.taskFailed"
          :task-error="impact.taskError"
          @dispose="emit('disposeImpact', $event)"
          @analyze="emit('analyzeImpact')"
          @retry-analyze="emit('retryAnalyzeImpact')"
          @change-page="emit('changeImpactPage', $event)"
          @change-page-size="emit('changeImpactPageSize', $event)"
          @retry="emit('retryImpact')"
        />
      </template>
    </template>

    <TraceEdgeCreateDialog v-model="createVisible" :case-options="caseOptions" @submit="submitCreate" />

    <el-dialog v-model="reattachVisible" title="改挂到新目标" width="520px" append-to-body>
      <div class="trace-chain__reattach">
        <el-select v-model="reattachTargetType" style="width: 150px">
          <el-option
            v-for="type in REATTACH_TARGETS[reattachEdgeType]"
            :key="type"
            :value="type"
            :label="traceNodeTypeMeta(type).label"
          />
        </el-select>
        <el-select
          v-model="reattachTargetId"
          filterable
          remote
          clearable
          :loading="reattachPicker.loading.value"
          :remote-method="(keyword: string) => reattachPicker.load(reattachTargetType, keyword)"
          placeholder="选择新目标"
          style="flex: 1"
        >
          <el-option
            v-for="option in reattachOptions"
            :key="option.id"
            :value="option.id"
            :label="option.label"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="reattachVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!reattachTargetId" @click="confirmReattach">
          确认改挂
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped lang="scss">
.trace-chain__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.trace-chain__title {
  margin: 0;
  font-size: 16px;
  color: var(--color-neutral-900);
}

.trace-chain__subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-neutral-500);
}

.trace-chain__error {
  margin-bottom: 12px;
}

.trace-chain__tree {
  min-height: 160px;
  max-height: 44vh;
  overflow: auto;
  padding: 4px 0;
}

.trace-chain__row {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 34px;
  padding-right: 8px;
  border-radius: 4px;
}

.trace-chain__row--focus {
  background: var(--color-primary-50);
}

.trace-chain__icon {
  color: var(--color-neutral-500);
}

.trace-chain__node {
  padding: 0;
  font: inherit;
  color: var(--color-neutral-800);
  text-align: left;
  background: none;
  border: none;
  cursor: pointer;
}

.trace-chain__node:hover {
  color: var(--color-primary-500);
}

.trace-chain__status {
  flex-shrink: 0;
}

.trace-chain__stale {
  font-size: 12px;
  color: var(--color-warning);
}

.trace-chain__actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.trace-chain__empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  margin: 24px 0;
  color: var(--color-neutral-500);
  text-align: center;
}

.trace-chain__empty p {
  margin: 0;
}

.trace-chain__foot {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.trace-chain__reattach {
  display: flex;
  gap: 8px;
}
</style>
