/**
 * AI 生成抽屉的多模式配置（纯对象，便于单测）：
 * 生成子树（US-AI-001）/ 补全步骤（US-AI-002）/ 外部文本导入（US-AI-016）
 * 共用 SSE→预览→勾选→挂载链路，差异集中在本表。
 */
export type AiPanelMode = 'generate' | 'complete' | 'import'

/** 与后端系统配置项 importTextMaxLength 默认值一致（提交前预校验，最终以服务端为准） */
export const IMPORT_TEXT_MAX_LENGTH = 20000

export interface AiPanelBodyContext {
  docId: string
  targetNodeId: string
  text: string
  modelId: string | null
}

export interface AiPanelModeConfig {
  title: string
  /** 相对 /api 的 SSE 路径 */
  url: string
  inputPlaceholder: string
  /** 文本可空即可发起（补全步骤的补充上下文为可选） */
  inputOptional: boolean
  /** 输入区只读（导入模式由 AI 指令输入区带入原文，重新生成沿用） */
  inputReadonly: boolean
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
    inputReadonly: false,
    emptyResultMessage: 'AI 未生成任何用例，请补充需求描述后重试',
    buildBody: ({ docId, targetNodeId, text, modelId }) => ({
      documentId: docId,
      targetNodeId,
      requirementText: text,
      modelId,
    }),
  },
  complete: {
    title: 'AI 补全步骤',
    url: '/project/ai/cases/complete-steps',
    inputPlaceholder: '可粘贴临时补充上下文（可留空，AI 将基于用例标题与文档上下文补全）',
    inputOptional: true,
    inputReadonly: false,
    emptyResultMessage: '既有步骤已完整，无需补全',
    buildBody: ({ docId, targetNodeId, text, modelId }) => ({
      documentId: docId,
      nodeId: targetNodeId,
      extraText: text || null,
      modelId,
    }),
  },
  import: {
    title: 'AI 文本导入',
    url: '/project/ai/minder/import',
    inputPlaceholder: '',
    inputOptional: false,
    inputReadonly: true,
    emptyResultMessage: '未能解析出用例结构，请调整文本格式',
    buildBody: ({ docId, targetNodeId, text, modelId }) => ({
      documentId: docId,
      targetNodeId,
      text,
      modelId,
    }),
  },
}

/** 导入文本预校验：不合法返回错误提示，合法返回 null（交互设计 4.5 超长禁用执行） */
export function validateImportText(text: string): string | null {
  if (!text.trim()) return '请输入或粘贴要解析的文本'
  if (text.length > IMPORT_TEXT_MAX_LENGTH) {
    return `文本长度 ${text.length} 超出上限 ${IMPORT_TEXT_MAX_LENGTH} 字符，请拆分后导入`
  }
  return null
}
