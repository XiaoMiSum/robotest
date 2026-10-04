import { describe, expect, it } from 'vitest'
import {
  TRACE_ACTION_LABEL,
  TRACE_DISPOSITIONS,
  TRACE_EDGE_LEGEND,
  TRACE_GAP_ROUTE,
  TRACE_GAP_TYPES,
  TRACE_MATRIX_COLUMNS,
  traceActionAvailability,
  traceCoverageMeta,
  traceDispositionMeta,
  traceEdgeStatusMeta,
  traceGapActionMeta,
  traceGapTypeMeta,
  traceNodeTypeMeta,
  traceRequirementStatusMeta,
  traceRowHighlight,
  traceRowHint,
} from './tracePresentation'

describe('tracePresentation', () => {
  describe('traceEdgeStatusMeta', () => {
    it('五个边状态命中固定映射', () => {
      expect(traceEdgeStatusMeta('confirmed')).toEqual({
        status: 'confirmed',
        label: '有效',
        symbol: '○',
        tagType: 'success',
        hint: '链路有效',
      })
      expect(traceEdgeStatusMeta('ai_created')).toEqual({
        status: 'ai_created',
        label: '待确认',
        symbol: '◐',
        tagType: 'info',
        hint: 'AI 建边待人工确认',
      })
      expect(traceEdgeStatusMeta('stale')).toEqual({
        status: 'stale',
        label: 'stale',
        symbol: '⚠',
        tagType: 'warning',
        hint: '目标版本已变更，需重新确认',
      })
      expect(traceEdgeStatusMeta('conflict')).toEqual({
        status: 'conflict',
        label: 'conflict',
        symbol: '⚠⚠',
        tagType: 'danger',
        hint: '人工与 AI 结论冲突，需采纳其一',
      })
      expect(traceEdgeStatusMeta('detached')).toEqual({
        status: 'detached',
        label: '已断开',
        symbol: '⋯',
        tagType: 'info',
        hint: '人工断开，不计入覆盖统计',
      })
    })

    it('未知状态回退为原文与默认符号', () => {
      expect(traceEdgeStatusMeta('mystery')).toEqual({
        status: 'confirmed',
        label: 'mystery',
        symbol: '○',
        tagType: 'info',
        hint: 'mystery',
      })
    })
  })

  describe('TRACE_EDGE_LEGEND', () => {
    it('五项且顺序与交互图例一致', () => {
      expect(TRACE_EDGE_LEGEND).toHaveLength(5)
      expect(TRACE_EDGE_LEGEND.map((item) => item.label)).toEqual([
        '有效',
        '待确认',
        'stale',
        'conflict',
        '已断开',
      ])
    })
  })

  describe('traceNodeTypeMeta', () => {
    it('六个节点类型命中图标映射', () => {
      expect(traceNodeTypeMeta('requirement')).toEqual({ label: '需求', icon: 'Tickets' })
      expect(traceNodeTypeMeta('module')).toEqual({ label: '模块', icon: 'Folder' })
      expect(traceNodeTypeMeta('mindmap_document')).toEqual({ label: '脑图文档', icon: 'Notebook' })
      expect(traceNodeTypeMeta('test_case')).toEqual({ label: '测试用例', icon: 'List' })
      expect(traceNodeTypeMeta('test_review')).toEqual({ label: '评审', icon: 'View' })
      expect(traceNodeTypeMeta('test_plan')).toEqual({ label: '测试计划', icon: 'Calendar' })
    })

    it('未知类型回退为原文与默认图标', () => {
      expect(traceNodeTypeMeta('mystery')).toEqual({ label: 'mystery', icon: 'Tickets' })
    })
  })

  describe('TRACE_MATRIX_COLUMNS', () => {
    it('五列且 key 顺序对齐 edgeCounts', () => {
      expect(TRACE_MATRIX_COLUMNS).toHaveLength(5)
      expect(TRACE_MATRIX_COLUMNS.map((column) => column.key)).toEqual([
        'module',
        'document',
        'testCase',
        'review',
        'plan',
      ])
    })
  })

  describe('traceCoverageMeta', () => {
    it('有值返回覆盖徽标', () => {
      expect(traceCoverageMeta('covered')).toEqual({ label: '完整覆盖', tagType: 'success' })
      expect(traceCoverageMeta('partial')).toEqual({ label: '部分覆盖', tagType: 'warning' })
      expect(traceCoverageMeta('uncovered')).toEqual({ label: '未覆盖', tagType: 'danger' })
      expect(traceCoverageMeta('pending')).toEqual({ label: '待分析', tagType: 'info' })
    })

    it('null 与 undefined 返回 null 以渲染「—」', () => {
      expect(traceCoverageMeta(null)).toBeNull()
      expect(traceCoverageMeta(undefined)).toBeNull()
    })
  })

  describe('traceRequirementStatusMeta', () => {
    it('命中需求状态映射', () => {
      expect(traceRequirementStatusMeta('draft')).toEqual({ label: '草稿', tagType: 'info' })
      expect(traceRequirementStatusMeta('confirmed')).toEqual({ label: '已确认', tagType: 'success' })
      expect(traceRequirementStatusMeta('changed')).toEqual({ label: '已变更', tagType: 'warning' })
      expect(traceRequirementStatusMeta('archived')).toEqual({
        label: '已归档',
        tagType: 'info',
        archived: true,
      })
    })

    it('未知状态回退为原文', () => {
      expect(traceRequirementStatusMeta('mystery')).toEqual({ label: 'mystery', tagType: 'info' })
    })
  })

  describe('traceRowHighlight', () => {
    it('仅 stale 时警示', () => {
      expect(traceRowHighlight({ staleCount: 2, conflictCount: 0 })).toEqual({
        warning: true,
        danger: false,
      })
    })

    it('仅 conflict 时危险', () => {
      expect(traceRowHighlight({ staleCount: 0, conflictCount: 1 })).toEqual({
        warning: false,
        danger: true,
      })
    })

    it('两者都为 0 时全 false', () => {
      expect(traceRowHighlight({ staleCount: 0, conflictCount: 0 })).toEqual({
        warning: false,
        danger: false,
      })
    })

    it('两者都大于 0 时同时点亮', () => {
      expect(traceRowHighlight({ staleCount: 3, conflictCount: 2 })).toEqual({
        warning: true,
        danger: true,
      })
    })
  })

  describe('traceRowHint', () => {
    it('只 stale 时仅版本变更文案', () => {
      expect(traceRowHint({ staleCount: 2, conflictCount: 0 })).toBe(
        '2 条边目标版本已变更，需重新确认',
      )
    })

    it('只 conflict 时仅冲突文案', () => {
      expect(traceRowHint({ staleCount: 0, conflictCount: 3 })).toBe('3 条边人工与 AI 结论冲突')
    })

    it('两者都有时以分号拼接', () => {
      expect(traceRowHint({ staleCount: 2, conflictCount: 3 })).toBe(
        '2 条边目标版本已变更，需重新确认；3 条边人工与 AI 结论冲突',
      )
    })

    it('都无时为空串', () => {
      expect(traceRowHint({ staleCount: 0, conflictCount: 0 })).toBe('')
    })
  })

  describe('traceGapTypeMeta', () => {
    it('四种缺口类型命中映射', () => {
      expect(traceGapTypeMeta('uncovered_requirement')).toEqual({
        label: '未生成用例',
        desc: '需求尚无派生用例',
      })
      expect(traceGapTypeMeta('orphan_case')).toEqual({ label: '未关联需求', desc: '用例未回溯到任何需求' })
      expect(traceGapTypeMeta('unreviewed_case')).toEqual({ label: '未评审', desc: '用例未进入评审' })
      expect(traceGapTypeMeta('unscheduled_case')).toEqual({ label: '未进计划', desc: '用例未纳入测试计划' })
    })

    it('未知类型回退为原文', () => {
      expect(traceGapTypeMeta('mystery')).toEqual({ label: 'mystery', desc: 'mystery' })
    })
  })

  describe('TRACE_GAP_TYPES', () => {
    it('四项且顺序与缺口子页签一致', () => {
      expect(TRACE_GAP_TYPES).toHaveLength(4)
      expect(TRACE_GAP_TYPES).toEqual([
        'uncovered_requirement',
        'orphan_case',
        'unreviewed_case',
        'unscheduled_case',
      ])
    })
  })

  describe('traceGapActionMeta', () => {
    it('generate 批次二前置灰并有悬浮提示', () => {
      expect(traceGapActionMeta('generate')).toEqual({
        label: '发起生成',
        disabled: true,
        disabledHint: '生成配置随批次二开放',
      })
    })

    it('review 与 schedule 可点击', () => {
      expect(traceGapActionMeta('review')).toEqual({ label: '发起评审', disabled: false, disabledHint: '' })
      expect(traceGapActionMeta('schedule')).toEqual({ label: '加入计划', disabled: false, disabledHint: '' })
    })

    it('未知动作回退为置灰', () => {
      expect(traceGapActionMeta('mystery')).toEqual({ label: 'mystery', disabled: true, disabledHint: '' })
    })
  })

  describe('TRACE_GAP_ROUTE', () => {
    it('review 与 schedule 的落地页路径', () => {
      expect(TRACE_GAP_ROUTE.review).toBe('/workspace/projects/reviews')
      expect(TRACE_GAP_ROUTE.schedule).toBe('/workspace/projects/plans')
    })
  })

  describe('traceDispositionMeta', () => {
    it('四个处置结论命中标签与理由要求', () => {
      expect(traceDispositionMeta('pending')).toEqual({
        label: '待处置',
        tagType: 'info',
        requireReason: false,
      })
      expect(traceDispositionMeta('no_impact')).toEqual({
        label: '确认无影响',
        tagType: 'success',
        requireReason: false,
      })
      expect(traceDispositionMeta('regenerate')).toEqual({
        label: '需要跟进',
        tagType: 'warning',
        requireReason: true,
      })
      expect(traceDispositionMeta('re_review')).toEqual({
        label: '已处理',
        tagType: 'success',
        requireReason: true,
      })
    })

    it('未知处置回退为原文且不强制理由', () => {
      expect(traceDispositionMeta('mystery')).toEqual({
        label: 'mystery',
        tagType: 'info',
        requireReason: false,
      })
    })
  })

  describe('TRACE_DISPOSITIONS', () => {
    it('筛选项为三个可处置结论', () => {
      expect(TRACE_DISPOSITIONS).toEqual(['no_impact', 'regenerate', 're_review'])
    })
  })

  describe('traceActionAvailability', () => {
    it('detached 只能恢复', () => {
      expect(traceActionAvailability('detached')).toEqual({
        confirm: false,
        reattach: false,
        detach: false,
        restore: true,
      })
    })

    it('其余状态可确认 / 改挂 / 断开且不可恢复', () => {
      for (const status of ['confirmed', 'stale', 'conflict', 'ai_created']) {
        expect(traceActionAvailability(status)).toEqual({
          confirm: true,
          reattach: true,
          detach: true,
          restore: false,
        })
      }
    })
  })

  describe('TRACE_ACTION_LABEL', () => {
    it('四个修正动作的中文名', () => {
      expect(Object.keys(TRACE_ACTION_LABEL)).toHaveLength(4)
      expect(TRACE_ACTION_LABEL).toEqual({
        confirm: '确认',
        reattach: '改挂',
        detach: '断开',
        restore: '恢复',
      })
    })
  })
})
