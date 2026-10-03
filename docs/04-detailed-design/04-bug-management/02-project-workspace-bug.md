# 软件测试平台——缺陷管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 缺陷管理接口

### 1.1 缺陷列表

- **路径**：`GET /api/project/bugs`

- **参数**：

| 参数         | 类型     | 必填  | 说明                                    |
| ---------- | ------ | --- | ------------------------------------- |
| status     | string | 否   | 状态筛选（active/resolved/rejected/closed） |
| severity   | string | 否   | 严重等级筛选                                |
| priority   | string | 否   | 优先级筛选                                 |
| bugType    | string | 否   | 缺陷类型                                  |
| assigneeId | UUID   | 否   | 按处理人筛选                                |
| reporterId | UUID   | 否   | 按报告人（由我创建）筛选                          |
| resolvedBy | UUID   | 否   | 按解决人筛选                                |
| closedBy   | UUID   | 否   | 按关闭人筛选                                |
| keyword    | string | 否   | 关键词搜索：支持 UUID **前缀/后缀**匹配与标题 **包含** 匹配 |
| pageNo     | number | 否   | 页码，从 1 开始，默认 1 |
| pageSize   | number | 否   | 每页数量，默认 20，最大 100 |

- **取值枚举**（筛选、创建与详情表单共用）：

| 枚举 | 取值（中文标签） |
| --- | --- |
| status | `active` 激活、`resolved` 已修复、`rejected` 已拒绝、`closed` 已关闭 |
| severity | `fatal` 致命、`serious` 严重、`general` 一般、`minor` 轻微 |
| priority | `high` 高、`medium` 中、`low` 低 |
| bugType | `code_error` 代码错误、`ui_improvement` 界面优化、`design_defect` 设计缺陷、`configuration` 配置相关、`installation` 安装部署、`security` 安全相关、`performance` 性能问题、`standard_spec` 标准规范、`other` 其他 |
| resolution | `fixed` 已解决、`by_design` 设计如此、`duplicate` 重复缺陷、`external` 外部原因、`cannot_reproduce` 无法重现、`deferred` 延期处理、`wont_fix` 不予解决 |

- **响应**（分页结构 `list` + `total`）：

  ```json
  {
  "list": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "title": "登录页面崩溃",
      "severity": "fatal",
      "priority": "high",
      "status": "active",
      "bugType": "code_error",
      "confirmed": true,
      "resolution": null,
      "dueDate": "2026-07-20",
      "reporter": { "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "name": "张三" },
      "assignee": { "id": "1f0c1a2e-8f1e-4f2f-9a1c-6c3d5b7a9e01", "name": "李四" },
      "resolvedBy": null,
      "resolvedAt": null,
      "rejectedBy": null,
      "closedBy": null,
      "closedAt": null,
      "createdAt": "2026-07-06T10:00:00Z"
    }
  ],
  "total": 20
  }
  ```

  示例仅列核心字段，实际还返回 `projectId`、`reproSteps`、`moduleId`、`keywords`、`reopenCount`、`lastReopenedAt`、`duplicateOfBugId`、`relatedCaseId`、`relatedPlanId`、`updatedAt` 等。

- **列表列**：ID（缩略展示，前 4 位 + `...` + 后 4 位）、标题（超 18 字截断并悬停显示全文，点击进入详情）、类型、严重等级、优先级、状态（`confirmed=true` 时追加「已确认」标记）、创建人、处理人、解决人、解决时间、解决方案、关闭时间、创建时间、操作；时间列格式统一为 `MM-dd HH:mm`，空值显示 `-`。

### 1.2 创建缺陷

- **路径**：`POST /api/project/bugs`

- **请求体**：
  
  ```json
  {
  "title": "登录页面崩溃",
  "severity": "fatal",
  "priority": "high",
  "bugType": "code_error",
  "reproSteps": "1. 打开登录页\n2. 点击登录按钮\n3. 页面白屏",
  "moduleId": "<uuid>",
  "keywords": "登录 白屏",
  "dueDate": "2026-07-20",
  "assigneeId": "1f0c1a2e-8f1e-4f2f-9a1c-6c3d5b7a9e01",
  "relatedCaseId": "b2f1c0d4-9a3e-4a1b-8c7d-2e5f6a7b8c9d",
  "relatedPlanId": "5d1e2f3a-4b5c-4d6e-8f7a-1b2c3d4e5f60"
  }
  ```

- **处理**：发现人默认为当前用户，状态初始为 active；bugType 必填且限枚举值；assigneeId 必填且校验为当前工作空间成员；moduleId 非空时校验属于当前项目的 test_case_module；reproSteps 以 Markdown 原文存储；写入 bug_log。

- **响应**：201，`data` 为新缺陷 ID（前端随后据此逐个上传创建时选择的附件）。

### 1.3 更新缺陷

- **路径**：`PUT /api/project/bugs/:id`
- **请求体**：可更新 title、severity、priority、bugType、reproSteps、moduleId、keywords、dueDate、assigneeId、relatedCaseId、relatedPlanId（status 不可通过本接口修改，须走 1.6 变更缺陷状态）。
- **处理**：缺陷已关闭（closed）时拒绝编辑（错误码 1000012019），须先激活；修改 assigneeId 时校验其为当前工作空间成员；关联字段采用三态语义：null=不修改、空串=清空、UUID 串=更新；记录变更到 bug_log。

### 1.4 获取缺陷详情

- **路径**：`GET /api/project/bugs/:id`
- **响应**：
  
  ```json
  {
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "title": "登录页面崩溃",
  "severity": "fatal",
  "priority": "high",
  "status": "active",
  "bugType": "code_error",
  "reproSteps": "1. 打开登录页",
  "moduleId": "8f62b1a0-1c2d-4e3f-9a4b-5c6d7e8f9a0b",
  "moduleName": "登录模块",
  "keywords": "登录 白屏",
  "dueDate": "2026-07-20",
  "confirmed": true,
  "reopenCount": 0,
  "lastReopenedAt": null,
  "resolution": null,
  "duplicateOfBugId": null,
  "resolvedBy": null,
  "resolvedAt": null,
  "closedBy": null,
  "closedAt": null,
  "reporter": { "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "name": "张三" },
  "assignee": { "id": "1f0c1a2e-8f1e-4f2f-9a1c-6c3d5b7a9e01", "name": "李四" },
  "relatedCaseId": null,
  "relatedPlanId": null,
  "createdAt": "2026-07-06T10:00:00Z",
  "updatedAt": "2026-07-06T10:00:00Z",
  "recentLogs": []
  }
  ```
- **处理**：与列表接口相比额外返回 `moduleName`（所属模块名称）与 `recentLogs`（最近操作日志，最多 10 条）；前端详情页同时并行请求本接口、日志接口与工作空间成员列表。

### 1.5 获取缺陷日志

- **路径**：`GET /api/project/bugs/:id/logs`
- **响应**：按时间排序的操作记录列表（`id`、`operatorId`、`operatorName`、`operationType`、`content`、`createdAt`）。

### 1.6 变更缺陷状态

- **路径**：`PATCH /api/project/bugs/:id/status`
- **请求体**：`{ "status": "resolved|rejected|closed|active", "resolution": "fixed", "duplicateOfBugId": "<uuid>", "comment": "变更说明" }`（resolution/duplicateOfBugId 仅 status=resolved 时使用，comment 在 reject/close/reopen 时必填）
- **处理**：按四态状态机校验流转合法性（active→resolved/rejected、resolved/rejected→closed/active、closed→active）；解决时 resolution 必填且需备注说明，resolution=duplicate 时 duplicateOfBugId 必填（校验存在、非自身且同项目），并自动置 confirmed=true（解决即视为确认），**处理人自动回设为创建人**；拒绝时 comment 必填，处理人回设为创建人，记录 rejected_by；关闭与重开时 comment 必填；重开时 reopen_count+1 并清空解决/关闭字段，**重开处理人流转**：已解决→回设给解决人，已拒绝→回设给拒绝人；写入 bug_log（RESOLVE/REJECT/CLOSE/REOPEN）。
- **前端约束**：前端持有与后端一致的转移矩阵，列表行按钮、看板拖拽与详情页底部按钮只暴露当前状态的合法目标；关闭、拒绝与重开统一弹出必填说明输入框，解决统一走 BugResolveDialog。

### 1.7 确认缺陷

- **路径**：`PATCH /api/project/bugs/:id/confirm`
- **处理**：仅 active 且未确认的缺陷可执行；confirmed=true，写入 bug_log。

### 1.8 指派处理人

- **路径**：`PUT /api/project/bugs/:id/assign`
- **请求体**：`{ "assigneeId": "<uuid>" }`
- **处理**：缺陷已关闭（closed）时拒绝改派（错误码 1000012019）；校验处理人为当前工作空间成员，写入 bug_log。

### 1.9 上传附件

- **路径**：`POST /api/project/bugs/:id/attachments`
- **请求**：multipart/form-data，字段名 `file`，单文件上限 10MB。
- **处理**：缺陷已关闭时拒绝上传；扩展名白名单（图片、办公文档、文本、压缩包，排除 html/svg/js/可执行体等）并按文件头内容校验与类型一致，不接受仅按扩展名判断（安全规范 6.3）；文件落盘后写入 bug_attachment 与 bug_log。
- **响应**：附件信息 `{ "id", "fileName", "fileSize", "contentType", "uploaderId", "uploaderName", "createdAt" }`。

### 1.10 附件列表

- **路径**：`GET /api/project/bugs/:id/attachments`
- **响应**：按上传时间排序的附件列表。

### 1.11 下载附件

- **路径**：`GET /api/project/bugs/attachments/:attachmentId/download`
- **响应**：文件流，`Content-Disposition` 携带原始文件名，`X-Content-Type-Options: nosniff` 禁止类型嗅探；`contentType` 解析失败时回退 `application/octet-stream`。

### 1.12 删除附件

- **路径**：`DELETE /api/project/bugs/attachments/:attachmentId`
- **处理**：缺陷已关闭时拒绝删除；逻辑删除附件记录并写入 bug_log。

---

### 1.13 缺陷管理页

**路由**：`/workspace/projects/bugs`（name `BugList`），通过顶部动态菜单「缺陷管理」（icon `Warning`，order 20，权限码 `bug:view`）进入；列表接口亦以 `bug:view` 做后端鉴权。

**视图**：列表与看板两种，筛选栏右侧单选切换，**默认列表**；列表模式筛选卡片粘性悬浮于顶部，看板列采用 DynamicSizeList 虚拟滚动。

```
┌──────────────────────────────────────────────────┐
│ [全部][未修复的][由我创建][指派给我][由我修复][由我关闭]  搜索框  更多筛选   [列表][看板] [+提交缺陷] │
├──────────────┬──────────────────┬──────────────┤
│  激活(3)       │    已修复(2)       │  已关闭(1)     │
│  ┌──────┐    │  ┌────────────┐  │  ┌──────┐    │
│  │登录   │    │  │支付        │  │  │首页   │    │
│  │致命🔴 │    │  │严重🟠      │  │  │轻微🟢 │    │
│  └──────┘    │  └────────────┘  │  └──────┘    │
└──────────────┴──────────────────┴──────────────┘
```

**筛选栏**（两种视图共用）：

| 区域 | 内容 |
| --- | --- |
| 快捷筛选 | 单选按钮组：全部 / 未修复的 / 由我创建 / 指派给我 / 由我修复 / 由我关闭；「未修复的」映射 `status=active`，其余分别映射 `reporterId`、`assigneeId`、`resolvedBy`、`closedBy` 为当前登录用户 |
| 关键词 | 输入框（占位「搜索 ID（前缀/后缀）或标题」），回车、失焦、清空触发搜索，连续输入停顿 1 秒自动搜索 |
| 更多筛选 | 弹层含状态 / 类型 / 严重等级 / 优先级四项与 [查询]、[重置]，按钮徽标显示已选条件数；选择过程中弹层保持展开，点 [查询] 收起并刷新，点 [重置] 清空全部条件 |
| 右侧 | 视图切换（[列表]/[看板]）与 [提交缺陷] |

**列表视图**：

- 列与分页见 1.1（每页 20/50/100，默认 20，布局 `total, sizes, prev, pager, next`）；空列表由表格默认空态提示，加载中显示 loading 遮罩，请求失败弹出错误消息。
- 行操作：

| 操作 | 显示条件 | 行为 |
| --- | --- | --- |
| 详情 | 始终 | 进入详情页 |
| 解决 | status=active | 弹 BugResolveDialog（选 resolution + 备注必填，`duplicate` 时选择原始缺陷） |
| 拒绝 | status=active | 弹必填说明输入框 |
| 关闭 | status=resolved/rejected | 弹必填说明输入框 |
| 激活 | status=closed | 弹必填说明输入框 |
| 更多·确认 | status=active 且未 confirmed | 二次确认弹窗后调用确认接口 |
| 更多·激活 | status=resolved/rejected | 弹必填说明输入框 |
| 更多·指派 | status≠closed | 弹「指派处理人」对话框（成员单选，必选） |
| 更多·复制 | 始终 | 跳转创建页并经 `?copyFrom=<id>` 回填源信息（处理人留空，由提交人重新指派） |

**看板视图**：

- 三列：激活 / 已修复 / 已关闭，列头显示该列总数；列内虚拟滚动（卡片高 76px），滚动到底加载下一页；列底显示「加载中…」，空列显示空态。
- 卡片：标题 + 严重等级标签 + 处理人，点击进入详情，可拖拽。
- 拖拽流转：仅合法目标列高亮，非法目标列半透明置灰且落点被忽略；拖至「已修复」列弹 BugResolveDialog，拖至「已关闭」「激活」列弹必填说明输入框；看板不设「已拒绝」列。
- 快捷筛选或高级筛选的 `status` 与列状态不一致时，该列不发起请求（保持空态）。

---

### 1.14 提交缺陷页

**路由**：`/workspace/projects/bugs/create`（name `BugCreate`，`meta.title` 提交缺陷，不挂菜单权限码）；入口为列表页/详情页的 [提交缺陷] 与列表行「复制」。页头 [返回] 与底部 [取消] 均回到列表页。

**布局**：两栏（主列 + 320px 属性侧栏，视口 ≤1024px 时单列），底部粘性操作条。

| 分区 | 字段 |
| --- | --- |
| 基本信息（主列） | 标题\*（≤300 字，实时字数）、重现步骤（Markdown 编辑器，可选） |
| 附件（主列） | 拖拽上传、多选、暂存不自动上传；单文件 >10MB 忽略并提示；提交成功后逐个上传 |
| 属性（侧栏） | 缺陷类型\*、所属模块（项目模块树，可选）、严重等级\*、优先级\*、截止日期（`YYYY-MM-DD`，可选）、关键词（≤255，可选） |
| 指派与关联（侧栏） | 指派给\*（工作空间成员，单选可搜索）、关联用例（CaseSelector 单选，选中后显示已选数量）、关联计划（进行中的计划，可选） |

**默认值与校验**：缺陷类型默认 `code_error`、严重等级默认 `general`、优先级默认 `medium`；title / bugType / severity / priority / assigneeId 必填（失焦或变更触发），其余字段可选。

**提交流程**：表单校验 → 创建缺陷 → 逐个上传暂存附件 → 提示成功并跳回列表页；任一步失败提示错误且停留当前页。模块树、计划与成员列表加载失败不阻塞表单（对应下拉为空，可重进页面重试）。

---

### 1.15 缺陷详情页

**路由**：`/workspace/projects/bugs/:bugId`（name `BugDetail`，`meta.title` 缺陷详情，不挂菜单权限码）。

**顶栏**：[返回] 回列表；缩略 ID 标签；标题（非 closed 为行内输入框 ≤300 字，closed 为纯文本、超 60 字悬停显示全文）；右侧状态标签、「激活 N 次」标签（reopenCount>0 红色）、[提交缺陷]。

**主区（左）**：

| 分区 | 说明 |
| --- | --- |
| 重现步骤 | 非 closed 用 Markdown 编辑器可编辑；closed 用 Markdown 渲染展示；无内容显示「暂无重现步骤」空态 |
| 附件 | 表格：文件名 / 大小 / 上传人 / 上传时间 / 操作（下载，非 closed 时删除）；卡头 [上传附件]（非 closed，单文件 ≤10MB）；删除前二次确认；空态「暂无附件」，加载失败不阻塞详情 |
| 操作记录 | 仅存在日志时渲染时间线：操作人 + 操作类型 + 说明内容 |

**右侧栏（BugSidebar）**：

- **解决信息卡**（status 为 resolved 或 closed 时显示）：解决方案、重复缺陷（`duplicateOfBugId` 存在时给出跳转原始缺陷的链接）、解决人（含解决时间）、关闭人（仅 closed）。
- **属性卡**：缺陷类型、所属模块（详情页仅展示模块树目录节点）、严重等级、优先级、截止日期、关键词、指派给、关联用例、关联计划；closed 时全部只读展示，其余状态可编辑。
- **关联用例**：已关联时悬停弹出用例明细（标题、优先级、所属文档、前置条件/步骤/预期结果行），可 [打开所在文档] 跳转用例管理；未关联时可 [选择用例]，已关联时可 [更换]/[清除]。

**底部操作条（粘性）**：

| 按钮 | 显示条件 | 行为 |
| --- | --- | --- |
| 确认 | active 且未 confirmed | 二次确认弹窗后调用确认接口 |
| 解决 | active | 弹 BugResolveDialog |
| 拒绝 | active | 弹必填说明输入框 |
| 关闭 | resolved / rejected | 弹必填说明输入框 |
| 激活 | resolved / rejected / closed | 弹必填说明输入框 |
| 保存 | 非 closed | 提交标题、严重等级、优先级、缺陷类型、所属模块、关键词、截止日期、重现步骤；关联用例/计划按三态仅在变更时提交；处理人变更时另调指派接口；成功后整页重新加载 |

**状态分支**：整页加载中显示 loading；详情、日志或保存请求失败弹出错误消息；加载详情失败时仅展示页面标题与返回入口，不渲染内容分区。

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 对齐前端实现：补齐取值枚举与详情接口、重写列表列/筛选/行操作/看板流转口径、新增提交缺陷页与缺陷详情页分区，移除未实现的缺陷统计接口 |
