import api from '@/services'
import type { AxiosRequestConfig } from 'axios'
import type {
  AdminUser,
  AdminWorkspace,
  AiAgent,
  AiAgentDetail,
  AiAgentSavePayload,
  AiChatModel,
  AiChatModelSavePayload,
  AiConfig,
  AiConfigSavePayload,
  AiConfigTestPayload,
  AiConnectivityTestResult,
  AiProviderPreset,
  AiSettingSchemaGroup,
  AiStatistics,
  AiTask,
  PageResult,
  PermissionModule,
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

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言（C1：unknown + 断言）
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return api.post(url, data, config) as unknown as Promise<T>
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

/** 预置工作空间角色 ID（与后端 Constants.WorkspaceRole / V5 迁移脚本保持一致） */
export const WORKSPACE_ROLE = {
  ADMIN: 'c0000000-0000-0000-0000-000000000001',
  MEMBER: 'c0000000-0000-0000-0000-000000000002',
} as const

/** 将工作空间角色 UUID 映射为可读名称 */
export function workspaceRoleLabel(roleId: string): string {
  if (roleId === WORKSPACE_ROLE.ADMIN) return '管理员'
  if (roleId === WORKSPACE_ROLE.MEMBER) return '成员'
  return '未知'
}

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

export function createWorkspace(data: { name: string; description?: string }): Promise<string> {
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

export function fetchRoleWorkspaceUsers(id: string): Promise<RoleWorkspaceUser[]> {
  return get(`/admin/roles/${id}/workspace-users`)
}

export function removeWorkspaceRoleUser(roleId: string, userId: string, workspaceId: string): Promise<void> {
  return del(`/admin/roles/${roleId}/users/${userId}/workspace/${workspaceId}`)
}

export function fetchPermissionTable(roleType?: string): Promise<PermissionModule[]> {
  return get('/admin/roles/permissions/table', { roleType })
}

// ==================== AI 配置与智能体（管理端） ====================

export function fetchAiConfig(): Promise<AiConfig | null> {
  return get('/admin/ai/config')
}

export function saveAiConfig(data: AiConfigSavePayload): Promise<AiConfig> {
  return put('/admin/ai/config', data)
}

export function testAiConnectivity(data: AiConfigTestPayload): Promise<AiConnectivityTestResult> {
  // 连通性测试真实调用外部供应商，上游慢时 15s 全局超时会误报失败，单独放宽到 120s
  return post('/admin/ai/config/test', data, { timeout: 120_000 })
}

export function fetchAiProviders(): Promise<AiProviderPreset[]> {
  return get('/admin/ai/providers')
}

// ==================== 对话模型管理（3.3.7） ====================

export function fetchAiChatModels(): Promise<AiChatModel[]> {
  return get('/admin/ai/chat-models')
}

export function createAiChatModel(data: AiChatModelSavePayload): Promise<AiChatModel> {
  return post('/admin/ai/chat-models', data)
}

export function updateAiChatModel(id: string, data: AiChatModelSavePayload): Promise<AiChatModel> {
  return put(`/admin/ai/chat-models/${id}`, data)
}

export function deleteAiChatModel(id: string): Promise<void> {
  return del(`/admin/ai/chat-models/${id}`)
}

export function setAiChatModelDefault(id: string): Promise<void> {
  return put(`/admin/ai/chat-models/${id}/default`)
}

export function setAiChatModelEnabled(id: string, enabled: boolean): Promise<void> {
  return put(`/admin/ai/chat-models/${id}/enabled`, { enabled })
}

export function fetchAiSettingsSchema(): Promise<AiSettingSchemaGroup[]> {
  return get('/admin/ai/settings-schema')
}

export function fetchAiStatistics(params: {
  startDate?: string
  endDate?: string
  groupBy?: string
}): Promise<AiStatistics> {
  return get('/admin/ai/statistics', { ...params })
}

export function fetchAiRebuildTask(): Promise<AiTask | null> {
  return get('/admin/ai/rebuild-task')
}

export function retryAiRebuildTask(): Promise<void> {
  return post('/admin/ai/rebuild-task/retry')
}

export function fetchAiAgents(): Promise<AiAgent[]> {
  return get('/admin/ai/agents')
}

export function fetchAiAgentDetail(functionType: string): Promise<AiAgentDetail> {
  return get(`/admin/ai/agents/${functionType}`)
}

export function saveAiAgent(functionType: string, data: AiAgentSavePayload): Promise<void> {
  return put(`/admin/ai/agents/${functionType}`, data)
}

export function restoreAiAgentDefault(functionType: string): Promise<void> {
  return del(`/admin/ai/agents/${functionType}`)
}
