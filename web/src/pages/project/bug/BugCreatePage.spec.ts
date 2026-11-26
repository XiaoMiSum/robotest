import { describe, expect, it } from 'vitest'
import pageSource from './BugCreatePage.vue?raw'

describe('BugCreatePage AI 建议弹窗（交互 2.2）', () => {
  it('结果弹窗展示原值与产物对照，不再逐项采纳', () => {
    expect(pageSource).toContain('<BugAiSuggestionDialog')
    expect(pageSource).toContain('v-model="suggestDialogVisible"')
    expect(pageSource).toContain(':title-suggestion="titleSuggestion"')
    expect(pageSource).toContain(':repro-steps-suggestion="reproStepsSuggestion"')
    expect(pageSource).toContain(':suggestions="suggestions"')
    expect(pageSource).toContain(':original-values="originalValues"')
    expect(pageSource).not.toContain('@adopt="handleAdoptSuggestion"')
  })

  it('任务执行中仅旋转按钮图标，疑似重复在标题下提示且不在弹窗内展示', () => {
    expect(pageSource).toContain('<BugDuplicateSuggestList')
    expect(pageSource).toContain('class="bug-create__duplicate"')
    expect(pageSource).toContain(':items="duplicates"')
    expect(pageSource).toContain('.bug-create__duplicate {\n  margin-top: var(--space-xs);')
    expect(pageSource).toContain(":class=\"{ 'is-suggesting': suggestLoading }\"")
    expect(pageSource.match(/:loading="suggestLoading"/g)).toHaveLength(1)
    expect(pageSource).toContain('bug-create-ai-spin')
  })

  it('任务完成后覆盖回填表单，枚举写 key、模块写 id、关联用例写节点 id', () => {
    expect(pageSource).toContain("if (typeof titleSuggestion.value?.value === 'string') form.title = titleSuggestion.value.value")
    expect(pageSource).toContain("if (typeof reproStepsSuggestion.value?.value === 'string') form.reproSteps = reproStepsSuggestion.value.value")
    expect(pageSource).toContain('if (suggestions.value.bugType) form.bugType = suggestions.value.bugType.value')
    expect(pageSource).toContain('if (suggestions.value.severity) form.severity = suggestions.value.severity.value')
    expect(pageSource).toContain('if (suggestions.value.priority) form.priority = suggestions.value.priority.value')
    expect(pageSource).toContain("if (typeof suggestions.value.moduleId?.value === 'string') form.moduleId = suggestions.value.moduleId.value")
    expect(pageSource).toContain("if (suggestions.value.keywords?.value.length) form.keywords = suggestions.value.keywords.value.join(' ')")
    expect(pageSource).toContain('if (relatedCase.value) {')
    expect(pageSource).toContain('form.relatedCaseId = relatedCase.value.value')
  })

  it('同一页面重开只回显，不重复发起任务', () => {
    expect(pageSource).toContain('if (suggestLoading.value || suggestHasResult.value) return')
    expect(pageSource).toContain('originalValues.value = captureOriginalValues()')
    expect(pageSource).toContain("reproSteps: normalizedReproSteps.value || '（未填写）'")
    expect(pageSource).toContain('if (!suggestError.value && suggestHasResult.value) applySuggestionToForm()')
  })

  it('AI 建议通过通用任务链路请求，不再使用独立 suggest 接口', () => {
    expect(pageSource).toContain('requestSuggestion({')
    expect(pageSource).toContain('title: form.title.trim()')
    expect(pageSource).not.toContain('suggestBugDraft')
    expect(pageSource).not.toContain('/analysis/suggest')
  })
})
