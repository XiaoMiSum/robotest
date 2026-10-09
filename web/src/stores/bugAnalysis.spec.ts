import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, AiTaskItem, BugMetricsResp, BugTrendsResp } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchBugTrends: vi.fn(),
  fetchBugMetrics: vi.fn(),
  submitAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  fetchAiArtifact: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  fetchBugTrends: mocks.fetchBugTrends,
  fetchBugMetrics: mocks.fetchBugMetrics,
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  fetchAiTask: mocks.fetchAiTask,
  fetchAiArtifact: mocks.fetchAiArtifact,
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
    type: 'bug_trend_summary',
    status: 'succeeded',
    progress: 100,
    phase: null,
    submittedBy: null,
    retryOfTaskId: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-09T00:00:00Z',
    error: null,
    ...overrides,
  }
}

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    ...makeTask(),
    result: null,
    documentMeta: null,
    artifacts: null,
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

  it('摘要同步快路径成功：取产物内容与引用', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask())
    mocks.fetchAiArtifact.mockResolvedValue({
      content: {
        text: '区间内新增 3 个缺陷，存量 5 个',
        citations: [{ type: 'bug', id: 'b1', title: '登录按钮无反应' }],
      },
    })

    await store.generateSummary()
    expect(mocks.submitAiTask).toHaveBeenCalledWith(
      'bug_trend_summary',
      {},
      10,
    )
    expect(store.summaryText).toContain('新增 3 个缺陷')
    expect(store.summaryCitations).toHaveLength(1)
    expect(store.summaryError).toBe('')
  })

  it('摘要生成失败：落错误供卡片重试，标记范围指纹', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'failed' }))

    await store.generateSummary()
    expect(store.summaryError).toContain('失败')
    expect(store.summaryRangeKey).toBe('||none')
  })

  it('范围变化后摘要标记过期（stale）', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask())
    mocks.fetchAiArtifact.mockResolvedValue({ content: { text: '摘要', citations: [] } })
    await store.generateSummary()
    expect(store.summaryStale).toBe(false)

    store.from = '2026-10-01'
    store.to = '2026-10-02'
    expect(store.summaryStale).toBe(true)
  })

  it('摘要超时转轮询：轮到终态后停止计时器并取产物', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'running' }))
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'succeeded' }))
    mocks.fetchAiArtifact.mockResolvedValue({ content: { text: 'ok', citations: [] } })

    await store.generateSummary()
    expect(store.summaryRunning).toBe(true)
    await vi.advanceTimersByTimeAsync(2100)
    expect(store.summaryText).toBe('ok')
    expect(store.summaryPollTimer).toBeNull()
  })

  it('分诊成功解析 items；空结果给降级提示', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ type: 'bug_triage' }))
    mocks.fetchAiArtifact.mockResolvedValue({
      content: {
        items: [{ bugId: 'b1', rank: 1, reason: '致命 + 存放 3 天' }],
        scope: 'active_unassigned',
      },
    })

    await store.loadTriage()
    expect(store.triageItems).toEqual([{ bugId: 'b1', rank: 1, reason: '致命 + 存放 3 天' }])
    expect(store.triageError).toBe('')

    mocks.submitAiTask.mockResolvedValue(makeTask({ type: 'bug_triage' }))
    mocks.fetchAiArtifact.mockResolvedValue({ content: { items: [], scope: 'active_unassigned' } })
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

  it('dispose 停掉两个轮询计时器', async () => {
    const store = useBugAnalysisStore()
    mocks.submitAiTask.mockResolvedValue(makeTask({ status: 'running' }))
    await store.generateSummary()
    expect(store.summaryPollTimer).not.toBeNull()

    store.dispose()
    expect(store.summaryPollTimer).toBeNull()
    expect(store.triagePollTimer).toBeNull()
  })
})
