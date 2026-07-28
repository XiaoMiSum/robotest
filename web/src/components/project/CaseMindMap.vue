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
import type { DocumentLayout } from '@/types'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import { KMEditor } from './minder/editor'
import { DEFAULT_NODE_TEXT } from './minder/jumping'
import { caseNodeToKm, uuidv7, UUID_RE } from './minder/adapter'
import { loadMinderEngine } from './minder/loader'
import { useMinderInstance } from './minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from './minder/useContextMenu'
import MinderContextMenu from './minder/MinderContextMenu.vue'
import MinderNavigator from './minder/MinderNavigator.vue'

const props = defineProps<{ docId: string }>()

// 编辑内核：contenteditable 接收器统一接管键盘，提供原位编辑与打字即编辑
let kmEditor: KMEditor | null = null

// 基座选中状态（id/type）之上的扩展字段
const selectedPriority = ref('')
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
  },
})

// Yjs 实时协作
let ydoc: Y.Doc | null = null
let wsProvider: WebsocketProvider | null = null
const onlineUsers = ref<{ id: string; name: string; color: string }[]>([])
const isConnected = ref(true)

const priorities = ['P0', 'P1', 'P2', 'P3']

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
          <el-button size="small" text @click="addChild"><el-icon><Plus /></el-icon><span>子级</span></el-button>
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
      <div class="mindmap-context-menu__item" @click="editSelectedText">编辑内容</div>
      <div class="mindmap-context-menu__item" @click="addChild">新建子节点</div>
      <div class="mindmap-context-menu__item" @click="addSibling">新建兄弟节点</div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__subtitle">标记类型 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="clearMark">普通</div>
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

.toolbar-caret {
  margin-left: 2px;
  font-size: 10px;
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
</style>
