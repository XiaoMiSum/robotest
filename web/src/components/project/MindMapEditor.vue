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
import type { TestCaseNode, TestReviewSnapshotNode, TestPlanSnapshotNode, ExecutionResult, ReviewMark, ReviewRecord } from '@/types'
import 'kityminder-core/dist/kityminder.core.css'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'

declare global {
  interface Window {
    kityminder?: { Minder: new (options: Record<string, unknown>) => unknown }
  }
}

const props = defineProps<{
  docId?: string
  reviewId?: string
  planId?: string
  mode: 'edit' | 'review' | 'plan'
  readonly?: boolean
}>()

const emit = defineEmits<{ nodeSelect: [nodeId: string, nodeType: string] }>()

const containerRef = ref<HTMLDivElement>()
const loading = ref(false)
const minder = ref<unknown>(null)

// 选中节点状态
const selectedNodeId = ref('')
const selectedType = ref('')
const selectedPriority = ref('')
const reviewResult = ref<string | null>(null)
const execResult = ref<string | null>(null)
const isGrabMode = ref(false)
const canUndo = ref(false)
const canRedo = ref(false)

// 评论抽屉
const commentVisible = ref(false)
const comments = ref<ReviewRecord[]>([])
const newComment = ref('')

// 右键菜单
const contextMenuVisible = ref(false)
const contextMenuPos = ref({ x: 0, y: 0 })

// Yjs 实时协作（仅 edit 模式）
let ydoc: Y.Doc | null = null
let wsProvider: WebsocketProvider | null = null
const onlineUsers = ref<{ id: string; name: string; color: string }[]>([])
const isConnected = ref(true)

const priorities = ['P0', 'P1', 'P2', 'P3']

const isEdit = computed(() => props.mode === 'edit' && !props.readonly)
const isReview = computed(() => props.mode === 'review' && !props.readonly)
const isPlan = computed(() => props.mode === 'plan' && !props.readonly)

// ==================== 数据转换 ====================
function caseNodeToKm(node: TestCaseNode): Record<string, unknown> {
  return {
    data: { id: node.id, text: node.title, type: node.type, priority: node.priority },
    children: node.children.map(caseNodeToKm),
  }
}

function reviewNodeToKm(node: TestReviewSnapshotNode): Record<string, unknown> {
  return {
    data: {
      id: node.id, text: node.title, type: node.type, priority: node.priority,
      isAssociated: node.isAssociated, lastMark: node.lastMark,
      reviewStatus: node.lastMark ? { result: node.lastMark } : null,
      relatedBugIds: [],
    },
    children: node.children.map(reviewNodeToKm),
  }
}

function planNodeToKm(node: TestPlanSnapshotNode): Record<string, unknown> {
  return {
    data: {
      id: node.id, text: node.title, type: node.type, priority: node.priority,
      isAssociated: node.isAssociated, lastResult: node.lastResult,
      executionStatus: node.lastResult ? { result: node.lastResult } : null,
      relatedBugIds: [],
    },
    children: node.children.map(planNodeToKm),
  }
}

// ==================== Minder 操作封装 ====================
function getMinder(): Record<string, (...args: unknown[]) => unknown> | null {
  return minder.value as Record<string, (...args: unknown[]) => unknown> | null
}

function getSelectedNodeData(): Record<string, unknown> | null {
  const m = getMinder()
  if (!m) return null
  const getter = (m as unknown as Record<string, unknown>).getSelectedNode as (() => Record<string, unknown> | null) | undefined
  const node = getter?.()
  return node ? ((node.data ?? {}) as Record<string, unknown>) : null
}

function updateSelectedState() {
  const data = getSelectedNodeData()
  if (data) {
    selectedNodeId.value = (data.id as string) || ''
    selectedType.value = (data.type as string) || ''
    selectedPriority.value = (data.priority as string) || ''
    reviewResult.value = (data.lastMark as string) || null
    execResult.value = (data.lastResult as string) || null
    emit('nodeSelect', selectedNodeId.value, selectedType.value)
  } else {
    selectedNodeId.value = ''
    selectedType.value = ''
    selectedPriority.value = ''
    reviewResult.value = null
    execResult.value = null
  }
}

// ==================== Yjs 实时协作（编辑模式） ====================
function setupYjs(docId: string) {
  destroyYjs()
  ydoc = new Y.Doc()
  const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/documents/${docId}`
  wsProvider = new WebsocketProvider(wsUrl, docId, ydoc)

  wsProvider.on('status', (event: { status: string }) => {
    isConnected.value = event.status === 'connected'
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
  ymap.observe(() => {
    const m = getMinder()
    if (!m) return
    const remoteData = ymap.get('data')
    if (remoteData) {
      m.importJson(remoteData)
    }
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
async function initMinder() {
  if (!containerRef.value) return
  loading.value = true
  destroyYjs()
  try {
    let kmData: Record<string, unknown>

    if (props.mode === 'edit' && props.docId) {
      const docData = await fetchDocumentNodes(props.docId)
      kmData = { root: caseNodeToKm(docData.node), template: 'right', theme: 'fresh-blue' }
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

    await import('kityminder-core' as string)
    const MinderClass = window.kityminder?.Minder
    if (!MinderClass) { ElMessage.error('脑图引擎加载失败'); return }

    const instance = new MinderClass({ renderTo: containerRef.value })
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
      canUndo.value = !!(m.queryCommandState?.('Undo') === 0)
      canRedo.value = !!(m.queryCommandState?.('Redo') === 0)
      // 编辑模式：内容变化同步到 Yjs
      if (props.mode === 'edit') {
        syncToYjs()
      }
    })

    // 编辑模式建立 WebSocket 协作
    if (props.mode === 'edit' && props.docId) {
      await nextTick()
      setupYjs(props.docId)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
  } finally {
    loading.value = false
  }
}

// ==================== Edit 模式工具栏操作 ====================
function exec(command: string) { getMinder()?.execCommand?.(command) }

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

function addChild() { exec('AppendChildNode') }
function addSibling() { exec('AppendSiblingNode') }
function deleteNode() { exec('RemoveNode') }
function undo() { exec('Undo') }
function redo() { exec('Redo') }
function zoomIn() { exec('ZoomIn') }
function zoomOut() { exec('ZoomOut') }
function fitToScreen() { exec('Camera') }
function toggleGrab() { isGrabMode.value = !isGrabMode.value }

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
function onContextMenu(e: MouseEvent) {
  contextMenuPos.value = { x: e.clientX, y: e.clientY }
  contextMenuVisible.value = true
}

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
  destroyYjs()
  ;(getMinder() as Record<string, () => void> | null)?.destroy?.()
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
      <el-button-group size="small">
        <el-button @click="zoomIn">🔍+</el-button>
        <el-button @click="zoomOut">🔍-</el-button>
        <el-button @click="fitToScreen">⊞</el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button size="small" :type="isGrabMode?'success':''" @click="toggleGrab">✋</el-button>
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
      <el-divider direction="vertical" />
      <el-button-group size="small">
        <el-button @click="zoomIn">🔍+</el-button>
        <el-button @click="zoomOut">🔍-</el-button>
        <el-button @click="fitToScreen">⊞</el-button>
      </el-button-group>
      <el-button size="small" :type="isGrabMode?'success':''" @click="toggleGrab">✋</el-button>
    </div>

    <!-- 计划模式工具栏 -->
    <div v-else-if="isPlan" class="mindmap-toolbar">
      <el-button-group size="small">
        <el-button :type="execResult==='pass'?'success':''" @click="markExecution('pass')">✅通过</el-button>
        <el-button :type="execResult==='fail'?'danger':''" @click="markExecution('fail')">❌失败</el-button>
        <el-button :type="execResult==='block'?'warning':''" @click="markExecution('block')">❓阻塞</el-button>
        <el-button :type="execResult==='untested'?'info':''" @click="markExecution('untested')">🔄未执行</el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button-group size="small">
        <el-button @click="zoomIn">🔍+</el-button>
        <el-button @click="zoomOut">🔍-</el-button>
        <el-button @click="fitToScreen">⊞</el-button>
      </el-button-group>
      <el-button size="small" :type="isGrabMode?'success':''" @click="toggleGrab">✋</el-button>
    </div>

    <!-- 脑图画布 -->
    <div ref="containerRef" class="minder-canvas" @contextmenu.prevent="onContextMenu" />

    <!-- 右键菜单（编辑模式） -->
    <teleport to="body">
      <div
        v-if="contextMenuVisible && isEdit"
        class="mindmap-context-menu"
        :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }"
        @click="contextMenuVisible = false"
        @mouseleave="contextMenuVisible = false"
      >
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
      </div>

      <!-- 右键菜单（评审模式） -->
      <div
        v-if="contextMenuVisible && isReview"
        class="mindmap-context-menu"
        :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }"
        @click="contextMenuVisible = false"
        @mouseleave="contextMenuVisible = false"
      >
        <div class="mindmap-context-menu__subtitle">标记评审结果 ▸</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview('pass')">通过</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview('fail')">不通过</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview(null)">待评审</div>
        <div class="mindmap-context-menu__divider" />
        <div class="mindmap-context-menu__item" @click="openComments">添加评论</div>
      </div>

      <!-- 右键菜单（计划模式） -->
      <div
        v-if="contextMenuVisible && isPlan"
        class="mindmap-context-menu"
        :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }"
        @click="contextMenuVisible = false"
        @mouseleave="contextMenuVisible = false"
      >
        <div class="mindmap-context-menu__subtitle">标记执行结果 ▸</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('pass')">通过</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('fail')">失败</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('block')">阻塞</div>
        <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('untested')">未执行</div>
      </div>
    </teleport>

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
.mindmap-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

.mindmap-disconnect-banner {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  text-align: center;
  padding: 4px;
  font-size: 12px;
  flex-shrink: 0;
}

.mindmap-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: var(--el-bg-color-overlay);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  flex-wrap: wrap;
  z-index: 10;
}

.online-users {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.minder-canvas {
  flex: 1;
  min-height: 400px;
  overflow: hidden;
  position: relative;
}

.minder-canvas :deep(svg) {
  width: 100%;
  height: 100%;
}

/* 右键菜单 */
.mindmap-context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
  padding: 4px 0;
  min-width: 170px;
}

.mindmap-context-menu__item {
  padding: 7px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.mindmap-context-menu__item:hover {
  background: var(--el-fill-color-light);
}

.mindmap-context-menu__item--danger {
  color: var(--el-color-danger);
}

.mindmap-context-menu__item--indent {
  padding-left: 28px;
}

.mindmap-context-menu__subtitle {
  padding: 5px 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: default;
}

.mindmap-context-menu__divider {
  height: 1px;
  background: var(--el-border-color-lighter);
  margin: 4px 0;
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
