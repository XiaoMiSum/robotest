# 软件测试平台——（分册：缺陷管理）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `02-project-workspace-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.6 缺陷管理接口

#### 3.6.1 缺陷列表

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

- **响应**：
  
  ```json
  {
  "records": [
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
      "reporter": { "id": 1, "name": "张三" },
      "assignee": { "id": 2, "name": "李四" },
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

- **列表列**：缩略 ID（显示 ID 前 8 位）、解决人、解决时间、解决方案、关闭人、关闭时间；时间列格式统一为 `MM-dd HH:mm`。

#### 3.6.2 创建缺陷

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
  "assigneeId": 2,
  "relatedCaseId": 101,
  "relatedPlanId": 5
  }
  ```

- **处理**：发现人默认为当前用户，状态初始为 active；bugType 必填且限枚举值；assigneeId 必填且校验为当前工作空间成员；moduleId 非空时校验属于当前项目的 test_case_module；reproSteps 以 Markdown 原文存储；写入 bug_log。

- **响应**：201。

#### 3.6.3 更新缺陷

- **路径**：`PUT /api/project/bugs/:id`
- **请求体**：可更新 title、severity、priority、bugType、reproSteps、moduleId、keywords、dueDate、assigneeId、relatedCaseId、relatedPlanId（status 不可通过本接口修改，须走 3.6.5 状态流转接口）。
- **处理**：缺陷已关闭（closed）时拒绝编辑（错误码 1000012019），须先激活；修改 assigneeId 时校验其为当前工作空间成员；关联字段采用三态语义：null=不修改、空串=清空、UUID 串=更新；记录变更到 bug_log。

#### 3.6.4 获取缺陷日志

- **路径**：`GET /api/project/bugs/:id/logs`
- **响应**：按时间排序的操作记录列表。

#### 3.6.5 变更缺陷状态

- **路径**：`PATCH /api/project/bugs/:id/status`
- **请求体**：`{ "status": "resolved|rejected|closed|active", "resolution": "fixed", "duplicateOfBugId": "<uuid>", "comment": "变更说明" }`（resolution/duplicateOfBugId 仅 status=resolved 时使用，comment 在 reject/close/reopen 时必填）
- **处理**：按四态状态机校验流转合法性（active→resolved/rejected、resolved/rejected→closed/active、closed→active）；解决时 resolution 必填且需备注说明，resolution=duplicate 时 duplicateOfBugId 必填（校验存在、非自身且同项目），并自动置 confirmed=true（解决即视为确认），**处理人自动回设为创建人**；拒绝时 comment 必填，处理人回设为创建人，记录 rejected_by；关闭与重开时 comment 必填；重开时 reopen_count+1 并清空解决/关闭字段，**重开处理人流转**：已解决→回设给解决人，已拒绝→回设给拒绝人；写入 bug_log（RESOLVE/REJECT/CLOSE/REOPEN）。

#### 3.6.6 确认缺陷

- **路径**：`PATCH /api/project/bugs/:id/confirm`
- **处理**：仅 active 且未确认的缺陷可执行；confirmed=true，写入 bug_log。

#### 3.6.7 指派处理人

- **路径**：`PUT /api/project/bugs/:id/assign`
- **请求体**：`{ "assigneeId": "<uuid>" }`
- **处理**：缺陷已关闭（closed）时拒绝改派（错误码 1000012019）；校验处理人为当前工作空间成员，写入 bug_log。

#### 3.6.8 缺陷统计

- **路径**：`GET /api/project/bugs/statistics`
- **响应**：`{ "total": 10, "byStatus": {}, "bySeverity": {}, "byPriority": {}, "byAssignee": {}, "byReporter": {} }`

#### 3.6.9 上传附件

- **路径**：`POST /api/project/bugs/:id/attachments`
- **请求**：multipart/form-data，字段名 `file`，单文件上限 10MB。
- **处理**：缺陷已关闭时拒绝上传；文件落盘后写入 bug_attachment 与 bug_log。
- **响应**：附件信息 `{ "id", "fileName", "fileSize", "contentType", "uploader", "createdAt" }`。

#### 3.6.10 附件列表

- **路径**：`GET /api/project/bugs/:id/attachments`
- **响应**：按上传时间排序的附件列表。

#### 3.6.11 下载附件

- **路径**：`GET /api/project/bugs/attachments/:attachmentId/download`
- **响应**：文件流，`Content-Disposition` 携带原始文件名。

#### 3.6.12 删除附件

- **路径**：`DELETE /api/project/bugs/attachments/:attachmentId`
- **处理**：缺陷已关闭时拒绝删除；逻辑删除附件记录并写入 bug_log。

---


#### 5.3.7 缺陷管理页

**路由**：`/workspace/projects/bugs`

通过顶部动态菜单"缺陷管理"进入，包含看板和列表两种视图。列表模式筛选栏在滚动时粘性悬浮于顶部；看板列采用 DynamicSizeList 虚拟化以支撑大数据量。

**看板视图**：

```
┌──────────────────────────────────────────────────┐
│  缺陷管理       [看板] [列表]       [+ 提交缺陷]   │
├──────────────┬──────────────────┬──────────────┤
│  激活(3)       │    已解决(2)       │  已关闭(1)     │
│  ┌──────┐    │  ┌────────────┐  │  ┌──────┐    │
│  │登录   │    │  │支付        │  │  │首页   │    │
│  │致命🔴 │    │  │严重🟠      │  │  │轻微🟢 │    │
│  └──────┘    │  └────────────┘  │  └──────┘    │
└──────────────┴──────────────────┴──────────────┘
```

**交互说明**：

| 操作   | 触发方式         | 反馈          |
| ---- | ------------ | ----------- |
| 切换视图 | 点击[看板]/[列表]  | 切换展示方式      |
| 提交缺陷 | 点击[提交缺陷]     | 进入创建页面（禅道式表单，重现步骤支持 Markdown 编辑） |
| 复制缺陷 | 列表行操作「复制」   | 跳转创建页并经 query 回填源信息（处理人留空，由提交人重新指派） |
| 更新状态 | 在看板中拖拽卡片至目标列 | 拖至「已解决」弹解决对话框（选 resolution + 备注说明），拖至「已拒绝」弹说明输入，拖至关闭/激活弹说明输入；非法目标列置灰 |
| 确认/解决/拒绝/关闭/激活 | 详情页状态按钮或列表行操作 | 按四态状态机流转，解决弹 BugResolveDialog，拒绝弹说明输入框 |
| 列表行操作 | 操作列按钮：详情始终显示；按状态直出解决（active）/关闭（resolved/rejected）/激活（closed）；非 closed 行收「更多」下拉（确认、拒绝、激活、指派、复制） | 解决弹 BugResolveDialog，关闭/拒绝/激活弹说明输入，指派弹成员选择对话框 |
| 查看详情 | 点击卡片/行       | 进入详情页（重现步骤 Markdown 渲染，关联用例悬停展示明细可跳转文档） |
| 快捷筛选 | 快捷过滤栏         | 「未修复」「由我创建」「指派给我」等快速选项，筛选条件选择后弹层保活不收起 |
| 筛选   | 筛选面板：状态/类型/严重等级/优先级/处理人/报告人/解决人/关闭人 | 列表/看板刷新     |

---


