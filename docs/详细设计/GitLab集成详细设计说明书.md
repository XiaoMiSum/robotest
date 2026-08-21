# 软件测试平台——GitLab 集成详细设计说明书

**文档版本**：V1.2
**日期**：2026-08-17
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台 V1.2 接口测试业务域的 **GitLab 集成**进行详细设计，定义仓库配置、可执行导入、元数据导入、仓库流水线执行的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.4 导入与仓库流水线执行、3.7.3 GitLab 仓库配置与概要设计第 3.1 章对应模块：

- **GitLab 仓库配置**：项目级仓库配置 CRUD（地址、分支、访问令牌）；
- **可执行导入**：解析仓库中 `@Test` + `@RyzeTest` 注解测试方法的 resource path yaml 为平台可执行场景；
- **元数据导入**：扫描源码提取测试类元数据与场景描述，不解析为可执行场景；
- **仓库流水线执行**：通过 GitLab API 传递测试类元数据触发仓库 CI 流水线，拉取状态与报告产物。

> 定时任务的统一 Cron 调度、执行记录与删除保护见《定时任务详细设计说明书》（`docs/详细设计/定时任务详细设计说明书.md`）。

### 1.3 参考资料

- 《接口测试需求规格说明书 V1.2》（`docs/需求/接口测试需求规格说明书.md`，3.7）
- 《概要设计说明书 V1.2》（`docs/概要/概要设计说明书.md`，4.4、4.7）
- 《API 测试基础设施详细设计说明书》（`docs/详细设计/API测试基础设施详细设计说明书.md`）
- GitLab REST API v4 文档（`https://docs.gitlab.com/api/`）

---

## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 GitLab 仓库配置表（api_gitlab_repository）

项目级公共配置，可执行导入与仓库流水线执行均引用此配置。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目（ws_project.id） |
| name | VARCHAR(100) | NOT NULL | 配置名称 |
| repo_url | VARCHAR(500) | NOT NULL | GitLab 仓库地址 |
| branch | VARCHAR(200) | NOT NULL DEFAULT 'main' | 分支或标签 |
| access_token_cipher | VARCHAR(1000) | NOT NULL | 项目访问令牌（加密） |
| token_suffix | VARCHAR(4) | NULL | 令牌末 4 位（脱敏展示） |
| test_source_path | VARCHAR(500) | NULL | 测试源码相对路径（如 `src/test/java`） |
| last_import_status | VARCHAR(20) | NULL | 最近导入状态：success / partial / failed / pending |
| last_import_at | TIMESTAMP | NULL | 最近导入时间 |
| last_metadata_sync_at | TIMESTAMP | NULL | 最近元数据同步时间 |
| last_commit_sha | VARCHAR(40) | NULL | 最近一次同步的 commit SHA |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_glab_project` (project_id)

> 令牌密钥永不回传明文：响应仅含 `tokenSuffix`（末4位）与 `configured`（布尔）。导入/触发操作以服务端身份调用 GitLab API，令牌仅存服务端。

#### 2.1.2 测试类元数据表（api_gitlab_test_class_metadata）

由元数据导入填充，存储仓库中扫描到的测试类描述信息，供仓库流水线执行触发时传递。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| repository_id | UUID | NOT NULL | 关联仓库配置（api_gitlab_repository.id） |
| full_class_name | VARCHAR(500) | NOT NULL | 测试类全限定名 |
| class_annotations | JSONB | NOT NULL DEFAULT '[]' | 类级注解配置 `[{name, params}]` |
| display_name | VARCHAR(200) | NULL | 场景显示名（从注解或注释提取） |
| description | TEXT | NULL | 场景描述 |
| resource_path | VARCHAR(500) | NULL | resource path（yaml 文件路径，可执行导入时使用） |
| is_executable | BOOLEAN | NOT NULL DEFAULT FALSE | 是否可执行（有 resource path 为 true） |
| methods | JSONB | NOT NULL DEFAULT '[]' | 测试方法清单 `[{name, annotations, displayName}]` |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_gmeta_repository` (repository_id), `idx_gmeta_class_name` (repository_id, full_class_name)

#### 2.1.3 测试范围参数表（api_gitlab_test_scope）

与 `.gitlab-ci.yml` 约定的变量名与取值规则，用于仓库流水线执行时传递测试范围。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| repository_id | UUID | NOT NULL | 关联仓库配置 |
| variable_name | VARCHAR(100) | NOT NULL | CI 变量名（如 `TEST_CLASS`、`TEST_METHOD`） |
| scope_type | VARCHAR(20) | NOT NULL | 范围类型：class / method / tag / custom |
| description | VARCHAR(500) | NULL | 变量说明 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_gscope_repository` (repository_id)

### 2.2 错误码补充

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| 7501 | API_GITLAB_REPO_NOT_FOUND | 仓库配置不存在 |
| 7502 | API_GITLAB_REPO_UNREACHABLE | 仓库地址不可达 |
| 7503 | API_GITLAB_TOKEN_INVALID | 令牌无效 |
| 7504 | API_GITLAB_METADATA_MISSING | 测试类元数据不存在 |

---

## 3. 接口详细设计

### 3.1 GitLab 仓库配置管理

#### 3.1.1 查询仓库配置列表

- **路径**：`GET /api/project/gitlab-repos`
- **响应**：

```json
[
  {
    "id": "018f...",
    "name": "主仓库",
    "repoUrl": "https://gitlab.example.com/team/robotest-tests.git",
    "branch": "main",
    "tokenSuffix": "c91d",
    "testSourcePath": "src/test/java",
    "lastImportStatus": "success",
    "lastImportAt": "2026-08-17T10:30:00Z",
    "lastMetadataSyncAt": "2026-08-17T09:00:00Z",
    "lastCommitSha": "a1b2c3d"
  }
]
```

#### 3.1.2 创建仓库配置

- **路径**：`POST /api/project/gitlab-repos`
- **请求体**：

```json
{
  "name": "主仓库",
  "repoUrl": "https://gitlab.example.com/team/robotest-tests.git",
  "branch": "main",
  "accessToken": "glpat-xxxxxxxxxxxx",
  "testSourcePath": "src/test/java"
}
```

- **校验**：
  - 创建时立即校验仓库可达性（GET `repoUrl` + branch，401 返回 7503，404 返回 7502）。
  - 令牌加密存储，不存明文。
- **响应**：`{ "id": "018f...", "tokenSuffix": "xxxx" }`

#### 3.1.3 更新仓库配置

- **路径**：`PUT /api/project/gitlab-repos/:id`
- **请求体**：同 3.1.2（`accessToken` 为 null 表示保持原值）。

#### 3.1.4 删除仓库配置

- **路径**：`DELETE /api/project/gitlab-repos/:id`
- **校验**：若有绑定的定时任务，需先删除定时任务（见《定时任务详细设计说明书》3.1.5）。

#### 3.1.5 测试仓库连接

- **路径**：`POST /api/project/gitlab-repos/:id/test-connection`
- **说明**：校验仓库地址与令牌有效性，返回连接结果。
- **响应**：

```json
{
  "success": true,
  "message": "连接成功",
  "repoName": "robotest-tests",
  "defaultBranch": "main",
  "commitCount": 1250
}
```

### 3.2 可执行导入

#### 3.2.1 触发可执行导入

- **路径**：`POST /api/project/gitlab-repos/:id/executable-import`
- **请求体**：

```json
{
  "scope": "all",
  "classNames": []
}
```

- `scope`：`all`（全量）/ `selected`（指定类）。
- `classNames`：`scope = selected` 时传入全限定名列表。
- **说明**：
  1. 从 GitLab 仓库下载源码（指定分支/标签）。
  2. 扫描 `testSourcePath` 下的 Java 文件，查找 `@Test` + `@RyzeTest` 注解的测试方法。
  3. 解析每个测试方法的 `resourcePath` 注解参数，定位 yaml 文件。
  4. 下载并解析 yaml 为平台场景模型。
  5. 按 `full_class_name + method_name` 去重，增量更新。
- **响应**：

```json
{
  "importHistoryId": "018f...",
  "summary": { "created": 5, "updated": 2, "failed": 0, "skipped": 1 },
  "scenes": [
    { "id": "018g...", "name": "登录接口测试", "stepCount": 8 }
  ]
}
```

#### 3.2.2 查询可执行导入结果

- **路径**：`GET /api/project/gitlab-repos/:id/executable-import/latest`
- **说明**：返回最近一次可执行导入的结果摘要。

### 3.3 元数据导入

#### 3.3.1 触发元数据导入

- **路径**：`POST /api/project/gitlab-repos/:id/metadata-import`
- **说明**：
  1. 扫描仓库源码，提取测试类元数据（类名、注解、方法清单）。
  2. 不下载/解析 yaml 文件。
  3. 元数据写入 `api_gitlab_test_class_metadata` 表。
  4. 更新 `last_metadata_sync_at` 与 `last_commit_sha`。
- **响应**：

```json
{
  "importHistoryId": "018f...",
  "summary": { "classes": 12, "methods": 45, "executable": 8 },
  "commitSha": "a1b2c3d"
}
```

#### 3.3.2 查询元数据列表

- **路径**：`GET /api/project/gitlab-repos/:id/metadata?isExecutable=true&page=1&pageSize=20`
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "fullClassName": "com.example.test.LoginTest",
      "displayName": "登录接口测试",
      "description": "验证用户登录流程",
      "resourcePath": "scenarios/login.yaml",
      "isExecutable": true,
      "methods": [
        { "name": "testLoginSuccess", "displayName": "登录成功" },
        { "name": "testLoginFailed", "displayName": "登录失败" }
      ]
    }
  ],
  "total": 12
}
```

#### 3.3.3 同步元数据

- **路径**：`POST /api/project/gitlab-repos/:id/sync-metadata`
- **说明**：手动触发元数据同步，重新拉取仓库最新元数据。
- **响应**：同 3.3.1。

### 3.4 仓库流水线执行

#### 3.4.1 触发仓库流水线

- **路径**：`POST /api/project/gitlab-repos/:id/trigger-pipeline`
- **请求体**：

```json
{
  "testScope": {
    "TEST_CLASS": "com.example.test.LoginTest",
    "TEST_METHOD": "testLoginSuccess"
  },
  "variables": {
    "ENV": "staging"
  }
}
```

- **说明**：
  1. 校验元数据是否过期（基于 `last_commit_sha` 与仓库最新 commit 时间比较），过期则自动触发元数据同步后执行。
  2. 通过 GitLab API（`POST /projects/:id/trigger/pipeline`）触发 CI 流水线。
  3. 将 `testScope` 映射为 CI 变量传递。
  4. 记录流水线 ID 与触发时间至 `api_execution_record`。
- **响应**：

```json
{
  "executionRecordId": "018f...",
  "pipelineId": "12345",
  "pipelineUrl": "https://gitlab.example.com/team/robotest-tests/-/pipelines/12345",
  "status": "pending"
}
```

#### 3.4.2 查询流水线状态

- **路径**：`GET /api/project/executions/:executionId/pipeline-status`
- **说明**：通过 GitLab API 拉取流水线最新状态。
- **响应**：

```json
{
  "pipelineId": "12345",
  "status": "success",
  "duration": 120,
  "stages": [
    { "name": "test", "status": "success" }
  ]
}
```

#### 3.4.3 拉取流水线报告

- **路径**：`POST /api/project/executions/:executionId/pull-report`
- **说明**：从 GitLab 流水线产物中拉取测试报告，写入平台 `api_report` 表。
- **响应**：

```json
{
  "reportId": "018f...",
  "summary": { "total": 10, "passed": 9, "failed": 1, "skipped": 0 }
}
```

---

## 4. 业务逻辑设计

### 4.1 可执行导入解析流程

```
触发可执行导入
  ↓ 校验仓库连接与令牌有效性
  ↓ 从 GitLab API 下载指定分支/标签的源码（ZIP archive）
  ↓ 解压至临时目录
  ↓ 扫描 testSourcePath 下 *.java 文件
  ↓ 查找 @Test + @RyzeTest 注解的测试方法
  ↓ 解析 resourcePath 注解参数 → 定位 yaml 文件
  ↓ 下载 yaml 文件内容
  ↓ 解析 yaml 为平台场景模型（格式转换逆向：Ryze JSON → 平台模型）
  ↓ 按 full_class_name + method_name 去重
  ↓ 增量更新（创建/更新/跳过）
  ↓ 写入 api_import_mapping 记录
  ↓ 清理临时目录
  ↓ 返回导入结果
```

**yaml 解析规则**：
- Ryze yaml 结构 → 平台场景模型的逆向映射，与《API 测试基础设施详细设计说明书》4.1.2 的正向映射对称。
- TestSuite → 场景，children → 步骤，variables → 场景参数，configelements → 环境配置。
- 解析失败的 yaml 记录到 `error_details`，不影响其他文件的导入。

### 4.2 元数据导入扫描

```
触发元数据导入
  ↓ 校验仓库连接
  ↓ 下载源码（ZIP archive）
  ↓ 扫描 *.java 文件
  ↓ 解析 AST（JavaParser 或正则匹配）
  ↓ 提取测试类元数据：
  │   ├── 类全限定名
  │   ├── 类级注解（@RyzeTest 等）
  │   ├── resourcePath 参数
  │   └── 测试方法清单（方法名、注解、displayName）
  ↓ 写入 api_gitlab_test_class_metadata（全量覆盖）
  ↓ 更新 last_metadata_sync_at、last_commit_sha
  ↓ 返回扫描结果
```

**元数据过期检测**：场景执行触发前，比较 `last_metadata_sync_at` 与仓库最新 commit 时间。过期（差距 > 阈值，可配置，默认 1 小时）则自动触发元数据同步后执行。

### 4.3 仓库流水线执行流程

```
触发仓库流水线执行
  ↓ 校验场景为元数据导入场景（非可执行场景）
  ↓ 校验元数据是否过期 → 过期则自动同步
  ↓ 组装 CI 变量（testScope + 自定义 variables）
  ↓ 调用 GitLab API POST /projects/:id/trigger/pipeline
  ↓ 记录流水线 ID 至 api_execution_record
  ↓ 返回触发结果
  ↓
  ↓ （异步）轮询流水线状态（定时任务或前端轮询，见《定时任务详细设计说明书》6.1）
  ↓ 流水线完成 → 拉取报告产物
  ↓ 写入 api_report 表
  ↓ 更新 api_execution_record 状态
```

---

## 5. 前端设计

### 5.1 GitLab 仓库配置页（项目设置）

- **路由**：`/workspace/projects/settings/gitlab-repos`，归入侧边栏「项目设置」分组；页面交互见《GitLab集成交互设计》第 2 章。
- **仓库配置列表**：展示仓库名称、地址、分支、最近导入状态。
- **仓库配置编辑表单**：地址、分支、令牌（密码输入框，编辑时显示 `****xxxx`）、测试源码路径。

### 5.2 测试场景页内子功能（弹窗/抽屉）

GitLab 集成的导入与执行能力为测试场景列表页的子功能，无独立路由，交互见《GitLab集成交互设计》第 3–5 章：

- **可执行导入向导弹窗**（[导入场景] 按钮）：选择仓库与文件 → 解析预览勾选场景 → 模块映射与冲突处理 → 导入进度与结果；
- **元数据同步抽屉**（[元数据同步] 按钮）：同步范围配置、变更预览、同步历史、「同步元数据」手动刷新；
- **流水线触发弹窗**（[触发流水线] 按钮）：从测试类元数据选择范围 → 配置执行参数 → 触发流水线；执行状态与结果由执行历史与报告体系承载（含流水线 ID 与链接）。

---

## 6. 实施说明

### 6.1 GitLab API 调用

所有 GitLab API 调用通过 `Private-Token` 头认证，使用 `java.net.http.HttpClient` 或 Spring `RestClient`。API 基础路径从仓库配置的 `repo_url` 推导：

```
repo_url = https://gitlab.example.com/team/robotest-tests.git
api_base = https://gitlab.example.com/api/v4
project_id = URL-encoded "team/robotest-tests"
```

### 6.2 源码下载策略

可执行导入与元数据导入均通过 GitLab Repository Files API 或 Archive API 下载源码：
- **Archive API**（`GET /projects/:id/repository/archive.zip?sha=branch`）：全量下载，适合小仓库。
- **Files API**（`GET /projects/:id/repository/files/:path/raw?ref=branch`）：按文件下载，适合大仓库按需获取。

默认使用 Archive API，仓库文件数 > 阈值（可配置，默认 500）时降级为 Files API 按路径扫描。

### 6.3 流水线状态轮询

流水线状态通过前端轮询（5 秒间隔）查询，或由定时任务每分钟批量检查进行中的流水线状态。轮询逻辑：
1. 查询 `api_execution_record` 中 `status = running` 且 `pipeline_id IS NOT NULL` 的记录。
2. 调用 GitLab API 获取流水线状态。
3. 状态变为终态 → 更新记录、拉取报告。

---

**文档结束**
