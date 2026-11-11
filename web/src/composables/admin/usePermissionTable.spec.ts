import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { PermissionTopModule, RoleDetail } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchPermissionTable: vi.fn(),
  fetchRoleDetail: vi.fn(),
  updateRolePermissions: vi.fn(),
  ElMessage: { error: vi.fn(), success: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/admin', () => ({
  fetchPermissionTable: mocks.fetchPermissionTable,
  fetchRoleDetail: mocks.fetchRoleDetail,
  updateRolePermissions: mocks.updateRolePermissions,
}))

import { usePermissionTable } from './usePermissionTable'

const permissionTable: PermissionTopModule[] = [
  {
    topModule: '系统管理',
    modules: [
      {
        module: '用户',
        permissions: [
          { code: 'user:list', name: '查看用户' },
          { code: 'user:create', name: '新增用户' },
        ],
      },
      { module: '角色', permissions: [{ code: 'role:list', name: '查看角色' }] },
    ],
  },
]

const allCodes = ['user:list', 'user:create', 'role:list']

function makeDetail(permissions: string[]): RoleDetail {
  return {
    id: 'role-1',
    name: '测试角色',
    type: 'system',
    isSystem: true,
    permissions,
    userCount: 0,
  }
}

async function createSut(options?: { roleId?: string; isSystem?: boolean }) {
  const roleId = ref(options?.roleId ?? 'role-1')
  const sut = usePermissionTable(
    () => roleId.value,
    () => 'system',
    () => options?.isSystem ?? true,
  )
  await vi.dynamicImportSettled()
  return { sut, roleId }
}

describe('usePermissionTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchPermissionTable.mockResolvedValue(permissionTable)
    mocks.fetchRoleDetail.mockResolvedValue(makeDetail(['user:list']))
    mocks.updateRolePermissions.mockResolvedValue(makeDetail(['user:list', 'user:create']))
  })

  it('预置角色默认锁定，解锁后放开且全选集合恢复全量', async () => {
    const { sut } = await createSut({ isSystem: true })

    expect(sut.unlocked.value).toBe(false)
    expect(sut.isLocked('user:list')).toBe(true)
    expect(sut.allVisibleCodes.value).toEqual([])

    sut.setUnlocked(true)

    expect(sut.unlocked.value).toBe(true)
    expect(sut.isLocked('user:list')).toBe(false)
    expect(sut.allVisibleCodes.value).toEqual(allCodes)
  })

  it('取消勾选时丢弃未保存修改并恢复到上次保存的快照', async () => {
    const { sut } = await createSut({ isSystem: true })
    sut.setUnlocked(true)
    sut.checkedCodes.value = ['user:list', 'user:create']
    expect(sut.dirty.value).toBe(true)

    sut.setUnlocked(false)

    expect(sut.unlocked.value).toBe(false)
    expect(sut.dirty.value).toBe(false)
    expect(sut.checkedCodes.value).toEqual(['user:list'])
    expect(sut.isLocked('user:list')).toBe(true)
  })

  it('非预置角色始终可编辑，不受解锁态影响', async () => {
    const { sut } = await createSut({ isSystem: false })

    expect(sut.isLocked('user:list')).toBe(false)
    expect(sut.allVisibleCodes.value).toEqual(allCodes)

    sut.setUnlocked(false)
    expect(sut.isLocked('user:create')).toBe(false)
  })

  it('切换角色时重置解锁态并重新加载权限', async () => {
    const { sut, roleId } = await createSut({ isSystem: true })
    sut.setUnlocked(true)
    expect(sut.unlocked.value).toBe(true)

    roleId.value = 'role-2'
    await vi.dynamicImportSettled()

    expect(sut.unlocked.value).toBe(false)
    expect(sut.isLocked('user:list')).toBe(true)
    expect(mocks.fetchRoleDetail).toHaveBeenCalledWith('role-2')
  })

  it('保存成功后保持解锁态且 dirty 清除', async () => {
    const { sut } = await createSut({ isSystem: true })
    sut.setUnlocked(true)
    sut.checkedCodes.value = ['user:list', 'user:create']

    await sut.handleSave()

    expect(sut.unlocked.value).toBe(true)
    expect(sut.dirty.value).toBe(false)
    expect(sut.savedCodes.value).toEqual(['user:list', 'user:create'])
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('权限已保存')
  })
})
