import { describe, expect, it } from 'vitest'
import componentSource from './ExtractorAssetPicker.vue?raw'

describe('ExtractorAssetPicker 错误反馈', () => {
  it('显示责任层传入的错误并提供搜索重试入口', () => {
    expect(componentSource).toContain('error?: string | null')
    expect(componentSource).toContain('v-if="error"')
    expect(componentSource).toContain('@click="emit(\'search\')"')
    expect(componentSource).toContain('重试')
  })
})
