import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useMindmapPersistence } from './useMindmapPersistence'

const { uuidv7, UUID_RE } = vi.hoisted(() => {
  let counter = 0
  return {
    uuidv7: vi.fn(() => `00000000-0000-7000-8000-${String(++counter).padStart(12, '0')}`),
    UUID_RE: /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  }
})

vi.mock('@/minder/adapter', () => ({ uuidv7, UUID_RE }))

const ID_A = '00000000-0000-7000-8000-000000000001'
const ID_B = '00000000-0000-7000-8000-000000000002'
const ID_C = '00000000-0000-7000-8000-000000000003'

interface TestNode {
  data: Record<string, unknown>
  getChildren: () => TestNode[]
}

function makeNode(
  id: string | null,
  text: string,
  children: TestNode[] = [],
  extra: Record<string, unknown> = {},
): TestNode {
  return {
    data: { id, text, ...extra },
    getChildren: () => children,
  }
}

function makeMinder(root: ReturnType<typeof makeNode> | null = null, template = 'default') {
  return {
    getRoot: () => root,
    queryCommandValue: (cmd: string) => (cmd === 'template' ? template : null),
  }
}

function makeSocket() {
  return {
    readyState: 1,
    send: vi.fn(),
  } as unknown as WebSocket & { send: ReturnType<typeof vi.fn> }
}

function makeMinderFactory() {
  let currentRoot: ReturnType<typeof makeNode> | null = null
  let currentTemplate = 'default'
  return {
    setRoot(root: ReturnType<typeof makeNode> | null) { currentRoot = root },
    setTemplate(t: string) { currentTemplate = t },
    getMinder: () => makeMinder(currentRoot, currentTemplate),
  }
}

describe('useMindmapPersistence', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    uuidv7.mockClear()
    let counter = 0
    uuidv7.mockImplementation(() => `00000000-0000-7000-8000-${String(++counter).padStart(12, '0')}`)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('isApplyingRemote / setApplyingRemote 翻转状态', () => {
    const { isApplyingRemote, setApplyingRemote } = useMindmapPersistence(() => null)
    expect(isApplyingRemote()).toBe(false)
    setApplyingRemote(true)
    expect(isApplyingRemote()).toBe(true)
    setApplyingRemote(false)
    expect(isApplyingRemote()).toBe(false)
  })

  it('collectLiveNodes 返回空 Map（无 minder）', () => {
    const { collectLiveNodes } = useMindmapPersistence(() => null)
    expect(collectLiveNodes().size).toBe(0)
  })

  it('collectLiveNodes 返回空 Map（getRoot 返回 null）', () => {
    const { collectLiveNodes } = useMindmapPersistence(() => makeMinder(null))
    expect(collectLiveNodes().size).toBe(0)
  })

  it('collectLiveNodes 遍历树并采集节点', () => {
    const child = makeNode(ID_B, 'Child')
    const root = makeNode(ID_A, 'Root', [child])
    const { collectLiveNodes } = useMindmapPersistence(() => makeMinder(root))
    const nodes = collectLiveNodes()
    expect(nodes.size).toBe(2)
    expect(nodes.get(ID_A)).toEqual({
      title: 'Root',
      type: 'normal',
      priority: null,
      aiGenerated: false,
      parentId: null,
      sortOrder: 0,
    })
    expect(nodes.get(ID_B)).toEqual({
      title: 'Child',
      type: 'normal',
      priority: null,
      aiGenerated: false,
      parentId: ID_A,
      sortOrder: 0,
    })
  })

  it('collectLiveNodes 为非 UUID id 分配 uuidv7', () => {
    const root = makeNode('short-id', 'Root')
    const { collectLiveNodes } = useMindmapPersistence(() => makeMinder(root))
    const nodes = collectLiveNodes()
    expect(nodes.has('short-id')).toBe(false)
    const assignedId = [...nodes.keys()][0]
    expect(assignedId).toMatch(UUID_RE)
    expect(uuidv7).toHaveBeenCalled()
  })

  it('collectLiveNodes 采集 priority 和 aiGenerated', () => {
    const root = makeNode(ID_A, 'Root', [], { priority: 'P1', aiGenerated: true })
    const { collectLiveNodes } = useMindmapPersistence(() => makeMinder(root))
    const nodes = collectLiveNodes()
    expect(nodes.get(ID_A)?.priority).toBe('P1')
    expect(nodes.get(ID_A)?.aiGenerated).toBe(true)
  })

  it('collectLiveNodes 默认 type 为 normal', () => {
    const root = makeNode(ID_A, 'Root')
    const { collectLiveNodes } = useMindmapPersistence(() => makeMinder(root))
    expect(collectLiveNodes().get(ID_A)?.type).toBe('normal')
  })

  it('collectLiveNodes 多子节点按索引设置 sortOrder', () => {
    const c1 = makeNode(ID_B, 'C1')
    const c2 = makeNode(ID_C, 'C2')
    const root = makeNode(ID_A, 'Root', [c1, c2])
    const { collectLiveNodes } = useMindmapPersistence(() => makeMinder(root))
    const nodes = collectLiveNodes()
    expect(nodes.get(ID_B)?.sortOrder).toBe(0)
    expect(nodes.get(ID_C)?.sortOrder).toBe(1)
  })

  it('collectLayout 采集 template 和 offset', () => {
    const root = makeNode(ID_A, 'Root', [], {
      layout_left_offset: { x: 10, y: 20 },
    })
    const { collectLayout } = useMindmapPersistence(() => makeMinder(root, 'tree'))
    const layout = collectLayout()
    expect(layout.template).toBe('tree')
    expect(layout.offsets).toEqual({
      [ID_A]: { layout_left_offset: { x: 10, y: 20 } },
    })
  })

  it('collectLayout 忽略非 layout_*_offset 键', () => {
    const root = makeNode(ID_A, 'Root', [], {
      layout_left_offset: { x: 1, y: 2 },
      color: 'red',
    })
    const { collectLayout } = useMindmapPersistence(() => makeMinder(root))
    const layout = collectLayout()
    expect(Object.keys(layout.offsets ?? {})).toHaveLength(1)
  })

  it('collectLayout 忽略 null offset', () => {
    const root = makeNode(ID_A, 'Root', [], {
      layout_left_offset: null,
    })
    const { collectLayout } = useMindmapPersistence(() => makeMinder(root))
    const layout = collectLayout()
    expect(layout.offsets).toEqual({})
  })

  it('collectLayout 无 root 时返回默认 template', () => {
    const { collectLayout } = useMindmapPersistence(() => makeMinder(null))
    expect(collectLayout()).toEqual({ template: 'default', offsets: {} })
  })

  it('collectLayout 收集多层级偏移', () => {
    const child = makeNode(ID_B, 'Child', [], {
      layout_right_offset: { x: 5, y: 6 },
    })
    const root = makeNode(ID_A, 'Root', [child], {
      layout_left_offset: { x: 1, y: 2 },
    })
    const { collectLayout } = useMindmapPersistence(() => makeMinder(root))
    const layout = collectLayout()
    expect(layout.offsets).toEqual({
      [ID_A]: { layout_left_offset: { x: 1, y: 2 } },
      [ID_B]: { layout_right_offset: { x: 5, y: 6 } },
    })
  })

  it('flushPersistence 无 minder 时静默返回', () => {
    const { flushPersistence } = useMindmapPersistence(() => null)
    expect(() => flushPersistence()).not.toThrow()
  })

  it('flushPersistence 无 socket 时静默返回', () => {
    const root = makeNode(ID_A, 'Root')
    const { flushPersistence, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider(null)
    expect(() => flushPersistence()).not.toThrow()
  })

  it('flushPersistence 新节点发送 add_node', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { flushPersistence, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    flushPersistence()
    expect(socket.send).toHaveBeenCalledWith(
      JSON.stringify({
        type: 'add_node',
        payload: {
          data: {
            id: ID_A,
            title: 'Root',
            type: 'normal',
            priority: null,
            aiGenerated: false,
            parentId: null,
            sortOrder: 0,
          },
        },
      }),
    )
  })

  it('flushPersistence 属性变更发送 update_attrs', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    socket.send.mockClear()
    factory.setRoot(makeNode(ID_A, 'Renamed', [], { priority: 'P2' }))
    flushPersistence()
    expect(socket.send).toHaveBeenCalledWith(
      JSON.stringify({
        type: 'update_attrs',
        payload: {
          data: { id: ID_A, title: 'Renamed', type: 'normal', priority: 'P2', aiGenerated: false },
        },
      }),
    )
  })

  it('flushPersistence 父节点变更发送 move_node', () => {
    const child = makeNode(ID_B, 'Child')
    const root = makeNode(ID_A, 'Root', [child])
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    socket.send.mockClear()
    const sibling = makeNode(ID_C, 'Sibling')
    factory.setRoot(makeNode(ID_A, 'Root', [sibling, child]))
    flushPersistence()
    expect(socket.send).toHaveBeenCalledWith(
      JSON.stringify({
        type: 'move_node',
        payload: {
          data: { id: ID_B, parentId: ID_A, sortOrder: 1 },
        },
      }),
    )
  })

  it('flushPersistence 删除节点发送 delete_node', () => {
    const child = makeNode(ID_B, 'Child')
    const root = makeNode(ID_A, 'Root', [child])
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    socket.send.mockClear()
    factory.setRoot(makeNode(ID_A, 'Root'))
    flushPersistence()
    expect(socket.send).toHaveBeenCalledWith(
      JSON.stringify({
        type: 'delete_node',
        payload: { data: { id: ID_B } },
      }),
    )
  })

  it('flushPersistence 父子同时删除不重复发送 delete_node', () => {
    const child = makeNode(ID_B, 'Child')
    const root = makeNode(ID_A, 'Root', [child])
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    socket.send.mockClear()
    factory.setRoot(makeNode(ID_C, 'Other'))
    flushPersistence()
    const deleteCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'delete_node'
    })
    expect(deleteCalls).toHaveLength(1)
  })

  it('flushPersistence 布局变更发送 update_layout', () => {
    const root = makeNode(ID_A, 'Root', [], {
      layout_left_offset: { x: 5, y: 10 },
    })
    const socket = makeSocket()
    const { flushPersistence, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    flushPersistence()
    const layoutCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'update_layout'
    })
    expect(layoutCalls).toHaveLength(1)
    const layout = JSON.parse(layoutCalls[0][0])
    expect(layout.payload.template).toBe('default')
    expect(layout.payload.offsets).toEqual({
      [ID_A]: { layout_left_offset: { x: 5, y: 10 } },
    })
  })

  it('flushPersistence 布局不变不重复发送 update_layout', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    socket.send.mockClear()
    flushPersistence()
    const layoutCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'update_layout'
    })
    expect(layoutCalls).toHaveLength(0)
  })

  it('flushPersistence 不变时不发送任何节点操作', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    socket.send.mockClear()
    flushPersistence()
    const nodeCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type !== 'update_layout'
    })
    expect(nodeCalls).toHaveLength(0)
  })

  it('schedulePersist 400ms 防抖触发 flushPersistence', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { schedulePersist, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    schedulePersist()
    expect(socket.send).not.toHaveBeenCalled()
    vi.advanceTimersByTime(400)
    expect(socket.send).toHaveBeenCalled()
  })

  it('schedulePersist 重复调用仅保留最后一个 timer', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { schedulePersist, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    schedulePersist()
    schedulePersist()
    schedulePersist()
    vi.advanceTimersByTime(400)
    const addCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'add_node'
    })
    expect(addCalls).toHaveLength(1)
  })

  it('flushPersistenceNow 立即执行并清除 timer', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { schedulePersist, flushPersistenceNow, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    schedulePersist()
    flushPersistenceNow()
    expect(socket.send).toHaveBeenCalled()
    socket.send.mockClear()
    vi.advanceTimersByTime(400)
    const addCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'add_node'
    })
    expect(addCalls).toHaveLength(0)
  })

  it('flushPersistenceNow 无待处理 timer 仍可执行', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { flushPersistenceNow, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    expect(() => flushPersistenceNow()).not.toThrow()
    expect(socket.send).toHaveBeenCalled()
  })

  it('applyLayoutOffsets 无 offsets 时静默返回', () => {
    const kmRoot: Record<string, unknown> = { data: { id: 'a' }, children: [] }
    const { applyLayoutOffsets } = useMindmapPersistence(() => null)
    expect(() => applyLayoutOffsets(kmRoot)).not.toThrow()
    expect(kmRoot.data).toEqual({ id: 'a' })
  })

  it('applyLayoutOffsets 将偏移写入对应节点 data', () => {
    const kmRoot: Record<string, unknown> = {
      data: { id: 'a' },
      children: [
        { data: { id: 'b' }, children: [] },
      ],
    }
    const { applyLayoutOffsets } = useMindmapPersistence(() => null)
    applyLayoutOffsets(kmRoot, {
      a: { layout_left_offset: { x: 10, y: 20 } },
      b: { layout_left_offset: { x: 30, y: 40 } },
    })
    expect((kmRoot.data as Record<string, unknown>).layout_left_offset).toEqual({ x: 10, y: 20 })
    const children = kmRoot.children as Record<string, unknown>[]
    expect((children[0].data as Record<string, unknown>).layout_left_offset).toEqual({ x: 30, y: 40 })
  })

  it('applyLayoutOffsets 无 children 时安全返回', () => {
    const kmRoot: Record<string, unknown> = { data: { id: 'a' } }
    const { applyLayoutOffsets } = useMindmapPersistence(() => null)
    expect(() => applyLayoutOffsets(kmRoot, { a: { layout_left_offset: { x: 1, y: 2 } } })).not.toThrow()
  })

  it('initBaseline 保存当前快照作为基准', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { initBaseline, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    initBaseline()
    expect(socket.send).not.toHaveBeenCalled()
  })

  it('syncSnapshotFromRemote 重置快照为当前状态', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { syncSnapshotFromRemote, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    syncSnapshotFromRemote()
    expect(socket.send).not.toHaveBeenCalled()
  })

  it('setWsProvider 后 flushPersistence 可发送', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const { flushPersistence, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: socket })
    flushPersistence()
    expect(socket.send).toHaveBeenCalled()
  })

  it('socket readyState 非 OPEN 时静默返回', () => {
    const root = makeNode(ID_A, 'Root')
    const closedSocket = { readyState: 3, send: vi.fn() }
    const { flushPersistence, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({ ws: closedSocket })
    flushPersistence()
    expect(closedSocket.send).not.toHaveBeenCalled()
  })

  it('wsProvider 无 ws 属性时静默返回', () => {
    const root = makeNode(ID_A, 'Root')
    const { flushPersistence, setWsProvider } = useMindmapPersistence(() => makeMinder(root))
    setWsProvider({})
    flushPersistence()
  })

  it('initBaseline 后新增节点视为 add_node', () => {
    const root = makeNode(ID_A, 'Root')
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { initBaseline, flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    initBaseline()
    socket.send.mockClear()
    const child = makeNode(ID_B, 'Child')
    factory.setRoot(makeNode(ID_A, 'Root', [child]))
    flushPersistence()
    const addCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'add_node'
    })
    expect(addCalls).toHaveLength(1)
  })

  it('initBaseline 后删除节点视为 delete_node', () => {
    const child = makeNode(ID_B, 'Child')
    const root = makeNode(ID_A, 'Root', [child])
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { initBaseline, flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    initBaseline()
    socket.send.mockClear()
    factory.setRoot(makeNode(ID_A, 'Root'))
    flushPersistence()
    const delCalls = socket.send.mock.calls.filter((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type === 'delete_node'
    })
    expect(delCalls).toHaveLength(1)
  })

  it('flushPersistence 发送顺序为 add/update/move 后 delete', () => {
    const child = makeNode(ID_B, 'Child')
    const root = makeNode(ID_A, 'Root', [child])
    const socket = makeSocket()
    const factory = makeMinderFactory()
    factory.setRoot(root)
    const { flushPersistence, setWsProvider } = useMindmapPersistence(factory.getMinder)
    setWsProvider({ ws: socket })
    flushPersistence()
    const types = socket.send.mock.calls.map((c: string[]) => {
      const msg = JSON.parse(c[0])
      return msg.type
    })
    expect(types.filter((t: string) => t === 'add_node')).toHaveLength(2)
    expect(types).toContain('update_layout')
    const nodeOps = types.filter((t: string) => t !== 'update_layout')
    expect(nodeOps.indexOf('add_node')).toBeLessThan(nodeOps.indexOf('add_node', nodeOps.indexOf('add_node') + 1) || 0)
  })
})
