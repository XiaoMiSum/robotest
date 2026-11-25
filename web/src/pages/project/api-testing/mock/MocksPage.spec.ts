import { describe, expect, it } from 'vitest'
import pageSource from './MocksPage.vue?raw'

describe('MocksPage 列表交互与反馈', () => {
  it('页头 + 单卡片布局：新建为主按钮、工具栏计数与分页总数齐全', () => {
    expect(pageSource).toContain('<header class="page-head">')
    expect(pageSource).toContain('class="page-head__desc"')
    expect(pageSource).toContain('新建 Mock')
    expect(pageSource).toContain('<ListWorkspace label="Mock 规则列表"')
    expect(pageSource).toContain('class="mocks-page__count"')
    expect(pageSource).toContain('共 {{ total }} 条')
    expect(pageSource).toContain('#pagination')
    expect(pageSource).not.toContain('bare')
  })

  it('加载、筛选和分页请求具备失效保护并支持首屏/刷新失败恢复', () => {
    expect(pageSource).toContain('listRequestId')
    expect(pageSource).toContain("errorMessage(err, '加载 Mock 列表失败')")
    expect(pageSource).toContain('ElMessage.error(message)')
    expect(pageSource).toContain('hasLoaded')
    expect(pageSource).toContain('loadError')
    expect(pageSource).toContain('ListLoadFeedback')
    expect(pageSource).toContain('refresh-error')
    expect(pageSource).toContain('@retry="retry"')
    expect(pageSource).toContain('@change="handleSearch"')
    expect(pageSource).toContain('void loadList()')
  })

  it('名称可键盘聚焦查看全文并保留表体高度布局', () => {
    expect(pageSource).toContain('ListTitleLink')
    expect(pageSource).toContain('class="mocks-page__table"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('min-height: 0')
  })

  it('列设置入口置于操作列表头右侧，工具栏不再展示列设置', () => {
    expect(pageSource).toContain('class="mocks-page__action-header"')
    expect(pageSource).toContain('justify-content: flex-end')
    expect(pageSource).toContain('icon-only')
    expect(pageSource.match(/<TableColumnSetting/g)).toHaveLength(1)
  })
})
