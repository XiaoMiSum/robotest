# 软件测试平台——场景管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 场景管理

### 1.1 查询场景列表

- **路径**：`GET /api/project/scenes?moduleId=&search=&status=&page=1&pageSize=20`
- **筛选参数**：`moduleId`（可选，模块 ID）、`search`（可选，场景名称模糊匹配）、`status`（可选，发布状态过滤：`draft`=草稿 / `published`=已发布）。
- **响应**：

```json
{
  "records": [
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
      "updatedAt": "2026-08-16T15:00:00Z"
    }
  ],
  "total": 18
}
```

### 1.2 查询场景详情

- **路径**：`GET /api/project/scenes/:id`
- **响应**：包含完整步骤列表、参数列表、处理器列表。

```json
{
  "id": "018f...",
  "name": "登录流程测试",
  "moduleId": "018e...",
  "environmentId": "018c...",
  "priority": "P2",
  "status": "draft",
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

- **路径**：`POST /api/project/scenes`
- **请求体**：同 1.2 响应结构（不含 id、steps 明细字段 id）。
- **说明**：创建态页面即可预先编排步骤/变量/前置处理器/后置处理器，随场景在同一事务内一并落库（`variables`、`processors`、`steps` 均为可选，缺省为空）。`steps` 以数组传入，服务端按数组顺序自 `1` 起分配 `sort_order`，其余字段取值同 3.3.1 步骤保存。
- **校验**：`priority` 可选，取值仅允许 `P0/P1/P2/P3`（字母大写）或空；非法返回 7210（`API_SCENE_SETTING_INVALID`）。`status` 可选，取值仅允许 `draft`（草稿）/ `published`（已发布），缺省 `draft`；非法返回 7210。`steps` 内每步 `step_type` 仅允许 `http`（与 3.3.1 一致）。新建场景前端缺省回填 `P2`。

### 1.4 更新场景

- **路径**：`PUT /api/project/scenes/:id`
- **请求体**：同 1.2 响应结构。
- **乐观锁**：请求体需包含 `changeVersion`，服务端校验版本号一致性，冲突返回 409。
- **说明**：`status` 随保存请求一并提交（「保存为草稿」写 `draft`、「发布」写 `published`），创建与编辑态均可自由二态切换。

### 1.5 删除场景

- **路径**：`DELETE /api/project/scenes/:id`
- **校验**：若场景被定时任务引用，返回错误码 7203（`API_SCENE_REFERENCED`）。

### 1.6 复制场景

- **交互流程**：列表行内 [复制] 打开新建态场景编辑器并预填源场景全部内容（名称默认「原名称（副本）」，模块 / 描述 / 默认环境 / 优先级 / 变量 / 前置、后置处理器 / 步骤均同源），由用户确认后走 1.3 创建场景落库，生成独立副本。
- **无独立后端接口**：复制不提供专用接口，预填为前端行为；保存统一走 1.3 `POST /api/project/scenes`。
- **说明**：整体复制场景及其下全部步骤（复制模式，非链接引用，副本与源后续修改互不影响）；步骤的 `source_type` / `source_id` 随步骤保留，供置灰展示与来源追溯。

### 1.7 批量移动场景（列表勾选）

- **路径**：`PUT /api/project/api-scenes/batch/move`
- **请求体**：

```json
{
  "ids": ["018f...", "018f..."],
  "moduleId": "018e..."
}
```

- **说明**：将所选场景批量移动至目标模块；移动不改变场景内容，仅更新 `module_id`。目标模块由前端项目级模块树选择，须与场景同属当前项目。
- **校验**：`ids` 非空；任一场景不存在或不属于当前项目则整体拒绝，返回 7201（`API_SCENE_NOT_FOUND`）。全量成功后 `updated_at` 刷新，返回 `true`。
- **事务**：整体成功语义，任一失败整体回滚。

### 1.8 批量删除场景（列表勾选）

- **路径**：`DELETE /api/project/api-scenes/batch`
- **请求体**：

```json
{
  "ids": ["018f...", "018f..."]
}
```

- **校验**：任一场景被定时任务引用则整体拒绝，不执行任何删除；全量成功后返回 `true`。


