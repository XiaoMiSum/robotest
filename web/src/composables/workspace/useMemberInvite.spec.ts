import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UserSimple } from '@/types'

const mocks = vi.hoisted(() => ({
  addMembers: vi.fn(),
  fetchMemberCandidates: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/workspace', () => ({
  addMembers: mocks.addMembers,
  fetchMemberCandidates: mocks.fetchMemberCandidates,
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))

import { useMemberInvite } from './useMemberInvite'

function makeUsers(names: string[]): UserSimple[] {
  return names.map((name, index) => ({ id: `user-${index}`, name }))
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.fetchMemberCandidates.mockResolvedValue(makeUsers(['张三']))
  mocks.addMembers.mockResolvedValue({ successCount: 2, skippedUserIds: [] })
})

describe('useMemberInvite', () => {
  it('打开弹窗会清空上一次的候选与已选用户', () => {
    const sut = useMemberInvite(() => {})
    sut.selectedUserIds.value = ['user-1']
    sut.userOptions.value = makeUsers(['李四'])

    sut.openAddDialog()

    expect(sut.addDialogVisible.value).toBe(true)
    expect(sut.selectedUserIds.value).toEqual([])
    expect(sut.userOptions.value).toEqual([])
  })

  it('选择结果统一收敛为字符串数组', () => {
    const sut = useMemberInvite(() => {})

    sut.handleSelectedUsersChange(['user-1', 'user-2'])
    expect(sut.selectedUserIds.value).toEqual(['user-1', 'user-2'])

    sut.handleSelectedUsersChange(null)
    expect(sut.selectedUserIds.value).toEqual([])
  })

  it('空关键字立即清空候选并结束加载态', () => {
    const sut = useMemberInvite(() => {})

    sut.searchUsers('   ')

    expect(sut.userOptions.value).toEqual([])
    expect(sut.userSearchLoading.value).toBe(false)
  })

  it('用户搜索按 300ms 防抖并只采用最后一次结果', async () => {
    vi.useFakeTimers()
    try {
      const sut = useMemberInvite(() => {})

      sut.searchUsers('张')
      await vi.advanceTimersByTimeAsync(299)
      expect(mocks.fetchMemberCandidates).not.toHaveBeenCalled()
      expect(sut.userSearchLoading.value).toBe(true)

      sut.searchUsers('张三')
      await vi.advanceTimersByTimeAsync(300)

      expect(mocks.fetchMemberCandidates).toHaveBeenCalledTimes(1)
      expect(mocks.fetchMemberCandidates).toHaveBeenCalledWith('张三')
      expect(sut.userOptions.value.map((user) => user.name)).toEqual(['张三'])
      expect(sut.userSearchLoading.value).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('搜索失败时清空候选并结束加载态', async () => {
    vi.useFakeTimers()
    try {
      mocks.fetchMemberCandidates.mockRejectedValue(new Error('搜索失败'))
      const sut = useMemberInvite(() => {})

      sut.searchUsers('张三')
      await vi.advanceTimersByTimeAsync(300)

      expect(sut.userOptions.value).toEqual([])
      expect(sut.userSearchLoading.value).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('未选择用户时不提交并提示', async () => {
    const onAdded = vi.fn()
    const sut = useMemberInvite(onAdded)

    await sut.submitAddMembers()

    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少选择一个用户')
    expect(mocks.addMembers).not.toHaveBeenCalled()
    expect(onAdded).not.toHaveBeenCalled()
  })

  it('提交成功按成员角色添加并通知刷新', async () => {
    const onAdded = vi.fn()
    const sut = useMemberInvite(onAdded)
    sut.selectedUserIds.value = ['user-1', 'user-2']

    await sut.submitAddMembers()

    expect(mocks.addMembers).toHaveBeenCalledWith([
      { userId: 'user-1', workspaceRole: expect.any(String) },
      { userId: 'user-2', workspaceRole: expect.any(String) },
    ])
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('成功添加 2 人')
    expect(sut.addDialogVisible.value).toBe(false)
    expect(onAdded).toHaveBeenCalledTimes(1)
    expect(sut.addSubmitting.value).toBe(false)
  })

  it('存在跳过用户时提示跳过数量', async () => {
    mocks.addMembers.mockResolvedValue({ successCount: 1, skippedUserIds: ['user-2'] })
    const sut = useMemberInvite(() => {})
    sut.selectedUserIds.value = ['user-1', 'user-2']

    await sut.submitAddMembers()

    expect(mocks.ElMessage.success).toHaveBeenCalledWith('成功添加 1 人，1 人已在空间中跳过')
  })

  it('提交失败按后端消息提示且保持弹窗打开', async () => {
    mocks.addMembers.mockRejectedValue(new Error('用户已在空间中'))
    const sut = useMemberInvite(() => {})
    sut.openAddDialog()
    sut.selectedUserIds.value = ['user-1']

    await sut.submitAddMembers()

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('用户已在空间中')
    expect(sut.addDialogVisible.value).toBe(true)
    expect(sut.addSubmitting.value).toBe(false)
  })

  it('dispose 取消待执行的搜索并丢弃在途结果', async () => {
    vi.useFakeTimers()
    try {
      const sut = useMemberInvite(() => {})

      sut.searchUsers('张三')
      sut.dispose()
      await vi.advanceTimersByTimeAsync(300)

      expect(mocks.fetchMemberCandidates).not.toHaveBeenCalled()
      expect(sut.userSearchLoading.value).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })
})
