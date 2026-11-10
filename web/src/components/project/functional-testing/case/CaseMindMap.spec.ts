import { describe, expect, it } from 'vitest'
import source from './CaseMindMap.vue?raw'

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
