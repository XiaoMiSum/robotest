import { describe, expect, it } from 'vitest'
import source from './PlanMindMap.vue?raw'

describe('PlanMindMap · 操作入口收拢到用例节点悬浮窗', () => {
  it('不保留顶部工具栏与自定义右键菜单', () => {
    expect(source).not.toContain('mindmap-toolbar')
    expect(source).not.toContain('MinderContextMenu')
    expect(source).not.toContain('useContextMenu')
    expect(source).not.toContain('onContextMenu')
    expect(source).toContain('@contextmenu.prevent')
  })

  it('由节点点击驱动悬浮窗，仅 case 节点展开并按渲染框定位', () => {
    expect(source).toContain('useNodeActionPopover')
    expect(source).toContain('onNodeClick: handleNodeClick')
    expect(source).toContain("node.data.type !== 'case'")
    expect(source).toContain("getRenderBox?.(undefined, 'screen')")
    expect(source).toContain('nodePopover.open(')
    expect(source).toContain('nodePopover.close()')
  })

  it('悬浮窗提供四态标记与移除，入口为透明背景 link 按钮', () => {
    expect(source).toContain('<NodeActionPopover')
    expect(source.match(/<el-button[^>]*\blink\b[^>]*>/g) ?? []).toHaveLength(5)
    expect(source).toContain("markFromPopover('pass')")
    expect(source).toContain("markFromPopover('fail')")
    expect(source).toContain("markFromPopover('block')")
    expect(source).toContain("markFromPopover('untested')")
    expect(source).toContain('removeFromPopover')
    expect(source).toContain(':disabled="!canRemove"')
    expect(source).toContain('--el-button-text-color')
    expect(source).toContain('--color-blocked')
  })

  it('重载画布前收起浮层，避免定位到过期节点', () => {
    expect(source).toContain('function reload(): void')
    expect(source).toContain('nodePopover.close()')
    expect(source).toContain('defineExpose({ openBug, reload, focusCase })')
  })

  it('执行引导按快照 id 定位并高亮，跨文档重载后再应用', () => {
    expect(source).toContain('executionOrder: ReadonlyMap<string, number>')
    expect(source).toContain('executionOrder: computed(() => props.executionOrder)')
    expect(source).toContain('root.traverse(')
    expect(source).toContain("m.select?.(node, true)")
    expect(source).toContain("m.execCommand?.('camera', node, 600)")
    expect(source).toContain('void initMinder().then(applyPendingFocus)')
  })
})
