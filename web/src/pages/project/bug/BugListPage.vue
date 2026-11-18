<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { DynamicSizeList } from 'element-plus'
import type { BugListItem } from '@/types'
import BugResolveDialog from '@/components/project/bug/BugResolveDialog.vue'
import BugListToolbar from '@/components/project/bug/BugListToolbar.vue'
import BugListTable from '@/components/project/bug/BugListTable.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useBugList } from '@/composables/project/bug/useBugList'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'

const BUG_COLUMNS: TableColumnMeta[] = [
  { key: 'id', label: 'ID' },
  { key: 'title', label: '标题' },
  { key: 'type', label: '类型' },
  { key: 'severity', label: '严重等级' },
  { key: 'priority', label: '优先级' },
  { key: 'status', label: '状态' },
  { key: 'reporter', label: '创建人' },
  { key: 'assignee', label: '处理人' },
  { key: 'resolver', label: '解决人' },
  { key: 'resolvedAt', label: '解决时间' },
  { key: 'resolution', label: '解决方案' },
  { key: 'closedAt', label: '关闭时间' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作', pinned: true },
]

const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'bug-list',
  columns: BUG_COLUMNS,
})

const {
  loading,
  loadError,
  hasLoaded,
  hasFilters,
  bugs,
  total,
  viewMode,
  query,
  quickFilter,
  quickFilterOptions,
  filtersExpanded,
  advancedFilterCount,
  boardColumns,
  boardItemSize,
  boardBodyHeight,
  boardRef,
  draggingBug,
  resolveDialogVisible,
  resolvingBug,
  assignDialogVisible,
  assigneeId,
  assigning,
  memberOptions,
  handleSearch,
  handleReset,
  handleKeywordSearch,
  handleAdvancedSearch,
  handleBoardEndReached,
  handleDragStart,
  handleDragEnd,
  isValidDropTarget,
  handleDrop,
  openResolveDialog,
  handleResolveConfirm,
  handleStatusAction,
  handleAssignConfirm,
  handleMoreAction,
  router,
  severityLabel,
  statusLabel,
  severityType,
  boardStatuses,
  loadBugs,
} = useBugList()

function openDetail(bug: BugListItem) {
  router.push(`/workspace/projects/bugs/${bug.id}`)
}

function createBug() {
  router.push('/workspace/projects/bugs/create')
}
</script>

<template>
  <div class="bug-page">
    <BugListToolbar
      v-model:quick-filter="quickFilter"
      v-model:view-mode="viewMode"
      v-model:keyword="query.keyword"
      v-model:status="query.status"
      v-model:bug-type="query.bugType"
      v-model:severity="query.severity"
      v-model:priority="query.priority"
      v-model:filters-expanded="filtersExpanded"
      :quick-filter-options="quickFilterOptions"
      :advanced-filter-count="advancedFilterCount"
      @search="handleSearch"
      @keyword-search="handleKeywordSearch"
      @advanced-search="handleAdvancedSearch"
      @reset="handleReset"
      @create="createBug"
    />

    <section
      v-if="viewMode === 'list'"
      class="bug-page__list"
      aria-label="缺陷列表"
      :aria-busy="loading"
    >
      <div v-if="loadError" class="bug-page__error" role="alert">
        <el-alert
          :title="hasLoaded ? '刷新失败，当前显示上次加载的数据' : '缺陷列表加载失败'"
          :description="loadError"
          type="error"
          show-icon
          :closable="false"
        />
        <el-button :loading="loading" @click="loadBugs">重新加载</el-button>
      </div>
      <div v-loading="loading" class="bug-page__content">
        <BugListTable
          :bugs="bugs"
          :visible-keys="visibleKeys"
          @detail="openDetail"
          @resolve="openResolveDialog"
          @status="handleStatusAction"
          @more="handleMoreAction"
        >
          <template #column-setting>
            <TableColumnSetting
              :columns="BUG_COLUMNS"
              :visible-keys="visibleKeys"
              icon-only
              @change="(key, visible) => setVisible(key, visible)"
              @select-all="selectAll"
              @reset="reset"
            />
          </template>
          <template #empty>
            <div v-if="loading" role="status">正在加载缺陷…</div>
            <el-empty v-else-if="loadError" description="加载失败，请重新加载" :image-size="72" />
            <el-empty
              v-else-if="hasLoaded"
              :description="hasFilters ? '没有符合条件的缺陷' : '暂无缺陷'"
              :image-size="72"
            >
              <el-button v-if="hasFilters" @click="handleReset">重置筛选</el-button>
              <el-button v-else type="primary" @click="createBug">提交缺陷</el-button>
            </el-empty>
          </template>
        </BugListTable>
      </div>
      <ListPagination
        v-model:page-no="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        @page-change="loadBugs"
        @size-change="handleSearch"
      />
    </section>

    <div v-else ref="boardRef" class="bug-board">
      <div
        v-for="status in boardStatuses"
        :key="status"
        class="bug-board__column"
        :class="{
          'bug-board__column--valid': draggingBug && isValidDropTarget(status),
          'bug-board__column--invalid':
            draggingBug && !isValidDropTarget(status) && draggingBug.status !== status,
        }"
        @dragover.prevent
        @drop="handleDrop(status)"
      >
        <div class="bug-board__col-header">
          <span class="bug-board__col-title">{{ statusLabel[status] }}</span>
          <span class="bug-board__col-count">{{ boardColumns[status].total }}</span>
        </div>
        <div class="bug-board__col-body">
          <DynamicSizeList
            v-if="boardColumns[status].list.length"
            :data="boardColumns[status].list"
            :total="boardColumns[status].list.length"
            :item-size="boardItemSize"
            :estimated-item-size="76"
            :height="boardBodyHeight"
            :cache="4"
            class="bug-board__vlist"
            @end-reached="(direction: string) => handleBoardEndReached(status, direction)"
          >
            <template #default="{ index, style }: { index: number; style: CSSProperties }">
              <div v-if="boardColumns[status].list[index]" class="bug-board__vitem" :style="style">
                <button
                  type="button"
                  class="bug-board__card"
                  draggable="true"
                  @dragstart="handleDragStart(boardColumns[status].list[index])"
                  @dragend="handleDragEnd"
                  @click="openDetail(boardColumns[status].list[index])"
                >
                  <span class="bug-board__card-title">{{
                    boardColumns[status].list[index].title
                  }}</span>
                  <span class="bug-board__card-meta">
                    <el-tag
                      :type="severityType[boardColumns[status].list[index].severity]"
                      size="small"
                      effect="light"
                      round
                    >
                      {{ severityLabel[boardColumns[status].list[index].severity] }}
                    </el-tag>
                    <span
                      v-if="boardColumns[status].list[index].assignee"
                      class="bug-board__card-assignee"
                    >
                      {{ boardColumns[status].list[index].assignee?.name }}
                    </span>
                  </span>
                </button>
              </div>
            </template>
          </DynamicSizeList>
          <el-empty v-else-if="!boardColumns[status].loading" description="" :image-size="30" />
          <div v-if="boardColumns[status].loading" class="bug-board__col-loading">加载中…</div>
        </div>
      </div>
    </div>

    <BugResolveDialog
      v-model="resolveDialogVisible"
      :exclude-bug-id="resolvingBug?.id"
      @confirm="handleResolveConfirm"
    />

    <el-dialog v-model="assignDialogVisible" title="指派处理人" width="420px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="处理人" required>
          <el-select
            v-model="assigneeId"
            filterable
            placeholder="选择处理人"
            class="bug-page__assign-select"
          >
            <el-option
              v-for="m in memberOptions"
              :key="m.userId"
              :label="m.name || m.username"
              :value="m.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssignConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.bug-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  // 矮窗口由外层接管兜底滚动，避免工具栏将表体与分页压没。
  min-height: 400px;
  background: var(--color-neutral-0);
}
.bug-page__list,
.bug-page__content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.bug-page__content {
  overflow: hidden;
}
.bug-page__error {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
}
.bug-page__assign-select {
  width: 100%;
}
.bug-board {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-lg);
  overflow-x: auto;
  flex: 1;
  min-height: 400px;
}
.bug-board__column {
  flex: 1;
  min-width: 200px;
  background: var(--color-neutral-50);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-neutral-200);
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);
}
.bug-board__column--valid {
  border-color: var(--color-primary-400);
  background: var(--color-primary-50);
}
.bug-board__column--invalid {
  opacity: 0.5;
}
.bug-board__col-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-neutral-200);
}
.bug-board__col-title {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}
.bug-board__col-count {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-600);
  background: var(--color-neutral-200);
  border-radius: var(--radius-lg);
  padding: 1px 6px;
  font-variant-numeric: tabular-nums;
}
.bug-board__col-body {
  flex: 1;
  overflow: hidden;
  position: relative;
}
/* 虚拟列表保留原滚动拥有者，隐藏组件条以遵守 UI-SC-03。 */
.bug-board__vlist,
.bug-board__vlist :deep(.el-vl__window) {
  width: 100%;
}
.bug-board__vlist :deep(.el-vl__window) {
  scrollbar-width: none;
}
.bug-board__vlist :deep(.el-vl__window)::-webkit-scrollbar {
  display: none;
}
.bug-board__vitem {
  box-sizing: border-box;
  padding: 0 var(--space-sm) var(--space-sm);
}
.bug-board__col-loading {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-600);
  padding: var(--space-xs) 0;
  background: var(--color-neutral-50);
}
.bug-board__card {
  display: block;
  width: 100%;
  font: inherit;
  text-align: left;
  background: var(--color-neutral-0);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
  cursor: grab;
  border: 1px solid var(--color-neutral-200);
  transition:
    box-shadow var(--transition-fast),
    border-color var(--transition-fast);
  &:hover {
    box-shadow: var(--shadow-sm);
    border-color: var(--color-primary-200);
  }
  &:focus-visible {
    outline: 2px solid var(--color-primary-500);
    outline-offset: -2px;
  }
  &:active {
    cursor: grabbing;
  }
}
.bug-board__card-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bug-board__card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.bug-board__card-assignee {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-600);
}
</style>
