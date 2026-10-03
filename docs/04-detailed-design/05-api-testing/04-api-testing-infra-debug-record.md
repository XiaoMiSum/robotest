# 软件测试平台——调试记录

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 调试记录接口

### 1.1 通用说明

- 调试记录表 `api_debug_record` 的 DDL、索引与清理策略定义于《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.1.1 / 2.4；错误码 1000017013（API_DEBUG_RECORD_NOT_FOUND）登记于该文档 2.2；通用响应结构、分页与上下文约定见该文档 2.3——下文响应示例**仅展示 `data` 字段内容**，省略外层 `code` / `msg` 包裹。
- 上下文（`X-Active-Workspace` / `X-Active-Project`）经请求头传递（C4），不出现在 URL 与请求体中；服务端按「活动项目 + 当前登录用户」隔离记录。
- **权限码**：查询、重命名、删除、恢复均要求 `api-debug:view`（与前端「快速调试」菜单入口挂载的权限码一致）；保存为接口定义要求 `api-interface:edit`。
- 记录不存在、不属于当前项目，或保存为接口时记录不属于当前用户的，统一返回错误码 1000017013。
- 调试记录**没有独立的新增接口**：记录由执行接口在服务端执行成功后自动写入（见 1.2），前端只做查询、重命名、删除、恢复与保存为接口。

### 1.2 记录自动写入（执行接口）

- **路径**：`POST /api/project/debug/execute`（请求/响应报文详见 `docs/04-detailed-design/05-api-testing/11-quick-debug.md` 3.1.1）
- **权限**：`api-debug:view`
- **说明**：
  - 执行完成后将请求快照（方法、URL、请求头、Query 参数、请求体、处理器、环境、超时）与响应结果（状态码、响应头、响应体、耗时、大小、错误信息）异步写入 `api_debug_record`，响应携带本次记录 ID `debugRecordId`；落库失败仅记录日志，不影响执行结果返回。
  - 初始命名为「方法 + URL 路径末段」（如 `POST /api/auth/login`），用户可在历史记录中重命名。
  - 保留上限 `api-test.debug.record-limit`（默认 200 条），超出后按执行时间倒序淘汰该用户最旧记录；响应体按 `api-test.debug.max-response-body-chars`（默认 1MB）截断存储。

### 1.3 查询调试记录列表

- **路径**：`GET /api/project/debug-records?pageNo=1&pageSize=20&keyword=登录`
- **参数**：`pageNo` / `pageSize`（默认 20，最大 100，见 `docs/00-spec/20-contracts/01-api.md`）、`keyword`（可选，按记录名称或 URL 模糊匹配）。
- **范围与排序**：仅返回当前项目内属于当前用户的记录，按 `executedAt` 倒序。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "name": "POST /api/auth/login",
      "method": "POST",
      "url": "https://staging.example.com/api/auth/login",
      "status": "success",
      "responseStatus": 200,
      "durationMs": 230,
      "executedAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 15
}
```

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| id | string | 记录 ID（UUID） |
| name | string \| null | 记录名称，用户可重命名 |
| method | string | HTTP 方法（大写） |
| url | string | 请求 URL |
| status | string | 执行结果：`success` / `failed` / `error` |
| responseStatus | number \| null | 响应状态码，无响应时为 null |
| durationMs | number \| null | 执行耗时（毫秒） |
| executedAt | string | 执行时间（UTC，`Z` 格式） |

### 1.4 重命名调试记录

- **路径**：`PUT /api/project/debug-records/:id`
- **权限**：`api-debug:view`
- **请求体**：

```json
{ "name": "登录接口调试" }
```

- **校验**：`name` 必填，长度不超过 200。
- **说明**：仅更新 `name` 字段，不改动请求快照与响应结果。
- **响应**：`true`

### 1.5 删除调试记录

- **路径**：`DELETE /api/project/debug-records/:id`
- **权限**：`api-debug:view`
- **说明**：逻辑删除（`is_deleted`）。
- **响应**：`true`

### 1.6 恢复调试记录

- **路径**：`GET /api/project/debug-records/:id/restore`
- **权限**：`api-debug:view`
- **说明**：返回该次调试的完整请求快照与响应结果，前端据此**新建调试标签**并回填（不覆盖当前标签）。认证配置不单独入库，恢复时相关头按普通请求头还原。
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
    "body": { "type": "json", "content": { "username": "admin", "password": "123456" } },
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

> `request.body` 为 `{type, content}` 结构（落库时已扁平化，恢复时重新包装）；`response.body` 可解析为 JSON 时为结构化对象，否则为原始字符串；`createdAt` 为记录创建时间。

### 1.7 保存为接口定义

- **路径**：`POST /api/project/debug-records/:id/save-as-interface`
- **权限**：`api-interface:edit`
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
    "body": { "type": "json", "content": { "username": "admin" } },
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

- **校验**：`mode` 必填且仅支持 `create` / `attach`；`create` 时 `name`（≤200）与 `moduleId` 必填；`attach` 时 `interfaceId` 与 `changeVersion` 必填。
- **说明**：
  - 请求快照由前端从当前标签构建后经 `request` 传入（方法、URL、头、参数、请求体、认证）；`request` 缺省时回落到调试记录自身的快照。`responseExample` 有响应时由前端填充。
  - 服务端按环境 `baseUrl` 从 `url` 中提取接口 `path`；`attach` 按 `changeVersion` 乐观锁更新，冲突由接口管理模块返回版本冲突错误（1000017105，见 1.1 所指错误码登记表）。
  - 前端 [保存] 按钮依赖当前标签执行返回的 `debugRecordId`：未执行时置灰并提示「请先发送请求获取调试记录」；保存成功后弹出确认框询问是否前往接口管理查看。
- **响应**：`{ "interfaceId": "018f..." }`

### 1.8 历史记录视图（前端消费口径）

实现位于 `web/src/pages/project/api-testing/debug/DebugHistoryView.vue` 与 `web/src/composables/project/api-testing/debug/useDebugPage.ts`。

- **入口**：调试页页签条末尾固定「历史记录」页签（不可关闭），点击切换为全宽历史记录视图；`Ctrl+Shift+H` 快速切换。
- **筛选**：顶部搜索框以 `keyword` 走服务端过滤（输入防抖 300ms、支持清空），旁侧展示「共 N 条」。
- **分页与加载**：`pageSize=100`，滚动到底自动加载下一页（无分页器）；翻页结果按 `id` 去重合并；删除后从第 1 页重新加载；追加页失败时保留已加载内容，仅重试失败页。
- **展示**：按 `executedAt` 归入「今天 / 昨天 / 更早」三组；每行包含方法徽标（按方法着色）、响应状态码（`<400` 成功配色、`≥400` 失败配色、无响应时空值占位）、名称（缺省依次回退 URL、`(未命名)`）、URL、时间（`MM-DD HH:mm`，年份由分组隐含）、耗时（ms，缺值留空占位）与行内操作。
- **交互**：单击条目或「恢复」→ 调 1.6 恢复并新建标签；双击条目或「重命名」→ 行内输入框，回车/失焦提交（空名或未变更不提交）；「删除」→ 二次确认后调 1.5。
- **状态分支**：首屏加载为整屏遮罩；追加加载在列表尾部显示「加载中…」；请求失败在列表顶部展示错误条与「重试」按钮并弹出错误消息；无数据显示「暂无调试记录」。
- **权限**：无 `api-debug:view` 时不展示「快速调试」菜单入口，历史记录视图随之不可达。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐实现：补全重命名/恢复/保存为接口与执行写入接口、列表 keyword 筛选与字段口径、权限码与历史视图状态分支 |
