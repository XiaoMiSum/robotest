// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProjectModule, TraceNodeType } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchProjectModuleTree: vi.fn(),
  fetchRequirements: vi.fn(),
  fetchReviews: vi.fn(),
  fetchPlans: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  fetchRequirements: mocks.fetchRequirements,
  fetchReviews: mocks.fetchReviews,
  fetchPlans: mocks.fetchPlans,
}))

import { useTraceNodePicker } from './useTraceNodePicker'

function makeTree(): ProjectModule[] {
  return [
    {
      id: 'd1',
      parentId: null,
      type: 'directory',
      name: '登录模块',
      sortOrder: 1,
      children: [
        {
          id: 'f1',
          parentId: 'd1',
          type: 'document',
          name: '登录用例文档',
          sortOrder: 1,
          children: [
            {
              id: 'd2',
              parentId: 'f1',
              type: 'directory',
              name: '验证码子模块',
              sortOrder: 1,
              children: [],
            },
          ],
        },
      ],
    },
    {
      id: 'f2',
      parentId: null,
      type: 'document',
      name: '结算文档',
      sortOrder: 2,
      children: [],
    },
  ]
}

function setup(): void {
  mocks.fetchRequirements.mockResolvedValue({
    list: [{ id: 'r1', code: 'REQ-001', title: '登录验证码' }],
    total: 1,
  })
  mocks.fetchProjectModuleTree.mockResolvedValue(makeTree())
  mocks.fetchReviews.mockResolvedValue({
    list: [{ id: 'rev1', title: 'V1 登录评审' }],
    total: 1,
  })
  mocks.fetchPlans.mockResolvedValue({
    list: [{ id: 'p1', name: 'V1 回归计划' }],
    total: 1,
  })
}

describe('useTraceNodePicker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setup()
  })

  describe('候选加载', () => {
    it('requirement 按关键字请求并映射 code + title', async () => {
      const s = useTraceNodePicker()
      await s.load('requirement', '登录')
      expect(mocks.fetchRequirements).toHaveBeenCalledWith({
        keyword: '登录',
        pageNo: 1,
        pageSize: 50,
      })
      expect(s.options.value).toEqual([{ id: 'r1', label: 'REQ-001 登录验证码' }])
      expect(s.loading.value).toBe(false)
    })

    it('requirement 空关键字传 undefined 由服务端省略条件', async () => {
      const s = useTraceNodePicker()
      await s.load('requirement')
      expect(mocks.fetchRequirements).toHaveBeenCalledWith({
        keyword: undefined,
        pageNo: 1,
        pageSize: 50,
      })
    })

    it('module 只收目录节点并按关键字过滤', async () => {
      const s = useTraceNodePicker()
      await s.load('module')
      expect(mocks.fetchProjectModuleTree).toHaveBeenCalledTimes(1)
      expect(mocks.fetchRequirements).not.toHaveBeenCalled()
      expect(s.options.value).toEqual([
        { id: 'd1', label: '登录模块' },
        { id: 'd2', label: '验证码子模块' },
      ])

      await s.load('module', '验证码')
      expect(s.options.value).toEqual([{ id: 'd2', label: '验证码子模块' }])
      expect(s.loading.value).toBe(false)
    })

    it('mindmap_document 只收文档节点', async () => {
      const s = useTraceNodePicker()
      await s.load('mindmap_document')
      expect(s.options.value).toEqual([
        { id: 'f1', label: '登录用例文档' },
        { id: 'f2', label: '结算文档' },
      ])
    })

    it('test_review 取评审列表 title 作为标签', async () => {
      const s = useTraceNodePicker()
      await s.load('test_review', '登录')
      expect(mocks.fetchReviews).toHaveBeenCalledWith({
        keyword: '登录',
        pageNo: 1,
        pageSize: 50,
      })
      expect(s.options.value).toEqual([{ id: 'rev1', label: 'V1 登录评审' }])
      expect(s.loading.value).toBe(false)
    })

    it('test_plan 取计划列表 name 作为标签', async () => {
      const s = useTraceNodePicker()
      await s.load('test_plan', '回归')
      expect(mocks.fetchPlans).toHaveBeenCalledWith({
        keyword: '回归',
        pageNo: 1,
        pageSize: 50,
      })
      expect(s.options.value).toEqual([{ id: 'p1', label: 'V1 回归计划' }])
      expect(s.loading.value).toBe(false)
    })

    it('test_case 无列表接口不发请求', async () => {
      const s = useTraceNodePicker()
      await s.load('test_case')
      expect(mocks.fetchProjectModuleTree).not.toHaveBeenCalled()
      expect(mocks.fetchRequirements).not.toHaveBeenCalled()
      expect(mocks.fetchReviews).not.toHaveBeenCalled()
      expect(mocks.fetchPlans).not.toHaveBeenCalled()
      expect(s.options.value).toEqual([])
      expect(s.loading.value).toBe(false)
    })

    it('未知节点类型走 else 兜底空列表', async () => {
      const s = useTraceNodePicker()
      // TS 枚举上不可表达该取值，断言进入源码 else 分支
      const unknownType = 'unknown_kind' as unknown as TraceNodeType
      await s.load(unknownType)
      expect(s.options.value).toEqual([])
      expect(mocks.fetchRequirements).not.toHaveBeenCalled()
      expect(s.loading.value).toBe(false)
    })

    it('接口失败时保持下拉可用', async () => {
      mocks.fetchRequirements.mockRejectedValueOnce(new Error('网络异常'))
      const s = useTraceNodePicker()
      await s.load('requirement')
      expect(s.options.value).toEqual([])
      expect(s.loading.value).toBe(false)
    })
  })

  describe('reset 与 isSearchable', () => {
    it('reset 清空候选', async () => {
      const s = useTraceNodePicker()
      await s.load('requirement')
      expect(s.options.value).toHaveLength(1)
      s.reset()
      expect(s.options.value).toEqual([])
    })

    it('isSearchable 仅列表接口类型支持关键字', () => {
      const s = useTraceNodePicker()
      expect(s.isSearchable('requirement')).toBe(true)
      expect(s.isSearchable('test_review')).toBe(true)
      expect(s.isSearchable('test_plan')).toBe(true)
      expect(s.isSearchable('module')).toBe(false)
      expect(s.isSearchable('mindmap_document')).toBe(false)
      expect(s.isSearchable('test_case')).toBe(false)
    })
  })
})
