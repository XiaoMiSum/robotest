import type {
  AiArtifactConfirmStatus,
  AiTaskStatus,
} from '@/types'

// ==================== 状态徽标 ====================

export interface AiTaskStatusMeta {
  label: string
  tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  /** failed 用警示色底 + 图标，不单靠颜色表意（视觉 4.1） */
  failed?: boolean
}

/** 状态色遵循视觉设计 4.1：排队 info、进行中 primary、成功 success、失败 danger、已取消中性灰 */
export const AI_TASK_STATUS_META: Record<AiTaskStatus, AiTaskStatusMeta> = {
  pending: { label: '排队', tagType: 'info' },
  running: { label: '进行中', tagType: 'primary' },
  succeeded: { label: '成功', tagType: 'success' },
  failed: { label: '失败', tagType: 'danger', failed: true },
  cancelled: { label: '已取消', tagType: 'info' },
}

export function aiTaskStatusMeta(status: string): AiTaskStatusMeta {
  return AI_TASK_STATUS_META[status as AiTaskStatus] ?? { label: status, tagType: 'info' }
}

// ==================== 任务类型 ====================

export interface AiTaskTypeMeta {
  /** 任务名（列表按类型派生，重试任务追加后缀） */
  label: string
  /** 能力域标签（列表「类型」列） */
  domain: string
}

/** 任务类型 → 中文名与能力域（详设 3.6.1 枚举） */
export const AI_TASK_TYPE_META: Record<string, AiTaskTypeMeta> = {
  requirement_import: { label: '需求导入', domain: '需求' },
  requirement_split: { label: '需求拆分', domain: '需求' },
  test_design_generation: { label: '用例生成', domain: '生成链' },
  review_selection: { label: '评审圈选', domain: '生成链' },
  plan_selection: { label: '计划圈选', domain: '生成链' },
  coverage_analysis: { label: '覆盖分析', domain: '矩阵' },
  impact_analysis: { label: '影响分析', domain: '矩阵' },
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

/** 任务名按类型派生（后端无任务名字段），重试任务追加「·重试」 */
export function aiTaskName(task: { type: string; retryOfTaskId?: string | null }): string {
  const base = aiTaskTypeMeta(task.type).label
  return task.retryOfTaskId ? `${base} ·重试` : base
}

/** 类型筛选下拉选项（枚举全量，顺序与详设 3.6.1 一致） */
export function aiTaskTypeOptions(): Array<{ value: string; label: string }> {
  return Object.entries(AI_TASK_TYPE_META).map(([value, meta]) => ({
    value,
    label: `${meta.label}（${meta.domain}）`,
  }))
}

// ==================== 阶段时间线 ====================

export type AiPhaseStepState = 'done' | 'current' | 'failed' | 'pending'

export interface AiPhaseStep {
  label: string
  state: AiPhaseStepState
}

/** 生成链六阶段（交互 2.2），由任务 phase 驱动高亮 */
const GENERATION_CHAIN_PHASES = [
  '解析需求',
  '生成模块结构',
  '生成脑图文档',
  '标记用例节点',
  '填充用例属性',
  '建立追溯边',
]

/** 需求拆分阶段（分册拆分处理器上报顺序） */
const REQUIREMENT_SPLIT_PHASES = [
  '读取需求与模块',
  '构建提示词',
  '模型生成拆分建议',
  '解析拆分建议',
]

const TASK_PHASES: Record<string, string[]> = {
  test_design_generation: GENERATION_CHAIN_PHASES,
  requirement_split: REQUIREMENT_SPLIT_PHASES,
}

/** 任务类型对应的完整阶段序列；未知类型返回空数组（详情页只展示当前阶段文本） */
export function aiTaskPhases(type: string): string[] {
  return TASK_PHASES[type] ?? []
}

/**
 * 由 status / phase / progress 推导阶段步骤状态：终态 succeeded 全部完成；
 * failed 停在当前阶段标红，其后待执行；phase 未命中已知序列时返回空（降级为纯文本）。
 */
export function aiTaskPhaseSteps(task: {
  type: string
  status: AiTaskStatus
  phase: string | null
}): AiPhaseStep[] {
  const phases = aiTaskPhases(task.type)
  if (phases.length === 0) return []

  const currentIndex = task.phase ? phases.indexOf(task.phase) : -1
  if (task.status === 'succeeded') {
    return phases.map((label) => ({ label, state: 'done' as const }))
  }
  if (task.status === 'cancelled') {
    return phases.map((label) => ({ label, state: 'pending' as const }))
  }
  if (currentIndex < 0) {
    // 上报阶段不在已知序列（如处理器升级）时不猜测进度，降级为纯文本展示
    return []
  }
  return phases.map((label, index) => {
    if (index < currentIndex) return { label, state: 'done' as const }
    if (index === currentIndex) {
      return { label, state: task.status === 'failed' ? 'failed' : 'current' }
    }
    return { label, state: 'pending' as const }
  })
}

// ==================== 产物 ====================

/** 产物 kind → 中文标签（详设 3.6.1 产物 kind 列） */
const ARTIFACT_KIND_LABEL: Record<string, string> = {
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

export interface AiArtifactKindCount {
  kind: string
  label: string
  count: number
}

/** 产物计数徽标（详情页，按 kind 分组，顺序按出现次序） */
export function aiArtifactKindCounts(
  artifacts: Array<{ kind: string }> | null | undefined,
): AiArtifactKindCount[] {
  if (!artifacts || artifacts.length === 0) return []
  const counts: AiArtifactKindCount[] = []
  for (const artifact of artifacts) {
    const existing = counts.find((item) => item.kind === artifact.kind)
    if (existing) {
      existing.count += 1
    } else {
      counts.push({
        kind: artifact.kind,
        label: ARTIFACT_KIND_LABEL[artifact.kind] ?? artifact.kind,
        count: 1,
      })
    }
  }
  return counts
}

export function aiArtifactKindLabel(kind: string): string {
  return ARTIFACT_KIND_LABEL[kind] ?? kind
}

// ==================== 产物确认 ====================

export interface AiArtifactConfirmMeta {
  label: string
  tagType: 'success' | 'info' | 'warning'
}

export const AI_ARTIFACT_CONFIRM_META: Record<AiArtifactConfirmStatus, AiArtifactConfirmMeta> = {
  pending: { label: '待确认', tagType: 'warning' },
  adopted: { label: '已采纳', tagType: 'success' },
  adopted_edited: { label: '已采纳', tagType: 'success' },
  rejected: { label: '已驳回', tagType: 'info' },
}

export function aiArtifactConfirmMeta(status: string): AiArtifactConfirmMeta {
  return (
    AI_ARTIFACT_CONFIRM_META[status as AiArtifactConfirmStatus] ?? {
      label: status,
      tagType: 'info',
    }
  )
}

/** 已处理数（非 pending）与总数 */
export function aiArtifactProcessed(
  artifacts: Array<{ confirmStatus: string }> | null | undefined,
): { processed: number; total: number } {
  if (!artifacts) return { processed: 0, total: 0 }
  return {
    processed: artifacts.filter((item) => item.confirmStatus !== 'pending').length,
    total: artifacts.length,
  }
}
