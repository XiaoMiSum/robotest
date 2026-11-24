<script setup lang="ts">
import { computed } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import BugDuplicateSuggestList from './BugDuplicateSuggestList.vue'
import type { BugDraftSuggestFields, BugDuplicateSuggestItem, BugSuggestionField } from '@/types'

interface SuggestionRow {
  key: string
  label: string
  suggestion: BugSuggestionField<string | string[]> | null
  displayValue: string
  adoptValue: string | string[]
}

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  error: string
  titleSuggestion: BugSuggestionField<string> | null
  reproStepsSuggestion: BugSuggestionField<string> | null
  suggestions: BugDraftSuggestFields
  duplicates: BugDuplicateSuggestItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  adopt: [payload: { key: string; value: string | string[]; caseTitle?: string }]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const rows = computed<SuggestionRow[]>(() => {
  const list: SuggestionRow[] = []
  if (props.titleSuggestion?.value) {
    list.push({
      key: 'title',
      label: '标题',
      suggestion: props.titleSuggestion,
      displayValue: props.titleSuggestion.value,
      adoptValue: props.titleSuggestion.value,
    })
  }
  if (props.reproStepsSuggestion?.value) {
    list.push({
      key: 'reproSteps',
      label: '重现步骤',
      suggestion: props.reproStepsSuggestion,
      displayValue: props.reproStepsSuggestion.value,
      adoptValue: props.reproStepsSuggestion.value,
    })
  }
  const mapping: Array<[keyof BugDraftSuggestFields, string]> = [
    ['bugType', '缺陷类型'],
    ['severity', '严重等级'],
    ['priority', '优先级'],
    ['moduleId', '所属模块'],
    ['keywords', '关键词'],
    ['relatedCase', '关联用例'],
  ]
  for (const [key, label] of mapping) {
    const suggestion = props.suggestions[key]
    if (!suggestion || !('value' in suggestion) || suggestion.value == null || suggestion.value === '') continue
    if (key === 'keywords' && Array.isArray(suggestion.value)) {
      list.push({ key, label, suggestion: suggestion as BugSuggestionField<string[]>, displayValue: suggestion.value.join('、'), adoptValue: suggestion.value })
      continue
    }
    if (key === 'relatedCase' && typeof suggestion === 'object' && 'caseTitle' in suggestion) {
      const related = suggestion as { value: string; caseTitle: string; reason: string }
      list.push({
        key,
        label,
        suggestion: related as BugSuggestionField<string>,
        displayValue: related.caseTitle || related.value,
        adoptValue: related.value,
      })
      continue
    }
    if (typeof suggestion.value === 'string') {
      list.push({ key, label, suggestion: suggestion as BugSuggestionField<string>, displayValue: suggestion.value, adoptValue: suggestion.value })
    }
  }
  return list
})

const hasContent = computed(() => rows.value.length > 0 || props.duplicates.length > 0)
</script>

<template>
  <el-dialog v-model="visible" title="AI 建议" width="760px" destroy-on-close>
    <div v-if="loading" class="bug-ai-suggest__loading">
      <el-icon class="is-loading"><MagicStick /></el-icon>
      <span>AI 正在生成建议，请稍候...</span>
    </div>

    <el-alert
      v-else-if="error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    />

    <template v-else>
      <el-empty v-if="!hasContent" description="暂无可用建议" :image-size="72" />

      <div v-else class="bug-ai-suggest__body">
        <section v-if="rows.length > 0" class="bug-ai-suggest__section">
          <div class="bug-ai-suggest__section-title">字段建议</div>
          <div class="bug-ai-suggest__list">
            <article v-for="row in rows" :key="row.key" class="bug-ai-suggest__item">
              <div class="bug-ai-suggest__item-main">
                <div class="bug-ai-suggest__item-label">{{ row.label }}</div>
                <div class="bug-ai-suggest__item-value">{{ row.displayValue }}</div>
                <div v-if="row.suggestion?.reason" class="bug-ai-suggest__item-reason">
                  理由：{{ row.suggestion.reason }}
                </div>
              </div>
              <el-button
                link
                type="primary"
                size="small"
                @click="emit('adopt', {
                  key: row.key,
                  value: row.adoptValue,
                  caseTitle: row.key === 'relatedCase' ? row.displayValue : undefined,
                })"
              >采纳</el-button>
            </article>
          </div>
        </section>

        <section v-if="duplicates.length > 0" class="bug-ai-suggest__section">
          <div class="bug-ai-suggest__section-title">疑似重复缺陷</div>
          <BugDuplicateSuggestList :items="duplicates" />
        </section>
      </div>
    </template>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.bug-ai-suggest__loading {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-neutral-600);
  padding: var(--space-lg) 0;
}

.bug-ai-suggest__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.bug-ai-suggest__section-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
  margin-bottom: var(--space-sm);
}

.bug-ai-suggest__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.bug-ai-suggest__item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.bug-ai-suggest__item-main {
  min-width: 0;
  flex: 1;
}

.bug-ai-suggest__item-label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin-bottom: 2px;
}

.bug-ai-suggest__item-value {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-900);
  white-space: pre-wrap;
  word-break: break-word;
}

.bug-ai-suggest__item-reason {
  margin-top: var(--space-xs);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
