import { describe, expect, it } from 'vitest'
import pageSource from './MocksPage.vue?raw'

describe('MocksPage 列表交互与反馈', () => {
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
})
