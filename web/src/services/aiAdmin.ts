import { del, get, post, put } from '@/services'
import type { PageResult } from '@/types'
import type {
  AiEmbeddingConfig,
  AiEmbeddingSavePayload,
  AiEmbeddingTestResult,
  AiModel,
  AiModelCreatePayload,
  AiModelList,
  AiModelTestResult,
  AiModelUpdatePayload,
  AiPromptDetail,
  AiPromptListItem,
  AiSettings,
  AiSettingsUpdatePayload,
  AiUsageQuery,
  AiUsageStatistics,
  AiUsageTask,
  AiUsageTaskQuery,
} from '@/types'

// ==================== 总开关与全局设置（详设 3.2） ====================

export function fetchAiSettings(): Promise<AiSettings> {
  return get('/ai/settings')
}

/** 部分更新：只传变更字段（C11） */
export function updateAiSettings(payload: AiSettingsUpdatePayload): Promise<AiSettings> {
  return put('/ai/settings', payload)
}

// ==================== 模型配置（详设 3.3） ====================

export function fetchAiModels(): Promise<AiModelList> {
  return get('/ai/models')
}

export function createAiModel(payload: AiModelCreatePayload): Promise<AiModel> {
  return post('/ai/models', payload)
}

/** 部分更新：apiKey 传入即替换、缺省保持原值 */
export function updateAiModel(modelId: string, payload: AiModelUpdatePayload): Promise<AiModel> {
  return put(`/ai/models/${modelId}`, payload)
}

export function deleteAiModel(modelId: string): Promise<void> {
  return del(`/ai/models/${modelId}`)
}

/** 连通性测试：单模型最小 chat 调用，失败回执 1000018105 */
export function testAiModel(modelId: string): Promise<AiModelTestResult> {
  return post(`/ai/models/${modelId}/test`)
}

// ==================== 向量 API（详设 3.4） ====================

export function fetchAiEmbedding(): Promise<AiEmbeddingConfig> {
  return get('/ai/embedding')
}

/** 单例创建或更新；维度 / 算子变更响应 requiresReindex = true */
export function saveAiEmbedding(payload: AiEmbeddingSavePayload): Promise<AiEmbeddingConfig> {
  return put('/ai/embedding', payload)
}

export function testAiEmbedding(): Promise<AiEmbeddingTestResult> {
  return post('/ai/embedding/test')
}

// ==================== 场景提示词（详设 3.5） ====================

export function fetchAiPrompts(): Promise<AiPromptListItem[]> {
  return get('/ai/prompts')
}

export function fetchAiPromptDetail(scene: string): Promise<AiPromptDetail> {
  return get(`/ai/prompts/${encodeURIComponent(scene)}`)
}

export function saveAiPrompt(scene: string, content: string): Promise<AiPromptDetail> {
  return put(`/ai/prompts/${encodeURIComponent(scene)}`, { content })
}

/** 恢复内置默认，返回重置后的详情 */
export function resetAiPrompt(scene: string): Promise<AiPromptDetail> {
  return post(`/ai/prompts/${encodeURIComponent(scene)}/reset`)
}

// ==================== 用量统计（详设 3.7） ====================

export function fetchAiUsageStatistics(query: AiUsageQuery): Promise<AiUsageStatistics> {
  return get('/ai/usage/statistics', { ...query })
}

export function fetchAiUsageTasks(query: AiUsageTaskQuery): Promise<PageResult<AiUsageTask>> {
  return get('/ai/usage/tasks', { ...query })
}
