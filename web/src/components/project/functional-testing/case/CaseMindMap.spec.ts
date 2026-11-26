import { describe, expect, it } from 'vitest'
import source from './CaseMindMap.vue?raw'

describe('CaseMindMap · 工具条单行排版（交互 04 §1.2）', () => {
  it('非全屏收纳脑图编辑功能，全屏恢复全平铺', () => {
    expect(source).toContain('fullscreenchange')
    expect(source).toContain('const isFullscreen = ref(false)')
    expect(source).toContain('<template v-if="!isFullscreen">')
    expect(source).toContain('<el-dropdown popper-class="case-edit-dropdown" @command="handleEditCommand">')
    expect(source).toContain('<el-dropdown @command="handleLayoutCommand">')
    expect(source).toContain('const handleEditCommand = (command: unknown)')
  })

  it('工具条沿用共享单行样式，组件层不引入横向滚动', () => {
    expect(source).toContain("@use '../minder/minder-base'")
    expect(source).not.toContain('overflow-x')
  })

  it('编辑下拉复用右键菜单的视觉 token，仅做视觉对齐', () => {
    expect(source).toContain("@use '../minder/minder-menu' as menu")
    expect(source).toContain('.toolbar-menu-item')
    expect(source).toContain('--el-dropdown-menuItem-hover-fill')
    expect(source).toContain('#{menu.$minder-menu-hover-fill}')
  })
})

describe('CaseMindMap · 业务入口收拢', () => {
  it('工具条不再放置 AI 优化、级别推荐与关联需求按钮', () => {
    expect(source).not.toContain('<span>AI 优化</span>')
    expect(source).not.toContain('<span>级别推荐</span>')
    expect(source).not.toContain('<span>关联需求</span>')
    expect(source).not.toContain('canLinkRequirement')
    expect(source).not.toContain('requirementGateReason')
    expect(source).not.toContain('canManageRequirements')
    expect(source).not.toContain('requirement:view')
  })
})

describe('CaseMindMap · AI 辅助入口（详设 5.1 / 交互 2.3）', () => {
  it('悬浮窗透传 AI 可用性与置灰原因，沿用同一任务入口', () => {
    expect(source).toContain('useCaseAssistEntry')
    expect(source).toContain('launchOptimize')
    expect(source).toContain('documentId: () => props.docId')
    expect(source).toContain('selectedNodeId: () => selectedNodeId.value')
    expect(source).toContain('selectedType: () => selectedType.value')
    expect(source).toContain(':ai-entry-visible="assistEntryVisible"')
    expect(source).toContain(':ai-gate-reason="assistGateReason"')
    expect(source).toContain(':can-launch-ai="canLaunchAssist"')
  })

  it('AI覆盖雷达为仅图标按钮，功能名由 title 与 aria-label 承担', () => {
    expect(source).toContain("import aiIcon from '@/assets/icon/ai.svg'")
    expect(source).toContain('title="AI覆盖雷达"')
    expect(source).toContain('aria-label="AI覆盖雷达"')
    expect(source).toContain('<img :src="aiIcon" alt="" class="ai-icon" />')
    expect(source).not.toContain('<span>AI覆盖雷达</span>')
  })

  it('发起后在对话框内覆盖进行中 / 终止 / 加载失败 / 对照面板四态', () => {
    expect(source).toContain('v-if="assistRunning"')
    expect(source).toContain('AI 正在生成建议')
    expect(source).toContain('关闭弹窗后任务仍在后台运行')
    expect(source).not.toContain('后台运行，关闭')
    expect(source).toContain('v-else-if="assistFailed"')
    expect(source).toContain('重试')
    expect(source).toContain('v-else-if="assistLoadError"')
    expect(source).toContain('<AssistComparePanel')
    expect(source).toContain(':can-adopt="canAdoptAssist"')
    expect(source).toContain(':retryable="assistRetryable"')
    expect(source).toContain('@retry="retryAssist"')
    expect(source).toContain('@closed="closeAssistPanel"')
  })

  it('采纳时在当前脑图树执行节点操作并打 AI 标识，不重载整棵脑图', () => {
    expect(source).toContain('useCaseOptimizeExecution')
    expect(source).toContain('executeOperations: (operations, caseNodeId) => caseOptimizeExecution.execute(operations, caseNodeId)')
    expect(source).not.toContain('onConfirmed')
    expect(source).toContain('persistence.flushPersistenceNow')
  })
})

describe('CaseMindMap · 脑图加载失败反馈（用例页 F4）', () => {
  it('加载失败时编辑区展示错误占位与重试，不展示误导空白画布', () => {
    expect(source).toContain('loadError')
    expect(source).toContain('mindmap-load-error')
    expect(source).toContain('v-if="loadError"')
    expect(source).toContain('<template v-else>')
    expect(source).toContain('description="脑图加载失败"')
    expect(source).toContain('@click="initMinder()"')
  })
})

describe('CaseMindMap · 用例节点悬浮窗（交互 04 §1.2）', () => {
  it('单击用例节点弹出悬浮窗并关闭右键菜单', () => {
    expect(source).toContain('onNodeClick: handleNodeClick')
    expect(source).toContain('closeContextMenu()')
    expect(source).toContain("node.data.type !== 'case'")
    expect(source).toContain('caseNodePopover.open(nodeId')
    expect(source).toContain('<CaseNodeActionPopover')
  })

  it('悬浮窗复用同一任务入口与关联选取器，已关联仍可重新打开', () => {
    expect(source).toContain('@ai-optimize="openOptimizeFromPopover"')
    expect(source).toContain('const nodeId = caseNodePopover.targetNodeId.value')
    expect(source).toContain('void launchOptimize(nodeId)')
    expect(source).toContain('@link-requirement="openRequirementFromPopover"')
    expect(source).toContain('void requirementLink.open()')
    expect(source).not.toContain('requirementLinked')
    expect(source).not.toContain('requirementDisabled')
    expect(source).not.toContain('如需修改请在顶部工具条操作')
  })
})

describe('CaseMindMap · 编辑下拉节点操作', () => {
  it('下拉项使用新文案，快捷键右对齐且删除项带警示色', () => {
    expect(source).toContain('<span>新建下级节点</span><span class="menu-shortcut">Tab</span>')
    expect(source).toContain('<span>新建同级节点</span><span class="menu-shortcut">Enter</span>')
    expect(source).toContain('<span>删除节点</span><span class="menu-shortcut">Delete</span>')
    expect(source).toContain('toolbar-menu-item toolbar-menu-item--danger')
    expect(source).toContain('.menu-shortcut {\n  margin-left: auto;')
  })

  it('删除项危险色在全局作用域按 popper 类命中 EP 下拉项', () => {
    expect(source).toContain('popper-class="case-edit-dropdown"')
    expect(source).toContain('<style lang="scss">')
    expect(source).toContain('.case-edit-dropdown {')
    expect(source).toContain('.el-dropdown-menu__item.toolbar-menu-item--danger {')
    expect(source).toContain('color: menu.$minder-menu-danger-color;')
  })

  it('节点操作在编辑下拉与全屏工具栏均按选中态置灰', () => {
    expect(source).toContain('const hasSelectedNode = computed(() => Boolean(selectedNodeId.value))')
    expect(source.match(/:disabled="!hasSelectedNode"/g) ?? []).toHaveLength(14)
  })

  it('类型标记按钮按选中态置灰', () => {
    for (const type of ['case', 'precondition', 'step', 'expected']) {
      expect(source).toContain(`:disabled="!hasSelectedNode" @click="nodeOps.markAs('${type}')"`)
    }
    expect(source).toContain(':disabled="!hasSelectedNode" @click="nodeOps.clearMark"')
  })

  it('优先级按钮按选中态置灰，右键等级组同步置灰', () => {
    expect(source).not.toContain('hasSelectedCaseNode')
    expect(source).toMatch(/:disabled="!hasSelectedNode"\s+@click="nodeOps\.markPriority\(p\)"/)
    expect(source).toContain("'is-selected': nodeOps.selectedPriority.value === p, 'is-disabled': !hasSelectedNode")
    expect(source).toContain('&.is-disabled { opacity: 0.45; cursor: not-allowed; pointer-events: none; }')
  })
})

describe('CaseMindMap · 全屏菜单挂载点', () => {
  it('全屏时右键菜单与悬浮窗挂载到脑图容器，非全屏回落 body', () => {
    expect(source).toContain('const mindmapContainerRef = ref<HTMLElement | null>(null)')
    expect(source).toContain("isFullscreen.value && mindmapContainerRef.value ? mindmapContainerRef.value : 'body'")
    expect(source).toContain('<div ref="mindmapContainerRef" v-loading="loading" class="mindmap-container">')
    expect(source.match(/:to="menuTeleportTarget"/g) ?? []).toHaveLength(2)
  })
})
