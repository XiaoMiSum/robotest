import api from '@/services'
import type {
  ApiScheduleCreatedResp,
  ApiScheduleExecuteNowResp,
  ApiScheduleExecutionItem,
  ApiSchedulePageItem,
  ApiScheduleSaveReq,
  ApiScheduleToggleReq,
  ApiScheduleValidateCronReq,
  ApiScheduleValidateCronResp,
  ApiSwaggerUrlItem,
  ApiSwaggerUrlSaveReq,
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

// ==================== 定时任务管理（定时任务详细设计 3.1） ====================

export function fetchSchedulePage(
  params: { pageNo: number; pageSize: number; taskType?: string },
): Promise<PageResult<ApiSchedulePageItem>> {
  return get('/project/scheduled-tasks', { ...params })
}

export function createSchedule(req: ApiScheduleSaveReq): Promise<ApiScheduleCreatedResp> {
  return post('/project/scheduled-tasks', req)
}

export function updateSchedule(id: string, req: ApiScheduleSaveReq): Promise<boolean> {
  return put(`/project/scheduled-tasks/${id}`, req)
}

export function toggleSchedule(id: string, req: ApiScheduleToggleReq): Promise<boolean> {
  return put(`/project/scheduled-tasks/${id}/toggle`, req)
}

export function deleteSchedule(id: string): Promise<boolean> {
  return del(`/project/scheduled-tasks/${id}`)
}

export function executeSchedule(id: string): Promise<ApiScheduleExecuteNowResp> {
  return post(`/project/scheduled-tasks/${id}/execute`)
}

export function fetchScheduleExecutions(
  id: string,
  params: { pageNo: number; pageSize: number },
): Promise<PageResult<ApiScheduleExecutionItem>> {
  return get(`/project/scheduled-tasks/${id}/executions`, { ...params })
}

export function validateCron(req: ApiScheduleValidateCronReq): Promise<ApiScheduleValidateCronResp> {
  return post('/project/scheduled-tasks/validate-cron', req)
}

// ==================== Swagger URL 配置（定时任务详细设计 3.1.9） ====================

export function fetchSwaggerUrlList(name?: string): Promise<ApiSwaggerUrlItem[]> {
  return get('/project/swagger-urls', name ? { name } : undefined)
}

export function createSwaggerUrl(req: ApiSwaggerUrlSaveReq): Promise<string> {
  return post('/project/swagger-urls', req).then((resp) => (resp as { id: string }).id)
}

export function updateSwaggerUrl(id: string, req: ApiSwaggerUrlSaveReq): Promise<boolean> {
  return put(`/project/swagger-urls/${id}`, req)
}

export function deleteSwaggerUrl(id: string): Promise<boolean> {
  return del(`/project/swagger-urls/${id}`)
}
