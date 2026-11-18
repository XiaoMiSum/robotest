// @vitest-environment jsdom
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import type { AiTaskDetail } from '@/types'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  push: vi.fn(),
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  useAssistTask: vi.fn(),
}))

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))
vi.mock('@/composables/project/ai/useAssistTask', () => ({
  useAssistTask: mocks.useAssistTask,
}))

import { useCaseAssistEntry } from './useCaseAssistEntry'
import type { CaseAssistEntryOptions } from './useCaseAssistEntry'

type EntryState = ReturnType<typeof useCaseAssistEntry>

interface FakeTask {
  taskId: Ref<string>
  task: Ref<AiTaskDetail | null>
  rows: Ref<AssistRow[]>
  loading: Ref<boolean>
  confirming: Ref<boolean>
  receipt: Ref<unknown[]>
  processed: Ref<{ processed: number; total: number }>
  submitting: Ref<boolean>
  isRunning: Ref<boolean>
  isSucceeded: Ref<boolean>
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
  batchAdopt: ReturnType<typeof vi.fn>
  reject: ReturnType<typeof vi.fn>
  clearReceipt: ReturnType<typeof vi.fn>
  reset: ReturnType<typeof vi.fn>
}

function optimizeRow(
  nodeId: string,
  status: 'pending' | 'adopted' = 'pending',
): AssistRow {
  return {
    key: `node-${nodeId}`,
    title: `${nodeId} 用例`,
    confirmStatus: status,
    confirmLabel: status === 'pending' ? '待确认' : '已采纳',
    confirmTagType: 'info',
    suggestion: {
      nodeId,
      fields: {
        precondition: { existing: [], suggested: [] },
        steps: { existing: [], suggested: [] },
        expected: { existing: [], suggested: [] },
        tags: { existing: [], suggested: [] },
      },
      priority: { current: 'P1', suggested: 'P1', reason: '' },
      sourceRefs: [],
    },
  }
}

function fakeTask(): FakeTask {
  const taskId = ref('')
  const rows = ref<AssistRow[]>([])
  const task = ref<AiTaskDetail | null>(null)
  return {
    taskId,
    task,
    rows,
    loading: ref(false),
    confirming: ref(false),
    receipt: ref<unknown[]>([]),
    processed: ref({ processed: 0, total: 0 }),
    submitting: ref(false),
    isRunning: ref(false),
    isSucceeded: ref(false),
    isFailed: ref(false),
    failedReason: ref(''),
    executionSummary: ref(null),
    loadError: ref(''),
    launch: vi.fn(async (input: Record<string, unknown>) => {
      const nodeIds = input.nodeIds as string[]
      taskId.value = `task-${nodeIds[0]}`
      rows.value = [optimizeRow(nodeIds[0])]
      task.value = { taskId: taskId.value, status: 'closed' } as AiTaskDetail
    }),
    open: vi.fn(async (id: string) => {
      taskId.value = id
    }),
    retry: vi.fn(),
    refresh: vi.fn(),
    adopt: vi.fn(),
    adoptEdited: vi.fn(),
    batchAdopt: vi.fn(),
    reject: vi.fn(),
    clearReceipt: vi.fn(),
    reset: vi.fn(),
  }
}

const tasks: FakeTask[] = []
const wrappers: Array<{ unmount: () => void }> = []

const docId = ref('doc-1')
const nodeId = ref('node-1')
const nodeType = ref('case')
const readonly = ref(false)
let onConfirmed: ReturnType<typeof vi.fn>

function mountEntry(extra: Partial<CaseAssistEntryOptions> = {}): EntryState {
  let state: EntryState | null = null
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useCaseAssistEntry({
          documentId: () => docId.value,
          selectedNodeId: () => nodeId.value,
          selectedType: () => nodeType.value,
          isReadonly: () => readonly.value,
          onConfirmed,
          ...extra,
        })
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  if (state === null) throw new Error('composable 未初始化')
  return state
}

describe('useCaseAssistEntry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    tasks.length = 0
    docId.value = 'doc-1'
    nodeId.value = 'node-1'
    nodeType.value = 'case'
    readonly.value = false
    onConfirmed = vi.fn()
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

  describe('入口显隐与置灰', () => {
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

    it('无选中节点 / 非用例节点 / 只读文档置灰并说明原因', async () => {
      const s = mountEntry()
      await flushPromises()

      nodeId.value = ''
      expect(s.canLaunch.value).toBe(false)
      expect(s.gateReason.value).toContain('选中一个用例节点')

      nodeId.value = 'node-1'
      nodeType.value = 'normal'
      expect(s.gateReason.value).toContain('仅用例节点')

      nodeType.value = 'case'
      readonly.value = true
      expect(s.gateReason.value).toContain('只读')

      readonly.value = false
      expect(s.canLaunch.value).toBe(true)
    })
  })

  describe('发起与面板状态', () => {
    it('AI 优化按 documentId + 选中节点发起并打开面板', async () => {
      const s = mountEntry()
      await flushPromises()

      expect(mocks.useAssistTask).toHaveBeenCalledWith('case_optimize', expect.anything())
      expect(tasks).toHaveLength(1)

      await s.launchOptimize()

      expect(s.panelVisible.value).toBe(true)
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-1'],
      })
    })

    it('按悬浮窗传入的节点发起，而非依赖当前选中态', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-9')

      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-9'],
      })
    })

    it('不满足入口条件时不发起且提示原因', async () => {
      nodeId.value = ''
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize()

      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.panelVisible.value).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先在脑图中选中一个用例节点')
    })

    it('面板读取任务的执行状态与进度摘要', async () => {
      const s = mountEntry()
      await flushPromises()

      tasks[0].submitting.value = false
      tasks[0].isRunning.value = true
      tasks[0].executionSummary.value = { text: '3/40 步 · 解析需求', stepPercent: 8 }
      expect(s.running.value).toBe(true)
      expect(s.progressText.value).toBe('3/40 步 · 解析需求')

      tasks[0].isRunning.value = false
      tasks[0].isFailed.value = true
      tasks[0].failedReason.value = '模型调用失败'
      expect(s.failed.value).toBe(true)
      expect(s.failedReason.value).toBe('模型调用失败')
    })

    it('发起中按运行中口径展示进度入口', async () => {
      const s = mountEntry()
      await flushPromises()

      tasks[0].submitting.value = true
      expect(s.running.value).toBe(true)
    })
  })

  describe('当前文档会话内的任务复用', () => {
    it('同一节点已有任务时直接打开，不重复发起', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()

      await s.launchOptimize('node-1')

      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
      expect(tasks[0].open).toHaveBeenCalledWith('task-node-1')
      expect(s.panelVisible.value).toBe(true)
    })

    it('未完成任务再次点击回显任务进度', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()
      tasks[0].isRunning.value = true

      await s.launchOptimize('node-1')

      expect(tasks[0].open).toHaveBeenCalledWith('task-node-1')
      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
      expect(s.running.value).toBe(true)
    })

    it('已执行完毕的任务再次点击展示提案，不重新发起', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()
      tasks[0].rows.value = [optimizeRow('node-1', 'adopted')]
      tasks[0].isSucceeded.value = true
      await nextTick()

      await s.launchOptimize('node-1')

      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
      expect(tasks[0].open).toHaveBeenCalledWith('task-node-1')
      expect(s.succeeded.value).toBe(true)
      expect(s.rows.value[0].confirmStatus).toBe('adopted')
    })

    it('切换节点只发起该节点任务，不复用其它节点的结果', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()

      await s.launchOptimize('node-2')

      expect(tasks[0].launch).toHaveBeenCalledTimes(2)
      expect(tasks[0].launch).toHaveBeenLastCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-2'],
      })
      expect(tasks[0].open).not.toHaveBeenCalled()
      expect(s.rows.value.map((row) => row.title)).toEqual(['node-2 用例'])
    })

    it('重新执行后回写任务缓存，再次点击打开新任务', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()
      tasks[0].retry.mockImplementation(async () => {
        tasks[0].taskId.value = 'task-node-1-r2'
      })

      await s.retry()
      await nextTick()

      await s.launchOptimize('node-1')

      expect(tasks[0].retry).toHaveBeenCalled()
      expect(tasks[0].open).toHaveBeenLastCalledWith('task-node-1-r2')
      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
    })

    it('任务终止后再次点击回显终止状态与再次执行入口', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()
      tasks[0].isFailed.value = true
      await nextTick()

      await s.launchOptimize('node-1')

      expect(tasks[0].open).toHaveBeenCalledWith('task-node-1')
      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
    })

    it('切换文档清空缓存，返回原文档按新任务计算', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await nextTick()

      docId.value = 'doc-2'
      await nextTick()
      docId.value = 'doc-1'
      await nextTick()

      await s.launchOptimize('node-1')

      expect(tasks[0].open).not.toHaveBeenCalled()
      expect(tasks[0].launch).toHaveBeenCalledTimes(2)
    })
  })

  describe('采纳动作与关闭', () => {
    it('采纳动作转发到当前任务', async () => {
      const s = mountEntry()
      await flushPromises()
      await s.launchOptimize()

      await s.adopt('k1')
      await s.adoptEdited('k1', { fields: { tags: ['a'] }, priority: 'P0' })
      await s.batchAdopt(['k1', 'k2'])
      await s.reject('k1', '理由')
      await s.refresh()

      expect(tasks[0].adopt).toHaveBeenCalledWith('k1')
      expect(tasks[0].adoptEdited).toHaveBeenCalledWith('k1', {
        fields: { tags: ['a'] },
        priority: 'P0',
      })
      expect(tasks[0].batchAdopt).toHaveBeenCalledWith(['k1', 'k2'])
      expect(tasks[0].reject).toHaveBeenCalledWith('k1', '理由')
      expect(tasks[0].refresh).toHaveBeenCalled()

      s.clearReceipt()
      expect(tasks[0].clearReceipt).toHaveBeenCalled()
    })

    it('关闭面板释放任务实例的本地状态', async () => {
      const s = mountEntry()
      await flushPromises()
      s.panelVisible.value = true

      s.closePanel()

      expect(s.panelVisible.value).toBe(false)
      expect(tasks[0].reset).toHaveBeenCalled()
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
  })
})
