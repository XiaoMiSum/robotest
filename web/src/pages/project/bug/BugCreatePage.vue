<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useBugCreate } from '@/composables/project/bug/useBugCreate'
import { useBugCreateSuggest } from '@/composables/project/bug/useBugCreateSuggest'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import BugSuggestBadge from '@/components/project/bug/BugSuggestBadge.vue'
import BugDuplicateDialog from '@/components/project/bug/BugDuplicateDialog.vue'
import { fetchAiStatus } from '@/services/ai'
import type { BugPriority, BugSeverity, BugRelatedCaseSuggestion } from '@/types'

const {
  formRef,
  submitting,
  form,
  normalizedReproSteps,
  moduleTree,
  caseSelectorVisible,
  selectedCaseTitle,
  handleCaseSelected,
  planOptions,
  rules,
  memberOptions,
  attachmentFiles,
  handleAttachmentChange,
  handleAttachmentRemove,
  handleSubmit,
  router,
  severityLabel,
  priorityLabel,
  BUG_TYPE_LABEL,
} = useBugCreate()

// ==================== AI 建议与录入检测（交互 2.2 / 3.1） ====================

const aiAvailable = ref(false)
void fetchAiStatus()
  .then((status) => {
    aiAvailable.value = status.available
  })
  .catch(() => {
    // 状态查询失败按不可用降级，不阻塞录入
    aiAvailable.value = false
  })

const {
  loading: suggestLoading,
  error: suggestError,
  titleSuggestion,
  reproStepsSuggestion,
  suggestions,
  relatedCase,
  requestSuggestion,
} = useBugCreateSuggest()

/** 标题填写后点击获取实时建议（详设 3.5，同步一次返回全部建议，不落库） */
async function handleRequestSuggest(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写缺陷标题')
    return
  }
  await requestSuggestion({
    title: form.title.trim(),
    // 默认模板未改动等同未填，不送建议，避免模板文本稀释语义
    steps: normalizedReproSteps.value || undefined,
    moduleId: form.moduleId || undefined,
  })
}

/** 选中态色系令牌：严重等级取缺陷等级色（视觉规范 4.3），与列表/详情标签同源 */
const SEVERITY_COLOR: Record<BugSeverity, string> = {
  fatal: 'var(--color-bug-fatal)',
  serious: 'var(--color-bug-serious)',
  general: 'var(--color-bug-general)',
  minor: 'var(--color-bug-minor)',
}

/** 优先级取优先级色 P1~P3（视觉规范 4.2），与列表/详情标签色一致 */
const PRIORITY_COLOR: Record<BugPriority, string> = {
  high: 'var(--color-priority-p1)',
  medium: 'var(--color-priority-p2)',
  low: 'var(--color-priority-p3)',
}

/** 分段按钮选中态颜色按色系令牌注入（EP 无逐按钮配置 prop，走组件级 CSS 变量，同需求新建页范式） */
function severityButtonStyle(value: BugSeverity): Record<string, string> {
  return {
    '--el-radio-button-checked-bg-color': SEVERITY_COLOR[value],
    '--el-radio-button-checked-border-color': SEVERITY_COLOR[value],
  }
}

/** 同上：优先级分段按钮选中态 */
function priorityButtonStyle(value: BugPriority): Record<string, string> {
  return {
    '--el-radio-button-checked-bg-color': PRIORITY_COLOR[value],
    '--el-radio-button-checked-border-color': PRIORITY_COLOR[value],
  }
}

/** 用户已填值与建议不一致时以用户值为准，徽标点击才采纳（交互 2.2） */
function adoptSuggestion(field: 'bugType' | 'severity' | 'priority' | 'moduleId', value: string): void {
  if (field === 'moduleId') form.moduleId = value
  else if (field === 'bugType') form.bugType = value as typeof form.bugType
  else if (field === 'severity') form.severity = value as typeof form.severity
  else form.priority = value as typeof form.priority
}

function adoptKeywords(value: string[]): void {
  form.keywords = value.join(' ')
}

/** 标题 / 重现步骤 / 关联用例经点击采纳后写入，属用户主动动作（交互 2.2） */
function adoptTitle(value: string): void {
  form.title = value
}

function adoptReproSteps(value: string): void {
  form.reproSteps = value
}

function adoptRelatedCase(suggestion: BugRelatedCaseSuggestion): void {
  form.relatedCaseId = suggestion.value
  selectedCaseTitle.value = suggestion.caseTitle
}

/** 关键词建议为字符串数组，展示时以空格连接（采纳即写入关键词字段） */
const keywordsSuggestion = computed(() =>
  suggestions.value.keywords
    ? { value: suggestions.value.keywords.value.join(' '), reason: suggestions.value.keywords.reason }
    : null,
)

/** moduleId 建议值可为 null（未指定），徽标只在有明确建议时展示 */
const moduleIdSuggestion = computed(() => {
  const suggestion = suggestions.value.moduleId
  return suggestion && suggestion.value ? { value: suggestion.value, reason: suggestion.reason } : null
})

/** 关联用例建议展示文本取用例标题（写入值为用例 id） */
const relatedCaseSuggestion = computed(() =>
  relatedCase.value
    ? { value: relatedCase.value.value, reason: relatedCase.value.reason }
    : null,
)

// ==================== 相似缺陷检测（交互 3.1） ====================

const duplicateVisible = ref(false)
const duplicateCheckRef = ref<InstanceType<typeof BugDuplicateDialog> | null>(null)

/** 标题失焦触发（交互 3.1）：无命中或功能不可用由组件静默处理，不弹窗不提示 */
function handleTitleBlur(): void {
  void duplicateCheckRef.value?.check()
}
</script>

<template>
  <div class="bug-create">
    <!-- UI-BC-01：两级面包屑（缺陷管理 › 提交缺陷），回退入口是唯一可点击项；操作区同行右对齐 -->
    <div class="bug-create__header">
      <div class="bug-create__breadcrumb">
        <router-link to="/workspace/projects/bugs">缺陷管理</router-link>
        <el-icon :size="13"><ArrowRight /></el-icon>
        <span>提交缺陷</span>
      </div>
      <div class="bug-create__actions">
        <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交缺陷</el-button>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="bug-create__form">
      <div class="bug-create__layout">
        <!-- 主列分区不渲染标题、不套卡片（详设 1.14），靠间距区分 -->
        <div class="bug-create__main">
          <el-alert
            v-if="suggestError"
            type="warning"
            :title="suggestError"
            show-icon
            :closable="false"
            class="bug-create__suggest-error"
          />
          <el-form-item prop="title">
            <el-input
              v-model="form.title"
              placeholder="用一句话描述缺陷现象"
              maxlength="300"
              show-word-limit
              size="large"
              :class="{ 'bug-create__title-input--noai': !aiAvailable }"
              @blur="handleTitleBlur"
            >
              <!-- 草稿建议入口（详设 3.5）：AI 未启用时挂载点隐藏，输入组按无 append 形态收口 -->
              <template #append>
                <el-button
                  v-if="aiAvailable"
                  class="bug-create__ai-append"
                  :loading="suggestLoading"
                  :disabled="!form.title.trim()"
                  @click="handleRequestSuggest"
                >
                  <el-icon><MagicStick /></el-icon>AI 建议
                </el-button>
              </template>
            </el-input>
            <!-- 标题优化建议：点击采纳覆盖标题（用户主动动作，交互 2.2） -->
            <BugSuggestBadge
              class="bug-create__suggest-badge"
              :model-value="form.title"
              label="标题"
              :suggestion="titleSuggestion"
              @adopt="adoptTitle"
            />
          </el-form-item>
          <el-form-item class="bug-create__repro">
            <MarkdownEditor v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown，可选）" maximizable />
            <!-- 重现步骤建议（含实际结果与期望结果）：点击采纳写入编辑器 -->
            <BugSuggestBadge
              class="bug-create__suggest-badge"
              :model-value="normalizedReproSteps"
              label="重现步骤"
              :suggestion="reproStepsSuggestion"
              display-text="执行步骤 / 实际结果 / 期望结果"
              @adopt="adoptReproSteps"
            />
          </el-form-item>
          <!-- UI-DS-09 例外（已确认）：拖拽区默认 40px 纵向内边距 + 大图标撑高，按需求压到约一半 -->
          <el-upload
            drag
            :auto-upload="false"
            :file-list="attachmentFiles"
            multiple
            :on-change="handleAttachmentChange"
            :on-remove="handleAttachmentRemove"
          >
            <el-icon class="bug-create__upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="bug-create__hint">单个文件不超过 10MB，创建后自动上传</div>
            </template>
          </el-upload>
        </div>

        <div class="bug-create__side">
          <!-- 侧栏分区保留卡片容器、只去卡片 header（不渲染分区标题，详设 1.14） -->
          <el-card shadow="never">
            <el-form-item label="缺陷类型" prop="bugType">
              <el-select v-model="form.bugType">
                <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
              <BugSuggestBadge
                :model-value="form.bugType"
                :label="'类型'"
                :suggestion="suggestions.bugType"
                @adopt="adoptSuggestion('bugType', $event)"
              />
            </el-form-item>
            <el-form-item label="所属模块">
              <el-tree-select
                v-model="form.moduleId"
                :data="moduleTree"
                :props="{ label: 'name', children: 'children' }"
                node-key="id"
                check-strictly
                clearable
                placeholder="选择所属模块（可选）"
              />
              <BugSuggestBadge
                :model-value="form.moduleId"
                :label="'模块'"
                :suggestion="moduleIdSuggestion"
                :adopt-value="moduleIdSuggestion?.value ?? ''"
                @adopt="adoptSuggestion('moduleId', $event)"
              />
            </el-form-item>
            <el-form-item label="严重等级" prop="severity">
              <el-radio-group v-model="form.severity">
                <el-radio-button
                  v-for="(label, key) in severityLabel"
                  :key="key"
                  :value="key"
                  :style="severityButtonStyle(key as BugSeverity)"
                >{{ label }}</el-radio-button>
              </el-radio-group>
              <BugSuggestBadge
                :model-value="form.severity"
                :label="'等级'"
                :suggestion="suggestions.severity"
                @adopt="adoptSuggestion('severity', $event)"
              />
            </el-form-item>
            <el-form-item label="优先级" prop="priority">
              <el-radio-group v-model="form.priority">
                <el-radio-button
                  v-for="(label, key) in priorityLabel"
                  :key="key"
                  :value="key"
                  :style="priorityButtonStyle(key as BugPriority)"
                >{{ label }}</el-radio-button>
              </el-radio-group>
              <BugSuggestBadge
                :model-value="form.priority"
                :label="'优先级'"
                :suggestion="suggestions.priority"
                @adopt="adoptSuggestion('priority', $event)"
              />
            </el-form-item>
            <el-form-item label="截止日期">
              <el-date-picker
                v-model="form.dueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择截止日期（可选）"
                class="bug-create__date"
              />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="form.keywords" placeholder="多个关键词用空格分隔（可选）" maxlength="255" />
              <BugSuggestBadge
                :model-value="form.keywords"
                :label="'关键词'"
                :suggestion="keywordsSuggestion"
                @adopt="adoptKeywords($event.split(' '))"
              />
            </el-form-item>
          </el-card>
          <el-card shadow="never">
            <el-form-item label="指派给" prop="assigneeId">
              <el-select v-model="form.assigneeId" filterable placeholder="选择处理人">
                <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name || m.username" :value="m.userId" />
              </el-select>
            </el-form-item>
            <el-form-item label="关联用例">
              <!-- 左状态右操作，与详情侧栏同构：空态占位避免行内控件随文案宽度漂移 -->
              <div class="bug-create__case">
                <span class="bug-create__case-state">{{ selectedCaseTitle || '未关联' }}</span>
                <el-button link type="primary" @click="caseSelectorVisible = true">选择用例</el-button>
                <!-- 关联用例建议：点击采纳写入关联用例（交互 2.2） -->
                <BugSuggestBadge
                  :model-value="form.relatedCaseId"
                  label="关联用例"
                  :suggestion="relatedCaseSuggestion"
                  :adopt-value="relatedCase?.value ?? ''"
                  :display-text="relatedCase?.caseTitle"
                  @adopt="relatedCase && adoptRelatedCase(relatedCase)"
                />
              </div>
            </el-form-item>
            <el-form-item label="关联计划">
              <el-select v-model="form.relatedPlanId" filterable clearable placeholder="选择计划（可选）">
                <el-option v-for="p in planOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-card>
        </div>
      </div>
    </el-form>

    <!-- 相似缺陷检测（交互 3.1）：标题失焦触发，命中弹窗展示，无命中或功能不可用静默 -->
    <BugDuplicateDialog
      ref="duplicateCheckRef"
      v-model="duplicateVisible"
      :title="form.title"
      :steps="normalizedReproSteps"
      :ai-available="aiAvailable"
    />

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />
  </div>
</template>

<style scoped lang="scss">
.bug-create__header {
  position: sticky;
  top: calc(-1 * var(--page-pad));
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-md);
  background: var(--color-neutral-0);
  border-bottom: 1px solid var(--color-neutral-200);
}

// UI-BC-02～04：面包屑排版与文字层级基准；底距与操作区对齐，避免行内错位
.bug-create__breadcrumb,
.bug-create__actions {
  margin-bottom: var(--space-sm);
}

.bug-create__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);

  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;
  }

  a:hover {
    color: var(--color-primary-600);
  }

  .el-icon {
    color: var(--color-neutral-400);
  }

  > :last-child {
    color: var(--color-neutral-900);
  }
}

.bug-create__form {
  margin-top: var(--space-md);
}

.bug-create__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-lg);
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.bug-create__main,
.bug-create__side {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.bug-create__suggest-error {
  margin-bottom: var(--space-md);
}

// 实时建议徽标换行挂在输入控件下方：去左缩进、补上间距
.bug-create__suggest-badge {
  margin-left: 0;
  margin-top: var(--space-xs);
}

.bug-create__form :deep(.el-select),
.bug-create__form :deep(.el-tree-select) {
  width: 100%;
}

// 单选组不压缩换行，窄侧栏下严重等级四项按行内回流
.bug-create__form :deep(.el-radio-group) {
  flex-wrap: wrap;
}

.bug-create__form :deep(.bug-create__date) {
  width: 100%;
  --el-date-editor-width: 100%;
}

.bug-create__form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--color-neutral-600);
  margin-bottom: var(--space-xs);
}

.bug-create__form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.bug-create__repro :deep(.md-editor) {
  width: 100%;
  border-radius: var(--radius-md);
}

// 标题输入组与 AI 按钮衔接：wrapper 右端去圆角交给 append 收口，append 去底色仅留 1px 分隔
.bug-create__form :deep(.el-input-group--append .el-input__wrapper) {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.bug-create__form :deep(.el-input-group__append) {
  padding: 0;
  background: transparent;
  border-left: 1px solid var(--color-neutral-200);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  overflow: hidden;
}

// AI 未启用：append 无按钮可渲染，隐藏空挂载点并把右端圆角交还输入框（等价无 append 形态）
.bug-create__form :deep(.bug-create__title-input--noai .el-input-group__append) {
  display: none;
}

.bug-create__form :deep(.bug-create__title-input--noai .el-input__wrapper) {
  border-top-right-radius: var(--radius-md);
  border-bottom-right-radius: var(--radius-md);
}

// 按钮与输入栏一体化：透明底、成功绿表达「可采纳的建议」，禁用态灰显
.bug-create__form :deep(.bug-create__ai-append) {
  height: 100%;
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--color-success);

  &.is-disabled,
  &:disabled {
    background: transparent;
    color: var(--color-neutral-400);
  }

  // 悬停维持与输入栏一体，不做 EP 默认底色
  &:hover:not(:disabled):not(.is-disabled) {
    background: transparent;
    color: var(--color-success);
  }

  // 填入标题后（可操作态）：呼吸光晕 + 魔棒微晃引导点击；禁用与 loading 态停用
  &:not(:disabled):not(.is-disabled):not(.is-loading) {
    animation: bug-create-ai-glow 2s ease-in-out infinite;

    .el-icon {
      animation: bug-create-ai-wiggle 2s ease-in-out infinite;
    }
  }
}

@keyframes bug-create-ai-glow {
  0%,
  100% {
    box-shadow: inset 0 0 0 0 color-mix(in srgb, var(--color-success) 0%, transparent);
  }

  50% {
    box-shadow: inset 0 0 10px 2px color-mix(in srgb, var(--color-success) 35%, transparent);
  }
}

@keyframes bug-create-ai-wiggle {
  0%,
  100% {
    transform: rotate(0deg);
  }

  25% {
    transform: rotate(-10deg);
  }

  75% {
    transform: rotate(10deg);
  }
}

// UI-DS-09 例外（已确认）：拖拽区默认纵向内边距 40px + 40px 图标，压到约一半高度
.bug-create__form :deep(.el-upload-dragger) {
  --el-upload-dragger-padding-vertical: var(--space-md);
  --el-upload-dragger-padding-horizontal: var(--space-lg);
}

.bug-create__upload-icon {
  font-size: var(--font-size-2xl);
  color: var(--color-neutral-300);
  margin-bottom: var(--space-xs);
}

.bug-create__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-create__case {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  width: 100%;
}

.bug-create__case-state {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  line-height: 1.6;
}
</style>
