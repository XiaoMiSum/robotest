// @vitest-environment jsdom
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  hasPermission: vi.fn(),
  useAssistTask: vi.fn(),
}))

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
}))
vi.mock('@/composables/project/ai/useAssistTask', () => ({
  useAssistTask: mocks.useAssistTask,
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
  launch: ReturnType<typeof vi.fn>
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
    launch: vi.fn(),
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

function mountEntry(planId = 'p1'): PlanOrderState {
  let state: PlanOrderState | null = null
  const wrapper = mount(
    defineComponent({
      setup() {
        state = usePlanOrderAssist({ planId, onConfirmed })
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  if (state === null) throw new Error('composable 未初始化')
  return state
}

describe('usePlanOrderAssist', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    task = fakeTask()
    mocks.useAssistTask.mockReturnValue(task)
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.hasPermission.mockReturnValue(true)
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

  it('AI 未启用 / 缺少 ai:task 时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const hidden = mountEntry()
    await flushPromises()
    expect(hidden.entryVisible.value).toBe(false)

    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.hasPermission.mockReturnValue(false)
    const noPermission = mountEntry()
    await flushPromises()
    expect(noPermission.entryVisible.value).toBe(false)
  })

  it('发起打开面板并按 planId 提交', async () => {
    const s = mountEntry()
    await flushPromises()

    await s.launch()

    expect(s.panelVisible.value).toBe(true)
    expect(task.launch).toHaveBeenCalledWith({ planId: 'p1' })
  })

  it('入口隐藏时不发起', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const s = mountEntry()
    await flushPromises()

    await s.launch()

    expect(task.launch).not.toHaveBeenCalled()
    expect(s.panelVisible.value).toBe(false)
  })

  it('应用建议按拖拽后的顺序交 adopted_edited，再次执行转发 retry', async () => {
    const s = mountEntry()
    await flushPromises()

    await s.apply('order-1', { items: [{ nodeId: 'n1', suggestedRank: 1 }] })
    await s.retry()
    await s.refresh()

    expect(task.adoptEdited).toHaveBeenCalledWith('order-1', {
      items: [{ nodeId: 'n1', suggestedRank: 1 }],
    })
    expect(task.retry).toHaveBeenCalled()
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
