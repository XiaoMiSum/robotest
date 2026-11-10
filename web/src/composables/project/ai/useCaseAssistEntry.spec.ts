// @vitest-environment jsdom
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  hasPermission: vi.fn(),
  push: vi.fn(),
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  useAssistTask: vi.fn(),
}))

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
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
  rows: Ref<AssistRow[]>
  loading: Ref<boolean>
  confirming: Ref<boolean>
  receipt: Ref<unknown[]>
  processed: Ref<{ processed: number; total: number }>
  submitting: Ref<boolean>
  isRunning: Ref<boolean>
  isFailed: Ref<boolean>
  failedReason: Ref<string>
  executionSummary: Ref<{ text: string; stepPercent: number | null } | null>
  loadError: Ref<string>
  launch: ReturnType<typeof vi.fn>
  retry: ReturnType<typeof vi.fn>
  refresh: ReturnType<typeof vi.fn>
  adopt: ReturnType<typeof vi.fn>
  adoptEdited: ReturnType<typeof vi.fn>
  batchAdopt: ReturnType<typeof vi.fn>
  reject: ReturnType<typeof vi.fn>
  clearReceipt: ReturnType<typeof vi.fn>
  reset: ReturnType<typeof vi.fn>
}

function fakeTask(): FakeTask {
  return {
    rows: ref<AssistRow[]>([]),
    loading: ref(false),
    confirming: ref(false),
    receipt: ref<unknown[]>([]),
    processed: ref({ processed: 0, total: 0 }),
    submitting: ref(false),
    isRunning: ref(false),
    isFailed: ref(false),
    failedReason: ref(''),
    executionSummary: ref(null),
    loadError: ref(''),
    launch: vi.fn(),
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
    mocks.hasPermission.mockImplementation((code: string) => code === 'ai:task' || code === 'ai:confirm')
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

    it('缺少 ai:task 权限时入口隐藏', async () => {
      mocks.hasPermission.mockImplementation(() => false)
      const s = mountEntry()
      await flushPromises()

      expect(s.entryVisible.value).toBe(false)
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
    it('AI 补全按 documentId + 选中节点发起并打开面板', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchComplete()

      expect(s.panelVisible.value).toBe(true)
      expect(tasks[0].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-1'],
      })
      expect(tasks[1].launch).not.toHaveBeenCalled()
      expect(tasks[1].reset).toHaveBeenCalled()
    })

    it('级别推荐切换活动任务并发起', async () => {
      const s = mountEntry()
      await flushPromises()

      await s.launchPriority()

      expect(tasks[0].reset).toHaveBeenCalled()
      expect(tasks[1].launch).toHaveBeenCalledWith({
        documentId: 'doc-1',
        nodeIds: ['node-1'],
      })
      expect(s.activeKind.value).toBe('case_priority')
    })

    it('不满足入口条件时不发起且提示原因', async () => {
      nodeId.value = ''
      const s = mountEntry()
      await flushPromises()

      await s.launchComplete()

      expect(tasks[0].launch).not.toHaveBeenCalled()
      expect(s.panelVisible.value).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先在脑图中选中一个用例节点')
    })

    it('面板读取当前活动任务的执行状态与进度摘要', async () => {
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

  describe('采纳动作与关闭', () => {
    it('采纳动作转发到当前活动任务', async () => {
      const s = mountEntry()
      await flushPromises()
      await s.launchPriority()

      await s.adopt('k1')
      await s.adoptEdited('k1', { fields: { tags: ['a'] } })
      await s.batchAdopt(['k1', 'k2'])
      await s.reject('k1', '理由')
      await s.refresh()

      expect(tasks[1].adopt).toHaveBeenCalledWith('k1')
      expect(tasks[1].adoptEdited).toHaveBeenCalledWith('k1', { fields: { tags: ['a'] } })
      expect(tasks[1].batchAdopt).toHaveBeenCalledWith(['k1', 'k2'])
      expect(tasks[1].reject).toHaveBeenCalledWith('k1', '理由')
      expect(tasks[1].refresh).toHaveBeenCalled()
      expect(tasks[0].adopt).not.toHaveBeenCalled()

      s.clearReceipt()
      expect(tasks[1].clearReceipt).toHaveBeenCalled()
    })

    it('关闭面板释放两个任务实例的本地状态', async () => {
      const s = mountEntry()
      await flushPromises()
      s.panelVisible.value = true

      s.closePanel()

      expect(s.panelVisible.value).toBe(false)
      expect(tasks[0].reset).toHaveBeenCalled()
      expect(tasks[1].reset).toHaveBeenCalled()
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
