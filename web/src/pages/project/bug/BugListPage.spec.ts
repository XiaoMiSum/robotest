import { describe, expect, it } from 'vitest'
import pageSource from './BugListPage.vue?raw'

describe('BugListPage 列表固定高度结构', () => {
  it('列表视图表格固定高度，数据超出仅在表体内部滚动', () => {
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('class="bug-page__table"')
    expect(pageSource).toContain('class="bug-page__card-body"')
    // 高度链路：页面定高 → 列表卡片 flex:1 → 表体 flex:1（UI-SC-01 / UI-SC-06）
    expect(pageSource).toContain('height: 100%')
    expect(pageSource).toContain('min-height: 0')
    expect(pageSource).toContain('overflow: hidden')
    // 分页为固定控件，不随表体收缩
    expect(pageSource).toContain('flex-shrink: 0')
  })

  it('卡片内容区用自有 class 走 CSS flex，不改写 el-card 内部结构', () => {
    expect(pageSource).not.toContain(':body-style')
    expect(pageSource).not.toContain(':deep(.el-card__body)')
    expect(pageSource).not.toContain(':deep(.el-table')
  })

  it('看板与列表共用高度链，不再按视口偏移写死看板高度', () => {
    expect(pageSource).not.toContain('calc(100vh')
  })
})
