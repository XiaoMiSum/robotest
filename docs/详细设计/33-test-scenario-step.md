# 软件测试平台——（分册：步骤与验证器提取器）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `31-test-scenario-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.3 步骤管理

#### 3.3.1 创建步骤

- **路径**：`POST /api/project/scenes/:sceneId/steps`
- **请求体**：

```json
{
  "name": "发送登录请求",
  "stepType": "http",
  "sortOrder": 0,
  "enabled": true,
  "sourceType": "custom",
  "sourceId": null,
  "requestConfig": {
    "method": "POST",
    "url": "/api/auth/login",
    "headers": [
      { "id": "018i...", "key": "Content-Type", "value": "application/json", "enabled": true }
    ],
    "params": [],
    "body": {
      "type": "json",
      "content": { "username": "${username}", "password": "${password}" }
    }
  },
  "processors": [],
  "validators": [
    { "id": "018g...", "name": "验证返回码", "enabled": true, "target": "status_code", "condition": "equals", "expected": "200" }
  ],
  "extractors": []
}
```

- **响应**：`{ "id": "018f..." }`

#### 3.3.2 通过接口快速创建步骤

- **路径**：`POST /api/project/scenes/:sceneId/steps/quick-create`
- **说明**：从一个接口快速创建场景步骤。自动引入接口定义本身的请求配置。引入的步骤在画布中以卡片展示，卡片通过 tag 标记来源接口名称。

- **请求体**：

```json
{
  "interfaceId": "018f...",
  "mode": "copy"
}
```

- `interfaceId`：来源接口定义 ID。
- `mode`：`copy`（快照，独立副本，默认）/ `link`（链接，跟随源变更）。对应生成步骤的 `source_type`。

- **创建逻辑**：

  1. 从接口定义生成一个步骤：
     - `name` = 接口名称（如「用户登录」）
     - `source_type` = `copy` 或 `link`（取 `mode`）
     - `source_id` = 接口定义 ID
     - `request_config` = 接口定义的 `method`/`path`/`headers`/`body`/`query_params` 快照
     - `source_interface_id` = 接口定义 ID（用于展示来源 tag）
     - `source_interface_name` = 接口名称（冗余存储，避免查询时 JOIN）

- **响应**：

```json
{
  "steps": [
    { "id": "018a...", "name": "用户登录", "sourceType": "copy", "sourceInterfaceName": "用户接口" }
  ]
}
```

#### 3.3.3 更新步骤

- **路径**：`PUT /api/project/scenes/:sceneId/steps/:stepId`
- **请求体**：同 3.3.1。

#### 3.3.4 删除步骤

- **路径**：`DELETE /api/project/scenes/:sceneId/steps/:stepId`

#### 3.3.5 步骤排序

- **路径**：`PUT /api/project/scenes/:sceneId/steps/reorder`
- **请求体**：

```json
{
  "stepIds": ["018f...", "018g...", "018h..."]
}
```

- **说明**：数组顺序即为新的排序。


### 3.10 步骤复制

- **路径**：`POST /api/project/scenes/:sceneId/steps/:stepId/copy`
- **请求体**：`{ "name": "发送登录请求（副本）" }`（可选，缺省「原名称（副本）」）
- **说明**：复制步骤为完全独立的副本：`source_type = copy`、重新生成 id 及步骤内 processors/validators/extractors 的 id，与原步骤无关联（对应交互设计「复制步骤」）。
- **响应**：`{ "id": "018a...", "name": "发送登录请求（副本）", "sortOrder": 2 }`


### 3.12 从全局资产引入

场景级处理器、步骤级验证器/提取器均可从全局资产库以**复制**方式引入（需求 3.8/3.9，语义同《API 测试基础设施详细设计说明书》3.6 全局资产复制）。

- **路径**：`POST /api/project/scenes/:sceneId/assets/import`
- **请求体**：

```json
{
  "target": "step_validator",
  "stepId": "018a...",
  "assetIds": ["018f...", "018g..."]
}
```

- `target`：`scene_processor`（场景级前置/后置处理器）/ `step_validator`（步骤级验证器）/ `step_extractor`（步骤级提取器）。
- `stepId`：`step_validator` / `step_extractor` 时必填；`scene_processor` 时省略。
- **说明**：引入为复制，产生独立副本，与源资产无关联；资产不存在或已停用返回 7014 / 7015。
- **响应**：`{ "imported": 2 }`

---


### 4.2 验证器配置模型

验证器采用**平台自有数据结构**，遵循三大原则：

1. **自然语言化**：使用用户理解的术语（如「返回码」「等于」），隐藏 Ryze 内部概念（testclass/rule）
2. **最小化暴露**：仅暴露用户必须配置的字段，其余由平台推断
3. **启用禁用**：每个验证器可独立启用/禁用，禁用时不可被导入但保留配置

**平台数据结构**（存储于 `validators` JSONB）：

```json
{
  "id": "uuid",
  "name": "验证描述（如：验证登录成功）",
  "enabled": true,
  "target": "status_code|json_field|response_header|response_body|regex|xpath|groovy",
  "condition": "equals|not_equals|greater_than|less_than|greater_or_equal|less_or_equal|contains|not_contains|starts_with|ends_with|matches_regex",
  "expected": "<期望值（仅部分 target 需要）>",
  "expression": "<表达式（仅部分 target 需要）>"
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | 是 | 主键，应用层生成 |
| name | string | 否 | 用户自定义验证器描述，用于报告展示；为空时由平台根据配置自动生成 |
| enabled | boolean | 是 | 启用/禁用。禁用时：①配置仍保留但不参与执行 ②不可被其他步骤导入 |
| target | enum | 是 | 验证目标，用户选择「验证什么」 |
| condition | enum | 是 | 比较条件，用户选择「怎么比较」 |
| expected | string | 视 target | 期望值 |
| expression | string | 视 target | 表达式路径 |

**target 与 condition 的联动关系**：

| target | 用户看到的标签 | condition 可选项 | expected 是否必填 | expression 是否必填 | expression 说明 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| status_code | 返回码 | equals / not_equals / greater_than / less_than | 是 | 否 | — |
| json_field | JSON 字段 | equals / not_equals / contains / matches_regex | 是 | 是 | JSONPath，如 `$.code` |
| response_header | 响应头 | equals / not_equals | 是 | 是 | 头名，如 `Content-Type` |
| response_body | 响应体 | contains / not_contains / starts_with / ends_with | 是 | 否 | — |
| regex | 正则匹配 | matches_regex | 否 | 是 | 正则表达式 |
| xpath | XPath | equals / not_equals | 是 | 是 | XPath 表达式 |
| groovy | Groovy 脚本 | — | 否 | 是 | Groovy 脚本内容 |

**UI 交互示例**：

```
┌─────────────────────────────────────────────────────────────┐
│ 验证器配置                                                    │
├─────────────────────────────────────────────────────────────┤
│ 验证目标：[返回码        ▼]  比较条件：[等于 ▼]  期望值：[200    ]  │
│ 验证器描述：[验证返回码为200                                     ]  │
│                                                             │
│ [+ 添加验证器]                                               │
└─────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────┐
│ 验证器配置                                                    │
├─────────────────────────────────────────────────────────────┤
│ 验证目标：[JSON 字段      ▼]  表达式：[$.code               ] │
│           比较条件：[等于 ▼]  期望值：[200    ]               │
│ 验证器描述：[验证业务码为200                                     ]  │
│                                                             │
│ [+ 添加验证器]                                               │
└─────────────────────────────────────────────────────────────┘
```

**平台 → Ryze 转换规则**（执行引擎层）：

| 平台 target | → Ryze testclass | field 推断逻辑 | condition → rule |
| ----------- | ----------------- | -------------- | ---------------- |
| status_code | `http_assertion` | 固定 `response.status` | equals→eq, not_equals→ne, greater_than→gt, less_than→lt |
| json_field | `json` | expression 直传 | equals→eq, not_equals→ne, contains→contains, matches_regex→matches |
| response_header | `http_assertion` | `headers.${expression}` | equals→eq, not_equals→ne |
| response_body | `json` | 固定 `$.response.body` | contains→contains, not_contains→not_contains |
| regex | `json` | expression 直传 | 固定 matches |
| xpath | `xpath` | expression 直传 | equals→eq, not_equals→ne |
| groovy | `groovy` | expression 直传（作为 script） | — |

**示例**：

```json
{ "id": "018g...", "name": "验证返回码为200", "enabled": true, "target": "status_code", "condition": "equals", "expected": "200" }
{ "id": "018g...", "name": "验证业务码成功", "enabled": true, "target": "json_field", "condition": "equals", "expected": "200", "expression": "$.code" }
{ "id": "018g...", "name": "验证返回消息包含成功", "enabled": true, "target": "json_field", "condition": "contains", "expected": "成功", "expression": "$.message" }
{ "id": "018g...", "enabled": false, "target": "status_code", "condition": "equals", "expected": "200" }
```


### 4.3 提取器配置模型

提取器采用与验证器一致的平台设计原则：自然语言化、最小化暴露、启用禁用。

**平台数据结构**（存储于 `extractors` JSONB）：

```json
{
  "id": "uuid",
  "name": "提取描述（如：提取登录 token）",
  "enabled": true,
  "source": "json_field|response_header|xpath|regex|boundary|full_body|groovy",
  "expression": "<表达式>",
  "variableName": "<目标变量名>"
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | 是 | 主键 |
| name | string | 否 | 用户自定义描述，用于报告展示 |
| enabled | boolean | 是 | 启用/禁用，语义同验证器 |
| source | enum | 是 | 提取来源，用户选择「从哪里提取」 |
| expression | string | 视 source | 提取表达式 |
| variableName | string | 是 | 提取结果存入的变量名 |

**source 与 expression 的联动关系**：

| source | 用户看到的标签 | expression 是否必填 | expression 说明 | UI 控件 |
| ---- | ---- | ---- | ---- | ---- |
| json_field | JSON 字段 | 是 | JSONPath，如 `$.data.token` | 输入 JSONPath |
| response_header | 响应头 | 是 | 头名，如 `Set-Cookie` | 输入头名 |
| xpath | XPath | 是 | XPath 表达式 | 输入 XPath |
| regex | 正则捕获 | 是 | 正则表达式（含捕获组） | 输入正则 |
| boundary | 两个标记之间 | 是 | `左边界\|\|右边界` | 两个输入框 |
| full_body | 完整响应体 | 否 | — | 无需输入 |
| groovy | Groovy 脚本 | 是 | 脚本内容 | 代码编辑器 |

**平台 → Ryze 转换规则**（执行引擎层）：

| 平台 source | → Ryze testclass | field 推断逻辑 | ref_name |
| ----------- | ----------------- | -------------- | -------- |
| json_field | `json` | expression 直传 | variableName 直传 |
| response_header | `http_header` | expression 直传（头名，如 `Set-Cookie`） | variableName 直传 |
| xpath | `xpath` | expression 直传 | variableName 直传 |
| regex | `regex` | expression 直传 | variableName 直传 |
| boundary | `boundary` | expression 直传 | variableName 直传 |
| full_body | `plaintext` | 固定 `$.response.body` | variableName 直传 |
| groovy | `groovy` | expression 直传（作为 script） | variableName 直传 |

**示例**：

```json
{ "id": "018h...", "name": "提取登录 token", "enabled": true, "source": "json_field", "expression": "$.data.token", "variableName": "token" }
{ "id": "018h...", "name": "提取用户ID", "enabled": true, "source": "regex", "expression": "\"id\":(\\d+)", "variableName": "userId" }
{ "id": "018h...", "name": "完整响应体", "enabled": true, "source": "full_body", "expression": "", "variableName": "fullResponse" }
{ "id": "018h...", "enabled": false, "source": "json_field", "expression": "$.data.id", "variableName": "tempId" }
```


### 4.4 请求配置（request_config）平台数据结构

请求配置同样遵循平台设计原则，将 HTTP 请求的各组成部分以用户友好的方式组织。

**平台数据结构**（存储于 `request_config` JSONB）：

```json
{
  "method": "POST",
  "url": "/api/auth/login",
  "headers": [
    { "id": "uuid", "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "params": [
    { "id": "uuid", "key": "page", "value": "1", "enabled": true }
  ],
  "body": {
    "type": "none|json|form|raw|binary",
    "content": "..."
  },
  "timeout": 30000
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| base_url | string | 否 | Base URL。允许为空，为空时继承环境 HTTP 配置的 base_url |
| method | enum | 否 | 请求方法：GET / POST / PUT / PATCH / DELETE。为空时缺省 GET |
| url | string | 是 | 请求路径（支持 `${变量名}` 引用）。与 base_url 拼接形成完整 URL |
| headers | array | 否 | 请求头列表（见下方）。与环境 HTTP 配置的默认请求头合并，步骤级优先覆盖 |
| params | array | 否 | 查询参数列表（见下方） |
| body | object | 视 method | 请求体（见下方） |
| timeout | int | 否 | 超时时间（毫秒）。为空时继承环境 HTTP 配置 |

> **配置继承原则**：步骤无需重复配置环境中已有的值。配置了也没关系——Ryze 以最低层级优先（步骤级 > 环境级）。

#### 4.4.1 请求头（headers）

**数据结构**：

```json
[
  {
    "id": "018i...",
    "key": "Content-Type",
    "value": "application/json",
    "description": "请求体格式",
    "enabled": true
  }
]
```

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | 是 | 主键 |
| key | string | 是 | 请求头名称（如 `Content-Type`、`Authorization`） |
| value | string | 是 | 请求头值（支持 `${变量名}` 引用） |
| description | string | 否 | 描述信息 |
| enabled | boolean | 是 | 启用/禁用。禁用时该请求头不参与请求发送 |

**UI 交互**：

```
┌─ 请求头 ──────────────────────────────────────────────────┐
│ ✓  Content-Type    [application/json          ]  [×]      │
│ ✓  Authorization   [Bearer ${token}           ]  [×]      │
│ ☐  X-Custom-Header [disabled_value            ]  [×]      │
│ [+ 添加请求头]                                              │
└──────────────────────────────────────────────────────────┘
```

**平台 → Ryze 转换规则**：将数组转换为 Map `{key: value}`，仅包含 `enabled=true` 的条目。

#### 4.4.2 查询参数（params）

**数据结构**：与请求头结构一致（`id`/`key`/`value`/`description`/`enabled`）。

**平台 → Ryze 转换规则**：将数组转换为 Map `{key: value}`，仅包含 `enabled=true` 的条目，映射到 Ryze 的 `query` 字段。

#### 4.4.3 请求体（body）

**数据结构**：

```json
{
  "type": "json",
  "content": { "username": "${username}", "password": "${password}" }
}
```

| type | content 类型 | content 说明 | UI 控件 |
| ---- | ---- | ---- | ---- |
| none | — | — | 无 |
| json | object/array/string | JSON 内容（支持 `${变量名}` 引用） | JSON 编辑器（带语法高亮） |
| form | array | `[{ "key": "field", "value": "val", "enabled": true, "type": "text" }]`，type 支持 text/file | KV 表格（支持文件上传） |
| raw | string | 原始文本内容 | 文本编辑器 |
| binary | string | 文件路径或 base64 | 文件选择器 |

**平台 → Ryze 转换规则**：`type` 映射为 Ryze 的 `body_type`，`content` 直传为 Ryze 的 `body`。

#### 4.4.4 完整示例

```json
{
  "method": "POST",
  "url": "/api/auth/login",
  "headers": [
    { "id": "018i...", "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "params": [],
  "body": {
    "type": "json",
    "content": { "username": "${username}", "password": "${password}" }
  },
  "timeout": 30000
}
```


### 5.1 场景编排器

核心交互组件，采用**左右分栏**布局（仿 Metersphere 场景编排页），移除 Card 头部，左右两栏间为可拖拽分割条：

- **左侧分栏 Tabs**：步骤编排 / 场景变量 / 前置处理器 / 后置处理器 / 执行历史。
- **右侧基础信息面板**：场景名称、所属模块、默认环境、描述（编辑态追加创建/更新元信息只读展示）；分割条可拖拽调整两栏宽度。

以可视化步骤画布为中心：

- **步骤画布**：垂直排列的步骤卡片列表，支持拖拽排序。每个步骤卡片包含：类型标识（http/jdbc）、名称、启用开关、展开/折叠。通过接口快速创建的步骤，卡片右上角显示来源接口名称 tag（如「用户接口」），hover 时显示来源类型（copy/link）。
- **步骤导入选择器**：点击「添加步骤」弹出选择器，支持三种来源：
  - **通过接口快速创建**（推荐）：从项目接口列表中选择，一键引入接口定义本身。弹出模式选择（copy/link，默认 copy），确认后创建步骤。
  - 系统请求：从接口定义列表选择，仅导入接口定义本身为一个步骤。
  - 自定义请求：直接在步骤卡片内编辑请求。
- **步骤级变量编辑器**：步骤卡片展开后的变量表格编辑器（变量名、值、来源、描述），支持手动编辑。
- **变量编辑器**：场景变量的表格编辑器（变量名、值、描述）。
- **前置/后置处理器标签**：两个独立标签页，分别编辑场景级前置/后置处理器列表（同一 `processors` 列表按元素 `type` 为 `pre`/`post` 拆分展示）。

---


