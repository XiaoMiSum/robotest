import { describe, expect, it } from 'vitest'
import {
  WORKSPACE_ROLE,
  isWorkspaceAdmin,
  isWorkspaceArchived,
  workspaceRoleLabel,
} from './workspaceRole'

describe('workspaceRoleLabel', () => {
  it('优先展示后端真实角色名称', () => {
    expect(workspaceRoleLabel(WORKSPACE_ROLE.ADMIN, '空间管理员')).toBe('空间管理员')
    expect(workspaceRoleLabel(WORKSPACE_ROLE.MEMBER, '成员')).toBe('成员')
  })

  it('预置角色缺少名称时回退为管理员和成员', () => {
    expect(workspaceRoleLabel(WORKSPACE_ROLE.ADMIN)).toBe('管理员')
    expect(workspaceRoleLabel(WORKSPACE_ROLE.MEMBER)).toBe('成员')
  })

  it('不把行为文案当作角色名称', () => {
    expect(workspaceRoleLabel(WORKSPACE_ROLE.ADMIN, '我管理')).toBe('管理员')
    expect(workspaceRoleLabel(WORKSPACE_ROLE.MEMBER, '我管理')).toBe('成员')
  })

  it('识别管理员与归档状态', () => {
    expect(isWorkspaceAdmin(WORKSPACE_ROLE.ADMIN)).toBe(true)
    expect(isWorkspaceAdmin(WORKSPACE_ROLE.MEMBER)).toBe(false)
    expect(isWorkspaceArchived('dissolved')).toBe(true)
    expect(isWorkspaceArchived('active')).toBe(false)
  })
})
