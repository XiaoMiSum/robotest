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
