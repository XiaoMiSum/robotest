<script setup lang="ts">
import { usePlanMindmapOps } from '@/composables/project/functional-testing/plan/usePlanMindmapOps'
/**
 * PlanMindMap 经本地 composable 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需响应用户执行标记操作并即时提交，
 * 数据流与交互深度耦合，抽到 page 层会导致大量 props/emit 透传。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useMinderInstance } from '@/minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from '@/minder/useContextMenu'
import MinderContextMenu from '../minder/MinderContextMenu.vue'
import MinderNavigator from '../minder/MinderNavigator.vue'

const props = defineProps<{ planId: string; documentId?: string; removable?: boolean }>()

// 标记成功后通知详情页刷新进度，否则页头进度条需手动刷新才能更新
const emit = defineEmits<{ marked: []; removed: [] }>()

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
  getPlanId: () => props.planId,
  getDocumentId: () => props.documentId,
  getRemovable: () => props.removable,
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

// reload 供详情页同步快照后刷新画布（planId 不变，watch 不会触发）
defineExpose({ openBug, reload: initMinder })

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
    <div class="mindmap-toolbar mindmap-toolbar--center">
      <el-button-group>
        <el-button :type="execResult==='pass'?'success':''" @click="markExecution('pass')">✅通过</el-button>
        <el-button :type="execResult==='fail'?'danger':''" @click="markExecution('fail')">❌失败</el-button>
        <el-button :class="execResult==='block'?'exec-block-active':''" @click="markExecution('block')">❓阻塞</el-button>
        <el-button :type="execResult==='untested'?'info':''" @click="markExecution('untested')">🔄待执行</el-button>
      </el-button-group>
      <el-button v-if="removable" :disabled="!canRemove" @click="removeSelectedCase">🗑移除用例</el-button>
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
