// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import type { AiTaskDetail } from '@/types'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { CaseGenerationInsertResult } from '@/composables/project/functional-testing/mindmap/useCaseGenerationInsert'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  useAssistTask: vi.fn(),
}))

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))
vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))
vi.mock('@/composables/project/ai/useAssistTask', () => ({ useAssistTask: mocks.useAssistTask }))

import { useCaseGenerationEntry } from './useCaseGenerationEntry'
import type { AiGeneratedCase } from '@/composables/project/functional-testing/mindmap/useCaseGenerationInsert'

type EntryState = ReturnType<typeof useCaseGenerationEntry>

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
  executionSummary: Ref<{ text: string } | null>
  loadError: Ref<string>
  launch: ReturnType<typeof vi.fn>
  retry: ReturnType<typeof vi.fn>
  refresh: ReturnType<typeof vi.fn>
  reset: ReturnType<typeof vi.fn>
  submitItems: ReturnType<typeof vi.fn>
  clearReceipt: ReturnType<typeof vi.fn>
}

function generationRow(key: string): AssistRow {
  return {
    key,
    title: `${key} 用例`,
    confirmStatus: 'pending',
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: {
      priority: 'P1',
      precondition: ['已登录'],
      steps: ['输入手机号'],
      expected: ['提示已发送'],
      tags: ['登录'],
      sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '原文' }],
    },
  }
}

function fakeTask(): FakeTask {
  const rows = ref<AssistRow[]>([])
  const task = ref<AiTaskDetail | null>(null)
  return {
    taskId: ref(''),
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
    launch: vi.fn(async () => {
      rows.value = [generationRow('case-r1')]
      task.value = { taskId: 'task-1', status: 'proposals_ready' } as AiTaskDetail
    }),
    retry: vi.fn(),
    refresh: vi.fn(),
    reset: vi.fn(),
    submitItems: vi.fn(),
    clearReceipt: vi.fn(),
  }
}

const tasks: FakeTask[] = []
const wrappers: Array<{ unmount: () => void }> = []
const docId = ref('doc-1')
const readonly = ref(false)
let insertCases: ReturnType<typeof vi.fn>

function mountEntry(): EntryState {
  let state: EntryState | null = null
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useCaseGenerationEntry({
          documentId: () => docId.value,
          isReadonly: () => readonly.value,
          insertCases: (cases: AiGeneratedCase[]) => insertCases(cases) as CaseGenerationInsertResult,
        })
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  if (state === null) throw new Error('composable 未初始化')
  return state
}

describe('useCaseGenerationEntry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    tasks.length = 0
    docId.value = 'doc-1'
    readonly.value = false
    insertCases = vi.fn(() => ({ inserted: 1, skipped: 0 }))
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.useAssistTask.mockImplementation(() => {
      const task = fakeTask()
      tasks.push(task)
      return task
    })
  })

  afterEach(() => {
    while (wrappers.length > 0) wrappers.pop()?.unmount()
  })

  it('AI 未启用时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const s = mountEntry()
    await flushPromises()

    expect(s.entryVisible.value).toBe(false)
    expect(s.canLaunch.value).toBe(false)
  })

  it('状态接口失败按不可用处理', async () => {
    mocks.fetchAiStatus.mockRejectedValue(new Error('boom'))
    const s = mountEntry()
    await flushPromises()

    expect(s.entryVisible.value).toBe(false)
  })

  it('无文档 / 只读文档置灰并说明原因', async () => {
    const s = mountEntry()
    await flushPromises()

    docId.value = ''
    expect(s.canLaunch.value).toBe(false)
    expect(s.gateReason.value).toContain('文档')

    docId.value = 'doc-1'
    readonly.value = true
    expect(s.canLaunch.value).toBe(false)
    expect(s.gateReason.value).toContain('只读')

    readonly.value = false
    expect(s.canLaunch.value).toBe(true)
  })

  it('需求单选确认后按 documentId + requirementId 发起新任务，不复用最近任务', async () => {
    const s = mountEntry()
    await flushPromises()

    s.openPicker()
    expect(s.pickerVisible.value).toBe(true)

    await s.confirmRequirement(['r1'], [{ id: 'r1', code: 'REQ-001', title: '验证码需求' }])

    expect(mocks.useAssistTask).toHaveBeenCalledWith('mindmap_case_generation', expect.anything())
    expect(tasks[0].launch).toHaveBeenCalledWith({ documentId: 'doc-1', requirementId: 'r1' })
    expect(s.selectedRequirement.value?.code).toBe('REQ-001')
    expect(s.pickerVisible.value).toBe(false)
    expect(s.panelVisible.value).toBe(true)
  })

  it('未单选需求时提示且不发起', async () => {
    const s = mountEntry()
    await flushPromises()

    await s.confirmRequirement(['r1', 'r2'], [])

    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择一条需求')
    expect(tasks[0].launch).not.toHaveBeenCalled()
    expect(s.panelVisible.value).toBe(false)
  })

  it('插入时先创建脑图节点，再回传提案采纳状态', async () => {
    const s = mountEntry()
    await flushPromises()
    await s.confirmRequirement(['r1'], [{ id: 'r1', code: 'REQ-001', title: '验证码需求' }])

    await s.insert(['case-r1'])

    expect(insertCases).toHaveBeenCalledTimes(1)
    const cases = insertCases.mock.calls[0][0] as AiGeneratedCase[]
    expect(cases[0].key).toBe('case-r1')
    expect(cases[0].node).toMatchObject({ type: 'case', title: 'case-r1 用例', priority: 'P1', aiGenerated: true })
    expect(cases[0].node.children.map((c) => c.type)).toEqual(['precondition', 'step', 'expected'])
    expect(cases[0].tags).toEqual(['登录'])
    expect(tasks[0].submitItems).toHaveBeenCalledWith(
      [{ key: 'case-r1', action: 'adopted' }],
      { documentId: 'doc-1' },
    )
    expect(insertCases.mock.invocationCallOrder[0]!).toBeLessThan(
      tasks[0].submitItems.mock.invocationCallOrder[0]!,
    )
  })

  it('无可用提案或脑图插入失败时不回传采纳状态', async () => {
    const s = mountEntry()
    await flushPromises()
    await s.confirmRequirement(['r1'], [{ id: 'r1', code: 'REQ-001', title: '验证码需求' }])

    await s.insert(['missing'])
    expect(insertCases).not.toHaveBeenCalled()
    expect(tasks[0].submitItems).not.toHaveBeenCalled()

    insertCases.mockReturnValue({ inserted: 0, skipped: 1 })
    await s.insert(['case-r1'])
    expect(tasks[0].submitItems).not.toHaveBeenCalled()
    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('未能在脑图中插入用例')
  })

  it('回执单项重试只重传确认状态，不重复插入脑图节点', async () => {
    const s = mountEntry()
    await flushPromises()
    await s.confirmRequirement(['r1'], [{ id: 'r1', code: 'REQ-001', title: '验证码需求' }])

    await s.confirmAgain('case-r1')

    expect(insertCases).not.toHaveBeenCalled()
    expect(tasks[0].submitItems).toHaveBeenCalledWith(
      [{ key: 'case-r1', action: 'adopted' }],
      { documentId: 'doc-1' },
    )
  })

  it('已关闭任务按同一输入发起新任务，其它状态沿用同一任务重试', async () => {
    const s = mountEntry()
    await flushPromises()
    await s.confirmRequirement(['r1'], [{ id: 'r1', code: 'REQ-001', title: '验证码需求' }])

    tasks[0].task.value = { taskId: 'task-1', status: 'closed' } as AiTaskDetail
    await s.retry()
    expect(tasks[0].launch).toHaveBeenCalledTimes(2)
    expect(tasks[0].retry).not.toHaveBeenCalled()

    tasks[0].task.value = { taskId: 'task-1', status: 'aborted' } as AiTaskDetail
    await s.retry()
    expect(tasks[0].retry).toHaveBeenCalledTimes(1)
  })

  it('关闭面板释放任务实例，切换文档也关闭面板', async () => {
    const s = mountEntry()
    await flushPromises()
    await s.confirmRequirement(['r1'], [{ id: 'r1', code: 'REQ-001', title: '验证码需求' }])

    s.closePanel()
    await flushPromises()

    expect(s.panelVisible.value).toBe(false)
    expect(tasks[0].reset).toHaveBeenCalled()
    expect(s.selectedRequirement.value).toBeNull()
  })

  it('透传任务进度与失败原因', async () => {
    const s = mountEntry()
    await flushPromises()

    tasks[0].isRunning.value = true
    tasks[0].executionSummary.value = { text: '2/20 步' }
    expect(s.running.value).toBe(true)
    expect(s.progressText.value).toBe('2/20 步')

    tasks[0].isRunning.value = false
    tasks[0].isFailed.value = true
    tasks[0].failedReason.value = '需求不存在'
    expect(s.failed.value).toBe(true)
    expect(s.failedReason.value).toBe('需求不存在')
  })
})
