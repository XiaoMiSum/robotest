import type {
  ApiDebugAuth,
  ApiDebugExecuteReq,
  ApiDebugExecuteResp,
  ApiDebugKeyValue,
  ApiDebugRequestBody,
  ApiDebugRestoreResp,
  DebugTab,
} from '@/types'

/** 同时最多打开的调试标签数（SRS 3.1） */
export const MAX_DEBUG_TABS = 10

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS', 'HEAD', 'CONNECT'] as const

/** 未命名标签的初始名称；首次执行后自动替换为「方法 + 路径」 */
export const NEW_TAB_NAME = '新建请求'

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
    bodies: { none: null, json: '', form: '', raw: '' },
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

function enabledEntries(entries: ApiDebugKeyValue[]): ApiDebugKeyValue[] {
  return entries.filter((entry) => entry.enabled && entry.key.trim() !== '')
}

/**
 * 认证换算：Basic 由前端编码进 Authorization 头提交；
 * Digest 需服务端 WWW-Authenticate challenge，无法离线构造，交由后续版本。
 */
export function buildAuthHeader(auth: ApiDebugAuth): ApiDebugKeyValue | null {
  if (auth.type !== 'basic' || !auth.username) {
    return null
  }
  const encoded = btoa(`${auth.username}:${auth.password ?? ''}`)
  return { key: 'Authorization', value: `Basic ${encoded}`, enabled: true }
}

export function buildExecutePayload(tab: DebugTab, environmentId?: string): ApiDebugExecuteReq {
  const headers = [...enabledEntries(tab.headers)]
  const authHeader = buildAuthHeader(tab.auth)
  if (authHeader && !headers.some((h) => h.key.toLowerCase() === 'authorization')) {
    // 手工 Authorization 头优先于认证配置，避免双重身份冲突
    headers.push(authHeader)
  }
  return {
    protocol: 'http',
    method: tab.method,
    url: tab.url,
    headers,
    params: enabledEntries(tab.params),
    body: activeBody(tab),
    processors: tab.processors,
    timeoutMs: tab.responseTimeoutMs,
    environmentId,
  }
}

function activeBody(tab: DebugTab): ApiDebugRequestBody | undefined {
  if (tab.bodies.none !== null) {
    return undefined
  }
  const content = tab.bodies.json !== '' ? parseOrRaw(tab.bodies.json)
    : tab.bodies.form !== '' ? parseForm(tab.bodies.form)
      : tab.bodies.raw !== '' ? tab.bodies.raw
        : undefined
  if (content === undefined) {
    return undefined
  }
  if (tab.bodies.json !== '') {
    return { type: 'json', content }
  }
  if (tab.bodies.form !== '') {
    return { type: 'form', content }
  }
  return { type: 'raw', content }
}

/** JSON 文本非法时降级为 raw 字符串提交，与后端 cURL 解析降级口径一致 */
function parseOrRaw(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

/** 表单文本按 k=v&k2=v2 解析为对象（URL 解码） */
function parseForm(text: string): Record<string, string> {
  const result: Record<string, string> = {}
  for (const pair of text.split('&')) {
    const eq = pair.indexOf('=')
    if (eq <= 0) continue
    const key = decodeURIComponent(pair.slice(0, eq))
    result[key] = decodeURIComponent(pair.slice(eq + 1))
  }
  return result
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

/** cURL 导入回填当前标签并标记脏 */
export function applyCurlToTab(tab: DebugTab, parsed: CurlLike): void {
  tab.method = parsed.method
  tab.url = parsed.url
  tab.headers = parsed.headers.map((h) => ({ ...h }))
  tab.params = (parsed.params ?? []).map((p) => ({ ...p }))
  const type = parsed.body.type
  if (type === 'none') {
    tab.bodies.none = null
  } else if (type === 'form') {
    tab.bodies.form = stringifyForm(parsed.body.content)
  } else if (type === 'raw') {
    tab.bodies.raw = String(parsed.body.content ?? '')
  } else {
    tab.bodies.json = typeof parsed.body.content === 'string'
      ? parsed.body.content
      : JSON.stringify(parsed.body.content, null, 2)
  }
  tab.dirty = true
}

function stringifyForm(content: unknown): string {
  if (!content || typeof content !== 'object') return ''
  return Object.entries(content as Record<string, unknown>)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')
}

/** 从历史记录恢复：生成新标签快照（响应同时回填） */
export function tabFromRestore(record: ApiDebugRestoreResp): DebugTab {
  const tab = createTab()
  const request = record.request
  tab.method = request.method ?? 'GET'
  tab.url = request.url ?? ''
  tab.headers = (request.headers ?? []).map((h) => ({ ...h }))
  tab.params = (request.params ?? []).map((p) => ({ ...p }))
  const restoredType = request.body?.type
  if (restoredType === 'form') {
    tab.bodies.form = stringifyForm(request.body?.content)
  } else if (restoredType === 'raw') {
    tab.bodies.raw = String(request.body?.content ?? '')
  } else if (restoredType === 'json') {
    tab.bodies.json = typeof request.body?.content === 'string'
      ? request.body.content
      : JSON.stringify(request.body?.content, null, 2)
  } else {
    tab.bodies.none = null
  }
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
