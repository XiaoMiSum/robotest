import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiScheduleExecutionItem, ApiSchedulePageItem } from '@/types'
import { fetchScheduleExecutions } from '@/services/project/schedule'

const EXECUTION_PAGE_SIZE = 10

export function useScheduleExecutions() {
  const showExecutionDrawer = ref(false)
  const executionTask = ref<ApiSchedulePageItem | null>(null)
  const executionRows = ref<ApiScheduleExecutionItem[]>([])
  const executionTotal = ref(0)
  const executionPageNo = ref(1)
  const executionLoading = ref(false)

  async function openExecutions(item: ApiSchedulePageItem) {
    executionTask.value = item
    executionRows.value = []
    executionTotal.value = 0
    executionPageNo.value = 1
    showExecutionDrawer.value = true
    await loadExecutions()
  }

  async function loadExecutions() {
    if (!executionTask.value) return
    executionLoading.value = true
    try {
      const page = await fetchScheduleExecutions(executionTask.value.id, { pageNo: executionPageNo.value, pageSize: EXECUTION_PAGE_SIZE })
      executionRows.value = page.list
      executionTotal.value = page.total
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '执行记录加载失败')
    } finally {
      executionLoading.value = false
    }
  }

  return {
    showExecutionDrawer,
    executionTask,
    executionRows,
    executionTotal,
    executionPageNo,
    executionLoading,
    openExecutions,
    loadExecutions,
  }
}
