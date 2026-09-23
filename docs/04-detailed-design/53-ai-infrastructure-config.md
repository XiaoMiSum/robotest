# 软件测试平台——（分册：AI 配置）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.3 AI 配置接口（管理端）

#### 3.3.1 获取 AI 配置

- **路径**：`GET /api/admin/ai/config`
- **响应**（对话模型配置不在本接口，见 3.3.7）：

```json
{
  "enabled": true,
  "embedding": {
    "provider": "zhipu",
    "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
    "model": "embedding-3",
    "dimension": 1024,
    "apiKey": { "configured": true, "keySuffix": "c91d" },
    "extraParams": {}
  },
  "settings": { "rateLimit.generation": 20, "logRetentionDays": 180 }
}
```

未配置时 `data` 为 `null`。`settings` 返回**合并后的完整键值视图**（内置默认值 + 落库覆盖值，见 2.2），供表单直接回显；落库仅存与默认值不同的覆盖键。

#### 3.3.2 保存 AI 配置

- **路径**：`PUT /api/admin/ai/config`
- **请求体**：结构同 3.3.1 响应，其中 `apiKey` 字段为字符串：非空即更新（加密后落库），`null` 或缺省表示保持原值。
- **校验**：
  - `embedding.provider` 组内填写时须为 2.5 注册表有效键（scopes 含 embedding），无效返回 1001；
  - `embedding.*` 整组可空，但组内一旦填写则供应商/地址/模型/维度/密钥必须齐全；
  - `embedding.dimension` ∈ [1, 2000]，超限返回 6008（HNSW 索引上限约束）；
  - `extraParams` 必须为 JSON 对象，且不得覆盖标准参数白名单键（见 4.2），违规返回 1001；`provider ≠ custom` 时命中供应商独有配置项模板键的值须通过模板类型/枚举校验（2.5），违规返回 1001；
  - `enabled = true` 保存时须存在至少一个已启用的对话模型（3.3.7），否则返回 1001；
  - `settings` 提交完整键值集（表单收集）：逐键按 3.3.8 定义校验类型与取值范围（未知键、类型不符或越界返回 1001，`planOrder.weights` 三权重之和须为 1）；后端仅持久化与内置默认值不同的键，其余键不落库（保持缺省回退默认值语义，2.2）；
  - Embedding 模型或维度发生变更时，保存成功后自动创建 `embedding_rebuild` 任务并进入语义降级（见 4.10，重建任务本体设计见《缺陷智能分析与向量检索详细设计说明书》）。仅变更 `provider` 标识或独有配置项不触发重建（向量空间由模型与维度决定）。
- **并发**：`ai_config` 为系统级单行表，保存按 id 全列覆盖更新，后写覆盖先写（不校验 `updated_at`，无并发冲突拒绝语义）。
- **响应**：保存后的配置（脱敏格式）。变更写入 sys_audit_log。

#### 3.3.3 连通性测试

- **路径**：`POST /api/admin/ai/config/test`
- **请求体**：`{ "target": "chat", "modelId": "018f...", "chat": { ... } }` 或 `{ "target": "embedding", "embedding": { ... } }`。`chat` / `embedding` 为未保存的临时配置（结构同对应保存请求）；`target = chat` 时临时配置优先，缺省则按 `modelId` 取已保存的对话模型配置测试（临时配置的密钥缺省回退该模型已存密文）；`target = embedding` 时缺省用已保存 Embedding 配置。
- **处理**：
  - `chat`：发送单条固定消息的最小对话请求（`max_tokens: 16`），验证连通性与鉴权；顺带以 `response_format: {"type":"json_object"}` 探测结构化参数支持情况，结果仅作提示不阻断保存；
  - `embedding`：对固定文本发起向量化，校验返回向量长度 === 配置维度，不一致返回 6008。
- **响应**：`{ "ok": true, "latencyMs": 832, "detail": "..." }`；失败返回 6007 及上游错误摘要。

#### 3.3.4 调用量统计

- **路径**：`GET /api/admin/ai/statistics`
- **参数**：`startDate`、`endDate`（默认最近 30 天）、`groupBy`（`functionType` / `workspace` / `day` / `model` / `user`）
- **响应**：

```json
{
  "totalCalls": 1284,
  "totalTokens": 5230400,
  "failedCalls": 23,
  "items": [
    { "key": "case_generation", "calls": 412, "tokens": 2100300, "avgDurationMs": 4200, "failed": 8 }
  ]
}
```

数据来源为 `ai_invocation_log` 聚合查询（`groupBy=workspace` 时 key 为工作空间名称；`groupBy=model` 时 key 为审计记录的实际模型名；`groupBy=user` 时 key 为用户显示名，缺失回退登录名；`groupBy=functionType` 时 key 转换为 `AiFunctionType` 枚举中文名，避免暴露内部 code）。

#### 3.3.5 向量重建任务查询与重试

`embedding_rebuild` 为**全局任务**（`workspace_id` / `project_id` 为空），不适用 3.5 项目级通用任务接口，由本组管理端接口承接：

- **查询**：`GET /api/admin/ai/rebuild-task` — 返回最近一次 `embedding_rebuild` 任务（字段结构同 3.5.1），从未创建过返回 `null`；管理端配置页据此展示进度条与失败原因；
- **重试**：`POST /api/admin/ai/rebuild-task/retry` — 仅最近一次任务为 `failed` / `cancelled` 时可重试（重置为 pending 重新入队，同 3.5.3 处理；`cancelled` 的 rebuild 同样意味着向量数据不完整，须提供恢复入口），**任意系统管理员可操作**（不限发起人，该任务由系统自动创建）；其余状态返回 6006。

#### 3.3.6 供应商预设查询

- **路径**：`GET /api/admin/ai/providers`
- **说明**：返回 2.5 注册表全量元数据，供配置页渲染供应商下拉、默认地址填充与独有配置项动态表单；纯代码内置数据，无落库。
- **响应**：

```json
[
  {
    "key": "qwen",
    "name": "阿里云百炼（通义千问）",
    "scopes": ["chat", "embedding"],
    "defaultBaseUrl": {
      "chat": "https://dashscope.aliyuncs.com/compatible-mode/v1",
      "embedding": "https://dashscope.aliyuncs.com/compatible-mode/v1"
    },
    "modelHints": { "chat": ["qwen-plus", "qwen-max"], "embedding": ["text-embedding-v4"] },
    "uniqueParams": {
      "chat": [
        {
          "key": "enable_thinking",
          "type": "boolean",
          "defaultValue": false,
          "label": "思考模式",
          "description": "开启后模型输出思考过程（响应侧自动剥离，见 4.2.2）"
        }
      ],
      "embedding": []
    }
  }
]
```

#### 3.3.7 对话模型管理

对话模型为多行配置（2.1.5），提供独立管理接口，均要求 `ai:edit`（查询仅需 `ai:view`）。全部变更写入 sys_audit_log。

- **列表**：`GET /api/admin/ai/chat-models` — 返回全部对话模型（脱敏），响应示例：

```json
[
  {
    "id": "018f...",
    "name": "DeepSeek-V3",
    "provider": "deepseek",
    "baseUrl": "https://api.deepseek.com/v1",
    "model": "deepseek-chat",
    "apiKey": { "configured": true, "keySuffix": "8f2a" },
    "extraParams": {},
    "enabled": true,
    "isDefault": true,
    "updatedBy": "张三",
    "updatedAt": "2026-07-31T10:00:00Z"
  }
]
```

- **新建**：`POST /api/admin/ai/chat-models` — 请求体含 `name` / `provider` / `baseUrl` / `model` / `apiKey`（必填）/ `extraParams`；首个创建的模型自动置为默认。
- **更新**：`PUT /api/admin/ai/chat-models/:id` — 结构同新建，`apiKey` 非空即更新、缺省保持原值；按 id 全列覆盖更新，后写覆盖先写（不校验 `updated_at`，无并发冲突拒绝语义）。
- **删除**：`DELETE /api/admin/ai/chat-models/:id` — 逻辑删除；默认模型不可删除（返回 1001，需先转移默认）。
- **设为默认**：`PUT /api/admin/ai/chat-models/:id/default` — 事务内先清除原默认再置新默认（唯一默认保证，见 4.11）；停用状态的模型不可设为默认（返回 1001）。
- **启用/停用**：`PUT /api/admin/ai/chat-models/:id/enabled` — 请求体 `{ "enabled": false }`；默认模型不可停用（返回 1001）。

**校验**（新建/更新共用）：`name` 全局唯一（冲突返回 1001）；`provider` 为 2.5 注册表有效键（scopes 含 chat）；`baseUrl` / `model` 必填；`extraParams` 白名单与独有配置项校验同 3.3.2。

#### 3.3.8 系统配置项定义查询

- **路径**：`GET /api/admin/ai/settings-schema`
- **说明**：返回 2.2 全量配置项的表单定义清单（代码内置元数据，无落库），供配置页渲染分组表单；键清单随功能演进由代码更新，前端不硬编码。
- **响应**：

```json
[
  {
    "group": "rateLimit",
    "groupLabel": "限流阈值",
    "items": [
      {
        "key": "rateLimit.generation",
        "type": "int",
        "label": "生成类调用上限",
        "description": "生成类每用户每小时调用上限",
        "defaultValue": 20,
        "min": 1,
        "max": null
      }
    ]
  }
]
```

- `type`：`int` / `number` / `object`（planOrder.weights，前端拆为固定子键数字输入）/ `string[]`（多选，选项随定义下发 `options` 字段）；
- `min` / `max`：取值范围（见 2.2 分组与控件映射表），空表示不限。


