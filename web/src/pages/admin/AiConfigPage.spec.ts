import { describe, expect, it } from 'vitest'
import pageSource from './AiConfigPage.vue?raw'
import composableSource from '../../composables/ai/useAiChatModels.ts?raw'

describe('AiConfigPage 对话模型列表错误边界', () => {
  it('刷新失败由 composable 提示后端消息，页面提供加载态和重试', () => {
    expect(composableSource).toContain('ElMessage.error(message)')
    expect(composableSource).toContain("errorMessage(err, '加载对话模型列表失败')")
    expect(composableSource).toContain('requestSequence')
    expect(composableSource).toContain('loading')
    expect(composableSource).toContain('retry')
    expect(pageSource).toContain('v-if="models.error.value"')
    expect(pageSource).toContain('v-loading="models.loading.value"')
    expect(pageSource).toContain('@click="models.retry"')
  })
})
