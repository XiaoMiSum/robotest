import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addWorkspaceMembers,
  dissolveWorkspace,
  fetchRoleList,
  fetchSimpleUserList,
  fetchWorkspaceDetail,
  fetchWorkspaceMembers,
  removeWorkspaceMember,
  updateWorkspace,
  updateWorkspaceMemberRole,
} from '@/services/admin'
import type { AdminWorkspace, UserSimple, WorkspaceMember } from '@/types'

export function useWorkspaceDetail() {
  const route = useRoute()
  const router = useRouter()
  const workspaceId = route.params.id as string

  const roleOptions = ref<{ value: string; label: string }[]>([])
  const defaultRoleId = ref('')

  async function loadRoleOptions() {
    try {
      const list = await fetchRoleList('workspace')
      roleOptions.value = list.map((r) => ({ value: r.id, label: r.name }))
      if (list.length > 0) {
        defaultRoleId.value = list[0].id
      }
    } catch {
      // 角色选项加载失败不阻塞页面
    }
  }

  const detail = ref<AdminWorkspace | null>(null)
  const infoLoading = ref(false)
  const infoSaving = ref(false)
  const infoFormRef = ref<FormInstance>()
  const infoForm = reactive({ name: '', description: '' })
  const infoRules: FormRules = {
    name: [
      { required: true, message: '请输入工作空间名称', trigger: 'blur' },
      { min: 2, max: 50, message: '名称长度需在 2-50 字符之间', trigger: 'blur' },
    ],
  }

  async function loadDetail() {
    infoLoading.value = true
    try {
      const data = await fetchWorkspaceDetail(workspaceId)
      detail.value = data
      infoForm.name = data.name
      infoForm.description = data.description ?? ''
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载工作空间详情失败')
    } finally {
      infoLoading.value = false
    }
  }

  async function saveInfo() {
    if (!infoFormRef.value) return
    try {
      await infoFormRef.value.validate()
    } catch {
      return
    }
    infoSaving.value = true
    try {
      const updated = await updateWorkspace(workspaceId, {
        name: infoForm.name.trim(),
        description: infoForm.description.trim(),
      })
      detail.value = updated
      ElMessage.success('已保存')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '保存失败')
    } finally {
      infoSaving.value = false
    }
  }

  const members = ref<WorkspaceMember[]>([])
  const membersLoading = ref(false)
  const memberTotal = ref(0)
  const memberQuery = reactive({ pageNo: 1, pageSize: 20 })

  async function loadMembers() {
    membersLoading.value = true
    try {
      const page = await fetchWorkspaceMembers(workspaceId, {
        pageNo: memberQuery.pageNo,
        pageSize: memberQuery.pageSize,
      })
      members.value = page.list
      memberTotal.value = page.total
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载成员列表失败')
    } finally {
      membersLoading.value = false
    }
  }

  async function handleRoleChange(member: WorkspaceMember, next: string) {
    try {
      await updateWorkspaceMemberRole(workspaceId, member.userId, next)
      member.workspaceRole = next
      ElMessage.success('角色已更新')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '更新角色失败')
      loadMembers()
    }
  }

  async function handleRemoveMember(member: WorkspaceMember) {
    try {
      await ElMessageBox.confirm(`确定要移除成员「${member.username}」吗？`, '确认移除', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await removeWorkspaceMember(workspaceId, member.userId)
      ElMessage.success('已移除')
      loadMembers()
      loadDetail()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除失败')
    }
  }

  const addDialogVisible = ref(false)
  const addSubmitting = ref(false)
  const userSearchLoading = ref(false)
  const userOptions = ref<UserSimple[]>([])
  const selectedUserIds = ref<string[]>([])
  const pendingRoles = reactive<Record<string, string>>({})

  function openAddDialog() {
    selectedUserIds.value = []
    userOptions.value = []
    Object.keys(pendingRoles).forEach((k) => delete pendingRoles[k])
    addDialogVisible.value = true
  }

  async function searchUsers(keyword: string) {
    if (!keyword) {
      userOptions.value = userOptions.value.filter((u) => selectedUserIds.value.includes(u.id))
      return
    }
    userSearchLoading.value = true
    try {
      const results = await fetchSimpleUserList(keyword)
      const selected = userOptions.value.filter((u) => selectedUserIds.value.includes(u.id))
      const merged = [...selected]
      for (const u of results) {
        if (!merged.some((m) => m.id === u.id)) {
          merged.push(u)
        }
      }
      userOptions.value = merged
    } catch {
      userOptions.value = []
    } finally {
      userSearchLoading.value = false
    }
  }

  function handleUserSelectChange(ids: string[]) {
    ids.forEach((id) => {
      if (!pendingRoles[id]) pendingRoles[id] = defaultRoleId.value
    })
    Object.keys(pendingRoles).forEach((id) => {
      if (!ids.includes(id)) delete pendingRoles[id]
    })
  }

  const selectedUsers = computed(() =>
    selectedUserIds.value.map((id) => {
      const opt = userOptions.value.find((u) => u.id === id)
      return { id, name: opt?.name ?? id }
    }),
  )

  async function submitAddMembers() {
    if (!selectedUserIds.value.length) {
      ElMessage.warning('请至少选择一个用户')
      return
    }
    addSubmitting.value = true
    try {
      await addWorkspaceMembers(
        workspaceId,
        selectedUserIds.value.map((id) => ({
          userId: id,
          workspaceRole: pendingRoles[id] ?? defaultRoleId.value,
        })),
      )
      ElMessage.success('成员已添加')
      addDialogVisible.value = false
      loadMembers()
      loadDetail()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '添加成员失败')
    } finally {
      addSubmitting.value = false
    }
  }

  async function handleDissolve() {
    if (!detail.value) return
    if (detail.value.projectCount > 0) {
      ElMessage.warning('该工作空间下仍有项目，无法解散')
      return
    }
    try {
      await ElMessageBox.prompt(
        `解散后数据不可恢复。请输入工作空间名称「${detail.value.name}」以确认解散。`,
        '解散工作空间',
        {
          type: 'warning',
          inputPlaceholder: '请输入工作空间名称',
          inputValidator: (val: string) => val === detail.value?.name || '名称不匹配',
        },
      )
    } catch {
      return
    }
    try {
      await dissolveWorkspace(workspaceId)
      ElMessage.success('工作空间已解散')
      router.push('/admin/workspaces')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '解散失败')
    }
  }

  onMounted(() => {
    loadRoleOptions()
    loadDetail()
    loadMembers()
  })

  return {
    route,
    router,
    roleOptions,
    defaultRoleId,
    detail,
    infoLoading,
    infoSaving,
    infoFormRef,
    infoForm,
    infoRules,
    saveInfo,
    members,
    membersLoading,
    memberTotal,
    memberQuery,
    loadMembers,
    handleRoleChange,
    handleRemoveMember,
    addDialogVisible,
    addSubmitting,
    userSearchLoading,
    userOptions,
    selectedUserIds,
    pendingRoles,
    openAddDialog,
    searchUsers,
    handleUserSelectChange,
    selectedUsers,
    submitAddMembers,
    handleDissolve,
  }
}
