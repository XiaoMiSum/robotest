import { describe, it, expect } from 'vitest'
import { caseNodeToKm, reviewNodeToKm, planNodeToKm, uuidv7, UUID_RE } from './adapter'
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
  it('递归映射 id/text/type/priority', () => {
    expect(caseNodeToKm(caseTree)).toEqual({
      data: { id: 'n1', text: '登录成功', type: 'case', priority: 'P1' },
      children: [
        {
          data: { id: 'n2', text: '输入账号密码', type: 'step', priority: null },
          children: [],
        },
      ],
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
    expect(km.data).toMatchObject({ id: 'r1', lastMark: 'pass', reviewStatus: { result: 'pass' } })
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
    expect(km.data).toMatchObject({ id: 'p1', lastResult: 'fail', executionStatus: { result: 'fail' } })
  })

  it('lastResult 为空时 executionStatus 为 null', () => {
    const km = planNodeToKm({ ...node, lastResult: null })
    expect(km.data).toMatchObject({ lastResult: null, executionStatus: null })
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
