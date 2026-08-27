import api from '@/services'
import type {
  ApiBuiltinFunctionGroup,
  ApiCustomFunctionDetail,
  ApiCustomFunctionListItem,
  ApiCustomFunctionSaveReq,
  ApiFunctionEvaluateReq,
  ApiFunctionEvaluateResp,
  ApiFunctionScope,
} from '@/types'

function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}
function patch<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.patch(url, null, { params }) as unknown as Promise<T>
}
function del<T>(url: string): Promise<T> {
  return api.delete(url) as unknown as Promise<T>
}

// ==================== 内置函数目录 ====================

export function fetchBuiltinCatalog(): Promise<ApiBuiltinFunctionGroup[]> {
  return get('/project/functions/builtin')
}

// ==================== 表达式试算 ====================

export function evaluateFunction(expression: string): Promise<ApiFunctionEvaluateResp> {
  return post('/project/functions/evaluate', { expression } satisfies ApiFunctionEvaluateReq)
}

// ==================== 自定义函数 CRUD ====================

export interface FetchCustomFunctionsParams {
  enabled?: boolean
  scope?: ApiFunctionScope
  keyword?: string
}

export function fetchCustomFunctions(params?: FetchCustomFunctionsParams): Promise<ApiCustomFunctionListItem[]> {
  return get('/project/functions/custom-functions', params as Record<string, unknown> | undefined)
}

export function fetchCustomFunctionDetail(id: string): Promise<ApiCustomFunctionDetail> {
  return get(`/project/functions/custom-functions/${id}`)
}

export function createCustomFunction(data: ApiCustomFunctionSaveReq): Promise<{ id: string }> {
  return post('/project/functions/custom-functions', data)
}

export function updateCustomFunction(id: string, data: ApiCustomFunctionSaveReq): Promise<boolean> {
  return put(`/project/functions/custom-functions/${id}`, data)
}

export function toggleCustomFunction(id: string, enabled: boolean): Promise<boolean> {
  return patch(`/project/functions/custom-functions/${id}/toggle`, { enabled: String(enabled) })
}

export function deleteCustomFunction(id: string): Promise<boolean> {
  return del(`/project/functions/custom-functions/${id}`)
}
