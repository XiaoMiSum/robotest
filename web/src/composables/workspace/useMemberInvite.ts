import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { addMembers, fetchMemberCandidates } from '@/services/workspace'
import type { UserSimple } from '@/types'
import { WORKSPACE_ROLE } from '@/utils/workspaceRole'

const SEARCH_DEBOUNCE_MS = 300

export function useMemberInvite(onAdded: () => void) {
  const addDialogVisible = ref(false)
  const addSubmitting = ref(false)
  const userSearchLoading = ref(false)
  const userOptions = ref<UserSimple[]>([])
  const selectedUserIds = ref<string[]>([])

  let userSearchTimer: ReturnType<typeof setTimeout> | null = null
  let userSearchRequestId = 0

  function openAddDialog(): void {
    selectedUserIds.value = []
    userOptions.value = []
    addDialogVisible.value = true
  }

  function handleSelectedUsersChange(users: unknown): void {
    selectedUserIds.value = Array.isArray(users) ? (users as string[]) : []
  }

  function cancelUserSearch(): void {
    if (userSearchTimer !== null) {
      clearTimeout(userSearchTimer)
      userSearchTimer = null
    }
  }

  function searchUsers(keyword: string): void {
    cancelUserSearch()
    const requestId = ++userSearchRequestId
    const normalizedKeyword = keyword.trim()
    if (!normalizedKeyword) {
      userOptions.value = []
      userSearchLoading.value = false
      return
    }

    userSearchLoading.value = true
    userSearchTimer = setTimeout(async () => {
      userSearchTimer = null
      try {
        const users = await fetchMemberCandidates(normalizedKeyword)
        if (requestId === userSearchRequestId) {
          userOptions.value = users
        }
      } catch {
        if (requestId === userSearchRequestId) {
          userOptions.value = []
        }
      } finally {
        if (requestId === userSearchRequestId) {
          userSearchLoading.value = false
        }
      }
    }, SEARCH_DEBOUNCE_MS)
  }

  async function submitAddMembers(): Promise<void> {
    if (!selectedUserIds.value.length) {
      ElMessage.warning('请至少选择一个用户')
      return
    }
    addSubmitting.value = true
    try {
      const result = await addMembers(
        selectedUserIds.value.map((id) => ({
          userId: id,
          workspaceRole: WORKSPACE_ROLE.MEMBER,
        })),
      )
      const message = result.skippedUserIds.length
        ? `成功添加 ${result.successCount} 人，${result.skippedUserIds.length} 人已在空间中跳过`
        : `成功添加 ${result.successCount} 人`
      ElMessage.success(message)
      addDialogVisible.value = false
      onAdded()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '邀请成员失败')
    } finally {
      addSubmitting.value = false
    }
  }

  function dispose(): void {
    cancelUserSearch()
    userSearchRequestId += 1
    userSearchLoading.value = false
  }

  return {
    addDialogVisible,
    addSubmitting,
    userSearchLoading,
    userOptions,
    selectedUserIds,
    openAddDialog,
    handleSelectedUsersChange,
    searchUsers,
    submitAddMembers,
    dispose,
  }
}
