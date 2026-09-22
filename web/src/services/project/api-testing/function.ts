import api, { get, post, put, del } from '@/services'
import type {
  ApiBuiltinFunctionGroup,
  ApiCustomFunctionDetail,
  ApiCustomFunctionListItem,
  ApiCustomFunctionSaveReq,
  ApiFunctionEvaluateReq,
  ApiFunctionEvaluateResp,
  ApiFunctionScope,
} from '@/types'

/** PATCH with query params（无 body）— apiFunction 特殊语义 */
function patchWithParams<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.patch(url, null, { params }) as unknown as Promise<T>
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
  return patchWithParams(`/project/functions/custom-functions/${id}/toggle`, { enabled: String(enabled) })
}

export function deleteCustomFunction(id: string): Promise<boolean> {
  return del(`/project/functions/custom-functions/${id}`)
}
