import { get, post, put, del } from '@/services'
import type {
  ApiGitConnectionTestReq,
  ApiGitConnectionTestResp,
  ApiGitRepoConfig,
  ApiGitRepoCreateReq,
  ApiGitRepoUpdateReq,
} from '@/types'

// ==================== GitLab 仓库配置（/api/project/git-repos，GitLab 集成详细设计 3.1） ====================
// 一项目一仓库、无分页；工作空间与项目上下文经拦截器注入 X-Active-Workspace / X-Active-Project 头传递（C4）

/** 查询当前项目仓库配置；未配置时返回 null（空态判定） */
export function fetchGitRepo(): Promise<ApiGitRepoConfig | null> {
  return get('/project/git-repos')
}

/** 创建仓库配置：服务端强制连通性实测，失败不落库（1000017702） */
export function createGitRepo(data: ApiGitRepoCreateReq): Promise<ApiGitRepoConfig> {
  return post('/project/git-repos', data)
}

/** 部分更新：仅更新实际传入字段；三锁定字段（repoPath / defaultBranch / importMode）不可传 */
export function updateGitRepo(repoId: string, data: ApiGitRepoUpdateReq): Promise<boolean> {
  return put(`/project/git-repos/${repoId}`, data)
}

/** 逻辑删除：其下场景保留并标记来源已移除；有启用中同步任务时拒绝（1000017712） */
export function deleteGitRepo(repoId: string): Promise<boolean> {
  return del(`/project/git-repos/${repoId}`)
}

/** 连通性测试：不依赖 repoId，字段缺省回落已存配置，测试不落库 */
export function testGitRepoConnection(data: ApiGitConnectionTestReq): Promise<ApiGitConnectionTestResp> {
  return post('/project/git-repos/connections/test', data)
}
