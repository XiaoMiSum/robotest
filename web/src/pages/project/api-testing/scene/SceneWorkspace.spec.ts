import { describe, expect, it } from 'vitest'
import workspaceSource from './SceneWorkspace.vue?raw'

describe('SceneWorkspace 标签头形态', () => {
  it('直接沿用快速调试页标签头结构，不再用 el-tabs 覆盖样式', () => {
    expect(workspaceSource).not.toContain('<el-tabs')
    expect(workspaceSource).toContain('class="workspace-tabbar"')
    expect(workspaceSource).toContain('class="workspace-tabs"')
    expect(workspaceSource).toContain('class="workspace-tab"')
    expect(workspaceSource).toContain('class="workspace-tabbar__add"')
    expect(workspaceSource).toContain('padding: 4px 6px 0')
    expect(workspaceSource).toContain('height: 36px')
    expect(workspaceSource).toContain('padding: 0 12px')
    expect(workspaceSource).toContain('bottom: -1px')
    expect(workspaceSource).toContain('background: var(--color-primary-500)')
    expect(workspaceSource).not.toContain('scene-create-btn')
  })
})
