# 软件测试平台——规则管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. Mock 管理

> 上下文标识（`X-Active-Workspace` / `X-Active-Project`）经请求头传递，不出现在 URL 与请求体中；表结构与错误码总表见 `docs/04-detailed-design/05-api-testing/21-mock-service-overview.md`。

### 1.1 查询 Mock 列表

- **路径**：`GET /api/project/mocks?interfaceId=&search=&enabled=&pageNo=1&pageSize=20`
- **权限**：`api-mock:view`
- **参数说明**：`interfaceId` 按关联接口过滤；`search` 按名称/路径模糊搜索；`enabled` 按启用状态过滤（true/false，可选，不传返回全部）；`pageNo` / `pageSize` 缺省 1 / 20。
- **排序**：`priority` 升序，其次创建时间倒序。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "name": "登录成功 Mock",
      "interfaceId": "018e...",
      "method": "POST",
      "path": "/api/auth/login",
      "priority": 1,
      "enabled": true,
      "followApi": false,
      "responseStatus": 200,
      "hitCount": 156,
      "lastHitAt": "2026-08-17T10:30:00Z",
      "updatedAt": "2026-08-16T15:00:00Z"
    }
  ],
  "total": 8
}
```

### 1.2 查询 Mock 详情

- **路径**：`GET /api/project/mocks/:id`
- **权限**：`api-mock:view`
- **响应**：包含完整匹配规则、响应定义、关联接口信息与同组规则条数。

```json
{
  "id": "018f...",
  "name": "登录成功 Mock",
  "interfaceId": "018e...",
  "interfaceName": "用户登录",
  "method": "POST",
  "path": "/api/auth/login",
  "priority": 1,
  "description": "模拟登录成功响应",
  "matchRules": [
    { "type": "header", "name": "Content-Type", "value": "application/json" },
    { "type": "body", "name": "$.username", "value": "admin" }
  ],
  "enabled": true,
  "followApi": false,
  "responseStatus": 200,
  "responseHeaders": { "Content-Type": "application/json" },
  "responseBodyType": "json",
  "responseBody": "{\n  \"code\": 200,\n  \"data\": {\n    \"token\": \"mock-token-${uuid()}\"\n  }\n}",
  "delayMs": 0,
  "hitCount": 156,
  "lastHitAt": "2026-08-17T10:30:00Z",
  "groupSize": 3
}
```

> `groupSize` 为同路径同方法的规则总数，前端用于编辑抽屉中「同组规则」提示（详见 2.4）。

### 1.3 创建 Mock

- **路径**：`POST /api/project/mocks`
- **权限**：`api-mock:edit`
- **请求体**：同 1.2 字段，不含 `id`、`interfaceName`、`hitCount`、`lastHitAt`、`groupSize`；其中 `interfaceId` 与 `priority` 可选。
- **校验规则**：
  - `name` 必填，长度不超过 200；`description` 长度不超过 500；
  - `method` 必填；`path` 必填且以 `/` 开头，长度不超过 500；
  - `enabled` 必填；`responseStatus` 必填且为合法 HTTP 状态码（100–599）；`delayMs` 不得为负数；
  - 同项目下同路径同方法已有启用中的 Mock 时返回错误码 1000017202（`API_MOCK_ADDR_CONFLICT`，仅 `enabled = true` 时校验）；
  - `priority` 缺省时取同路径同方法组内最大值 + 1。
- **提交约定**：`description` 空串提交为 `null`；`matchRules` 中 `name` 为空的行、`responseHeaders` 中 Key 为空的行由前端过滤后提交。
- **响应**：`{ "id": "018f..." }`

### 1.4 从接口定义创建 Mock

- **路径**：`POST /api/project/mocks/from-interface/:interfaceId`
- **权限**：`api-mock:edit`
- **说明**：继承接口定义的 `method` 与 `path` 并设置 `interface_id` 关联接口（请求体中的 `method`、`path` 同名字段不生效）；地址冲突校验按继承后的方法与路径执行；接口不存在或跨项目时返回错误码 1000017101（`API_INTERFACE_NOT_FOUND`）。
- **前端流程**：编辑抽屉以 `interfaceId` 打开（新建时预选关联接口）并在用户保存时调用本接口；Mock 列表页以 `interfaceId` 属性承载预选接口，接口测试页以子页方式渲染列表时未传入该属性，页面内常规新建与复制走 1.3。
- **响应**：`{ "id": "018f..." }`

### 1.5 更新 Mock

- **路径**：`PUT /api/project/mocks/:id`
- **权限**：`api-mock:edit`
- **请求体**：同 1.3。
- **校验**：同 1.3 创建校验规则；Mock 不存在或跨项目时返回错误码 1000017201（`API_MOCK_NOT_FOUND`）；仅当 `method` / `path` 变更或 `enabled = true` 时执行地址冲突校验。按请求字段构建更新载体，仅更新本次提交的字段。

### 1.6 启停 Mock

- **路径**：`PATCH /api/project/mocks/:id/toggle`
- **权限**：`api-mock:edit`
- **请求体**：`{ "enabled": false }`
- **说明**：即时生效，不重启服务；仅更新 `enabled` 字段；启用时执行地址冲突校验，冲突返回错误码 1000017202；Mock 不存在或跨项目时返回错误码 1000017201（`API_MOCK_NOT_FOUND`）。
- **响应**：`true`

### 1.7 删除 Mock

- **路径**：`DELETE /api/project/mocks/:id`
- **权限**：`api-mock:edit`
- **说明**：Mock 不存在或跨项目时返回错误码 1000017201（`API_MOCK_NOT_FOUND`）。
- **响应**：`true`

### 1.8 重置命中统计

- **路径**：`POST /api/project/mocks/:id/reset-hit-count`
- **说明**：将 `hit_count` 归零、`last_hit_at` 置空，即时生效。
- **响应**：`true`

### 1.9 查询 Mock 地址

- **路径**：`GET /api/project/mocks/:id/address`
- **说明**：返回 Mock 的完整访问地址（含平台 Mock 域名）与访问该 Mock 需携带的请求头；地址可免登录直接访问。
- **响应**：

```json
{
  "mockUrl": "https://mock.robotest.example.com/api/auth/login",
  "method": "POST",
  "headers": {}
}
```

### 1.10 复制 Mock

- **说明**：复制无独立接口，由前端编排完成：行内 [复制] → 打开编辑抽屉并以源规则全部配置预填（标题「复制 Mock」，名称在原名后追加「 - 副本」，`enabled` 默认停用以避免与源规则地址冲突）→ 用户确认保存时按 1.3 调用创建接口生成新规则。
- **优先级**：请求体不携带 `priority`，由后端取同路径同方法组内最大值 + 1。

### 1.11 批量启停 Mock

- **路径**：`POST /api/project/mocks/batch-toggle`
- **权限**：`api-mock:edit`
- **请求体**：

```json
{
  "ids": ["018f...", "018g..."],
  "enabled": false
}
```

- **响应**：

```json
{
  "updatedCount": 2
}
```

- **说明**：批量启用/停用，逐条处理，即时生效、不重启服务；规则不存在或跨项目、以及启用时同组已有启用中规则的条目跳过，仅计入 `updatedCount`。

---

## 2. Mock 管理页

### 2.1 入口与权限

- **入口**：接口测试页左侧菜单「Mock 服务」，菜单项挂权限码 `api-mock:view`；子页切换仅写入 `?tab=mocks`，不新增路由。
- **权限口径**：前端仅在菜单项控权，列表与抽屉内按钮不再单独挂权限码；后端按接口校验——列表、详情、调试为 `api-mock:view`，创建、从接口创建、更新、启停、批量启停、删除为 `api-mock:edit`。
- **无权限分支**：持任一 `api-*:view` 权限即展示接口测试模块入口；模块内子菜单按各自 `view` 权限过滤；全部子模块均无权限时页面展示「暂无可用功能模块」空态。

### 2.2 列表与筛选

- **工具栏左侧**：关键字输入框（placeholder「请输入名称或路径」，回车或 [查询] 触发）、状态下拉（已启用 / 已停用，可清空，变更即查询）、[查询]、[重置]（清空关键字与状态筛选并回到第 1 页）。
- **工具栏右侧**：[新建 Mock]；勾选行后出现 [批量启用 (n)]、[批量停用 (n)]。
- **表格列**：多选框、名称、方法（按方法着色的标签）、路径、状态码、优先级、启停开关、命中次数、最后命中（无值显示 `-`，按浏览器时区格式化）、操作。
- **操作列**：编辑、调试、地址、复制，以及「更多」下拉（重置命中、删除）。
- **分页**：`total, sizes, prev, pager, next`，每页可选 10 / 20 / 50，默认 20；关联接口属性变化时回到第 1 页重新加载。
- **筛选说明**：页面筛选项为关键字与启用状态，`interfaceId` 由页面属性传入（不作为可见筛选控件）；参数与响应见 1.1。

### 2.3 状态分支

| 状态 | 表现 |
| ---- | ---- |
| 加载中 | 卡片级 loading 遮罩 |
| 加载失败 | 顶部错误条展示错误信息并提供 [重试]，同时弹出错误提示；无数据时隐藏表格与分页，已有数据时表格保留展示 |
| 空数据 | 表格默认空态 |
| 启停失败 | 重新拉取列表以恢复开关实际状态 |
| 批量启停结果 | 提示成功更新条数并清空勾选后刷新列表 |

### 2.4 规则编辑抽屉（MockEditorDrawer）

抽屉宽 640px，点击遮罩不关闭；标题按模式区分：新建 Mock / 编辑 Mock / 复制 Mock；分为「基本信息」「匹配条件」「响应定义」三个分区。

**基本信息**

| 字段 | 控件 | 约束与说明 |
| ---- | ---- | ---------- |
| Mock 名称 | 单行输入 | 必填，≤200 字符，保存时前端校验非空并提示「请输入 Mock 名称」 |
| 描述 | 多行输入 | ≤500 字符，空串提交 `null` |
| 请求方法 | 下拉 | GET / POST / PUT / PATCH / DELETE / HEAD / OPTIONS，默认 GET |
| 请求路径 | 单行输入 | 必填，≤500 字符，前端校验非空并提示「请输入请求路径」；`/` 开头由后端校验 |
| 启用 | 开关 | 默认启用；复制模式默认停用 |
| 跟随 API | 开关 | 默认关闭，提示「Mock 未配置响应时使用关联接口的响应示例」 |
| 同组规则 | 只读标签 | 仅编辑模式且 `groupSize > 1` 时显示，展示同路径同方法的规则条数与优先级排序说明 |

**匹配条件**

- 默认 1 行，可 [添加匹配条件] 追加空行；每行 = 类型下拉 + 名称 / JSONPath + 值 / 正则 + 删除按钮。
- 类型可选：请求头（header）、Query 参数（param）、请求体 (JSONPath)（body）。
- 仅剩 1 行时删除按钮禁用；保存时剔除 `name` 为空的行。

**响应定义**

| 字段 | 控件 | 约束与说明 |
| ---- | ---- | ---------- |
| 状态码 | 数字输入 | 100–599，默认 200 |
| 响应体类型 | 下拉 | JSON / Text / XML / Binary，默认 JSON |
| 延迟 (ms) | 数字输入 | 0–60000，默认 0 |
| 响应头 | 键值对行 | 可增删，默认预填 `Content-Type: application/json`；保存时剔除 Key 为空的行 |
| 响应体 | 多行文本域（等宽字体） | 默认空，占位提示 `{"code": 200, "data": {"token": "mock-${uuid()}"}}`；为纯文本输入，无格式化按钮 |

**保存与取消**：校验通过后提交，成功则关闭抽屉并刷新列表（提示「已创建」/「已更新」）；保存分支为——编辑（携带 `mockId`）→ 1.5，携带 `interfaceId` → 1.4，其余（含复制模式）→ 1.3；[取消] 关闭抽屉不提交。

### 2.5 Mock 地址弹窗（MockAddressDialog）

- 行内 [地址] → 调用 1.9 获取地址后打开 520px 弹窗。
- 弹窗内容：说明文案「以下地址可免登录直接访问 Mock 响应」、方法标签 + 完整访问地址；响应含请求头时展示「需要携带的请求头」键值列表。
- 底部操作：[关闭] 关闭弹窗；[复制地址] 写入剪贴板，成功提示「已复制到剪贴板」，失败提示「复制失败，请手动复制」。

### 2.6 复制、删除与命中重置

- **复制**：行内 [复制] → 按 1.10 预填编辑抽屉，用户调整后保存生成新规则。
- **删除**：[更多] → 删除 → 二次确认（警告类型，文案「确定删除 Mock「名称」？」）→ 成功提示「已删除」并刷新列表。
- **重置命中**：[更多] → 重置命中 → 调用 1.8，成功后本地将命中次数置 0、最后命中置空并提示「已重置」。

### 2.7 Mock 调试面板

- 行内 [调试] 打开 560px 抽屉：输入请求头（JSON 或 `Header: Value` 逐行格式）与请求体（JSON），[发送调试请求] 后展示状态码标签、耗时、响应头与响应体。
- 接口定义与交互细节见 `docs/04-detailed-design/05-api-testing/23-mock-service-debug.md`。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐前端实现：补全列表/详情字段与接口权限，修正复制与批量启停的接口口径及响应示例，重写管理页列表筛选、编辑抽屉字段校验、地址弹窗与状态分支章节 |
