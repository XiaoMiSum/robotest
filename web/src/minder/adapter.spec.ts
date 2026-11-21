import { describe, it, expect } from 'vitest'
import {
  buildPlanExecutionOrder,
  caseNodeToKm,
  planExecutionOrderMap,
  planNodeToKm,
  reviewNodeToKm,
  uuidv7,
  UUID_RE,
} from './adapter'
import type { TestCaseNode, TestReviewSnapshotNode, TestPlanSnapshotNode } from '@/types'

const caseTree: TestCaseNode = {
  id: 'n1',
  parentId: null,
  type: 'case',
  title: '登录成功',
  priority: 'P1',
  sortOrder: 0,
  version: 1,
  children: [
    {
      id: 'n2',
      parentId: 'n1',
      type: 'step',
      title: '输入账号密码',
      priority: null,
      sortOrder: 0,
      version: 1,
      children: [],
    },
  ],
}

describe('caseNodeToKm', () => {
  it('递归映射 id/text/type/priority/aiGenerated/requirementId', () => {
    expect(caseNodeToKm(caseTree)).toEqual({
      data: { id: 'n1', text: '登录成功', type: 'case', priority: 'P1', aiGenerated: false, requirementId: null },
      children: [
        {
          data: { id: 'n2', text: '输入账号密码', type: 'step', priority: null, aiGenerated: false, requirementId: null },
          children: [],
        },
      ],
    })
  })

  it('AI 标识与关联需求映射到脑图节点 data', () => {
    expect(caseNodeToKm({ ...caseTree, aiGenerated: true, requirementId: 'r1' })).toMatchObject({
      data: { aiGenerated: true, requirementId: 'r1' },
    })
  })
})

describe('reviewNodeToKm', () => {
  const node: TestReviewSnapshotNode = {
    id: 'r1',
    originalNodeId: 'n1',
    parentId: null,
    title: '登录成功',
    type: 'case',
    priority: 'P1',
    isAssociated: true,
    lastMark: 'pass',
    lastReviewerId: null,
    lastReviewedAt: null,
    sortOrder: 0,
    children: [],
  }

  it('lastMark 存在时生成 reviewStatus', () => {
    const km = reviewNodeToKm(node)
    expect(km.data).toMatchObject({ id: 'r1', originalNodeId: 'n1', lastMark: 'pass', reviewStatus: { result: 'pass' } })
  })

  it('lastMark 为空时 reviewStatus 为 null', () => {
    const km = reviewNodeToKm({ ...node, lastMark: null })
    expect(km.data).toMatchObject({ lastMark: null, reviewStatus: null })
  })
})

describe('planNodeToKm', () => {
  const node: TestPlanSnapshotNode = {
    id: 'p1',
    originalNodeId: 'n1',
    parentId: null,
    title: '登录成功',
    type: 'case',
    priority: 'P0',
    isAssociated: true,
    lastResult: 'fail',
    lastExecutorId: null,
    lastExecutedAt: null,
    sortOrder: 0,
    children: [],
  }

  it('lastResult 存在时生成 executionStatus', () => {
    const km = planNodeToKm(node)
    expect(km.data).toMatchObject({ id: 'p1', originalNodeId: 'n1', lastResult: 'fail', executionStatus: { result: 'fail' } })
  })

  it('lastResult 为空时 executionStatus 为 null', () => {
    const km = planNodeToKm({ ...node, lastResult: null })
    expect(km.data).toMatchObject({ lastResult: null, executionStatus: null })
  })

  it('按序号映射写入 executionOrder，非关联节点为 null', () => {
    const child: TestPlanSnapshotNode = { ...node, id: 'p2', type: 'step', isAssociated: false }
    const tree: TestPlanSnapshotNode = { ...node, children: [child] }
    const km = planNodeToKm(tree, new Map([['p1', 3]])) as {
      data: Record<string, unknown>
      children: Array<{ data: Record<string, unknown> }>
    }
    expect(km.data.executionOrder).toBe(3)
    expect(km.children[0].data.executionOrder).toBeNull()
  })
})

describe('buildPlanExecutionOrder', () => {
  const caseNode = (id: string, title: string, lastResult: TestPlanSnapshotNode['lastResult'] = null): TestPlanSnapshotNode => ({
    id, originalNodeId: `orig-${id}`, parentId: null, title, type: 'case', priority: 'P1',
    isAssociated: true, lastResult, lastExecutorId: null, lastExecutedAt: null, sortOrder: 0, children: [],
  })

  it('跨文档按 DFS 现序编号，仅关联用例入选', () => {
    const unassociated: TestPlanSnapshotNode = { ...caseNode('x', '未关联'), isAssociated: false }
    const nestedDoc: TestPlanSnapshotNode = {
      ...caseNode('doc', '文档'), type: 'normal', isAssociated: false,
      children: [caseNode('c2', '用例二', 'pass')],
    }
    const cases = buildPlanExecutionOrder([caseNode('c1', '用例一'), unassociated, nestedDoc])
    expect(cases.map((c) => [c.snapshotNodeId, c.order])).toEqual([['c1', 1], ['c2', 2]])
    expect(cases[0]).toMatchObject({ originalNodeId: 'orig-c1', title: '用例一', lastResult: null })
    expect(cases[1].lastResult).toBe('pass')
  })

  it('序号映射由快照 id 索引', () => {
    const cases = buildPlanExecutionOrder([caseNode('c1', '用例一'), caseNode('c2', '用例二')])
    expect(planExecutionOrderMap(cases)).toEqual(new Map([['c1', 1], ['c2', 2]]))
  })
})

describe('uuidv7', () => {
  it('符合 UUID 格式且版本位为 7、变体位为 RFC4122', () => {
    const id = uuidv7()
    expect(id).toMatch(UUID_RE)
    expect(id[14]).toBe('7')
    expect(['8', '9', 'a', 'b']).toContain(id[19])
  })

  it('时间有序：后生成的 UUID 时间戳前缀不小于先生成的', () => {
    const a = uuidv7()
    const b = uuidv7()
    expect(b.slice(0, 8) >= a.slice(0, 8)).toBe(true)
  })

  it('kityminder 短随机 id 不匹配 UUID_RE', () => {
    expect(UUID_RE.test('dk9uc1zn6q00')).toBe(false)
  })
})
