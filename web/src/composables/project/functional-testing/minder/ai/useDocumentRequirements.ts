import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDocumentRequirements } from '@/services/project'
import type { RequirementSummary } from '@/types'

export function useDocumentRequirements(getDocId: () => string) {
  /** 已选需求池条目（US-AI-004），随请求体透传；打开时默认带入文档关联条目 */
  const selectedRequirements = ref<RequirementSummary[]>([])

  /** 打开时默认带入文档关联条目（交互设计 6.1），仅 generate/complete 消费 */
  async function loadDocumentRequirements(): Promise<void> {
    try {
      selectedRequirements.value = await getDocumentRequirements(getDocId())
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载文档关联需求失败')
    }
  }

  return {
    selectedRequirements,
    loadDocumentRequirements,
  }
}
