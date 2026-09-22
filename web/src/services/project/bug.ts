import api, { get, post, put, patch, del } from '@/services'
import type {
  BugAttachment,
  BugDetail,
  BugListItem,
  BugLog,
  BugPriority,
  BugResolution,
  BugSeverity,
  BugStatus,
  BugType,
  PageResult,
} from '@/types'

// ==================== 缺陷管理 ====================

export function fetchBugs(params: {
  status?: BugStatus | ''
  severity?: BugSeverity | ''
  priority?: BugPriority | ''
  bugType?: BugType | ''
  assigneeId?: string
  reporterId?: string
  resolvedBy?: string
  closedBy?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<BugListItem>> {
  return get('/project/bugs', { ...params })
}

export function createBug(data: {
  title: string
  severity: BugSeverity
  priority: BugPriority
  bugType: BugType
  reproSteps?: string
  moduleId?: string
  keywords?: string
  dueDate?: string
  assigneeId?: string
  relatedCaseId?: string
  relatedPlanId?: string
}): Promise<string> {
  return post('/project/bugs', data)
}

export function getBugDetail(id: string): Promise<BugDetail> {
  return get(`/project/bugs/${id}`)
}

export function updateBug(
  id: string,
  data: {
    title?: string
    severity?: BugSeverity
    priority?: BugPriority
    bugType?: BugType
    reproSteps?: string
    moduleId?: string
    keywords?: string
    dueDate?: string
    assigneeId?: string | null
    // 三态语义：undefined=不修改、空串=清空、UUID 串=更新
    relatedCaseId?: string
    relatedPlanId?: string
  },
): Promise<void> {
  return put(`/project/bugs/${id}`, data)
}

export function getBugLogs(id: string): Promise<BugLog[]> {
  return get(`/project/bugs/${id}/logs`)
}

export function changeBugStatus(
  id: string,
  data: {
    status: BugStatus
    comment?: string
    resolution?: BugResolution
    duplicateOfBugId?: string
  },
): Promise<void> {
  return patch(`/project/bugs/${id}/status`, data)
}

export function confirmBug(id: string): Promise<void> {
  return patch(`/project/bugs/${id}/confirm`)
}

export function assignBug(id: string, assigneeId: string): Promise<void> {
  return put(`/project/bugs/${id}/assign`, { assigneeId })
}

// ==================== 缺陷附件 ====================

export function uploadBugAttachment(bugId: string, file: File): Promise<BugAttachment> {
  const formData = new FormData()
  formData.append('file', file)
  // 覆盖默认的 application/json，由浏览器自动生成 multipart 边界
  return api.post(`/project/bugs/${bugId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<BugAttachment>
}

export function fetchBugAttachments(bugId: string): Promise<BugAttachment[]> {
  return get(`/project/bugs/${bugId}/attachments`)
}

// 下载为文件流，拦截器对 Blob 透传，此处触发浏览器保存
export async function downloadBugAttachment(attachmentId: string, fileName: string): Promise<void> {
  const blob = (await api.get(`/project/bugs/attachments/${attachmentId}/download`, {
    responseType: 'blob',
  })) as unknown as Blob
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

export function deleteBugAttachment(attachmentId: string): Promise<void> {
  return del(`/project/bugs/attachments/${attachmentId}`)
}
