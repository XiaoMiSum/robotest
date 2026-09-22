import type { ApiMockMatchRule, ApiMockMatchRuleType, ApiMockSavePayload } from '@/types'

export type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

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

export const MATCH_RULE_TYPES: { value: ApiMockMatchRuleType; label: string }[] = [
  { value: 'header', label: '请求头' },
  { value: 'param', label: 'Query 参数' },
  { value: 'body', label: '请求体 (JSONPath)' },
]

export const BODY_TYPES: { value: string; label: string }[] = [
  { value: 'json', label: 'JSON' },
  { value: 'text', label: 'Text' },
  { value: 'xml', label: 'XML' },
  { value: 'binary', label: 'Binary' },
]

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

/** 新建空匹配规则行 */
export function createEmptyMatchRule(): ApiMockMatchRule {
  return { type: 'header', name: '', value: '' }
}

/** 编辑器表单状态 */
export interface MockEditorForm {
  name: string
  description: string
  method: string
  path: string
  enabled: boolean
  followApi: boolean
  matchRules: ApiMockMatchRule[]
  responseStatus: number
  responseHeaders: Array<{ key: string; value: string }>
  responseBodyType: string
  responseBody: string
  delayMs: number
  groupSize: number
}

export function createEditorForm(): MockEditorForm {
  return {
    name: '',
    description: '',
    method: 'GET',
    path: '/',
    enabled: true,
    followApi: false,
    matchRules: [createEmptyMatchRule()],
    responseStatus: 200,
    responseHeaders: [{ key: 'Content-Type', value: 'application/json' }],
    responseBodyType: 'json',
    responseBody: '',
    delayMs: 0,
    groupSize: 0,
  }
}

/** 详情 → 表单（编辑回显） */
export function detailToForm(detail: {
  name: string
  description: string | null
  method: string
  path: string
  enabled: boolean
  followApi: boolean
  matchRules: ApiMockMatchRule[]
  responseStatus: number
  responseHeaders: Record<string, string> | null
  responseBodyType: string
  responseBody: string | null
  delayMs: number
  groupSize: number
}): MockEditorForm {
  const headers = detail.responseHeaders
    ? Object.entries(detail.responseHeaders).map(([key, value]) => ({ key, value }))
    : [{ key: 'Content-Type', value: 'application/json' }]
  const rules = detail.matchRules?.length
    ? detail.matchRules.map((r) => ({ ...r }))
    : [createEmptyMatchRule()]
  return {
    name: detail.name,
    description: detail.description ?? '',
    method: detail.method,
    path: detail.path,
    enabled: detail.enabled,
    followApi: detail.followApi,
    matchRules: rules,
    responseStatus: detail.responseStatus,
    responseHeaders: headers,
    responseBodyType: detail.responseBodyType,
    responseBody: detail.responseBody ?? '',
    delayMs: detail.delayMs,
    groupSize: detail.groupSize ?? 0,
  }
}

/** 表单 → API 载荷 */
export function formToPayload(form: MockEditorForm): ApiMockSavePayload {
  return {
    name: form.name.trim(),
    description: form.description.trim() || null,
    method: form.method,
    path: form.path.trim(),
    enabled: form.enabled,
    followApi: form.followApi,
    matchRules: form.matchRules.filter((r) => r.name.trim() !== ''),
    responseStatus: form.responseStatus,
    responseHeaders: keyValuesToRecord(form.responseHeaders),
    responseBodyType: form.responseBodyType as ApiMockSavePayload['responseBodyType'],
    responseBody: form.responseBody || null,
    delayMs: form.delayMs,
  }
}

function keyValuesToRecord(rows: Array<{ key: string; value: string }>): Record<string, string> {
  const record: Record<string, string> = {}
  for (const row of rows) {
    if (row.key.trim()) record[row.key.trim()] = row.value
  }
  return record
}
