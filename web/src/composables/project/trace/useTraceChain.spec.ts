// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { TraceChain, TraceEdge, TraceEdgeCreatePayload } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchTraceChain: vi.fn(),
  patchTraceEdge: vi.fn(),
  createTraceEdge: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchTraceChain: mocks.fetchTraceChain,
  patchTraceEdge: mocks.patchTraceEdge,
  createTraceEdge: mocks.createTraceEdge,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useTraceChain } from './useTraceChain'
import { useTraceStore, type TraceChainOrigin } from '@/stores/trace'

type TraceChainState = ReturnType<typeof useTraceChain>

function origin(overrides: Partial<TraceChainOrigin> = {}): TraceChainOrigin {
  return { type: 'requirement', id: 'r1', title: 'REQ-001 登录', ...overrides }
}

function makeChain(): TraceChain {
  return {
    root: { type: 'requirement', id: 'r1', title: 'REQ-001 登录', version: null },
    nodes: [{ id: 'r1', type: 'requirement', title: 'REQ-001 登录', version: null, level: 0 }],
    edges: [
      {
        edgeId: 'e1',
        sourceId: 'r1',
        targetId: 't1',
        edgeType: 'derivation',
        status: 'ai_created',
        targetVersion: null,
        versionMatched: null,
      },
    ],
    hasMore: false,
  }
}

function makeEdge(overrides: Partial<TraceEdge> = {}): TraceEdge {
  return {
    edgeId: 'e1',
    edgeType: 'derivation',
    source: { type: 'requirement', id: 'r1', title: 'REQ-001 登录', version: null },
    target: { type: 'test_case', id: 't1', title: '登录验证码', version: null },
    targetVersion: null,
    status: 'confirmed',
    establishedBy: 'ai',
    confirmedBy: null,
    confirmedAt: null,
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
  mocks.fetchTraceChain.mockResolvedValue(makeChain())
  mocks.patchTraceEdge.mockResolvedValue(makeEdge())
  mocks.createTraceEdge.mockResolvedValue(makeEdge())
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => true) })
}

/** composable 的 onMounted / watch 决定首屏加载时机，需经真实组件挂载触发 */
function mountChain(): { state: TraceChainState } {
  let state: TraceChainState | null = null
  mount(
    defineComponent({
      setup() {
        state = useTraceChain()
        return () => h('div')
      },
    }),
  )
  if (state === null) throw new Error('composable 未初始化')
  return { state }
}

/** 直连调用（不触发 onMounted）：用于只验证写操作的用例 */
function direct(): TraceChainState {
  return useTraceChain()
}

describe('useTraceChain', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('抽屉开关', () => {
    it('visible 读取 store 起点，置 false 时关闭抽屉', () => {
      const s = direct()
      const traceStore = useTraceStore()
      expect(s.visible.value).toBe(false)
      expect(s.origin.value).toBeNull()

      traceStore.openChain(origin())
      expect(s.visible.value).toBe(true)
      expect(s.origin.value).toEqual(origin())

      s.visible.value = false
      expect(traceStore.chainOrigin).toBeNull()
      expect(s.visible.value).toBe(false)
    })

    it('挂载时无起点不请求，出现起点后按 down 方向加载', async () => {
      const traceStore = useTraceStore()
      const { state } = mountChain()
      expect(mocks.fetchTraceChain).not.toHaveBeenCalled()

      traceStore.openChain(origin())
      await nextTick()
      expect(mocks.fetchTraceChain).toHaveBeenCalledWith({
        sourceType: 'requirement',
        sourceId: 'r1',
        direction: 'down',
      })
      await vi.waitFor(() => expect(state.chain.value).not.toBeNull())
      expect(state.chain.value?.edges).toHaveLength(1)
      expect(state.loading.value).toBe(false)
    })

    it('起点变化触发 watch 重新赋值 chain', async () => {
      const traceStore = useTraceStore()
      const s = direct()
      traceStore.openChain(origin({ id: 'r2', title: 'REQ-002 支付' }))
      await nextTick()
      expect(mocks.fetchTraceChain).toHaveBeenCalledTimes(1)
      expect(mocks.fetchTraceChain).toHaveBeenCalledWith({
        sourceType: 'requirement',
        sourceId: 'r2',
        direction: 'down',
      })
      await vi.waitFor(() => expect(s.chain.value).not.toBeNull())
    })
  })

  describe('加载失败', () => {
    it('错误落 loadError、chain 为空，可重试', async () => {
      mocks.fetchTraceChain.mockRejectedValueOnce(new Error('链路不可用'))
      const traceStore = useTraceStore()
      const { state } = mountChain()
      traceStore.openChain(origin())
      await vi.waitFor(() => expect(state.loadError.value).toBe('链路不可用'))
      expect(state.chain.value).toBeNull()
      expect(state.loading.value).toBe(false)

      state.retry()
      await vi.waitFor(() => expect(state.loadError.value).toBe(''))
      expect(state.chain.value).not.toBeNull()
      expect(mocks.fetchTraceChain.mock.calls.length).toBe(2)
    })
  })

  describe('边修正', () => {
    it('confirm 成功后提示、自增 revision 并重载', async () => {
      const traceStore = useTraceStore()
      const { state } = mountChain()
      traceStore.openChain(origin())
      await vi.waitFor(() => expect(state.chain.value).not.toBeNull())
      mocks.fetchTraceChain.mockClear()

      const ok = await state.confirmEdge('e1')
      expect(ok).toBe(true)
      expect(mocks.patchTraceEdge).toHaveBeenCalledWith('e1', { action: 'confirm' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('边已确认')
      expect(state.revision.value).toBe(1)
      expect(mocks.fetchTraceChain).toHaveBeenCalledTimes(1)
      expect(state.busy.value).toBe(false)
    })

    it('confirm 失败提示错误且 revision 不变', async () => {
      mocks.patchTraceEdge.mockRejectedValueOnce(new Error('状态不允许'))
      const traceStore = useTraceStore()
      const { state } = mountChain()
      traceStore.openChain(origin())
      await vi.waitFor(() => expect(state.chain.value).not.toBeNull())

      const ok = await state.confirmEdge('e1')
      expect(ok).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('状态不允许')
      expect(state.revision.value).toBe(0)
      expect(state.busy.value).toBe(false)
    })

    it('断开 / 恢复 / 改挂分别携带对应请求体', async () => {
      const s = direct()

      await s.detachEdge('e1', '版本已废弃')
      expect(mocks.patchTraceEdge).toHaveBeenLastCalledWith('e1', {
        action: 'detach',
        reason: '版本已废弃',
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('边已断开')

      await s.restoreEdge('e1')
      expect(mocks.patchTraceEdge).toHaveBeenLastCalledWith('e1', { action: 'restore' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('边已恢复')

      await s.reattachEdge('e1', { type: 'mindmap_document', id: 'm1' })
      expect(mocks.patchTraceEdge).toHaveBeenLastCalledWith('e1', {
        action: 'reattach',
        targetType: 'mindmap_document',
        targetId: 'm1',
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('边已改挂')

      expect(s.revision.value).toBe(3)
    })

    it('请求挂起期间 busy 为 true，结束后复位', async () => {
      const traceStore = useTraceStore()
      const { state } = mountChain()
      traceStore.openChain(origin())
      await vi.waitFor(() => expect(state.chain.value).not.toBeNull())

      const pending = deferred<TraceEdge>()
      mocks.patchTraceEdge.mockReturnValueOnce(pending.promise)
      const result = state.confirmEdge('e1')
      expect(state.busy.value).toBe(true)

      pending.resolve(makeEdge())
      expect(await result).toBe(true)
      expect(state.busy.value).toBe(false)
    })
  })

  describe('人工建边', () => {
    it('成功后提示、自增 revision 并重载', async () => {
      const traceStore = useTraceStore()
      const { state } = mountChain()
      traceStore.openChain(origin())
      await vi.waitFor(() => expect(state.chain.value).not.toBeNull())
      mocks.fetchTraceChain.mockClear()

      const payload: TraceEdgeCreatePayload = {
        edgeType: 'derivation',
        sourceType: 'requirement',
        sourceId: 'r1',
        targetType: 'test_case',
        targetId: 't1',
      }
      const ok = await state.addEdge(payload)
      expect(ok).toBe(true)
      expect(mocks.createTraceEdge).toHaveBeenCalledWith(payload)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('边已建立')
      expect(state.revision.value).toBe(1)
      expect(mocks.fetchTraceChain).toHaveBeenCalledTimes(1)
    })

    it('失败提示错误且 revision 不变', async () => {
      mocks.createTraceEdge.mockRejectedValueOnce(new Error('目标已存在'))
      const s = direct()

      const ok = await s.addEdge({
        edgeType: 'derivation',
        sourceType: 'requirement',
        sourceId: 'r1',
        targetType: 'test_case',
        targetId: 't1',
      })
      expect(ok).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('目标已存在')
      expect(s.revision.value).toBe(0)
      expect(s.busy.value).toBe(false)
    })
  })
})
