import { onBeforeUnmount, ref } from 'vue'
import { fetchAiProposal, fetchAiTask, submitAiTask } from '@/services/ai'
import type {
  AiTaskDetail,
  AiTaskStatus,
  BugDraftSuggestContent,
  BugDraftSuggestFields,
  BugDuplicateSuggestItem,
  BugRelatedCaseSuggestion,
  BugSuggestionField,
} from '@/types'

const POLL_INTERVAL_MS = 2000
const SYNC_WAIT_SECONDS = 10
const TERMINAL: AiTaskStatus[] = ['proposals_ready', 'closed', 'aborted']

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readSuggestion<T>(value: unknown): BugSuggestionField<T> | null {
  if (!isRecord(value)) return null
  if (!('value' in value)) return null
  return {
    value: value.value as T,
    reason: typeof value.reason === 'string' ? value.reason : '',
  }
}

function normalizeContent(content: Record<string, unknown>): BugDraftSuggestContent {
  const rawSuggestions = isRecord(content.suggestions) ? content.suggestions : {}
  const suggestions: BugDraftSuggestFields = {}
  const bugType = readSuggestion(rawSuggestions.bugType)
  const severity = readSuggestion(rawSuggestions.severity)
  const priority = readSuggestion(rawSuggestions.priority)
  const moduleId = readSuggestion(rawSuggestions.moduleId)
  const keywords = readSuggestion(rawSuggestions.keywords)
  if (bugType) suggestions.bugType = bugType as BugDraftSuggestFields['bugType']
  if (severity) suggestions.severity = severity as BugDraftSuggestFields['severity']
  if (priority) suggestions.priority = priority as BugDraftSuggestFields['priority']
  if (moduleId) suggestions.moduleId = moduleId as BugDraftSuggestFields['moduleId']
  if (keywords) suggestions.keywords = keywords as BugDraftSuggestFields['keywords']
  const relatedCaseRaw = rawSuggestions.relatedCase
  if (isRecord(relatedCaseRaw) && typeof relatedCaseRaw.value === 'string') {
    suggestions.relatedCase = {
      value: relatedCaseRaw.value,
      caseTitle: typeof relatedCaseRaw.caseTitle === 'string' ? relatedCaseRaw.caseTitle : '',
      reason: typeof relatedCaseRaw.reason === 'string' ? relatedCaseRaw.reason : '',
    }
  }
  const duplicates = Array.isArray(content.duplicates)
    ? content.duplicates.filter(isRecord).map((item) => ({
        bugId: String(item.bugId ?? ''),
        title: String(item.title ?? ''),
        status: String(item.status ?? ''),
        similarity: typeof item.similarity === 'number' ? item.similarity : 0,
        basis: String(item.basis ?? ''),
      })) as BugDuplicateSuggestItem[]
    : []
  return {
    title: readSuggestion<string>(content.title) ?? undefined,
    reproSteps: readSuggestion<string>(content.reproSteps) ?? undefined,
    suggestions,
    duplicates: [...duplicates].sort((left, right) => right.similarity - left.similarity),
  }
}

/**
 * 新建缺陷 AI 建议（详设 3.4）：提交 bug_draft_suggest 任务，等待/轮询提案结果；
 * 任务完成后由页面覆盖回填表单，已取得结果时重开只回显，不重复发起任务。
 */
export function useBugCreateSuggest() {
  const loading = ref(false)
  const error = ref('')
  const hasResult = ref(false)
  const taskId = ref('')
  const taskStatus = ref<AiTaskStatus | ''>('')
  const titleSuggestion = ref<BugSuggestionField<string> | null>(null)
  const reproStepsSuggestion = ref<BugSuggestionField<string> | null>(null)
  const suggestions = ref<BugDraftSuggestFields>({})
  const relatedCase = ref<BugRelatedCaseSuggestion | null>(null)
  const duplicates = ref<BugDuplicateSuggestItem[]>([])
  let timer: ReturnType<typeof setTimeout> | null = null
  let requestToken = 0

  function stopPolling(): void {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  function reset(): void {
    hasResult.value = false
    titleSuggestion.value = null
    reproStepsSuggestion.value = null
    suggestions.value = {}
    relatedCase.value = null
    duplicates.value = []
  }

  function applyContent(content: BugDraftSuggestContent): void {
    titleSuggestion.value = content.title ?? null
    reproStepsSuggestion.value = content.reproSteps ?? null
    suggestions.value = content.suggestions ?? {}
    relatedCase.value = content.suggestions?.relatedCase ?? null
    duplicates.value = content.duplicates ?? []
    hasResult.value = true
  }

  async function loadProposal(detail: AiTaskDetail, token: number): Promise<void> {
    const summary = detail.proposals?.find((item) => item.kind === 'bug_draft_suggestion') ?? detail.proposals?.[0]
    if (!summary) throw new Error('AI 建议未返回可审核产物')
    const proposal = await fetchAiProposal(detail.taskId, summary.key)
    if (token !== requestToken) return
    applyContent(normalizeContent((proposal.content ?? {}) as Record<string, unknown>))
  }

  async function waitForTerminal(taskIdValue: string, token: number): Promise<AiTaskDetail> {
    for (;;) {
      const detail = await fetchAiTask(taskIdValue)
      if (token !== requestToken) return detail
      taskStatus.value = detail.status
      if (TERMINAL.includes(detail.status)) return detail
      await new Promise<void>((resolve) => {
        timer = setTimeout(resolve, POLL_INTERVAL_MS)
      })
      if (token !== requestToken) return detail
    }
  }

  async function requestSuggestion(input: {
    title: string
    steps?: string
    moduleId?: string
  }): Promise<void> {
    const token = ++requestToken
    stopPolling()
    loading.value = true
    error.value = ''
    taskId.value = ''
    taskStatus.value = ''
    reset()
    try {
      const submitted = await submitAiTask('bug_draft_suggest', {
        title: input.title,
        ...(input.steps ? { steps: input.steps } : {}),
        ...(input.moduleId ? { moduleId: input.moduleId } : {}),
      }, SYNC_WAIT_SECONDS)
      if (token !== requestToken) return
      taskId.value = submitted.taskId
      taskStatus.value = submitted.status
      const detail = TERMINAL.includes(submitted.status)
        ? await fetchAiTask(submitted.taskId)
        : await waitForTerminal(submitted.taskId, token)
      if (token !== requestToken) return
      taskStatus.value = detail.status
      if (detail.status === 'aborted') {
        throw new Error(detail.terminateReason?.msg || 'AI 建议任务已终止')
      }
      if (detail.status !== 'proposals_ready' && detail.status !== 'closed') {
        throw new Error('AI 建议任务未完成')
      }
      await loadProposal(detail, token)
    } catch (err) {
      if (token === requestToken) error.value = errorMessage(err, '获取缺陷建议失败')
    } finally {
      if (token === requestToken) loading.value = false
    }
  }

  onBeforeUnmount(stopPolling)

  return {
    loading,
    error,
    hasResult,
    taskId,
    taskStatus,
    titleSuggestion,
    reproStepsSuggestion,
    suggestions,
    relatedCase,
    duplicates,
    requestSuggestion,
  }
}
