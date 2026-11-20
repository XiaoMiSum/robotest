// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { useCaseGenerationInsert } from './useCaseGenerationInsert'
import { UUID_RE } from '@/minder/adapter'
import type { Minder, MinderNode } from '@/minder/types'
import type { CaseNodeType, TestCaseNode } from '@/types'

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
}

function node(id: string, title: string, type: CaseNodeType, priority: string | null = null): TestCaseNode {
  return { id, parentId: null, type, title, priority, sortOrder: 0, version: 1, children: [] }
}

function setup() {
  const root = new FakeNode({ id: 'root', text: '文档根节点', type: 'normal' })
  const fire = vi.fn()
  const refresh = vi.fn()
  const createNode = vi.fn(
    (data: Record<string, unknown>, parent: MinderNode, index?: number) => {
      const child = new FakeNode(data)
      const siblings = parent.getChildren() as unknown as FakeNode[]
      siblings.splice(index ?? siblings.length, 0, child)
      return child as unknown as MinderNode
    },
  )
  const minder = { getRoot: () => root as unknown as MinderNode, fire, refresh, createNode } as unknown as Minder
  const flush = vi.fn()
  return { root, minder, fire, refresh, createNode, flush, executor: useCaseGenerationInsert(() => minder, flush) }
}

describe('useCaseGenerationInsert', () => {
  it('在根节点下创建用例与前置/步骤/预期子节点并走两段落库', () => {
    const { root, createNode, fire, refresh, flush, executor } = setup()
    const priority = node('p1', '先登录', 'precondition')
    const step = node('s1', '输入手机号', 'step')
    const expected = node('e1', '提示已发送', 'expected')
    const source = node('case-1', '获取验证码', 'case', 'P0')
    source.children = [priority, step, expected]

    const result = executor.execute([{ key: 'k1', node: source, tags: ['登录'] }])

    expect(result.inserted).toBe(1)
    expect(result.skipped).toBe(0)
    expect(result.caseIds).toHaveLength(1)
    expect(UUID_RE.test(result.caseIds[0]!)).toBe(true)
    expect(createNode).toHaveBeenCalledTimes(4)
    const [caseData, parent, index] = createNode.mock.calls[0]
    expect(parent).toBe(root)
    expect(index).toBe(0)
    expect(caseData).toMatchObject({ text: '获取验证码', type: 'case', priority: 'P0' })
    expect(UUID_RE.test(caseData['id'] as string)).toBe(true)
    expect(root.children).toHaveLength(1)
    expect(root.children[0].children.map((c) => c.data['text'])).toEqual(['先登录', '输入手机号', '提示已发送'])
    expect(root.children[0].children.every((c) => c.data['aiGenerated'] === true)).toBe(true)
    expect(root.children[0].data).toMatchObject({ tags: ['登录'], aiGenerated: true })
    expect(root.children[0].data['id']).toBe(result.caseIds[0])
    expect(refresh).toHaveBeenCalledTimes(2)
    expect(fire).toHaveBeenCalledTimes(2)
    expect(fire).toHaveBeenCalledWith('contentchange')
    expect(flush).toHaveBeenCalledTimes(2)
  })

  it('优先级缺省回落 P2，多个提案顺序追加', () => {
    const { root, createNode, executor } = setup()
    const result = executor.execute([
      { key: 'k1', node: node('a', '用例 A', 'case'), tags: [] },
      { key: 'k2', node: node('b', '用例 B', 'case'), tags: [] },
    ])

    expect(result.inserted).toBe(2)
    expect(result.skipped).toBe(0)
    expect(result.caseIds).toHaveLength(2)
    expect(root.children).toHaveLength(2)
    expect(createNode.mock.calls[0][0]).toMatchObject({ priority: 'P2' })
    expect(createNode.mock.calls[1][2]).toBe(1)
  })

  it('脑图未就绪或无勾选项时不触发落库', () => {
    const empty = useCaseGenerationInsert(() => null, vi.fn())
    expect(empty.execute([{ key: 'k1', node: node('a', '用例 A', 'case'), tags: [] }])).toEqual({
      inserted: 0,
      skipped: 1,
      caseIds: [],
    })

    const { fire, flush, executor } = setup()
    expect(executor.execute([])).toEqual({ inserted: 0, skipped: 0, caseIds: [] })
    expect(fire).not.toHaveBeenCalled()
    expect(flush).not.toHaveBeenCalled()
  })
})
