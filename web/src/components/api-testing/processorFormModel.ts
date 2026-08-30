import type { ApiComponentListItem, ApiComponentType } from '@/types'

/** 处理器内嵌提取器行，与提取器资产（ExtractorForm）字段一致，保证从公共组件复制后结构可回读 */
export interface ProcessorExtractor {
  enabled: boolean
  source: string
  expression: string
  variableName: string
  description: string
}

/** 键值对编辑器行（请求头 / Query / 表单） */
export interface ProcessorKvRow {
  key: string
  value: string
}

/** 请求体编辑类型，与 Ryze `data`（表单）/`body`（JSON/原始）对应 */
export type ProcessorBodyKind = 'none' | 'json' | 'form' | 'raw'

/** HTTP 处理器表单编辑态 */
export interface HttpProcessorForm {
  method: string
  baseUrl: string
  path: string
  http2: boolean
  headerRows: ProcessorKvRow[]
  queryRows: ProcessorKvRow[]
  bodyKind: ProcessorBodyKind
  bodyText: string
  formRows: ProcessorKvRow[]
}

/** JDBC 处理器表单编辑态，`datasource` 为环境数据源 `ref_name` */
export interface JdbcProcessorForm {
  datasource: string
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

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function pickString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function pickBoolean(value: unknown): boolean {
  return value === true
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

/** kv 行 → Map（忽略空键，供保存为 Ryze headers/query/data） */
export function kvRowsToMap(rows: ProcessorKvRow[]): Record<string, string> {
  const out: Record<string, string> = {}
  rows.forEach((row) => {
    const key = row.key.trim()
    if (key) out[key] = row.value
  })
  return out
}

/** Map → kv 行（供表单回填） */
export function mapToKvRows(map: Record<string, string> | undefined): ProcessorKvRow[] {
  return Object.entries(map ?? {}).map(([key, value]) => ({ key, value }))
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
    baseUrl: pickString(cfg.base_url),
    path: pickString(cfg.path),
    http2: pickBoolean(cfg['http/2']),
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
  if (form.baseUrl.trim()) cfg.base_url = form.baseUrl.trim()
  if (form.path.trim()) cfg.path = form.path.trim()
  if (form.http2) cfg['http/2'] = true
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
    datasource: pickString(cfg.datasource),
    sql: pickString(cfg.sql),
    args,
  }
}

/** JDBC 表单编辑态 → 元素 config（仅含 Ryze 键） */
export function toJdbcConfig(form: JdbcProcessorForm): Record<string, unknown> {
  const cfg: Record<string, unknown> = {}
  if (form.datasource.trim()) cfg.datasource = form.datasource.trim()
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
  const extractors = form.extractors.map((item) => ({ ...item }))
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