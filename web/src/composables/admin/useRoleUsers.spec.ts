import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminUser, RoleWorkspaceUser } from '@/types'

const mocks = vi.hoisted(() => ({
  addRoleUsers: vi.fn(),
  addWorkspaceRoleUsers: vi.fn(),
  fetchRoleWorkspaceUsers: vi.fn(),
  fetchUsers: vi.fn(),
  removeRoleUser: vi.fn(),
  removeWorkspaceRoleUser: vi.fn(),
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/admin', () => ({
  addRoleUsers: mocks.addRoleUsers,
  addWorkspaceRoleUsers: mocks.addWorkspaceRoleUsers,
  fetchRoleWorkspaceUsers: mocks.fetchRoleWorkspaceUsers,
  fetchUsers: mocks.fetchUsers,
  removeRoleUser: mocks.removeRoleUser,
  removeWorkspaceRoleUser: mocks.removeWorkspaceRoleUser,
}))

import { useRoleUsers } from './useRoleUsers'

function makeAdminUser(id = 'user-1'): AdminUser {
  return {
    id,
    username: id,
    name: '测试用户',
    email: `${id}@example.com`,
    status: 'active',
    roles: [],
    workspaces: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

function makeWorkspaceUser(id = 'user-1', workspaceIds = ['workspace-1']): RoleWorkspaceUser {
  return {
    userId: id,
    username: id,
    name: '测试用户',
    workspaces: workspaceIds.map((workspaceId) => ({ workspaceId, workspaceName: '测试空间' })),
    grantedAt: '2026-03-01T10:00:00Z',
  }
}

async function createSut(roleType = 'system') {
  const state = { roleId: 'role-1', roleType }
  const sut = useRoleUsers(
    () => state.roleId,
    () => state.roleType,
  )
  await vi.dynamicImportSettled()
  return { sut, state }
}

describe('useRoleUsers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchUsers.mockResolvedValue({ list: [makeAdminUser()], total: 41 })
    mocks.fetchRoleWorkspaceUsers.mockResolvedValue({ list: [makeWorkspaceUser()], total: 1 })
    mocks.addRoleUsers.mockResolvedValue(undefined)
    mocks.addWorkspaceRoleUsers.mockResolvedValue(undefined)
    mocks.removeRoleUser.mockResolvedValue(undefined)
    mocks.removeWorkspaceRoleUser.mockResolvedValue(undefined)
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  })

  it('系统角色使用后端分页加载关联用户', async () => {
    const { sut } = await createSut('system')

    expect(mocks.fetchUsers).toHaveBeenCalledWith({ roleId: 'role-1', pageNo: 1, pageSize: 20 })
    expect(sut.users.value).toEqual([makeAdminUser()])
    expect(sut.workspaceUsers.value).toEqual([])
    expect(sut.total.value).toBe(41)
  })

  it('工作空间角色使用后端分页加载关联用户', async () => {
    const { sut } = await createSut('workspace')

    expect(mocks.fetchRoleWorkspaceUsers).toHaveBeenCalledWith('role-1', 1, 20)
    expect(sut.workspaceUsers.value).toEqual([makeWorkspaceUser()])
    expect(sut.users.value).toEqual([])
    expect(sut.total.value).toBe(1)
  })

  it('切换页码和每页数量时重新请求，修改每页数量后回到第一页', async () => {
    const { sut } = await createSut('system')

    sut.handlePageChange(3)
    await vi.dynamicImportSettled()
    expect(sut.query.pageNo).toBe(3)
    expect(mocks.fetchUsers).toHaveBeenLastCalledWith({ roleId: 'role-1', pageNo: 3, pageSize: 20 })

    sut.handlePageSizeChange(50)
    await vi.dynamicImportSettled()
    expect(sut.query.pageNo).toBe(1)
    expect(sut.query.pageSize).toBe(50)
    expect(mocks.fetchUsers).toHaveBeenLastCalledWith({ roleId: 'role-1', pageNo: 1, pageSize: 50 })
  })

  it('当前页为空时回退到最后一个有效页', async () => {
    const { sut } = await createSut('system')
    mocks.fetchUsers.mockReset()
    mocks.fetchUsers
      .mockResolvedValueOnce({ list: [], total: 41 })
      .mockResolvedValueOnce({ list: [makeAdminUser('user-41')], total: 41 })
    sut.query.pageNo = 4

    await sut.load()

    expect(sut.query.pageNo).toBe(3)
    expect(sut.users.value).toEqual([makeAdminUser('user-41')])
    expect(mocks.fetchUsers).toHaveBeenCalledTimes(2)
  })

  it('新增用户后关闭选择弹窗并回到第一页', async () => {
    const { sut } = await createSut('system')
    sut.query.pageNo = 2
    sut.pickerVisible.value = true

    await sut.handleAddUsers(['user-2'])

    expect(mocks.addRoleUsers).toHaveBeenCalledWith('role-1', ['user-2'])
    expect(sut.pickerVisible.value).toBe(false)
    expect(sut.query.pageNo).toBe(1)
    expect(mocks.fetchUsers).toHaveBeenLastCalledWith({ roleId: 'role-1', pageNo: 1, pageSize: 20 })
  })

  it('工作空间角色未选择空间时阻止新增', async () => {
    const { sut } = await createSut('workspace')

    await sut.handleAddUsers(['user-2'])

    expect(mocks.addWorkspaceRoleUsers).not.toHaveBeenCalled()
    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少选择一个空间')
  })

  it('移除系统角色用户前二次确认，成功后刷新当前页', async () => {
    const { sut } = await createSut('system')
    const user = makeAdminUser()

    await sut.handleRemove(user)

    expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith('确定要移除用户「测试用户」的该角色吗？', '确认移除', {
      type: 'warning',
    })
    expect(mocks.removeRoleUser).toHaveBeenCalledWith('role-1', user.id)
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('已移除')
  })

  it('取消确认时不移除系统角色用户', async () => {
    mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
    const { sut } = await createSut('system')

    await sut.handleRemove(makeAdminUser())

    expect(mocks.removeRoleUser).not.toHaveBeenCalled()
  })

  it('单空间工作空间角色用户直接确认移除', async () => {
    const { sut } = await createSut('workspace')
    const user = makeWorkspaceUser()

    await sut.handleRemoveWorkspace(user)

    expect(mocks.removeWorkspaceRoleUser).toHaveBeenCalledWith('role-1', user.userId, 'workspace-1')
  })

  it('多空间用户先选择空间再批量移除', async () => {
    const { sut } = await createSut('workspace')
    const user = makeWorkspaceUser('user-2', ['workspace-1', 'workspace-2'])

    await sut.handleRemoveWorkspace(user)
    expect(sut.wsRemoveVisible.value).toBe(true)
    expect(sut.wsRemoveTarget.value).toEqual(user)
    expect(mocks.removeWorkspaceRoleUser).not.toHaveBeenCalled()

    sut.wsRemoveSelected.value = ['workspace-1', 'workspace-2']
    await sut.handleWsRemoveConfirm()

    expect(mocks.removeWorkspaceRoleUser).toHaveBeenCalledTimes(2)
    expect(mocks.removeWorkspaceRoleUser).toHaveBeenCalledWith('role-1', user.userId, 'workspace-1')
    expect(mocks.removeWorkspaceRoleUser).toHaveBeenCalledWith('role-1', user.userId, 'workspace-2')
    expect(sut.wsRemoveVisible.value).toBe(false)
  })
})
