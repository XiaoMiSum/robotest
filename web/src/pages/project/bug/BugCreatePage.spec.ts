import { describe, expect, it } from 'vitest'
import pageSource from './BugCreatePage.vue?raw'

describe('BugCreatePage AI 建议徽标展示（交互 2.2）', () => {
  it('枚举徽标展示名称而非 key，模块徽标展示模块名而非 id', () => {
    expect(pageSource).toContain(':display-text="bugTypeBadgeText"')
    expect(pageSource).toContain(':display-text="severityBadgeText"')
    expect(pageSource).toContain(':display-text="priorityBadgeText"')
    expect(pageSource).toContain(':display-text="moduleBadgeText"')
  })

  it('徽标文案与表单控件同源，未知取值回退原文', () => {
    expect(pageSource).toContain('BUG_TYPE_LABEL[value as BugType] ?? value')
    expect(pageSource).toContain('severityLabel[value as BugSeverity] ?? value')
    expect(pageSource).toContain('priorityLabel[value as BugPriority] ?? value')
    expect(pageSource).toContain('findModuleName(moduleTree.value, id) ?? id')
  })

  it('采纳写入值不变：枚举写 key、模块写 id、关联用例写节点 id', () => {
    expect(pageSource).toContain("@adopt=\"adoptSuggestion('bugType', $event)\"")
    expect(pageSource).toContain("@adopt=\"adoptSuggestion('severity', $event)\"")
    expect(pageSource).toContain("@adopt=\"adoptSuggestion('priority', $event)\"")
    expect(pageSource).toContain("@adopt=\"adoptSuggestion('moduleId', $event)\"")
    expect(pageSource).toContain('@adopt="relatedCase && adoptRelatedCase(relatedCase)"')
  })
})
