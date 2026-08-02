/**
 * AI 生成抽屉的多模式配置（纯对象，便于单测）：
 * 生成子树（US-AI-001）/ 补全步骤（US-AI-002）
 * 共用 SSE→预览→勾选→挂载链路，差异集中在本表。
 */
export type AiPanelMode = 'generate' | 'complete'

export interface AiPanelBodyContext {
  docId: string
  targetNodeId: string
  text: string
  modelId: string | null
  /** 需求池条目 ID（US-AI-004）：generate/complete 消费，为空时省略 */
  requirementIds?: string[]
}

export interface AiPanelModeConfig {
  title: string
  /** 相对 /api 的 SSE 路径 */
  url: string
  inputPlaceholder: string
  /** 文本可空即可发起（补全步骤的补充上下文为可选） */
  inputOptional: boolean
  /** 生成结果为空时的提示文案 */
  emptyResultMessage: string
  buildBody(context: AiPanelBodyContext): Record<string, unknown>
}

export const AI_PANEL_MODES: Record<AiPanelMode, AiPanelModeConfig> = {
  generate: {
    title: 'AI 生成用例',
    url: '/project/ai/cases/generate',
    inputPlaceholder: '输入需求描述或模块说明，AI 将生成包含前置条件、执行步骤、预期结果的用例子树',
    inputOptional: false,
    emptyResultMessage: 'AI 未生成任何用例，请补充需求描述后重试',
    buildBody: ({ docId, targetNodeId, text, modelId, requirementIds }) => ({
      documentId: docId,
      targetNodeId,
      requirementText: text,
      ...(requirementIds?.length ? { requirementIds } : {}),
      modelId,
    }),
  },
  complete: {
    title: 'AI 补全步骤',
    url: '/project/ai/cases/complete-steps',
    inputPlaceholder: '可粘贴临时补充上下文（可留空，AI 将基于用例标题与文档上下文补全）',
    inputOptional: true,
    emptyResultMessage: '既有步骤已完整，无需补全',
    buildBody: ({ docId, targetNodeId, text, modelId, requirementIds }) => ({
      documentId: docId,
      nodeId: targetNodeId,
      extraText: text || null,
      ...(requirementIds?.length ? { requirementIds } : {}),
      modelId,
    }),
  },
}
