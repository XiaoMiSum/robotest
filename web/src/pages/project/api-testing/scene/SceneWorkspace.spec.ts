import { describe, expect, it } from 'vitest'
import workspaceSource from './SceneWorkspace.vue?raw'

describe('SceneWorkspace 页签条形态', () => {
  it('默认型页签 + 页签末尾虚线新建入口，不再有独立实心新建按钮', () => {
    expect(workspaceSource).toContain('addable')
    expect(workspaceSource).not.toContain('type="card"')
    expect(workspaceSource).toContain('@tab-add="handleCreate"')
    expect(workspaceSource).not.toContain('border-style: dashed')
    expect(workspaceSource).toContain('color: var(--color-neutral-400)')
    expect(workspaceSource).not.toContain('scene-create-btn')
  })
})
