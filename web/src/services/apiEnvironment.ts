import api from '@/services'
import type {
  ApiDataSource,
  ApiDataSourcePayload,
  ApiDataSourceTestResp,
  ApiEnvironmentDetail,
  ApiEnvironmentListItem,
  ApiEnvironmentSaveReq,
  ApiHttpConfig,
  ApiHttpConfigPayload,
  ApiHttpTestResp,
  ApiIdResp,
  ApiImportResult,
  ApiProcessor,
  ApiSetDefaultResp,
  ApiVariable,
  ApiVariablePayload,
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

// ---------- 处理器子资源（3.2） ----------

export function fetchProcessors(environmentId: string, processorType?: string): Promise<ApiProcessor[]> {
  const params: Record<string, unknown> = {}
  if (processorType) params.processorType = processorType
  return get(`/project/environments/${environmentId}/processors`, params)
}

export function createProcessor(
  environmentId: string,
  data: Pick<ApiProcessor, 'processorType' | 'name'> & Partial<Pick<ApiProcessor, 'config' | 'sortOrder' | 'enabled'>>,
): Promise<ApiProcessor> {
  return post(`/project/environments/${environmentId}/processors`, data)
}

export function updateProcessor(
  environmentId: string,
  procId: string,
  data: Pick<ApiProcessor, 'processorType' | 'name'> & Partial<Pick<ApiProcessor, 'config' | 'sortOrder' | 'enabled'>>,
): Promise<ApiProcessor> {
  return put(`/project/environments/${environmentId}/processors/${procId}`, data)
}

export function deleteProcessor(environmentId: string, procId: string): Promise<boolean> {
  return del(`/project/environments/${environmentId}/processors/${procId}`)
}

// ---------- HTTP 配置子资源（3.1.12） ----------

/** 新增 HTTP 配置：立即持久化，返回含 id 的配置供行内更新 */
export function createHttpConfig(environmentId: string, data: ApiHttpConfigPayload): Promise<ApiHttpConfig> {
  return post(`/project/environments/${environmentId}/http-configs`, data)
}

export function updateHttpConfig(
  environmentId: string,
  httpConfigId: string,
  data: ApiHttpConfigPayload,
): Promise<ApiHttpConfig> {
  return put(`/project/environments/${environmentId}/http-configs/${httpConfigId}`, data)
}

export function deleteHttpConfig(environmentId: string, httpConfigId: string): Promise<boolean> {
  return del(`/project/environments/${environmentId}/http-configs/${httpConfigId}`)
}

// ---------- 数据源子资源（3.1.12） ----------

export function createDataSource(environmentId: string, data: ApiDataSourcePayload): Promise<ApiDataSource> {
  return post(`/project/environments/${environmentId}/data-sources`, data)
}

export function updateDataSource(
  environmentId: string,
  dsId: string,
  data: ApiDataSourcePayload,
): Promise<ApiDataSource> {
  return put(`/project/environments/${environmentId}/data-sources/${dsId}`, data)
}

export function deleteDataSource(environmentId: string, dsId: string): Promise<boolean> {
  return del(`/project/environments/${environmentId}/data-sources/${dsId}`)
}

// ---------- 变量子资源（3.3） ----------

export function batchReplaceVariables(environmentId: string, variables: ApiVariablePayload[]): Promise<ApiVariable[]> {
  return put(`/project/environments/${environmentId}/variables`, { variables })
}

export function importVariables(
  environmentId: string,
  variables: ApiVariablePayload[],
  overwrite: boolean,
): Promise<ApiImportResult> {
  return post(`/project/environments/${environmentId}/variables/import`, { variables, overwrite })
}

export function exportVariables(environmentId: string): Promise<ApiVariable[]> {
  return get(`/project/environments/${environmentId}/variables/export`)
}

// ---------- 连接测试（3.1.7 / 3.1.8） ----------

export function testDataSource(environmentId: string, dataSourceId: string): Promise<ApiDataSourceTestResp> {
  return post(`/project/environments/${environmentId}/data-sources/${dataSourceId}/test`)
}

export function testDataSourceConfig(
  environmentId: string,
  config: Pick<ApiDataSourcePayload, 'driver' | 'url' | 'connectionProperties'>,
): Promise<ApiDataSourceTestResp> {
  return post(`/project/environments/${environmentId}/data-sources/test`, config)
}

export function testHttpConfig(environmentId: string, httpConfigId: string): Promise<ApiHttpTestResp> {
  return post(`/project/environments/${environmentId}/http-configs/${httpConfigId}/test`)
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
