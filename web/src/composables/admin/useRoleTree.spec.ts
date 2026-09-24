import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RoleTreeNode } from '@/types'

const mocks = vi.hoisted(() => ({
  onMounted: vi.fn(),
  createRole: vi.fn(),
  deleteRole: vi.fn(),
  fetchRoleList: vi.fn(),
  updateRole: vi.fn(),
  ElMessage: { error: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: mocks.onMounted,
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/admin', () => ({
  createRole: mocks.createRole,
  deleteRole: mocks.deleteRole,
  fetchRoleList: mocks.fetchRoleList,
  updateRole: mocks.updateRole,
}))

import { isRoleActionVisible, useRoleTree } from './useRoleTree'

const group: RoleTreeNode = {
  id: 'type-system',
  name: '系统角色',
  type: 'system',
  isGroup: true,
}

const role: RoleTreeNode = {
  id: 'role-1',
  name: '测试角色',
  type: 'system',
  isSystem: false,
}

describe('isRoleActionVisible', () => {
  it('父节点操作区常显，叶节点仅在当前选中时显示', () => {
    expect(isRoleActionVisible(group, 'role-1')).toBe(true)
    expect(isRoleActionVisible(role, 'role-1')).toBe(true)
    expect(isRoleActionVisible(role, 'type-system')).toBe(false)
  })
})

describe('useRoleTree', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchRoleList.mockResolvedValue([])
    mocks.createRole.mockResolvedValue('role-new')
    mocks.updateRole.mockResolvedValue(undefined)
    mocks.deleteRole.mockResolvedValue(undefined)
    mocks.ElMessageBox.prompt.mockResolvedValue({ value: '新角色' })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  })

  it('按角色类型构建树并维护加载状态', async () => {
    mocks.fetchRoleList.mockResolvedValue([
      role,
      { id: 'role-2', name: '空间成员', type: 'workspace', isSystem: true },
    ])
    const sut = useRoleTree(vi.fn(), vi.fn())

    await sut.load()

    expect(sut.loading.value).toBe(false)
    expect(sut.treeData.value).toEqual([
      { ...group, children: [role] },
      {
        id: 'type-workspace',
        name: '工作空间角色',
        type: 'workspace',
        isGroup: true,
        children: [{ id: 'role-2', name: '空间成员', type: 'workspace', isSystem: true }],
      },
    ])
  })

  it('加载失败时结束 loading 并提示错误', async () => {
    mocks.fetchRoleList.mockRejectedValue(new Error('network error'))
    const sut = useRoleTree(vi.fn(), vi.fn())

    await sut.load()

    expect(sut.loading.value).toBe(false)
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('network error')
  })

  it('点击分组节点后取消子节点选中且不加载分组详情', () => {
    const onSelect = vi.fn()
    const onCleared = vi.fn()
    const sut = useRoleTree(onSelect, onCleared)

    sut.handleNodeClick(role)
    expect(sut.currentId.value).toBe('role-1')
    expect(onSelect).toHaveBeenCalledWith({
      id: 'role-1',
      isSystem: false,
      type: 'system',
      name: '测试角色',
    })

    sut.handleNodeClick(group)
    expect(sut.currentId.value).toBe('type-system')
    expect(onSelect).toHaveBeenCalledTimes(1)
    expect(onCleared).not.toHaveBeenCalled()
  })

  it('新增成功后刷新树并选中新角色', async () => {
    const onSelect = vi.fn()
    const sut = useRoleTree(onSelect, vi.fn())

    await sut.handleAdd(group)

    expect(mocks.createRole).toHaveBeenCalledWith({ name: '新角色', type: 'system' })
    expect(mocks.fetchRoleList).toHaveBeenCalledOnce()
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('角色已创建')
    expect(sut.currentId.value).toBe('role-new')
    expect(onSelect).toHaveBeenCalledWith({
      id: 'role-new',
      isSystem: false,
      type: 'system',
      name: '新角色',
    })
  })

  it('取消新增时不调用创建接口', async () => {
    mocks.ElMessageBox.prompt.mockRejectedValue('cancel')
    const sut = useRoleTree(vi.fn(), vi.fn())

    await sut.handleAdd(group)

    expect(mocks.createRole).not.toHaveBeenCalled()
    expect(mocks.ElMessage.error).not.toHaveBeenCalled()
  })

  it('新增失败时提示错误', async () => {
    mocks.createRole.mockRejectedValue(new Error('create failed'))
    const sut = useRoleTree(vi.fn(), vi.fn())

    await sut.handleAdd(group)

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('create failed')
  })

  it('重命名时去除首尾空格并刷新树', async () => {
    mocks.ElMessageBox.prompt.mockResolvedValue({ value: '  新名称  ' })
    mocks.fetchRoleList.mockResolvedValue([{ ...role, name: '新名称' }])
    const sut = useRoleTree(vi.fn(), vi.fn())

    await sut.handleRename(role)

    expect(mocks.updateRole).toHaveBeenCalledWith('role-1', { name: '新名称' })
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('已重命名')
    expect(mocks.fetchRoleList).toHaveBeenCalledOnce()
  })

  it('删除当前角色后清空选中状态与详情', async () => {
    const onCleared = vi.fn()
    const sut = useRoleTree(vi.fn(), onCleared)
    sut.handleNodeClick(role)

    await sut.handleDelete(role)

    expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith('确定要删除角色「测试角色」吗？', '确认删除', {
      type: 'warning',
    })
    expect(mocks.deleteRole).toHaveBeenCalledWith('role-1')
    expect(sut.currentId.value).toBe('')
    expect(onCleared).toHaveBeenCalledOnce()
  })

  it('取消删除时不调用删除接口', async () => {
    mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
    const sut = useRoleTree(vi.fn(), vi.fn())

    await sut.handleDelete(role)

    expect(mocks.deleteRole).not.toHaveBeenCalled()
  })

  it('删除失败时保留选中状态并提示错误', async () => {
    mocks.deleteRole.mockRejectedValue(new Error('delete failed'))
    const onCleared = vi.fn()
    const sut = useRoleTree(vi.fn(), onCleared)
    sut.handleNodeClick(role)

    await sut.handleDelete(role)

    expect(sut.currentId.value).toBe('role-1')
    expect(onCleared).not.toHaveBeenCalled()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('delete failed')
  })
})
