import { describe, expect, it } from 'vitest'
import pageSource from './StepEditorDrawer.vue?raw'
import composableSource from '../../../../composables/project/api-testing/scene/useStepEditorDrawer.ts?raw'

describe('StepEditorDrawer 依赖列表错误边界', () => {
  it('变量和接口候选由 composable 处理竞态/消息，页面提供重试', () => {
    expect(composableSource).toContain('variablesRequestId')
    expect(composableSource).toContain('interfaceRequestId')
    expect(composableSource).toContain('ElMessage.error(message)')
    expect(composableSource).toContain('retryStepVariables')
    expect(composableSource).toContain('retryInterfaces')
    expect(pageSource).toContain('v-if="variablesError"')
    expect(pageSource).toContain('v-if="interfaceError"')
    expect(pageSource).toContain('@click="retryStepVariables"')
    expect(pageSource).toContain('@click="retryInterfaces"')
  })
})
