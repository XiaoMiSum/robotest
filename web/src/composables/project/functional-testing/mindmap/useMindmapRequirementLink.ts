/**
 * 脑图文档的需求关联：选择器打开前先回填已有关联，保存失败时不污染本地状态
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDocumentRequirements, setDocumentRequirements } from '@/services/project'
import type { RequirementSummary } from '@/types'

export function useMindmapRequirementLink(docId: () => string) {
  const reqSelectorVisible = ref(false)
  const associatedReqIds = ref<string[]>([])

  async function openRequirementSelector() {
    try {
      const list = await getDocumentRequirements(docId())
      associatedReqIds.value = list.map((r) => r.id)
      reqSelectorVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载关联需求失败')
    }
  }

  async function handleRequirementConfirm(selected: RequirementSummary[]) {
    try {
      await setDocumentRequirements(docId(), selected.map((r) => r.id))
      associatedReqIds.value = selected.map((r) => r.id)
      ElMessage.success('已更新文档关联需求')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '保存关联失败')
    }
  }

  return {
    reqSelectorVisible,
    associatedReqIds,
    openRequirementSelector,
    handleRequirementConfirm,
  }
}
