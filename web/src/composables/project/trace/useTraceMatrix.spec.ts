// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { PageResult, TraceMatrixRow } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchTraceMatrix: vi.fn(),
  fetchAiStatus: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchTraceMatrix: mocks.fetchTraceMatrix,
}))

vi.mock('@/services/ai', () => ({
  fetchAiStatus: mocks.fetchAiStatus,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useTraceMatrix } from './useTraceMatrix'
import { useTraceStore } from '@/stores/trace'

type TraceMatrixState = ReturnType<typeof useTraceMatrix>

function makeRow(overrides: Partial<TraceMatrixRow> = {}): TraceMatrixRow {
  return {
    requirementId: 'r1',
    code: 'REQ-001',
    title: '登录验证码',
    status: 'draft',
    coverageStatus: 'pending',
    edgeCounts: { module: 1, document: 0, testCase: 2, review: 0, plan: 0 },
    staleCount: 0,
    conflictCount: 0,
    ...overrides,
  }
}

function page(list: TraceMatrixRow[], total = list.length): PageResult<TraceMatrixRow> {
  return { list, total }
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
  setActivePinia(createPinia())
  mocks.fetchTraceMatrix.mockResolvedValue(page([makeRow()]))
  mocks.fetchAiStatus.mockResolvedValue({ enabled: true, modelReady: true, available: true })
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => true) })
}

/** composable 的初始化在 onMounted（保活回填 + 首屏加载），需经真实组件挂载触发 */
function mountMatrix(): { state: TraceMatrixState } {
  let state: TraceMatrixState | null = null
  mount(
    defineComponent({
      setup() {
        state = useTraceMatrix()
        return () => h('div')
      },
    }),
  )
  if (state === null) throw new Error('composable 未初始化')
  return { state }
}

/** 直连调用（不触发 onMounted）：用于只验证手动 load / search 的用例 */
function direct(): TraceMatrixState {
  return useTraceMatrix()
}

describe('useTraceMatrix', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('AI 可用性', () => {
    it('状态接口返回可用时置 aiAvailable', async () => {
      const { state } = mountMatrix()
      await vi.waitFor(() => expect(state.aiStatusLoaded.value).toBe(true))
      expect(state.aiAvailable.value).toBe(true)
      expect(mocks.fetchAiStatus).toHaveBeenCalledTimes(1)
    })

    it('状态接口抛错按不可用处理且完成标记置真（catch 分支）', async () => {
      mocks.fetchAiStatus.mockRejectedValueOnce(new Error('down'))
      const { state } = mountMatrix()
      await vi.waitFor(() => expect(state.aiStatusLoaded.value).toBe(true))
      expect(state.aiAvailable.value).toBe(false)
    })
  })

  describe('首屏加载', () => {
    it('挂载同时发起 AI 状态与矩阵加载，行映射为视图模型', async () => {
      mocks.fetchTraceMatrix.mockResolvedValue(
        page([
          makeRow(),
          makeRow({
            requirementId: 'r2',
            code: 'REQ-002',
            title: '支付回调',
            status: 'confirmed',
            coverageStatus: 'covered',
            staleCount: 2,
            conflictCount: 1,
          }),
        ]),
      )
      const { state } = mountMatrix()
      await vi.waitFor(() => expect(state.hasLoaded.value).toBe(true))
      expect(mocks.fetchAiStatus).toHaveBeenCalledTimes(1)
      expect(mocks.fetchTraceMatrix).toHaveBeenLastCalledWith({
        requirementStatus: undefined,
        coverage: undefined,
        keyword: undefined,
        pageNo: 1,
        pageSize: 20,
      })

      expect(state.rows.value).toHaveLength(2)
      const [first, second] = state.rows.value
      expect(first.code).toBe('REQ-001')
      expect(first.coverageMeta).toEqual({ label: '待分析', tagType: 'info' })
      expect(first.statusMeta.label).toBe('草稿')
      expect(first.highlight).toEqual({ warning: false, danger: false })
      expect(first.hint).toBe('')
      expect(second.coverageMeta).toEqual({ label: '完整覆盖', tagType: 'success' })
      expect(second.statusMeta.label).toBe('已确认')
      expect(second.highlight).toEqual({ warning: true, danger: true })
      expect(second.hint).toBe('2 条边目标版本已变更，需重新确认；1 条边人工与 AI 结论冲突')

      expect(state.total.value).toBe(2)
      expect(state.loadError.value).toBe('')
      expect(state.loading.value).toBe(false)
    })

    it('加载失败记录页面级错误并可重试', async () => {
      mocks.fetchTraceMatrix.mockRejectedValueOnce(new Error('服务不可用'))
      const { state } = mountMatrix()
      await vi.waitFor(() => expect(state.loadError.value).toBe('服务不可用'))
      expect(state.hasLoaded.value).toBe(false)
      expect(state.loading.value).toBe(false)

      state.retry()
      await vi.waitFor(() => expect(state.hasLoaded.value).toBe(true))
      expect(state.loadError.value).toBe('')
      expect(mocks.fetchTraceMatrix.mock.calls.length).toBeGreaterThanOrEqual(2)
    })

    it('过期响应被丢弃，不覆盖新结果', async () => {
      const s = direct()
      const stale = deferred<PageResult<TraceMatrixRow>>()
      mocks.fetchTraceMatrix.mockReturnValueOnce(stale.promise)
      const first = s.load()

      mocks.fetchTraceMatrix.mockResolvedValueOnce(page([makeRow({ requirementId: 'r2' })], 1))
      await s.load()
      expect(s.rows.value[0].requirementId).toBe('r2')
      expect(s.total.value).toBe(1)

      stale.resolve(page([makeRow({ requirementId: 'r1' })], 9))
      await first
      expect(s.rows.value[0].requirementId).toBe('r2')
      expect(s.total.value).toBe(1)
    })
  })

  describe('筛选', () => {
    it('search 携带筛选条件并重置到第一页', () => {
      const s = direct()
      const traceStore = useTraceStore()
      traceStore.pageNo = 3
      s.filters.requirementStatus = 'confirmed'
      s.filters.coverage = 'covered'
      s.filters.keyword = ' 登录 '

      s.search()
      expect(traceStore.pageNo).toBe(1)
      expect(s.pageNo.value).toBe(1)
      expect(mocks.fetchTraceMatrix).toHaveBeenLastCalledWith({
        requirementStatus: 'confirmed',
        coverage: 'covered',
        keyword: '登录',
        pageNo: 1,
        pageSize: 20,
      })
      expect(s.filterCount.value).toBe(3)
    })

    it('resetFilters 清空条件并以空参数重新加载', () => {
      const s = direct()
      const traceStore = useTraceStore()
      s.filters.requirementStatus = 'confirmed'
      s.filters.keyword = 'abc'
      traceStore.pageNo = 5

      s.resetFilters()
      expect(s.filters).toEqual({ requirementStatus: '', coverage: '', keyword: '' })
      expect(traceStore.filterCount).toBe(0)
      expect(traceStore.pageNo).toBe(1)
      expect(mocks.fetchTraceMatrix).toHaveBeenLastCalledWith({
        requirementStatus: undefined,
        coverage: undefined,
        keyword: undefined,
        pageNo: 1,
        pageSize: 20,
      })
    })
  })

  describe('关键词防抖', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('关键词变化 1 秒后才触发查询', async () => {
      const s = direct()
      await s.load()
      const baseline = mocks.fetchTraceMatrix.mock.calls.length

      s.filters.keyword = '登'
      await nextTick()
      await vi.advanceTimersByTimeAsync(999)
      expect(mocks.fetchTraceMatrix.mock.calls.length).toBe(baseline)

      await vi.advanceTimersByTimeAsync(1)
      expect(mocks.fetchTraceMatrix.mock.calls.length).toBe(baseline + 1)
      expect(mocks.fetchTraceMatrix).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: '登' }),
      )
    })

    it('trim 后与上次搜索值相同则不重复查询', async () => {
      const s = direct()
      s.filters.keyword = '登录'
      s.search()
      const baseline = mocks.fetchTraceMatrix.mock.calls.length

      s.filters.keyword = '登录 '
      await nextTick()
      await vi.advanceTimersByTimeAsync(1200)
      expect(mocks.fetchTraceMatrix.mock.calls.length).toBe(baseline)
    })
  })

  describe('分页', () => {
    it('changePage 与 changePageSize 更新 store 并重新加载', () => {
      const s = direct()
      const traceStore = useTraceStore()

      s.changePage(3)
      expect(traceStore.pageNo).toBe(3)
      expect(s.pageNo.value).toBe(3)
      expect(mocks.fetchTraceMatrix).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageNo: 3, pageSize: 20 }),
      )

      s.changePageSize(50)
      expect(traceStore.pageSize).toBe(50)
      expect(traceStore.pageNo).toBe(1)
      expect(s.pageSize.value).toBe(50)
      expect(mocks.fetchTraceMatrix).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageNo: 1, pageSize: 50 }),
      )
    })
  })

  describe('链路入口', () => {
    it('openChain 以需求节点写入抽屉起点', () => {
      const s = direct()
      const traceStore = useTraceStore()
      s.openChain(makeRow())
      expect(traceStore.chainOrigin).toEqual({
        type: 'requirement',
        id: 'r1',
        title: 'REQ-001 登录验证码',
      })
    })
  })

  describe('权限', () => {
    it('hasPermission 返回 true 时可编辑', () => {
      mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => true) })
      const s = direct()
      expect(s.canEdit.value).toBe(true)
    })

    it('hasPermission 返回 false 时只读', () => {
      mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => false) })
      const s = direct()
      expect(s.canEdit.value).toBe(false)
    })
  })
})
