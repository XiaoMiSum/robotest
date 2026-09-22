// ==================== 接口测试 · 项目设置（环境管理） ====================

export type ApiEnvironmentScope = 'project' | 'global'

/** 处理器类别：作用于该环境下所有请求的前置/后置处理器 */
export type ApiProcessorType = 'preprocessor' | 'postprocessor'

export interface ApiHeaderItem {
  key: string
  value: string
  enabled: boolean
}

export interface ApiHttpConfigPayload {
  name: string
  refName?: string
  baseUrl?: string
  headers?: ApiHeaderItem[]
  isDefault?: boolean
}

export interface ApiHttpConfig extends ApiHttpConfigPayload {
  id?: string
}

/** 数据源独立新增/编辑负载（3.1.12），响应 ApiDataSource 附 id */
export interface ApiDataSourcePayload {
  name: string
  refName?: string
  driver?: string
  url?: string
  connectionProperties?: Record<string, unknown>
  maxPoolSize?: number
  isDefault?: boolean
}

export interface ApiVariablePayload {
  name: string
  value?: string
  description?: string
}

/** 变量取值明文回显；hasValue 标识服务端是否已配置（详细设计 3.1.9） */
export interface ApiVariable extends ApiVariablePayload {
  id?: string
  hasValue: boolean
}

export interface ApiDataSource extends ApiDataSourcePayload {
  id?: string
}

export interface ApiProcessor {
  id?: string
  processorType: ApiProcessorType
  name: string
  config?: Record<string, unknown>
  sortOrder?: number
  enabled: boolean
}

export interface ApiEnvironmentListItem {
  id: string
  name: string
  description?: string
  /** 全局环境为 V1.2 预留扩展位，后端当前恒为 project 且暂未下发该字段 */
  scope?: ApiEnvironmentScope
  isDefault: boolean
  sortOrder: number
  httpConfigCount: number
  variableCount: number
  dataSourceCount: number
  processorCount: number
}

export interface ApiEnvironmentDetail {
  id: string
  name: string
  description?: string
  scope: ApiEnvironmentScope
  isDefault: boolean
  sortOrder: number
  httpConfigs: ApiHttpConfig[]
  variables: ApiVariable[]
  dataSources: ApiDataSource[]
  processors: ApiProcessor[]
}

/** 环境聚合保存：子资源全量替换语义，缺省段落视为清空 */
export interface ApiEnvironmentSaveReq {
  name: string
  description?: string
  sortOrder?: number
  isDefault?: boolean
  httpConfigs?: ApiHttpConfigPayload[]
  variables?: ApiVariablePayload[]
  dataSources?: {
    name: string
    refName?: string
    driver?: string
    url?: string
    connectionProperties?: Record<string, unknown>
    maxPoolSize?: number
  }[]
  processors?: {
    processorType: ApiProcessorType
    name: string
    config?: Record<string, unknown>
    sortOrder?: number
    enabled?: boolean
  }[]
}

export interface ApiIdResp {
  id: string
}

export interface ApiSetDefaultResp {
  success: boolean
}

export interface ApiImportResult {
  createdCount: number
  overwrittenCount: number
  skippedCount: number
}

export interface ApiDataSourceTestResp {
  success: boolean
  message: string
  databaseVersion?: string
}

export interface ApiHttpTestResp {
  success: boolean
  message: string
  statusCode?: number
  durationMs?: number
}

// ==================== 快速调试（详细设计 3.1） ====================

export type ApiDebugBodyType = 'none' | 'json' | 'form' | 'raw' | 'binary'

/** 键值对条目（请求头 / Query 参数共用结构） */
export interface ApiDebugKeyValue {
  key: string
  value: string
  enabled: boolean
  /** 行描述（Postman 风格 Params/Headers），提交执行时剥离 */
  description?: string
}

export interface ApiDebugRequestBody {
  type: ApiDebugBodyType
  /** json→对象、form→对象、raw/binary→字符串 */
  content?: unknown
}

export interface ApiDebugExecuteReq {
  protocol?: 'http'
  method: string
  url: string
  headers?: ApiDebugKeyValue[]
  body?: ApiDebugRequestBody
  params?: ApiDebugKeyValue[]
  processors?: Record<string, unknown>[]
  timeoutMs?: number
  environmentId?: string
}

export interface ApiDebugExecuteResp {
  debugRecordId: string
  status: 'success' | 'failed' | 'error'
  responseStatus?: number
  responseHeaders?: Record<string, string>
  responseBody?: unknown
  durationMs?: number
  size?: number
  errorMessage?: string
}

export interface ApiDebugRecordItem {
  id: string
  name?: string
  method: string
  url?: string
  status: ApiDebugExecuteResp['status']
  responseStatus?: number
  durationMs?: number
  executedAt: string
}

export interface ApiDebugRestoreResp {
  debugRecordId: string
  request: {
    protocol?: string
    method?: string
    url?: string
    headers?: ApiDebugKeyValue[] | null
    body?: { type?: string; content?: unknown } | null
    params?: ApiDebugKeyValue[] | null
  }
  response: {
    statusCode?: number
    headers?: Record<string, string> | null
    body?: unknown
    elapsed?: number
    size?: number
  }
  createdAt: string
}

/** 认证配置：提交时由前端换算为请求头，不单独持久化（详细设计 5.1） */
export interface ApiDebugAuth {
  type: 'none' | 'basic' | 'digest' | 'bearer' | 'apiKey'
  username?: string
  password?: string
  /** Bearer Token（type='bearer'） */
  token?: string
  /** API Key 键名（type='apiKey'），缺省换算为 X-API-Key */
  apiKeyName?: string
  apiKeyValue?: string
}

/** raw 请求体子类型（Postman raw 类型选择器） */
export type ApiDebugRawSubtype = 'text' | 'json' | 'xml' | 'html' | 'javascript'

/** 调试请求体激活类型行；请求体数据存于 bodies 对应槽位，切换保留 */
export type ApiDebugBodyKind = 'none' | 'urlencoded' | 'raw'

/** 调试标签页状态（debugModel 状态机维护） */
export interface DebugTab {
  id: string
  name: string
  method: string
  url: string
  headers: ApiDebugKeyValue[]
  params: ApiDebugKeyValue[]
  /** 各请求体类型内容独立缓存（SRS 3.1 业务规则）；bodyType 记录当前激活类型 */
  bodies: {
    /** x-www-form-urlencoded 键值对 */
    urlencoded: ApiDebugKeyValue[]
    /** raw 文本 + 子类型 */
    raw: { text: string; subtype: ApiDebugRawSubtype } | null
  }
  bodyType: ApiDebugBodyKind
  auth: ApiDebugAuth
  responseTimeoutMs: number
  response: ApiDebugExecuteResp | null
}

/** 新建接口（mode=create）/归属已有接口（mode=attach）保存请求（详细设计 3.1.3） */
export interface ApiDebugSaveAsInterfaceReq {
  mode: 'create' | 'attach'
  /** create 必填 */
  name?: string
  moduleId?: string
  /** attach 必填 */
  interfaceId?: string
  /** attach 必填，乐观锁匹配（详细设计 3.1.3） */
  changeVersion?: number
  /** 从 UI 表单构建的请求快照（method/url/headers/params/body），取代原先从 debug record 读取 */
  request?: {
    method: string
    url: string
    headers?: ApiDebugKeyValue[]
    params?: ApiDebugKeyValue[]
    body?: ApiDebugRequestBody
    auth?: ApiDebugAuth
  }
  /** 响应示例（status/headers/body），有响应时由前端填充 */
  responseExample?: { status: number; headers?: Record<string, string> | null; body?: unknown }
}

export interface ApiDebugSaveAsInterfaceResp {
  interfaceId: string
}

// ==================== 接口管理（接口管理详细设计 3.1–3.4） ====================

export type ApiInterfaceView = 'all' | 'followed' | 'created'
export type ApiInterfaceStatus = 'enabled' | 'disabled'
/** V1.2 仅 http（详细设计 6.3），jdbc 随场景模块梯队三 */
export type ApiInterfaceProtocol = 'http'
export type ApiInterfaceMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS'

/** 列表行（3.1.1） */
export interface ApiInterfaceItem {
  id: string
  name: string
  protocol: ApiInterfaceProtocol
  method: string
  path: string
  moduleId?: string | null
  status: ApiInterfaceStatus
  referenceCount: number
  changeVersion: number
  followed: boolean
  updatedAt: string
}

/** 详情（3.1.2）：params/restParams 与文档字段名对齐 */
export interface ApiInterfaceDetail {
  id: string
  name: string
  protocol: ApiInterfaceProtocol
  method: string
  path: string
  description?: string | null
  moduleId?: string | null
  headers: ApiDebugKeyValue[] | null
  body: { type?: string; content?: unknown } | null
  params: ApiDebugKeyValue[] | null
  restParams: ApiDebugKeyValue[] | null
  auth?: Record<string, unknown> | null
  status: ApiInterfaceStatus
  changeVersion: number
  responseExample?: Record<string, unknown> | null
  referenceCount: number
  followed: boolean
  createdAt: string
  updatedAt: string
  /** 响应验证器 [{..}]，仅定义存储 */
  validators?: Record<string, unknown>[]
  /** 响应提取器 [{..}]，仅定义存储 */
  extractors?: Record<string, unknown>[]
}

export interface ApiInterfaceCreateReq {
  name: string
  protocol?: ApiInterfaceProtocol
  method: string
  path: string
  description?: string
  moduleId?: string | null
  headers?: ApiDebugKeyValue[]
  body?: { type?: string; content?: unknown }
  params?: ApiDebugKeyValue[]
  restParams?: ApiDebugKeyValue[]
  auth?: Record<string, unknown>
  status?: ApiInterfaceStatus
  responseExample?: Record<string, unknown>
  /** 响应验证器 [{..}]，仅定义存储 */
  validators?: Record<string, unknown>[]
  /** 响应提取器 [{..}]，仅定义存储 */
  extractors?: Record<string, unknown>[]
}

export interface ApiInterfaceUpdateReq extends ApiInterfaceCreateReq {
  /** 乐观锁版本，服务端不一致返回 7105 */
  changeVersion: number
}

/** 变更历史条目（3.1.13） */
export interface ApiInterfaceChangeLogItem {
  id: string
  changeVersion: number
  action: string
  summary?: string
  operatorId?: string | null
  createdAt: string
}

/** 引用情况（3.1.7）；场景/Mock 未上线恒为空列表 */
export interface ApiInterfaceReferences {
  scenes: { id: string; name: string }[]
  mocks: { id: string; name: string }[]
}

export interface ApiInterfaceImportError {
  source: string
  message: string
}

export interface ApiInterfaceImportResult {
  importHistoryId: string
  summary: Record<string, number>
  errors: ApiInterfaceImportError[]
}

export interface ApiInterfaceImportPreviewItem {
  name?: string
  method?: string
  path?: string
  action: 'create' | 'update' | 'skip'
  conflict: boolean
}

export interface ApiInterfaceImportPreview {
  items: ApiInterfaceImportPreviewItem[]
  summary: Record<string, number>
}

// ==================== Mock 服务 (3.3) ====================

/** Mock 匹配规则类型 */
export type ApiMockMatchRuleType = 'header' | 'param' | 'body'

/** Mock 匹配规则条目 */
export interface ApiMockMatchRule {
  type: ApiMockMatchRuleType
  name: string
  value: string
}

/** Mock 响应体类型 */
export type ApiMockBodyType = 'json' | 'text' | 'xml' | 'binary'

/** Mock 列表项 */
export interface ApiMockItem {
  id: string
  name: string
  interfaceId: string | null
  method: string
  path: string
  priority: number
  enabled: boolean
  followApi: boolean
  responseStatus: number
  hitCount: number
  lastHitAt: string | null
  updatedAt: string
}

/** Mock 详情 */
export interface ApiMockDetail {
  id: string
  name: string
  interfaceId: string | null
  interfaceName: string | null
  method: string
  path: string
  priority: number
  description: string | null
  matchRules: ApiMockMatchRule[]
  enabled: boolean
  followApi: boolean
  responseStatus: number
  responseHeaders: Record<string, string> | null
  responseBodyType: ApiMockBodyType
  responseBody: string | null
  delayMs: number
  hitCount: number
  lastHitAt: string | null
  groupSize: number
}

/** Mock 创建/更新请求体 */
export interface ApiMockSavePayload {
  interfaceId?: string | null
  name: string
  description?: string | null
  method: string
  path: string
  priority?: number | null
  matchRules?: ApiMockMatchRule[]
  enabled: boolean
  followApi?: boolean
  responseStatus: number
  responseHeaders?: Record<string, string> | null
  responseBodyType?: ApiMockBodyType
  responseBody?: string | null
  delayMs?: number
}

/** Mock 调试请求 */
export interface ApiMockDebugRequest {
  headers?: Record<string, string>
  body?: unknown
}

/** Mock 调试响应 */
export interface ApiMockDebugResponse {
  status: number
  headers: Record<string, unknown>
  body: unknown
  durationMs: number
}

/** Mock 访问地址 */
export interface ApiMockAddress {
  mockUrl: string
  method: string
  headers: Record<string, unknown>
}

/** Mock 批量启停请求 */
export interface ApiMockBatchTogglePayload {
  ids: string[]
  enabled: boolean
}

/** Mock 批量启停响应 */
export interface ApiMockBatchToggleResponse {
  success: boolean
  updatedCount: number
}

// ==================== 接口测试 · 函数管理与函数助手 ====================

/** 函数类型：builtin（Ryze 内置元数据）/ custom（用户创建的自定义函数） */
export type ApiFunctionType = 'builtin' | 'custom'

/** 函数作用域：project（项目）/ workspace（空间）/ global（公共） */
export type ApiFunctionScope = 'project' | 'workspace' | 'global'

/** 内置函数参数说明 */
export interface ApiBuiltinParam {
  name: string
  required: boolean
  description: string
}

/** 内置函数项 */
export interface ApiBuiltinFunction {
  name: string
  signature: string
  description: string
  params: ApiBuiltinParam[]
  example: string
  builtin: boolean
}

/** 内置函数分组 */
export interface ApiBuiltinFunctionGroup {
  name: string
  functions: ApiBuiltinFunction[]
}

/** 自定义函数列表项 */
export interface ApiCustomFunctionListItem {
  id: string
  type: ApiFunctionType
  scope: ApiFunctionScope
  name: string
  description: string | null
  paramsDesc: string | null
  enabled: boolean
  updatedAt: string
}

/** 自定义函数详情（含脚本体） */
export interface ApiCustomFunctionDetail extends ApiCustomFunctionListItem {
  script: string
}

/** 自定义函数创建/更新请求 */
export interface ApiCustomFunctionSaveReq {
  name: string
  description?: string
  paramsDesc?: string
  script: string
  scope?: ApiFunctionScope
}

/** 函数试算请求 */
export interface ApiFunctionEvaluateReq {
  expression: string
}

/** 函数试算响应 */
export interface ApiFunctionEvaluateResp {
  result: string
  durationMs: number
}

// ==================== 接口测试 · 公共组件 ====================

/** 公共组件作用域：project（项目）/ workspace（空间）/ global（公共） */
export type ApiComponentScope = 'project' | 'workspace' | 'global'

/** 公共组件类型：前置处理器 / 后置处理器 / 验证器 / 提取器 */
export type ApiComponentType = 'preprocessor' | 'postprocessor' | 'validator' | 'extractor'

/** 公共组件列表项 */
export interface ApiComponentListItem {
  id: string
  scope: ApiComponentScope
  type: ApiComponentType
  name: string
  description: string | null
  /** 组件排序号（仅处理器类组件生效，顶层字段，config 不承载） */
  sortOrder: number
  /** 组件配置内容（JSON 字符串） */
  config: string | null
  enabled: boolean
  updatedAt: string
}

/** 公共组件新建/编辑请求 */
export interface ApiComponentSaveReq {
  type: ApiComponentType
  name: string
  description?: string
  scope?: ApiComponentScope
  sortOrder?: number
  config?: Record<string, unknown>
}

/** 公共组件批量操作请求 */
export interface ApiComponentBatchReq {
  ids: string[]
}
