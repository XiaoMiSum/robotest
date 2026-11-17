<script setup lang="ts">
import type { BugPriority, BugSeverity, BugStatus, BugType } from '@/types'
import type { QuickFilter } from '@/composables/project/bug/useBugList'
import {
  BUG_PRIORITY_LABEL,
  BUG_SEVERITY_LABEL,
  BUG_STATUS_LABEL,
  BUG_TYPE_LABEL,
} from '@/composables/project/bug/bugStatus'

defineProps<{
  quickFilterOptions: { value: QuickFilter; label: string }[]
  advancedFilterCount: number
}>()

const quickFilter = defineModel<QuickFilter>('quickFilter', { required: true })
const viewMode = defineModel<'list' | 'board'>('viewMode', { required: true })
const keyword = defineModel<string>('keyword', { required: true })
const status = defineModel<BugStatus | ''>('status', { required: true })
const bugType = defineModel<BugType | ''>('bugType', { required: true })
const severity = defineModel<BugSeverity | ''>('severity', { required: true })
const priority = defineModel<BugPriority | ''>('priority', { required: true })
const filtersExpanded = defineModel<boolean>('filtersExpanded', { required: true })

const emit = defineEmits<{
  search: []
  'keyword-search': []
  'advanced-search': []
  reset: []
  create: []
}>()
</script>

<template>
  <section class="bug-toolbar" aria-label="缺陷筛选与操作">
    <div class="bug-toolbar__row">
      <el-radio-group
        v-model="quickFilter"
        aria-label="快捷过滤"
        class="bug-toolbar__quick"
        @change="emit('search')"
      >
        <el-radio-button v-for="opt in quickFilterOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="bug-toolbar__actions">
        <el-radio-group v-model="viewMode" aria-label="缺陷视图">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="board">看板</el-radio-button>
        </el-radio-group>
        <slot name="column-setting" />
        <el-button type="primary" @click="emit('create')">
          <el-icon><Plus /></el-icon>提交缺陷
        </el-button>
      </div>
    </div>
    <div class="bug-toolbar__search-row">
      <el-input
        v-model="keyword"
        class="bug-toolbar__search"
        aria-label="搜索缺陷 ID 或标题"
        placeholder="搜索 ID（前缀/后缀）或标题"
        clearable
        @keyup.enter="emit('keyword-search')"
        @blur="emit('keyword-search')"
        @clear="emit('keyword-search')"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-popover
        v-model:visible="filtersExpanded"
        placement="bottom-start"
        :width="300"
        trigger="click"
      >
        <template #reference>
          <el-badge :value="advancedFilterCount" :hidden="!advancedFilterCount" type="primary">
            <el-button :aria-expanded="filtersExpanded">
              更多筛选<el-icon
                ><component :is="filtersExpanded ? 'ArrowUp' : 'ArrowDown'"
              /></el-icon>
            </el-button>
          </el-badge>
        </template>
        <el-form label-width="70px" @submit.prevent="emit('advanced-search')">
          <el-form-item label="状态">
            <el-select
              v-model="status"
              aria-label="状态"
              placeholder="全部"
              clearable
              :teleported="false"
            >
              <el-option
                v-for="(label, key) in BUG_STATUS_LABEL"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select
              v-model="bugType"
              aria-label="类型"
              placeholder="全部"
              clearable
              :teleported="false"
            >
              <el-option
                v-for="(label, key) in BUG_TYPE_LABEL"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="严重等级">
            <el-select
              v-model="severity"
              aria-label="严重等级"
              placeholder="全部"
              clearable
              :teleported="false"
            >
              <el-option
                v-for="(label, key) in BUG_SEVERITY_LABEL"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select
              v-model="priority"
              aria-label="优先级"
              placeholder="全部"
              clearable
              :teleported="false"
            >
              <el-option
                v-for="(label, key) in BUG_PRIORITY_LABEL"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
          </el-form-item>
          <div class="bug-toolbar__filter-actions">
            <el-button @click="emit('reset')">重置</el-button>
            <el-button type="primary" native-type="submit">查询</el-button>
          </div>
        </el-form>
      </el-popover>
    </div>
  </section>
</template>

<style scoped lang="scss">
.bug-toolbar {
  flex-shrink: 0;
  padding: var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-200);
}
.bug-toolbar__row,
.bug-toolbar__search-row,
.bug-toolbar__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-md);
}
.bug-toolbar__row {
  justify-content: space-between;
}
.bug-toolbar__quick {
  flex-wrap: wrap;
  gap: var(--space-xs) 0;
}
.bug-toolbar__actions {
  margin-left: auto;
}
.bug-toolbar__search-row {
  margin-top: var(--space-lg);
}
.bug-toolbar__search {
  flex: 1 1 16rem;
  max-width: 32rem;
  min-width: 0;
}
.bug-toolbar__filter-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
