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
  accessExpiry: string
  refreshExpiry: string
  user: LoginUser
}

export interface LoginUser {
  id: string
  username: string
  email: string
  avatarUrl?: string
  status: string
  roles: string[]
  permissions: string[]
  hasWorkspace: boolean
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
  name: string
  email: string
  avatarUrl?: string
  status: UserStatus
  roles: RoleSimple[]
  workspaces: WorkspaceSimple[]
  createdAt: string
  updatedAt: string
}

/** 用户精简信息（用于下拉选择） */
export interface UserSimple {
  id: string
  name: string
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
  name: string
  email: string
  password: string
  roleIds?: string[]
}

/** 更新用户请求体 */
export interface UserUpdatePayload {
  name?: string
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
  name: string
  email: string
  status: UserStatus
  createdAt: string
}

/** 空间角色关联用户（含归属空间） */
export interface RoleWorkspaceUser {
  userId: string
  username: string
  name: string
  workspaces: {
    workspaceId: string
    workspaceName: string
  }[]
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

/** 邀请邮箱查询响应 */
export interface InvitationCheckEmailResult {
  exists: boolean
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

/** 评审/计划模块快照树节点（目录/文档层级，供详情页左侧文档切换） */
export interface SnapshotModule {
  id: string
  parentId: string | null
  name: string
  type: ModuleType
  sortOrder: number
  children: SnapshotModule[]
}

/** 评审/计划规划的用例选择（原始 documentId/caseId 维度，创建与调整共用） */
export interface PlannedCases {
  documentId: string
  caseIds: string[]
}

/** 用例节点类型 */
export type CaseNodeType = 'case' | 'normal' | 'precondition' | 'step' | 'expected'

/** 测试用例脑图节点 */
export interface TestCaseNode {
  id: string
  /** 所属文档 id，用例明细接口用于定位所在文档 */
  documentId?: string | null
  parentId: string | null
  type: CaseNodeType
  title: string
  priority: string | null
  sortOrder: number
  version: number
  /** AI 生成标识（挂载执行器写入，可手动移除，V1.1） */
  aiGenerated?: boolean
  children: TestCaseNode[]
}

/** 文档布局：模板名 + 各节点自由拖拽偏移（键为节点 data 中的 layout_*_offset 原始键名） */
export interface DocumentLayout {
  template?: string
  offsets?: Record<string, Record<string, { x: number; y: number }>>
}

/** 文档节点响应（脑图根节点 + 布局） */
export interface DocumentNodes {
  node: TestCaseNode
  layout: DocumentLayout | null
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
  aiGenerated?: boolean
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
export type ReviewStatus = 'new' | 'in_progress' | 'completed'

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
  totalAssociated: number
  passed: number
  progressPercent: number
  passRate: number
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
  aiGenerated?: boolean
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
  mark: ReviewMark | 'pending' | null
  comment: string | null
  createdAt: string
}

// --- 缺陷管理 ---

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
  | 'fixed'
  | 'by_design'
  | 'duplicate'
  | 'external'
  | 'cannot_reproduce'
  | 'deferred'
  | 'wont_fix'

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

// ==================== AI 基础设施 ====================

/** AI 可用性状态（GET /api/workspace/ai/status） */
export interface AiStatus {
  enabled: boolean
  /** available / degraded / unavailable，enabled=false 时不返回 */
  semanticSearch?: 'available' | 'degraded' | 'unavailable'
  /** 已启用对话模型清单（脱敏，仅 id/显示名/是否默认），enabled=false 时不返回 */
  chatModels?: AiChatModelView[]
}

/** 对话模型选择器数据项（status 下发的脱敏视图） */
export interface AiChatModelView {
  id: string
  name: string
  isDefault: boolean
}

/** 密钥脱敏信息（永不回传明文） */
export interface AiApiKeyInfo {
  configured: boolean
  keySuffix: string | null
}

export interface AiConfigEmbeddingGroup {
  provider: string
  baseUrl: string
  model: string
  dimension: number | null
  apiKey: AiApiKeyInfo
  extraParams: Record<string, unknown>
}

/** AI 配置（GET /api/admin/ai/config，未配置为 null；对话模型独立于 chat-models 接口） */
export interface AiConfig {
  enabled: boolean
  embedding: AiConfigEmbeddingGroup | null
  settings: Record<string, unknown>
  updatedAt: string | null
}

/** 对话模型配置（GET /api/admin/ai/chat-models 列表项，脱敏） */
export interface AiChatModel {
  id: string
  name: string
  provider: string
  baseUrl: string
  model: string
  apiKey: AiApiKeyInfo
  extraParams: Record<string, unknown>
  enabled: boolean
  isDefault: boolean
  updatedBy: string | null
  updatedAt: string | null
}

/** 新建/更新对话模型载荷（apiKey 非空即更新，空表示保持原值） */
export interface AiChatModelSavePayload {
  name: string
  provider: string
  baseUrl: string
  model: string
  apiKey?: string | null
  extraParams?: Record<string, unknown>
  expectedUpdatedAt?: string | null
}

/** 保存 AI 配置的对话临时配置（供连通性测试临时透传） */
export interface AiConfigChatGroupPayload {
  provider: string
  baseUrl: string
  model: string
  apiKey?: string | null
  extraParams?: Record<string, unknown>
}

export interface AiConfigEmbeddingGroupPayload {
  provider?: string
  baseUrl?: string
  model?: string
  dimension?: number | null
  apiKey?: string | null
  extraParams?: Record<string, unknown>
}

export interface AiConfigSavePayload {
  enabled: boolean
  embedding?: AiConfigEmbeddingGroupPayload | null
  settings?: Record<string, unknown>
  expectedUpdatedAt?: string | null
}

/** 连通性测试请求与结果 */
export interface AiConfigTestPayload {
  target: 'chat' | 'embedding'
  /** target=chat 且缺省临时配置时，指定已保存的对话模型（缺省则测系统默认） */
  modelId?: string | null
  chat?: AiConfigChatGroupPayload
  embedding?: AiConfigEmbeddingGroupPayload
}

export interface AiConnectivityTestResult {
  ok: boolean
  latencyMs: number | null
  detail: string | null
}

/** 系统配置项表单定义（GET /api/admin/ai/settings-schema） */
export interface AiSettingSchemaItem {
  key: string
  type: 'int' | 'number' | 'object' | 'string[]'
  label: string
  description: string
  defaultValue: unknown
  min: number | null
  max: number | null
  step: number | null
  options?: string[]
}

export interface AiSettingSchemaGroup {
  group: string
  groupLabel: string
  items: AiSettingSchemaItem[]
}

/** 供应商预设独有配置项模板 */
export interface AiProviderUniqueParam {
  key: string
  type: 'boolean' | 'number' | 'string' | 'enum'
  defaultValue: unknown
  options?: string[]
  label: string
  description: string
}

/** 供应商预设注册表元数据 */
export interface AiProviderPreset {
  key: string
  name: string
  scopes: string[]
  defaultBaseUrl: Record<string, string>
  modelHints: Record<string, string[]>
  uniqueParams: Record<string, AiProviderUniqueParam[]>
}

/** 智能体列表项 */
export interface AiAgent {
  functionType: string
  name: string
  customized: boolean
  formatEditable: boolean
  updatedBy: string | null
  updatedAt: string | null
}

/** 智能体详情（当前生效段 + 内置默认段） */
export interface AiAgentDetail {
  functionType: string
  name: string
  customized: boolean
  formatEditable: boolean
  roleInstruction: string
  formatConstraint: string
  defaults: {
    roleInstruction: string
    formatConstraint: string
  }
}

export interface AiAgentSavePayload {
  roleInstruction: string
  formatEditable: boolean
  formatConstraint?: string | null
}

/** 调用量统计 */
export interface AiStatisticsItem {
  key: string
  calls: number
  tokens: number
  avgDurationMs: number
  failed: number
}

export interface AiStatistics {
  totalCalls: number
  totalTokens: number
  failedCalls: number
  items: AiStatisticsItem[]
}

/** AI 异步任务状态 */
export interface AiTask {
  id: string
  type: string
  targetId: string | null
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
  progress: number
  result: Record<string, unknown> | null
  errorMessage: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** AI 生成用例树节点（生成子树/补全步骤/文本导入共用输出结构，详细设计 2.2） */
export interface AiGeneratedNode {
  type: CaseNodeType
  title: string
  /** 仅 case 节点携带，P0-P3 */
  priority?: string | null
  children?: AiGeneratedNode[]
}

/** AI 生成用例子树 done 帧载荷 */
export interface AiCaseGenerateResult {
  nodes: AiGeneratedNode[]
  /** 截断等宽容规整提示 */
  warnings: string[]
}

/** AI 评审摘要按文档不通过分布 */
export interface AiReviewSummaryFailByDocument {
  documentName: string
  failCount: number
}

/** AI 评审摘要统计（SQL 精确计算，随 statistics 帧即时返回） */
export interface AiReviewSummaryStats {
  totalCases: number
  passCount: number
  failCount: number
  pendingCount: number
  passRate: number
  failByDocument: AiReviewSummaryFailByDocument[]
}

/** AI 评审摘要（GET 查询与 done 帧共用；generatedAt 仅持久化查询返回） */
export interface AiReviewSummary {
  statistics: AiReviewSummaryStats
  summaryMarkdown: string
  generatedAt?: string
}

/** 需求池条目列表项（US-AI-004） */
export interface RequirementPoolItem {
  id: string
  title: string
  sourceUrl: string | null
  createdBy: string
  creatorName: string | null
  updatedAt: string
}

/** 需求池条目详情 */
export interface RequirementDetail {
  id: string
  title: string
  content: string
  sourceUrl: string | null
  createdBy: string
  creatorName: string | null
  updatedBy: string
  createdAt: string
  updatedAt: string
}

/** 需求池条目摘要（文档关联查询与条目选取器共用） */
export interface RequirementSummary {
  id: string
  title: string
}



