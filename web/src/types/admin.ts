/** 通用状态：启用 / 禁用 / 锁定（locked 由管理员设置，与 disabled 一样阻止登录） */
export type UserStatus = 'active' | 'disabled' | 'locked'

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
  /** 创建人姓名（created_by 回查）；历史数据无创建人时为 null，前端展示 — */
  createdByName?: string | null
  createdAt: string
}

// ==================== 数据概览统计 ====================

/** 用户维度统计（口径见《系统管理模块详细设计说明书》§3.2） */
export interface DashboardUsersStats {
  total: number
  weekNew: number
  enabled: number
  disabled: number
  locked: number
}

/** 工作空间维度统计 */
export interface DashboardWorkspacesStats {
  total: number
  active: number
  dissolved: number
}

/** 项目维度统计 */
export interface DashboardProjectsStats {
  total: number
  weekNew: number
}

/** 今日活跃（登录人次，接口调用/失败率按需求裁剪不返回） */
export interface DashboardActivityStats {
  todayLogins: number
}

/** 单日活跃用户（按日去重），date 为 YYYY-MM-DD */
export interface DailyActiveUsers {
  date: string
  count: number
}

/** 数据概览统计（GET /admin/dashboard/stats） */
export interface DashboardStats {
  generatedAt: string
  users: DashboardUsersStats
  workspaces: DashboardWorkspacesStats
  projects: DashboardProjectsStats
  activity: DashboardActivityStats
  activeUsersDaily: DailyActiveUsers[]
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

/** 按一级模块分组的权限配置表格（一级模块 → 二级模块 → 权限点） */
export interface PermissionTopModule {
  topModule: string
  modules: PermissionModule[]
}
