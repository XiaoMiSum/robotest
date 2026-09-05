import type {
  ApiDebugAuth,
  ApiDebugKeyValue,
  ApiDebugRawSubtype,
  ApiInterfaceCreateReq,
  ApiInterfaceDetail,
  ApiInterfaceUpdateReq,
  ApiInterfaceImportResult,
  ProjectModule,
} from '@/types'

/** 列表查询参数：空值不发送，避免后端把空串当过滤条件 */
export function buildInterfaceListQuery(input: {
  pageNo: number
  pageSize: number
  moduleId?: string | null
  search?: string
  status?: string
  view?: string
}): Record<string, unknown> {
  const query: Record<string, unknown> = { pageNo: input.pageNo, pageSize: input.pageSize }
  if (input.moduleId) query.moduleId = input.moduleId
  if (input.search) query.search = input.search
  if (input.status) query.status = input.status
  if (input.view && input.view !== 'all') query.view = input.view
  return query
}

/** el-tag 语义色取值（Element Plus tag type） */
export type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

/** HTTP 方法 → el-tag 语义色 */
export const METHOD_TAG_TYPES: Record<string, TagType> = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  PATCH: 'warning',
  DELETE: 'danger',
  HEAD: 'info',
  OPTIONS: 'info',
}

export function methodTagType(method: string): TagType {
  return METHOD_TAG_TYPES[method.toUpperCase()] ?? 'info'
}

interface ModuleOption {
  value: string
  label: string
  children: ModuleOption[] | undefined
}

/** 模块树 → el-tree-cascader 数据：叶子文档节点不可选（接口只挂目录） */
export function toSelectableModuleOptions(modules: ProjectModule[]): ModuleOption[] {
  return modules
    .filter((node) => node.type === 'directory')
    .map((node) => {
      const children = node.children?.length ? toSelectableModuleOptions(node.children) : undefined
      return { value: node.id, label: node.name, children }
    })
}

/** 展平模块树为 id→名称映射（列表列展示用，含目录与文档） */
export function flattenModuleNames(modules: ProjectModule[], acc: Map<string, string> = new Map()): Map<string, string> {
  for (const node of modules) {
    acc.set(node.id, node.name)
    if (node.children?.length) flattenModuleNames(node.children, acc)
  }
  return acc
}

const EMPTY_KEY_VALUE: ApiDebugKeyValue = { key: '', value: '', enabled: true }

function normalizeKeyValues(rows: ApiDebugKeyValue[] | null | undefined): ApiDebugKeyValue[] {
  const list = (rows ?? []).filter((row) => row.key.trim() !== '')
  return list.length ? list : [{ ...EMPTY_KEY_VALUE }]
}

/** 编辑器表单状态：详情回显与新建默认值统一在此构造 */
export interface InterfaceEditorForm {
  name: string
  protocol: 'http'
  method: string
  path: string
  description: string
  moduleId: string | null
  status: 'enabled' | 'disabled'
  headers: ApiDebugKeyValue[]
  params: ApiDebugKeyValue[]
  bodyType: 'none' | 'urlencoded' | 'raw'
  /** raw 子类型，与快速调试一致（text/json/xml/html/javascript） */
  rawSubtype: ApiDebugRawSubtype
  rawText: string
  urlencodedRows: ApiDebugKeyValue[]
  /** 响应验证器 [{..}]，仅定义存储 */
  validators: Record<string, unknown>[]
  /** 响应提取器 [{..}]，仅定义存储 */
  extractors: Record<string, unknown>[]
  /** 认证配置（No Auth/Bearer/API Key/Basic），持久化于接口 auth 列 */
  auth: ApiDebugAuth
  /** 响应示例 - 响应体 JSON 文本 */
  responseBodyText: string
  /** 响应示例 - 响应头 JSON 文本 */
  responseHeadersText: string
  /** 响应示例原始对象（含 status），内部保留用于保存时不丢失 status */
  _responseExampleRaw?: Record<string, unknown> | null
}

/** 后端 body.type → 编辑器四态：form→urlencoded，json/raw→raw，其余 none（详细设计 6.3 映射） */
function mapBodyType(type: string | undefined): InterfaceEditorForm['bodyType'] {
  if (type === 'form') return 'urlencoded'
  if (type === 'json' || type === 'raw') return 'raw'
  return 'none'
}

function keyValuesFromContent(content: unknown): ApiDebugKeyValue[] | null {
  if (!Array.isArray(content)) return null
  return content.map((entry) => {
    const item = entry as Record<string, unknown>
    return { key: String(item.key ?? ''), value: String(item.value ?? ''), enabled: item.enabled !== false }
  })
}

/** 后端 auth 列（Map）→ 表单认证结构；无/未知类型归为 No Auth */
function authFromDetail(auth: Record<string, unknown> | null | undefined): ApiDebugAuth {
  if (!auth || typeof auth !== 'object') return { type: 'none' }
  const type = auth.type
  if (type === 'basic') {
    return { type: 'basic', username: String(auth.username ?? ''), password: String(auth.password ?? '') }
  }
  if (type === 'bearer') {
    return { type: 'bearer', token: String(auth.token ?? '') }
  }
  if (type === 'apiKey') {
    return { type: 'apiKey', apiKeyName: String(auth.apiKeyName ?? ''), apiKeyValue: String(auth.apiKeyValue ?? '') }
  }
  return { type: 'none' }
}

/** 表单认证结构 → 后端 auth 列；No Auth 不发送 */
function authToPayload(auth: ApiDebugAuth): Record<string, unknown> | undefined {
  if (auth.type === 'basic') {
    return { type: 'basic', username: auth.username ?? '', password: auth.password ?? '' }
  }
  if (auth.type === 'bearer') {
    return { type: 'bearer', token: auth.token ?? '' }
  }
  if (auth.type === 'apiKey') {
    return { type: 'apiKey', apiKeyName: auth.apiKeyName ?? '', apiKeyValue: auth.apiKeyValue ?? '' }
  }
  return undefined
}

export function createEditorForm(detail?: ApiInterfaceDetail): InterfaceEditorForm {
  const common = {
    protocol: 'http' as const,
    status: 'enabled' as const,
    headers: normalizeKeyValues(detail?.headers),
    params: normalizeKeyValues(detail?.params),
    validators: detail?.validators ?? [],
    extractors: detail?.extractors ?? [],
    auth: authFromDetail(detail?.auth),
    rawSubtype: 'text' as ApiDebugRawSubtype,
    rawText: '',
    urlencodedRows: [{ ...EMPTY_KEY_VALUE }],
  }
  if (!detail) {
    return {
      name: '',
      method: 'GET',
      path: '/',
      description: '',
      moduleId: null,
      bodyType: 'none',
      responseBodyText: '',
      responseHeadersText: '',
      _responseExampleRaw: null,
      ...common,
    }
  }
  const bodyType = mapBodyType(detail.body?.type)
  let rawSubtype: ApiDebugRawSubtype = 'text'
  let rawText = ''
  let urlencodedRows: ApiDebugKeyValue[] = [{ ...EMPTY_KEY_VALUE }]
  const content = detail.body?.content
  if (bodyType === 'raw') {
    // json 保留 json 子类型；raw 按内容前缀推断，否则缺省 text（对齐 debugModel 回读）
    if (detail.body?.type === 'json') rawSubtype = 'json'
    else if (typeof content === 'string') rawSubtype = /^\s*[{[]/.test(content) ? 'json' : 'text'
    else rawSubtype = 'json'
    rawText = typeof content === 'string' ? content : JSON.stringify(content ?? {}, null, 2)
  } else if (bodyType === 'urlencoded') {
    urlencodedRows = normalizeKeyValues(keyValuesFromContent(content))
  }
  return {
    name: detail.name,
    protocol: 'http',
    method: detail.method,
    path: detail.path,
    description: detail.description ?? '',
    moduleId: detail.moduleId ?? null,
    status: detail.status,
    headers: normalizeKeyValues(detail.headers),
    params: normalizeKeyValues(detail.params),
    bodyType,
    rawSubtype,
    rawText,
    urlencodedRows,
    validators: detail.validators ?? [],
    extractors: detail.extractors ?? [],
    auth: authFromDetail(detail.auth),
    responseBodyText: detail.responseExample?.body != null
      ? JSON.stringify(detail.responseExample.body, null, 2) : '',
    responseHeadersText: detail.responseExample?.headers != null
      ? JSON.stringify(detail.responseExample.headers, null, 2) : '',
    _responseExampleRaw: detail.responseExample as Record<string, unknown> ?? null,
  }
}

/** 表单响应示例 → {body, headers, status} 结构；解析失败返回错误 */
function buildResponseExample(
  form: InterfaceEditorForm,
): { value?: Record<string, unknown>; error?: string } {
  const bodyText = form.responseBodyText.trim()
  const headersText = form.responseHeadersText.trim()
  if (!bodyText && !headersText) return { value: undefined }
  let body: unknown = undefined
  let headers: unknown = undefined
  if (bodyText) {
    try { body = JSON.parse(bodyText) } catch { return { error: '响应体 JSON 格式非法，请修正后再保存' } }
  }
  if (headersText) {
    try { headers = JSON.parse(headersText) } catch { return { error: '响应头 JSON 格式非法，请修正后再保存' } }
  }
  // status 从原始数据保留（编辑器不展示，但保存时必须存在）
  const status = form._responseExampleRaw?.status ?? 200
  return { value: { body, status, headers } }
}

/** 表单 → 创建请求体；空键行剔除，raw+json 解析失败返回错误提示而非静默丢弃 */
export function toCreatePayload(form: InterfaceEditorForm): { req: ApiInterfaceCreateReq; error?: string } {
  const req: ApiInterfaceCreateReq = {
    name: form.name.trim(),
    protocol: form.protocol,
    method: form.method,
    path: form.path.trim(),
    description: form.description.trim() || undefined,
    moduleId: form.moduleId,
    headers: enabledKeyValues(form.headers),
    params: enabledKeyValues(form.params),
    status: form.status,
  }
  switch (form.bodyType) {
    case 'none':
      break
    case 'urlencoded':
      req.body = { type: 'form', content: enabledKeyValues(form.urlencodedRows) }
      break
    case 'raw':
      if (form.rawSubtype === 'json') {
        try {
          req.body = { type: 'json', content: form.rawText.trim() ? JSON.parse(form.rawText) : {} }
        } catch {
          return { req, error: 'JSON 请求体格式非法，请修正后再保存' }
        }
      } else {
        req.body = { type: 'raw', content: form.rawText }
      }
      break
  }
  if (form.validators.length) req.validators = form.validators
  if (form.extractors.length) req.extractors = form.extractors
  const auth = authToPayload(form.auth)
  if (auth) req.auth = auth
  const { value: responseExample, error: responseError } = buildResponseExample(form)
  if (responseError) return { req, error: responseError }
  if (responseExample) req.responseExample = responseExample
  return { req }
}

export function toUpdatePayload(form: InterfaceEditorForm, changeVersion: number): { req: ApiInterfaceUpdateReq; error?: string } {
  const base = toCreatePayload(form)
  return { req: { ...base.req, changeVersion }, error: base.error }
}

function enabledKeyValues(rows: ApiDebugKeyValue[]): ApiDebugKeyValue[] {
  return rows
    .filter((row) => row.key.trim() !== '' && row.enabled)
    .map((row) => ({ key: row.key.trim(), value: row.value, enabled: true }))
}

/** 导入结果摘要文案：created/updated/failed 为服务端 summary 键 */
export function summarizeImportResult(result: ApiInterfaceImportResult): string {
  const { created = 0, updated = 0, failed = 0 } = result.summary
  return `新建 ${created} · 更新 ${updated} · 失败 ${failed}`
}
