import type { ApiComponentListItem, ApiDebugKeyValue, ApiDebugRawSubtype, ApiSceneStepItem, ApiSceneStepVariableItem } from '@/types'
import { FORM_ENCODED_CONTENT_TYPE, RAW_SUBTYPE_CONTENT_TYPE } from './debugModel'
import { parseComponentConfig } from '@/components/api-testing/processorFormModel'

/** 步骤类型选项 */
export const STEP_TYPE_OPTIONS = [
  { value: 'http', label: 'HTTP 请求' },
  { value: 'jdbc', label: 'JDBC 请求' },
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

/** 从步骤提取 SQL 语句类型（JDBC 步骤），无法识别返回 null */
export function stepSqlType(step: ApiSceneStepItem): string | null {
  const config = step.requestConfig
  if (!config || typeof config !== 'object' || typeof (config as Record<string, unknown>).sql !== 'string') {
    return null
  }
  const match = /^\s*([a-zA-Z]+)/.exec(String((config as Record<string, unknown>).sql))
  return match ? match[1].toUpperCase() : null
}

/** 按排序序号排列步骤 */
export function sortedSteps(steps: ApiSceneStepItem[]): ApiSceneStepItem[] {
  return [...steps].sort((a, b) => a.sortOrder - b.sortOrder)
}

/** 复制预填：深拷贝源场景步骤为新建态草稿，重新生成 new- 临时 id；
    sourceType/sourceId 保留，供置灰展示与来源追溯（测试场景详细设计 3.1.6） */
export function prefillDraftSteps(steps: ApiSceneStepItem[]): ApiSceneStepItem[] {
  let nonce = 0
  return sortedSteps(steps).map((s) => ({
    ...s,
    id: `new-${Date.now()}-${nonce++}-${Math.random().toString(36).slice(2, 8)}`,
    processors: [...s.processors],
    validators: [...s.validators],
    extractors: [...s.extractors],
    variables: [...s.variables],
  }))
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

// ==================== 验证器（Validator） ====================

export interface ValidatorItem {
  id: string
  name: string
  enabled: boolean
  target: string
  condition: string
  expected: string
  expression: string
}

// 步骤/处理器共用编辑行的最小字段集（id/name/description 等额外字段由调用方持有，编辑器拷贝保留）
export interface PaneValidatorItem {
  enabled: boolean
  target: string
  condition: string
  expression: string
  expected: string
}

export interface PaneExtractorItem {
  enabled: boolean
  source: string
  expression: string
  variableName: string
  description?: string
}

export const VALIDATOR_TARGETS = [
  { value: 'status_code', label: '状态码' },
  { value: 'json_field', label: 'JSON 字段' },
  { value: 'response_header', label: '响应头' },
  { value: 'response_body', label: '响应体' },
  { value: 'regex', label: '正则匹配' },
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
  return items
    .filter((v) => v.target?.trim())
    .map((v) => ({ ...v, name: v.name?.trim() || `验证器 ${v.target}` }))
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
  { value: 'regex', label: '正则匹配' },
  { value: 'full_body', label: '完整响应体' },
]

export function createExtractor(): ExtractorItem {
  return { id: crypto.randomUUID(), name: '', enabled: true, source: 'json_field', expression: '', variableName: '' }
}

export function serializeExtractors(items: ExtractorItem[]): Record<string, unknown>[] {
  return items
    .filter((e) => e.source?.trim() && e.variableName?.trim())
    .map((e) => ({ ...e, name: e.name?.trim() || `提取器 ${e.source}` }))
}

/** 读取字符串字段，非字符串或空串回退默认值（组件 config 直通存储，字段可能缺失） */
function pickString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value.trim() : fallback
}

/** 验证器资产 → 步骤验证器行（复制引入，独立副本；target/condition 与 VALIDATOR_* 回读一致） */
export function stepValidatorsFromComponents(items: ApiComponentListItem[]): ValidatorItem[] {
  return items.map((item) => {
    const cfg = parseComponentConfig(item.config)
    return {
      id: crypto.randomUUID(),
      name: item.name,
      enabled: true,
      target: pickString(cfg.target, 'status_code'),
      condition: pickString(cfg.condition, 'equals'),
      expression: pickString(cfg.expression),
      expected: pickString(cfg.expected),
    }
  })
}

/** 提取器资产 → 步骤提取器行（复制引入，独立副本；source 与 EXTRACTOR_SOURCES 回读一致） */
export function stepExtractorsFromComponents(items: ApiComponentListItem[]): ExtractorItem[] {
  return items.map((item) => {
    const cfg = parseComponentConfig(item.config)
    return {
      id: crypto.randomUUID(),
      name: item.name,
      enabled: true,
      source: pickString(cfg.source, 'json_field'),
      expression: pickString(cfg.expression),
      variableName: pickString(cfg.variableName),
    }
  })
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

// ==================== 请求体（对齐快速调试：none / x-www-form-urlencoded / raw + 子类型） ====================

/** 场景步骤请求体编辑态：与快速调试/接口编辑器一致的三态 + raw 子类型（接口域详细设计 body_type 映射） */
export interface SceneBodyEditState {
  kind: 'none' | 'urlencoded' | 'raw'
  rawSubtype: ApiDebugRawSubtype
  rawText: string
  urlencodedRows: ApiDebugKeyValue[]
}

export const SCENE_BODY_TYPES = [
  { value: 'none', label: 'none' },
  { value: 'urlencoded', label: 'x-www-form-urlencoded' },
  { value: 'raw', label: 'raw' },
] as const satisfies ReadonlyArray<{ value: SceneBodyEditState['kind']; label: string }>

export const SCENE_RAW_SUBTYPES: ApiDebugRawSubtype[] = ['text', 'json', 'xml', 'html', 'javascript']

/** 落库 body.type → 编辑态三态：form→urlencoded、json/raw→raw、其余 none（接口域详细设计 body_type 映射） */
export function mapBodyEditKind(type: string | undefined): SceneBodyEditState['kind'] {
  if (type === 'form') return 'urlencoded'
  if (type === 'json' || type === 'raw') return 'raw'
  return 'none'
}

/** 将请求头行还原为 {key,value,enabled} 三元组 */
function toKeyValueTriples(rows: ApiDebugKeyValue[]): { key: string; value: string; enabled: boolean }[] {
  return rows
    .filter((row) => row.key.trim() !== '' && row.enabled)
    .map((row) => ({ key: row.key.trim(), value: row.value, enabled: true }))
}

/** 落库 content 数组 → urlencoded 行（content 为 null/非数组时回退空表） */
function kvRowsFromContent(content: unknown): ApiDebugKeyValue[] {
  if (!Array.isArray(content)) return []
  return content.map((entry) => {
    const item = entry as Record<string, unknown>
    return { key: String(item.key ?? ''), value: String(item.value ?? ''), enabled: item.enabled !== false }
  })
}

/** 落库请求体 → 编辑态（回显）；json 保留 json 子类型，raw 按内容前缀推断（对齐 interfacesModel 回读） */
export function parseBodyEditState(body?: { type?: string; content?: unknown } | null): SceneBodyEditState {
  const kind = mapBodyEditKind(body?.type)
  const content = body?.content
  if (kind === 'urlencoded') {
    return { kind, rawSubtype: 'text', rawText: '', urlencodedRows: kvRowsFromContent(content) }
  }
  if (kind === 'raw') {
    // 与 interfacesModel 回读一致：json 保留 json 子类型，raw 字符串按 JSON 前缀推断
    const rawSubtype: ApiDebugRawSubtype = body?.type === 'json'
      ? 'json'
      : typeof content === 'string'
        ? (/^\s*[{[]/.test(content) ? 'json' : 'text')
        : 'json'
    const rawText = typeof content === 'string' ? content : JSON.stringify(content ?? {}, null, 2)
    return { kind, rawSubtype, rawText, urlencodedRows: [] }
  }
  return { kind: 'none', rawSubtype: 'text', rawText: '', urlencodedRows: [] }
}

/** 编辑态 → 落库请求体；raw+json 解析失败返回错误而非静默丢弃（对齐接口保存行为） */
export function buildBodyFromEditState(
  state: SceneBodyEditState,
): { body?: { type: string; content: unknown }; error?: string } {
  if (state.kind === 'urlencoded') {
    return { body: { type: 'form', content: toKeyValueTriples(state.urlencodedRows) } }
  }
  if (state.kind === 'raw') {
    if (state.rawSubtype === 'json') {
      try {
        return { body: { type: 'json', content: state.rawText.trim() ? (JSON.parse(state.rawText) as unknown) : {} } }
      } catch {
        return { error: 'JSON 请求体格式非法，请修正后再保存' }
      }
    }
    return { body: { type: 'raw', content: state.rawText } }
  }
  return { body: { type: 'none', content: null } }
}

/** 编辑态请求体 → 应注入的 Content-Type；none/text 不注入（对齐快速调试联动） */
export function resolveBodyContentType(state: SceneBodyEditState): string | undefined {
  if (state.kind === 'urlencoded') return FORM_ENCODED_CONTENT_TYPE
  if (state.kind === 'raw') return RAW_SUBTYPE_CONTENT_TYPE[state.rawSubtype]
  return undefined
}

/** 注入/移除 Content-Type 头行到请求头（替换已存在的同名头，Quick Debug 语义） */
export function syncBodyContentTypeHeader(
  headers: ApiDebugKeyValue[],
  state: SceneBodyEditState,
): ApiDebugKeyValue[] {
  const contentType = resolveBodyContentType(state)
  const rest = headers.filter((h) => h.key.trim().toLowerCase() !== 'content-type')
  if (!contentType) return rest
  return [{ key: 'Content-Type', value: contentType, enabled: true }, ...rest]
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
