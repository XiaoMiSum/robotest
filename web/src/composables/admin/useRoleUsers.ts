import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addRoleUsers,
  addWorkspaceRoleUsers,
  fetchRoleWorkspaceUsers,
  fetchUsers,
  removeRoleUser,
  removeWorkspaceRoleUser,
} from '@/services/admin'
import type { AdminUser, RoleWorkspaceUser } from '@/types'

export function useRoleUsers(getRoleId: () => string, getRoleType: () => string) {
  const loading = ref(false)
  const users = ref<AdminUser[]>([])
  const workspaceUsers = ref<RoleWorkspaceUser[]>([])
  const total = ref(0)
  const query = reactive({ pageNo: 1, pageSize: 20 })
  const pickerVisible = ref(false)

  // 多空间移除弹窗
  const wsRemoveVisible = ref(false)
  const wsRemoveTarget = ref<RoleWorkspaceUser | null>(null)
  const wsRemoveSelected = ref<string[]>([])

  const isWorkspaceRole = () => getRoleType() === 'workspace'

  async function load() {
    if (!getRoleId()) return
    loading.value = true
    try {
      if (isWorkspaceRole()) {
        workspaceUsers.value = await fetchRoleWorkspaceUsers(getRoleId())
      } else {
        const page = await fetchUsers({
          roleId: getRoleId(),
          pageNo: query.pageNo,
          pageSize: query.pageSize,
        })
        users.value = page.list
        total.value = page.total
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载关联用户失败')
    } finally {
      loading.value = false
    }
  }

  async function handleAddUsers(userIds: string[], workspaceIds?: string[]) {
    try {
      if (isWorkspaceRole()) {
        if (!workspaceIds?.length) {
          ElMessage.warning('请至少选择一个空间')
          return
        }
        await addWorkspaceRoleUsers(getRoleId(), userIds, workspaceIds)
      } else {
        await addRoleUsers(getRoleId(), userIds)
      }
      ElMessage.success('已添加用户')
      pickerVisible.value = false
      query.pageNo = 1
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '添加用户失败')
    }
  }

  async function handleRemove(user: AdminUser) {
    try {
      await ElMessageBox.confirm(`确定要移除用户「${user.name || user.username}」的该角色吗？`, '确认移除', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await removeRoleUser(getRoleId(), user.id)
      ElMessage.success('已移除')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除失败')
    }
  }

  async function handleRemoveWorkspace(user: RoleWorkspaceUser) {
    if (user.workspaces.length === 1) {
      // 只有一个空间，确认后直接移除
      const ws = user.workspaces[0]
      try {
        await ElMessageBox.confirm(
          `确定要移除用户「${user.name || user.username}」在「${ws.workspaceName}」中的该角色吗？`,
          '确认移除',
          { type: 'warning' },
        )
      } catch {
        return
      }
      try {
        await removeWorkspaceRoleUser(getRoleId(), user.userId, ws.workspaceId)
        ElMessage.success('已移除')
        load()
      } catch (err) {
        ElMessage.error(err instanceof Error ? err.message : '移除失败')
      }
    } else {
      // 多个空间，弹窗选择要移除的空间
      wsRemoveTarget.value = user
      wsRemoveSelected.value = []
      wsRemoveVisible.value = true
    }
  }

  async function handleWsRemoveConfirm() {
    if (!wsRemoveSelected.value.length) {
      ElMessage.warning('请至少选择一个空间')
      return
    }
    const user = wsRemoveTarget.value
    if (!user) return
    try {
      await Promise.all(
        wsRemoveSelected.value.map((wsId) => removeWorkspaceRoleUser(getRoleId(), user.userId, wsId)),
      )
      ElMessage.success('已移除')
      wsRemoveVisible.value = false
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除失败')
    }
  }

  watch(
    () => getRoleId(),
    () => {
      query.pageNo = 1
      load()
    },
    { immediate: true },
  )

  return {
    loading,
    users,
    workspaceUsers,
    total,
    query,
    pickerVisible,
    wsRemoveVisible,
    wsRemoveTarget,
    wsRemoveSelected,
    isWorkspaceRole,
    load,
    handleAddUsers,
    handleRemove,
    handleRemoveWorkspace,
    handleWsRemoveConfirm,
  }
}
