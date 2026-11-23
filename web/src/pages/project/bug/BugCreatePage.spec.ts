import { describe, expect, it } from 'vitest'
import pageSource from './BugCreatePage.vue?raw'

describe('BugCreatePage AI 建议弹窗（交互 2.2）', () => {
  it('通过 AI 建议弹窗审核建议并挂载任务建议状态', () => {
    expect(pageSource).toContain('<BugAiSuggestionDialog')
    expect(pageSource).toContain('v-model="suggestDialogVisible"')
    expect(pageSource).toContain(':title-suggestion="titleSuggestion"')
    expect(pageSource).toContain(':repro-steps-suggestion="reproStepsSuggestion"')
    expect(pageSource).toContain(':suggestions="suggestions"')
    expect(pageSource).toContain(':duplicates="duplicates"')
    expect(pageSource).toContain('@adopt="handleAdoptSuggestion"')
  })

  it('采纳写入值不变：枚举写 key、模块写 id、关联用例写节点 id', () => {
    expect(pageSource).toContain("if (key === 'title' && typeof value === 'string') form.title = value")
    expect(pageSource).toContain("else if (key === 'reproSteps' && typeof value === 'string') form.reproSteps = value")
    expect(pageSource).toContain("else if (key === 'bugType' && typeof value === 'string') form.bugType = value as typeof form.bugType")
    expect(pageSource).toContain("else if (key === 'severity' && typeof value === 'string') form.severity = value as typeof form.severity")
    expect(pageSource).toContain("else if (key === 'priority' && typeof value === 'string') form.priority = value as typeof form.priority")
    expect(pageSource).toContain("else if (key === 'moduleId' && typeof value === 'string') form.moduleId = value")
    expect(pageSource).toContain("else if (key === 'relatedCase' && typeof value === 'string') {")
    expect(pageSource).toContain('form.relatedCaseId = value')
  })

  it('AI 建议通过通用任务链路请求，不再使用独立 suggest 接口', () => {
    expect(pageSource).toContain('requestSuggestion({')
    expect(pageSource).toContain('title: form.title.trim()')
    expect(pageSource).not.toContain('suggestBugDraft')
    expect(pageSource).not.toContain('/analysis/suggest')
  })
})
