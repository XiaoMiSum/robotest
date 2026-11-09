import { get, post } from '@/services'
import type {
  AiProposalConfirmPayload,
  AiProposalConfirmReceipt,
  AiStatus,
  AiTaskDetail,
  AiTaskEventsQuery,
  AiTaskEventsResult,
  AiTaskItem,
  AiTaskPageQuery,
  AiTaskProposalPayload,
  AiTaskType,
} from '@/types'
import type { PageResult } from '@/types'

/** 业务端 AI 可用性（详设 3.2）：登录即可，路径不属业务域，请求上下文按 scope=none 不带头 */
export function fetchAiStatus(): Promise<AiStatus> {
  return get('/ai/status')
}

/** 提交任务（详设 3.6.2，创建即启动）：input 按任务类型分发校验；waitSeconds ∈ [0,10] 同步等待快路径 */
export function submitAiTask(
  type: AiTaskType,
  input: Record<string, unknown> = {},
  waitSeconds = 0,
): Promise<AiTaskItem> {
  return post('/ai/tasks', { type, input, waitSeconds })
}

/** 任务列表（详设 3.6.3）：项目范围经 X-Active-Project 头过滤，未附带则返回本人提交的任务 */
export function fetchAiTasks(params: AiTaskPageQuery): Promise<PageResult<AiTaskItem>> {
  return get('/ai/tasks', { ...params })
}

/** 进行中任务计数（详设 3.6.3）：顶栏指示器轮询专用，status = running 语义 */
export function fetchAiRunningCount(): Promise<{ count: number }> {
  return get('/ai/tasks/running-count')
}

/** 任务详情（详设 3.6.3）：最新执行 + 提案清单摘要 + documentMeta */
export function fetchAiTask(taskId: string): Promise<AiTaskDetail> {
  return get(`/ai/tasks/${taskId}`)
}

/** 提案详情（详设 3.6.3）：完整内容，键在最新一次执行的提案中定位 */
export function fetchAiProposal(
  taskId: string,
  proposalKey: string,
): Promise<AiTaskProposalPayload> {
  return get(`/ai/tasks/${taskId}/proposals/${encodeURIComponent(proposalKey)}`)
}

/** 轨迹查询（详设 3.6.4）：afterSeq 游标增量，默认 200 上限 500 */
export function fetchAiTaskEvents(
  taskId: string,
  executionId: string,
  params: AiTaskEventsQuery = {},
): Promise<AiTaskEventsResult> {
  return get(`/ai/tasks/${taskId}/executions/${executionId}/events`, { ...params })
}

/** 终止执行（详设 3.6.4）：仅 running / waiting_confirmation 可终止，否则 1000018127 */
export function abortAiExecution(taskId: string, executionId: string): Promise<AiTaskDetail> {
  return post(`/ai/tasks/${taskId}/executions/${executionId}/abort`)
}

/** 再次执行（详设 3.6.4）：沿用同一任务新建执行实例，反馈并入任务输入随新执行下发 */
export function reExecuteAiTask(taskId: string, feedback?: string): Promise<AiTaskDetail> {
  return post(`/ai/tasks/${taskId}/executions`, feedback ? { feedback } : {})
}

/** 提案确认（详设 3.6.5）：批量逐项，整体 200 + 逐项成败回执 */
export function confirmAiProposals(
  taskId: string,
  payload: AiProposalConfirmPayload,
): Promise<AiProposalConfirmReceipt> {
  return post(`/ai/tasks/${taskId}/proposals/confirm`, payload)
}
