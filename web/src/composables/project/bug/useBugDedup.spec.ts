import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AiBugDedupItem } from '@/types'

const mocks = vi.hoisted(() => ({
  dedupBugs: vi.fn<() => Promise<{ semanticDegraded: boolean; items: AiBugDedupItem[] }>>(),
}))

vi.mock('@/services/ai', () => ({
  dedupBugs: mocks.dedupBugs,
}))

import { useBugDedup } from './useBugDedup'

function makeItem(id: string, title: string): AiBugDedupItem {
  return {
    bugId: id,
    title,
    similarity: 0.8,
    status: 'active',
    assigneeName: '张三',
  }
}

function setupMocks(result?: { semanticDegraded: boolean; items: AiBugDedupItem[] }) {
  mocks.dedupBugs.mockResolvedValue(result ?? { semanticDegraded: false, items: [] })
}

describe('useBugDedup', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('初始状态', () => {
    it('items 为空数组', () => {
      const s = useBugDedup({ title: () => '' })
      expect(s.items.value).toEqual([])
    })

    it('semanticDegraded 为 false', () => {
      const s = useBugDedup({ title: () => '' })
      expect(s.semanticDegraded.value).toBe(false)
    })

    it('loading 为 false', () => {
      const s = useBugDedup({ title: () => '' })
      expect(s.loading.value).toBe(false)
    })
  })

  describe('run', () => {
    it('标题足够长时调用 dedupBugs', async () => {
      setupMocks({ semanticDegraded: false, items: [makeItem('b1', '相似缺陷')] })
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: undefined,
        excludeBugId: undefined,
      })
    })

    it('标题不足最小长度时不调用 dedupBugs', async () => {
      setupMocks()
      const s = useBugDedup({ title: () => '1234' })
      await s.run()
      expect(mocks.dedupBugs).not.toHaveBeenCalled()
    })

    it('标题恰好为最小长度时调用', async () => {
      setupMocks()
      const s = useBugDedup({ title: () => '12345' })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalled()
    })

    it('成功时更新 items 和 semanticDegraded', async () => {
      setupMocks({ semanticDegraded: true, items: [makeItem('b1', '重复缺陷')] })
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(s.items.value).toEqual([makeItem('b1', '重复缺陷')])
      expect(s.semanticDegraded.value).toBe(true)
    })

    it('失败时清空 items 和 semanticDegraded', async () => {
      mocks.dedupBugs.mockRejectedValue(new Error('网络错误'))
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(s.items.value).toEqual([])
      expect(s.semanticDegraded.value).toBe(false)
    })

    it('失败不抛出错误', async () => {
      mocks.dedupBugs.mockRejectedValue(new Error('网络错误'))
      const s = useBugDedup({ title: () => '测试标题内容' })
      await expect(s.run()).resolves.toBeUndefined()
    })

    it('失败非 Error 异常同样清空', async () => {
      mocks.dedupBugs.mockRejectedValue(42)
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(s.items.value).toEqual([])
      expect(s.semanticDegraded.value).toBe(false)
    })

    it('标题为空格时不调用 dedupBugs', async () => {
      setupMocks()
      const s = useBugDedup({ title: () => '     ' })
      await s.run()
      expect(mocks.dedupBugs).not.toHaveBeenCalled()
    })

    it('标题包含空格后长度不足时不调用', async () => {
      setupMocks()
      const s = useBugDedup({ title: () => '  123  ' })
      await s.run()
      expect(mocks.dedupBugs).not.toHaveBeenCalled()
    })

    it('传递 reproSteps', async () => {
      setupMocks()
      const s = useBugDedup({
        title: () => '测试标题内容',
        reproSteps: () => '复现步骤详情',
      })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: '复现步骤详情',
        excludeBugId: undefined,
      })
    })

    it('reproSteps 为空时传递 undefined', async () => {
      setupMocks()
      const s = useBugDedup({
        title: () => '测试标题内容',
        reproSteps: () => '',
      })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: undefined,
        excludeBugId: undefined,
      })
    })

    it('传递 excludeBugId', async () => {
      setupMocks()
      const s = useBugDedup({
        title: () => '测试标题内容',
        excludeBugId: () => 'bug-99',
      })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: undefined,
        excludeBugId: 'bug-99',
      })
    })

    it('excludeBugId 为 null 时传递 undefined', async () => {
      setupMocks()
      const s = useBugDedup({
        title: () => '测试标题内容',
        excludeBugId: () => null,
      })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: undefined,
        excludeBugId: undefined,
      })
    })

    it('excludeBugId 为 undefined 时传递 undefined', async () => {
      setupMocks()
      const s = useBugDedup({
        title: () => '测试标题内容',
        excludeBugId: () => undefined,
      })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: undefined,
        excludeBugId: undefined,
      })
    })

    it('reproSteps 未提供时传递 undefined', async () => {
      setupMocks()
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(mocks.dedupBugs).toHaveBeenCalledWith({
        title: '测试标题内容',
        reproSteps: undefined,
        excludeBugId: undefined,
      })
    })

    it('loading 在请求开始时为 true，结束后为 false', async () => {
      let resolve!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      mocks.dedupBugs.mockImplementation(() => new Promise((r) => { resolve = r }))
      const s = useBugDedup({ title: () => '测试标题内容' })
      const p = s.run()
      expect(s.loading.value).toBe(true)
      resolve({ semanticDegraded: false, items: [] })
      await p
      expect(s.loading.value).toBe(false)
    })

    it('失败后 loading 为 false', async () => {
      mocks.dedupBugs.mockRejectedValue(new Error('fail'))
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('并发过期响应丢弃', () => {
    it('第二次请求覆盖第一次结果', async () => {
      let resolveFirst!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      let resolveSecond!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      mocks.dedupBugs
        .mockImplementationOnce(() => new Promise((r) => { resolveFirst = r }))
        .mockImplementationOnce(() => new Promise((r) => { resolveSecond = r }))

      const s = useBugDedup({ title: () => '测试标题内容' })
      const p1 = s.run()
      const p2 = s.run()

      resolveFirst({ semanticDegraded: false, items: [makeItem('b1', '旧结果')] })
      resolveSecond({ semanticDegraded: true, items: [makeItem('b2', '新结果')] })

      await Promise.all([p1, p2])

      expect(s.items.value).toEqual([makeItem('b2', '新结果')])
      expect(s.semanticDegraded.value).toBe(true)
    })

    it('先到的慢响应不覆盖后到的快响应', async () => {
      let resolveFirst!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      let resolveSecond!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      mocks.dedupBugs
        .mockImplementationOnce(() => new Promise((r) => { resolveFirst = r }))
        .mockImplementationOnce(() => new Promise((r) => { resolveSecond = r }))

      const s = useBugDedup({ title: () => '测试标题内容' })
      const p1 = s.run()
      const p2 = s.run()

      resolveSecond({ semanticDegraded: true, items: [makeItem('b2', '快响应')] })
      await p2
      resolveFirst({ semanticDegraded: false, items: [makeItem('b1', '慢响应')] })
      await p1

      expect(s.items.value).toEqual([makeItem('b2', '快响应')])
      expect(s.semanticDegraded.value).toBe(true)
    })

    it('reset 后在途请求结果被丢弃', async () => {
      let resolve!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      mocks.dedupBugs.mockImplementation(() => new Promise((r) => { resolve = r }))

      const s = useBugDedup({ title: () => '测试标题内容' })
      const p = s.run()
      s.reset()
      resolve({ semanticDegraded: false, items: [makeItem('b1', '不应出现')] })
      await p

      expect(s.items.value).toEqual([])
      expect(s.semanticDegraded.value).toBe(false)
    })
  })

  describe('自定义 runner', () => {
    it('使用注入的 runner 而非 dedupBugs', async () => {
      const customRunner = vi.fn().mockResolvedValue({ semanticDegraded: false, items: [makeItem('b1', '自定义')] })
      const s = useBugDedup({
        title: () => '测试标题内容',
        run: customRunner,
      })
      await s.run()
      expect(customRunner).toHaveBeenCalled()
      expect(mocks.dedupBugs).not.toHaveBeenCalled()
      expect(s.items.value).toEqual([makeItem('b1', '自定义')])
    })

    it('自定义 runner 失败时同样清空', async () => {
      const customRunner = vi.fn().mockRejectedValue(new Error('自定义错误'))
      const s = useBugDedup({
        title: () => '测试标题内容',
        run: customRunner,
      })
      await s.run()
      expect(s.items.value).toEqual([])
      expect(s.semanticDegraded.value).toBe(false)
    })
  })

  describe('reset', () => {
    it('清空 items', () => {
      const s = useBugDedup({ title: () => '测试标题内容' })
      s.items.value = [makeItem('b1', '残留')]
      s.reset()
      expect(s.items.value).toEqual([])
    })

    it('清空 semanticDegraded', () => {
      const s = useBugDedup({ title: () => '测试标题内容' })
      s.semanticDegraded.value = true
      s.reset()
      expect(s.semanticDegraded.value).toBe(false)
    })

    it('清空 loading', () => {
      const s = useBugDedup({ title: () => '测试标题内容' })
      s.loading.value = true
      s.reset()
      expect(s.loading.value).toBe(false)
    })

    it('作废在途请求', async () => {
      let resolve!: (v: { semanticDegraded: boolean; items: AiBugDedupItem[] }) => void
      mocks.dedupBugs.mockImplementation(() => new Promise((r) => { resolve = r }))

      const s = useBugDedup({ title: () => '测试标题内容' })
      const p = s.run()
      s.reset()
      resolve({ semanticDegraded: true, items: [makeItem('b1', '旧')] })
      await p

      expect(s.items.value).toEqual([])
      expect(s.semanticDegraded.value).toBe(false)
      expect(s.loading.value).toBe(false)
    })

    it('多次 reset 不影响状态', () => {
      const s = useBugDedup({ title: () => '测试标题内容' })
      s.reset()
      s.reset()
      expect(s.items.value).toEqual([])
      expect(s.semanticDegraded.value).toBe(false)
      expect(s.loading.value).toBe(false)
    })
  })

  describe('run 后 reset 后再 run', () => {
    it('正常工作', async () => {
      setupMocks({ semanticDegraded: false, items: [makeItem('b1', '第一次')] })
      const s = useBugDedup({ title: () => '测试标题内容' })
      await s.run()
      expect(s.items.value).toEqual([makeItem('b1', '第一次')])
      s.reset()
      expect(s.items.value).toEqual([])
      setupMocks({ semanticDegraded: true, items: [makeItem('b2', '第二次')] })
      await s.run()
      expect(s.items.value).toEqual([makeItem('b2', '第二次')])
      expect(s.semanticDegraded.value).toBe(true)
    })
  })
})
