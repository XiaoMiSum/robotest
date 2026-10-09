import { ref } from 'vue'
import { fetchAiArtifact, fetchAiTask, submitAiTask } from '@/services/ai'
import type {
  AiTaskDetail,
  BugAssigneeCandidate,
  BugClassifySuggestions,
  BugPriority,
  BugSeverity,
  BugSourceRef,
  BugType,
} from '@/types'

/** 同步快路径等待上限（详设 3.6.2），超时转轮询 */
const SYNC_WAIT_SECONDS = 10
const POLL_INTERVAL_MS = 2000

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function isTerminal(status: string): boolean {
  return status === 'succeeded' || status === 'failed' || status === 'cancelled'
}

function readCandidate(raw: unknown): BugAssigneeCandidate | null {
  if (typeof raw !== 'object' || raw === null) return null
  const source = raw as Record<string, unknown>
  const userId = source['userId']
  if (typeof userId !== 'string' || !userId) return null
  return {
    userId,
    name: typeof source['name'] === 'string' ? source['name'] : userId,
    reason: typeof source['reason'] === 'string' ? source['reason'] : '',
    memberValid: source['memberValid'] !== false,
  }
}

function readSourceRef(raw: unknown): BugSourceRef | null {
  if (typeof raw !== 'object' || raw === null) return null
  const source = raw as Record<string, unknown>
  const id = source['id']
  if (typeof id !== 'string') return null
  return {
    type: typeof source['type'] === 'string' ? source['type'] : 'bug',
    id,
    title: typeof source['title'] === 'string' ? source['title'] : '',
    quote: typeof source['quote'] === 'string' ? source['quote'] : undefined,
  }
}

function readSuggestion(raw: unknown): { value: string; reason: string } | null {
  if (typeof raw !== 'object' || raw === null) return null
  const source = raw as Record<string, unknown>
  const value = source['value']
  if (typeof value !== 'string' || !value) return null
  return { value, reason: typeof source['reason'] === 'string' ? source['reason'] : '' }
}

/**
 * 新建缺陷草稿建议（详设 3.5）：基于标题、重现步骤与所属模块给出分类与指派候选。
 * 表单场景产物不落库（confirmStatus = not_applicable），状态存组件本地，提交后释放。
 */
export function useBugCreateSuggest() {
  const loading = ref(false)
  const error = ref('')
  const suggestions = ref<BugClassifySuggestions>({})
  const assigneeCandidates = ref<BugAssigneeCandidate[]>([])
  const sourceRefs = ref<BugSourceRef[]>([])

  let pollTimer: ReturnType<typeof setInterval> | null = null

  function stopPolling(): void {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function requestSuggestion(input: {
    title: string
    steps?: string
    moduleId?: string
  }): Promise<void> {
    loading.value = true
    error.value = ''
    suggestions.value = {}
    assigneeCandidates.value = []
    sourceRefs.value = []
    try {
      const task = await submitAiTask('bug_classify', { draft: input }, SYNC_WAIT_SECONDS)
      if (isTerminal(task.status)) {
        await resolve(task.taskId, task.status)
      } else {
        pollTimer = setInterval(() => {
          void poll(task.taskId)
        }, POLL_INTERVAL_MS)
      }
    } catch (err) {
      // 建议失败不阻塞录入，仅提示原因（交互 2.2 状态分支）
      error.value = errorMessage(err, '获取分类建议失败')
      loading.value = false
    }
  }

  async function poll(taskId: string): Promise<void> {
    try {
      const detail: AiTaskDetail = await fetchAiTask(taskId)
      if (isTerminal(detail.status)) {
        stopPolling()
        await resolve(taskId, detail.status)
      }
    } catch {
      // 轮询网络失败不终止，下一轮继续
    }
  }

  async function resolve(taskId: string, status: AiTaskDetail['status']): Promise<void> {
    loading.value = false
    if (status !== 'succeeded') {
      if (status === 'failed') error.value = '分类建议生成失败，可重试'
      return
    }
    try {
      const artifact = await fetchAiArtifact(taskId, 'draft')
      const content = (artifact['content'] ?? {}) as Record<string, unknown>
      const rawSuggestions = (content['suggestions'] ?? {}) as Record<string, unknown>
      const next: BugClassifySuggestions = {}
      // 逐字段解析：各字段 value 类型不同，联合键写入无法通过类型收窄
      const bugType = readSuggestion(rawSuggestions['bugType'])
      if (bugType) next.bugType = { value: bugType.value as BugType, reason: bugType.reason }
      const severity = readSuggestion(rawSuggestions['severity'])
      if (severity) next.severity = { value: severity.value as BugSeverity, reason: severity.reason }
      const priority = readSuggestion(rawSuggestions['priority'])
      if (priority) next.priority = { value: priority.value as BugPriority, reason: priority.reason }
      const moduleId = readSuggestion(rawSuggestions['moduleId'])
      if (moduleId) next.moduleId = { value: moduleId.value, reason: moduleId.reason }
      // keywords 的 value 为字符串数组（详设 3.5 产物示例），与枚举字段分流解析
      const rawKeywords = rawSuggestions['keywords']
      if (typeof rawKeywords === 'object' && rawKeywords !== null) {
        const source = rawKeywords as Record<string, unknown>
        if (Array.isArray(source['value'])) {
          const values = source['value'].filter(
            (item): item is string => typeof item === 'string' && item.length > 0,
          )
          if (values.length > 0) {
            next.keywords = {
              value: values,
              reason: typeof source['reason'] === 'string' ? source['reason'] : '',
            }
          }
        }
      }
      suggestions.value = next
      const rawCandidates = Array.isArray(content['assigneeCandidates'])
        ? content['assigneeCandidates']
        : []
      assigneeCandidates.value = rawCandidates
        .map(readCandidate)
        .filter((item): item is BugAssigneeCandidate => item !== null && item.memberValid)
      const rawRefs = Array.isArray(content['sourceRefs']) ? content['sourceRefs'] : []
      sourceRefs.value = rawRefs
        .map(readSourceRef)
        .filter((item): item is BugSourceRef => item !== null)
    } catch (err) {
      error.value = errorMessage(err, '加载分类建议失败')
    }
  }

  function dispose(): void {
    stopPolling()
  }

  return { loading, error, suggestions, assigneeCandidates, sourceRefs, requestSuggestion, dispose }
}
