import type {
  AiProposalConfirmStatus,
  AiTaskDocumentMeta,
  AiTaskExecutionItem,
  AiTaskExecutionStatus,
  AiTaskPlanSnapshot,
  AiTaskStatus,
  AiTraceEventType,
} from '@/types'

// ==================== 任务状态徽标 ====================

export interface AiTaskStatusMeta {
  label: string
  tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

/**
 * 任务状态徽标（交互 2.1.2）：running 执行中、proposals_ready 待采纳、
 * closed 已结束中性灰、aborted 已终止（悬浮终止原因由调用方承载）。
 */
export const AI_TASK_STATUS_META: Record<AiTaskStatus, AiTaskStatusMeta> = {
  running: { label: '执行中', tagType: 'primary' },
  proposals_ready: { label: '待采纳', tagType: 'warning' },
  closed: { label: '已结束', tagType: 'info' },
  aborted: { label: '已终止', tagType: 'danger' },
}

/**
 * 状态徽标：任务 running 且执行挂起（waiting_confirmation）时展示「挂起」，
 * 其余按任务状态取值；未知状态降级原文展示。
 */
export function aiTaskStatusMeta(
  status: string,
  executionStatus?: AiTaskExecutionStatus,
): AiTaskStatusMeta {
  if (status === 'running' && executionStatus === 'waiting_confirmation') {
    return { label: '挂起', tagType: 'info' }
  }
  return AI_TASK_STATUS_META[status as AiTaskStatus] ?? { label: status, tagType: 'info' }
}

// ==================== 执行状态徽标 ====================

export interface AiExecutionStatusMeta {
  label: string
  tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

/** 执行状态徽标（总册 2.6.2）：执行区与轨迹状态变更展示 */
export const AI_EXECUTION_STATUS_META: Record<AiTaskExecutionStatus, AiExecutionStatusMeta> = {
  running: { label: '执行中', tagType: 'primary' },
  waiting_confirmation: { label: '挂起', tagType: 'info' },
  succeeded: { label: '已成功', tagType: 'success' },
  aborted: { label: '已终止', tagType: 'danger' },
}

export function aiExecutionStatusMeta(status: string): AiExecutionStatusMeta {
  return (
    AI_EXECUTION_STATUS_META[status as AiTaskExecutionStatus] ?? {
      label: status,
      tagType: 'info',
    }
  )
}

// ==================== 任务类型 ====================

export interface AiTaskTypeMeta {
  /** 任务名（列表按类型派生） */
  label: string
  /** 能力域标签（列表「类型」列） */
  domain: string
}

/** 任务类型 → 中文名与能力域（详设 3.6.1 枚举） */
export const AI_TASK_TYPE_META: Record<string, AiTaskTypeMeta> = {
  requirement_import: { label: '需求导入', domain: '需求' },
  test_design_generation: { label: '用例生成', domain: '生成链' },
  review_selection: { label: '评审圈选', domain: '生成链' },
  plan_selection: { label: '计划圈选', domain: '生成链' },
  case_complete: { label: '用例补全', domain: '辅助' },
  case_priority: { label: '级别建议', domain: '辅助' },
  plan_order: { label: '计划排序', domain: '辅助' },
  bug_classify: { label: '缺陷批量分类', domain: '缺陷' },
  bug_duplicate_scan: { label: '重复缺陷扫描', domain: '缺陷' },
  bug_triage: { label: '缺陷分诊', domain: '缺陷' },
  bug_trend_summary: { label: '缺陷趋势总结', domain: '缺陷' },
  assistant_parse: { label: '助手解析', domain: '助手' },
  vector_reindex: { label: '向量索引重建', domain: '底座' },
}

export function aiTaskTypeMeta(type: string): AiTaskTypeMeta {
  return AI_TASK_TYPE_META[type] ?? { label: type, domain: '其他' }
}

/** 任务名按类型派生（后端无任务名字段） */
export function aiTaskName(task: { type: string }): string {
  return aiTaskTypeMeta(task.type).label
}

/** 类型筛选下拉选项（枚举全量，顺序与详设 3.6.1 一致） */
export function aiTaskTypeOptions(): Array<{ value: string; label: string }> {
  return Object.entries(AI_TASK_TYPE_META).map(([value, meta]) => ({
    value,
    label: `${meta.label}（${meta.domain}）`,
  }))
}

// ==================== 执行摘要（列表行） ====================

export interface AiExecutionSummary {
  /** 展示文案：步数与最近动作 / 终止原因摘要 */
  text: string
  /** 步数进度百分比（0–100），无预算或非执行中为 null 不渲染进度条 */
  stepPercent: number | null
}

function clampPercent(value: number): number {
  return Math.min(100, Math.max(0, Math.round(value)))
}

/** 计划快照中当前 / 最近一步文案（列表无轨迹，动作取 plan 快照） */
function currentPlanStep(plan: AiTaskPlanSnapshot | null | undefined, steps: number): string {
  const labels = plan?.steps
  if (!labels || labels.length === 0) return ''
  return labels[steps] ?? ''
}

/**
 * 列表执行摘要（交互 2.1.2 执行摘要列）：执行中给步数与最近动作并附步数进度；
 * 挂起给待人工批准；已结束给完成步数；已终止给终止原因摘要。
 */
export function aiExecutionSummary(item: {
  status: AiTaskStatus
  latestExecution?: AiTaskExecutionItem | null
  terminateReason?: { msg: string | null } | null
}): AiExecutionSummary {
  const execution = item.latestExecution ?? null
  if (item.status === 'aborted') {
    const reason = item.terminateReason?.msg || execution?.terminateReason?.msg || ''
    return { text: reason || '已终止', stepPercent: null }
  }
  if (!execution) return { text: '—', stepPercent: null }

  const steps = execution.steps ?? 0
  const maxSteps = execution.budget?.maxSteps ?? null
  const stepText = maxSteps && maxSteps > 0 ? `${steps}/${maxSteps} 步` : `${steps} 步`

  if (execution.status === 'waiting_confirmation') {
    return { text: `${stepText} · 待人工批准`, stepPercent: null }
  }
  if (execution.status === 'succeeded') {
    return { text: `完成 · ${stepText}`, stepPercent: null }
  }
  if (execution.status === 'aborted') {
    return { text: execution.terminateReason?.msg || `已终止 · ${stepText}`, stepPercent: null }
  }
  const current = currentPlanStep(execution.plan, steps)
  return {
    text: `${stepText}${current ? ` · ${current}` : ''}`,
    stepPercent:
      maxSteps && maxSteps > 0 ? clampPercent((steps / maxSteps) * 100) : null,
  }
}

// ==================== 计划步骤时间线 ====================

export type AiPlanStepState = 'done' | 'current' | 'aborted' | 'pending'

export interface AiPlanStep {
  label: string
  state: AiPlanStepState
}

/**
 * 计划步骤视图模型（交互 2.2 计划步骤）：由 plan 快照 + 已执行步数推导，
 * 完成打勾、当前高亮、终止标红、其后待执行；无 plan 快照返回空（降级不渲染）。
 */
export function aiPlanSteps(execution: AiTaskExecutionItem | null | undefined): AiPlanStep[] {
  const labels = execution?.plan?.steps
  if (!labels || labels.length === 0) return []
  const executed = execution?.steps ?? 0
  const status = execution?.status
  return labels.map((label, index) => {
    if (status === 'succeeded') return { label, state: 'done' as const }
    if (index < executed) return { label, state: 'done' as const }
    if (index === executed) {
      return { label, state: status === 'aborted' ? ('aborted' as const) : ('current' as const) }
    }
    return { label, state: 'pending' as const }
  })
}

// ==================== 轨迹事件 ====================

/** 轨迹事件类型中文标签（总册 2.6.3） */
export const AI_TRACE_EVENT_LABEL: Record<AiTraceEventType, string> = {
  plan: '计划',
  ai_decision: 'AI 决策',
  tool_call: '工具调用',
  tool_result: '工具结果',
  proposal_emitted: '产出提案',
  status_change: '状态变更',
}

export function aiTraceEventLabel(eventType: string): string {
  return AI_TRACE_EVENT_LABEL[eventType as AiTraceEventType] ?? eventType
}

// ==================== 提案 ====================

/** 提案 kind → 中文标签（详设 3.6.1 提案 kind 列） */
const PROPOSAL_KIND_LABEL: Record<string, string> = {
  requirement_suggestion: '需求建议',
  module_suggestion: '模块',
  mindmap_document_suggestion: '文档',
  test_case_suggestion: '用例',
  review_selection: '评审圈选',
  plan_selection: '计划圈选',
  case_suggestion: '用例',
  priority_suggestion: '级别建议',
  order_suggestion: '排序建议',
  classify_suggestion: '分类建议',
  duplicate_group: '重复组',
  triage_order: '分诊顺序',
  summary: '总结',
  intent_preview: '意图预览',
  answer: '回答',
}

export interface AiProposalKindCount {
  kind: string
  label: string
  count: number
}

/** 提案计数徽标（详情页，按 kind 分组，顺序按出现次序） */
export function aiProposalKindCounts(
  proposals: Array<{ kind: string }> | null | undefined,
): AiProposalKindCount[] {
  if (!proposals || proposals.length === 0) return []
  const counts: AiProposalKindCount[] = []
  for (const proposal of proposals) {
    const existing = counts.find((item) => item.kind === proposal.kind)
    if (existing) {
      existing.count += 1
    } else {
      counts.push({
        kind: proposal.kind,
        label: PROPOSAL_KIND_LABEL[proposal.kind] ?? proposal.kind,
        count: 1,
      })
    }
  }
  return counts
}

export function aiProposalKindLabel(kind: string): string {
  return PROPOSAL_KIND_LABEL[kind] ?? kind
}

// ==================== 提案确认 ====================

export interface AiProposalConfirmMeta {
  label: string
  tagType: 'success' | 'info' | 'warning'
}

export const AI_PROPOSAL_CONFIRM_META: Record<AiProposalConfirmStatus, AiProposalConfirmMeta> =
  {
    pending: { label: '待确认', tagType: 'warning' },
    not_applicable: { label: '只读建议', tagType: 'info' },
    adopted: { label: '已采纳', tagType: 'success' },
    adopted_edited: { label: '已采纳', tagType: 'success' },
    rejected: { label: '已驳回', tagType: 'info' },
  }

export function aiProposalConfirmMeta(status: string): AiProposalConfirmMeta {
  return (
    AI_PROPOSAL_CONFIRM_META[status as AiProposalConfirmStatus] ?? {
      label: status,
      tagType: 'info',
    }
  )
}

/** 已处理数（非 pending，含只读建议）与总数 */
export function aiProposalProcessed(
  proposals: Array<{ confirmStatus: string }> | null | undefined,
): { processed: number; total: number } {
  if (!proposals) return { processed: 0, total: 0 }
  return {
    processed: proposals.filter((item) => item.confirmStatus !== 'pending').length,
    total: proposals.length,
  }
}

// ==================== 导入识别版本（需求详设 3.8 / 交互 06 §2.4） ====================

/** 识别版本预填值：识别不到返回空串（采纳后留空待手工补录） */
export function importVersionPrefill(documentMeta: AiTaskDocumentMeta | null): string {
  return documentMeta?.detectedVersion ?? ''
}

/** 识别依据引语（悬浮展示）：无引语返回空串不渲染 */
export function importVersionEvidence(documentMeta: AiTaskDocumentMeta | null): string {
  return documentMeta?.versionEvidence ? `识别依据：${documentMeta.versionEvidence}` : ''
}

/**
 * 版本输入提示：导入按识别态给预填 / 补录口径，拆分给继承口径；
 * 触碰后统一按当前值提示采纳结果（交互 06 §2.4.2）。
 */
export function importVersionHint(options: {
  taskType: string
  documentMeta: AiTaskDocumentMeta | null
  touched: boolean
  value: string
}): string {
  if (!options.touched) {
    if (options.taskType !== 'requirement_import') {
      return '留空将继承原条目版本，可修改、可清空'
    }
    return importVersionPrefill(options.documentMeta)
      ? '已预填文档识别版本，可修改、可清空'
      : '未识别到版本，采纳后可手工补录'
  }
  return options.value.trim() ? '采纳时使用当前版本值' : '采纳后版本留空，可手工补录'
}
