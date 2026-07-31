import { describe, expect, it } from 'vitest'
import {
  AI_PANEL_MODES,
  IMPORT_TEXT_MAX_LENGTH,
  validateImportText,
  type AiPanelBodyContext,
} from './aiPanelModes'

const context: AiPanelBodyContext = {
  docId: 'doc-1',
  targetNodeId: 'node-1',
  text: '需求文本',
  modelId: 'model-1',
}

describe('AI_PANEL_MODES 请求体组装', () => {
  it('generate 模式映射 requirementText', () => {
    expect(AI_PANEL_MODES.generate.buildBody(context)).toEqual({
      documentId: 'doc-1',
      targetNodeId: 'node-1',
      requirementText: '需求文本',
      modelId: 'model-1',
    })
  })

  it('complete 模式映射 nodeId 与 extraText，空文本转 null', () => {
    expect(AI_PANEL_MODES.complete.buildBody(context)).toEqual({
      documentId: 'doc-1',
      nodeId: 'node-1',
      extraText: '需求文本',
      modelId: 'model-1',
    })
    expect(AI_PANEL_MODES.complete.buildBody({ ...context, text: '' })).toMatchObject({
      extraText: null,
    })
  })

  it('import 模式映射 text', () => {
    expect(AI_PANEL_MODES.import.buildBody({ ...context, modelId: null })).toEqual({
      documentId: 'doc-1',
      targetNodeId: 'node-1',
      text: '需求文本',
      modelId: null,
    })
  })

  it('仅 complete 模式允许空文本发起', () => {
    expect(AI_PANEL_MODES.generate.inputOptional).toBe(false)
    expect(AI_PANEL_MODES.complete.inputOptional).toBe(true)
    expect(AI_PANEL_MODES.import.inputOptional).toBe(false)
  })

  it('三种模式指向各自的 SSE 路径', () => {
    expect(AI_PANEL_MODES.generate.url).toBe('/project/ai/cases/generate')
    expect(AI_PANEL_MODES.complete.url).toBe('/project/ai/cases/complete-steps')
    expect(AI_PANEL_MODES.import.url).toBe('/project/ai/minder/import')
  })
})

describe('validateImportText 导入文本预校验', () => {
  it('空白文本不允许', () => {
    expect(validateImportText('')).not.toBeNull()
    expect(validateImportText('   \n\t ')).not.toBeNull()
  })

  it('上限内的文本合法', () => {
    expect(validateImportText('登录模块\t输入账号\t登录成功')).toBeNull()
    expect(validateImportText('字'.repeat(IMPORT_TEXT_MAX_LENGTH))).toBeNull()
  })

  it('超出上限返回含长度信息的提示（交互设计 4.5）', () => {
    const error = validateImportText('字'.repeat(IMPORT_TEXT_MAX_LENGTH + 1))
    expect(error).toContain(String(IMPORT_TEXT_MAX_LENGTH))
  })
})
