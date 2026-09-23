import { ref } from 'vue'
import { fetchBugs } from '@/services/project'
import type { BugListItem } from '@/types'

export function useBugResolve(getExcludeBugId: () => string | undefined) {
  const duplicateOptions = ref<BugListItem[]>([])
  const searching = ref(false)

  async function searchBugs(keyword: string) {
    searching.value = true
    try {
      const page = await fetchBugs({ keyword: keyword || undefined, pageNo: 1, pageSize: 20 })
      duplicateOptions.value = page.list.filter((b) => b.id !== getExcludeBugId())
    } catch {
      // 搜索失败不阻塞，用户可重试
    } finally {
      searching.value = false
    }
  }

  return {
    duplicateOptions,
    searching,
    searchBugs,
  }
}
