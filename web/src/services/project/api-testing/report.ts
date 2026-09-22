import { get, post, del } from '@/services'
import type {
  ApiPublicReportResp,
  ApiReportDetail,
  ApiReportPageItem,
  ApiReportShareResp,
  PageResult,
} from '@/types'

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

// ==================== 删除 ====================

export function deleteReport(id: string): Promise<boolean> {
  return del(`/project/reports/${id}`)
}

export function batchDeleteReports(ids: string[]): Promise<boolean> {
  return post('/project/reports/batch-delete', { ids })
}
