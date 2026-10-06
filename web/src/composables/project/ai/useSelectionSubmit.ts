import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitAiTask } from '@/services/ai'
import type { AiSelectionConfig, AiTaskType } from '@/types'

/** 圈选输入非法：范围为空 / 轮次越界 / 需求不属于当前项目（生成链详设 3.2） */
const SELECTION_INVALID_INPUT = 1000018206
/** 同项目已有进行中的同输入圈选任务（交互 2.4 重复提交口径） */
const SELECTION_DUPLICATE_TASK = 1000018209

/** 圈选任务类型：评审单轮、计划按轮次分组（详设 3.1 类型 B） */
export type SelectionTaskType = Extract<AiTaskType, 'review_selection' | 'plan_selection'>

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function errorCode(error: unknown): number | undefined {
  if (error instanceof Error && 'code' in error) {
    const code = (error as Error & { code?: number }).code
    return typeof code === 'number' ? code : undefined
  }
  return undefined
}

/** 提交结果：submitted 已跳转；failed 已提示 */
export type SelectionSubmitResult = 'submitted' | 'failed'

/**
 * 圈选建议提交（交互 2.4）：成功跳任务详情页；重复任务引导去任务中心；
 * 计划必带轮次（缺省 1），评审不传 roundCount（后端见即拒）。
 */
export function useSelectionSubmit() {
  const router = useRouter()
  const submitting = ref(false)

  async function promptTaskCenter(message: string): Promise<void> {
    try {
      await ElMessageBox.confirm(message, '无法提交圈选任务', {
        type: 'warning',
        confirmButtonText: '前往任务中心',
        cancelButtonText: '留在本页',
      })
    } catch {
      return
    }
    void router.push('/workspace/projects/ai/tasks')
  }

  async function submitSelection(
    type: SelectionTaskType,
    config: AiSelectionConfig,
  ): Promise<SelectionSubmitResult> {
    const input: Record<string, unknown> = { requirementIds: config.requirementIds }
    if (type === 'plan_selection') input.roundCount = config.roundCount ?? 1

    submitting.value = true
    try {
      const task = await submitAiTask(type, input)
      ElMessage.success('圈选任务已提交')
      void router.push(`/workspace/projects/ai/tasks/${task.taskId}`)
      return 'submitted'
    } catch (err) {
      const code = errorCode(err)
      if (code === SELECTION_DUPLICATE_TASK) {
        await promptTaskCenter(errorMessage(err, '已存在进行中的同输入圈选任务'))
        return 'failed'
      }
      if (code === SELECTION_INVALID_INPUT) {
        ElMessage.warning(errorMessage(err, '圈选范围已变化，请刷新后重试'))
        return 'failed'
      }
      ElMessage.error(errorMessage(err, '提交圈选任务失败'))
      return 'failed'
    } finally {
      submitting.value = false
    }
  }

  return { submitting, submitSelection }
}
