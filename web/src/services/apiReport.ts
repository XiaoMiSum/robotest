import api from '@/services'
import type {
  ApiPublicReportResp,
  ApiReportDetail,
  ApiReportPageItem,
  ApiReportShareResp,
  PageResult,
} from '@/types'

function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}

// ==================== 报告列表与详情 ====================

export function fetchReportPage(
  params: {
    pageNo: number
    pageSize: number
    status?: string
    sceneId?: string
    executionMode?: string
    keyword?: string
    startDate?: string
    endDate?: string
  },
): Promise<PageResult<ApiReportPageItem>> {
  return get('/project/reports', { ...params })
}

export function fetchReportDetail(id: string): Promise<ApiReportDetail> {
  return get(`/project/reports/${id}`)
}

// ==================== 分享 ====================

export function shareReport(id: string, expiresInDays?: number): Promise<ApiReportShareResp> {
  return post(`/project/reports/${id}/share`, { expiresInDays })
}

// 免登录访问分享报告
export function fetchPublicReport(id: string, token: string): Promise<ApiPublicReportResp> {
  return get(`/public/api-reports/${id}`, { token })
}

// ==================== 导出 ====================

export function exportReportUrl(id: string, format: 'json' | 'html' = 'json'): string {
  return `${api.defaults.baseURL}/project/reports/${id}/export?format=${format}`
}

export function batchExportReports(ids: string[], format: 'json' | 'html' = 'json'): Promise<Blob> {
  return api
    .post(
      '/project/reports/batch-export',
      { ids, format },
      { responseType: 'blob' },
    ) as unknown as Promise<Blob>
}

// ==================== 删除 ====================

export function deleteReport(id: string): Promise<boolean> {
  return api.delete(`/project/reports/${id}`) as unknown as Promise<boolean>
}

export function batchDeleteReports(ids: string[]): Promise<boolean> {
  return post('/project/reports/batch-delete', { ids })
}
