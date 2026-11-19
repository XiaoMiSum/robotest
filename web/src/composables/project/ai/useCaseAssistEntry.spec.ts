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
      operations: [{
        op: 'update', nodeId, field: 'title',
        existing: ['旧标题'], suggested: ['新标题'],
        path: `${nodeId} 用例`, reason: '',
      }],
      sourceRefs: [],
    },
  }
}

function latestTask(taskId: string, status: AiTaskDetail['status']): AiTaskDetail {
  return { taskId, status } as AiTaskDetail
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
let executeOperations: ReturnType<typeof vi.fn>

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
          executeOperations,
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
    executeOperations = vi.fn()
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
    it('该节点无任务时按 documentId + 选中节点发起并打开面板', async () => {
      const s = mountEntry()
      await flushPromises()

      expect(mocks.useAssistTask).toHaveBeenCalledWith('case_optimize', expect.anything())
      expect(tasks).toHaveLength(1)

      await s.launchOptimize()

      expect(mocks.fetchLatestAiTask).toHaveBeenCalledWith('case_optimize', {
        documentId: 'doc-1',
        nodeId: 'node-1',
      })
      expect(s.panelVisible.value).toBe(true)
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-1'],
      })
    })

    it('按悬浮窗传入的节点查询并回退发起，而非依赖当前选中态', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-9')

      expect(mocks.fetchLatestAiTask).toHaveBeenCalledWith('case_optimize', {
        documentId: 'doc-1',
        nodeId: 'node-9',
      })
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-9'],
      })
    })

    it('不满足入口条件时不查询、不发起且提示原因', async () => {
      nodeId.value = ''
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize()

      expect(mocks.fetchLatestAiTask).not.toHaveBeenCalled()
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

  describe('按节点复用最近一次任务', () => {
    it('命中进行中任务时回显进度，不重复发起', async () => {
      mocks.fetchLatestAiTask.mockResolvedValue(latestTask('task-1', 'running'))
      const s = mountEntry()
      await flushPromises()
      tasks[0].isRunning.value = true

      await s.launchOptimize('node-1')

      expect(tasks[0].open).toHaveBeenCalledWith('task-1')
      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.panelVisible.value).toBe(true)
      expect(s.running.value).toBe(true)
    })

    it('命中已产出提案的任务时展示提案并提供重新执行', async () => {
      mocks.fetchLatestAiTask.mockResolvedValue(latestTask('task-1', 'proposals_ready'))
      const s = mountEntry()
      await flushPromises()
      tasks[0].rows.value = [optimizeRow('node-1')]

      await s.launchOptimize('node-1')

      expect(tasks[0].open).toHaveBeenCalledWith('task-1')
      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.retryable.value).toBe(true)
      expect(s.rows.value[0].title).toBe('node-1 用例')
    })

    it('已处理完毕的任务重新执行时按同一输入发起新任务', async () => {
      mocks.fetchLatestAiTask.mockResolvedValue(latestTask('task-1', 'closed'))
      const s = mountEntry()
      await flushPromises()
      tasks[0].rows.value = [optimizeRow('node-1', 'adopted')]

      await s.launchOptimize('node-1')
      tasks[0].task.value = latestTask('task-1', 'closed')
      await s.retry()

      expect(s.retryable.value).toBe(true)
      expect(tasks[0].retry).not.toHaveBeenCalled()
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-1'],
      })
    })

    it('已终止任务再次执行沿用同一任务', async () => {
      mocks.fetchLatestAiTask.mockResolvedValue(latestTask('task-1', 'aborted'))
      const s = mountEntry()
      await flushPromises()
      tasks[0].isFailed.value = true

      await s.launchOptimize('node-1')
      await s.retry()

      expect(tasks[0].open).toHaveBeenCalledWith('task-1')
      expect(tasks[0].retry).toHaveBeenCalled()
      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.failed.value).toBe(true)
      expect(s.retryable.value).toBe(false)
    })

    it('切换节点按目标节点独立查询，不复用其它节点任务', async () => {
      mocks.fetchLatestAiTask
        .mockResolvedValueOnce(latestTask('task-1', 'closed'))
        .mockResolvedValueOnce(null)
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      await s.launchOptimize('node-2')

      expect(mocks.fetchLatestAiTask).toHaveBeenLastCalledWith('case_optimize', {
        documentId: 'doc-1',
        nodeId: 'node-2',
      })
      expect(tasks[0].open).toHaveBeenCalledTimes(1)
      expect(tasks[0].open).toHaveBeenCalledWith('task-1')
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-2'],
      })
    })

    it('快速切换节点时丢弃过期查询结果', async () => {
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

      const first = s.launchOptimize('node-1')
      await s.launchOptimize('node-2')
      resolveFirst(latestTask('task-1', 'closed'))
      await first
      await nextTick()

      expect(tasks[0].open).not.toHaveBeenCalled()
      expect(tasks[0].launch).toHaveBeenCalledTimes(1)
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-2'],
      })
    })

    it('查询失败只提示错误，不自动发起任务', async () => {
      mocks.fetchLatestAiTask.mockRejectedValue(new Error('网络错误'))
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')

      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.panelVisible.value).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
    })

    it('切换文档后重新查询，不沿用上一文档的任务', async () => {
      mocks.fetchLatestAiTask.mockResolvedValueOnce(latestTask('task-1', 'closed'))
      const s = mountEntry()
      await flushPromises()

      await s.launchOptimize('node-1')
      docId.value = 'doc-2'
      await nextTick()
      mocks.fetchLatestAiTask.mockResolvedValueOnce(null)

      await s.launchOptimize('node-1')

      expect(tasks[0].reset).toHaveBeenCalled()
      expect(mocks.fetchLatestAiTask).toHaveBeenLastCalledWith('case_optimize', {
        documentId: 'doc-2',
        nodeId: 'node-1',
      })
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-2',
        nodeIds: ['node-1'],
      })
    })
  })

  describe('采纳动作与关闭', () => {
    it('采纳动作转发到当前任务并先执行节点操作', async () => {
      const s = mountEntry()
      await flushPromises()
      await s.launchOptimize()

      const editedContent = {
        operations: [{ op: 'update', nodeId: 'node-1', field: 'tags', suggested: ['a'] }],
      }
      const key1 = 'node-node-1'
      const key2 = 'node-node-2'
      tasks[0].rows.value = [optimizeRow('node-1'), optimizeRow('node-2')]
      await s.adopt(key1)
      await s.adoptEdited(key1, editedContent)
      await s.batchAdopt([key1, key2])
      await s.reject(key1, '理由')
      await s.refresh()

      expect(tasks[0].adopt).toHaveBeenCalledWith(key1)
      expect(tasks[0].adoptEdited).toHaveBeenCalledWith(key1, editedContent)
      expect(tasks[0].batchAdopt).toHaveBeenCalledWith([key1, key2])
      expect(tasks[0].reject).toHaveBeenCalledWith(key1, '理由')
      expect(tasks[0].refresh).toHaveBeenCalled()

      // 整条 / 批量采纳取提案原值，编辑后采纳取载荷值；驳回不执行
      const suggestion = tasks[0].rows.value[0].suggestion
      const proposalOps = suggestion && 'operations' in suggestion ? suggestion.operations : []
      expect(executeOperations).toHaveBeenCalledTimes(4)
      expect(executeOperations).toHaveBeenCalledWith(proposalOps, 'node-1')
      expect(executeOperations).toHaveBeenCalledWith([
        expect.objectContaining({
          op: 'update', nodeId: 'node-1', field: 'tags', suggested: ['a'],
        }),
      ], 'node-1')
      const secondSuggestion = tasks[0].rows.value[1].suggestion
      const proposalOps2 = secondSuggestion && 'operations' in secondSuggestion ? secondSuggestion.operations : []
      expect(executeOperations).toHaveBeenCalledWith(proposalOps2, 'node-2')

      s.clearReceipt()
      expect(tasks[0].clearReceipt).toHaveBeenCalled()
    })

    it('提案未就绪时采纳不执行节点操作', async () => {
      const s = mountEntry()
      await flushPromises()
      tasks[0].rows.value = [optimizeRow('node-1')]
      tasks[0].rows.value[0].suggestion = null

      await s.adopt('node-node-1')

      expect(tasks[0].adopt).toHaveBeenCalledWith('node-node-1')
      expect(executeOperations).not.toHaveBeenCalled()
    })

    it('提案无操作时仍透传锚点用于给用例节点打 AI 标识', async () => {
      const s = mountEntry()
      await flushPromises()
      tasks[0].rows.value = [optimizeRow('node-1')]
      const suggestion = tasks[0].rows.value[0].suggestion
      if (suggestion && 'operations' in suggestion) suggestion.operations = []

      await s.adopt('node-node-1')

      expect(executeOperations).toHaveBeenCalledWith([], 'node-1')
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
