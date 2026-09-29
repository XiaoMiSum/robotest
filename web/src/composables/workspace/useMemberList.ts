import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  fetchMembers,
  fetchWorkspaceRoles,
  removeMember,
  updateMemberRole,
} from '@/services/workspace'
import type { WorkspaceMember } from '@/types'

const SEARCH_DEBOUNCE_MS = 300

export function useMemberList() {
  const router = useRouter()
  const authStore = useAuthStore()

  const currentUserId = computed(() => authStore.user?.id ?? '')
  const roleOptions = ref<{ value: string; label: string }[]>([])
  const members = ref<WorkspaceMember[]>([])
  const membersLoading = ref(false)
  const membersLoadError = ref(false)
  const memberTotal = ref(0)
  const memberQuery = reactive({ keyword: '', workspaceRole: '', pageNo: 1, pageSize: 20 })
  const editingUserId = ref('')

  let memberSearchTimer: ReturnType<typeof setTimeout> | null = null
  let memberRequestId = 0

  async function loadRoleOptions(): Promise<void> {
    try {
      const list = await fetchWorkspaceRoles()
      roleOptions.value = list
        .filter((role) => !role.isGroup)
        .map((role) => ({ value: role.id, label: role.name }))
    } catch {
      roleOptions.value = []
    }
  }

  function cancelMemberSearch(): void {
    if (memberSearchTimer !== null) {
      clearTimeout(memberSearchTimer)
      memberSearchTimer = null
    }
  }

  async function loadMembers(): Promise<void> {
    const requestId = ++memberRequestId
    membersLoading.value = true
    membersLoadError.value = false
    try {
      const page = await fetchMembers({
        keyword: memberQuery.keyword.trim() || undefined,
        workspaceRole: memberQuery.workspaceRole || undefined,
        pageNo: memberQuery.pageNo,
        pageSize: memberQuery.pageSize,
      })
      if (requestId !== memberRequestId) return
      members.value = page.list
      memberTotal.value = page.total
    } catch (err) {
      if (requestId !== memberRequestId) return
      // 空表无法自证是「没有数据」还是「没加载到」，用错误标记区分
      membersLoadError.value = true
      ElMessage.error(err instanceof Error ? err.message : '加载成员列表失败')
    } finally {
      if (requestId === memberRequestId) {
        membersLoading.value = false
      }
    }
  }

  function handleMemberSearchInput(value: string): void {
    memberQuery.keyword = value
    cancelMemberSearch()
    memberQuery.pageNo = 1
    memberSearchTimer = setTimeout(() => {
      memberSearchTimer = null
      void loadMembers()
    }, SEARCH_DEBOUNCE_MS)
  }

  function handleMemberSearchClear(): void {
    memberQuery.keyword = ''
    cancelMemberSearch()
    memberQuery.pageNo = 1
    void loadMembers()
  }

  function handleRoleFilterChange(value: string): void {
    memberQuery.workspaceRole = value
    cancelMemberSearch()
    memberQuery.pageNo = 1
    void loadMembers()
  }

  function handleMemberPageChange(pageNo: number): void {
    memberQuery.pageNo = pageNo
    void loadMembers()
  }

  function startEditRole(userId: string): void {
    editingUserId.value = userId
  }

  function handleRoleVisibleChange(visible: boolean): void {
    if (!visible) editingUserId.value = ''
  }

  async function submitRoleChange(member: WorkspaceMember, nextRoleId: string): Promise<void> {
    editingUserId.value = ''
    try {
      await updateMemberRole(member.userId, nextRoleId)
      member.workspaceRole = nextRoleId
      ElMessage.success('角色已更新')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '更新角色失败')
      void loadMembers()
    }
  }

  async function handleRemoveMember(member: WorkspaceMember): Promise<void> {
    const isSelf = member.userId === currentUserId.value
    const message = isSelf
      ? '确定退出该工作空间？退出后将无法访问此空间的资源。'
      : `确定要移除成员「${member.name || member.username}」吗？`
    try {
      await ElMessageBox.confirm(message, isSelf ? '退出工作空间' : '确认移除', {
        type: 'warning',
      })
    } catch {
      return
    }

    try {
      await removeMember(member.userId)
      ElMessage.success(isSelf ? '已退出工作空间' : '已移除')
      if (isSelf) {
        authStore.setActiveWorkspace(null)
        await router.push('/workspaces')
      } else {
        void loadMembers()
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除失败')
    }
  }

  function dispose(): void {
    cancelMemberSearch()
    memberRequestId += 1
    // 页面卸载后不再有渲染方消费 loading，直接复位避免残留
    membersLoading.value = false
  }

  return {
    currentUserId,
    roleOptions,
    members,
    membersLoading,
    membersLoadError,
    memberTotal,
    memberQuery,
    editingUserId,
    loadRoleOptions,
    loadMembers,
    handleMemberSearchInput,
    handleMemberSearchClear,
    handleRoleFilterChange,
    handleMemberPageChange,
    startEditRole,
    handleRoleVisibleChange,
    submitRoleChange,
    handleRemoveMember,
    dispose,
  }
}
