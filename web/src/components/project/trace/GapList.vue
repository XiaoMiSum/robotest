<script setup lang="ts">
import type { TraceGapType } from '@/types'
import type { TraceGapView } from '@/composables/project/trace/useTraceGaps'

/**
 * 缺口清单（交互 04 §2.4）：按缺口类型分组切换，行内给出引导动作；
 * 引导动作的可执行性由 actionMeta 承载（生成动作就地打开生成配置对话框）。
 */
defineProps<{
  gaps: TraceGapView[]
  typeOptions: { value: TraceGapType; label: string; desc: string }[]
  gapType: TraceGapType
  total: number
  pageNo: number
  pageSize: number
  loading: boolean
  loadError: string
}>()

const emit = defineEmits<{
  setType: [type: TraceGapType]
  retry: []
  changePage: [pageNo: number]
  changePageSize: [pageSize: number]
  openChain: [gap: TraceGapView]
  runAction: [gap: TraceGapView]
}>()
</script>

<template>
  <section class="gap-list" aria-label="缺口清单">
    <el-tabs :model-value="gapType" @update:model-value="emit('setType', $event as TraceGapType)">
      <el-tab-pane v-for="option in typeOptions" :key="option.value" :name="option.value">
        <template #label>
          <el-tooltip :content="option.desc" placement="top">
            <span>{{ option.label }}</span>
          </el-tooltip>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="gap-list__error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="emit('retry')">重试</el-button>
      </template>
    </el-alert>

    <el-table v-loading="loading" :data="gaps" row-key="targetId" @row-click="emit('openChain', $event as TraceGapView)">
      <el-table-column label="条目" min-width="320" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="gap-list__title">{{ (row as TraceGapView).title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="覆盖状态" width="120">
        <template #default="{ row }">
          <span v-if="(row as TraceGapView).coverageStatus" class="gap-list__muted">
            {{ (row as TraceGapView).coverageStatus }}
          </span>
          <span v-else class="gap-list__muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click.stop="emit('openChain', row as TraceGapView)">
            查看链路
          </el-button>
          <el-tooltip
            :content="(row as TraceGapView).actionMeta.disabledHint"
            :disabled="!(row as TraceGapView).actionMeta.disabled"
            placement="top"
          >
            <span>
              <el-button
                size="small"
                :disabled="(row as TraceGapView).actionMeta.disabled"
                @click.stop="emit('runAction', row as TraceGapView)"
              >
                {{ (row as TraceGapView).actionMeta.label }}
              </el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <template #empty>
        <span class="gap-list__empty">当前无追溯缺口</span>
      </template>
    </el-table>

    <el-pagination
      v-if="total > 0"
      :current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      class="gap-list__pager"
      @current-change="emit('changePage', $event)"
      @size-change="emit('changePageSize', $event)"
    />
  </section>
</template>

<style scoped lang="scss">
.gap-list__title {
  color: var(--color-neutral-800);
}

.gap-list__muted,
.gap-list__empty {
  color: var(--color-neutral-500);
}

.gap-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
