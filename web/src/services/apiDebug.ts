import api from '@/services'
import type {
  ApiDebugCurlImportResp,
  ApiDebugExecuteReq,
  ApiDebugExecuteResp,
  ApiDebugRecordItem,
  ApiDebugRestoreResp,
  ApiDebugSaveAsInterfaceReq,
  ApiDebugSaveAsInterfaceResp,
  PageResult,
} from '@/types'

function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}

// ==================== 快速调试（/api/project/debug*，详细设计 3.1 / 基础设施 3.3） ====================

/** 服务端执行调试请求；结果自动持久化为调试记录。后端护栏为 timeoutMs+5s，放宽 axios 全局 15s 超时 */
export function executeDebug(req: ApiDebugExecuteReq): Promise<ApiDebugExecuteResp> {
  return api.post('/project/debug/execute', req, { timeout: 120_000 }) as unknown as Promise<ApiDebugExecuteResp>
}

/** cURL 解析：仅解析不执行，结果回填当前标签 */
export function importCurl(curl: string): Promise<ApiDebugCurlImportResp> {
  return post('/project/debug/import-curl', { curl })
}

/** 调试记录分页（仅当前用户），keyword 匹配名称或 URL */
export function fetchDebugRecords(pageNo: number, pageSize: number, keyword?: string): Promise<PageResult<ApiDebugRecordItem>> {
  const params: Record<string, unknown> = { pageNo, pageSize }
  if (keyword) params.keyword = keyword
  return get('/project/debug-records', params)
}

export function renameDebugRecord(id: string, name: string): Promise<boolean> {
  return api.put(`/project/debug-records/${id}`, { name }) as unknown as Promise<boolean>
}

export function deleteDebugRecord(id: string): Promise<boolean> {
  return api.delete(`/project/debug-records/${id}`) as unknown as Promise<boolean>
}

/** 恢复调试记录：返回完整请求快照与响应，前端据此新建标签并回填 */
export function restoreDebugRecord(id: string): Promise<ApiDebugRestoreResp> {
  return get(`/project/debug-records/${id}/restore`)
}

/** 保存为接口定义：新建接口（create）或归属已有接口（attach，携带 changeVersion 乐观锁） */
export function saveDebugRecordAsInterface(
  id: string,
  req: ApiDebugSaveAsInterfaceReq,
): Promise<ApiDebugSaveAsInterfaceResp> {
  return post(`/project/debug-records/${id}/save-as-interface`, req)
}
