import { describe, expect, it } from 'vitest'
import type { TestPlanSnapshotNode } from '@/types'
import { collectPlanCaseMeta, resolveCaseTitle, scoreLabel, type PlanCaseMeta } from './planOrderRecommend'

const tree: TestPlanSnapshotNode[] = [
  {
    id: 'doc1',
    originalNodeId: null,
    parentId: null,
    title: '登录文档',
    type: 'normal',
    priority: null,
    isAssociated: true,
    lastResult: null,
    lastExecutorId: null,
    lastExecutedAt: null,
    sortOrder: 1,
    children: [
      {
        id: 'c1',
        originalNodeId: 'oc1',
        parentId: 'doc1',
        title: '余额不足',
        type: 'case',
        priority: 'P0',
        isAssociated: true,
        lastResult: null,
        lastExecutorId: null,
        lastExecutedAt: null,
        sortOrder: 1,
        children: [
          {
            id: 'step1',
            originalNodeId: 'os1',
            parentId: 'c1',
            title: '输入密码',
            type: 'step',
            priority: null,
            isAssociated: true,
            lastResult: null,
            lastExecutorId: null,
            lastExecutedAt: null,
            sortOrder: 1,
            children: [],
          },
        ],
      },
      {
        id: 'c2',
        originalNodeId: 'oc2',
        parentId: 'doc1',
        title: '密码错误',
        type: 'case',
        priority: 'P1',
        isAssociated: true,
        lastResult: null,
        lastExecutorId: null,
        lastExecutedAt: null,
        sortOrder: 2,
        children: [],
      },
    ],
  },
  {
    id: 'doc2',
    originalNodeId: null,
    parentId: null,
    title: '订单文档',
    type: 'normal',
    priority: null,
    isAssociated: true,
    lastResult: null,
    lastExecutorId: null,
    lastExecutedAt: null,
    sortOrder: 2,
    children: [
      {
        id: 'c3',
        originalNodeId: 'oc3',
        parentId: 'doc2',
        title: '超时取消',
        type: 'case',
        priority: 'P2',
        isAssociated: true,
        lastResult: null,
        lastExecutorId: null,
        lastExecutedAt: null,
        sortOrder: 1,
        children: [],
      },
    ],
  },
]

describe('collectPlanCaseMeta 快照树扁平化', () => {
  it('仅 case 节点入表，含标题与优先级，跨文档/跨层级生效', () => {
    const meta = collectPlanCaseMeta(tree)
    expect(meta.get('c1')).toEqual({ title: '余额不足', priority: 'P0' })
    expect(meta.get('c2')).toEqual({ title: '密码错误', priority: 'P1' })
    expect(meta.get('c3')).toEqual({ title: '超时取消', priority: 'P2' })
  })

  it('文档/步骤等非 case 节点不入表', () => {
    const meta = collectPlanCaseMeta(tree)
    expect(meta.has('doc1')).toBe(false)
    expect(meta.has('step1')).toBe(false)
  })

  it('空树返回空映射', () => {
    expect(collectPlanCaseMeta([]).size).toBe(0)
  })
})

describe('resolveCaseTitle 行内标题回退', () => {
  const meta: Map<string, PlanCaseMeta> = collectPlanCaseMeta(tree)

  it('映射命中返回用例标题', () => {
    expect(resolveCaseTitle(meta, 'c1')).toBe('余额不足')
  })

  it('映射缺失（快照已更新）回退展示节点 ID，避免空行', () => {
    expect(resolveCaseTitle(meta, 'gone')).toBe('gone')
  })
})

describe('scoreLabel 指数文案', () => {
  it('score ∈ [0,1] 放大为百分位整数', () => {
    expect(scoreLabel(0.92)).toBe('指数 92')
    expect(scoreLabel(0.74)).toBe('指数 74')
  })

  it('边界值四舍五入', () => {
    expect(scoreLabel(1)).toBe('指数 100')
    expect(scoreLabel(0)).toBe('指数 0')
  })
})
