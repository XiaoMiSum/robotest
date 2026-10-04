import { describe, expect, it } from 'vitest'
import type { AiTaskDetail, AiTaskStatus } from '@/types'
import {
  AI_TASK_STATUS_META,
  aiArtifactConfirmMeta,
  aiArtifactKindCounts,
  aiArtifactKindLabel,
  aiArtifactProcessed,
  aiTaskName,
  aiTaskPhaseSteps,
  aiTaskPhases,
  aiTaskStatusMeta,
  aiTaskTypeMeta,
  aiTaskTypeOptions,
} from './taskPresentation'

function makeTask(overrides: Partial<AiTaskDetail> = {}): AiTaskDetail {
  return {
    taskId: 't1',
    type: 'requirement_split',
    status: 'running' as AiTaskStatus,
    progress: 50,
    phase: '模型生成拆分建议',
    submittedBy: null,
    retryOfTaskId: null,
    tokensIn: 0,
    tokensOut: 0,
    createdAt: '2026-10-03T00:00:00Z',
    error: null,
    result: null,
    artifacts: null,
    ...overrides,
  }
}

describe('taskPresentation', () => {
  describe('状态徽标', () => {
    it('终态与进行态映射到中文标签与标签类型', () => {
      expect(aiTaskStatusMeta('pending').label).toBe('排队')
      expect(aiTaskStatusMeta('running').tagType).toBe('primary')
      expect(aiTaskStatusMeta('succeeded').tagType).toBe('success')
      expect(aiTaskStatusMeta('failed').tagType).toBe('danger')
      expect(aiTaskStatusMeta('cancelled').label).toBe('已取消')
      expect(AI_TASK_STATUS_META.pending.failed).toBeUndefined()
    })

    it('未知状态降级为原文展示', () => {
      expect(aiTaskStatusMeta('unknown').label).toBe('unknown')
      expect(aiTaskStatusMeta('unknown').tagType).toBe('info')
    })
  })

  describe('任务名与类型', () => {
    it('任务名按类型派生，重试任务追加后缀', () => {
      expect(aiTaskName({ type: 'requirement_split' })).toBe('需求拆分')
      expect(
        aiTaskName({ type: 'requirement_split', retryOfTaskId: 't0' }),
      ).toBe('需求拆分 ·重试')
    })

    it('未知类型降级为原文与「其他」域', () => {
      expect(aiTaskTypeMeta('future_type')).toEqual({ label: 'future_type', domain: '其他' })
      expect(aiTaskName({ type: 'future_type' })).toBe('future_type')
    })

    it('类型筛选选项带能力域后缀', () => {
      const options = aiTaskTypeOptions()
      expect(options.length).toBeGreaterThanOrEqual(16)
      expect(options.find((item) => item.value === 'requirement_split')?.label).toBe(
        '需求拆分（需求）',
      )
    })
  })

  describe('阶段时间线', () => {
    it('生成链与拆分各自持有阶段序列，未知类型返回空', () => {
      expect(aiTaskPhases('test_design_generation')).toHaveLength(6)
      expect(aiTaskPhases('requirement_split')).toHaveLength(4)
      expect(aiTaskPhases('vector_reindex')).toEqual([])
    })

    it('成功态全部完成', () => {
      const steps = aiTaskPhaseSteps(makeTask({ status: 'succeeded', phase: null }))
      expect(steps).toHaveLength(4)
      expect(steps.every((step) => step.state === 'done')).toBe(true)
    })

    it('进行中停在当前阶段，其后待执行', () => {
      const steps = aiTaskPhaseSteps(makeTask({ phase: '模型生成拆分建议' }))
      expect(steps.map((step) => step.state)).toEqual(['done', 'done', 'current', 'pending'])
    })

    it('失败态当前阶段标红', () => {
      const steps = aiTaskPhaseSteps(makeTask({ status: 'failed', phase: '构建提示词' }))
      expect(steps.map((step) => step.state)).toEqual(['done', 'failed', 'pending', 'pending'])
    })

    it('阶段未命中已知序列时降级为空', () => {
      expect(aiTaskPhaseSteps(makeTask({ phase: '处理器升级后的未知阶段' }))).toEqual([])
      expect(
        aiTaskPhaseSteps(makeTask({ type: 'vector_reindex', phase: '重嵌' })),
      ).toEqual([])
    })

    it('已取消不猜测进度', () => {
      const steps = aiTaskPhaseSteps(makeTask({ status: 'cancelled', phase: '构建提示词' }))
      expect(steps.every((step) => step.state === 'pending')).toBe(true)
    })
  })

  describe('产物', () => {
    it('按 kind 分组计数并映射中文标签', () => {
      const counts = aiArtifactKindCounts([
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
      expect(aiArtifactKindLabel('test_case_suggestion')).toBe('用例')
    })

    it('空清单返回空计数', () => {
      expect(aiArtifactKindCounts(null)).toEqual([])
      expect(aiArtifactKindCounts([])).toEqual([])
    })

    it('已处理数排除待确认', () => {
      expect(
        aiArtifactProcessed([
          { confirmStatus: 'pending' },
          { confirmStatus: 'adopted' },
          { confirmStatus: 'rejected' },
        ]),
      ).toEqual({ processed: 2, total: 3 })
      expect(aiArtifactProcessed(null)).toEqual({ processed: 0, total: 0 })
    })

    it('确认状态映射中文标签，未知降级原文', () => {
      expect(aiArtifactConfirmMeta('pending').label).toBe('待确认')
      expect(aiArtifactConfirmMeta('adopted_edited').label).toBe('已采纳')
      expect(aiArtifactConfirmMeta('rejected').tagType).toBe('info')
      expect(aiArtifactConfirmMeta('future').label).toBe('future')
    })
  })
})
