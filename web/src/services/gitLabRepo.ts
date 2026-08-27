import api from '@/services'
import type {
  GitLabRepoListItem,
  GitLabRepoSavePayload,
  GitLabRepoTestConnectionResult,
  GitLabFileTreeNode,
  GitLabExecutableImportPayload,
  GitLabExecutableImportResult,
  GitLabMetadataImportResult,
  GitLabMetadataListItem,
  GitLabSyncConfig,
  GitLabSyncConfigPayload,
  GitLabSyncHistoryItem,
  GitLabTestScopeItem,
  GitLabTestScopeSavePayload,
  GitLabPipelineResult,
  GitLabPipelineStatusResult,
  GitLabPipelineReportResult,
  PageResult,
} from '@/types'

// 响应拦截器已将 Result<T> 解包为 data，此处集中处理静态类型断言
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}
function post<T>(url: string, data?: unknown): Promise<T> {
  return api.post(url, data) as unknown as Promise<T>
}
function put<T>(url: string, data?: unknown): Promise<T> {
  return api.put(url, data) as unknown as Promise<T>
}
function del<T>(url: string): Promise<T> {
  return api.delete(url) as unknown as Promise<T>
}

// ==================== GitLab 仓库配置（/api/project/gitlab-repos） ====================

// 工作空间与项目上下文经拦截器注入 X-Active-Workspace / X-Active-Project 头传递（C4）

/** 查询仓库配置列表 */
export function fetchGitLabRepos(
  pageNo: number,
  pageSize: number,
  keyword?: string,
): Promise<PageResult<GitLabRepoListItem>> {
  const params: Record<string, unknown> = { pageNo, pageSize }
  if (keyword) params.keyword = keyword
  return get('/project/gitlab-repos', params)
}

/** 创建仓库配置 */
export function createGitLabRepo(data: GitLabRepoSavePayload): Promise<string> {
  return post('/project/gitlab-repos', data)
}

/** 更新仓库配置 */
export function updateGitLabRepo(id: string, data: GitLabRepoSavePayload): Promise<boolean> {
  return put(`/project/gitlab-repos/${id}`, data)
}

/** 删除仓库配置 */
export function deleteGitLabRepo(id: string): Promise<boolean> {
  return del(`/project/gitlab-repos/${id}`)
}

/** 测试仓库连接 */
export function testGitLabConnection(id: string): Promise<GitLabRepoTestConnectionResult> {
  return post(`/project/gitlab-repos/${id}/test-connection`)
}

/** 获取仓库分支列表 */
export function fetchGitLabBranches(id: string): Promise<string[]> {
  return get(`/project/gitlab-repos/${id}/branches`)
}

// ==================== GitLab 文件浏览 ====================

/** 浏览仓库文件树 */
export function fetchGitLabFiles(id: string, path?: string, ref?: string): Promise<GitLabFileTreeNode[]> {
  const params: Record<string, unknown> = {}
  if (path) params.path = path
  if (ref) params.ref = ref
  return get(`/project/gitlab-repos/${id}/files`, params)
}

// ==================== GitLab 可执行导入 ====================

/** 导入可执行测试类 */
export function importGitLabExecutable(id: string, data: GitLabExecutableImportPayload): Promise<GitLabExecutableImportResult> {
  return post(`/project/gitlab-repos/${id}/executable-import`, data)
}

/** 查询最近一次导入结果 */
export function fetchGitLabLatestImport(id: string): Promise<GitLabExecutableImportResult | null> {
  return get(`/project/gitlab-repos/${id}/executable-import/latest`)
}

// ==================== GitLab 元数据 ====================

/** 触发元数据导入 */
export function importGitLabMetadata(id: string): Promise<GitLabMetadataImportResult> {
  return post(`/project/gitlab-repos/${id}/metadata-import`)
}

/** 查询元数据列表（分页） */
export function fetchGitLabMetadataList(
  id: string,
  pageNo: number,
  pageSize: number,
  isExecutable?: boolean,
  keyword?: string,
): Promise<PageResult<GitLabMetadataListItem>> {
  const params: Record<string, unknown> = { pageNo, pageSize }
  if (isExecutable !== undefined) params.isExecutable = isExecutable
  if (keyword) params.keyword = keyword
  return get(`/project/gitlab-repos/${id}/metadata`, params)
}

/** 同步元数据 */
export function syncGitLabMetadata(id: string): Promise<GitLabMetadataImportResult> {
  return post(`/project/gitlab-repos/${id}/sync-metadata`)
}

// ==================== GitLab 同步配置 ====================

/** 查询同步配置 */
export function fetchGitLabSyncConfig(id: string): Promise<GitLabSyncConfig> {
  return get(`/project/gitlab-repos/${id}/sync-config`)
}

/** 更新同步配置 */
export function updateGitLabSyncConfig(id: string, data: GitLabSyncConfigPayload): Promise<boolean> {
  return put(`/project/gitlab-repos/${id}/sync-config`, data)
}

/** 查询同步历史 */
export function fetchGitLabSyncHistory(id: string): Promise<GitLabSyncHistoryItem[]> {
  return get(`/project/gitlab-repos/${id}/sync-history`)
}

// ==================== GitLab 测试范围 ====================

/** 查询测试范围变量列表 */
export function fetchGitLabTestScope(id: string): Promise<GitLabTestScopeItem[]> {
  return get(`/project/gitlab-repos/${id}/test-scope`)
}

/** 保存测试范围变量（全量覆盖） */
export function saveGitLabTestScope(id: string, data: GitLabTestScopeSavePayload): Promise<boolean> {
  return put(`/project/gitlab-repos/${id}/test-scope`, data)
}

// ==================== GitLab 流水线 ====================

/** 触发流水线 */
export function triggerGitLabPipeline(
  id: string,
  data: { sceneId: string; testScope?: Record<string, string>; variables?: Record<string, string> },
): Promise<GitLabPipelineResult> {
  return post(`/project/gitlab-repos/${id}/trigger-pipeline`, data)
}

/** 查询流水线状态 */
export function fetchGitLabPipelineStatus(executionId: string): Promise<GitLabPipelineStatusResult> {
  return get(`/project/gitlab-repos/executions/${executionId}/pipeline-status`)
}

/** 拉取流水线报告 */
export function pullGitLabPipelineReport(executionId: string): Promise<GitLabPipelineReportResult> {
  return post(`/project/gitlab-repos/executions/${executionId}/pull-report`)
}
