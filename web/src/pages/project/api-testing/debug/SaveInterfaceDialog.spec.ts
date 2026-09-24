import { describe, expect, it } from 'vitest'
import pageSource from './SaveInterfaceDialog.vue?raw'

describe('SaveInterfaceDialog 依赖列表错误边界', () => {
  it('模块树和接口候选分别显示后端消息、加载态和重试入口', () => {
    expect(pageSource).toContain('moduleRequestId')
    expect(pageSource).toContain('interfaceRequestId')
    expect(pageSource).toContain("errorMessage(err, '加载接口模块失败')")
    expect(pageSource).toContain("errorMessage(err, '加载接口候选列表失败')")
    expect(pageSource).toContain('v-if="moduleError"')
    expect(pageSource).toContain('v-if="interfaceError"')
    expect(pageSource).toContain('@click="retryModules"')
    expect(pageSource).toContain('@click="retryInterfaces"')
  })
})
