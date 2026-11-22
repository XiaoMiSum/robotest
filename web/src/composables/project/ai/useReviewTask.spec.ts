// @vitest-environment jsdom
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import type { AiTaskDetail } from '@/types'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  fetchLatestAiTask: vi.fn(),
  push: vi.fn(),
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  useAssistTask: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiStatus: mocks.fetchAiStatus,
  fetchLatestAiTask: mocks.fetchLatestAiTask,
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))
vi.mock('@/composables/project/ai/useAssistTask', () => ({
  useAssistTask: mocks.useAssistTask,
}))

import { useReviewTask } from './useReviewTask'
import type { ReviewTaskOptions } from './useReviewTask'

type ReviewState = ReturnType<typeof useReviewTask>

interface FakeTask {
  taskId: Ref<string>
  task: Ref<AiTaskDetail | null>
  rows: Ref<AssistRow[]>
  loading: Ref<boolean>
  confirming: Ref<boolean>
  receipt: Ref<unknown[]>
  submitting: Ref<boolean>
  isRunning: Ref<boolean>
  isFailed: Ref<boolean>
  failedReason: Ref<string>
  executionSummary: Ref<{ text: string; stepPercent: number | null } | null>
  loadError: Ref<string>
  launch: ReturnType<typeof vi.fn>
  open: ReturnType<typeof vi.fn>
  retry: ReturnType<typeof vi.fn>
  refresh: ReturnType<typeof vi.fn>
  adopt: ReturnType<typeof vi.fn>
  adoptEdited: ReturnType<typeof vi.fn>
  reject: ReturnType<typeof vi.fn>
  clearReceipt: ReturnType<typeof vi.fn>
  reset: ReturnType<typeof vi.fn>
}

function reviewRow(status: AssistRow['confirmStatus'] = 'pending'): AssistRow {
  return {
    key: 'case-1',
    title: '获取验证码',
    confirmStatus: status,
    confirmLabel: status === 'pending' ? '待确认' : '已采纳',
    confirmTagType: 'info',
    suggestion: {
      reviewId: 'rv-1',
      snapshotNodeId: 'snap-1',
      caseTitle: '获取验证码',
      suggestedMark: 'fail',
      reason: '缺少错误分支预期',
      issues: [],
      sourceRefs: [],
    },
  }
}

function latestTask(taskId: string, status: AiTaskDetail['status']): AiTaskDetail {
  return { taskId, status } as AiTaskDetail
}

function fakeTask(): FakeTask {
  const taskId = ref('')
  const task = ref<AiTaskDetail | null>(null)
  const rows = ref<AssistRow[]>([])
  return {
    taskId,
    task,
    rows,
    loading: ref(false),
    confirming: ref(false),
    receipt: ref([]),
    submitting: ref(false),
    isRunning: ref(false),
    isFailed: ref(false),
    failedReason: ref(''),
    executionSummary: ref(null),
    loadError: ref(''),
    launch: vi.fn(async (input: Record<string, unknown>) => {
      taskId.value = `task-${String(input.reviewId)}`
      rows.value = [reviewRow()]
      task.value = latestTask(taskId.value, 'closed')
    }),
    open: vi.fn(async (id: string) => {
      taskId.value = id
    }),
    retry: vi.fn(),
    refresh: vi.fn(),
    adopt: vi.fn(),
    adoptEdited: vi.fn(),
    reject: vi.fn(),
    clearReceipt: vi.fn(),
    reset: vi.fn(),
  }
}

const tasks: FakeTask[] = []
const wrappers: Array<{ unmount: () => void }> = []
const reviewId = ref('rv-1')
let confirmed: ReturnType<typeof vi.fn>
let options: ReviewTaskOptions

function mountEntry(extra: Partial<ReviewTaskOptions> = {}): ReviewState {
  let state: ReviewState | null = null
  const wrapper = mount(
    defineComponent({
      setup() {
        options = { reviewId: reviewId.value, onConfirmed: confirmed, ...extra }
        state = useReviewTask(options)
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  if (state === null) throw new Error('composable 未初始化')
  return state
}

describe('useReviewTask', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    tasks.length = 0
    reviewId.value = 'rv-1'
    confirmed = vi.fn()
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.fetchLatestAiTask.mockResolvedValue(null)
    mocks.useAssistTask.mockImplementation(() => {
      const task = fakeTask()
      tasks.push(task)
      return task
    })
  })

  afterEach(() => {
    while (wrappers.length > 0) wrappers.pop()?.unmount()
  })

  describe('入口显隐', () => {
    it('AI 未启用时入口隐藏且不发起任务', async () => {
      mocks.fetchAiStatus.mockResolvedValue({ available: false })
      const s = mountEntry()
      await flushPromises()

      expect(s.entryVisible.value).toBe(false)
      await s.open()
      expect(mocks.fetchLatestAiTask).not.toHaveBeenCalled()
    })

    it('状态接口失败按不可用处理', async () => {
      mocks.fetchAiStatus.mockRejectedValue(new Error('boom'))
      const s = mountEntry()
      await flushPromises()

      expect(s.entryVisible.value).toBe(false)
    })

    it('按 review_case_review 类型挂载任务编排并透传确认回调', async () => {
      const s = mountEntry()
      await flushPromises()

      expect(mocks.useAssistTask).toHaveBeenCalledWith('review_case_review', {
        onConfirmed: confirmed,
      })
      expect(s.entryVisible.value).toBe(true)
    })
  })

  describe('最近任务复用与发起', () => {
    it('无任务时按 reviewId 发起并打开面板', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.open()

      expect(mocks.fetchLatestAiTask).toHaveBeenCalledWith('review_case_review', {
        reviewId: 'rv-1',
      })
      expect(tasks[0].launch).toHaveBeenCalledWith({ reviewId: 'rv-1' })
      expect(s.panelVisible.value).toBe(true)
    })

    it('命中进行中任务时回显进度，不重复发起', async () => {
      mocks.fetchLatestAiTask.mockResolvedValue(latestTask('task-1', 'running'))
      const s = mountEntry()
      await flushPromises()
      tasks[0].isRunning.value = true

      await s.open()

      expect(tasks[0].open).toHaveBeenCalledWith('task-1')
      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.panelVisible.value).toBe(true)
      expect(s.running.value).toBe(true)
    })

    it('命中已产出提案的任务时展示提案并提供再次执行', async () => {
      mocks.fetchLatestAiTask.mockResolvedValue(latestTask('task-1', 'proposals_ready'))
      const s = mountEntry()
      await flushPromises()
      tasks[0].rows.value = [reviewRow()]

      await s.open()

      expect(tasks[0].open).toHaveBeenCalledWith('task-1')
      expect(s.retryable.value).toBe(true)
      expect(s.rows.value[0].title).toBe('获取验证码')
    })

    it('查询失败只提示错误，不自动发起任务', async () => {
      mocks.fetchLatestAiTask.mockRejectedValue(new Error('网络错误'))
      const s = mountEntry()
      await flushPromises()

      await s.open()

      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.panelVisible.value).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
    })

    it('快速切换评审时丢弃过期查询响应', async () => {
      let resolveFirst: (value: AiTaskDetail | null) => void = () => {}
      mocks.fetchLatestAiTask
        .mockImplementationOnce(
          () => new Promise<AiTaskDetail | null>((resolve) => {
            resolveFirst = resolve
          }),
        )
        .mockResolvedValueOnce(null)
      const s = mountEntry()
      await flushPromises()

      const first = s.open()
      reviewId.value = 'rv-2'
      options.reviewId = reviewId.value
      await s.open()
      resolveFirst(latestTask('task-old', 'closed'))
      await first
      await nextTick()

      expect(tasks[0].open).not.toHaveBeenCalled()
      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
      expect(tasks[0].launch).toHaveBeenCalledWith({ reviewId: 'rv-2' })
    })

    it('切换评审关闭面板后重新打开按新评审查询', async () => {
      const s = mountEntry()
      await flushPromises()
      await s.open()

      s.closePanel()
      options.reviewId = 'rv-2'
      await s.open()

      expect(tasks[0].reset).toHaveBeenCalled()
      expect(mocks.fetchLatestAiTask).toHaveBeenLastCalledWith('review_case_review', {
        reviewId: 'rv-2',
      })
      expect(s.panelVisible.value).toBe(true)
    })
  })

  describe('状态与动作', () => {
    it('展平任务进度、失败原因与加载错误', async () => {
      const s = mountEntry()
      await flushPromises()

      tasks[0].submitting.value = true
      expect(s.running.value).toBe(true)

      tasks[0].submitting.value = false
      tasks[0].isRunning.value = true
      tasks[0].executionSummary.value = { text: '2/20 步 · 读取快照', stepPercent: 10 }
      tasks[0].loading.value = true
      tasks[0].confirming.value = true
      tasks[0].loadError.value = '加载失败'
      expect(s.running.value).toBe(true)
      expect(s.progressText.value).toBe('2/20 步 · 读取快照')
      expect(s.loading.value).toBe(true)
      expect(s.confirming.value).toBe(true)
      expect(s.loadError.value).toBe('加载失败')

      tasks[0].isRunning.value = false
      tasks[0].isFailed.value = true
      tasks[0].failedReason.value = '模型调用失败'
      expect(s.failed.value).toBe(true)
      expect(s.failedReason.value).toBe('模型调用失败')
    })

    it('已处理任务再次执行按同一评审发起新任务，其余沿用任务重试', async () => {
      const s = mountEntry()
      await flushPromises()
      await s.open()
      tasks[0].task.value = latestTask('task-rv-1', 'closed')

      await s.retry()
      expect(tasks[0].retry).not.toHaveBeenCalled()
      expect(tasks[0].launch).toHaveBeenLastCalledWith({ reviewId: 'rv-1' })

      tasks[0].task.value = latestTask('task-rv-1', 'aborted')
      await s.retry()
      expect(tasks[0].retry).toHaveBeenCalledTimes(1)
    })

    it('采纳 / 编辑后采纳 / 驳回直接透传，前端不执行写回', async () => {
      const s = mountEntry()
      await flushPromises()
      const content = { suggestedMark: 'pass', reason: '人工复核' }

      await s.adopt('case-1')
      await s.adoptEdited('case-1', content)
      await s.reject('case-1', '本期不评审')
      await s.refresh()

      expect(tasks[0].adopt).toHaveBeenCalledWith('case-1')
      expect(tasks[0].adoptEdited).toHaveBeenCalledWith('case-1', content)
      expect(tasks[0].reject).toHaveBeenCalledWith('case-1', '本期不评审')
      expect(tasks[0].refresh).toHaveBeenCalledTimes(1)
      expect(confirmed).not.toHaveBeenCalled()

      s.clearReceipt()
      expect(tasks[0].clearReceipt).toHaveBeenCalled()
    })

    it('来源引用跳转需求详情', async () => {
      const s = mountEntry()
      await flushPromises()

      s.openSource({ id: 'r1', title: 'REQ-001', quote: '' })

      expect(mocks.push).toHaveBeenCalledWith({
        name: 'RequirementDetail',
        params: { requirementId: 'r1' },
      })
    })

    it('关闭面板释放任务实例并阻断在途查询', async () => {
      const s = mountEntry()
      await flushPromises()
      s.panelVisible.value = true

      s.closePanel()

      expect(s.panelVisible.value).toBe(false)
      expect(tasks[0].reset).toHaveBeenCalled()
    })
  })
})
