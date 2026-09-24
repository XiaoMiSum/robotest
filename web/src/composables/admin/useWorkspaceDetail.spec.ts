import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminWorkspace, UserSimple, WorkspaceMember } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchRoleList: vi.fn(),
  fetchWorkspaceDetail: vi.fn(),
  fetchWorkspaceMembers: vi.fn(),
  fetchSimpleUserList: vi.fn(),
  updateWorkspace: vi.fn(),
  updateWorkspaceMemberRole: vi.fn(),
  removeWorkspaceMember: vi.fn(),
  addWorkspaceMembers: vi.fn(),
  dissolveWorkspace: vi.fn(),
  restoreWorkspace: vi.fn(),
  useRoute: vi.fn(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: {
    confirm: vi.fn(),
    prompt: vi.fn(),
  },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => {
      cb()
    },
  }
})

vi.mock('vue-router', () => ({
  useRoute: mocks.useRoute,
  useRouter: mocks.useRouter,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/admin', () => ({
  fetchRoleList: mocks.fetchRoleList,
  fetchWorkspaceDetail: mocks.fetchWorkspaceDetail,
  fetchWorkspaceMembers: mocks.fetchWorkspaceMembers,
  fetchSimpleUserList: mocks.fetchSimpleUserList,
  updateWorkspace: mocks.updateWorkspace,
  updateWorkspaceMemberRole: mocks.updateWorkspaceMemberRole,
  removeWorkspaceMember: mocks.removeWorkspaceMember,
  addWorkspaceMembers: mocks.addWorkspaceMembers,
  dissolveWorkspace: mocks.dissolveWorkspace,
  restoreWorkspace: mocks.restoreWorkspace,
}))

import { useWorkspaceDetail } from './useWorkspaceDetail'

function makeWorkspace(overrides?: Partial<AdminWorkspace>): AdminWorkspace {
  return {
    id: 'ws-1',
    name: '测试工作空间',
    description: '描述',
    status: 'active',
    memberCount: 5,
    projectCount: 0,
    createdAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function makeMember(overrides?: Partial<WorkspaceMember>): WorkspaceMember {
  return {
    userId: 'u-1',
    username: 'zhangsan',
    name: '张三',
    email: 'zhang@test.com',
    workspaceRole: 'role-1',
    joinedAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

function makeUser(id: string, name: string): UserSimple {
  return { id, name }
}

function setupMocks(overrides?: {
  workspace?: AdminWorkspace
  members?: WorkspaceMember[]
  memberTotal?: number
  roles?: { id: string; name: string }[]
  users?: UserSimple[]
}) {
  mocks.fetchWorkspaceDetail.mockResolvedValue(overrides?.workspace ?? makeWorkspace())
  mocks.fetchWorkspaceMembers.mockResolvedValue({
    list: overrides?.members ?? [makeMember()],
    total: overrides?.memberTotal ?? 1,
  })
  mocks.fetchRoleList.mockResolvedValue(overrides?.roles ?? [{ id: 'role-1', name: '管理员' }])
  mocks.fetchSimpleUserList.mockResolvedValue(overrides?.users ?? [])
}

describe('useWorkspaceDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRoute.mockReturnValue({ params: { id: 'ws-1' } })
    mocks.useRouter.mockReturnValue({ push: vi.fn() })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
    mocks.ElMessageBox.prompt.mockResolvedValue(undefined)
  })

  function init() {
    return useWorkspaceDetail()
  }

  describe('初始状态', () => {
    it('detail 初始为 null', () => {
      const s = init()
      expect(s.detail.value).toBeNull()
    })

    it('infoSaving 初始为 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.infoSaving.value).toBe(false)
    })

    it('onMounted 后 infoLoading 变为 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.infoLoading.value).toBe(false)
    })

    it('infoForm 初始 name 和 description 为空字符串', () => {
      const s = init()
      expect(s.infoForm.name).toBe('')
      expect(s.infoForm.description).toBe('')
    })

    it('infoRules 包含 name 校验规则', () => {
      const s = init()
      expect(s.infoRules.name).toHaveLength(2)
    })

    it('members 初始为空数组', () => {
      const s = init()
      expect(s.members.value).toEqual([])
    })

    it('membersLoading 最终为 false', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.membersLoading.value).toBe(false)
    })

    it('memberTotal 初始为 0', () => {
      const s = init()
      expect(s.memberTotal.value).toBe(0)
    })

    it('memberQuery 初始 pageNo=1 pageSize=20', () => {
      const s = init()
      expect(s.memberQuery.pageNo).toBe(1)
      expect(s.memberQuery.pageSize).toBe(20)
    })

    it('roleOptions 初始为空数组', () => {
      const s = init()
      expect(s.roleOptions.value).toEqual([])
    })

    it('defaultRoleId 初始为空字符串', () => {
      const s = init()
      expect(s.defaultRoleId.value).toBe('')
    })

    it('addDialogVisible 初始为 false', () => {
      const s = init()
      expect(s.addDialogVisible.value).toBe(false)
    })

    it('addSubmitting 初始为 false', () => {
      const s = init()
      expect(s.addSubmitting.value).toBe(false)
    })

    it('userSearchLoading 初始为 false', () => {
      const s = init()
      expect(s.userSearchLoading.value).toBe(false)
    })

    it('userOptions 初始为空数组', () => {
      const s = init()
      expect(s.userOptions.value).toEqual([])
    })

    it('selectedUserIds 初始为空数组', () => {
      const s = init()
      expect(s.selectedUserIds.value).toEqual([])
    })

    it('pendingRoles 初始为空对象', () => {
      const s = init()
      expect(s.pendingRoles).toEqual({})
    })
  })

  describe('onMounted 自动加载', () => {
    it('调用 fetchWorkspaceDetail', async () => {
      setupMocks()
      init()
      await vi.dynamicImportSettled()
      expect(mocks.fetchWorkspaceDetail).toHaveBeenCalledWith('ws-1')
    })

    it('调用 fetchWorkspaceMembers', async () => {
      setupMocks()
      init()
      await vi.dynamicImportSettled()
      expect(mocks.fetchWorkspaceMembers).toHaveBeenCalledWith('ws-1', { pageNo: 1, pageSize: 20 })
    })

    it('调用 fetchRoleList', async () => {
      setupMocks()
      init()
      await vi.dynamicImportSettled()
      expect(mocks.fetchRoleList).toHaveBeenCalledWith('workspace')
    })

    it('成功加载后填充 detail', async () => {
      const ws = makeWorkspace()
      setupMocks({ workspace: ws })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.detail.value).toEqual(ws)
      expect(s.infoForm.name).toBe(ws.name)
      expect(s.infoForm.description).toBe(ws.description)
    })

    it('description 为 null 时 infoForm.description 为空字符串', async () => {
      setupMocks({ workspace: makeWorkspace({ description: null as never }) })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.infoForm.description).toBe('')
    })

    it('成功加载后填充 members', async () => {
      const members = [makeMember(), makeMember({ userId: 'u-2', username: '李四' })]
      setupMocks({ members, memberTotal: 2 })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.members.value).toHaveLength(2)
      expect(s.memberTotal.value).toBe(2)
    })

    it('成功加载后填充 roleOptions 和 defaultRoleId', async () => {
      const roles = [{ id: 'r-1', name: '管理员' }, { id: 'r-2', name: '成员' }]
      setupMocks({ roles })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.roleOptions.value).toEqual([
        { value: 'r-1', label: '管理员' },
        { value: 'r-2', label: '成员' },
      ])
      expect(s.defaultRoleId.value).toBe('r-1')
    })

    it('角色列表为空时 defaultRoleId 保持空字符串', async () => {
      setupMocks({ roles: [] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.defaultRoleId.value).toBe('')
    })
  })

  describe('loadDetail', () => {
    it('加载失败时显示错误消息（Error）', async () => {
      mocks.fetchWorkspaceDetail.mockRejectedValue(new Error('网络错误'))
      mocks.fetchWorkspaceMembers.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchRoleList.mockResolvedValue([])
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
    })

    it('加载失败时显示通用消息（非 Error）', async () => {
      mocks.fetchWorkspaceDetail.mockRejectedValue('string err')
      mocks.fetchWorkspaceMembers.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchRoleList.mockResolvedValue([])
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载工作空间详情失败')
    })

    it('无论成功失败 infoLoading 最终为 false', async () => {
      mocks.fetchWorkspaceDetail.mockRejectedValue(new Error('fail'))
      mocks.fetchWorkspaceMembers.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchRoleList.mockResolvedValue([])
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.infoLoading.value).toBe(false)
    })
  })

  describe('loadMembers', () => {
    it('加载失败时显示错误消息（Error）', async () => {
      mocks.fetchWorkspaceMembers.mockRejectedValue(new Error('成员加载失败'))
      mocks.fetchWorkspaceDetail.mockResolvedValue(makeWorkspace())
      mocks.fetchRoleList.mockResolvedValue([])
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('成员加载失败')
    })

    it('加载失败时显示通用消息（非 Error）', async () => {
      mocks.fetchWorkspaceMembers.mockRejectedValue(42)
      mocks.fetchWorkspaceDetail.mockResolvedValue(makeWorkspace())
      mocks.fetchRoleList.mockResolvedValue([])
      init()
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载成员列表失败')
    })

    it('无论成功失败 membersLoading 最终为 false', async () => {
      mocks.fetchWorkspaceMembers.mockRejectedValue(new Error('fail'))
      mocks.fetchWorkspaceDetail.mockResolvedValue(makeWorkspace())
      mocks.fetchRoleList.mockResolvedValue([])
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.membersLoading.value).toBe(false)
    })

    it('手动调用 loadMembers 使用当前 memberQuery', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.memberQuery.pageNo = 2
      s.memberQuery.pageSize = 10
      await s.loadMembers()
      expect(mocks.fetchWorkspaceMembers).toHaveBeenCalledWith('ws-1', { pageNo: 2, pageSize: 10 })
    })
  })

  describe('saveInfo', () => {
    it('infoFormRef 为 null 时不执行', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.infoFormRef.value = undefined
      await s.saveInfo()
      expect(mocks.updateWorkspace).not.toHaveBeenCalled()
    })

    it('表单验证失败时不调用 updateWorkspace', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const mockValidate = vi.fn().mockRejectedValue(new Error('验证失败'))
      s.infoFormRef.value = { validate: mockValidate } as never
      await s.saveInfo()
      expect(mocks.updateWorkspace).not.toHaveBeenCalled()
    })

    it('验证通过后调用 updateWorkspace 并显示成功', async () => {
      const updated = makeWorkspace({ name: '新名称' })
      mocks.updateWorkspace.mockResolvedValue(updated)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.infoForm.name = '新名称'
      const mockValidate = vi.fn().mockResolvedValue(undefined)
      s.infoFormRef.value = { validate: mockValidate } as never
      await s.saveInfo()
      expect(mocks.updateWorkspace).toHaveBeenCalledWith('ws-1', {
        name: '新名称',
        description: '描述',
      })
      expect(s.detail.value).toEqual(updated)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
    })

    it('名称和描述会 trim 后提交', async () => {
      mocks.updateWorkspace.mockResolvedValue(makeWorkspace())
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.infoForm.name = '  新名称  '
      s.infoForm.description = '  新描述  '
      s.infoFormRef.value = { validate: vi.fn().mockResolvedValue(undefined) } as never
      await s.saveInfo()
      expect(mocks.updateWorkspace).toHaveBeenCalledWith('ws-1', {
        name: '新名称',
        description: '新描述',
      })
    })

    it('保存失败时显示错误（Error）', async () => {
      mocks.updateWorkspace.mockRejectedValue(new Error('保存失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.infoFormRef.value = { validate: vi.fn().mockResolvedValue(undefined) } as never
      await s.saveInfo()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('保存失败时显示通用消息（非 Error）', async () => {
      mocks.updateWorkspace.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.infoFormRef.value = { validate: vi.fn().mockResolvedValue(undefined) } as never
      await s.saveInfo()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('无论成功失败 infoSaving 最终为 false', async () => {
      mocks.updateWorkspace.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.infoFormRef.value = { validate: vi.fn().mockResolvedValue(undefined) } as never
      await s.saveInfo()
      expect(s.infoSaving.value).toBe(false)
    })
  })

  describe('handleRoleChange', () => {
    it('成功更新角色', async () => {
      mocks.updateWorkspaceMemberRole.mockResolvedValue(undefined)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const member = makeMember()
      await s.handleRoleChange(member, 'role-2')
      expect(mocks.updateWorkspaceMemberRole).toHaveBeenCalledWith('ws-1', 'u-1', 'role-2')
      expect(member.workspaceRole).toBe('role-2')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('角色已更新')
    })

    it('更新失败时显示错误（Error）并重新加载成员', async () => {
      mocks.updateWorkspaceMemberRole.mockRejectedValue(new Error('更新失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      const member = makeMember()
      await s.handleRoleChange(member, 'role-2')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新失败')
      expect(mocks.fetchWorkspaceMembers).toHaveBeenCalledTimes(2)
    })

    it('更新失败时显示通用消息（非 Error）', async () => {
      mocks.updateWorkspaceMemberRole.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRoleChange(makeMember(), 'role-2')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('更新角色失败')
    })
  })

  describe('handleRemoveMember', () => {
    it('用户取消时不执行移除', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRemoveMember(makeMember())
      expect(mocks.removeWorkspaceMember).not.toHaveBeenCalled()
    })

    it('确认后调用 removeWorkspaceMember 并刷新', async () => {
      mocks.removeWorkspaceMember.mockResolvedValue(undefined)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRemoveMember(makeMember())
      expect(mocks.removeWorkspaceMember).toHaveBeenCalledWith('ws-1', 'u-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已移除')
    })

    it('移除成功后调用 loadMembers 和 loadDetail', async () => {
      mocks.removeWorkspaceMember.mockResolvedValue(undefined)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRemoveMember(makeMember())
      expect(mocks.fetchWorkspaceMembers).toHaveBeenCalledTimes(2)
      expect(mocks.fetchWorkspaceDetail).toHaveBeenCalledTimes(2)
    })

    it('移除失败时显示错误（Error）', async () => {
      mocks.removeWorkspaceMember.mockRejectedValue(new Error('移除失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRemoveMember(makeMember())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('移除失败')
    })

    it('移除失败时显示通用消息（非 Error）', async () => {
      mocks.removeWorkspaceMember.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRemoveMember(makeMember())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('移除失败')
    })
  })

  describe('openAddDialog', () => {
    it('重置状态并打开弹窗', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1']
      s.userOptions.value = [makeUser('u-1', '张三')]
      s.pendingRoles['u-1'] = 'role-1'
      s.openAddDialog()
      expect(s.selectedUserIds.value).toEqual([])
      expect(s.userOptions.value).toEqual([])
      expect(s.pendingRoles).toEqual({})
      expect(s.addDialogVisible.value).toBe(true)
    })
  })

  describe('searchUsers', () => {
    it('关键词为空时保留已选用户', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.userOptions.value = [makeUser('u-1', '张三'), makeUser('u-2', '李四')]
      s.selectedUserIds.value = ['u-1']
      await s.searchUsers('')
      expect(s.userOptions.value).toEqual([makeUser('u-1', '张三')])
    })

    it('关键词非空时搜索并合并已选用户', async () => {
      setupMocks({ users: [makeUser('u-2', '李四'), makeUser('u-3', '王五')] })
      const s = init()
      await vi.dynamicImportSettled()
      s.userOptions.value = [makeUser('u-1', '张三')]
      s.selectedUserIds.value = ['u-1']
      await s.searchUsers('李')
      expect(mocks.fetchSimpleUserList).toHaveBeenCalledWith('李')
      expect(s.userOptions.value.map((u) => u.id)).toEqual(['u-1', 'u-2', 'u-3'])
    })

    it('搜索结果与已选用户去重', async () => {
      mocks.fetchSimpleUserList.mockResolvedValue([makeUser('u-1', '张三')])
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.userOptions.value = [makeUser('u-1', '张三')]
      s.selectedUserIds.value = ['u-1']
      await s.searchUsers('张')
      expect(s.userOptions.value).toEqual([makeUser('u-1', '张三')])
    })

    it('搜索失败时清空 userOptions', async () => {
      mocks.fetchSimpleUserList.mockRejectedValue(new Error('网络错误'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.userOptions.value = [makeUser('u-1', '张三')]
      await s.searchUsers('张')
      expect(s.userOptions.value).toEqual([])
    })

    it('无论成功失败 userSearchLoading 最终为 false', async () => {
      mocks.fetchSimpleUserList.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.searchUsers('张')
      expect(s.userSearchLoading.value).toBe(false)
    })
  })

  describe('handleUserSelectChange', () => {
    it('新增选中时设置默认角色', async () => {
      setupMocks({ roles: [{ id: 'r-1', name: '管理员' }] })
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.defaultRoleId.value).toBe('r-1')
      s.handleUserSelectChange(['u-1', 'u-2'])
      expect(s.pendingRoles['u-1']).toBe('r-1')
      expect(s.pendingRoles['u-2']).toBe('r-1')
    })

    it('取消选中时移除 pendingRoles', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.handleUserSelectChange(['u-1', 'u-2'])
      s.handleUserSelectChange(['u-1'])
      expect(s.pendingRoles['u-1']).toBe('role-1')
      expect(s.pendingRoles['u-2']).toBeUndefined()
    })

    it('已有 pendingRole 的用户不会被覆盖', async () => {
      setupMocks({ roles: [{ id: 'r-1', name: '管理员' }, { id: 'r-2', name: '成员' }] })
      const s = init()
      await vi.dynamicImportSettled()
      s.handleUserSelectChange(['u-1'])
      s.pendingRoles['u-1'] = 'r-2'
      s.handleUserSelectChange(['u-1', 'u-2'])
      expect(s.pendingRoles['u-1']).toBe('r-2')
      expect(s.pendingRoles['u-2']).toBe('r-1')
    })
  })

  describe('selectedUsers', () => {
    it('映射已选 ID 为包含 name 的对象', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.userOptions.value = [makeUser('u-1', '张三'), makeUser('u-2', '李四')]
      s.selectedUserIds.value = ['u-1', 'u-2']
      expect(s.selectedUsers.value).toEqual([
        { id: 'u-1', name: '张三' },
        { id: 'u-2', name: '李四' },
      ])
    })

    it('未在 userOptions 中的 ID 使用 ID 作为 name', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-missing']
      expect(s.selectedUsers.value).toEqual([{ id: 'u-missing', name: 'u-missing' }])
    })

    it('空选择返回空数组', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      expect(s.selectedUsers.value).toEqual([])
    })
  })

  describe('submitAddMembers', () => {
    it('未选择用户时显示警告', async () => {
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.submitAddMembers()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少选择一个用户')
      expect(mocks.addWorkspaceMembers).not.toHaveBeenCalled()
    })

    it('选择用户后调用 addWorkspaceMembers', async () => {
      mocks.addWorkspaceMembers.mockResolvedValue(undefined)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1', 'u-2']
      s.pendingRoles['u-1'] = 'r-1'
      await s.submitAddMembers()
      expect(mocks.addWorkspaceMembers).toHaveBeenCalledWith('ws-1', [
        { userId: 'u-1', workspaceRole: 'r-1' },
        { userId: 'u-2', workspaceRole: 'role-1' },
      ])
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('成员已添加')
      expect(s.addDialogVisible.value).toBe(false)
    })

    it('添加成功后刷新成员列表和详情', async () => {
      mocks.addWorkspaceMembers.mockResolvedValue(undefined)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1']
      await s.submitAddMembers()
      expect(mocks.fetchWorkspaceMembers).toHaveBeenCalledTimes(2)
      expect(mocks.fetchWorkspaceDetail).toHaveBeenCalledTimes(2)
    })

    it('添加失败时显示错误（Error）', async () => {
      mocks.addWorkspaceMembers.mockRejectedValue(new Error('添加失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1']
      await s.submitAddMembers()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('添加失败')
    })

    it('添加失败时显示通用消息（非 Error）', async () => {
      mocks.addWorkspaceMembers.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1']
      await s.submitAddMembers()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('添加成员失败')
    })

    it('无论成功失败 addSubmitting 最终为 false', async () => {
      mocks.addWorkspaceMembers.mockRejectedValue(new Error('fail'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1']
      await s.submitAddMembers()
      expect(s.addSubmitting.value).toBe(false)
    })

    it('无 pendingRole 时使用 defaultRoleId', async () => {
      mocks.addWorkspaceMembers.mockResolvedValue(undefined)
      setupMocks({ roles: [{ id: 'r-default', name: '默认' }] })
      const s = init()
      await vi.dynamicImportSettled()
      s.selectedUserIds.value = ['u-1']
      await s.submitAddMembers()
      expect(mocks.addWorkspaceMembers).toHaveBeenCalledWith('ws-1', [
        { userId: 'u-1', workspaceRole: 'r-default' },
      ])
    })
  })

  describe('handleDissolve', () => {
    it('detail 为 null 时不执行', async () => {
      mocks.fetchWorkspaceDetail.mockRejectedValue(new Error('fail'))
      mocks.fetchWorkspaceMembers.mockResolvedValue({ list: [], total: 0 })
      mocks.fetchRoleList.mockResolvedValue([])
      const s = init()
      await vi.dynamicImportSettled()
      s.detail.value = null
      await s.handleDissolve()
      expect(mocks.ElMessageBox.prompt).not.toHaveBeenCalled()
    })

    it('有项目时不再拦截，归档不校验项目数', async () => {
      mocks.dissolveWorkspace.mockResolvedValue(undefined)
      setupMocks({ workspace: makeWorkspace({ projectCount: 3 }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleDissolve()
      expect(mocks.ElMessageBox.prompt).toHaveBeenCalled()
      expect(mocks.dissolveWorkspace).toHaveBeenCalledWith('ws-1')
    })

    it('用户取消时不执行解散', async () => {
      mocks.ElMessageBox.prompt.mockRejectedValue('cancel')
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleDissolve()
      expect(mocks.dissolveWorkspace).not.toHaveBeenCalled()
    })

    it('确认后调用 dissolveWorkspace 并跳转', async () => {
      mocks.dissolveWorkspace.mockResolvedValue(undefined)
      const mockPush = vi.fn()
      mocks.useRouter.mockReturnValue({ push: mockPush })
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleDissolve()
      expect(mocks.dissolveWorkspace).toHaveBeenCalledWith('ws-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('空间已归档')
      expect(mockPush).toHaveBeenCalledWith('/admin/workspaces')
    })

    it('归档失败时显示错误（Error）', async () => {
      mocks.dissolveWorkspace.mockRejectedValue(new Error('归档失败'))
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleDissolve()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('归档失败')
    })

    it('归档失败时显示通用消息（非 Error）', async () => {
      mocks.dissolveWorkspace.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleDissolve()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('归档失败')
    })

    it('ElMessageBox.prompt 的 inputValidator 校验名称匹配', async () => {
      setupMocks({ workspace: makeWorkspace({ name: '测试空间' }) })
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleDissolve()
      const callArgs = mocks.ElMessageBox.prompt.mock.calls[0]
      const validator = callArgs[2].inputValidator
      expect(validator('测试空间')).toBe(true)
      expect(validator('错误名称')).toBe('名称不匹配')
    })
  })

  describe('handleRestore', () => {
    it('成功后提示并刷新详情', async () => {
      mocks.restoreWorkspace.mockResolvedValue(undefined)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRestore()
      expect(mocks.restoreWorkspace).toHaveBeenCalledWith('ws-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('空间已重新启用')
      expect(mocks.fetchWorkspaceDetail).toHaveBeenCalledTimes(2)
    })

    it('失败时显示通用消息（非 Error）', async () => {
      mocks.restoreWorkspace.mockRejectedValue(42)
      setupMocks()
      const s = init()
      await vi.dynamicImportSettled()
      await s.handleRestore()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('重新启用失败')
    })
  })

  describe('workspaceId 从路由获取', () => {
    it('使用 route.params.id 作为 workspaceId', async () => {
      mocks.useRoute.mockReturnValue({ params: { id: 'ws-custom' } })
      setupMocks()
      init()
      await vi.dynamicImportSettled()
      expect(mocks.fetchWorkspaceDetail).toHaveBeenCalledWith('ws-custom')
      expect(mocks.fetchWorkspaceMembers).toHaveBeenCalledWith('ws-custom', { pageNo: 1, pageSize: 20 })
    })
  })
})
