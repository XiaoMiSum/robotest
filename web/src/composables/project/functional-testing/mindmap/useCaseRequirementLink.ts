import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchCaseRequirements, updateCaseRequirements } from '@/services/project'
import type { RequirementRef } from '@/types'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 脑图「关联需求」：作用于当前选中用例节点，打开前拉取既有关联回填，
 * 保存为全量覆盖语义（空选择即清空）。
 */
export function useCaseRequirementLink(caseId: () => string) {
  const visible = ref(false)
  const saving = ref(false)
  const linked = ref<RequirementRef[]>([])

  let requestId = 0
  let targetCaseId = ''

  async function open(): Promise<void> {
    const id = caseId()
    if (!id) return
    const seq = ++requestId
    try {
      const result = await fetchCaseRequirements(id)
      if (seq !== requestId || id !== caseId()) return
      linked.value = result
      targetCaseId = id
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
    requestId += 1
    visible.value = false
  }

  /** 选取器回填格式：仅需 id / code / title 三元组 */
  function picks(): RequirementPick[] {
    return linked.value.map((item) => ({ id: item.id, code: item.code, title: item.title }))
  }

  async function confirm(ids: string[]): Promise<void> {
    const id = targetCaseId
    if (!id || saving.value) return
    saving.value = true
    try {
      linked.value = await updateCaseRequirements(id, ids)
      visible.value = false
      ElMessage.success('关联需求已更新')
    } catch (err) {
      ElMessage.error(errorMessage(err, '保存关联需求失败'))
    } finally {
      saving.value = false
    }
  }

  return { visible, saving, linked, open, close, picks, confirm }
}
