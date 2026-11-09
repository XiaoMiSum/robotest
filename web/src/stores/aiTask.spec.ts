import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, AiTaskTraceEvent } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiTask: vi.fn(),
  fetchAiRunningCount: vi.fn(),
  fetchAiTaskEvents: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiTask: mocks.fetchAiTask,
  fetchAiRunningCount: mocks.fetchAiRunningCount,
  fetchAiTaskEvents: mocks.fetchAiTaskEvents,
}))

import { isTerminalTaskStatus, useAiTaskStore } from './aiTask'

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 't1',
    executionId: 'e1',
    type: 'requirement_import',
    status: 'running',
    budget: { maxSteps: 4 },
    latestExecution: {
      executionId: 'e1',
      seq: 1,
      status: 'running',
      steps: 1,
      budget: { maxSteps: 4 },
      consumed: { durationMs: 0, tokensIn: 0, tokensOut: 0, cost: 0 },
      plan: null,
      terminateReason: null,
    },
    submittedBy: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-03T00:00:00Z',
    terminateReason: null,
    resultMeta: null,
    documentMeta: null,
    proposals: null,
    ...overrides,
  }
}

function makeEvent(seq: number): AiTaskTraceEvent {
  return {
    seq,
    eventType: 'plan',
    summary: `事件 ${seq}`,
    detail: null,
    createdAt: '2026-10-03T00:00:00Z',
  }
}

describe('stores/aiTask', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('筛选变更回第一页并保活条件', () => {
    const store = useAiTaskStore()
    store.pageNo = 3
    store.applyFilters({ type: 'requirement_import', status: 'running' })
    expect(store.pageNo).toBe(1)
    expect(store.filters).toEqual({ type: 'requirement_import', status: 'running' })

    store.pageNo = 2
    store.resetFilters()
    expect(store.pageNo).toBe(1)
    expect(store.filters).toEqual({ type: '', status: '' })
  })

  it('详情缓存按 taskId 命中', () => {
    const store = useAiTaskStore()
    store.cacheDetail(makeDetail())
    expect(store.takeCachedDetail('t1')?.status).toBe('running')
    expect(store.takeCachedDetail('other')).toBeNull()
  })

  it('终态判定：aborted / closed 不再轮询，proposals_ready 仍轮询', () => {
    expect(isTerminalTaskStatus('aborted')).toBe(true)
    expect(isTerminalTaskStatus('closed')).toBe(true)
    expect(isTerminalTaskStatus('running')).toBe(false)
    expect(isTerminalTaskStatus('proposals_ready')).toBe(false)
  })

  it('轮询成功写入缓存并清除中断标记', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockResolvedValue(makeDetail())
    const detail = await store.pollOnce('t1')
    expect(detail?.taskId).toBe('t1')
    expect(store.detail?.taskId).toBe('t1')
    expect(store.pollError).toBe(false)
  })

  it('轮询网络失败置中断标记但不抛出', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockRejectedValue(new Error('网络中断'))
    const detail = await store.pollOnce('t1')
    expect(detail).toBeNull()
    expect(store.pollError).toBe(true)
    expect(store.detail).toBeNull()
  })

  it('轮询到终态自动停止', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'closed' }))
    store.startPolling('t1')
    expect(store.polling).toBe(true)
    await store.pollTick('t1')
    expect(store.polling).toBe(false)
    expect(store.pollTimer).toBeNull()
  })

  it('非终态继续轮询，慢请求不叠加', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'proposals_ready' }))
    store.startPolling('t1')
    const slow = store.pollTick('t1')
    // 慢请求未返回时再次 tick 直接跳过
    await store.pollTick('t1')
    expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    await slow
    expect(store.polling).toBe(true)
    store.stopPolling()
    expect(store.polling).toBe(false)
    expect(store.pollTimer).toBeNull()
  })

  it('定时器按 2 秒间隔驱动一次 tick', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockResolvedValue(makeDetail())
    store.startPolling('t1')
    await vi.advanceTimersByTimeAsync(2100)
    expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    store.stopPolling()
  })

  it('轨迹按 afterSeq 游标增量追加', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTaskEvents
      .mockResolvedValueOnce({ items: [makeEvent(1), makeEvent(2)], nextSeq: 3 })
      .mockResolvedValueOnce({ items: [makeEvent(3)], nextSeq: 4 })
    await store.loadEvents('t1', 'e1')
    expect(mocks.fetchAiTaskEvents).toHaveBeenLastCalledWith('t1', 'e1', {
      afterSeq: 0,
      pageSize: 200,
    })
    expect(store.events).toHaveLength(2)
    expect(store.eventsNextSeq).toBe(3)
    expect(store.eventsHasMore).toBe(false)

    await store.loadEvents('t1', 'e1')
    expect(mocks.fetchAiTaskEvents).toHaveBeenLastCalledWith('t1', 'e1', {
      afterSeq: 3,
      pageSize: 200,
    })
    expect(store.events.map((event) => event.seq)).toEqual([1, 2, 3])
  })

  it('换执行实例（再次执行）自动重置轨迹', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTaskEvents.mockResolvedValue({ items: [makeEvent(1)], nextSeq: 2 })
    await store.loadEvents('t1', 'e1')
    expect(store.events).toHaveLength(1)

    await store.loadEvents('t1', 'e2')
    expect(store.events).toHaveLength(1)
    expect(store.eventsExecutionId).toBe('e2')
    expect(mocks.fetchAiTaskEvents).toHaveBeenLastCalledWith('t1', 'e2', {
      afterSeq: 0,
      pageSize: 200,
    })
  })

  it('打满单页上限标记还有更多，失败置错误标记', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTaskEvents.mockResolvedValue({
      items: Array.from({ length: 200 }, (_, index) => makeEvent(index + 1)),
      nextSeq: 200,
    })
    await store.loadEvents('t1', 'e1')
    expect(store.eventsHasMore).toBe(true)

    store.resetEvents()
    mocks.fetchAiTaskEvents.mockRejectedValue(new Error('网络中断'))
    await store.loadEvents('t1', 'e1')
    expect(store.eventsError).toBe(true)
    expect(store.eventsLoading).toBe(false)
  })

  it('计数刷新成功写入进行中数量', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiRunningCount.mockResolvedValue({ count: 2 })
    await store.refreshRunningCount()
    expect(store.runningCount).toBe(2)
  })

  it('计数刷新失败静默保留上次值', async () => {
    const store = useAiTaskStore()
    store.runningCount = 3
    mocks.fetchAiRunningCount.mockRejectedValue(new Error('网络中断'))
    await store.refreshRunningCount()
    expect(store.runningCount).toBe(3)
  })

  it('计数轮询装载即刷新并按 15 秒间隔续跑，重复装载幂等', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiRunningCount.mockResolvedValue({ count: 1 })
    store.startCountPolling()
    store.startCountPolling()
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(15100)
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(2)

    store.stopCountPolling()
    await vi.advanceTimersByTimeAsync(15100)
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(2)
    expect(store.countPollTimer).toBeNull()
  })

  it('计数轮询慢请求不叠加', async () => {
    const store = useAiTaskStore()
    // 闭包内赋值，TS 控制流会把外层变量窄化为 null；经对象属性持有保持可调用
    const deferred: { resolve: ((value: { count: number }) => void) | null } = { resolve: null }
    mocks.fetchAiRunningCount.mockImplementation(
      () => new Promise((resolve) => (deferred.resolve = resolve)),
    )
    const first = store.refreshRunningCount()
    await store.refreshRunningCount()
    expect(mocks.fetchAiRunningCount).toHaveBeenCalledTimes(1)
    deferred.resolve?.({ count: 5 })
    await first
    expect(store.runningCount).toBe(5)
  })
})
