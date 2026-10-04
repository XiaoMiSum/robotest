import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDocumentRequirements, setDocumentRequirements } from '@/services/project'
import type { RequirementSummary } from '@/types'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 脑图「关联需求」（脑图详设 4.3）：打开选取器前先拉既有关联回填，
 * 保存为全量覆盖语义，成功后以摘要列表回显计数。
 */
export function useMindmapRequirementLink(docId: () => string) {
  const visible = ref(false)
  const saving = ref(false)
  const linked = ref<RequirementSummary[]>([])

  let requestId = 0

  async function open(): Promise<void> {
    const id = docId()
    if (!id) return
    const seq = ++requestId
    try {
      linked.value = await fetchDocumentRequirements(id)
    } catch (err) {
      if (seq !== requestId) return
      linked.value = []
      ElMessage.error(errorMessage(err, '加载关联需求失败'))
      return
    }
    if (seq !== requestId) return
    visible.value = true
  }

  function close(): void {
    visible.value = false
  }

  /** 选取器回填格式：仅需 id / code / title 三元组 */
  function picks(): RequirementPick[] {
    return linked.value.map((item) => ({ id: item.id, code: item.code, title: item.title }))
  }

  async function confirm(ids: string[]): Promise<void> {
    const id = docId()
    if (!id) return
    saving.value = true
    try {
      linked.value = await setDocumentRequirements(id, ids)
      visible.value = false
      ElMessage.success('文档关联需求已更新')
    } catch (err) {
      ElMessage.error(errorMessage(err, '保存关联需求失败'))
    } finally {
      saving.value = false
    }
  }

  return { visible, saving, linked, open, close, picks, confirm }
}
