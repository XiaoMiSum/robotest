import { ref } from 'vue'
import { fetchSimpleUserList, fetchWorkspaces } from '@/services/admin'

export function useUserPicker(getExcludeIds: () => string[]) {
  const loading = ref(false)
  const options = ref<{ id: string; name: string }[]>([])
  const selectedIds = ref<string[]>([])

  // 空间选择
  const wsLoading = ref(false)
  const wsOptions = ref<{ id: string; name: string }[]>([])
  const selectedWsIds = ref<string[]>([])

  async function searchUsers(keyword: string) {
    if (!keyword) {
      options.value = []
      return
    }
    loading.value = true
    try {
      const list = await fetchSimpleUserList(keyword)
      const exclude = getExcludeIds()
      options.value = list.filter((u) => !exclude.includes(u.id))
    } catch {
      options.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadWorkspaces() {
    wsLoading.value = true
    try {
      const page = await fetchWorkspaces({ pageNo: 1, pageSize: 100 })
      wsOptions.value = page.list.map((w) => ({ id: w.id, name: w.name }))
    } catch {
      wsOptions.value = []
    } finally {
      wsLoading.value = false
    }
  }

  return {
    loading,
    options,
    selectedIds,
    wsLoading,
    wsOptions,
    selectedWsIds,
    searchUsers,
    loadWorkspaces,
  }
}
