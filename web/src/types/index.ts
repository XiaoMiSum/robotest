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

/** 邀请链接（创建接口返回，含敏感 token，仅创建后立即展示） */
export interface Invitation {
  id: string
  token: string
  expiresAt: string | null
  maxUses: number | null
  useCount: number
  status: InvitationStatus
  createdAt: string
}

/** 邀请链接列表项（列表接口不下发 token，避免敏感凭据随列表泄露） */
export interface InvitationListItem {
  id: string
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

/** 项目级统一模块树节点（V1.2 起，目录与文档混合树） */
export interface ProjectModule {
  id: string
  parentId: string | null
  /** 节点类型：directory = 目录，document = 文档 */
  type: ModuleType
  name: string
  sortOrder: number
  children: ProjectModule[]
}

/** 测试用例文档（V1.2 起，挂载在 ProjectModule 节点下的脑图型用例资产） */
export interface TestCaseDocument {
  id: string
  /** null 表示未分组（文档管理详细设计 2.1） */
  moduleId: string | null
  name: string
  sortOrder: number
  nodeCount: number
  updatedAt: string
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

/** 智能体详情（当前生效段，内容全部来自数据库） */
export interface AiAgentDetail {
  functionType: string
  name: string
  customized: boolean
  formatEditable: boolean
  roleInstruction: string
  formatConstraint: string
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

/** AI 一键检查建议维度（US-AI-005，详细设计 2.2.1 枚举） */
export type AiReviewCheckDimension =
  | 'missing_precondition'
  | 'vague_step'
  | 'missing_expected'
  | 'priority_conflict'

/** AI 一键检查单条建议（result.items 元素，2.2.1） */
export interface AiReviewCheckItem {
  snapshotNodeId: string
  dimension: AiReviewCheckDimension
  suggestion: string
}

/** AI 一键检查结果（review_check 任务 result；分批累计写入，running/cancelled 亦含部分结果） */
export interface AiReviewCheckResult {
  checkedCaseCount: number
  totalCaseCount: number
  skippedBatches: number
  items: AiReviewCheckItem[]
}

/** AI 遗漏测试点分析单条（US-AI-007，详细设计 3.3） */
export interface AiMissingPoint {
  title: string
  description: string
  /** 建议归属模块路径（「转用例生成」用于目标文档默认预选） */
  suggestedModulePath: string | null
  /** 关联候选用例标题（幻觉过滤后仅含候选清单中真实存在的标题） */
  relatedCaseTitles: string[]
}

/** AI 遗漏测试点分析响应（3.3，同步长调用） */
export interface AiMissingPointResult {
  /** 语义降级：true 时顶部提示「当前为关键词匹配结果」 */
  semanticDegraded: boolean
  points: AiMissingPoint[]
}

/** AI 用例规划智能推荐单条（US-AI-018，详细设计 3.5） */
export interface AiCasePlanRecommendItem {
  caseNodeId: string
  title: string
  /** 所属模块路径（如 登录 > 密码登录），清单展示用 */
  modulePath: string
  /** 命中方式：semantic（语义匹配；降级态关键词匹配亦归此值） */
  matchType: 'semantic'
  /** 推荐度（score 降序，结果上限 50；降级模式 0.6 仅作展示排序） */
  score: number
  /** 一句话推荐理由；生成失败时整体置空，不影响清单可用性（4.5） */
  reason: string | null
}

/** AI 用例规划智能推荐响应（3.5，同步长调用） */
export interface AiCasePlanRecommendResult {
  /** 语义降级：true 时顶部提示「当前为关键词匹配结果」 */
  semanticDegraded: boolean
  items: AiCasePlanRecommendItem[]
}

/** AI 执行顺序推荐单条因子（US-AI-017，详细设计 2.2.3 / 4.4） */
export interface AiPlanOrderFactors {
  /** bug.related_case_id = 快照节点.original_node_id 的未删除缺陷数 */
  relatedBugCount: number
  /** P0=1.0 / P1=0.75 / P2=0.5 / P3=0.25 / 无=0.25 */
  priorityWeight: number
  /** 所属文档对应模块（含子孙模块）缺陷数 ÷ 现势 case 节点数 */
  moduleBugDensity: number
}

/** AI 执行顺序推荐单条（items 元素，按 score 降序排列，order 为推荐序号） */
export interface AiPlanOrderRecommendItem {
  snapshotNodeId: string
  order: number
  score: number
  factors: AiPlanOrderFactors
  /** 按需生成后回填（3.4.3，缓存复用），未生成时为 null */
  reason: string | null
}

/** AI 执行顺序推荐结果（type=plan_order_recommend，target=计划 ID，2.2.3） */
export interface AiPlanOrderRecommendResult {
  /** 计算时刻 test_plan.snapshot_synced_at 的列值（含 NULL），用于失效判定 */
  planSyncedAt: string | null
  /** 本次计算实际使用的权重（settings 键 planOrder.weights） */
  weights: Record<string, number>
  items: AiPlanOrderRecommendItem[]
}

/** 执行顺序推荐计算响应（3.4.1）：同步计算立即返回任务标识与推荐结果 */
export interface AiPlanOrderComputeResp {
  taskId: string
  result: AiPlanOrderRecommendResult
}

/** 执行顺序推荐结果查询响应（3.4.2） */
export interface AiPlanOrderQueryResp {
  /** true 表示计划快照在计算后重新同步过，结果已失效需重算 */
  stale: boolean
  result: AiPlanOrderRecommendResult | null
}

/** 执行顺序推荐理由响应（3.4.3） */
export interface AiPlanOrderReasonResp {
  reason: string | null
}

/** 需求池条目状态：active 参与 AI 消费，archived 归档只读（需求规格 3.2.4） */
export type RequirementStatus = 'active' | 'archived'

/** 需求池条目列表项（US-AI-004） */
export interface RequirementPoolItem {
  id: string
  title: string
  sourceUrl: string | null
  status: RequirementStatus
  createdBy: string
  creatorName: string | null
  updatedAt: string
  /** AI 拆分入库标识（US-AI-019，仅展示 AI 徽标，不影响业务规则） */
  aiGenerated: boolean
}

/** 需求池条目详情 */
export interface RequirementDetail {
  id: string
  title: string
  content: string
  sourceUrl: string | null
  status: RequirementStatus
  createdBy: string
  creatorName: string | null
  updatedBy: string
  createdAt: string
  updatedAt: string
  /** AI 拆分入库标识（US-AI-019，仅展示 AI 徽标，不影响业务规则） */
  aiGenerated: boolean
}

/** 需求池条目摘要（文档关联查询与条目选取器共用） */
export interface RequirementSummary {
  id: string
  title: string
}

// --- 需求文档 AI 拆分（US-AI-019，详细设计 3.2.3） ---

/** AI 拆分预览需求点（done 帧 items 元素） */
export interface AiRequirementSplitItem {
  title: string
  content: string
}

/** AI 拆分模块分组（done 帧 modules 元素） */
export interface AiRequirementSplitModule {
  module: string
  items: AiRequirementSplitItem[]
}

/** AI 拆分 done 帧载荷：模块分组 + 警告（纯预览，不落库） */
export interface AiRequirementSplitResult {
  modules: AiRequirementSplitModule[]
  warnings: string[]
}

// --- 缺陷 AI 能力（US-AI-008/009/010，详细设计 3.1–3.3） ---

/** 缺陷表单智能建议响应（3.1） */
export interface AiBugSuggestion {
  optimizedTitle: string
  severity: BugSeverity
  priority: BugPriority
  reason: string
}

/** 语义查重单条命中（3.2） */
export interface AiBugDedupItem {
  bugId: string
  title: string
  status: BugStatus
  assigneeName: string | null
  /** 降级模式下为 null，前端据此不展示相似度徽标（3.2） */
  similarity: number | null
}

/** 语义查重响应（3.2） */
export interface AiBugDedupResult {
  semanticDegraded: boolean
  items: AiBugDedupItem[]
}

/** 聚类模块分布条目（2.3；moduleId 为 null 表示未指定模块） */
export interface AiBugClusterModule {
  moduleId: string | null
  moduleName: string
  count: number
}

/** 聚类单簇内缺陷（2.3，携带标题/严重度/状态供明细直接渲染，无需再查详情） */
export interface AiBugClusterBug {
  id: string
  title: string
  severity: BugSeverity
  status: BugStatus
}

/** 聚类单簇（2.3；labeled=false 表示 LLM 归纳失败/超限，label 为占位「未命名主题 N」，前端需明示标签生成失败） */
export interface AiBugCluster {
  label: string
  labeled: boolean
  rootCause: string | null
  bugs: AiBugClusterBug[]
  severityDist: Record<BugSeverity, number>
  moduleDist: AiBugClusterModule[]
}

/** 聚类结果快照（bug_clustering 任务 result，2.3；unclustered 仅含 ID 无标题，仅计数展示） */
export interface AiBugClusterSnapshot {
  generatedAt: string
  bugCount: number
  clusters: AiBugCluster[]
  unclustered: string[]
}

// ==================== 全局智能助手（ChatBot，US-AI-011~014） ====================

/** 助手会话列表项（后端 AiConversationItemRespDTO，全局智能助手详细设计 3.1） */
export interface AiConversation {
  id: string
  title: string
  lastActiveAt: string
}

/** 助手会话列表响应（键集分页，nextCursor 为空表示无更多，3.1） */
export interface AiConversationListResp {
  items: AiConversation[]
  nextCursor: string | null
}

/** 助手消息角色（3.1；tool 消息前端渲染为工具调用卡片） */
export type AiMessageRole = 'user' | 'assistant' | 'tool'

/** assistant 消息内单条工具调用载荷（AiMessageRespDTO.toolCalls 元素） */
export interface AiMessageToolCall {
  name: string
  /** OpenAI 工具参数 JSON 字符串 */
  arguments: string
  callId: string
}

/** 助手消息（AiMessageRespDTO，3.1；role=tool 渲染为工具调用卡片） */
export interface AiMessage {
  id: string
  role: AiMessageRole
  content: string | null
  /** assistant 消息发起的工具调用载荷，非工具消息为空 */
  toolCalls: AiMessageToolCall[] | null
  /** tool 消息对应的调用 ID */
  toolCallId: string | null
  createdAt: string
}

/** 页面上下文桥（4.4）：脑图页注入 documentId/selectedNodeId，项目内注入 projectId */
export interface AiPageContext {
  projectId?: string | null
  documentId?: string | null
  selectedNodeId?: string | null
}

/** 发送消息请求体（AiAssistantSendReqDTO，3.2） */
export interface AiAssistantSendPayload {
  content: string
  pageContext?: AiPageContext | null
  modelId?: string | null
}

/** 写操作确认/取消请求体（AiConfirmReqDTO，3.3；令牌经请求体传递不入 URL） */
export interface AiConfirmPayload {
  confirmToken: string
}

// ---- 助手 SSE 扩展事件载荷（基础设施统一帧格式，3.2） ----

/** delta 帧：回复文本增量 */
export interface AiDeltaEvent {
  content: string
}

/** tool_call 帧：只读工具执行通知（前端渲染过程卡片） */
export interface AiToolCallEvent {
  toolName: string
  summary: string
}

/** confirm_required 帧：写操作确认请求，本轮 SSE 随即以 done 结束 */
export interface AiConfirmRequiredEvent {
  confirmToken: string
  toolName: string
  /** 写工具参数 JSON（arguments），前端渲染为操作明细 */
  preview: string
  expiresAt: string
}

/** minder_commands 帧：对话式编辑翻译结果（DSL），交前端预览执行 */
export interface AiMinderCommandsEvent {
  commands: AiMinderCommand[]
  documentId: string
}

/** done 帧：本轮回复完成 */
export interface AiDoneEvent {
  messageId: string
}

/** error 帧：失败 */
export interface AiErrorEvent {
  code: number
  message: string
}

// ---- 脑图操作指令集（DSL，智能用例生成与脑图智能编辑详细设计 4.4） ----

/** 用例优先级（P0-P3，仅对 case 生效） */
export type AiPriority = 'P0' | 'P1' | 'P2' | 'P3'

/** selector 各条件为 AND 关系；@selected 为标题引用的保留值，仅允许出现在 subtreeRootTitle/targetParentTitle */
export interface AiMinderSelector {
  types?: CaseNodeType[]
  priorities?: AiPriority[]
  /** 标题包含（忽略大小写） */
  keyword?: string
  /** 以标题引用限定子树范围（默认全文档），预览阶段按标题精确匹配解析 */
  subtreeRootTitle?: string
  /** 按 AI 标识筛选 */
  aiGenerated?: boolean
}

export type AiMinderAction =
  | { type: 'mark_type'; params: { nodeType: CaseNodeType } }
  | { type: 'mark_priority'; params: { priority: AiPriority } }
  | { type: 'highlight'; params: Record<string, never> }
  | { type: 'move'; params: { targetParentTitle: string } }
  | { type: 'add_child'; params: { nodes: AiGeneratedNode[] } }

/** 脑图操作指令（4.4.1）；commands 数组按序执行，上限 10 条 */
export interface AiMinderCommand {
  selector: AiMinderSelector
  action: AiMinderAction
}



// ==================== 接口测试 · 项目设置（安全策略与应用设置） ====================

/** 设置项业务域归属（common 平台通用域预留；func_test 由功能测试域后续扩展注册） */
export type ProjectSettingDomain = 'common' | 'api_test' | 'func_test'

/** 单个设置项；未落库键由后端以注册表默认值填充（explicit=false） */
export interface ProjectSettingItem {
  domain: ProjectSettingDomain
  settingKey: string
  settingValue: string
  defaultValue: string
  explicit: boolean
}

export interface ProjectSettingListResp {
  items: ProjectSettingItem[]
}

export interface ProjectSettingUpdateReq {
  items: {
    domain: ProjectSettingDomain
    settingKey: string
    settingValue: string
  }[]
}

export interface ProjectSettingUpdateResp {
  updated: number
}

// ==================== 接口测试 · 项目设置（环境管理） ====================

export type ApiEnvironmentScope = 'project' | 'global'

export type ApiVariableType = 'text' | 'number' | 'sensitive'

/** 处理器类别：作用于该环境下所有请求的前置/后置处理器 */
export type ApiProcessorType = 'preprocessor' | 'postprocessor'

export interface ApiHeaderItem {
  key: string
  value: string
  enabled: boolean
}

export interface ApiHttpConfigPayload {
  name: string
  refName?: string
  baseUrl?: string
  defaultMethod?: string
  headers?: ApiHeaderItem[]
  timeoutMs?: number
  connectTimeoutMs?: number
  followRedirects?: boolean
  verifySsl?: boolean
  isDefault?: boolean
}

export interface ApiHttpConfig extends ApiHttpConfigPayload {
  id: string
}

export interface ApiVariablePayload {
  name: string
  value?: string
  type?: ApiVariableType
  description?: string
}

/** 敏感值不回显明文：value 恒为掩码或空，hasValue 标识服务端是否已配置 */
export interface ApiVariable extends ApiVariablePayload {
  id: string
  hasValue: boolean
}

export interface ApiDataSource {
  id: string
  name: string
  refName: string
  driver: string
  url: string
  connectionProperties?: Record<string, unknown>
  maxPoolSize?: number
}

export interface ApiProcessor {
  id: string
  processorType: ApiProcessorType
  name: string
  config?: Record<string, unknown>
  sortOrder?: number
  enabled: boolean
}

export interface ApiEnvironmentListItem {
  id: string
  name: string
  description?: string
  /** 全局环境为 V1.2 预留扩展位，后端当前恒为 project 且暂未下发该字段 */
  scope?: ApiEnvironmentScope
  isDefault: boolean
  sortOrder: number
  variableCount: number
  dataSourceCount: number
  processorCount: number
}

export interface ApiEnvironmentDetail {
  id: string
  name: string
  description?: string
  scope: ApiEnvironmentScope
  isDefault: boolean
  sortOrder: number
  httpConfigs: ApiHttpConfig[]
  variables: ApiVariable[]
  dataSources: ApiDataSource[]
  processors: ApiProcessor[]
}

/** 环境聚合保存：子资源全量替换语义，缺省段落视为清空 */
export interface ApiEnvironmentSaveReq {
  name: string
  description?: string
  sortOrder?: number
  isDefault?: boolean
  httpConfigs?: ApiHttpConfigPayload[]
  variables?: ApiVariablePayload[]
  dataSources?: {
    name: string
    refName?: string
    driver?: string
    url?: string
    connectionProperties?: Record<string, unknown>
    maxPoolSize?: number
  }[]
  processors?: {
    processorType: ApiProcessorType
    name: string
    config?: Record<string, unknown>
    sortOrder?: number
    enabled?: boolean
  }[]
}

export interface ApiIdResp {
  id: string
}

export interface ApiSetDefaultResp {
  success: boolean
}

export interface ApiImportResult {
  createdCount: number
  overwrittenCount: number
  skippedCount: number
}

export interface ApiDataSourceTestResp {
  success: boolean
  message: string
  databaseVersion?: string
}

export interface ApiHttpTestResp {
  success: boolean
  message: string
  statusCode?: number
  durationMs?: number
}

export interface ApiVariableRevealResp {
  id: string
  name: string
  value?: string
}

// ==================== 快速调试（详细设计 3.1） ====================

export type ApiDebugBodyType = 'none' | 'json' | 'form' | 'raw' | 'binary'

/** 键值对条目（请求头 / Query 参数共用结构） */
export interface ApiDebugKeyValue {
  key: string
  value: string
  enabled: boolean
}

export interface ApiDebugRequestBody {
  type: ApiDebugBodyType
  /** json→对象、form→对象、raw/binary→字符串 */
  content?: unknown
}

export interface ApiDebugExecuteReq {
  protocol?: 'http'
  method: string
  url: string
  headers?: ApiDebugKeyValue[]
  body?: ApiDebugRequestBody
  params?: ApiDebugKeyValue[]
  processors?: Record<string, unknown>[]
  timeoutMs?: number
  environmentId?: string
}

export interface ApiDebugExecuteResp {
  debugRecordId: string
  status: 'success' | 'failed' | 'error'
  responseStatus?: number
  responseHeaders?: Record<string, string>
  responseBody?: unknown
  durationMs?: number
  size?: number
  errorMessage?: string
}

export interface ApiDebugCurlImportResp {
  protocol: string
  method: string
  url: string
  headers: ApiDebugKeyValue[]
  body: { type: ApiDebugBodyType; content?: unknown }
  params: ApiDebugKeyValue[]
}

export interface ApiDebugRecordItem {
  id: string
  name?: string
  method: string
  url?: string
  status: ApiDebugExecuteResp['status']
  responseStatus?: number
  durationMs?: number
  executedAt: string
}

export interface ApiDebugRestoreResp {
  debugRecordId: string
  request: {
    protocol?: string
    method?: string
    url?: string
    headers?: ApiDebugKeyValue[] | null
    body?: { type?: string; content?: unknown } | null
    params?: ApiDebugKeyValue[] | null
  }
  response: {
    statusCode?: number
    headers?: Record<string, string> | null
    body?: unknown
    elapsed?: number
    size?: number
  }
  createdAt: string
}

/** 认证配置：提交时由前端换算为 Authorization 头，不单独持久化（详细设计 5.1） */
export interface ApiDebugAuth {
  type: 'none' | 'basic' | 'digest'
  username?: string
  password?: string
}

/** 调试标签页状态（debugModel 状态机维护） */
export interface DebugTab {
  id: string
  name: string
  method: string
  url: string
  headers: ApiDebugKeyValue[]
  params: ApiDebugKeyValue[]
  /** 四种请求体类型内容独立缓存，切换类型不丢失已填内容（SRS 3.1 业务规则） */
  bodies: Record<Exclude<ApiDebugBodyType, 'binary'>, unknown>
  auth: ApiDebugAuth
  connectTimeoutMs: number
  responseTimeoutMs: number
  followRedirects: boolean
  dirty: boolean
  response: ApiDebugExecuteResp | null
}
