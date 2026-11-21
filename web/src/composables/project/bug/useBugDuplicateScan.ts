import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitAiTask } from '@/services/ai'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 存量重复扫描发起（详设 3.9）：
 * 提交后跳转任务详情并定位审核区，分组确认在详情页审核区完成（交互 2.1.2）。
 */
export function useBugDuplicateScan(onSubmitted: () => void = () => {}) {
  const router = useRouter()
  const scanBusy = ref(false)

  async function submitScan(): Promise<void> {
    scanBusy.value = true
    try {
      const task = await submitAiTask('bug_duplicate_scan', { scope: 'active' })
      onSubmitted()
      ElMessage.success('存量重复扫描已发起，跳转任务详情确认分组')
      navigateToReview(task.taskId)
    } catch (err) {
      ElMessage.error(errorMessage(err, '发起重复扫描失败'))
    } finally {
      scanBusy.value = false
    }
  }

  function navigateToReview(taskId: string): void {
    void router.push({
      path: `/workspace/projects/ai/tasks/${taskId}`,
      query: { review: '1' },
    })
  }

  return {
    scanBusy,
    submitScan,
  }
}
