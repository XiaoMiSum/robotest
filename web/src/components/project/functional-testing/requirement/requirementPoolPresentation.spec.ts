import { describe, expect, it } from 'vitest'
import {
  REQUIREMENT_SEGMENT_OPTIONS,
  REQUIREMENT_STATUS_META,
  canEditRequirement,
  requirementPagerTotal,
  requirementSourceSub,
  requirementStatusLabel,
  requirementStatusMeta,
} from './requirementPoolPresentation'

describe('requirementStatusMeta', () => {
  it('两态标签与圆点修饰与基准一致', () => {
    expect(REQUIREMENT_STATUS_META.active).toEqual({ label: '启用', modifier: 'active' })
    expect(REQUIREMENT_STATUS_META.archived).toEqual({ label: '已归档', modifier: 'archived' })
  })

  it('未知状态兜底为占位符', () => {
    expect(requirementStatusLabel('pending')).toBe('—')
    expect(requirementStatusMeta('pending').modifier).toBe('active')
    expect(requirementStatusMeta('archived').label).toBe('已归档')
  })
})

describe('REQUIREMENT_SEGMENT_OPTIONS', () => {
  it('启用/已归档两段即全集，默认启用', () => {
    expect(REQUIREMENT_SEGMENT_OPTIONS.map((option) => option.value)).toEqual(['active', 'archived'])
  })
})

describe('canEditRequirement', () => {
  it('启用可编辑，归档只读', () => {
    expect(canEditRequirement('active')).toBe(true)
    expect(canEditRequirement('archived')).toBe(false)
  })
})

describe('requirementSourceSub', () => {
  it('有来源 URL 截取 host + path 并保留可点击地址', () => {
    const sub = requirementSourceSub('https://prd.internal/1523?from=pool')
    expect(sub.text).toBe('prd.internal/1523')
    expect(sub.href).toBe('https://prd.internal/1523?from=pool')
  })

  it('根路径只保留域名', () => {
    expect(requirementSourceSub('https://prd.internal/').text).toBe('prd.internal')
  })

  it('无来源 URL 展示「内部提出」且不可点击', () => {
    expect(requirementSourceSub(null)).toEqual({ text: '内部提出', href: null })
    expect(requirementSourceSub('')).toEqual({ text: '内部提出', href: null })
  })

  it('非法 URL 原样展示，避免整列空白', () => {
    expect(requirementSourceSub('not a url').text).toBe('not a url')
  })
})

describe('requirementPagerTotal', () => {
  it('分页左侧计数文案', () => {
    expect(requirementPagerTotal(63, 20)).toBe('共 63 条 · 每页 20 条')
    expect(requirementPagerTotal(0, 20)).toBe('共 0 条 · 每页 20 条')
  })
})
