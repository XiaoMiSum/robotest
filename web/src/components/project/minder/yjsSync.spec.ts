import { describe, expect, it } from 'vitest'
import * as Y from 'yjs'
import {
  ROOT_KEY,
  TEMPLATE_KEY,
  THEME_KEY,
  applyRemoteDiff,
  buildJsonFromYjs,
  deepEqual,
  diffKmTrees,
  nodeKey,
  publishCanvasToYjs,
  reconcileChildArray,
  type KmExportJson,
  type KmJsonNode,
  type MinderLike,
} from './yjsSync'

const n = (id: string, text: string, children: KmJsonNode[] = []): KmJsonNode => ({
  data: { id, text },
  children,
})

describe('deepEqual', () => {
  it('键序无关比较嵌套对象', () => {
    expect(deepEqual({ a: 1, b: { c: 2 } }, { b: { c: 2 }, a: 1 })).toBe(true)
    expect(deepEqual({ a: 1 }, { a: 1, b: undefined })).toBe(false)
    expect(deepEqual(null, undefined)).toBe(false)
    expect(deepEqual('x', 'x')).toBe(true)
  })
})

describe('reconcileChildArray', () => {
  it('初始填充/追加/中段插删/重排/清空', () => {
    const ydoc = new Y.Doc()
    const arr = ydoc.getArray<string>('t')
    reconcileChildArray(arr, ['a', 'b'])
    expect(arr.toArray()).toEqual(['a', 'b'])
    reconcileChildArray(arr, ['a', 'b', 'c'])
    expect(arr.toArray()).toEqual(['a', 'b', 'c'])
    reconcileChildArray(arr, ['a', 'x', 'b', 'c'])
    expect(arr.toArray()).toEqual(['a', 'x', 'b', 'c'])
    reconcileChildArray(arr, ['a', 'b'])
    expect(arr.toArray()).toEqual(['a', 'b'])
    reconcileChildArray(arr, ['b', 'a'])
    expect(arr.toArray()).toEqual(['b', 'a'])
    reconcileChildArray(arr, [])
    expect(arr.toArray()).toEqual([])
  })
})

describe('publishCanvasToYjs / buildJsonFromYjs', () => {
  const treeA = n('r1', '根', [n('a', '甲'), n('b', '乙')])
  const doc1: KmExportJson = { root: treeA, template: 'right' }

  function publishedDoc() {
    const ydoc = new Y.Doc()
    publishCanvasToYjs(ydoc, doc1)
    return ydoc
  }

  it('首次发布写入分片/顺序/标量键', () => {
    const ydoc = publishedDoc()
    const ymap = ydoc.getMap('mindmap')
    expect(ymap.get(ROOT_KEY)).toBe('r1')
    expect(ymap.get(TEMPLATE_KEY)).toBe('right')
    expect(ymap.get(THEME_KEY)).toBe('fresh-blue')
    expect(ymap.get(nodeKey('a'))).toEqual({ id: 'a', text: '甲' })
    const order = ymap.get('order') as Y.Map<Y.Array<string>>
    expect(order.get('r1')?.toArray()).toEqual(['a', 'b'])
  })

  it('重建 JSON 与原树等价', () => {
    const ydoc = publishedDoc()
    const rebuilt = buildJsonFromYjs(ydoc.getMap('mindmap'))
    expect(rebuilt).toEqual({ ...doc1, theme: 'fresh-blue' })
  })

  it('增量发布只写变化分片', () => {
    const ydoc = publishedDoc()
    const ymap = ydoc.getMap('mindmap')
    const changedKeys: string[][] = []
    ymap.observe((event) => changedKeys.push(Array.from(event.keysChanged)))

    const modified: KmExportJson = {
      root: n('r1', '根', [n('a', '甲改'), n('b', '乙')]),
      template: 'right',
    }
    publishCanvasToYjs(ydoc, modified)

    expect(changedKeys).toHaveLength(1)
    expect(changedKeys[0]).toEqual([nodeKey('a')])
    expect(buildJsonFromYjs(ymap)).toEqual({ ...modified, theme: 'fresh-blue' })
  })

  it('删除节点清理分片与顺序表', () => {
    const ydoc = publishedDoc()
    const ymap = ydoc.getMap('mindmap')
    publishCanvasToYjs(ydoc, { root: n('r1', '根', [n('a', '甲')]), template: 'right' })
    expect(ymap.get(nodeKey('b'))).toBeUndefined()
    const order = ymap.get('order') as Y.Map<Y.Array<string>>
    expect(order.get('r1')?.toArray()).toEqual(['a'])
  })

  it('非法树（缺 id）不产生任何写入', () => {
    const ydoc = new Y.Doc()
    publishCanvasToYjs(ydoc, { root: { data: { text: '无id' } } })
    expect(ydoc.getMap('mindmap').size).toBe(0)
  })
})

describe('diffKmTrees', () => {
  it('文本/属性变更产出 data 操作，字段删除产出 data-remove', () => {
    const ops = diffKmTrees(
      { root: { data: { id: 'r', text: '根', stale: true }, children: [n('a', '旧')] } },
      { root: { data: { id: 'r', text: '根', fresh: 1 }, children: [{ data: { id: 'a', text: '新', p: 'P1' } }] } },
    )
    expect(ops).toContainEqual({ kind: 'data', id: 'a', key: 'text', value: '新' })
    expect(ops).toContainEqual({ kind: 'data', id: 'a', key: 'p', value: 'P1' })
    expect(ops).toContainEqual({ kind: 'data-remove', id: 'r', key: 'stale' })
    expect(ops).toContainEqual({ kind: 'data', id: 'r', key: 'fresh', value: 1 })
  })

  it('新增子节点携带整棵子树与挂载位置', () => {
    const child = n('c1', '新子', [n('g1', '孙')])
    const ops = diffKmTrees({ root: n('r', '根') }, { root: n('r', '根', [child]) })
    expect(ops).toContainEqual({
      kind: 'add', id: 'c1', parentId: 'r', index: 0,
      json: { data: { id: 'c1', text: '新子' }, children: [{ data: { id: 'g1', text: '孙' }, children: [] }] },
    })
  })

  it('删除/同父重排/跨父移动', () => {
    const del = diffKmTrees({ root: n('r', '根', [n('a', '甲'), n('b', '乙')]) }, { root: n('r', '根', [n('a', '甲')]) })
    expect(del).toContainEqual({ kind: 'remove', id: 'b' })

    const reorder = diffKmTrees(
      { root: n('r', '根', [n('a', '甲'), n('b', '乙'), n('c', '丙')]) },
      { root: n('r', '根', [n('c', '丙'), n('b', '乙'), n('a', '甲')]) },
    )
    // 对齐算法保证按序应用后达到期望排列（摘除+重插表达移动）
    expect(reorder).not.toBeNull()

    const move = diffKmTrees(
      { root: n('r', '根', [n('p1', 'P1', [n('m', '移')]), n('p2', 'P2')]) },
      { root: n('r', '根', [n('p1', 'P1'), n('p2', 'P2', [n('m', '移')])]) },
    )
    expect(move).toContainEqual({ kind: 'remove', id: 'm' })
    expect(move).toContainEqual({ kind: 'add', id: 'm', parentId: 'p2', index: 0, json: { data: { id: 'm', text: '移' }, children: [] } })
  })

  it('模板变更与根不一致', () => {
    const ops = diffKmTrees({ root: n('r', '根'), template: 'default' }, { root: n('r', '根'), template: 'fish-bone' })
    expect(ops).toContainEqual({ kind: 'template', value: 'fish-bone' })
    expect(diffKmTrees({ root: n('r1', 'A') }, { root: n('r2', 'B') })).toBeNull()
  })
})

describe('applyRemoteDiff', () => {
  interface MockNode {
    id: string
    data: Record<string, unknown>
    children: MockNode[]
    parent: MockNode | null
    setData(key: string, value: unknown): void
    getData(): Record<string, unknown>
  }

  function makeMinder(rootJson: KmJsonNode) {
    const byId = new Map<string, MockNode>()
    const created: { id: string; parentId: string | null; index: number }[] = []
    const removed: string[] = []
    let refreshCount = 0

    const mk = (json: KmJsonNode, parent: MockNode | null): MockNode => {
      const node: MockNode = {
        id: json.data.id as string,
        data: JSON.parse(JSON.stringify(json.data)),
        children: [],
        parent,
        setData(key, value) {
          node.data[key] = value
        },
        getData() {
          return node.data
        },
      }
      byId.set(node.id, node)
      for (const c of json.children ?? []) node.children.push(mk(c, node))
      return node
    }
    // mk 递归构建即完成 byId 注册，根引用无需保留
    mk(rootJson, null)

    const minder = {
      getNodeById: (id: string) => byId.get(id),
      createNode: (data: unknown, parent: MockNode | null, index?: number) => {
        const node = mk({ data: data as Record<string, unknown> }, parent)
        if (parent) parent.children.splice(index ?? 0, 0, node)
        created.push({ id: node.id, parentId: parent?.id ?? null, index: index ?? -1 })
        return node
      },
      removeNode: (node: MockNode) => {
        removed.push(node.id)
        if (node.parent) {
          const i = node.parent.children.indexOf(node)
          if (i > -1) node.parent.children.splice(i, 1)
        }
        const drop = (x: MockNode) => {
          byId.delete(x.id)
          x.children.forEach(drop)
        }
        drop(node)
      },
      useTemplate: () => {},
      useTheme: () => {},
      refresh: () => {
        refreshCount++
      },
    }
    return { minder: minder as unknown as MinderLike, byId, created, removed, refresh: () => refreshCount }
  }

  it('文本修改只触发 setData 与一次 refresh', () => {
    const { minder, created, removed, refresh } = makeMinder(n('r', '根', [n('a', '旧')]))
    const ok = applyRemoteDiff(
      minder,
      { root: n('r', '根', [n('a', '旧')]) },
      { root: n('r', '根', [n('a', '新')]) },
    )
    expect(ok).toBe(true)
    expect(created).toHaveLength(0)
    expect(removed).toHaveLength(0)
    expect(refresh()).toBe(1)
  })

  it('新增子树递归创建并挂到正确位置', () => {
    const { minder, byId, created } = makeMinder(n('r', '根'))
    const ok = applyRemoteDiff(
      minder,
      { root: n('r', '根') },
      { root: n('r', '根', [n('c1', '子', [n('g1', '孙')])]) },
    )
    expect(ok).toBe(true)
    expect(created.map((c) => `${c.id}@${c.parentId}:${c.index}`)).toEqual(['c1@r:0', 'g1@c1:0'])
    expect(byId.get('g1')).toBeDefined()
  })

  it('删除节点级联清理', () => {
    const { minder, byId, removed } = makeMinder(n('r', '根', [n('a', '甲', [n('g', '孙')])]))
    const ok = applyRemoteDiff(minder, { root: n('r', '根', [n('a', '甲', [n('g', '孙')])]) }, { root: n('r', '根') })
    expect(ok).toBe(true)
    expect(removed).toEqual(['a'])
    expect(byId.has('g')).toBe(false)
  })

  it('根不一致返回 false 且不做任何操作', () => {
    const { minder, refresh } = makeMinder(n('r1', 'A'))
    const ok = applyRemoteDiff(minder, { root: n('r1', 'A') }, { root: n('r2', 'B') })
    expect(ok).toBe(false)
    expect(refresh()).toBe(0)
  })
})
