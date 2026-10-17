<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown, Memo } from '@element-plus/icons-vue'
import type { ApiEnvironmentListItem, ApiExecutionHistoryItem } from '@/types'
import type { CascaderOption } from 'element-plus'
import SceneHistoryPopover from './SceneHistoryPopover.vue'

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
  /** 场景来源：gitlab 时展示 [GitLab] 徽标（交互 10 §1.9） */
  sourceType?: string | null
  /** 源状态：parse_failed 源码已退化 / repo_unreachable 仓库不可达（交互 10 §1.9） */
  sourceStatus?: string | null
  /** 来源徽标 hover 展示的「仓库名 · 项目路径」 */
  repoLabel?: string | null
  /** gitlab_ci 只读形态：单行头部——场景名 / 模块 / 发布状态 / 徽标 / 执行历史 / 运行状态（交互 10 §1.9，对齐 demo） */
  readonlyMode?: boolean
  /** 只读头部展示的所属模块名（demo ci-info 之前先在头部呈现） */
  moduleName?: string | null
  /** 运行状态徽标：待执行 / 执行中 / 执行成功 / 执行失败（对齐 demo ci-run 四态） */
  runState?: 'idle' | 'running' | 'success' | 'failed'
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

// 执行入口文案：CI 场景与平台统一为「运行」，由页面按引擎决定触发链路（交互 26 §2）
const runLabel = '▶ 运行'

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

// 运行状态文案（对齐 demo api-scenes.html 的 ci-run 四态）
const RUN_STATE_TEXT: Record<string, string> = {
  idle: '待执行',
  running: '执行中',
  success: '执行成功',
  failed: '执行失败',
}

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
  <header class="scene-editor__nav" :class="{ 'is-card': readonlyMode }">
    <!-- 只读形态（交互 10 §1.9，对齐 demo api-scenes.html edit-ci）：单行——场景名 · 模块 · 发布状态 · 徽标 · 执行历史 …… 运行状态 -->
    <template v-if="readonlyMode">
      <span class="scene-editor__readonly-name">{{ editName }}</span>
      <span v-if="moduleName" class="scene-editor__readonly-module">{{ moduleName }}</span>
      <span
        class="scene-editor__status-tag"
        :class="editStatus === 'published' ? 'is-published' : 'is-draft'"
      >{{ editStatus === 'published' ? '已发布' : '草稿' }}</span>
      <el-tooltip v-if="isGitImport" :content="repoLabel || 'GitLab 导入'" placement="bottom">
        <span
          class="scene-editor__git-badge"
          role="button"
          tabindex="0"
          @click="emit('go-gitlab')"
          @keydown.enter="emit('go-gitlab')"
        >GitLab</span>
      </el-tooltip>
      <span v-if="statusText" class="scene-editor__source-status" :class="`is-${sourceStatus}`">{{ statusText }}</span>
      <SceneHistoryPopover
        :history="executionHistory"
        :total="executionHistoryTotal"
        :page="executionHistoryPage"
        :loading="historyLoading"
        :error="historyError"
        @load="emit('load-history')"
        @view-report="(id: string) => emit('view-report', id)"
        @update:page="(p: number) => { emit('update:executionHistoryPage', p); emit('load-history') }"
      />
      <div class="scene-editor__nav-spacer" />
      <span class="scene-editor__ci-run" :class="`is-${runState ?? 'idle'}`">{{ RUN_STATE_TEXT[runState ?? 'idle'] }}</span>
    </template>

    <template v-if="!readonlyMode">
      <div class="scene-editor__nav-field">
        <span class="scene-editor__field-label">名称<span class="scene-editor__required">*</span></span>
        <el-input
          :model-value="editName"
          class="scene-editor__nav-name-input"
          placeholder="场景名称"
         
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
        <span class="scene-editor__field-label">模块<span class="scene-editor__required">*</span></span>
        <el-cascader
          :model-value="editModuleId"
          :options="moduleOptions"
          :props="{ checkStrictly: true, emitPath: false, value: 'value', label: 'label' }"
          class="scene-editor__nav-module"
          placeholder="所属模块"
         
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
            <el-icon class="scene-editor__priority-caret"><ArrowDown /></el-icon>
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
        <el-button link @click="emit('update:showDescription', !showDescription)">
          <el-icon><Memo /></el-icon>
        </el-button>
      </el-tooltip>

      <div v-if="!isCreateMode" style="display: inline-flex">
        <SceneHistoryPopover
          :history="executionHistory"
          :total="executionHistoryTotal"
          :page="executionHistoryPage"
          :loading="historyLoading"
          :error="historyError"
          @load="emit('load-history')"
          @view-report="(id: string) => emit('view-report', id)"
          @update:page="(p: number) => { emit('update:executionHistoryPage', p); emit('load-history') }"
        />
      </div>

      </div>

      <div class="scene-editor__nav-spacer" />

      <!-- 环境选择：无边框 + 主色文字 + 不可清除（交互 10 §1.1），只覆盖组件暴露的 --el-* 变量 -->
      <el-select
        :model-value="editEnvironmentId"
        class="scene-editor__nav-env"
        placeholder="默认环境"
        style="width: 140px"
        @update:model-value="emit('update:editEnvironmentId', $event)"
      >
        <el-option v-for="env in environmentOptions" :key="env.id" :value="env.id" :label="env.name" />
      </el-select>
    </template>

    <!-- 执行入口（交互 10 §1.9）：只读形态为页面唯一动作且实心主色（对齐 demo）；仓库不可达时置灰并提示原因 -->
    <template v-if="readonlyMode">
      <el-tooltip :content="`仓库不可达，无法执行`" :disabled="!repoUnreachable" placement="bottom">
        <span class="scene-editor__run-wrap">
          <el-button type="primary" :disabled="repoUnreachable" :loading="running" @click="emit('run')">{{ runLabel }}</el-button>
        </span>
      </el-tooltip>
    </template>
    <template v-else-if="isCreateMode">
      <div class="scene-editor__nav-actions">
        <el-tooltip :content="`仓库不可达，无法执行`" :disabled="!repoUnreachable" placement="bottom">
          <span class="scene-editor__run-wrap">
            <el-button link type="primary" :disabled="repoUnreachable" :loading="running" @click="emit('run')">{{ runLabel }}</el-button>
          </span>
        </el-tooltip>
        <el-button link @click="emit('save', 'draft')">保存</el-button>
        <el-button link type="primary" :loading="saving" @click="emit('save', 'published')">发布</el-button>
      </div>
    </template>
    <template v-else>
      <div class="scene-editor__nav-actions">
        <el-tooltip :content="`仓库不可达，无法执行`" :disabled="!repoUnreachable" placement="bottom">
          <span class="scene-editor__run-wrap">
            <el-button link type="primary" :disabled="repoUnreachable" :loading="running" @click="emit('run')">{{ runLabel }}</el-button>
          </span>
        </el-tooltip>
        <el-button link @click="emit('save', 'draft')">保存</el-button>
        <el-button link type="primary" :loading="saving" @click="emit('save', 'published')">发布</el-button>
        <el-button link type="danger" @click="emit('delete')">删除</el-button>
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
// 顶部导航：按内容排布、允许换行；自身不设下内边距，与页签条的距离交给外层 gap 控制（交互 10 §1.1）
.scene-editor__nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  padding-bottom: 0;
}

// 只读形态整体卡片化（对齐 demo .card.ed-nav：白底 + 1px 描边 + 圆角 + 浅阴影，内距 10/16）
.scene-editor__nav.is-card {
  padding: 10px var(--space-lg);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);

  // 只读页文本一律不加粗：场景名、状态与来源徽标、运行状态统一常规字重
  .scene-editor__readonly-name,
  .scene-editor__status-tag,
  .scene-editor__git-badge,
  .scene-editor__source-status,
  .scene-editor__ci-run {
    font-weight: 400;
  }
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

// 输入类控件定宽：给头部一个稳定的节奏，窄视口下允许换行（交互 10 §1.1）
.scene-editor__nav-name-input {
  width: 180px;
}

.scene-editor__nav-module {
  width: 200px;
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

// 环境选择：去边框、选中值取主色；聚焦仍由组件默认的主色内描边提示
.scene-editor__nav-env {
  --el-border-color: transparent;
  --el-border-color-hover: transparent;
  --el-text-color-regular: var(--el-color-primary);
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

// 优先级触发器：配色块 + 下拉箭头，提示可展开（交互 10 §1.1）
.scene-editor__priority-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0 8px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  color: var(--color-neutral-800);
  cursor: pointer;
}

.scene-editor__priority-caret {
  font-size: 10px;
  opacity: 0.75;
}

// 优先级下拉：行式菜单，悬停浅底、选中项主色高亮（交互 10 §1.1）
.scene-editor__priority-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
}

.scene-editor__priority-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--color-neutral-50);
  }

  &.is-active {
    background: var(--color-primary-50);
    color: var(--color-primary-500);
    font-weight: 600;
  }
}

.scene-editor__priority-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

// 只读头部（对齐 demo api-scenes.html edit-ci 的单行结构）
.scene-editor__readonly-name {
  font-weight: 600;
}

// 模块名：紧跟场景名的次级说明
.scene-editor__readonly-module {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

// 运行状态徽标：待执行 / 执行中 / 执行成功 / 执行失败（demo ci-run 四态）
.scene-editor__ci-run {
  font-size: var(--font-size-xs);
  font-weight: 600;

  &.is-idle { color: var(--color-neutral-400); }
  &.is-running { color: var(--color-warning-strong); }
  &.is-success { color: var(--color-success-strong); }
  &.is-failed { color: var(--el-color-danger); }
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

// [运行] 与 [保存] 之间补间距：运行按钮外包 span，拿不到 el-button 兄弟选择器的默认外边距
.scene-editor__nav-actions .scene-editor__run-wrap {
  margin-right: var(--space-sm);
}
</style>
