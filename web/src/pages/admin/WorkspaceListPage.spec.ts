import { describe, expect, it } from 'vitest'
import pageSource from './WorkspaceListPage.vue?raw'

describe('WorkspaceListPage 列表交互与反馈', () => {
  it('空间名称支持键盘聚焦查看全文并保留加载/刷新失败恢复', () => {
    expect(pageSource).toContain('ListTitleLink')
    expect(pageSource).toContain('goDetail')
    expect(pageSource).toContain('ListLoadFeedback')
    expect(pageSource).toContain('hasLoaded')
    expect(pageSource).toContain('loadError')
    expect(pageSource).toContain('requestId')
    expect(pageSource).toContain('onBeforeUnmount')
    expect(pageSource).toContain('refresh-error')
    expect(pageSource).toContain('ElMessage.error')
  })

  it('状态筛选分段按钮具备读屏可感知的选中态与分组语义', () => {
    expect(pageSource).toContain('role="group"')
    expect(pageSource).toContain('aria-label="空间状态"')
    expect(pageSource).toContain(':aria-pressed="query.status === opt.value"')
  })
})
