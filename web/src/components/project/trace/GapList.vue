<script setup lang="ts">
import { computed } from 'vue'
import type { TraceGapType } from '@/types'
import type { TraceGapView } from '@/composables/project/trace/useTraceGaps'

/**
 * 缺口清单（交互 04 §2.4）：按缺口类型分组切换，行内给出引导动作；
 * 引导动作的可执行性由 actionMeta 承载（生成动作就地打开生成配置对话框）。
 */
const props = defineProps<{
  gaps: TraceGapView[]
  typeOptions: { value: TraceGapType; label: string; desc: string }[]
  gapType: TraceGapType
  total: number
  pageNo: number
  pageSize: number
  loading: boolean
  loadError: string
  hasLoaded: boolean
}>()

const emit = defineEmits<{
  setType: [type: TraceGapType]
  retry: []
  changePage: [pageNo: number]
  changePageSize: [pageSize: number]
  openChain: [gap: TraceGapView]
  runAction: [gap: TraceGapView]
}>()

/** 首屏骨架：仅首屏加载中显示，刷新走整卡遮罩（交互 04 §2.6） */
const showSkeleton = computed(() => !props.hasLoaded && !props.loadError && props.loading)
</script>

<template>
  <section class="gap-list" aria-label="缺口清单">
    <!-- 错误提示条保持卡片外，重试与卡片 loading 分离（交互 04 §2.6） -->
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

    <!-- UI-SC-10：卡片内边距归零使表格贴边，类型切换作工具栏，刷新走整卡遮罩（交互 04 §2.4） -->
    <el-card v-loading="loading && hasLoaded" shadow="never" class="gap-list__card">
      <el-tabs
        :model-value="gapType"
        class="gap-list__tabs"
        @update:model-value="emit('setType', $event as TraceGapType)"
      >
        <el-tab-pane v-for="option in typeOptions" :key="option.value" :name="option.value">
          <template #label>
            <el-tooltip :content="option.desc" placement="top">
              <span>{{ option.label }}</span>
            </el-tooltip>
          </template>
        </el-tab-pane>
      </el-tabs>

      <el-skeleton v-if="showSkeleton" :rows="6" animated class="gap-list__skeleton" />

      <el-table
        v-else
        :data="gaps"
        row-key="targetId"
        @row-click="emit('openChain', $event as TraceGapView)"
      >
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
            <el-button
              size="small"
              text
              type="primary"
              @click.stop="emit('openChain', row as TraceGapView)"
            >
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

      <footer v-if="!showSkeleton" class="gap-list__pager">
        <span class="gap-list__pager-total">共 {{ total }} 条 · 每页 {{ pageSize }} 条</span>
        <el-pagination
          layout="prev, pager, next, sizes"
          :current-page="pageNo"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @current-change="emit('changePage', $event)"
          @size-change="emit('changePageSize', $event)"
        />
      </footer>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
.gap-list__error {
  margin-bottom: var(--space-md);
}

/* UI-SC-10：卡片内边距归零，留白由工具栏/分页条承担 */
.gap-list__card {
  --el-card-padding: 0;
}

/* UI-SC-10：缺口类型切换作卡片工具栏，独立留白并与表格以分割线分隔 */
.gap-list__tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 0;
    padding: 14px 20px 0;
    border-bottom: 1px solid var(--color-neutral-100);
  }

  /* EP 默认整条下划线会与自绘的头部分隔线叠成双线，隐藏之 */
  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }
}

.gap-list__skeleton {
  padding: 14px 20px;
}

.gap-list__title {
  color: var(--color-neutral-800);
}

.gap-list__muted,
.gap-list__empty {
  color: var(--color-neutral-500);
}

/* UI-SC-10：分页条以分割线与表格分隔，左端计数、右端分页控件 */
.gap-list__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}

.gap-list__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}
</style>
