import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useMindmapYjs } from './useMindmapYjs'

vi.stubGlobal('window', {
  location: { protocol: 'https:', host: 'example.com' },
})

const shared = vi.hoisted(() => ({
  deepListeners: [] as ((events: unknown[], transaction: { local: boolean }) => void)[],
  awarenessListeners: new Map<string, (() => void)[]>(),
  awarenessState: {} as Record<string, unknown>,
  wsOnmessage: null as ((event: MessageEvent) => void) | null,
  wsTextPatched: false,
  wsLastMessageReceived: undefined as number | undefined,
  statusListeners: [] as ((event: { status: string }) => void)[],
  providerDestroy: vi.fn(),
}))

vi.mock('@/services', () => ({
  getAccessToken: vi.fn(() => 'test-token'),
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() },
}))

vi.mock('yjs', () => {
  class FakeMap {
    private data = new Map<string, unknown>()
    get(key: string) { return this.data.get(key) }
    set(key: string, value: unknown) { this.data.set(key, value) }
    delete(key: string) { this.data.delete(key) }
    keys() { return this.data.keys() }
    observeDeep(fn: (events: unknown[], transaction: { local: boolean }) => void) { shared.deepListeners.push(fn) }
    unobserveDeep(fn: (events: unknown[], transaction: { local: boolean }) => void) {
      shared.deepListeners.splice(shared.deepListeners.indexOf(fn), 1)
    }
  }

  class FakeDoc {
    clientID = 1
    private maps = new Map<string, FakeMap>()
    getMap(name: string) {
      if (!this.maps.has(name)) this.maps.set(name, new FakeMap())
      return this.maps.get(name)!
    }
    transact(fn: () => void) { fn() }
    destroy() {
      shared.deepListeners.length = 0
      shared.awarenessListeners.clear()
    }
  }

  return { Doc: FakeDoc, Map: FakeMap }
})

vi.mock('y-websocket', () => {
  const awareness = {
    state: shared.awarenessState,
    setLocalStateField(field: string, value: unknown) { this.state[field] = value },
    on(event: string, fn: () => void) {
      if (!shared.awarenessListeners.has(event)) shared.awarenessListeners.set(event, [])
      shared.awarenessListeners.get(event)!.push(fn)
    },
    getStates() {
      const m = new Map()
      m.set(1, this.state)
      m.set(999, { user: { name: 'alice', color: '#f00' } })
      return m
    },
    emit(event: string) {
      (shared.awarenessListeners.get(event) ?? []).forEach((fn) => fn())
    },
  }

  const ws = {
    get onmessage() { return shared.wsOnmessage },
    set onmessage(fn: ((event: MessageEvent) => void) | null) { shared.wsOnmessage = fn },
    get __textPatched() { return shared.wsTextPatched },
    set __textPatched(v: boolean) { shared.wsTextPatched = v },
  }

  const provider = {
    awareness,
    ws,
    wsLastMessageReceived: 0,
    destroy: shared.providerDestroy,
    on(event: string, fn: (event: { status: string }) => void) {
      if (event === 'status') shared.statusListeners.push(fn)
    },
    statusListeners: shared.statusListeners,
  }

  return {
    WebsocketProvider: vi.fn(() => provider),
  }
})

vi.mock('@/minder/yjsSync', () => ({
  publishCanvasToYjs: vi.fn(),
  buildJsonFromYjs: vi.fn(() => null),
  applyRemoteDiff: vi.fn(() => true),
}))

import { WebsocketProvider } from 'y-websocket'
import { getAccessToken } from '@/services'
import { ElMessage } from 'element-plus'
import { publishCanvasToYjs, buildJsonFromYjs, applyRemoteDiff } from '@/minder/yjsSync'

function makeSut() {
  const getMinder = vi.fn()
  const setWsProvider = vi.fn()
  const setApplyingRemote = vi.fn()
  const syncSnapshotFromRemote = vi.fn()
  const schedulePersist = vi.fn()
  const sut = useMindmapYjs(getMinder, setWsProvider, setApplyingRemote, syncSnapshotFromRemote, schedulePersist)
  return { sut, getMinder, setWsProvider, setApplyingRemote, syncSnapshotFromRemote, schedulePersist }
}

function setupYjsConnected(sut: ReturnType<typeof useMindmapYjs>) {
  sut.setupYjs('doc-1')
  shared.statusListeners[0]({ status: 'connected' })
}

describe('useMindmapYjs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    shared.deepListeners.length = 0
    shared.awarenessListeners.clear()
    for (const key of Object.keys(shared.awarenessState)) delete shared.awarenessState[key]
    shared.wsOnmessage = null
    shared.wsTextPatched = false
    shared.wsLastMessageReceived = undefined
    shared.statusListeners.length = 0
    vi.stubGlobal('window', {
      location: { protocol: 'https:', host: 'example.com' },
    })
  })

  describe('初始状态', () => {
    it('onlineUsers 为空数组', () => {
      const { sut } = makeSut()
      expect(sut.onlineUsers.value).toEqual([])
    })

    it('isConnected 为 true', () => {
      const { sut } = makeSut()
      expect(sut.isConnected.value).toBe(true)
    })
  })

  describe('setupYjs', () => {
    it('创建 Y.Doc 和 WebsocketProvider', () => {
      const { sut, setWsProvider } = makeSut()
      sut.setupYjs('doc-123')
      expect(WebsocketProvider).toHaveBeenCalledOnce()
      expect(setWsProvider).toHaveBeenCalled()
    })

    it('使用 wss 协议拼接 URL', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-123')
      expect(WebsocketProvider).toHaveBeenCalledWith(
        'wss://example.com/ws/documents',
        'doc-123',
        expect.anything(),
        { params: { token: 'test-token' } },
      )
    })

    it('token 为 null 时传空字符串', () => {
      vi.mocked(getAccessToken).mockReturnValueOnce(null)
      const { sut } = makeSut()
      sut.setupYjs('doc-123')
      expect(WebsocketProvider).toHaveBeenCalledWith(
        expect.anything(),
        expect.anything(),
        expect.anything(),
        { params: { token: '' } },
      )
    })

    it('status connected 时调用 schedulePersist', () => {
      const { sut, schedulePersist } = makeSut()
      sut.setupYjs('doc-123')
      shared.statusListeners[0]({ status: 'connected' })
      expect(schedulePersist).toHaveBeenCalledOnce()
    })

    it('status disconnected 时 isConnected 为 false', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-123')
      shared.statusListeners[0]({ status: 'disconnected' })
      expect(sut.isConnected.value).toBe(false)
    })

    it('status connected 时 isConnected 恢复 true', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-123')
      shared.statusListeners[0]({ status: 'disconnected' })
      shared.statusListeners[0]({ status: 'connected' })
      expect(sut.isConnected.value).toBe(true)
    })

    it('重复 setupYjs 先销毁旧实例', () => {
      const { sut, setWsProvider } = makeSut()
      sut.setupYjs('doc-1')
      sut.setupYjs('doc-2')
      expect(setWsProvider).toHaveBeenCalledWith(null)
      expect(WebsocketProvider).toHaveBeenCalledTimes(2)
    })
  })

  describe('awareness', () => {
    it('设置本地用户状态', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-123')
      expect(shared.awarenessState.user).toEqual({ name: 'me', color: '#4A90D9' })
    })

    it('awareness change 时更新 onlineUsers', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-123')
      shared.awarenessListeners.get('change')?.forEach((fn) => fn())
      expect(sut.onlineUsers.value).toEqual([
        { id: '999', name: 'alice', color: '#f00' },
      ])
    })
  })

  describe('syncToYjs', () => {
    it('无 ydoc 时不报错', () => {
      const { sut } = makeSut()
      expect(() => sut.syncToYjs()).not.toThrow()
    })

    it('getMinder 返回 null 时不调用 publishCanvasToYjs', () => {
      const { sut, getMinder } = makeSut()
      getMinder.mockReturnValue(null)
      sut.setupYjs('doc-1')
      sut.syncToYjs()
      expect(publishCanvasToYjs).not.toHaveBeenCalled()
    })

    it('正常调用 publishCanvasToYjs', () => {
      const { sut, getMinder } = makeSut()
      const fakeMinder = { exportJson: vi.fn(() => ({ root: { data: { id: 'r' } } })) }
      getMinder.mockReturnValue(fakeMinder)
      sut.setupYjs('doc-1')
      sut.syncToYjs()
      expect(publishCanvasToYjs).toHaveBeenCalledOnce()
    })
  })

  describe('destroyYjs', () => {
    it('销毁后 onlineUsers 清空', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-1')
      sut.destroyYjs()
      expect(sut.onlineUsers.value).toEqual([])
    })

    it('调用 setWsProvider(null)', () => {
      const { sut, setWsProvider } = makeSut()
      sut.setupYjs('doc-1')
      sut.destroyYjs()
      expect(setWsProvider).toHaveBeenCalledWith(null)
    })

    it('重复销毁不报错', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-1')
      expect(() => {
        sut.destroyYjs()
        sut.destroyYjs()
      }).not.toThrow()
    })
  })

  describe('handleServerTextFrame', () => {
    it('JSON error 类型显示 ElMessage.error', () => {
      const { sut } = makeSut()
      setupYjsConnected(sut)
      shared.wsOnmessage!(new MessageEvent('message', { data: JSON.stringify({ type: 'error', message: '保存失败' }) }))
      expect(ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('JSON error 无 message 时使用默认文案', () => {
      const { sut } = makeSut()
      setupYjsConnected(sut)
      shared.wsOnmessage!(new MessageEvent('message', { data: JSON.stringify({ type: 'error' }) }))
      expect(ElMessage.error).toHaveBeenCalledWith('文档保存失败')
    })

    it('非 JSON 文本帧静默忽略', () => {
      const { sut } = makeSut()
      setupYjsConnected(sut)
      expect(() => shared.wsOnmessage!(new MessageEvent('message', { data: 'not json' }))).not.toThrow()
      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('非 error 类型 JSON 不触发 ElMessage', () => {
      const { sut } = makeSut()
      setupYjsConnected(sut)
      shared.wsOnmessage!(new MessageEvent('message', { data: JSON.stringify({ type: 'info', message: 'hi' }) }))
      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('文本帧更新 wsLastMessageReceived', () => {
      const { sut } = makeSut()
      setupYjsConnected(sut)
      const provider = vi.mocked(WebsocketProvider).mock.results[0].value
      shared.wsOnmessage!(new MessageEvent('message', { data: 'ping' }))
      expect(provider.wsLastMessageReceived).toBeGreaterThanOrEqual(0)
    })
  })

  describe('patchProviderSocket', () => {
    it('不重复 patch', () => {
      const { sut } = makeSut()
      sut.setupYjs('doc-1')
      shared.statusListeners[0]({ status: 'connected' })
      const firstOnmessage = shared.wsOnmessage
      shared.statusListeners[0]({ status: 'connected' })
      expect(shared.wsOnmessage).toBe(firstOnmessage)
    })
  })

  describe('remote sync', () => {
    it('ymap observeDeep 本地事务不处理', () => {
      const { sut, setApplyingRemote } = makeSut()
      sut.setupYjs('doc-1')
      expect(shared.deepListeners.length).toBeGreaterThan(0)
      shared.deepListeners[0]([], { local: true })
      expect(setApplyingRemote).not.toHaveBeenCalled()
    })

    it('ymap observeDeep 远端变更调用 applyRemoteDiff', () => {
      const { sut, getMinder, setApplyingRemote } = makeSut()
      const fakeMinder = { exportJson: vi.fn(() => ({ root: { data: { id: 'r' } } })) }
      getMinder.mockReturnValue(fakeMinder)
      vi.mocked(buildJsonFromYjs).mockReturnValueOnce({ root: { data: { id: 'r' } } } as never)
      vi.mocked(applyRemoteDiff).mockReturnValueOnce(true)
      sut.setupYjs('doc-1')
      shared.deepListeners[0]([], { local: false })
      expect(setApplyingRemote).toHaveBeenCalledWith(true)
      expect(setApplyingRemote).toHaveBeenCalledWith(false)
    })

    it('ymap observeDeep applyRemoteDiff 失败时 fallback importJson', () => {
      const { sut, getMinder } = makeSut()
      const fakeMinder = {
        exportJson: vi.fn(() => ({ root: { data: { id: 'r' } } })),
        importJson: vi.fn(),
      }
      getMinder.mockReturnValue(fakeMinder)
      vi.mocked(buildJsonFromYjs).mockReturnValueOnce({ root: { data: { id: 'r' } } } as never)
      vi.mocked(applyRemoteDiff).mockReturnValueOnce(false)
      sut.setupYjs('doc-1')
      shared.deepListeners[0]([], { local: false })
      expect(fakeMinder.importJson).toHaveBeenCalled()
    })

    it('ymap observeDeep 异常时 fallback importJson', () => {
      const { sut, getMinder } = makeSut()
      const fakeMinder = {
        exportJson: vi.fn(() => { throw new Error('boom') }),
        importJson: vi.fn(),
      }
      getMinder.mockReturnValue(fakeMinder)
      vi.mocked(buildJsonFromYjs).mockReturnValueOnce({ root: { data: { id: 'r' } } } as never)
      sut.setupYjs('doc-1')
      shared.deepListeners[0]([], { local: false })
      expect(fakeMinder.importJson).toHaveBeenCalled()
    })

    it('buildJsonFromYjs 返回 null 时跳过处理', () => {
      const { sut, setApplyingRemote, getMinder } = makeSut()
      getMinder.mockReturnValue({ exportJson: vi.fn() })
      vi.mocked(buildJsonFromYjs).mockReturnValueOnce(null)
      sut.setupYjs('doc-1')
      shared.deepListeners[0]([], { local: false })
      expect(setApplyingRemote).not.toHaveBeenCalled()
    })

    it('getMinder 返回 null 时跳过处理', () => {
      const { sut, setApplyingRemote, getMinder } = makeSut()
      getMinder.mockReturnValue(null)
      vi.mocked(buildJsonFromYjs).mockReturnValueOnce({ root: { data: { id: 'r' } } } as never)
      sut.setupYjs('doc-1')
      shared.deepListeners[0]([], { local: false })
      expect(setApplyingRemote).not.toHaveBeenCalled()
    })

    it('每次远端变更后调用 syncSnapshotFromRemote', () => {
      const { sut, getMinder, syncSnapshotFromRemote } = makeSut()
      const fakeMinder = { exportJson: vi.fn(() => ({ root: { data: { id: 'r' } } })) }
      getMinder.mockReturnValue(fakeMinder)
      vi.mocked(buildJsonFromYjs).mockReturnValue({ root: { data: { id: 'r' } } } as never)
      vi.mocked(applyRemoteDiff).mockReturnValue(true)
      sut.setupYjs('doc-1')
      shared.deepListeners[0]([], { local: false })
      expect(syncSnapshotFromRemote).toHaveBeenCalledOnce()
    })
  })

  describe('http 协议', () => {
    it('http 协议使用 ws:', () => {
      vi.stubGlobal('window', {
        location: { protocol: 'http:', host: 'localhost:5173' },
      })
      const { sut } = makeSut()
      sut.setupYjs('doc-1')
      expect(WebsocketProvider).toHaveBeenCalledWith(
        'ws://localhost:5173/ws/documents',
        'doc-1',
        expect.anything(),
        expect.anything(),
      )
    })
  })
})
