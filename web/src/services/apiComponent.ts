import api from '@/services'
import type {
  ApiComponentBatchReq,
  ApiComponentListItem,
  ApiComponentSaveReq,
  ApiComponentScope,
  ApiComponentType,
  ApiIdResp,
  ApiComponentCopyResp,
  PageResult,
} from '@/types'

function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
  return api.post(url, data, config) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}
function patch<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
  return api.patch(url, data, config) as unknown as Promise<T>
}
function del<T>(url: string, data?: unknown): Promise<T> {
  return api.delete(url, { data }) as unknown as Promise<T>
}

// ==================== 公共组件（/api/project/components） ====================

export interface ComponentListParams {
  pageNo?: number
  pageSize?: number
  type?: ApiComponentType
  enabled?: boolean
  scope?: ApiComponentScope
  keyword?: string
}

/** 分页查询可见范围内的组件列表 */
export function fetchComponents(params?: ComponentListParams): Promise<PageResult<ApiComponentListItem>> {
  return get('/project/components', params as Record<string, unknown> | undefined)
}

/** 创建公共组件 */
export function createComponent(data: ApiComponentSaveReq): Promise<ApiIdResp> {
  return post('/project/components', data)
}

/** 更新公共组件 */
export function updateComponent(id: string, data: ApiComponentSaveReq): Promise<boolean> {
  return put(`/project/components/${id}`, data)
}

/** 启停公共组件 */
export function toggleComponent(id: string, enabled: boolean): Promise<boolean> {
  return patch(`/project/components/${id}/toggle?enabled=${String(enabled)}`)
}

/** 删除公共组件 */
export function deleteComponent(id: string): Promise<boolean> {
  return del(`/project/components/${id}`)
}

/** 复制公共组件 */
export function copyComponent(id: string): Promise<ApiComponentCopyResp> {
  return post(`/project/components/${id}/copy`)
}

/** 批量启停 */
export function batchToggleComponents(ids: string[], enabled: boolean): Promise<boolean> {
  return patch('/project/components/batch/toggle', { ids } satisfies ApiComponentBatchReq, {
    params: { enabled },
  })
}

/** 批量删除 */
export function batchDeleteComponents(ids: string[]): Promise<boolean> {
  return del('/project/components/batch', { ids } satisfies ApiComponentBatchReq)
}
