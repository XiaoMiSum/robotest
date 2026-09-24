import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchExecutionHistory: vi.fn(),
  fetchChangeHistory: vi.fn(),
  ElMessage: { error: vi.fn() },
}))

vi.mock('@/services/project/api-testing/scene', () => ({
  fetchExecutionHistory: mocks.fetchExecutionHistory,
  fetchChangeHistory: mocks.fetchChangeHistory,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useSceneHistory } from './useSceneHistory'

function makeExecItem(overrides?: Record<string, unknown>) {
  return {
    id: 'exec-1',
    status: 'passed',
    executionMode: 'manual',
    triggerType: 'user',
    executedAt: '2026-01-01T00:00:00Z',
    durationMs: 100,
    reportId: null,
    ...overrides,
  }
}

function makeChangeItem(overrides?: Record<string, unknown>) {
  return {
    id: 'change-1',
    version: 1,
    operatorName: 'admin',
    changeSummary: '修改了名称',
    changedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
  mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
})

describe('useSceneHistory', () => {
  describe('初始状态', () => {
    it('executionHistory 初始为空数组', () => {
      const { executionHistory } = useSceneHistory(() => 'scene-1')
      expect(executionHistory.value).toEqual([])
    })

    it('executionHistoryTotal 初始为 0', () => {
      const { executionHistoryTotal } = useSceneHistory(() => 'scene-1')
      expect(executionHistoryTotal.value).toBe(0)
    })

    it('executionHistoryPage 初始为 1', () => {
      const { executionHistoryPage } = useSceneHistory(() => 'scene-1')
      expect(executionHistoryPage.value).toBe(1)
    })

    it('changeHistory 初始为空数组', () => {
      const { changeHistory } = useSceneHistory(() => 'scene-1')
      expect(changeHistory.value).toEqual([])
    })

    it('changeHistoryTotal 初始为 0', () => {
      const { changeHistoryTotal } = useSceneHistory(() => 'scene-1')
      expect(changeHistoryTotal.value).toBe(0)
    })

    it('changeHistoryPage 初始为 1', () => {
      const { changeHistoryPage } = useSceneHistory(() => 'scene-1')
      expect(changeHistoryPage.value).toBe(1)
    })

    it('historyLoading 初始为 false', () => {
      const { historyLoading } = useSceneHistory(() => 'scene-1')
      expect(historyLoading.value).toBe(false)
    })

    it('showHistory 初始为 false', () => {
      const { showHistory } = useSceneHistory(() => 'scene-1')
      expect(showHistory.value).toBe(false)
    })

    it('reportDialogVisible 初始为 false', () => {
      const { reportDialogVisible } = useSceneHistory(() => 'scene-1')
      expect(reportDialogVisible.value).toBe(false)
    })

    it('reportDetailId 初始为空字符串', () => {
      const { reportDetailId } = useSceneHistory(() => 'scene-1')
      expect(reportDetailId.value).toBe('')
    })
  })

  describe('loadHistory', () => {
    it('sceneId 不存在时直接返回', async () => {
      const { loadHistory } = useSceneHistory(() => undefined)
      await loadHistory()
      expect(mocks.fetchExecutionHistory).not.toHaveBeenCalled()
      expect(mocks.fetchChangeHistory).not.toHaveBeenCalled()
    })

    it('成功加载时填充数据', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [makeExecItem()], total: 1 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [makeChangeItem()], total: 2 })
      const { loadHistory, executionHistory, executionHistoryTotal, changeHistory, changeHistoryTotal } = useSceneHistory(() => 'scene-1')
      await loadHistory()
      expect(executionHistory.value).toEqual([makeExecItem()])
      expect(executionHistoryTotal.value).toBe(1)
      expect(changeHistory.value).toEqual([makeChangeItem()])
      expect(changeHistoryTotal.value).toBe(2)
    })

    it('调用 API 时传入当前页码和每页 20 条', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
      const { loadHistory, executionHistoryPage } = useSceneHistory(() => 'scene-1')
      executionHistoryPage.value = 3
      await loadHistory()
      expect(mocks.fetchExecutionHistory).toHaveBeenCalledWith('scene-1', 3, 20)
    })

    it('加载完成后 historyLoading 恢复为 false', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
      const { loadHistory, historyLoading } = useSceneHistory(() => 'scene-1')
      const p = loadHistory()
      expect(historyLoading.value).toBe(true)
      await p
      expect(historyLoading.value).toBe(false)
    })

    it('请求失败时透传后端消息并保留错误态', async () => {
      mocks.fetchExecutionHistory.mockRejectedValue(new Error('执行历史接口失败'))
      mocks.fetchChangeHistory.mockRejectedValue(new Error('变更历史接口失败'))
      const { loadHistory, historyLoading, executionHistory, historyError } = useSceneHistory(() => 'scene-1')
      await loadHistory()
      expect(historyLoading.value).toBe(false)
      expect(executionHistory.value).toEqual([])
      expect(historyError.value).toContain('执行历史接口失败')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行历史接口失败')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('变更历史接口失败')
    })

    it('只有一侧失败时独立保留成功结果并提示失败请求', async () => {
      mocks.fetchExecutionHistory.mockRejectedValue(new Error('执行历史接口失败'))
      mocks.fetchChangeHistory.mockResolvedValue({ list: [makeChangeItem()], total: 1 })
      const { loadHistory, historyLoading, changeHistory, executionHistoryError } = useSceneHistory(() => 'scene-1')
      await loadHistory()
      expect(historyLoading.value).toBe(false)
      expect(changeHistory.value).toEqual([makeChangeItem()])
      expect(executionHistoryError.value).toBe('执行历史接口失败')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行历史接口失败')
    })

    it('忽略晚到的旧历史响应，避免覆盖当前结果或重复提示', async () => {
      const firstExec = deferred<{ list: ReturnType<typeof makeExecItem>[]; total: number }>()
      const firstChange = deferred<{ list: ReturnType<typeof makeChangeItem>[]; total: number }>()
      const secondExec = deferred<{ list: ReturnType<typeof makeExecItem>[]; total: number }>()
      const secondChange = deferred<{ list: ReturnType<typeof makeChangeItem>[]; total: number }>()
      mocks.fetchExecutionHistory
        .mockReturnValueOnce(firstExec.promise)
        .mockReturnValueOnce(secondExec.promise)
      mocks.fetchChangeHistory
        .mockReturnValueOnce(firstChange.promise)
        .mockReturnValueOnce(secondChange.promise)
      const sut = useSceneHistory(() => 'scene-1')
      const first = sut.loadHistory()
      const second = sut.loadHistory()
      secondExec.resolve({ list: [makeExecItem({ id: 'new-exec' })], total: 1 })
      secondChange.resolve({ list: [makeChangeItem({ id: 'new-change' })], total: 1 })
      await second
      firstExec.resolve({ list: [makeExecItem({ id: 'old-exec' })], total: 1 })
      firstChange.resolve({ list: [makeChangeItem({ id: 'old-change' })], total: 1 })
      await first
      expect(sut.executionHistory.value[0]?.id).toBe('new-exec')
      expect(sut.changeHistory.value[0]?.id).toBe('new-change')
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })
  })

  describe('showHistory watcher', () => {
    it('showHistory 设为 true 时自动调用 loadHistory', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
      const { showHistory } = useSceneHistory(() => 'scene-1')
      showHistory.value = true
      await nextTick()
      expect(mocks.fetchExecutionHistory).toHaveBeenCalled()
      expect(mocks.fetchChangeHistory).toHaveBeenCalled()
    })

    it('showHistory 设为 false 时不调用 loadHistory', async () => {
      const { showHistory } = useSceneHistory(() => 'scene-1')
      showHistory.value = false
      await nextTick()
      expect(mocks.fetchExecutionHistory).not.toHaveBeenCalled()
    })

    it('showHistory 从 true 到 true 不重复触发', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
      const { showHistory } = useSceneHistory(() => 'scene-1')
      showHistory.value = true
      await nextTick()
      const callCount = mocks.fetchExecutionHistory.mock.calls.length
      showHistory.value = true
      await nextTick()
      expect(mocks.fetchExecutionHistory.mock.calls.length).toBe(callCount)
    })

    it('sceneId 为 undefined 时 showHistory 触发也不调用 API', async () => {
      const { showHistory } = useSceneHistory(() => undefined)
      showHistory.value = true
      await nextTick()
      expect(mocks.fetchExecutionHistory).not.toHaveBeenCalled()
    })
  })

  describe('handleViewReport', () => {
    it('设置 reportDetailId 并打开对话框', () => {
      const { handleViewReport, reportDetailId, reportDialogVisible } = useSceneHistory(() => 'scene-1')
      handleViewReport('report-42')
      expect(reportDetailId.value).toBe('report-42')
      expect(reportDialogVisible.value).toBe(true)
    })

    it('多次调用更新 reportDetailId', () => {
      const { handleViewReport, reportDetailId, reportDialogVisible } = useSceneHistory(() => 'scene-1')
      handleViewReport('report-1')
      handleViewReport('report-2')
      expect(reportDetailId.value).toBe('report-2')
      expect(reportDialogVisible.value).toBe(true)
    })
  })

  describe('分页', () => {
    it('修改 executionHistoryPage 后 loadHistory 使用新页码', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
      const { loadHistory, executionHistoryPage } = useSceneHistory(() => 'scene-1')
      executionHistoryPage.value = 5
      await loadHistory()
      expect(mocks.fetchExecutionHistory).toHaveBeenCalledWith('scene-1', 5, 20)
    })

    it('修改 changeHistoryPage 后 loadHistory 使用新页码', async () => {
      mocks.fetchExecutionHistory.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchChangeHistory.mockResolvedValue({ list: [], total: 0 })
      const { loadHistory, changeHistoryPage } = useSceneHistory(() => 'scene-1')
      changeHistoryPage.value = 7
      await loadHistory()
      expect(mocks.fetchChangeHistory).toHaveBeenCalledWith('scene-1', 7, 20)
    })
  })

  describe('返回值完整性', () => {
    it('返回所有必需的 state 和方法', () => {
      const result = useSceneHistory(() => 'scene-1')
      expect(result).toHaveProperty('executionHistory')
      expect(result).toHaveProperty('executionHistoryTotal')
      expect(result).toHaveProperty('executionHistoryPage')
      expect(result).toHaveProperty('changeHistory')
      expect(result).toHaveProperty('changeHistoryTotal')
      expect(result).toHaveProperty('changeHistoryPage')
      expect(result).toHaveProperty('historyLoading')
      expect(result).toHaveProperty('showHistory')
      expect(result).toHaveProperty('loadHistory')
      expect(result).toHaveProperty('reportDialogVisible')
      expect(result).toHaveProperty('reportDetailId')
      expect(result).toHaveProperty('handleViewReport')
    })
  })
})
