import { describe, expect, it } from 'vitest'
import pageSource from './SchedulesPage.vue?raw'

describe('SchedulesPage 列表交互与反馈', () => {
  it('页头 + 单卡片布局：新建为主按钮、工具栏计数与分页总数齐全', () => {
    expect(pageSource).toContain('<header class="page-head">')
    expect(pageSource).toContain('class="page-head__desc"')
    expect(pageSource).toContain('>新建任务</el-button>')
    expect(pageSource).toContain('<ListWorkspace label="定时任务列表"')
    expect(pageSource).toContain('class="schedules-page__count"')
    expect(pageSource).toContain('共 {{ total }} 条')
    expect(pageSource).toContain('#pagination')
    expect(pageSource.match(/\bbare\b/g)).toHaveLength(1)
  })

  it('表格区固定高度并支持加载/刷新失败恢复', () => {
    expect(pageSource).toContain('class="schedules-page__table"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toMatch(/\.schedules-page__table \{[^}]*height: 100%;/s)
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

  it('列设置入口置于操作列表头右侧，工具栏不再展示列设置', () => {
    expect(pageSource).toContain('class="schedules-page__action-header"')
    expect(pageSource).toContain('justify-content: flex-end')
    expect(pageSource).toContain('icon-only')
    expect(pageSource.match(/<TableColumnSetting/g)).toHaveLength(1)
  })
})
