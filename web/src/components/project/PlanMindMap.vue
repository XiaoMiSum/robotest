<script setup lang="ts">
/**
 * PlanMindMap 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需响应用户执行标记操作并即时提交，
 * 数据流与交互深度耦合，抽到 page 层会导致大量 props/emit 透传。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPlanSnapshotTree, submitExecutionRecord } from '@/services/project'
import type { ExecutionResult } from '@/types'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import { planNodeToKm } from './minder/adapter'
import { loadMinderEngine } from './minder/loader'
import { useMinderInstance } from './minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from './minder/useContextMenu'
import MinderContextMenu from './minder/MinderContextMenu.vue'
import MinderNavigator from './minder/MinderNavigator.vue'

const props = defineProps<{ planId: string }>()

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

// ==================== 初始化 ====================
async function initMinder() {
  if (!containerRef.value || !props.planId) return
  const token = beginInit()
  loading.value = true
  destroyMinder()
  try {
    const tree = await getPlanSnapshotTree(props.planId)
    const root = tree.length ? planNodeToKm(tree[0]) : { data: { text: '空快照' }, children: [] }
    const kmData = { root, template: 'default', theme: 'fresh-purple' }

    const km = await loadMinderEngine()
    // 异步等待期间组件可能已卸载或已切换目标，过期结果直接丢弃
    if (isStale(token) || !containerRef.value) return

    // 快照只读展示，裸 minder 即可，无需编辑内核
    const instance: unknown = new km.Minder({ renderTo: containerRef.value })
    minder.value = instance
    const m = instance as Record<string, (...args: unknown[]) => unknown>
    m.importJson(kmData)

    // 禁用画布编辑以防止用户修改快照原始数据
    m.disable?.()

    m.on('selectionchange', updateSelectedState)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
  } finally {
    loading.value = false
  }
}

// ==================== 执行标记操作 ====================
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
watch(() => props.planId, initMinder)
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
        <el-button :type="execResult==='block'?'warning':''" @click="markExecution('block')">❓阻塞</el-button>
        <el-button :type="execResult==='untested'?'info':''" @click="markExecution('untested')">🔄未执行</el-button>
      </el-button-group>
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
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markExecution('untested')">未执行</div>
    </MinderContextMenu>
  </div>
</template>

<style scoped lang="scss">
@use './minder/minder-base';
</style>
