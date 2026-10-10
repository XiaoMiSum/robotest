// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AiTaskDetail, AiTaskStatus } from '@/types'

const mocks = vi.hoisted(() => ({
  submitAiTask: vi.fn(),
  fetchAiTask: vi.fn(),
  fetchAiArtifact: vi.fn(),
  retryAiTask: vi.fn(),
  confirmAiArtifacts: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  submitAiTask: mocks.submitAiTask,
  fetchAiTask: mocks.fetchAiTask,
  fetchAiArtifact: mocks.fetchAiArtifact,
  retryAiTask: mocks.retryAiTask,
  confirmAiArtifacts: mocks.confirmAiArtifacts,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useAiTaskStore } from '@/stores/aiTask'
import { useAssistTask } from './useAssistTask'
import type { AssistTaskOptions } from './useAssistTask'

type AssistState = ReturnType<typeof useAssistTask>

const wrappers: Array<{ unmount: () => void }> = []

function makeDetail(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 't1',
    type: 'plan_order',
    status: 'succeeded' as AiTaskStatus,
    progress: 100,
    phase: null,
    submittedBy: null,
    retryOfTaskId: null,
    tokensIn: 0,
    tokensOut: 0,
    createdAt: '2026-10-08T02:00:00Z',
    error: null,
    result: null,
    documentMeta: null,
    artifacts: [
      {
        key: 'order-1',
        kind: 'order_suggestion',
        title: '执行顺序建议（3 项）',
        parentKey: null,
        confirmStatus: 'pending',
      },
    ],
    ...overrides,
  }
}

function orderContent(): Record<string, unknown> {
  return {
    planId: 'p1',
    items: [
      { nodeId: 'n2', caseTitle: 'B', suggestedRank: 1, reason: '阻塞面最广' },
      { nodeId: 'n1', caseTitle: 'A', suggestedRank: 2, reason: '核心链路' },
      { nodeId: 'n3', caseTitle: 'C', suggestedRank: 3, reason: '' },
    ],
    beforeOrder: ['A', 'B', 'C'],
    afterOrder: ['B', 'A', 'C'],
  }
}

function setup(): void {
  setActivePinia(createPinia())
  mocks.submitAiTask.mockReset()
  mocks.fetchAiTask.mockReset()
  mocks.fetchAiArtifact.mockReset()
  mocks.retryAiTask.mockReset()
  mocks.confirmAiArtifacts.mockReset()
  mocks.ElMessage.success.mockReset()
  mocks.ElMessage.error.mockReset()
  mocks.ElMessage.warning.mockReset()
  mocks.submitAiTask.mockResolvedValue({ taskId: 't1' })
  mocks.fetchAiTask.mockResolvedValue(makeDetail())
  mocks.fetchAiArtifact.mockResolvedValue({ content: orderContent() })
  mocks.confirmAiArtifacts.mockResolvedValue({
    results: [
      {
        key: 'order-1',
        action: 'adopted',
        success: true,
        createdId: null,
        errorCode: null,
        errorMsg: null,
      },
    ],
  })
}

function mountAssist(options: AssistTaskOptions = {}): AssistState {
  let state: AssistState | null = null
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useAssistTask('plan_order', options)
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  if (state === null) throw new Error('composable 未初始化')
  return state
}

describe('useAssistTask', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  afterEach(() => {
    while (wrappers.length > 0) wrappers.pop()?.unmount()
  })

  describe('发起与状态分支', () => {
    it('发起后拉取详情并规范化产物内容', async () => {
      const s = mountAssist()
      await s.launch({ planId: 'p1' })

      expect(mocks.submitAiTask).toHaveBeenCalledWith('plan_order', { planId: 'p1' })
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))
      expect(s.taskId.value).toBe('t1')
      expect(s.isSucceeded.value).toBe(true)
      expect(s.statusMeta.value.label).toBe('成功')
      expect(s.rows.value[0].confirmLabel).toBe('待确认')
      expect(s.rows.value[0].suggestion).toMatchObject({
        planId: 'p1',
        beforeOrder: ['A', 'B', 'C'],
      })
      expect(s.processed.value).toEqual({ processed: 0, total: 1 })
    })

    it('非终态启动轮询，轮询到终态后自动加载内容', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'running', artifacts: null }))
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.isRunning.value).toBe(true))

      mocks.fetchAiTask.mockResolvedValue(makeDetail())
      useAiTaskStore().cacheDetail(makeDetail())
      await vi.waitFor(() => expect(s.isSucceeded.value).toBe(true))
      await vi.waitFor(() => expect(s.rows.value[0].suggestion).not.toBeNull())
      expect(mocks.fetchAiArtifact).toHaveBeenCalledWith('t1', 'order-1')
    })

    it('失败态展示失败原因且不加载产物内容', async () => {
      mocks.fetchAiTask.mockResolvedValue(
        makeDetail({
          status: 'failed',
          artifacts: null,
          error: { code: 1000018117, msg: '模型调用失败' },
        }),
      )
      const s = mountAssist()
      await s.launch({})

      await vi.waitFor(() => expect(s.isFailed.value).toBe(true))
      expect(s.failedReason.value).toBe('模型调用失败')
      expect(s.rows.value).toEqual([])
      expect(mocks.fetchAiArtifact).not.toHaveBeenCalled()
    })

    it('发起失败记录错误条并提示', async () => {
      mocks.submitAiTask.mockRejectedValue(new Error('网络中断'))
      const s = mountAssist()
      await s.launch({})

      expect(s.loadError.value).toBe('网络中断')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络中断')
      expect(s.submitting.value).toBe(false)
    })

    it('挂载既有任务读取其详情', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeDetail({ taskId: 't9' }))
      const s = mountAssist()
      await s.open('t9')

      await vi.waitFor(() => expect(s.task.value?.taskId).toBe('t9'))
      expect(s.taskId.value).toBe('t9')
    })

    it('重试跟随新任务并回到排队', async () => {
      mocks.fetchAiTask
        .mockResolvedValueOnce(makeDetail({ taskId: 't1', status: 'failed', artifacts: null }))
        .mockResolvedValueOnce(makeDetail({ taskId: 't2', status: 'pending', artifacts: null }))
      mocks.retryAiTask.mockResolvedValue({ taskId: 't2' })
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.isFailed.value).toBe(true))

      await s.retry()

      expect(mocks.retryAiTask).toHaveBeenCalledWith('t1')
      expect(s.taskId.value).toBe('t2')
      expect(s.isRunning.value).toBe(true)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已重新排队')
    })

    it('reset 清空状态并停止轮询', async () => {
      mocks.fetchAiTask.mockResolvedValue(makeDetail({ status: 'running', artifacts: null }))
      const s = mountAssist()
      await s.launch({})
      const store = useAiTaskStore()
      await vi.waitFor(() => expect(store.polling).toBe(true))

      s.reset()

      expect(store.polling).toBe(false)
      expect(s.taskId.value).toBe('')
      expect(s.task.value).toBeNull()
      expect(s.rows.value).toEqual([])
    })
  })

  describe('采纳与回执', () => {
    it('采纳成功后提示、清空内容缓存并回调联动', async () => {
      const onConfirmed = vi.fn()
      const s = mountAssist({ onConfirmed })
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.adopt('order-1')

      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [{ key: 'order-1', action: 'adopted' }],
        target: undefined,
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('采纳完成')
      expect(onConfirmed).toHaveBeenCalledTimes(1)
      await vi.waitFor(() => expect(s.receipt.value).toHaveLength(1))
      expect(s.receiptSummary.value).toEqual({ succeeded: 1, failed: 0 })
    })

    it('显式 target 优先于 options.target 回退值', async () => {
      const s = mountAssist({ target: () => ({ planId: 'p1', round: 2 }) })
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.adopt('order-1', { planId: 'p2' })
      expect(mocks.confirmAiArtifacts).toHaveBeenLastCalledWith('t1', {
        items: [{ key: 'order-1', action: 'adopted' }],
        target: { planId: 'p2' },
      })

      await s.adopt('order-1')
      expect(mocks.confirmAiArtifacts).toHaveBeenLastCalledWith('t1', {
        items: [{ key: 'order-1', action: 'adopted' }],
        target: { planId: 'p1', round: 2 },
      })
    })

    it('编辑后采纳携带拖拽后的顺序清单', async () => {
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.adoptEdited('order-1', {
        items: [
          { nodeId: 'n2', suggestedRank: 1 },
          { nodeId: 'n1', suggestedRank: 2 },
        ],
      })

      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [
          {
            key: 'order-1',
            action: 'adopted_edited',
            content: {
              items: [
                { nodeId: 'n2', suggestedRank: 1 },
                { nodeId: 'n1', suggestedRank: 2 },
              ],
            },
          },
        ],
        target: undefined,
      })
    })

    it('驳回只在有说明时携带 note，空白说明剔除', async () => {
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.reject('order-1', '  ')
      expect(mocks.confirmAiArtifacts).toHaveBeenLastCalledWith('t1', {
        items: [{ key: 'order-1', action: 'rejected' }],
        target: undefined,
      })

      await s.reject('order-1', '与人工执行顺序冲突')
      expect(mocks.confirmAiArtifacts).toHaveBeenLastCalledWith('t1', {
        items: [
          { key: 'order-1', action: 'rejected', note: '与人工执行顺序冲突' },
        ],
        target: undefined,
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已全部驳回，未创建任何数据')
    })

    it('批量采纳按所选 key 逐项提交', async () => {
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.batchAdopt(['order-1', 'order-2'])

      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [
          { key: 'order-1', action: 'adopted' },
          { key: 'order-2', action: 'adopted' },
        ],
        target: undefined,
      })
    })

    it('逐项回执存在失败时按警告提示并保留成功计数', async () => {
      mocks.confirmAiArtifacts.mockResolvedValue({
        results: [
          {
            key: 'order-1',
            action: 'adopted',
            success: true,
            createdId: null,
            errorCode: null,
            errorMsg: null,
          },
          {
            key: 'order-2',
            action: 'adopted',
            success: false,
            createdId: null,
            errorCode: 1000018306,
            errorMsg: '无权限',
          },
        ],
      })
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.batchAdopt(['order-1', 'order-2'])

      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('已处理 1 项，失败 1 项')
      expect(s.receiptSummary.value).toEqual({ succeeded: 1, failed: 1 })
      s.clearReceipt()
      expect(s.receipt.value).toEqual([])
    })

    it('确认请求失败提示错误并复位确认中状态', async () => {
      mocks.confirmAiArtifacts.mockRejectedValue(new Error('状态不允许'))
      const s = mountAssist()
      await s.launch({})
      await vi.waitFor(() => expect(s.rows.value).toHaveLength(1))

      await s.adopt('order-1')

      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态不允许')
      expect(s.confirming.value).toBe(false)
    })

    it('任务尚未就绪时拦截采纳', async () => {
      const s = mountAssist()

      await s.adopt('order-1')

      expect(mocks.confirmAiArtifacts).not.toHaveBeenCalled()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('建议任务尚未就绪')
    })
  })
})
