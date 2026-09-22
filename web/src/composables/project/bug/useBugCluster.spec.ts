import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import type { AiTask, AiBugClusterSnapshot } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchLatestBugClustering: vi.fn<() => Promise<AiTask | null>>(),
  fetchAiTask: vi.fn<() => Promise<AiTask>>(),
  startBugClustering: vi.fn<() => Promise<{ taskId: string }>>(),
  cancelAiTask: vi.fn<() => Promise<void>>(),
  retryAiTask: vi.fn<() => Promise<void>>(),
  toBugClusterSnapshot: vi.fn<(r: Record<string, unknown> | null) => AiBugClusterSnapshot | null>(),
  useRouter: vi.fn(),
  useAiStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  buildModuleBars: vi.fn().mockReturnValue([]),
  buildSeveritySegments: vi.fn().mockReturnValue([]),
}))

vi.mock('vue-router', () => ({
  useRouter: mocks.useRouter,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/ai', () => ({
  fetchLatestBugClustering: mocks.fetchLatestBugClustering,
  fetchAiTask: mocks.fetchAiTask,
  startBugClustering: mocks.startBugClustering,
  cancelAiTask: mocks.cancelAiTask,
  retryAiTask: mocks.retryAiTask,
  toBugClusterSnapshot: mocks.toBugClusterSnapshot,
}))

vi.mock('@/stores/ai', () => ({
  useAiStore: mocks.useAiStore,
}))

vi.mock('@/components/project/bug/bugClusterChart', () => ({
  buildModuleBars: mocks.buildModuleBars,
  buildSeveritySegments: mocks.buildSeveritySegments,
}))

import { useBugCluster } from './useBugCluster'

function makeTask(overrides?: Partial<AiTask>): AiTask {
  return {
    id: 'task-1',
    type: 'bug_clustering',
    targetId: null,
    status: 'pending',
    progress: 0,
    result: null,
    errorMessage: null,
    createdBy: 'u1',
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function makeSnapshot(overrides?: Partial<AiBugClusterSnapshot>): AiBugClusterSnapshot {
  return {
    generatedAt: '2025-01-01T00:00:00',
    bugCount: 2,
    clusters: [
      {
        label: '登录问题',
        labeled: true,
        rootCause: '密码校验逻辑缺陷',
        bugs: [
          { id: 'b1', title: '登录失败', severity: 'serious', status: 'active' },
          { id: 'b2', title: '密码错误', severity: 'fatal', status: 'active' },
        ],
        severityDist: { fatal: 1, serious: 1, general: 0, minor: 0 },
        moduleDist: [{ moduleId: 'm1', moduleName: '认证模块', count: 2 }],
      },
    ],
    unclustered: ['b3'],
    ...overrides,
  }
}

describe('useBugCluster', () => {
  let routerPush: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    routerPush = vi.fn()
    mocks.useRouter.mockReturnValue({ push: routerPush })
    mocks.useAiStore.mockReturnValue({ semanticDegraded: false })
    mocks.toBugClusterSnapshot.mockImplementation((r) => {
      if (!r) return null
      return makeSnapshot()
    })
    mocks.fetchLatestBugClustering.mockResolvedValue(null)
    mocks.buildModuleBars.mockReturnValue([])
    mocks.buildSeveritySegments.mockReturnValue([])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function init(modelValue = false) {
    const emit = vi.fn()
    const result = useBugCluster({ modelValue, emit })
    return { ...result, emit }
  }

  describe('初始状态', () => {
    it('drawerVisible 跟随 modelValue', () => {
      const s = init(false)
      expect(s.drawerVisible.value).toBe(false)
    })

    it('drawerVisible 为 true 时对应 modelValue', () => {
      const s = init(true)
      expect(s.drawerVisible.value).toBe(true)
    })

    it('task 为 null', () => {
      const s = init()
      expect(s.task.value).toBeNull()
    })

    it('starting 为 false', () => {
      const s = init()
      expect(s.starting.value).toBe(false)
    })

    it('expandedCluster 为 0', () => {
      const s = init()
      expect(s.expandedCluster.value).toBe(0)
    })

    it('expandedCause 为 null', () => {
      const s = init()
      expect(s.expandedCause.value).toBeNull()
    })

    it('snapshot 为 null', () => {
      const s = init()
      expect(s.snapshot.value).toBeNull()
    })

    it('running 为 false', () => {
      const s = init()
      expect(s.running.value).toBe(false)
    })

    it('semanticDegraded 来自 aiStore', () => {
      mocks.useAiStore.mockReturnValue({ semanticDegraded: true })
      const s = init()
      expect(s.semanticDegraded.value).toBe(true)
    })

    it('summary 全为 0', () => {
      const s = init()
      expect(s.summary.value).toEqual({ bugs: 0, clusters: 0, unclustered: 0 })
    })

    it('STATUS_LABEL 存在', () => {
      const s = init()
      expect(s.STATUS_LABEL).toBeDefined()
      expect(s.STATUS_LABEL.active).toBe('活跃')
      expect(s.STATUS_LABEL.resolved).toBe('已解决')
      expect(s.STATUS_LABEL.rejected).toBe('已拒绝')
      expect(s.STATUS_LABEL.closed).toBe('已关闭')
    })
  })

  describe('drawerVisible setter', () => {
    it('set 时触发 emit', () => {
      const s = init(false)
      s.drawerVisible.value = true
      expect(s.emit).toHaveBeenCalledWith('update:modelValue', true)
    })

    it('set false 触发 emit', () => {
      const s = init(true)
      s.drawerVisible.value = false
      expect(s.emit).toHaveBeenCalledWith('update:modelValue', false)
    })
  })

  describe('start', () => {
    it('发起聚类成功并创建新任务', async () => {
      mocks.startBugClustering.mockResolvedValue({ taskId: 'new-task' })
      const s = init()
      await s.start()
      expect(mocks.startBugClustering).toHaveBeenCalled()
      expect(s.task.value?.id).toBe('new-task')
      expect(s.task.value?.status).toBe('pending')
      expect(s.starting.value).toBe(false)
    })

    it('发起聚类成功后重置 expandedCluster 和 expandedCause', async () => {
      mocks.startBugClustering.mockResolvedValue({ taskId: 'new-task' })
      const s = init()
      s.expandedCluster.value = 5
      s.expandedCause.value = 3
      await s.start()
      expect(s.expandedCluster.value).toBe(0)
      expect(s.expandedCause.value).toBeNull()
    })

    it('发起聚类成功后启动轮询', async () => {
      mocks.startBugClustering.mockResolvedValue({ taskId: 'new-task' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledWith('new-task')
    })

    it('已有运行任务时提示并开始轮询', async () => {
      mocks.startBugClustering.mockResolvedValue({ taskId: 'new-task' })
      const s = init()
      s.task.value = makeTask({ status: 'running' })
      await s.start()
      expect(mocks.ElMessage.info).toHaveBeenCalledWith('已有聚类任务在执行')
      expect(mocks.startBugClustering).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledWith('task-1')
    })

    it('发起聚类失败时显示错误消息', async () => {
      mocks.startBugClustering.mockRejectedValue(new Error('发起失败'))
      const s = init()
      await s.start()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('发起失败')
      expect(s.starting.value).toBe(false)
    })

    it('发起聚类失败非 Error 异常显示通用消息', async () => {
      mocks.startBugClustering.mockRejectedValue(42)
      const s = init()
      await s.start()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('发起聚类失败')
    })

    it('starting 在 finally 中重置', async () => {
      mocks.startBugClustering.mockRejectedValue(new Error('fail'))
      const s = init()
      await s.start()
      expect(s.starting.value).toBe(false)
    })
  })

  describe('轮询', () => {
    it('任务成功时停止轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeTask({ status: 'success' }))
      mocks.startBugClustering.mockResolvedValue({ taskId: 'poll-task' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(4000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    })

    it('任务失败时停止轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeTask({ status: 'failed' }))
      mocks.startBugClustering.mockResolvedValue({ taskId: 'poll-task' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(4000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    })

    it('任务取消时停止轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeTask({ status: 'cancelled' }))
      mocks.startBugClustering.mockResolvedValue({ taskId: 'poll-task' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(4000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    })

    it('轮询出错时停止轮询', async () => {
      mocks.fetchAiTask.mockRejectedValue(new Error('network'))
      mocks.startBugClustering.mockResolvedValue({ taskId: 'poll-task' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(4000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    })

    it('轮询持续更新 task', async () => {
      mocks.fetchAiTask
        .mockResolvedValueOnce(makeTask({ status: 'running' }))
        .mockResolvedValueOnce(makeTask({ status: 'success' }))
      mocks.startBugClustering.mockResolvedValue({ taskId: 'poll-task' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(s.task.value?.status).toBe('running')
      await vi.advanceTimersByTimeAsync(2000)
      expect(s.task.value?.status).toBe('success')
    })
  })

  describe('cancel', () => {
    it('task 为 null 时直接返回', async () => {
      const s = init()
      await s.cancel()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('用户确认后取消任务', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.cancelAiTask.mockResolvedValue()
      const s = init()
      s.task.value = makeTask()
      await s.cancel()
      expect(mocks.cancelAiTask).toHaveBeenCalledWith('task-1')
    })

    it('用户取消时不调用 cancelAiTask', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const s = init()
      s.task.value = makeTask()
      await s.cancel()
      expect(mocks.cancelAiTask).not.toHaveBeenCalled()
    })

    it('取消失败时显示错误消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.cancelAiTask.mockRejectedValue(new Error('取消失败'))
      const s = init()
      s.task.value = makeTask()
      await s.cancel()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('取消失败')
    })

    it('取消失败非 Error 异常显示通用消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.cancelAiTask.mockRejectedValue(42)
      const s = init()
      s.task.value = makeTask()
      await s.cancel()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('取消失败')
    })
  })

  describe('retry', () => {
    it('task 为 null 时直接返回', async () => {
      const s = init()
      await s.retry()
      expect(mocks.retryAiTask).not.toHaveBeenCalled()
    })

    it('重试成功并启动轮询', async () => {
      mocks.retryAiTask.mockResolvedValue()
      const s = init()
      s.task.value = makeTask({ status: 'failed' })
      await s.retry()
      expect(mocks.retryAiTask).toHaveBeenCalledWith('task-1')
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledWith('task-1')
    })

    it('重试失败时显示错误消息', async () => {
      mocks.retryAiTask.mockRejectedValue(new Error('重试失败'))
      const s = init()
      s.task.value = makeTask({ status: 'failed' })
      await s.retry()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('重试失败')
    })

    it('重试失败非 Error 异常显示通用消息', async () => {
      mocks.retryAiTask.mockRejectedValue(42)
      const s = init()
      s.task.value = makeTask({ status: 'failed' })
      await s.retry()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('重试失败')
    })
  })

  describe('goDetail', () => {
    it('关闭 drawer 并跳转到缺陷详情', () => {
      const s = init(true)
      s.goDetail('bug-1')
      expect(s.emit).toHaveBeenCalledWith('update:modelValue', false)
      expect(routerPush).toHaveBeenCalledWith('/workspace/projects/bugs/bug-1')
    })

    it('不同 bugId 跳转正确路径', () => {
      const s = init()
      s.goDetail('bug-99')
      expect(routerPush).toHaveBeenCalledWith('/workspace/projects/bugs/bug-99')
    })
  })

  describe('computed summary', () => {
    it('有 snapshot 时返回正确数据', () => {
      const snapshot = makeSnapshot()
      mocks.toBugClusterSnapshot.mockReturnValue(snapshot)
      const s = init()
      s.task.value = makeTask({ result: {} })
      expect(s.summary.value).toEqual({ bugs: 2, clusters: 1, unclustered: 1 })
    })

    it('snapshot 为 null 时全为 0', () => {
      mocks.toBugClusterSnapshot.mockReturnValue(null)
      const s = init()
      expect(s.summary.value).toEqual({ bugs: 0, clusters: 0, unclustered: 0 })
    })
  })

  describe('computed running', () => {
    it('status 为 pending 时 running 为 true', () => {
      const s = init()
      s.task.value = makeTask({ status: 'pending' })
      expect(s.running.value).toBe(true)
    })

    it('status 为 running 时 running 为 true', () => {
      const s = init()
      s.task.value = makeTask({ status: 'running' })
      expect(s.running.value).toBe(true)
    })

    it('status 为 success 时 running 为 false', () => {
      const s = init()
      s.task.value = makeTask({ status: 'success' })
      expect(s.running.value).toBe(false)
    })

    it('status 为 failed 时 running 为 false', () => {
      const s = init()
      s.task.value = makeTask({ status: 'failed' })
      expect(s.running.value).toBe(false)
    })

    it('status 为 cancelled 时 running 为 false', () => {
      const s = init()
      s.task.value = makeTask({ status: 'cancelled' })
      expect(s.running.value).toBe(false)
    })

    it('task 为 null 时 running 为 false', () => {
      const s = init()
      expect(s.running.value).toBe(false)
    })
  })

  describe('computed snapshot', () => {
    it('task result 为 null 时 snapshot 为 null', () => {
      mocks.toBugClusterSnapshot.mockReturnValue(null)
      const s = init()
      s.task.value = makeTask({ result: null })
      expect(s.snapshot.value).toBeNull()
    })

    it('调用 toBugClusterSnapshot 转换 result', () => {
      const snapshot = makeSnapshot()
      mocks.toBugClusterSnapshot.mockReturnValue(snapshot)
      const s = init()
      s.task.value = makeTask({ result: { some: 'data' } })
      expect(s.snapshot.value).toBe(snapshot)
    })
  })

  describe('computed semanticDegraded', () => {
    it('aiStore.semanticDegraded 为 true 时返回 true', () => {
      mocks.useAiStore.mockReturnValue({ semanticDegraded: true })
      const s = init()
      expect(s.semanticDegraded.value).toBe(true)
    })

    it('aiStore.semanticDegraded 为 false 时返回 false', () => {
      mocks.useAiStore.mockReturnValue({ semanticDegraded: false })
      const s = init()
      expect(s.semanticDegraded.value).toBe(false)
    })
  })

  describe('computed moduleBars', () => {
    it('有 snapshot 时调用 buildModuleBars', () => {
      const snapshot = makeSnapshot()
      mocks.toBugClusterSnapshot.mockReturnValue(snapshot)
      mocks.buildModuleBars.mockReturnValue([{ name: '认证模块', count: 2 }])
      const s = init()
      s.task.value = makeTask({ result: {} })
      const bars = s.moduleBars.value
      expect(mocks.buildModuleBars).toHaveBeenCalledWith([
        { moduleId: 'm1', moduleName: '认证模块', count: 2 },
      ])
      expect(bars).toEqual([{ name: '认证模块', count: 2 }])
    })

    it('snapshot 为 null 时调用 buildModuleBars 传空数组', () => {
      mocks.toBugClusterSnapshot.mockReturnValue(null)
      mocks.buildModuleBars.mockReturnValue([])
      const s = init()
      const bars = s.moduleBars.value
      expect(bars).toEqual([])
      expect(mocks.buildModuleBars).toHaveBeenCalledWith([])
    })
  })

  describe('computed severitySegments', () => {
    it('有 snapshot 时调用 buildSeveritySegments', () => {
      const snapshot = makeSnapshot()
      mocks.toBugClusterSnapshot.mockReturnValue(snapshot)
      mocks.buildSeveritySegments.mockReturnValue([{ severity: 'fatal', count: 1 }])
      const s = init()
      s.task.value = makeTask({ result: {} })
      const segments = s.severitySegments.value
      expect(mocks.buildSeveritySegments).toHaveBeenCalledWith({ fatal: 1, serious: 1, general: 0, minor: 0 })
      expect(segments).toEqual([{ severity: 'fatal', count: 1 }])
    })

    it('snapshot 为 null 时传递默认值', () => {
      mocks.toBugClusterSnapshot.mockReturnValue(null)
      mocks.buildSeveritySegments.mockReturnValue([])
      const s = init()
      const segments = s.severitySegments.value
      expect(mocks.buildSeveritySegments).toHaveBeenCalledWith({ fatal: 0, serious: 0, general: 0, minor: 0 })
      expect(segments).toEqual([])
    })

    it('多个 cluster 聚合 severityDist', () => {
      const snapshot: AiBugClusterSnapshot = {
        generatedAt: '2025-01-01T00:00:00',
        bugCount: 4,
        clusters: [
          {
            label: 'A', labeled: true, rootCause: null,
            bugs: [],
            severityDist: { fatal: 2, serious: 0, general: 0, minor: 0 },
            moduleDist: [],
          },
          {
            label: 'B', labeled: true, rootCause: null,
            bugs: [],
            severityDist: { fatal: 1, serious: 3, general: 0, minor: 0 },
            moduleDist: [],
          },
        ],
        unclustered: [],
      }
      mocks.toBugClusterSnapshot.mockReturnValue(snapshot)
      const s = init()
      s.task.value = makeTask({ result: {} })
      s.severitySegments.value
      expect(mocks.buildSeveritySegments).toHaveBeenCalledWith({ fatal: 3, serious: 3, general: 0, minor: 0 })
    })
  })

  describe('severityDots', () => {
    it('返回 4 个圆点', () => {
      const s = init()
      const dots = s.severityDots({ fatal: 1, serious: 0, general: 0, minor: 0 })
      expect(dots).toHaveLength(4)
    })

    it('有值的严重度标记 filled', () => {
      const s = init()
      const dots = s.severityDots({ fatal: 1, serious: 0, general: 3, minor: 0 })
      expect(dots[0]).toEqual({ severity: 'fatal', filled: true })
      expect(dots[1]).toEqual({ severity: 'serious', filled: false })
      expect(dots[2]).toEqual({ severity: 'general', filled: true })
      expect(dots[3]).toEqual({ severity: 'minor', filled: false })
    })

    it('空 dist 全部 filled 为 false', () => {
      const s = init()
      const dots = s.severityDots({})
      dots.forEach((dot) => {
        expect(dot.filled).toBe(false)
      })
    })

    it('全部有值时全部 filled 为 true', () => {
      const s = init()
      const dots = s.severityDots({ fatal: 5, serious: 3, general: 2, minor: 1 })
      dots.forEach((dot) => {
        expect(dot.filled).toBe(true)
      })
    })

    it('顺序固定为 fatal, serious, general, minor', () => {
      const s = init()
      const dots = s.severityDots({ fatal: 1, serious: 1, general: 1, minor: 1 })
      expect(dots.map((d) => d.severity)).toEqual(['fatal', 'serious', 'general', 'minor'])
    })
  })

  describe('start 替换轮询', () => {
    it('新 start 停止旧轮询并启动新轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeTask({ status: 'running' }))
      mocks.startBugClustering.mockResolvedValue({ taskId: 'task-a' })
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledWith('task-a')
      expect(s.running.value).toBe(true)
      mocks.fetchAiTask.mockClear()
      mocks.startBugClustering.mockResolvedValue({ taskId: 'task-b' })
      s.task.value = makeTask({ id: 'task-b', status: 'failed' })
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledWith('task-b')
    })
  })

  describe('onBeforeUnmount', () => {
    it('组件卸载时停止轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeTask({ status: 'running' }))
      const s = init()
      await s.start()
      await vi.advanceTimersByTimeAsync(2000)
      expect(mocks.fetchAiTask).toHaveBeenCalledTimes(1)
    })
  })
})
