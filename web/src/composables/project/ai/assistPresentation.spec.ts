import { describe, expect, it } from 'vitest'
import {
  caseOptimizeOperationFoldable,
  caseOptimizeOperationLabel,
  caseOptimizeOperationUnchanged,
  isCaseOptimizeSuggestion,
  isPlanOrderSuggestion,
  isReviewSuggestion,
  planOrderMovedCount,
  readAssistSuggestion,
  readCaseOptimizeOperations,
  readPlanOrder,
  readReview,
  readWireOperations,
} from './assistPresentation'

describe('assistPresentation', () => {
  describe('readCaseOptimizeOperations（脑图操作提案）', () => {
    it('保留 update / add 指令与定位字段，兼容字符串与列表值', () => {
      const view = readCaseOptimizeOperations({
        nodeId: 'n1',
        operations: [
          {
            op: 'update', nodeId: 'n1', field: 'title',
            existing: '输入邮箱', suggested: '输入邮箱和密码',
            path: '登录用例 › 步骤 1', reason: '补充账号类型',
          },
          {
            op: 'update', nodeId: 'n1', field: 'tags',
            existing: ['登录'], suggested: ['登录', '验证码'],
            path: '登录用例', reason: '补充标签',
          },
          {
            op: 'add', parentId: 'n1', nodeType: 'expected',
            title: '发送成功提示', path: '登录用例', reason: '补充预期',
          },
        ],
        sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '验证码 60 秒重发' }],
      })

      expect(view.nodeId).toBe('n1')
      expect(view.operations).toHaveLength(3)
      expect(view.operations[0]).toMatchObject({
        op: 'update', field: 'title',
        existing: ['输入邮箱'], suggested: ['输入邮箱和密码'],
      })
      expect(view.operations[1]).toMatchObject({ field: 'tags', suggested: ['登录', '验证码'] })
      expect(view.operations[2]).toMatchObject({
        op: 'add', parentId: 'n1', nodeType: 'expected', title: '发送成功提示',
      })
      expect(view.sourceRefs).toEqual([
        { id: 'r1', title: 'REQ-001', quote: '验证码 60 秒重发' },
      ])
    })

    it('丢弃非法字段 / 节点类型 / 缺定位字段的指令', () => {
      const view = readCaseOptimizeOperations({
        operations: [
          { op: 'update', nodeId: 'n1', field: 'steps', suggested: 'x' },
          { op: 'update', field: 'title', suggested: 'x' },
          { op: 'add', parentId: 'n1', nodeType: 'case', title: 'x' },
          { op: 'add', parentId: 'n1', nodeType: 'step', title: '' },
          { op: 'unknown', nodeId: 'n1' },
        ],
      })

      expect(view.operations).toEqual([])
    })

    it('operations 非数组时给空清单', () => {
      expect(readCaseOptimizeOperations({ operations: '畸形' }).operations).toEqual([])
    })
  })

  describe('readWireOperations（编辑后采纳载荷）', () => {
    it('只保留采纳执行所需的定位与建议值，忽略现状 / 理由', () => {
      const ops = readWireOperations({
        operations: [
          {
            op: 'update', nodeId: 'n1', field: 'priority',
            existing: 'P1', suggested: 'P0', path: '登录用例', reason: '优先级高',
          },
          { op: 'add', parentId: 'n1', nodeType: 'step', title: '输入密码', path: '登录用例' },
        ],
      })

      expect(ops).toHaveLength(2)
      expect(ops[0]).toMatchObject({ op: 'update', nodeId: 'n1', field: 'priority', suggested: ['P0'] })
      expect(ops[1]).toMatchObject({ op: 'add', parentId: 'n1', nodeType: 'step', title: '输入密码' })
    })
  })

  describe('readPlanOrder', () => {
    it('按后端顺序保留条目，非数值名次排尾', () => {
      const view = readPlanOrder({
        planId: 'p1',
        items: [
          { nodeId: 'n2', caseTitle: 'B', suggestedRank: 2, reason: '核心链路' },
          { nodeId: 'n1', caseTitle: 'A', suggestedRank: 1, reason: '阻塞面最广' },
          { nodeId: 'n3', caseTitle: 'C', suggestedRank: 'x' },
          { caseTitle: '缺 nodeId 丢弃' },
        ],
        beforeOrder: ['A', 'B'],
        afterOrder: ['A', 'B', 'C'],
      })

      expect(view.planId).toBe('p1')
      expect(view.items.map((item) => item.nodeId)).toEqual(['n2', 'n1', 'n3'])
      expect(view.items[0].reason).toBe('核心链路')
      expect(view.items[2].suggestedRank).toBe(Number.MAX_SAFE_INTEGER)
      expect(view.beforeOrder).toEqual(['A', 'B'])
      expect(view.afterOrder).toEqual(['A', 'B', 'C'])
    })

    it('清单非数组时给空清单', () => {
      expect(readPlanOrder({ planId: 'p1', items: '畸形' }).items).toEqual([])
    })
  })

  describe('readReview（AI 评审建议）', () => {
    it('保留建议标记、理由、问题清单与来源引用', () => {
      const view = readReview({
        reviewId: 'rv-1',
        snapshotNodeId: 'snap-1',
        caseTitle: '获取验证码',
        suggestedMark: 'fail',
        reason: '缺少错误分支预期',
        issues: [
          {
            severity: 'high', category: '预期不完整',
            description: '未覆盖错误输入', path: '获取验证码 › 步骤 2',
          },
        ],
        sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '错误时提示' }],
      })

      expect(view).toMatchObject({
        reviewId: 'rv-1',
        snapshotNodeId: 'snap-1',
        caseTitle: '获取验证码',
        suggestedMark: 'fail',
        reason: '缺少错误分支预期',
      })
      expect(view.issues).toEqual([{
        severity: 'high', category: '预期不完整',
        description: '未覆盖错误输入', path: '获取验证码 › 步骤 2',
      }])
      expect(view.sourceRefs).toEqual([{ id: 'r1', title: 'REQ-001', quote: '错误时提示' }])
    })

    it('丢弃非法严重度，非法标记回退通过，清单非数组给空数组', () => {
      const view = readReview({
        suggestedMark: 'pending',
        issues: [
          { severity: 'critical', category: '非法' },
          { severity: 'low', category: '低' },
        ],
        sourceRefs: '畸形',
      })

      expect(view.suggestedMark).toBe('pass')
      expect(view.issues.map((issue) => issue.severity)).toEqual(['low'])
      expect(view.sourceRefs).toEqual([])
    })
  })

  describe('readAssistSuggestion 与类型守卫', () => {
    it('按任务类型分发，content 为空返回 null', () => {
      expect(readAssistSuggestion('case_optimize', null)).toBeNull()
      expect(isCaseOptimizeSuggestion(
        readAssistSuggestion('case_optimize', { operations: [], nodeId: 'n1' }),
      )).toBe(true)
      expect(isPlanOrderSuggestion(
        readAssistSuggestion('plan_order', { beforeOrder: [], afterOrder: [] }),
      )).toBe(true)
      expect(isReviewSuggestion(
        readAssistSuggestion('review_case_review', { suggestedMark: 'pass' }),
      )).toBe(true)
    })

    it('守卫对 null 与异类视图返回 false', () => {
      const order = readPlanOrder({ planId: 'p1' })
      expect(isCaseOptimizeSuggestion(order)).toBe(false)
      expect(isCaseOptimizeSuggestion(null)).toBe(false)
      expect(isPlanOrderSuggestion(null)).toBe(false)
      expect(isReviewSuggestion(order)).toBe(false)
      expect(isReviewSuggestion(null)).toBe(false)
    })
  })

  describe('派生展示', () => {
    it('更新值与现状相同视为无变化，新增始终为变更', () => {
      expect(caseOptimizeOperationUnchanged({
        op: 'update', nodeId: 'n1', field: 'title',
        existing: ['同名'], suggested: ['同名'], path: '', reason: '',
      })).toBe(true)
      expect(caseOptimizeOperationUnchanged({
        op: 'update', nodeId: 'n1', field: 'tags',
        existing: ['登录'], suggested: ['登录', '验证码'], path: '', reason: '',
      })).toBe(false)
      expect(caseOptimizeOperationUnchanged({
        op: 'add', parentId: 'n1', nodeType: 'step',
        title: '新步骤', path: '', reason: '',
      })).toBe(false)
    })

    it('仅同值标题更新折叠，同级 / 标签一致照常展示', () => {
      expect(caseOptimizeOperationFoldable({
        op: 'update', nodeId: 'n1', field: 'title',
        existing: ['同名'], suggested: ['同名'], path: '', reason: '',
      })).toBe(true)
      expect(caseOptimizeOperationFoldable({
        op: 'update', nodeId: 'n1', field: 'priority',
        existing: ['P1'], suggested: ['P1'], path: '', reason: '',
      })).toBe(false)
      expect(caseOptimizeOperationFoldable({
        op: 'update', nodeId: 'n1', field: 'tags',
        existing: ['登录'], suggested: ['登录'], path: '', reason: '',
      })).toBe(false)
    })

    it('操作卡标签区分更新字段与新增类型', () => {
      expect(caseOptimizeOperationLabel({
        op: 'update', nodeId: 'n1', field: 'priority',
        existing: ['P1'], suggested: ['P0'], path: '', reason: '',
      })).toBe('优先级')
      expect(caseOptimizeOperationLabel({
        op: 'add', parentId: 'n1', nodeType: 'expected',
        title: '新预期', path: '', reason: '',
      })).toBe('新增预期结果')
    })

    it('顺序变化按位置统计', () => {
      expect(planOrderMovedCount(['A', 'B', 'C'], ['A', 'B', 'C'])).toBe(0)
      expect(planOrderMovedCount(['A', 'B', 'C'], ['B', 'C', 'A'])).toBe(3)
      expect(planOrderMovedCount(['A', 'B'], ['A', 'B', 'C'])).toBe(1)
    })
  })
})
