<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useBugCreate } from '@/composables/project/bug/useBugCreate'
import { useBugCreateSuggest } from '@/composables/project/bug/useBugCreateSuggest'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import BugSuggestBadge from '@/components/project/bug/BugSuggestBadge.vue'
import AssigneeCandidateChips from '@/components/project/bug/AssigneeCandidateChips.vue'
import DuplicateCheckPanel from '@/components/project/bug/DuplicateCheckPanel.vue'
import { fetchAiStatus } from '@/services/ai'

const {
  formRef,
  submitting,
  form,
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
  suggestions,
  assigneeCandidates,
  requestSuggestion,
  dispose: disposeSuggest,
} = useBugCreateSuggest()

/** 标题与重现步骤填写后点击获取建议（草稿模式详设 3.5，不落库） */
async function handleRequestSuggest(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写缺陷标题')
    return
  }
  await requestSuggestion({
    title: form.title.trim(),
    steps: form.reproSteps.trim() || undefined,
    moduleId: form.moduleId || undefined,
  })
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

function adoptAssignee(userId: string): void {
  form.assigneeId = userId
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

onBeforeUnmount(() => {
  disposeSuggest()
})
</script>

<template>
  <div class="bug-create">
    <el-page-header @back="router.push('/workspace/projects/bugs')">
      <template #content><span class="bug-create__title">提交缺陷</span></template>
    </el-page-header>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="bug-create__form">
      <div class="bug-create__layout">
        <div class="bug-create__main">
          <el-card shadow="never">
            <template #header>
              <div class="bug-create__section-row">
                <span class="bug-create__section">基本信息</span>
                <!-- 草稿建议入口（详设 3.5）：AI 未启用时隐藏 -->
                <el-button
                  v-if="aiAvailable"
                  size="small"
                  :loading="suggestLoading"
                  @click="handleRequestSuggest"
                >
                  <el-icon><MagicStick /></el-icon>获取 AI 建议
                </el-button>
              </div>
            </template>
            <el-alert
              v-if="suggestError"
              type="warning"
              :title="suggestError"
              show-icon
              :closable="false"
              class="bug-create__suggest-error"
            />
            <el-form-item label="标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="用一句话描述缺陷现象"
                maxlength="300"
                show-word-limit
                size="large"
              />
            </el-form-item>
            <el-form-item label="重现步骤" class="bug-create__repro">
              <MarkdownEditor v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown，可选）" />
            </el-form-item>
            <!-- 录入时重复检测（交互 3.1）：仅作建议，不自动合并、不改状态 -->
            <DuplicateCheckPanel
              :title="form.title"
              :steps="form.reproSteps"
              :ai-available="aiAvailable"
            />
          </el-card>

          <el-card shadow="never">
            <template #header><span class="bug-create__section">附件</span></template>
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
          </el-card>
        </div>

        <div class="bug-create__side">
          <el-card shadow="never">
            <template #header><span class="bug-create__section">属性</span></template>
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
              <el-select v-model="form.severity">
                <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key">
                  <span class="bug-create__severity-dot" :class="`bug-create__severity-dot--${key}`" />{{ label }}
                </el-option>
              </el-select>
              <BugSuggestBadge
                :model-value="form.severity"
                :label="'等级'"
                :suggestion="suggestions.severity"
                @adopt="adoptSuggestion('severity', $event)"
              />
            </el-form-item>
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority">
                <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
              </el-select>
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
            <template #header><span class="bug-create__section">指派与关联</span></template>
            <el-form-item label="指派给" prop="assigneeId">
              <el-select v-model="form.assigneeId" filterable placeholder="选择处理人">
                <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name || m.username" :value="m.userId" />
              </el-select>
              <!-- 指派候选：悬浮理由，点击采纳（交互 2.2） -->
              <AssigneeCandidateChips
                :candidates="assigneeCandidates"
                :selected-id="form.assigneeId"
                @adopt="adoptAssignee"
              />
            </el-form-item>
            <el-form-item label="关联用例">
              <el-button @click="caseSelectorVisible = true">选择用例</el-button>
              <span v-if="selectedCaseTitle" class="bug-create__hint">{{ selectedCaseTitle }}</span>
            </el-form-item>
            <el-form-item label="关联计划">
              <el-select v-model="form.relatedPlanId" filterable clearable placeholder="选择计划（可选）">
                <el-option v-for="p in planOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-card>
        </div>
      </div>

      <div class="bug-create__footer">
        <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交缺陷</el-button>
      </div>
    </el-form>

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />
  </div>
</template>

<style scoped lang="scss">
.bug-create__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.bug-create__form {
  margin-top: var(--space-lg);
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

.bug-create__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}

.bug-create__section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bug-create__suggest-error {
  margin-bottom: var(--space-md);
}

.bug-create__form :deep(.el-select),
.bug-create__form :deep(.el-tree-select) {
  width: 100%;
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

.bug-create__upload-icon {
  font-size: 40px;
  color: var(--color-neutral-300);
  margin-bottom: var(--space-sm);
}

.bug-create__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-create__severity-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: var(--space-sm);
  vertical-align: middle;

  &--fatal { background: var(--color-bug-fatal); }
  &--serious { background: var(--color-bug-serious); }
  &--general { background: var(--color-bug-general); }
  &--minor { background: var(--color-bug-minor); }
}

.bug-create__footer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: transparent;
  pointer-events: none;

  .el-button {
    pointer-events: auto;
    box-shadow: var(--shadow-md);
  }
}
</style>
