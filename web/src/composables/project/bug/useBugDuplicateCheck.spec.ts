import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'

const mocks = vi.hoisted(() => ({
  checkBugDuplicates: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  checkBugDuplicates: mocks.checkBugDuplicates,
}))

import { useBugDuplicateCheck } from './useBugDuplicateCheck'

describe('composables/project/bug/useBugDuplicateCheck', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function setup() {
    const title = ref('')
    const steps = ref('')
    const enabled = ref(true)
    const scope = effectScope()
    const result = scope.run(() =>
      useBugDuplicateCheck(() => title.value, () => steps.value, () => enabled.value),
    )!
    return { title, steps, enabled, result, scope }
  }

  it('标题填写后防抖检索，结果按相似度降序', async () => {
    mocks.checkBugDuplicates.mockResolvedValue({
      list: [
        { bugId: 'b1', title: '低相似', status: 'active', similarity: 0.4, basis: '块A' },
        { bugId: 'b2', title: '高相似', status: 'closed', similarity: 0.9, basis: '块B' },
      ],
    })
    const { title, result, scope } = setup()
    title.value = '登录页点击登录无响应'
    // 防抖窗口内不触发
    expect(mocks.checkBugDuplicates).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(900)
    expect(result.checked.value).toBe(true)
    expect(result.items.value.map((item) => item.bugId)).toEqual(['b2', 'b1'])
    expect(result.vectorUnavailable.value).toBe(false)

    scope.stop()
  })

  it('标题为空不触发请求并清空结果', async () => {
    const { title, result, scope } = setup()
    title.value = ''
    vi.advanceTimersByTime(1000)
    expect(mocks.checkBugDuplicates).not.toHaveBeenCalled()
    expect(result.checked.value).toBe(false)

    scope.stop()
  })

  it('向量未就绪（1000018258）：置灰提示且不再重复探测', async () => {
    const err = new Error('向量能力未就绪') as Error & { code: number }
    err.code = 1000018258
    mocks.checkBugDuplicates.mockRejectedValue(err)

    const { title, result, scope } = setup()
    title.value = '登录无响应'
    await vi.advanceTimersByTimeAsync(900)
    expect(result.vectorUnavailable.value).toBe(true)

    // 再次变更标题不再探测
    title.value = '登录无响应（更新）'
    await vi.advanceTimersByTimeAsync(2000)
    expect(mocks.checkBugDuplicates).toHaveBeenCalledTimes(1)

    scope.stop()
  })

  it('其他错误落错误信息', async () => {
    mocks.checkBugDuplicates.mockRejectedValue(new Error('boom'))
    const { title, result, scope } = setup()
    title.value = '标题'
    await vi.advanceTimersByTimeAsync(900)
    expect(result.error.value).toContain('boom')

    scope.stop()
  })

  it('过期响应丢弃：输入连续变化以最新结果为准', async () => {
    const resolvers: Array<() => void> = []
    mocks.checkBugDuplicates
      .mockImplementationOnce(
        () => new Promise((resolve) => { resolvers.push(() => resolve({ list: [] })) }),
      )
      .mockResolvedValueOnce({
        list: [{ bugId: 'b-new', title: '新结果', status: 'active', similarity: 0.8, basis: 'x' }],
      })

    const { title, result, scope } = setup()
    title.value = '旧输入'
    await vi.advanceTimersByTimeAsync(900)
    title.value = '新输入'
    await vi.advanceTimersByTimeAsync(900)
    expect(result.items.value[0]?.bugId).toBe('b-new')

    // 旧请求迟到 → 丢弃
    resolvers[0]?.()
    expect(result.items.value[0]?.bugId).toBe('b-new')

    scope.stop()
  })
})
