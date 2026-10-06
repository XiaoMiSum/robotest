import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitAiTask } from '@/services/ai'
import type { AiGenerationConfig } from '@/types'

/** 输入含非已确认需求（生成链详设 3.6.2 校验）：范围回显需刷新后重试 */
const GENERATION_INVALID_INPUT = 1000018202
/** 同项目已有进行中的同输入生成任务（生成链详设 3.6.2） */
const GENERATION_DUPLICATE_TASK = 1000018209

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

/** 提交结果：submitted 已跳转；stale 输入状态变化需刷新范围；failed 已提示 */
export type GenerationSubmitResult = 'submitted' | 'stale' | 'failed'

/**
 * 测试设计生成提交（交互 2.1）：成功跳任务详情页；重复任务引导去任务中心；
 * 输入状态变化回传 stale 由调用方刷新范围回显（交互 2.5）。
 */
export function useGenerationSubmit() {
  const router = useRouter()
  const submitting = ref(false)

  async function promptTaskCenter(message: string): Promise<void> {
    try {
      await ElMessageBox.confirm(message, '无法提交生成任务', {
        type: 'warning',
        confirmButtonText: '前往任务中心',
        cancelButtonText: '留在本页',
      })
    } catch {
      return
    }
    void router.push('/workspace/projects/ai/tasks')
  }

  async function submitGeneration(
    requirementIds: string[],
    config: AiGenerationConfig,
  ): Promise<GenerationSubmitResult> {
    const input: Record<string, unknown> = {
      requirementIds,
      placement: config.placement,
      granularity: config.granularity,
    }
    if (config.targetModuleId) input.targetModuleId = config.targetModuleId

    submitting.value = true
    try {
      const task = await submitAiTask('test_design_generation', input)
      ElMessage.success('生成任务已提交')
      void router.push(`/workspace/projects/ai/tasks/${task.taskId}`)
      return 'submitted'
    } catch (err) {
      const code = errorCode(err)
      if (code === GENERATION_DUPLICATE_TASK) {
        await promptTaskCenter(errorMessage(err, '已存在进行中的同输入生成任务'))
        return 'failed'
      }
      if (code === GENERATION_INVALID_INPUT) {
        ElMessage.warning(errorMessage(err, '所选需求状态已变化，请刷新后重试'))
        return 'stale'
      }
      ElMessage.error(errorMessage(err, '提交生成任务失败'))
      return 'failed'
    } finally {
      submitting.value = false
    }
  }

  return { submitting, submitGeneration }
}
