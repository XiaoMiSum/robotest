import { get, post } from '@/services'
import type {
  AiArtifactConfirmPayload,
  AiArtifactConfirmReceipt,
  AiStatus,
  AiTaskDetail,
  AiTaskItem,
  AiTaskPageQuery,
} from '@/types'
import type { PageResult } from '@/types'

/** 业务端 AI 可用性（详设 3.2）：登录即可，路径不属业务域，请求上下文按 scope=none 不带头 */
export function fetchAiStatus(): Promise<AiStatus> {
  return get('/ai/status')
}

/** 任务列表（详设 3.6.3）：项目范围经 X-Active-Project 头过滤，未附带则返回本人提交的任务 */
export function fetchAiTasks(params: AiTaskPageQuery): Promise<PageResult<AiTaskItem>> {
  return get('/ai/tasks', { ...params })
}

/** 任务详情（详设 3.6.3）：succeeded 时附产物清单摘要，不返回 input / result 明细 */
export function fetchAiTask(taskId: string): Promise<AiTaskDetail> {
  return get(`/ai/tasks/${taskId}`)
}

/** 产物内容（详设 3.6.4）：产物不存在回执 1000018112 */
export function fetchAiArtifact(
  taskId: string,
  artifactKey: string,
): Promise<Record<string, unknown>> {
  return get(`/ai/tasks/${taskId}/artifacts/${encodeURIComponent(artifactKey)}`)
}

/** 取消任务（详设 3.6.6）：仅 pending / running 可取消，终态回执 1000018111 */
export function cancelAiTask(taskId: string): Promise<AiTaskDetail> {
  return post(`/ai/tasks/${taskId}/cancel`)
}

/** 重试任务（详设 3.6.6）：failed 重试生成新任务并回到排队 */
export function retryAiTask(taskId: string): Promise<AiTaskDetail> {
  return post(`/ai/tasks/${taskId}/retry`)
}

/** 产物确认（详设 3.6.5）：批量逐项，整体 200 + 逐项成败回执 */
export function confirmAiArtifacts(
  taskId: string,
  payload: AiArtifactConfirmPayload,
): Promise<AiArtifactConfirmReceipt> {
  return post(`/ai/tasks/${taskId}/artifacts/confirm`, payload)
}
