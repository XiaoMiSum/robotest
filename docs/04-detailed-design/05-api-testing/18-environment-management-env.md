# 软件测试平台——环境管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 环境管理

> **权限口径**：查询列表、查询详情、连接测试与导出需 `api-env:view`；创建、更新、删除、设为默认、排序与导入需 `api-env:edit`。工作空间与项目上下文经 `X-Active-Workspace` / `X-Active-Project` 请求头传递，不出现在 URL 中。

### 1.1 查询环境列表

- **路径**：`GET /api/project/environments?keyword=xxx`
- **参数**：`keyword` 环境名称模糊搜索（前端输入防抖 300ms 后触发请求，空值不传参）。
- **排序**：默认环境置顶，其余按 `sortOrder` 升序（服务端按 `is_default DESC, sort_order ASC` 返回，前端再按默认标记与 `sortOrder` 排序、同序按名称兜底）。
- **分页约定**：该接口为有限配置项的非分页列表，响应 `data` 直接为环境数组，不使用 `PageResult.list/total`。
- **响应**：

```json
[
  {
    "id": "018f...",
    "name": "测试环境",
    "description": "日常测试",
    "isDefault": true,
    "sortOrder": 0,
    "httpConfigCount": 2,
    "variableCount": 5,
    "dataSourceCount": 2,
    "processorCount": 1
  }
]
```

### 1.2 查询环境详情

- **路径**：`GET /api/project/environments/:id`
- **响应**：环境聚合详情，含 HTTP 配置列表、变量列表、数据源列表、处理器列表；子资源以聚合数组整体读写，元素不携带独立 `id`。
- **响应示例**：

```json
{
  "id": "018f...",
  "name": "测试环境",
  "description": "日常测试",
  "scope": "project",
  "isDefault": true,
  "sortOrder": 0,
  "httpConfigs": [
    {
      "name": "内部 API",
      "refName": "http_1",
      "baseUrl": "https://staging.example.com",
      "isDefault": false,
      "headers": [{ "key": "Content-Type", "value": "application/json", "enabled": true }]
    }
  ],
  "variables": [
    { "name": "TEST_PASSWORD", "value": "123456", "hasValue": true, "description": "测试密码" }
  ],
  "dataSources": [
    {
      "name": "测试库",
      "refName": "db_1",
      "driver": "com.mysql.cj.jdbc.Driver",
      "url": "jdbc:mysql://staging-db:3306/test?user=test_user&password=123456",
      "connectionProperties": {},
      "maxPoolSize": 5,
      "isDefault": false
    }
  ],
  "processors": [
    {
      "processorType": "preprocessor",
      "name": "登录前置",
      "config": {},
      "sortOrder": 1,
      "enabled": true
    }
  ]
}
```

- **说明**：`scope` 恒为 `project`（全局环境为预留扩展位）；变量取值明文返回，`hasValue` 标识是否已配置；数据源 `url` 原样返回（凭据内嵌于 URL，不做脱敏，导出时整段排除，见 1.9）。导出接口复用本结构（见 1.9）。

### 1.3 创建环境

- **路径**：`POST /api/project/environments`
- **请求体**：

```json
{
  "name": "测试环境",
  "description": "日常测试",
  "isDefault": true,
  "sortOrder": 0,
  "httpConfigs": [
    {
      "name": "内部 API",
      "refName": "http_1",
      "baseUrl": "https://staging.example.com",
      "headers": [{ "key": "Content-Type", "value": "application/json", "enabled": true }]
    }
  ],
  "variables": [
    { "name": "TEST_PASSWORD", "value": "123456", "description": "测试密码" }
  ],
  "dataSources": [
    {
      "name": "测试库",
      "refName": "db_1",
      "driver": "com.mysql.cj.jdbc.Driver",
      "url": "jdbc:mysql://staging-db:3306/test?user=test_user&password=123456",
      "connectionProperties": {},
      "maxPoolSize": 5
    }
  ],
  "processors": []
}
```

- **响应**：`{ "id": "018f..." }`
- **校验规则**：`name` 必填且 ≤100 字符、项目内唯一（重复返回 1000017401）；`description` ≤500 字符；变量名匹配 `^[A-Za-z0-9_]+$` 且同环境内唯一；数据源 `name` / `refName` / `driver` / `url` 必填；处理器 `processorType` 仅取 `preprocessor` / `postprocessor`。`httpConfigs` 缺省时服务端自动生成一条默认配置，`refName` 缺省按 `http_N` 生成。
- **说明**：新建与复制共用本接口。复制不设独立接口，前端以选中环境详情预填面板新建态，用户确认后按本接口落库（见 `docs/05-interaction-design/05-api-testing/12-environment-ui-page.md` 1.5）；副本不携带源环境 `id`，`sortOrder` 取列表末尾序号（`max(sortOrder)+1`），`isDefault` 预置为 `false`（新建态可在吸顶区打开「设为默认」开关），不抢占源环境默认标记。数据源随创建负载一并复制：凭据内嵌于连接 URL，复制仅在项目内流转、不越项目边界（导出/导入仍排除数据源段，见 1.9 / 1.10）。HTTP 配置、变量与处理器同样全量随副本。

### 1.4 更新环境

- **路径**：`PUT /api/project/environments/:id`
- **请求体**：同 1.3。
- **说明**：聚合全量保存。HTTP 配置、变量、数据源、处理器均以 JSONB 随环境整体提交，一次性写入主表 `http_configs` / `variables` / `data_sources` / `processors` 列；子资源以请求列表整批替换，未传的段落视为清空（`httpConfigs` 缺省时仍自动生成默认配置）；`sortOrder` 缺省保留原值；`isDefault` 为 `true` 时自动取消项目内其余环境的默认标记。供环境编辑、导入/导出及全部配置面板（HttpConfig / 变量 / 数据源 / 处理器）的整体保存使用（见 3.2 / 3.3 / 3.4）。

### 1.5 删除环境

- **路径**：`DELETE /api/project/environments/:id`
- **校验**：若环境被定时任务绑定，返回错误码 1000017404（`API_ENV_TASK_BOUND`），需先在定时任务中解除绑定；环境不存在或不属于当前项目返回 1000017405（`API_ENV_NOT_FOUND`）。
- **交互**：前端二次确认后调用，成功 Toast「已删除」并刷新列表；删除当前选中环境时清空右侧选中态。

### 1.6 设置默认环境

- **路径**：`PATCH /api/project/environments/:id/set-default`
- **响应**：`{ "success": true }`
- **说明**：默认环境项目内唯一，服务端先清除项目内其余环境的默认标记再置位；前端二次确认后调用并刷新列表。

### 1.7 测试数据源连接

- **路径**：`POST /api/project/environments/:id/data-sources/test`
- **说明**：连接测试为请求体传入**完整数据源配置**进行测试（免保存）：按表单当前值试连，新建中或已修改未保存的数据源亦可直接验证。成功返回连接信息，失败以错误码 1000017403（`API_DATASOURCE_CONN_FAILED`）抛出并附带详细原因。
- **前置条件**：路径需 `:id`，环境尚未创建（面板新建态）时无 ID，连接测试不可用，前端置灰并提示创建后再试（见 `docs/05-interaction-design/05-api-testing/12-environment-ui-page.md` 1.6）。
- **判定口径**：URL 以 `redis://` 或 `rediss://` 开头的数据源不走 JDBC、不校验驱动：按 RESP 协议建立连接后发送 `PING` 验证连通性，成功时通过 `INFO server` 提取 `redis_version` 填入 `databaseVersion`。JDBC 数据源仅放行随服务打包的驱动（当前为 `com.mysql.cj.jdbc.Driver`、`org.postgresql.Driver`），其余拒绝测试并返回「不支持的数据库驱动」。
- **请求体**：

```json
{
  "driver": "com.mysql.cj.jdbc.Driver",
  "url": "jdbc:mysql://staging-db:3306/test?user=test_user&password=123456",
  "connectionProperties": {}
}
```

- **响应**：

```json
{
  "success": true,
  "message": "连接成功",
  "databaseVersion": "MySQL 8.0"
}
```

Redis 数据源成功响应示例：

```json
{
  "success": true,
  "message": "连接成功",
  "databaseVersion": "Redis 7.2.4"
}
```

### 1.8 测试 HTTP 连接

- **路径**：`POST /api/project/environments/:id/http-configs/test`
- **说明**：连接测试为请求体传入**当前表单值**进行测试（免保存）：试连仅向 `baseUrl` 发送 GET 请求，`refName` 与 `headers` 随负载传递但不参与请求；新建中或已修改未保存的 HTTP 配置亦可直接验证。
- **前置条件**：同 1.7，环境尚未创建时连接测试不可用。
- **请求体**：

```json
{
  "baseUrl": "https://staging.example.com",
  "refName": "http_1",
  "headers": [{ "key": "Content-Type", "value": "application/json", "enabled": true }]
}
```

- **判定口径**：收到任意 HTTP 响应（含 4xx / 5xx）均视为连通；网络层失败以 `success=false` 结构化返回（HTTP 200），不使用业务错误码。
- **响应**：

```json
{
  "success": true,
  "message": "连接成功",
  "statusCode": 200,
  "durationMs": 125
}
```

### 1.9 导出环境

- **路径**：`GET /api/project/environments/:id/export`
- **响应**：环境详情结构（同 1.2），前端以 `<环境名>.json` 落地为文件下载。
- **说明**：数据源段整段排除（响应中 `dataSources` 恒为 `[]`）：凭据内嵌于连接 URL 无法导出一部分，导入端亦不消费该段，环境导入后需重新配置数据源。变量值明文导出（无敏感值概念）。

### 1.10 导入环境

- **路径**：`POST /api/project/environments/import`
- **Content-Type**：`multipart/form-data`
- **请求参数**：`file`（环境配置 JSON 文件，查询参数 `overwrite`：`true` 覆盖 / `false` 跳过）。
- **说明**：导入环境配置 JSON 文件，空文件与超过 10MB 的文件拒绝（大小限制独立于容器 multipart 配置）；重名环境按 `overwrite` 开关处理：开启时覆盖，关闭时跳过（不新增）。文件中的 `dataSources` 段被忽略（见 1.9 导出规则），环境导入后需重新配置数据源。变量值与 HTTP 配置、处理器一并导入。新增环境 `isDefault` 恒为 `false`、`sortOrder` 取项目内末尾序号；覆盖沿用原环境 `id` 与排序，仅替换描述与四段聚合资源。
- **响应**：

```json
{
  "createdCount": 1,
  "overwrittenCount": 0,
  "skippedCount": 1
}
```

- **前端反馈**：导入完成后弹窗展示「导入完成：新增 x 个、覆盖 x 个、跳过 x 个」，无变更时提示「未发生任何变更」，随后刷新列表。

### 1.11 调整环境排序

- **路径**：`PATCH /api/project/environments/:id/sort`
- **请求体**：

```json
{
  "sortOrder": 1
}
```

- **说明**：调整环境排序序号，列表按默认环境置顶 + `sort_order` 升序展示。前端「上移 / 下移」与相邻环境互换序号（对两条记录各调用一次本接口）后刷新列表。


## 2. 环境配置页

- **入口与权限**：环境管理无独立路由，作为接口测试页（`/workspace/projects/api-testing`）侧边导航项 `environments`（菜单项「环境管理」，图标 Compass）呈现，切换时同步 `?tab=environments`；导航项按 `api-env:view` 展示，页面内写操作按 `api-env:edit` 控制入口显隐或置灰，无权限时相关按钮不渲染或点击提示「无环境编辑权限」。
- **页头**：标题「环境管理」+ 描述文案，说明环境含 HTTP 配置、变量、数据源与处理器，场景执行未指定环境时自动使用标记「默认」的环境。

```
┌───────────────────────────────┬───────────────────────────────────────┐
│ [搜索环境名称...] [新建 ▾]      │ 环境名称 [默认]        [未保存][保存全部]│
│ 测试环境 [默认]                │ [HTTP(2)][变量(5)][数据源(2)]          │
│  2 HTTP · 5 变量 · 2 数据源 · 1 │ [前置处理器(1)][后置处理器(0)]         │
│  [上移][下移][编辑][复制][删除]  │ ───────────────────────────────────  │
│ 预发环境 [设为默认]             │  左列表  │  内联表单 / 键值编辑器       │
└───────────────────────────────┴───────────────────────────────────────┘
```

- **环境列表（左栏，宽 300px）**：
  - 首行：搜索框（防抖 300ms 按名称过滤，可清空）+ [新建] 分裂按钮；主按钮进入新建态，右侧下拉含 [导入环境]（无 `api-env:edit` 时置灰）与 [导出当前环境]（未选中环境时置灰，仅需 `api-env:view`）。
  - 列表行：环境名称 + 「默认」标签；非默认环境行内悬浮 [设为默认]（仅 `api-env:edit` 可见，点击二次确认）。行元信息为 `N HTTP · N 变量 · N 数据源 · N 处理器` 计数。
  - 行内悬浮操作（仅 `api-env:edit` 渲染）：[上移] / [下移]（边界置灰）、[编辑]、[复制]、[删除]。
  - 其下为左栏唯一滚动区。
- **编辑环境弹窗**：字段 名称（必填、≤100）、描述（≤500、可选）、设为默认开关；确定后取详情全量回传聚合保存（其余子资源原样提交），成功 Toast「已保存」并刷新列表；新建与复制不走本弹窗，由右侧面板新建态承载。
- **导入环境弹窗**：拖拽/点击选择单个 `.json` 文件（限 1 个、不自动上传）+「重名时覆盖（关闭则跳过不新增）」开关；提交后以弹窗展示导入结果（见 1.10）。
- **详情面板（右栏）**：
  - **页签**：HTTP、变量、数据源、前置处理器、后置处理器，各带计数徽标；页签与标题吸顶，滚动不丢失上下文。
  - **详情态**：吸顶区为环境名称 + 「默认」标签 + 「未保存」标记 + [保存全部]（仅 `api-env:edit` 渲染）；名称 / 描述 / 设为默认在「编辑环境」弹窗维护。表单在无 `api-env:edit` 时整体禁用。
  - **新建态**（新建与复制共用）：吸顶区为 [名称（必填）] [描述] [设为默认] + [取消] / [创建]，不发详情请求；离开新建态（取消、切换选中、编辑、删除、再次新建/复制）在有改动时先确认放弃；创建成功 Toast「环境已创建」、排到列表末尾并选中，副本名称预填 `原名称（副本）`。新建态无环境 ID，HTTP 与数据源的连接测试按钮置灰并提示「创建环境后可测试连接」（见 1.7 / 1.8）。
  - **未保存与保存**：以聚合载荷快照比对判定「未保存」；[保存全部] 提交前校验环境名称非空、HTTP 配置的名称/引用名/Base URL、数据源的名称/引用名/驱动/URL、变量名格式与同环境唯一、处理器名称，校验失败 Toast 警告并不提交；提交走 1.4 聚合更新，成功 Toast「已保存」并清除标记。
  - **HTTP 页签**：左侧配置列表 + 右侧内联表单（名称、引用名、Base URL、设为默认开关——同一环境至多一个默认，列表中默认配置置顶）+ 请求头键值表（键 / 值 / 启用）；[＋ 新增配置] 在列表末尾新增并选中（预置 `配置 N` / `http_N` 与一行空请求头），[删除配置] 移除当前配置；[连接测试] 结果内联显示在按钮旁（成功展示状态码与耗时，失败展示原因），切换配置即清空。变更计入未保存，随 [保存全部] 整体写入（见 1.4 / 3.4），执行侧超时 / 重定向 / SSL 为固定值（见 2.1.2）。
  - **变量页签**：键值表格（变量名、值、描述），无启用列，表头 [＋ 新增] 添加行；取值明文展示，随环境整体保存（见 3.3）；面板下方提示引用语法 `${变量名}`（如 `${BASE_URL}`）；变量随环境导入 / 导出一并处理（见 1.9 / 1.10）。
  - **数据源页签**：与 HTTP 同构的左列表 + 右内联表单（名称、引用名、驱动下拉、设为默认开关、URL 多行输入独占一行）+ [连接测试] / [删除数据源]；驱动下拉为 MySQL / PostgreSQL / Oracle / SQLServer / Redis 五项，切换驱动且 URL 为空时自动填充对应 URL 示例（含用户名密码占位符），URL 输入框提示「用户名/密码通过 URL 设置」；Redis 选项驱动以 `-` 占位（后端 driver 必填），连接按 `redis://` 协议识别。变更计入未保存，随 [保存全部] 整体保存（见 3.4）。
  - **前置 / 后置处理器页签**：平铺展开式列表（序号、类型标签、名称），行内悬浮 [上移] / [下移] / [编辑] / [复制] / [删除]；点击行展开只读明细（再次点击收起），[编辑] 展开编辑表单、[＋ 添加处理器] 在列表末尾展开新增草稿，同一时刻仅展开一处，编辑期间行内操作替换为 [取消] / [保存]；[从公共组件引入] 将启用中的处理器资产复制为独立副本，处理器表单内可 [从公共组件引入提取器]；切换页签即收起展开与草稿。变更计入未保存，随 [保存全部] 整体保存（见 3.2）。
- **状态分支**：
  - 加载中：左栏骨架屏，右栏 loading 遮罩。
  - 加载失败：左栏「环境详情加载失败」/「环境列表加载失败」占位 + [重试]；保存与操作失败按错误码统一 Toast（如 1000017401 环境名称重复、1000017403 数据源连接失败、1000017404 定时任务绑定）。
  - 空态：无环境时「暂无环境，点击新建」；搜索无结果「无匹配环境」+ [清除搜索]；右侧未选中时「暂无环境，点击「新建」创建第一个环境」。
  - 权限不足：无 `api-env:view` 时侧边导航不展示「环境管理」项；无 `api-env:edit` 时列表行内操作、[保存全部]、导入等入口不渲染或置灰。

---

## 3. 环境删除保护

- 删除前弹窗二次确认；环境被定时任务绑定时禁止删除（错误码 1000017404），需先在定时任务中解除绑定；环境不存在或不属于当前项目返回错误码 1000017405。

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-03 | 按前后端实现对齐环境列表/详情字段、连接测试与导入导出规则、CRUD 校验、权限口径、页面结构与状态分支及删除保护校验 |
