import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { WorkspaceMember } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchMembers: vi.fn(),
  fetchWorkspaceRoles: vi.fn(),
  removeMember: vi.fn(),
  updateMemberRole: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
  authStore: {
    user: { id: 'user-me', username: 'qa-admin' } as { id: string; username: string } | null,
    setActiveWorkspace: vi.fn(),
  },
  router: { push: vi.fn() },
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
  fetchWorkspaceRoles: mocks.fetchWorkspaceRoles,
  removeMember: mocks.removeMember,
  updateMemberRole: mocks.updateMemberRole,
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('vue-router', () => ({ useRouter: () => mocks.router }))
vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useMemberList } from './useMemberList'

function makeMember(overrides: Partial<WorkspaceMember> = {}): WorkspaceMember {
  return {
    userId: 'user-1',
    username: 'zhangsan',
    name: '张三',
    email: 'zhangsan@example.com',
    workspaceRole: 'role-admin',
    joinedAt: '2026-01-15T02:00:00',
    ...overrides,
  }
}

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolveFn, rejectFn) => {
    resolve = resolveFn
    reject = rejectFn
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.authStore.user = { id: 'user-me', username: 'qa-admin' }
  mocks.router.push.mockResolvedValue(undefined)
  mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  mocks.fetchWorkspaceRoles.mockResolvedValue([])
  mocks.fetchMembers.mockResolvedValue({ list: [], total: 0 })
})

describe('useMemberList', () => {
  it('角色选项只保留非分组角色', async () => {
    mocks.fetchWorkspaceRoles.mockResolvedValue([
      { id: 'role-admin', name: '管理员', isGroup: false },
      { id: 'role-group', name: '预置分组', isGroup: true },
    ])
    const sut = useMemberList()

    await sut.loadRoleOptions()

    expect(sut.roleOptions.value).toEqual([{ value: 'role-admin', label: '管理员' }])
  })

  it('角色选项失败时回落为空列表', async () => {
    mocks.fetchWorkspaceRoles.mockRejectedValue(new Error('角色接口失败'))
    const sut = useMemberList()

    await sut.loadRoleOptions()

    expect(sut.roleOptions.value).toEqual([])
  })

  it('加载成员时透传去空格后的筛选条件', async () => {
    mocks.fetchMembers.mockResolvedValue({ list: [makeMember()], total: 24 })
    const sut = useMemberList()
    sut.memberQuery.keyword = '  张三  '
    sut.memberQuery.workspaceRole = 'role-admin'

    await sut.loadMembers()

    expect(mocks.fetchMembers).toHaveBeenCalledWith({
      keyword: '张三',
      workspaceRole: 'role-admin',
      pageNo: 1,
      pageSize: 20,
    })
    expect(sut.members.value).toHaveLength(1)
    expect(sut.memberTotal.value).toBe(24)
    expect(sut.membersLoading.value).toBe(false)
    expect(sut.membersLoadError.value).toBe(false)
  })

  it('加载失败时按后端消息提示并结束 loading', async () => {
    mocks.fetchMembers.mockRejectedValue(new Error('成员接口失败'))
    const sut = useMemberList()

    await sut.loadMembers()

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('成员接口失败')
    expect(sut.membersLoading.value).toBe(false)
    // 空表无法自证是「没数据」还是「没加载到」，交给错误空态展示
    expect(sut.membersLoadError.value).toBe(true)
  })

  it('加载失败后重试成功会清掉错误标记', async () => {
    mocks.fetchMembers.mockRejectedValueOnce(new Error('成员接口失败'))
    const sut = useMemberList()

    await sut.loadMembers()
    await sut.loadMembers()

    expect(sut.membersLoadError.value).toBe(false)
  })

  it('忽略晚到的旧成员响应', async () => {
    const first = deferred<{ list: WorkspaceMember[]; total: number }>()
    const second = deferred<{ list: WorkspaceMember[]; total: number }>()
    mocks.fetchMembers.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const sut = useMemberList()

    const firstLoad = sut.loadMembers()
    const secondLoad = sut.loadMembers()

    second.resolve({ list: [makeMember({ userId: 'new' })], total: 1 })
    await secondLoad
    first.resolve({ list: [makeMember({ userId: 'old' })], total: 9 })
    await firstLoad

    expect(sut.members.value[0]?.userId).toBe('new')
    expect(sut.memberTotal.value).toBe(1)
    expect(sut.membersLoading.value).toBe(false)
  })

  it('关键词输入防抖查询，清空关键词立即查询', async () => {
    vi.useFakeTimers()
    try {
      const sut = useMemberList()

      sut.handleMemberSearchInput('张')
      await vi.advanceTimersByTimeAsync(299)
      expect(mocks.fetchMembers).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      expect(mocks.fetchMembers).toHaveBeenCalledTimes(1)

      sut.handleMemberSearchClear()
      expect(mocks.fetchMembers).toHaveBeenCalledTimes(2)
      expect(sut.memberQuery.keyword).toBe('')
      expect(sut.memberQuery.pageNo).toBe(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('防抖未触发前的新输入会取消上一次定时器', async () => {
    vi.useFakeTimers()
    try {
      const sut = useMemberList()

      sut.handleMemberSearchInput('张')
      await vi.advanceTimersByTimeAsync(200)
      sut.handleMemberSearchInput('张三')
      await vi.advanceTimersByTimeAsync(300)

      expect(mocks.fetchMembers).toHaveBeenCalledTimes(1)
      const firstCall = mocks.fetchMembers.mock.calls[0]?.[0] as { keyword?: string } | undefined
      expect(firstCall?.keyword).toBe('张三')
    } finally {
      vi.useRealTimers()
    }
  })

  it('角色筛选与翻页都会重置或传递页码', async () => {
    const sut = useMemberList()

    sut.handleMemberPageChange(3)
    expect(mocks.fetchMembers).toHaveBeenLastCalledWith({
      keyword: undefined,
      workspaceRole: undefined,
      pageNo: 3,
      pageSize: 20,
    })

    sut.handleRoleFilterChange('role-member')
    expect(sut.memberQuery.pageNo).toBe(1)
    await vi.waitFor(() => expect(mocks.fetchMembers).toHaveBeenCalledTimes(2))
    expect(mocks.fetchMembers).toHaveBeenLastCalledWith({
      keyword: undefined,
      workspaceRole: 'role-member',
      pageNo: 1,
      pageSize: 20,
    })
  })

  it('角色编辑成功就地更新，失败回滚并重新加载', async () => {
    const member = makeMember()
    const sut = useMemberList()

    sut.startEditRole('user-1')
    expect(sut.editingUserId.value).toBe('user-1')
    sut.handleRoleVisibleChange(false)
    expect(sut.editingUserId.value).toBe('')

    sut.startEditRole('user-1')
    mocks.updateMemberRole.mockResolvedValue(undefined)
    await sut.submitRoleChange(member, 'role-member')
    expect(member.workspaceRole).toBe('role-member')
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('角色已更新')
    expect(sut.editingUserId.value).toBe('')

    mocks.updateMemberRole.mockRejectedValue(new Error('无权限'))
    await sut.submitRoleChange(member, 'role-admin')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('无权限')
    await vi.waitFor(() => expect(mocks.fetchMembers).toHaveBeenCalled())
  })

  it('取消移除确认时不发起请求', async () => {
    mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
    const sut = useMemberList()

    await sut.handleRemoveMember(makeMember())

    expect(mocks.removeMember).not.toHaveBeenCalled()
  })

  it('移除他人后刷新列表，移除自己后清空空间并跳转', async () => {
    const sut = useMemberList()

    await sut.handleRemoveMember(makeMember())
    expect(mocks.removeMember).toHaveBeenCalledWith('user-1')
    await vi.waitFor(() => expect(mocks.fetchMembers).toHaveBeenCalled())

    await sut.handleRemoveMember(makeMember({ userId: 'user-me' }))
    expect(mocks.authStore.setActiveWorkspace).toHaveBeenCalledWith(null)
    expect(mocks.router.push).toHaveBeenCalledWith('/workspaces')
  })

  it('移除失败按后端消息提示', async () => {
    mocks.removeMember.mockRejectedValue(new Error('最后一位管理员不可移除'))
    const sut = useMemberList()

    await sut.handleRemoveMember(makeMember())

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('最后一位管理员不可移除')
  })

  it('dispose 后在途响应不再写回状态', async () => {
    const pending = deferred<{ list: WorkspaceMember[]; total: number }>()
    mocks.fetchMembers.mockReturnValue(pending.promise)
    const sut = useMemberList()

    const load = sut.loadMembers()
    sut.dispose()
    pending.resolve({ list: [makeMember()], total: 1 })
    await load

    expect(sut.members.value).toEqual([])
    expect(sut.memberTotal.value).toBe(0)
    expect(sut.membersLoading.value).toBe(false)
  })
})
