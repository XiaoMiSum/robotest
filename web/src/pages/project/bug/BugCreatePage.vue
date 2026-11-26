<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useBugCreate } from '@/composables/project/bug/useBugCreate'
import { useBugCreateSuggest } from '@/composables/project/bug/useBugCreateSuggest'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import BugAiSuggestionDialog from '@/components/project/bug/BugAiSuggestionDialog.vue'
import BugDuplicateSuggestList from '@/components/project/bug/BugDuplicateSuggestList.vue'
import { fetchAiStatus } from '@/services/ai'
import aiIcon from '@/assets/icon/ai.svg'
import type { BugPriority, BugSeverity, ProjectModule } from '@/types'

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

// ==================== AI 建议（含重复缺陷检测，交互 2.2） ====================

const aiAvailable = ref(false)
void fetchAiStatus()
  .then((status) => {
    aiAvailable.value = status.available
  })
  .catch(() => {
    aiAvailable.value = false
  })

const suggestDialogVisible = ref(false)
const originalValues = ref<Record<string, string>>({})
const {
  loading: suggestLoading,
  error: suggestError,
  hasResult: suggestHasResult,
  titleSuggestion,
  reproStepsSuggestion,
  suggestions,
  relatedCase,
  duplicates,
  requestSuggestion,
} = useBugCreateSuggest()

function moduleName(id: string, nodes: ProjectModule[]): string {
  for (const node of nodes) {
    if (node.id === id) return node.name
    const nested = moduleName(id, node.children ?? [])
    if (nested) return nested
  }
  return ''
}

function captureOriginalValues(): Record<string, string> {
  return {
    title: form.title,
    reproSteps: normalizedReproSteps.value || '（未填写）',
    bugType: BUG_TYPE_LABEL[form.bugType],
    severity: severityLabel[form.severity],
    priority: priorityLabel[form.priority],
    moduleId: form.moduleId ? moduleName(form.moduleId, moduleTree.value) || form.moduleId : '（未选择）',
    keywords: form.keywords || '（未填写）',
    relatedCase: selectedCaseTitle.value || '未关联',
  }
}

/** 默认采纳只覆盖产物提供的字段，不改动用户已填但未被建议的字段 */
function applySuggestionToForm(): void {
  if (typeof titleSuggestion.value?.value === 'string') form.title = titleSuggestion.value.value
  if (typeof reproStepsSuggestion.value?.value === 'string') form.reproSteps = reproStepsSuggestion.value.value
  if (suggestions.value.bugType) form.bugType = suggestions.value.bugType.value
  if (suggestions.value.severity) form.severity = suggestions.value.severity.value
  if (suggestions.value.priority) form.priority = suggestions.value.priority.value
  if (typeof suggestions.value.moduleId?.value === 'string') form.moduleId = suggestions.value.moduleId.value
  if (suggestions.value.keywords?.value.length) form.keywords = suggestions.value.keywords.value.join(' ')
  if (relatedCase.value) {
    form.relatedCaseId = relatedCase.value.value
    selectedCaseTitle.value = relatedCase.value.caseTitle || '已选 1 个用例'
  }
  ElMessage.success('建议已默认采纳并填入表单')
}

async function handleRequestSuggest(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写缺陷标题')
    return
  }
  suggestDialogVisible.value = true
  if (suggestLoading.value || suggestHasResult.value) return
  originalValues.value = captureOriginalValues()
  await requestSuggestion({
    title: form.title.trim(),
    steps: normalizedReproSteps.value || undefined,
    moduleId: form.moduleId || undefined,
  })
  if (!suggestError.value && suggestHasResult.value) applySuggestionToForm()
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
          <el-form-item prop="title">
            <el-input
              v-model="form.title"
              placeholder="用一句话描述缺陷现象"
              maxlength="300"
              show-word-limit
              size="large"
              :class="{ 'bug-create__title-input--noai': !aiAvailable }"
            >
              <template #append>
                <el-button
                  v-if="aiAvailable"
                  class="bug-create__ai-append"
                  title="AI 建议"
                  aria-label="AI 建议"
                  @click="handleRequestSuggest"
                >
                  <img :src="aiIcon" alt="" class="bug-create__ai-icon" />
                </el-button>
              </template>
            </el-input>
            <BugDuplicateSuggestList
              v-if="suggestHasResult"
              class="bug-create__duplicate"
              :items="duplicates"
            />
          </el-form-item>
          <el-form-item class="bug-create__repro">
            <MarkdownEditor v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown，可选）" maximizable />
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
            </el-form-item>
          </el-card>
          <el-card shadow="never">
            <el-form-item label="指派给" prop="assigneeId">
              <el-select v-model="form.assigneeId" filterable placeholder="选择处理人">
                <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name || m.username" :value="m.userId" />
              </el-select>
            </el-form-item>
            <el-form-item label="关联用例">
              <div class="bug-create__case">
                <span class="bug-create__case-state">{{ selectedCaseTitle || '未关联' }}</span>
                <el-button link type="primary" @click="caseSelectorVisible = true">选择用例</el-button>
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

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />
    <BugAiSuggestionDialog
      v-model="suggestDialogVisible"
      :loading="suggestLoading"
      :error="suggestError"
      :title-suggestion="titleSuggestion"
      :repro-steps-suggestion="reproStepsSuggestion"
      :suggestions="suggestions"
      :original-values="originalValues"
    />
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

.bug-create__duplicate {
  margin-top: var(--space-xs);
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

// 按钮与输入栏一体化：透明底、成功绿表达「可采纳的建议」
.bug-create__form :deep(.bug-create__ai-append) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 100%;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--color-success);
  animation: bug-create-ai-glow 2s ease-in-out infinite;

  // 悬停维持与输入栏一体，不做 EP 默认底色
  &:hover {
    background: transparent;
    color: var(--color-success);
  }

  .bug-create__ai-icon {
    width: 16px;
    height: 16px;
    animation: bug-create-ai-wiggle 2s ease-in-out infinite;
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
