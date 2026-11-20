import { describe, expect, it } from 'vitest'
import pageSource from './ReviewDetailPage.vue?raw'

// 详情页头收拢为单条无边框横条（交互 05 §1.2）：面包屑保持纯净，右侧同行按序承载需求/状态/进度/操作
describe('ReviewDetailPage 页头单条横条', () => {
  it('删除独立标题卡片，当前页名称由面包屑末段承担', () => {
    expect(pageSource).toContain('class="review-detail__header"')
    expect(pageSource).toContain('class="review-detail__breadcrumb"')
    expect(pageSource).toContain('to="/workspace/projects/functional-testing?tab=reviews"')
    expect(pageSource).not.toContain('review-detail__head"')
    expect(pageSource).not.toContain('review-detail__title-row')
    expect(pageSource).not.toContain('class="review-detail__title"')
  })

  it('右侧区按需求、状态、进度统计、操作按钮顺序排布且统计与按钮竖线分隔', () => {
    const requirement = pageSource.indexOf('review-detail__requirements')
    const status = pageSource.indexOf('statusMeta.tagType')
    const progress = pageSource.indexOf('review-detail__progress-row')
    const actions = pageSource.indexOf('class="review-detail__actions"')
    expect(requirement).toBeGreaterThanOrEqual(0)
    expect(requirement).toBeLessThan(status)
    expect(status).toBeLessThan(progress)
    expect(progress).toBeLessThan(actions)
    expect(pageSource).toContain('border-left: 1px solid var(--color-neutral-200)')
  })

  it('超宽时页头单行横向滚动不换行，保留滚动能力但隐藏视觉滚动条', () => {
    const headerStyle = pageSource.slice(pageSource.indexOf('.review-detail__header {'))
    expect(headerStyle).toContain('overflow-x: auto')
    expect(headerStyle).toContain('scrollbar-width: none')
    expect(headerStyle).not.toContain('flex-wrap: wrap')
  })

  it('快照文档树卡片保留固定 header「快照文档 / 只读」', () => {
    expect(pageSource).toContain('class="review-detail__tree-header"')
    expect(pageSource).toContain('快照文档')
    expect(pageSource).toContain('只读')
  })
})
