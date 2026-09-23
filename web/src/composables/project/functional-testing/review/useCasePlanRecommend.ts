import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { planRecommend, type AiCasePlanRecommendReq } from '@/services/ai'
import type { AiCasePlanRecommendResult } from '@/types'

export function useCasePlanRecommend({
  getText,
  getRequirementIds,
  getExcludeCaseNodeIds,
  getHasAnyInput,
}: {
  getText: () => string
  getRequirementIds: () => string[]
  getExcludeCaseNodeIds: () => string[]
  getHasAnyInput: () => boolean
}) {
  const recommending = ref(false)
  const result = ref<AiCasePlanRecommendResult | null>(null)
  const checkedIndexes = ref<Set<number>>(new Set())

  let controller: AbortController | null = null

  function buildReq(): AiCasePlanRecommendReq | null {
    if (!getHasAnyInput()) {
      ElMessage.warning('请至少输入需求文本或选择需求条目')
      return null
    }
    const req: AiCasePlanRecommendReq = {
      text: getText().trim() || undefined,
      requirementIds: getRequirementIds().length ? getRequirementIds() : undefined,
      excludeCaseNodeIds: getExcludeCaseNodeIds().length ? getExcludeCaseNodeIds() : undefined,
    }
    return req
  }

  async function recommend(): Promise<void> {
    const req = buildReq()
    if (!req) return
    recommending.value = true
    result.value = null
    const { controller: c, promise } = planRecommend(req)
    controller = c
    try {
      const resp = await promise
      result.value = resp
      checkedIndexes.value = new Set(resp.items.map((_, index) => index))
    } catch (err) {
      // 用户主动取消不提示（同步调用无部分结果）
      if (controller?.signal.aborted) return
      ElMessage.error(err instanceof Error ? err.message : '推荐失败')
    } finally {
      recommending.value = false
      controller = null
    }
  }

  function cancelRecommend(): void {
    controller?.abort()
    controller = null
    recommending.value = false
  }

  // 抽屉关闭不保留本次推荐结果（交互设计 6.2），并中止进行中的长调用；
  // watch 而非关闭按钮回调：父组件程序化关闭（如加入成功后）同样触发清理
  function handleClosed(): void {
    cancelRecommend()
    result.value = null
    checkedIndexes.value = new Set()
  }

  return {
    recommending,
    result,
    checkedIndexes,
    controller: controller as AbortController | null,
    buildReq,
    recommend,
    cancelRecommend,
    handleClosed,
  }
}
