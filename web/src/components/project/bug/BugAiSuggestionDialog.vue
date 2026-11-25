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
  original: string
}

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  error: string
  titleSuggestion: BugSuggestionField<string> | null
  reproStepsSuggestion: BugSuggestionField<string> | null
  suggestions: BugDraftSuggestFields
  originalValues: Record<string, string>
  duplicates: BugDuplicateSuggestItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const rows = computed<SuggestionRow[]>(() => {
  const list: SuggestionRow[] = []
  const push = (key: string, label: string, suggestion: BugSuggestionField<string | string[]> | null, value: string) => {
    if (!suggestion || !value) return
    list.push({ key, label, suggestion, displayValue: value, original: props.originalValues[key] || '—' })
  }
  if (props.titleSuggestion?.value) push('title', '标题', props.titleSuggestion, props.titleSuggestion.value)
  if (props.reproStepsSuggestion?.value) {
    push('reproSteps', '重现步骤', props.reproStepsSuggestion, props.reproStepsSuggestion.value)
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
      push(key, label, suggestion as BugSuggestionField<string[]>, suggestion.value.join('、'))
      continue
    }
    if (key === 'relatedCase' && 'caseTitle' in suggestion) {
      const related = suggestion as BugSuggestionField<string> & { caseTitle: string }
      push(key, label, related, related.caseTitle || related.value)
      continue
    }
    if (typeof suggestion.value === 'string') {
      push(key, label, suggestion as BugSuggestionField<string>, suggestion.value)
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
        <el-alert type="success" title="建议已默认采纳并自动填入表单" :closable="false" show-icon />

        <section v-if="rows.length > 0" class="bug-ai-suggest__section">
          <div class="bug-ai-suggest__section-title">原填写值与 AI 产物值对照</div>
          <div class="bug-ai-suggest__list">
            <article v-for="row in rows" :key="row.key" class="bug-ai-suggest__item">
              <div class="bug-ai-suggest__row">
                <div class="bug-ai-suggest__item-label">{{ row.label }}</div>
                <div>
                  <div class="bug-ai-suggest__item-label">原填写值</div>
                  <div class="bug-ai-suggest__item-value">{{ row.original }}</div>
                </div>
                <div>
                  <div class="bug-ai-suggest__item-label">AI 产物值（已填入）</div>
                  <div class="bug-ai-suggest__item-value">{{ row.displayValue }}</div>
                </div>
              </div>
              <div v-if="row.suggestion?.reason" class="bug-ai-suggest__item-reason">
                理由：{{ row.suggestion.reason }}
              </div>
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
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.bug-ai-suggest__row {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-sm);
}

.bug-ai-suggest__item-label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
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
