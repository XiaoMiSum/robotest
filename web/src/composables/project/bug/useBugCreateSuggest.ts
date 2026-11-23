import { ref } from 'vue'
import { suggestBugDraft } from '@/services/project'
import type {
  BugDraftSuggestFields,
  BugDuplicateSuggestItem,
  BugRelatedCaseSuggestion,
  BugSuggestionField,
} from '@/types'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 新建缺陷实时建议（详设 3.4）：点击「AI 建议」发起 SSE 流式请求，done 终态一次返回字段建议
 * 与疑似重复缺陷（不产生任务、不落库）；向量未就绪时 duplicates 为空且不作提示，
 * 失败三形态（前置校验 / 流内 error / 连接中断）均落 error 轻提示。状态存组件本地，提交后释放。
 */
export function useBugCreateSuggest() {
  const loading = ref(false)
  const error = ref('')
  const titleSuggestion = ref<BugSuggestionField<string> | null>(null)
  const reproStepsSuggestion = ref<BugSuggestionField<string> | null>(null)
  const suggestions = ref<BugDraftSuggestFields>({})
  const relatedCase = ref<BugRelatedCaseSuggestion | null>(null)
  const duplicates = ref<BugDuplicateSuggestItem[]>([])

  function reset(): void {
    titleSuggestion.value = null
    reproStepsSuggestion.value = null
    suggestions.value = {}
    relatedCase.value = null
    duplicates.value = []
  }

  async function requestSuggestion(input: {
    title: string
    steps?: string
    moduleId?: string
  }): Promise<void> {
    loading.value = true
    error.value = ''
    reset()
    try {
      const resp = await suggestBugDraft(input)
      titleSuggestion.value = resp.title ?? null
      reproStepsSuggestion.value = resp.reproSteps ?? null
      suggestions.value = resp.suggestions ?? {}
      relatedCase.value = resp.suggestions?.relatedCase ?? null
      // 后端检索已按相似度排序，此处兜底保证展示稳定（高相似项前置，不单靠颜色表意）
      duplicates.value = [...(resp.duplicates ?? [])].sort((left, right) => right.similarity - left.similarity)
    } catch (err) {
      // 建议失败不阻塞录入，仅提示原因（交互 2.3 状态分支）
      error.value = errorMessage(err, '获取缺陷建议失败')
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    titleSuggestion,
    reproStepsSuggestion,
    suggestions,
    relatedCase,
    duplicates,
    requestSuggestion,
  }
}
