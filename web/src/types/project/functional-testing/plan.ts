import type { CaseNodeType } from '../shared'
import type { SelectedCaseNode } from './case'

/** 计划创建载荷（列表页创建与圈选确认 target.createParams 共用） */
export interface TestPlanCreatePayload {
  name: string
  description?: string
  executorId?: string
  startTime?: string | null
  endTime?: string | null
  environment?: string
  selectedNodes: SelectedCaseNode[]
}

/** 计划状态（blocked 为阻塞态，仅负责人可恢复为 in_progress） */
export type PlanStatus = 'new' | 'in_progress' | 'completed' | 'blocked' | 'closed'

/** 测试计划列表项 */
export interface TestPlanListItem {
  id: string
  name: string
  status: PlanStatus
  environment: string | null
  startTime: string | null
  endTime: string | null
  executor: { id: string; name: string } | null
  createdAt: string
  totalAssociated: number
  passed: number
  progressPercent: number
  passRate: number
}

/** 测试计划详情 */
export interface TestPlanDetail {
  id: string
  name: string
  description: string | null
  status: PlanStatus
  environment: string | null
  startTime: string | null
  endTime: string | null
  executor: { id: string; name: string } | null
  createdAt: string
}

/** 执行结果 */
export type ExecutionResult = 'pass' | 'fail' | 'block' | 'untested'

/** 计划快照节点 */
export interface TestPlanSnapshotNode {
  id: string
  originalNodeId: string | null
  parentId: string | null
  title: string
  type: CaseNodeType
  priority: string | null
  isAssociated: boolean
  lastResult: ExecutionResult | null
  lastExecutorId: string | null
  lastExecutedAt: string | null
  sortOrder: number
  children: TestPlanSnapshotNode[]
}

/** 计划执行进度 */
export interface TestPlanProgress {
  totalAssociated: number
  passed: number
  failed: number
  blocked: number
  untested: number
  progressPercent: number
}

/** 执行记录 */
export interface ExecutionRecord {
  id: string
  snapshotNodeId: string
  executorId: string
  executorName: string
  result: ExecutionResult
  note: string | null
  executedAt: string
  createdAt: string
}
