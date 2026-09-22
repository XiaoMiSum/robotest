<script setup lang="ts">
import { useBugCreate } from '@/composables/useBugCreate'
import CaseSelector from '@/components/project/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import BugAiSuggest from '@/components/project/BugAiSuggest.vue'

const {
  aiEnabled,
  formRef,
  submitting,
  aiSuggestRef,
  dedupItems,
  dedupConfirmVisible,
  dedupSubmitting,
  dedupTargetId,
  applyTitle,
  applySeverity,
  applyPriority,
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
  handleSelectDuplicate,
  handleAbandonSubmit,
  handleSubmit,
  handleDedupAbandon,
  handleDedupContinue,
  handleDedupMarkDuplicate,
  router,
  severityLabel,
  priorityLabel,
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
} = useBugCreate()
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
            <template #header><span class="bug-create__section">基本信息</span></template>
            <el-form-item label="标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="用一句话描述缺陷现象"
                maxlength="300"
                show-word-limit
                size="large"
              >
                <template #append>
                  <el-button
                    v-if="aiEnabled"
                    class="bug-create__ai-append"
                    :loading="aiSuggestRef?.loading"
                    :disabled="!form.title.trim()"
                    @click="aiSuggestRef?.requestSuggestion()"
                  >
                    <el-icon><MagicStick /></el-icon>AI 建议
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
            <BugAiSuggest
              v-if="aiEnabled"
              ref="aiSuggestRef"
              :title="form.title"
              :repro-steps="form.reproSteps"
              @apply-title="applyTitle"
              @apply-severity="applySeverity"
              @apply-priority="applyPriority"
              @dedup-change="dedupItems = $event"
              @select-duplicate="handleSelectDuplicate"
              @abandon-submit="handleAbandonSubmit"
            />
            <el-form-item label="重现步骤" class="bug-create__repro">
              <MarkdownEditor v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown，可选）" />
            </el-form-item>
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
              <el-select v-model="form.severity">
                <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key">
                  <span class="bug-create__severity-dot" :class="`bug-create__severity-dot--${key}`" />{{ label }}
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority">
                <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
              </el-select>
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
            <template #header><span class="bug-create__section">指派与关联</span></template>
            <el-form-item label="指派给" prop="assigneeId">
              <el-select v-model="form.assigneeId" filterable placeholder="选择处理人">
                <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
              </el-select>
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

    <el-dialog
      v-model="dedupConfirmVisible"
      title="检测到疑似重复缺陷"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="!dedupSubmitting"
      append-to-body
    >
      <div class="bug-create__dedup-confirm-tip">
        以下缺陷与您提交的内容可能存在重复，请选择处理方式；标记重复需先选择对应的原始缺陷：
      </div>
      <el-radio-group v-model="dedupTargetId" class="bug-create__dedup-confirm-list">
        <el-radio
          v-for="item in dedupItems"
          :key="item.bugId"
          :value="item.bugId"
          class="bug-create__dedup-confirm-item"
        >
          <span v-if="item.similarity !== null" class="bug-create__dedup-confirm-sim">
            {{ Math.round(item.similarity * 100) }}%
          </span>
          <span class="bug-create__dedup-confirm-title">{{ item.title }}</span>
          <el-tag :type="BUG_STATUS_TAG_TYPE[item.status]" size="small" effect="light" round>
            {{ BUG_STATUS_LABEL[item.status] }}
          </el-tag>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button :disabled="dedupSubmitting" @click="handleDedupAbandon">放弃提交</el-button>
        <el-button :loading="dedupSubmitting" @click="handleDedupContinue">继续提交</el-button>
        <el-button type="primary" :loading="dedupSubmitting" @click="handleDedupMarkDuplicate">
          继续并标记重复
        </el-button>
      </template>
    </el-dialog>
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

.bug-create__form :deep(.el-select),
.bug-create__form :deep(.el-tree-select) {
  width: 100%;
}

.bug-create__form :deep(.bug-create__date) {
  width: 100%;
  --el-date-editor-width: 100%;
}

.bug-create__form :deep(.el-input-group--append .el-input__wrapper) {
  border-top-right-radius: 0 !important;
  border-bottom-right-radius: 0 !important;
}

.bug-create__form :deep(.el-input-group__append) {
  padding: 0;
  background: transparent;
  border-left: 1px solid var(--color-neutral-200);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  overflow: hidden;
}

.bug-create__form :deep(.bug-create__ai-append) {
  height: 100%;
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--color-success);
  font-weight: 600;

  &.is-disabled {
    background: transparent;
    color: var(--color-neutral-400);
  }

  &:hover {
    background: transparent;
    color: var(--color-success);
  }

  &:not(.is-disabled):not(.is-loading) {
    animation: bug-create-ai-glow 2s ease-in-out infinite;

    .el-icon {
      animation: bug-create-ai-wiggle 2s ease-in-out infinite;
    }
  }
}

@keyframes bug-create-ai-glow {
  0%,
  100% {
    box-shadow: inset 0 0 0 0 rgba(34, 197, 94, 0);
  }

  50% {
    box-shadow: inset 0 0 10px 2px rgba(34, 197, 94, 0.35);
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

.bug-create__dedup-confirm-tip {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-600);
  margin-bottom: var(--space-sm);
}

.bug-create__dedup-confirm-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  width: 100%;
}

.bug-create__dedup-confirm-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: var(--space-xs);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);

  :deep(.el-radio__label) {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    flex: 1;
    min-width: 0;
  }
}

.bug-create__dedup-confirm-sim {
  flex-shrink: 0;
  font-size: var(--font-size-2xs);
  font-weight: 700;
  color: var(--color-warning);
}

.bug-create__dedup-confirm-title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
