import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiSchedulePageItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchScheduleExecutions: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/project/api-testing/schedule', () => ({
  fetchScheduleExecutions: mocks.fetchScheduleExecutions,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useScheduleExecutions } from './useScheduleExecutions'

function buildItem(partial: Partial<ApiSchedulePageItem> = {}): ApiSchedulePageItem {
  return {
    id: 'task-1',
    taskType: 'scene_execute',
    name: '测试任务',
    description: null,
    boundObjectId: null,
    boundObjectName: null,
    executionScope: 'all',
    moduleIds: null,
    sceneIds: null,
    openapiUrl: null,
    environmentId: null,
    environmentName: null,
    cronExpression: '0 2 * * *',
    enabled: true,
    lastExecutionStatus: null,
    lastExecutionAt: null,
    nextExecutions: [],
    createdAt: '2026-01-01T00:00:00',
    ...partial,
  }
}

function pageResult<T>(list: T[] = [], total = list.length) {
  return { list, total }
}

describe('useScheduleExecutions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function makeSut() {
    return useScheduleExecutions()
  }

  describe('初始状态', () => {
    it('showExecutionDrawer 默认 false', () => {
      const s = makeSut()
      expect(s.showExecutionDrawer.value).toBe(false)
    })

    it('executionTask 默认 null', () => {
      const s = makeSut()
      expect(s.executionTask.value).toBeNull()
    })

    it('executionRows 默认空数组', () => {
      const s = makeSut()
      expect(s.executionRows.value).toEqual([])
    })

    it('executionTotal 默认 0', () => {
      const s = makeSut()
      expect(s.executionTotal.value).toBe(0)
    })

    it('executionPageNo 默认 1', () => {
      const s = makeSut()
      expect(s.executionPageNo.value).toBe(1)
    })

    it('executionLoading 默认 false', () => {
      const s = makeSut()
      expect(s.executionLoading.value).toBe(false)
    })
  })

  describe('openExecutions', () => {
    it('设置 executionTask 并打开抽屉', async () => {
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult())
      const s = makeSut()
      const item = buildItem({ id: 't1' })
      await s.openExecutions(item)
      expect(s.executionTask.value).toEqual(item)
      expect(s.showExecutionDrawer.value).toBe(true)
    })

    it('重置 rows、total、pageNo', async () => {
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult())
      const s = makeSut()
      s.executionRows.value = [{ id: 'old' }] as never
      s.executionTotal.value = 99
      s.executionPageNo.value = 5
      await s.openExecutions(buildItem())
      expect(s.executionRows.value).toEqual([])
      expect(s.executionTotal.value).toBe(0)
      expect(s.executionPageNo.value).toBe(1)
    })

    it('调用 loadExecutions 加载数据', async () => {
      const rows = [{ id: 'e1' }, { id: 'e2' }]
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult(rows, 2))
      const s = makeSut()
      await s.openExecutions(buildItem({ id: 't1' }))
      expect(mocks.fetchScheduleExecutions).toHaveBeenCalledWith('t1', { pageNo: 1, pageSize: 10 })
      expect(s.executionRows.value).toEqual(rows)
      expect(s.executionTotal.value).toBe(2)
    })

    it('加载完成后 executionLoading 恢复 false', async () => {
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult())
      const s = makeSut()
      await s.openExecutions(buildItem())
      expect(s.executionLoading.value).toBe(false)
    })
  })

  describe('loadExecutions', () => {
    it('executionTask 为 null 时不调用 API', async () => {
      const s = makeSut()
      s.executionTask.value = null
      await s.loadExecutions()
      expect(mocks.fetchScheduleExecutions).not.toHaveBeenCalled()
    })

    it('加载成功更新 rows 和 total', async () => {
      const rows = [{ id: 'e1' }, { id: 'e2' }, { id: 'e3' }]
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult(rows, 15))
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      await s.loadExecutions()
      expect(s.executionRows.value).toEqual(rows)
      expect(s.executionTotal.value).toBe(15)
      expect(s.executionLoading.value).toBe(false)
    })

    it('加载失败显示错误消息', async () => {
      mocks.fetchScheduleExecutions.mockRejectedValue(new Error('网络错误'))
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      await s.loadExecutions()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.executionLoading.value).toBe(false)
    })

    it('加载失败非 Error 显示通用消息', async () => {
      mocks.fetchScheduleExecutions.mockRejectedValue('string err')
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      await s.loadExecutions()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行记录加载失败')
    })

    it('加载过程中 executionLoading 为 true', async () => {
      let resolve!: (value: unknown) => void
      mocks.fetchScheduleExecutions.mockImplementation(
        () => new Promise((r) => { resolve = r }),
      )
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      const promise = s.loadExecutions()
      expect(s.executionLoading.value).toBe(true)
      resolve(pageResult())
      await promise
      expect(s.executionLoading.value).toBe(false)
    })

    it('使用当前 executionPageNo 调用 API', async () => {
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult())
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      s.executionPageNo.value = 3
      await s.loadExecutions()
      expect(mocks.fetchScheduleExecutions).toHaveBeenCalledWith('t1', { pageNo: 3, pageSize: 10 })
    })
  })

  describe('多次 openExecutions 调用', () => {
    it('第二次调用覆盖第一次的任务', async () => {
      const rows1 = [{ id: 'e1' }]
      const rows2 = [{ id: 'e2' }, { id: 'e3' }]
      mocks.fetchScheduleExecutions
        .mockResolvedValueOnce(pageResult(rows1, 1))
        .mockResolvedValueOnce(pageResult(rows2, 2))
      const s = makeSut()
      await s.openExecutions(buildItem({ id: 't1' }))
      expect(s.executionTask.value?.id).toBe('t1')
      expect(s.executionRows.value).toEqual(rows1)
      await s.openExecutions(buildItem({ id: 't2' }))
      expect(s.executionTask.value?.id).toBe('t2')
      expect(s.executionRows.value).toEqual(rows2)
      expect(mocks.fetchScheduleExecutions).toHaveBeenCalledTimes(2)
    })
  })

  describe('loadExecutions 后 openExecutions 重置', () => {
    it('openExecutions 将 pageNo 重置为 1 后加载', async () => {
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult())
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 'old' })
      s.executionPageNo.value = 5
      await s.openExecutions(buildItem({ id: 'new' }))
      expect(s.executionPageNo.value).toBe(1)
      expect(mocks.fetchScheduleExecutions).toHaveBeenCalledWith('new', { pageNo: 1, pageSize: 10 })
    })
  })

  describe('loadExecutions 错误恢复', () => {
    it('加载失败后 loading 恢复 false', async () => {
      mocks.fetchScheduleExecutions.mockRejectedValue(new Error('fail'))
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      await s.loadExecutions()
      expect(s.executionLoading.value).toBe(false)
    })

    it('加载失败不影响已有数据', async () => {
      const rows = [{ id: 'e1' }]
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult(rows, 1))
      const s = makeSut()
      s.executionTask.value = buildItem({ id: 't1' })
      await s.loadExecutions()
      expect(s.executionRows.value).toEqual(rows)
      mocks.fetchScheduleExecutions.mockRejectedValue(new Error('fail'))
      await s.loadExecutions()
      expect(s.executionRows.value).toEqual(rows)
      expect(s.executionTotal.value).toBe(1)
    })
  })
})
