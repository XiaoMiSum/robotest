import { describe, expect, it } from 'vitest'
import workspaceSource from './SceneWorkspace.vue?raw'

describe('SceneWorkspace 页签头形态', () => {
  it('整条标签头对齐快速调试：36px 页签、内缩下划线、无边框新建入口', () => {
    expect(workspaceSource).toContain('addable')
    expect(workspaceSource).not.toContain('type="card"')
    expect(workspaceSource).toContain('@tab-add="handleCreate"')
    expect(workspaceSource).toContain('padding: 4px 6px 0')
    expect(workspaceSource).toContain('height: 36px')
    expect(workspaceSource).toContain('padding: 0 12px')
    expect(workspaceSource).toContain('background: var(--color-primary-500)')
    expect(workspaceSource).not.toContain('border-style: dashed')
    expect(workspaceSource).toContain('height: 26px')
    expect(workspaceSource).toContain('color: var(--color-neutral-400)')
    expect(workspaceSource).not.toContain('scene-create-btn')
  })
})
