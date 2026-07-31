import { describe, expect, it } from 'vitest'
import type { AiReviewSummaryStats } from '@/types'
import { buildStatCards, failRate } from './reviewSummary'

function stats(overrides: Partial<AiReviewSummaryStats> = {}): AiReviewSummaryStats {
  return {
    totalCases: 100,
    passCount: 80,
    failCount: 15,
    pendingCount: 5,
    passRate: 80,
    failByDocument: [{ documentName: '登录用例集', failCount: 15 }],
    ...overrides,
  }
}

describe('failRate 不通过率', () => {
  it('按不通过数/总数计算并保留两位小数', () => {
    expect(failRate(stats())).toBe(15)
    expect(failRate(stats({ totalCases: 3, failCount: 1 }))).toBe(33.33)
  })

  it('总数为 0 时返回 0（不除零）', () => {
    expect(failRate(stats({ totalCases: 0, failCount: 0, passCount: 0, pendingCount: 0 }))).toBe(0)
  })
})

describe('buildStatCards 统计卡片', () => {
  it('输出通过率/不通过率/待评审数/问题分布四张卡片', () => {
    const cards = buildStatCards(stats())
    expect(cards.map((c) => c.key)).toEqual(['pass', 'fail', 'pending', 'dist'])
    expect(cards[0].value).toBe('80%')
    expect(cards[1].value).toBe('15%')
    expect(cards[2].value).toBe('5')
    expect(cards[3].value).toBe('1 个文档')
  })

  it('无不通过分布时问题分布显示「无」', () => {
    const cards = buildStatCards(stats({ failByDocument: [] }))
    expect(cards[3].value).toBe('无')
  })
})
