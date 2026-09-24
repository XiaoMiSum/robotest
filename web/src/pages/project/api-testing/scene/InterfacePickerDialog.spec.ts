import { describe, expect, it } from 'vitest'
import pageSource from './InterfacePickerDialog.vue?raw'

describe('InterfacePickerDialog 远程候选错误边界', () => {
  it('搜索失败提示后端消息并保留可重试入口', () => {
    expect(pageSource).toContain('interfaceRequestId')
    expect(pageSource).toContain("errorMessage(err, '加载接口候选列表失败')")
    expect(pageSource).toContain('ElMessage.error(message)')
    expect(pageSource).toContain('v-if="interfaceError"')
    expect(pageSource).toContain('@click="retry"')
    expect(pageSource).toContain('void loadInterfaces()')
  })
})
