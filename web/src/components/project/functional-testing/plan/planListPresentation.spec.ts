import { describe, expect, it } from 'vitest'
import {
  PLAN_STATUS_META,
  canBlockPlan,
  canResumePlan,
  planCountText,
  planEnvironment,
  planListAction,
  planNameSub,
  planPagerTotalText,
  planPassRate,
  planProgressStatus,
  planProgressText,
  planStatusMeta,
  planStatusLabel,
  planTimeRange,
} from './planListPresentation'

describe('planListPresentation', () => {
  it('PLAN_STATUS_META 覆盖五态文案与语义色', () => {
    expect(PLAN_STATUS_META.new).toEqual({ label: '未开始', tagType: 'info', modifier: 'neutral' })
    expect(PLAN_STATUS_META.in_progress).toEqual({ label: '执行中', tagType: 'warning', modifier: 'running' })
    expect(PLAN_STATUS_META.completed).toEqual({ label: '已完成', tagType: 'success', modifier: 'success' })
    expect(PLAN_STATUS_META.blocked).toEqual({ label: '已阻塞', tagType: 'danger', modifier: 'blocked' })
    expect(PLAN_STATUS_META.closed).toEqual({ label: '已关闭', tagType: 'info', modifier: 'neutral' })
  })

  it('未知状态兜底为占位文案', () => {
    expect(planStatusMeta('future_state').label).toBe('—')
    expect(planStatusLabel('future_state')).toBe('—')
    expect(planStatusLabel('blocked')).toBe('已阻塞')
  })

  it('阻塞/恢复的可见条件：未开始或执行中可阻塞，仅已阻塞可恢复', () => {
    expect(canBlockPlan('new')).toBe(true)
    expect(canBlockPlan('in_progress')).toBe(true)
    expect(canBlockPlan('completed')).toBe(false)
    expect(canBlockPlan('blocked')).toBe(false)
    expect(canResumePlan('blocked')).toBe(true)
    expect(canResumePlan('in_progress')).toBe(false)
  })

  it('列表主操作：执行中进入执行，其余查看详情', () => {
    expect(planListAction('in_progress')).toBe('enter')
    expect(planListAction('new')).toBe('view')
    expect(planListAction('blocked')).toBe('view')
    expect(planListAction('completed')).toBe('view')
  })

  it('通过率：未开始为占位、已完成绿色、其余常规展示', () => {
    expect(planPassRate('new', 0)).toEqual({ text: '—', tone: 'muted' })
    expect(planPassRate('completed', 96.54)).toEqual({ text: '96.5%', tone: 'success' })
    expect(planPassRate('in_progress', 94.6)).toEqual({ text: '94.6%', tone: 'default' })
    expect(planPassRate('blocked', 87.2)).toEqual({ text: '87.2%', tone: 'default' })
  })

  it('进度条配色：执行中橙、已阻塞红、其余默认', () => {
    expect(planProgressStatus('in_progress')).toBe('warning')
    expect(planProgressStatus('blocked')).toBe('exception')
    expect(planProgressStatus('new')).toBeUndefined()
    expect(planProgressStatus('completed')).toBeUndefined()
  })

  it('进度文案按整数百分比展示', () => {
    expect(planProgressText(58.4)).toBe('58%')
    expect(planProgressText(100)).toBe('100%')
    expect(planProgressText(0)).toBe('0%')
  })

  it('起止时间：双值日期区间、单值带时刻', () => {
    const range = planTimeRange('2026-09-20T00:00:00Z', '2026-09-24T00:00:00Z')
    expect(range).toMatch(/^\d{2}-\d{2} ~ \d{2}-\d{2}$/)
    const single = planTimeRange('2026-09-22T09:00:00Z', null)
    expect(single).toMatch(/^\d{2}-\d{2} \d{2}:\d{2}$/)
    expect(planTimeRange(null, null)).toBe('-')
  })

  it('环境展示：空白视作缺失', () => {
    expect(planEnvironment('staging')).toBe('staging')
    expect(planEnvironment(null)).toBe('—')
    expect(planEnvironment('   ')).toBe('—')
  })

  it('名称副行与计数文案', () => {
    expect(planNameSub(47)).toBe('关联用例 47 条')
    expect(planCountText(18)).toBe('共 18 个计划')
    expect(planPagerTotalText(18, 20)).toBe('共 18 个计划 · 每页 20 条')
  })
})
