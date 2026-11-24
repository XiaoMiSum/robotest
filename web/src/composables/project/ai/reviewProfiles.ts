import {
  caseOptimizeOperationLabel,
  caseOptimizeOperationUnchanged,
  readCaseGeneration,
  readCaseOptimizeOperations,
  readPlanOrder,
  readReview,
} from '@/composables/project/ai/assistPresentation'
import type { AiPlanOrderItem } from '@/types'

/** 编辑字段的输入形态；module 为已有模块树 / 新增模块名的二选一，ranks 由有序 rankItems 驱动 */
export type ProposalEditFieldType =
  | 'text'
  | 'textarea'
  | 'select'
  | 'tree'
  | 'module'
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

export interface ProposalReviewProfile {
  taskType: string
  title: string
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

function readBoolean(source: Record<string, unknown>, key: string): boolean {
  return source[key] === true
}

function readStrings(raw: unknown): string[] {
  if (typeof raw === 'string') return raw.trim() ? [raw] : []
  if (!Array.isArray(raw)) return []
  return raw.filter((item): item is string => typeof item === 'string')
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

function requirementProfile(taskType: string, title: string, withNewModule = false): ProposalReviewProfile {
  return {
    taskType,
    title,
    allowAdopt: true,
    allowEdit: true,
    allowReject: true,
    allowBatch: true,
    needModuleTree: true,
    needSystemVersion: true,
    editFields: [
      { key: 'title', label: '标题', type: 'text', required: true, maxlength: 300 },
      { key: 'description', label: '描述', type: 'textarea' },
      {
        key: 'moduleId',
        label: withNewModule ? '模块归属' : '所属模块',
        type: withNewModule ? 'module' : 'tree',
      },
      { key: 'priority', label: '优先级', type: 'select', options: PRIORITY_OPTIONS },
    ],
    parse(content, fallbackTitle) {
      const parsed = emptyParsed(fallbackTitle)
      parsed.description = readString(content, 'description')
      const title = readString(content, 'title') || readString(content, 'name') || fallbackTitle
      const moduleId = readString(content, 'moduleId')
      const moduleName = readString(content, 'moduleName')
      const newModule = readBoolean(content, 'newModule')
      const priority = readString(content, 'priority')
      if (moduleId) parsed.fields.push({ label: '模块 ID', value: moduleId })
      if (withNewModule && newModule) parsed.fields.push({ label: '新增模块', value: '是' })
      if (priority) parsed.fields.push({ label: '优先级', value: priority })
      const sourceRef = readString(content, 'sourceRef')
      if (sourceRef) parsed.fields.push({ label: '来源引用', value: sourceRef })
      parsed.editValues = {
        title,
        description: parsed.description,
        moduleId,
        moduleName,
        newModule,
        priority,
      }
      return parsed
    },
    buildEditedContent(values, content) {
      const payload: Record<string, unknown> = {}
      const title = typeof values.title === 'string' ? values.title.trim() : ''
      const description = typeof values.description === 'string' ? values.description.trim() : ''
      const moduleId = typeof values.moduleId === 'string' ? values.moduleId : ''
      const priority = typeof values.priority === 'string' ? values.priority : ''
      if (title) payload.title = title
      if (description) payload.description = description
      if (moduleId) payload.moduleId = moduleId
      if (withNewModule) {
        const moduleName = typeof values.moduleName === 'string'
          ? values.moduleName.trim() : readString(content, 'moduleName')
        payload.moduleName = moduleName
        payload.newModule = typeof values.newModule === 'boolean'
          ? values.newModule : readBoolean(content, 'newModule')
      }
      if (priority) payload.priority = priority
      return payload
    },
  }
}

/** 计划排序：rankItems 驱动人工调整，提交 { items: [{ nodeId, suggestedRank }] } */
const planOrderProfile: ProposalReviewProfile = {
  taskType: 'plan_order',
  title: '执行顺序审核',
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

/** AI 评审采纳在评审详情页面板执行，任务详情仅只读展示逐用例建议（AI 评审详设 5.2） */
const reviewCaseReviewProfile: ProposalReviewProfile = {
  ...readonlyProfile('review_case_review', 'AI 评审结果'),
  parse(content, fallbackTitle) {
    const parsed = emptyParsed(fallbackTitle)
    const view = readReview(content)
    parsed.description = view.reason
    parsed.fields.push({ label: '用例', value: view.caseTitle || fallbackTitle })
    parsed.fields.push({
      label: '建议标记',
      value: view.suggestedMark === 'pass' ? '通过' : '不通过',
    })
    if (view.issues.length > 0) {
      parsed.fields.push({
        label: '问题清单',
        value: view.issues
          .map((issue) => `${issue.severity} · ${issue.category} · ${issue.description} · ${issue.path}`)
          .join('；'),
      })
    }
    parsed.sourceRefs = view.sourceRefs.map((ref) => ({ ...ref, changed: false }))
    return parsed
  },
}

/** 只读分诊 / 解析类提案：不提供确认动作，仅结构化预览 */
function readonlyProfile(taskType: string, title: string): ProposalReviewProfile {
  return {
    taskType,
    title,
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

/** 用例优化采纳在脑图面板执行，任务详情仅只读展示节点操作提案（详设 3.2 / 4.2） */
const caseOptimizeProfile: ProposalReviewProfile = {
  ...readonlyProfile('case_optimize', '用例优化结果'),
  parse(content, fallbackTitle) {
    const parsed = emptyParsed(fallbackTitle)
    const view = readCaseOptimizeOperations(content)
    parsed.fields.push({ label: '用例节点', value: view.nodeId })
    parsed.compare = view.operations.map((op) => ({
      label: caseOptimizeOperationLabel(op),
      existing: op.op === 'update' ? joinValues(op.existing) : '—',
      suggested: op.op === 'update' ? joinValues(op.suggested) : `新增：${op.title}`,
      changed: op.op === 'add' || !caseOptimizeOperationUnchanged(op),
    }))
    parsed.sourceRefs = view.sourceRefs.map((ref) => ({ ...ref, changed: false }))
    return parsed
  },
}

/** 脑图生成用例的节点插入在脑图面板执行，任务详情仅只读展示用例属性（详设 3.4 / 4.4） */
const mindmapCaseGenerationProfile: ProposalReviewProfile = {
  ...readonlyProfile('mindmap_case_generation', '脑图生成用例结果'),
  parse(content, fallbackTitle) {
    const parsed = emptyParsed(fallbackTitle)
    const view = readCaseGeneration(content)
    if (view.precondition.length) parsed.fields.push({ label: '前置条件', value: joinValues(view.precondition) })
    parsed.fields.push({ label: '优先级', value: view.priority || '—' })
    parsed.fields.push({ label: '操作步骤', value: joinValues(view.steps) })
    parsed.fields.push({ label: '预期结果', value: joinValues(view.expected) })
    parsed.fields.push({ label: '标签', value: joinValues(view.tags) })
    parsed.sourceRefs = view.sourceRefs.map((ref) => ({ ...ref, changed: false }))
    return parsed
  },
}

const REGISTRY: Record<string, ProposalReviewProfile> = {
  requirement_import: requirementProfile('requirement_import', '导入结果审核', true),
  requirement_split: requirementProfile('requirement_split', '拆分结果审核'),
  case_optimize: caseOptimizeProfile,
  mindmap_case_generation: mindmapCaseGenerationProfile,
  plan_order: planOrderProfile,
  review_case_review: reviewCaseReviewProfile,
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
