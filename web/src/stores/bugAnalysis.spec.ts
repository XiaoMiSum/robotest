import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, AiTaskItem, BugMetricsResp, BugTrendsResp } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchBugTrends: vi.fn(),
  fetchBugMetrics: vi.fn(),
  submitAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  fetchAiProposal: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  fetchBugTrends: mocks.fetchBugTrends,
  fetchBugMetrics: mocks.fetchBugMetrics,
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  fetchAiTask: mocks.fetchAiTask,
  fetchAiProposal: mocks.fetchAiProposal,
}))

import { resetBugAnalysisSeq, useBugAnalysisStore } from './bugAnalysis'

function makeTrends(): BugTrendsResp {
  return {
    axis: ['2026-10-01', '2026-10-02'],
    series: [{ key: 'all', label: '全部', created: [1, 2], closed: [0, 1], active: [1, 2] }],
    groupBy: 'none',
  }
}

function makeMetrics(): BugMetricsResp {
  return {
    fixDuration: { avgHours: 12, p50Hours: 8, p90Hours: 30, sample: 5 },
    reopenRate: 0.1,
    duplicateRate: 0.05,
    severityDist: [{ key: 'fatal', count: 2 }],
    moduleDist: [],
    typeDist: [],
  }
}

function makeTask(overrides: Partial<AiTaskItem> = {}): AiTaskItem {
  return {
    taskId: 't1',
    executionId: 'e1',
    type: 'bug_triage',
    title: '',
    status: 'closed',
    budget: { maxSteps: 10 },
    latestExecution: {
      executionId: 'e1',
      seq: 1,
      status: 'succeeded',
      steps: 3,
      budget: { maxSteps: 10 },
      consumed: { durationMs: 0, tokensIn: 0, tokensOut: 0, cost: 0 },
      plan: null,
      terminateReason: null,
    },
    submittedBy: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-09T00:00:00Z',
    terminateReason: null,
    ...overrides,
  }
}

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    ...makeTask(),
    resultMeta: null,
    documentMeta: null,
    proposals: null,
    ...overrides,
  }
}

describe('stores/bugAnalysis', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setActivePinia(createPinia())
    resetBugAnalysisSeq()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('queryRange：空值不携带，非 none 分组才传 groupBy', () => {
    const store = useBugAnalysisStore()
    expect(store.queryRange).toEqual({})

    store.from = '2026-10-01'
    store.to = '2026-10-02'
    store.groupBy = 'severity'
    expect(store.queryRange).toEqual({ from: '2026-10-01', to: '2026-10-02', groupBy: 'severity' })
  })

  it('度量加载成功写入，失败落错误', async () => {
    const store = useBugAnalysisStore()
    mocks.fetchBugMetrics.mockResolvedValueOnce(makeMetrics())
    await store.loadMetrics()
    expect(store.metrics?.reopenRate).toBe(0.1)
    expect(store.metricsError).toBe('')

    mocks.fetchBugMetrics.mockRejectedValueOnce(new Error('boom'))
    await store.loadMetrics()
    expect(store.metricsError).toContain('boom')
  })

  it('范围切换后过期响应丢弃，以最新范围结果为准（防竞态）', async () => {
    const store = useBugAnalysisStore()
    const resolvers: Array<(value: BugTrendsResp) => void> = []
    mocks.fetchBugTrends
      .mockImplementationOnce(
        () => new Promise<BugTrendsResp>((resolve) => { resolvers.push(resolve) }),
      )
      .mockResolvedValueOnce({
        axis: ['2026-10-08'],
        series: [{ key: 'all', label: '全部', created: [9], closed: [0], active: [9] }],
        groupBy: 'none',
      })

    const first = store.loadTrends()
    const second = store.loadTrends()
    // 后发起的（新范围）先返回
    await second
    expect(store.trends?.axis).toEqual(['2026-10-08'])
    // 旧范围响应迟到 → 丢弃
    resolvers[0]?.(makeTrends())
    await first
    expect(store.trends?.axis).toEqual(['2026-10-08'])
  })

  it('分诊成功解析 items；空结果给降级提示', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask())
    mocks.fetchAiProposal.mockResolvedValue({
      content: {
        items: [{ bugId: 'b1', rank: 1, reason: '致命 + 存放 3 天' }],
        scope: 'active_unassigned',
      },
    })

    await store.loadTriage()
    expect(mocks.submitAiTask).toHaveBeenCalledWith('bug_triage', {}, 10)
    expect(store.triageItems).toEqual([{ bugId: 'b1', rank: 1, reason: '致命 + 存放 3 天' }])
    expect(store.triageError).toBe('')

    mocks.submitAiTask.mockResolvedValue(makeTask())
    mocks.fetchAiProposal.mockResolvedValue({ content: { items: [], scope: 'active_unassigned' } })
    await store.loadTriage()
    expect(store.triageError).toContain('没有需要分诊')
  })

  it('分诊业务错误（空集 1000018283）按提示降级，不抛出', async () => {
    const store = useBugAnalysisStore()
    const err = new Error('分析输入为空') as Error & { code: number }
    err.code = 1000018283
    mocks.submitAiTask.mockRejectedValue(err)

    await expect(store.loadTrends().then(() => store.loadTriage())).resolves.toBeUndefined()
    expect(store.triageError).toContain('分析输入为空')
  })

  it('分诊超时转轮询：轮到终态后停止计时器并取产物', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'running' }))
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'closed' }))
    mocks.fetchAiProposal.mockResolvedValue({
      content: { items: [{ bugId: 'b1', rank: 1, reason: '优先处理' }], scope: 'active_unassigned' },
    })

    await store.loadTriage()
    expect(store.triagePollTimer).not.toBeNull()
    await vi.advanceTimersByTimeAsync(2100)
    expect(store.triageItems).toEqual([{ bugId: 'b1', rank: 1, reason: '优先处理' }])
    expect(store.triagePollTimer).toBeNull()
  })

  it('分诊轮询到 aborted：停止计时器、落失败占位且不取产物', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'running' }))
    mocks.fetchAiTask.mockResolvedValue(
      makeDetail({
        status: 'aborted',
        terminateReason: { code: 1000018117, msg: '模型不可用' },
      }),
    )

    await store.loadTriage()
    expect(store.triagePollTimer).not.toBeNull()
    await vi.advanceTimersByTimeAsync(2100)
    expect(store.triageError).toContain('失败')
    expect(store.triagePollTimer).toBeNull()
    expect(mocks.fetchAiProposal).not.toHaveBeenCalled()
  })

  it('分诊轮询网络失败不终止，下一轮继续', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'running' }))
    mocks.fetchAiTask
      .mockRejectedValueOnce(new Error('网络抖动'))
      .mockResolvedValueOnce(makeDetail({ status: 'closed' }))
    mocks.fetchAiProposal.mockResolvedValue({
      content: { items: [], scope: 'active_unassigned' },
    })

    await store.loadTriage()
    await vi.advanceTimersByTimeAsync(2100)
    expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    // 首轮网络失败不终止轮询，下一轮继续并取到产物
    await vi.advanceTimersByTimeAsync(2100)
    expect(mocks.fetchAiTask).toHaveBeenCalledTimes(2)
    expect(store.triagePollTimer).toBeNull()
  })

  it('dispose 停掉分诊轮询计时器', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'running' }))
    await store.loadTriage()
    expect(store.triagePollTimer).not.toBeNull()

    store.dispose()
    expect(store.triagePollTimer).toBeNull()
  })
})
