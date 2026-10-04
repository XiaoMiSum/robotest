import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTraceCoverage, patchTraceCoverage } from '@/services/project'
import type { TraceCoverage, TraceCoveragePatchPayload } from '@/types'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 覆盖修正面板（交互 04 §2.3）：按需求取覆盖结论，人工修正即写即回，
 * 无 AI 记录时同样可人工判定（后端无记录则新建，AI 分析字段留空）。
 */
export function useTraceCoverage() {
  const requirementId = ref('')
  const record = ref<TraceCoverage | null>(null)
  const loading = ref(false)
  const loadError = ref('')
  const saving = ref(false)
  const revision = ref(0)

  async function load() {
    const id = requirementId.value
    if (!id) return
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchTraceCoverage({ requirementIds: id, pageNo: 1, pageSize: 1 })
      record.value = page.list[0] ?? null
    } catch (err) {
      record.value = null
      loadError.value = errorMessage(err, '加载覆盖结论失败')
    } finally {
      loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  async function openFor(id: string): Promise<void> {
    if (requirementId.value !== id) {
      requirementId.value = id
      record.value = null
    }
    await load()
  }

  async function save(payload: TraceCoveragePatchPayload): Promise<boolean> {
    const id = requirementId.value
    if (!id) return false
    saving.value = true
    try {
      record.value = await patchTraceCoverage(id, payload)
      ElMessage.success('覆盖结论已更新，后续 AI 分析不再覆盖人工判定')
      revision.value += 1
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '覆盖修正失败'))
      return false
    } finally {
      saving.value = false
    }
  }

  function close(): void {
    requirementId.value = ''
    record.value = null
    loadError.value = ''
  }

  return {
    requirementId,
    record,
    loading,
    loadError,
    saving,
    revision,
    openFor,
    load,
    retry,
    save,
    close,
  }
}
