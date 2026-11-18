// @vitest-environment jsdom
import { createApp, defineComponent } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReplanStatusResp } from '@/types'
import { isRequirementSetChanged, useReplanPolling } from './replan'

function status(overrides: Partial<ReplanStatusResp> = {}): ReplanStatusResp {
  return { status: 'running', message: null, requestedAt: null, finishedAt: null, ...overrides }
}

function setup(fetchStatus: (id: string) => Promise<ReplanStatusResp>) {
  let api!: ReturnType<typeof useReplanPolling>
  const app = createApp(
    defineComponent({
      setup() {
        api = useReplanPolling(fetchStatus)
        return () => null
      },
    }),
  )
  app.mount(document.createElement('div'))
  return { api, app }
}

async function settle(ms = 0): Promise<void> {
  await vi.advanceTimersByTimeAsync(ms)
}

describe('isRequirementSetChanged', () => {
  it('只比较集合，顺序变化不算变化', () => {
    expect(isRequirementSetChanged(['a', 'b'], ['b', 'a'])).toBe(false)
    expect(isRequirementSetChanged([], [])).toBe(false)
  })

  it('数量或元素不同即视为变化', () => {
    expect(isRequirementSetChanged(['a'], ['a', 'b'])).toBe(true)
    expect(isRequirementSetChanged(['a', 'b'], ['a', 'c'])).toBe(true)
  })
})

describe('useReplanPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('立即查询一次，运行中按 2 秒间隔继续，终态停止并回调', async () => {
    const fetchStatus = vi
      .fn<() => Promise<ReplanStatusResp>>()
      .mockResolvedValueOnce(status())
      .mockResolvedValueOnce(status({ status: 'succeeded', message: '完成' }))
      .mockResolvedValue(status())
    const onFinish = vi.fn()
    const onError = vi.fn()
    const { api } = setup(fetchStatus)

    api.start('r1', onFinish, onError)
    await settle()
    expect(fetchStatus).toHaveBeenCalledTimes(1)
    expect(onFinish).not.toHaveBeenCalled()
    expect(api.polling.value).toBe(true)

    await settle(2000)
    expect(fetchStatus).toHaveBeenCalledTimes(2)
    expect(onFinish).toHaveBeenCalledWith(status({ status: 'succeeded', message: '完成' }))
    expect(api.polling.value).toBe(false)

    await settle(2000)
    expect(fetchStatus).toHaveBeenCalledTimes(2)
  })

  it('查询失败停止轮询并回调错误', async () => {
    const fetchStatus = vi.fn<() => Promise<ReplanStatusResp>>().mockRejectedValue(new Error('网络异常'))
    const onFinish = vi.fn()
    const onError = vi.fn()
    const { api } = setup(fetchStatus)

    api.start('r1', onFinish, onError)
    await settle()
    expect(onError).toHaveBeenCalledWith('网络异常')
    expect(onFinish).not.toHaveBeenCalled()
    expect(api.polling.value).toBe(false)

    await settle(2000)
    expect(fetchStatus).toHaveBeenCalledTimes(1)
  })

  it('页面卸载后不再轮询', async () => {
    const fetchStatus = vi.fn<() => Promise<ReplanStatusResp>>().mockResolvedValue(status())
    const { api, app } = setup(fetchStatus)

    api.start('r1', vi.fn(), vi.fn())
    await settle()
    expect(fetchStatus).toHaveBeenCalledTimes(1)

    app.unmount()
    await settle(2000)
    expect(fetchStatus).toHaveBeenCalledTimes(1)
  })

  it('停止后到达的在途响应不会触发终态回调', async () => {
    let resolveFirst!: (value: ReplanStatusResp) => void
    const first = new Promise<ReplanStatusResp>((resolve) => {
      resolveFirst = resolve
    })
    const fetchStatus = vi
      .fn<() => Promise<ReplanStatusResp>>()
      .mockReturnValueOnce(first)
      .mockResolvedValue(status())
    const onFinish = vi.fn()
    const { api } = setup(fetchStatus)

    api.start('r1', onFinish, vi.fn())
    await settle()
    api.stop()
    resolveFirst(status({ status: 'succeeded' }))
    await settle()
    expect(onFinish).not.toHaveBeenCalled()
  })
})
