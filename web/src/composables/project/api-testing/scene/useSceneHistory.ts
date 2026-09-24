import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchExecutionHistory, fetchChangeHistory } from '@/services/project/api-testing/scene'
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
  const executionHistoryError = ref<string | null>(null)
  const changeHistoryError = ref<string | null>(null)
  const historyError = computed(() => {
    const errors = [executionHistoryError.value, changeHistoryError.value].filter(
      (message): message is string => Boolean(message),
    )
    return errors.length > 0 ? errors.join('；') : null
  })
  let requestSequence = 0

  function errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error && error.message ? error.message : fallback
  }

  async function loadHistory(): Promise<void> {
    const id = sceneId()
    if (!id) {
      requestSequence += 1
      historyLoading.value = false
      executionHistoryError.value = null
      changeHistoryError.value = null
      return
    }
    const sequence = ++requestSequence
    historyLoading.value = true
    executionHistoryError.value = null
    changeHistoryError.value = null
    try {
      const [execResult, changeResult] = await Promise.allSettled([
        Promise.resolve().then(() => fetchExecutionHistory(id, executionHistoryPage.value, 20)),
        Promise.resolve().then(() => fetchChangeHistory(id, changeHistoryPage.value, 20)),
      ])
      if (sequence !== requestSequence) return
      const notifiedMessages = new Set<string>()
      if (execResult.status === 'fulfilled') {
        executionHistory.value = execResult.value.list
        executionHistoryTotal.value = execResult.value.total
      } else {
        const message = errorMessage(execResult.reason, '加载执行历史失败')
        executionHistoryError.value = message
        if (!notifiedMessages.has(message)) {
          notifiedMessages.add(message)
          ElMessage.error(message)
        }
      }
      if (changeResult.status === 'fulfilled') {
        changeHistory.value = changeResult.value.list
        changeHistoryTotal.value = changeResult.value.total
      } else {
        const message = errorMessage(changeResult.reason, '加载变更历史失败')
        changeHistoryError.value = message
        if (!notifiedMessages.has(message)) {
          notifiedMessages.add(message)
          ElMessage.error(message)
        }
      }
    } finally {
      if (sequence === requestSequence) {
        historyLoading.value = false
      }
    }
  }

  function retryHistory(): Promise<void> {
    return loadHistory()
  }

  const showHistory = ref(false)
  watch(showHistory, (v) => { if (v) void loadHistory() })

  const reportDialogVisible = ref(false)
  const reportDetailId = ref('')

  function handleViewReport(reportId: string) {
    reportDetailId.value = reportId
    reportDialogVisible.value = true
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      requestSequence += 1
      historyLoading.value = false
    })
  }

  return {
    executionHistory,
    executionHistoryTotal,
    executionHistoryPage,
    changeHistory,
    changeHistoryTotal,
    changeHistoryPage,
    historyLoading,
    executionHistoryError,
    changeHistoryError,
    historyError,
    showHistory,
    loadHistory,
    retryHistory,
    reportDialogVisible,
    reportDetailId,
    handleViewReport,
  }
}
