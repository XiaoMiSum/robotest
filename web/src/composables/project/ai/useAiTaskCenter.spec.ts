// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskItem, PageResult } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiTasks: vi.fn(),
  fetchAiStatus: vi.fn(),
  cancelAiTask: vi.fn(),
  retryAiTask: vi.fn(),
  fetchMembers: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  fetchAiTasks: mocks.fetchAiTasks,
  fetchAiStatus: mocks.fetchAiStatus,
  cancelAiTask: mocks.cancelAiTask,
  retryAiTask: mocks.retryAiTask,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useAiTaskCenter } from './useAiTaskCenter'

type CenterState = ReturnType<typeof useAiTaskCenter>

function makeItem(overrides: Partial<AiTaskItem> = {}): AiTaskItem {
  return {
    taskId: 't1',
    type: 'requirement_split',
    status: 'running',
    progress: 30,
    phase: '构建提示词',
    submittedBy: 'u1',
    retryOfTaskId: null,
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-03T01:00:00Z',
    error: null,
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
  mocks.fetchAiTasks.mockResolvedValue({ list: [makeItem()], total: 1 } as PageResult<AiTaskItem>)
  mocks.fetchAiStatus.mockResolvedValue({ available: true, reason: null })
  mocks.fetchMembers.mockResolvedValue({
    list: [{ userId: 'u1', username: 'zhangsan', name: '张三', workspaceRole: 'member' }],
    total: 1,
  })
  mocks.cancelAiTask.mockResolvedValue(makeItem({ status: 'cancelled' }))
  mocks.retryAiTask.mockResolvedValue(makeItem({ taskId: 't2', status: 'pending' }))
  mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => true) })
}

/** composable 的初始化在 onMounted，需经真实组件挂载触发（保活回填 + 首屏加载） */
function mountCenter(): { state: CenterState } {
  let state: CenterState | null = null
  mount(
    defineComponent({
      setup() {
        state = useAiTaskCenter()
        return () => h('div')
      },
    }),
  )
  if (state === null) throw new Error('composable 未初始化')
  return { state }
}

function center(): CenterState {
  // 直连调用（不触发 onMounted）用于只验证手动加载的用例
  return useAiTaskCenter()
}

describe('useAiTaskCenter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('列表加载', () => {
    it('成功加载后填充行视图模型与总数', async () => {
      const { state: s } = mountCenter()
      await vi.waitFor(() => expect(s.hasLoaded.value).toBe(true))
      expect(s.rows.value).toHaveLength(1)
      expect(s.rows.value[0].name).toBe('需求拆分')
      expect(s.rows.value[0].typeMeta.domain).toBe('需求')
      expect(s.rows.value[0].statusMeta.label).toBe('进行中')
      await vi.waitFor(() => expect(s.rows.value[0].submitterText).toBe('张三'))
      expect(s.total.value).toBe(1)
      expect(s.hasLoaded.value).toBe(true)
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('加载失败按 UI-PAGE-11 记录错误并可重试', async () => {
      const s = center()
      mocks.fetchAiTasks.mockRejectedValueOnce(new Error('服务不可用'))
      await s.load()
      expect(s.loadError.value).toBe('服务不可用')
      expect(s.hasLoaded.value).toBe(false)

      s.retry()
      await vi.waitFor(() => expect(s.loadError.value).toBe(''))
      expect(s.rows.value).toHaveLength(1)
    })

    it('过期响应不覆盖新结果', async () => {
      const s = center()
      const first = deferred<PageResult<AiTaskItem>>()
      mocks.fetchAiTasks.mockReturnValueOnce(first.promise)
      const loading = s.load()

      mocks.fetchAiTasks.mockResolvedValueOnce({
        list: [makeItem({ taskId: 't2', status: 'succeeded' })],
        total: 1,
      } as PageResult<AiTaskItem>)
      await s.load()
      expect(s.rows.value[0].taskId).toBe('t2')

      first.resolve({ list: [makeItem({ taskId: 'stale' })], total: 9 } as PageResult<AiTaskItem>)
      await loading
      expect(s.rows.value[0].taskId).toBe('t2')
      expect(s.total.value).toBe(1)
    })

    it('发起人映射不到时显示短 UUID', async () => {
      const s = center()
      mocks.fetchAiTasks.mockResolvedValue({
        list: [makeItem({ submittedBy: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee' })],
        total: 1,
      } as PageResult<AiTaskItem>)
      mocks.fetchMembers.mockRejectedValue(new Error('成员接口失败'))
      await s.load()
      expect(s.rows.value[0].submitterText).toBe('aaaaaaaa…')
    })

    it('系统提交的任务发起人显示「系统」', async () => {
      const s = center()
      mocks.fetchAiTasks.mockResolvedValue({
        list: [makeItem({ submittedBy: null })],
        total: 1,
      } as PageResult<AiTaskItem>)
      await s.load()
      expect(s.rows.value[0].submitterText).toBe('系统')
    })
  })

  describe('筛选与分页', () => {
    it('查询按当前条件回第一页', async () => {
      const s = center()
      await s.load()
      s.filters.type = 'requirement_split'
      s.search()
      await vi.waitFor(() =>
        expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
          expect.objectContaining({ type: 'requirement_split', pageNo: 1 }),
        ),
      )
      expect(s.pageNo.value).toBe(1)
    })

    it('重置清空条件并恢复第一页', async () => {
      const s = center()
      await s.changePage(3)
      s.filters.status = 'failed'
      s.resetFilters()
      await vi.waitFor(() => expect(s.pageNo.value).toBe(1))
      expect(s.filters).toEqual({ type: '', status: '' })
      expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
        expect.objectContaining({ type: undefined, status: undefined, pageNo: 1 }),
      )
    })

    it('切页与改页大小均带最新条件请求', async () => {
      const s = center()
      await s.load()
      await s.changePageSize(50)
      expect(s.pageSize.value).toBe(50)
      expect(s.pageNo.value).toBe(1)
      expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageSize: 50 }),
      )
      expect(s.pageSizes).toEqual([20, 50, 100])
    })

    it('刷新沿用当前条件重取', async () => {
      const s = center()
      await s.load()
      s.filters.status = 'failed'
      s.refresh()
      await vi.waitFor(() =>
        expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
          expect.objectContaining({ status: 'failed' }),
        ),
      )
    })

    it('保活：从详情返回时沿用上次筛选与页码', async () => {
      const s = center()
      s.filters.type = 'requirement_split'
      s.filters.status = 'succeeded'
      s.search()
      await vi.waitFor(() => expect(s.pageNo.value).toBe(1))
      s.filters.type = ''
      s.filters.status = ''

      const { state } = mountCenter()
      await vi.waitFor(() => expect(state.hasLoaded.value).toBe(true))
      expect(state.filters).toEqual({ type: 'requirement_split', status: 'succeeded' })
      expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
        expect.objectContaining({ type: 'requirement_split', status: 'succeeded' }),
      )
    })
  })

  describe('AI 可用性与权限', () => {
    it('AI 可用时展示列表', async () => {
      const { state } = mountCenter()
      await vi.waitFor(() => expect(state.aiStatusLoaded.value).toBe(true))
      expect(state.aiAvailable.value).toBe(true)
      expect(state.hasLoaded.value).toBe(true)
    })

    it('AI 不可用时整页降级', async () => {
      mocks.fetchAiStatus.mockResolvedValue({ available: false, reason: null })
      const { state } = mountCenter()
      await vi.waitFor(() => expect(state.aiStatusLoaded.value).toBe(true))
      expect(state.aiAvailable.value).toBe(false)
    })

    it('状态接口失败按不可用处理', async () => {
      mocks.fetchAiStatus.mockRejectedValue(new Error('down'))
      const { state } = mountCenter()
      await vi.waitFor(() => expect(state.aiStatusLoaded.value).toBe(true))
      expect(state.aiAvailable.value).toBe(false)
    })

    it('入口与行操作均按 ai:task 显隐', () => {
      mocks.useAuthStore.mockReturnValue({
        hasPermission: (code: string) => code === 'ai:task',
      })
      const { state } = mountCenter()
      expect(state.canViewTasks.value).toBe(true)
      expect(state.canManageTasks.value).toBe(true)
    })

    it('无 ai:task 时隐藏入口', () => {
      mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => false) })
      const { state } = mountCenter()
      expect(state.canViewTasks.value).toBe(false)
    })
  })

  describe('行操作', () => {
    it('取消需二次确认，成功后更新行状态', async () => {
      const s = center()
      await s.load()
      expect(s.isPending(s.rows.value[0])).toBe(true)

      await s.handleCancel(s.rows.value[0])
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.cancelAiTask).toHaveBeenCalledWith('t1')
      expect(s.rows.value[0].status).toBe('cancelled')
      expect(s.rows.value[0].statusMeta.label).toBe('已取消')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('任务已取消')
    })

    it('取消被用户放弃时不发请求', async () => {
      const s = center()
      await s.load()
      mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
      await s.handleCancel(s.rows.value[0])
      expect(mocks.cancelAiTask).not.toHaveBeenCalled()
    })

    it('取消失败提示错误', async () => {
      const s = center()
      await s.load()
      mocks.cancelAiTask.mockRejectedValueOnce(new Error('状态不允许'))
      await s.handleCancel(s.rows.value[0])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态不允许')
    })

    it('重试成功后重取列表', async () => {
      const s = center()
      mocks.fetchAiTasks.mockResolvedValue({
        list: [makeItem({ status: 'failed', error: { code: 1000018117, msg: '模型调用失败' } })],
        total: 1,
      } as PageResult<AiTaskItem>)
      await s.load()
      expect(s.rows.value[0].statusMeta.label).toBe('失败')
      expect(s.rows.value[0].errorMessage).toBe('模型调用失败')
      expect(s.isPending(s.rows.value[0])).toBe(false)

      mocks.fetchAiTasks.mockClear()
      await s.handleRetry(s.rows.value[0])
      expect(mocks.retryAiTask).toHaveBeenCalledWith('t1')
      expect(mocks.fetchAiTasks).toHaveBeenCalled()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已重新排队')
    })

    it('重试失败提示错误', async () => {
      const s = center()
      await s.load()
      mocks.retryAiTask.mockRejectedValueOnce(new Error('任务不存在'))
      await s.handleRetry(s.rows.value[0])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('任务不存在')
    })
  })
})
