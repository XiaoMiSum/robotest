import api from '@/services'
import type {
  Invitation,
  InvitationJoinResult,
  InvitationVerifyResult,
  MemberAddResult,
  PageResult,
  Project,
  ProjectStatus,
  WorkspaceContext,
  WorkspaceItem,
  WorkspaceMember,
} from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}
function del<T>(url: string): Promise<T> {
  return api.delete(url) as unknown as Promise<T>
}

// ==================== 我的空间（/api/workspaces，无需 X-Active-Workspace） ====================

export function fetchMyWorkspaces(params: {
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<WorkspaceItem>> {
  return get('/workspaces', { ...params })
}

export function setActiveWorkspacePreference(workspaceId: string): Promise<void> {
  return put('/workspaces/active', { workspaceId })
}

// ==================== 空间上下文（/api/workspace，自动注入 X-Active-Workspace） ====================

export function fetchWorkspaceContext(): Promise<WorkspaceContext> {
  return get('/workspace')
}

export function updateWorkspaceInfo(data: {
  name?: string
  description?: string
}): Promise<WorkspaceContext> {
  return put('/workspace', data)
}

export function setDefaultProject(projectId: string | null): Promise<WorkspaceContext> {
  return put('/workspace/default-project', { projectId })
}

// ==================== 成员管理（/api/workspace/members） ====================

export function fetchMembers(params: {
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<WorkspaceMember>> {
  return get('/workspace/members', { ...params })
}

export function addMembers(
  members: { userId: string; workspaceRole: string }[],
): Promise<MemberAddResult> {
  return post('/workspace/members', { members })
}

export function updateMemberRole(userId: string, workspaceRole: string): Promise<void> {
  return put(`/workspace/members/${userId}`, { workspaceRole })
}

export function removeMember(userId: string): Promise<void> {
  return del(`/workspace/members/${userId}`)
}

// ==================== 邀请链接（/api/workspace/invitations） ====================

export function createInvitation(data: {
  expiresAt?: string | null
  maxUses?: number | null
}): Promise<Invitation> {
  return post('/workspace/invitations', data)
}

export function fetchInvitations(params: {
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<Invitation>> {
  return get('/workspace/invitations', { ...params })
}

export function revokeInvitation(id: string): Promise<void> {
  return put(`/workspace/invitations/${id}/revoke`)
}

// ==================== 邀请公开接口（无需认证） ====================

export function verifyInvitation(token: string): Promise<InvitationVerifyResult> {
  return get('/workspace/invitations/verify', { token })
}

export function joinByInvitation(data: {
  token: string
  email: string
  password: string
}): Promise<InvitationJoinResult> {
  return post('/workspace/invitations/join', data)
}

// ==================== 项目管理（/api/workspace/projects） ====================

export function fetchProjects(params: {
  keyword?: string
  status?: ProjectStatus | ''
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<Project>> {
  return get('/workspace/projects', { ...params })
}

export function fetchProjectDetail(id: string): Promise<Project> {
  return get(`/workspace/projects/${id}`)
}

export function createProject(data: {
  name: string
  description?: string
  startTime?: string | null
  endTime?: string | null
}): Promise<Project> {
  return post('/workspace/projects', data)
}

export function updateProject(
  id: string,
  data: { name?: string; description?: string; startTime?: string | null; endTime?: string | null },
): Promise<Project> {
  return put(`/workspace/projects/${id}`, data)
}

export function archiveProject(id: string, archived: boolean): Promise<void> {
  return post(`/workspace/projects/${id}/archive`, { archived })
}

export function deleteProject(id: string): Promise<void> {
  return del(`/workspace/projects/${id}`)
}
