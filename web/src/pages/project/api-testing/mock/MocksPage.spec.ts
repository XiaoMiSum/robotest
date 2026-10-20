import { describe, expect, it } from 'vitest'
import pageSource from './MocksPage.vue?raw'

describe('MocksPage 列表错误边界', () => {
  it('加载、筛选和分页请求由页面责任层处理错误并支持重试', () => {
    expect(pageSource).toContain('listRequestId')
    expect(pageSource).toContain("errorMessage(err, '加载 Mock 列表失败')")
    expect(pageSource).toContain('ElMessage.error(message)')
    expect(pageSource).toContain('v-if="error"')
    expect(pageSource).toContain('@click="retry"')
    expect(pageSource).toContain('@change="handleSearch"')
    expect(pageSource).toContain('void loadList()')
  })

  it('表格区固定高度沿页面高度链路取窗口剩余可用空间，数据超出时表体内滚动', () => {
    expect(pageSource).toContain('class="mocks-page__table"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('min-height: 0')
  })
})
