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

// ==================== 导入与解析（GitLab 集成详细设计 3.2 / 3.4） ====================

/** 导入进度阶段：idle 未导入 → fetch 拉取源码 → scan 扫描测试类 → parse 解析用例 → merge 合并环境 → persist 回写结果 → done；任一阶段异常转 failed */
export type ApiGitImportStage =
  | 'idle'
  | 'fetch'
  | 'scan'
  | 'parse'
  | 'merge'
  | 'persist'
  | 'done'
  | 'failed'

/** 失败明细类型：parseFailed 解析失败（预期内，不建场景）/ errorFailed 真实错误（BR-1.9） */
export type ApiGitImportDetailType = 'parseFailed' | 'errorFailed'

/** 预览失败明细：文件级失败 sceneKey 为空，出错文件路径内嵌 reason 文本（交互稿 27 §3.2） */
export interface ApiGitImportPreviewDetail {
  type: ApiGitImportDetailType
  /** 类全限定名#方法名；整文件级失败为空 */
  sceneKey: string | null
  reason: string
}

/** commit 幂等闸门判定（详设 3.4）：blocked 时不拉源码不扫描，计数字段为空 */
export interface ApiGitImportGate {
  blocked: boolean
  reason: string | null
}

export interface ApiGitImportPreviewModules {
  containers: { create: number; update: number }
  /** backfill：「同包容器 + 同简单名」认领并回填全限定名的类模块数（BR-1.8） */
  classes: { create: number; update: number; backfill: number }
}

export interface ApiGitImportPreviewScenes {
  create: number
  update: number
  parseFailed: number
  errorFailed: number
}

/** 成功解析的场景条目（详设 3.2）：场景级，与 scenes 计数同口径 */
export interface ApiGitImportPreviewParsedScene {
  /** 类全限定名#方法名 */
  sceneKey: string
  name: string
  action: 'create' | 'update'
}

/** 成功解析清单：items 超出服务端单次上限时 truncated=true，截断不影响任何计数 */
export interface ApiGitImportPreviewParsedScenes {
  total: number
  truncated: boolean
  items: ApiGitImportPreviewParsedScene[]
}

/** 环境合并判定（仅计数不落库）：gitlab_ci 恒 skip（BR-1.20），已封印环境亦 skip（BR-1.18） */
export interface ApiGitImportPreviewEnvironment {
  action: 'merge' | 'skip'
  target: string | null
  httpConfigs: number | null
  dataSources: number | null
}

/** 扫描预览响应（详设 3.2）：干跑不落库；闸门拦截时 classes / modules / scenes / environment 为 null */
export interface ApiGitImportPreview {
  repoId: string
  branch: string
  headCommit: string
  gate: ApiGitImportGate
  classes: number | null
  effectiveCases: number | null
  modules: ApiGitImportPreviewModules | null
  scenes: ApiGitImportPreviewScenes | null
  environment: ApiGitImportPreviewEnvironment | null
  details: ApiGitImportPreviewDetail[]
  parsedScenes: ApiGitImportPreviewParsedScenes | null
}

/** 导入进度响应（详设 3.2）：服务端内存态不落库，页面离开即停止轮询（交互稿 27 §5） */
export interface ApiGitImportStatus {
  stage: ApiGitImportStage
  /** 已处理数（fetch / scan 为文件数，parse 及之后为用例数） */
  processed: number
  total: number
  /** failed 时的失败原因，其余阶段为空 */
  error: string | null
  /** done 时与仓库配置 lastImportSummary 同构 */
  summary: Partial<ApiGitImportSummary> | null
}
