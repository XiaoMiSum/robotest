# 软件测试平台——数据与接口设计

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 数据设计

### 1.1 核心实体关系图

    User ──── UserRole ──── Role (type: system / workspace, scope: global / workspace, full_access: bool)
    User ──── UserWorkspace (workspaceRole: UUID → sys_role.id) ──── Workspace
    Workspace ──── Project ──── TestCaseModule
                     ├── TestPlan ─── PlanModuleSnapshot
                     │                PlanNodeSnapshot
                     │                PlanExecutionRecord
                     ├── TestReview ─── ReviewModuleSnapshot
                     │                  ReviewNodeSnapshot
                     │                  ReviewRecord
                     └── Bug

AI 能力域实体关系：

    Project ──── RequirementPoolItem（需求池条目）
    TestCaseModule(文档) ──── DocumentRequirementRel ──── RequirementPoolItem
    Bug ──── BugEmbedding（1:1，向量索引）
    TestCaseNode(用例节点) ──── CaseEmbedding（1:1，向量索引，随语义升级梯队启用）
    User ──── AiConversation ──── AiMessage
    TestReview / Project ──── AiAnalysisTask（含结果快照）
    （全局）AiInvocationLog（调用审计）
    （全局）AiConfig（AI 配置，系统级）
    （全局）AiChatModel（对话模型配置，系统级，多行，唯一默认）
    （全局）AiPromptTemplate（智能体提示词模板，系统级，功能类型唯一）

### 1.2 核心数据对象概要

| 对象                   | 描述                                  |
| -------------------- | ----------------------------------- |
| User                 | 账号、邮箱、密码哈希、状态                       |
| Role                 | 名称、类型（system/workspace）、作用域（global/workspace）、全量权限标记、权限点列表、是否系统预置 |
| UserRole             | 用户与角色多对多关联                          |
| Workspace            | 名称、描述、状态                            |
| UserWorkspace        | 用户-工作空间关联，含 `workspaceRole` 和加入时间   |
| Project              | 名称、描述、状态、所属工作空间                     |
| TestCaseModule       | 模块树节点（目录/文档）                        |
| TestCaseNode         | 脑图节点（用例/普通），树形结构                    |
| TestPlan             | 计划基本信息                              |
| PlanModuleSnapshot   | 计划-模块快照                             |
| PlanNodeSnapshot     | 计划-节点快照，冗余最后执行结果                    |
| PlanExecutionRecord  | 执行记录                                |
| TestReview           | 评审基本信息（发起人、参与者）                     |
| ReviewModuleSnapshot | 评审-模块快照                             |
| ReviewNodeSnapshot   | 评审-节点快照，冗余最后评审标记                    |
| ReviewRecord         | 评审记录（标记/评论）                         |
| Bug                  | 缺陷信息，状态流转                           |

AI 能力域数据对象：

| 对象 | 描述 |
| ---- | ---- |
| AiConfig | 系统级全局配置：AI 能力总开关、系统配置项键值集、Embedding 单一配置（供应商、服务地址、密钥（加密存储）、模型名、向量维度） |
| AiChatModel | 对话模型配置（多行，全系统唯一默认）：供应商、服务地址、密钥（加密存储）、模型名、附加参数、启用状态、是否默认 |
| AiPromptTemplate | 智能体提示词模板（功能类型唯一键）：角色指令段、输出格式约束段、格式约束段编辑开关；仅存自定义覆盖，默认模板内置于代码 |
| RequirementPoolItem | 需求池条目（项目级）：标题、内容（Markdown）、来源 URL（仅记录出处，不抓取）、状态（active / archived）、更新人 |
| DocumentRequirementRel | 文档-需求条目关联（多对多） |
| BugEmbedding | 缺陷语义向量：与缺陷记录一对一，无物理外键；含源文本哈希用于判断向量是否过期 |
| CaseEmbedding | 用例语义向量：与用例节点一对一；供遗漏测试点分析与用例规划推荐的语义匹配使用，随语义升级梯队启用（见 AD-5 / 4.11） |
| AiConversation | 助手会话（用户 + 工作空间归属）：标题、最后活跃时间 |
| AiMessage | 会话消息：角色（user/assistant/tool）、内容、工具调用载荷 |
| AiAnalysisTask | 异步任务：类型（评审检查/评审摘要/缺陷聚类/向量回填）、目标对象、状态（含 cancelled）、进度、结果快照、失败原因；评审摘要虽经 SSE 流式生成，其最终结果同样落入本表结果快照，供事后查看与覆盖式重新生成 |
| AiInvocationLog | 调用审计：用户/空间/项目归属、功能类型、模型、耗时、token 用量、状态 |

AI 能力域全部数据表遵循平台数据库规范：必含 `id`、`created_at`、`updated_at`、`is_deleted`（逻辑删除），主键类型与索引规范沿用平台既有约定，禁止物理外键；业务归属字段（项目 / 工作空间）用于数据隔离过滤。向量表按语义相似度检索需要建立向量索引，具体索引类型在详细设计确定。

### 1.3 数据隔离与生命周期

业务数据通过所属项目 → 工作空间间接隔离，查询时强制附加上下文条件。管理端无此限制。

AI 能力域数据的隔离与生命周期：

* 需求池、分析任务、向量索引按项目隔离；助手会话按用户 + 工作空间隔离；审计日志全局存储、按空间过滤查询。
* 需求池条目随项目删除而删除；BugEmbedding 随缺陷删除而删除；会话支持用户清空；分析任务结果可被同类型新任务覆盖。
* AI 调用审计与会话历史按系统配置的保留期限归档清理；语义查重/聚类检索排除已逻辑删除的缺陷。

---

## 2. 接口设计概要

### 2.1 接口风格与约定

* 协议：HTTPS，数据格式 JSON。
* 认证：请求头 `Authorization: Bearer <token>`。
* 接口分类：
  * 管理接口：`/api/admin`，需要系统角色。
  * 工作空间级业务接口：`/api/workspace`，需头 `X-Active-Workspace`。
  * 项目内业务接口：`/api/project`，需头 `X-Active-Project`。
* 上下文传递：工作空间和项目 ID 通过请求头传递，不在 URL 中暴露。
* 通用响应格式：`{ "code": 200, "message": "success", "data": {...} }`
* AI 能力遵循上述约定；经 AI 网关的能力按归属范围归入项目内业务接口与工作空间级接口（助手），管理端 AI 配置归入管理端接口；具体路径在详细设计阶段定义。
* 需求池为常规业务实体（不经 AI 网关），归入常规项目业务路径，AI 功能仅将其作为上下文消费，以免受 AI 整体降级开关影响；相应地，**需求池入口不随 AI 开关隐藏**，AI 不可用时条目管理照常可用（与 SRS 3.2.4 业务规则对齐）。
* 流式接口以 `text/event-stream` 响应。

### 2.2 认证与初始化接口

* **初始化状态**：`GET /api/auth/init/status`，返回 `{ initialized: boolean }`，无需认证。
* **初始化设置**：`POST /api/auth/init/setup`，提交管理员密码创建 admin 账号，无需认证。
* **登录**：`POST /api/auth/login`，返回双令牌及用户信息。
* **刷新令牌**：`POST /api/auth/refresh`，通过刷新令牌延长会话。
* **修改密码**：`POST /api/auth/change-password`，需认证。

### 2.3 管理端接口概要

* **用户管理**：CRUD，支持多系统角色分配，状态启用/禁用/锁定。
* **数据概览**：平台指标统计聚合（无独立权限点，可访问管理端即可读取）。
* **工作空间管理**：创建、归档/重新启用、成员管理（含设置空间管理员）。
* **系统角色管理**：CRUD，权限配置（仅系统角色）。
* **权限点树**：获取管理端权限树（按模块分组）。

### 2.4 业务端接口概要

* **我的工作空间**：查看归属列表、切换活跃空间、编辑信息。
* **工作空间成员管理**：邀请、移除、设置空间管理员。
* **项目管理**：CRUD、归档。
* **测试用例**：模块树、脑图节点、用例详情。
* **测试评审**：发起、快照树、模块快照树、评审记录、同步、调整用例。
* **测试计划**：创建、快照树、模块快照树、执行记录、同步、调整用例。
* **缺陷管理**：CRUD、状态流转。

详细端点定义参见《API 详细设计文档》。

### 2.5 AI 能力接口分组概要

* **能力开关**：查询 AI 可用性（前端据此显隐入口），随同下发可供用户切换的对话模型清单（仅标识与显示名，脱敏）。
* **管理端**：AI 配置（总开关 / Embedding / 系统配置项）、对话模型管理（增删改查 / 设默认 / 启停）、连通性测试、调用量统计、智能体提示词模板（查看含默认值/自定义覆盖/恢复默认/格式约束段编辑开关）。
* **生成类（SSE）**：用例子树生成、步骤补全、评审摘要生成。
* **同步建议类**：优先级推荐、缺陷标题优化与等级建议、DSL 翻译、执行顺序推荐（含理由生成）。
* **检索类**：缺陷语义查重、遗漏测试点分析、用例规划推荐。
* **异步任务类**：创建任务（评审检查/缺陷聚类）、查询任务状态与结果、重试。
* **需求池**：条目 CRUD、归档/取消归档、关键字检索、文档关联维护（常规业务接口，不经 AI 网关，见 2.1）。
* **助手（SSE）**：会话 CRUD、发送消息（流式回复 + 工具调用事件）、写操作确认/取消。

详细端点、请求/响应结构在详细设计阶段定义（先后端后前端，经 OpenAPI 同步）。

### 2.6 WebSocket 接口概要

* 端点：`/ws/documents/:docId`
* 认证：连接时通过查询参数传递 Token。
* 消息基于 JSON，支持节点增删改移、布局更新。
* 广播至同一文档的在线用户。
* 文档协作 WebSocket 保持既有设计：AI 生成节点经前端挂载后走既有编辑帧持久化（AD-3），协同广播复用既有二进制帧。
