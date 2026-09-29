import type { ApiComponentScope, ApiComponentType } from '@/types'
import {
  EXTRACTOR_SOURCES,
  VALIDATOR_CONDITIONS,
  VALIDATOR_TARGETS,
} from '@/composables/project/api-testing/scene/scenesModel'
import { parseComponentConfig } from '@/composables/project/api-testing/processorFormModel'
import { buildProcessorDetailRows } from '@/composables/project/api-testing/processorDetailModel'
import type { ProcessorConfigRow } from '@/composables/project/api-testing/processorDetailModel'

// ==================== 常量 ====================

export const COMPONENT_TYPE_OPTIONS: { value: ApiComponentType; label: string }[] = [
  { value: 'preprocessor', label: '前置处理器' },
  { value: 'postprocessor', label: '后置处理器' },
  { value: 'validator', label: '验证器' },
  { value: 'extractor', label: '提取器' },
]

/** 列表页签：全部 + 四种类型（页签文案收窄，避免 340px 列宽溢出） */
export type ComponentTab = 'all' | ApiComponentType

export const COMPONENT_TAB_OPTIONS: { value: ComponentTab; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'preprocessor', label: '前置' },
  { value: 'postprocessor', label: '后置' },
  { value: 'validator', label: '验证器' },
  { value: 'extractor', label: '提取器' },
]

/** 右栏面板形态：查看详情 / 新建 / 编辑 */
export type ComponentPanelMode = 'view' | 'create' | 'edit'

/** 新建/编辑面板共享表单态（深层原地编辑为既定契约，见 eslint no-mutating-props shallowOnly） */
export interface ComponentFormData {
  type: ApiComponentType
  name: string
  description: string
  scope: ApiComponentScope
  sortOrder: number
  config: Record<string, unknown>
}

export const COMPONENT_SCOPE_OPTIONS: { value: ApiComponentScope; label: string }[] = [
  { value: 'global', label: '公共' },
  { value: 'workspace', label: '空间' },
  { value: 'project', label: '项目' },
]

/** scope 标签颜色映射 */
export const SCOPE_TAG_TYPE: Record<ApiComponentScope, 'success' | 'warning' | 'info' | undefined> = {
  global: undefined,
  workspace: 'success',
  project: 'info',
}

// ==================== 错误码映射 ====================

interface ErrorCodeLike {
  code?: number
  message?: string
}

/** 业务错误码 → 可操作文案（公共组件号段 17321–17322） */
const COMPONENT_ERROR_MESSAGES: Record<number, string> = {
  1000017321: '公共组件不存在或不属于当前可见范围',
  1000017322: '同作用域下已存在同名公共组件',
}

export function resolveComponentError(err: unknown): string {
  const error = err as ErrorCodeLike
  if (!error || typeof error.code !== 'number') {
    return typeof error?.message === 'string' && error.message ? error.message : '操作失败，请稍后重试'
  }
  return COMPONENT_ERROR_MESSAGES[error.code] ?? error.message ?? '操作失败，请稍后重试'
}

// ==================== 辅助函数 ====================

export function componentTypeLabel(type: ApiComponentType): string {
  return COMPONENT_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type
}

export function componentScopeLabel(scope: ApiComponentScope): string {
  return COMPONENT_SCOPE_OPTIONS.find((o) => o.value === scope)?.label ?? scope
}

// ==================== 查看态配置摘要 ====================

function textRow(label: string, value: string): ProcessorConfigRow {
  return { label, kind: 'text', value, pairs: [] }
}

function optionLabel(options: { value: string; label: string }[], value: string, fallback: string): string {
  return options.find((o) => o.value === value)?.label ?? fallback
}

function buildValidatorRows(config: Record<string, unknown>): ProcessorConfigRow[] {
  const pick = (key: string): string => (typeof config[key] === 'string' ? (config[key] as string) : '')
  const target = pick('target') || 'status_code'
  const condition = pick('condition') || 'equals'
  const rows = [
    textRow('验证目标', optionLabel(VALIDATOR_TARGETS, target, target)),
    textRow('表达式', pick('expression') || '—'),
    textRow('比较条件', optionLabel(VALIDATOR_CONDITIONS, condition, condition)),
    textRow('期望值', pick('expected') || '—'),
  ]
  const desc = pick('description')
  if (desc) rows.push(textRow('验证器描述', desc))
  return rows
}

function buildExtractorAssetRows(config: Record<string, unknown>): ProcessorConfigRow[] {
  const pick = (key: string): string => (typeof config[key] === 'string' ? (config[key] as string) : '')
  const source = pick('source') || 'json_field'
  const rows = [
    textRow('提取来源', optionLabel(EXTRACTOR_SOURCES, source, source)),
    textRow('表达式', pick('expression') || '—'),
    textRow('目标变量名', pick('variableName') || '—'),
  ]
  const desc = pick('description')
  if (desc) rows.push(textRow('提取描述', desc))
  return rows
}

/** 组件 config（JSON 字符串）→ 查看态「配置」区展示行（处理器分支委托共享模型，保证与环境/场景同源） */
export function buildComponentConfigRows(type: ApiComponentType, config: string | null): ProcessorConfigRow[] {
  const parsed = parseComponentConfig(config)
  if (type === 'validator') return buildValidatorRows(parsed)
  if (type === 'extractor') return buildExtractorAssetRows(parsed)
  return buildProcessorDetailRows({
    testclass: parsed.testclass,
    config: parsed.config,
  })
}
