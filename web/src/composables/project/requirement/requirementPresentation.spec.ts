import { describe, expect, it } from 'vitest'
import type { RequirementChangeLog, RequirementListItem, RequirementStatus } from '@/types'
import {
  canArchiveRequirement,
  canConfirmRequirement,
  canSplitRequirement,
  canUnarchiveRequirement,
  isRequirementReadonly,
  requirementChangeLines,
  requirementChangeTypeLabel,
  requirementCoverageMeta,
  requirementPagerTotal,
  requirementPriorityLabel,
  requirementRow,
  requirementSourceLabel,
  requirementStatusMeta,
} from './requirementPresentation'

function item(overrides: Partial<RequirementListItem> = {}): RequirementListItem {
  return {
    id: 'r1',
    code: 'REQ-001',
    title: '登录验证码',
    moduleId: null,
    moduleName: null,
    systemVersion: null,
    status: 'draft',
    coverageStatus: null,
    priority: null,
    ownerId: null,
    ownerName: null,
    source: 'manual',
    updatedAt: '2026-10-02T08:00:00Z',
    ...overrides,
  }
}

function log(overrides: Partial<RequirementChangeLog> = {}): RequirementChangeLog {
  return {
    id: 'log1',
    changeType: 'title',
    operatorId: 'u1',
    operatorName: '张三',
    beforeSummary: null,
    afterSummary: null,
    createdAt: '2026-10-02T08:30:00Z',
    ...overrides,
  }
}

describe('requirementPresentation', () => {
  describe('状态徽标', () => {
    it('四态映射到指定标签与类型', () => {
      expect(requirementStatusMeta('draft')).toEqual({ label: '草稿', tagType: 'info' })
      expect(requirementStatusMeta('confirmed')).toEqual({ label: '已确认', tagType: 'success' })
      expect(requirementStatusMeta('changed')).toEqual({ label: '已变更', tagType: 'warning' })
      expect(requirementStatusMeta('archived')).toEqual({
        label: '已归档',
        tagType: 'info',
        archived: true,
      })
    })

    it('未知状态回退为原文且不抛错', () => {
      expect(requirementStatusMeta('mystery')).toEqual({ label: 'mystery', tagType: 'info' })
    })
  })

  describe('覆盖状态徽标', () => {
    it('null 展示为 null（页面渲染「—」）', () => {
      expect(requirementCoverageMeta(null)).toBeNull()
      expect(requirementCoverageMeta(undefined)).toBeNull()
    })

    it('四档覆盖状态映射标签', () => {
      expect(requirementCoverageMeta('covered')).toEqual({ label: '完整覆盖', tagType: 'success' })
      expect(requirementCoverageMeta('partial')).toEqual({ label: '部分覆盖', tagType: 'warning' })
      expect(requirementCoverageMeta('uncovered')).toEqual({ label: '未覆盖', tagType: 'danger' })
      expect(requirementCoverageMeta('pending')).toEqual({ label: '待分析', tagType: 'info' })
    })

    it('未知值回退为原文', () => {
      expect(requirementCoverageMeta('weird' as never)).toEqual({ label: 'weird', tagType: 'info' })
    })
  })

  describe('操作可用性（状态机 4.2）', () => {
    const statuses: RequirementStatus[] = ['draft', 'confirmed', 'changed', 'archived']

    it('确认仅 draft / changed 可用', () => {
      const allowed = statuses.filter((status) => canConfirmRequirement({ status }))
      expect(allowed).toEqual(['draft', 'changed'])
    })

    it('归档对非 archived 可用，取消归档仅 archived 可用', () => {
      expect(statuses.filter((s) => canArchiveRequirement({ status: s }))).toEqual([
        'draft',
        'confirmed',
        'changed',
      ])
      expect(statuses.filter((s) => canUnarchiveRequirement({ status: s }))).toEqual(['archived'])
    })

    it('拆分与可编辑性：archived 不可拆分且只读', () => {
      expect(statuses.filter((s) => canSplitRequirement({ status: s }))).toEqual([
        'draft',
        'confirmed',
        'changed',
      ])
      expect(isRequirementReadonly({ status: 'archived' })).toBe(true)
      expect(isRequirementReadonly({ status: 'draft' })).toBe(false)
    })
  })

  describe('文案', () => {
    it('优先级与来源文案', () => {
      expect(requirementPriorityLabel('high')).toBe('高')
      expect(requirementPriorityLabel('medium')).toBe('中')
      expect(requirementPriorityLabel('low')).toBe('低')
      expect(requirementPriorityLabel(null)).toBe('—')
      expect(requirementSourceLabel('manual')).toBe('手工创建')
      expect(requirementSourceLabel('import')).toBe('导入')
      expect(requirementSourceLabel('x')).toBe('x')
    })

    it('变更类型文案未知回退原文', () => {
      expect(requirementChangeTypeLabel('title')).toBe('标题')
      expect(requirementChangeTypeLabel('unknown')).toBe('unknown')
    })
  })

  describe('变更记录摘要', () => {
    it('title 变更渲染字段旧新值', () => {
      expect(
        requirementChangeLines(
          log({ beforeSummary: { title: '登录' }, afterSummary: { title: '登录验证码' } }),
        ),
      ).toEqual(['标题：登录 → 登录验证码'])
    })

    it('description 变更只标已修改', () => {
      expect(
        requirementChangeLines(log({ changeType: 'description', afterSummary: { description: 'x' } })),
      ).toEqual(['描述已修改'])
    })

    it('status 变更渲染目标状态标签', () => {
      expect(
        requirementChangeLines(log({ changeType: 'status', afterSummary: { status: 'confirmed' } })),
      ).toEqual(['状态更新为「已确认」'])
    })

    it('attribute 变更渲染字段名，空值显示（空），标签数组拼接', () => {
      expect(
        requirementChangeLines(
          log({
            changeType: 'attribute',
            beforeSummary: { systemVersion: 'V1', ownerId: null },
            afterSummary: { systemVersion: '', ownerId: 'u2' },
          }),
        ),
      ).toEqual(['版本：V1 → （空）', '负责人：（空） → u2'])
      expect(
        requirementChangeLines(
          log({
            changeType: 'attribute',
            beforeSummary: { tags: ['a'] },
            afterSummary: { tags: [] },
          }),
        ),
      ).toEqual(['标签：a → （空）'])
    })

    it('超长正文截断，空摘要返回空数组', () => {
      const long = 'x'.repeat(40)
      expect(
        requirementChangeLines(
          log({ changeType: 'attribute', afterSummary: { systemVersion: long } }),
        ),
      ).toEqual([`版本：（空） → ${'x'.repeat(30)}…`])
      expect(requirementChangeLines(log())).toEqual([])
    })

    it('未知字段回退键名', () => {
      expect(
        requirementChangeLines(log({ beforeSummary: { foo: 'a' }, afterSummary: { foo: 'b' } })),
      ).toEqual(['foo：a → b'])
    })
  })

  describe('列表行视图模型', () => {
    it('补全徽标与空值占位', () => {
      const row = requirementRow(item({ status: 'archived', coverageStatus: 'covered' }))
      expect(row.statusMeta.label).toBe('已归档')
      expect(row.coverageMeta).toEqual({ label: '完整覆盖', tagType: 'success' })
      expect(row.moduleText).toBe('—')
      expect(row.ownerText).toBe('—')
      expect(row.versionText).toBe('—')
    })

    it('有值时透出原始文本', () => {
      const row = requirementRow(
        item({ moduleName: '登录', ownerName: '张三', systemVersion: 'V2', coverageStatus: null }),
      )
      expect(row.moduleText).toBe('登录')
      expect(row.ownerText).toBe('张三')
      expect(row.versionText).toBe('V2')
      expect(row.coverageMeta).toBeNull()
    })

    it('分页总数文案', () => {
      expect(requirementPagerTotal(0)).toBe('共 0 条')
      expect(requirementPagerTotal(12)).toBe('共 12 条')
    })
  })
})
