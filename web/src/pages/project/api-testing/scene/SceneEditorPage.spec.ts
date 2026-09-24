import { describe, expect, it } from 'vitest'
import pageSource from './SceneEditorPage.vue?raw'
import headerSource from './SceneEditorHeader.vue?raw'
import historySource from '../../../../composables/project/api-testing/scene/useSceneHistory.ts?raw'
import assetSource from '../../../../composables/project/api-testing/scene/useAssetPicker.ts?raw'

describe('SceneEditorPage 历史与资产错误边界', () => {
  it('执行/变更历史和公共组件列表均透传后端消息并提供重试', () => {
    expect(historySource).toContain('Promise.allSettled')
    expect(historySource).toContain('requestSequence')
    expect(historySource).toContain('ElMessage.error(message)')
    expect(historySource).toContain('retryHistory')
    expect(assetSource).toContain('assetPickerError')
    expect(assetSource).toContain('ElMessage.error(message)')
    expect(pageSource).toContain(':history-error="historyError"')
    expect(pageSource).toContain('@load-history="retryHistory"')
    expect(pageSource).toContain(':error="assetPickerError"')
    expect(headerSource).toContain('v-if="historyError"')
    expect(headerSource).toContain('@click="emit(\'load-history\')"')
  })
})
