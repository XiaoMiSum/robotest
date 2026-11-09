import { describe, expect, it } from 'vitest'
import type { AiTaskDetail, AiTaskExecutionItem } from '@/types'
import {
  AI_TASK_STATUS_META,
  aiExecutionStatusMeta,
  aiExecutionSummary,
  aiPlanSteps,
  aiProposalConfirmMeta,
  aiProposalKindCounts,
  aiProposalKindLabel,
  aiProposalProcessed,
  aiTaskName,
  aiTaskStatusMeta,
  aiTaskTypeMeta,
  aiTaskTypeOptions,
  aiTraceEventLabel,
  importVersionEvidence,
  importVersionHint,
  importVersionPrefill,
} from './taskPresentation'

function makeExecution(overrides: Partial<AiTaskExecutionItem> = {}): AiTaskExecutionItem {
  return {
    executionId: 'e1',
    seq: 1,
    status: 'running',
    steps: 0,
    budget: { maxSteps: 4 },
    consumed: { durationMs: 0, tokensIn: 0, tokensOut: 0, cost: 0 },
    plan: null,
    terminateReason: null,
    ...overrides,
  }
}

function makeTask(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 't1',
    executionId: 'e1',
    type: 'requirement_import',
    status: 'running',
    budget: { maxSteps: 4 },
    latestExecution: makeExecution(),
    submittedBy: null,
    tokensIn: 0,
    tokensOut: 0,
    createdAt: '2026-10-03T00:00:00Z',
    terminateReason: null,
    resultMeta: null,
    documentMeta: null,
    proposals: null,
    ...overrides,
  }
}

describe('taskPresentation', () => {
  describe('任务状态徽标', () => {
    it('四态映射到中文标签与标签类型', () => {
      expect(aiTaskStatusMeta('running')).toEqual({ label: '执行中', tagType: 'primary' })
      expect(aiTaskStatusMeta('proposals_ready')).toEqual({ label: '待采纳', tagType: 'warning' })
      expect(aiTaskStatusMeta('closed')).toEqual({ label: '已结束', tagType: 'info' })
      expect(aiTaskStatusMeta('aborted')).toEqual({ label: '已终止', tagType: 'danger' })
      expect(AI_TASK_STATUS_META.closed.tagType).toBe('info')
    })

    it('执行挂起时任务徽标降级为「挂起」', () => {
      expect(aiTaskStatusMeta('running', 'waiting_confirmation')).toEqual({
        label: '挂起',
        tagType: 'info',
      })
      expect(aiTaskStatusMeta('running', 'running').label).toBe('执行中')
    })

    it('未知状态降级为原文展示', () => {
      expect(aiTaskStatusMeta('unknown').label).toBe('unknown')
      expect(aiTaskStatusMeta('unknown').tagType).toBe('info')
    })
  })

  describe('执行状态徽标', () => {
    it('执行四态映射中文标签，未知降级原文', () => {
      expect(aiExecutionStatusMeta('running').label).toBe('执行中')
      expect(aiExecutionStatusMeta('waiting_confirmation').label).toBe('挂起')
      expect(aiExecutionStatusMeta('succeeded').tagType).toBe('success')
      expect(aiExecutionStatusMeta('aborted').tagType).toBe('danger')
      expect(aiExecutionStatusMeta('future').label).toBe('future')
    })
  })

  describe('任务名与类型', () => {
    it('任务名按类型派生', () => {
      expect(aiTaskName({ type: 'requirement_import' })).toBe('需求导入')
      expect(aiTaskName({ type: 'vector_reindex' })).toBe('向量索引重建')
    })

    it('未知类型降级为原文与「其他」域', () => {
      expect(aiTaskTypeMeta('future_type')).toEqual({ label: 'future_type', domain: '其他' })
      expect(aiTaskName({ type: 'future_type' })).toBe('future_type')
    })

    it('类型筛选选项带能力域后缀', () => {
      const options = aiTaskTypeOptions()
      expect(options.length).toBeGreaterThanOrEqual(13)
      expect(options.find((item) => item.value === 'requirement_import')?.label).toBe(
        '需求导入（需求）',
      )
    })
  })

  describe('执行摘要（列表行）', () => {
    it('执行中给步数与最近动作并附步数进度', () => {
      const summary = aiExecutionSummary(
        makeTask({
          latestExecution: makeExecution({
            steps: 2,
            plan: { steps: ['读取需求', '生成提示词', '模型生成', '落库校验'] },
          }),
        }),
      )
      expect(summary.stepPercent).toBe(50)
      expect(summary.text).toBe('2/4 步 · 模型生成')
    })

    it('执行中无预算不渲染进度条', () => {
      const summary = aiExecutionSummary(
        makeTask({ latestExecution: makeExecution({ steps: 3, budget: null }) }),
      )
      expect(summary.stepPercent).toBeNull()
      expect(summary.text).toBe('3 步')
    })

    it('挂起给待人工批准，成功给完成步数', () => {
      expect(
        aiExecutionSummary(makeTask({ latestExecution: makeExecution({ status: 'waiting_confirmation' }) })).text,
      ).toBe('0/4 步 · 待人工批准')
      expect(
        aiExecutionSummary(makeTask({ latestExecution: makeExecution({ status: 'succeeded', steps: 4 }) })).text,
      ).toBe('完成 · 4/4 步')
    })

    it('任务已终止优先展示终止原因，无执行给占位', () => {
      const summary = aiExecutionSummary(
        makeTask({
          status: 'aborted',
          terminateReason: { code: 1000018127, msg: '用户终止' },
          latestExecution: null,
        }),
      )
      expect(summary.text).toBe('用户终止')
      expect(summary.stepPercent).toBeNull()
      expect(aiExecutionSummary(makeTask({ latestExecution: null })).text).toBe('—')
    })
  })

  describe('计划步骤时间线', () => {
    const plan = { steps: ['读取需求', '生成提示词', '模型生成', '落库校验'] }

    it('成功态全部完成', () => {
      const steps = aiPlanSteps(
        makeExecution({ status: 'succeeded', steps: 4, plan }),
      )
      expect(steps).toHaveLength(4)
      expect(steps.every((step) => step.state === 'done')).toBe(true)
    })

    it('执行中已完成打勾、当前高亮、其后待执行', () => {
      const steps = aiPlanSteps(makeExecution({ steps: 2, plan }))
      expect(steps.map((step) => step.state)).toEqual(['done', 'done', 'current', 'pending'])
    })

    it('终止时当前步标红', () => {
      const steps = aiPlanSteps(makeExecution({ status: 'aborted', steps: 1, plan }))
      expect(steps.map((step) => step.state)).toEqual(['done', 'aborted', 'pending', 'pending'])
    })

    it('无计划快照返回空降级不渲染', () => {
      expect(aiPlanSteps(makeExecution({ plan: null }))).toEqual([])
      expect(aiPlanSteps(null)).toEqual([])
      expect(aiPlanSteps(makeExecution({ plan: { steps: [] } }))).toEqual([])
    })
  })

  describe('轨迹事件', () => {
    it('事件类型映射中文标签，未知降级原文', () => {
      expect(aiTraceEventLabel('plan')).toBe('计划')
      expect(aiTraceEventLabel('ai_decision')).toBe('AI 决策')
      expect(aiTraceEventLabel('proposal_emitted')).toBe('产出提案')
      expect(aiTraceEventLabel('future')).toBe('future')
    })
  })

  describe('提案', () => {
    it('按 kind 分组计数并映射中文标签', () => {
      const counts = aiProposalKindCounts([
        { kind: 'module_suggestion' },
        { kind: 'module_suggestion' },
        { kind: 'mindmap_document_suggestion' },
        { kind: 'custom_kind' },
      ])
      expect(counts).toEqual([
        { kind: 'module_suggestion', label: '模块', count: 2 },
        { kind: 'mindmap_document_suggestion', label: '文档', count: 1 },
        { kind: 'custom_kind', label: 'custom_kind', count: 1 },
      ])
      expect(aiProposalKindLabel('test_case_suggestion')).toBe('用例')
    })

    it('空清单返回空计数', () => {
      expect(aiProposalKindCounts(null)).toEqual([])
      expect(aiProposalKindCounts([])).toEqual([])
    })

    it('已处理数排除待确认', () => {
      expect(
        aiProposalProcessed([
          { confirmStatus: 'pending' },
          { confirmStatus: 'adopted' },
          { confirmStatus: 'rejected' },
        ]),
      ).toEqual({ processed: 2, total: 3 })
      expect(aiProposalProcessed(null)).toEqual({ processed: 0, total: 0 })
    })

    it('确认状态映射中文标签，未知降级原文', () => {
      expect(aiProposalConfirmMeta('pending').label).toBe('待确认')
      expect(aiProposalConfirmMeta('adopted_edited').label).toBe('已采纳')
      expect(aiProposalConfirmMeta('rejected').tagType).toBe('info')
      expect(aiProposalConfirmMeta('future').label).toBe('future')
    })
  })

  describe('导入识别版本', () => {
    const detected = { detectedVersion: 'V2.3', versionEvidence: '见第 2 章 V2.3' }
    const missing = { detectedVersion: null, versionEvidence: null }

    it('识别到版本返回预填值，识别不到返回空串', () => {
      expect(importVersionPrefill(detected)).toBe('V2.3')
      expect(importVersionPrefill(missing)).toBe('')
      expect(importVersionPrefill(null)).toBe('')
    })

    it('识别依据引语带前缀，缺失不渲染', () => {
      expect(importVersionEvidence(detected)).toBe('识别依据：见第 2 章 V2.3')
      expect(importVersionEvidence(missing)).toBe('')
      expect(importVersionEvidence(null)).toBe('')
    })

    it('导入未触碰按识别态提示预填或补录', () => {
      expect(
        importVersionHint({ taskType: 'requirement_import', documentMeta: detected, touched: false, value: '' }),
      ).toBe('已预填文档识别版本，可修改、可清空')
      expect(
        importVersionHint({ taskType: 'requirement_import', documentMeta: missing, touched: false, value: '' }),
      ).toBe('未识别到版本，采纳后可手工补录')
    })

    it('拆分未触碰提示继承原条目版本', () => {
      expect(
        importVersionHint({ taskType: 'requirement_split', documentMeta: null, touched: false, value: '' }),
      ).toBe('留空将继承原条目版本，可修改、可清空')
    })

    it('触碰后按当前值提示，空白串为显式清空', () => {
      expect(
        importVersionHint({ taskType: 'requirement_import', documentMeta: detected, touched: true, value: ' V9 ' }),
      ).toBe('采纳时使用当前版本值')
      expect(
        importVersionHint({ taskType: 'requirement_import', documentMeta: detected, touched: true, value: '  ' }),
      ).toBe('采纳后版本留空，可手工补录')
    })
  })
})
