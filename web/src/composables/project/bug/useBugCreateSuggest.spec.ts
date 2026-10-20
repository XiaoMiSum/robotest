import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  suggestBugDraft: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  suggestBugDraft: mocks.suggestBugDraft,
}))

import { useBugCreateSuggest } from './useBugCreateSuggest'

describe('composables/project/bug/useBugCreateSuggest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('同步一次返回全部建议：标题 / 重现步骤 / 分类 / 关联用例', async () => {
    mocks.suggestBugDraft.mockResolvedValue({
      title: { value: '登录页点击登录按钮后无任何响应', reason: '现象明确' },
      reproSteps: { value: '##### 执行步骤\n1. 打开登录页', reason: '三段结构' },
      suggestions: {
        bugType: { value: 'code_error', reason: '功能失效' },
        severity: { value: 'fatal', reason: '阻断主流程' },
        priority: { value: 'high', reason: '紧急' },
        moduleId: { value: 'm1', reason: '登录模块' },
        keywords: { value: ['登录', '无响应'], reason: '检索词' },
        relatedCase: { value: 'c1', caseTitle: '登录-正常登录', reason: '正向用例' },
      },
    })

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: '登录页点击登录无响应', steps: '1. 打开登录页' })

    expect(mocks.suggestBugDraft).toHaveBeenCalledWith({
      title: '登录页点击登录无响应',
      steps: '1. 打开登录页',
    })
    expect(suggest.titleSuggestion.value?.value).toBe('登录页点击登录按钮后无任何响应')
    expect(suggest.reproStepsSuggestion.value?.value).toContain('##### 执行步骤')
    expect(suggest.suggestions.value.bugType?.value).toBe('code_error')
    expect(suggest.suggestions.value.keywords?.value).toEqual(['登录', '无响应'])
    expect(suggest.relatedCase.value?.value).toBe('c1')
    expect(suggest.relatedCase.value?.caseTitle).toBe('登录-正常登录')
    expect(suggest.loading.value).toBe(false)
  })

  it('建议字段缺省时按空处理', async () => {
    mocks.suggestBugDraft.mockResolvedValue({ suggestions: {} })

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })

    expect(suggest.titleSuggestion.value).toBeNull()
    expect(suggest.reproStepsSuggestion.value).toBeNull()
    expect(suggest.relatedCase.value).toBeNull()
  })

  it('失败不阻塞录入：落错误提示并清空建议', async () => {
    mocks.suggestBugDraft.mockRejectedValue(new Error('模型不可用'))
    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })

    expect(suggest.error.value).toContain('模型不可用')
    expect(suggest.loading.value).toBe(false)
    expect(suggest.suggestions.value).toEqual({})
  })

  it('重复请求先清空上一次建议，避免过期值残留', async () => {
    mocks.suggestBugDraft.mockResolvedValueOnce({
      suggestions: { severity: { value: 'serious', reason: 'r' } },
    })
    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't1' })
    expect(suggest.suggestions.value.severity?.value).toBe('serious')

    mocks.suggestBugDraft.mockResolvedValueOnce({ suggestions: {} })
    const pending = suggest.requestSuggestion({ title: 't2' })
    expect(suggest.suggestions.value.severity).toBeUndefined()
    await pending
  })
})
