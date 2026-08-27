import api from '@/services'
import type {
  ApiMockAddress,
  ApiMockBatchTogglePayload,
  ApiMockBatchToggleResponse,
  ApiMockDebugRequest,
  ApiMockDebugResponse,
  ApiMockDetail,
  ApiMockItem,
  ApiMockMoveResponse,
  ApiMockSavePayload,
  PageResult,
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
function del<T>(url: string): Promise<T> {
  return api.delete(url) as unknown as Promise<T>
}

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
  return api.patch(`/project/mocks/${id}/toggle`, { enabled }) as unknown as Promise<boolean>
}

export function batchToggleMocks(req: ApiMockBatchTogglePayload): Promise<ApiMockBatchToggleResponse> {
  return post('/project/mocks/batch-toggle', req)
}

export function deleteMock(id: string): Promise<boolean> {
  return del(`/project/mocks/${id}`)
}

export function duplicateMock(id: string): Promise<string> {
  return post(`/project/mocks/${id}/duplicate`).then((resp) => (resp as { id: string }).id)
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

// ==================== Mock 优先级调整（3.1.12） ====================

export function moveMockUp(id: string): Promise<ApiMockMoveResponse> {
  return post(`/project/mocks/${id}/move-up`)
}

export function moveMockDown(id: string): Promise<ApiMockMoveResponse> {
  return post(`/project/mocks/${id}/move-down`)
}
