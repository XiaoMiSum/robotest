<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { DynamicSizeList } from 'element-plus'
import { formatShortDateTime, formatShortId, truncateText } from '@/utils/format'
import type { BugListItem, BugResolution, BugStatus, BugType } from '@/types'
import BugClusterPanel from '@/components/project/BugClusterPanel.vue'
import BugResolveDialog from '@/components/project/BugResolveDialog.vue'
import { useBugList } from '@/composables/project/bug/useBugList'
import { useAiStore } from '@/stores/ai'
import { computed } from 'vue'

const aiStore = useAiStore()
const aiEnabled = computed(() => aiStore.aiEnabled)

const {
  loading,
  bugs,
  total,
  viewMode,
  clusterVisible,
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
  priorityLabel,
  statusLabel,
  severityType,
  priorityType,
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  boardStatuses,
  loadBugs,
} = useBugList()
</script>

<template>
  <div class="bug-page">

    <el-card shadow="never" class="bug-page__filters" :class="{ 'bug-page__filters--sticky': viewMode === 'list' }">
      <el-form :inline="true" class="bug-page__filter-form" @submit.prevent>
        <el-form-item>
          <el-radio-group v-model="quickFilter" @change="handleSearch">
            <el-radio-button v-for="opt in quickFilterOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="query.keyword"
            placeholder="搜索 ID（前缀/后缀）或标题"
            clearable
            style="width: 360px"
            @keyup.enter="handleKeywordSearch"
            @blur="handleKeywordSearch"
            @clear="handleKeywordSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-popover v-model:visible="filtersExpanded" placement="bottom-start" :width="300" trigger="click">
            <template #reference>
              <el-badge :value="advancedFilterCount" :hidden="!advancedFilterCount" type="primary">
                <el-button link type="primary">
                  更多筛选<el-icon><component :is="filtersExpanded ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <el-form label-width="70px" class="bug-page__advanced-form" @submit.prevent>
              <el-form-item label="状态">
                <el-select v-model="query.status" placeholder="全部" clearable :teleported="false">
                  <el-option v-for="(label, key) in statusLabel" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="query.bugType" placeholder="全部" clearable :teleported="false">
                  <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="严重等级">
                <el-select v-model="query.severity" placeholder="全部" clearable :teleported="false">
                  <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="query.priority" placeholder="全部" clearable :teleported="false">
                  <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <div class="bug-page__advanced-actions">
                <el-button type="primary" @click="handleAdvancedSearch">
                  <el-icon><Search /></el-icon>查询
                </el-button>
                <el-button @click="handleReset">重置</el-button>
              </div>
            </el-form>
          </el-popover>
        </el-form-item>
        <el-form-item class="bug-page__filter-spacer" />
        <el-form-item>
          <el-radio-group v-model="viewMode">
            <el-radio-button value="list">列表</el-radio-button>
            <el-radio-button value="board">看板</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="aiEnabled">
          <el-button type="primary" plain @click="clusterVisible = true">
            <el-icon><MagicStick /></el-icon>AI 分析
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="router.push('/workspace/projects/bugs/create')">
            <el-icon><Plus /></el-icon>提交缺陷
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <BugClusterPanel v-if="aiEnabled" v-model="clusterVisible" />

    <el-card v-if="viewMode === 'list'" v-loading="loading" shadow="never">
      <el-table :data="bugs" row-key="id">
        <el-table-column label="ID" width="110">
          <template #default="{ row }">{{ formatShortId(row.id) }}</template>
        </el-table-column>
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <el-link
              type="primary"
              underline="never"
              :title="row.title.length > 18 ? row.title : undefined"
              @click="router.push(`/workspace/projects/bugs/${row.id}`)"
              >{{ truncateText(row.title, 18) }}</el-link
            >
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ BUG_TYPE_LABEL[row.bugType as BugType] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="严重等级" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType[row.severity]" size="small" effect="light" round>{{ severityLabel[row.severity] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :type="priorityType[row.priority]" size="small" effect="light" round>{{ priorityLabel[row.priority] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="BUG_STATUS_TAG_TYPE[row.status as BugStatus]" size="small" effect="light" round>{{ statusLabel[row.status as BugStatus] }}</el-tag>
            <el-tag v-if="row.confirmed" size="small" type="warning" effect="plain" class="bug-page__confirmed-tag">已确认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="100">
          <template #default="{ row }">{{ row.reporter?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="处理人" width="100">
          <template #default="{ row }">{{ row.assignee?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="解决人" width="100">
          <template #default="{ row }">{{ row.resolvedBy?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="解决时间" width="110">
          <template #default="{ row }">{{ formatShortDateTime(row.resolvedAt) }}</template>
        </el-table-column>
        <el-table-column label="解决方案" width="100">
          <template #default="{ row }">{{ row.resolution ? BUG_RESOLUTION_LABEL[row.resolution as BugResolution] : '-' }}</template>
        </el-table-column>
        <el-table-column label="关闭时间" width="110">
          <template #default="{ row }">{{ formatShortDateTime(row.closedAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="110">
          <template #default="{ row }">{{ formatShortDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/workspace/projects/bugs/${row.id}`)">详情</el-button>
            <el-button v-if="row.status === 'active'" link type="success" @click="openResolveDialog(row as BugListItem)">解决</el-button>
            <el-button v-if="row.status === 'active'" link type="warning" @click="handleStatusAction(row as BugListItem, 'rejected', '缺陷已拒绝')">拒绝</el-button>
            <el-button v-if="row.status === 'resolved' || row.status === 'rejected'" link type="info" @click="handleStatusAction(row as BugListItem, 'closed', '缺陷已关闭')">关闭</el-button>
            <el-button v-if="row.status === 'closed'" link type="danger" @click="handleStatusAction(row as BugListItem, 'active', '缺陷已激活')">激活</el-button>
            <el-dropdown
              class="bug-page__more"
              @command="(cmd: string) => handleMoreAction(cmd, row as BugListItem)"
            >
              <el-button link type="primary">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status === 'active' && !row.confirmed" command="confirm">确认</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'resolved' || row.status === 'rejected'" command="reopen">激活</el-dropdown-item>
                  <el-dropdown-item v-if="row.status !== 'closed'" command="assign">指派</el-dropdown-item>
                  <el-dropdown-item command="copy">复制</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <div class="bug-page__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadBugs"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <div v-else ref="boardRef" class="bug-board">
      <div
        v-for="status in boardStatuses"
        :key="status"
        class="bug-board__column"
        :class="{
          'bug-board__column--valid': draggingBug && isValidDropTarget(status),
          'bug-board__column--invalid': draggingBug && !isValidDropTarget(status) && draggingBug.status !== status,
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
                <div
                  class="bug-board__card"
                  draggable="true"
                  @dragstart="handleDragStart(boardColumns[status].list[index])"
                  @dragend="handleDragEnd"
                  @click="router.push(`/workspace/projects/bugs/${boardColumns[status].list[index].id}`)"
                >
                  <div class="bug-board__card-title">{{ boardColumns[status].list[index].title }}</div>
                  <div class="bug-board__card-meta">
                    <el-tag :type="severityType[boardColumns[status].list[index].severity]" size="small" effect="light" round>
                      {{ severityLabel[boardColumns[status].list[index].severity] }}
                    </el-tag>
                    <span v-if="boardColumns[status].list[index].assignee" class="bug-board__card-assignee">
                      {{ boardColumns[status].list[index].assignee?.name }}
                    </span>
                  </div>
                </div>
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
          <el-select v-model="assigneeId" filterable placeholder="选择处理人" class="bug-page__assign-select">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
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
.bug-page__filters {
  margin-bottom: var(--space-lg);
}

.bug-page__filters--sticky {
  position: sticky;
  top: 0;
  z-index: 10;
}

.bug-page__filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.bug-page__filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

.bug-page__filter-spacer {
  flex: 1;
}

.bug-page__advanced-form :deep(.el-select) {
  width: 100%;
}

.bug-page__advanced-actions {
  display: flex;
  justify-content: flex-end;
}

.bug-page__confirmed-tag {
  margin-left: 4px;
}

.bug-page__more {
  margin-left: 12px;
  vertical-align: middle;
}

.bug-page__assign-select {
  width: 100%;
}

.bug-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.bug-board {
  display: flex;
  gap: var(--space-md);
  overflow-x: auto;
  height: calc(100vh - var(--header-height) - 138px);
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
  transition: all var(--transition-fast);
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
  font-size: var(--font-size-xs);
  color: var(--color-neutral-700);
}

.bug-board__col-count {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  background: var(--color-neutral-200);
  border-radius: var(--radius-full);
  padding: 1px 6px;
  font-weight: 500;
}

.bug-board__col-body {
  flex: 1;
  overflow: hidden;
  position: relative;
}

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
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  padding: var(--space-xs) 0;
  background: var(--color-neutral-50);
}

.bug-board__card {
  background: var(--color-neutral-0);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
  cursor: grab;
  border: 1px solid var(--color-neutral-200);
  transition: all var(--transition-fast);

  &:hover {
    box-shadow: var(--shadow-sm);
    border-color: var(--color-primary-200);
  }

  &:active {
    cursor: grabbing;
  }
}

.bug-board__card-title {
  font-size: var(--font-size-xs);
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
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
}
</style>
