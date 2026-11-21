<script setup lang="ts">
import { usePlanMindmapOps } from '@/composables/project/functional-testing/plan/usePlanMindmapOps'
/**
 * PlanMindMap 经本地 composable 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需响应用户执行标记操作并即时提交，
 * 数据流与交互深度耦合，抽到 page 层会导致大量 props/emit 透传。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import type { ExecutionResult } from '@/types'
import { useMinderInstance } from '@/minder/useMinderInstance'
import { useNodeActionPopover, type NodeActionAnchorNode } from '@/minder/useNodeActionPopover'
import type { MinderNode } from '@/minder/types'
import NodeActionPopover from '../minder/NodeActionPopover.vue'
import MinderNavigator from '../minder/MinderNavigator.vue'

const props = defineProps<{
  planId: string
  documentId?: string
  removable?: boolean
  executionOrder: ReadonlyMap<string, number>
}>()

// 标记成功后通知详情页刷新进度并推进引导，否则页头进度条需手动刷新才能更新
const emit = defineEmits<{
  marked: [payload: { snapshotNodeId: string; result: ExecutionResult }]
  removed: []
}>()

// 原生全屏只渲染全屏元素子树，默认 body 下的 teleport 浮层会消失
const popoverHostRef = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const syncFullscreen = () => {
  isFullscreen.value = Boolean(document.fullscreenElement)
}
onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))
const popoverTeleportTarget = computed(() =>
  isFullscreen.value && popoverHostRef.value ? popoverHostRef.value : 'body',
)

// 基座选中状态（id/type）之上的扩展字段：当前节点的执行标记
const execResult = ref<string | null>(null)

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
    execResult.value = data ? (data.lastResult as string) || null : null
  },
})

const {
  markExecution,
  removeSelectedCase,
  initMinder,
} = usePlanMindmapOps({
  containerRef,
  loading,
  minder,
  selectedNodeId,
  selectedType,
  beginInit,
  isStale,
  getMinder,
  getSelectedNodeData,
  updateSelectedState,
  destroyMinder,
  execResult,
  executionOrder: computed(() => props.executionOrder),
  getPlanId: () => props.planId,
  getDocumentId: () => props.documentId,
  getRemovable: () => props.removable,
  onMarked: (payload) => emit('marked', payload),
  onRemoved: () => emit('removed'),
  onNodeClick: handleNodeClick,
})

// 移除按钮可用态：计划未结束（详情页传入）且当前选中为已关联 case 节点
const canRemove = computed(() => props.removable === true && selectedType.value === 'case')

// 左键单击用例节点展开悬浮窗：按节点渲染框定位，避免浮层遮挡被点击节点
const nodePopover = useNodeActionPopover()

function handleNodeClick(node: NodeActionAnchorNode): void {
  const nodeId = node.data.id
  if (node.data.type !== 'case' || typeof nodeId !== 'string' || !nodeId) {
    nodePopover.close()
    return
  }
  const box = node.getRenderBox?.(undefined, 'screen')
  if (!box) {
    nodePopover.close()
    return
  }
  nodePopover.open(nodeId, { x: Math.round(box.x), y: Math.round(box.y + box.height + 4) })
}

function markFromPopover(result: Parameters<typeof markExecution>[0]): void {
  nodePopover.close()
  void markExecution(result)
}

function removeFromPopover(): void {
  nodePopover.close()
  void removeSelectedCase()
}

// ==================== 执行引导 ====================
// 跨文档切换时目标节点尚未渲染，先记下 id 待重载完成后再定位
let pendingFocusId: string | null = null

function findNodeById(id: string): MinderNode | null {
  const root = getMinder()?.getRoot?.() as MinderNode | null | undefined
  if (!root) return null
  let found: MinderNode | null = null
  root.traverse((node) => {
    if (!found && node.data?.id === id) found = node
  })
  return found
}

function applyPendingFocus(): void {
  if (!pendingFocusId) return
  const m = getMinder()
  const node = findNodeById(pendingFocusId)
  if (!m || !node) return
  pendingFocusId = null
  m.select?.(node, true)
  m.execCommand?.('camera', node, 600)
  updateSelectedState()
}

function focusCase(snapshotNodeId: string): void {
  pendingFocusId = snapshotNodeId
  applyPendingFocus()
}

// ==================== Bug 链接跳转 ====================
// 暴露给 kityminder 扩展节点渲染模板通过 ref 调用，避免事件冒泡干扰节点选中
function openBug(bugId: string) {
  window.open(`/workspace/projects/bugs/${bugId}`, '_blank')
}

// reload 供详情页同步快照后刷新画布（planId 不变，watch 不会触发）；先收起浮层避免定位到旧节点
function reload(): void {
  nodePopover.close()
  void initMinder().then(applyPendingFocus)
}
defineExpose({ openBug, reload, focusCase })

// ==================== 生命周期 ====================
// 序号依赖额外快照读取，可能晚于脑图初始化到达，变化时原位刷新而不重载整棵树
watch(() => props.executionOrder, (order) => {
  const m = getMinder()
  const root = m?.getRoot?.() as MinderNode | null | undefined
  if (!m || !root) return
  root.traverse((node) => {
    const id = node.data?.id
    if (typeof id === 'string') node.setData('executionOrder', order.get(id) ?? null)
  })
  m.refresh?.()
})
watch(() => [props.planId, props.documentId], reload)
onMounted(initMinder)
onBeforeUnmount(() => {
  invalidate()
  destroyMinder()
})
</script>

<template>
  <div ref="popoverHostRef" v-loading="loading" class="mindmap-container">
    <!-- 脑图画布：右键仅抑制浏览器原生菜单，不渲染自定义右键菜单 -->
    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent
    />

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 用例节点悬浮窗：仅点击 case 节点展开，入口水平并排、透明背景、link 按钮 -->
    <NodeActionPopover
      :to="popoverTeleportTarget"
      :visible="nodePopover.visible.value"
      :x="nodePopover.anchor.value.x"
      :y="nodePopover.anchor.value.y"
    >
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--pass': execResult === 'pass' }" @click="markFromPopover('pass')">✅通过</el-button>
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--fail': execResult === 'fail' }" @click="markFromPopover('fail')">❌失败</el-button>
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--block': execResult === 'block' }" @click="markFromPopover('block')">❓阻塞</el-button>
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--untested': execResult === 'untested' }" @click="markFromPopover('untested')">🔄待执行</el-button>
      <el-button v-if="removable" link class="node-action-btn node-action-btn--danger" :disabled="!canRemove" @click="removeFromPopover">🗑移除用例</el-button>
    </NodeActionPopover>
  </div>
</template>

<style scoped lang="scss">
@use '../minder/minder-base';

// link 按钮以文字色区分状态；EP 无内建阻塞档，取 --color-blocked 深红避免与「失败」撞色
.node-action-btn {
  --el-button-text-color: var(--el-text-color-regular);

  &--pass { --el-button-text-color: var(--color-success); }
  &--fail { --el-button-text-color: var(--color-danger); }
  &--block { --el-button-text-color: var(--color-blocked); }
  &--untested { --el-button-text-color: var(--el-color-info); }
  &--danger { --el-button-text-color: var(--color-danger); }
}
</style>
