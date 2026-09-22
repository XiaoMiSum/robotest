// ==================== 接口测试 · 测试场景（测试场景详细设计 3.1–3.12） ====================

/** 场景列表项（3.1.1） */
export interface ApiScenePageItem {
  id: string
  name: string
  moduleId?: string | null
  environmentId?: string | null
  priority?: string | null
  status?: string
  stepCount: number
  lastExecutedAt?: string | null
  lastStatus?: string | null
  updatedAt: string
  followed?: boolean
}

/** 场景详情（3.1.2） */
export interface ApiSceneDetail {
  id: string
  name: string
  moduleId?: string | null
  description?: string | null
  environmentId?: string | null
  priority?: string | null
  status?: string
  followed?: boolean
  variables: ApiSceneVariableItem[]
  processors: Record<string, unknown>[]
  changeVersion: number
  steps: ApiSceneStepItem[]
}

/** 场景步骤（3.1.2 内嵌 steps） */
export interface ApiSceneStepItem {
  id: string
  name: string
  stepType: string
  sortOrder: number
  enabled: boolean
  sourceType: string
  sourceId?: string | null
  sourceInterfaceId?: string | null
  sourceInterfaceName?: string | null
  sourceMissing?: boolean
  requestConfig: Record<string, unknown>
  variables: ApiSceneStepVariableItem[]
  processors: Record<string, unknown>[]
  validators: Record<string, unknown>[]
  extractors: Record<string, unknown>[]
}

/** 场景变量条目 */
export interface ApiSceneVariableItem {
  name: string
  value?: string
  description?: string
}

/** 步骤级变量条目（3.4.1） */
export interface ApiSceneStepVariableItem {
  id: string
  name: string
  value?: string
  source: string
  interfaceVariableId?: string | null
  description?: string
  sortOrder: number
}

/** 场景创建请求（3.1.3） */
export interface ApiSceneCreateReq {
  name: string
  moduleId?: string | null
  description?: string
  environmentId?: string | null
  priority?: string | null
  status?: string
  variables?: ApiSceneVariableItem[]
  processors?: Record<string, unknown>[]
  steps?: ApiSceneStepSaveReq[]
}

/** 场景更新请求（3.1.4，含乐观锁） */
export interface ApiSceneUpdateReq extends ApiSceneCreateReq {
  changeVersion: number
}

/** 场景步骤保存请求（3.3.1） */
export interface ApiSceneStepSaveReq {
  id?: string
  name: string
  stepType?: string
  sortOrder?: number
  enabled?: boolean
  sourceType?: string
  sourceId?: string | null
  requestConfig: Record<string, unknown>
  processors?: Record<string, unknown>[]
  validators?: Record<string, unknown>[]
  extractors?: Record<string, unknown>[]
}

/** 通过接口快速创建步骤请求（3.3.2） */
export interface ApiSceneQuickCreateReq {
  interfaceId: string
  mode?: string
  importInterfaceVariables?: boolean
}

/** 通过接口快速创建步骤响应 */
export interface ApiSceneQuickCreateResp {
  steps: { id: string; name: string; sourceType: string; sourceInterfaceName?: string }[]
}

/** 步骤排序请求（3.3.6） */
export interface ApiSceneStepReorderReq {
  stepIds: string[]
}

/** 步骤复制请求（3.10） */
export interface ApiSceneStepCopyReq {
  name?: string
}

/** 步骤变量批量更新请求（3.4.2） */
export interface ApiSceneStepVariableBatchReq {
  variables: { name: string; value?: string; description?: string }[]
}

/** 从接口导入步骤变量请求（3.4.3） */
export interface ApiSceneStepVariableImportReq {
  interfaceId: string
  strategy?: string
}

/** 场景执行请求（3.6.1） */
export interface ApiSceneExecuteReq {
  environmentId?: string | null
  variableOverrides?: Record<string, string>
}

/** 场景执行启动响应 */
export interface ApiExecutionStartResp {
  executionId: string
  status: string
}

/** 执行状态响应（3.6.2） */
export interface ApiExecutionStatusResp {
  executionId: string
  status: string
  currentStepIndex?: number
  totalSteps?: number
}

/** 执行历史列表项（3.11.1） */
export interface ApiExecutionHistoryItem {
  id: string
  status: string
  executionMode: string
  triggerType: string
  executedAt: string
  durationMs: number
  reportId?: string | null
}

/** 变更历史列表项（3.11.2） */
export interface ApiChangeHistoryItem {
  id: string
  version: number
  operatorName: string
  changeSummary: string
  changedAt: string
}

/** 单步调试请求（3.6.3） */
export interface ApiSceneStepDebugReq {
  environmentId?: string | null
}

/** 单步调试响应 */
export interface ApiSceneStepDebugResp {
  stepResult: {
    stepId: string
    status: string
    durationMs: number
    request: Record<string, unknown>
    response: Record<string, unknown>
    validatorResults: { name: string; passed: boolean }[]
    extractedVariables: Record<string, string>
  }
}

/** 取消执行响应 */
export interface ApiExecutionCancelResp {
  executionId: string
  status: string
}

/** 草稿单步骤调试请求（3.6.4，创建态未保存） */
export interface ApiSceneStepDraftDebugReq {
  environmentId?: string | null
  sceneVariables?: { name: string; value?: string }[]
  step: {
    name: string
    stepType?: string
    sourceType?: string | null
    sourceId?: string | null
    requestConfig: Record<string, unknown>
    validators?: Record<string, unknown>[]
    extractors?: Record<string, unknown>[]
    stepVariables?: { name: string; value?: string }[]
  }
}

/** 草稿场景执行请求（3.6.5，创建态未保存） */
export interface ApiSceneDraftExecuteReq {
  name?: string
  environmentId?: string | null
  sceneVariables?: { name: string; value?: string }[]
  steps: {
    name: string
    stepType?: string
    sourceType?: string | null
    sourceId?: string | null
    enabled?: boolean
    requestConfig: Record<string, unknown>
    validators?: Record<string, unknown>[]
    extractors?: Record<string, unknown>[]
    stepVariables?: { name: string; value?: string }[]
  }[]
}

/** 草稿场景执行响应（3.6.5） */
export interface ApiSceneDraftExecuteResp {
  status: string
  passed: number
  failed: number
  skipped: number
  durationMs: number
  steps: {
    status: string
    name: string
    durationMs?: number
    request?: Record<string, unknown>
    response?: Record<string, unknown>
    errorMessage?: string
  }[]
}

/** 全局资产引入请求（3.12） */
export interface ApiSceneAssetsImportReq {
  target: string
  stepId?: string
  assetIds: string[]
}

/** 全局资产引入响应 */
export interface ApiSceneAssetsImportResp {
  imported: number
}

/** 批量删除场景请求 */
export interface ApiSceneBatchDeleteReq {
  ids: string[]
}

/** 公共组件复制响应 */
export interface ApiComponentCopyResp {
  id: string
  type: string
  name: string
  sourceAssetId: string
}
