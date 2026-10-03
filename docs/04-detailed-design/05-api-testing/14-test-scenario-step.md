# 软件测试平台——步骤与验证器提取器

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

> 本篇聚焦测试场景的步骤数据与编辑行为：步骤管理接口、步骤复制、公共组件引入、验证器/提取器/请求配置三类平台数据结构，以及承载这些能力的场景编排器。执行与单步调试、变量体系分别见 `docs/04-detailed-design/05-api-testing/16-test-scenario-execution.md`、`docs/04-detailed-design/05-api-testing/15-test-scenario-variable.md`。

## 1. 步骤管理

场景步骤接口统一以 `/api/project/api-scenes/:sceneId` 为前缀；上下文标识（workspace / project）经请求头传递，不出现在 URL 中，URL 仅携带资源自身 ID。响应示例仅展示 `data` 字段内容。

### 1.1 创建步骤

- **路径**：`POST /api/project/api-scenes/:sceneId/steps`
- **请求体**：

```json
{
  "name": "发送登录请求",
  "stepType": "http",
  "sortOrder": 1,
  "enabled": true,
  "sourceType": "custom",
  "sourceId": null,
  "requestConfig": {
    "method": "POST",
    "url": "/api/auth/login",
    "headers": [
      { "key": "Content-Type", "value": "application/json", "enabled": true }
    ],
    "params": [],
    "body": {
      "type": "json",
      "content": { "username": "${username}", "password": "${password}" }
    }
  },
  "processors": [],
  "validators": [
    { "id": "018g...", "name": "验证返回码", "enabled": true, "target": "status_code", "condition": "equals", "expected": "200", "expression": "" }
  ],
  "extractors": []
}
```

- **字段说明**：`name` 必填；`stepType` 缺省 `http`，仅接受 `http`（执行引擎 V1.2 只覆盖 HTTP 取样器）；`sortOrder` 缺省为当前最大排序号 + 1；`enabled` 缺省 `true`；`sourceType` 取值为 `custom`（后端缺省，手动步骤）、`manual`（前端手动新建）、`copy`（接口快照副本）或 `link`（链接源接口）；`requestConfig`、`processors`、`validators`、`extractors` 结构见第 4～6 节。
- **响应**：`{ "id": "018f..." }`
- **说明**：编辑页不调用本接口——新增步骤先在前端内存中维护，随场景保存聚合落库（见 1.6）。

### 1.2 从接口添加步骤

入口为步骤列表头部的「从接口添加」，弹出「从接口添加步骤」对话框（`InterfacePickerDialog.vue`）：远程搜索接口（每页 50 条，选项显示「方法 路径 - 名称」），选中后点「导入」拉取接口详情，**由前端本地构造步骤**，不请求后端步骤接口：

- 新步骤 `id` 为 `new-` 前缀临时 id，`stepType = http`、`enabled = true`、`sourceType = copy`；
- `sourceInterfaceId` / `sourceInterfaceName` 记录来源接口，卡片据此显示来源 tag（保存时提交的步骤字段不含来源字段，来源 tag 仅在当前编辑会话内展示）；
- `requestConfig` 取接口的 `method`、`path`（写入 `url`）、`headers`、`params`、`body` 快照，并置 `conditionExpression` 为空串（执行条件表达式占位，当前无编辑入口）；
- 接口定义的验证器 / 提取器补齐 `id`（UUID）、`name`、`enabled: true` 后并入；`processors` 置空（接口定义不携带处理器）；
- 步骤追加到列表末尾（`sortOrder` = 列表长度 + 1）并选中，右侧内联编辑器回填，随场景保存聚合落库。

对话框状态分支：未选接口点「导入」提示「请选择接口」；列表加载失败显示错误条并可「重试」；导入过程中按钮 loading。

后端另提供单步快速创建接口（当前编辑页未接入）：

- **路径**：`POST /api/project/api-scenes/:sceneId/steps/quick-create`
- **请求体**：

```json
{
  "interfaceId": "018f...",
  "mode": "copy",
  "importInterfaceVariables": false
}
```

- `interfaceId`：来源接口 ID（须属于当前项目，否则返回接口不存在错误）。
- `mode`：`copy`（快照，缺省）/ `link`（跟随源变更），归一化后写入步骤 `sourceType`。
- `importInterfaceVariables`：是否一并引入接口变量（布尔，可选）。
- **创建逻辑**：由接口定义生成一个步骤——`name` = 接口名称；`sourceId` = `sourceInterfaceId` = 接口 ID；`sourceInterfaceName` = 接口名称（冗余存储，避免查询时 JOIN）；`requestConfig` 快照 `method` / `url`（取接口 `path`）/ `headers` / `params` / `body`（`type` 取接口 bodyType）；`stepType = http`；`sortOrder` 追加到末尾；验证器 / 提取器取接口定义。
- **响应**：

```json
{
  "steps": [
    { "id": "018a...", "name": "用户登录", "sourceType": "copy", "sourceInterfaceName": "用户接口" }
  ]
}
```

### 1.3 更新步骤

- **路径**：`PUT /api/project/api-scenes/:sceneId/steps/:stepId`
- **请求体**：同 1.1。语义为部分更新：仅覆盖请求体中实际传入的字段；`stepType` 传入时校验取值。
- **编辑页用途**：切换步骤卡片的启用开关时立即调用，携带 `name` / `stepType` / `enabled` / `requestConfig` / `processors` / `validators` / `extractors` / `sourceType` / `sourceId`，成功后本地翻转启用态，失败提示错误信息；其余字段编辑随场景保存聚合落库（见 1.6）。

### 1.4 删除步骤

- **路径**：`DELETE /api/project/api-scenes/:sceneId/steps/:stepId`
- **编辑态**：先弹确认框「删除步骤「××」？」，确认后调用本接口，成功提示「步骤已删除」、清空当前选中并重新拉取场景详情；`new-` 临时步骤确认后仅本地移除，不调接口。
- **创建态**：本地直接移除，不弹确认、不调接口。

### 1.5 步骤排序

- **路径**：`PUT /api/project/api-scenes/:sceneId/steps/reorder`
- **请求体**：

```json
{
  "stepIds": ["018f...", "018g...", "018h..."]
}
```

- **说明**：数组顺序即为新的排序，后端校验 id 归属后按下标（0 起）重排 `sortOrder`。
- **编辑页触发**：卡片拖拽（卡片间插入区 / 卡片本体 / 末尾插入区）与「操作」菜单的「上移」「下移」（首项上移、末项下移禁用）；编辑态调用本接口并回写本地顺序，失败提示「排序失败」；创建态仅本地重排。

### 1.6 随场景保存聚合落库

编辑页新增与修改步骤的主要持久化路径是场景保存接口（`PUT /api/project/api-scenes/:sceneId`）的 `steps` 数组：

- 提交前按 `sortOrder` 升序排列，`new-` 临时 id 提交时置空（不传 `id`）；
- 后端合并规则：**含 `id`** 的步骤按 id 定位做局部更新（仅覆盖传入字段），**无 `id`** 的步骤新建并按传入顺序赋 `sortOrder`；`steps` 为空数组时不改动既有步骤——删除步骤必须走 1.4 的删除接口；
- 创建态走 `POST /api/project/api-scenes` 一次性创建，步骤按传入顺序自 1 起赋 `sortOrder`；
- 前端「编辑即生效」：内联编辑器任一字段变更立即写回内存中的步骤对象，实际落库由顶部「保存为草稿」「发布」触发（运行场景若存在未保存改动则先保存再执行），请求携带 `changeVersion` 乐观锁；
- 场景创建与保存接口同样校验步骤类型仅 `http`。

## 2. 步骤复制

- **入口**：步骤卡片「操作」下拉 →「复制」。
- **说明**：复制为前端行为，不提供专用后端接口——将源步骤深拷贝为独立副本：重新生成步骤 id（`new-` 临时 id）、`sourceType = copy`，插入**源步骤之后**并重排 `sortOrder`（自 1 起）；`processors` / `validators` / `extractors` 随深拷贝复制，但保留原条目 id，与原步骤无关联。副本随即选中，右侧内联编辑器回填副本配置，未落库，随场景保存聚合落库（见 1.6）。创建态同样只在本地复制，不触发接口。

## 3. 从公共组件引入

场景级处理器、步骤级验证器 / 提取器均可从公共组件库以**复制**方式引入（能力来源见公共需求 `docs/01-requirements/05-api-testing/09-api-srs-common.md`；复制语义见 `docs/04-detailed-design/05-api-testing/06-api-testing-infra-common-component.md` 第 2 节）。引入为纯前端行为：拉取组件列表后在本地转换为编辑行，产生独立副本，与源组件无关联，随场景保存聚合落库；后端不提供独立的资产引入接口。

统一的列表接口为 `GET /api/project/components`，弹窗 `ExtractorAssetPicker.vue` 支持关键字搜索、加载态与失败态（错误提示 + 「重试」），确认后提示「已引入 N 个〈类型〉」：

| 引入入口 | type 参数 | 其他参数 | 本地转换 |
| ---- | ---- | ---- | ---- |
| 步骤内联编辑器「验证器」页签 →「从公共组件获取」 | `validator` | `enabled=true`、`pageNo=1`、`pageSize=100` | 新 id、`name` = 组件名、`enabled=true`；组件 `config` → `target` / `condition` / `expression` / `expected`（缺省 `status_code` / `equals`） |
| 步骤内联编辑器「提取器」页签 →「从公共组件获取」 | `extractor` | 同上 | 新 id、`name` = 组件名、`enabled=true`；组件 `config` → `source` / `expression` / `variableName`（缺省 `json_field`） |
| 前置 / 后置处理器页签 →「从公共组件引入」 | `preprocessor` / `postprocessor` | `pageNo=1`、`pageSize=200`（不过滤启用态） | `type` 取当前页签，`testclass` / `config` / `extractors` 取自组件 `config`，追加到处理器列表 |
| 处理器配置「提取器」页签 →「从公共组件获取」 | `extractor` | `pageNo=1`、`pageSize=200` | 解析组件 `config` 为处理器内嵌提取器行，追加到该处理器的 `extractors` |

- 步骤级获取仅返回启用中的组件（`enabled=true`）；场景级处理器引入不传启用过滤。
- 组件 `config` 解析失败时回退为空对象，避免引入崩溃；缺省值回填保证 `target` / `condition` / `source` 落在执行引擎支持的值域内。

## 4. 验证器配置模型

验证器采用**平台自有数据结构**，遵循三大原则：

1. **自然语言化**：使用用户理解的术语（如「状态码」「等于」），隐藏 Ryze 内部概念（testclass / rule）
2. **最小化暴露**：仅暴露用户必须配置的字段，其余由平台推断
3. **启用禁用**：每个验证器可独立启用 / 禁用；禁用时配置保留但不参与执行，执行引擎转换时直接跳过

**平台数据结构**（存储于 `validators` JSONB）：

```json
{
  "id": "uuid",
  "name": "验证描述（如：验证登录成功）",
  "enabled": true,
  "target": "status_code|json_field|response_header|response_body|regex",
  "condition": "equals|not_equals|greater_than|less_than|greater_or_equal|less_or_equal|contains|not_contains|starts_with|ends_with|matches_regex",
  "expected": "<期望值（视 target 取用）>",
  "expression": "<表达式（视 target 取用）>"
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | 是 | 主键，应用层生成（空态默认行用随机 UUID，序列化前过滤） |
| name | string | 否 | 用户自定义描述，用于报告展示；为空时序列化补 `验证器 <target>`，从组件引入时取组件名 |
| enabled | boolean | 是 | 启用 / 禁用。禁用时配置保留但不参与执行，引擎转换时跳过 |
| target | enum | 是 | 验证目标，用户选择「验证什么」；序列化时 `target` 为空的行被过滤 |
| condition | enum | 是 | 比较条件，用户选择「怎么比较」 |
| expected | string | 否 | 期望值 |
| expression | string | 否 | 表达式（JSONPath、头名、正则等） |

**target 与字段取用**：编辑器不做联动——启用开关、验证目标、比较条件、表达式、期望值恒显示，比较条件固定提供全部 11 项；引擎按 `target` 取用所需字段。

| target | 用户看到的标签 | 引擎取用字段 |
| ---- | ---- | ---- |
| status_code | 状态码 | `expected` + `condition` |
| json_field | JSON 字段 | `expression`（JSONPath，如 `$.code`）+ `expected` + `condition` |
| response_header | 响应头 | `expression`（头名，如 `Content-Type`）+ `expected` + `condition` |
| response_body | 响应体 | `expected` + `condition` |
| regex | 正则匹配 | `expression`（作为待匹配正则） |

**UI 交互示例**（`ValidatorsExtractorsPanes.vue`，页签标题带有效条数徽标，HTTP 与 JDBC 步骤、处理器配置共用同一面板）：

```
┌ 验证器 ─────────────────────────────────────────────────────────┐
│                       [+ 添加验证器]  [从公共组件获取]              │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ ✓ [状态码 ▼] [等于 ▼] [表达式（如 $.code）] [期望值] [删除]     │  │
│ └────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

行内编辑任一字段即时回写；「删除」移除该行；无验证器时提供一条全空默认行（不落库），仅作为输入起点。

**平台 → Ryze 转换规则**（执行引擎层）：

| 平台 target | → Ryze testclass | field 推断逻辑 |
| ----------- | ----------------- | -------------- |
| status_code | `http` | 固定 `status` |
| json_field | `json` | `expression` 直传 |
| response_header | `http` | `header.<expression>` |
| response_body | `result` | —（对响应体断言） |
| regex | `result` | —（`expected` 取 `expression`，固定按正则匹配） |

`condition` → `rule` 映射：

| condition | rule | condition | rule |
| ---- | ---- | ---- | ---- |
| equals | equals | contains | contains |
| not_equals | not_equals | not_contains | not_contains |
| greater_than | gt | greater_or_equal | gte |
| less_than | lt | less_or_equal | lte |
| matches_regex | regex | — | — |

- `response_body` 的 `starts_with` / `ends_with` 转锚定正则（`^<期望值>` / `<期望值>$`，期望值按正则字面量转义），`rule` 取 `regex`。
- 不在上表内的 `target` / `condition` 抛出「不支持的验证目标 / 比较条件」错误。

**示例**：

```json
{ "id": "018g...", "name": "验证返回码为200", "enabled": true, "target": "status_code", "condition": "equals", "expected": "200", "expression": "" }
{ "id": "018g...", "name": "验证业务码成功", "enabled": true, "target": "json_field", "condition": "equals", "expected": "200", "expression": "$.code" }
{ "id": "018g...", "name": "验证返回消息包含成功", "enabled": true, "target": "json_field", "condition": "contains", "expected": "成功", "expression": "$.message" }
{ "id": "018g...", "enabled": false, "target": "status_code", "condition": "equals", "expected": "200", "expression": "" }
```

## 5. 提取器配置模型

提取器采用与验证器一致的平台设计原则：自然语言化、最小化暴露、启用禁用。

**平台数据结构**（存储于 `extractors` JSONB）：

```json
{
  "id": "uuid",
  "name": "提取描述（如：提取登录 token）",
  "enabled": true,
  "source": "json_field|response_header|regex|full_body",
  "expression": "<表达式>",
  "variableName": "<目标变量名>"
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | 是 | 主键，应用层生成 |
| name | string | 否 | 用户自定义描述，用于报告展示；为空时序列化补 `提取器 <source>`，从组件引入时取组件名 |
| enabled | boolean | 是 | 启用 / 禁用，语义同验证器；禁用行引擎转换时跳过 |
| source | enum | 是 | 提取来源，用户选择「从哪里提取」 |
| expression | string | 视 source | 提取表达式 |
| variableName | string | 是 | 提取结果存入的变量名；序列化要求 `source` 与 `variableName` 均非空，否则该行被过滤 |

**source 与 expression 的关系**（编辑器不做联动，三列恒显示）：

| source | 用户看到的标签 | expression 说明 | UI 控件 |
| ---- | ---- | ---- | ---- |
| json_field | JSON 字段 | JSONPath，如 `$.data.token` | 输入框 |
| response_header | 响应头 | 头名，如 `Set-Cookie` | 输入框 |
| regex | 正则匹配 | 正则表达式（含捕获组） | 输入框 |
| full_body | 完整响应体 | 无需表达式 | 输入框留空 |

**UI 交互示例**（与验证器同面板）：

```
┌ 提取器 ─────────────────────────────────────────────────────────┐
│                       [+ 添加提取器]  [从公共组件获取]              │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ ✓ [JSON 字段 ▼] [表达式] [变量名] [删除]                      │  │
│ └────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

**平台 → Ryze 转换规则**（执行引擎层）：

| 平台 source | → Ryze testclass | field 推断逻辑 | ref_name |
| ----------- | ----------------- | -------------- | -------- |
| json_field | `json` | `expression` 直传 | `variableName` 直传 |
| response_header | `http` | `expression` 直传（头名，如 `Set-Cookie`） | `variableName` 直传 |
| regex | `regex` | `expression` 直传 | `variableName` 直传 |
| full_body | `result` | —（取完整响应体） | `variableName` 直传 |

- 禁用行在转换时跳过；不在上表内的 `source` 抛出「不支持的提取来源」错误。

**示例**：

```json
{ "id": "018h...", "name": "提取登录 token", "enabled": true, "source": "json_field", "expression": "$.data.token", "variableName": "token" }
{ "id": "018h...", "name": "提取用户ID", "enabled": true, "source": "regex", "expression": "\"id\":(\\d+)", "variableName": "userId" }
{ "id": "018h...", "name": "完整响应体", "enabled": true, "source": "full_body", "expression": "", "variableName": "fullResponse" }
{ "id": "018h...", "enabled": false, "source": "json_field", "expression": "$.data.id", "variableName": "tempId" }
```

## 6. 请求配置（request_config）平台数据结构

HTTP 步骤的请求配置由内联编辑器写入，遵循平台设计原则，将 HTTP 请求的各组成部分以用户友好的方式组织：

**平台数据结构**（存储于 `request_config` JSONB）：

```json
{
  "method": "POST",
  "url": "/api/auth/login",
  "refName": "default-http",
  "headers": [
    { "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "params": [
    { "key": "page", "value": "1", "enabled": true }
  ],
  "body": {
    "type": "json",
    "content": { "username": "${username}", "password": "${password}" }
  }
}
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| method | enum | 否 | 请求方法，编辑器下拉含 GET / POST / PUT / PATCH / DELETE / HEAD / OPTIONS，为空时缺省 GET |
| url | string | 是 | 请求路径（支持 `${变量名}` 引用），相对路径或 `https://` 绝对地址 |
| refName | string | 否 | 步骤引用的环境 HTTP 配置（存 `refName`）；为空时执行期注入环境默认 HTTP 配置的 `ref` |
| headers | array | 否 | 请求头列表（见 6.1），执行期与环境 HTTP 配置的默认请求头合并，步骤级同名项覆盖 |
| params | array | 否 | 查询参数列表（见 6.2） |
| body | object | 视 method | 请求体（见 6.3） |

JDBC 步骤的 `requestConfig` 结构不同：

```json
{ "datasource": "orders-ds", "sql": "SELECT * FROM t_order WHERE id = ?", "args": ["1"] }
```

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| datasource | string | 否 | 环境数据源 `refName`，新建步骤默认选中第一个数据源 |
| sql | string | 是 | SQL 语句 |
| args | array | 否 | 与 `?` 占位符一一对应的参数值；无参数时该字段不写入 |

> **配置继承原则**：`base_url`、`timeout` 不在步骤编辑器中配置，环境级 base_url 与默认请求头由执行期的环境 HTTP 配置引用提供；步骤无需重复配置环境中已有的值，配置了也由步骤级覆盖环境级。

### 6.1 请求头（headers）

**数据结构**：

```json
[
  { "key": "Content-Type", "value": "application/json", "enabled": true }
]
```

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| key | string | 是 | 请求头名称（如 `Content-Type`、`Authorization`）；空 `key` 行在序列化时被过滤 |
| value | string | 否 | 请求头值（支持 `${变量名}` 引用） |
| enabled | boolean | 是 | 启用 / 禁用。禁用时该请求头不参与请求发送 |

> 编辑器序列化仅保留 `key` / `value` / `enabled` 三项：`id`、`description` 不写入存储。

**UI 交互**（`KeyValueTable.vue`，末行自动补一条空行，与 Postman 一致）：

```
┌ 请求头 ────────────────────────────────────────────────────────┐
│ ✓  Content-Type    [application/json          ]  [×]           │
│ ✓  Authorization   [Bearer ${token}           ]  [×]           │
│ ☐  X-Custom-Header [disabled_value            ]  [×]           │
│ ✓  [Header        ] [值                      ]  [×]           │
└────────────────────────────────────────────────────────────────┘
```

**平台 → Ryze 转换规则**：将数组转换为 Map `{key: value}`，仅包含 `enabled=true` 的条目（引擎同时兼容 KV 数组与 map 两种落库形态）。

### 6.2 查询参数（params）

**数据结构**：与请求头结构一致（`key` / `value` / `enabled`）。

**UI 交互**：与请求头同一表格组件，页签标题为「Query 参数」并带条数徽标。

**平台 → Ryze 转换规则**：将数组转换为 Map `{key: value}`，仅包含 `enabled=true` 的条目，映射到 Ryze 的 `query` 字段。

### 6.3 请求体（body）

**数据结构**：

```json
{
  "type": "json",
  "content": { "username": "${username}", "password": "${password}" }
}
```

| type | content 类型 | content 说明 | UI 控件 |
| ---- | ---- | ---- | ---- |
| none | null | 无请求体 | 提示文案「该请求不携带请求体」 |
| form | array | `[{ "key": "field", "value": "val", "enabled": true }]`，序列化过滤空 `key` 行 | KV 表格（编辑器态显示为 `x-www-form-urlencoded`） |
| json | string / object | JSON 文本解析后的结构 | raw + JSON 子类型文本域，带「格式化」按钮 |
| raw | string | 原始文本内容 | raw 文本域，子类型含 text / json / xml / html / javascript |

**编辑器行为**：

- 请求体类型为三态切换：`none` / `x-www-form-urlencoded` / `raw`（+ 子类型下拉）；落库映射为 `none` / `form` / `raw`，`raw` 下选 JSON 子类型落库为 `json`。
- 切换类型或子类型时自动注入 / 移除 `Content-Type` 请求头（替换同名头）：`x-www-form-urlencoded` 注入 `application/x-www-form-urlencoded`，`raw` 按子类型注入（如 `application/json`，text 不注入），`none` 不注入。
- JSON 子类型提供格式化按钮；保存时 JSON 解析失败提示「JSON 请求体格式非法，请修正后再保存」，并保留上一份合法请求体不被覆盖。

**平台 → Ryze 转换规则**：`none` 移除 `body`；`form` 的 `content`（仅启用行）转换为 `data` Map `{key: value}`；`json` / `raw` 的 `content` 直传 `body`。

### 6.4 完整示例

```json
{
  "method": "POST",
  "url": "/api/auth/login",
  "refName": "default-http",
  "headers": [
    { "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "params": [],
  "body": {
    "type": "json",
    "content": { "username": "${username}", "password": "${password}" }
  }
}
```

## 7. 场景编排器

编排器为场景编辑器（`SceneEditorPage.vue`），承载于「接口测试 → 测试场景」的多页签工作区（`SceneWorkspace.vue`）：「全部场景」列表页签常驻，新建 / 编辑 / 复制各占一个可关闭页签，页签名显示场景名（创建态为「新场景」），有未保存改动时页签名前显示圆点，关闭未保存页签弹出「该场景有未保存的修改，确定关闭？」确认；URL query 同步 `?tab=scenes`、`&sceneId=`、`&action=create`（含 `&moduleId=` / `&copyFrom=`），刷新与直链可恢复。

### 7.1 页面结构

```
┌ 场景编辑器 ─────────────────────────────────────────────────────────┐
│ 顶部信息栏  *名称  *所属模块  状态(草稿/已发布)  优先级  描述  执行历史   │
│            默认环境   [▶ 运行场景] [保存为草稿] [发布] [删除]           │
├────────────────────────────────────────────────────────────────────┤
│ 页签区    [步骤] [场景变量] [前置处理器] [后置处理器]   函数助手 变量助手   │
│           ┌步骤页：左步骤画布 ── 可拖拽分割条 ── 右内联编辑器┐           │
│           └处理器页：左处理器列表 ── 分割条 ── 右配置编辑 ┘            │
├────────────────────────────────────────────────────────────────────┤
│ 底部状态条   保存状态提示文案                                          │
└────────────────────────────────────────────────────────────────────┘
```

**顶部信息栏**：

- 字段：场景名称（必填 `*`，最长 100）、所属模块（级联选择，必填 `*`）、状态 tag（草稿 / 已发布）、优先级（P0–P3 弹层选择，缺省 P2）、描述（图标切换显示 / 隐藏文本域）、默认环境（可清空下拉）。
- 动作按钮：创建态为「▶ 运行场景」「保存为草稿」「发布」；编辑态同组按钮外另有「删除」（确认框「删除场景后不可恢复，确定删除？」）。保存前校验场景名称与所属模块必填，缺失分别提示「请填写场景名称」「请选择所属模块」。
- 运行分支：创建态步骤为空提示「请先添加步骤」，否则发起草稿执行并提示「草稿运行完成：通过 N · 失败 N · 跳过 N」；编辑态若存在未保存改动先保存，成功后提示「场景已触发执行（executionId）」。
- 执行历史（仅编辑态）：顶部弹出面板，加载中显示 loading，失败显示错误条 + 「重试」，空态「暂无执行记录」；行内展示状态、执行时间与「报告」按钮（存在报告时打开报告详情对话框），分页每页 20 条。

**页签区**：四个页签——步骤 / 场景变量 / 前置处理器 / 后置处理器，无独立的执行历史页签（执行历史在顶部信息栏）。页签区右上角固定「函数助手」「变量助手」入口。场景变量页签为 KV 表格（变量名、值、描述），无启用列，空行自动补行。

**底部状态条**：创建态缺省文案「草稿未保存」，编辑态缺省「自动保存已开启」；本地改动后 5 秒内显示「自动保存已开启 HH:mm:ss」时间戳。落库仍由「保存为草稿」「发布」与运行前自动保存触发。

### 7.2 步骤页（左右分栏，可拖拽分割条）

左侧步骤画布（`StepCanvas.vue`），头部显示步骤计数与两个入口：

- **「从接口添加」**：打开 1.2 的接口选择对话框。
- **「+ 添加步骤」**：直接追加一个未命名 HTTP 步骤（`new-` 临时 id、`sourceType = manual`、默认 GET 空路径）并选中，不弹选择器。

步骤卡片内容：序号、类型 tag（HTTP / JDBC）、方法或 SQL 类型 tag、来源接口名称 tag、`sourceMissing` 时的「源已删除」徽标、启用开关、展开 / 折叠按钮（仅 `sourceType = link` 的步骤显示）、「操作」下拉（编辑 / 上移 / 下移 / 调试 / 复制 / 删除；首项上移与末项下移禁用）。点击卡片主体选中并进入右侧编辑。

- 展开区（link 步骤）：源已删除时提示「源已删除，使用创建时的快照执行」，并显示验证器 / 提取器条数。
- 拖拽：卡片间与列表末尾为插入区，拖拽或上移 / 下移触发排序（见 1.5）；禁用步骤卡片半透明显示。
- 未选中步骤时右侧显示「选中左侧步骤卡片后在右侧编辑」；切换步骤时若当前步骤缺少名称或 HTTP 路径 / JDBC SQL，阻止切换并提示「请先填写当前步骤的名称 / HTTP 路径 / SQL 语句」。

右侧步骤内联编辑器（`SceneStepInlineEditor.vue`，编辑即生效）：

- 头部：步骤名称输入、启用开关、类型单选（HTTP 请求 / JDBC 请求）、环境引用下拉（HTTP 类型选择环境 HTTP 配置，未引用时优先选默认配置；JDBC 类型选择环境数据源，未选择时选第一个）。
- HTTP：复用请求配置编辑器（`RequestConfigEditor.vue`），请求行 = 方法下拉 + URL 输入，页签为请求头 / Query 参数 / 请求体 / 验证器 / 提取器。
- JDBC：页签为 SQL / 验证器 / 提取器，SQL 为文本域，另可维护 `?` 占位参数列表。
- 环境配置加载失败时显示错误条并可「重试」；任一字段变更立即写回步骤对象，持久化由顶部保存触发。

### 7.3 前置 / 后置处理器页（同构左右分栏）

- 同一 `processors` 列表按元素 `type` 为 `pre` / `post` 拆分展示；列表头部为「从公共组件引入」「+ 添加前/后置处理器」，空态显示空态占位与添加按钮。
- 列表项：拖拽序号、类型与引用 tag、名称（缺省显示 `处理器 N`）、启用开关、「操作」下拉（编辑 / 上移 / 下移 / 复制 / 删除），支持拖拽排序与上移 / 下移。
- 右侧配置编辑器（`ProcessorConfigEditor.vue`）：处理器名称 + 启用开关 + 类型单选（HTTP / JDBC）+ 引用下拉（环境 HTTP 配置 / 数据源，未选择时补默认引用）；HTTP 复用请求配置编辑器（含提取器页签），JDBC 为 SQL + 提取器页签；未选中处理器时右侧显示「选中左侧处理器后在右侧编辑」。

### 7.4 单步调试入口

步骤卡片「操作」→「调试」调用单步调试接口，结果在「调试结果」对话框（`StepDebugResultDialog.vue`）展示：状态 tag 与耗时、请求 / 响应 JSON、验证器结果（验证器名 + 通过 / 失败）、提取的变量（变量名 / 值）。接口路径与响应结构见 `docs/04-detailed-design/05-api-testing/16-test-scenario-execution.md` 第 1.3 节；创建态点击「调试」提示「创建成功后可在编辑页单步调试」，不发起请求。

### 7.5 状态分支与权限

- **加载中**：编辑器整体 loading；接口列表、公共组件、环境配置、执行历史各自独立 loading，互不阻塞。
- **错误**：各数据源加载失败均在对应面板内显示错误信息并提供「重试」；保存 / 运行 / 调试 / 排序失败通过顶部消息提示错误内容。
- **禁用态**：禁用步骤卡片半透明；上移 / 下移在首末项禁用；执行中的步骤调试项禁用；创建态不显示「删除」与「执行历史」。
- **权限口径**：编辑器内部无按钮级权限码；「测试场景」菜单项需 `api-scene:view`，接口测试模块入口对任一接口测试模块 view 权限开放（`api-debug:view`、`api-scene:view`、`api-env:view` 等 9 项）。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐场景步骤编辑器实现：修正步骤接口路径与聚合落库、公共组件引入方式、验证器/提取器与请求配置结构及 Ryze 转换表、场景编排器页面结构与状态分支 |
