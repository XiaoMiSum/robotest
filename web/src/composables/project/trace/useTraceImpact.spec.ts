// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, PageResult, TraceImpactItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchTraceImpactItems: vi.fn(),
  patchTraceImpactItem: vi.fn(),
  submitAiTask: vi.fn(),
  retryAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  hasPermission: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchTraceImpactItems: mocks.fetchTraceImpactItems,
  patchTraceImpactItem: mocks.patchTraceImpactItem,
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  retryAiTask: mocks.retryAiTask,
  fetchAiTask: mocks.fetchAiTask,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
}))

import { useTraceImpact } from './useTraceImpact'
import { useAiTaskStore } from '@/stores/aiTask'

function makeItem(overrides: Partial<TraceImpactItem> = {}): TraceImpactItem {
  return {
    edgeId: 'edge-1',
    target: { type: 'test_case', id: 'tc1', title: '登录成功用例', version: null },
    impactType: 'derivation',
    disposition: 'pending',
    reason: null,
    disposedBy: null,
    ...overrides,
  }
}

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 'task-1',
    type: 'impact_analysis',
    status: 'succeeded',
    progress: 100,
    phase: null,
    submittedBy: 'u1',
    retryOfTaskId: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-03T01:00:00Z',
    error: null,
    result: null,
    documentMeta: null,
    artifacts: null,
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
  mocks.fetchTraceImpactItems.mockResolvedValue({ list: [makeItem()], total: 1 })
  mocks.patchTraceImpactItem.mockResolvedValue(
    makeItem({ disposition: 'no_impact', reason: '已核对' }),
  )
  mocks.submitAiTask.mockResolvedValue({ taskId: 'task-1', status: 'pending' })
  mocks.retryAiTask.mockResolvedValue({ taskId: 'task-2', status: 'pending' })
  mocks.hasPermission.mockReturnValue(true)
}

function create(): ReturnType<typeof useTraceImpact> {
  const s = useTraceImpact()
  // 测试进程无轮询消费者，替换为空实现避免真实定时器泄漏
  useAiTaskStore().startPolling = vi.fn()
  return s
}

describe('useTraceImpact', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('加载与分页', () => {
    it('openFor 切换需求先复位再取数并映射处置视图', async () => {
      const s = create()
      await s.openFor('r1')
      expect(mocks.fetchTraceImpactItems).toHaveBeenCalledWith({
        requirementId: 'r1',
        pageNo: 1,
        pageSize: 20,
      })
      expect(s.items.value).toHaveLength(1)
      expect(s.items.value[0].dispositionMeta.label).toBe('待处置')
      expect(s.total.value).toBe(1)

      s.changePage(2)
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      expect(s.pageNo.value).toBe(2)

      const slow = deferred<PageResult<TraceImpactItem>>()
      mocks.fetchTraceImpactItems.mockReturnValueOnce(slow.promise)
      const pending = s.openFor('r2')
      // 旧需求的受影响项不可见，避免串需求展示
      expect(s.items.value).toEqual([])
      expect(s.total.value).toBe(0)
      expect(s.pageNo.value).toBe(1)
      expect(mocks.fetchTraceImpactItems).toHaveBeenLastCalledWith({
        requirementId: 'r2',
        pageNo: 1,
        pageSize: 20,
      })
      slow.resolve({ list: [makeItem({ edgeId: 'edge-2' })], total: 1 })
      await pending
      expect(s.items.value[0].edgeId).toBe('edge-2')
    })

    it('requirementId 为空时 load 跳过请求', async () => {
      const s = create()
      await s.load()
      expect(mocks.fetchTraceImpactItems).not.toHaveBeenCalled()
      expect(s.loading.value).toBe(false)
    })

    it('加载失败记录错误并可重试', async () => {
      const s = create()
      mocks.fetchTraceImpactItems.mockRejectedValueOnce(new Error('服务不可用'))
      await s.openFor('r1')
      expect(s.items.value).toEqual([])
      expect(s.loadError.value).toBe('服务不可用')
      expect(s.loading.value).toBe(false)

      s.retry()
      await vi.waitFor(() => expect(s.items.value).toHaveLength(1))
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('非 Error 异常回退默认文案', async () => {
      const s = create()
      mocks.fetchTraceImpactItems.mockRejectedValueOnce('boom')
      await s.openFor('r1')
      expect(s.loadError.value).toBe('加载受影响项失败')
    })

    it('过期响应不覆盖新结果', async () => {
      const slow = deferred<PageResult<TraceImpactItem>>()
      mocks.fetchTraceImpactItems.mockReturnValueOnce(slow.promise)
      const s = create()
      const first = s.openFor('r1')

      mocks.fetchTraceImpactItems.mockResolvedValueOnce({
        list: [makeItem({ edgeId: 'edge-new' })],
        total: 1,
      })
      const second = s.openFor('r1')
      await second
      expect(s.items.value[0].edgeId).toBe('edge-new')

      slow.resolve({ list: [makeItem({ edgeId: 'edge-old' })], total: 9 })
      await first
      expect(s.items.value[0].edgeId).toBe('edge-new')
      expect(s.total.value).toBe(1)
      expect(s.loading.value).toBe(false)
    })

    it('changePage 与 changePageSize 的取数参数', async () => {
      const s = create()
      await s.openFor('r1')

      s.changePage(3)
      await vi.waitFor(() =>
        expect(mocks.fetchTraceImpactItems).toHaveBeenLastCalledWith({
          requirementId: 'r1',
          pageNo: 3,
          pageSize: 20,
        }),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))

      s.changePageSize(50)
      expect(s.pageNo.value).toBe(1)
      expect(s.pageSize.value).toBe(50)
      await vi.waitFor(() =>
        expect(mocks.fetchTraceImpactItems).toHaveBeenLastCalledWith({
          requirementId: 'r1',
          pageNo: 1,
          pageSize: 50,
        }),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
    })
  })

  describe('处置', () => {
    it('处置成功更新行、提示并自增刷新信号', async () => {
      const s = create()
      await s.openFor('r1')
      const row = s.items.value[0]

      const ok = await s.dispose(row, 'no_impact', '理由')
      expect(mocks.patchTraceImpactItem).toHaveBeenCalledWith('edge-1', {
        disposition: 'no_impact',
        reason: '理由',
      })
      expect(s.items.value[0].disposition).toBe('no_impact')
      expect(s.items.value[0].dispositionMeta.label).toBe('确认无影响')
      expect(s.items.value[0].reason).toBe('已核对')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('处置已记录')
      expect(s.refreshSignal.value).toBe(1)
      expect(ok).toBe(true)
      expect(s.busy.value).toBe(false)
    })

    it('处置失败提示错误且刷新信号不变', async () => {
      const s = create()
      await s.openFor('r1')
      const row = s.items.value[0]
      mocks.patchTraceImpactItem.mockRejectedValueOnce(new Error('处置失败'))

      const ok = await s.dispose(row, 'no_impact', '理由')
      expect(ok).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('处置失败')
      expect(s.refreshSignal.value).toBe(0)
      expect(s.items.value[0].disposition).toBe('pending')
      expect(s.busy.value).toBe(false)
    })
  })

  describe('影响分析', () => {
    it('发起成功记录任务并启动轮询', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')

      const ok = await s.analyze()
      expect(mocks.submitAiTask).toHaveBeenCalledWith('impact_analysis', { requirementId: 'r1' })
      expect(aiStore.startPolling).toHaveBeenCalledWith('task-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith(
        '影响分析已发起，完成后自动刷新受影响项',
      )
      expect(s.taskFailed.value).toBe(false)
      expect(s.taskError.value).toBe('')
      expect(ok).toBe(true)
      expect(s.busy.value).toBe(false)
    })

    it('requirementId 为空时 analyze 直接失败', async () => {
      const s = create()
      const ok = await s.analyze()
      expect(ok).toBe(false)
      expect(mocks.submitAiTask).not.toHaveBeenCalled()
    })

    it('发起失败记录任务错误并提示', async () => {
      const s = create()
      await s.openFor('r1')
      mocks.submitAiTask.mockRejectedValueOnce(new Error('未开启'))

      const ok = await s.analyze()
      expect(ok).toBe(false)
      expect(s.taskFailed.value).toBe(true)
      expect(s.taskError.value).toBe('未开启')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('未开启')
      expect(s.busy.value).toBe(false)
    })

    it('无运行任务时 retryAnalyze 委托 analyze', async () => {
      const s = create()
      await s.openFor('r1')

      const ok = await s.retryAnalyze()
      expect(ok).toBe(true)
      expect(mocks.submitAiTask).toHaveBeenCalledWith('impact_analysis', { requirementId: 'r1' })
      expect(mocks.retryAiTask).not.toHaveBeenCalled()
    })

    it('已有任务时换新任务轮询并复位失败态', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()

      aiStore.detail = makeDetail({
        taskId: 'task-1',
        status: 'failed',
        error: { code: 1, msg: '模型超时' },
      })
      await nextTick()
      expect(s.taskFailed.value).toBe(true)

      const ok = await s.retryAnalyze()
      expect(ok).toBe(true)
      expect(mocks.retryAiTask).toHaveBeenCalledWith('task-1')
      expect(aiStore.startPolling).toHaveBeenCalledWith('task-2')
      expect(s.taskFailed.value).toBe(false)
      expect(s.taskError.value).toBe('')
      expect(s.busy.value).toBe(false)
      // 详情仍是旧任务，与新的 runningTaskId 不再匹配
      expect(s.taskRunning.value).toBe(false)
    })

    it('任务重试失败提示错误', async () => {
      const s = create()
      await s.openFor('r1')
      await s.analyze()
      mocks.retryAiTask.mockRejectedValueOnce(new Error('任务不存在'))

      const ok = await s.retryAnalyze()
      expect(ok).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('任务不存在')
      expect(s.busy.value).toBe(false)
    })
  })

  describe('任务回执 watch', () => {
    it('本面板任务成功后自增刷新信号并重载受影响项', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()
      const calls = mocks.fetchTraceImpactItems.mock.calls.length

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'succeeded' })
      await nextTick()
      expect(s.refreshSignal.value).toBe(1)
      await vi.waitFor(() =>
        expect(mocks.fetchTraceImpactItems.mock.calls.length).toBe(calls + 1),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
    })

    it('非本面板任务的回执不触发刷新', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()
      const calls = mocks.fetchTraceImpactItems.mock.calls.length

      aiStore.detail = makeDetail({ taskId: 'other-task', status: 'succeeded' })
      await nextTick()
      expect(s.refreshSignal.value).toBe(0)
      expect(mocks.fetchTraceImpactItems.mock.calls.length).toBe(calls)
    })

    it('任务失败取错误消息并自增刷新信号', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()

      aiStore.detail = makeDetail({
        taskId: 'task-1',
        status: 'failed',
        error: { code: 1, msg: '模型超时' },
      })
      await nextTick()
      expect(s.taskFailed.value).toBe(true)
      expect(s.taskError.value).toBe('模型超时')
      expect(s.refreshSignal.value).toBe(1)
    })

    it('失败无错误消息时回退默认文案', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'failed', error: null })
      await nextTick()
      expect(s.taskFailed.value).toBe(true)
      expect(s.taskError.value).toBe('影响分析执行失败')
    })

    it('任务进行中不触发刷新', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()
      const calls = mocks.fetchTraceImpactItems.mock.calls.length

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'pending', progress: 0 })
      await nextTick()
      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'running', progress: 40 })
      await nextTick()
      expect(s.refreshSignal.value).toBe(0)
      expect(s.taskFailed.value).toBe(false)
      expect(mocks.fetchTraceImpactItems.mock.calls.length).toBe(calls)
      expect(s.taskRunning.value).toBe(true)
      expect(s.taskProgress.value).toBe(40)
    })
  })

  describe('任务运行态与权限', () => {
    it('taskRunning / taskProgress 只跟随本面板任务', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      expect(s.taskRunning.value).toBe(false)
      expect(s.taskProgress.value).toBeNull()

      await s.openFor('r1')
      await s.analyze()

      aiStore.detail = makeDetail({ taskId: 'other-task', status: 'running', progress: 50 })
      await nextTick()
      expect(s.taskRunning.value).toBe(false)
      expect(s.taskProgress.value).toBeNull()

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'cancelled', progress: 60 })
      await nextTick()
      expect(s.taskRunning.value).toBe(false)
      expect(s.taskProgress.value).toBe(60)
    })

    it('canEdit 反映 trace:edit 权限', () => {
      const s = create()
      expect(s.canEdit.value).toBe(true)
      expect(mocks.hasPermission).toHaveBeenCalledWith('trace:edit')
    })

    it('无 trace:edit 权限时 canEdit 为 false', () => {
      mocks.hasPermission.mockReturnValue(false)
      const s = create()
      expect(s.canEdit.value).toBe(false)
    })
  })
})
