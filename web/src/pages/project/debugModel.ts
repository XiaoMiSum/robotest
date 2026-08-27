import type {
  ApiDebugAuth,
  ApiDebugExecuteReq,
  ApiDebugExecuteResp,
  ApiDebugKeyValue,
  ApiDebugRawSubtype,
  ApiDebugRequestBody,
  ApiDebugRestoreResp,
  DebugTab,
} from '@/types'

/** 同时最多打开的调试标签数（SRS 3.1） */
export const MAX_DEBUG_TABS = 10

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS', 'HEAD', 'CONNECT'] as const

/** 未命名标签的初始名称；首次执行后自动替换为「方法 + 路径」 */
export const NEW_TAB_NAME = '新建请求'

/** raw 子类型对应 Content-Type；text 不加头（后端按文本处理） */
export const RAW_SUBTYPE_CONTENT_TYPE: Record<ApiDebugRawSubtype, string | undefined> = {
  text: undefined,
  json: 'application/json',
  xml: 'application/xml',
  html: 'text/html',
  javascript: 'text/javascript',
}

/** 提交执行时请求体类型（后端枚举），debug 四态映射到该枚举（详细设计 5.1） */
const FORM_ENCODED_CONTENT_TYPE = 'application/x-www-form-urlencoded'

let tabSeq = 0

export function createTab(): DebugTab {
  tabSeq += 1
  return {
    id: `tab-${Date.now()}-${tabSeq}`,
    name: '',
    method: 'GET',
    url: '',
    headers: [],
    params: [],
    bodies: { urlencoded: [], raw: null },
    bodyType: 'none',
    auth: { type: 'none' },
    responseTimeoutMs: 30000,
    dirty: false,
    response: null,
  }
}

/** 标签名展示：未命名时显示占位名 */
export function tabTitle(tab: DebugTab): string {
  return tab.name || NEW_TAB_NAME
}

/** 自动命名规则：方法 + URL 路径（与后端 autoName 一致），路径缺失时回退 URL 本身 */
export function autoTabName(method: string, url: string): string {
  let rest = url.trim()
  const schemeEnd = rest.indexOf('://')
  if (schemeEnd >= 0) {
    const pathStart = rest.indexOf('/', schemeEnd + 3)
    rest = pathStart < 0 ? '/' : rest.slice(pathStart)
  }
  const query = rest.indexOf('?')
  const path = query < 0 ? rest : rest.slice(0, query)
  return `${method.toUpperCase()} ${path || '/'}`
}

/** 过滤禁用/空键项，并剥离 description 等 UI 字段 */
function enabledEntries(entries: ApiDebugKeyValue[]): ApiDebugKeyValue[] {
  return entries
    .filter((entry) => entry.enabled && entry.key.trim() !== '')
    .map((entry) => ({ key: entry.key, value: entry.value, enabled: true }))
}

/**
 * 认证换算：Basic 编码进 Authorization；Bearer 拼 Authorization: Bearer；
 * API Key 换算为自定义请求头（缺省键 X-API-Key）；
 * Digest 需服务端 WWW-Authenticate challenge，无法离线构造，交由后续版本。
 */
export function buildAuthHeader(auth: ApiDebugAuth): ApiDebugKeyValue | null {
  if (auth.type === 'basic' && auth.username) {
    const encoded = btoa(`${auth.username}:${auth.password ?? ''}`)
    return { key: 'Authorization', value: `Basic ${encoded}`, enabled: true }
  }
  if (auth.type === 'bearer' && auth.token) {
    return { key: 'Authorization', value: `Bearer ${auth.token}`, enabled: true }
  }
  if (auth.type === 'apiKey' && auth.apiKeyValue) {
    return { key: auth.apiKeyName?.trim() || 'X-API-Key', value: auth.apiKeyValue, enabled: true }
  }
  return null
}

export function buildExecutePayload(tab: DebugTab, environmentId?: string): ApiDebugExecuteReq {
  const headers = [...enabledEntries(tab.headers)]
  const authHeader = buildAuthHeader(tab.auth)
  if (authHeader && !headers.some((h) => h.key.toLowerCase() === authHeader.key.toLowerCase())) {
    // 手工同键头优先于认证配置，避免双重身份冲突
    headers.push(authHeader)
  }
  const body = activeBody(tab)
  applyBodyContentType(tab, headers, body)
  return {
    protocol: 'http',
    method: tab.method,
    url: tab.url,
    headers,
    params: enabledEntries(tab.params),
    body,
    timeoutMs: tab.responseTimeoutMs,
    environmentId,
  }
}

/** x-www-form-urlencoded 按 k=v 编码串提交（后端 form 语义，详细设计 5.1）；raw 按原始文本提交 */
function activeBody(tab: DebugTab): ApiDebugRequestBody | undefined {
  if (tab.bodyType === 'raw') {
    if (!tab.bodies.raw?.text) {
      return undefined
    }
    return { type: 'raw', content: tab.bodies.raw.text }
  }
  if (tab.bodyType !== 'urlencoded') {
    return undefined
  }
  const active = enabledEntries(tab.bodies.urlencoded)
  if (active.length === 0) {
    return undefined
  }
  return { type: 'form', content: joinForm(active) }
}

function joinForm(entries: ApiDebugKeyValue[]): string {
  return entries.map((e) => `${encodeURIComponent(e.key)}=${encodeURIComponent(e.value)}`).join('&')
}

/** 表单固定 urlencoded 编码串；raw 按所选子类型注入 Content-Type；均不覆盖用户手工设置（详细设计 5.1） */
function applyBodyContentType(tab: DebugTab, headers: ApiDebugKeyValue[], body?: ApiDebugRequestBody): void {
  if (!body) return
  const hasContentType = headers.some((h) => h.key.toLowerCase() === 'content-type')
  if (hasContentType) return
  let contentType: string | undefined
  if (body.type === 'form') {
    contentType = FORM_ENCODED_CONTENT_TYPE
  } else if (body.type === 'raw' && tab.bodyType === 'raw') {
    contentType = tab.bodies.raw?.text ? RAW_SUBTYPE_CONTENT_TYPE[tab.bodies.raw.subtype] : undefined
  }
  if (contentType) {
    headers.push({ key: 'Content-Type', value: contentType, enabled: true })
  }
}

/** 执行成功后的标签状态更新：自动命名 + 清除脏标记 */
export function markExecuted(tab: DebugTab, response: ApiDebugExecuteResp): void {
  if (!tab.name) {
    tab.name = autoTabName(tab.method, tab.url)
  }
  tab.response = response
  tab.dirty = false
}

interface CurlLike {
  method: string
  url: string
  headers: ApiDebugKeyValue[]
  body: { type: string; content?: unknown }
  params?: ApiDebugKeyValue[]
}

/** cURL 导入回填当前标签并标记脏；解析的 body 类型映射为四态（form→urlencoded，json/raw→raw） */
export function applyCurlToTab(tab: DebugTab, parsed: CurlLike): void {
  tab.method = parsed.method
  tab.url = parsed.url
  tab.headers = parsed.headers.map((h) => ({ ...h }))
  tab.params = (parsed.params ?? []).map((p) => ({ ...p }))
  applyBodyCache(tab, parsed.body.type, parsed.body.content)
  tab.dirty = true
}

/** 从历史记录恢复：生成新标签快照（响应同时回填） */
export function tabFromRestore(record: ApiDebugRestoreResp): DebugTab {
  const tab = createTab()
  const request = record.request
  tab.method = request.method ?? 'GET'
  tab.url = request.url ?? ''
  tab.headers = (request.headers ?? []).map((h) => ({ ...h }))
  tab.params = (request.params ?? []).map((p) => ({ ...p }))
  applyBodyCache(tab, request.body?.type, request.body?.content)
  // 认证头与手工头无法区分，降级还原为普通头（详细设计 5.1）
  if (tab.url) {
    tab.name = autoTabName(tab.method, tab.url)
  }
  tab.response = {
    debugRecordId: record.debugRecordId,
    status: 'success',
    responseStatus: record.response.statusCode,
    responseHeaders: record.response.headers ?? undefined,
    responseBody: record.response.body,
    durationMs: record.response.elapsed,
    size: record.response.size,
  }
  tab.dirty = false
  return tab
}

/** 后端 body 类型（json/form/raw 等）写入四态缓存；无关类型置 none（详细设计 5.1 映射） */
function applyBodyCache(tab: DebugTab, type: string | undefined, content: unknown): void {
  if (type === 'form') {
    tab.bodyType = 'urlencoded'
    tab.bodies.urlencoded = objectToEntries(content)
  } else if (type === 'json') {
    tab.bodyType = 'raw'
    tab.bodies.raw = { text: stringifyContent(content), subtype: 'json' }
  } else if (type === 'raw') {
    tab.bodyType = 'raw'
    tab.bodies.raw = { text: typeof content === 'string' ? content : stringifyContent(content), subtype: 'text' }
  } else {
    tab.bodyType = 'none'
  }
}

function objectToEntries(content: unknown): ApiDebugKeyValue[] {
  if (!content || typeof content !== 'object') return []
  return Object.entries(content as Record<string, unknown>).map(([key, value]) => ({
    key,
    value: typeof value === 'string' ? value : JSON.stringify(value),
    enabled: true,
  }))
}

function stringifyContent(content: unknown): string {
  if (content === undefined || content === null) return ''
  if (typeof content === 'string') return content
  return JSON.stringify(content, null, 2)
}