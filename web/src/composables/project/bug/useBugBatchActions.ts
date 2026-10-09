import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitAiTask } from '@/services/ai'
import { BUG_STATUS_LABEL } from '@/composables/project/bug/bugStatus'
import type { BugStatus } from '@/types'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** 状态范围选项：filter.statuses 按状态筛选（详设 3.6） */
const STATUS_OPTIONS: BugStatus[] = ['active', 'resolved', 'rejected', 'closed']

/**
 * 批量分类与存量重复扫描发起（详设 3.6 / 3.9）：
 * 提交后跳转任务详情并定位审核区，审核在详情页审核区完成（交互 2.1.2）。
 */
export function useBugBatchActions(onSubmitted: () => void = () => {}) {
  const router = useRouter()

  // ---------- 批量分类 ----------
  const classifyVisible = ref(false)
  const classifyBusy = ref(false)
  /** 默认激活缺陷；未分类 / 分类存疑由人工按状态圈定（后端无该语义字段） */
  const classifyStatuses = ref<BugStatus[]>(['active'])

  const classifySummary = computed(() =>
    classifyStatuses.value.length === 0
      ? '请至少选择一个缺陷状态'
      : `将对状态为「${classifyStatuses.value.map((status) => BUG_STATUS_LABEL[status]).join('、')}」的缺陷发起批量分类`,
  )

  function openClassify(): void {
    classifyStatuses.value = ['active']
    classifyVisible.value = true
  }

  async function submitClassify(): Promise<void> {
    if (classifyStatuses.value.length === 0) return
    classifyBusy.value = true
    try {
      const task = await submitAiTask('bug_classify', {
        filter: { statuses: classifyStatuses.value },
      })
      classifyVisible.value = false
      onSubmitted()
      ElMessage.success('批量分类已发起，跳转任务详情审核')
      navigateToReview(task.taskId)
    } catch (err) {
      ElMessage.error(errorMessage(err, '发起批量分类失败'))
    } finally {
      classifyBusy.value = false
    }
  }

  // ---------- 存量重复扫描 ----------
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
    classifyVisible,
    classifyBusy,
    classifyStatuses,
    classifySummary,
    scanBusy,
    STATUS_OPTIONS,
    openClassify,
    submitClassify,
    submitScan,
  }
}
