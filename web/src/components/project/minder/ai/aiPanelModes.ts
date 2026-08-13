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
  /** 操作行主按钮文案：发起态（生成/补全区分，交互设计 3.1） */
  startButtonText: string
  /** 完成态底部操作条重试按钮文案（交互设计 2.2：完成态发起按钮移至底部操作条） */
  retryButtonText: string
  /** 预览弹窗底部确认按钮文案（挂载/追加区分） */
  confirmButtonText: string
  /** 预览弹窗标题（生成结果预览 / 补全结果预览，交互设计 3.1） */
  previewTitle: string
  /** 预览弹窗勾选计数单位（个用例 / 项，交互设计 2.3） */
  countLabel: string
  /** 完成态输出区提示文字：进度条完成态不显示，以文字替代（交互设计 2.2） */
  doneTipMessage: string
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
    startButtonText: '开始生成',
    retryButtonText: '重新生成',
    confirmButtonText: '确认挂载',
    previewTitle: '生成结果预览',
    countLabel: '个用例',
    doneTipMessage: '生成完成，点击下方「查看预览」在脑图中核对并勾选取舍',
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
    title: 'AI 补全用例',
    url: '/project/ai/cases/complete-steps',
    inputPlaceholder: '可粘贴临时补充上下文（可留空，AI 将基于用例标题与文档上下文补全）',
    inputOptional: true,
    startButtonText: '开始补全',
    retryButtonText: '重新补全',
    confirmButtonText: '确认追加',
    previewTitle: '补全结果预览',
    countLabel: '项',
    doneTipMessage: '补全完成，点击下方「查看预览」在脑图中核对并勾选取舍',
    emptyResultMessage: '既有前置/步骤/预期已完整，无需补全',
    buildBody: ({ docId, targetNodeId, text, modelId, requirementIds }) => ({
      documentId: docId,
      nodeId: targetNodeId,
      extraText: text || null,
      ...(requirementIds?.length ? { requirementIds } : {}),
      modelId,
    }),
  },
}
