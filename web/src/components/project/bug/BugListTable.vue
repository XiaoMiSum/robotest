<script setup lang="ts">
import type {
  BugListItem,
  BugPriority,
  BugResolution,
  BugSeverity,
  BugStatus,
  BugType,
} from '@/types'
import { formatShortDateTime, formatShortId } from '@/utils/format'
import {
  BUG_PRIORITY_LABEL,
  BUG_PRIORITY_TAG_TYPE,
  BUG_RESOLUTION_LABEL,
  BUG_SEVERITY_LABEL,
  BUG_SEVERITY_TAG_TYPE,
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
} from '@/composables/project/bug/bugStatus'

defineProps<{ bugs: BugListItem[] }>()
const emit = defineEmits<{
  detail: [bug: BugListItem]
  resolve: [bug: BugListItem]
  status: [bug: BugListItem, target: BugStatus, message: string]
  more: [command: string, bug: BugListItem]
}>()

const statusIcons: Record<BugStatus, string> = {
  active: 'Warning',
  resolved: 'CircleCheck',
  rejected: 'CircleClose',
  closed: 'Lock',
}
</script>

<template>
  <div class="bug-table">
    <el-table :data="bugs" row-key="id" height="100%">
      <template #empty><slot name="empty" /></template>
      <el-table-column label="ID" width="110">
        <template #default="{ row }"
          ><span class="bug-table__id">{{ formatShortId(row.id) }}</span></template
        >
      </el-table-column>
      <el-table-column label="标题" min-width="300">
        <template #default="{ row }">
          <el-tooltip
            :content="row.title"
            placement="top-start"
            :show-after="300"
            :trigger="['hover', 'focus']"
            :trigger-keys="[]"
          >
            <button
              class="bug-table__title"
              type="button"
              @click="emit('detail', row as BugListItem)"
            >
              {{ row.title }}
            </button>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ BUG_TYPE_LABEL[row.bugType as BugType] ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="严重等级" width="100">
        <template #default="{ row }">
          <el-tag
            :type="BUG_SEVERITY_TAG_TYPE[row.severity as BugSeverity]"
            size="small"
            effect="light"
          >
            <span class="bug-table__dot" aria-hidden="true" />{{
              BUG_SEVERITY_LABEL[row.severity as BugSeverity]
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="80">
        <template #default="{ row }">
          <el-tag
            :type="BUG_PRIORITY_TAG_TYPE[row.priority as BugPriority]"
            size="small"
            effect="plain"
          >
            {{ BUG_PRIORITY_LABEL[row.priority as BugPriority] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="180">
        <template #default="{ row }">
          <div class="bug-table__status">
            <el-tag
              :type="BUG_STATUS_TAG_TYPE[row.status as BugStatus]"
              size="small"
              effect="light"
            >
              <el-icon class="bug-table__status-icon" aria-hidden="true"
                ><component :is="statusIcons[row.status as BugStatus]"
              /></el-icon>
              {{ BUG_STATUS_LABEL[row.status as BugStatus] }}
            </el-tag>
            <span v-if="row.confirmed" class="bug-table__confirmed">已确认</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="创建人" width="100" show-overflow-tooltip>
        <template #default="{ row }"
          ><span class="bug-table__secondary">{{ row.reporter?.name ?? '-' }}</span></template
        >
      </el-table-column>
      <el-table-column label="处理人" width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.assignee?.name ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="解决人" width="100" show-overflow-tooltip>
        <template #default="{ row }"
          ><span class="bug-table__secondary">{{ row.resolvedBy?.name ?? '-' }}</span></template
        >
      </el-table-column>
      <el-table-column label="解决时间" width="120">
        <template #default="{ row }"
          ><span class="bug-table__time">{{ formatShortDateTime(row.resolvedAt) }}</span></template
        >
      </el-table-column>
      <el-table-column label="解决方案" width="100">
        <template #default="{ row }">{{
          row.resolution ? BUG_RESOLUTION_LABEL[row.resolution as BugResolution] : '-'
        }}</template>
      </el-table-column>
      <el-table-column label="关闭时间" width="120">
        <template #default="{ row }"
          ><span class="bug-table__time">{{ formatShortDateTime(row.closedAt) }}</span></template
        >
      </el-table-column>
      <el-table-column label="创建时间" width="120">
        <template #default="{ row }"
          ><span class="bug-table__time">{{ formatShortDateTime(row.createdAt) }}</span></template
        >
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-space class="bug-table__actions" :size="8">
            <el-button link type="primary" @click="emit('detail', row as BugListItem)"
              >详情</el-button
            >
            <el-button
              v-if="row.status === 'active'"
              link
              type="primary"
              @click="emit('resolve', row as BugListItem)"
              >解决</el-button
            >
            <el-button
              v-if="row.status === 'active'"
              link
              type="primary"
              @click="emit('status', row as BugListItem, 'rejected', '缺陷已拒绝')"
              >拒绝</el-button
            >
            <el-button
              v-if="row.status === 'resolved' || row.status === 'rejected'"
              link
              type="primary"
              @click="emit('status', row as BugListItem, 'closed', '缺陷已关闭')"
              >关闭</el-button
            >
            <el-button
              v-if="row.status === 'closed'"
              link
              type="primary"
              @click="emit('status', row as BugListItem, 'active', '缺陷已激活')"
              >激活</el-button
            >
            <el-dropdown @command="(command: string) => emit('more', command, row as BugListItem)">
              <el-button link type="primary" :aria-label="`${row.title}的更多操作`"
                >更多<el-icon><ArrowDown /></el-icon
              ></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="row.status === 'active' && !row.confirmed"
                    command="confirm"
                    >确认</el-dropdown-item
                  >
                  <el-dropdown-item
                    v-if="row.status === 'resolved' || row.status === 'rejected'"
                    command="reopen"
                    >激活</el-dropdown-item
                  >
                  <el-dropdown-item v-if="row.status !== 'closed'" command="assign"
                    >指派</el-dropdown-item
                  >
                  <el-dropdown-item command="copy">复制</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-space>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.bug-table {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.bug-table__title {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-primary-600);
  font: inherit;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  &:hover {
    text-decoration: underline;
  }
  &:focus-visible {
    outline: 2px solid var(--color-primary-500);
    outline-offset: -2px;
  }
}
.bug-table__id {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}
.bug-table__secondary,
.bug-table__time,
.bug-table__confirmed {
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}
.bug-table__time {
  font-variant-numeric: tabular-nums;
}
.bug-table__status,
.bug-table__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.bug-table__status-icon {
  margin-right: var(--space-xs);
  vertical-align: middle;
}
.bug-table__dot {
  display: inline-block;
  width: var(--space-xs);
  height: var(--space-xs);
  margin-right: var(--space-xs);
  border-radius: 50%;
  background: currentColor;
  vertical-align: middle;
}
</style>
