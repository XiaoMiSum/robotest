import { get, post, put, patch, del } from '@/services'
import type {
  ApiMockAddress,
  ApiMockBatchTogglePayload,
  ApiMockBatchToggleResponse,
  ApiMockDebugRequest,
  ApiMockDebugResponse,
  ApiMockDetail,
  ApiMockItem,
  ApiMockSavePayload,
  PageResult,
} from '@/types'

// ==================== Mock 管理（3.1） ====================

export function fetchMockPage(params: {
  pageNo: number
  pageSize: number
  interfaceId?: string
  search?: string
  enabled?: boolean
}): Promise<PageResult<ApiMockItem>> {
  return get('/project/mocks', { ...params })
}

export function fetchMockDetail(id: string): Promise<ApiMockDetail> {
  return get(`/project/mocks/${id}`)
}

export function createMock(req: ApiMockSavePayload): Promise<string> {
  return post('/project/mocks', req).then((resp) => (resp as { id: string }).id)
}

export function createMockFromInterface(interfaceId: string, req: ApiMockSavePayload): Promise<string> {
  return post(`/project/mocks/from-interface/${interfaceId}`, req).then((resp) => (resp as { id: string }).id)
}

export function updateMock(id: string, req: ApiMockSavePayload): Promise<boolean> {
  return put(`/project/mocks/${id}`, req)
}

export function toggleMock(id: string, enabled: boolean): Promise<boolean> {
  return patch(`/project/mocks/${id}/toggle`, { enabled })
}

export function batchToggleMocks(req: ApiMockBatchTogglePayload): Promise<ApiMockBatchToggleResponse> {
  return post('/project/mocks/batch-toggle', req)
}

export function deleteMock(id: string): Promise<boolean> {
  return del(`/project/mocks/${id}`)
}

export function resetMockHitCount(id: string): Promise<boolean> {
  return post(`/project/mocks/${id}/reset-hit-count`)
}

export function fetchMockAddress(id: string): Promise<ApiMockAddress> {
  return get(`/project/mocks/${id}/address`)
}

// ==================== Mock 调试（3.2） ====================

export function debugMock(id: string, req: ApiMockDebugRequest): Promise<ApiMockDebugResponse> {
  return post(`/project/mocks/${id}/debug`, req)
}
