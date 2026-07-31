import api from '@/services'
import type { AiStatus, AiTask } from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言（C1：unknown + 断言）
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string): Promise<T> {
  return api.post(url) as unknown as Promise<T>
}

// ==================== AI 能力开关（工作空间级） ====================

export function fetchAiStatus(): Promise<AiStatus> {
  return get('/workspace/ai/status')
}

// ==================== AI 异步任务通用接口（项目级） ====================

export function fetchAiTask(taskId: string): Promise<AiTask> {
  return get(`/project/ai/tasks/${taskId}`)
}

export function cancelAiTask(taskId: string): Promise<void> {
  return post(`/project/ai/tasks/${taskId}/cancel`)
}

export function retryAiTask(taskId: string): Promise<void> {
  return post(`/project/ai/tasks/${taskId}/retry`)
}
