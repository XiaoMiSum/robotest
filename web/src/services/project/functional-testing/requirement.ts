import { get, post, put } from '@/services'
import type {
  PageResult,
  RequirementChangeLog,
  RequirementCreatePayload,
  RequirementDetail,
  RequirementListItem,
  RequirementPageQuery,
  RequirementSplitSubmit,
  RequirementUpdatePayload,
} from '@/types'

// ==================== 需求管理（详设 3.2–3.10） ====================

export function fetchRequirements(
  params: RequirementPageQuery = {},
): Promise<PageResult<RequirementListItem>> {
  return get('/project/requirements', { ...params })
}

export function getRequirement(id: string): Promise<RequirementDetail> {
  return get(`/project/requirements/${id}`)
}

export function createRequirement(data: RequirementCreatePayload): Promise<RequirementDetail> {
  return post('/project/requirements', data)
}

/** 部分更新（C11）：载荷只包含实际变化的字段，null/缺省表示不修改 */
export function updateRequirement(
  id: string,
  data: RequirementUpdatePayload,
): Promise<RequirementDetail> {
  return put(`/project/requirements/${id}`, data)
}

export function confirmRequirement(id: string): Promise<RequirementDetail> {
  return post(`/project/requirements/${id}/confirm`)
}

export function archiveRequirement(id: string): Promise<RequirementDetail> {
  return post(`/project/requirements/${id}/archive`)
}

/** 取消归档：服务端一律回 draft（详设 3.7），前端不承诺恢复归档前状态 */
export function unarchiveRequirement(id: string): Promise<RequirementDetail> {
  return post(`/project/requirements/${id}/unarchive`)
}

/** 提交 AI 拆分：提交即返回任务入口，进度与审核在任务详情页完成（详设 3.9） */
export function splitRequirement(id: string): Promise<RequirementSplitSubmit> {
  return post(`/project/requirements/${id}/split`)
}

/** 导入需求文档（详设 3.8）：multipart 单文件，提交即返回任务入口 */
export function importRequirement(file: File): Promise<RequirementSplitSubmit> {
  const formData = new FormData()
  formData.append('file', file)
  // 覆盖默认的 application/json，由浏览器自动生成 multipart 边界
  return post('/project/requirements/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function fetchRequirementChangeLogs(
  id: string,
  params: { pageNo?: number; pageSize?: number } = {},
): Promise<PageResult<RequirementChangeLog>> {
  return get(`/project/requirements/${id}/change-logs`, { ...params })
}
