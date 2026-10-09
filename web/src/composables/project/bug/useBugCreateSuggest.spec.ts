import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  submitAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  fetchAiArtifact: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  fetchAiTask: mocks.fetchAiTask,
  fetchAiArtifact: mocks.fetchAiArtifact,
}))

import { useBugCreateSuggest } from './useBugCreateSuggest'

function draftArtifact(content: Record<string, unknown>): Record<string, unknown> {
  return { key: 'draft', kind: 'classify_suggestion', title: '新建缺陷建议', content }
}

describe('composables/project/bug/useBugCreateSuggest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('同步快路径成功：解析建议值、候选与引用', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 't1', status: 'succeeded' })
    mocks.fetchAiArtifact.mockResolvedValue(
      draftArtifact({
        suggestions: {
          bugType: { value: 'code_error', reason: '功能失效' },
          severity: { value: 'fatal', reason: '阻断主流程' },
          priority: { value: 'high', reason: '紧急' },
          moduleId: { value: 'm1', reason: '登录模块' },
          keywords: { value: ['登录', '无响应'], reason: '检索词' },
        },
        assigneeCandidates: [
          { userId: 'u1', name: '李四', reason: '同模块历史处理人', memberValid: true },
          // 服务端已过滤，前端防御剔除 memberValid=false
          { userId: 'u2', name: '外部', reason: '', memberValid: false },
        ],
        sourceRefs: [{ type: 'bug', id: 'b1', title: '登录按钮无反应' }],
      }),
    )

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: '登录页点击登录无响应' })

    expect(mocks.submitAiTask).toHaveBeenCalledWith(
      'bug_classify',
      { draft: { title: '登录页点击登录无响应' } },
      10,
    )
    expect(suggest.suggestions.value.bugType?.value).toBe('code_error')
    expect(suggest.suggestions.value.keywords?.value).toEqual(['登录', '无响应'])
    expect(suggest.assigneeCandidates.value).toHaveLength(1)
    expect(suggest.assigneeCandidates.value[0]?.userId).toBe('u1')
    expect(suggest.sourceRefs.value[0]?.id).toBe('b1')
    expect(suggest.loading.value).toBe(false)
  })

  it('超时转轮询：轮到终态后取产物', async () => {
    vi.useFakeTimers()
    mocks.submitAiTask.mockResolvedValue({ taskId: 't2', status: 'running' })
    mocks.fetchAiTask.mockResolvedValue({ taskId: 't2', status: 'succeeded' })
    mocks.fetchAiArtifact.mockResolvedValue(
      draftArtifact({ suggestions: { severity: { value: 'serious', reason: 'r' } } }),
    )

    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })
    expect(suggest.loading.value).toBe(true)

    await vi.advanceTimersByTimeAsync(2100)
    expect(suggest.suggestions.value.severity?.value).toBe('serious')
    expect(suggest.loading.value).toBe(false)
  })

  it('失败不阻塞录入：落错误提示', async () => {
    mocks.submitAiTask.mockRejectedValue(new Error('模型不可用'))
    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })
    expect(suggest.error.value).toContain('模型不可用')
    expect(suggest.loading.value).toBe(false)
  })

  it('dispose 停掉轮询计时器', async () => {
    vi.useFakeTimers()
    mocks.submitAiTask.mockResolvedValue({ taskId: 't3', status: 'running' })
    const suggest = useBugCreateSuggest()
    await suggest.requestSuggestion({ title: 't' })
    suggest.dispose()
    // dispose 后推进定时器不应再触发请求
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.fetchAiTask).not.toHaveBeenCalled()
  })
})
