import { ref, watch } from 'vue'
import { fetchExecutionHistory, fetchChangeHistory } from '@/services/apiScene'
import type { ApiExecutionHistoryItem, ApiChangeHistoryItem } from '@/types'

/**
 * 场景执行/变更历史（从 SceneEditorPage 提取）。
 * 仅依赖 props.sceneId，零外部耦合。
 */
export function useSceneHistory(sceneId: () => string | undefined) {
  const executionHistory = ref<ApiExecutionHistoryItem[]>([])
  const executionHistoryTotal = ref(0)
  const executionHistoryPage = ref(1)
  const changeHistory = ref<ApiChangeHistoryItem[]>([])
  const changeHistoryTotal = ref(0)
  const changeHistoryPage = ref(1)
  const historyLoading = ref(false)

  async function loadHistory() {
    const id = sceneId()
    if (!id) return
    historyLoading.value = true
    try {
      const [execResp, changeResp] = await Promise.all([
        fetchExecutionHistory(id, executionHistoryPage.value, 20),
        fetchChangeHistory(id, changeHistoryPage.value, 20),
      ])
      executionHistory.value = execResp.list
      executionHistoryTotal.value = execResp.total
      changeHistory.value = changeResp.list
      changeHistoryTotal.value = changeResp.total
    } catch { /* 静默 */ } finally { historyLoading.value = false }
  }

  const showHistory = ref(false)
  watch(showHistory, (v) => { if (v) void loadHistory() })

  const reportDialogVisible = ref(false)
  const reportDetailId = ref('')

  function handleViewReport(reportId: string) {
    reportDetailId.value = reportId
    reportDialogVisible.value = true
  }

  return {
    executionHistory,
    executionHistoryTotal,
    executionHistoryPage,
    changeHistory,
    changeHistoryTotal,
    changeHistoryPage,
    historyLoading,
    showHistory,
    loadHistory,
    reportDialogVisible,
    reportDetailId,
    handleViewReport,
  }
}
