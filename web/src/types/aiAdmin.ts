/**
 * AI 配置中心类型（详设 02-ai-infra-overview 3.2–3.7）：
 * 密钥类字段接口只回 keyConfigured 标记，明文仅在提交载荷中出现、不入 store 缓存。
 */

/** 全局设置（3.2）：available = enabled && modelReady，供业务端入口显隐 */
export interface AiSettings {
  enabled: boolean
  defaultModelId: string | null
  defaultModelName: string | null
  taskTimeoutSeconds: number
  taskMaxRetries: number
  modelReady: boolean
  embeddingReady: boolean
  available: boolean
}

/** 设置更新载荷（3.2 部分更新：只传变更字段） */
export interface AiSettingsUpdatePayload {
  enabled?: boolean
  defaultModelId?: string
  taskTimeoutSeconds?: number
  taskMaxRetries?: number
}

/** 模型能力（3.3：capabilities ⊆ {chat, vision, embedding}） */
export type AiModelCapability = 'chat' | 'vision' | 'embedding'

/** 最近一次连通性测试；未测过时 lastTest 为 null（未测 = 未知态） */
export interface AiConnectivityTest {
  success: boolean
  latencyMs: number | null
  msg: string | null
  /** 测试时间（ISO 日期时间串） */
  at: string | null
}

/** 模型配置（3.3） */
export interface AiModel {
  id: string
  name: string
  provider: string
  baseUrl: string
  modelName: string
  capabilities: AiModelCapability[]
  priority: number
  enabled: boolean
  /** 每百万 token 输入单价；未配置为 null（计费按 0 处理） */
  inputPrice: number | null
  /** 每百万 token 输出单价 */
  outputPrice: number | null
  keyConfigured: boolean
  lastTest: AiConnectivityTest | null
}

/** 模型列表（3.3，全量返回不分页） */
export interface AiModelList {
  list: AiModel[]
  total: number
}

/** 模型创建载荷（3.3：apiKey / modelName 必填，单价 ≥ 0） */
export interface AiModelCreatePayload {
  name: string
  provider: string
  baseUrl: string
  apiKey: string
  modelName: string
  capabilities: AiModelCapability[]
  priority?: number
  enabled?: boolean
  inputPrice?: number
  outputPrice?: number
}

/** 模型更新载荷（3.3 部分更新：apiKey 传入即替换、不传保持原值） */
export interface AiModelUpdatePayload {
  name?: string
  provider?: string
  baseUrl?: string
  apiKey?: string
  modelName?: string
  capabilities?: AiModelCapability[]
  priority?: number
  enabled?: boolean
  inputPrice?: number
  outputPrice?: number
}

/** 模型连通性测试结果（3.3） */
export interface AiModelTestResult {
  success: boolean
  latencyMs: number | null
  msg: string | null
}

/** 向量 API 配置（3.4，单例） */
export interface AiEmbeddingConfig {
  provider: string
  baseUrl: string
  embeddingModel: string
  dimensions: number
  /** 算子：cosine / l2 / ip */
  operator: string
  /** 索引类型（只读，保存 DTO 不含此字段） */
  indexType: string
  enabled: boolean
  keyConfigured: boolean
  /** 历史维度版本；维度 / 算子变更时旧配置追加于此 */
  versions: Record<string, unknown>[]
  /** 维度 / 算子相对原值变化 → 需全量重建（引导条触发条件） */
  requiresReindex: boolean
  lastTest: AiConnectivityTest | null
}

/** 向量配置保存载荷（3.4：dimensions ∈ [64, 4096]，apiKey 为空则不修改） */
export interface AiEmbeddingSavePayload {
  provider?: string
  baseUrl?: string
  apiKey?: string
  embeddingModel?: string
  dimensions?: number
  operator?: string
  enabled?: boolean
}

/** 向量 API 连通性测试结果（3.4） */
export interface AiEmbeddingTestResult {
  success: boolean
  dimensions: number | null
  latencyMs: number | null
  msg: string | null
}

/** 提示词来源：default = 内置默认，custom = 已自定义 */
export type AiPromptSource = 'default' | 'custom'

/** 提示词列表项（3.5，按 scene 升序） */
export interface AiPromptListItem {
  scene: string
  name: string
  summary: string
  source: AiPromptSource
  /** 从未自定义时为 null */
  updatedByName: string | null
  updatedAt: string | null
}

/** 提示词可用变量（3.5：content 中 {{变量}} 必须全部在此清单内） */
export interface AiPromptVariable {
  name: string
  desc: string
  required: boolean
}

/** 提示词详情（3.5：无自定义行时返回内置默认内容，source = default） */
export interface AiPromptDetail {
  scene: string
  name: string
  content: string
  variables: AiPromptVariable[]
  source: AiPromptSource
  version: number
  updatedAt: string | null
}

/** 用量分组维度（3.7：day 默认 / model / scene / callType） */
export type AiUsageGroupBy = 'day' | 'model' | 'scene' | 'callType'

/** 用量聚合查询（3.7：UTC 时间范围，缺省最近 30 天，跨度 ≤ 366 天） */
export interface AiUsageQuery {
  /** YYYY-MM-DD */
  from?: string
  /** YYYY-MM-DD */
  to?: string
  groupBy?: AiUsageGroupBy
}

/** 用量汇总（3.7；successRate ∈ [0, 1]，totalCost 为金额） */
export interface AiUsageSummary {
  totalCalls: number
  failedCalls: number
  successRate: number
  totalTokens: number
  avgLatencyMs: number
  totalCost: number
}

/** 用量序列项（3.7：day 分组 key 为日期已补 0；model / scene 分组附 keyName） */
export interface AiUsageSeriesItem {
  key: string
  keyName: string | null
  calls: number
  failed: number
  tokens: number
  avgLatencyMs: number
  cost: number
}

/** 用量聚合响应（3.7） */
export interface AiUsageStatistics {
  summary: AiUsageSummary
  series: AiUsageSeriesItem[]
}

/** 用量下钻查询（3.7） */
export interface AiUsageTaskQuery {
  pageNo: number
  pageSize: number
  /** YYYY-MM-DD */
  from?: string
  /** YYYY-MM-DD */
  to?: string
  /** 按模型下钻（series.key） */
  modelId?: string
  /** 按提示词场景下钻（series.key） */
  scene?: string
  /** success / failed */
  status?: string
}

/** 用量下钻任务行（3.7）：连通性测试等无任务调用 taskId 为 null，不可跳转详情 */
export interface AiUsageTask {
  taskId: string | null
  /** 任务类型；无任务调用为 null */
  type: string | null
  /** 用量记录状态：success / failed */
  status: string
  modelName: string | null
  scene: string | null
  totalTokens: number
  latencyMs: number
  createdAt: string
}
