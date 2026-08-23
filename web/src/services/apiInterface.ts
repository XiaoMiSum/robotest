import api from '@/services'
import type {
  ApiInterfaceImportPreview,
  ApiInterfaceImportResult,
  ApiInterfaceChangeLogItem,
  ApiInterfaceCreateReq,
  ApiInterfaceDetail,
  ApiInterfaceItem,
  ApiInterfaceReferences,
  ApiInterfaceStepPayload,
  ApiInterfaceStatus,
  ApiInterfaceUpdateReq,
  ApiInterfaceVariablePayload,
  ApiInterfaceView,
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

// ==================== 接口定义（3.1） ====================

export function fetchInterfacePage(
  params: { pageNo: number; pageSize: number; moduleId?: string; search?: string; status?: string; view?: ApiInterfaceView },
): Promise<PageResult<ApiInterfaceItem>> {
  return get('/project/interfaces', { ...params })
}

export function fetchInterfaceDetail(id: string): Promise<ApiInterfaceDetail> {
  return get(`/project/interfaces/${id}`)
}

export function createInterface(req: ApiInterfaceCreateReq): Promise<string> {
  return post('/project/interfaces', req).then((resp) => (resp as { id: string }).id)
}

export function updateInterface(id: string, req: ApiInterfaceUpdateReq): Promise<boolean> {
  return put(`/project/interfaces/${id}`, req)
}

export function deleteInterface(id: string): Promise<boolean> {
  return api.delete(`/project/interfaces/${id}`) as unknown as Promise<boolean>
}

/** 复制接口：copyName 缺省时服务端追加「（副本）」后缀 */
export function copyInterface(id: string, copyName?: string): Promise<string> {
  return post(`/project/interfaces/${id}/copy`, copyName ? { name: copyName } : undefined).then(
    (resp) => (resp as { id: string }).id,
  )
}

export function fetchInterfaceReferences(id: string): Promise<ApiInterfaceReferences> {
  return get(`/project/interfaces/${id}/references`)
}

export function batchMoveInterfaces(ids: string[], moduleId: string): Promise<boolean> {
  return put('/project/interfaces/batch/move', { ids, moduleId })
}

export function batchDeleteInterfaces(ids: string[]): Promise<boolean> {
  return api.delete('/project/interfaces/batch', { data: { ids } }) as unknown as Promise<boolean>
}

export function updateInterfaceStatus(id: string, status: ApiInterfaceStatus): Promise<boolean> {
  return put(`/project/interfaces/${id}/status`, { status })
}

export function followInterface(id: string): Promise<boolean> {
  return post(`/project/interfaces/${id}/follow`)
}

export function unfollowInterface(id: string): Promise<boolean> {
  return api.delete(`/project/interfaces/${id}/follow`) as unknown as Promise<boolean>
}

export function fetchInterfaceChangeLogs(id: string, pageNo: number, pageSize: number): Promise<PageResult<ApiInterfaceChangeLogItem>> {
  return get(`/project/interfaces/${id}/change-logs`, { pageNo, pageSize })
}

// ==================== 公共步骤（3.2） ====================

export function createInterfaceStep(interfaceId: string, step: ApiInterfaceStepPayload): Promise<string> {
  return post(`/project/interfaces/${interfaceId}/steps`, step).then((resp) => (resp as { id: string }).id)
}

export function updateInterfaceStep(interfaceId: string, stepId: string, step: ApiInterfaceStepPayload): Promise<boolean> {
  return put(`/project/interfaces/${interfaceId}/steps/${stepId}`, step)
}

export function deleteInterfaceStep(interfaceId: string, stepId: string): Promise<boolean> {
  return api.delete(`/project/interfaces/${interfaceId}/steps/${stepId}`) as unknown as Promise<boolean>
}

export function sortInterfaceStep(interfaceId: string, stepId: string, sortOrder: number): Promise<boolean> {
  return put(`/project/interfaces/${interfaceId}/steps/${stepId}/sort`, { sortOrder })
}

// ==================== 接口级变量（3.3） ====================

export function fetchInterfaceVariables(interfaceId: string): Promise<ApiInterfaceVariablePayload[]> {
  return get(`/project/interfaces/${interfaceId}/variables`)
}

/** 全量覆盖语义：按 name 匹配更新，未包含的删除 */
export function updateInterfaceVariables(interfaceId: string, variables: ApiInterfaceVariablePayload[]): Promise<boolean> {
  return put(`/project/interfaces/${interfaceId}/variables`, { variables })
}

// ==================== 导入（3.4） ====================

function withFormat(form: FormData, format?: string): FormData {
  if (format) form.append('format', format)
  return form
}

export function importInterfacesFile(file: File, format?: string): Promise<ApiInterfaceImportResult> {
  const form = new FormData()
  form.append('file', file)
  return post('/project/interfaces/import/file', withFormat(form, format))
}

export function importInterfacesUrl(url: string, format?: string): Promise<ApiInterfaceImportResult> {
  return post('/project/interfaces/import/url', format ? { url, format } : { url })
}

export function previewInterfaceImport(file: File, format?: string): Promise<ApiInterfaceImportPreview> {
  const form = new FormData()
  form.append('file', file)
  return post('/project/interfaces/import/preview', withFormat(form, format))
}
