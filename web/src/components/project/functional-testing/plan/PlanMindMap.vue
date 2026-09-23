<script setup lang="ts">
import { usePlanMindmapOps } from '@/composables/project/functional-testing/plan/usePlanMindmapOps'
/**
 * PlanMindMap 经本地 composable 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需响应用户执行标记操作并即时提交，
 * 数据流与交互深度耦合，抽到 page 层会导致大量 props/emit 透传。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import type { AiPlanOrderRecommendItem } from '@/types'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import type { Minder, MinderNode } from '@/minder/types'
import { useMinderInstance } from '@/minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from '@/minder/useContextMenu'
import MinderContextMenu from '../minder/MinderContextMenu.vue'
import MinderNavigator from '../minder/MinderNavigator.vue'

const props = defineProps<{ planId: string; documentId?: string; removable?: boolean }>()

// 标记成功后通知详情页刷新进度，否则页头进度条需手动刷新才能更新
const emit = defineEmits<{ marked: []; orderSelect: [order: number]; removed: [] }>()

// 执行顺序推荐序号缓存：脑图初始化/文档切换后重新回填（badges 渲染模块按需注入 orderNo）
const orderBadges = ref<AiPlanOrderRecommendItem[]>([])

// 程序化定位（推荐列表行点击）会触发 selectionchange，需抑制反向 orderSelect 避免与列表互跳
let suppressOrderSelect = false

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
    // 用户点击携带推荐序号徽标的节点时通知详情页滚动推荐列表至对应行（双向联动）；
    // 推荐列表行点击触发的程序化定位走 suppressOrderSelect 短路，避免两面板互跳
    const orderNo = data?.orderNo
    if (!suppressOrderSelect && typeof orderNo === 'number' && Number.isInteger(orderNo) && orderNo > 0) {
      emit('orderSelect', orderNo)
    }
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
  getPlanId: () => props.planId,
  getDocumentId: () => props.documentId,
  getRemovable: () => props.removable,
  applyOrderBadges,
  onMarked: () => emit('marked'),
  onRemoved: () => emit('removed'),
})

// 移除按钮可用态：计划未结束（详情页传入）且当前选中为已关联 case 节点
const canRemove = computed(() => props.removable === true && selectedType.value === 'case')

// ==================== 右键菜单 ====================
const {
  visible: menuVisible,
  pos: menuPos,
  onContextMenu,
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

// ==================== 执行顺序推荐联动（US-AI-017） ====================

/** 按推荐结果给节点 data 回填 orderNo（badges 渲染模块据此画 #序号 徽标），未命中置空避免残留旧序号 */
function applyOrderBadges(): void {
  const raw = getMinder()
  if (!raw) return
  const m = raw as unknown as Minder
  const orderByNodeId = new Map(orderBadges.value.map((item) => [item.snapshotNodeId, item.order]))
  m.getRoot().traverse((node) => {
    const order = orderByNodeId.get(node.data.id as string)
    if (order !== undefined) node.setData('orderNo', order)
    else node.setData('orderNo', null)
  })
  m.refresh?.()
}

/** 详情页推荐标签页产出新结果时注入，立即回填当前文档并留存供后续初始化复用 */
function setOrderBadges(items: AiPlanOrderRecommendItem[]): void {
  orderBadges.value = items
  applyOrderBadges()
}

// 供推荐列表行点击定位：当前文档含该快照节点则选中高亮，否则返回 false 由调用方提示
function locateNode(snapshotNodeId: string): boolean {
  const raw = getMinder()
  if (!raw) return false
  const m = raw as unknown as Minder
  let found: MinderNode | null = null
  m.getRoot().traverse((node) => {
    if (!found && node.data.id === snapshotNodeId) found = node
  })
  if (!found) return false
  suppressOrderSelect = true
  m.select(found, true)
  suppressOrderSelect = false
  return true
}

// reload 供详情页同步快照后刷新画布（planId 不变，watch 不会触发）
defineExpose({ openBug, reload: initMinder, setOrderBadges, locateNode })

// ==================== 生命周期 ====================
watch(() => [props.planId, props.documentId], initMinder)
onMounted(initMinder)
onBeforeUnmount(() => {
  invalidate()
  destroyMinder()
})
</script>

<template>
  <div v-loading="loading" class="mindmap-container">
    <!-- 计划工具栏 -->
    <div class="mindmap-toolbar">
      <el-button-group size="small">
        <el-button :type="execResult==='pass'?'success':''" @click="markExecution('pass')">✅通过</el-button>
        <el-button :type="execResult==='fail'?'danger':''" @click="markExecution('fail')">❌失败</el-button>
        <el-button :class="execResult==='block'?'exec-block-active':''" @click="markExecution('block')">❓阻塞</el-button>
        <el-button :type="execResult==='untested'?'info':''" @click="markExecution('untested')">🔄待执行</el-button>
      </el-button-group>
      <el-button v-if="removable" size="small" :disabled="!canRemove" @click="removeSelectedCase">🗑移除用例</el-button>
    </div>

    <!-- 脑图画布 -->
    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent="onContextMenu"
    />

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 右键菜单 -->
    <MinderContextMenu
      v-if="menuVisible"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__subtitle">标记执行结果 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('pass')">通过</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('fail')">失败</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('block')">阻塞</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('untested')">待执行</div>
      <div v-if="removable" class="mindmap-context-menu__divider" />
      <div v-if="removable" class="mindmap-context-menu__item mindmap-context-menu__item--danger" @click="removeSelectedCase">从计划中移除</div>
    </MinderContextMenu>
  </div>
</template>

<style scoped lang="scss">
@use '../minder/minder-base';

// EP 无内建阻塞档：danger 会与「失败」按钮撞色、warning 为橙，故覆盖按钮变量取
// --color-blocked 深红（视觉设计 4.1），悬停/按下取同相加深档
.exec-block-active {
  --el-button-bg-color: var(--color-blocked);
  --el-button-border-color: var(--color-blocked);
  --el-button-text-color: var(--color-neutral-0);
  --el-button-hover-bg-color: color-mix(in srgb, var(--color-blocked) 88%, black);
  --el-button-hover-border-color: color-mix(in srgb, var(--color-blocked) 88%, black);
  --el-button-hover-text-color: var(--color-neutral-0);
  --el-button-active-bg-color: color-mix(in srgb, var(--color-blocked) 88%, black);
  --el-button-active-border-color: color-mix(in srgb, var(--color-blocked) 88%, black);
  --el-button-active-text-color: var(--color-neutral-0);
}
</style>
