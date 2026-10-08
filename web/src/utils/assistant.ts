import type { AiAssistantClarify, AiAssistantMessage } from '@/types'

/** 空会话引导示例（交互 05 §2.4）：点击只回填输入框，由用户确认后发送 */
export const ASSISTANT_EXAMPLES: string[] = [
  '帮我建一个登录评审',
  '生成一个登录模块的回归计划',
  '查询上周计划的执行进度',
]

/**
 * 澄清消息落盘格式（详设 3.5 / AssistantParseHandler）：反问 + 空行 + "- 选项" 行。
 * 从末尾识别连续选项行，容忍反问正文内部出现空行；无选项时整段即反问。
 */
export function parseClarifyContent(content: string | null): AiAssistantClarify {
  const text = (content ?? '').trim()
  if (!text) return { question: '', options: [] }
  const lines = text.split(/\r?\n/)
  let cursor = lines.length
  while (cursor > 0 && lines[cursor - 1].trim().startsWith('- ')) cursor -= 1
  if (cursor === lines.length || cursor === 0) return { question: text, options: [] }
  let questionEnd = cursor
  while (questionEnd > 0 && lines[questionEnd - 1].trim() === '') questionEnd -= 1
  const question = lines.slice(0, questionEnd).join('\n').trim()
  if (!question) return { question: text, options: [] }
  const options = lines
    .slice(cursor)
    .map((line) => line.trim().slice(2))
    .filter((option) => option.length > 0)
  return { question, options }
}

/**
 * 澄清反问是唯一以「assistant + done + 无意图 + 无引用」落盘的消息（详设 3.5）：
 * done 的问答必带 citations（可能为空数组），失败消息状态为 error，预览消息必带 intent。
 */
export function isClarifyMessage(message: AiAssistantMessage): boolean {
  return (
    message.role === 'assistant' &&
    message.status === 'done' &&
    message.intent === null &&
    message.citations === null
  )
}

/**
 * 引用跳转映射（用户确认口径：可定位类型展开 + 跳转，其余仅展开原文）：
 * mindmap_document 走功能测试页外部跳参消费 documentId，module / test_case 无唯一详情页不跳转。
 */
export function citationRoute(
  type: string | undefined,
  id: string | undefined,
): string | null {
  if (!type || !id) return null
  switch (type) {
    case 'requirement':
      return `/workspace/projects/requirements/${id}`
    case 'test_review':
      return `/workspace/projects/reviews/${id}`
    case 'test_plan':
      return `/workspace/projects/plans/${id}`
    case 'mindmap_document':
      return `/workspace/projects/functional-testing?documentId=${id}`
    default:
      return null
  }
}

/** 动作展示标签：intent.kind 与回执 action 同枚举（详设 4.3），未登记值原样展示 */
const ASSISTANT_ACTION_LABELS: Record<string, string> = {
  create_case: '创建用例',
  update_case: '修改用例',
  complete_case: '补全用例',
  batch_tag: '批量标记',
  create_review: '创建评审',
  adjust_review: '调整评审',
  view_review_progress: '查看评审进度',
  create_plan: '创建计划',
  adjust_plan: '调整计划',
  view_plan_progress: '查看计划进度',
}

export function assistantActionLabel(action: string): string {
  return ASSISTANT_ACTION_LABELS[action] ?? action
}

/** 预览变更字段的展示标签，未登记字段原样展示 */
const INTENT_FIELD_LABELS: Record<string, string> = {
  title: '标题',
  priority: '优先级',
  type: '类型',
  precondition: '前置条件',
  steps: '步骤',
  expected: '预期结果',
}

export function intentFieldLabel(field: string): string {
  return INTENT_FIELD_LABELS[field] ?? field
}

/** 变更值可为任意 JSON（服务端只对字符串截断，详设 4.3）：统一序列化为展示文本 */
export function formatIntentChangeValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return value.map((item) => formatIntentChangeValue(item)).join('\n')
  const text = JSON.stringify(value)
  return typeof text === 'string' ? text : String(value)
}

/** replace 值按「原值 → 新值」拆分（详设 4.3 展示口径）；未携带原值时返回 null 按整体变更展示 */
export function splitReplaceChange(value: unknown): { before: string; after: string } | null {
  const text = formatIntentChangeValue(value)
  const separator = ' → '
  const index = text.indexOf(separator)
  if (index < 0) return null
  const before = text.slice(0, index).trim()
  const after = text.slice(index + separator.length).trim()
  if (!before || !after) return null
  return { before, after }
}
