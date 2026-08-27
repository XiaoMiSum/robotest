import api from '@/services'
import type {
  ApiChangeHistoryItem,
  ApiExecutionCancelResp,
  ApiExecutionHistoryItem,
  ApiExecutionStartResp,
  ApiExecutionStatusResp,
  ApiSceneAssociationItem,
  ApiSceneAssetsImportResp,
  ApiSceneCopyReq,
  ApiSceneCreateReq,
  ApiSceneDetail,
  ApiSceneExecuteReq,
  ApiSceneInterfaceAssociateReq,
  ApiSceneInterfaceSyncModeReq,
  ApiScenePageItem,
  ApiScenePublicStepReq,
  ApiSceneQuickCreateResp,
  ApiSceneSettings,
  ApiSceneStepCopyReq,
  ApiSceneStepDebugReq,
  ApiSceneStepDebugResp,
  ApiSceneStepReorderReq,
  ApiSceneStepSaveReq,
  ApiSceneStepVariableBatchReq,
  ApiSceneStepVariableImportReq,
  ApiSceneStepVariableItem,
  ApiSceneUpdateReq,
  ApiSceneVariableBatchReq,
  ApiPublicStepBrowseItem,
  PageResult,
} from '@/types'

function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}

// ==================== 场景管理（3.1） ====================

export function fetchScenePage(
  params: { pageNo: number; pageSize: number; moduleId?: string; search?: string; followedOnly?: boolean },
): Promise<PageResult<ApiScenePageItem>> {
  return get('/project/api-scenes', { ...params })
}

export function fetchSceneDetail(id: string): Promise<ApiSceneDetail> {
  return get(`/project/api-scenes/${id}`)
}

export function createScene(req: ApiSceneCreateReq): Promise<string> {
  return post('/project/api-scenes', req).then((resp) => (resp as { id: string }).id)
}

export function updateScene(id: string, req: ApiSceneUpdateReq): Promise<boolean> {
  return put(`/project/api-scenes/${id}`, req)
}

export function deleteScene(id: string): Promise<boolean> {
  return api.delete(`/project/api-scenes/${id}`) as unknown as Promise<boolean>
}

export function copyScene(id: string, req?: ApiSceneCopyReq): Promise<string> {
  return post(`/project/api-scenes/${id}/copy`, req).then((resp) => (resp as { id: string }).id)
}

// ==================== 场景设置（3.9） ====================

export function fetchSceneSettings(id: string): Promise<ApiSceneSettings> {
  return get(`/project/api-scenes/${id}/settings`)
}

export function updateSceneSettings(id: string, req: Partial<ApiSceneSettings>): Promise<boolean> {
  return put(`/project/api-scenes/${id}/settings`, req)
}

// ==================== 场景变量（3.5） ====================

export function updateSceneVariables(id: string, req: ApiSceneVariableBatchReq): Promise<boolean> {
  return put(`/project/api-scenes/${id}/variables`, req)
}

// ==================== 步骤管理（3.3） ====================

export function createSceneStep(sceneId: string, req: ApiSceneStepSaveReq): Promise<string> {
  return post(`/project/api-scenes/${sceneId}/steps`, req).then((resp) => (resp as { id: string }).id)
}

export function quickCreateSteps(sceneId: string, req: { interfaceId: string; mode?: string; importInterfaceVariables?: boolean }): Promise<ApiSceneQuickCreateResp> {
  return post(`/project/api-scenes/${sceneId}/steps/quick-create`, req)
}

export function addPublicStep(sceneId: string, req: ApiScenePublicStepReq): Promise<string> {
  return post(`/project/api-scenes/${sceneId}/steps/public-step`, req).then((resp) => (resp as { id: string }).id)
}

export function updateSceneStep(sceneId: string, stepId: string, req: ApiSceneStepSaveReq): Promise<boolean> {
  return put(`/project/api-scenes/${sceneId}/steps/${stepId}`, req)
}

export function deleteSceneStep(sceneId: string, stepId: string): Promise<boolean> {
  return api.delete(`/project/api-scenes/${sceneId}/steps/${stepId}`) as unknown as Promise<boolean>
}

export function reorderSceneSteps(sceneId: string, req: ApiSceneStepReorderReq): Promise<boolean> {
  return put(`/project/api-scenes/${sceneId}/steps/reorder`, req)
}

export function copySceneStep(sceneId: string, stepId: string, req?: ApiSceneStepCopyReq): Promise<string> {
  return post(`/project/api-scenes/${sceneId}/steps/${stepId}/copy`, req).then((resp) => (resp as { id: string }).id)
}

// ==================== 步骤级变量（3.4） ====================

export function fetchStepVariables(sceneId: string, stepId: string): Promise<ApiSceneStepVariableItem[]> {
  return get(`/project/api-scenes/${sceneId}/steps/${stepId}/variables`)
}

export function updateStepVariables(sceneId: string, stepId: string, req: ApiSceneStepVariableBatchReq): Promise<boolean> {
  return put(`/project/api-scenes/${sceneId}/steps/${stepId}/variables`, req)
}

export function importStepVariables(sceneId: string, stepId: string, req: ApiSceneStepVariableImportReq): Promise<ApiSceneStepVariableItem[]> {
  return post(`/project/api-scenes/${sceneId}/steps/${stepId}/variables/import`, req)
}

// ==================== 场景关联接口（3.2） ====================

export function fetchSceneAssociations(sceneId: string): Promise<ApiSceneAssociationItem[]> {
  return get(`/project/api-scenes/${sceneId}/associations`)
}

export function associateInterfaces(sceneId: string, req: ApiSceneInterfaceAssociateReq): Promise<boolean> {
  return post(`/project/api-scenes/${sceneId}/associations`, req)
}

export function unassociateInterface(sceneId: string, associationId: string): Promise<boolean> {
  return api.delete(`/project/api-scenes/${sceneId}/associations/${associationId}`) as unknown as Promise<boolean>
}

export function switchSyncMode(sceneId: string, associationId: string, req: ApiSceneInterfaceSyncModeReq): Promise<boolean> {
  return put(`/project/api-scenes/${sceneId}/associations/${associationId}/sync-mode`, req)
}

// ==================== 执行与调试（3.6） ====================

export function executeScene(sceneId: string, req?: ApiSceneExecuteReq): Promise<ApiExecutionStartResp> {
  return post(`/project/api-scenes/${sceneId}/executions`, req)
}

export function getExecutionStatus(sceneId: string, executionId: string): Promise<ApiExecutionStatusResp> {
  return get(`/project/api-scenes/${sceneId}/executions/${executionId}`)
}

export function cancelExecution(sceneId: string, executionId: string): Promise<ApiExecutionCancelResp> {
  return post(`/project/api-scenes/${sceneId}/executions/${executionId}/cancel`)
}

export function debugStep(sceneId: string, stepId: string, req?: ApiSceneStepDebugReq): Promise<ApiSceneStepDebugResp> {
  return post(`/project/api-scenes/${sceneId}/steps/${stepId}/debug`, req)
}

// ==================== 执行历史与变更历史（3.11） ====================

export function fetchExecutionHistory(sceneId: string, pageNo: number, pageSize: number): Promise<PageResult<ApiExecutionHistoryItem>> {
  return get(`/project/api-scenes/${sceneId}/executions`, { pageNo, pageSize })
}

export function fetchChangeHistory(sceneId: string, pageNo: number, pageSize: number): Promise<PageResult<ApiChangeHistoryItem>> {
  return get(`/project/api-scenes/${sceneId}/change-history`, { pageNo, pageSize })
}

// ==================== 全局资产引入（3.12） ====================

export function importAssets(sceneId: string, req: { target: string; stepId?: string; assetIds: string[] }): Promise<ApiSceneAssetsImportResp> {
  return post(`/project/api-scenes/${sceneId}/assets/import`, req)
}

// ==================== 关注（follow/unfollow） ====================

export function followScene(sceneId: string): Promise<boolean> {
  return post(`/project/api-scenes/${sceneId}/follow`)
}

export function unfollowScene(sceneId: string): Promise<boolean> {
  return api.delete(`/project/api-scenes/${sceneId}/follow`) as unknown as Promise<boolean>
}

// ==================== 批量操作 ====================

export function batchDeleteScenes(ids: string[]): Promise<boolean> {
  return api.delete('/project/api-scenes/batch', { data: { ids } }) as unknown as Promise<boolean>
}

// ==================== 公共步骤浏览 ====================

export function browsePublicSteps(sceneId: string): Promise<ApiPublicStepBrowseItem[]> {
  return get(`/project/api-scenes/${sceneId}/public-steps`)
}
