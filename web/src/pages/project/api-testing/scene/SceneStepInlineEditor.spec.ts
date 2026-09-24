import { describe, expect, it } from 'vitest'
import pageSource from './SceneStepInlineEditor.vue?raw'

describe('SceneStepInlineEditor 公共组件错误边界', () => {
  it('公共组件列表失败优先展示后端消息并保留重试', () => {
    expect(pageSource).toContain('assetPickerRequestId')
    expect(pageSource).toContain("errorMessage(err, '公共组件加载失败')")
    expect(pageSource).toContain('ElMessage.error(message)')
    expect(pageSource).toContain(':error="assetPickerError"')
    expect(pageSource).toContain('@search="retryAssetPicker"')
    expect(pageSource).toContain('environmentError')
    expect(pageSource).toContain('retryEnvironmentOptions')
  })
})
