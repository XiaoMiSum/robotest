import api from '@/services'
import type { AiReviewSummary, AiStatus, AiTask } from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言（C1：unknown + 断言）
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string): Promise<T> {
  return api.post(url) as unknown as Promise<T>
}
function postData<T>(url: string, data: Record<string, unknown>): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
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

// ==================== AI 评审摘要（项目级） ====================

/** 查询最近一次成功摘要（无则 null）；生成走 SSE，见 useAiStream */
export function fetchReviewSummary(reviewId: string): Promise<AiReviewSummary | null> {
  return get(`/project/ai/reviews/${reviewId}/summary`)
}

/** 发起评审检查响应（3.1.1）：返回异步任务 ID，前端轮询任务状态 */
export interface AiReviewCheckStartResp {
  taskId: string
}

/** 发起评审一键检查，创建异步任务并返回任务 ID（US-AI-005，3.1.1） */
export function startReviewCheck(reviewId: string): Promise<AiReviewCheckStartResp> {
  return post(`/project/ai/reviews/${reviewId}/check`)
}

/** 查询评审最近一次检查任务（无记录返回 null；running/cancelled 亦含已产出部分结果，3.1.2） */
export function fetchReviewCheckResult(reviewId: string): Promise<AiTask | null> {
  return get(`/project/ai/reviews/${reviewId}/check-result`)
}

// ==================== 优先级推荐（项目级，US-AI-003） ====================

export interface AiPriorityRecommendResp {
  /** 推荐优先级 P0-P3，无推荐时为 null（前端静默忽略） */
  priority: string | null
  /** 推荐来源：rule（关键词规则命中）/ llm（模型兜底） */
  source: 'rule' | 'llm'
}

/** 手工标记用例时同步推荐优先级（rule 命中瞬时返回，LLM 兜底失败返回 null） */
export function recommendPriority(title: string, ancestorTitles: string[]): Promise<AiPriorityRecommendResp> {
  return postData('/project/ai/cases/priority-recommend', { title, ancestorTitles })
}
