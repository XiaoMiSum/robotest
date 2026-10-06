// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { PageResult, TraceGap } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchTraceGaps: vi.fn(),
  getRequirement: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  router: { push: vi.fn() },
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchTraceGaps: mocks.fetchTraceGaps,
  getRequirement: mocks.getRequirement,
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useTraceGaps } from './useTraceGaps'
import { useTraceStore } from '@/stores/trace'

function makeGap(overrides: Partial<TraceGap> = {}): TraceGap {
  return {
    targetType: 'requirement',
    targetId: 'r1',
    title: 'REQ-001 登录',
    coverageStatus: 'uncovered',
    suggestedAction: 'review',
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
  setActivePinia(createPinia())
  mocks.fetchTraceGaps.mockResolvedValue({ list: [makeGap()], total: 1 })
}

describe('useTraceGaps', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('加载与分页', () => {
    it('首次加载按 store 初始缺口类型取数并映射视图', async () => {
      const s = useTraceGaps()
      expect(s.gapType.value).toBe('uncovered_requirement')
      await s.load()
      expect(mocks.fetchTraceGaps).toHaveBeenCalledWith({
        type: 'uncovered_requirement',
        pageNo: 1,
        pageSize: 20,
      })
      expect(s.gaps.value).toHaveLength(1)
      expect(s.gaps.value[0].targetType).toBe('requirement')
      expect(s.gaps.value[0].actionMeta.label).toBe('发起评审')
      expect(s.gaps.value[0].actionMeta.disabled).toBe(false)
      expect(s.total.value).toBe(1)
      expect(s.loading.value).toBe(false)
      expect(s.loadError.value).toBe('')
    })

    it('加载失败清空列表记录错误并可重试', async () => {
      const s = useTraceGaps()
      mocks.fetchTraceGaps.mockRejectedValueOnce(new Error('服务不可用'))
      await s.load()
      expect(s.gaps.value).toEqual([])
      expect(s.loadError.value).toBe('服务不可用')
      expect(s.loading.value).toBe(false)

      s.retry()
      await vi.waitFor(() => expect(s.gaps.value).toHaveLength(1))
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('非 Error 异常回退默认文案', async () => {
      mocks.fetchTraceGaps.mockRejectedValueOnce('boom')
      const s = useTraceGaps()
      await s.load()
      expect(s.loadError.value).toBe('加载缺口清单失败')
    })

    it('过期响应不覆盖新结果', async () => {
      const slow = deferred<PageResult<TraceGap>>()
      mocks.fetchTraceGaps.mockReturnValueOnce(slow.promise)
      const s = useTraceGaps()
      const first = s.load()

      mocks.fetchTraceGaps.mockResolvedValueOnce({
        list: [makeGap({ title: '新结果' })],
        total: 2,
      })
      const second = s.load()
      await second
      expect(s.gaps.value[0].title).toBe('新结果')
      expect(s.total.value).toBe(2)

      slow.resolve({ list: [makeGap({ title: '旧结果' })], total: 9 })
      await first
      expect(s.gaps.value[0].title).toBe('新结果')
      expect(s.total.value).toBe(2)
      expect(s.loading.value).toBe(false)
    })

    it('切换缺口类型复位页码并按新类型取数', async () => {
      const s = useTraceGaps()
      await s.load()
      s.changePage(2)
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      expect(s.pageNo.value).toBe(2)

      s.setType('orphan_case')
      expect(s.gapType.value).toBe('orphan_case')
      expect(s.pageNo.value).toBe(1)
      // watch(gapType) 也会补一次取数，允许调用次数大于一次，只断言最终口径
      await vi.waitFor(() =>
        expect(mocks.fetchTraceGaps).toHaveBeenLastCalledWith({
          type: 'orphan_case',
          pageNo: 1,
          pageSize: 20,
        }),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      expect(s.gaps.value).toHaveLength(1)
    })

    it('changePage 与 changePageSize 的取数参数', async () => {
      const s = useTraceGaps()
      await s.load()

      s.changePage(3)
      await vi.waitFor(() =>
        expect(mocks.fetchTraceGaps).toHaveBeenLastCalledWith({
          type: 'uncovered_requirement',
          pageNo: 3,
          pageSize: 20,
        }),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))

      s.changePageSize(50)
      expect(s.pageNo.value).toBe(1)
      expect(s.pageSize.value).toBe(50)
      await vi.waitFor(() =>
        expect(mocks.fetchTraceGaps).toHaveBeenLastCalledWith({
          type: 'uncovered_requirement',
          pageNo: 1,
          pageSize: 50,
        }),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
    })
  })

  describe('链路入口', () => {
    it('需求缺口以需求为链路起点', async () => {
      const traceStore = useTraceStore()
      const s = useTraceGaps()
      await s.load()
      s.openChain(s.gaps.value[0])
      expect(traceStore.chainOrigin).toEqual({
        type: 'requirement',
        id: 'r1',
        title: 'REQ-001 登录',
      })
    })

    it('用例缺口以测试用例为链路起点', async () => {
      mocks.fetchTraceGaps.mockResolvedValue({
        list: [makeGap({ targetType: 'test_case', targetId: 'tc1', title: '登录成功用例' })],
        total: 1,
      })
      const traceStore = useTraceStore()
      const s = useTraceGaps()
      await s.load()
      s.openChain(s.gaps.value[0])
      expect(traceStore.chainOrigin).toEqual({
        type: 'test_case',
        id: 'tc1',
        title: '登录成功用例',
      })
    })
  })

  describe('引导动作', () => {
    it('generate 动作就地打开生成配置对话框并回显缺口需求', async () => {
      mocks.fetchTraceGaps.mockResolvedValue({
        list: [makeGap({ suggestedAction: 'generate' })],
        total: 1,
      })
      mocks.getRequirement.mockResolvedValue({
        id: 'r1',
        code: 'REQ-001',
        title: '登录',
        status: 'confirmed',
      })
      mocks.fetchProjectModuleTree.mockResolvedValue([
        { id: 'm1', type: 'directory', children: [] },
      ])
      const s = useTraceGaps()
      await s.load()

      s.runAction(s.gaps.value[0])
      await new Promise((resolve) => setTimeout(resolve, 0))

      expect(s.generationDialogVisible.value).toBe(true)
      expect(s.generationScope.value).toEqual([
        { id: 'r1', code: 'REQ-001', title: '登录', status: 'confirmed' },
      ])
      expect(s.generationModuleTree.value).toHaveLength(1)
      expect(mocks.router.push).not.toHaveBeenCalled()
    })

    it('generate 读取需求失败时提示且不打开对话框', async () => {
      mocks.fetchTraceGaps.mockResolvedValue({
        list: [makeGap({ suggestedAction: 'generate' })],
        total: 1,
      })
      mocks.getRequirement.mockRejectedValue(new Error('需求不存在'))
      const s = useTraceGaps()
      await s.load()

      s.runAction(s.gaps.value[0])
      await new Promise((resolve) => setTimeout(resolve, 0))

      expect(mocks.ElMessage.error).toHaveBeenCalledWith('需求不存在')
      expect(s.generationDialogVisible.value).toBe(false)
    })

    it('review 动作跳转评审入口', async () => {
      mocks.fetchTraceGaps.mockResolvedValue({
        list: [makeGap({ suggestedAction: 'review' })],
        total: 1,
      })
      const s = useTraceGaps()
      await s.load()
      s.runAction(s.gaps.value[0])
      expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/reviews')
      expect(mocks.ElMessage.info).not.toHaveBeenCalled()
    })

    it('schedule 动作跳转计划入口', async () => {
      mocks.fetchTraceGaps.mockResolvedValue({
        list: [makeGap({ suggestedAction: 'schedule' })],
        total: 1,
      })
      const s = useTraceGaps()
      await s.load()
      s.runAction(s.gaps.value[0])
      expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/plans')
      expect(mocks.ElMessage.info).not.toHaveBeenCalled()
    })
  })

  describe('typeOptions', () => {
    it('四项选项与缺口类型元数据一致', () => {
      const s = useTraceGaps()
      expect(s.typeOptions.value).toEqual([
        { value: 'uncovered_requirement', label: '未生成用例', desc: '需求尚无派生用例' },
        { value: 'orphan_case', label: '未关联需求', desc: '用例未回溯到任何需求' },
        { value: 'unreviewed_case', label: '未评审', desc: '用例未进入评审' },
        { value: 'unscheduled_case', label: '未进计划', desc: '用例未纳入测试计划' },
      ])
    })
  })
})
