import { describe, expect, it } from 'vitest'
import {
  AI_PANEL_MODES,
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

  it('generate/complete 模式透传需求条目 ID，为空时省略', () => {
    const withReqs = { ...context, requirementIds: ['req-1', 'req-2'] }
    expect(AI_PANEL_MODES.generate.buildBody(withReqs)).toMatchObject({
      requirementIds: ['req-1', 'req-2'],
    })
    expect(AI_PANEL_MODES.complete.buildBody(withReqs)).toMatchObject({
      requirementIds: ['req-1', 'req-2'],
    })
    expect(AI_PANEL_MODES.generate.buildBody(context)).not.toHaveProperty('requirementIds')
    expect(AI_PANEL_MODES.complete.buildBody(context)).not.toHaveProperty('requirementIds')
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

  it('仅 complete 模式允许空文本发起', () => {
    expect(AI_PANEL_MODES.generate.inputOptional).toBe(false)
    expect(AI_PANEL_MODES.complete.inputOptional).toBe(true)
  })

  it('两种模式指向各自的 SSE 路径', () => {
    expect(AI_PANEL_MODES.generate.url).toBe('/project/ai/cases/generate')
    expect(AI_PANEL_MODES.complete.url).toBe('/project/ai/cases/complete-steps')
  })

  it('两模式标题与按钮文案区分（交互设计 3.1）', () => {
    expect(AI_PANEL_MODES.generate.title).toBe('AI 生成用例')
    expect(AI_PANEL_MODES.generate.startButtonText).toBe('开始生成')
    expect(AI_PANEL_MODES.generate.retryButtonText).toBe('重新生成')
    expect(AI_PANEL_MODES.generate.confirmButtonText).toBe('确认挂载')
    expect(AI_PANEL_MODES.complete.title).toBe('AI 补全用例')
    expect(AI_PANEL_MODES.complete.startButtonText).toBe('开始补全')
    expect(AI_PANEL_MODES.complete.retryButtonText).toBe('重新补全')
    expect(AI_PANEL_MODES.complete.confirmButtonText).toBe('确认追加')
    expect(AI_PANEL_MODES.complete.emptyResultMessage).toContain('前置')
  })

  it('两模式完成提示文案齐备', () => {
    expect(AI_PANEL_MODES.generate.doneTipMessage.length).toBeGreaterThan(0)
    expect(AI_PANEL_MODES.complete.doneTipMessage.length).toBeGreaterThan(0)
  })
})
