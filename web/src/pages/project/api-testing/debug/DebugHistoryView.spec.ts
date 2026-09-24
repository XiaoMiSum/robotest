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
})
