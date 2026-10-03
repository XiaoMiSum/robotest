# 软件测试平台——场景管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 场景管理

> 前端统一经 `web/src/services/project/api-testing/scene.ts` 调用；当前项目上下文经请求头传递，不出现在 URL；响应示例仅展示 `data` 字段内容。
> 权限口径由服务端 `@PreAuthorize` 强制：列表、详情 `api-scene:view`；创建、更新、删除、批量移动、批量删除、关注 `api-scene:edit`；触发执行 `api-scene:execute`。接口测试页路由对任一接口测试模块 view 权限开放，「测试场景」子页入口挂 `api-scene:view`；前端页面不做按钮级权限隐藏。

### 1.1 查询场景列表

- **路径**：`GET /api/project/api-scenes?pageNo=1&pageSize=20&moduleId=&search=&status=&followedOnly=`
- **权限**：`api-scene:view`
- **筛选与分页**：`pageNo`、`pageSize`（前端固定每页 20 条）、`moduleId`（可选，模块 ID）、`search`（可选，场景名称模糊匹配）、`status`（可选，发布状态过滤：`draft`=草稿 / `published`=已发布）、`followedOnly`（可选，`true` 时仅返回当前用户关注的场景）。
- **排序**：服务端固定按 `updatedAt` 倒序返回，不提供排序参数。
- **前端提交的未实现筛选**：列表页另提交 `view`（`all`=全部 / `followed`=我关注的 / `created`=我创建的）与 `lastStatus`（执行状态，`success` / `failed`，未执行传空值），服务端当前无对应过滤逻辑，返回结果不受其影响。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "name": "登录流程测试",
      "moduleId": "018e...",
      "environmentId": "018c...",
      "priority": "P2",
      "status": "draft",
      "stepCount": 5,
      "lastExecutedAt": "2026-08-17T10:30:00Z",
      "lastStatus": "success",
      "updatedAt": "2026-08-16T15:00:00Z",
      "followed": true
    }
  ],
  "total": 18
}
```

- **字段说明**：`lastStatus` 取值 `success` / `failed` / `error` / `cancelled` / `timeout` / `running` / `pending`，未执行过时与 `lastExecutedAt` 同为空；`followed` 为当前用户是否已关注该场景；`priority` 未设置时为空。
- **列表页**（`web/src/pages/project/api-testing/scene/ScenariosPage.vue`）：
  - 工具栏：视图分段切换（全部 / 我关注的 / 我创建的）、名称搜索（回车、清空或「查询」触发）、执行状态下拉（执行成功 / 执行失败 / 未执行）、发布状态下拉（草稿 / 已发布）、查询、重置；勾选行后追加「已选 N 项 + 批量移动 + 批量删除」。模块、执行状态、发布状态、视图变化时回到第 1 页，视图切换同时清空勾选；「重置」清空搜索与状态/视图筛选，不解除模块筛选。
  - 表格列（自左至右）：多选框、星标（关注/取消关注）、场景名称（链接打开编辑器）、所属模块（无模块显示「未分组」，模块名缺失显示「—」）、优先级（P0–P3 色块徽标，未设置显示「—」）、步骤数、状态（草稿 / 已发布徽标）、最近执行（状态标签：成功 / 失败 / 执行中 / 等待中 / 异常 / 未执行）、最近执行时间、更新时间（`formatDateTime` 按浏览器时区展示）、操作（编辑；更多 → 执行 / 复制 / 删除）。
  - 排序与分页：列头无排序控件，不支持前端排序；分页 `total, prev, pager, next`，不提供每页条数切换。
  - 状态分支：空态 `暂无测试场景，点击右上角「新建场景」开始创建`；加载中显示卡片遮罩；列表加载失败提示「场景列表加载失败」；删除、执行与批量操作失败按对应动作提示；当前页删空且非首页时自动回退一页。
  - 行动作：「执行」立即触发场景执行并提示 `场景「×」执行已启动` 后刷新列表（接口见 `docs/04-detailed-design/05-api-testing/16-test-scenario-execution.md`）；「复制」按 1.6 打开预填新建态；「删除」二次确认后按 1.5 删除。
- **模块树联动**（`web/src/components/project/ProjectModuleTree.vue`，`asset-type="scene"`）：目录模式（场景无文档节点），支持目录名称搜索、新建目录、重命名、删除（目录必须为空，二次确认）、同级排序与拖入目录移动（成功提示「移动成功」）、每 60 秒静默刷新；点击目录即作为列表 `moduleId` 筛选条件并高亮，筛选态由树选中态维持；模块树加载失败时「所属模块」列退化为「—」，不阻塞列表。

### 1.2 查询场景详情

- **路径**：`GET /api/project/api-scenes/:id`
- **权限**：`api-scene:view`
- **响应**：包含完整步骤列表、参数列表、处理器列表。

```json
{
  "id": "018f...",
  "name": "登录流程测试",
  "moduleId": "018e...",
  "description": "登录并校验返回码",
  "environmentId": "018c...",
  "priority": "P2",
  "status": "draft",
  "followed": true,
  "variables": [
    { "name": "username", "value": "admin", "description": "测试用户名" },
    { "name": "password", "value": "${env:TEST_PASSWORD}", "description": "从环境变量获取" }
  ],
  "processors": [],
  "changeVersion": 3,
  "steps": [
    {
      "id": "018a...",
      "name": "发送登录请求",
      "stepType": "http",
      "sortOrder": 0,
      "enabled": true,
      "sourceType": "system",
      "sourceId": "018b...",
      "sourceInterfaceId": "018b...",
      "sourceInterfaceName": "用户登录",
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
      "variables": [
        { "name": "username", "value": "admin", "source": "custom", "description": "用户名" },
        { "name": "password", "value": "${env:TEST_PASSWORD}", "source": "custom", "description": "密码" }
      ],
      "processors": [],
      "validators": [
        { "id": "018g...", "name": "验证返回码", "enabled": true, "target": "status_code", "condition": "equals", "expected": "200" },
        { "id": "018g...", "name": "验证业务码", "enabled": true, "target": "json_field", "condition": "equals", "expected": "200", "expression": "$.code" }
      ],
      "extractors": [
        { "id": "018h...", "name": "提取登录 token", "enabled": true, "source": "json_field", "expression": "$.data.token", "variableName": "token" }
      ]
    }
  ]
}
```

### 1.3 创建场景

- **路径**：`POST /api/project/api-scenes`
- **权限**：`api-scene:edit`
- **请求体**：同 1.2 响应结构（不含 `id`、`changeVersion`、`followed` 与步骤明细 `id`）。
- **说明**：创建态页面即可预先编排步骤/变量/前置处理器/后置处理器，随场景在同一事务内一并落库（`variables`、`processors`、`steps` 均为可选，缺省为空）。`steps` 以数组传入，服务端按数组顺序自 `1` 起分配 `sort_order`，其余字段取值同 3.3.1 步骤保存。
- **校验**：`name` 必填且不超过 200 字符（前端名称输入框限 100 字符）。`priority` 可选，取值仅允许 `P0/P1/P2/P3`（字母大写）或空；非法返回 1000017304（`API_SCENE_SETTING_INVALID`）。`status` 可选，取值仅允许 `draft`（草稿）/ `published`（已发布），缺省 `draft`；非法返回 1000017304。`steps` 内每步 `step_type` 仅允许 `http`（与 3.3.1 一致）。
- **前端行为**：入口为工作区右上角「新建场景」按钮（列表页内无新建按钮），打开新建态编辑器 Tab（见 1.6 的 query 约定）。保存前校验：名称为空提示「请填写场景名称」、未选模块提示「请选择所属模块」，均不提交；缺省回填优先级 `P2`（优先级菜单标注「（默认）」），创建态缺省选中环境列表中的默认环境。创建成功提示「已创建」并关闭该 Tab 回列表刷新。

### 1.4 更新场景

- **路径**：`PUT /api/project/api-scenes/:id`
- **权限**：`api-scene:edit`
- **请求体**：同 1.2 响应结构。
- **乐观锁**：请求体需包含 `changeVersion`；与库中当前版本不一致时更新 0 行并返回业务错误码 1000017303（`API_SCENE_VERSION_CONFLICT`），框架统一 Result 封装，不以 HTTP 状态码表达冲突；成功后 `change_version` 递增并写入一条变更历史。
- **说明**：`status` 随保存请求一并提交（「保存为草稿」写 `draft`、「发布」写 `published`），创建与编辑态均可自由二态切换。
- **前端行为**：保存中按钮 loading，成功提示「已保存」并重新拉取详情刷新表单与版本号；识别到版本冲突时提示服务端消息「场景已被他人修改，请刷新后重试」，由用户手动刷新后重试。

### 1.5 删除场景

- **路径**：`DELETE /api/project/api-scenes/:id`
- **权限**：`api-scene:edit`
- **校验**：若场景被定时任务引用，返回错误码 1000017302（`API_SCENE_REFERENCED`）。
- **前端行为**：列表「更多 → 删除」二次确认「删除场景「×」？删除后不可恢复。」，成功提示「已删除」；编辑器顶部「删除」按钮（仅编辑态展示）二次确认「删除场景后不可恢复，确定删除？」，成功后关闭编辑 Tab 返回列表。

### 1.6 复制场景

- **交互流程**：列表行内「更多 → 复制」打开新建态场景编辑器 Tab 并预填源场景全部内容（名称默认「原名称（副本）」且可修改，模块 / 描述 / 默认环境 / 优先级 / 变量 / 前置、后置处理器 / 步骤均同源），由用户确认后走 1.3 创建场景落库，生成独立副本；副本不继承源状态，按 1.3 缺省 `draft`。预填失败提示「复制预填失败」并保留空白新建态。
- **query 约定**：`?tab=scenes&action=create&copyFrom=<id>`，刷新 / 直链可恢复复制态。
- **无独立后端接口**：复制不提供专用接口，预填为前端行为；保存统一走 1.3 `POST /api/project/api-scenes`。
- **说明**：整体复制场景及其下全部步骤（复制模式，非链接引用，副本与源后续修改互不影响）；步骤的 `source_type` / `source_id` 随步骤保留，供置灰展示与来源追溯。

### 1.7 批量移动场景（列表勾选）

- **路径**：`PUT /api/project/api-scenes/batch/move`
- **权限**：`api-scene:edit`
- **请求体**：

```json
{
  "ids": ["018f...", "018f..."],
  "moduleId": "018e..."
}
```

- **说明**：将所选场景批量移动至目标模块；移动不改变场景内容，仅更新 `module_id`。`moduleId` 为空表示移至未分组；目标模块由前端项目级模块树选择，须与场景同属当前项目。
- **校验**：`ids` 非空；任一场景不存在或不属于当前项目则整体拒绝，返回 1000017301（`API_SCENE_NOT_FOUND`）；目标模块不存在或不属于当前项目返回 1000017051（`PROJECT_MODULE_NOT_FOUND`）。全量成功后 `updated_at` 刷新，返回 `true`。
- **事务**：整体成功语义，任一失败整体回滚。
- **前端行为**：勾选行后出现「批量移动」按钮，对话框「批量移动到模块」内以项目模块树选择目标模块，未选目标时提示「请选择目标模块」；成功提示「已移动 N 个场景」并刷新列表、清空勾选。

### 1.8 批量删除场景（列表勾选）

- **路径**：`DELETE /api/project/api-scenes/batch`
- **权限**：`api-scene:edit`
- **请求体**：

```json
{
  "ids": ["018f...", "018f..."]
}
```

- **校验**：`ids` 非空；任一场景被定时任务引用则整体拒绝，不执行任何删除；全量成功后返回 `true`。
- **前端行为**：确认框「批量删除 N 个场景？删除后不可恢复。」，成功提示「已删除」并清空勾选，当前页删空且非首页时自动回退一页。

### 1.9 关注/取消关注场景

- **路径**：`POST /api/project/api-scenes/:id/follow`（关注）、`DELETE /api/project/api-scenes/:id/follow`（取消关注）
- **权限**：`api-scene:edit`
- **交互流程**：列表行首星标点击切换关注态（已关注为实心星），成功后刷新列表使 `followed` 与星标同步；失败统一提示「操作失败」。
- **说明**：关注关系按用户记录，列表项 `followed` 标记当前用户的关注态；1.1 的 `followedOnly=true` 过滤依赖该关系。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 按前后端实现对齐：修正场景接口路径与筛选/响应字段，补全权限口径、列表页列与筛选/分页/排序、模块树联动、批量与关注交互及状态分支 |
