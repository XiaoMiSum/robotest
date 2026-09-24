# 软件测试平台——项目工作台

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 项目工作台接口

### 1.1 获取项目工作台数据

- **路径**：`GET /api/project/dashboard`
- **上下文**：接口只作用于 `X-Active-Project` 指定的当前项目，不在 URL 或请求体中传递项目 ID。
- **说明**：返回当前项目页头信息、概览统计数据和最近动态。

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
  - `recentActivities`：按 `occurredAt DESC, id DESC` 返回最近 8 条项目语义动态。
  - 原有统计字段和最近评审、最近计划、最近缺陷字段保持兼容。

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

**路由**：`/workspace/projects/dashboard`

页面在原有统计卡片和最近评审/计划基础上，增加页头、快捷入口和最近动态；不新增导出、发起执行或图表功能。

**页面布局**：

```text
┌──────────────────────────────────────────────────────────────┐
│ 项目工作台                                                    │
│ 电商核心系统 · 活跃 · 2026-06-01 ~ 2026-12-31               │
├──────────────────────────────────────────────────────────────┤
│ 用例总数     进行中评审     进行中计划     未关闭缺陷          │
├──────────────────────────────────────┬───────────────────────┤
│ 快捷入口                              │ 最近动态              │
│ 编写测试用例 / 新建测试计划             │ · 计划执行完成         │
│ 提交缺陷 / 接口调试                    │ · 缺陷提交             │
│                                      │ · 评审发起             │
└──────────────────────────────────────┴───────────────────────┘
```

**交互说明**：

| 操作 | 触发方式 | 反馈 |
| ---- | -------- | ---- |
| 页面加载 | 进入项目工作台 | 调用 `GET /api/project/dashboard`，填充页头、统计和最近动态 |
| 快捷入口 | 点击入口卡片 | 跳转到已有功能页面，不创建新业务数据 |
| 统计卡片 | 点击卡片 | 跳转至对应功能页面 |
| 最近动态 | 点击动态项 | 根据 `resourceType/resourceId` 跳转已有详情页；无详情时仅展示 |
| 时间显示 | 展示动态和项目起止时间 | 后端时间按 UTC 返回，前端使用统一时间格式化工具 |

---

## 2. 约束与实施说明

- 动态接口和项目工作台接口均使用项目上下文请求头，不新增项目 ID URL 参数。
- 动态查询只返回当前项目且未删除的记录，按创建时间稳定排序；资源动态按当前用户对应模块的查看权限过滤。
- 动态记录属于业务时间线，不替代系统审计日志；系统审计仍按现有 `sys_audit_log` 机制保留。
- 首期动态写入失败不得阻断核心业务操作，写入异常记录日志并由后续补偿机制处理。
- 数据库变更同步写入 `server/src/main/resources/db/schema.sql`，存量环境按上述 DDL 执行。
- 存量升级先创建 `ws_project_activity` 及时间线索引，再发布应用；回滚前确认不再需要动态数据后可删除该表及索引，不影响现有业务表。
