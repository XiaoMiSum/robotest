import type {
  AiAssistFieldPair,
  AiAssistFieldName,
  AiAssistSourceRef,
  AiAssistSuggestion,
  AiAssistTaskType,
  AiCaseOptimizeField,
  AiCaseOptimizeNodeType,
  AiCaseOptimizeOperation,
  AiCaseOptimizeOperationSuggestion,
  AiCaseOptimizeSuggestion,
  AiPlanOrderItem,
  AiPlanOrderSuggestion,
} from '@/types'

/** 旧版字段键仅用于任务详情审核视图；脑图面板消费节点操作指令 */
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

export const CASE_OPTIMIZE_FIELD_LABEL: Record<AiCaseOptimizeField, string> = {
  title: '标题',
  priority: '优先级',
  tags: '标签',
}

export const CASE_OPTIMIZE_NODE_TYPE_LABEL: Record<AiCaseOptimizeNodeType, string> = {
  precondition: '前置条件',
  step: '操作步骤',
  expected: '预期结果',
  normal: '普通节点',
}

const CASE_OPTIMIZE_FIELDS: AiCaseOptimizeField[] = ['title', 'priority', 'tags']
const CASE_OPTIMIZE_NODE_TYPES: AiCaseOptimizeNodeType[] = [
  'precondition', 'step', 'expected', 'normal',
]

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

/** existing / suggested 兼容字符串（title / priority）与列表（tags） */
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

/** 任务详情旧版审核视图的字段模型，节点操作提案不复用 */
export function readCaseOptimize(content: Record<string, unknown>): AiCaseOptimizeSuggestion {
  const fields = asRecord(content['fields'])
  const priority = asRecord(content['priority'])
  return {
    nodeId: readString(content, 'nodeId'),
    fields: {
      precondition: readPair(fields['precondition']),
      steps: readPair(fields['steps']),
      expected: readPair(fields['expected']),
      tags: readPair(fields['tags']),
    },
    priority: {
      current: readString(priority, 'current'),
      suggested: readString(priority, 'suggested'),
      reason: readString(priority, 'reason'),
    },
    sourceRefs: readSourceRefs(content['sourceRefs']),
  }
}

function readOperation(raw: unknown): AiCaseOptimizeOperation | null {
  const source = asRecord(raw)
  const op = readString(source, 'op')
  if (op === 'update') {
    const nodeId = readString(source, 'nodeId')
    const field = readString(source, 'field') as AiCaseOptimizeField
    if (!nodeId || !CASE_OPTIMIZE_FIELDS.includes(field)) return null
    return {
      op,
      nodeId,
      field,
      existing: readValues(source['existing']),
      suggested: readValues(source['suggested']),
      path: readString(source, 'path'),
      reason: readString(source, 'reason'),
    }
  }
  if (op === 'add') {
    const parentId = readString(source, 'parentId')
    const nodeType = readString(source, 'nodeType') as AiCaseOptimizeNodeType
    const title = readString(source, 'title')
    if (!parentId || !title || !CASE_OPTIMIZE_NODE_TYPES.includes(nodeType)) return null
    return {
      op,
      parentId,
      nodeType,
      title,
      path: readString(source, 'path'),
      reason: readString(source, 'reason'),
    }
  }
  return null
}

export function readCaseOptimizeOperationList(raw: unknown): AiCaseOptimizeOperation[] {
  if (!Array.isArray(raw)) return []
  return raw.map(readOperation).filter((op): op is AiCaseOptimizeOperation => op !== null)
}

export function readCaseOptimizeOperations(
  content: Record<string, unknown>,
): AiCaseOptimizeOperationSuggestion {
  return {
    nodeId: readString(content, 'nodeId'),
    operations: readCaseOptimizeOperationList(content['operations']),
    sourceRefs: readSourceRefs(content['sourceRefs']),
  }
}

/** 编辑后采纳的载荷只写操作清单，定位字段沿用提案（详设 3.2 / 4.2） */
export function readWireOperations(content: Record<string, unknown>): AiCaseOptimizeOperation[] {
  return readCaseOptimizeOperationList(content['operations'])
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
  if (kind === 'case_optimize') return readCaseOptimizeOperations(content)
  return readPlanOrder(content)
}

export function isCaseOptimizeSuggestion(
  view: AiAssistSuggestion | null,
): view is AiCaseOptimizeOperationSuggestion {
  return view !== null && 'operations' in view
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

/** 更新值与现状相同视为无实质变更（新增始终视为变更），用于「全部一致」判定与徽标 */
export function caseOptimizeOperationUnchanged(op: AiCaseOptimizeOperation): boolean {
  return op.op === 'update' && sameValues(op.existing, op.suggested)
}

/** 仅同值标题更新折叠为「无变化」；同级 / 标签一致照常展示以可采纳留痕（交互 2.1.1） */
export function caseOptimizeOperationFoldable(op: AiCaseOptimizeOperation): boolean {
  return op.op === 'update' && op.field === 'title' && sameValues(op.existing, op.suggested)
}

export function caseOptimizeOperationLabel(op: AiCaseOptimizeOperation): string {
  return op.op === 'update'
    ? CASE_OPTIMIZE_FIELD_LABEL[op.field]
    : `新增${CASE_OPTIMIZE_NODE_TYPE_LABEL[op.nodeType]}`
}

/** 采纳前后顺序对照：按位置比对，返回发生变化的条目数（交互 2.2 前后对照） */
export function planOrderMovedCount(before: string[], after: string[]): number {
  return after.filter((title, index) => before[index] !== title).length
}
