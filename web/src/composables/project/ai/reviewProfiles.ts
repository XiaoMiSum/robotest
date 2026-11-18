import {
  ASSIST_FIELD_NAMES,
  assistFieldLabel,
  readCaseOptimize,
  readPlanOrder,
} from '@/composables/project/ai/assistPresentation'
import type { AiCaseOptimizeSuggestion, AiPlanOrderItem } from '@/types'

/** 编辑字段的输入形态；lines 按行拆为字符串数组，ranks 由有序 rankItems 驱动 */
export type ProposalEditFieldType =
  | 'text'
  | 'textarea'
  | 'select'
  | 'tree'
  | 'lines'
  | 'ranks'

export interface ProposalEditField {
  key: string
  label: string
  type: ProposalEditFieldType
  required?: boolean
  options?: Array<{ value: string; label: string }>
  placeholder?: string
  maxlength?: number
}

export interface ProposalCompareRow {
  label: string
  existing: string
  suggested: string
  changed: boolean
}

export interface ProposalRankItem {
  nodeId: string
  title: string
  rank: number
  reason: string
}

export interface ProposalSourceRefView {
  id: string
  title: string
  quote: string
  changed: boolean
}

/** 提案内容的结构化视图：预览、编辑初值与警示均由策略解析，未知字段不丢失 */
export interface ProposalParsed {
  description: string
  fields: Array<{ label: string; value: string }>
  compare: ProposalCompareRow[]
  rankItems: ProposalRankItem[]
  sourceRefs: ProposalSourceRefView[]
  warnings: string[]
  editValues: Record<string, unknown>
}

/** 审核面板形态：生成链三层树、缺陷批量分类专用面板、其余通用面板 */
export type ProposalReviewPanelKind = 'generic' | 'tree' | 'bug'

export interface ProposalReviewProfile {
  taskType: string
  title: string
  panel: ProposalReviewPanelKind
  tree: boolean
  allowAdopt: boolean
  allowEdit: boolean
  allowReject: boolean
  allowBatch: boolean
  needModuleTree: boolean
  needSystemVersion: boolean
  editFields: ProposalEditField[]
  parse: (content: Record<string, unknown>, fallbackTitle: string) => ProposalParsed
  buildEditedContent: (
    values: Record<string, unknown>,
    content: Record<string, unknown>,
  ) => Record<string, unknown>
}

function readString(source: Record<string, unknown>, key: string): string {
  const value = source[key]
  return typeof value === 'string' ? value : ''
}

function readStrings(raw: unknown): string[] {
  if (typeof raw === 'string') return raw.trim() ? [raw] : []
  if (!Array.isArray(raw)) return []
  return raw.filter((item): item is string => typeof item === 'string')
}

function toLines(value: unknown): string[] {
  if (Array.isArray(value)) return readStrings(value)
  if (typeof value !== 'string') return []
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

function joinValues(values: string[]): string {
  return values.length > 0 ? values.join('、') : '—'
}

function emptyParsed(fallbackTitle: string): ProposalParsed {
  return {
    description: '',
    fields: [],
    compare: [],
    rankItems: [],
    sourceRefs: [],
    warnings: [],
    editValues: { title: fallbackTitle },
  }
}

/** 需求域：标题 / 描述 / 模块 / 优先级，版本走 target 单独提交 */
const PRIORITY_OPTIONS = [
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
]

function requirementProfile(taskType: string, title: string): ProposalReviewProfile {
  return {
    taskType,
    title,
    panel: 'generic',
    tree: false,
    allowAdopt: true,
    allowEdit: true,
    allowReject: true,
    allowBatch: true,
    needModuleTree: true,
    needSystemVersion: true,
    editFields: [
      { key: 'title', label: '标题', type: 'text', required: true, maxlength: 300 },
      { key: 'description', label: '描述', type: 'textarea' },
      { key: 'moduleId', label: '所属模块', type: 'tree' },
      { key: 'priority', label: '优先级', type: 'select', options: PRIORITY_OPTIONS },
    ],
    parse(content, fallbackTitle) {
      const parsed = emptyParsed(fallbackTitle)
      parsed.description = readString(content, 'description')
      const title = readString(content, 'title') || readString(content, 'name') || fallbackTitle
      const moduleId = readString(content, 'moduleId')
      const priority = readString(content, 'priority')
      if (moduleId) parsed.fields.push({ label: '模块 ID', value: moduleId })
      if (priority) parsed.fields.push({ label: '优先级', value: priority })
      const sourceRef = readString(content, 'sourceRef')
      if (sourceRef) parsed.fields.push({ label: '来源引用', value: sourceRef })
      parsed.editValues = {
        title,
        description: parsed.description,
        moduleId,
        priority,
      }
      return parsed
    },
    buildEditedContent(values) {
      const payload: Record<string, unknown> = {}
      const title = typeof values.title === 'string' ? values.title.trim() : ''
      const description = typeof values.description === 'string' ? values.description.trim() : ''
      const moduleId = typeof values.moduleId === 'string' ? values.moduleId : ''
      const priority = typeof values.priority === 'string' ? values.priority : ''
      if (title) payload.title = title
      if (description) payload.description = description
      if (moduleId) payload.moduleId = moduleId
      if (priority) payload.priority = priority
      return payload
    },
  }
}

/** 用例优化：编辑 fields（前置条件 / 步骤 / 预期 / 标签）与优先级，载荷按详设 3.6.5 组装 */
const CASE_PRIORITY_OPTIONS = ['P0', 'P1', 'P2', 'P3'].map((value) => ({ value, label: value }))

const caseOptimizeProfile: ProposalReviewProfile = {
  taskType: 'case_optimize',
  title: '用例优化审核',
  panel: 'generic',
  tree: false,
  allowAdopt: true,
  allowEdit: true,
  allowReject: true,
  allowBatch: false,
  needModuleTree: false,
  needSystemVersion: false,
  editFields: [
    { key: 'precondition', label: '前置条件', type: 'textarea' },
    { key: 'steps', label: '操作步骤（每行一步）', type: 'lines' },
    { key: 'expected', label: '预期结果（每行一条）', type: 'lines' },
    { key: 'tags', label: '标签（每行一个）', type: 'lines' },
    { key: 'priority', label: '优先级', type: 'select', options: CASE_PRIORITY_OPTIONS },
  ],
  parse(content, fallbackTitle) {
    const parsed = emptyParsed(fallbackTitle)
    const view: AiCaseOptimizeSuggestion = readCaseOptimize(content)
    parsed.compare = ASSIST_FIELD_NAMES.map((name) => {
      const pair = view.fields[name]
      return {
        label: assistFieldLabel(name),
        existing: joinValues(pair.existing),
        suggested: joinValues(pair.suggested),
        changed: pair.existing.join('\n') !== pair.suggested.join('\n'),
      }
    })
    const priorityChanged =
      view.priority.suggested !== '' && view.priority.suggested !== view.priority.current
    parsed.compare.push({
      label: '优先级',
      existing: view.priority.current || '—',
      suggested: view.priority.suggested || '—',
      changed: priorityChanged,
    })
    if (view.priority.reason) {
      parsed.fields.push({ label: '级别依据', value: view.priority.reason })
    }
    parsed.sourceRefs = view.sourceRefs.map((ref) => ({
      id: ref.id,
      title: ref.title,
      quote: ref.quote,
      changed: false,
    }))
    parsed.editValues = {
      precondition: view.fields.precondition.suggested.join('\n'),
      steps: view.fields.steps.suggested.join('\n'),
      expected: view.fields.expected.suggested.join('\n'),
      tags: view.fields.tags.suggested.join('\n'),
      priority: view.priority.suggested,
    }
    return parsed
  },
  buildEditedContent(values) {
    const fields: Record<string, string | string[]> = {}
    const precondition = typeof values.precondition === 'string' ? values.precondition.trim() : ''
    if (precondition) fields.precondition = precondition
    for (const key of ['steps', 'expected', 'tags']) {
      const lines = toLines(values[key])
      if (lines.length > 0) fields[key] = lines
    }
    const payload: Record<string, unknown> = {}
    if (Object.keys(fields).length > 0) payload.fields = fields
    const priority = typeof values.priority === 'string' ? values.priority : ''
    if (priority) payload.priority = priority
    return payload
  },
}

/** 计划排序：rankItems 驱动人工调整，提交 { items: [{ nodeId, suggestedRank }] } */
const planOrderProfile: ProposalReviewProfile = {
  taskType: 'plan_order',
  title: '执行顺序审核',
  panel: 'generic',
  tree: false,
  allowAdopt: true,
  allowEdit: true,
  allowReject: true,
  allowBatch: false,
  needModuleTree: false,
  needSystemVersion: false,
  editFields: [{ key: 'items', label: '执行顺序', type: 'ranks' }],
  parse(content, fallbackTitle) {
    const parsed = emptyParsed(fallbackTitle)
    const view = readPlanOrder(content)
    const sorted: AiPlanOrderItem[] = [...view.items].sort(
      (left, right) => left.suggestedRank - right.suggestedRank,
    )
    parsed.rankItems = sorted.map((item) => ({
      nodeId: item.nodeId,
      title: item.caseTitle || item.nodeId,
      rank: item.suggestedRank,
      reason: item.reason,
    }))
    parsed.compare = view.beforeOrder.map((title, index) => ({
      label: `第 ${index + 1} 位`,
      existing: title,
      suggested: view.afterOrder[index] ?? '—',
      changed: title !== view.afterOrder[index],
    }))
    parsed.fields.push({ label: '建议条目', value: `${sorted.length} 项` })
    parsed.editValues = { items: parsed.rankItems.map((item) => ({ ...item })) }
    return parsed
  },
  buildEditedContent(values) {
    const items: ProposalRankItem[] = Array.isArray(values.items)
      ? (values.items as ProposalRankItem[])
      : []
    return {
      items: items.map((item, index) => ({
        nodeId: item.nodeId,
        suggestedRank: index + 1,
      })),
    }
  },
}

/** 只读分诊 / 总结 / 解析类提案：不提供确认动作，仅结构化预览 */
function readonlyProfile(taskType: string, title: string): ProposalReviewProfile {
  return {
    taskType,
    title,
    panel: 'generic',
    tree: false,
    allowAdopt: false,
    allowEdit: false,
    allowReject: false,
    allowBatch: false,
    needModuleTree: false,
    needSystemVersion: false,
    editFields: [],
    parse(content, fallbackTitle) {
      const parsed = emptyParsed(fallbackTitle)
      parsed.description = readString(content, 'description') || readString(content, 'summary')
      for (const [key, value] of Object.entries(content)) {
        if (['title', 'name', 'description', 'summary'].includes(key)) continue
        if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
          parsed.fields.push({ label: key, value: String(value) })
        } else if (Array.isArray(value)) {
          const values = readStrings(value)
          if (values.length > 0) parsed.fields.push({ label: key, value: values.join('、') })
        }
      }
      return parsed
    },
    buildEditedContent: () => ({}),
  }
}

/** 生成链与缺陷批量分类走专用面板，注册表仅登记其分发与树形态 */
function specializedProfile(
  taskType: string,
  title: string,
  panel: ProposalReviewPanelKind,
  tree: boolean,
): ProposalReviewProfile {
  return {
    ...readonlyProfile(taskType, title),
    panel,
    tree,
    allowAdopt: true,
    allowEdit: true,
    allowReject: true,
    allowBatch: true,
  }
}

const REGISTRY: Record<string, ProposalReviewProfile> = {
  requirement_import: requirementProfile('requirement_import', '导入结果审核'),
  requirement_split: requirementProfile('requirement_split', '拆分结果审核'),
  case_optimize: caseOptimizeProfile,
  plan_order: planOrderProfile,
  test_design_generation: specializedProfile('test_design_generation', '用例生成', 'tree', true),
  bug_classify: specializedProfile('bug_classify', '缺陷批量分类', 'bug', false),
  bug_duplicate_scan: specializedProfile('bug_duplicate_scan', '重复缺陷扫描', 'bug', false),
  bug_triage: readonlyProfile('bug_triage', '缺陷分诊结果'),
  bug_trend_summary: readonlyProfile('bug_trend_summary', '缺陷趋势总结'),
  assistant_parse: readonlyProfile('assistant_parse', '助手解析结果'),
  vector_reindex: readonlyProfile('vector_reindex', '向量索引重建结果'),
}

/**
 * 未登记类型沿用需求域的通用审核能力，避免新增任务类型时审核区整体失效；
 * 已登记类型按各自策略解析内容、校验编辑字段与构造载荷。
 */
export function reviewProfile(taskType: string): ProposalReviewProfile {
  const registered = REGISTRY[taskType]
  if (registered) return registered
  // 未登记类型无 documentMeta，版本输入无识别依据可展示，故关闭
  return { ...requirementProfile(taskType, '提案审核'), needSystemVersion: false }
}

export function isReadonlyProfile(profile: ProposalReviewProfile): boolean {
  return !profile.allowAdopt && !profile.allowEdit && !profile.allowReject
}
