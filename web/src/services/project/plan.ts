import { get, post, put, del } from '@/services'
import type {
  ExecutionResult,
  PageResult,
  PlannedCases,
  PlanStatus,
  SnapshotModule,
  TestPlanDetail,
  TestPlanListItem,
  TestPlanProgress,
  TestPlanSnapshotNode,
} from '@/types'

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
