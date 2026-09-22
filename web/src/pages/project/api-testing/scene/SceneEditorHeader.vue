<script setup lang="ts">
import { Memo } from '@element-plus/icons-vue'
import type { ApiEnvironmentListItem, ApiExecutionHistoryItem } from '@/types'
import type { CascaderOption } from 'element-plus'
import { formatDateTime } from '@/utils/format'

defineProps<{
  editName: string
  editDescription: string
  editModuleId: string | null
  editEnvironmentId: string | null
  editPriority: string | null
  editStatus: string
  showDescription: boolean
  isCreateMode: boolean
  saving: boolean
  running: boolean
  moduleOptions: CascaderOption[]
  environmentOptions: ApiEnvironmentListItem[]
  currentPriorityColor: string | undefined
  priorityOptions: readonly { value: string; label: string; color: string }[]
  executionHistory: ApiExecutionHistoryItem[]
  executionHistoryTotal: number
  executionHistoryPage: number
  historyLoading: boolean
}>()

const emit = defineEmits<{
  'update:editName': [value: string]
  'update:editDescription': [value: string]
  'update:editModuleId': [value: string | null]
  'update:editEnvironmentId': [value: string | null]
  'update:editPriority': [value: string]
  'update:showDescription': [value: boolean]
  'update:executionHistoryPage': [value: number]
  'save': [status?: string]
  'delete': []
  'run': []
  'view-report': [reportId: string]
  'load-history': []
}>()
</script>

<template>
  <header class="scene-editor__nav">
    <div class="scene-editor__nav-field">
      <span class="scene-editor__required">*</span>
      <el-input
        :model-value="editName"
        class="scene-editor__nav-name-input"
        placeholder="场景名称"
        size="small"
        maxlength="100"
        @update:model-value="emit('update:editName', $event)"
      />
    </div>

    <div class="scene-editor__nav-field">
      <span class="scene-editor__required">*</span>
      <el-cascader
        :model-value="editModuleId"
        :options="moduleOptions"
        :props="{ checkStrictly: true, emitPath: false, value: 'value', label: 'label' }"
        class="scene-editor__nav-module"
        placeholder="所属模块"
        size="small"
        clearable
        @change="(val: unknown) => emit('update:editModuleId', (val as string) ?? null)"
      />
    </div>

    <span
      class="scene-editor__status-tag"
      :class="editStatus === 'published' ? 'is-published' : 'is-draft'"
    >
      {{ editStatus === 'published' ? '已发布' : '草稿' }}
    </span>

    <el-popover placement="bottom-start" :width="120" trigger="click">
      <template #reference>
        <button
          class="scene-editor__priority-tag"
          :style="{ background: currentPriorityColor }"
          type="button"
        >
          {{ editPriority ?? '优先级' }}
        </button>
      </template>
      <div class="scene-editor__priority-menu">
        <button
          v-for="opt in priorityOptions"
          :key="opt.value"
          class="scene-editor__priority-option"
          :class="{ 'is-active': editPriority === opt.value }"
          type="button"
          @click="emit('update:editPriority', opt.value)"
        >
          <span class="scene-editor__priority-dot" :style="{ background: opt.color }" />
          {{ opt.label }}
          <span v-if="opt.value === 'P2' && !editPriority">（默认）</span>
        </button>
      </div>
    </el-popover>

    <el-tooltip content="场景描述" placement="bottom">
      <el-button link size="small" @click="emit('update:showDescription', !showDescription)">
        <el-icon><Memo /></el-icon>
      </el-button>
    </el-tooltip>

    <div v-if="!isCreateMode" style="display: inline-flex">
      <el-popover placement="bottom-start" :width="360" trigger="click">
        <template #reference>
          <el-button link size="small" @click="emit('load-history')">
            执行历史
          </el-button>
        </template>
        <div v-loading="historyLoading" class="scene-editor__history-panel">
          <div class="scene-editor__settings-section">
            <div class="scene-editor__settings-head">执行历史</div>
            <div v-for="h in executionHistory" :key="h.id" class="scene-editor__history-row">
              <span class="scene-editor__history-status" :class="`is-${h.status}`">{{ h.status }}</span>
              <span>{{ formatDateTime(h.executedAt) }}</span>
              <el-button v-if="h.reportId" link size="small" type="primary" @click="emit('view-report', h.reportId)">报告</el-button>
            </div>
            <div v-if="!executionHistory.length" class="scene-editor__empty-text">暂无执行记录</div>
            <el-pagination
              v-if="executionHistoryTotal > 0"
              :current-page="executionHistoryPage"
              :page-size="20"
              :total="executionHistoryTotal"
              layout="prev, pager, next"
              small
              class="scene-editor__history-pager"
              @current-change="emit('update:executionHistoryPage', $event); emit('load-history')"
            />
          </div>
        </div>
      </el-popover>
    </div>

    <div class="scene-editor__nav-spacer" />

    <el-select :model-value="editEnvironmentId" size="small" placeholder="默认环境" clearable style="width: 180px" @update:model-value="emit('update:editEnvironmentId', $event)">
      <el-option v-for="env in environmentOptions" :key="env.id" :value="env.id" :label="env.name" />
    </el-select>

    <template v-if="isCreateMode">
      <el-button size="small" :loading="running" @click="emit('run')">▶ 运行场景</el-button>
      <el-button size="small" @click="emit('save', 'draft')">保存为草稿</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="emit('save', 'published')">发布</el-button>
    </template>
    <template v-else>
      <el-button size="small" :loading="running" type="primary" plain @click="emit('run')">▶ 运行场景</el-button>
      <el-button size="small" @click="emit('save', 'draft')">保存为草稿</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="emit('save', 'published')">发布</el-button>
      <el-button size="small" type="danger" plain @click="emit('delete')">删除</el-button>
    </template>

    <el-input
      v-if="showDescription"
      :model-value="editDescription"
      type="textarea"
      :rows="2"
      class="scene-editor__nav-desc"
      placeholder="场景描述（可选）"
      @update:model-value="emit('update:editDescription', $event)"
    />
  </header>
</template>
