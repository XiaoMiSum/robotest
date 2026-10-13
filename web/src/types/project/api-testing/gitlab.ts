// GitLab 仓库配置（GitLab 集成详细设计 3.1），一项目一仓库、无分页；
// tokenCipher 只进不出，任何响应与请求类型都不出现令牌密文，token 仅在请求中出现

/** 接入方式：platform 平台执行 / gitlab_ci 流水线执行（BR-1.2，创建后锁定） */
export type ApiGitImportMode = 'platform' | 'gitlab_ci'

/** 源探查状态：ok 正常 / unreachable 仓库不可达（探查失败仅留痕，不阻塞场景使用） */
export type ApiGitHeadProbeStatus = 'ok' | 'unreachable'

/** 上次导入结果计数（部分失败分列：解析失败不建场景，真实错误阻断导入） */
export interface ApiGitImportSummary {
  created: number
  updated: number
  parseFailed: number
  errorFailed: number
  skipped: number
}

/** 仓库配置（GET / POST 响应） */
export interface ApiGitRepoConfig {
  id: string
  projectId: string
  name: string
  gitlabBaseUrl: string
  /** 创建后锁定：group/subgroup/repo，不含 .git 后缀与协议前缀 */
  repoPath: string
  /** 创建后锁定：缺省 master */
  defaultBranch: string
  importMode: ApiGitImportMode
  enabled: boolean
  testSourceDir: string
  resourceDir: string
  envFilePath: string
  /** 重复导入是否以源侧内容覆盖平台侧场景内容（默认不覆盖） */
  reimportOverwrite: boolean
  lastImportAt: string | null
  lastImportCommit: string | null
  lastImportSummary: Partial<ApiGitImportSummary> | null
  headCommit: string | null
  headCheckedAt: string | null
  headProbeStatus: ApiGitHeadProbeStatus | null
  /** 服务端派生（headCommit != lastImportCommit，任一 NULL 为 false），前端不自行比对提交号 */
  hasUpdates: boolean
}

/** 创建仓库配置请求（POST）：三锁定字段 + 令牌必填，保存前服务端强制连通性实测 */
export interface ApiGitRepoCreateReq {
  name: string
  gitlabBaseUrl: string
  repoPath: string
  defaultBranch?: string
  importMode: ApiGitImportMode
  token: string
  testSourceDir?: string
  resourceDir?: string
  envFilePath?: string
  reimportOverwrite?: boolean
}

/** 更新仓库配置请求（PUT，部分更新）：不含三锁定字段（出现即 1000017711），令牌留空表示不修改 */
export interface ApiGitRepoUpdateReq {
  name?: string
  gitlabBaseUrl?: string
  token?: string
  testSourceDir?: string
  resourceDir?: string
  envFilePath?: string
  reimportOverwrite?: boolean
}

/** 连通性测试请求：字段缺省回落已存配置，不依赖 repoId，保存前空态亦可调用 */
export interface ApiGitConnectionTestReq {
  gitlabBaseUrl?: string
  repoPath?: string
  defaultBranch?: string
  token?: string
}

/** 连通性测试响应：仅在全部检查通过时返回；失败抛 1000017702 且原因可区分（BR-1.5） */
export interface ApiGitConnectionTestResp {
  reachable: boolean
  tokenValid: boolean
  branchExists: boolean
  /** GitLab 实例版本号 */
  version: string
  /** 目标分支实际分支名（回显用） */
  resolvedBranch: string
}
