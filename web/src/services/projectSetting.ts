import api from '@/services'
import type { ProjectSettingListResp, ProjectSettingUpdateReq, ProjectSettingUpdateResp } from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}

// ==================== 项目设置（/api/project/settings，安全策略与应用设置） ====================

// 工作空间与项目上下文经拦截器注入 X-Active-Workspace / X-Active-Project 头传递（C4）

/** 查询当前项目指定业务域的设置项（含注册表默认值合并） */
export function fetchProjectSettings(domain: string): Promise<ProjectSettingListResp> {
  return get('/project/settings', { domain })
}

/** 批量更新设置项，逐键 upsert；任一键校验失败整批拒绝 */
export function updateProjectSettings(data: ProjectSettingUpdateReq): Promise<ProjectSettingUpdateResp> {
  return put('/project/settings', data)
}
