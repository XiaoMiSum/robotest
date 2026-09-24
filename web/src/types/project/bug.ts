import type { ProjectActivity } from './activity'

/** 缺陷严重等级 */
export type BugSeverity = 'fatal' | 'serious' | 'general' | 'minor'

/** 缺陷优先级 */
export type BugPriority = 'high' | 'medium' | 'low'

/** 缺陷状态（禅道式三态 + 已拒绝） */
export type BugStatus = 'active' | 'resolved' | 'rejected' | 'closed'

/** 缺陷类型 */
export type BugType =
  | 'code_error'
  | 'ui_improvement'
  | 'design_defect'
  | 'configuration'
  | 'installation'
  | 'security'
  | 'performance'
  | 'standard_spec'
  | 'other'

/** 缺陷解决方案 */
export type BugResolution =
  'fixed' | 'by_design' | 'duplicate' | 'external' | 'cannot_reproduce' | 'deferred' | 'wont_fix'

/** 缺陷列表项 */
export interface BugListItem {
  id: string
  projectId: string
  title: string
  severity: BugSeverity
  priority: BugPriority
  status: BugStatus
  bugType: BugType
  reproSteps: string | null
  moduleId: string | null
  keywords: string | null
  confirmed: boolean
  reopenCount: number
  lastReopenedAt: string | null
  resolution: BugResolution | null
  duplicateOfBugId: string | null
  dueDate: string | null
  relatedCaseId: string | null
  relatedPlanId: string | null
  reporter: { id: string; name: string }
  assignee: { id: string; name: string } | null
  resolvedBy: { id: string; name: string } | null
  resolvedAt: string | null
  rejectedBy: { id: string; name: string } | null
  closedBy: { id: string; name: string } | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 缺陷详情 */
export interface BugDetail {
  id: string
  title: string
  severity: BugSeverity
  priority: BugPriority
  status: BugStatus
  bugType: BugType
  reproSteps: string | null
  moduleId: string | null
  moduleName: string | null
  keywords: string | null
  dueDate: string | null
  confirmed: boolean
  reopenCount: number
  lastReopenedAt: string | null
  resolution: BugResolution | null
  duplicateOfBugId: string | null
  resolvedBy: { id: string; name: string } | null
  resolvedAt: string | null
  closedBy: { id: string; name: string } | null
  closedAt: string | null
  reporter: { id: string; name: string }
  assignee: { id: string; name: string } | null
  relatedCaseId: string | null
  relatedPlanId: string | null
  createdAt: string
  updatedAt: string
  recentLogs: BugLog[]
}

/** 缺陷操作日志 */
export interface BugLog {
  id: string
  operatorId: string
  operatorName: string
  operationType: string
  content: string | null
  createdAt: string
}

/** 缺陷附件 */
export interface BugAttachment {
  id: string
  fileName: string
  fileSize: number
  contentType: string | null
  uploaderId: string
  uploaderName: string | null
  createdAt: string
}

/** 缺陷统计 */
export interface BugStatistics {
  total: number
  byStatus: Record<string, number>
  bySeverity: Record<string, number>
  byPriority: Record<string, number>
  byAssignee: Record<string, number>
  byReporter: Record<string, number>
}

// --- 项目工作台 ---

/** 项目工作台数据 */
export interface ProjectDashboard {
  projectName: string
  projectStatus: string
  startTime: string | null
  endTime: string | null
  caseCount: number
  activeReviewCount: number
  activePlanCount: number
  openBugCount: number
  recentActivities: ProjectActivity[]
  recentReviews: DashboardRecentItem[]
  recentPlans: DashboardRecentItem[]
  recentBugs: DashboardRecentBug[]
}

export interface DashboardRecentItem {
  id: string
  title: string
  status: string
  createdAt: string
}

export interface DashboardRecentBug {
  id: string
  title: string
  severity: string
  priority: string
  status: string
  assignee: string | null
  createdAt: string
}
