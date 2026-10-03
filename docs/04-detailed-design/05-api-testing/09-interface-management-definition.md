# 软件测试平台——接口定义管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 接口定义管理

> 前端统一经 `web/src/services/project/api-testing/interface.ts` 调用；当前项目上下文经请求头传递，不出现在 URL。响应示例仅展示 `data` 字段内容。
> 权限口径由服务端 `@PreAuthorize` 强制：查询类接口（含导入预览）`api-interface:view`，新增/更新/移动/状态/关注/导入执行 `api-interface:edit`，删除 `api-interface:delete`；前端页面不做按钮级权限隐藏。

### 1.1 查询接口列表

- **路径**：`GET /api/project/interfaces?moduleId=&search=&pageNo=1&pageSize=20`
- **权限**：`api-interface:view`
- **筛选参数**：`moduleId`（可选，模块 ID）、`search`（可选，模糊匹配名称/路径）、`status`（可选，enabled / disabled）、`view`（可选，视图切换：followed=我关注的 / created=我创建的 / all=全部，缺省 all；followed 按当前用户关注关系过滤，created 按 `created_by` 过滤）。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "name": "用户登录",
      "protocol": "http",
      "method": "POST",
      "path": "/api/auth/login",
      "moduleId": "018e...",
      "status": "enabled",
      "referenceCount": 3,
      "changeVersion": 2,
      "followed": true,
      "updatedAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 25
}
```

### 1.2 查询接口详情

- **路径**：`GET /api/project/interfaces/:id`
- **权限**：`api-interface:view`
- **响应**：包含完整请求参数模型、验证器/提取器定义、响应示例。

```json
{
  "id": "018f...",
  "name": "用户登录",
  "protocol": "http",
  "method": "POST",
  "path": "/api/auth/login",
  "description": "用户登录接口",
  "moduleId": "018e...",
  "headers": [
    { "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "body": {
    "type": "json",
    "content": { "username": "", "password": "" }
  },
  "params": [],
  "restParams": [],
  "auth": { "type": "none" },
  "status": "enabled",
  "changeVersion": 1,
  "validators": [],
  "extractors": [],
  "responseExample": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": { "code": 200, "data": { "token": "xxx" } }
  },
  "referenceCount": 3,
  "followed": true,
  "createdAt": "2026-08-17T10:00:00Z",
  "updatedAt": "2026-08-17T10:30:00Z"
}
```

- **说明**：`auth.type` 取值 `none` / `bearer` / `apiKey` / `basic`；`responseExample` 为 `{status, headers, body}` 结构，编辑器只编辑 `body` 与 `headers`（JSON 文本），`status` 缺省 200 并在保存时原样保留；`validators` / `extractors` 为 `[{..}]` 数组，仅作定义存储。

### 1.3 创建接口定义

- **路径**：`POST /api/project/interfaces`
- **权限**：`api-interface:edit`
- **请求体**：

```json
{
  "name": "用户登录",
  "protocol": "http",
  "method": "POST",
  "path": "/api/auth/login",
  "moduleId": "018f...",
  "description": "用户登录接口",
  "headers": [
    { "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "body": {
    "type": "json",
    "content": { "username": "", "password": "" }
  },
  "params": [],
  "auth": { "type": "none" },
  "status": "enabled",
  "validators": [],
  "extractors": [],
  "responseExample": null
}
```

- **响应**：`{ "id": "018f..." }`
- **校验**：
  - `name` 必填且不超过 200 字符；同一模块内名称唯一，重复返回错误码 1000017102（`API_INTERFACE_NAME_EXISTS`）。
  - 前端：名称为空提示「请填写接口名称」并不提交；请求体为 raw + json 时解析失败提示「JSON 请求体格式非法，请修正后再保存」；响应示例 Body / Headers 非法 JSON 时提示「响应体 / 响应头 JSON 格式非法，请修正后再保存」。以上校验失败均不发起请求。
  - 请求体 `type` 取值：`json`（raw+json 子类型）、`raw`（raw 其余子类型）、`form`（x-www-form-urlencoded）、缺省不携带请求体。
- **说明**：当前版本仅支持 http 协议，请求体 `protocol` 缺省为 `http`；jdbc 协议随测试场景模块（梯队三）开放。空键行、未勾选的键值行不随请求提交。创建成功提示「接口已创建」并关闭当前编辑 Tab 返回列表。

### 1.4 更新接口定义

- **路径**：`PUT /api/project/interfaces/:id`
- **权限**：`api-interface:edit`
- **请求体**：同 1.3，另携带 `changeVersion`（当前版本号）。
- **校验**：`changeVersion` 与库中当前版本不一致时返回错误码 1000017105（`API_INTERFACE_VERSION_CONFLICT`）；保存成功后版本号递增，并写入一条变更历史记录（见 1.13）。
- **前端行为**：保存中按钮 loading；成功提示「已保存」并重新拉取详情刷新表单与版本号；识别到版本冲突时弹出确认框「接口已被他人修改，是否加载最新版本（将丢弃当前未保存的编辑）？」，确认后重新加载详情，取消则保留当前未保存的编辑。

> **乐观锁口径**：框架统一 Result 响应封装（业务错误码 ≠200），不使用 HTTP 状态码表达冲突；1000017105 由前端按错误码识别。

### 1.5 删除接口定义

- **路径**：`DELETE /api/project/interfaces/:id`
- **权限**：`api-interface:delete`
- **校验**：若接口被场景或 Mock 引用（`referenceCount > 0`），返回错误码 1000017103（`API_INTERFACE_REFERENCED`）。
- **前端行为**：删除前二次确认「删除接口「××」？删除后不可恢复。」；成功提示「已删除」，当前页删空且非首页时自动回退一页。

### 1.6 复制接口定义

- **入口**：列表行内「更多 → 复制」。
- **说明**：复制为前端预填行为，不提供专用后端接口：打开新建态编辑器 Tab（`?tab=interfaces&action=create&copyFrom=<id>`）并回填源接口全部定义，名称默认「原名称（副本）」且可修改，由用户确认后走 1.3 创建接口落库，产生与原接口无关联的独立副本。源接口预填失败时回退为空白新建态，显示错误条与「重试」。

### 1.7 查询引用关系

- **现状**：当前版本未提供该查询接口（服务端无对应路由，前端无调用入口）。
- **引用情况的现有口径**：仅通过 1.1 列表行的 `referenceCount`（「引用」列）展示；引用数大于 0 时由 1.5 / 1.10 的删除校验拦截。

### 1.8 查询引用场景

- **现状**：当前版本未提供该查询接口（服务端无对应路由，前端无调用入口）。
- **说明**：场景对本接口的引用仅在删除 / 批量删除时以错误码 1000017103 拦截体现，不提供引用方清单查询。

### 1.9 批量移动接口

- **路径**：`PUT /api/project/interfaces/batch/move`
- **权限**：`api-interface:edit`
- **请求体**：

```json
{
  "ids": ["018f...", "018g..."],
  "moduleId": "018e..."
}
```

- **前端行为**：勾选行后出现「批量移动」按钮，弹出「批量移动到模块」对话框（模块树选择）；未选择目标模块时提示「请选择目标模块」，成功提示「已移动 N 个接口」并刷新列表。

### 1.10 批量删除接口

- **路径**：`DELETE /api/project/interfaces/batch`
- **权限**：`api-interface:delete`
- **请求体**：

```json
{
  "ids": ["018f...", "018g..."]
}
```

- **校验**：所选接口中存在被场景或 Mock 引用（`referenceCount > 0`）时整体拒绝，返回错误码 1000017103（`API_INTERFACE_REFERENCED`）。
- **前端行为**：确认框「删除选中的 N 个接口？任一被场景引用将整体拒绝。」；成功后清空选择并刷新列表，删空且非首页时自动回退一页。

### 1.11 启用/停用接口

- **路径**：`PUT /api/project/interfaces/:id/status`
- **权限**：`api-interface:edit`
- **请求体**：`{ "status": "enabled" }`（enabled / disabled）
- **前端行为**：列表「状态」列开关即时切换，成功后列表状态与筛选立即生效；失败回滚开关原值并提示错误信息。

### 1.12 关注/取消关注接口

- **路径**：`POST /api/project/interfaces/:id/follow`（关注）、`DELETE /api/project/interfaces/:id/follow`（取消关注）
- **权限**：`api-interface:edit`
- **说明**：关注关系按用户记录（`api_interface_follow`），列表页「我关注的」视图按关注关系过滤。
- **前端行为**：列表首列星标图标切换（已关注实心高亮），行内即时更新，失败提示错误信息并保持原状态。

### 1.13 查询变更历史

- **路径**：`GET /api/project/interfaces/:id/change-logs?pageNo=1&pageSize=20`
- **权限**：`api-interface:view`
- **说明**：创建、更新（摘要为字段差异）、导入创建/导入覆盖更新各写入一条记录；复制经 1.3 创建接口落库，记为创建；状态切换、关注、移动、删除不写入记录。按 `change_version` 倒序分页。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "changeVersion": 3,
      "action": "update",
      "summary": "修改请求路径与默认请求头",
      "operatorId": "018c...",
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 1
}
```

- **action 取值**：`create`（创建）、`update`（更新）、`import`（导入创建/导入覆盖更新）。
- **前端现状**：服务调用已封装（`fetchInterfaceChangeLogs`），但变更历史弹窗组件当前未接入页面，列表与编辑器暂无查看入口。


## 2. 接口模块树组件

列表页左侧复用通用模块树组件（`web/src/components/project/ProjectModuleTree.vue`，`asset-type="interface"`，筛选模式即只有目录节点），支持：

- 顶部搜索框：按目录名称模糊过滤，命中节点自动展开其祖先链，清空关键字还原。
- 拖拽排序与移动：同级前后排序、跨级移动均可，只能拖入目录内部；成功提示「移动成功」。
- 节点悬停操作（图标 / 下拉按钮，非右键菜单）：新建子目录、重命名、删除（目录必须为空才能删除，二次确认）。
- 点击目录即选中并作为列表筛选条件，选中节点高亮；再次点击已展开目录保持展开。
- 接口只挂目录（本资产无文档节点），树上无「新建接口」入口；新建由列表页或工作区头部按钮发起。
- 无「未分组」分组区：未指定模块的接口在列表「模块」列显示「—」。
- 空态「暂无模块，点击[新建]创建」；每 60 秒静默刷新（不打断操作、失败不弹提示）。


## 3. 接口管理页与编辑器

### 3.1 页面入口与工作区

- 入口：接口测试页左侧菜单「接口管理」子页（`/workspace/projects/api-testing?tab=interfaces`，权限 `api-interface:view`）；旧独立编辑路由 `workspace/projects/interfaces/:interfaceId` 重定向到 `?tab=interfaces&interfaceId=<id>`（`new` 转为 `action=create`）。
- 工作区（`web/src/pages/project/api-testing/interface/InterfaceWorkspace.vue`）为卡片式多 Tab：固定不可关闭的「全部接口」列表 Tab（KeepAlive 缓存）+ 每个编辑器一个可关闭 Tab。
- Tab 标题为接口名称，新建未填名时显示「新接口」；标签前置蓝点表示有未保存修改，关闭时弹出确认框「该接口有未保存的修改，确定关闭？」。
- Tab 条最右常驻「导入」「新建接口」按钮，始终作用于列表（自动切回列表 Tab 并复用当前模块上下文）。
- 直链 / 刷新恢复的 query 约定：`?tab=interfaces&interfaceId=<id>` 打开编辑；`?tab=interfaces&action=create[&moduleId=]` 打开新建；`?tab=interfaces&action=create&copyFrom=<id>` 打开复制。
- 快速调试子页「查看接口」经 `pendingInterfaceId` 注入，跨子页打开对应编辑 Tab。

```
┌──────────────────────────────────────────────────────────────┐
│ [全部接口] [接口A ×] [接口B ×]        [导入] [+ 新建接口]   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   列表 Tab：筛选工具栏 + 模块树 + 表格 + 分页                │
│   编辑器 Tab：请求行 + 上下分栏（请求区 / 响应示例）         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 列表页（`web/src/pages/project/api-testing/interface/InterfacesPage.vue`）

- 工具栏：视图分段切换（全部 / 我关注的 / 我创建的）、搜索框（名称 / 路径，回车或「查询」触发、清空即重查）、状态下拉（启用 / 停用）、查询、重置；勾选行后追加「已选 N 项 + 批量移动 + 批量删除」。
- 左侧模块树区（宽 280px，见第 2 节）。
- 表格列：多选框、星标（关注/取消关注）、名称（链接打开编辑器）、方法（语义色标签）、路径、模块、状态（开关）、引用数、更新时间（`formatDateTime` 按浏览器时区展示）、操作（调试 / 编辑 / 更多 → 复制、删除）。
- 「调试」：把名称、方法、路径与来源写入 store 并切到快速调试子页（`?tab=debug`）预填请求。
- 空态「暂无接口，点击右上角「新建接口」或导入现有定义」；加载中显示遮罩；加载失败经统一消息提示。
- 分页：`total, prev, pager, next`，每页 20 条；模块、状态、视图筛选变化时回到第 1 页。
- 导入对话框：Swagger 文档 URL（先「预览」后导入）或粘贴 cURL（本地解析后提交），对应 `POST /api/project/interfaces/import/preview`、`POST /api/project/interfaces/import/url`、`POST /api/project/interfaces/import/parsed`；结果以「新建 N · 更新 N · 失败 N」摘要提示，存在失败条目时保留对话框展示明细。

### 3.3 编辑器（`web/src/pages/project/api-testing/interface/InterfaceEditorPage.vue`）

- 顶部请求行：协议下拉（仅 http）、方法下拉（GET / POST / PUT / PATCH / DELETE / HEAD / OPTIONS / CONNECT）、路径输入、名称输入、[保存]；`Ctrl/Cmd+S` 触发保存。
- 上下分栏可拖拽调节（默认各 50%，请求区范围 20%~80%），下方为响应示例区。
- 请求区页签（el-tabs，页签徽标统计启用且非空的行数 / 请求体是否携带）：
  - **基本信息**：所属模块（级联选择，仅目录可选，可清空）、描述；模块树加载失败时显示错误条与「重试」。
  - **请求头**：键值表格（启用勾选、自动补尾行、空键行不提交）。
  - **Query 参数**：键值表格，同上。
  - **请求体**：`none` / `x-www-form-urlencoded` / `raw`（子类型 text / json / xml / html / javascript）；json 提供格式化按钮；none 显示「该请求不携带请求体。」。
  - **认证**：No Auth / Bearer Token / API Key / Basic Auth（Digest Auth 置灰不可选），按类型展示字段与换算提示；No Auth 不随请求提交。
  - **验证器 / 提取器**：行编辑，支持「从公共组件引入」（弹窗仅展示启用组件，引入为复制，得到与源资产无关联的独立副本）。
- 右侧响应示例区：Body / Headers 两个 JSON 文本域，提供格式化与复制按钮；内容为空时不随请求提交。
- 保存与校验流程：
  1. 名称为空 → 警告「请填写接口名称」，不提交。
  2. 路径不以 `/` 开头 → 失焦警告（不阻断提交）；路径携带 `?k=v&…` 时自动拆分：路径截断查询串，键值追加到 Query 页签并自动切过去。
  3. 请求体 / 响应示例 JSON 非法 → 见 1.3，不提交。
  4. 新建走创建（成功关闭 Tab 回列表并刷新），编辑携带 `changeVersion` 走更新（冲突处理见 1.4）。
- 状态分支：详情加载中全屏遮罩；详情加载失败显示错误条与「重试」；新建 Tab 默认定位「请求头」页签、编辑 Tab 默认定位「基本信息」；Tab 标题随名称输入实时联动；复制预填失败回退空白新建态并提示。
- 权限：子页挂载 `api-interface:view`；保存、导入、移动、状态、关注由服务端按 `api-interface:edit` 校验，删除按 `api-interface:delete` 校验，前端不做按钮级权限隐藏。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 按前端实现对齐：补全列表/详情字段与权限口径，修正变更历史写入与复制/批量操作交互，移除未实现的引用关系与引用场景查询，重写模块树与页面结构章节 |
