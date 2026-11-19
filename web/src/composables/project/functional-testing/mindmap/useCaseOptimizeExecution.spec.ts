// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { useCaseOptimizeExecution } from './useCaseOptimizeExecution'
import { UUID_RE } from '@/minder/adapter'
import type { Minder, MinderNode } from '@/minder/types'
import type { AiCaseOptimizeOperation } from '@/types'

class FakeNode {
  data: Record<string, unknown>
  children: FakeNode[] = []

  constructor(data: Record<string, unknown>) {
    this.data = data
  }

  setData(key: string, value: unknown): this {
    this.data[key] = value
    return this
  }

  getChildren(): MinderNode[] {
    return this.children as unknown as MinderNode[]
  }

  traverse(fn: (node: MinderNode) => void): void {
    fn(this as unknown as MinderNode)
    this.children.forEach((child) => child.traverse(fn))
  }
}

function setup(data: Record<string, unknown>[] = [{ id: 'case-1', text: '登录用例', type: 'case' }]) {
  const nodes = data.map((item) => new FakeNode(item))
  nodes[0].children = nodes.slice(1)
  const fire = vi.fn()
  const refresh = vi.fn()
  const createNode = vi.fn(
    (nodeData: Record<string, unknown>, parent: MinderNode, index?: number) => {
      const child = new FakeNode(nodeData)
      const siblings = parent.getChildren() as unknown as FakeNode[]
      siblings.splice(index ?? siblings.length, 0, child)
      return child as unknown as MinderNode
    },
  )
  const minder = {
    getRoot: () => nodes[0] as unknown as MinderNode,
    fire,
    refresh,
    createNode,
  } as unknown as Minder
  const flush = vi.fn()
  const executor = useCaseOptimizeExecution(() => minder, flush)
  return { nodes, minder, fire, refresh, createNode, flush, executor }
}

describe('useCaseOptimizeExecution', () => {
  it('按 nodeId 原地更新标题 / 优先级 / 标签，ID 与层级不变', () => {
    const { nodes, fire, refresh, flush, executor } = setup([
      { id: 'case-1', text: '登录用例', type: 'case', priority: 'P1' },
      { id: 'step-1', text: '输入邮箱', type: 'step' },
    ])
    const ops: AiCaseOptimizeOperation[] = [
      { op: 'update', nodeId: 'case-1', field: 'title', existing: ['登录用例'], suggested: ['登录（邮箱）'], path: '', reason: '' },
      { op: 'update', nodeId: 'case-1', field: 'priority', existing: ['P1'], suggested: ['P0'], path: '', reason: '' },
      { op: 'update', nodeId: 'case-1', field: 'tags', existing: [], suggested: ['登录', '高优'], path: '', reason: '' },
    ]

    const result = executor.execute(ops)

    expect(result).toEqual({ updated: 3, added: 0, marked: 0, skipped: 0 })
    expect(nodes[0].data).toMatchObject({
      id: 'case-1', text: '登录（邮箱）', priority: 'P0', tags: ['登录', '高优'],
    })
    expect(nodes[0].data['id']).toBe('case-1')
    expect(nodes[0].getChildren()).toHaveLength(1)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(fire).toHaveBeenCalledWith('contentchange')
    expect(flush).toHaveBeenCalledTimes(1)
  })

  it('按 parentId 在任意节点下新增子节点，写入 UUID 与 AI 标识', () => {
    const { nodes, createNode, executor } = setup([
      { id: 'case-1', text: '登录用例', type: 'case' },
      { id: 'step-1', text: '输入邮箱', type: 'step' },
    ])
    const ops: AiCaseOptimizeOperation[] = [
      { op: 'add', parentId: 'step-1', nodeType: 'expected', title: '邮箱校验', path: '', reason: '' },
    ]

    const result = executor.execute(ops)

    expect(result).toEqual({ updated: 0, added: 1, marked: 0, skipped: 0 })
    expect(createNode).toHaveBeenCalledTimes(1)
    const [data, parent, index] = createNode.mock.calls[0]
    expect(parent.getChildren).toBeDefined()
    expect(data).toMatchObject({ text: '邮箱校验', type: 'expected', aiGenerated: true })
    expect(UUID_RE.test(data['id'] as string)).toBe(true)
    expect(index).toBe(0)
    expect(nodes[1].children).toHaveLength(1)
  })

  it('目标节点或父节点缺失时跳过该指令，不触发落库', () => {
    const { fire, flush, executor } = setup()
    const ops: AiCaseOptimizeOperation[] = [
      { op: 'update', nodeId: 'missing', field: 'title', existing: [], suggested: ['x'], path: '', reason: '' },
      { op: 'add', parentId: 'missing', nodeType: 'step', title: 'x', path: '', reason: '' },
    ]

    const result = executor.execute(ops)

    expect(result).toEqual({ updated: 0, added: 0, marked: 0, skipped: 2 })
    expect(fire).not.toHaveBeenCalled()
    expect(flush).not.toHaveBeenCalled()
  })

  it('脑图未就绪时全部跳过', () => {
    const executor = useCaseOptimizeExecution(() => null, vi.fn())
    const ops: AiCaseOptimizeOperation[] = [
      { op: 'update', nodeId: 'case-1', field: 'title', existing: [], suggested: ['x'], path: '', reason: '' },
    ]

    expect(executor.execute(ops)).toEqual({ updated: 0, added: 0, marked: 0, skipped: 1 })
  })

  it('部分成功部分跳过时仍触发一次落库', () => {
    const { fire, flush, executor } = setup()
    const ops: AiCaseOptimizeOperation[] = [
      { op: 'update', nodeId: 'case-1', field: 'title', existing: [], suggested: ['新标题'], path: '', reason: '' },
      { op: 'add', parentId: 'missing', nodeType: 'step', title: 'x', path: '', reason: '' },
    ]

    const result = executor.execute(ops)

    expect(result).toEqual({ updated: 1, added: 0, marked: 0, skipped: 1 })
    expect(fire).toHaveBeenCalledTimes(1)
    expect(flush).toHaveBeenCalledTimes(1)
  })

  it('按提案锚点给被优化的用例节点打 AI 标识，可与操作同批落库', () => {
    const { nodes, fire, flush, executor } = setup([
      { id: 'case-1', text: '登录用例', type: 'case' },
      { id: 'step-1', text: '输入邮箱', type: 'step' },
    ])
    const ops: AiCaseOptimizeOperation[] = [
      { op: 'add', parentId: 'step-1', nodeType: 'expected', title: '邮箱校验', path: '', reason: '' },
    ]

    const result = executor.execute(ops, 'case-1')

    expect(result).toEqual({ updated: 0, added: 1, marked: 1, skipped: 0 })
    expect(nodes[0].data['aiGenerated']).toBe(true)
    expect(nodes[1].data['aiGenerated']).toBeUndefined()
    expect(fire).toHaveBeenCalledWith('contentchange')
    expect(flush).toHaveBeenCalledTimes(1)
  })

  it('仅打标也触发落库，锚点节点缺失时跳过打标', () => {
    const { fire, flush, executor } = setup()

    expect(executor.execute([], 'case-1')).toEqual({ updated: 0, added: 0, marked: 1, skipped: 0 })
    expect(fire).toHaveBeenCalledTimes(1)
    expect(flush).toHaveBeenCalledTimes(1)

    const missing = setup()
    expect(missing.executor.execute([], '不存在的节点')).toEqual({
      updated: 0, added: 0, marked: 0, skipped: 1,
    })
    expect(missing.fire).not.toHaveBeenCalled()
    expect(missing.flush).not.toHaveBeenCalled()
  })
})
