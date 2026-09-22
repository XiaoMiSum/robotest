import { get, post, put, patch, del } from '@/services'
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
  return del('/project/components/batch', { data: { ids } satisfies ApiComponentBatchReq })
}
