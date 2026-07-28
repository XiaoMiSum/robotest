<script setup lang="ts">
/**
 * MindMapEditor 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需管理 WebSocket 连接生命周期、
 * 响应用户标记操作并即时提交，数据流与交互深度耦合，
 * 抽到 page 层会导致大量 props/emit 透传且破坏协作状态一致性。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { onMounted, onBeforeUnmount, ref, watch, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchDocumentNodes,
  getReviewSnapshotTree,
  getPlanSnapshotTree,
  submitReviewRecord,
  submitExecutionRecord,
  getNodeReviewRecords,
} from '@/services/project'
import { getAccessToken } from '@/services'
import type { DocumentLayout, ExecutionResult, ReviewMark, ReviewRecord } from '@/types'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import { KMEditor } from './minder/editor'
import { DEFAULT_NODE_TEXT } from './minder/jumping'
import { caseNodeToKm, reviewNodeToKm, planNodeToKm, uuidv7, UUID_RE } from './minder/adapter'
import { loadMinderEngine } from './minder/loader'
import { useMinderInstance } from './minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from './minder/useContextMenu'
import MinderContextMenu from './minder/MinderContextMenu.vue'
import MinderNavigator from './minder/MinderNavigator.vue'

const props = defineProps<{
  docId?: string
  reviewId?: string
  planId?: string
  mode: 'edit' | 'review' | 'plan'
  readonly?: boolean
}>()

const emit = defineEmits<{ nodeSelect: [nodeId: string, nodeType: string] }>()

// 编辑内核（仅 edit 模式创建）：contenteditable 接收器统一接管键盘，提供原位编辑与打字即编辑
let kmEditor: KMEditor | null = null

// 各模式在基座选中状态（id/type）之上的扩展字段
const selectedPriority = ref('')
const reviewResult = ref<string | null>(null)
const execResult = ref<string | null>(null)
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
    reviewResult.value = data ? (data.lastMark as string) || null : null
    execResult.value = data ? (data.lastResult as string) || null : null
    if (data) emit('nodeSelect', selectedNodeId.value, selectedType.value)
  },
})

// 评论抽屉
const commentVisible = ref(false)
const comments = ref<ReviewRecord[]>([])
const newComment = ref('')

// Yjs 实时协作（仅 edit 模式）
let ydoc: Y.Doc | null = null
let wsProvider: WebsocketProvider | null = null
const onlineUsers = ref<{ id: string; name: string; color: string }[]>([])
const isConnected = ref(true)

const priorities = ['P0', 'P1', 'P2', 'P3']

const isEdit = computed(() => props.mode === 'edit' && !props.readonly)
const isReview = computed(() => props.mode === 'review' && !props.readonly)
const isPlan = computed(() => props.mode === 'plan' && !props.readonly)

// ==================== 文档持久化（JSON 操作通路） ====================
// Yjs 二进制帧仅做实时协同转发、不落库；节点增删改需经同一连接的文本帧
// 提交给后端 DocumentPersistenceHandler 持久化，否则刷新后编辑内容丢失
interface PersistSnap {
  title: string
  type: string
  priority: string | null
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
  if (!isEdit.value || !getMinder()) return
  const socket = getProviderSocket()
  if (!socket) return
  const current = collectLiveNodes()

  for (const [id, snap] of current) {
    const prev = persistedSnapshot.get(id)
    if (!prev) {
      sendPersistOp(socket, 'add_node', { id, ...snap })
      continue
    }
    if (prev.title !== snap.title || prev.type !== snap.type || prev.priority !== snap.priority) {
      sendPersistOp(socket, 'update_attrs', { id, title: snap.title, type: snap.type, priority: snap.priority })
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
  const template = (m?.queryCommandValue?.('template') as string) || 'right'
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

// ==================== Yjs 实时协作（编辑模式） ====================
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
// edit 模式的实例经编辑内核创建，销毁也须走内核（接收器/历史等 runtime 一并清理）
function teardownMinder() {
  destroyMinder(kmEditor ? () => kmEditor?.destroy() : undefined)
  kmEditor = null
}

async function initMinder() {
  if (!containerRef.value) return
  const token = beginInit()
  loading.value = true
  // 切换文档前冲刷防抖中的增量编辑，避免旧文档最后一次修改丢失
  flushPersistenceNow()
  destroyYjs()
  teardownMinder()
  try {
    let kmData: Record<string, unknown>

    if (props.mode === 'edit' && props.docId) {
      const docData = await fetchDocumentNodes(props.docId)
      const root = caseNodeToKm(docData.node)
      // 应用已保存的布局：模板 + 自由拖拽偏移；无记录时回退默认右侧分布
      applyLayoutOffsets(root, docData.layout?.offsets)
      const template = docData.layout?.template || 'right'
      currentTemplate.value = template
      kmData = { root, template, theme: 'fresh-blue' }
    } else if (props.mode === 'review' && props.reviewId) {
      const tree = await getReviewSnapshotTree(props.reviewId)
      const root = tree.length ? reviewNodeToKm(tree[0]) : { data: { text: '空快照' }, children: [] }
      kmData = { root, template: 'right', theme: 'fresh-green' }
    } else if (props.mode === 'plan' && props.planId) {
      const tree = await getPlanSnapshotTree(props.planId)
      const root = tree.length ? planNodeToKm(tree[0]) : { data: { text: '空快照' }, children: [] }
      kmData = { root, template: 'right', theme: 'fresh-purple' }
    } else {
      return
    }

    const km = await loadMinderEngine()
    // 异步等待期间组件可能已卸载或已切换文档，过期结果直接丢弃
    if (isStale(token) || !containerRef.value) return

    // edit 模式经编辑内核创建（键盘接收器/原位编辑/撤销重做）；review/plan 只读，裸 minder 即可
    let instance: unknown
    if (props.mode === 'edit') {
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
      instance = kmEditor.minder
    } else {
      instance = new km.Minder({ renderTo: containerRef.value })
    }
    minder.value = instance
    const m = instance as Record<string, (...args: unknown[]) => unknown>
    m.importJson(kmData)

    // review/plan 模式禁用画布编辑以防止用户修改快照原始数据
    if (props.mode !== 'edit') {
      m.disable?.()
    }

    // 监听事件
    m.on('selectionchange', updateSelectedState)
    m.on('contentchange', () => {
      // 撤销/重做/远端回放均可能改变模板，跟随刷新工具栏下拉显示
      currentTemplate.value = (m.queryCommandValue?.('template') as string) || currentTemplate.value
      // 编辑模式：内容变化同步到 Yjs 并防抖落库；远端回放不回写以免循环与重复持久化
      if (props.mode === 'edit' && !applyingRemote) {
        // 先把新节点的短 id 归一化为 UUID 再写入 Yjs，否则各端会各自生成不同 id 导致数据分裂
        collectLiveNodes()
        syncToYjs()
        schedulePersist()
      }
    })

    // 基线快照对齐服务端存量数据，避免首次 diff 把已有节点误判为新增
    persistedSnapshot = props.mode === 'edit' ? collectLiveNodes() : new Map()
    persistedLayoutJson = props.mode === 'edit' ? JSON.stringify(collectLayout()) : ''

    // 编辑模式建立 WebSocket 协作
    if (props.mode === 'edit' && props.docId) {
      await nextTick()
      if (isStale(token)) return
      setupYjs(props.docId)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
  } finally {
    loading.value = false
  }
}

// ==================== Edit 模式工具栏操作 ====================
function exec(command: string, ...args: unknown[]) {
  getMinder()?.execCommand?.(command, ...args)
  // 命令执行后焦点回到键盘接收器，保证快捷键持续可用
  kmEditor?.minder.fire('receiverfocus')
}

// ==================== 节点原位编辑 ====================
// 原位编辑由编辑内核（minder/input.ts）经 contenteditable 接收器实现，
// 双击节点由内核监听，这里只是工具栏 / 右键菜单的编辑入口
function editSelectedText() {
  if (!isEdit.value) return
  kmEditor?.editText()
}

function markAs(type: string) {
  const data = getSelectedNodeData()
  if (!data) return
  data.type = type
  // 标记联动规则：标记为用例时无优先级默认P2
  if (type === 'case' && !data.priority) data.priority = 'P2'
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

// 新建节点统一携默认名称（与 Tab/Enter 快捷键行为一致，见 minder/jumping.ts）
function addChild() { exec('AppendChildNode', DEFAULT_NODE_TEXT) }
function addSibling() { exec('AppendSiblingNode', DEFAULT_NODE_TEXT) }
function deleteNode() { exec('RemoveNode') }
// 撤销/重做由编辑内核的 history 提供（core 无 Undo/Redo 命令）
function undo() { kmEditor?.history.undo() }
function redo() { kmEditor?.history.redo() }
// 缩放/定位/抓手/缩略图/全屏由导航器（minder/MinderNavigator.vue）提供

// ==================== 布局模板 ====================
// core 原生 6 模板；template 命令触发 contentchange，自动搭上 Yjs 同步与落库管道
const templates = [
  { name: 'right', label: '右侧分布' },
  { name: 'default', label: '思维导图' },
  { name: 'structure', label: '组织结构' },
  { name: 'filetree', label: '目录' },
  { name: 'fish-bone', label: '鱼骨图' },
  { name: 'tianpan', label: '天盘' },
]
const currentTemplate = ref('right')
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

// ==================== Review 模式操作 ====================
async function markReview(mark: ReviewMark | null) {
  if (!props.reviewId || !selectedNodeId.value) return
  // 仅 case 节点可标记
  if (selectedType.value !== 'case' && mark !== null) {
    ElMessage.warning('仅用例节点可标记评审结果')
    return
  }
  try {
    await submitReviewRecord(props.reviewId, {
      snapshotNodeId: selectedNodeId.value,
      operationType: 'mark',
      mark: mark ?? undefined,
    })
    reviewResult.value = mark
    const data = getSelectedNodeData()
    if (data) { data.lastMark = mark; data.reviewStatus = mark ? { result: mark } : null }
    getMinder()?.refresh?.()
    ElMessage.success(mark ? `已标记${mark === 'pass' ? '通过' : '不通过'}` : '已清除标记')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交标记失败')
  }
}

async function openComments() {
  if (!props.reviewId || !selectedNodeId.value) {
    ElMessage.warning('请先选中一个节点')
    return
  }
  commentVisible.value = true
  try {
    comments.value = await getNodeReviewRecords(props.reviewId, selectedNodeId.value)
  } catch {
    comments.value = []
  }
}

async function addCommentFn() {
  if (!newComment.value.trim() || !props.reviewId || !selectedNodeId.value) return
  try {
    await submitReviewRecord(props.reviewId, {
      snapshotNodeId: selectedNodeId.value,
      operationType: 'comment',
      comment: newComment.value.trim(),
    })
    ElMessage.success('评论已发送')
    newComment.value = ''
    comments.value = await getNodeReviewRecords(props.reviewId, selectedNodeId.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '发送评论失败')
  }
}

// ==================== Plan 模式操作 ====================
async function markExecution(result: ExecutionResult) {
  if (!props.planId || !selectedNodeId.value) return
  if (selectedType.value !== 'case') {
    ElMessage.warning('仅关联用例节点可标记执行结果')
    return
  }
  try {
    await submitExecutionRecord(props.planId, { snapshotNodeId: selectedNodeId.value, result })
    execResult.value = result
    const data = getSelectedNodeData()
    if (data) { data.lastResult = result; data.executionStatus = { result } }
    getMinder()?.refresh?.()
    const labels: Record<string, string> = { pass: '通过', fail: '失败', block: '阻塞', untested: '未执行' }
    ElMessage.success(`已标记${labels[result]}`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交执行结果失败')
  }
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

// ==================== Bug 链接跳转 ====================
// 暴露给 kityminder 扩展节点渲染模板通过 ref 调用，避免事件冒泡干扰节点选中
function openBug(bugId: string) {
  window.open(`/workspace/projects/bugs/${bugId}`, '_blank')
}

defineExpose({ openBug })

// ==================== 生命周期 ====================
watch([() => props.docId, () => props.reviewId, () => props.planId], initMinder)
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
    <div v-if="isEdit && !isConnected" class="mindmap-disconnect-banner">
      连接已断开，正在重连...
    </div>

    <!-- 编辑模式工具栏 -->
    <div v-if="isEdit" class="mindmap-toolbar">
      <el-button-group size="small" class="type-group">
        <el-button :type="selectedType==='case'?'primary':''" @click="markAs('case')">📋用例</el-button>
        <el-button :type="selectedType==='precondition'?'primary':''" @click="markAs('precondition')">📘前置</el-button>
        <el-button :type="selectedType==='step'?'primary':''" @click="markAs('step')">📗步骤</el-button>
        <el-button :type="selectedType==='expected'?'primary':''" @click="markAs('expected')">📙预期</el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button-group size="small" class="priority-group">
        <el-button v-for="p in priorities" :key="p" :type="selectedPriority===p?'warning':''" @click="markPriority(p)">{{ p }}</el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button-group size="small">
        <el-button title="编辑内容 (双击节点/F2)" @click="editSelectedText">✏️编辑</el-button>
        <el-button title="添加子节点 (Tab)" @click="addChild">＋子</el-button>
        <el-button title="添加兄弟节点 (Enter)" @click="addSibling">＋兄</el-button>
        <el-button title="删除 (Delete)" @click="deleteNode">🗑</el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button-group size="small">
        <el-button :disabled="!canUndo" @click="undo">↩</el-button>
        <el-button :disabled="!canRedo" @click="redo">↪</el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-dropdown size="small" @command="switchTemplate">
        <el-button size="small" title="切换布局模板">🗺{{ currentTemplateLabel }} ▾</el-button>
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
      <el-button size="small" title="清除手动拖拽的节点偏移，恢复自动排版" @click="tidyLayout">🧹整理</el-button>
      <!-- 在线用户头像 -->
      <div v-if="onlineUsers.length" class="online-users">
        <el-avatar v-for="user in onlineUsers" :key="user.id" :size="24" :style="{ border: `2px solid ${user.color}` }">
          {{ user.name.charAt(0) }}
        </el-avatar>
      </div>
    </div>

    <!-- 评审模式工具栏 -->
    <div v-else-if="isReview" class="mindmap-toolbar">
      <el-button-group size="small">
        <el-button :type="reviewResult==='pass'?'success':''" @click="markReview('pass')">✅通过</el-button>
        <el-button :type="reviewResult==='fail'?'danger':''" @click="markReview('fail')">❌不通过</el-button>
        <el-button :type="reviewResult===null?'info':''" @click="markReview(null)">❓待评审</el-button>
      </el-button-group>
      <el-button size="small" @click="openComments">💬评论</el-button>
    </div>

    <!-- 计划模式工具栏 -->
    <div v-else-if="isPlan" class="mindmap-toolbar">
      <el-button-group size="small">
        <el-button :type="execResult==='pass'?'success':''" @click="markExecution('pass')">✅通过</el-button>
        <el-button :type="execResult==='fail'?'danger':''" @click="markExecution('fail')">❌失败</el-button>
        <el-button :type="execResult==='block'?'warning':''" @click="markExecution('block')">❓阻塞</el-button>
        <el-button :type="execResult==='untested'?'info':''" @click="markExecution('untested')">🔄未执行</el-button>
      </el-button-group>
    </div>

    <!-- 脑图画布（编辑模式下内核会向容器注入 .km-receiver 接收器元素，双击节点进入编辑） -->
    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent="onContextMenu"
    />

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏；minder 切换文档重建时随 v-if 重建 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 右键菜单（编辑模式） -->
    <MinderContextMenu
      v-if="menuVisible && isEdit"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__item" @click="editSelectedText">编辑内容</div>
      <div class="mindmap-context-menu__item" @click="addChild">新建子节点</div>
      <div class="mindmap-context-menu__item" @click="addSibling">新建兄弟节点</div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__subtitle">标记类型 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markAs('normal')">普通</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markAs('precondition')">前置条件</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markAs('step')">执行步骤</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markAs('expected')">预期结果</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markAs('case')">测试用例</div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__subtitle">标记优先级 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markPriority('P0')">P0</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markPriority('P1')">P1</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markPriority('P2')">P2</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markPriority('P3')">P3</div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item mindmap-context-menu__item--danger" @click="deleteNode">删除节点</div>
    </MinderContextMenu>

    <!-- 右键菜单（评审模式） -->
    <MinderContextMenu
      v-if="menuVisible && isReview"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__subtitle">标记评审结果 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview('pass')">通过</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview('fail')">不通过</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview(null)">待评审</div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item" @click="openComments">添加评论</div>
    </MinderContextMenu>

    <!-- 右键菜单（计划模式） -->
    <MinderContextMenu
      v-if="menuVisible && isPlan"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__subtitle">标记执行结果 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('pass')">通过</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('fail')">失败</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('block')">阻塞</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('untested')">未执行</div>
    </MinderContextMenu>

    <!-- 评论抽屉（评审模式） -->
    <el-drawer v-model="commentVisible" title="评论" :size="360">
      <div class="comment-list">
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-item__header">
            <strong>{{ c.reviewerName }}</strong>
            <el-tag v-if="c.operationType === 'mark'" size="small" :type="c.mark === 'pass' ? 'success' : 'danger'">
              {{ c.mark === 'pass' ? '通过' : '不通过' }}
            </el-tag>
            <small>{{ c.createdAt }}</small>
          </div>
          <p v-if="c.comment" class="comment-item__body">{{ c.comment }}</p>
        </div>
        <el-empty v-if="!comments.length" description="暂无评论或标记记录" :image-size="40" />
      </div>
      <div class="comment-input">
        <el-input v-model="newComment" placeholder="输入评论..." @keyup.enter="addCommentFn" />
        <el-button type="primary" size="small" :disabled="!newComment.trim()" @click="addCommentFn">发送</el-button>
      </div>
    </el-drawer>
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

.online-users {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

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

/* 评论 */
.comment-list {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 12px;
}

.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.comment-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.comment-item__header small {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.comment-item__body {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.comment-input {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
