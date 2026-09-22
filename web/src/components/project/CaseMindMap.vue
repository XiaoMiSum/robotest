<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAssistantContextStore } from '@/stores/assistantContext'
import { useMinderInstance } from './minder/useMinderInstance'
import MinderContextMenu from './minder/MinderContextMenu.vue'
import MinderNavigator from './minder/MinderNavigator.vue'
import AiGeneratePanel from './minder/ai/AiGeneratePanel.vue'
import RequirementSelector from './RequirementSelector.vue'
import MissingPointsPanel from './MissingPointsPanel.vue'
import { KMEditor } from './minder/editor'
import { useMindmapPersistence } from '@/composables/useMindmapPersistence'
import { useMindmapAI } from '@/composables/useMindmapAI'
import { useMindmapLayout } from '@/composables/useMindmapLayout'
import { useMindmapNodeOps } from '@/composables/useMindmapNodeOps'
import { useMindmapYjs } from '@/composables/useMindmapYjs'
import { useMindmapInit } from '@/composables/useMindmapInit'

const props = defineProps<{ docId: string }>()

const authStore = useAuthStore()
const assistantContext = useAssistantContextStore()

const canManageRequirements = computed(() => authStore.hasPermission('requirement:view'))

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
    assistantContext.setSelectedNode(selectedNodeId.value || null)
  },
})

const getMinderTyped = () => getMinder() as unknown as import('./minder/types').Minder | null

const persistence = useMindmapPersistence(getMinderTyped)

const ai = useMindmapAI(getMinderTyped, () => getSelectedNodeData(), () => props.docId)

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

const { menuVisible, menuPos, onContextMenu, closeContextMenu } = useMindmapInit({
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
  assistantContext,
  aiResetPanels: ai.resetPanels,
  aiStopAiReadyPoll: ai.stopAiReadyPoll,
})

defineExpose({ openAiGenerateWithText: ai.openAiGenerateWithText })
</script>

<template>
  <div v-loading="loading" class="mindmap-container">
    <div v-if="!yjs.isConnected.value" class="mindmap-disconnect-banner">
      连接已断开，正在重连...
    </div>

    <div class="mindmap-toolbar">
      <div class="mindmap-toolbar__core">
        <div class="toolbar-group">
          <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
            <el-button size="small" text :disabled="!nodeOps.canUndo.value" @click="nodeOps.undo"><el-icon><RefreshLeft /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
            <el-button size="small" text :disabled="!nodeOps.canRedo.value" @click="nodeOps.redo"><el-icon><RefreshRight /></el-icon></el-button>
          </el-tooltip>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-tooltip content="添加子节点 (Tab)" placement="bottom">
            <el-button size="small" text @click="nodeOps.addChild"><el-icon><Plus /></el-icon><span>下级</span></el-button>
          </el-tooltip>
          <el-tooltip content="添加兄弟节点 (Enter)" placement="bottom">
            <el-button size="small" text @click="nodeOps.addSibling"><el-icon><Plus /></el-icon><span>同级</span></el-button>
          </el-tooltip>
          <el-tooltip content="编辑内容 (双击节点/F2)" placement="bottom">
            <el-button size="small" text @click="nodeOps.editSelectedText"><el-icon><EditPen /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="删除 (Delete)" placement="bottom">
            <el-button size="small" text class="toolbar-btn--danger" @click="nodeOps.deleteNode"><el-icon><Delete /></el-icon></el-button>
          </el-tooltip>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-dropdown size="small" @command="layout.switchTemplate">
            <el-button size="small" text>
              <el-icon><Grid /></el-icon><span>{{ layout.currentTemplateLabel.value }}</span><el-icon class="toolbar-caret"><ArrowDown /></el-icon>
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
          <el-tooltip content="清除手动拖拽的节点偏移，恢复自动排版" placement="bottom">
            <el-button size="small" text @click="layout.tidyLayout"><el-icon><MagicStick /></el-icon></el-button>
          </el-tooltip>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'case' }]" @click="nodeOps.markAs('case')"><span class="type-dot type-dot--case" /><span>用例</span></el-button>
          <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'precondition' }]" @click="nodeOps.markAs('precondition')"><span class="type-dot type-dot--precondition" /><span>前置</span></el-button>
          <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'step' }]" @click="nodeOps.markAs('step')"><span class="type-dot type-dot--step" /><span>步骤</span></el-button>
          <el-button size="small" text :class="['type-btn', { 'is-selected': selectedType === 'expected' }]" @click="nodeOps.markAs('expected')"><span class="type-dot type-dot--expected" /><span>预期</span></el-button>
          <el-tooltip content="取消标记，恢复普通节点" placement="bottom">
            <el-button size="small" text @click="nodeOps.clearMark"><el-icon><CircleClose /></el-icon></el-button>
          </el-tooltip>
        </div>
        <el-divider direction="vertical" />
        <div class="toolbar-group">
          <el-tooltip v-if="nodeOps.priorityRecommendation.value && selectedType === 'case'" content="AI 推荐优先级，点击采纳" placement="bottom">
            <el-button size="small" text class="priority-recommend-btn" @click="nodeOps.applyPriorityRecommendation">
              ✨ 推荐 {{ nodeOps.priorityRecommendation.value.priority }}
            </el-button>
          </el-tooltip>
          <el-button
            v-for="p in ['P0', 'P1', 'P2', 'P3']"
            :key="p"
            size="small"
            text
            :class="['priority-btn', `priority-btn--${p.toLowerCase()}`, { 'is-selected': nodeOps.selectedPriority.value === p }]"
            @click="nodeOps.markPriority(p)"
          >{{ p }}</el-button>
        </div>
      </div>

      <div v-if="nodeOps.aiStore.aiEnabled || canManageRequirements" class="mindmap-toolbar__ai-cmd">
        <template v-if="canManageRequirements">
          <div class="toolbar-group">
            <el-button size="small" text @click="ai.openRequirementSelector">
              <el-icon><Link /></el-icon><span>关联需求</span>
            </el-button>
          </div>
          <el-divider direction="vertical" />
        </template>
        <div v-if="nodeOps.aiStore.aiEnabled" class="toolbar-group">
          <el-button size="small" text class="ai-entry-btn" @click="ai.openAiPanel">
            <el-icon><MagicStick /></el-icon><span>AI 生成用例</span>
          </el-button>
          <el-button size="small" text class="ai-entry-btn" @click="ai.missingPointsVisible.value = true">
            <el-icon><MagicStick /></el-icon><span>遗漏测试点</span>
          </el-button>
        </div>
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
      <div
        v-if="nodeOps.priorityRecommendation.value && selectedType === 'case'"
        class="menu-priority-recommend"
        @click="nodeOps.applyPriorityRecommendation"
      >✨ 推荐 {{ nodeOps.priorityRecommendation.value.priority }}（点击采纳）</div>
      <div class="menu-chip-row">
        <span class="menu-chip-label">等级</span>
        <span
          v-for="p in ['P0', 'P1', 'P2', 'P3']"
          :key="p"
          :class="['menu-chip', `menu-chip--${p.toLowerCase()}`, { 'is-selected': nodeOps.selectedPriority.value === p }]"
          @click="nodeOps.markPriority(p)"
        >{{ p }}</span>
      </div>
      <div v-if="nodeOps.selectedAiGenerated.value" class="menu-chip-row">
        <span class="menu-chip-label">AI</span>
        <span class="menu-chip" @click="nodeOps.removeAiFlag">移除 AI 标识</span>
      </div>
      <template v-if="nodeOps.aiStore.aiEnabled && selectedType === 'case'">
        <div class="mindmap-context-menu__divider" />
        <div class="mindmap-context-menu__item menu-action" @click="ai.openAiCompletePanel">
          <span>✨ AI 补全步骤</span>
        </div>
      </template>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item mindmap-context-menu__item--danger menu-action" @click="nodeOps.deleteNode"><span>删除节点</span><span class="menu-shortcut">Delete</span></div>
    </MinderContextMenu>

    <AiGeneratePanel
      v-model="ai.aiGenerateVisible.value"
      mode="generate"
      :doc-id="props.docId"
      :target-node-id="ai.aiGenerateTargetNodeId.value"
      :target-path="ai.aiGenerateTargetPath.value"
      :get-doc-tree="ai.getLiveRoot"
      :initial-text="ai.aiGenerateInitialText.value"
      :reset-token="ai.aiGenerateSession.value"
      @mount="ai.handleAiMount"
    />

    <AiGeneratePanel
      v-model="ai.aiCompleteVisible.value"
      mode="complete"
      :doc-id="props.docId"
      :target-node-id="ai.aiCompleteTargetNodeId.value"
      :target-path="ai.aiCompleteTargetPath.value"
      :get-doc-tree="ai.getLiveRoot"
      :initial-text="ai.aiCompleteInitialText.value"
      :reset-token="ai.aiCompleteSession.value"
      @mount="ai.handleAiMount"
    />

    <el-dialog v-model="ai.aiReselectVisible.value" title="重新选择挂载位置" width="360px" append-to-body>
      <el-tree
        :data="ai.aiReselectTree.value"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        @node-click="ai.handleAiReselect"
      />
    </el-dialog>

    <RequirementSelector
      v-if="ai.reqSelectorVisible.value"
      v-model="ai.reqSelectorVisible.value"
      :selected-ids="ai.associatedReqIds.value"
      @confirm="ai.handleRequirementConfirm"
    />

    <MissingPointsPanel v-model="ai.missingPointsVisible.value" :doc-id="props.docId" />
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

.mindmap-toolbar {
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  gap: 2px;
  overflow-x: hidden;
}

.mindmap-toolbar__core {
  display: flex;
  align-items: center;
  justify-content: safe center;
  gap: var(--space-xs);
  width: 100%;
  overflow-x: auto;
  scrollbar-width: thin;

  &::-webkit-scrollbar { height: 4px; }
  &::-webkit-scrollbar-thumb { background: var(--color-neutral-300); border-radius: 2px; }
  &::-webkit-scrollbar-track { background: transparent; }
}

.mindmap-toolbar__ai-cmd {
  display: flex;
  justify-content: flex-end;
  width: 100%;
}

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

.ai-entry-btn {
  --el-button-text-color: #13c2c2;
  --el-button-hover-text-color: #0da8a8;
}

.toolbar-caret { margin-left: 2px; font-size: 10px; }

.menu-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;

  &.is-disabled {
    color: var(--el-text-color-disabled);
    cursor: not-allowed;
    &:hover { background: none; color: var(--el-text-color-disabled); }
  }
}

.menu-shortcut {
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

.menu-priority-recommend {
  margin: 6px 12px 2px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--color-primary-600);
  background: var(--color-primary-50);
  cursor: pointer;
}

.priority-recommend-btn { color: var(--color-primary-600) !important; }

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
</style>
