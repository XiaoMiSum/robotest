import api from '@/services'
import type {
  ApiDataSourceTestResp,
  ApiEnvironmentDetail,
  ApiEnvironmentListItem,
  ApiEnvironmentSaveReq,
  ApiHttpTestResp,
  ApiIdResp,
  ApiImportResult,
  ApiSetDefaultResp,
} from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
  return api.post(url, data, config) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}
function patch<T>(url: string, data?: unknown): Promise<T> {
  return api.patch(url, data) as unknown as Promise<T>
}
function del<T>(url: string): Promise<T> {
  return api.delete(url) as unknown as Promise<T>
}

// ==================== 环境管理（/api/project/environments，详细设计 3.1–3.3） ====================

// 工作空间与项目上下文经拦截器注入 X-Active-Workspace / X-Active-Project 头传递（C4）

/** 环境列表：默认环境置顶由后端排序保证，keyword 按名称模糊过滤 */
export function fetchEnvironments(keyword?: string): Promise<ApiEnvironmentListItem[]> {
  return get('/project/environments', keyword ? { keyword } : undefined)
}

export function fetchEnvironmentDetail(id: string): Promise<ApiEnvironmentDetail> {
  return get(`/project/environments/${id}`)
}

export function createEnvironment(data: ApiEnvironmentSaveReq): Promise<ApiIdResp> {
  return post('/project/environments', data)
}

export function updateEnvironment(id: string, data: ApiEnvironmentSaveReq): Promise<boolean> {
  return put(`/project/environments/${id}`, data)
}

export function deleteEnvironment(id: string): Promise<boolean> {
  return del(`/project/environments/${id}`)
}

export function setDefaultEnvironment(id: string): Promise<ApiSetDefaultResp> {
  return patch(`/project/environments/${id}/set-default`)
}

export function sortEnvironment(id: string, sortOrder: number): Promise<boolean> {
  return patch(`/project/environments/${id}/sort`, { sortOrder })
}

/** 复制环境：HTTP 配置与变量随副本，敏感值与数据源不复制（详细设计 3.1.11） */
export function copyEnvironment(id: string, name: string): Promise<ApiIdResp> {
  return post(`/project/environments/${id}/copy`, { name })
}

// ---------- 连接测试（3.1.7 / 3.1.8，请求体传配置不落库） ----------

export function testDataSourceConfig(
  environmentId: string,
  config: { driver?: string; url: string; connectionProperties?: Record<string, unknown> },
): Promise<ApiDataSourceTestResp> {
  return post(`/project/environments/${environmentId}/data-sources/test`, config)
}

/** HTTP 配置免保存试连：按请求体当前值验证，无需先落库（详细设计 3.1.8） */
export function testHttpConfig(
  environmentId: string,
  config: { baseUrl: string; refName?: string },
): Promise<ApiHttpTestResp> {
  return post(`/project/environments/${environmentId}/http-configs/test`, config)
}

// ---------- 导入导出（3.1.9 / 3.1.10） ----------

export function exportEnvironment(id: string): Promise<ApiEnvironmentDetail> {
  return get(`/project/environments/${id}/export`)
}

/** 导出当前环境为 JSON 文件：走解包后的 Detail 结构，确保与导入解析格式严格互逆 */
export async function downloadEnvironmentJson(id: string, fileName: string): Promise<void> {
  const detail = await exportEnvironment(id)
  const blob = new Blob([JSON.stringify(detail, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/** 导入环境 JSON；重名按 overwrite 覆盖或跳过 */
export function importEnvironment(file: File, overwrite: boolean): Promise<ApiImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return post('/project/environments/import', formData, {
    params: { overwrite },
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
