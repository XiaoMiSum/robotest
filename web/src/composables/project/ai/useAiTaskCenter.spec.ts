// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskItem, PageResult } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiTasks: vi.fn(),
  fetchAiStatus: vi.fn(),
  fetchAiRunningCount: vi.fn(),
  abortAiExecution: vi.fn(),
  reExecuteAiTask: vi.fn(),
  fetchMembers: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  fetchAiTasks: mocks.fetchAiTasks,
  fetchAiStatus: mocks.fetchAiStatus,
  fetchAiRunningCount: mocks.fetchAiRunningCount,
  abortAiExecution: mocks.abortAiExecution,
  reExecuteAiTask: mocks.reExecuteAiTask,
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
    executionId: 'e1',
    type: 'requirement_import',
    status: 'running',
    budget: { maxSteps: 4 },
    latestExecution: {
      executionId: 'e1',
      seq: 1,
      status: 'running',
      steps: 1,
      budget: { maxSteps: 4 },
      consumed: { durationMs: 0, tokensIn: 0, tokensOut: 0, cost: 0 },
      plan: null,
      terminateReason: null,
    },
    submittedBy: 'u1',
    tokensIn: null,
    tokensOut: null,
    createdAt: '2026-10-03T01:00:00Z',
    terminateReason: null,
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
  mocks.fetchAiRunningCount.mockResolvedValue({ count: 0 })
  mocks.fetchMembers.mockResolvedValue({
    list: [{ userId: 'u1', username: 'zhangsan', name: '张三', workspaceRole: 'member' }],
    total: 1,
  })
  mocks.abortAiExecution.mockResolvedValue(
    makeItem({
      status: 'aborted',
      terminateReason: { code: 1000018127, msg: '用户终止' },
      latestExecution: {
        executionId: 'e1',
        seq: 1,
        status: 'aborted',
        steps: 1,
        budget: { maxSteps: 4 },
        consumed: { durationMs: 0, tokensIn: 0, tokensOut: 0, cost: 0 },
        plan: null,
        terminateReason: { code: 1000018127, msg: '用户终止' },
      },
    }),
  )
  mocks.reExecuteAiTask.mockResolvedValue(makeItem({ taskId: 't2', status: 'running' }))
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
      expect(s.rows.value[0].name).toBe('需求导入')
      expect(s.rows.value[0].typeMeta.domain).toBe('需求')
      expect(s.rows.value[0].statusMeta.label).toBe('执行中')
      expect(s.rows.value[0].summary.text).toBe('1/4 步')
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
        list: [makeItem({ taskId: 't2', status: 'closed' })],
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
      s.filters.type = 'requirement_import'
      s.search()
      await vi.waitFor(() =>
        expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
          expect.objectContaining({ type: 'requirement_import', pageNo: 1 }),
        ),
      )
      expect(s.pageNo.value).toBe(1)
    })

    it('重置清空条件并恢复第一页', async () => {
      const s = center()
      await s.changePage(3)
      s.filters.status = 'aborted'
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
      s.filters.status = 'aborted'
      s.refresh()
      await vi.waitFor(() =>
        expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
          expect.objectContaining({ status: 'aborted' }),
        ),
      )
    })

    it('保活：从详情返回时沿用上次筛选与页码', async () => {
      const s = center()
      s.filters.type = 'requirement_import'
      s.filters.status = 'closed'
      s.search()
      await vi.waitFor(() => expect(s.pageNo.value).toBe(1))
      s.filters.type = ''
      s.filters.status = ''

      const { state } = mountCenter()
      await vi.waitFor(() => expect(state.hasLoaded.value).toBe(true))
      expect(state.filters).toEqual({ type: 'requirement_import', status: 'closed' })
      expect(mocks.fetchAiTasks).toHaveBeenLastCalledWith(
        expect.objectContaining({ type: 'requirement_import', status: 'closed' }),
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
    it('执行中的任务可终止，终止需二次确认并更新行', async () => {
      const s = center()
      await s.load()
      expect(s.rows.value[0].canAbort).toBe(true)
      expect(s.rows.value[0].canReExecute).toBe(false)

      await s.handleAbort(s.rows.value[0])
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.abortAiExecution).toHaveBeenCalledWith('t1', 'e1')
      expect(s.rows.value[0].status).toBe('aborted')
      expect(s.rows.value[0].statusMeta.label).toBe('已终止')
      expect(s.rows.value[0].canAbort).toBe(false)
      expect(s.rows.value[0].canReExecute).toBe(true)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('执行已终止')
    })

    it('终止被用户放弃时不发请求', async () => {
      const s = center()
      await s.load()
      mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
      await s.handleAbort(s.rows.value[0])
      expect(mocks.abortAiExecution).not.toHaveBeenCalled()
    })

    it('终止失败提示错误', async () => {
      const s = center()
      await s.load()
      mocks.abortAiExecution.mockRejectedValueOnce(new Error('状态不允许'))
      await s.handleAbort(s.rows.value[0])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态不允许')
    })

    it('已终止的任务可再次执行，成功后重取列表', async () => {
      const s = center()
      mocks.fetchAiTasks.mockResolvedValue({
        list: [
          makeItem({
            status: 'aborted',
            terminateReason: { code: 1000018127, msg: '用户终止' },
            latestExecution: {
              executionId: 'e1',
              seq: 1,
              status: 'aborted',
              steps: 2,
              budget: { maxSteps: 4 },
              consumed: { durationMs: 0, tokensIn: 0, tokensOut: 0, cost: 0 },
              plan: null,
              terminateReason: { code: 1000018127, msg: '用户终止' },
            },
          }),
        ],
        total: 1,
      } as PageResult<AiTaskItem>)
      await s.load()
      expect(s.rows.value[0].statusMeta.label).toBe('已终止')
      expect(s.rows.value[0].terminateText).toBe('用户终止')
      expect(s.rows.value[0].canReExecute).toBe(true)

      mocks.fetchAiTasks.mockClear()
      await s.handleReExecute(s.rows.value[0])
      expect(mocks.reExecuteAiTask).toHaveBeenCalledWith('t1')
      expect(mocks.fetchAiTasks).toHaveBeenCalled()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已发起再次执行')
    })

    it('再次执行失败提示错误', async () => {
      const s = center()
      await s.load()
      mocks.reExecuteAiTask.mockRejectedValueOnce(new Error('任务不存在'))
      await s.handleReExecute(s.rows.value[0])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('任务不存在')
    })

    it('待采纳任务给出审核入口标记', async () => {
      const s = center()
      mocks.fetchAiTasks.mockResolvedValue({
        list: [makeItem({ status: 'proposals_ready' })],
        total: 1,
      } as PageResult<AiTaskItem>)
      await s.load()
      expect(s.rows.value[0].canReview).toBe(true)
      expect(s.rows.value[0].statusMeta.label).toBe('待采纳')
    })
  })
})
