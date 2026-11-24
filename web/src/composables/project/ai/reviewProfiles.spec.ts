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
        moduleName: '',
        newModule: false,
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
        moduleName: '',
        newModule: false,
        priority: 'high',
      })
    })

    it('导入产物保留模块名称与新增模块标记', () => {
      const profile = reviewProfile('requirement_import')
      const parsed = profile.parse(
        { title: '需求管理', moduleName: '需求管理', newModule: true },
        '兜底',
      )
      expect(parsed.editValues).toMatchObject({ moduleName: '需求管理', newModule: true })
      expect(parsed.fields).toContainEqual({ label: '新增模块', value: '是' })
      expect(profile.buildEditedContent(parsed.editValues, {})).toMatchObject({
        moduleName: '需求管理',
        newModule: true,
      })
    })

    it('缺省值回退到摘要标题且不提交空字段', () => {
      const profile = reviewProfile('requirement_split')
      const parsed = profile.parse({ name: '订单条目' }, '兜底')
      expect(parsed.editValues.title).toBe('订单条目')
      expect(profile.buildEditedContent(parsed.editValues, {})).toEqual({ title: '订单条目' })
    })

    it('导入审核只开放逐条动作，不提供批量审核', () => {
      const profile = reviewProfile('requirement_import')

      expect(profile.allowAdopt).toBe(true)
      expect(profile.allowEdit).toBe(true)
      expect(profile.allowReject).toBe(true)
      expect(profile.allowBatch).toBe(false)
    })
  })

  describe('用例优化（详情只读）', () => {
    it('不提供采纳 / 编辑 / 驳回动作，采纳在脑图面板执行', () => {
      const profile = reviewProfile('case_optimize')
      expect(isReadonlyProfile(profile)).toBe(true)
      expect(profile.editFields).toEqual([])
    })

    it('只读展示节点操作指令、来源引用与用例节点', () => {
      const profile = reviewProfile('case_optimize')
      const parsed = profile.parse(
        {
          nodeId: 'n1',
          operations: [
            {
              op: 'update', nodeId: 'n1', field: 'priority',
              existing: ['P1'], suggested: ['P0'], path: '登录用例', reason: '上游需求为高',
            },
            {
              op: 'add', parentId: 'n1', nodeType: 'step',
              title: '输入图形验证码', path: '登录用例', reason: '需求要求校验',
            },
          ],
          sourceRefs: [{ id: 'REQ-1', title: '需求一', quote: '原文' }],
        },
        '兜底',
      )
      expect(parsed.fields).toContainEqual({ label: '用例节点', value: 'n1' })
      expect(parsed.compare).toContainEqual({
        label: '优先级', existing: 'P1', suggested: 'P0', changed: true,
      })
      expect(parsed.compare).toContainEqual({
        label: '新增操作步骤', existing: '—', suggested: '新增：输入图形验证码', changed: true,
      })
      expect(parsed.sourceRefs).toEqual([
        { id: 'REQ-1', title: '需求一', quote: '原文', changed: false },
      ])
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

  describe('AI 评审', () => {
    it('任务详情只读展示，不提供采纳、编辑与驳回动作', () => {
      const profile = reviewProfile('review_case_review')

      expect(profile.title).toBe('AI 评审结果')
      expect(isReadonlyProfile(profile)).toBe(true)
      expect(profile.allowAdopt).toBe(false)
      expect(profile.allowEdit).toBe(false)
      expect(profile.allowReject).toBe(false)
      expect(profile.allowBatch).toBe(false)
      expect(profile.editFields).toEqual([])
      expect(profile.buildEditedContent({}, {})).toEqual({})
    })

    it('解析建议原因、问题清单与来源引用作为只读预览', () => {
      const profile = reviewProfile('review_case_review')
      const parsed = profile.parse(
        {
          reviewId: 'rv-1',
          snapshotNodeId: 'snap-1',
          caseTitle: '获取验证码',
          suggestedMark: 'fail',
          reason: '缺少错误分支预期',
          issues: [
            {
              severity: 'medium', category: '预期不完整',
              description: '未覆盖错误输入', path: '获取验证码 › 步骤 2',
            },
          ],
          sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '错误时提示' }],
        },
        '兜底',
      )

      expect(parsed.description).toBe('缺少错误分支预期')
      expect(parsed.fields).toContainEqual({ label: '用例', value: '获取验证码' })
      expect(parsed.fields).toContainEqual({ label: '建议标记', value: '不通过' })
      expect(parsed.fields.some((field) => field.label === '问题清单'
        && field.value.includes('medium · 预期不完整'))).toBe(true)
      expect(parsed.sourceRefs).toEqual([
        { id: 'r1', title: 'REQ-001', quote: '错误时提示', changed: false },
      ])
      expect(parsed.editValues).toEqual({ title: '兜底' })
    })
  })

  describe('只读与兜底', () => {
    it('解析类提案不提供确认动作', () => {
      for (const type of ['assistant_parse', 'vector_reindex']) {
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
  })
})
