/**
 * 脑图文档持久化（JSON 操作通路）
 * Yjs 二进制帧仅做实时协同转发、不落库；节点增删改需经同一连接的文本帧
 * 提交给后端 DocumentPersistenceHandler 持久化，否则刷新后编辑内容丢失
 */
import type { DocumentLayout } from '@/types'
import { uuidv7, UUID_RE } from '@/components/project/functional-testing/minder/adapter'

export interface PersistSnap {
  title: string
  type: string
  priority: string | null
  aiGenerated: boolean
  parentId: string | null
  sortOrder: number
}

interface LiveNode {
  data: Record<string, unknown>
  getChildren: () => LiveNode[]
}

interface MinderLike {
  getRoot?: () => LiveNode | null
  queryCommandValue?: (cmd: string) => unknown
}

export function useMindmapPersistence(getMinder: () => MinderLike | null) {
  let persistedSnapshot = new Map<string, PersistSnap>()
  let persistTimer: ReturnType<typeof setTimeout> | null = null
  let persistedLayoutJson = ''
  let applyingRemote = false
  let wsProviderRef: unknown = null

  const setApplyingRemote = (v: boolean) => { applyingRemote = v }
  const isApplyingRemote = () => applyingRemote
  const setWsProvider = (provider: unknown) => { wsProviderRef = provider }

  function getProviderSocket(): WebSocket | null {
    const ws = (wsProviderRef as { ws?: WebSocket | null } | null)?.ws
    return ws && ws.readyState === WebSocket.OPEN ? ws : null
  }

  function sendPersistOp(socket: WebSocket, type: string, data: Record<string, unknown>) {
    socket.send(JSON.stringify({ type, payload: { data } }))
  }

  function collectLiveNodes(): Map<string, PersistSnap> {
    const result = new Map<string, PersistSnap>()
    const m = getMinder()
    const root = m?.getRoot?.()
    if (!root) return result
    const walk = (node: LiveNode, parentId: string | null, sortOrder: number) => {
      const data = node.data
      if (typeof data.id !== 'string' || !UUID_RE.test(data.id)) {
        data.id = uuidv7()
      }
      const id = data.id as string
      result.set(id, {
        title: (data.text as string) ?? '',
        type: (data.type as string) || 'normal',
        priority: (data.priority as string) ?? null,
        aiGenerated: data.aiGenerated === true,
        parentId,
        sortOrder,
      })
      node.getChildren().forEach((child, index) => walk(child, id, index))
    }
    walk(root, null, 0)
    return result
  }

  function collectLayout(): DocumentLayout {
    const m = getMinder()
    const template = (m?.queryCommandValue?.('template') as string) || 'default'
    const offsets: NonNullable<DocumentLayout['offsets']> = {}
    const root = m?.getRoot?.()
    const walk = (node: LiveNode) => {
      const data = node.data
      const id = typeof data.id === 'string' ? data.id : ''
      if (id) {
        for (const key of Object.keys(data)) {
          if (!/^layout_.+_offset$/.test(key)) continue
          const point = data[key] as { x: number; y: number } | null | undefined
          if (point) (offsets[id] ??= {})[key] = { x: point.x, y: point.y }
        }
      }
      node.getChildren().forEach(walk)
    }
    if (root) walk(root)
    return { template, offsets }
  }

  function flushPersistence() {
    if (!getMinder()) return
    const socket = getProviderSocket()
    if (!socket) return
    const current = collectLiveNodes()

    for (const [id, snap] of current) {
      const prev = persistedSnapshot.get(id)
      if (!prev) {
        sendPersistOp(socket, 'add_node', { id, ...snap })
        continue
      }
      if (prev.title !== snap.title || prev.type !== snap.type || prev.priority !== snap.priority
        || prev.aiGenerated !== snap.aiGenerated) {
        sendPersistOp(socket, 'update_attrs', {
          id, title: snap.title, type: snap.type, priority: snap.priority, aiGenerated: snap.aiGenerated,
        })
      }
      if (prev.parentId !== snap.parentId || prev.sortOrder !== snap.sortOrder) {
        sendPersistOp(socket, 'move_node', { id, parentId: snap.parentId, sortOrder: snap.sortOrder })
      }
    }

    for (const [id, snap] of persistedSnapshot) {
      if (current.has(id)) continue
      const parentAlsoDeleted = snap.parentId !== null && persistedSnapshot.has(snap.parentId) && !current.has(snap.parentId)
      if (!parentAlsoDeleted) {
        sendPersistOp(socket, 'delete_node', { id })
      }
    }

    persistedSnapshot = current

    const layout = collectLayout()
    const layoutJson = JSON.stringify(layout)
    if (layoutJson !== persistedLayoutJson) {
      socket.send(JSON.stringify({ type: 'update_layout', payload: layout }))
      persistedLayoutJson = layoutJson
    }
  }

  function schedulePersist() {
    if (persistTimer) clearTimeout(persistTimer)
    persistTimer = setTimeout(() => {
      persistTimer = null
      flushPersistence()
    }, 400)
  }

  function flushPersistenceNow() {
    if (persistTimer) {
      clearTimeout(persistTimer)
      persistTimer = null
    }
    flushPersistence()
  }

  function applyLayoutOffsets(kmRoot: Record<string, unknown>, offsets?: DocumentLayout['offsets']) {
    if (!offsets) return
    const walk = (node: Record<string, unknown>) => {
      const data = node.data as Record<string, unknown>
      const id = data.id
      if (typeof id === 'string' && offsets[id]) Object.assign(data, offsets[id])
      ;(node.children as Record<string, unknown>[] | undefined)?.forEach(walk)
    }
    walk(kmRoot)
  }

  function initBaseline() {
    persistedSnapshot = collectLiveNodes()
    persistedLayoutJson = JSON.stringify(collectLayout())
  }

  function syncSnapshotFromRemote() {
    persistedSnapshot = collectLiveNodes()
    persistedLayoutJson = JSON.stringify(collectLayout())
  }

  return {
    isApplyingRemote,
    setApplyingRemote,
    setWsProvider,
    collectLiveNodes,
    collectLayout,
    flushPersistence,
    schedulePersist,
    flushPersistenceNow,
    applyLayoutOffsets,
    initBaseline,
    syncSnapshotFromRemote,
  }
}
