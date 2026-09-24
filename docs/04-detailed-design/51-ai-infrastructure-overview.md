# 软件测试平台——AI基础设施详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对 AI 能力域的**公共基础设施**进行详细设计，定义数据结构、接口规范与业务逻辑，为开发实现提供完整依据。各 AI 业务功能（用例生成、缺陷分析、评审辅助、全局助手等）的详细设计见对应的独立文档，它们均构建在本文档定义的基础设施之上。

### 1.2 范围

覆盖 SRS 3.3「AI 基础设施（公共需求）」与概要设计第 2/4 章对应机制：

- **AI 配置**：多对话模型配置管理（多行配置、唯一系统默认、启停，用户经交互式功能切换）与 Embedding 单一配置（含供应商预设选择与独有配置项，见 2.5）、系统配置项表单化维护、连通性测试、调用量统计（管理端）；
- **智能体**：各 AI 功能的提示词模板管理（初始化种子落库 / 页面查看修改 / 恢复默认，管理端）；
- **AI 网关**：Provider 适配、Prompt 组装、流式输出（SSE）、结构化输出校验、失败重试；
- **调用审计与限流**：调用日志、Redis 用户级滑动窗口限流；
- **异步分析任务管理**：任务表、状态机、通用查询/取消/重试接口；
- **能力开关**：AI 可用性与语义检索降级状态的查询。

所有 AI 业务接口的鉴权、上下文传递（`X-Active-Workspace` / `X-Active-Project` 请求头）沿用平台既有约定（C4）。

#### 1.2.1 AI 域上下文边界

| 场景 | 上下文入口 | 目标/资源 ID | 约束 |
| --- | --- | --- | --- |
| 管理端 AI 配置和全局任务 | 无活动 workspace/project Header | 模板、模型、供应商等资源 ID | 使用系统管理员权限，不读取项目上下文 |
| 工作空间级 AI 接口 | `X-Active-Workspace` | 会话、确认令牌等资源 ID | 服务端校验会话归属当前空间 |
| 项目级 AI 接口 | `X-Active-Workspace` + `X-Active-Project` | 文档、缺陷、任务、用例等资源 ID | 资源 ID 只能定位对象，不能替代活动上下文 |
| 助手工具调用 | 当前请求的活动上下文 Header | 工具参数中的 `projectId` 等目标资源 ID | 工具执行前由业务 Service 再次校验归属；不能信任 Payload 改写活动上下文 |


### 1.3 参考资料

- 《软件测试平台需求规格说明书》（3.3、4.1–4.5）
- 《软件测试平台概要设计说明书》（2.4–2.6、3.2、4.7、4.13、8）
- 《工程规范 — API 设计》（`docs/00-spec/05-api.md`）
- 《工程规范 — 数据库》（`docs/00-spec/06-database.md`）

---


## 2. 数据设计

### 2.1 数据库表设计

数据库为 PostgreSQL，字段 snake_case，接口 JSON 使用 camelCase。全部新表遵循平台规范：`id`（框架默认 UUID 策略）、`created_at`、`updated_at`、`is_deleted`，禁止物理外键（C5）；索引遵循 C9。

#### 2.1.1 AI 配置表（ai_config）

系统级单行表：全系统仅一条有效记录（`is_deleted = false`），首次保存时创建。存放 AI 能力总开关、系统配置项与 Embedding 单一配置；对话模型配置独立多行存放于 `ai_chat_model`（2.1.5）。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| embedding_provider | VARCHAR(50) | NULL | Embedding 供应商标识（预设注册表键，见 2.5；与对话组独立选择，未配置 Embedding 组时为空） |
| embedding_base_url | VARCHAR(500) | NULL | Embedding 服务地址（未配置则语义检索能力不可用） |
| embedding_api_key_cipher | VARCHAR(1000) | NULL | Embedding 服务密钥（加密） |
| embedding_key_suffix | VARCHAR(4) | NULL | Embedding 密钥末 4 位（脱敏展示） |
| embedding_model | VARCHAR(100) | NULL | Embedding 模型名 |
| embedding_dimension | INT | NULL | 向量维度（1–2000，保存时强制校验，见 4.10） |
| embedding_extra_params | JSONB | NOT NULL DEFAULT '{}' | Embedding 请求附加参数 |
| enabled | BOOLEAN | NOT NULL DEFAULT FALSE | AI 能力总开关 |
| settings | JSONB | NOT NULL DEFAULT '{}' | AI 系统配置项键值集（见 2.2），缺省键取代码内置默认值 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：无额外索引（单行表）。

> **迁移说明**：基线 DDL 中 ai_config 原含 chat_* 前缀六列（provider / base_url / api_key_cipher / key_suffix / model / extra_params），多对话模型改造后移除，改由 `ai_chat_model` 多行承载（见 2.1.5）；迁移时将原 chat_* 列值转为 `ai_chat_model` 的一行并置 `is_default = true`。建库脚本（`server/src/main/resources/db/v1.1.sql`）随本文档同步修订。

#### 2.1.2 智能体提示词模板表（ai_prompt_template）

默认模板初始化时**全量落库**（`server/src/main/resources/db/v1.1.sql` 种子数据，与代码内置资源同源），管理端可查看并修改；运行时仅从本表读取，不存在资源文件兜底；恢复默认即将该功能记录重置为内置默认内容（资源文件仅作恢复数据源），记录始终存在。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| function_type | VARCHAR(50) | NOT NULL | 功能类型枚举（见 2.3），每功能至多一条有效记录 |
| role_instruction | TEXT | NOT NULL | 角色指令段 |
| format_constraint | TEXT | NOT NULL | 输出格式约束段 |
| format_editable | BOOLEAN | NOT NULL DEFAULT FALSE | 格式约束段编辑开关（高级开关，默认关闭锁定） |
| updated_by | UUID | NOT NULL | 最后更新人（sys_user.id） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`uk_prompt_function_type` UNIQUE (function_type) WHERE is_deleted = false

#### 2.1.3 AI 异步任务表（ai_analysis_task）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 任务 ID |
| workspace_id | UUID | NULL | 归属工作空间（`embedding_rebuild` 全局任务为空） |
| project_id | UUID | NULL | 归属项目（`embedding_rebuild` 全局任务为空） |
| type | VARCHAR(30) | NOT NULL | 任务类型：review_check / review_summary / bug_clustering / embedding_rebuild / plan_order_recommend |
| target_id | UUID | NULL | 目标对象 ID（评审 ID 等；聚类/回填以项目为目标时为空） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'pending' | pending / running / success / failed / cancelled |
| progress | INT | NOT NULL DEFAULT 0 | 进度百分比（0–100） |
| result | JSONB | NULL | 结果快照（结构由各任务类型在对应文档定义） |
| error_message | VARCHAR(500) | NULL | 失败原因 |
| executor_instance | VARCHAR(100) | NULL | 执行实例标识（多实例防重复消费，见 4.6） |
| created_by | UUID | NOT NULL | 发起人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_task_project_id` (project_id), `idx_task_type_target` (type, target_id), `idx_task_status` (status)

> `workspace_id` 为归属字段但不单独建索引：项目级任务查询一律经项目级接口并携带 `X-Active-Project`，实际过滤走 `project_id`（全局任务 `embedding_rebuild` 的 `workspace_id`/`project_id` 为空，经管理端接口按 `type` 查询，见 3.3.5，不依赖 `workspace_id` 索引）；`workspace_id` 仅用于数据归属与联动取消的批量更新，命中量小。受 C9「单表索引不超过 5 个」约束，此处不为其建索引。

**任务类型的执行形态**（同一张表承载两类记录，状态机语义不同）：

| type | 执行形态 | 生命周期 |
| ---- | ---- | ---- |
| review_check / bug_clustering | 执行器异步任务 | 走 4.6 完整生命周期（pending → running → success/failed/cancelled），可取消/重试 |
| review_summary | SSE 流式生成 | **不经执行器抢占**；仅借本表持久化结果快照（建立即 running、`done` 帧前置 success），供事后查看与覆盖式重新生成，详见《AI 评审与测试计划辅助详细设计说明书》 |
| plan_order_recommend | 同步确定性计算 | 创建即终态 success，本表仅存结果快照，无 running 过程 |
| embedding_rebuild | 系统内部任务 | 由 Embedding 模型/维度变更触发（见 4.10）；其 LLM/Embedding 调用侧对应 2.3 功能类型枚举的 `embedding_index`（两者分属「任务类型」与「功能类型」两套枚举，指向同一后台向量重建活动），执行逻辑见《缺陷智能分析与向量检索详细设计说明书》 |

#### 2.1.4 AI 调用审计表（ai_invocation_log）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| user_id | UUID | NOT NULL | 调用用户 |
| workspace_id | UUID | NULL | 工作空间（管理端调用为空） |
| project_id | UUID | NULL | 项目（工作空间级功能为空） |
| function_type | VARCHAR(50) | NOT NULL | 功能类型枚举（见 2.3） |
| model | VARCHAR(100) | NULL | 实际调用的模型名 |
| duration_ms | INT | NULL | 端到端耗时（毫秒） |
| prompt_tokens | INT | NULL | 输入 token（取上游 usage，缺失为空） |
| completion_tokens | INT | NULL | 输出 token |
| status | VARCHAR(20) | NOT NULL | success / failed / cancelled / rate_limited / schema_invalid |
| error_code | VARCHAR(50) | NULL | 失败错误码或上游错误摘要 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_log_user_id` (user_id), `idx_log_workspace_created` (workspace_id, created_at), `idx_log_function_type` (function_type), `idx_log_created_at` (created_at)

> `idx_log_created_at` 支撑两条纯时间范围路径：4.8 每日保留期清理、3.3.4 统计接口在 `groupBy=day` / `functionType` 且 `workspace_id` 为空时的时间窗扫描（此时无法命中 `idx_log_workspace_created` 前缀）。合计 4 个普通索引，符合 C9（单表 ≤ 5）。

> 审计日志只记录调用元数据，**不存储 Prompt 与生成内容**（SRS 4.2 安全性需求：审计权限用户仅可见调用元数据，不可见对话内容）。

#### 2.1.5 对话模型配置表（ai_chat_model）

多行表：每行一个可用的对话模型配置，密钥按行独立加密存储；全系统有且仅有一行 `is_default = true`（应用层保证，见 4.11）。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键（业务请求中的模型标识 modelId） |
| name | VARCHAR(50) | NOT NULL | 显示名（管理端与用户模型选择器展示，全局唯一，如「GPT-4o」「DeepSeek-V3」） |
| provider | VARCHAR(50) | NOT NULL DEFAULT 'custom' | 供应商标识（预设注册表键，见 2.5；`custom` 为通用 OpenAI 兼容） |
| base_url | VARCHAR(500) | NOT NULL | 服务地址（OpenAI 兼容根路径，不含 `/chat/completions`） |
| api_key_cipher | VARCHAR(1000) | NOT NULL | 服务密钥（AES-256-GCM 加密，见 4.9） |
| key_suffix | VARCHAR(4) | NULL | 密钥末 4 位（脱敏展示） |
| model | VARCHAR(100) | NOT NULL | 模型名（请求体 `model` 字段值） |
| extra_params | JSONB | NOT NULL DEFAULT '{}' | 请求附加参数（厂商非标参数透传，如 `{"enable_thinking": false}`） |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE | 启用状态（停用后不出现在用户模型清单，进行中调用不中断） |
| is_default | BOOLEAN | NOT NULL DEFAULT FALSE | 是否系统默认模型（全系统唯一，见 4.11） |
| updated_by | UUID | NOT NULL | 最后更新人（sys_user.id） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`uk_chat_model_name` UNIQUE (name) WHERE is_deleted = false

> 行数为个位数量级（管理员手工维护），默认模型与启用清单查询全表扫描即可，不为 `is_default` / `enabled` 建索引（C9 从简）。

### 2.2 AI 系统配置项（ai_config.settings 键值定义）

| 键 | 类型 | 默认值 | 说明 |
| ---- | ---- | ---- | ---- |
| rateLimit.generation | int | 20 | 生成类每用户每小时调用上限 |
| rateLimit.suggestion | int | 60 | 建议类每用户每小时上限 |
| rateLimit.retrieval | int | 120 | 检索类每用户每小时上限 |
| rateLimit.task | int | 10 | 异步任务类每用户每小时上限 |
| rateLimit.assistant | int | 60 | 助手对话每用户每小时上限 |
| dedup.topK | int | 5 | 缺陷语义查重返回条数上限 |
| dedup.similarityThreshold | number | 0.75 | 缺陷查重相似度阈值 |
| clustering.similarityThreshold | number | 0.82 | 缺陷聚类并簇相似度阈值 |
| clustering.maxLabeledClusters | int | 30 | 聚类 LLM 归纳标签的簇数上限（按簇大小降序） |
| requirementContentMaxLength | int | 20000 | 需求池条目内容长度上限（字符；需求池不受 AI 开关影响，但配置键随本键值集管理） |
| missingPoint.topK | int | 100 | 遗漏测试点分析语义检索候选用例条数上限 |
| planRecommend.topK | int | 50 | 用例规划推荐语义检索条数上限 |
| planRecommend.similarityThreshold | number | 0.7 | 用例规划推荐语义相似度阈值 |
| planOrder.weights | object | {"w1":0.5,"w2":0.3,"w3":0.2} | 执行顺序推荐评分权重 |
| assistantConfirmTimeoutSeconds | int | 300 | 助手写操作确认超时（秒） |
| assistantWriteToolWhitelist | string[] | ["create_bug","create_plan_draft"] | 助手写工具启用白名单（工具名见《全局智能助手详细设计说明书》4.1） |
| logRetentionDays | int | 180 | 调用审计保留天数 |
| conversationRetentionDays | int | 180 | 助手会话保留天数 |

缺省键一律回退代码内置默认值；键清单随功能演进在本表增补。助手相关键的消费逻辑见《全局智能助手详细设计说明书》。

**配置项定义清单（表单元数据）**：上表每个键在代码中随同默认值内置一份表单定义（键名、控件类型、标签与说明文案 i18n、默认值、取值范围、所属分组），经 3.3.8 下发给管理端，前端据此渲染**完整表单**（不提供裸 JSON 编辑）。分组与控件映射：

| 分组 | 键 | 控件 |
| ---- | ---- | ---- |
| 限流阈值 | rateLimit.*（5 键） | 数字输入（≥ 1） |
| 语义查重 | dedup.topK / dedup.similarityThreshold | 数字输入（topK 1–50；阈值 0–1，步进 0.01） |
| 聚类分析 | clustering.similarityThreshold / clustering.maxLabeledClusters | 数字输入（阈值 0–1；簇数 1–100） |
| 检索与推荐 | missingPoint.topK / planRecommend.topK / planRecommend.similarityThreshold | 数字输入（同上口径） |
| 执行顺序推荐 | planOrder.weights | 三个数字输入（w1/w2/w3，各 0–1，保存校验三者之和 = 1，容差 0.001） |
| 长度限制 | requirementContentMaxLength | 数字输入（1000–100000） |
| 全局助手 | assistantConfirmTimeoutSeconds / assistantWriteToolWhitelist | 数字输入（30–3600）/ 多选框（选项为写工具枚举） |
| 数据保留 | logRetentionDays / conversationRetentionDays | 数字输入（30–3650） |

### 2.3 功能类型枚举（function_type）

功能类型是贯穿智能体模板、调用审计、限流类别的统一枚举：

| 枚举值 | 功能 | 调用形态 | 限流类别 |
| ---- | ---- | ---- | ---- |
| case_generation | 用例子树生成 | 流式 | generation |
| step_completion | 用例步骤补全 | 流式 | generation |
| review_summary | 评审摘要生成 | 流式 | generation |
| assistant_chat | 全局助手对话 | 流式 | assistant |
| priority_recommendation | 优先级推荐（LLM 兜底） | 同步 | suggestion |
| bug_form_suggestion | 缺陷标题优化与等级建议 | 同步 | suggestion |
| dsl_translation | 脑图指令翻译（DSL） | 同步 | suggestion |
| plan_order_reason | 执行顺序推荐理由 | 同步 | suggestion |
| missing_point_analysis | 遗漏测试点分析 | 同步 | retrieval |
| case_plan_recommendation | 用例规划推荐 | 同步 | retrieval |
| bug_dedup | 缺陷语义查重（Embedding） | 同步 | retrieval |
| review_check | 评审完整性检查 | 异步任务 | task |
| bug_clustering | 缺陷聚类归纳 | 异步任务 | task |
| embedding_index | 向量写入/回填/重建（Embedding） | 系统内部 | 不限流 |

> `bug_dedup` / `embedding_index` 仅调用 Embedding 接口，无提示词模板；智能体管理页只展示有模板位的功能类型。

### 2.4 数据生命周期

- `ai_invocation_log` 按 `logRetentionDays` 由每日定时任务物理清理（先逻辑删除、次日物理删除，避免长事务）；
- `ai_conversation` / `ai_message` 按 `conversationRetentionDays` 随同一每日清理任务回收（按会话 `last_active_at` 判定超期，级联清理消息；表定义见《全局智能助手详细设计说明书》2.1）；
- `ai_analysis_task` 结果随同类型新任务覆盖策略由各业务文档定义；任务记录本身不主动清理；
- `ai_config` / `ai_chat_model` / `ai_prompt_template` 变更均写入平台既有审计日志（sys_audit_log），记录操作人与动作（不记录密钥明文与模板全文 diff）。

### 2.5 供应商预设注册表（Provider Preset）

供应商预设内置于代码（资源文件随代码维护），**仅作为管理端配置引导与校验元数据**，不改变调用协议——运行期仍由唯一 `OpenAiCompatProvider` 按 OpenAI 兼容协议调用（4.1/4.2），供应商差异只体现为默认服务地址与**独有配置项**模板。每个预设声明：

| 元数据 | 说明 |
| ---- | ---- |
| key | 注册表键（`ai_chat_model.provider` / `ai_config.embedding_provider` 取值，snake_case） |
| name | 展示名（i18n 资源） |
| scopes | 适用组：`chat` / `embedding`（部分厂商不提供 Embedding 接口） |
| defaultBaseUrl | 各组默认服务地址（选择供应商后自动填充，允许修改） |
| modelHints | 常用模型名提示清单（下拉建议，不限制手工输入） |
| uniqueParams | **独有配置项模板**：键名、控件类型（boolean / number / string / enum）、默认值、取值范围、说明文案 |

**首发预设清单**（独有配置项键值随厂商 API 演进由代码更新，下表为首发基线）：

| key | scopes | 对话组独有配置项（→ `ai_chat_model.extra_params`） |
| ---- | ---- | ---- |
| openai | chat, embedding | 无 |
| deepseek | chat | 无 |
| qwen（阿里云百炼） | chat, embedding | `enable_thinking`（boolean，默认 false；兼容模式下需显式关闭思考输出） |
| zhipu（智谱） | chat, embedding | `thinking.type`（enum：enabled / disabled，默认 disabled） |
| moonshot | chat | 无 |
| custom（通用 OpenAI 兼容） | chat, embedding | 无模板，自由键值编辑 |

首发各预设的 **Embedding 组均无独有配置项**（`uniqueParams.embedding = []`，维度经标准参数 `dimensions` 传递，见 4.2.1），后续随厂商 API 演进增补。

**存储与合并规则**：

- 独有配置项**不新增存储列**，其值仍写入 `ai_chat_model.extra_params` / `ai_config.embedding_extra_params`（JSONB），运行期沿用 4.2.1 既有「白名单装配后浅合并透传」机制——预设只决定管理端渲染哪些结构化控件、默认值与类型校验；
- **模板键路径语义**：模板键支持点号路径表示嵌套参数（如 `thinking.type`），保存时按路径**展开为嵌套对象**并入 `extraParams`（`{"thinking": {"type": "disabled"}}`，而非字面键 `"thinking.type"`）；模板校验与前端控件取值同样按路径寻址；点号路径键与自定义键中的同名顶层对象合并时，模板路径值优先；
- `provider ≠ custom` 时：`extraParams` 中命中模板键的值须通过模板声明的类型/枚举校验（违规返回 1000001001，`VALIDATION_FAILED`）；模板外的自定义键**仍允许**（管理端高级折叠区维护，保留透传能力）；「不得覆盖标准参数白名单键」的既有校验（3.3.2）对全部键继续生效；
- `provider = custom` 时：仅执行既有的 JSON 对象与白名单校验，不做模板校验。

---


### 2.6 通用约定

- 管理端：`/api/admin/ai/**`，头 `Authorization`；仅系统管理员（沿用既有管理端鉴权）。
- 工作空间级：`/api/workspace/ai/**`，头 `Authorization` + `X-Active-Workspace`。
- 项目级：`/api/project/ai/**`，头 `Authorization` + `X-Active-Workspace` + `X-Active-Project`。
- 通用响应：`{ "code": 200, "msg": "success", "data": {} }`；命名 camelCase。下文各接口的响应示例**仅展示 `data` 字段内容**，省略外层 `code` / `msg` 包裹（SSE 帧格式除外）。
- 密钥字段**永不回传明文**：响应仅含 `configured`（布尔）与 `keySuffix`（末 4 位）。
- **对话模型选择**：交互式功能（用例生成、步骤补全、评审摘要、助手对话、DSL 翻译）的请求体支持可选字段 `modelId`（对话模型标识，见 2.1.5），缺省或失效时后端回退系统默认模型（解析规则见 4.11）；后台异步任务与建议类接口不接受该字段。

**SSE 流式接口统一帧格式**（`Content-Type: text/event-stream`，各生成类接口共用）：

```
event: delta
data: {"content": "增量文本"}

event: done
data: { ...该接口定义的完整结构化结果... }

event: error
data: {"code": 1000013002, "message": "AI 调用失败"}
```

- 每 15 秒发送注释行 `: ping` 心跳，防反向代理断流；
- 客户端断开连接时，服务端立即取消上游 LLM 调用，审计状态记 `cancelled`；
- `done` / `error` 后连接由服务端关闭；
- 业务接口可在上述三类之外扩展自定义事件类型（如评审摘要的 `statistics`、全局助手的 `tool_call` / `confirm_required` / `minder_commands`，定义见对应详细设计）；前端 `useAiStream()`（2.9）对未识别事件原样透传给调用方处理，不丢弃、不报错。


### 2.7 错误码补充（AI 段，1000013001–1000013099）

> AI 业务错误以 `ErrorCodeConstants` 当前登记的十位错误码为准。下表中的业务结果码用于 `Result.data` 或 SSE 帧内的业务字段，不替代外层 `Result.code`；异常路径统一通过 `ServiceExceptionUtil.get(ErrorCodeConstants.X)` 抛出（C3）。

| 错误码 | 说明 | HTTP 状态 |
| --- | --- | --- |
| 1000013001 | AI 功能未启用或配置缺失 | 503 |
| 1000013002 | AI 调用失败（上游错误/超时/网络） | 502 |
| 1000013003 | AI 输出结构化校验失败（重试后仍失败） | 502 |
| 1000013004 | AI 调用频率超限 | 429 |
| 1000013005 | 已存在进行中的同类任务 | 409 |
| 1000013006 | 任务不存在或当前状态不允许该操作 | 409 |
| 1000013007 | 连通性测试失败 | 200（业务结果，非异常） |
| 1000013008 | Embedding 维度校验失败（超上限或与实测不一致） | 400 |
| 1000013009 | 提示词模板校验失败（格式约束段锁定时被修改） | 400 |
| 1000013010 | 语义检索能力降级中（关键词模式结果，提示性语义，随正常数据返回） | 200（业务结果，非异常） |
| 1000013011 | 助手写操作确认令牌不存在或已失效（超时/已消费/空间上下文不一致，见《全局智能助手详细设计说明书》3.3） | 409 |
| 1000013012 | 目标对象状态不允许该 AI 操作（评审状态不符、计划未关联快照、项目无可分析缺陷等，语义见《AI 评审与测试计划辅助详细设计说明书》2.7 与《缺陷智能分析与向量检索详细设计说明书》3.3.1） | 409 |
| 1000013013 | 提示词模板不存在 | 409 |

> `1000013007` 和 `1000013010` 随 HTTP 200 正常响应作为业务结果返回；其余错误通过 `ServiceExceptionUtil.get(ErrorCodeConstants.X)` 抛出，错误消息使用 i18n 资源。

---


### 2.8 页面与组件

| 文件 | 说明 |
| ---- | ---- |
| `web/src/pages/admin/AiConfigPage.vue` | AI 配置页（`/admin/ai-config`）：「AI 配置」标签页（对话模型卡片区（模型列表 + 新建/编辑弹窗，弹窗内供应商下拉 + 独有配置项动态区 + 高级自定义参数折叠区，列表行内设默认/启停/测试/删除操作）+ 总开关 + Embedding 单组表单（供应商下拉 + 独有配置项 + 连通性测试）+ 系统配置项**分组表单**（按 3.3.8 定义清单动态渲染，见 2.9））+「智能体」标签页（引入 `AiAgentsTab` 组件，见下行）+「调用统计」标签页（按功能/空间/日期/模型/用户聚合表格，功能维度显示中文名） |
| `web/src/components/admin/AiAgentsTab.vue` | 智能体标签页组件（智能体列表（功能类型、是否自定义、更新人/时间）+ 编辑抽屉（角色指令段文本域、格式约束段默认只读、高级开关、恢复默认按钮）），首次切换至该标签时加载列表 |
| `web/src/components/common/AiModelSelect.vue` | 对话模型选择器（下拉，数据源为 `stores/ai.ts` 的 `chatModels`）：交互式 AI 功能入口（助手输入区、生成抽屉等）复用；选择写入 `localStorage`（见 4.11），仅一个可用模型时渲染只读标签（不可切换），无可用模型时不渲染 |
| `web/src/services/admin.ts` | 增补 3.3 / 3.4 接口封装（含 3.3.6 供应商预设查询与 3.3.7 对话模型管理，进入配置页时拉取一次） |
| `web/src/stores/ai.ts` | 新增：缓存 `GET /api/workspace/ai/status` 结果，暴露 `aiEnabled` / `semanticSearch` / `chatModels` 计算属性，供全部 AI 入口组件显隐判断与模型选择器渲染；负责校验并回收 `localStorage` 中失效的 `modelId`（4.11） |
| `web/src/types/index.ts` | 增补 AiConfig、AiChatModel、AiProviderPreset、AiAgent、AiTask、AiStatus 等类型（无 `any`，C1） |


### 2.9 交互要点

- AdminLayout 菜单新增「AI 配置」一项（沿用既有菜单权限控制，仅系统管理员可见），智能体作为该页第 2 个标签页；
- **供应商切换交互**：选择供应商后自动填充该组 `defaultBaseUrl` 并按 `uniqueParams` 模板渲染独有配置项控件（取默认值）；已有配置下切换供应商时弹二次确认（提示将重置该组地址与独有配置项，模型名与密钥保留待用户自行核对）；`custom` 供应商不渲染独有配置项区，仅保留高级自定义参数折叠区（自由键值编辑）；独有配置项与自定义参数在提交时合并为 `extraParams`（模板键在前，重名以独有配置项控件值为准）；
- 密钥输入框：占位符显示 `已配置（末位 ****）`，留空提交表示不修改；
- **模型选择器交互**：交互式 AI 功能入口渲染 `AiModelSelect`，默认选中 `localStorage` 记忆值（失效则回退系统默认，见 4.11）；切换后立即写入记忆并作用于本次及后续调用；后台任务与建议类功能不展示选择器；
- **系统配置项表单**：进入配置页拉取 3.3.8 定义清单与 3.3.1 合并视图后按分组渲染类型化控件（数字输入带 min/max 与步进、多选框、权重组合输入），**不提供裸 JSON 编辑**；每项展示说明文案与默认值提示，值偏离默认时在说明文案后同一行显示「已修改」标记并提供单项 [恢复默认]；前端按定义做即时校验（越界红字提示，`planOrder.weights` 之和实时校验），保存随 3.3.2 整体提交；
- **模型配置表单**：对话模型与 Embedding 模型的必填校验仅在点击保存或连通性测试时触发，校验失败不提交请求；Embedding 的供应商、服务地址、模型名、向量维度与 API 密钥均为必填项，已配置密钥编辑时允许留空保持原值；
- 格式约束段编辑：高级开关开启时弹出二次确认（说明可能导致结构化校验失败的风险）；
- 业务端全部 AI 入口组件挂载时读取 `stores/ai.ts`，`aiEnabled === false` 时不渲染；SSE 消费封装为公共组合式函数 `useAiStream()`（基于 `fetch` + `ReadableStream` 解析 2.6 帧格式，支持取消），供各业务文档引用。

---


## 3. 实施说明

- **数据库迁移**：现有全量建库脚本 `init.sql` 更名为 `v1.sql`（作为 V1.0 建库基线，内容保持不变）；V1.1 新增的表结构统一存入新脚本 `server/src/main/resources/db/v1.1.sql`——本文档 2.1 的五张 AI 表 DDL（含 `ai_chat_model`）与 `CREATE EXTENSION IF NOT EXISTS vector`（为后续向量表铺垫）写入 `v1.1.sql`，其余详细设计新增的表亦追加至同一脚本；首次建库按 `v1.sql` → `v1.1.sql` 顺序执行，无存量数据迁移。V1.1 未发布，多对话模型改造直接修订 `v1.1.sql`（ai_config 移除 chat_* 列、新增 ai_chat_model 表），不另立增量脚本；已按旧版 v1.1.sql 建库的开发环境按 2.1.1 迁移说明手工迁移；
- **模块归属**：后端代码位于 `service/ai`、`controller/admin`（配置/智能体）、`controller/workspace`（status）、`controller/project`（tasks），遵循既有分层（C2：Controller 无业务逻辑）；
- **定时任务**：pending 任务拾取（4.6，每 30 秒）、孤儿任务回收（4.6，每 5 分钟）与审计/会话清理（4.8，每日 03:00）依赖 `@Scheduled`，需在应用启动类新增 `@EnableScheduling`（当前工程仅有 `@EnableAsync`，无调度先例）；多实例部署下各实例均会触发，三者均以带状态/时间谓词的条件 `UPDATE`（拾取经 4.6 抢占更新）实现、天然幂等，无需额外分布式锁；若后续出现严格单次执行需求，再复用既有 Redis 加锁。
- **实施顺序**：本文档对应 SRS 实施梯队一的基础部分，先于其余 4 份详细设计对应的功能开发。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `51-ai-infrastructure-overview.md` | 前言、1. 引言、2. 数据设计、2.6 通用约定、2.7 错误码补充、2.8 页面与组件、2.9 交互要点、6. 实施说明 |
| 能力开关 | `52-ai-infrastructure-switch.md` | 3.2 能力开关接口、4.10 能力开关与降级状态计算 |
| AI 配置 | `53-ai-infrastructure-config.md` | 3.3 AI 配置接口 |
| 智能体 | `54-ai-infrastructure-agent.md` | 3.4 智能体接口 |
| 异步任务 | `55-ai-infrastructure-async.md` | 3.5 异步任务通用接口、4.6 异步任务生命周期 |
| 网关与调用链路 | `56-ai-infrastructure-gateway.md` | 4.1 AI 网关总体结构、4.2 Provider 适配器、4.3 Prompt 组装与注入隔离、4.4 结构化输出防线、4.5 流式调用链路、4.7 限流、4.8 调用审计、4.9 密钥加密存储、4.11 对话模型解析与默认唯一性 |
