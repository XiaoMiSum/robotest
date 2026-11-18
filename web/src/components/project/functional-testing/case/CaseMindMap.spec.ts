import { describe, expect, it } from 'vitest'
import source from './CaseMindMap.vue?raw'

describe('CaseMindMap · 工具条单行排版（交互 04 §1.2）', () => {
  it('非全屏收纳脑图编辑功能，全屏恢复全平铺', () => {
    expect(source).toContain('fullscreenchange')
    expect(source).toContain('const isFullscreen = ref(false)')
    expect(source).toContain('<template v-if="!isFullscreen">')
    expect(source).toContain('<el-dropdown @command="handleEditCommand">')
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

  it('发起后在对话框内覆盖进行中 / 终止 / 加载失败 / 对照面板四态', () => {
    expect(source).toContain('v-if="assistRunning"')
    expect(source).toContain('AI 正在生成建议')
    expect(source).toContain('v-else-if="assistFailed"')
    expect(source).toContain('再次执行')
    expect(source).toContain('v-else-if="assistLoadError"')
    expect(source).toContain('<AssistComparePanel')
    expect(source).toContain(':can-adopt="canAdoptAssist"')
    expect(source).toContain('@closed="closeAssistPanel"')
  })

  it('采纳成功后重载脑图同步落库结果', () => {
    expect(source).toContain('onConfirmed: () => void initMinder()')
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
    expect(source).toContain('@ai-optimize="openAssistFromPopover(launchOptimize)"')
    expect(source).toContain('@link-requirement="openRequirementFromPopover"')
    expect(source).toContain('void requirementLink.open()')
    expect(source).not.toContain('requirementLinked')
    expect(source).not.toContain('requirementDisabled')
    expect(source).not.toContain('如需修改请在顶部工具条操作')
  })
})
