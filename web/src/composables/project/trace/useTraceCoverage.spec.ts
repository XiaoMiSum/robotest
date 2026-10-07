// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, PageResult, TraceCoverage, TraceCoveragePatchPayload } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchTraceCoverage: vi.fn(),
  patchTraceCoverage: vi.fn(),
  submitAiTask: vi.fn(),
  retryAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchTraceCoverage: mocks.fetchTraceCoverage,
  patchTraceCoverage: mocks.patchTraceCoverage,
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  retryAiTask: mocks.retryAiTask,
  fetchAiTask: mocks.fetchAiTask,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useTraceCoverage } from './useTraceCoverage'
import { useAiTaskStore } from '@/stores/aiTask'

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

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 'task-1',
    type: 'coverage_analysis',
    status: 'succeeded',
    progress: 100,
    phase: null,
    submittedBy: 'u1',
    retryOfTaskId: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-04T01:00:00Z',
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
  mocks.fetchTraceCoverage.mockResolvedValue({ list: [makeCoverage()], total: 1 })
  mocks.patchTraceCoverage.mockResolvedValue(makeCoverage())
  mocks.submitAiTask.mockResolvedValue({ taskId: 'task-1', status: 'pending' })
  mocks.retryAiTask.mockResolvedValue({ taskId: 'task-2', status: 'pending' })
}

function create(): ReturnType<typeof useTraceCoverage> {
  setActivePinia(createPinia())
  const s = useTraceCoverage()
  // 测试进程无轮询消费者，替换为空实现避免真实定时器泄漏
  useAiTaskStore().startPolling = vi.fn()
  return s
}

describe('useTraceCoverage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('openFor 与 load', () => {
    it('切换需求时先清空记录再按单条取数', async () => {
      const s = create()
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
      const s = create()
      await s.openFor('r1')
      expect(s.record.value).not.toBeNull()

      const slow = deferred<PageResult<TraceCoverage>>()
      mocks.fetchTraceCoverage.mockReturnValueOnce(slow.promise)
      const pending = s.openFor('r1')
      expect(s.requirementId.value).toBe('r1')
      slow.resolve({ list: [makeCoverage({ coverageStatus: 'partial' })], total: 1 })
      await pending
      expect(s.record.value?.coverageStatus).toBe('partial')
    })

    it('接口返回空列表时记录置空', async () => {
      const s = create()
      await s.openFor('r1')
      expect(s.record.value).not.toBeNull()
      mocks.fetchTraceCoverage.mockResolvedValueOnce({ list: [], total: 0 })
      await s.load()
      expect(s.record.value).toBeNull()
    })

    it('加载失败记录错误、清空记录并可重试', async () => {
      const s = create()
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
      const s = create()
      mocks.fetchTraceCoverage.mockRejectedValueOnce('boom')
      await s.openFor('r1')
      expect(s.loadError.value).toBe('加载覆盖结论失败')
    })

    it('requirementId 为空时 load 直接返回不发请求', async () => {
      const s = create()
      await s.load()
      expect(mocks.fetchTraceCoverage).not.toHaveBeenCalled()
      expect(s.loading.value).toBe(false)
    })
  })

  describe('save', () => {
    it('保存成功写回记录、提示并自增修订号', async () => {
      const s = create()
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
      const s = create()
      await s.openFor('r1')
      mocks.patchTraceCoverage.mockRejectedValueOnce(new Error('覆盖修正失败'))

      const ok = await s.save({ coverageStatus: 'partial' })
      expect(ok).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('覆盖修正失败')
      expect(s.revision.value).toBe(0)
      expect(s.saving.value).toBe(false)
    })

    it('requirementId 为空时 save 返回 false 不调接口', async () => {
      const s = create()
      const ok = await s.save({ coverageStatus: 'partial' })
      expect(ok).toBe(false)
      expect(mocks.patchTraceCoverage).not.toHaveBeenCalled()
      expect(s.saving.value).toBe(false)
    })
  })

  describe('覆盖分析', () => {
    it('发起成功记录任务并启动轮询', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')

      const ok = await s.analyze()
      expect(mocks.submitAiTask).toHaveBeenCalledWith('coverage_analysis', {
        requirementIds: ['r1'],
      })
      expect(aiStore.startPolling).toHaveBeenCalledWith('task-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith(
        '覆盖分析已发起，完成后自动刷新覆盖结论',
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
      mocks.submitAiTask.mockRejectedValueOnce(new Error('AI 未开启'))

      const ok = await s.analyze()
      expect(ok).toBe(false)
      expect(s.taskFailed.value).toBe(true)
      expect(s.taskError.value).toBe('AI 未开启')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('AI 未开启')
      expect(s.busy.value).toBe(false)
    })

    it('无运行任务时 retryAnalyze 委托 analyze', async () => {
      const s = create()
      await s.openFor('r1')

      const ok = await s.retryAnalyze()
      expect(ok).toBe(true)
      expect(mocks.submitAiTask).toHaveBeenCalledWith('coverage_analysis', {
        requirementIds: ['r1'],
      })
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

    it('切换需求复位任务态，旧需求的失败不再显示', async () => {
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

      await s.openFor('r2')
      expect(s.taskFailed.value).toBe(false)
      expect(s.taskError.value).toBe('')
      expect(s.taskRunning.value).toBe(false)
    })

    it('close 后任务态一并复位', async () => {
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

      s.close()
      expect(s.taskFailed.value).toBe(false)
      expect(s.taskError.value).toBe('')
      expect(s.taskRunning.value).toBe(false)
    })
  })

  describe('任务回执 watch', () => {
    it('本面板任务成功后自增修订号并重载覆盖结论', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()
      const calls = mocks.fetchTraceCoverage.mock.calls.length

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'succeeded' })
      await nextTick()
      expect(s.revision.value).toBe(1)
      await vi.waitFor(() =>
        expect(mocks.fetchTraceCoverage.mock.calls.length).toBe(calls + 1),
      )
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      expect(s.record.value).not.toBeNull()
    })

    it('非本面板任务的回执不触发刷新', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()
      const calls = mocks.fetchTraceCoverage.mock.calls.length

      aiStore.detail = makeDetail({ taskId: 'other-task', status: 'succeeded' })
      await nextTick()
      expect(s.revision.value).toBe(0)
      expect(mocks.fetchTraceCoverage.mock.calls.length).toBe(calls)
    })

    it('任务失败取错误消息并自增修订号', async () => {
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
      expect(s.revision.value).toBe(1)
    })

    it('失败无错误消息时回退默认文案', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'failed', error: null })
      await nextTick()
      expect(s.taskFailed.value).toBe(true)
      expect(s.taskError.value).toBe('覆盖分析执行失败')
    })

    it('任务进行中不触发刷新', async () => {
      const s = create()
      const aiStore = useAiTaskStore()
      await s.openFor('r1')
      await s.analyze()
      const calls = mocks.fetchTraceCoverage.mock.calls.length

      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'pending', progress: 0 })
      await nextTick()
      aiStore.detail = makeDetail({ taskId: 'task-1', status: 'running', progress: 40 })
      await nextTick()
      expect(s.revision.value).toBe(0)
      expect(s.taskFailed.value).toBe(false)
      expect(mocks.fetchTraceCoverage.mock.calls.length).toBe(calls)
      expect(s.taskRunning.value).toBe(true)
      expect(s.taskProgress.value).toBe(40)
    })
  })

  describe('任务运行态', () => {
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
  })

  describe('close', () => {
    it('关闭后清空需求、记录与错误', async () => {
      const s = create()
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
