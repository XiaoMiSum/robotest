import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FormInstance } from 'element-plus'
import type { UserSimple } from '@/types'

const mocks = vi.hoisted(() => ({
  createWorkspace: vi.fn(),
  fetchSimpleUserList: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
  authStore: {
    user: {
      id: 'current-user',
      username: '当前用户',
      email: 'current@example.com',
    } as { id: string; username: string; email: string } | null,
  },
}))

vi.mock('@/services/admin', () => ({
  createWorkspace: mocks.createWorkspace,
  fetchSimpleUserList: mocks.fetchSimpleUserList,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useWorkspaceCreate } from './useWorkspaceCreate'

function formInstance(): FormInstance {
  return {
    validate: vi.fn().mockResolvedValue(undefined),
    clearValidate: vi.fn(),
  } as unknown as FormInstance
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.authStore.user = {
    id: 'current-user',
    username: '当前用户',
    email: 'current@example.com',
  }
  mocks.createWorkspace.mockResolvedValue('workspace-new')
  mocks.fetchSimpleUserList.mockResolvedValue([] as UserSimple[])
})

describe('useWorkspaceCreate', () => {
  it('打开表单时默认当前用户为管理员，并支持远程搜索', async () => {
    const sut = useWorkspaceCreate()
    sut.formRef.value = formInstance()
    sut.reset()

    expect(sut.form.adminUserId).toBe('current-user')
    expect(sut.adminOptions.value[0]).toEqual({ id: 'current-user', name: '当前用户' })

    mocks.fetchSimpleUserList.mockResolvedValue([{ id: 'other-user', name: '其他用户' }])
    await sut.searchAdmins('其他')

    expect(mocks.fetchSimpleUserList).toHaveBeenCalledWith('其他')
    expect(sut.adminOptions.value.map((user) => user.id)).toEqual(['current-user', 'other-user'])
  })

  it('提交时保留表单字段、阻止重复提交并返回新空间 ID', async () => {
    let resolveCreate!: (value: string) => void
    const deferred = new Promise<string>((resolve) => {
      resolveCreate = resolve
    })
    mocks.createWorkspace.mockReturnValue(deferred)
    const sut = useWorkspaceCreate()
    sut.formRef.value = formInstance()
    sut.form.name = '  质量中台  '
    sut.form.description = '  描述  '
    sut.form.adminUserId = 'current-user'

    const first = sut.submit()
    const second = sut.submit()
    await Promise.resolve()
    expect(mocks.createWorkspace).toHaveBeenCalledTimes(1)
    expect(mocks.createWorkspace).toHaveBeenCalledWith({
      name: '质量中台',
      description: '描述',
      adminUserId: 'current-user',
    })

    resolveCreate('workspace-new')
    await expect(first).resolves.toBe('workspace-new')
    await expect(second).resolves.toBeNull()
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('工作空间已创建')
    expect(sut.form.name).toBe('  质量中台  ')
  })

  it('创建失败时保留输入并显示错误', async () => {
    mocks.createWorkspace.mockRejectedValue(new Error('名称已存在'))
    const sut = useWorkspaceCreate()
    sut.formRef.value = formInstance()
    sut.form.name = '质量中台'
    sut.form.adminUserId = 'current-user'

    await expect(sut.submit()).resolves.toBeNull()

    expect(sut.form.name).toBe('质量中台')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('名称已存在')
  })
})
