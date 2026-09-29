import { describe, expect, it } from 'vitest'
import pageSource from './DebugHistoryView.vue?raw'

describe('DebugHistoryView 列表与行操作错误边界', () => {
  it('列表和同页行操作均透传后端消息，过期请求不提示且有重试入口', () => {
    expect(pageSource).toContain('listRequestId')
    expect(pageSource).toContain("errorMessage(err, '加载调试记录失败')")
    expect(pageSource).toContain("errorMessage(err, '删除调试记录失败')")
    expect(pageSource).toContain("errorMessage(err, '重命名调试记录失败')")
    expect(pageSource).toContain('v-if="error"')
    expect(pageSource).toContain('@click="retry"')
    expect(pageSource).toContain('onBeforeUnmount')
  })

  it('滚动到底自动加载下一页，不再渲染分页器', () => {
    expect(pageSource).not.toContain('el-pagination')
    expect(pageSource).toContain('PAGE_SIZE = 100')
    expect(pageSource).toContain('@scroll="handleListScroll"')
    expect(pageSource).toContain('hasMore')
    expect(pageSource).toContain('loadedPage')
  })

  it('行内操作平铺为恢复重命名删除，不使用下拉菜单', () => {
    expect(pageSource).not.toContain('el-dropdown')
    expect(pageSource).toContain('>恢复</el-button>')
    expect(pageSource).toContain('>重命名</el-button>')
    expect(pageSource).toContain('>删除</el-button>')
  })

  it('方法、状态码、时间与耗时列固定列宽，缺值行保留占位保证对齐', () => {
    expect(pageSource).toContain('responseCodeClass')
    expect(pageSource).toContain('width: 56px')
    expect(pageSource).toContain('width: 40px')
    expect(pageSource).toContain('history__item-time')
    expect(pageSource).toContain('width: 100px')
    expect(pageSource).toContain('history__item-cost')
    expect(pageSource).toContain('width: 64px')
  })
})
