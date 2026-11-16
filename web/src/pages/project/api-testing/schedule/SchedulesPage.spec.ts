import { describe, expect, it } from 'vitest'
import pageSource from './SchedulesPage.vue?raw'

describe('SchedulesPage 列表交互与反馈', () => {
  it('表格区固定高度并支持加载/刷新失败恢复', () => {
    expect(pageSource).toContain('class="schedules-page__table"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('min-height: 0')
    expect(pageSource).toContain('ListLoadFeedback')
    expect(pageSource).toContain('refresh-error')
    expect(pageSource).toContain('hasLoaded')
    expect(pageSource).toContain('loadError')
  })

  it('任务名称支持键盘聚焦查看全文', () => {
    expect(pageSource).toContain('ListTitleLink')
    expect(pageSource).toContain('@open="openEdit')
  })
})
