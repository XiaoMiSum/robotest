import type { AiReviewSummaryStats } from '@/types'

/** 摘要统计卡片展示模型（纯函数产出，便于单测） */
export interface SummaryStatCard {
  key: string
  label: string
  value: string
}

/** 不通过率 = 不通过数 / 关联用例总数（总数为 0 时为 0） */
export function failRate(stats: AiReviewSummaryStats): number {
  if (stats.totalCases <= 0) return 0
  return Math.round((stats.failCount * 10000.0) / stats.totalCases) / 100.0
}

/** 统计卡片区（通过率 / 不通过率 / 待评审数 / 问题分布，交互设计 3.1） */
export function buildStatCards(stats: AiReviewSummaryStats): SummaryStatCard[] {
  const distinctDocs = stats.failByDocument.length
  return [
    { key: 'pass', label: '通过率', value: `${stats.passRate}%` },
    { key: 'fail', label: '不通过率', value: `${failRate(stats)}%` },
    { key: 'pending', label: '待评审数', value: String(stats.pendingCount) },
    {
      key: 'dist',
      label: '问题分布',
      value: distinctDocs > 0 ? `${distinctDocs} 个文档` : '无',
    },
  ]
}
