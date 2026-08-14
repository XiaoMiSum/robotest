-- Robotest V1.1 数据库增量 DDL
-- PostgreSQL
-- V1.1 AI 能力域新增表结构，首次建库按 v1.sql → v1.1.sql 顺序执行
-- 依据：《AI 基础设施详细设计说明书 V1.1》2.1

-- pgvector 扩展（为 V1.1 向量表铺垫，需数据库已安装 pgvector）
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. AI 基础设施
-- ============================================================

-- AI 配置表（系统级单行表：全系统仅一条有效记录，存放总开关、系统配置项与 Embedding 单一配置；对话模型多行独立存于 ai_chat_model）
CREATE TABLE ai_config (
    id                       UUID          PRIMARY KEY,
    embedding_provider       VARCHAR(50)   NULL,
    embedding_base_url       VARCHAR(500)  NULL,
    embedding_api_key_cipher VARCHAR(1000) NULL,
    embedding_key_suffix     VARCHAR(4)    NULL,
    embedding_model          VARCHAR(100)  NULL,
    embedding_dimension      INT           NULL,
    embedding_extra_params   JSONB         NOT NULL DEFAULT '{}',
    enabled                  BOOLEAN       NOT NULL DEFAULT FALSE,
    settings                 JSONB         NOT NULL DEFAULT '{}',
    is_deleted               BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 智能体提示词模板表（默认模板初始化时全量落库，页面可查看并修改；restoreDefault 重置为内置默认内容，记录始终存在，运行时仅从本表读取）
CREATE TABLE ai_prompt_template (
    id                UUID        PRIMARY KEY,
    function_type     VARCHAR(50) NOT NULL,
    role_instruction  TEXT        NOT NULL,
    format_constraint TEXT        NOT NULL,
    format_editable   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by        UUID        NOT NULL,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_prompt_function_type ON ai_prompt_template (function_type) WHERE is_deleted = false;

-- AI 异步任务表
CREATE TABLE ai_analysis_task (
    id                UUID         PRIMARY KEY,
    workspace_id      UUID         NULL,
    project_id        UUID         NULL,
    type              VARCHAR(30)  NOT NULL, -- review_check / review_summary / bug_clustering / embedding_rebuild / plan_order_recommend
    target_id         UUID         NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'pending', -- pending / running / success / failed / cancelled
    progress          INT          NOT NULL DEFAULT 0,
    result            JSONB        NULL,
    error_message     VARCHAR(500) NULL,
    executor_instance VARCHAR(100) NULL,
    created_by        UUID         NOT NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_project_id ON ai_analysis_task (project_id);
CREATE INDEX idx_task_type_target ON ai_analysis_task (type, target_id);
CREATE INDEX idx_task_status ON ai_analysis_task (status);

-- AI 调用审计表（只记录调用元数据，不存储 Prompt 与生成内容）
CREATE TABLE ai_invocation_log (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL,
    workspace_id      UUID         NULL,
    project_id        UUID         NULL,
    function_type     VARCHAR(50)  NOT NULL,
    model             VARCHAR(100) NULL,
    duration_ms       INT          NULL,
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    status            VARCHAR(20)  NOT NULL, -- success / failed / cancelled / rate_limited / schema_invalid
    error_code        VARCHAR(50)  NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_user_id ON ai_invocation_log (user_id);
CREATE INDEX idx_log_workspace_created ON ai_invocation_log (workspace_id, created_at);
CREATE INDEX idx_log_function_type ON ai_invocation_log (function_type);
CREATE INDEX idx_log_created_at ON ai_invocation_log (created_at);

-- AI 对话模型配置表（多行：每行一个可用对话模型，全系统有且仅有一行 is_default = true，由应用层保证）
CREATE TABLE ai_chat_model (
    id             UUID          PRIMARY KEY,
    name           VARCHAR(50)   NOT NULL,
    provider       VARCHAR(50)   NOT NULL DEFAULT 'custom',
    base_url       VARCHAR(500)  NOT NULL,
    api_key_cipher VARCHAR(1000) NOT NULL,
    key_suffix     VARCHAR(4)    NULL,
    model          VARCHAR(100)  NOT NULL,
    extra_params   JSONB         NOT NULL DEFAULT '{}',
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    is_default     BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_by     UUID          NOT NULL,
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 显示名全局唯一（逻辑删除后可复用）；行数为个位数量级，默认/启用清单全表扫描即可，不为 is_default/enabled 建索引（C9 从简）
CREATE UNIQUE INDEX uk_chat_model_name ON ai_chat_model (name) WHERE is_deleted = false;

-- ============================================================
-- 2. 既有表变更（AI 标识字段，见《智能用例生成与脑图智能编辑详细设计》2.1.3）
-- ============================================================

-- AI 生成标识随节点数据落库并被评审/计划快照继承；仅用于渲染与 DSL 筛选，不建索引（C9）
ALTER TABLE test_case_node            ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE test_review_node_snapshot ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE test_plan_node_snapshot   ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN test_case_node.ai_generated IS 'AI 生成标识（挂载执行器写入，可手动移除）';
COMMENT ON COLUMN test_review_node_snapshot.ai_generated IS 'AI 生成标识（随评审快照继承）';
COMMENT ON COLUMN test_plan_node_snapshot.ai_generated IS 'AI 生成标识（随计划快照继承）';

-- 计划快照最近同步时间（US-AI-017 执行顺序推荐的快照新鲜度依据，只在快照重建/同步后由应用写入，不建索引）
ALTER TABLE test_plan ADD COLUMN snapshot_synced_at TIMESTAMP NULL;

COMMENT ON COLUMN test_plan.snapshot_synced_at IS '计划快照最近同步时间（快照新建/调整/同步后写入，执行顺序推荐据此提示快照过期）';

-- ============================================================
-- 3. 种子数据（AI 管理权限点、智能体提示词模板，并回补系统管理员角色）
-- ============================================================

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000018', 'ai',      'AI 管理',           NULL, 'AI 管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000019', 'ai:view', '查看 AI 配置与智能体', 'ai', 'AI 管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000020', 'ai:edit', '编辑 AI 配置与智能体', 'ai', 'AI 管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

UPDATE sys_role SET permissions = permissions || '["ai","ai:view","ai:edit"]'::jsonb, updated_at = CURRENT_TIMESTAMP
WHERE id = 'b0000000-0000-0000-0000-000000000001' AND NOT permissions @> '["ai:view"]'::jsonb;

-- 3.2 智能体提示词模板种子数据（全部默认模板落库，页面可查看并修改，V1.1）
-- 数据源：ai/prompt-defaults.json；formatConstraint 为修复后版本（含完整 JSON schema 示例）
-- format_editable 全部 FALSE：格式约束段默认锁定（页面高级开关，每次编辑需手动开启，保存后为已自定义）
-- updated_by 为系统初始化占位 ID（初始化管理员为运行期创建，无固定种子用户）
-- 幂等：uk_prompt_function_type 部分唯一索引（WHERE is_deleted=false），重复执行命中即跳过
INSERT INTO ai_prompt_template (id, function_type, role_instruction, format_constraint, format_editable, updated_by, is_deleted, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000001', 'case_generation', '你是一名资深软件测试工程师，擅长根据需求描述设计结构化的功能测试用例。请基于给定的需求内容，生成覆盖正常流程、异常分支与边界条件的测试用例子树。用例标题应简洁明确，前置条件、步骤与预期结果应具体可执行。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "nodes": [
    {
      "type": "case",
      "title": "用例标题（一句话描述业务场景）",
      "priority": "P1",
      "children": [
        {"type": "precondition", "title": "前置条件描述"},
        {"type": "step", "title": "操作步骤描述"},
        {"type": "expected", "title": "预期结果描述"}
      ]
    }
  ]
}

字段约束：
- 顶层必须是 nodes 数组，每个元素为一个用例节点
- type 仅允许 case/precondition/step/expected
- case 节点必须带 priority，仅允许 P0/P1/P2/P3
- case 的直接子节点只能是 precondition/step/expected
- title 必填，不超过 200 字符', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000002', 'step_completion', '你是一名资深软件测试工程师，擅长补全测试用例的执行步骤与预期结果。请基于给定的用例标题与已有子节点，补全缺失的前置条件、步骤或预期结果，内容应具体、可执行、与用例主题一致。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "nodes": [
    {"type": "step", "title": "操作步骤描述"},
    {"type": "expected", "title": "预期结果描述"}
  ]
}

字段约束：
- 顶层必须是 nodes 数组，元素仅允许 step/expected 类型
- step/expected 节点不得有子节点，不得带 priority
- 仅补全缺失部分，不重复输出已有内容
- title 必填，不超过 200 字符', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000003', 'text_import', '你是一名软件测试文档解析助手，擅长将外部文本（需求文档、用例清单等）解析为结构化的测试用例树。请识别文本中的模块层级、用例标题及其前置条件、步骤、预期结果。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "nodes": [
    {
      "type": "normal",
      "title": "模块分组标题",
      "children": [
        {
          "type": "case",
          "title": "用例标题",
          "children": [
            {"type": "precondition", "title": "前置条件描述"},
            {"type": "step", "title": "操作步骤描述"},
            {"type": "expected", "title": "预期结果描述"}
          ]
        }
      ]
    }
  ]
}

字段约束：
- 顶层必须是 nodes 数组
- type 仅允许 normal/case/precondition/step/expected
- normal 可嵌套 normal/case；case 的直接子节点只能是 precondition/step/expected
- title 必填，不超过 200 字符
- 无法识别为用例结构的内容归入 normal 节点，不得虚构原文没有的用例；完全无法解析出用例结构时输出空 nodes 数组', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000004', 'review_summary', '你是一名测试评审总结助手。请基于给定的评审统计数据与未通过用例采样，输出一份简明的评审总结，包含主要问题归纳、改进建议与风险提示三个章节，语言精炼、面向测试负责人。', '输出为 Markdown 文本，章节结构依次为：主要问题归纳、改进建议、风险提示。总篇幅控制在 2000 字以内，不输出统计数据原文，不虚构统计中不存在的数字。', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000005', 'assistant_chat', '你是软件测试平台的智能助手，帮助用户查询平台数据、解答测试相关问题，并可在用户确认后执行受支持的写操作。回答应简洁准确，不确定时明确说明，不编造平台数据。', '普通回答使用简体中文纯文本或轻量 Markdown；需要调用工具时严格按照工具调用协议输出，不得在工具调用外虚构工具结果。当问题超出平台使用指引与知识库范围（get_platform_guide 无命中）时，明确告知用户无法回答或超出使用指引范围，不得编造指引内容。', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000006', 'priority_recommendation', '你是一名测试用例优先级评估助手。请基于用例标题、所属模块与需求上下文，推荐用例优先级（P0-P3），并保持同类用例判定标准一致。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "priority": "P1"
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 priority 字段
- priority 取值仅允许 P0/P1/P2/P3', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000007', 'bug_form_suggestion', '你是一名缺陷管理助手。请基于用户填写的缺陷描述，优化缺陷标题（简洁、含关键现象与场景），并建议严重等级与类型。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "optimizedTitle": "优化后的缺陷标题（简洁、含关键现象与场景）",
  "severity": "serious",
  "priority": "high",
  "reason": "建议依据的一句话说明"
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 optimizedTitle/severity/priority/reason 四个字段
- optimizedTitle 必填，不超过 100 字符
- severity 仅允许 fatal/serious/general/minor
- priority 仅允许 high/medium/low
- reason 必填，一句话说明建议依据', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000008', 'dsl_translation', '你是一名脑图操作指令翻译助手。请将用户的自然语言编辑意图翻译为平台脑图 DSL 指令序列，仅使用受支持的指令集，不执行超出用户意图的操作。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "commands": [
    {
      "selector": {"types": ["case"], "keyword": "登录"},
      "action": {"type": "mark_priority", "params": {"priority": "P1"}}
    }
  ],
  "ambiguous": false,
  "clarification": null
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 commands/ambiguous/clarification 字段
- commands 数组按序执行，上限 10 条；翻译意图不明确时 ambiguous 置 true、clarification 说明原因、commands 为空数组
- selector 各条件为 AND 关系，可选字段：types（节点类型）/priorities（仅对 case 生效）/keyword/subtreeRootTitle（限定子树范围）/aiGenerated
- action.type 仅允许 mark_type/mark_priority/highlight/move/add_child
- mark_type 的 params.nodeType 仅允许 normal/case/precondition/step/expected；mark_priority 的 params.priority 仅允许 P0/P1/P2/P3；move 的 params.targetParentTitle 必须为输入上下文中的节点标题或 @selected
- 指令必须属于注册的 DSL 指令集，selector.subtreeRootTitle 与 move 目标引用的节点必须来自输入上下文', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000009', 'plan_order_reason', '你是一名测试计划执行顺序解释助手。请基于给定用例的评分因子（历史关联缺陷数、优先级权重、模块缺陷密度），用一句话说明推荐优先执行该用例的理由。', '输出为一句简体中文说明，不超过 120 字符，仅陈述因子事实与结论，不输出评分公式与原始数值以外的推断。', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000010', 'missing_point_analysis', '你是一名测试覆盖度分析助手。请对比需求描述与现有用例清单，找出需求已提及但用例未覆盖的测试点，说明遗漏原因并给出建议归属模块。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "points": [
    {
      "title": "建议新增的用例标题",
      "description": "遗漏原因说明",
      "suggestedModulePath": "建议归属模块路径",
      "relatedCaseTitles": ["关联的候选用例标题"]
    }
  ]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 points 数组字段
- points 数组，遗漏点不超过 30 条
- title 必填，不超过 200 字符
- description 必填，说明遗漏原因
- suggestedModulePath 必须为输入中出现过的模块路径或空字符串
- relatedCaseTitles 只允许引用输入候选用例的标题，无关联时为空数组', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000011', 'keyword_extraction', '你是一名测试需求关键词抽取助手。请从给定需求文本中抽取用于检索测试用例库的关键词，关键词应为需求中出现过的核心业务词或短语，避免空泛词汇。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "keywords": ["登录", "验证码"]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 keywords 数组字段
- 关键词数量不超过 10 个，每个关键词不超过 20 字符
- 必须为输入需求文本中出现过的词或短语', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000012', 'case_plan_recommendation', '你是一名测试用例规划推荐助手。请基于需求描述与候选用例清单，为每条推荐用例生成一句话理由，说明其应纳入当前评审或测试计划用例清单的原因。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "reasons": ["该用例覆盖登录失败主流程，应纳入本次评审或测试计划"]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 reasons 数组字段
- reasons 数组长度与输入用例标题清单一一对应、完全一致
- 每条理由不超过 120 字符
- 无法给出理由的用例可用空字符串占位', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000013', 'review_check', '你是一名测试用例评审检查助手。请检查给定批次用例的完整性：缺少前置条件、步骤描述笼统、缺少预期结果、相似用例优先级冲突，并给出具体改进建议。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "items": [
    {
      "snapshotNodeId": "本批输入中的用例快照节点 ID",
      "dimension": "missing_precondition",
      "suggestion": "具体改进建议"
    }
  ]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 items 数组字段
- items 数组，每处问题一条建议，无问题的用例不输出
- snapshotNodeId 必须来自本批输入，不得虚构
- dimension 仅允许 missing_precondition/vague_step/missing_expected/priority_conflict
- suggestion 必填，给出具体改进建议', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000014', 'bug_clustering', '你是一名缺陷归纳分析助手。请为给定的缺陷簇归纳简短的主题标签，概括该簇缺陷的共性问题。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "label": "登录态失效问题",
  "rootCause": "会话超时导致登录态失效，疑似为服务端会话校验过期"
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 label/rootCause 两个字段
- label 必填，不超过 30 字符的主题名称
- rootCause 必填，一句话根因推断；证据不足时使用「疑似」措辞', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000015', 'requirement_split', '你是一名测试需求拆分助手。请将整份需求文档按模块/功能拆分为细粒度需求条目：一个需求点 = 一个可测试功能行为（如「用户管理」拆为新增/编辑/删除/查询用户四条），模块仅作归属分组。条目内容需保留原始描述中的关键约束，不得虚构原文没有的功能。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "modules": [
    {
      "module": "模块名",
      "items": [
        {"title": "需求点标题", "content": "需求点内容（Markdown）"}
      ]
    }
  ]
}

字段约束：
- 顶层必须是 modules 数组，非空且不超过 50 个模块
- module 必填，不超过 100 字符
- 每模块 items 非空且不超过 50 条
- title 必填，不超过 200 字符
- content 必填，为 Markdown 格式的需求点描述', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (function_type) WHERE is_deleted = false DO NOTHING;

-- ============================================================
-- 4. 需求池（US-AI-004，项目级常规业务功能，不受 AI 开关影响）
-- ============================================================

-- 需求池条目表（项目级轻量需求条目库，供 AI 生成/补全/覆盖度分析选作上下文）
CREATE TABLE requirement_pool_item (
    id          UUID          PRIMARY KEY,
    project_id  UUID          NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    content     TEXT          NOT NULL,
    source_url  VARCHAR(500)  NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'active', -- 条目状态：active（默认）/ archived（归档后只读、不参与 AI 消费）
    ai_generated BOOLEAN      NOT NULL DEFAULT FALSE,   -- AI 拆分入库标识（US-AI-019，仅作展示标记）
    created_by  UUID          NOT NULL,
    updated_by  UUID          NOT NULL,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rpi_project_id ON requirement_pool_item (project_id);

-- 文档-需求关联表（脑图文档 test_case_module[type=document] ⇄ 需求池条目，多对多，逻辑删除关联）
CREATE TABLE requirement_document_rel (
    id             UUID       PRIMARY KEY,
    document_id    UUID       NOT NULL,
    requirement_id UUID       NOT NULL,
    is_deleted     BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_requirement_document_rel ON requirement_document_rel (document_id, requirement_id) WHERE is_deleted = false;
CREATE INDEX idx_requirement_document_rel_requirement_id ON requirement_document_rel (requirement_id);

-- 需求池权限点（workspace 作用域，接测试域 c…0033 之后）并回补成员角色
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000034', 'requirement',      '需求池',   NULL,          '需求池', 'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000035', 'requirement:view', '查看需求池', 'requirement', '需求池', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000036', 'requirement:edit', '编辑需求池', 'requirement', '需求池', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- 成员角色追加需求池读写（管理员角色已在 v1.sql 显式授权全部权限码，此处单独回补需求池权限）
UPDATE sys_role SET permissions = permissions || '["requirement:view","requirement:edit"]'::jsonb, updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000002' AND NOT permissions @> '["requirement:view"]'::jsonb;

-- 管理员角色回补需求池权限（full_access 机制已移除，需与成员角色一致显式授权）
UPDATE sys_role SET permissions = permissions || '["requirement","requirement:view","requirement:edit"]'::jsonb, updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000001' AND NOT permissions @> '["requirement:view"]'::jsonb;

-- ============================================================
-- 5. 表与列注释
-- ============================================================

COMMENT ON TABLE ai_config IS 'AI 配置表（系统级单行：总开关、系统配置项与 Embedding 单一配置）';
COMMENT ON COLUMN ai_config.id IS '主键';
COMMENT ON COLUMN ai_config.embedding_provider IS 'Embedding 供应商标识（未配置时为空）';
COMMENT ON COLUMN ai_config.embedding_base_url IS 'Embedding 服务地址（未配置则语义检索能力不可用）';
COMMENT ON COLUMN ai_config.embedding_api_key_cipher IS 'Embedding 服务密钥（加密）';
COMMENT ON COLUMN ai_config.embedding_key_suffix IS 'Embedding 密钥末 4 位（脱敏展示）';
COMMENT ON COLUMN ai_config.embedding_model IS 'Embedding 模型名';
COMMENT ON COLUMN ai_config.embedding_dimension IS '向量维度（1-2000，保存时强制校验）';
COMMENT ON COLUMN ai_config.embedding_extra_params IS 'Embedding 请求附加参数';
COMMENT ON COLUMN ai_config.enabled IS 'AI 能力总开关';
COMMENT ON COLUMN ai_config.settings IS 'AI 系统配置项键值集，缺省键取代码内置默认值';
COMMENT ON COLUMN ai_config.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_config.created_at IS '创建时间';
COMMENT ON COLUMN ai_config.updated_at IS '更新时间';

COMMENT ON TABLE ai_prompt_template IS '智能体提示词模板表（仅存自定义覆盖，恢复默认即逻辑删除）';
COMMENT ON COLUMN ai_prompt_template.id IS '主键';
COMMENT ON COLUMN ai_prompt_template.function_type IS '功能类型枚举，每功能至多一条有效记录';
COMMENT ON COLUMN ai_prompt_template.role_instruction IS '角色指令段';
COMMENT ON COLUMN ai_prompt_template.format_constraint IS '输出格式约束段';
COMMENT ON COLUMN ai_prompt_template.format_editable IS '格式约束段编辑开关（高级开关，默认关闭锁定）';
COMMENT ON COLUMN ai_prompt_template.updated_by IS '最后更新人，关联 sys_user.id';
COMMENT ON COLUMN ai_prompt_template.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_prompt_template.created_at IS '创建时间';
COMMENT ON COLUMN ai_prompt_template.updated_at IS '更新时间';

COMMENT ON TABLE ai_analysis_task IS 'AI 异步任务表（任务状态机与结果快照）';
COMMENT ON COLUMN ai_analysis_task.id IS '任务 ID';
COMMENT ON COLUMN ai_analysis_task.workspace_id IS '归属工作空间（embedding_rebuild 全局任务为空）';
COMMENT ON COLUMN ai_analysis_task.project_id IS '归属项目（embedding_rebuild 全局任务为空）';
COMMENT ON COLUMN ai_analysis_task.type IS '任务类型：review_check/review_summary/bug_clustering/embedding_rebuild/plan_order_recommend';
COMMENT ON COLUMN ai_analysis_task.target_id IS '目标对象 ID（评审 ID 等；聚类/回填以项目为目标时为空）';
COMMENT ON COLUMN ai_analysis_task.status IS '任务状态：pending/running/success/failed/cancelled';
COMMENT ON COLUMN ai_analysis_task.progress IS '进度百分比（0-100）';
COMMENT ON COLUMN ai_analysis_task.result IS '结果快照（结构由各任务类型在对应文档定义）';
COMMENT ON COLUMN ai_analysis_task.error_message IS '失败原因';
COMMENT ON COLUMN ai_analysis_task.executor_instance IS '执行实例标识（多实例防重复消费）';
COMMENT ON COLUMN ai_analysis_task.created_by IS '发起人，关联 sys_user.id';
COMMENT ON COLUMN ai_analysis_task.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_analysis_task.created_at IS '创建时间';
COMMENT ON COLUMN ai_analysis_task.updated_at IS '更新时间';

COMMENT ON TABLE ai_invocation_log IS 'AI 调用审计表（仅调用元数据，不存 Prompt 与生成内容）';
COMMENT ON COLUMN ai_invocation_log.id IS '主键';
COMMENT ON COLUMN ai_invocation_log.user_id IS '调用用户，关联 sys_user.id';
COMMENT ON COLUMN ai_invocation_log.workspace_id IS '工作空间（管理端调用为空）';
COMMENT ON COLUMN ai_invocation_log.project_id IS '项目（工作空间级功能为空）';
COMMENT ON COLUMN ai_invocation_log.function_type IS '功能类型枚举';
COMMENT ON COLUMN ai_invocation_log.model IS '实际调用的模型名';
COMMENT ON COLUMN ai_invocation_log.duration_ms IS '端到端耗时（毫秒）';
COMMENT ON COLUMN ai_invocation_log.prompt_tokens IS '输入 token（取上游 usage，缺失为空）';
COMMENT ON COLUMN ai_invocation_log.completion_tokens IS '输出 token';
COMMENT ON COLUMN ai_invocation_log.status IS '调用状态：success/failed/cancelled/rate_limited/schema_invalid';
COMMENT ON COLUMN ai_invocation_log.error_code IS '失败错误码或上游错误摘要';
COMMENT ON COLUMN ai_invocation_log.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_invocation_log.created_at IS '创建时间';
COMMENT ON COLUMN ai_invocation_log.updated_at IS '更新时间';

COMMENT ON TABLE ai_chat_model IS 'AI 对话模型配置表（多行：每行一个可用对话模型，全系统唯一默认）';
COMMENT ON COLUMN ai_chat_model.id IS '主键（业务请求中的模型标识 modelId）';
COMMENT ON COLUMN ai_chat_model.name IS '显示名（管理端与用户模型选择器展示，全局唯一）';
COMMENT ON COLUMN ai_chat_model.provider IS '供应商标识（预设注册表键，custom 为通用 OpenAI 兼容）';
COMMENT ON COLUMN ai_chat_model.base_url IS '服务地址（OpenAI 兼容根路径，不含 /chat/completions）';
COMMENT ON COLUMN ai_chat_model.api_key_cipher IS '服务密钥（AES-256-GCM 加密）';
COMMENT ON COLUMN ai_chat_model.key_suffix IS '密钥末 4 位（脱敏展示）';
COMMENT ON COLUMN ai_chat_model.model IS '模型名（请求体 model 字段值）';
COMMENT ON COLUMN ai_chat_model.extra_params IS '请求附加参数（厂商非标参数透传）';
COMMENT ON COLUMN ai_chat_model.enabled IS '启用状态（停用后不出现在用户模型清单）';
COMMENT ON COLUMN ai_chat_model.is_default IS '是否系统默认模型（全系统唯一）';
COMMENT ON COLUMN ai_chat_model.updated_by IS '最后更新人，关联 sys_user.id';
COMMENT ON COLUMN ai_chat_model.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_chat_model.created_at IS '创建时间';
COMMENT ON COLUMN ai_chat_model.updated_at IS '更新时间';

COMMENT ON TABLE requirement_pool_item IS '需求池条目表（项目级轻量需求条目库，US-AI-004）';
COMMENT ON COLUMN requirement_pool_item.id IS '条目 ID';
COMMENT ON COLUMN requirement_pool_item.project_id IS '归属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN requirement_pool_item.title IS '条目标题';
COMMENT ON COLUMN requirement_pool_item.content IS '需求文本（Markdown 原文，长度上限见 requirementContentMaxLength 配置键）';
COMMENT ON COLUMN requirement_pool_item.source_url IS '来源 URL（仅记录出处，平台不抓取）';
COMMENT ON COLUMN requirement_pool_item.ai_generated IS 'AI 拆分入库标识（US-AI-019，仅作展示标记，不影响业务规则）';
COMMENT ON COLUMN requirement_pool_item.created_by IS '创建人（编辑/删除权限判定依据），关联 sys_user.id';
COMMENT ON COLUMN requirement_pool_item.updated_by IS '最后更新人，关联 sys_user.id';
COMMENT ON COLUMN requirement_pool_item.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN requirement_pool_item.created_at IS '创建时间';
COMMENT ON COLUMN requirement_pool_item.updated_at IS '更新时间';

COMMENT ON TABLE requirement_document_rel IS '文档-需求关联表（脑图文档 ⇄ 需求池条目，US-AI-004）';
COMMENT ON COLUMN requirement_document_rel.id IS '主键';
COMMENT ON COLUMN requirement_document_rel.document_id IS '脑图文档 ID，关联 test_case_module.id（type=document）';
COMMENT ON COLUMN requirement_document_rel.requirement_id IS '需求池条目 ID，关联 requirement_pool_item.id';
COMMENT ON COLUMN requirement_document_rel.is_deleted IS '逻辑删除标志（解除关联）';
COMMENT ON COLUMN requirement_document_rel.created_at IS '创建时间';
COMMENT ON COLUMN requirement_document_rel.updated_at IS '更新时间';

-- ============================================================
-- 6. 向量表（《缺陷智能分析与向量检索详细设计说明书》2.1，随 embedding_rebuild 任务维护）
-- ============================================================

-- 缺陷向量表（与 bug 一对一，无物理外键；维度以默认 1024 建列，运行期与 ai_config.embedding_dimension 不一致时由 embedding_rebuild 任务 ALTER）
CREATE TABLE ai_bug_embedding (
    id          UUID          PRIMARY KEY,
    bug_id      UUID          NOT NULL,
    project_id  UUID          NOT NULL,
    embedding   vector(1024)  NOT NULL,
    source_hash VARCHAR(64)   NOT NULL,
    model       VARCHAR(100)  NOT NULL,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 部分唯一索引承担 UPSERT 冲突目标（ON CONFLICT 须显式携带相同 WHERE 谓词）
CREATE UNIQUE INDEX uk_ai_bug_embedding_bug_id ON ai_bug_embedding (bug_id) WHERE is_deleted = false;
CREATE INDEX idx_ai_bug_embedding_project_id ON ai_bug_embedding (project_id);
CREATE INDEX idx_ai_bug_embedding_hnsw ON ai_bug_embedding USING hnsw (embedding vector_cosine_ops);

-- 用例向量表（与 test_case_node 中 type=case 的节点一对一，冗余 project_id 供检索前置过滤）
CREATE TABLE ai_case_embedding (
    id          UUID          PRIMARY KEY,
    node_id     UUID          NOT NULL,
    project_id  UUID          NOT NULL,
    embedding   vector(1024)  NOT NULL,
    source_hash VARCHAR(64)   NOT NULL,
    model       VARCHAR(100)  NOT NULL,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ai_case_embedding_node_id ON ai_case_embedding (node_id) WHERE is_deleted = false;
CREATE INDEX idx_ai_case_embedding_project_id ON ai_case_embedding (project_id);
CREATE INDEX idx_ai_case_embedding_hnsw ON ai_case_embedding USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE ai_bug_embedding IS '缺陷向量表（缺陷语义索引，1:1，检索前置按 project_id 过滤）';
COMMENT ON COLUMN ai_bug_embedding.id IS '主键';
COMMENT ON COLUMN ai_bug_embedding.bug_id IS '对应缺陷 ID，关联 bug.id（逻辑外键，无物理外键）';
COMMENT ON COLUMN ai_bug_embedding.project_id IS '冗余项目归属，检索前置过滤防跨项目泄漏';
COMMENT ON COLUMN ai_bug_embedding.embedding IS '语义向量（维度随配置，默认 1024，重建任务按需 ALTER）';
COMMENT ON COLUMN ai_bug_embedding.source_hash IS '源文本 SHA-256（含模型名，判断过期）';
COMMENT ON COLUMN ai_bug_embedding.model IS '生成向量的模型名';
COMMENT ON COLUMN ai_bug_embedding.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_bug_embedding.created_at IS '创建时间';
COMMENT ON COLUMN ai_bug_embedding.updated_at IS '更新时间';

COMMENT ON TABLE ai_case_embedding IS '用例向量表（type=case 节点语义索引，1:1，检索前置按 project_id 过滤）';
COMMENT ON COLUMN ai_case_embedding.id IS '主键';
COMMENT ON COLUMN ai_case_embedding.node_id IS '对应用例节点 ID，关联 test_case_node.id（type=case，逻辑外键）';
COMMENT ON COLUMN ai_case_embedding.project_id IS '冗余项目归属（不冗余 document_id，见详细设计 2.1.2）';
COMMENT ON COLUMN ai_case_embedding.embedding IS '语义向量（维度随配置，默认 1024，重建任务按需 ALTER）';
COMMENT ON COLUMN ai_case_embedding.source_hash IS '源文本 SHA-256（含模型名，判断过期）';
COMMENT ON COLUMN ai_case_embedding.model IS '生成向量的模型名';
COMMENT ON COLUMN ai_case_embedding.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_case_embedding.created_at IS '创建时间';
COMMENT ON COLUMN ai_case_embedding.updated_at IS '更新时间';

-- ============================================================
-- 7. 全局智能助手（《全局智能助手详细设计说明书》2.1）
-- ============================================================

-- 助手会话表（归属用户+空间，内容仅本人可见）
CREATE TABLE ai_conversation (
    id             UUID          PRIMARY KEY,
    user_id        UUID          NOT NULL,
    workspace_id   UUID          NOT NULL,
    title          VARCHAR(100)  NOT NULL,
    last_active_at TIMESTAMP     NOT NULL,
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 键集分页命中列（2.1.1）：按 (user_id, workspace_id) 过滤后按 last_active_at DESC 排序，UUID v7 时序性保证 id 决胜稳定
CREATE INDEX idx_conv_user_ws ON ai_conversation (user_id, workspace_id, last_active_at DESC);

-- 助手消息表（tool_calls 为 assistant 消息发起的工具调用载荷数组，tool 消息回填 tool_call_id 保持序列完整）
CREATE TABLE ai_message (
    id              UUID         PRIMARY KEY,
    conversation_id UUID         NOT NULL,
    role            VARCHAR(10)  NOT NULL,
    content         TEXT         NULL,
    tool_calls      JSONB        NULL,
    tool_call_id    VARCHAR(64)  NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_msg_conversation_id ON ai_message (conversation_id);

COMMENT ON TABLE ai_conversation IS '助手会话表（归属用户+工作空间，仅本人可见）';
COMMENT ON COLUMN ai_conversation.id IS '会话 ID';
COMMENT ON COLUMN ai_conversation.user_id IS '归属用户，关联 sys_user.id（逻辑外键）';
COMMENT ON COLUMN ai_conversation.workspace_id IS '归属工作空间（跨空间隔离），关联 ws_workspace.id（逻辑外键）';
COMMENT ON COLUMN ai_conversation.title IS '会话标题（首条用户消息前 30 字自动生成）';
COMMENT ON COLUMN ai_conversation.last_active_at IS '最后活跃时间（列表排序锚点）';
COMMENT ON COLUMN ai_conversation.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_conversation.created_at IS '创建时间';
COMMENT ON COLUMN ai_conversation.updated_at IS '更新时间';

COMMENT ON TABLE ai_message IS '助手消息表（对话内容，仅会话归属者可见）';
COMMENT ON COLUMN ai_message.id IS '消息 ID';
COMMENT ON COLUMN ai_message.conversation_id IS '所属会话，关联 ai_conversation.id（逻辑外键）';
COMMENT ON COLUMN ai_message.role IS '消息角色：user/assistant/tool';
COMMENT ON COLUMN ai_message.content IS '文本内容（tool 消息为工具执行结果 JSON 文本）';
COMMENT ON COLUMN ai_message.tool_calls IS 'assistant 消息发起的工具调用载荷（name/arguments/callId 数组）';
COMMENT ON COLUMN ai_message.tool_call_id IS 'tool 消息对应的调用 ID';
COMMENT ON COLUMN ai_message.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_message.created_at IS '创建时间';
COMMENT ON COLUMN ai_message.updated_at IS '更新时间';
