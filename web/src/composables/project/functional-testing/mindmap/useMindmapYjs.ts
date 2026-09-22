/**
 * 脑图 Yjs 实时协作
 * WebsocketProvider 会自动把房间名（docId）拼到 URL 尾部，serverUrl 不能重复携带；
 * 浏览器 WebSocket 无法携带 Authorization 头，token 走查询参数供后端握手拦截器校验
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAccessToken } from '@/services'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import {
  publishCanvasToYjs,
  buildJsonFromYjs,
  applyRemoteDiff,
  type KmExportJson,
  type MinderLike,
} from '@/minder/yjsSync'
import type { Minder } from '@/minder/types'

export function useMindmapYjs(
  getMinder: () => Minder | null,
  setWsProvider: (provider: unknown) => void,
  setApplyingRemote: (v: boolean) => void,
  syncSnapshotFromRemote: () => void,
  schedulePersist: () => void,
) {
  let ydoc: Y.Doc | null = null
  let wsProvider: WebsocketProvider | null = null
  const onlineUsers = ref<{ id: string; name: string; color: string }[]>([])
  const isConnected = ref(true)

  function handleServerTextFrame(raw: string) {
    try {
      const msg = JSON.parse(raw) as { type?: string; message?: string }
      if (msg.type === 'error') {
        ElMessage.error(msg.message ?? '文档保存失败')
      }
    } catch {
      /* 非 JSON 文本帧，忽略 */
    }
  }

  function patchProviderSocket() {
    const ws = (wsProvider as unknown as { ws?: (WebSocket & { __textPatched?: boolean }) | null } | null)?.ws
    if (!ws || ws.__textPatched) return
    ws.__textPatched = true
    const origin = ws.onmessage?.bind(ws)
    ws.onmessage = (event: MessageEvent) => {
      if (typeof event.data === 'string') {
        const p = wsProvider as unknown as { wsLastMessageReceived?: number } | null
        if (p) p.wsLastMessageReceived = Date.now()
        handleServerTextFrame(event.data)
        return
      }
      origin?.(event)
    }
  }

  function syncToYjs() {
    if (!ydoc) return
    const m = getMinder()
    if (!m) return
    publishCanvasToYjs(ydoc, m.exportJson() as unknown as KmExportJson)
  }

  function destroyYjs() {
    wsProvider?.destroy()
    ydoc?.destroy()
    wsProvider = null
    ydoc = null
    setWsProvider(null)
    onlineUsers.value = []
  }

  function setupYjs(docId: string) {
    destroyYjs()
    ydoc = new Y.Doc()
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/documents`
    wsProvider = new WebsocketProvider(wsUrl, docId, ydoc, {
      params: { token: getAccessToken() ?? '' },
    })
    setWsProvider(wsProvider)

    wsProvider.on('status', (event: { status: string }) => {
      isConnected.value = event.status === 'connected'
      if (event.status === 'connected') {
        patchProviderSocket()
        schedulePersist()
      }
    })

    const awareness = wsProvider.awareness
    awareness.setLocalStateField('user', { name: 'me', color: '#4A90D9' })
    awareness.on('change', () => {
      const states = awareness.getStates()
      const users: { id: string; name: string; color: string }[] = []
      states.forEach((state, clientId) => {
        if (clientId !== ydoc!.clientID && state.user) {
          users.push({ id: String(clientId), name: state.user.name, color: state.user.color })
        }
      })
      onlineUsers.value = users
    })

    const ymap = ydoc.getMap('mindmap')
    ymap.observeDeep((_events, transaction) => {
      if (transaction.local) return
      const m = getMinder()
      if (!m) return
      const remoteJson = buildJsonFromYjs(ymap)
      if (!remoteJson) return
      setApplyingRemote(true)
      try {
        const localJson = m.exportJson() as unknown as KmExportJson
        const applied = applyRemoteDiff(m as unknown as MinderLike, localJson, remoteJson)
        if (!applied) m.importJson(remoteJson as unknown as Record<string, unknown>)
      } catch {
        m.importJson(remoteJson as unknown as Record<string, unknown>)
      } finally {
        setApplyingRemote(false)
      }
      syncSnapshotFromRemote()
    })
  }

  return {
    onlineUsers,
    isConnected,
    setupYjs,
    syncToYjs,
    destroyYjs,
  }
}
