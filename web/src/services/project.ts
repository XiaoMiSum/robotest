import api from '@/services'
import type {
  BugDetail,
  BugListItem,
  BugLog,
  BugPriority,
  BugSeverity,
  BugStatistics,
  BugStatus,
  CaseListItem,
  DocumentNodes,
  ExecutionRecord,
  ExecutionResult,
  PageResult,
  PlanStatus,
  ProjectDashboard,
  ReviewMark,
  ReviewRecord,
  ReviewStatus,
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

export function startPlan(id: string): Promise<void> {
  return post(`/project/plans/${id}/start`)
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
  assigneeId?: string
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
  description?: string
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
    description?: string
    assigneeId?: string | null
  },
): Promise<void> {
  return put(`/project/bugs/${id}`, data)
}

export function getBugLogs(id: string): Promise<BugLog[]> {
  return get(`/project/bugs/${id}/logs`)
}

export function changeBugStatus(
  id: string,
  data: { status: BugStatus; comment?: string },
): Promise<void> {
  return patch(`/project/bugs/${id}/status`, data)
}

export function assignBug(id: string, assigneeId: string): Promise<void> {
  return put(`/project/bugs/${id}/assign`, { assigneeId })
}

export function getBugStatistics(): Promise<BugStatistics> {
  return get('/project/bugs/statistics')
}
