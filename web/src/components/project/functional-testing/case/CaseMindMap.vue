<script setup lang="ts">
import { useMinderInstance } from '@/minder/useMinderInstance'
import MinderContextMenu from '../minder/MinderContextMenu.vue'
import MinderNavigator from '../minder/MinderNavigator.vue'
import { KMEditor } from '@/minder/editor'
import { useMindmapPersistence } from '@/composables/project/functional-testing/mindmap/useMindmapPersistence'
import { useMindmapLayout } from '@/composables/project/functional-testing/mindmap/useMindmapLayout'
import { useMindmapNodeOps } from '@/composables/project/functional-testing/mindmap/useMindmapNodeOps'
import { useMindmapYjs } from '@/composables/project/functional-testing/mindmap/useMindmapYjs'
import { useMindmapInit } from '@/composables/project/functional-testing/mindmap/useMindmapInit'
import { useCaseRequirementLink } from '@/composables/project/functional-testing/mindmap/useCaseRequirementLink'
import { useCaseAssistEntry } from '@/composables/project/ai/useCaseAssistEntry'
import { useCaseGenerationEntry } from '@/composables/project/ai/useCaseGenerationEntry'
import { useCaseOptimizeExecution } from '@/composables/project/functional-testing/mindmap/useCaseOptimizeExecution'
import { useCaseGenerationInsert } from '@/composables/project/functional-testing/mindmap/useCaseGenerationInsert'
import { useCaseNodePopover } from '@/composables/project/functional-testing/mindmap/useCaseNodePopover'
import type { ClickedMinderNode } from '@/composables/project/functional-testing/mindmap/useMindmapInit'
import CaseNodeActionPopover from './CaseNodeActionPopover.vue'
import AssistComparePanel from '@/components/project/ai/AssistComparePanel.vue'
import AiCaseGenerationPanel from '@/components/project/functional-testing/case/AiCaseGenerationPanel.vue'
import RequirementPickerDialog from '@/components/common/RequirementPickerDialog.vue'
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps<{ docId: string }>()

const kmEditorRef = { value: null as KMEditor | null }

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
    nodeOps.onSelectionChange(data)
  },
})

const getMinderTyped = () => getMinder() as unknown as import('@/minder/types').Minder | null

const persistence = useMindmapPersistence(getMinderTyped)

const layout = useMindmapLayout((cmd, ...args) => {
  getMinder()?.execCommand?.(cmd, ...args)
  kmEditorRef.value?.minder.fire('receiverfocus')
})

const nodeOps = useMindmapNodeOps(getMinderTyped, () => getSelectedNodeData(), updateSelectedState, kmEditorRef)

const yjs = useMindmapYjs(
  getMinderTyped,
  persistence.setWsProvider,
  persistence.setApplyingRemote,
  persistence.syncSnapshotFromRemote,
  persistence.schedulePersist,
)

// 左键单击用例节点展开悬浮窗：按节点渲染框定位，避免浮层遮挡被点击节点（交互 04 §1.2）
const caseNodePopover = useCaseNodePopover()

const { menuVisible, menuPos, onContextMenu, closeContextMenu, initMinder, loadError } = useMindmapInit({
  docId: () => props.docId,
  containerRef,
  loading,
  selectedNodeId,
  beginInit,
  isStale,
  invalidate,
  getMinder,
  minder,
  updateSelectedState,
  destroyMinder,
  kmEditorRef,
  persistence,
  yjs,
  layout,
  nodeOps,
  onNodeClick: handleNodeClick,
})

function handleNodeClick(node: ClickedMinderNode): void {
  closeContextMenu()
  const nodeId = node.data.id
  if (node.data.type !== 'case' || typeof nodeId !== 'string' || !nodeId) {
    caseNodePopover.close()
    return
  }
  const box = node.getRenderBox?.(undefined, 'screen')
  if (!box) {
    caseNodePopover.close()
    return
  }
  caseNodePopover.open(nodeId, { x: Math.round(box.x), y: Math.round(box.y + box.height + 4) })
}

// 右键菜单与节点悬浮窗互斥，避免两层浮叠在同一节点上
watch(menuVisible, (visible) => {
  if (visible) caseNodePopover.close()
})

// 用例优化的节点操作在当前脑图树上执行，不再重载整棵脑图（交互 2.1.2）
const caseOptimizeExecution = useCaseOptimizeExecution(
  getMinderTyped,
  persistence.flushPersistenceNow,
)

// AI 优化入口（详设 5.1）：选中用例节点发起，采纳后即时更新脑图节点并落库
const {
  entryVisible: assistEntryVisible,
  gateReason: assistGateReason,
  canLaunch: canLaunchAssist,
  canAdopt: canAdoptAssist,
  panelVisible: assistPanelVisible,
  rows: assistRows,
  loading: assistLoading,
  confirming: assistConfirming,
  receipt: assistReceipt,
  running: assistRunning,
  failed: assistFailed,
  failedReason: assistFailedReason,
  retryable: assistRetryable,
  progressText: assistProgressText,
  loadError: assistLoadError,
  launchOptimize,
  retry: retryAssist,
  refresh: refreshAssist,
  adopt: adoptAssist,
  adoptEdited: adoptAssistEdited,
  reject: rejectAssist,
  clearReceipt: clearAssistReceipt,
  openSource: openAssistSource,
  closePanel: closeAssistPanel,
} = useCaseAssistEntry({
  documentId: () => props.docId,
  selectedNodeId: () => selectedNodeId.value,
  selectedType: () => selectedType.value,
  isReadonly: () => getMinder()?.getStatus?.() === 'readonly',
  executeOperations: (operations, caseNodeId) => caseOptimizeExecution.execute(operations, caseNodeId),
})

// 脑图生成用例：提案节点插入当前文档根节点下，再走既有 WS 持久化管道（详设 4.4）
const caseGenerationInsert = useCaseGenerationInsert(getMinderTyped, persistence.flushPersistenceNow)

const {
  entryVisible: caseGenEntryVisible,
  gateReason: caseGenGateReason,
  pickerVisible: caseGenPickerVisible,
  pickerPicks: caseGenPickerPicks,
  selectedRequirement: caseGenRequirement,
  panelVisible: caseGenPanelVisible,
  rows: caseGenRows,
  loading: caseGenLoading,
  confirming: caseGenConfirming,
  receipt: caseGenReceipt,
  running: caseGenRunning,
  failed: caseGenFailed,
  failedReason: caseGenFailedReason,
  retryable: caseGenRetryable,
  progressText: caseGenProgressText,
  loadError: caseGenLoadError,
  openRadar: openCaseGeneration,
  confirmRequirement: confirmCaseGenerationRequirement,
  adopt: adoptGeneratedCases,
  retry: retryCaseGeneration,
  confirmAgain: confirmCaseGenerationAgain,
  refresh: refreshCaseGeneration,
  clearReceipt: clearCaseGenerationReceipt,
} = useCaseGenerationEntry({
  documentId: () => props.docId,
  isReadonly: () => getMinder()?.getStatus?.() === 'readonly',
  insertCases: (cases) => caseGenerationInsert.execute(cases),
})

// 悬浮窗与选取器复用同一实例，保证回显与全量覆盖语义一致
const requirementLink = useCaseRequirementLink({
  caseId: () => selectedNodeId.value,
  getMinder: getMinderTyped,
  flushPersistence: persistence.flushPersistenceNow,
})

function openOptimizeFromPopover(): void {
  // 悬浮窗记录的是被点击节点；先取值再关窗，避免关闭时清空目标节点
  const nodeId = caseNodePopover.targetNodeId.value
  caseNodePopover.close()
  void launchOptimize(nodeId)
}

function openRequirementFromPopover(): void {
  caseNodePopover.close()
  void requirementLink.open()
}


// 工具条形态跟随 MinderNavigator 的原生全屏，避免窄编辑区按钮溢出
const isFullscreen = ref(false)
const syncFullscreen = () => {
  isFullscreen.value = Boolean(document.fullscreenElement)
}
onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))

const mindmapContainerRef = ref<HTMLElement | null>(null)
// 原生全屏只渲染全屏元素子树，默认 body 下的 teleport 菜单会消失，故全屏时改挂到脑图容器
const menuTeleportTarget = computed(() =>
  isFullscreen.value && mindmapContainerRef.value ? mindmapContainerRef.value : 'body',
)

const hasSelectedNode = computed(() => Boolean(selectedNodeId.value))

type EditCommand = 'undo' | 'redo' | 'addChild' | 'addSibling' | 'edit' | 'delete'
const editActions: Record<EditCommand, () => void> = {
  undo: () => nodeOps.undo(),
  redo: () => nodeOps.redo(),
  addChild: () => nodeOps.addChild(),
  addSibling: () => nodeOps.addSibling(),
  edit: () => nodeOps.editSelectedText(),
  delete: () => nodeOps.deleteNode(),
}
const handleEditCommand = (command: unknown) => {
  if (typeof command !== 'string') return
  editActions[command as EditCommand]?.()
}
const templateNames = new Set(layout.templates.map((t) => t.name))
const handleLayoutCommand = (command: unknown) => {
  if (typeof command !== 'string') return
  if (command === 'tidy') layout.tidyLayout()
  else if (templateNames.has(command)) layout.switchTemplate(command)
}
</script>

<template>
  <div ref="mindmapContainerRef" v-loading="loading" class="mindmap-container">
    <div v-if="loadError" class="mindmap-load-error" role="alert">
      <el-empty description="脑图加载失败">
        <el-button type="danger" plain size="small" @click="initMinder()">重试</el-button>
      </el-empty>
    </div>

    <template v-else>
    <div v-if="!yjs.isConnected.value" class="mindmap-disconnect-banner">
      连接已断开，正在重连...
    </div>

    <div class="mindmap-toolbar">
      <template v-if="!isFullscreen">
        <el-tooltip content="撤销、重做、节点操作与删除" placement="bottom">
          <el-dropdown popper-class="case-edit-dropdown" @command="handleEditCommand">
            <el-button text>
              <el-icon><EditPen /></el-icon><span>编辑</span><el-icon class="toolbar-caret"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="undo" :disabled="!nodeOps.canUndo.value" class="toolbar-menu-item">
                  <span>撤销</span><span class="menu-shortcut">Ctrl+Z</span>
                </el-dropdown-item>
                <el-dropdown-item command="redo" :disabled="!nodeOps.canRedo.value" class="toolbar-menu-item">
                  <span>重做</span><span class="menu-shortcut">Ctrl+Y</span>
                </el-dropdown-item>
                <el-dropdown-item command="addChild" divided class="toolbar-menu-item" :disabled="!hasSelectedNode">
                  <span>新建下级节点</span><span class="menu-shortcut">Tab</span>
                </el-dropdown-item>
                <el-dropdown-item command="addSibling" class="toolbar-menu-item" :disabled="!hasSelectedNode">
                  <span>新建同级节点</span><span class="menu-shortcut">Enter</span>
                </el-dropdown-item>
                <el-dropdown-item command="edit" class="toolbar-menu-item" :disabled="!hasSelectedNode">
                  <span>编辑内容</span><span class="menu-shortcut">F2</span>
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided class="toolbar-menu-item toolbar-menu-item--danger" :disabled="!hasSelectedNode">
                  <span>删除节点</span><span class="menu-shortcut">Delete</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>
        <!-- 模板名不回显占宽，仅经悬浮提示给出（交互设计 04 §1.2） -->
        <el-tooltip :content="`排版模板：${layout.currentTemplateLabel.value}`" placement="bottom">
          <el-dropdown @command="handleLayoutCommand">
            <el-button text>
              <el-icon><Grid /></el-icon><span>排版</span><el-icon class="toolbar-caret"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="t in layout.templates"
                  :key="t.name"
                  :command="t.name"
                  :disabled="t.name === layout.currentTemplate.value"
                  class="toolbar-menu-item"
                >
                  <el-icon v-if="t.name === layout.currentTemplate.value"><Check /></el-icon>
                  <span>{{ t.label }}</span>
                </el-dropdown-item>
                <el-dropdown-item command="tidy" divided class="toolbar-menu-item">
                  <el-icon><MagicStick /></el-icon><span>恢复自动排版</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>
      </template>

      <template v-else>
        <div class="toolbar-group">
          <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
            <el-button text :disabled="!nodeOps.canUndo.value" @click="nodeOps.undo"><el-icon><RefreshLeft /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
            <el-button text :disabled="!nodeOps.canRedo.value" @click="nodeOps.redo"><el-icon><RefreshRight /></el-icon></el-button>
          </el-tooltip>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-tooltip content="添加子节点 (Tab)" placement="bottom">
            <el-button text :disabled="!hasSelectedNode" @click="nodeOps.addChild"><el-icon><Plus /></el-icon><span>下级</span></el-button>
          </el-tooltip>
          <el-tooltip content="添加兄弟节点 (Enter)" placement="bottom">
            <el-button text :disabled="!hasSelectedNode" @click="nodeOps.addSibling"><el-icon><Plus /></el-icon><span>同级</span></el-button>
          </el-tooltip>
          <el-tooltip content="编辑内容 (双击节点/F2)" placement="bottom">
            <el-button text :disabled="!hasSelectedNode" @click="nodeOps.editSelectedText"><el-icon><EditPen /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="删除 (Delete)" placement="bottom">
            <el-button text class="toolbar-btn--danger" :disabled="!hasSelectedNode" @click="nodeOps.deleteNode"><el-icon><Delete /></el-icon></el-button>
          </el-tooltip>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-tooltip :content="`排版模板：${layout.currentTemplateLabel.value}`" placement="bottom">
            <el-dropdown size="small" @command="handleLayoutCommand">
              <el-button text>
                <el-icon><Grid /></el-icon><el-icon class="toolbar-caret"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="t in layout.templates"
                    :key="t.name"
                    :command="t.name"
                    :disabled="t.name === layout.currentTemplate.value"
                  >
                    {{ t.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-tooltip>
          <el-tooltip content="清除手动拖拽的节点偏移，恢复自动排版" placement="bottom">
            <el-button text @click="layout.tidyLayout"><el-icon><MagicStick /></el-icon></el-button>
          </el-tooltip>
        </div>
      </template>

      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-button text :class="['type-btn', { 'is-selected': selectedType === 'case' }]" :disabled="!hasSelectedNode" @click="nodeOps.markAs('case')"><span class="type-dot type-dot--case" /><span>用例</span></el-button>
        <el-button text :class="['type-btn', { 'is-selected': selectedType === 'precondition' }]" :disabled="!hasSelectedNode" @click="nodeOps.markAs('precondition')"><span class="type-dot type-dot--precondition" /><span>前置</span></el-button>
        <el-button text :class="['type-btn', { 'is-selected': selectedType === 'step' }]" :disabled="!hasSelectedNode" @click="nodeOps.markAs('step')"><span class="type-dot type-dot--step" /><span>步骤</span></el-button>
        <el-button text :class="['type-btn', { 'is-selected': selectedType === 'expected' }]" :disabled="!hasSelectedNode" @click="nodeOps.markAs('expected')"><span class="type-dot type-dot--expected" /><span>预期</span></el-button>
        <el-tooltip content="取消标记，恢复普通节点" placement="bottom">
          <el-button text :disabled="!hasSelectedNode" @click="nodeOps.clearMark"><el-icon><CircleClose /></el-icon></el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-button
          v-for="p in ['P0', 'P1', 'P2', 'P3']"
          :key="p"
          text
          :class="['priority-btn', `priority-btn--${p.toLowerCase()}`, { 'is-selected': nodeOps.selectedPriority.value === p }]"
          :disabled="!hasSelectedNode"
          @click="nodeOps.markPriority(p)"
        >{{ p }}</el-button>
      </div>

      <!-- AI覆盖雷达入口常驻工具条最右，全屏与非全屏共用同一按钮（详设 5.1） -->
      <div v-if="caseGenEntryVisible" class="toolbar-ai-entry">
        <el-tooltip :content="caseGenGateReason || '扫描需求测试用例覆盖率并补齐缺漏'" placement="bottom">
          <el-button text :disabled="Boolean(caseGenGateReason)" @click="openCaseGeneration">
            <el-icon><MagicStick /></el-icon><span>AI覆盖雷达</span>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent="onContextMenu"
    />

    <div v-if="yjs.onlineUsers.value.length" class="online-users">
      <el-avatar v-for="user in yjs.onlineUsers.value" :key="user.id" :size="24" :style="{ border: `2px solid ${user.color}` }">
        {{ user.name.charAt(0) }}
      </el-avatar>
    </div>

    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <MinderContextMenu
      v-if="menuVisible"
      :to="menuTeleportTarget"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__item menu-action" @click="nodeOps.addChild"><span>新建下级节点</span><span class="menu-shortcut">Tab</span></div>
      <div class="mindmap-context-menu__item menu-action" @click="nodeOps.addSibling"><span>新建同级节点</span><span class="menu-shortcut">Enter</span></div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item menu-action" @click="nodeOps.copyNode"><span>复制</span><span class="menu-shortcut">Ctrl+C</span></div>
      <div class="mindmap-context-menu__item menu-action" @click="nodeOps.cutNode"><span>剪切</span><span class="menu-shortcut">Ctrl+X</span></div>
      <div :class="['mindmap-context-menu__item', 'menu-action', { 'is-disabled': !nodeOps.hasClipboard }]" @click="nodeOps.pasteNode"><span>粘贴</span><span class="menu-shortcut">Ctrl+V</span></div>
      <div class="mindmap-context-menu__divider" />
      <div class="menu-chip-row">
        <span class="menu-chip-label">类型</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'case' }]" @click="nodeOps.markAs('case')"><span class="type-dot type-dot--case" />用例</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'precondition' }]" @click="nodeOps.markAs('precondition')"><span class="type-dot type-dot--precondition" />前置</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'step' }]" @click="nodeOps.markAs('step')"><span class="type-dot type-dot--step" />步骤</span>
        <span :class="['menu-chip', { 'is-selected': selectedType === 'expected' }]" @click="nodeOps.markAs('expected')"><span class="type-dot type-dot--expected" />预期</span>
        <span class="menu-chip" title="取消标记" @click="nodeOps.clearMark"><el-icon><CircleClose /></el-icon></span>
      </div>
      <div class="menu-chip-row">
        <span class="menu-chip-label">等级</span>
        <span
          v-for="p in ['P0', 'P1', 'P2', 'P3']"
          :key="p"
          :class="['menu-chip', `menu-chip--${p.toLowerCase()}`, { 'is-selected': nodeOps.selectedPriority.value === p, 'is-disabled': !hasSelectedNode }]"
          @click="nodeOps.markPriority(p)"
        >{{ p }}</span>
      </div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item mindmap-context-menu__item--danger menu-action" @click="nodeOps.deleteNode"><span>删除节点</span><span class="menu-shortcut">Delete</span></div>
    </MinderContextMenu>

    <CaseNodeActionPopover
      :to="menuTeleportTarget"
      :visible="caseNodePopover.visible.value"
      :x="caseNodePopover.anchor.value.x"
      :y="caseNodePopover.anchor.value.y"
      :ai-entry-visible="assistEntryVisible"
      :ai-gate-reason="assistGateReason"
      :can-launch-ai="canLaunchAssist"
      @ai-optimize="openOptimizeFromPopover"
      @link-requirement="openRequirementFromPopover"
    />

    <!-- AI 优化面板：进行中展示进度入口，完成后切换为对照面板（交互 2.3） -->
    <el-dialog
      v-model="assistPanelVisible"
      title="AI 优化"
      width="720px"
      class="ai-assist-dialog"
      :close-on-click-modal="false"
      @closed="closeAssistPanel"
    >
      <div v-if="assistRunning" class="ai-assist-progress">
        <div class="ai-assist-progress__status">
          <el-icon class="is-loading ai-assist-progress__spinner"><Loading /></el-icon>
          <div class="ai-assist-progress__text">
            <span class="ai-assist-progress__title">AI 正在生成建议…</span>
            <span v-if="assistProgressText" class="ai-assist-progress__detail">
              {{ assistProgressText }}
            </span>
          </div>
          <el-button size="small" plain @click="closeAssistPanel">关闭弹窗</el-button>
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="关闭弹窗后任务仍在后台运行，可在顶部 AI 任务角标或 AI 任务中心查看进度"
        />
      </div>
      <el-alert
        v-else-if="assistFailed"
        type="error"
        :closable="false"
        show-icon
        :title="assistFailedReason || '建议生成失败'"
      >
        <el-button type="danger" plain size="small" @click="retryAssist">重试</el-button>
      </el-alert>
      <el-alert
        v-else-if="assistLoadError"
        type="error"
        :closable="false"
        show-icon
        :title="assistLoadError"
      >
        <el-button type="danger" plain size="small" @click="refreshAssist">重试</el-button>
        <el-button size="small" plain @click="closeAssistPanel">关闭</el-button>
      </el-alert>
      <AssistComparePanel
        v-else
        :rows="assistRows"
        :can-adopt="canAdoptAssist"
        :confirming="assistConfirming"
        :loading="assistLoading"
        :receipt="assistReceipt"
        :retryable="assistRetryable"
        @adopt="adoptAssist"
        @adopt-edited="adoptAssistEdited"
        @reject="rejectAssist"
        @open-source="openAssistSource"
        @clear-receipt="clearAssistReceipt"
        @retry="retryAssist"
        @close="closeAssistPanel"
      />
    </el-dialog>

    <RequirementPickerDialog
      v-model="requirementLink.visible.value"
      :initial="requirementLink.picks()"
      :multiple="false"
      allow-empty
      @confirm="(ids) => void requirementLink.confirm(ids)"
    />

    <!-- AI覆盖雷达：命中最近任务直接回显，未命中需求单选 → 生成提案 → 勾选插入当前文档（详设 5.2） -->
    <RequirementPickerDialog
      v-model="caseGenPickerVisible"
      :initial="caseGenPickerPicks"
      :multiple="false"
      @confirm="(ids, picks) => void confirmCaseGenerationRequirement(ids, picks)"
    />

    <AiCaseGenerationPanel
      v-model="caseGenPanelVisible"
      :requirement="caseGenRequirement"
      :rows="caseGenRows"
      :loading="caseGenLoading"
      :confirming="caseGenConfirming"
      :running="caseGenRunning"
      :failed="caseGenFailed"
      :failed-reason="caseGenFailedReason"
      :progress-text="caseGenProgressText"
      :retryable="caseGenRetryable"
      :receipt="caseGenReceipt"
      :load-error="caseGenLoadError"
      @adopt="adoptGeneratedCases"
      @retry="retryCaseGeneration"
      @retry-item="(key) => void confirmCaseGenerationAgain(key)"
      @refresh="refreshCaseGeneration"
      @clear-receipt="clearCaseGenerationReceipt"
    />
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '../minder/minder-base';
@use '../minder/minder-menu' as menu;

.mindmap-load-error {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 0;
}

.mindmap-disconnect-banner {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  text-align: center;
  padding: 4px;
  font-size: 12px;
  flex-shrink: 0;

  & ~ .online-users { top: 40px; }
}

.online-users {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  z-index: 10;
  display: flex;
  gap: 4px;
}

/* 工具条结构样式（单行、分组、命令组右靠）由 minder-base 承载；
   此处仅保留本组件的按钮视觉覆盖 */
.mindmap-toolbar :deep(.el-button) {
  padding-left: 8px;
  padding-right: 8px;
  margin-left: 0;
}

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
  --el-fill-color-light: var(--color-primary-100);
  --el-button-text-color: var(--color-primary-600);
  --el-button-hover-text-color: var(--color-primary-600);
}

$priorities: p0, p1, p2, p3;

@each $p in $priorities {
  .priority-btn--#{$p} {
    --el-button-text-color: var(--color-priority-#{$p});
    --el-button-hover-text-color: var(--color-priority-#{$p});
  }
  .priority-btn--#{$p}.is-selected {
    background-color: var(--color-priority-#{$p});
    --el-fill-color-light: var(--color-priority-#{$p});
  }
}

.priority-btn.is-selected {
  --el-button-text-color: #fff;
  --el-button-hover-text-color: #fff;
}

.toolbar-btn--danger { --el-button-hover-text-color: var(--color-danger); }

.toolbar-ai-entry {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.toolbar-caret { margin-left: 2px; font-size: 10px; }

.menu-action {
  @include menu.minder-menu-row;

  &.is-disabled {
    color: var(--el-text-color-disabled);
    cursor: not-allowed;
    &:hover { background: none; color: var(--el-text-color-disabled); }
  }
}

.menu-shortcut {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.menu-chip-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;

  .menu-chip-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-right: 4px;
    flex-shrink: 0;
  }
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

  &:hover { background: var(--el-fill-color-light); }
  &.is-selected { background: var(--color-primary-50); color: var(--color-primary-600); }
  &.is-disabled { opacity: 0.45; cursor: not-allowed; pointer-events: none; }
}

@each $p in $priorities {
  .menu-chip--#{$p} { color: var(--color-priority-#{$p}); }
  .menu-chip--#{$p}.is-selected { background: var(--color-priority-#{$p}); color: #fff; }
}

.minder-canvas :deep(.km-receiver) {
  position: absolute;
  z-index: 20;
  opacity: 0;
  pointer-events: none;
  padding: 0;
  width: max-content;
  min-width: 1em;
  max-width: 300px;
  border: none;
  outline: none;
  background: transparent;
  white-space: pre-wrap;
  word-break: break-all;

  &.input {
    opacity: 1;
    pointer-events: auto;
    caret-color: var(--el-color-primary);
  }
}

.ai-assist-progress {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.ai-assist-progress__status {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-lg);
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
}

.ai-assist-progress__spinner {
  flex-shrink: 0;
  font-size: var(--font-size-xl);
  color: var(--color-primary-500);
}

.ai-assist-progress__text {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-xs);
  font-size: var(--font-size-sm);
}

.ai-assist-progress__title {
  color: var(--color-neutral-800);
  font-weight: 600;
}

.ai-assist-progress__detail {
  color: var(--color-neutral-500);
}
</style>

<style lang="scss">
/*
 * CODE-008 例外：Element Plus el-dropdown-item 为多根 Fragment 子组件，父组件 scopeId 不会透传到下拉项 li，
 * scoped 选择器无法命中，故在全局作用域按 popper-class 锚定，避免扩散到其它下拉。
 * 规则来源：docs/00-spec/10-engineering/01-frontend.md §8。
 */
@use '../minder/minder-menu' as menu;

// Dialog 根节点 Teleport 到 body 且无 scopeId，scoped 选择器无法命中，按 class 锚定仅收紧本弹窗容器 padding
.ai-assist-dialog {
  .el-dialog__header,
  .el-dialog__body,
  .el-dialog__footer {
    padding: 12px 16px;
  }
}

.case-edit-dropdown {
  .el-dropdown-menu__item.toolbar-menu-item {
    @include menu.minder-menu-row;
    --el-font-size-base: #{menu.$minder-menu-font-size};
    --el-dropdown-menuItem-hover-fill: #{menu.$minder-menu-hover-fill};
    --el-dropdown-menuItem-hover-color: #{menu.$minder-menu-hover-color};
  }

  .el-dropdown-menu__item.toolbar-menu-item--danger {
    --el-dropdown-menuItem-hover-color: #{menu.$minder-menu-danger-color};
    color: menu.$minder-menu-danger-color;

    &.is-disabled { color: var(--el-text-color-disabled); }
  }
}
</style>
