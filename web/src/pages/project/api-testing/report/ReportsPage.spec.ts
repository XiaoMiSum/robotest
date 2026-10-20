import { describe, expect, it } from 'vitest'
import pageSource from './ReportsPage.vue?raw'

describe('ReportsPage 表格高度', () => {
  it('表格固定高度且由容器承接剩余空间，表体内滚动（UI-SC-01 / UI-SC-05 / UI-SC-06）', () => {
    expect(pageSource).toContain('class="reports-page__body"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('flex: 1;')
    expect(pageSource).toContain('min-height: 0;')
    expect(pageSource).toContain('overflow: hidden;')
  })
})
