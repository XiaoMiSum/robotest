import { describe, expect, it } from 'vitest'
import pageSource from './ReportsPage.vue?raw'

describe('ReportsPage 列表交互与反馈', () => {
  it('表格固定高度且由容器承接剩余空间，表体内滚动（UI-SC-01 / UI-SC-05 / UI-SC-06）', () => {
    expect(pageSource).toContain('class="reports-page__body"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('flex: 1;')
    expect(pageSource).toContain('min-height: 0;')
    expect(pageSource).toContain('overflow: hidden;')
  })

  it('报告名称可键盘聚焦查看全文并保留列表加载/刷新失败恢复', () => {
    expect(pageSource).toContain('ListTitleLink')
    expect(pageSource).toContain('ListLoadFeedback')
    expect(pageSource).toContain('hasLoaded')
    expect(pageSource).toContain('loadError')
    expect(pageSource).toContain('requestId')
    expect(pageSource).toContain('onBeforeUnmount')
    expect(pageSource).toContain('refresh-error')
    expect(pageSource).toContain('ElMessage.error')
  })

  it('列设置入口置于操作列表头右侧，工具栏不再展示列设置', () => {
    expect(pageSource).toContain('class="reports-page__action-header"')
    expect(pageSource).toContain('justify-content: flex-end')
    expect(pageSource).toContain('icon-only')
    expect(pageSource.match(/<TableColumnSetting/g)).toHaveLength(1)
  })
})
