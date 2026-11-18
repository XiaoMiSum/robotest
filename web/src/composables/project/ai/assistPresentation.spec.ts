import { describe, expect, it } from 'vitest'
import {
  assistFieldLabel,
  caseOptimizeChangedFields,
  caseOptimizeCompareRows,
  casePriorityChanged,
  isCaseOptimizeSuggestion,
  isPlanOrderSuggestion,
  planOrderMovedCount,
  readAssistSuggestion,
  readCaseOptimize,
  readPlanOrder,
} from './assistPresentation'

describe('assistPresentation', () => {
  describe('readCaseOptimize', () => {
    it('归一字段、级别建议与来源引用', () => {
      const view = readCaseOptimize({
        nodeId: 'n1',
        fields: {
          precondition: { existing: '手机号已输入', suggested: '手机号已输入并获取过一次验证码' },
          steps: { existing: ['点击获取验证码'], suggested: ['点击获取验证码', '60 秒内再次点击'] },
          expected: { existing: [], suggested: ['发送成功提示'] },
          tags: { existing: ['登录'], suggested: ['登录', '验证码'] },
        },
        priority: { current: 'P1', suggested: 'P0', reason: '上游需求优先级为高' },
        sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '验证码 60 秒重发' }],
      })

      expect(view.nodeId).toBe('n1')
      expect(view.fields.precondition).toEqual({
        existing: ['手机号已输入'],
        suggested: ['手机号已输入并获取过一次验证码'],
      })
      expect(view.fields.steps.suggested).toEqual(['点击获取验证码', '60 秒内再次点击'])
      expect(view.priority).toEqual({
        current: 'P1',
        suggested: 'P0',
        reason: '上游需求优先级为高',
      })
      expect(view.sourceRefs).toEqual([
        { id: 'r1', title: 'REQ-001', quote: '验证码 60 秒重发' },
      ])
    })

    it('结构缺字段时给空值而非猜测', () => {
      const view = readCaseOptimize({ nodeId: 'n1' })

      expect(view.fields).toEqual({
        precondition: { existing: [], suggested: [] },
        steps: { existing: [], suggested: [] },
        expected: { existing: [], suggested: [] },
        tags: { existing: [], suggested: [] },
      })
      expect(view.priority).toEqual({ current: '', suggested: '', reason: '' })
      expect(view.sourceRefs).toEqual([])
    })

    it('丢弃无 id 的来源引用', () => {
      const view = readCaseOptimize({
        nodeId: 'n1',
        sourceRefs: [{ title: '缺 id' }, '畸形项'],
      })

      expect(view.sourceRefs).toEqual([])
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

  describe('readAssistSuggestion 与类型守卫', () => {
    it('按任务类型分发，content 为空返回 null', () => {
      expect(readAssistSuggestion('case_optimize', null)).toBeNull()
      expect(isCaseOptimizeSuggestion(
        readAssistSuggestion('case_optimize', { fields: {}, priority: {} }),
      )).toBe(true)
      expect(isPlanOrderSuggestion(
        readAssistSuggestion('plan_order', { beforeOrder: [], afterOrder: [] }),
      )).toBe(true)
    })

    it('守卫对 null 与异类视图返回 false', () => {
      const order = readPlanOrder({ planId: 'p1' })
      expect(isCaseOptimizeSuggestion(order)).toBe(false)
      expect(isCaseOptimizeSuggestion(null)).toBe(false)
      expect(isPlanOrderSuggestion(null)).toBe(false)
    })
  })

  describe('对照与变化派生', () => {
    it('字段标签按渲染口径给出', () => {
      expect(assistFieldLabel('precondition')).toBe('前置条件')
      expect(assistFieldLabel('tags')).toBe('标签')
    })

    it('只返回与现有内容不同的字段', () => {
      const view = readCaseOptimize({
        fields: {
          precondition: { existing: '手机号已输入', suggested: '手机号已输入' },
          steps: { existing: ['点击获取验证码'], suggested: ['点击获取验证码', '60 秒内再次点击'] },
          expected: { existing: [], suggested: [] },
          tags: { existing: ['登录'], suggested: ['登录', '验证码'] },
        },
      })

      expect(caseOptimizeChangedFields(view.fields)).toEqual(['steps', 'tags'])
    })

    it('对照行保持字段顺序并标注变化', () => {
      const view = readCaseOptimize({
        fields: {
          precondition: { existing: [], suggested: [] },
          steps: { existing: [], suggested: ['点击获取验证码'] },
        },
      })
      const rows = caseOptimizeCompareRows(view.fields)

      expect(rows.map((row) => row.name)).toEqual([
        'precondition', 'steps', 'expected', 'tags',
      ])
      expect(rows.map((row) => row.changed)).toEqual([false, true, false, false])
      expect(rows[1].label).toBe('操作步骤')
    })

    it('级别建议按同级判定变化，无建议视为无变化', () => {
      const priorityOf = (suggested: string, current: string) => ({
        current,
        suggested,
        reason: '',
      })

      expect(casePriorityChanged(priorityOf('P0', 'P1'))).toBe(true)
      expect(casePriorityChanged(priorityOf('P1', 'P1'))).toBe(false)
      expect(casePriorityChanged(priorityOf('', 'P1'))).toBe(false)
    })

    it('顺序变化按位置统计', () => {
      expect(planOrderMovedCount(['A', 'B', 'C'], ['A', 'B', 'C'])).toBe(0)
      expect(planOrderMovedCount(['A', 'B', 'C'], ['B', 'C', 'A'])).toBe(3)
      expect(planOrderMovedCount(['A', 'B'], ['A', 'B', 'C'])).toBe(1)
    })
  })
})
