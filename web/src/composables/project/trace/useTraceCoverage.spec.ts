// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult, TraceCoverage, TraceCoveragePatchPayload } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchTraceCoverage: vi.fn(),
  patchTraceCoverage: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchTraceCoverage: mocks.fetchTraceCoverage,
  patchTraceCoverage: mocks.patchTraceCoverage,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useTraceCoverage } from './useTraceCoverage'

function makeCoverage(overrides: Partial<TraceCoverage> = {}): TraceCoverage {
  return {
    requirementId: 'r1',
    coverageStatus: 'covered',
    evidence: null,
    aiAnalyzedAt: null,
    reviewedBy: null,
    reviewedNote: null,
    reviewedAt: null,
    analyzedTaskId: null,
    ...overrides,
  }
}

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function setup(): void {
  mocks.fetchTraceCoverage.mockResolvedValue({ list: [makeCoverage()], total: 1 })
  mocks.patchTraceCoverage.mockResolvedValue(makeCoverage())
}

describe('useTraceCoverage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('openFor 与 load', () => {
    it('切换需求时先清空记录再按单条取数', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      expect(mocks.fetchTraceCoverage).toHaveBeenCalledWith({
        requirementIds: 'r1',
        pageNo: 1,
        pageSize: 1,
      })
      expect(s.record.value?.requirementId).toBe('r1')

      const slow = deferred<PageResult<TraceCoverage>>()
      mocks.fetchTraceCoverage.mockReturnValueOnce(slow.promise)
      const pending = s.openFor('r2')
      // 新需求的旧记录不可见，避免展示串需求的覆盖结论
      expect(s.requirementId.value).toBe('r2')
      expect(s.record.value).toBeNull()
      slow.resolve({ list: [makeCoverage({ requirementId: 'r2' })], total: 1 })
      await pending
      expect(s.record.value?.requirementId).toBe('r2')
    })

    it('相同需求再次打开直接加载且不清空记录', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      expect(s.record.value).not.toBeNull()

      const slow = deferred<PageResult<TraceCoverage>>()
      mocks.fetchTraceCoverage.mockReturnValueOnce(slow.promise)
      const pending = s.openFor('r1')
      expect(s.record.value?.requirementId).toBe('r1')
      slow.resolve({ list: [makeCoverage({ coverageStatus: 'partial' })], total: 1 })
      await pending
      expect(s.record.value?.coverageStatus).toBe('partial')
    })

    it('接口返回空列表时记录置空', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      expect(s.record.value).not.toBeNull()
      mocks.fetchTraceCoverage.mockResolvedValueOnce({ list: [], total: 0 })
      await s.load()
      expect(s.record.value).toBeNull()
    })

    it('加载失败记录错误、清空记录并可重试', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      expect(s.record.value).not.toBeNull()

      mocks.fetchTraceCoverage.mockRejectedValueOnce(new Error('服务不可用'))
      await s.load()
      expect(s.record.value).toBeNull()
      expect(s.loadError.value).toBe('服务不可用')
      expect(s.loading.value).toBe(false)

      s.retry()
      await vi.waitFor(() => expect(s.record.value?.requirementId).toBe('r1'))
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('非 Error 异常回退默认文案', async () => {
      const s = useTraceCoverage()
      mocks.fetchTraceCoverage.mockRejectedValueOnce('boom')
      await s.openFor('r1')
      expect(s.loadError.value).toBe('加载覆盖结论失败')
    })

    it('requirementId 为空时 load 直接返回不发请求', async () => {
      const s = useTraceCoverage()
      await s.load()
      expect(mocks.fetchTraceCoverage).not.toHaveBeenCalled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('save', () => {
    it('保存成功写回记录、提示并自增修订号', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      const payload: TraceCoveragePatchPayload = { coverageStatus: 'partial', note: '人工确认' }
      mocks.patchTraceCoverage.mockResolvedValue(
        makeCoverage({ coverageStatus: 'partial', reviewedNote: '人工确认' }),
      )

      const ok = await s.save(payload)
      expect(mocks.patchTraceCoverage).toHaveBeenCalledWith('r1', payload)
      expect(s.record.value?.coverageStatus).toBe('partial')
      expect(s.record.value?.reviewedNote).toBe('人工确认')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith(
        '覆盖结论已更新，后续 AI 分析不再覆盖人工判定',
      )
      expect(s.revision.value).toBe(1)
      expect(ok).toBe(true)
      expect(s.saving.value).toBe(false)
    })

    it('保存失败提示错误且修订号不变', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      mocks.patchTraceCoverage.mockRejectedValueOnce(new Error('覆盖修正失败'))

      const ok = await s.save({ coverageStatus: 'partial' })
      expect(ok).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('覆盖修正失败')
      expect(s.revision.value).toBe(0)
      expect(s.saving.value).toBe(false)
    })

    it('requirementId 为空时 save 返回 false 不调接口', async () => {
      const s = useTraceCoverage()
      const ok = await s.save({ coverageStatus: 'partial' })
      expect(ok).toBe(false)
      expect(mocks.patchTraceCoverage).not.toHaveBeenCalled()
      expect(s.saving.value).toBe(false)
    })
  })

  describe('close', () => {
    it('关闭后清空需求、记录与错误', async () => {
      const s = useTraceCoverage()
      await s.openFor('r1')
      expect(s.record.value).not.toBeNull()
      s.close()
      expect(s.requirementId.value).toBe('')
      expect(s.record.value).toBeNull()
      expect(s.loadError.value).toBe('')

      // 错误态同样要清掉，否则重开面板会残留上一次的失败文案
      mocks.fetchTraceCoverage.mockRejectedValueOnce(new Error('服务不可用'))
      await s.openFor('r2')
      expect(s.loadError.value).toBe('服务不可用')
      s.close()
      expect(s.loadError.value).toBe('')
      expect(s.record.value).toBeNull()
    })
  })
})
