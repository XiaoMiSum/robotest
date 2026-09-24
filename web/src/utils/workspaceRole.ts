export const WORKSPACE_ROLE = {
  ADMIN: 'c0000000-0000-0000-0000-000000000001',
  MEMBER: 'c0000000-0000-0000-0000-000000000002',
} as const

export function isWorkspaceAdmin(roleId: string): boolean {
  return roleId === WORKSPACE_ROLE.ADMIN
}

export function isWorkspaceMember(roleId: string): boolean {
  return roleId === WORKSPACE_ROLE.MEMBER
}

export function isWorkspaceArchived(status: string): boolean {
  return status === 'dissolved' || status === 'archived'
}

export function workspaceRoleLabel(roleId: string, roleName?: string | null): string {
  const suppliedName = roleName?.trim()
  if (suppliedName && suppliedName !== '我管理') return suppliedName
  if (isWorkspaceAdmin(roleId)) return '管理员'
  if (isWorkspaceMember(roleId)) return '成员'
  return suppliedName || '未知'
}
