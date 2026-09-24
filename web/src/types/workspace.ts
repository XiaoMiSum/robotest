import type { WorkspaceItem } from './common'

/** 项目状态 */
export type ProjectStatus = 'active' | 'archived'

/** 我的空间范围 */
export type WorkspaceScope = 'all' | 'managed' | 'archived'

/** 我的空间分段计数 */
export interface WorkspaceScopeCounts {
  all: number
  managed: number
  archived: number
}

/** 我的空间分页响应 */
export interface WorkspaceListResult {
  list: WorkspaceItem[]
  total: number
  counts: WorkspaceScopeCounts
}

/** 我的空间查询参数 */
export interface WorkspaceListQuery {
  keyword?: string
  scope?: WorkspaceScope
  pageNo?: number
  pageSize?: number
}

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

/** 邀请链接落库状态 */
export type InvitationStatus = 'active' | 'revoked'

/** 邀请链接运行时有效状态 */
export type InvitationEffectiveStatus = 'active' | 'exhausted' | 'expired' | 'revoked'

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

/** 邀请链接列表项（完整 token 仅在创建或明确复制时获取） */
export interface InvitationListItem {
  id: string
  tokenPreview: string
  effectiveStatus: InvitationEffectiveStatus
  expiresAt: string | null
  maxUses: number | null
  useCount: number
  status: InvitationStatus
  createdAt: string
}

/** 按需获取的邀请复制凭据 */
export interface InvitationCopyLink {
  token: string
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
