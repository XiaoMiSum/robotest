import { get, post, put, del } from '@/services'
import type {
  ApiChangeHistoryItem,
  ApiExecutionHistoryItem,
  ApiExecutionStartResp,
  ApiSceneCreateReq,
  ApiSceneDetail,
  ApiSceneDraftExecuteReq,
  ApiSceneDraftExecuteResp,
  ApiSceneExecuteReq,
  ApiScenePageItem,
  ApiSceneQuickCreateResp,
  ApiSceneStepCopyReq,
  ApiSceneStepDebugReq,
  ApiSceneStepDebugResp,
  ApiSceneStepReorderReq,
  ApiSceneStepSaveReq,
  ApiSceneStepVariableBatchReq,
  ApiSceneStepVariableItem,
  ApiSceneUpdateReq,
  PageResult,
} from '@/types'

// ==================== 场景管理（3.1） ====================

export function fetchScenePage(
  params: { pageNo: number; pageSize: number; moduleId?: string; search?: string; status?: string; view?: string; followedOnly?: boolean },
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
  return del(`/project/api-scenes/${id}`)
}

// ==================== 步骤管理（3.3） ====================

export function createSceneStep(sceneId: string, req: ApiSceneStepSaveReq): Promise<string> {
  return post(`/project/api-scenes/${sceneId}/steps`, req).then((resp) => (resp as { id: string }).id)
}

export function quickCreateSteps(sceneId: string, req: { interfaceId: string; mode?: string; importInterfaceVariables?: boolean }): Promise<ApiSceneQuickCreateResp> {
  return post(`/project/api-scenes/${sceneId}/steps/quick-create`, req)
}

export function updateSceneStep(sceneId: string, stepId: string, req: ApiSceneStepSaveReq): Promise<boolean> {
  return put(`/project/api-scenes/${sceneId}/steps/${stepId}`, req)
}

export function deleteSceneStep(sceneId: string, stepId: string): Promise<boolean> {
  return del(`/project/api-scenes/${sceneId}/steps/${stepId}`)
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

// ==================== 执行与调试（3.6） ====================

export function executeScene(sceneId: string, req?: ApiSceneExecuteReq): Promise<ApiExecutionStartResp> {
  return post(`/project/api-scenes/${sceneId}/executions`, req)
}

export function debugStep(sceneId: string, stepId: string, req?: ApiSceneStepDebugReq): Promise<ApiSceneStepDebugResp> {
  return post(`/project/api-scenes/${sceneId}/steps/${stepId}/debug`, req)
}

// ==================== 草稿调试/执行（3.6.4 / 3.6.5，创建态未保存） ====================

export function executeDraftScene(req: ApiSceneDraftExecuteReq): Promise<ApiSceneDraftExecuteResp> {
  return post('/project/api-scenes/draft/execute', req)
}

// ==================== 执行历史与变更历史（3.11） ====================

export function fetchExecutionHistory(sceneId: string, pageNo: number, pageSize: number): Promise<PageResult<ApiExecutionHistoryItem>> {
  return get(`/project/api-scenes/${sceneId}/executions`, { pageNo, pageSize })
}

export function fetchChangeHistory(sceneId: string, pageNo: number, pageSize: number): Promise<PageResult<ApiChangeHistoryItem>> {
  return get(`/project/api-scenes/${sceneId}/change-history`, { pageNo, pageSize })
}

// ==================== 全局资产引入（3.12） ====================

// ==================== 关注（follow/unfollow） ====================

export function followScene(sceneId: string): Promise<boolean> {
  return post(`/project/api-scenes/${sceneId}/follow`)
}

export function unfollowScene(sceneId: string): Promise<boolean> {
  return del(`/project/api-scenes/${sceneId}/follow`)
}

// ==================== 批量操作 ====================

export function batchDeleteScenes(ids: string[]): Promise<boolean> {
  return del('/project/api-scenes/batch', { data: { ids } })
}

export function batchMoveScenes(ids: string[], moduleId: string | null): Promise<boolean> {
  return put('/project/api-scenes/batch/move', { ids, moduleId })
}
