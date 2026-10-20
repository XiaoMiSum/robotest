import { describe, expect, it } from 'vitest'
import pageSource from './InterfacesPage.vue?raw'

describe('InterfacesPage 列表表格高度', () => {
  it('表格区固定高度沿页面高度链路取窗口剩余可用空间，数据超出时表体内滚动', () => {
    expect(pageSource).toContain('class="interfaces-page__table"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('min-height: 0')
  })
})
