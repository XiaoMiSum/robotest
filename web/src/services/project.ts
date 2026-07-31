import api from '@/services'
import type {
  BugAttachment,
  BugDetail,
  BugListItem,
  BugLog,
  BugPriority,
  BugResolution,
  BugSeverity,
  BugStatistics,
  BugStatus,
  BugType,
  CaseListItem,
  DocumentNodes,
  ExecutionRecord,
  ExecutionResult,
  PageResult,
  PlannedCases,
  PlanStatus,
  ProjectDashboard,
  ReviewMark,
  ReviewRecord,
  ReviewStatus,
  RequirementDetail,
  RequirementPoolItem,
  SnapshotModule,
  TestCaseModule,
  TestCaseNode,
  TestPlanDetail,
  TestPlanListItem,
  TestPlanProgress,
  TestPlanSnapshotNode,
  TestReviewDetail,
  TestReviewListItem,
  TestReviewProgress,
  TestReviewSnapshotNode,
} from '@/types'

// 响应拦截器已将 Result<T> 解包为 data
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}
function patch<T>(url: string, data?: unknown): Promise<T> {
  return api.patch(url, data) as unknown as Promise<T>
}
function del<T>(url: string): Promise<T> {
  return api.delete(url) as unknown as Promise<T>
}

// ==================== 项目工作台 ====================

export function fetchDashboard(): Promise<ProjectDashboard> {
  return get('/project/dashboard')
}

// ==================== 测试用例模块树 ====================

export function fetchModuleTree(): Promise<TestCaseModule[]> {
  return get('/project/modules')
}

export function createModule(data: {
  parentId: string | null
  type: 'directory' | 'document'
  name: string
}): Promise<TestCaseModule> {
  return post('/project/modules', data)
}

// targetIndex 非空时为拖拽移动：parentId 为目标父目录（null 表示根层级）
export function updateModule(
  id: string,
  data: { name?: string; parentId?: string | null; targetIndex?: number },
): Promise<TestCaseModule> {
  return put(`/project/modules/${id}`, data)
}

export function deleteModule(id: string): Promise<void> {
  return del(`/project/modules/${id}`)
}

// ==================== 测试用例节点 ====================

export function fetchDocumentNodes(docId: string): Promise<DocumentNodes> {
  return get(`/project/documents/${docId}/nodes`)
}

export function getCaseDetail(caseId: string): Promise<TestCaseNode> {
  return get(`/project/cases/${caseId}`)
}

export function getCaseList(params: {
  keyword?: string
  priority?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<CaseListItem>> {
  return get('/project/cases', { ...params })
}

export function updateCaseNode(
  caseId: string,
  data: { title?: string; type?: string; priority?: string | null },
): Promise<void> {
  return put(`/project/cases/${caseId}`, data)
}

// ==================== 测试计划 ====================

export function fetchPlans(params: {
  status?: PlanStatus | ''
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<TestPlanListItem>> {
  return get('/project/plans', { ...params })
}

export function createPlan(data: {
  name: string
  description?: string
  executorId?: string
  startTime?: string | null
  endTime?: string | null
  environment?: string
  selectedNodes: { documentId: string; caseIds: string[] }[]
}): Promise<TestPlanDetail> {
  return post('/project/plans', data)
}

export function getPlanDetail(id: string): Promise<TestPlanDetail> {
  return get(`/project/plans/${id}`)
}

export function getPlanSnapshotTree(
  id: string,
  documentId?: string,
): Promise<TestPlanSnapshotNode[]> {
  return get(`/project/plans/${id}/modules`, documentId ? { documentId } : undefined)
}

export function getPlanModuleTree(id: string): Promise<SnapshotModule[]> {
  return get(`/project/plans/${id}/module-tree`)
}

export function getPlanPlannedCases(id: string): Promise<PlannedCases[]> {
  return get(`/project/plans/${id}/cases`)
}

export function updatePlanCases(id: string, selectedNodes: PlannedCases[]): Promise<void> {
  return put(`/project/plans/${id}/cases`, { selectedNodes })
}

export function submitExecutionRecord(
  planId: string,
  data: { snapshotNodeId: string; result: ExecutionResult; note?: string },
): Promise<void> {
  return post(`/project/plans/${planId}/records`, data)
}

export function getNodeExecutionRecords(
  planId: string,
  nodeId: string,
): Promise<ExecutionRecord[]> {
  return get(`/project/plans/${planId}/nodes/${nodeId}/records`)
}

export function syncPlan(id: string): Promise<void> {
  return post(`/project/plans/${id}/sync`)
}

export function completePlan(id: string): Promise<void> {
  return post(`/project/plans/${id}/complete`)
}

export function deletePlan(id: string): Promise<void> {
  return del(`/project/plans/${id}`)
}

export function getPlanProgress(id: string): Promise<TestPlanProgress> {
  return get(`/project/plans/${id}/progress`)
}

export function closePlan(id: string): Promise<void> {
  return post(`/project/plans/${id}/close`)
}

// ==================== 测试评审 ====================

export function fetchReviews(params: {
  status?: ReviewStatus | ''
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<TestReviewListItem>> {
  return get('/project/reviews', { ...params })
}

export function createReview(data: {
  title: string
  description?: string
  participantIds: string[]
  selectedNodes: { documentId: string; caseIds: string[] }[]
}): Promise<TestReviewDetail> {
  return post('/project/reviews', data)
}

export function getReviewDetail(id: string): Promise<TestReviewDetail> {
  return get(`/project/reviews/${id}`)
}

export function getReviewSnapshotTree(
  id: string,
  documentId?: string,
): Promise<TestReviewSnapshotNode[]> {
  return get(`/project/reviews/${id}/modules`, documentId ? { documentId } : undefined)
}

export function getReviewModuleTree(id: string): Promise<SnapshotModule[]> {
  return get(`/project/reviews/${id}/module-tree`)
}

export function getReviewPlannedCases(id: string): Promise<PlannedCases[]> {
  return get(`/project/reviews/${id}/cases`)
}

export function updateReviewCases(id: string, selectedNodes: PlannedCases[]): Promise<void> {
  return put(`/project/reviews/${id}/cases`, { selectedNodes })
}

export function submitReviewRecord(
  reviewId: string,
  data: {
    snapshotNodeId: string
    operationType: 'mark' | 'comment'
    // pending 为显式重置回待评审，后端落库 last_mark = null
    mark?: ReviewMark | 'pending'
    comment?: string
  },
): Promise<void> {
  return post(`/project/reviews/${reviewId}/records`, data)
}

export function getNodeReviewRecords(reviewId: string, nodeId: string): Promise<ReviewRecord[]> {
  return get(`/project/reviews/${reviewId}/nodes/${nodeId}/records`)
}

export function completeReview(id: string): Promise<void> {
  return post(`/project/reviews/${id}/complete`)
}

export function deleteReview(id: string): Promise<void> {
  return del(`/project/reviews/${id}`)
}

export function getReviewProgress(id: string): Promise<TestReviewProgress> {
  return get(`/project/reviews/${id}/progress`)
}

export function syncReview(id: string): Promise<void> {
  return post(`/project/reviews/${id}/sync`)
}

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

export function getBugStatistics(): Promise<BugStatistics> {
  return get('/project/bugs/statistics')
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

// ==================== 需求池（US-AI-004） ====================

export function fetchRequirements(params: {
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<RequirementPoolItem>> {
  return get('/project/requirements', { ...params })
}

export function getRequirement(id: string): Promise<RequirementDetail> {
  return get(`/project/requirements/${id}`)
}

export function createRequirement(data: {
  title: string
  content: string
  sourceUrl?: string
}): Promise<string> {
  return post('/project/requirements', data)
}

export function updateRequirement(
  id: string,
  data: {
    title?: string
    content?: string
    // 三态语义：undefined 不修改、空串清空、非空更新
    sourceUrl?: string
  },
): Promise<void> {
  return put(`/project/requirements/${id}`, data)
}

export function deleteRequirement(id: string): Promise<void> {
  return del(`/project/requirements/${id}`)
}
