import type { ApiComponentListItem, ApiComponentType } from '@/types'

/** 处理器内嵌提取器行，与提取器资产（ExtractorForm）字段一致，保证从公共组件复制后结构可回读 */
export interface ProcessorExtractor {
  enabled: boolean
  source: string
  expression: string
  variableName: string
  description: string
}

/** 键值对编辑器行（请求头 / Query / 表单；enabled 仅编辑态承载，Ryze map 序列化时跳过未启用行） */
export interface ProcessorKvRow {
  key: string
  value: string
  enabled?: boolean
}

/** 请求体编辑类型，与 Ryze `data`（表单）/`body`（JSON/原始）对应 */
export type ProcessorBodyKind = 'none' | 'json' | 'form' | 'raw'

/** HTTP 处理器表单编辑态，`ref` 为环境 http 配置 `refName`（config 键 `ref`） */
export interface HttpProcessorForm {
  method: string
  ref: string
  path: string
  headerRows: ProcessorKvRow[]
  queryRows: ProcessorKvRow[]
  bodyKind: ProcessorBodyKind
  bodyText: string
  formRows: ProcessorKvRow[]
}

/** JDBC 处理器表单编辑态，`ref` 为环境数据源 `ref_name`（写入 config 键 `datasource`） */
export interface JdbcProcessorForm {
  ref: string
  sql: string
  args: string[]
}

export interface ProcessorElementForm {
  testclass: '' | 'http' | 'jdbc'
  http: HttpProcessorForm
  jdbc: JdbcProcessorForm
  extractors: ProcessorExtractor[]
}

/** 平台旧扁平结构键，改造后不再写入元素（顺带清理历史数据残留） */
const LEGACY_CONFIG_KEYS = ['handlerType', 'url', 'contentType', 'dataSource', 'method', 'headers', 'body', 'sql', 'args'] as const

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'
const PROCESSOR_METHOD_COLORS: Record<string, TagType> = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  PATCH: 'warning',
  DELETE: 'danger',
}
function processorMethodTagType(method: string): TagType {
  return PROCESSOR_METHOD_COLORS[method.toUpperCase()] ?? 'info'
}

// 处理器卡片摘要标签：http → 请求方法，jdbc → SQL 语句类型（与步骤卡片的 method/SQL 标签同构）
export function processorSummaryTag(element: Record<string, unknown> | null | undefined): { text: string; type: TagType } | null {
  if (!isRecord(element)) return null
  if (element.testclass === 'http') {
    const config = isRecord(element.config) ? element.config : {}
    const method = typeof config.method === 'string' && config.method.trim() ? config.method.toUpperCase() : 'GET'
    return { text: method, type: processorMethodTagType(method) }
  }
  if (element.testclass === 'jdbc') {
    const config = isRecord(element.config) ? element.config : {}
    if (typeof config.sql !== 'string') return null
    const match = /^\s*([a-zA-Z]+)/.exec(config.sql)
    return match ? { text: match[1].toUpperCase(), type: 'primary' } : null
  }
  return null
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function pickString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function pickStringMap(value: unknown): Record<string, string> {
  if (!isRecord(value)) return {}
  const out: Record<string, string> = {}
  Object.entries(value).forEach(([key, item]) => {
    if (typeof item === 'string') out[key] = item
  })
  return out
}

function readConfig(element: Record<string, unknown> | undefined): Record<string, unknown> {
  return element && isRecord(element.config) ? element.config : {}
}

/** kv 行 → Map（忽略空键与未启用行，供保存为 Ryze headers/query/data） */
export function kvRowsToMap(rows: ProcessorKvRow[]): Record<string, string> {
  const out: Record<string, string> = {}
  rows.forEach((row) => {
    if (row.enabled === false) return
    const key = row.key.trim()
    if (key) out[key] = row.value
  })
  return out
}

/** Map → kv 行（供表单回填；新行默认启用） */
export function mapToKvRows(map: Record<string, string> | undefined): ProcessorKvRow[] {
  return Object.entries(map ?? {}).map(([key, value]) => ({ key, value, enabled: true }))
}

/** 请求体 JSON 文本 → 对象；解析失败保留原文本，避免破坏用户输入 */
function parseJsonBody(text: string): unknown {
  try {
    const parsed: unknown = JSON.parse(text)
    return parsed
  } catch {
    return text
  }
}

/** 元素 config → HTTP 表单编辑态（body 对象判 JSON，字符串按前缀判 JSON/原始） */
export function parseHttpProcessorForm(element: Record<string, unknown> | undefined): HttpProcessorForm {
  const cfg = readConfig(element)
  const headers = pickStringMap(cfg.headers)
  const query = pickStringMap(cfg.query)
  const dataMap = pickStringMap(cfg.data)
  let bodyKind: ProcessorBodyKind = 'none'
  let bodyText = ''
  let formRows: ProcessorKvRow[] = []
  if (Object.keys(dataMap).length > 0) {
    bodyKind = 'form'
    formRows = mapToKvRows(dataMap)
  } else {
    const body = cfg.body
    if (isRecord(body)) {
      bodyKind = 'json'
      bodyText = JSON.stringify(body, null, 2)
    } else if (typeof body === 'string') {
      const trimmed = body.trim()
      bodyKind = trimmed.startsWith('{') || trimmed.startsWith('[') ? 'json' : 'raw'
      bodyText = body
    }
  }
  return {
    method: pickString(cfg.method, 'GET'),
    ref: pickString(cfg.ref),
    path: pickString(cfg.path),
    headerRows: mapToKvRows(headers),
    queryRows: mapToKvRows(query),
    bodyKind,
    bodyText,
    formRows,
  }
}

/** HTTP 表单编辑态 → 元素 config（仅含 Ryze 键，空值省略；`body` 优先级高于 `data`） */
export function toHttpConfig(form: HttpProcessorForm): Record<string, unknown> {
  const cfg: Record<string, unknown> = {}
  if (form.method && form.method !== 'GET') cfg.method = form.method
  if (form.ref.trim()) cfg.ref = form.ref.trim()
  if (form.path.trim()) cfg.path = form.path.trim()
  const headers = kvRowsToMap(form.headerRows)
  if (Object.keys(headers).length > 0) cfg.headers = headers
  const query = kvRowsToMap(form.queryRows)
  if (Object.keys(query).length > 0) cfg.query = query
  if (form.bodyKind === 'form') {
    const data = kvRowsToMap(form.formRows)
    if (Object.keys(data).length > 0) cfg.data = data
  } else if (form.bodyKind === 'json' && form.bodyText.trim()) {
    cfg.body = parseJsonBody(form.bodyText)
  } else if (form.bodyKind === 'raw' && form.bodyText) {
    cfg.body = form.bodyText
  }
  return cfg
}

/** 元素 config → JDBC 表单编辑态 */
export function parseJdbcProcessorForm(element: Record<string, unknown> | undefined): JdbcProcessorForm {
  const cfg = readConfig(element)
  const rawArgs = Array.isArray(cfg.args) ? cfg.args : []
  const args = rawArgs.filter((item): item is string => typeof item === 'string')
  return {
    ref: pickString(cfg.datasource),
    sql: pickString(cfg.sql),
    args,
  }
}

/** JDBC 表单编辑态 → 元素 config（仅含 Ryze 键） */
export function toJdbcConfig(form: JdbcProcessorForm): Record<string, unknown> {
  const cfg: Record<string, unknown> = {}
  if (form.ref.trim()) cfg.datasource = form.ref.trim()
  if (form.sql.trim()) cfg.sql = form.sql.trim()
  if (form.args.length > 0) cfg.args = [...form.args]
  return cfg
}

function toExtractorRow(raw: unknown): ProcessorExtractor {
  const obj = isRecord(raw) ? raw : {}
  return {
    enabled: obj.enabled !== false,
    source: pickString(obj.source),
    expression: pickString(obj.expression),
    variableName: pickString(obj.variableName),
    description: pickString(obj.description),
  }
}

/** 处理器元素 → 表单编辑态 */
export function parseProcessorElement(element: Record<string, unknown> | undefined): ProcessorElementForm {
  const testclass = pickString(element?.testclass)
  return {
    testclass: testclass === 'http' || testclass === 'jdbc' ? testclass : '',
    http: parseHttpProcessorForm(testclass === 'http' ? element : undefined),
    jdbc: parseJdbcProcessorForm(testclass === 'jdbc' ? element : undefined),
    extractors: Array.isArray(element?.extractors) ? element.extractors.map(toExtractorRow) : [],
  }
}

/** 表单编辑态 → 处理器元素；保留元素级平台 overlay（enabled/sortOrder），config 仅含 Ryze 键 */
export function toProcessorElement(
  element: Record<string, unknown> | undefined,
  form: ProcessorElementForm,
): Record<string, unknown> {
  const overlay: Record<string, unknown> = { ...(element ?? {}) }
  delete overlay.testclass
  delete overlay.config
  delete overlay.extractors
  LEGACY_CONFIG_KEYS.forEach((key) => delete overlay[key])
  const config = form.testclass === 'http'
    ? toHttpConfig(form.http)
    : form.testclass === 'jdbc'
      ? toJdbcConfig(form.jdbc)
      : {}
  const extractors = form.extractors
    // 丢弃全空行（空态默认行/误加的空行不落库），部分填写行保留避免编辑中断
    .filter((item) => item.source.trim() || item.expression.trim() || item.variableName.trim() || item.description.trim())
    .map((item) => ({ ...item }))
  return { ...overlay, testclass: form.testclass, config, extractors }
}

export function isProcessorComponentType(type: ApiComponentType): boolean {
  return type === 'preprocessor' || type === 'postprocessor'
}

/** 处理器基础信息（启用/排序号）与新配置合并时的默认值 */
export function defaultProcessorConfig(): Record<string, unknown> {
  return { enabled: true, sortOrder: 0 }
}

// 组件排序号走顶层 sort_order 列（payload.sortOrder），config 不再承载
/** 组件 config 默认值：仅补齐启用开关，启用态各类型均有 */
export function defaultComponentConfig(): Record<string, unknown> {
  return { enabled: true }
}

/** 新建处理器组件的 config 默认值：类型默认 http，避免新建后还需手动点选 */
export function createProcessorComponentConfig(): Record<string, unknown> {
  return { enabled: true, testclass: 'http', config: {}, extractors: [] }
}

/** 解析公共组件 config 为对象，无法解析返回空对象（避免引入崩溃） */
export function parseComponentConfig(config: string | null): Record<string, unknown> {
  if (!config) return {}
  try {
    const parsed: unknown = JSON.parse(config)
    if (isRecord(parsed)) {
      return parsed
    }
    return {}
  } catch {
    return {}
  }
}

/** 提取器资产 → 处理器内嵌提取器行（复制引入，独立副本） */
export function extractorsFromComponents(items: ApiComponentListItem[]): ProcessorExtractor[] {
  return items.map((item) => toExtractorRow(parseComponentConfig(item.config)))
}

/** 验证器资产 → 接口验证器元素（复制引入，独立副本；字段与 ValidatorForm 读取一致） */
export function validatorFromComponent(item: ApiComponentListItem): Record<string, unknown> {
  const cfg = parseComponentConfig(item.config)
  return {
    target: pickString(cfg.target, 'status'),
    expression: pickString(cfg.expression),
    operator: pickString(cfg.operator, 'eq'),
    expected: pickString(cfg.expected),
    description: pickString(cfg.description),
  }
}

/** 提取器资产 → 接口提取器元素（复制引入，独立副本；字段与 ExtractorForm 读取一致） */
export function extractorFromComponent(item: ApiComponentListItem): Record<string, unknown> {
  const cfg = parseComponentConfig(item.config)
  return {
    source: pickString(cfg.source, 'body'),
    expression: pickString(cfg.expression),
    variableName: pickString(cfg.variableName),
    description: pickString(cfg.description),
  }
}

/** 前置/后置处理器资产 → 接口处理器元素（复制引入，独立副本；元素结构同 ProcessorForm 读取的 Ryze 元素） */
export function processorFromComponent(item: ApiComponentListItem, type: 'pre' | 'post'): Record<string, unknown> {
  const cfg = parseComponentConfig(item.config)
  return {
    type,
    testclass: pickString(cfg.testclass),
    config: isRecord(cfg.config) ? cfg.config : {},
    extractors: Array.isArray(cfg.extractors) ? cfg.extractors.map(toExtractorRow) : [],
  }
}