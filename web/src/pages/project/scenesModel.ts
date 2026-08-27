import type { ApiSceneStepItem, ApiSceneStepVariableItem } from '@/types'

/** 步骤类型选项 */
export const STEP_TYPE_OPTIONS = [
  { value: 'http', label: 'HTTP 请求' },
  { value: 'jdbc', label: 'JDBC 请求' },
]

/** 失败规则选项 */
export const FAILURE_RULE_OPTIONS = [
  { value: 'all', label: '停止运行', desc: '任一步骤失败即终止后续步骤' },
  { value: 'continue', label: '继续运行', desc: '忽略错误，全部步骤执行完毕' },
]

/** 方法标签颜色 */
type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'
const METHOD_COLORS: Record<string, TagType> = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  PATCH: 'warning',
  DELETE: 'danger',
}
export function methodTagType(method: string): TagType {
  return METHOD_COLORS[method.toUpperCase()] ?? 'info'
}

/** 从步骤中提取请求方法 */
export function stepMethod(step: ApiSceneStepItem): string | null {
  const config = step.requestConfig
  if (config && typeof config === 'object' && 'method' in config) {
    return String((config as Record<string, unknown>).method ?? '').toUpperCase()
  }
  return null
}

/** 从步骤中提取请求路径 */
export function stepPath(step: ApiSceneStepItem): string | null {
  const config = step.requestConfig
  if (config && typeof config === 'object' && 'url' in config) {
    return String((config as Record<string, unknown>).url ?? '')
  }
  return null
}

/** 来源类型标签 */
export const SOURCE_TYPE_LABELS: Record<string, string> = {
  system: '系统请求',
  custom: '自定义',
  public_step: '公共步骤',
  copy: '复制',
  link: '链接引用',
}

/** 按排序序号排列步骤 */
export function sortedSteps(steps: ApiSceneStepItem[]): ApiSceneStepItem[] {
  return [...steps].sort((a, b) => a.sortOrder - b.sortOrder)
}

/** 构造空步骤默认值 */
export function emptyStepDraft(): {
  name: string
  stepType: string
  requestConfig: Record<string, unknown>
} {
  return {
    name: '',
    stepType: 'http',
    requestConfig: { method: 'GET', url: '', headers: [], params: [], body: { type: 'none', content: null } },
  }
}

// ==================== 断言（Validator） ====================

export interface ValidatorItem {
  id: string
  name: string
  enabled: boolean
  target: string
  condition: string
  expected: string
  expression: string
}

export const VALIDATOR_TARGETS = [
  { value: 'status_code', label: '状态码' },
  { value: 'json_field', label: 'JSON 字段' },
  { value: 'response_header', label: '响应头' },
  { value: 'response_body', label: '响应体' },
  { value: 'regex', label: '正则匹配' },
  { value: 'xpath', label: 'XPath' },
  { value: 'groovy', label: 'Groovy 脚本' },
]

export const VALIDATOR_CONDITIONS = [
  { value: 'equals', label: '等于' },
  { value: 'not_equals', label: '不等于' },
  { value: 'greater_than', label: '大于' },
  { value: 'less_than', label: '小于' },
  { value: 'greater_or_equal', label: '大于等于' },
  { value: 'less_or_equal', label: '小于等于' },
  { value: 'contains', label: '包含' },
  { value: 'not_contains', label: '不包含' },
  { value: 'starts_with', label: '以…开头' },
  { value: 'ends_with', label: '以…结尾' },
  { value: 'matches_regex', label: '正则匹配' },
]

export function createValidator(): ValidatorItem {
  return { id: crypto.randomUUID(), name: '', enabled: true, target: 'status_code', condition: 'equals', expected: '', expression: '' }
}

export function serializeValidators(items: ValidatorItem[]): Record<string, unknown>[] {
  return items.filter((v) => v.name.trim()).map((v) => ({ ...v }))
}

// ==================== 提取器（Extractor） ====================

export interface ExtractorItem {
  id: string
  name: string
  enabled: boolean
  source: string
  expression: string
  variableName: string
}

export const EXTRACTOR_SOURCES = [
  { value: 'json_field', label: 'JSON 字段' },
  { value: 'response_header', label: '响应头' },
  { value: 'xpath', label: 'XPath' },
  { value: 'regex', label: '正则匹配' },
  { value: 'boundary', label: '边界值' },
  { value: 'full_body', label: '完整响应体' },
  { value: 'groovy', label: 'Groovy 脚本' },
]

export function createExtractor(): ExtractorItem {
  return { id: crypto.randomUUID(), name: '', enabled: true, source: 'json_field', expression: '', variableName: '' }
}

export function serializeExtractors(items: ExtractorItem[]): Record<string, unknown>[] {
  return items.filter((e) => e.name.trim() && e.variableName.trim()).map((e) => ({ ...e }))
}

// ==================== 步骤变量 ====================

export function createStepVariable(): ApiSceneStepVariableItem {
  return { id: crypto.randomUUID(), name: '', value: '', source: 'custom', description: '', sortOrder: 0 }
}

// ==================== 请求配置辅助 ====================

export interface RequestConfig {
  method?: string
  url?: string
  headers?: { key: string; value: string; enabled: boolean }[]
  params?: { key: string; value: string; enabled: boolean }[]
  body?: { type: string; content: unknown }
  timeout?: number
}

export function parseRequestConfig(config: Record<string, unknown> | undefined | null): RequestConfig {
  if (!config || typeof config !== 'object') return {}
  return config as unknown as RequestConfig
}

export function buildEmptyRequestConfig(): Record<string, unknown> {
  return {
    method: 'GET',
    url: '',
    headers: [],
    params: [],
    body: { type: 'none', content: null },
    timeout: 30000,
  }
}

// ==================== 执行配置 ====================

export interface ExecutionConfig {
  timeout: number
  retryCount: number
  conditionExpression: string
}

export function createExecutionConfig(): ExecutionConfig {
  return { timeout: 30000, retryCount: 0, conditionExpression: '' }
}
