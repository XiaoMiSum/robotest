import { get, post, put, patch, del } from '@/services'
import type {
  AdminUser,
  AdminWorkspace,
  DashboardStats,
  PageResult,
  PermissionTopModule,
  RoleDetail,
  RoleTreeNode,
  RoleWorkspaceUser,
  UserCreatePayload,
  UserQueryParams,
  UserSimple,
  UserStatus,
  UserUpdatePayload,
  WorkspaceMember,
} from '@/types'

// ==================== 用户管理 ====================

export function fetchUsers(params: UserQueryParams): Promise<PageResult<AdminUser>> {
  return get('/admin/users', { ...params })
}

export function fetchSimpleUserList(keyword?: string): Promise<UserSimple[]> {
  return get('/admin/users/simple', { keyword })
}

export function fetchUserDetail(id: string): Promise<AdminUser> {
  return get(`/admin/users/${id}`)
}

export function createUser(data: UserCreatePayload): Promise<string> {
  return post('/admin/users', data)
}

export function updateUser(id: string, data: UserUpdatePayload): Promise<AdminUser> {
  return put(`/admin/users/${id}`, data)
}

export function updateUserStatus(id: string, status: UserStatus): Promise<AdminUser> {
  return patch(`/admin/users/${id}/status`, { status })
}

export function batchUpdateUserStatus(userIds: string[], status: UserStatus): Promise<void> {
  return patch('/admin/users/batch-status', { userIds, status })
}

export function resetUserPassword(id: string, newPassword: string): Promise<void> {
  return post(`/admin/users/${id}/reset-password`, { newPassword })
}

// ==================== 数据概览 ====================

export function fetchDashboardStats(): Promise<DashboardStats> {
  return get('/admin/dashboard/stats')
}

// ==================== 工作空间管理 ====================

export function fetchWorkspaces(params: {
  keyword?: string
  status?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<AdminWorkspace>> {
  return get('/admin/workspaces', { ...params })
}

export function fetchWorkspaceDetail(id: string): Promise<AdminWorkspace> {
  return get(`/admin/workspaces/${id}`)
}

export function createWorkspace(data: {
  name: string
  description?: string
  adminUserId: string
}): Promise<string> {
  return post('/admin/workspaces', data)
}

export function updateWorkspace(
  id: string,
  data: { name?: string; description?: string },
): Promise<AdminWorkspace> {
  return put(`/admin/workspaces/${id}`, data)
}

export function dissolveWorkspace(id: string): Promise<void> {
  return del(`/admin/workspaces/${id}`)
}

export function restoreWorkspace(id: string): Promise<void> {
  return post(`/admin/workspaces/${id}/restore`)
}

export function fetchWorkspaceMembers(
  id: string,
  params: { pageNo?: number; pageSize?: number },
): Promise<PageResult<WorkspaceMember>> {
  return get(`/admin/workspaces/${id}/members`, { ...params })
}

export function addWorkspaceMembers(
  id: string,
  members: { userId: string; workspaceRole: string }[],
): Promise<string[]> {
  return post(`/admin/workspaces/${id}/members`, { members })
}

export function updateWorkspaceMemberRole(
  id: string,
  userId: string,
  workspaceRole: string,
): Promise<void> {
  return put(`/admin/workspaces/${id}/members/${userId}`, { workspaceRole })
}

export function removeWorkspaceMember(id: string, userId: string): Promise<void> {
  return del(`/admin/workspaces/${id}/members/${userId}`)
}

// ==================== 角色与权限管理 ====================

export function fetchRoleList(type?: string): Promise<RoleTreeNode[]> {
  return get('/admin/roles', { type })
}

export function fetchRoleDetail(id: string): Promise<RoleDetail> {
  return get(`/admin/roles/${id}`)
}

export function createRole(data: { name: string; type: string }): Promise<string> {
  return post('/admin/roles', data)
}

export function updateRole(id: string, data: { name: string }): Promise<RoleDetail> {
  return put(`/admin/roles/${id}`, data)
}

export function deleteRole(id: string): Promise<void> {
  return del(`/admin/roles/${id}`)
}

export function updateRolePermissions(id: string, permissions: string[]): Promise<RoleDetail> {
  return put(`/admin/roles/${id}/permissions`, { permissions })
}

export function addRoleUsers(id: string, userIds: string[]): Promise<void> {
  return post(`/admin/roles/${id}/users`, { userIds })
}

export function addWorkspaceRoleUsers(id: string, userIds: string[], workspaceIds: string[]): Promise<void> {
  return post(`/admin/roles/${id}/workspace-users`, { userIds, workspaceIds })
}

export function removeRoleUser(id: string, userId: string): Promise<void> {
  return del(`/admin/roles/${id}/users/${userId}`)
}

export function fetchRoleWorkspaceUsers(
  id: string,
  pageNo: number,
  pageSize: number,
): Promise<PageResult<RoleWorkspaceUser>> {
  return get(`/admin/roles/${id}/workspace-users`, { pageNo, pageSize })
}

export function removeWorkspaceRoleUser(roleId: string, userId: string, workspaceId: string): Promise<void> {
  return del(`/admin/roles/${roleId}/users/${userId}/workspace/${workspaceId}`)
}

export function fetchPermissionTable(roleType?: string): Promise<PermissionTopModule[]> {
  return get('/admin/roles/permissions/table', { roleType })
}

