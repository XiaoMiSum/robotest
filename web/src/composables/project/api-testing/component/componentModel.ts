import type { ApiComponentScope, ApiComponentType } from '@/types'
import {
  EXTRACTOR_SOURCES,
  VALIDATOR_CONDITIONS,
  VALIDATOR_TARGETS,
} from '@/composables/project/api-testing/scene/scenesModel'
import {
  isRecord,
  parseComponentConfig,
  parseHttpProcessorForm,
  parseJdbcProcessorForm,
} from '@/composables/project/api-testing/processorFormModel'

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

/** 配置行呈现形态：纯文本 / 代码块 / 键值对列表 */
export type ComponentConfigRowKind = 'text' | 'code' | 'kv'

export interface ComponentKvPair {
  key: string
  value: string
}

export interface ComponentConfigRow {
  label: string
  kind: ComponentConfigRowKind
  /** text / code 行展示值，kv 行恒为空串 */
  value: string
  /** kv 行键值对，其余形态恒为空数组 */
  pairs: ComponentKvPair[]
}

/** 提取器只读表格行（来源已映射为展示文案） */
export interface ComponentExtractorRow {
  source: string
  expression: string
  variableName: string
  description: string
}

/** 请求体类型展示文案：与请求配置编辑器的三态 + raw 子类型推断口径一致 */
const BODY_KIND_LABELS: Record<string, string> = {
  none: 'none',
  form: 'x-www-form-urlencoded',
  json: 'raw · json',
  raw: 'raw · text',
}

function textRow(label: string, value: string): ComponentConfigRow {
  return { label, kind: 'text', value, pairs: [] }
}

function codeRow(label: string, value: string): ComponentConfigRow {
  return { label, kind: 'code', value, pairs: [] }
}

function kvRow(label: string, pairs: ComponentKvPair[]): ComponentConfigRow {
  return { label, kind: 'kv', value: '', pairs }
}

function rowsToPairs(rows: { key: string; value: string }[]): ComponentKvPair[] {
  return rows
    .filter((row) => row.key.trim())
    .map((row) => ({ key: row.key.trim(), value: row.value }))
}

function optionLabel(options: { value: string; label: string }[], value: string, fallback: string): string {
  return options.find((o) => o.value === value)?.label ?? fallback
}

/** 处理器 config → 只读配置行（复用表单解析口径，保证详情与编辑器同源） */
function buildProcessorRows(element: Record<string, unknown>): ComponentConfigRow[] {
  const inner = isRecord(element.config) ? element.config : {}
  if (element.testclass === 'jdbc') {
    const form = parseJdbcProcessorForm({ testclass: 'jdbc', config: inner })
    const rows = [
      textRow('处理器类型', 'JDBC'),
      textRow('数据源', form.ref || '—'),
      codeRow('SQL 语句', form.sql || '—'),
    ]
    if (form.args.length > 0) rows.push(textRow('参数', form.args.join(', ')))
    return rows
  }
  const form = parseHttpProcessorForm({ testclass: 'http', config: inner })
  const query = rowsToPairs(form.queryRows)
  const headers = rowsToPairs(form.headerRows)
  const rows = [
    textRow('处理器类型', 'HTTP'),
    textRow('环境 HTTP 配置', form.ref || '—'),
    textRow('请求方法', form.method || '—'),
    textRow('路径', form.path || '—'),
  ]
  if (query.length > 0) rows.push(kvRow('Query 参数', query))
  if (headers.length > 0) rows.push(kvRow('请求头', headers))
  rows.push(textRow('请求体类型', BODY_KIND_LABELS[form.bodyKind] ?? 'none'))
  if (form.bodyKind === 'form') {
    const formRows = rowsToPairs(form.formRows)
    if (formRows.length > 0) rows.push(kvRow('表单参数', formRows))
  } else if (form.bodyText.trim()) {
    rows.push(codeRow('请求体', form.bodyText))
  }
  return rows
}

function buildValidatorRows(config: Record<string, unknown>): ComponentConfigRow[] {
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

function buildExtractorAssetRows(config: Record<string, unknown>): ComponentConfigRow[] {
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

/** 组件 config（JSON 字符串）→ 查看态「配置」区展示行 */
export function buildComponentConfigRows(type: ApiComponentType, config: string | null): ComponentConfigRow[] {
  const parsed = parseComponentConfig(config)
  if (type === 'validator') return buildValidatorRows(parsed)
  if (type === 'extractor') return buildExtractorAssetRows(parsed)
  return buildProcessorRows({
    testclass: parsed.testclass,
    config: parsed.config,
  })
}

/** 处理器组件内嵌提取器 → 只读表格行（非处理器类型无提取器子表） */
export function buildComponentExtractorRows(type: ApiComponentType, config: string | null): ComponentExtractorRow[] {
  if (type !== 'preprocessor' && type !== 'postprocessor') return []
  const parsed = parseComponentConfig(config)
  const raw: unknown = parsed.extractors
  if (!Array.isArray(raw)) return []
  return raw.map((item) => {
    const row = isRecord(item) ? item : {}
    const source = typeof row.source === 'string' ? row.source : ''
    return {
      source: source ? optionLabel(EXTRACTOR_SOURCES, source, source) : '—',
      expression: typeof row.expression === 'string' && row.expression ? row.expression : '—',
      variableName: typeof row.variableName === 'string' && row.variableName ? row.variableName : '—',
      description: typeof row.description === 'string' && row.description ? row.description : '—',
    }
  })
}
