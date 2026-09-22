import { describe, it, expect } from 'vitest'
import { typeBadge, priorityBadge, reviewMarkBadge, executionResultBadge, aiBadge, orderBadge, badgeWidth } from './badges'

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

describe('reviewMarkBadge', () => {
  it('通过/不通过映射到文案与颜色（通过绿 / 不通过红）', () => {
    expect(reviewMarkBadge('pass')).toEqual({ label: '✓ 通过', color: '#67C23A' })
    expect(reviewMarkBadge('fail')).toEqual({ label: '✕ 不通过', color: '#F56C6C' })
  })

  it('待评审（空值）与非法标记不显示徽标', () => {
    expect(reviewMarkBadge(null)).toBeNull()
    expect(reviewMarkBadge(undefined)).toBeNull()
    expect(reviewMarkBadge('block')).toBeNull()
  })
})

describe('executionResultBadge', () => {
  it('通过/失败/阻塞映射到文案与颜色', () => {
    expect(executionResultBadge('pass')).toEqual({ label: '✓ 通过', color: '#67C23A' })
    expect(executionResultBadge('fail')).toEqual({ label: '✕ 失败', color: '#F56C6C' })
    expect(executionResultBadge('block')).toEqual({ label: '⚠ 阻塞', color: '#E6A23C' })
  })

  it('未执行是默认态不显示徽标', () => {
    expect(executionResultBadge('untested')).toBeNull()
    expect(executionResultBadge(null)).toBeNull()
  })
})

describe('aiBadge', () => {
  it('aiGenerated 严格为 true 时渲染 AI 徽标', () => {
    expect(aiBadge(true)).toEqual({ label: 'AI', color: '#13C2C2' })
  })

  it('false/缺省/非布尔真值不渲染（移除标识后即消失）', () => {
    expect(aiBadge(false)).toBeNull()
    expect(aiBadge(undefined)).toBeNull()
    expect(aiBadge(null)).toBeNull()
    expect(aiBadge('true')).toBeNull()
    expect(aiBadge(1)).toBeNull()
  })
})

describe('orderBadge 执行顺序推荐序号徽标', () => {
  it('正整数 orderNo 渲染 #序号 亮橙徽标', () => {
    expect(orderBadge(1)).toEqual({ label: '#1', color: '#FF6F00' })
    expect(orderBadge(12)).toEqual({ label: '#12', color: '#FF6F00' })
  })

  it('非正整数/缺省不渲染（未进入推荐结果即无徽标）', () => {
    expect(orderBadge(0)).toBeNull()
    expect(orderBadge(-3)).toBeNull()
    expect(orderBadge(1.5)).toBeNull()
    expect(orderBadge(null)).toBeNull()
    expect(orderBadge(undefined)).toBeNull()
    expect(orderBadge('2')).toBeNull()
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
