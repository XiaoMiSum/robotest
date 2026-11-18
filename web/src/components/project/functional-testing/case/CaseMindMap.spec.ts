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

describe('CaseMindMap · 关联需求入口', () => {
  it('关联需求工具条常驻，仅按选中节点类型置灰', () => {
    expect(source).toContain('<el-icon><Link /></el-icon><span>关联需求</span>')
    expect(source).toContain(':disabled="!canLinkRequirement"')
    expect(source).not.toContain('canManageRequirements')
    expect(source).not.toContain('requirement:view')
  })
})

describe('CaseMindMap · AI 辅助入口（详设 5.1 / 交互 2.3）', () => {
  it('工具栏提供 AI 补全与级别推荐，按 AI 可用性隐藏、按选中态置灰', () => {
    expect(source).toContain('AI 补全')
    expect(source).toContain('级别推荐')
    expect(source).toContain('v-if="assistEntryVisible"')
    expect(source).toContain(':disabled="!canLaunchAssist"')
    expect(source).toContain('useCaseAssistEntry')
    expect(source).toContain('documentId: () => props.docId')
    expect(source).toContain('selectedNodeId: () => selectedNodeId.value')
    expect(source).toContain('selectedType: () => selectedType.value')
    // 置灰原因常驻悬浮提示，不靠猜测（交互 2.1.2 动作置灰）
    expect(source).toContain(':content="assistGateReason')
  })

  it('发起后在对话框内覆盖进行中 / 终止 / 加载失败 / 对照面板四态', () => {
    expect(source).toContain('v-if="assistRunning"')
    expect(source).toContain('AI 正在生成建议')
    expect(source).toContain('v-else-if="assistFailed"')
    expect(source).toContain('再次执行')
    expect(source).toContain('v-else-if="assistLoadError"')
    expect(source).toContain('<AssistComparePanel')
    expect(source).toContain(':kind="assistKind"')
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
