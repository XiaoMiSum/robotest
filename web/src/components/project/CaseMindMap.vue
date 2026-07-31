<script setup lang="ts">
/**
 * CaseMindMap 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需管理 WebSocket 连接生命周期、
 * 编辑操作与持久化/协同深度耦合，抽到 page 层会导致大量 props/emit
 * 透传且破坏协作状态一致性。设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { onMounted, onBeforeUnmount, ref, watch, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDocumentNodes } from '@/services/project'
import { getAccessToken } from '@/services'
import type { AiGeneratedNode, DocumentLayout } from '@/types'
import { useAiStore } from '@/stores/ai'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import { KMEditor } from './minder/editor'
import { DEFAULT_NODE_TEXT } from './minder/jumping'
import { copySelected, cutSelected, pasteToSelected, hasClipboard } from './minder/clipboard'
import { caseNodeToKm, uuidv7, UUID_RE } from './minder/adapter'
import { loadMinderEngine } from './minder/loader'
import { useMinderInstance } from './minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from './minder/useContextMenu'
import MinderContextMenu from './minder/MinderContextMenu.vue'
import MinderNavigator from './minder/MinderNavigator.vue'
import AiGeneratePanel from './minder/ai/AiGeneratePanel.vue'
import { mountGeneratedNodes, type MountTargetSource } from './minder/ai/aiMount'

const props = defineProps<{ docId: string }>()

// 编辑内核：contenteditable 接收器统一接管键盘，提供原位编辑与打字即编辑
let kmEditor: KMEditor | null = null

// 基座选中状态（id/type）之上的扩展字段
const selectedPriority = ref('')
const selectedAiGenerated = ref(false)
const canUndo = ref(false)
const canRedo = ref(false)

const {
  containerRef,
  loading,
  minder,
  selectedNodeId,
  selectedType,
  beginInit,
  isStale,
  invalidate,
  getMinder,
  getSelectedNodeData,
  updateSelectedState,
  destroyMinder,
} = useMinderInstance({
  onSelectionChange(data) {
    selectedPriority.value = data ? (data.priority as string) || '' : ''
    selectedAiGenerated.value = data ? data.aiGenerated === true : false
  },
})

// Yjs 实时协作
let ydoc: Y.Doc | null = null
let wsProvider: WebsocketProvider | null = null
const onlineUsers = ref<{ id: string; name: string; color: string }[]>([])
const isConnected = ref(true)

const priorities = ['P0', 'P1', 'P2', 'P3']

// ==================== AI 生成用例（US-AI-001） ====================
// 入口显隐由工作空间级 AI 开关控制（stores/ai 缓存 status，未启用隐藏全部 AI 入口）
const aiStore = useAiStore()

const aiPanelVisible = ref(false)
const aiTargetNodeId = ref('')
const aiTargetPath = ref('')
// 目标节点被协同删除时暂存预览结果，重选挂载位置后继续（交互设计 2.2，不丢弃预览）
const aiPendingNodes = ref<AiGeneratedNode[] | null>(null)
const aiReselectVisible = ref(false)

interface ReselectTreeNode {
  id: string
  label: string
  children: ReselectTreeNode[]
}
const aiReselectTree = ref<ReselectTreeNode[]>([])

function getLiveRoot(): MountTargetSource | null {
  const m = getMinder() as unknown as { getRoot?: () => MountTargetSource | null } | null
  return m?.getRoot?.() ?? null
}

/** 根到目标节点的标题路径（找不到返回 null） */
function findNodePath(node: MountTargetSource | null, id: string): string[] | null {
  if (!node) return null
  const title = (node.data.text as string) ?? ''
  if (node.data.id === id) return [title]
  for (const child of node.getChildren()) {
    const sub = findNodePath(child, id)
    if (sub) return [title, ...sub]
  }
  return null
}

// 打开抽屉时锁定挂载目标：当前选中节点，未选中默认文档根节点（SRS 3.2.1）
function openAiPanel() {
  const root = getLiveRoot()
  if (!root) return
  const selected = getSelectedNodeData()
  const targetId = (selected?.id as string) || (root.data.id as string) || ''
  if (!targetId) return
  aiTargetNodeId.value = targetId
  aiTargetPath.value = (findNodePath(root, targetId) ?? []).join(' > ')
  aiPendingNodes.value = null
  aiPanelVisible.value = true
}

// 确认挂载：目标存在则经挂载执行器批量创建（单撤销组，自动搭上协同与落库管道）
function handleAiMount(nodes: AiGeneratedNode[]) {
  const m = getMinder()
  if (!m) return
  const count = mountGeneratedNodes(m as unknown as Parameters<typeof mountGeneratedNodes>[0], aiTargetNodeId.value, nodes)
  if (count === null) {
    // 目标已被协同删除：弹出节点选择器重选，预览结果保留在抽屉中
    aiPendingNodes.value = nodes
    aiReselectTree.value = buildReselectTree(getLiveRoot())
    aiReselectVisible.value = true
    ElMessage.warning('挂载目标已被删除，请重新选择挂载位置')
    return
  }
  ElMessage.success(`已挂载 ${count} 个 AI 生成节点`)
  aiPendingNodes.value = null
  aiPanelVisible.value = false
}

function buildReselectTree(node: MountTargetSource | null): ReselectTreeNode[] {
  if (!node) return []
  return [{
    id: (node.data.id as string) ?? '',
    label: (node.data.text as string) ?? '',
    children: node.getChildren().flatMap((child) => buildReselectTree(child)),
  }]
}

function handleAiReselect(node: ReselectTreeNode) {
  const pending = aiPendingNodes.value
  aiReselectVisible.value = false
  if (!pending) return
  aiTargetNodeId.value = node.id
  const root = getLiveRoot()
  aiTargetPath.value = (findNodePath(root, node.id) ?? []).join(' > ')
  handleAiMount(pending)
}

// ==================== 文档持久化（JSON 操作通路） ====================
// Yjs 二进制帧仅做实时协同转发、不落库；节点增删改需经同一连接的文本帧
// 提交给后端 DocumentPersistenceHandler 持久化，否则刷新后编辑内容丢失
interface PersistSnap {
  title: string
  type: string
  priority: string | null
  aiGenerated: boolean
  parentId: string | null
  sortOrder: number
}

let persistedSnapshot = new Map<string, PersistSnap>()
let persistTimer: ReturnType<typeof setTimeout> | null = null
// 远端变更经 importJson 回放时不产生本地持久化操作，避免多端重复落库
let applyingRemote = false

function getProviderSocket(): WebSocket | null {
  const ws = (wsProvider as unknown as { ws?: WebSocket | null } | null)?.ws
  return ws && ws.readyState === WebSocket.OPEN ? ws : null
}

// y-websocket 把收到的所有帧按二进制协议解码，服务端的 JSON 文本帧
// （持久化错误回执/操作广播）会令其解码抛错，因此接管 onmessage 截流文本帧
function patchProviderSocket() {
  const ws = (wsProvider as unknown as { ws?: (WebSocket & { __textPatched?: boolean }) | null } | null)?.ws
  if (!ws || ws.__textPatched) return
  ws.__textPatched = true
  const origin = ws.onmessage?.bind(ws)
  ws.onmessage = (event: MessageEvent) => {
    if (typeof event.data === 'string') {
      // 文本帧被截流后 y-websocket 感知不到，需手动刷新活跃时间戳，
      // 否则其 30 秒假死检测会误判断连（二进制帧走 origin 由其自行刷新）；
      // lib0 的 getUnixTime 就是 Date.now（毫秒），此处单位须一致
      const p = wsProvider as unknown as { wsLastMessageReceived?: number } | null
      if (p) p.wsLastMessageReceived = Date.now()
      handleServerTextFrame(event.data)
      return
    }
    origin?.(event)
  }
}

function handleServerTextFrame(raw: string) {
  try {
    const msg = JSON.parse(raw) as { type?: string; message?: string }
    if (msg.type === 'error') {
      ElMessage.error(msg.message ?? '文档保存失败')
    }
    // 其他客户端操作的 JSON 广播无需处理：画布同步依赖 Yjs 二进制通路
  } catch {
    /* 非 JSON 文本帧，忽略 */
  }
}

interface LiveNode {
  data: Record<string, unknown>
  getChildren: () => LiveNode[]
}

// 遍历画布真实节点（exportJson 是拷贝，写不回 id），顺便为新节点生成 UUID
function collectLiveNodes(): Map<string, PersistSnap> {
  const result = new Map<string, PersistSnap>()
  const m = getMinder() as unknown as { getRoot?: () => LiveNode | null } | null
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

function sendPersistOp(socket: WebSocket, type: string, data: Record<string, unknown>) {
  socket.send(JSON.stringify({ type, payload: { data } }))
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
    // 后端会级联删除子树，只需提交被删子树的顶层节点
    const parentAlsoDeleted = snap.parentId !== null && persistedSnapshot.has(snap.parentId) && !current.has(snap.parentId)
    if (!parentAlsoDeleted) {
      sendPersistOp(socket, 'delete_node', { id })
    }
  }

  persistedSnapshot = current

  // 布局（模板 + 自由拖拽偏移）独立于节点属性，整体走 update_layout 帧 upsert 布局表
  const layout = collectLayout()
  const layoutJson = JSON.stringify(layout)
  if (layoutJson !== persistedLayoutJson) {
    socket.send(JSON.stringify({ type: 'update_layout', payload: layout }))
    persistedLayoutJson = layoutJson
  }
}

// 布局落库基线：与节点快照同理，仅在有变化时发送 update_layout
let persistedLayoutJson = ''

function collectLayout(): DocumentLayout {
  const m = getMinder()
  const template = (m?.queryCommandValue?.('template') as string) || 'default'
  const offsets: NonNullable<DocumentLayout['offsets']> = {}
  const root = (m as unknown as { getRoot?: () => LiveNode | null } | null)?.getRoot?.()
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

// 把持久化的自由拖拽偏移回填进 km 节点 data，importJson 时随布局生效
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

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => {
    persistTimer = null
    flushPersistence()
  }, 400)
}

// 立即冲刷未落库的增量编辑（切换文档/卸载前调用）
function flushPersistenceNow() {
  if (persistTimer) {
    clearTimeout(persistTimer)
    persistTimer = null
  }
  flushPersistence()
}

// ==================== Yjs 实时协作 ====================
function setupYjs(docId: string) {
  destroyYjs()
  ydoc = new Y.Doc()
  // WebsocketProvider 会自动把房间名（docId）拼到 URL 尾部，serverUrl 不能重复携带；
  // 浏览器 WebSocket 无法携带 Authorization 头，token 走查询参数供后端握手拦截器校验
  const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/documents`
  wsProvider = new WebsocketProvider(wsUrl, docId, ydoc, {
    params: { token: getAccessToken() ?? '' },
  })

  wsProvider.on('status', (event: { status: string }) => {
    isConnected.value = event.status === 'connected'
    if (event.status === 'connected') {
      // 重连会创建新的 WebSocket 实例，需要重新拦截文本帧；
      // 断线期间的本地编辑在重连后重新 diff 补发
      patchProviderSocket()
      flushPersistence()
    }
  })

  // 协作感知：跟踪其他在线用户以渲染彩色光标
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

  // 远端 Yjs 变更同步到本地画布，确保多人编辑一致性
  const ymap = ydoc.getMap('mindmap')
  ymap.observe((_event, transaction) => {
    // 本地 syncToYjs 触发的 observe 无需回放，否则 importJson→contentchange→set 死循环
    if (transaction.local) return
    const m = getMinder()
    if (!m) return
    const remoteData = ymap.get('data')
    if (!remoteData) return
    applyingRemote = true
    try {
      m.importJson(remoteData)
    } finally {
      applyingRemote = false
    }
    // 远端操作由发起方负责落库，本端只需对齐快照防止重复 diff 提交
    persistedSnapshot = collectLiveNodes()
    persistedLayoutJson = JSON.stringify(collectLayout())
  })
}

function syncToYjs() {
  if (!ydoc || !getMinder()) return
  const m = getMinder()!
  const json = m.exportJson()
  const ymap = ydoc.getMap('mindmap')
  ydoc.transact(() => {
    ymap.set('data', json)
  })
}

function destroyYjs() {
  wsProvider?.destroy()
  ydoc?.destroy()
  wsProvider = null
  ydoc = null
  onlineUsers.value = []
}

// ==================== 初始化 ====================
// 实例经编辑内核创建，销毁也须走内核（接收器/历史等 runtime 一并清理）
function teardownMinder() {
  destroyMinder(kmEditor ? () => kmEditor?.destroy() : undefined)
  kmEditor = null
}

async function initMinder() {
  if (!containerRef.value || !props.docId) return
  const token = beginInit()
  loading.value = true
  // 切换文档前冲刷防抖中的增量编辑，避免旧文档最后一次修改丢失
  flushPersistenceNow()
  destroyYjs()
  teardownMinder()
  try {
    const docData = await fetchDocumentNodes(props.docId)
    const root = caseNodeToKm(docData.node)
    // 应用已保存的布局：模板 + 自由拖拽偏移；无记录时回退默认思维导图
    applyLayoutOffsets(root, docData.layout?.offsets)
    const template = docData.layout?.template || 'default'
    currentTemplate.value = template
    const kmData = { root, template, theme: 'fresh-blue' }

    await loadMinderEngine()
    // 异步等待期间组件可能已卸载或已切换文档，过期结果直接丢弃
    if (isStale(token) || !containerRef.value) return

    kmEditor = new KMEditor(containerRef.value, {
      // 远端协同回放不入本地撤销栈，避免撤销掉他人的编辑
      historyFrozen: () => applyingRemote,
      onHistoryChange: () => {
        canUndo.value = kmEditor?.history.hasUndo() ?? false
        canRedo.value = kmEditor?.history.hasRedo() ?? false
      },
      // 空格唤醒右键菜单（内核仅在选中节点时触发）
      onMenuRequest: openContextMenuAtSelection,
    })
    const instance: unknown = kmEditor.minder
    minder.value = instance
    const m = instance as Record<string, (...args: unknown[]) => unknown>
    m.importJson(kmData)

    // 监听事件
    m.on('selectionchange', updateSelectedState)
    m.on('contentchange', () => {
      // 撤销/重做/远端回放均可能改变模板，跟随刷新工具栏下拉显示
      currentTemplate.value = (m.queryCommandValue?.('template') as string) || currentTemplate.value
      // 内容变化同步到 Yjs 并防抖落库；远端回放不回写以免循环与重复持久化
      if (!applyingRemote) {
        // 先把新节点的短 id 归一化为 UUID 再写入 Yjs，否则各端会各自生成不同 id 导致数据分裂
        collectLiveNodes()
        syncToYjs()
        schedulePersist()
      }
    })

    // 基线快照对齐服务端存量数据，避免首次 diff 把已有节点误判为新增
    persistedSnapshot = collectLiveNodes()
    persistedLayoutJson = JSON.stringify(collectLayout())

    // 建立 WebSocket 协作
    await nextTick()
    if (isStale(token)) return
    setupYjs(props.docId)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
  } finally {
    loading.value = false
  }
}

// ==================== 工具栏操作 ====================
function exec(command: string, ...args: unknown[]) {
  getMinder()?.execCommand?.(command, ...args)
  // 命令执行后焦点回到键盘接收器，保证快捷键持续可用
  kmEditor?.minder.fire('receiverfocus')
}

// ==================== 节点原位编辑 ====================
// 原位编辑由编辑内核（minder/input.ts）经 contenteditable 接收器实现，
// 双击节点由内核监听，这里只是工具栏 / 右键菜单的编辑入口
function editSelectedText() {
  kmEditor?.editText()
}

function markAs(type: string) {
  const data = getSelectedNodeData()
  if (!data) return
  data.type = type
  // 标记联动规则：标记为用例时无优先级默认P2
  if (type === 'case' && !data.priority) data.priority = 'P2'
  // 标记联动规则：优先级只对用例有意义，改标非用例类型时连带清除，避免残留 P 徽标
  if (type !== 'case') delete data.priority
  getMinder()?.refresh?.()
  updateSelectedState()
}

function markPriority(p: string) {
  const data = getSelectedNodeData()
  if (!data) return
  data.priority = p
  // 标记联动规则：设置优先级时自动标记为用例
  if (data.type !== 'case') data.type = 'case'
  getMinder()?.refresh?.()
  updateSelectedState()
}

// 取消标记：恢复普通节点并连带清掉优先级，否则残留的 P 徽标会造成"已取消却仍有等级"的歧义
function clearMark() {
  const data = getSelectedNodeData()
  if (!data) return
  data.type = 'normal'
  delete data.priority
  getMinder()?.refresh?.()
  updateSelectedState()
}

// 移除 AI 标识（仅 aiGenerated=true 时可见；前端任何入口不提供置 true，见详细设计 4.6）
function removeAiFlag() {
  const data = getSelectedNodeData()
  if (!data || data.aiGenerated !== true) return
  data.aiGenerated = false
  getMinder()?.refresh?.()
  updateSelectedState()
}

// 新建节点统一携默认名称（与 Tab/Enter 快捷键行为一致，见 minder/jumping.ts）
function addChild() { exec('AppendChildNode', DEFAULT_NODE_TEXT) }
function addSibling() { exec('AppendSiblingNode', DEFAULT_NODE_TEXT) }
function deleteNode() { exec('RemoveNode') }
// 复制/剪切/粘贴走应用内剪贴板（minder/clipboard.ts），粘贴节点由落库管道补发新 id
function copyNode() {
  if (kmEditor) copySelected(kmEditor.minder)
}
function cutNode() {
  if (!kmEditor) return
  if (cutSelected(kmEditor.minder) === 'root') ElMessage.warning('根节点不能剪切')
}
function pasteNode() {
  if (kmEditor) pasteToSelected(kmEditor.minder)
}
// 撤销/重做由编辑内核的 history 提供（core 无 Undo/Redo 命令）
function undo() { kmEditor?.history.undo() }
function redo() { kmEditor?.history.redo() }
// 缩放/定位/抓手/缩略图/全屏由导航器（minder/MinderNavigator.vue）提供

// ==================== 布局模板 ====================
// core 原生 6 模板；template 命令触发 contentchange，自动搭上 Yjs 同步与落库管道
const templates = [
  { name: 'default', label: '思维导图' },
  { name: 'right', label: '右侧分布' },
  { name: 'structure', label: '组织结构' },
  { name: 'filetree', label: '目录' },
  { name: 'fish-bone', label: '鱼骨图' },
  { name: 'tianpan', label: '天盘' },
]
const currentTemplate = ref('default')
const currentTemplateLabel = computed(
  () => templates.find((t) => t.name === currentTemplate.value)?.label ?? currentTemplate.value,
)

function switchTemplate(name: string) {
  // currentTemplate 由 contentchange 统一回读，避免命令未生效时 UI 与实际不一致
  exec('template', name)
}

// 清除全部自由拖拽偏移恢复自动排版；core resetlayout 只作用于选中子树，先清空选区保证整理全树
function tidyLayout() {
  getMinder()?.removeAllSelectedNodes?.()
  exec('resetlayout')
}

// ==================== 右键菜单 ====================
const {
  visible: menuVisible,
  pos: menuPos,
  onContextMenu,
  openAtSelection: openContextMenuAtSelection,
  close: closeContextMenu,
} = useContextMenu({
  hasSelection: () => !!selectedNodeId.value,
  getSelectedNode: () =>
    getMinder()?.getSelectedNode?.() as ContextMenuAnchorNode | null | undefined,
})

// ==================== 生命周期 ====================
watch(() => props.docId, initMinder)
onMounted(initMinder)
onBeforeUnmount(() => {
  invalidate()
  flushPersistenceNow()
  destroyYjs()
  teardownMinder()
})
</script>

<template>
  <div v-loading="loading" class="mindmap-container">
    <!-- 断线提示横幅 -->
    <div v-if="!isConnected" class="mindmap-disconnect-banner">
      连接已断开，正在重连...
    </div>

    <!-- 编辑工具栏：按操作频率分域——历史 | 节点结构 | 标记（类型+优先级） | 视图布局 -->
    <div class="mindmap-toolbar">
      <div class="toolbar-group">
        <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
          <el-button size="small" text :disabled="!canUndo" @click="undo"><el-icon><RefreshLeft /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
          <el-button size="small" text :disabled="!canRedo" @click="redo"><el-icon><RefreshRight /></el-icon></el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-tooltip content="添加子节点 (Tab)" placement="bottom">
          <el-button size="small" text @click="addChild"><el-icon><Plus /></el-icon><span>下级</span></el-button>
        </el-tooltip>
        <el-tooltip content="添加兄弟节点 (Enter)" placement="bottom">
          <el-button size="small" text @click="addSibling"><el-icon><Plus /></el-icon><span>同级</span></el-button>
        </el-tooltip>
        <el-tooltip content="编辑内容 (双击节点/F2)" placement="bottom">
          <el-button size="small" text @click="editSelectedText"><el-icon><EditPen /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="删除 (Delete)" placement="bottom">
          <el-button size="small" text class="toolbar-btn--danger" @click="deleteNode"><el-icon><Delete /></el-icon></el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-dropdown size="small" @command="switchTemplate">
          <el-button size="small" text>
            <el-icon><Grid /></el-icon><span>{{ currentTemplateLabel }}</span><el-icon class="toolbar-caret"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="t in templates"
                :key="t.name"
                :command="t.name"
                :disabled="t.name === currentTemplate"
              >
                {{ t.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-tooltip content="清除手动拖拽的节点偏移，恢复自动排版" placement="bottom">
          <el-button size="small" text @click="tidyLayout"><el-icon><MagicStick /></el-icon></el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'case' }]" @click="markAs('case')"><span class="type-dot type-dot--case" /><span>用例</span></el-button>
        <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'precondition' }]" @click="markAs('precondition')"><span class="type-dot type-dot--precondition" /><span>前置</span></el-button>
        <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'step' }]" @click="markAs('step')"><span class="type-dot type-dot--step" /><span>步骤</span></el-button>
        <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'expected' }]" @click="markAs('expected')"><span class="type-dot type-dot--expected" /><span>预期</span></el-button>
        <el-tooltip content="取消标记，恢复普通节点" placement="bottom">
          <el-button size="small" text @click="clearMark"><el-icon><CircleClose /></el-icon></el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-button
          v-for="p in priorities"
          :key="p"
          size="small"
          text
          :class="['priority-btn', `priority-btn--${p.toLowerCase()}`, { 'is-selected': selectedPriority === p }]"
          @click="markPriority(p)"
        >{{ p }}</el-button>
      </div>
      <!-- AI 入口：工作空间 AI 未启用时整组隐藏（交互设计 1.1/5.2） -->
      <template v-if="aiStore.aiEnabled">
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-button size="small" text class="ai-entry-btn" @click="openAiPanel">
            <el-icon><MagicStick /></el-icon><span>AI 生成用例</span>
          </el-button>
        </div>
      </template>
    </div>

    <!-- 脑图画布（编辑内核会向容器注入 .km-receiver 接收器元素，双击节点进入编辑） -->
    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent="onContextMenu"
    />

    <!-- 在线用户头像：悬浮画布右上角，状态展示不进操作条 -->
    <div v-if="onlineUsers.length" class="online-users">
      <el-avatar v-for="user in onlineUsers" :key="user.id" :size="24" :style="{ border: `2px solid ${user.color}` }">
        {{ user.name.charAt(0) }}
      </el-avatar>
    </div>

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏；minder 切换文档重建时随 v-if 重建 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 右键菜单 -->
    <MinderContextMenu
      v-if="menuVisible"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__item menu-action" @click="addChild"><span>新建下级节点</span><span class="menu-shortcut">Tab</span></div>
      <div class="mindmap-context-menu__item menu-action" @click="addSibling"><span>新建同级节点</span><span class="menu-shortcut">Enter</span></div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item menu-action" @click="copyNode"><span>复制</span><span class="menu-shortcut">Ctrl+C</span></div>
      <div class="mindmap-context-menu__item menu-action" @click="cutNode"><span>剪切</span><span class="menu-shortcut">Ctrl+X</span></div>
      <div :class="['mindmap-context-menu__item', 'menu-action', { 'is-disabled': !hasClipboard }]" @click="pasteNode"><span>粘贴</span><span class="menu-shortcut">Ctrl+V</span></div>
      <div class="mindmap-context-menu__divider" />
      <!-- 标记域横排芯片：压缩菜单高度，选中态直接展示节点现状 -->
      <div class="menu-chip-row">
        <span class="menu-chip-label">类型</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'case' }]" @click="markAs('case')"><span class="type-dot type-dot--case" />用例</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'precondition' }]" @click="markAs('precondition')"><span class="type-dot type-dot--precondition" />前置</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'step' }]" @click="markAs('step')"><span class="type-dot type-dot--step" />步骤</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'expected' }]" @click="markAs('expected')"><span class="type-dot type-dot--expected" />预期</span>
        <span class="menu-chip" title="取消标记" @click="clearMark"><el-icon><CircleClose /></el-icon></span>
      </div>
      <div class="menu-chip-row">
        <span class="menu-chip-label">等级</span>
        <span
          v-for="p in priorities"
          :key="p"
          :class="['menu-chip', `menu-chip--${p.toLowerCase()}`, { 'is-selected': selectedPriority === p }]"
          @click="markPriority(p)"
        >{{ p }}</span>
      </div>
      <!-- 移除后不可重新添加（撤销回退除外），不提供置 true 入口 -->
      <div v-if="selectedAiGenerated" class="menu-chip-row">
        <span class="menu-chip-label">AI</span>
        <span class="menu-chip" @click="removeAiFlag">移除 AI 标识</span>
      </div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item mindmap-context-menu__item--danger menu-action" @click="deleteNode"><span>删除节点</span><span class="menu-shortcut">Delete</span></div>
    </MinderContextMenu>

    <!-- AI 生成用例抽屉：非模态，预览-确认阶段可继续编辑脑图（交互设计 2.2） -->
    <AiGeneratePanel
      v-if="aiPanelVisible"
      v-model="aiPanelVisible"
      :doc-id="props.docId"
      :target-node-id="aiTargetNodeId"
      :target-path="aiTargetPath"
      @mount="handleAiMount"
    />

    <!-- 挂载目标被协同删除后的重选节点选择器（预览结果不丢弃） -->
    <el-dialog v-model="aiReselectVisible" title="重新选择挂载位置" width="360px" append-to-body>
      <el-tree
        :data="aiReselectTree"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleAiReselect"
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use './minder/minder-base';

.mindmap-disconnect-banner {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  text-align: center;
  padding: 4px;
  font-size: 12px;
  flex-shrink: 0;
}

/* 横幅出现时悬浮元素整体下移，避免叠在横幅上 */
.mindmap-disconnect-banner + .mindmap-toolbar,
.mindmap-disconnect-banner ~ .online-users {
  top: 40px;
}

.online-users {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  z-index: 10;
  display: flex;
  gap: 4px;
}

/* 类型标记按钮：左侧色点与节点渲染色一一对应，选中态主色浅底 */
.type-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  flex-shrink: 0;
}

.type-dot--case { background: var(--color-node-case); }
.type-dot--precondition { background: var(--color-node-precondition); }
.type-dot--step { background: var(--color-node-step); }
.type-dot--expected { background: var(--color-node-expected); }

.type-btn.is-selected {
  background-color: var(--color-primary-50);
  /* text 按钮 hover 底色取自该变量，选中态下悬停加深一档 */
  --el-fill-color-light: var(--color-primary-100);
  --el-button-text-color: var(--color-primary-600);
  --el-button-hover-text-color: var(--color-primary-600);
}

/* 优先级按钮：未选中态文字即着优先级令牌色，选中态翻转为同色实底白字，
   与节点徽标颜色保持一致 */
.priority-btn--p0 {
  --el-button-text-color: var(--color-priority-p0);
  --el-button-hover-text-color: var(--color-priority-p0);
}

.priority-btn--p1 {
  --el-button-text-color: var(--color-priority-p1);
  --el-button-hover-text-color: var(--color-priority-p1);
}

.priority-btn--p2 {
  --el-button-text-color: var(--color-priority-p2);
  --el-button-hover-text-color: var(--color-priority-p2);
}

.priority-btn--p3 {
  --el-button-text-color: var(--color-priority-p3);
  --el-button-hover-text-color: var(--color-priority-p3);
}

.priority-btn.is-selected {
  --el-button-text-color: #fff;
  --el-button-hover-text-color: #fff;
}

.priority-btn--p0.is-selected {
  background-color: var(--color-priority-p0);
  --el-fill-color-light: var(--color-priority-p0);
}

.priority-btn--p1.is-selected {
  background-color: var(--color-priority-p1);
  --el-fill-color-light: var(--color-priority-p1);
}

.priority-btn--p2.is-selected {
  background-color: var(--color-priority-p2);
  --el-fill-color-light: var(--color-priority-p2);
}

.priority-btn--p3.is-selected {
  background-color: var(--color-priority-p3);
  --el-fill-color-light: var(--color-priority-p3);
}

.toolbar-btn--danger {
  --el-button-hover-text-color: var(--color-danger);
}

// AI 入口按钮强调色（与 AI 徽标同色系，区别于普通工具按钮）
.ai-entry-btn {
  --el-button-text-color: #13c2c2;
  --el-button-hover-text-color: #0da8a8;
}

.toolbar-caret {
  margin-left: 2px;
  font-size: 10px;
}

/* 右键菜单：动作项快捷键提示右对齐 */
.menu-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

/* 粘贴项在剪贴板为空时置灰不可点（pointer-events 保留冒泡，点击仍会关闭菜单） */
.menu-action.is-disabled {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;

  &:hover {
    background: none;
    color: var(--el-text-color-disabled);
  }
}

.menu-shortcut {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 右键菜单：标记域横排芯片（slot 内容属本组件作用域，样式无需 :slotted） */
.menu-chip-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
}

.menu-chip-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-right: 4px;
  flex-shrink: 0;
}

.menu-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--color-neutral-700);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.menu-chip:hover {
  background: var(--el-fill-color-light);
}

.menu-chip.is-selected {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.menu-chip--p0 { color: var(--color-priority-p0); }
.menu-chip--p1 { color: var(--color-priority-p1); }
.menu-chip--p2 { color: var(--color-priority-p2); }
.menu-chip--p3 { color: var(--color-priority-p3); }

.menu-chip--p0.is-selected { background: var(--color-priority-p0); color: #fff; }
.menu-chip--p1.is-selected { background: var(--color-priority-p1); color: #fff; }
.menu-chip--p2.is-selected { background: var(--color-priority-p2); color: #fff; }
.menu-chip--p3.is-selected { background: var(--color-priority-p3); color: #fff; }

/* 键盘接收器（编辑内核注入）：平时隐藏只接收键盘；
   进入 input 态时节点自身文本被隐藏，接收器透明叠合在文本位置上，
   字号/字色/行高由 input runtime 内联设置，形成“直接在节点内编辑”的观感 */
.minder-canvas :deep(.km-receiver) {
  position: absolute;
  z-index: 20;
  opacity: 0;
  pointer-events: none;
  padding: 0;
  /* 宽度由内容决定：绝对定位默认 shrink-to-fit，节点靠画布右侧时剩余空间不足会被挤成一字一行 */
  width: max-content;
  min-width: 1em;
  max-width: 300px;
  border: none;
  outline: none;
  background: transparent;
  white-space: pre-wrap;
  word-break: break-all;
}

.minder-canvas :deep(.km-receiver.input) {
  opacity: 1;
  pointer-events: auto;
  caret-color: var(--el-color-primary);
}
</style>
