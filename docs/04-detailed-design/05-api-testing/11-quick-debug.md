# 软件测试平台——快速调试详细设计说明书

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**快速调试**进行详细设计，定义单请求调试（服务端执行）、cURL 命令解析、调试记录管理与保存为接口定义的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS《快速调试》分册与概要设计第 3.1 章对应模块：

- **单请求调试**：服务端执行；
- **cURL 命令解析**：cURL 命令粘贴解析，回填请求构造；
- **调试记录管理**：调试历史记录的自动保存、查询、删除、重命名与恢复；
- **保存为接口**：将服务端执行产出的请求快照保存为接口定义资产。

> 实施边界说明：本模块当前版本仅实现 `http` 协议（jdbc 取样器随测试场景模块提供）；「保存为接口定义」（3.1.3）复用接口管理模块的接口定义资产，前后端均已交付；「本地执行」依赖独立交付的本地代理客户端，当前页面仅提供服务端执行，无本地执行入口。

接口定义、导入等接口资产的管理详见《接口管理详细设计说明书总览》（`docs/04-detailed-design/05-api-testing/08-interface-management-overview.md`）。

所有接口测试接口的鉴权、上下文传递沿用平台既有约定（C4），详见《API 测试基础设施详细设计说明书总览》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.3；本模块各接口的权限码见 3.1。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/05-api-testing/03-api-srs-quick-debug.md`）
- 《概要设计说明书》（`docs/02-high-level-design/02-hld-overview.md`，3.1）
- 《API 测试基础设施详细设计说明书总览》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）
- 《调试记录》分册（`docs/04-detailed-design/05-api-testing/04-api-testing-infra-debug-record.md`）
- 《接口管理详细设计说明书总览》（`docs/04-detailed-design/05-api-testing/08-interface-management-overview.md`）
- 《快速调试交互设计》（`docs/05-interaction-design/05-api-testing/06-quick-debug-ui-page.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

---

## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 调试记录表（api_debug_record）

调试记录表 `api_debug_record` 定义于《API 测试基础设施详细设计说明书总览》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.1.1，本模块直接复用其 DDL 与索引，不重复定义。

调试记录的写入由 `POST /api/project/debug/execute` 服务端执行触发（详见 4.1 调试记录自动保存）。

### 2.2 错误码补充

快速调试模块使用的十位错误码均登记于《API 测试基础设施详细设计说明书总览》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.2，本模块涉及以下错误码：

| 错误码 | 常量名 | 说明 |
| --- | --- | --- |
| 1000017001 | API_EXECUTOR_BUSY | 执行引擎繁忙（并发队列满） |
| 1000017002 | API_EXEC_TIMEOUT | 执行超时（服务端护栏为 `timeoutMs + 5s`） |
| 1000017003 | API_FORMAT_CONVERT_FAILED | 协议/格式转换失败（`http` 以外的协议被拒绝） |
| 1000017013 | API_DEBUG_RECORD_NOT_FOUND | 调试记录不存在 |

---

## 3. 接口详细设计

### 3.1 快速调试接口

- 上下文（`X-Active-Workspace` / `X-Active-Project`）经请求头传递（C4），不出现在 URL 与请求体中；下文响应示例仅展示 `data` 字段内容，省略外层 `code` / `msg` 包裹。
- **权限码**：执行、列表查询、重命名、删除、恢复均要求 `api-debug:view`（与前端「快速调试」页签挂载的权限码一致）；保存为接口定义要求 `api-interface:edit`。

#### 3.1.1 执行调试请求（服务端）

- **路径**：`POST /api/project/debug/execute`
- **权限**：`api-debug:view`
- **请求体**（前端实际提交形态）：

```json
{
  "protocol": "http",
  "method": "POST",
  "url": "https://staging.example.com/api/auth/login",
  "headers": [
    { "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "params": [
    { "key": "from", "value": "web", "enabled": true }
  ],
  "body": {
    "type": "raw",
    "content": "{\"username\":\"admin\",\"password\":\"123456\"}"
  },
  "timeoutMs": 30000,
  "environmentId": "018f..."
}
```

- **说明**：
  - 服务端执行：由平台执行引擎发起请求，结果持久化为调试记录（自动保存，无需用户操作，详见 4.1）。
  - `url` 支持完整 URL（绝对 URL 在步骤级覆盖环境 `baseUrl`）或相对路径（映射为 `path`，与所选环境 HTTP 配置的 `baseUrl` 拼接）；`environmentId` 为执行引用的环境（相对路径拼接与 `${变量名}` 引用的来源），缺省时使用项目默认环境；前端在面板加载时缺省选中默认环境并随请求提交。
  - `body.type` 契约取值 `none` / `json` / `form` / `raw` / `binary`；当前调试前端仅提交 `form`（x-www-form-urlencoded，`content` 为键值对列表）与 `raw`（原始文本），无请求体时不传 `body`。
  - 认证配置与 Content-Type 由前端在提交前换算/注入为请求头（见 5.1），不作为独立字段提交。
  - `timeoutMs` 缺省 30000，服务端执行护栏为 `timeoutMs + 5s`；前端请求超时放宽为 120s 以容纳护栏。
  - 执行完成后自动将请求快照与响应结果保存为历史记录，保留最近 200 条（超出自动淘汰最旧记录，自动保存规则详见 4.1）。
- **响应**：

```json
{
  "debugRecordId": "018f...",
  "status": "success",
  "responseStatus": 200,
  "responseHeaders": { "Content-Type": "application/json" },
  "responseBody": { "code": 200, "data": { "token": "xxx" } },
  "durationMs": 230,
  "size": 2300
}
```

  - `status` 取值 `success` / `failed` / `error`；平台侧执行失败（超时、连接失败、队列满等）时 `status=error`、`responseStatus` 缺省，`errorMessage` 携带失败原因，前端响应区以 `ERROR` 徽标与错误横幅展示。

#### 3.1.2 从 cURL 导入

- **入口**：页签条右侧「导入 cURL」按钮打开粘贴弹窗，点击「解析并回填」触发。
- **说明**：cURL 命令解析在**前端**完成（`web/src/composables/project/api-testing/debug/curlParser.ts`），解析规则详见 4.2，无后端接口；解析结果回填当前激活标签的请求编辑器（method、URL、headers、body），不执行命令，查询串保留在 URL 中。
- **反馈**：解析成功关闭弹窗并提示「cURL 解析成功，已回填当前标签」；未找到 URL 等解析错误在弹窗内提示「cURL 命令中未找到请求 URL」。
- 兼容 Windows CMD 复制格式：`^` 续行符与 `^"` 转义引号在解析前归一化（详见 4.2）。

#### 3.1.3 保存调试记录为接口定义

- **路径**：`POST /api/project/debug-records/:id/save-as-interface`
- **权限**：`api-interface:edit`
- **路径参数**：`:id` 为当前标签执行/恢复产出的调试记录 ID（`debugRecordId`）。
- **请求体**（`mode=create` 新建 / `mode=attach` 归属已有接口）：

```json
{
  "mode": "create",
  "name": "用户登录",
  "moduleId": "018f...",
  "request": {
    "method": "POST",
    "url": "https://staging.example.com/api/auth/login",
    "headers": [{ "key": "Content-Type", "value": "application/json", "enabled": true }],
    "params": [],
    "body": { "type": "raw", "content": "{\"username\":\"admin\"}" },
    "auth": { "type": "none" }
  },
  "responseExample": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": { "code": 200, "msg": "success" }
  }
}
```

`mode=attach` 时改传 `interfaceId` 与 `changeVersion`（乐观锁）：

```json
{ "mode": "attach", "interfaceId": "018f...", "changeVersion": 3, "request": {} }
```

- **说明**：将请求快照保存为接口定义资产——`request` 由前端从当前标签表单构建（方法、URL、请求头、参数、请求体、认证），`responseExample` 在存在响应时由前端填充；`create` 必填 `name`（≤200）与 `moduleId`，`attach` 必填 `interfaceId` 与 `changeVersion`（版本冲突返回 1000017105）。接口定义的维护详见《接口定义管理》（`docs/04-detailed-design/05-api-testing/09-interface-management-definition.md`）第 1 章。
- **响应**：`{ "interfaceId": "018f..." }`

#### 3.1.4 查询调试记录列表

- **路径**：`GET /api/project/debug-records?pageNo=1&pageSize=20&keyword=登录`
- **权限**：`api-debug:view`
- **参数**：`pageNo` / `pageSize`（默认 20，最大 100）；`keyword` 可选，按记录名称或 URL 模糊匹配。
- **说明**：仅返回当前项目内属于当前用户的调试记录，按 `executedAt` 倒序。
- **响应**：详见《调试记录》分册（`docs/04-detailed-design/05-api-testing/04-api-testing-infra-debug-record.md`）1.3。

#### 3.1.5 删除调试记录

- **路径**：`DELETE /api/project/debug-records/:id`
- **权限**：`api-debug:view`
- **说明**：逻辑删除；记录不存在或不属于当前项目返回 1000017013。
- **响应**：`true`

#### 3.1.6 重命名调试记录

- **路径**：`PUT /api/project/debug-records/:id`
- **权限**：`api-debug:view`
- **请求体**：`{ "name": "用户登录调试" }`
- **说明**：`name` 必填且不超过 200；仅更新名称字段，不改动请求快照与响应结果（4.1 记录命名中「用户可手动重命名」的服务端支撑）。
- **响应**：`true`

#### 3.1.7 恢复调试记录到新标签

- **路径**：`GET /api/project/debug-records/:id/restore`
- **权限**：`api-debug:view`
- **说明**：返回该次调试记录的完整请求快照与响应结果，前端据此**新建调试标签**并回填请求构造区与响应区，同时切换到新标签（不覆盖当前标签）；认证配置不单独入库，恢复时相关头按普通请求头还原。
- **响应**：

```json
{
  "debugRecordId": "018f...",
  "request": {
    "protocol": "http",
    "method": "POST",
    "url": "https://staging.example.com/api/auth/login",
    "headers": [
      { "key": "Content-Type", "value": "application/json", "enabled": true }
    ],
    "body": {
      "type": "json",
      "content": { "username": "admin", "password": "123456" }
    },
    "params": []
  },
  "response": {
    "statusCode": 200,
    "headers": { "Content-Type": "application/json" },
    "body": { "code": 200, "msg": "success", "data": { "token": "..." } },
    "elapsed": 125,
    "size": 2300
  },
  "createdAt": "2026-08-17T10:30:00Z"
}
```

> `request.body` 为 `{type, content}` 结构（落库时已扁平化，恢复时重新包装）；`response.body` 可解析为 JSON 时为结构化对象，否则为原始字符串。报文字段口径详见《调试记录》分册（`docs/04-detailed-design/05-api-testing/04-api-testing-infra-debug-record.md`）1.6。

---

## 4. 业务逻辑设计

### 4.1 调试记录自动保存

每次服务端执行完成后，自动将请求快照与响应结果保存为历史记录：

- **触发时机**：`POST /api/project/debug/execute` 同步返回前由后台持久化线程异步写入 `api_debug_record` 表；落库失败仅记录日志，不影响执行响应返回。
- **保存内容**：请求快照（方法、URL、请求头、Query 参数、请求体、处理器、环境、超时）+ 响应结果（状态、状态码、响应头、响应体、耗时、大小、错误信息）。
- **记录上限**：`api-test.debug.record-limit`（默认 200 条），超出后按 `executedAt` 倒序淘汰该用户最旧记录；响应体按 `api-test.debug.max-response-body-chars`（默认 1MB）截断存储。
- **恢复逻辑**：前端调用 `GET /api/project/debug-records/:id/restore` 获取完整快照，**新建调试标签**回填请求构造区并展示响应（不覆盖当前标签）。
- **记录命名**：自动保存的记录初始名称为「方法 + URL 路径（去除查询串）」（如 `POST /api/auth/login`，无路径时回退「请求」），用户可在历史记录视图中手动重命名。

### 4.2 cURL 解析规则

> 实现位置：**前端**（`web/src/composables/project/api-testing/debug/curlParser.ts`），仅文本解析不执行命令；入口与反馈见 3.1.2，后端无对应解析逻辑。

cURL 命令解析支持以下要素提取：

| cURL 参数 | 前端映射 |
| --------- | -------- |
| `-X` / `--request` | method |
| 位置参数（含 `://` 或 `/` 开头的首个参数） | url |
| `-H` / `--header` | headers（数组，同名后者覆盖前者） |
| `-d` / `--data` / `--data-raw` / `--data-binary` / `--data-urlencode` | body.content（type=json，多个以 `&` 拼接） |
| `-F` / `--form` | body.content（type=form，`@文件` 引用丢弃） |
| `-b` / `--cookie` | headers（Cookie） |

不支持的参数（如 `--proxy`、`--cert`、`--max-time`）连同其取值一并忽略，不报错；未知布尔参数（`-k`、`-L`、`-s` 等）静默跳过。

- **方法缺省**：存在请求体（`-d`/`-F`）且未指定 `-X` 时默认 `POST`，否则默认 `GET`。
- **负载降级**：`-d` 值无法解析为 JSON 时，`body.type` 降级为 `raw` 按原始文本回填。
- **未找到 URL**：抛错「cURL 命令中未找到请求 URL」，由弹窗内提示。
- **Windows CMD 兼容**：仅当命令包含 `^` 续行（`^` + 换行）或 `^"` 转义引号标记时才做归一化——`^` + 换行折叠为空格、`^X` 还原为字面 `X`（`^"` → `"`、`^^` → `^`），避免误伤含字面 `^` 的 bash 命令。

---

## 5. 前端设计

> 实现位置：`web/src/pages/project/api-testing/debug/`（DebugPage、DebugRequestPanel、DebugResponseViewer、DebugHistoryView、SaveInterfaceDialog）与 `web/src/composables/project/api-testing/debug/`。

### 5.1 调试面板

- **入口与权限**：接口测试页内「快速调试」子页（路由 `/workspace/projects/api-testing?tab=debug`），子页签挂载权限码 `api-debug:view`，无权限时不展示该页签。
- **布局**：页面主体上下分栏——请求构造区（上）与响应查看区（下），中间分割线可拖拽调节（请求区高度 20%–80%，默认 50%）；切到「历史记录」页签时主内容区变为全宽历史视图。
- **调试标签栏**：页面顶部横向页签条，每个标签代表一个独立调试会话；标签显示方法徽标（按方法着色）+ 名称 + 关闭按钮（鼠标中键点击亦可关闭）；会话页签之后固定一个不可关闭的「历史记录」页签，其后为 `[+]` 新建按钮（达到 10 个上限时禁用）；页签条最右侧为「导入 cURL」按钮。点击「历史记录」页签切换为全宽历史记录视图。
- **标签状态管理**：初始名称「新建请求」，首次执行后自动以「方法 + URL 路径」命名（如 `POST /api/auth/login`，去除查询串）；双击标签进入行内编辑态（回车或失焦提交，空名不生效）；关闭标签不二次确认，直接关闭并切换到相邻标签，关闭最后一个标签后自动新建空白标签。
- **环境选择器**：置于参数页签行最右侧（非 URL 栏下方），页面级共享（切换标签不重置），面板加载时缺省预选默认环境（无默认环境则取第一条，亦可不选）；相对 URL 按所选环境 `baseUrl` 拼接，`${变量名}` 引用取自该环境变量；环境列表加载失败不阻塞调试。
- **请求编辑器**：方法下拉（GET/POST/PUT/PATCH/DELETE/OPTIONS/HEAD/CONNECT）+ URL 输入（回车即发送；无 `http/https` 前缀且非 `/` 开头时自动补 `http://`）+ 参数页签（Params/Body/Headers/Auth，缺省激活 Body，Params/Headers 页签显示条目数徽标）。URL 栏右侧为 **[发送]**（服务端执行，执行中 loading，URL 为空时禁用）与 **[保存]**（保存为接口定义）；URL 栏下方常驻快捷键提示行。
- **快捷键**：`⌘/Ctrl+Enter` 发送、`⌘/Ctrl+T` 新建标签、`⌘/Ctrl+W` 关闭当前标签、`⌘/Ctrl+Shift+H` 切换「历史记录」页签（已在历史视图时切回第一个调试标签）。
- **键值对编辑器（Params/Headers/请求体）**：默认呈现一行空行，末行填入内容后自动追加新的空行，删除行后同样自动补足空行；首列为启用勾选（不设表头文字），Params/Headers 另有描述列（提交执行时剥离）；Headers 的 Key 支持常用头名下拉选择与自定义输入（`allow-create`）。提交执行时丢弃空键行与停用行。
- **Ryze 映射**：服务端 `DebugRyzeConverter` 按提交内容生成 Ryze 取样器配置——Params 页签 → `query`（URL 查询参数）；`x-www-form-urlencoded` 请求体 → `data`（表单 `k=v` 语义）；raw 请求体 → `body`（原始文本）。绝对 URL 在步骤级覆盖 `base_url`，相对路径映射为 `path` 并经环境 HTTP 配置继承 `baseUrl`。
- **请求体类型行**：`none` / `x-www-form-urlencoded` / `raw`。
  - `none`：提示「该请求不携带请求体」，提交不传 `body`。
  - `x-www-form-urlencoded`：键值对编辑器，提交时映射为后端 `body.type=form`、`content` 为键值对列表（服务端转换为 Ryze `data`）；请求头无 `Content-Type` 时注入 `application/x-www-form-urlencoded`。
  - `raw`：文本编辑器（支持变量引用）；子类型选择器（Text/JSON/XML/HTML/JavaScript，缺省 JSON）置于类型行右侧同一行，选择 JSON 时提供格式化按钮（非法 JSON 提示无法格式化）；按所选子类型自动注入对应 `Content-Type`（`application/json`、`application/xml`、`text/html`、`text/javascript`，`text` 不注入）。`raw` 一律按原始文本提交（`body.type=raw`），前端不做 JSON 对象化。
  - 切换类型/子类型时同步 Headers 中的 `Content-Type` 行（`none` 移除该行），提交执行时请求头已含 `Content-Type` 则不重复注入；切换类型保留各类型已填内容（`bodies` 分槽缓存，`bodyType` 记录当前激活类型）。
- **认证页签**：类型下拉置顶，支持 No Auth / Bearer Token / API Key / Basic Auth / Digest Auth（Digest 暂停用置灰）。认证不随调试记录单独持久化——提交执行时由前端换算为请求头：Bearer → `Authorization: Bearer <token>`，API Key → 自定义请求头（默认键 `X-API-Key`，键名可编辑），Basic → `Authorization: Basic base64(user:pass)`；请求头已存在同名手工头时不注入（手工优先）；密码、Token 脱敏展示。保存为接口时 `auth` 随 `request` 快照一并提交（3.1.3）；从历史恢复时认证头与手工头无法区分，降级还原为普通请求头。
- **响应查看器（对齐 Postman）**：未执行时为空态「点击「发送」查看响应结果」；有响应时顶部状态行同排展示状态徽标（2xx 绿 / 3xx 蓝 / 4xx 橙 / 5xx 红，悬停显示状态码英文名与中文描述）、耗时（Time）与大小（Size，按 B/KB/MB 换算）；平台侧执行失败时徽标显示红色 `ERROR`，下方错误横幅完整展示 `errorMessage`。顶部页签 [Body]/[Headers]/[Cookies]（Headers、Cookies 显示条目数）：
  - Body 内 Pretty/Raw/Preview 三态 + 格式类型下拉（Text/JSON/XML/HTML/JavaScript，按 `Content-Type` 自动检测，可手动切换）；JSON 在 Pretty 态语法高亮且节点可折叠（存在搜索词时回退为文本高亮视图），Raw 展示原始文本，Preview 对 HTML 用 `iframe srcdoc`（`sandbox="allow-scripts"`）渲染、其余等同 Raw；
  - Headers 为响应头键值表（空态「无响应头」）；Cookies 从响应头 `Set-Cookie` 解析为名称/值/属性三列表格（客户端解析，无内存态 cookie 语义，仅展示，空态「响应头中无 Set-Cookie」）；
  - 搜索与复制：`Ctrl+F` 展开并聚焦搜索框（再次按下收起并清空），`Ctrl+G` 下一个 / `Ctrl+Shift+G` 上一个，输入框回车跳下一个，`Esc` 收起；全部匹配高亮 + `n/m` 计数（无匹配显示「无匹配」）；[复制] 按钮复制响应体文本，成功打勾反馈。
- **保存为接口**：URL 栏 [保存] 按钮，依赖当前标签执行/恢复产出的调试记录 ID（`debugRecordId`）——未执行时置灰并提示「请先发送请求获取调试记录」。点击打开保存弹窗（《快速调试交互设计》1.6）：归属方式单选「新建接口 / 归属已有接口定义」；新建需填接口名称（必填）与所属模块（模块树目录节点，加载失败可重试）；归属已有提供可搜索的接口选择器（首批 50 条，`方法 path — 名称` 展示，加载失败可重试），提交携带 `changeVersion` 乐观锁。提交调用 `POST /api/project/debug-records/:id/save-as-interface`（3.1.3，`mode=create|attach`），保存中按钮 loading；成功后弹出确认框「已保存为接口定义，是否前往查看？」，确认则切换到「接口管理」子页并打开该接口编辑器。
- **历史记录视图**：点击「历史记录」页签后主内容区切换为全宽视图——顶部搜索框（`keyword` 匹配名称或 URL，防抖 300ms 走服务端过滤）+「共 N 条」；列表按「今天 / 昨天 / 更早」分组，每行展示方法徽标、状态码（`<400` 成功配色、`≥400` 失败配色、无响应空值占位）、名称（缺省依次回退 URL、`(未命名)`）、URL、时间（`MM-DD HH:mm`，年份由分组隐含）、耗时与行内操作。单击条目或「恢复」→ 调 3.1.7 **新建调试标签**回填并切换；双击条目或「重命名」→ 行内改名（回车/失焦提交）；「删除」→ 二次确认后调 3.1.5。滚动到底自动加载下一页（100 条/页，按 `id` 去重合并），删除后从头刷新；首屏加载为整屏遮罩，追加加载在列表尾部显示「加载中…」，失败在列表顶部展示错误条并可「重试」，无数据显示「暂无调试记录」。
- **cURL 导入**：页签条右侧「导入 cURL」按钮打开粘贴弹窗，由前端（`curlParser`，见 4.2）解析后回填当前激活标签的请求编辑器，不执行命令（入口与反馈见 3.1.2）。
- **暂缓项**：「本地执行」依赖本地代理客户端（实施分期第三梯队），当前无入口，随对应交付物启用。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐实现：校正保存为接口/恢复等接口路径与新建标签回填口径，补全权限码、执行请求响应字段、cURL 弹窗入口、请求体与认证换算、响应查看器与历史视图状态分支 |

---

**文档结束**
