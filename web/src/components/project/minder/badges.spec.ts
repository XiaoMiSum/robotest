import { describe, it, expect } from 'vitest'
import { typeBadge, priorityBadge, badgeWidth } from './badges'

describe('typeBadge', () => {
  it('四种业务类型映射到文案与颜色', () => {
    expect(typeBadge('case')).toEqual({ label: '用例', color: '#A464FF' })
    expect(typeBadge('precondition')).toEqual({ label: '前置', color: '#409EFF' })
    expect(typeBadge('step')).toEqual({ label: '步骤', color: '#67C23A' })
    expect(typeBadge('expected')).toEqual({ label: '预期', color: '#E6A23C' })
  })

  it('normal 与未知类型不显示徽标', () => {
    expect(typeBadge('normal')).toBeNull()
    expect(typeBadge('unknown')).toBeNull()
    expect(typeBadge(undefined)).toBeNull()
    expect(typeBadge(3)).toBeNull()
  })
})

describe('priorityBadge', () => {
  it('P0-P3 映射到文案与颜色（P0 红 / P1 橙 / P2 蓝 / P3 灰）', () => {
    expect(priorityBadge('P0')).toEqual({ label: 'P0', color: '#F56C6C' })
    expect(priorityBadge('P1')).toEqual({ label: 'P1', color: '#E6A23C' })
    expect(priorityBadge('P2')).toEqual({ label: 'P2', color: '#409EFF' })
    expect(priorityBadge('P3')).toEqual({ label: 'P3', color: '#909399' })
  })

  it('空值与非法优先级不显示徽标', () => {
    expect(priorityBadge(null)).toBeNull()
    expect(priorityBadge('P4')).toBeNull()
    expect(priorityBadge('p0')).toBeNull()
  })
})

describe('badgeWidth', () => {
  it('中文比同字符数拉丁文更宽', () => {
    expect(badgeWidth('用例')).toBeGreaterThan(badgeWidth('P0'))
  })

  it('宽度随字符数递增', () => {
    expect(badgeWidth('前置条件')).toBeGreaterThan(badgeWidth('前置'))
  })
})
