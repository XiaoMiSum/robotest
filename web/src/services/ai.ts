import api from '@/services'
import type {
  AiBugClusterSnapshot,
  AiBugDedupResult,
  AiBugSuggestion,
  AiCasePlanRecommendResult,
  AiMissingPointResult,
  AiPlanOrderComputeResp,
  AiPlanOrderQueryResp,
  AiPlanOrderReasonResp,
  AiReviewSummary,
  AiStatus,
  AiTask,
} from '@/types'

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

// ==================== 遗漏测试点分析（项目级，US-AI-007） ====================

export interface AiMissingPointReq {
  /** 分析关键词（3.3）：直接标题检索；与 text/requirementIds 至少一项非空 */
  keywords?: string[]
  /** 需求文本：可空，后端先抽取关键词再检索 */
  text?: string
  /** 需求池条目 ID 列表 */
  requirementIds?: string[]
}

/** 发起遗漏测试点分析（3.3 同步长调用）：返回 AbortController 供 [取消] 中止请求 */
export function analyzeMissingPoints(
  data: AiMissingPointReq,
): { controller: AbortController; promise: Promise<AiMissingPointResult> } {
  // 长调用：覆盖实例默认 15s 超时，放宽至 70s，并支持面板 [取消] 中止
  const controller = new AbortController()
  const promise = api.post('/project/ai/cases/missing-points', data, {
    timeout: 70000,
    signal: controller.signal,
  }) as unknown as Promise<AiMissingPointResult>
  return { controller, promise }
}

// ==================== 用例规划智能推荐（项目级，US-AI-018） ====================

export interface AiCasePlanRecommendReq {
  /** 需求文本：可空；与 requirementIds 至少一项非空 */
  text?: string
  /** 需求池条目 ID 列表 */
  requirementIds?: string[]
  /** 当前评审/计划已纳入的用例节点 ID，推荐结果排除这些用例（不重复推荐） */
  excludeCaseNodeIds?: string[]
}

/** 发起用例规划智能推荐（3.5 同步长调用）：返回 AbortController 供 [取消] 中止请求 */
export function planRecommend(
  data: AiCasePlanRecommendReq,
): { controller: AbortController; promise: Promise<AiCasePlanRecommendResult> } {
  // 长调用：覆盖实例默认 15s 超时，放宽至 70s，并支持面板 [取消] 中止（约定同 3.3）
  const controller = new AbortController()
  const promise = api.post('/project/ai/cases/plan-recommend', data, {
    timeout: 70000,
    signal: controller.signal,
  }) as unknown as Promise<AiCasePlanRecommendResult>
  return { controller, promise }
}

// ==================== 执行顺序推荐（项目级，US-AI-017） ====================

/** 计算执行顺序推荐（3.4.1 同步长调用）：返回 AbortController 供 [取消] 中止请求 */
export function planOrderRecommend(
  planId: string,
): { controller: AbortController; promise: Promise<AiPlanOrderComputeResp> } {
  // 与后端 LLM 60s 读超时保持同量级，前端放宽至 70s 并支持 [取消] 中止
  const controller = new AbortController()
  const promise = api.post(`/project/ai/plans/${planId}/order-recommend`, null, {
    timeout: 70000,
    signal: controller.signal,
  }) as unknown as Promise<AiPlanOrderComputeResp>
  return { controller, promise }
}

/** 查询最近一次执行顺序推荐结果（3.4.2，无记录 result 为 null；stale=true 表示计划快照已重新同步需重算） */
export function fetchPlanOrderRecommend(planId: string): Promise<AiPlanOrderQueryResp> {
  return get(`/project/ai/plans/${planId}/order-recommend`)
}

/** 生成单条推荐理由（3.4.3 同步长调用）：返回 AbortController 供 [取消] 中止请求 */
export function planOrderReason(
  planId: string,
  snapshotNodeId: string,
): { controller: AbortController; promise: Promise<AiPlanOrderReasonResp> } {
  const controller = new AbortController()
  const promise = api.post(
    `/project/ai/plans/${planId}/order-reason`,
    { snapshotNodeId },
    {
      timeout: 70000,
      signal: controller.signal,
    },
  ) as unknown as Promise<AiPlanOrderReasonResp>
  return { controller, promise }
}

// ==================== 缺陷 AI 能力（US-AI-008/009/010，详细设计 3.1–3.3） ====================

/** 缺陷表单智能建议（3.1，同步） */
export function suggestBugForm(data: {
  title: string
  reproSteps?: string
}): Promise<AiBugSuggestion> {
  return postData('/project/ai/bugs/suggest', data)
}

/** 缺陷语义查重（3.2，同步检索；编辑既有缺陷时排除自身） */
export function dedupBugs(data: {
  title: string
  reproSteps?: string
  excludeBugId?: string
}): Promise<AiBugDedupResult> {
  return postData('/project/ai/bugs/dedup', data)
}

/** 发起缺陷聚类分析（3.3.1）：返回异步任务 ID，前端 2s 轮询任务状态 */
export interface AiBugClusteringStartResp {
  taskId: string
}

export function startBugClustering(): Promise<AiBugClusteringStartResp> {
  return post('/project/ai/bugs/clustering')
}

/** 查询缺陷聚类最近一次任务（3.3.2，无记录返回 null；running/cancelled 亦含部分快照） */
export function fetchLatestBugClustering(): Promise<AiTask | null> {
  return get('/project/ai/bugs/clustering/latest')
}

/** 聚类任务快照强类型读取（task.result 为松散对象，集中一处断言） */
export function toBugClusterSnapshot(result: Record<string, unknown> | null): AiBugClusterSnapshot | null {
  return result as unknown as AiBugClusterSnapshot | null
}
