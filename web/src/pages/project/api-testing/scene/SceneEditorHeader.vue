<script setup lang="ts">
import { computed } from 'vue'
import { Memo } from '@element-plus/icons-vue'
import type { ApiEnvironmentListItem, ApiExecutionHistoryItem } from '@/types'
import type { CascaderOption } from 'element-plus'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
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
  /** 派生执行引擎（详设 5.3）：gitlab_ci 场景动作文案为「流水线运行」（交互 10 §1.9） */
  engine?: string | null
  /** 场景来源：gitlab 时展示 [GitLab] 徽标（交互 10 §1.9） */
  sourceType?: string | null
  /** 源状态：parse_failed 源码已退化 / repo_unreachable 仓库不可达（交互 10 §1.9） */
  sourceStatus?: string | null
  /** 来源徽标 hover 展示的「仓库名 · 项目路径」 */
  repoLabel?: string | null
  /** gitlab_ci 只读形态：顶部仅保留场景名、徽标与 [流水线运行]（交互 10 §1.9） */
  readonlyMode?: boolean
  moduleOptions: CascaderOption[]
  environmentOptions: ApiEnvironmentListItem[]
  currentPriorityColor: string | undefined
  priorityOptions: readonly { value: string; label: string; color: string }[]
  executionHistory: ApiExecutionHistoryItem[]
  executionHistoryTotal: number
  executionHistoryPage: number
  historyLoading: boolean
  historyError: string | null
}>()

// 单按钮执行无引擎下拉：文案随引擎派生，gitlab_ci 点击即触发流水线（交互 26 §2）
const runLabel = computed(() => (props.engine === 'gitlab_ci' ? '▶ 流水线运行' : '▶ 运行场景'))

const isGitImport = computed(() => props.sourceType === 'gitlab')

// 源状态在编排页只呈现两类（交互 10 §1.9）：源码已退化不阻断编辑，仓库不可达置灰执行入口
const SOURCE_STATUS_TEXT: Record<string, string> = {
  parse_failed: '源码已退化',
  repo_unreachable: '仓库不可达',
}
const statusText = computed(() =>
  props.sourceStatus ? SOURCE_STATUS_TEXT[props.sourceStatus] ?? null : null,
)
const repoUnreachable = computed(() => props.sourceStatus === 'repo_unreachable')

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
  'go-gitlab': []
  'view-report': [reportId: string]
  'load-history': []
}>()
</script>

<template>
  <header class="scene-editor__nav">
    <!-- 只读形态（交互 10 §1.9）：两行头部——场景名与徽标在上，元数据行在下；动作固定最右端 -->
    <div v-if="readonlyMode" class="scene-editor__nav-readonly">
      <div class="scene-editor__readonly-title">
        <span class="scene-editor__readonly-name">{{ editName }}</span>
        <el-tooltip v-if="isGitImport" :content="repoLabel || 'GitLab 导入'" placement="bottom">
          <span
            class="scene-editor__git-badge"
            role="button"
            tabindex="0"
            @click="emit('go-gitlab')"
            @keydown.enter="emit('go-gitlab')"
          >GitLab</span>
        </el-tooltip>
      </div>
      <div class="scene-editor__readonly-meta">
        <span
          class="scene-editor__status-tag"
          :class="editStatus === 'published' ? 'is-published' : 'is-draft'"
        >{{ editStatus === 'published' ? '已发布' : '草稿' }}</span>
        <span v-if="editPriority" class="scene-editor__priority-tag" :style="{ background: currentPriorityColor }">
          {{ editPriority }}
        </span>
        <span v-if="statusText" class="scene-editor__source-status" :class="`is-${sourceStatus}`">{{ statusText }}</span>
      </div>
    </div>
    <div v-if="readonlyMode" class="scene-editor__nav-spacer" />

    <template v-if="!readonlyMode">
      <div class="scene-editor__nav-field">
        <span class="scene-editor__field-label">场景名称<span class="scene-editor__required">*</span></span>
        <el-input
          :model-value="editName"
          class="scene-editor__nav-name-input"
          placeholder="场景名称"
          size="small"
          maxlength="100"
          @update:model-value="emit('update:editName', $event)"
        />
        <el-tooltip v-if="isGitImport" :content="repoLabel || 'GitLab 导入'" placement="bottom">
          <span
            class="scene-editor__git-badge"
            role="button"
            tabindex="0"
            @click="emit('go-gitlab')"
            @keydown.enter="emit('go-gitlab')"
          >GitLab</span>
        </el-tooltip>
      </div>

      <div class="scene-editor__nav-field">
        <span class="scene-editor__field-label">所属模块<span class="scene-editor__required">*</span></span>
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

      <!-- 信息组：状态 / 优先级 / 描述 / 执行历史以分隔线与字段区分开（交互 10 §1.1） -->
      <div class="scene-editor__info-group">
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
              <div v-if="historyError" class="scene-editor__history-error" role="alert">
                <span>{{ historyError }}</span>
                <el-button link size="small" type="primary" @click="emit('load-history')">重试</el-button>
              </div>
              <div v-for="h in executionHistory" :key="h.id" class="scene-editor__history-row">
                <span class="scene-editor__history-status" :class="`is-${h.status}`">{{ h.status }}</span>
                <span>{{ formatDateTime(h.executedAt) }}</span>
                <el-button v-if="h.reportId" link size="small" type="primary" @click="emit('view-report', h.reportId)">报告</el-button>
              </div>
              <div v-if="!executionHistory.length && !historyError" class="scene-editor__empty-text">暂无执行记录</div>
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

      </div>

      <div class="scene-editor__nav-spacer" />

      <span class="scene-editor__field-label">默认环境</span>
      <el-select :model-value="editEnvironmentId" size="small" placeholder="默认环境" clearable style="width: 180px" @update:model-value="emit('update:editEnvironmentId', $event)">
        <el-option v-for="env in environmentOptions" :key="env.id" :value="env.id" :label="env.name" />
      </el-select>
    </template>

    <!-- 执行入口（交互 10 §1.9）：只读形态下为页面唯一动作；仓库不可达时置灰并提示原因 -->
    <template v-if="readonlyMode">
      <el-tooltip :content="`仓库不可达，无法执行`" :disabled="!repoUnreachable" placement="bottom">
        <span class="scene-editor__run-wrap">
          <el-button size="small" type="primary" plain :disabled="repoUnreachable" :loading="running" @click="emit('run')">{{ runLabel }}</el-button>
        </span>
      </el-tooltip>
    </template>
    <template v-else-if="isCreateMode">
      <div class="scene-editor__nav-actions">
        <el-tooltip :content="`仓库不可达，无法执行`" :disabled="!repoUnreachable" placement="bottom">
          <span class="scene-editor__run-wrap">
            <el-button size="small" :disabled="repoUnreachable" :loading="running" @click="emit('run')">{{ runLabel }}</el-button>
          </span>
        </el-tooltip>
        <el-button size="small" @click="emit('save', 'draft')">保存为草稿</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="emit('save', 'published')">发布</el-button>
      </div>
    </template>
    <template v-else>
      <div class="scene-editor__nav-actions">
        <el-tooltip :content="`仓库不可达，无法执行`" :disabled="!repoUnreachable" placement="bottom">
          <span class="scene-editor__run-wrap">
            <el-button size="small" :disabled="repoUnreachable" :loading="running" type="primary" plain @click="emit('run')">{{ runLabel }}</el-button>
          </span>
        </el-tooltip>
        <el-button size="small" @click="emit('save', 'draft')">保存为草稿</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="emit('save', 'published')">发布</el-button>
        <el-button size="small" type="danger" plain @click="emit('delete')">删除</el-button>
      </div>
    </template>

    <el-input
      v-if="showDescription && !readonlyMode"
      :model-value="editDescription"
      type="textarea"
      :rows="2"
      class="scene-editor__nav-desc"
      placeholder="场景描述（可选）"
      @update:model-value="emit('update:editDescription', $event)"
    />
  </header>
</template>

<style scoped lang="scss">
// 顶部导航：字段区 → 信息组 → 弹性留白 → 环境与动作（交互 10 §1.1）
.scene-editor__nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  padding-bottom: var(--space-md);
}

.scene-editor__nav-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

// 字段小标签：必填星号随标签同行，不单独占行（交互 10 §1.1）
.scene-editor__field-label {
  display: inline-flex;
  align-items: center;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  white-space: nowrap;
}

.scene-editor__required {
  margin-left: 2px;
  color: var(--el-color-danger);
}

.scene-editor__nav-name-input {
  width: 240px;
}

.scene-editor__nav-module {
  width: 260px;
}

// 信息组：与字段区之间以分隔线分组（交互 10 §1.1）
.scene-editor__info-group {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  padding-left: var(--space-sm);
  border-left: 1px solid var(--color-neutral-200);
}

.scene-editor__nav-spacer {
  flex: 1 1 auto;
}

// 动作组固定右端：换行时整体贴右侧，不残留在字段下方（交互 10 §1.1）
.scene-editor__nav-actions {
  display: inline-flex;
  align-items: center;
  margin-left: auto;
}

.scene-editor__status-tag {
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;

  &.is-published {
    background: rgba(var(--el-color-primary-rgb), 0.1);
    color: var(--el-color-primary);
  }

  &.is-draft {
    background: var(--color-neutral-100);
    color: var(--color-neutral-600);
  }
}

.scene-editor__priority-tag {
  padding: 0 8px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  color: var(--color-neutral-800);
  cursor: pointer;
}

.scene-editor__history-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

// 只读形态顶部（交互 10 §1.9）：上行场景名与徽标，下行元数据行，动作固定最右端
.scene-editor__nav-readonly {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.scene-editor__readonly-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
}

.scene-editor__readonly-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
}

.scene-editor__readonly-name {
  font-weight: 600;
}

// [GitLab] 来源徽标：hover 展示仓库与项目路径，点击跳集成页导入页签
.scene-editor__git-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  background: rgba(var(--el-color-info-rgb), 0.1);
  color: var(--el-color-info);
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
  cursor: pointer;
}

// 源状态徽标：源码已退化（警告）/ 仓库不可达（危险）
.scene-editor__source-status {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;

  &.is-parse_failed {
    background: rgba(var(--el-color-warning-rgb), 0.1);
    color: var(--el-color-warning);
  }

  &.is-repo_unreachable {
    background: rgba(var(--el-color-danger-rgb), 0.1);
    color: var(--el-color-danger);
  }
}

// 仓库不可达时按钮置灰，tooltip 由外层 span 承载（disabled 按钮不触发 hover）
.scene-editor__run-wrap {
  display: inline-flex;
}
</style>
