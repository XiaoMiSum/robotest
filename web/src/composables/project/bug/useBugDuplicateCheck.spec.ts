import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

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
  })

  function setup() {
    const title = ref('')
    const steps = ref('')
    const enabled = ref(true)
    const result = useBugDuplicateCheck(() => title.value, () => steps.value, () => enabled.value)
    return { title, steps, enabled, result }
  }

  it('标题为空或能力未启用时不发请求', async () => {
    const { title, enabled, result } = setup()
    await result.check()
    expect(mocks.checkBugDuplicates).not.toHaveBeenCalled()

    title.value = '登录无响应'
    enabled.value = false
    await result.check()
    expect(mocks.checkBugDuplicates).not.toHaveBeenCalled()
    expect(result.items.value).toEqual([])
  })

  it('触发时按当前标题与步骤请求，结果按相似度降序', async () => {
    mocks.checkBugDuplicates.mockResolvedValue({
      list: [
        { bugId: 'b1', title: '低相似', status: 'active', similarity: 0.4, basis: '块A' },
        { bugId: 'b2', title: '高相似', status: 'closed', similarity: 0.9, basis: '块B' },
      ],
    })
    const { title, steps, result } = setup()
    title.value = '登录页点击登录无响应'
    steps.value = '1. 打开登录页'
    await result.check()
    expect(mocks.checkBugDuplicates).toHaveBeenCalledWith({
      title: '登录页点击登录无响应',
      steps: '1. 打开登录页',
      limit: 5,
    })
    expect(result.items.value.map((item) => item.bugId)).toEqual(['b2', 'b1'])
    expect(result.loading.value).toBe(false)
  })

  it('步骤为空时不传 steps 参数并清理标题首尾空白', async () => {
    mocks.checkBugDuplicates.mockResolvedValue({ list: [] })
    const { title, result } = setup()
    title.value = '  登录无响应  '
    await result.check()
    expect(mocks.checkBugDuplicates).toHaveBeenCalledWith({
      title: '登录无响应',
      steps: undefined,
      limit: 5,
    })
  })

  it('向量未就绪（1000018258）与其他错误统一静默清空', async () => {
    const vectorErr = new Error('向量能力未就绪') as Error & { code: number }
    vectorErr.code = 1000018258
    mocks.checkBugDuplicates.mockRejectedValueOnce(vectorErr)
    const { title, result } = setup()
    title.value = '登录无响应'
    await result.check()
    expect(result.items.value).toEqual([])

    mocks.checkBugDuplicates.mockRejectedValueOnce(new Error('boom'))
    await result.check()
    expect(result.items.value).toEqual([])
    expect(result.loading.value).toBe(false)
  })

  it('连续触发以最后一次结果为准，过期响应丢弃', async () => {
    const resolvers: Array<(value: { list: Array<Record<string, unknown>> }) => void> = []
    mocks.checkBugDuplicates.mockImplementation(
      () => new Promise((resolve) => { resolvers.push(resolve) }),
    )

    const { title, result } = setup()
    title.value = '旧输入'
    const first = result.check()
    title.value = '新输入'
    const second = result.check()

    resolvers[1]?.({ list: [{ bugId: 'b-new', title: '新结果', status: 'active', similarity: 0.8, basis: 'x' }] })
    await second
    expect(result.items.value[0]?.bugId).toBe('b-new')

    // 旧请求迟到 → 丢弃
    resolvers[0]?.({ list: [{ bugId: 'b-old', title: '旧结果', status: 'active', similarity: 0.9, basis: 'y' }] })
    await first
    expect(result.items.value[0]?.bugId).toBe('b-new')
    expect(result.loading.value).toBe(false)
  })

  it('dispose 后在途响应丢弃不写结果', async () => {
    let resolveFirst: ((value: { list: unknown[] }) => void) | undefined
    mocks.checkBugDuplicates.mockImplementationOnce(
      () => new Promise((resolve) => { resolveFirst = resolve }),
    )

    const { title, result } = setup()
    title.value = '登录无响应'
    const pending = result.check()
    result.dispose()
    resolveFirst?.({ list: [{ bugId: 'b1', title: 'x', status: 'active', similarity: 0.9, basis: 'y' }] })
    await pending
    expect(result.items.value).toEqual([])
  })
})
