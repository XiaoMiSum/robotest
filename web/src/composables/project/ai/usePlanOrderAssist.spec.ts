// @vitest-environment jsdom
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, isRef, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiTaskStatus } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  fetchLatestAiTask: vi.fn(),
  elMessageError: vi.fn(),
  useAssistTask: vi.fn(),
  resolveReExecuteRoute: vi.fn(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiStatus: mocks.fetchAiStatus,
  fetchLatestAiTask: mocks.fetchLatestAiTask,
}))
vi.mock('element-plus', () => ({ ElMessage: { error: mocks.elMessageError } }))
vi.mock('@/composables/project/ai/useAssistTask', () => ({
  useAssistTask: mocks.useAssistTask,
  resolveReExecuteRoute: mocks.resolveReExecuteRoute,
}))

import { usePlanOrderAssist } from './usePlanOrderAssist'

type PlanOrderState = ReturnType<typeof usePlanOrderAssist>

interface FakeTask {
  rows: Ref<AssistRow[]>
  loading: Ref<boolean>
  confirming: Ref<boolean>
  submitting: Ref<boolean>
  isRunning: Ref<boolean>
  isFailed: Ref<boolean>
  failedReason: Ref<string>
  executionSummary: Ref<{ text: string; stepPercent: number | null } | null>
  loadError: Ref<string>
  taskStatus: Ref<AiTaskStatus | undefined>
  proposalAudit: Ref<{ adopted: number; pending: number; rejected: number; notes: string[] }>
  launch: ReturnType<typeof vi.fn>
  open: ReturnType<typeof vi.fn>
  retry: ReturnType<typeof vi.fn>
  refresh: ReturnType<typeof vi.fn>
  adoptEdited: ReturnType<typeof vi.fn>
  clearReceipt: ReturnType<typeof vi.fn>
  reset: ReturnType<typeof vi.fn>
}

function fakeTask(): FakeTask {
  return {
    rows: ref<AssistRow[]>([]),
    loading: ref(false),
    confirming: ref(false),
    submitting: ref(false),
    isRunning: ref(false),
    isFailed: ref(false),
    failedReason: ref(''),
    executionSummary: ref(null),
    loadError: ref(''),
    taskStatus: ref<AiTaskStatus | undefined>(undefined),
    proposalAudit: ref({ adopted: 0, pending: 0, rejected: 0, notes: [] }),
    launch: vi.fn(),
    open: vi.fn(),
    retry: vi.fn(),
    refresh: vi.fn(),
    adoptEdited: vi.fn(),
    clearReceipt: vi.fn(),
    reset: vi.fn(),
  }
}

let task: FakeTask
const wrappers: Array<{ unmount: () => void }> = []
let onConfirmed: ReturnType<typeof vi.fn>

function mountEntry(planId: string | Ref<string> = 'p1'): PlanOrderState {
  let state: PlanOrderState | null = null
  const options = isRef(planId)
    ? { get planId() { return planId.value }, onConfirmed }
    : { planId, onConfirmed }
  const wrapper = mount(
    defineComponent({
      setup() {
        state = usePlanOrderAssist(options)
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  if (state === null) throw new Error('composable 未初始化')
  return state
}

/** 构造可手动兑现的查询响应，用于验证切计划时丢弃过期响应 */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('usePlanOrderAssist', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    task = fakeTask()
    mocks.useAssistTask.mockReturnValue(task)
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.fetchLatestAiTask.mockResolvedValue(null)
    mocks.resolveReExecuteRoute.mockImplementation((status: AiTaskStatus | undefined) =>
      status === 'running' ? 'echo' : status === 'aborted' ? 'reuse' : 'new-task',
    )
    onConfirmed = vi.fn()
  })

  afterEach(() => {
    while (wrappers.length > 0) wrappers.pop()?.unmount()
  })

  it('以 plan_order 与计划上下文初始化任务，采纳后回调刷新', async () => {
    mountEntry()

    expect(mocks.useAssistTask).toHaveBeenCalledWith(
      'plan_order',
      expect.objectContaining({
        target: expect.any(Function),
        onConfirmed,
      }),
    )
    const options = mocks.useAssistTask.mock.calls[0]?.[1] as { target: () => unknown }
    expect(options.target()).toEqual({ planId: 'p1' })
  })

  it('AI 未启用时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const hidden = mountEntry()
    await flushPromises()
    expect(hidden.entryVisible.value).toBe(false)
  })

  it('无最近任务时打开面板并按 planId 提交', async () => {
    const s = mountEntry()
    await flushPromises()

    await s.launch()

    expect(mocks.fetchLatestAiTask).toHaveBeenCalledWith('plan_order', { planId: 'p1' })
    expect(s.panelVisible.value).toBe(true)
    expect(task.launch).toHaveBeenCalledWith({ planId: 'p1' })
    expect(task.open).not.toHaveBeenCalled()
  })

  it('命中最近任务时回显而非重新发起', async () => {
    mocks.fetchLatestAiTask.mockResolvedValue({ taskId: 't-reuse' })
    const s = mountEntry()
    await flushPromises()

    await s.launch()

    expect(s.panelVisible.value).toBe(true)
    expect(task.open).toHaveBeenCalledWith('t-reuse')
    expect(task.launch).not.toHaveBeenCalled()
  })

  it('查询最近任务失败只提示错误、不自动发起', async () => {
    mocks.fetchLatestAiTask.mockRejectedValue(new Error('查询失败'))
    const s = mountEntry()
    await flushPromises()

    await s.launch()

    expect(mocks.elMessageError).toHaveBeenCalledWith('查询失败')
    expect(s.panelVisible.value).toBe(false)
    expect(task.launch).not.toHaveBeenCalled()
  })

  it('切计划后丢弃过期查询响应', async () => {
    const pending = deferred<{ taskId: string } | null>()
    mocks.fetchLatestAiTask.mockReturnValueOnce(pending.promise)
    const planId = ref('p1')
    const s = mountEntry(planId)
    await flushPromises()

    const launching = s.launch()
    planId.value = 'p2'
    await flushPromises()
    pending.resolve({ taskId: 't-stale' })
    await launching
    await flushPromises()

    expect(task.reset).toHaveBeenCalled()
    expect(task.open).not.toHaveBeenCalled()
    expect(task.launch).not.toHaveBeenCalled()
  })

  it('入口隐藏时不发起', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const s = mountEntry()
    await flushPromises()

    await s.launch()

    expect(task.launch).not.toHaveBeenCalled()
    expect(mocks.fetchLatestAiTask).not.toHaveBeenCalled()
    expect(s.panelVisible.value).toBe(false)
  })

  it('重新执行按状态分叉：running 回显、aborted 复用、其余新建任务', async () => {
    const s = mountEntry()
    await flushPromises()

    task.taskStatus.value = 'running'
    await s.retry()
    expect(task.retry).not.toHaveBeenCalled()
    expect(task.launch).not.toHaveBeenCalled()

    task.taskStatus.value = 'aborted'
    await s.retry()
    expect(task.retry).toHaveBeenCalledTimes(1)
    expect(task.launch).not.toHaveBeenCalled()

    task.taskStatus.value = 'proposals_ready'
    await s.retry()
    expect(task.launch).toHaveBeenCalledWith({ planId: 'p1' })
    expect(task.retry).toHaveBeenCalledTimes(1)
  })

  it('仅产出提案的任务提供重新执行', async () => {
    const s = mountEntry()
    await flushPromises()

    task.taskStatus.value = 'proposals_ready'
    task.rows.value = [{ key: 'order-1' }] as AssistRow[]
    expect(s.retryable.value).toBe(true)

    task.taskStatus.value = 'running'
    expect(s.retryable.value).toBe(false)
  })

  it('应用建议按拖拽后的顺序交 adopted_edited，刷新转发', async () => {
    const s = mountEntry()
    await flushPromises()

    await s.apply('order-1', { items: [{ nodeId: 'n1', suggestedRank: 1 }] })
    await s.refresh()

    expect(task.adoptEdited).toHaveBeenCalledWith('order-1', {
      items: [{ nodeId: 'n1', suggestedRank: 1 }],
    })
    expect(task.refresh).toHaveBeenCalled()
  })

  it('关闭面板释放本地状态，「放弃」同口径不产生变更', async () => {
    const s = mountEntry()
    await flushPromises()
    s.panelVisible.value = true

    s.onPanelClose()

    expect(s.panelVisible.value).toBe(false)
    expect(task.reset).toHaveBeenCalled()
    expect(task.adoptEdited).not.toHaveBeenCalled()
  })

  it('运行中展示进度摘要，失败展示终止原因', async () => {
    const s = mountEntry()
    await flushPromises()

    task.isRunning.value = true
    task.executionSummary.value = { text: '5/40 步 · 读取计划', stepPercent: 12 }
    expect(s.running.value).toBe(true)
    expect(s.progressText.value).toBe('5/40 步 · 读取计划')

    task.isRunning.value = false
    task.isFailed.value = true
    task.failedReason.value = '预算耗尽'
    expect(s.failed.value).toBe(true)
    expect(s.failedReason.value).toBe('预算耗尽')
  })
})
