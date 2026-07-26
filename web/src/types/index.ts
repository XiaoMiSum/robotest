/** Backend standard response wrapper (migoo framework uses `msg`) */
export interface Result<T> {
  code: number
  msg: string
  data: T
}

/** Backend paginated result */
export interface PageResult<T> {
  list: T[]
  total: number
}

/** Login request */
export interface LoginReqDTO {
  identifier: string
  password: string
}

/** Login response from backend */
export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessExpiry: string
  refreshExpiry: string
  user: LoginUser
  activeWorkspace: ActiveWorkspace | null
}

export interface LoginUser {
  id: string
  username: string
  email: string
  avatarUrl?: string
  status: string
  roles: string[]
}

export interface ActiveWorkspace {
  id: string
  name: string
  workspaceRole: string
}

/** Navigation mode */
export type NavMode = 'admin' | 'workspace' | 'project' | 'none'

/** Workspace in my list */
export interface WorkspaceItem {
  id: string
  name: string
  description: string
  workspaceRole: string
  defaultProjectId: string | null
  defaultProjectName: string | null
  memberCount: number
  projectCount: number
  status: string
  createdAt: string
}

/** Workspace context info */
export interface WorkspaceContext {
  id: string
  name: string
  description: string
  workspaceRole: string
  defaultProjectId: string | null
  defaultProjectName: string | null
  memberCount: number
  projectCount: number
  status: string
  createdAt: string
}

// ==================== 系统管理 (Admin) ====================

/** 通用状态：启用 / 禁用 */
export type UserStatus = 'active' | 'disabled'

/** 角色类型 */
export type RoleType = 'system' | 'workspace'

/** 用户所属角色的精简信息（内嵌于用户列表/详情） */
export interface RoleSimple {
  id: string
  name: string
  type: RoleType
}

/** 用户所属工作空间的精简信息（内嵌于用户列表/详情） */
export interface WorkspaceSimple {
  id: string
  name: string
  workspaceRole: string
}

/** 后台用户（列表项 / 详情） */
export interface AdminUser {
  id: string
  username: string
  email: string
  avatarUrl?: string
  status: UserStatus
  roles: RoleSimple[]
  workspaces: WorkspaceSimple[]
  createdAt: string
  updatedAt: string
}

/** 用户列表查询参数 */
export interface UserQueryParams {
  keyword?: string
  status?: UserStatus | ''
  roleId?: string
  workspaceId?: string
  pageNo?: number
  pageSize?: number
}

/** 创建用户请求体 */
export interface UserCreatePayload {
  username: string
  email: string
  password: string
  roleIds?: string[]
}

/** 更新用户请求体 */
export interface UserUpdatePayload {
  email?: string
  roleIds?: string[]
}

/** 后台工作空间（列表项 / 详情） */
export interface AdminWorkspace {
  id: string
  name: string
  description: string
  status: string
  memberCount: number
  projectCount: number
  createdAt: string
}

/** 工作空间成员（workspaceRole 为 workspace 类型角色的 UUID） */
export interface WorkspaceMember {
  userId: string
  username: string
  email: string
  avatarUrl?: string
  workspaceRole: string
  joinedAt: string
}

/** 角色树节点（分组节点 isGroup=true，无 children 的叶子为具体角色） */
export interface RoleTreeNode {
  id: string
  name: string
  type: RoleType
  isGroup?: boolean
  isSystem?: boolean
  fullAccess?: boolean
  userCount?: number
  children?: RoleTreeNode[]
}

/** 角色详情 */
export interface RoleDetail {
  id: string
  name: string
  description?: string
  type: RoleType
  isSystem: boolean
  fullAccess: boolean
  permissions: string[]
  userCount: number
}

/** 角色关联用户 */
export interface RoleUser {
  id: string
  username: string
  email: string
  status: UserStatus
  createdAt: string
}

/** 权限点 */
export interface PermissionItem {
  code: string
  name: string
}

/** 按模块分组的权限点（用于权限配置表格） */
export interface PermissionModule {
  module: string
  permissions: PermissionItem[]
}

// ==================== 空间管理业务 (Workspace Business) ====================

/** 项目状态 */
export type ProjectStatus = 'active' | 'archived'

/** 项目（列表项 / 详情） */
export interface Project {
  id: string
  name: string
  description: string
  status: ProjectStatus
  isDefault: boolean
  startTime: string | null
  endTime: string | null
  createdBy: { id: string; name: string }
  createdAt: string
}

/** 邀请链接状态 */
export type InvitationStatus = 'active' | 'revoked'

/** 邀请链接 */
export interface Invitation {
  id: string
  token: string
  expiresAt: string | null
  maxUses: number | null
  useCount: number
  status: InvitationStatus
  createdAt: string
}

/** 邀请令牌验证响应 */
export interface InvitationVerifyResult {
  valid: boolean
  workspaceName: string
  expiresAt: string | null
}

/** 邀请加入响应 */
export interface InvitationJoinResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  user: { id: string; username: string; email: string }
  activeWorkspace: { id: string; name: string; workspaceRole: string }
  isNewUser: boolean
}

/** 添加成员批量操作结果 */
export interface MemberAddResult {
  successCount: number
  skippedUserIds: string[]
}

// ==================== 项目工作区 (Project Workspace) ====================

/** 模块树节点类型 */
export type ModuleType = 'directory' | 'document'

/** 测试用例模块树节点 */
export interface TestCaseModule {
  id: string
  parentId: string | null
  type: ModuleType
  name: string
  sortOrder: number
  createdAt: string
  children: TestCaseModule[]
}

/** 用例节点类型 */
export type CaseNodeType = 'case' | 'normal' | 'precondition' | 'step' | 'expected'

/** 测试用例脑图节点 */
export interface TestCaseNode {
  id: string
  parentId: string | null
  type: CaseNodeType
  title: string
  priority: string | null
  sortOrder: number
  version: number
  children: TestCaseNode[]
}

/** 文档节点响应（脑图根节点 + 布局） */
export interface DocumentNodes {
  node: TestCaseNode
  layout: string | null
}

/** 用例列表项 */
export interface CaseListItem {
  id: string
  documentId: string
  title: string
  type: CaseNodeType
  priority: string | null
  createdAt: string
}

// --- 测试计划 ---

/** 计划状态 */
export type PlanStatus = 'new' | 'in_progress' | 'completed' | 'closed'

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

// --- 测试评审 ---

/** 评审状态 */
export type ReviewStatus = 'in_progress' | 'completed'

/** 评审标记 */
export type ReviewMark = 'pass' | 'fail'

/** 测试评审列表项 */
export interface TestReviewListItem {
  id: string
  title: string
  status: ReviewStatus
  initiator: { id: string; name: string }
  participantCount: number
  createdAt: string
}

/** 测试评审详情 */
export interface TestReviewDetail {
  id: string
  title: string
  description: string | null
  status: ReviewStatus
  initiator: { id: string; name: string }
  participantIds: string[]
  createdAt: string
}

/** 评审快照节点 */
export interface TestReviewSnapshotNode {
  id: string
  originalNodeId: string | null
  parentId: string | null
  title: string
  type: CaseNodeType
  priority: string | null
  isAssociated: boolean
  lastMark: ReviewMark | null
  lastReviewerId: string | null
  lastReviewedAt: string | null
  sortOrder: number
  children: TestReviewSnapshotNode[]
}

/** 评审进度 */
export interface TestReviewProgress {
  totalAssociated: number
  passed: number
  failed: number
  pending: number
  progressPercent: number
}

/** 评审记录 */
export interface ReviewRecord {
  id: string
  snapshotNodeId: string
  reviewerId: string
  reviewerName: string
  operationType: 'mark' | 'comment'
  mark: ReviewMark | null
  comment: string | null
  createdAt: string
}

// --- 缺陷管理 ---

/** 缺陷严重等级 */
export type BugSeverity = 'fatal' | 'serious' | 'general' | 'minor'

/** 缺陷优先级 */
export type BugPriority = 'high' | 'medium' | 'low'

/** 缺陷状态 */
export type BugStatus = 'new' | 'assigned' | 'fixing' | 'fixed' | 'verified' | 'closed'

/** 缺陷列表项 */
export interface BugListItem {
  id: string
  title: string
  severity: BugSeverity
  priority: BugPriority
  status: BugStatus
  reporter: { id: string; name: string }
  assignee: { id: string; name: string } | null
  createdAt: string
}

/** 缺陷详情 */
export interface BugDetail {
  id: string
  title: string
  severity: BugSeverity
  priority: BugPriority
  status: BugStatus
  description: string | null
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
  caseCount: number
  activeReviewCount: number
  activePlanCount: number
  openBugCount: number
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
