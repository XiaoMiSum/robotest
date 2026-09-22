import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getBugDetail } from '@/services/project'
import type { AiBugDedupItem, BugDetail } from '@/types'

export function useBugDedupDetail() {
  const drawerVisible = ref(false)
  const detailLoading = ref(false)
  const detail = ref<BugDetail | null>(null)

  async function openDetail(item: AiBugDedupItem): Promise<void> {
    drawerVisible.value = true
    detailLoading.value = true
    detail.value = null
    try {
      detail.value = await getBugDetail(item.bugId)
    } catch {
      // 详情加载失败仅关闭抽屉，不打断表单
      drawerVisible.value = false
    } finally {
      detailLoading.value = false
    }
  }

  const router = useRouter()
  function goDetail(bugId: string): void {
    drawerVisible.value = false
    router.push(`/workspace/projects/bugs/${bugId}`)
  }

  return {
    drawerVisible,
    detailLoading,
    detail,
    openDetail,
    goDetail,
  }
}
