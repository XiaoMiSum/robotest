import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiTask: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiTask: mocks.fetchAiTask,
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
})
