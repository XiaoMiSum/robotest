import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearClipboard,
  cloneTree,
  copySelected,
  cutSelected,
  exportSubtree,
  hasClipboard,
  pasteToSelected,
  type ClipboardTree,
} from './clipboard'
import type { Minder, MinderNode } from './types'

interface FakeNode {
  data: Record<string, unknown>
  getChildren(): FakeNode[]
}

function fakeNode(data: Record<string, unknown>, children: FakeNode[] = []): FakeNode {
  return { data, getChildren: () => children }
}

describe('clipboard', () => {
  beforeEach(() => {
    clearClipboard()
  })

  it('exportSubtree 剥离 id 与布局偏移，保留业务字段', () => {
    const node = fakeNode({
      id: 'old-uuid',
      text: '登录用例',
      type: 'case',
      priority: 'P1',
      layout_default_offset: { x: 10, y: 20 },
    })
    const tree = exportSubtree(node)
    expect(tree.data).toEqual({ text: '登录用例', type: 'case', priority: 'P1' })
  })

  it('exportSubtree 递归导出子树结构', () => {
    const node = fakeNode({ id: 'a', text: '模块' }, [
      fakeNode({ id: 'b', text: '用例', type: 'case', priority: 'P2' }, [
        fakeNode({ id: 'c', text: '步骤', type: 'step' }),
      ]),
    ])
    const tree = exportSubtree(node)
    expect(tree.children).toHaveLength(1)
    expect(tree.children[0].data).toEqual({ text: '用例', type: 'case', priority: 'P2' })
    expect(tree.children[0].children[0].data).toEqual({ text: '步骤', type: 'step' })
  })

  it('cloneTree 深拷贝互不影响', () => {
    const source: ClipboardTree = {
      data: { text: '原始' },
      children: [{ data: { text: '子' }, children: [] }],
    }
    const clone = cloneTree(source)
    clone.data.text = '改动'
    clone.children[0].data.text = '子改动'
    expect(source.data.text).toBe('原始')
    expect(source.children[0].data.text).toBe('子')
  })

  it('copySelected 无选中节点时返回 false 且不置位剪贴板', () => {
    const minder = { getSelectedNode: () => null } as unknown as Minder
    expect(copySelected(minder)).toBe(false)
    expect(hasClipboard.value).toBe(false)
  })

  it('cutSelected 根节点被拒绝：不动剪贴板、不执行删除', () => {
    const root = fakeNode({ id: 'root', text: '根' })
    const execCommand = vi.fn()
    const minder = {
      getSelectedNode: () => root,
      getRoot: () => root,
      execCommand,
    } as unknown as Minder
    expect(cutSelected(minder)).toBe('root')
    expect(hasClipboard.value).toBe(false)
    expect(execCommand).not.toHaveBeenCalled()
  })

  it('cutSelected 普通节点：置位剪贴板并执行 RemoveNode', () => {
    const root = fakeNode({ id: 'root', text: '根' })
    const node = fakeNode({ id: 'n1', text: '待剪切', type: 'case' })
    const execCommand = vi.fn()
    const minder = {
      getSelectedNode: () => node,
      getRoot: () => root,
      execCommand,
    } as unknown as Minder
    expect(cutSelected(minder)).toBe('ok')
    expect(hasClipboard.value).toBe(true)
    expect(execCommand).toHaveBeenCalledWith('RemoveNode')
  })

  it('pasteToSelected 空剪贴板时 no-op', () => {
    const target = fakeNode({ id: 't', text: '目标' })
    const minder = { getSelectedNode: () => target } as unknown as Minder
    expect(pasteToSelected(minder)).toBe(false)
  })

  it('复制后粘贴：新节点不带旧 id、还原父子结构并触发 contentchange', () => {
    const source = fakeNode({ id: 'src', text: '用例', type: 'case', priority: 'P0' }, [
      fakeNode({ id: 'src-child', text: '步骤', type: 'step' }),
    ])
    copySelected({ getSelectedNode: () => source } as unknown as Minder)

    const target = fakeNode({ id: 'target', text: '目标模块' })
    const created: { data: Record<string, unknown>; parent: FakeNode }[] = []
    const fired: string[] = []
    const pasteMinder = {
      getSelectedNode: () => target,
      createNode(data: Record<string, unknown>, parent: FakeNode) {
        const node = fakeNode(data)
        created.push({ data, parent })
        return node as unknown as MinderNode
      },
      select: vi.fn(),
      refresh: vi.fn(),
      fire: (type: string) => fired.push(type),
    } as unknown as Minder

    expect(pasteToSelected(pasteMinder)).toBe(true)
    expect(created).toHaveLength(2)
    expect(created[0].data).toEqual({ text: '用例', type: 'case', priority: 'P0' })
    expect(created[0].data.id).toBeUndefined()
    expect(created[0].parent).toBe(target)
    // 子节点应挂在刚创建的父节点下，而不是粘贴目标下
    expect(created[1].parent.data).toEqual({ text: '用例', type: 'case', priority: 'P0' })
    expect(fired).toContain('contentchange')
  })
})
