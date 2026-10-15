import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiTask: vi.fn(),
  fetchAiRunningCount: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiTask: mocks.fetchAiTask,
  fetchAiRunningCount: mocks.fetchAiRunningCount,
}))

import { isTerminalTaskStatus, useAiTaskStore } from './aiTask'

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 't1',
    type: 'requirement_split',
    status: 'running',
    progress: 40,
    phase: '构建提示词',
    submittedBy: null,
    retryOfTaskId: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-03T00:00:00Z',
    error: null,
    result: null,
    documentMeta: null,
    artifacts: null,
    ...overrides,
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
    store.applyFilters({ type: 'requirement_split', status: 'running' })
    expect(store.pageNo).toBe(1)
    expect(store.filters).toEqual({ type: 'requirement_split', status: 'running' })

    store.pageNo = 2
    store.resetFilters()
    expect(store.pageNo).toBe(1)
    expect(store.filters).toEqual({ type: '', status: '' })
  })

  it('详情缓存按 taskId 命中', () => {
    const store = useAiTaskStore()
    store.cacheDetail(makeDetail())
    expect(store.takeCachedDetail('t1')?.progress).toBe(40)
    expect(store.takeCachedDetail('other')).toBeNull()
  })

  it('终态判定：succeeded / failed / cancelled 不再轮询', () => {
    expect(isTerminalTaskStatus('succeeded')).toBe(true)
    expect(isTerminalTaskStatus('failed')).toBe(true)
    expect(isTerminalTaskStatus('cancelled')).toBe(true)
    expect(isTerminalTaskStatus('pending')).toBe(false)
    expect(isTerminalTaskStatus('running')).toBe(false)
  })

  it('轮询成功写入缓存并清除中断标记', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ progress: 80 }))
    const detail = await store.pollOnce('t1')
    expect(detail?.progress).toBe(80)
    expect(store.detail?.progress).toBe(80)
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
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'succeeded' }))
    store.startPolling('t1')
    expect(store.polling).toBe(true)
    await store.pollTick('t1')
    expect(store.polling).toBe(false)
    expect(store.pollTimer).toBeNull()
  })

  it('非终态继续轮询，慢请求不叠加', async () => {
    const store = useAiTaskStore()
    mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'running' }))
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
