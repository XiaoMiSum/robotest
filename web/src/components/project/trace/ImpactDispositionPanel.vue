<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { TraceImpactDisposition } from '@/types'
import type { TraceImpactItemView } from '@/composables/project/trace/useTraceImpact'
import {
  TRACE_DISPOSITIONS,
  traceDispositionMeta,
  traceNodeTypeMeta,
} from '@/composables/project/trace/tracePresentation'

/**
 * 影响处置面板（交互 04 §2.5 / 3.2）：三类处置逐项提交，理由按标记必填；
 * 任务进行中展示进度，失败给出原因与重试入口。
 */
defineProps<{
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
}>()

const emit = defineEmits<{
  dispose: [payload: { item: TraceImpactItemView; disposition: TraceImpactDisposition; reason: string }]
  analyze: []
  retryAnalyze: []
  changePage: [pageNo: number]
  changePageSize: [pageSize: number]
  retry: []
}>()

/** 行内处置草稿：key 为 edgeId，避免污染行数据 */
const drafts = reactive<Record<string, { disposition: TraceImpactDisposition; reason: string }>>({})
const invalid = ref('')

function draftOf(item: TraceImpactItemView) {
  if (!drafts[item.edgeId]) {
    drafts[item.edgeId] = { disposition: 'no_impact', reason: '' }
  }
  return drafts[item.edgeId]
}

function submit(item: TraceImpactItemView): void {
  const draft = draftOf(item)
  const meta = traceDispositionMeta(draft.disposition)
  if (meta.requireReason && draft.reason.trim() === '') {
    invalid.value = item.edgeId
    return
  }
  invalid.value = ''
  emit('dispose', { item, disposition: draft.disposition, reason: draft.reason })
}

function reasonLabel(disposition: TraceImpactDisposition): string {
  return traceDispositionMeta(disposition).requireReason ? '理由（必填）' : '理由'
}

function dispositionOptions() {
  return TRACE_DISPOSITIONS.map((value) => ({ value, label: traceDispositionMeta(value).label }))
}
</script>

<template>
  <section class="impact-panel" aria-label="受影响项处置">
    <header class="impact-panel__head">
      <el-button
        v-if="aiAvailable && canEdit && !taskRunning"
        type="primary"
        size="small"
        :loading="busy"
        @click="emit('analyze')"
      >
        发起影响分析
      </el-button>
      <el-button
        v-if="aiAvailable && canEdit && taskFailed"
        size="small"
        :loading="busy"
        @click="emit('retryAnalyze')"
      >
        重试
      </el-button>
      <el-progress
        v-if="taskRunning"
        :percentage="taskProgress ?? 0"
        :stroke-width="8"
        class="impact-panel__progress"
      />
    </header>

    <el-alert
      v-if="taskFailed && taskError"
      type="warning"
      :title="taskError"
      show-icon
      :closable="false"
    />

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="emit('retry')">重试</el-button>
      </template>
    </el-alert>

    <el-table v-loading="loading" :data="items" row-key="edgeId">
      <el-table-column label="受影响项" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="impact-panel__title">{{ (row as TraceImpactItemView).target.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" type="info">
            {{ traceNodeTypeMeta((row as TraceImpactItemView).target.type).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="影响路径" width="120">
        <template #default="{ row }">
          <span class="impact-panel__muted">
            {{ (row as TraceImpactItemView).impactType === 'case_snapshot' ? '快照引用' : '派生' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="处置" min-width="360">
        <template #default="{ row }">
          <template v-if="(row as TraceImpactItemView).disposition !== 'pending'">
            <el-tag
              :type="(row as TraceImpactItemView).dispositionMeta.tagType"
              size="small"
              effect="light"
            >
              {{ (row as TraceImpactItemView).dispositionMeta.label }}
            </el-tag>
            <span class="impact-panel__reason">
              {{ (row as TraceImpactItemView).reason || '—' }}
            </span>
          </template>
          <div v-else-if="canEdit" class="impact-panel__action">
            <el-select
              :model-value="draftOf(row as TraceImpactItemView).disposition"
              style="width: 130px"
              size="small"
              @update:model-value="
                (value: TraceImpactDisposition) => (draftOf(row as TraceImpactItemView).disposition = value)
              "
            >
              <el-option
                v-for="option in dispositionOptions()"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
            <el-input
              :model-value="draftOf(row as TraceImpactItemView).reason"
              size="small"
              maxlength="500"
              clearable
              :placeholder="reasonLabel(draftOf(row as TraceImpactItemView).disposition)"
              :status="invalid === (row as TraceImpactItemView).edgeId ? 'error' : ''"
              style="width: 220px"
              @update:model-value="(value: string) => (draftOf(row as TraceImpactItemView).reason = value)"
            />
            <el-button
              size="small"
              type="primary"
              plain
              :loading="busy"
              @click="submit(row as TraceImpactItemView)"
            >
              提交
            </el-button>
          </div>
          <span v-else class="impact-panel__muted">待处置</span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="impact-panel__empty">
          暂无受影响项，发起影响分析后沿追溯边列出下游
        </span>
      </template>
    </el-table>

    <el-pagination
      v-if="total > 0"
      :current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      class="impact-panel__pager"
      @current-change="emit('changePage', $event)"
      @size-change="emit('changePageSize', $event)"
    />
  </section>
</template>

<style scoped lang="scss">
.impact-panel__head {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.impact-panel__progress {
  flex: 1;
  max-width: 320px;
}

.impact-panel__title {
  color: var(--color-neutral-800);
}

.impact-panel__muted,
.impact-panel__empty,
.impact-panel__reason {
  color: var(--color-neutral-500);
  font-size: 13px;
}

.impact-panel__action {
  display: flex;
  gap: 8px;
  align-items: center;
}

.impact-panel__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
