// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, AiTaskStatus } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiTask: vi.fn(),
  fetchAiStatus: vi.fn(),
  cancelAiTask: vi.fn(),
  retryAiTask: vi.fn(),
  fetchMembers: vi.fn(),
  useAuthStore: vi.fn(),
  route: { params: { taskId: 't1' }, query: {} } as Record<string, unknown>,
  router: { push: vi.fn(), replace: vi.fn() },
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  fetchAiTask: mocks.fetchAiTask,
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

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => mocks.router,
}))

import { useAiTaskDetail } from './useAiTaskDetail'

type DetailState = ReturnType<typeof useAiTaskDetail>

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 't1',
    type: 'requirement_split',
    status: 'succeeded' as AiTaskStatus,
    progress: 100,
    phase: '解析拆分建议',
    submittedBy: 'u1',
    retryOfTaskId: null,
    tokensIn: 10,
    tokensOut: 20,
    createdAt: '2026-10-03T02:00:00Z',
    error: null,
    result: null,
    documentMeta: null,
    artifacts: [
      {
        key: 'a1',
        kind: 'requirement_suggestion',
        title: '登录验证码',
        parentKey: null,
        confirmStatus: 'pending',
      },
      {
        key: 'a2',
        kind: 'requirement_suggestion',
        title: '订单超时',
        parentKey: null,
        confirmStatus: 'pending',
      },
    ],
    ...overrides,
  }
}

function setup(): void {
  setActivePinia(createPinia())
  mocks.route = { params: { taskId: 't1' }, query: {} }
  mocks.router.push.mockReset()
  mocks.router.replace.mockReset()
  mocks.fetchAiTask.mockResolvedValue(makeDetail())
  mocks.fetchAiStatus.mockResolvedValue({ available: true, reason: null })
  mocks.fetchMembers.mockResolvedValue({
    list: [{ userId: 'u1', username: 'zhangsan', name: '张三', workspaceRole: 'member' }],
    total: 1,
  })
  mocks.cancelAiTask.mockResolvedValue(makeDetail({ status: 'cancelled' }))
  mocks.retryAiTask.mockResolvedValue(
    makeDetail({ taskId: 't9', status: 'pending', artifacts: null }),
  )
  mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => true) })
}

/** 详情页初始化在 onMounted，需经真实组件挂载触发 */
function mountDetail(): DetailState {
  let state: DetailState | null = null
  mount(
    defineComponent({
      setup() {
        state = useAiTaskDetail()
        return () => h('div')
      },
    }),
  )
  if (state === null) throw new Error('composable 未初始化')
  return state
}

function apiError(code: number, message: string): Error {
  const err = new Error(message) as Error & { code?: number }
  err.code = code
  return err
}

describe('useAiTaskDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('加载与状态分支', () => {
    it('成功加载后填充派生状态与产物计数', async () => {
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      expect(s.detail.value?.taskId).toBe('t1')
      expect(s.name.value).toBe('需求拆分')
      expect(s.statusMeta.value.label).toBe('成功')
      expect(s.typeMeta.value.domain).toBe('需求')
      expect(s.phaseSteps.value.every((step) => step.state === 'done')).toBe(true)
      expect(s.artifactCounts.value).toEqual([
        { kind: 'requirement_suggestion', label: '需求建议', count: 2 },
      ])
      expect(s.showReviewArea.value).toBe(true)
      expect(s.showEmptyArtifacts.value).toBe(false)
      await vi.waitFor(() => expect(s.submitterText.value).toBe('张三'))
    })

    it('任务不存在（1000018110）展示 404 而非错误条', async () => {
      mocks.fetchAiTask.mockRejectedValue(apiError(1000018110, '任务不存在'))
      const s = mountDetail()
      await vi.waitFor(() => expect(s.notFound.value).toBe(true))
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('普通失败记录错误条并可重试', async () => {
      mocks.fetchAiTask.mockRejectedValueOnce(new Error('网络中断'))
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loadError.value).toBe('网络中断'))
      expect(s.notFound.value).toBe(false)

      s.load()
      await vi.waitFor(() => expect(s.loadError.value).toBe(''))
      expect(s.detail.value?.taskId).toBe('t1')
    })

    it('保活缓存先行展示再刷新', async () => {
      const first = mountDetail()
      await vi.waitFor(() => expect(first.loading.value).toBe(false))
      first.backToCenter()
      expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks')

      mocks.fetchAiTask.mockResolvedValueOnce(makeDetail({ progress: 55 }))
      const second = mountDetail()
      // 缓存命中：不再走骨架屏
      expect(second.loading.value).toBe(false)
      expect(second.detail.value?.progress).toBe(100)
      await vi.waitFor(() => expect(second.detail.value?.progress).toBe(55))
    })

    it('非终态启动轮询，终态不轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'running' }))
      const s = mountDetail()
      await vi.waitFor(() => expect(s.shouldPoll.value).toBe(true))
      expect(s.isPendingOrRunning.value).toBe(true)

      mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'succeeded' }))
      const terminal = mountDetail()
      await vi.waitFor(() => expect(terminal.shouldPoll.value).toBe(false))
      expect(terminal.isPendingOrRunning.value).toBe(false)
    })

    it('失败态展示失败原因与重试入口', async () => {
      mocks.fetchAiTask.mockResolvedValue(
        makeDetail({
          status: 'failed',
          artifacts: null,
          progress: 30,
          error: { code: 1000018117, msg: '模型调用失败' },
        }),
      )
      const s = mountDetail()
      await vi.waitFor(() => expect(s.isFailed.value).toBe(true))
      expect(s.showReviewArea.value).toBe(false)
      expect(s.canManageTask.value).toBe(true)
      expect(s.canConfirm.value).toBe(true)
    })

    it('成功但无产物展示空态', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeDetail({ artifacts: null }))
      const s = mountDetail()
      await vi.waitFor(() => expect(s.isSucceeded.value).toBe(true))
      expect(s.showReviewArea.value).toBe(false)
      expect(s.showEmptyArtifacts.value).toBe(true)
      expect(s.artifacts.value).toEqual([])
    })

    it('AI 状态接口失败按不可用处理', async () => {
      mocks.fetchAiStatus.mockRejectedValue(new Error('down'))
      const s = mountDetail()
      await vi.waitFor(() => expect(s.aiAvailable.value).toBe(false))
    })

    it('路由参数缺失时 taskId 为空串', () => {
      mocks.route = { params: {}, query: {} }
      const s = useAiTaskDetail()
      expect(s.taskId).toBe('')
    })

    it('权限按 ai:task / ai:confirm 显隐', () => {
      mocks.useAuthStore.mockReturnValue({
        hasPermission: (code: string) => code === 'ai:confirm',
      })
      const s = useAiTaskDetail()
      expect(s.canConfirm.value).toBe(true)
      expect(s.canManageTask.value).toBe(false)
    })

    it('发起人映射不到或接口失败时显示短 UUID，无发起人显示系统', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeDetail({ submittedBy: null }))
      const s = mountDetail()
      await vi.waitFor(() => expect(s.submitterText.value).toBe('系统'))

      mocks.fetchAiTask.mockResolvedValue(
        makeDetail({ submittedBy: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee' }),
      )
      mocks.fetchMembers.mockRejectedValue(new Error('down'))
      const t = mountDetail()
      await vi.waitFor(() => expect(t.submitterText.value).toBe('aaaaaaaa…'))

      mocks.fetchMembers.mockResolvedValue({ list: [], total: 0 })
      const u = mountDetail()
      await vi.waitFor(() => expect(u.submitterText.value).toBe('aaaaaaaa…'))
    })
  })

  describe('行操作', () => {
    it('取消需二次确认，成功后停止轮询', async () => {
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      await s.handleCancel()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.cancelAiTask).toHaveBeenCalledWith('t1')
      expect(s.detail.value?.status).toBe('cancelled')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('任务已取消')
    })

    it('取消被放弃时不发请求', async () => {
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
      await s.handleCancel()
      expect(mocks.cancelAiTask).not.toHaveBeenCalled()
    })

    it('取消失败提示错误', async () => {
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      mocks.cancelAiTask.mockRejectedValueOnce(new Error('状态不允许'))
      await s.handleCancel()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态不允许')
    })

    it('重试成功跳转新任务详情', async () => {
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      await s.handleRetry()
      expect(mocks.retryAiTask).toHaveBeenCalledWith('t1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已重新排队')
      expect(mocks.router.replace).toHaveBeenCalledWith('/workspace/projects/ai/tasks/t9')
    })

    it('重试失败提示错误', async () => {
      const s = mountDetail()
      await vi.waitFor(() => expect(s.loading.value).toBe(false))
      mocks.retryAiTask.mockRejectedValueOnce(new Error('任务不存在'))
      await s.handleRetry()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('任务不存在')
    })
  })
})
