<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { loadMinderEngine } from '../loader'
import { previewToKityJsonDeep } from './aiPreviewRender'
import { filterCheckedTree, type AiPreviewNode } from './aiMount'
import type { AiGeneratedNode } from '@/types'
import type { Minder, MinderEvent, MinderNode } from '../types'

/**
 * AI 生成结果独立预览弹窗（交互设计 2.1/2.2/2.3）：
 * 生成弹窗点击 [查看预览] 后打开（两窗并存、本弹窗置顶）；以裸 kityminder 只读实例渲染
 * 完整文档树快照（本地组装不落库、不产生撤销历史），AI 节点带勾选框可点击取舍。
 * 勾选状态（aiSelected）仅存于本弹窗工作副本，关闭即丢弃（纯预览约束）；确认挂载后由
 * 生成弹窗透传节点树给脑图组件执行挂载。
 */
const props = defineProps<{
  nodes: AiPreviewNode[]
  targetPath: string
  /** 预览组装时挂载目标已缺失（回退为仅生成节点树）：确认前需重新选择挂载位置 */
  targetMissing: boolean
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  /** 确认挂载：经勾选过滤后的节点树 */
  confirm: [nodes: AiGeneratedNode[]]
}>()

const containerRef = ref<HTMLDivElement>()
const minder = ref<Minder | null>(null)
/** 勾选态工作副本：弹窗内点击切换，确认时据此过滤；关闭即丢弃（交互设计 2.2） */
const workingTree = ref<AiPreviewNode[]>([])

// 异步初始化竞态令牌：快速开/关或重复打开时丢弃过期结果
let initToken = 0
let unmounted = false

const selectedCount = computed(() => countAiSelected(workingTree.value))
const totalCount = computed(() => countAiTotal(workingTree.value))

function countAiSelected(nodes: AiPreviewNode[]): number {
  return nodes.reduce(
    (sum, node) => sum + (node.aiGenerated && node.aiSelected ? 1 : 0) + countAiSelected(node.children),
    0,
  )
}

function countAiTotal(nodes: AiPreviewNode[]): number {
  return nodes.reduce((sum, node) => sum + (node.aiGenerated ? 1 : 0) + countAiTotal(node.children), 0)
}

/** AiPreviewNode 为纯数据，深拷贝作工作副本（避免直接改动 props） */
function cloneTree(nodes: AiPreviewNode[]): AiPreviewNode[] {
  return JSON.parse(JSON.stringify(nodes)) as AiPreviewNode[]
}

// ==================== 实例生命周期 ====================

async function initPreview(): Promise<void> {
  const token = ++initToken
  await nextTick()
  const container = containerRef.value
  if (!container) return
  const tree = cloneTree(props.nodes)
  if (!tree.length) return
  const km = await loadMinderEngine()
  if (token !== initToken || unmounted || !containerRef.value) return

  workingTree.value = tree
  // 勾选框渲染器经 loadMinderEngine 的 registerCheckboxModule 全局注册（构造期统一收集），
  // 与 badges 同池、与 plan/review 组件同一初始化路径；构造后注入实例 _rendererClasses
  // 会导致 root 渲染器数量与 importJson 新建节点不一致，布局越界节点叠在原点
  const instance = new km.Minder({ renderTo: container, enableKeyReceiver: false })
  instance.importJson(previewToKityJsonDeep(tree) ?? { root: { data: {}, children: [] } })
  // 只读展示：禁用命令/交互，但 click 事件照常派发（勾选点击仍需响应）
  instance.disable()
  instance.on('click', onNodeClick)
  minder.value = instance
}

function destroyPreview(): void {
  minder.value?.destroy()
  minder.value = null
  workingTree.value = []
}

// ==================== 勾选取舍（交互设计 2.2 级联规则） ====================

interface PreviewClickEvent extends MinderEvent {
  getTargetNode?: () => MinderNode | null
}

function onNodeClick(e: MinderEvent): void {
  const target = (e as PreviewClickEvent).getTargetNode?.()
  if (!target) return
  // 既有节点只读：仅本次生成节点响应勾选
  if (target.getData('aiGenerated') !== true) return
  const key = target.getData('id') as string
  const next = target.getData('aiSelected') !== true
  // 级联：本节点及全部 AI 子孙同步切换（取消父节点则子孙一并排除，恢复则整组恢复）
  applyKitySelection(target, next)
  applyTreeSelection(workingTree.value, key, next)
  target.render()
}

function applyKitySelection(node: MinderNode, selected: boolean): void {
  node.setData('aiSelected', selected)
  node.getChildren().forEach((child) => applyKitySelection(child, selected))
}

/** 在树中按 key 找到目标节点并级联更新其 AI 子孙 */
function applyTreeSelection(nodes: AiPreviewNode[], key: string, selected: boolean): boolean {
  for (const node of nodes) {
    if (node.key === key) {
      setSubtreeSelected(node, selected)
      return true
    }
    if (applyTreeSelection(node.children, key, selected)) return true
  }
  return false
}

function setSubtreeSelected(node: AiPreviewNode, selected: boolean): void {
  node.aiSelected = selected
  node.children.forEach((child) => setSubtreeSelected(child, selected))
}

// ==================== 确认 / 关闭 ====================

function handleConfirm(): void {
  const nodes = filterCheckedTree(workingTree.value)
  if (!nodes.length) {
    ElMessage.warning('请至少勾选一个要挂载的节点')
    return
  }
  emit('confirm', nodes)
}

function handleClose(): void {
  // 仅关闭预览弹窗：生成弹窗保留「完成」态，可重新打开预览（交互设计 2.2）
  visible.value = false
}

watch(
  visible,
  (open) => {
    if (open) void initPreview()
    else destroyPreview()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  unmounted = true
  destroyPreview()
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="920px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="true"
    class="ai-preview-dialog"
    @close="handleClose"
  >
    <template #header>
      <span class="ai-preview-dialog__title">
        <el-icon><MagicStick /></el-icon> 生成结果预览
      </span>
    </template>

    <div class="ai-preview-dialog__body">
      <div class="ai-preview-dialog__target">挂载目标：{{ targetPath }}</div>
      <el-alert
        v-if="targetMissing"
        type="warning"
        :closable="false"
        show-icon
        title="挂载目标已被删除，确认挂载前请重新选择挂载位置"
      />
      <div ref="containerRef" class="ai-preview-dialog__minder" />
    </div>

    <template #footer>
      <span class="ai-preview-dialog__count">已勾选 {{ selectedCount }}/{{ totalCount }} 个生成节点</span>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleConfirm">确认挂载</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.ai-preview-dialog__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ai-preview-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-preview-dialog__target {
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

// 脑图容器：预览快照仅展示于此，画布不参与编辑与协同
.ai-preview-dialog__minder {
  height: 420px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.ai-preview-dialog__count {
  margin-right: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
