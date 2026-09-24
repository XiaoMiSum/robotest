import { describe, expect, it } from 'vitest'
import pageSource from './InterfaceEditorPage.vue?raw'
import composableSource from '../../../../composables/project/api-testing/interface/useInterfaceEditor.ts?raw'

describe('InterfaceEditorPage 依赖列表错误边界', () => {
  it('详情、模块树和公共组件列表均有后端消息、竞态保护与重试', () => {
    expect(composableSource).toContain('detailRequestId')
    expect(composableSource).toContain('moduleRequestId')
    expect(composableSource).toContain('assetRequestId')
    expect(composableSource).toContain('ElMessage.error(message)')
    expect(composableSource).toContain('retryDetail')
    expect(composableSource).toContain('retryModules')
    expect(composableSource).toContain('retryAssetPicker')
    expect(pageSource).toContain('v-if="detailError"')
    expect(pageSource).toContain('v-if="moduleError"')
    expect(pageSource).toContain(':error="assetPickerError"')
    expect(pageSource).toContain('@click="retryDetail"')
    expect(pageSource).toContain('@click="retryModules"')
  })
})
