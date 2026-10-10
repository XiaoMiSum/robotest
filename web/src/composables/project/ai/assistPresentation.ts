import type {
  AiAssistExtraNode,
  AiAssistFieldPair,
  AiAssistFieldName,
  AiAssistSourceRef,
  AiAssistSuggestion,
  AiAssistTaskType,
  AiCaseCompleteSuggestion,
  AiCasePrioritySuggestion,
  AiPlanOrderItem,
  AiPlanOrderSuggestion,
} from '@/types'

/** 补全字段渲染顺序（详设 3.2 产物形态） */
export const ASSIST_FIELD_NAMES: AiAssistFieldName[] = [
  'precondition',
  'steps',
  'expected',
  'tags',
]

const ASSIST_FIELD_LABEL: Record<AiAssistFieldName, string> = {
  precondition: '前置条件',
  steps: '操作步骤',
  expected: '预期结果',
  tags: '标签',
}

/** 对照面板行（交互 2.1 两栏对照） */
export interface AssistCompareRow {
  name: AiAssistFieldName
  label: string
  existing: string[]
  suggested: string[]
  /** changed 驱动「新增」徽标与无变化项折叠 */
  changed: boolean
}

function asRecord(raw: unknown): Record<string, unknown> {
  return typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {}
}

function readString(source: Record<string, unknown>, key: string): string {
  const value = source[key]
  return typeof value === 'string' ? value : ''
}

function readStrings(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((item): item is string => typeof item === 'string')
}

/** existing / suggested 兼容字符串（precondition）与列表（steps / expected / tags） */
function readValues(raw: unknown): string[] {
  if (typeof raw === 'string') {
    return raw.trim() ? [raw] : []
  }
  return readStrings(raw)
}

function readPair(raw: unknown): AiAssistFieldPair {
  const source = asRecord(raw)
  return { existing: readValues(source['existing']), suggested: readValues(source['suggested']) }
}

function readExtraNodes(raw: unknown): AiAssistExtraNode[] {
  if (!Array.isArray(raw)) return []
  const nodes: AiAssistExtraNode[] = []
  for (const item of raw) {
    const source = asRecord(item)
    const title = readString(source, 'title').trim()
    if (!title) continue
    nodes.push({ title, isTestCase: source['isTestCase'] === true })
  }
  return nodes
}

function readSourceRefs(raw: unknown): AiAssistSourceRef[] {
  if (!Array.isArray(raw)) return []
  const refs: AiAssistSourceRef[] = []
  for (const item of raw) {
    const source = asRecord(item)
    const id = readString(source, 'id')
    if (!id) continue
    refs.push({ id, title: readString(source, 'title'), quote: readString(source, 'quote') })
  }
  return refs
}

export function readCaseComplete(content: Record<string, unknown>): AiCaseCompleteSuggestion {
  const fields = asRecord(content['fields'])
  return {
    nodeId: readString(content, 'nodeId'),
    fields: {
      precondition: readPair(fields['precondition']),
      steps: readPair(fields['steps']),
      expected: readPair(fields['expected']),
      tags: readPair(fields['tags']),
    },
    extraNodes: readExtraNodes(content['extraNodes']),
    sourceRefs: readSourceRefs(content['sourceRefs']),
  }
}

export function readCasePriority(content: Record<string, unknown>): AiCasePrioritySuggestion {
  return {
    nodeId: readString(content, 'nodeId'),
    current: readString(content, 'current'),
    suggested: readString(content, 'suggested'),
    reason: readString(content, 'reason'),
    sourceRefs: readSourceRefs(content['sourceRefs']),
  }
}

export function readPlanOrder(content: Record<string, unknown>): AiPlanOrderSuggestion {
  const items: AiPlanOrderItem[] = []
  for (const element of Array.isArray(content['items']) ? content['items'] : []) {
    const source = asRecord(element)
    const nodeId = readString(source, 'nodeId')
    if (!nodeId) continue
    const rank = source['suggestedRank']
    items.push({
      nodeId,
      caseTitle: readString(source, 'caseTitle'),
      // 非数值名次按排尾处理，与后端清洗口径一致（详设 3.4）
      suggestedRank: typeof rank === 'number' ? rank : Number.MAX_SAFE_INTEGER,
      reason: readString(source, 'reason'),
    })
  }
  return {
    planId: readString(content, 'planId'),
    items,
    beforeOrder: readStrings(content['beforeOrder']),
    afterOrder: readStrings(content['afterOrder']),
  }
}

/** 产物内容未加载（或已被确认后清空）时返回 null，面板降级为只读占位 */
export function readAssistSuggestion(
  kind: AiAssistTaskType,
  content: Record<string, unknown> | null,
): AiAssistSuggestion | null {
  if (!content) return null
  if (kind === 'case_complete') return readCaseComplete(content)
  if (kind === 'case_priority') return readCasePriority(content)
  return readPlanOrder(content)
}

export function isCaseCompleteSuggestion(
  view: AiAssistSuggestion | null,
): view is AiCaseCompleteSuggestion {
  return view !== null && 'fields' in view
}

export function isCasePrioritySuggestion(
  view: AiAssistSuggestion | null,
): view is AiCasePrioritySuggestion {
  return view !== null && 'suggested' in view
}

export function isPlanOrderSuggestion(view: AiAssistSuggestion | null): view is AiPlanOrderSuggestion {
  return view !== null && 'beforeOrder' in view
}

export function assistFieldLabel(name: AiAssistFieldName): string {
  return ASSIST_FIELD_LABEL[name] ?? name
}

function sameValues(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

/** 与现有内容不同的字段（无变化项默认折叠，交互 2.1.2） */
export function caseCompleteChangedFields(
  fields: Record<AiAssistFieldName, AiAssistFieldPair>,
): AiAssistFieldName[] {
  return ASSIST_FIELD_NAMES.filter((name) => {
    const pair = fields[name]
    return !sameValues(pair.existing, pair.suggested)
  })
}

/** 两栏对照行；labels 顺序即渲染顺序 */
export function caseCompleteCompareRows(
  fields: Record<AiAssistFieldName, AiAssistFieldPair>,
): AssistCompareRow[] {
  return ASSIST_FIELD_NAMES.map((name) => ({
    name,
    label: assistFieldLabel(name),
    existing: fields[name].existing,
    suggested: fields[name].suggested,
    changed: !sameValues(fields[name].existing, fields[name].suggested),
  }))
}

/** 无建议值（模型未响应）按无变化处理，不误导用户去改写既有内容 */
export function casePriorityChanged(suggestion: AiCasePrioritySuggestion): boolean {
  return suggestion.suggested !== '' && suggestion.suggested !== suggestion.current
}

/** 采纳前后顺序对照：按位置比对，返回发生变化的条目数（交互 2.2 前后对照） */
export function planOrderMovedCount(before: string[], after: string[]): number {
  return after.filter((title, index) => before[index] !== title).length
}
