# 软件测试平台——项目工作台

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 项目工作台接口

### 1.1 获取项目工作台数据

- **路径**：`GET /api/project/dashboard`
- **上下文**：接口只作用于 `X-Active-Project` 指定的当前项目，不在 URL 或请求体中传递项目 ID。
- **权限**：`project:view`（`@PreAuthorize` 校验），并校验当前用户为该项目成员。
- **说明**：返回当前项目页头信息、概览统计数据、最近评审/计划/缺陷和最近动态。

- **响应**：

  ```json
  {
    "projectName": "电商核心系统",
    "projectStatus": "active",
    "startTime": "2026-06-01T00:00:00Z",
    "endTime": "2026-12-31T00:00:00Z",
    "caseCount": 1284,
    "activeReviewCount": 3,
    "activePlanCount": 2,
    "openBugCount": 18,
    "recentActivities": [
      {
        "id": "uuid-activity",
        "projectId": "uuid-project",
        "actorId": "uuid-user",
        "actorName": "张明",
        "resourceType": "TEST_PLAN",
        "resourceId": "uuid-plan",
        "resourceName": "回归测试计划",
        "action": "PLAN_EXECUTED",
        "summary": "回归计划执行完成，通过率 94.6%",
        "occurredAt": "2026-09-22T14:32:00Z"
      }
    ],
    "recentReviews": [],
    "recentPlans": [],
    "recentBugs": []
  }
  ```

- **字段说明**：
  - `projectName`：项目名称；`projectStatus`：`active` 或 `archived`；起止时间为空时返回 `null`。
  - `recentActivities`：按 `occurredAt DESC, id DESC` 取最近 8 条项目语义动态，再按当前用户对应模块的查看权限过滤后返回，数量可能少于 8 条。
  - `recentReviews` / `recentPlans` / `recentBugs`：分别返回最近 5 条评审、计划、缺陷（`recentBugs` 的 `assignee` 已解析为姓名）。

### 1.2 项目动态数据

#### 1.2.1 数据表

```sql
CREATE TABLE ws_project_activity (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    resource_name VARCHAR(200) NOT NULL,
    action VARCHAR(32) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_activity_project_occurred
    ON ws_project_activity (project_id, occurred_at DESC, id DESC)
    WHERE is_deleted = FALSE;
```

- `project_id` 是项目范围查询条件；动态保留逻辑删除字段，不建立物理外键。
- `actor_name` 保存展示快照，用户删除后仍可显示历史操作人；系统任务使用“系统”。
- `resource_name` 保存资源名称快照，资源删除后动态仍可展示；`resource_type/resource_id` 用于已有详情页跳转。
- 首期不保存请求参数、IP 或完整业务 JSON，避免将敏感数据和调试信息暴露到用户动态。

#### 1.2.2 记录规则

动态服务在业务操作成功提交后记录语义事件：

| 类型 | 触发时机 |
| ---- | -------- |
| `PROJECT_CREATED` / `PROJECT_UPDATED` | 项目创建或编辑成功 |
| `PROJECT_ARCHIVED` / `PROJECT_UNARCHIVED` / `PROJECT_DELETED` | 项目归档、启封或删除成功 |
| `CASE_CREATED` / `CASE_UPDATED` / `CASE_DELETED` | 用例文档关键变更成功 |
| `REVIEW_CREATED` / `REVIEW_CASES_UPDATED` / `REVIEW_STATUS_CHANGED` / `REVIEW_COMPLETED` / `REVIEW_DELETED` | 评审创建、用例调整、状态变化、完成或删除成功 |
| `PLAN_CREATED` / `PLAN_CASES_UPDATED` / `PLAN_STARTED` / `PLAN_COMPLETED` / `PLAN_DELETED` | 测试计划创建、用例调整、开始执行、完成或删除成功 |
| `BUG_CREATED` / `BUG_UPDATED` / `BUG_STATUS_CHANGED` / `BUG_CONFIRMED` / `BUG_ASSIGNED` | 缺陷提交、编辑、状态变化、确认或指派成功 |

自动保存、轮询、读取、排序、筛选和重试不生成动态。动态摘要由后端业务服务生成，前端不自行推断业务结果。

### 1.3 项目工作台页面

**路由**：`/workspace/projects/dashboard`（路由名 `ProjectDashboard`，`meta.title` 为「项目工作台」，`meta.mode` 为 `project`，无 `meta.menu`，不进顶部菜单，为进入项目后的默认落地页）。

页面自上而下分为四个区块：页头、快捷入口与项目统计（左右两个等宽父卡片，中屏及以上各占 12 栅格，内部为统一尺寸的 2×2 网格，窄屏 ≤640px 时网格降为单列）、最近评审与最近计划（左右两个等宽卡片，固定高度 320px，条目过多时在卡片内滚动）、最近动态时间线；不提供导出、发起执行或图表功能。

**页面布局**：

```text
┌──────────────────────────────────────────────────────────────┐
│ 项目工作台                                                    │
│ 电商核心系统 [活跃] 2026-06-01 ~ 2026-12-31                  │
├──────────────────────────────┬───────────────────────────────┤
│ 快捷入口 快速进入常用功能     │ 项目统计 当前项目数据概览      │
│ ┌──────────┐ ┌──────────┐     │ ┌──────────┐ ┌──────────┐     │
│ │编写测试用例│ │新建测试计划│   │ │ 用例总数 │ │ 进行中评审│     │
│ └──────────┘ └──────────┘     │ └──────────┘ └──────────┘     │
│ ┌──────────┐ ┌──────────┐     │ ┌──────────┐ ┌──────────┐     │
│ │ 提交缺陷 │ │ 接口调试 │     │ │ 进行中计划│ │ 未关闭缺陷│     │
│ └──────────┘ └──────────┘     │ └──────────┘ └──────────┘     │
├──────────────────────────────┬───────────────────────────────┤
│ 最近评审                     │ 最近计划                      │
│ · 条目（标题 状态标签 时间）  │ · 条目（标题 状态标签 时间）   │
├──────────────────────────────┴───────────────────────────────┤
│ 最近动态 记录项目关键操作                                     │
│ · 摘要 / 操作人 · 资源名 [查看]  时间戳                        │
└──────────────────────────────────────────────────────────────┘
```

**页头**：标题「项目工作台」；项目名（未加载时显示「当前项目」）+ 状态标签（`archived` 显示「已归档」，否则显示「活跃」）+ 起止时间（`formatDate` 格式，两端均为空时显示「未设置起止时间」，否则显示 `开始 ~ 结束`）。

**快捷入口与统计卡片**：

| 区块 | 项目 | 标签 | 点击跳转 |
| ---- | ---- | ---- | -------- |
| 快捷入口 | 编写测试用例 | 管理项目测试用例 | `/workspace/projects/functional-testing` |
| 快捷入口 | 新建测试计划 | 安排测试执行任务 | `/workspace/projects/functional-testing?tab=plans` |
| 快捷入口 | 提交缺陷 | 记录和跟踪问题 | `/workspace/projects/bugs/create` |
| 快捷入口 | 接口调试 | 快速验证接口请求 | `/workspace/projects/api-testing` |
| 项目统计 | 用例总数 | `caseCount` | `/workspace/projects/functional-testing` |
| 项目统计 | 进行中评审 | `activeReviewCount` | `/workspace/projects/functional-testing?tab=reviews` |
| 项目统计 | 进行中计划 | `activePlanCount` | `/workspace/projects/functional-testing?tab=plans` |
| 项目统计 | 未关闭缺陷 | `openBugCount` | `/workspace/projects/bugs` |

快捷入口与统计卡片均为跳转导航，点击本身不创建新业务数据；统计值未加载时显示 0。

**最近评审 / 最近计划**：列表项展示标题、状态标签（分别取评审/计划状态元数据）与 `formatDateTime(createdAt)`；点击条目分别跳转 `/workspace/projects/reviews/{id}`、`/workspace/projects/plans/{id}`；列表为空时显示「暂无评审」「暂无计划」占位。

**最近动态**：时间线项展示 `summary`、`actorName · resourceName` 与 `formatDateTime(occurredAt)`；列表为空时显示「暂无项目动态」占位；存在可跳转资源时右侧显示「查看」按钮，按 `resourceType` 映射：

| `resourceType` | 跳转目标 |
| -------------- | -------- |
| `TEST_REVIEW` | `/workspace/projects/reviews/{resourceId}` |
| `TEST_PLAN` | `/workspace/projects/plans/{resourceId}` |
| `BUG` | `/workspace/projects/bugs/{resourceId}` |
| `TEST_CASE_DOCUMENT` / `TEST_CASE_NODE` | `/workspace/projects/functional-testing` |
| 其他类型 | 不显示「查看」按钮，仅展示 |

**交互说明**：

| 操作 | 触发方式 | 反馈 |
| ---- | -------- | ---- |
| 页面加载 | 进入项目工作台 | 整页 `v-loading` 遮罩，调用 `GET /api/project/dashboard`，填充页头、统计、最近评审/计划和最近动态 |
| 加载失败 | 请求异常 | 弹出错误提示（优先用错误对象的 message，由请求拦截器提取后端 `msg`；无 message 时回退「加载工作台数据失败」），页面保留空数据默认值 |
| 快捷入口 | 点击入口卡片 | 跳转到对应功能页面 |
| 统计卡片 | 点击卡片 | 按上表跳转对应功能页 |
| 最近评审 / 最近计划 | 点击列表条目 | 跳转对应评审 / 计划详情页 |
| 最近动态 | 点击「查看」按钮 | 按 `resourceType/resourceId` 跳转；无对应详情时不出按钮 |
| 时间显示 | 展示动态、列表和项目起止时间 | 后端时间按 UTC 返回，前端使用 `formatDate` / `formatDateTime` 统一格式化 |

---

## 2. 约束与实施说明

- 动态接口和项目工作台接口均使用项目上下文请求头，不新增项目 ID URL 参数。
- 动态查询只返回当前项目且未删除的记录，按 `occurred_at DESC, id DESC` 稳定排序；资源动态按当前用户对应模块的查看权限过滤。
- 动态记录属于业务时间线，不替代系统审计日志；系统审计仍按现有 `sys_audit_log` 机制保留。
- 首期动态写入失败不得阻断核心业务操作，写入异常记录日志并由后续补偿机制处理。
- 数据库变更同步写入 `server/src/main/resources/db/schema.sql`，存量环境按上述 DDL 执行。
- 存量升级先创建 `ws_project_activity` 及时间线索引，再发布应用；回滚前确认不再需要动态数据后可删除该表及索引，不影响现有业务表。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐 DashboardPage 实现：补全四区块布局与最近评审/计划面板，细化快捷入口、统计卡片跳转目标、动态「查看」映射及加载/错误/空态 |
| V1.0 | 2026-10-02 | 补充窄屏单列与最近动态空态，按实现校准错误提示口径及动态排序字段 |
