import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  submitAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  fetchAiProposal: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  fetchAiTask: mocks.fetchAiTask,
  fetchAiProposal: mocks.fetchAiProposal,
}))

import { useBugCreateSuggest } from './useBugCreateSuggest'

describe('composables/project/bug/useBugCreateSuggest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fast path: waitSeconds 完成后直接加载提案', async () => {
    mocks.submitAiTask.mockResolvedValue({
      taskId: 'task-1',
      status: 'proposals_ready',
    })
    mocks.fetchAiTask.mockResolvedValue({
      taskId: 'task-1',
      status: 'proposals_ready',
      proposals: [{ key: 'bug-draft-1', kind: 'bug_draft_suggestion' }],
    })
    mocks.fetchAiProposal.mockResolvedValue({
      content: {
        title: { value: '优化后标题', reason: '现象明确' },
        reproSteps: { value: '##### 执行步骤\n1. 打开登录页', reason: '三段结构' },
        suggestions: {
          bugType: { value: 'code_error', reason: '功能失效' },
          keywords: { value: ['登录', '无响应'], reason: '检索词' },
          relatedCase: { value: 'c1', caseTitle: '登录-正常登录', reason: '正向用例' },
        },
        duplicates: [
          { bugId: 'b1', title: '低相似', status: 'active', similarity: 0.4, basis: '块A' },
          { bugId: 'b2', title: '高相似', status: 'closed', similarity: 0.9, basis: '块B' },
        ],
      },
    })

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: '登录页点击登录无响应', steps: '1. 打开登录页', moduleId: 'm1' })

    expect(mocks.submitAiTask).toHaveBeenCalledWith('bug_draft_suggest', {
      title: '登录页点击登录无响应',
      steps: '1. 打开登录页',
      moduleId: 'm1',
    }, 10)
    expect(suggest.titleSuggestion.value?.value).toBe('优化后标题')
    expect(suggest.relatedCase.value?.caseTitle).toBe('登录-正常登录')
    expect(suggest.duplicates.value.map((item) => item.bugId)).toEqual(['b2', 'b1'])
    expect(suggest.hasResult.value).toBe(true)
    expect(suggest.error.value).toBe('')
  })

  it('closed 任务直接加载提案并按默认采纳对待', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 'task-closed', status: 'closed' })
    mocks.fetchAiTask.mockResolvedValue({
      taskId: 'task-closed',
      status: 'closed',
      proposals: [{ key: 'bug-draft-1', kind: 'bug_draft_suggestion', confirmStatus: 'adopted' }],
    })
    mocks.fetchAiProposal.mockResolvedValue({ content: { title: { value: '已采纳标题', reason: '优化' } } })

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })

    expect(mocks.fetchAiProposal).toHaveBeenCalledWith('task-closed', 'bug-draft-1')
    expect(suggest.hasResult.value).toBe(true)
    expect(suggest.titleSuggestion.value?.value).toBe('已采纳标题')
  })

  it('polling: running 后轮询到 proposals_ready', async () => {
    vi.useFakeTimers()
    mocks.submitAiTask.mockResolvedValue({ taskId: 'task-2', status: 'running' })
    mocks.fetchAiTask
      .mockResolvedValueOnce({ taskId: 'task-2', status: 'running', proposals: [] })
      .mockResolvedValueOnce({
        taskId: 'task-2',
        status: 'proposals_ready',
        proposals: [{ key: 'bug-draft-1', kind: 'bug_draft_suggestion' }],
      })
    mocks.fetchAiProposal.mockResolvedValue({
      content: { suggestions: {}, duplicates: [] },
    })

    const suggest = useBugCreateSuggest()
    const pending = suggest.requestSuggestion({ title: 't' })
    await vi.runAllTimersAsync()
    await pending

    expect(mocks.fetchAiTask).toHaveBeenCalledTimes(2)
    expect(suggest.loading.value).toBe(false)
    expect(suggest.error.value).toBe('')
    vi.useRealTimers()
  })

  it('任务终止时暴露错误且不阻塞后续录入', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 'task-3', status: 'running' })
    mocks.fetchAiTask.mockResolvedValue({
      taskId: 'task-3',
      status: 'aborted',
      terminateReason: { code: 1000018128, msg: '用户中止' },
      proposals: [],
    })

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })

    expect(suggest.error.value).toContain('用户中止')
    expect(suggest.hasResult.value).toBe(false)
    expect(suggest.suggestions.value).toEqual({})
    expect(suggest.duplicates.value).toEqual([])
  })
})
