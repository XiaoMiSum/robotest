import { describe, expect, it } from 'vitest'
import { isReadonlyProfile, reviewProfile } from './reviewProfiles'

describe('reviewProfiles', () => {
  describe('需求域', () => {
    it('解析标题、描述、模块与优先级作为编辑初值', () => {
      const profile = reviewProfile('requirement_import')
      const parsed = profile.parse(
        {
          title: '登录验证码',
          description: '支持图形验证码',
          moduleId: 'm1',
          priority: 'high',
          sourceRef: 'PRD §2.1',
        },
        '兜底标题',
      )
      expect(parsed.editValues).toEqual({
        title: '登录验证码',
        description: '支持图形验证码',
        moduleId: 'm1',
        priority: 'high',
      })
      expect(parsed.fields).toEqual([
        { label: '模块 ID', value: 'm1' },
        { label: '优先级', value: 'high' },
        { label: '来源引用', value: 'PRD §2.1' },
      ])
      expect(profile.buildEditedContent(parsed.editValues, {})).toEqual({
        title: '登录验证码',
        description: '支持图形验证码',
        moduleId: 'm1',
        priority: 'high',
      })
    })

    it('缺省值回退到摘要标题且不提交空字段', () => {
      const profile = reviewProfile('requirement_split')
      const parsed = profile.parse({ name: '订单条目' }, '兜底')
      expect(parsed.editValues.title).toBe('订单条目')
      expect(profile.buildEditedContent(parsed.editValues, {})).toEqual({ title: '订单条目' })
    })
  })

  describe('用例优化', () => {
    it('解析 existing/suggested 对照与来源引用，编辑初值取建议值', () => {
      const profile = reviewProfile('case_optimize')
      const parsed = profile.parse(
        {
          nodeId: 'n1',
          fields: {
            steps: { existing: ['点击登录'], suggested: ['点击登录', '点击验证码'] },
            expected: { existing: ['成功'], suggested: ['成功'] },
          },
          priority: { current: 'P1', suggested: 'P0', reason: '上游需求为高' },
          sourceRefs: [{ id: 'REQ-1', title: '需求一', quote: '原文' }],
        },
        '兜底',
      )
      expect(parsed.compare.find((row) => row.label === '操作步骤')).toMatchObject({
        existing: '点击登录',
        suggested: '点击登录、点击验证码',
        changed: true,
      })
      expect(parsed.compare.find((row) => row.label === '预期结果')?.changed).toBe(false)
      expect(parsed.compare.find((row) => row.label === '优先级')).toMatchObject({
        existing: 'P1',
        suggested: 'P0',
        changed: true,
      })
      expect(parsed.sourceRefs).toEqual([
        { id: 'REQ-1', title: '需求一', quote: '原文', changed: false },
      ])
      expect(parsed.editValues.priority).toBe('P0')
      expect((parsed.editValues.steps as string).split('\n')).toEqual(['点击登录', '点击验证码'])
    })

    it('编辑载荷按详设组装为 fields + priority，多行按行拆分', () => {
      const profile = reviewProfile('case_optimize')
      const content = profile.buildEditedContent(
        {
          precondition: '  已登录  ',
          steps: '步骤一\n步骤二\n',
          expected: '',
          tags: '登录',
          priority: 'P0',
        },
        {},
      )
      expect(content).toEqual({
        fields: {
          precondition: '已登录',
          steps: ['步骤一', '步骤二'],
          tags: ['登录'],
        },
        priority: 'P0',
      })
    })
  })

  describe('计划排序', () => {
    it('解析排序条目并按名次输出编辑初值', () => {
      const profile = reviewProfile('plan_order')
      const parsed = profile.parse(
        {
          planId: 'p1',
          items: [
            { nodeId: 'n2', caseTitle: 'TC-002', suggestedRank: 2, reason: '后置' },
            { nodeId: 'n1', caseTitle: 'TC-001', suggestedRank: 1, reason: '前置' },
          ],
          beforeOrder: ['TC-002', 'TC-001'],
          afterOrder: ['TC-001', 'TC-002'],
        },
        '兜底',
      )
      expect(parsed.rankItems.map((item) => item.nodeId)).toEqual(['n1', 'n2'])
      expect(parsed.compare).toEqual([
        { label: '第 1 位', existing: 'TC-002', suggested: 'TC-001', changed: true },
        { label: '第 2 位', existing: 'TC-001', suggested: 'TC-002', changed: true },
      ])
    })

    it('编辑载荷按当前顺序重排名次', () => {
      const profile = reviewProfile('plan_order')
      const content = profile.buildEditedContent(
        {
          items: [
            { nodeId: 'n2', title: 'TC-002' },
            { nodeId: 'n1', title: 'TC-001' },
          ],
        },
        {},
      )
      expect(content).toEqual({
        items: [
          { nodeId: 'n2', suggestedRank: 1 },
          { nodeId: 'n1', suggestedRank: 2 },
        ],
      })
    })
  })

  describe('只读与兜底', () => {
    it('分诊 / 总结 / 解析类提案不提供确认动作', () => {
      for (const type of ['bug_triage', 'bug_trend_summary', 'assistant_parse', 'vector_reindex']) {
        const profile = reviewProfile(type)
        expect(isReadonlyProfile(profile)).toBe(true)
        expect(profile.editFields).toEqual([])
        const parsed = profile.parse({ summary: '摘要', count: 3 }, '兜底')
        expect(parsed.description).toBe('摘要')
        expect(parsed.fields).toContainEqual({ label: 'count', value: '3' })
      }
    })

    it('未登记任务类型沿用通用审核能力', () => {
      const profile = reviewProfile('future_task')
      expect(profile.title).toBe('提案审核')
      expect(profile.allowEdit).toBe(true)
      expect(profile.needSystemVersion).toBe(false)
    })

    it('按任务类型指定审核面板形态', () => {
      expect(reviewProfile('test_design_generation').panel).toBe('tree')
      expect(reviewProfile('bug_classify').panel).toBe('bug')
      expect(reviewProfile('bug_duplicate_scan').panel).toBe('bug')
      expect(reviewProfile('case_optimize').panel).toBe('generic')
      expect(reviewProfile('future_task').panel).toBe('generic')
    })
  })
})
