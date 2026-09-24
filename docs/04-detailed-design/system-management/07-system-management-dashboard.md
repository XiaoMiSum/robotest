# 软件测试平台——数据概览

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 数据概览统计

**接口**：`GET /api/admin/dashboard/stats`  
**方法**：GET  
**请求参数**：无

**响应示例（data）**

```json
{
  "generatedAt": "2026-09-23T01:00:00",
  "users": { "total": 128, "weekNew": 6, "enabled": 108, "disabled": 16, "locked": 4 },
  "workspaces": { "total": 16, "active": 14, "dissolved": 2 },
  "projects": { "total": 54, "weekNew": 3 },
  "activity": { "todayLogins": 63 },
  "activeUsersDaily": [
    { "date": "2026-09-10", "count": 41 },
    { "date": "2026-09-11", "count": 45 },
    { "date": "2026-09-12", "count": 43 },
    { "date": "2026-09-13", "count": 52 },
    { "date": "2026-09-14", "count": 49 },
    { "date": "2026-09-15", "count": 58 },
    { "date": "2026-09-16", "count": 55 },
    { "date": "2026-09-17", "count": 61 },
    { "date": "2026-09-18", "count": 59 },
    { "date": "2026-09-19", "count": 64 },
    { "date": "2026-09-20", "count": 60 },
    { "date": "2026-09-21", "count": 62 },
    { "date": "2026-09-22", "count": 57 },
    { "date": "2026-09-23", "count": 63 }
  ]
}
```

**字段口径**

| 字段 | 口径 |
| ---- | ---- |
| `generatedAt` | 统计生成时间（服务端当前时间，平台约定 UTC+0 无时区标识） |
| `users.total` | `sys_user` 未删除总数（全状态） |
| `users.weekNew` | 近 7 日（`created_at >= now - 7d`）创建的用户数；KPI 文案「较上周 +N」 |
| `users.enabled/disabled/locked` | 按 `status` 分组计数；三者之和 = `total` |
| `workspaces.total/active/dissolved` | `ws_workspace` 按 `status` 分组计数（平台空间状态机为 active/dissolved，无归档态） |
| `projects.total` | `ws_project` 未删除总数（全工作空间） |
| `projects.weekNew` | 近 7 日创建的项目数 |
| `activity.todayLogins` | **今日登录人次**：`sys_audit_log` 中 `operation='LOGIN'` 且 `created_at` 落在今日的记录数（一次成功登录 = 1 人次）。登录审计尚无记录的环境恒为 0 |
| `activeUsersDaily` | 近 14 个自然日（含今日）**按日去重**登录用户数（`COUNT(DISTINCT operator_id)`，`operation='LOGIN'`），日期升序；无记录的日期补 `count = 0`，恒返回 14 项 |

> 口径备注：`todayLogins` 为「人次」（登录次数累加），`activeUsersDaily` 为「人数」（按日去重），与交互设计 §2.2/§2.3 对应。
> 「接口调用数 / 失败率」按需求裁剪，**不统计、不返回、不展示**（示例 KPI 脚注 `接口调用 12.4k · 失败 0.3%` 不实现）。

---


## 2. DashboardStatsService 端口

```java
public interface DashboardStatsService {
    DashboardStatsRespDTO getStats();
}
```

**实现要点**（`service/admin/dashboard/DashboardStatsServiceImpl`）：

1. 聚合查询互相独立，可并行/顺序执行单表统计：
   - `SysUserMapper`：`Map<String, Long> countGroupByStatus()`、`long countCreatedSince(LocalDateTime since)`；
   - `WorkspaceMapper`：`Map<String, Long> countGroupByStatus()`；
   - `ProjectMapper`：`long countAll()`、`long countCreatedSince(LocalDateTime since)`；
   - `AuditLogMapper`：`long countLoginsBetween(LocalDateTime begin, LocalDateTime end)`、`List<Map<String,Object>> countDistinctLoginsByDay(LocalDateTime begin)`（按 `TO_CHAR(created_at,'YYYY-MM-DD')` 分组，模式沿用既有 `aggregateByDay`）。
2. Mapper 统计方法以 `default` 方法封装 Wrapper/`@Select`，Service 不直接构造 Wrapper（`docs/00-spec/10-engineering/02-backend.md` §9）。
3. `activeUsersDaily` 组装：生成 `today-13 … today` 的 14 个日期，以查询结果填充、缺日补 0，保证恒 14 项、日期升序。
4. 只读接口，无事务注解；任何单表查询异常向上抛出，由全局异常处理器统一返回。


## 3. 数据概览与状态扩展的文件分层

| 层 | 文件 | 职责 |
| -- | ---- | ---- |
| 类型 | `web/src/types/admin.ts` | `UserStatus` 增加 `'locked'`；新增 `DashboardStats` 及嵌套类型；`AdminWorkspace` 新增 `createdByName?: string \| null` |
| 服务 | `web/src/services/admin.ts` | 新增 `fetchDashboardStats(): Promise<DashboardStats>`（`GET /admin/dashboard/stats`） |
| 组合式 | `web/src/composables/admin/useDashboard.ts`（新增） | 页面状态与 services 调用下沉：加载/刷新、KPI 脚注文案组装、折线图坐标点与环图 `stroke-dasharray` 纯函数计算（可单测，C8） |
| 页面 | `web/src/pages/admin/DashboardPage.vue`（重构） | 按交互设计 §2 渲染 page-head / KPI×4 / 双图表 / 空间表格（SVG 内联模板） |
| 页面 | `web/src/pages/admin/UserListPage.vue` | 状态三态标签、筛选项、行操作按钮矩阵（交互设计 §3） |
| 页面 | `web/src/pages/admin/WorkspaceListPage.vue` | 状态/筛选/展示文案不受影响；列表响应新增字段仅数据概览消费 |
| 路由 | `web/src/router/index.ts` | `meta.title` 由「仪表盘」改为「数据概览」 |

> 分层约束（web/AGENTS.md）：组件不直接 import services，状态与 services 调用置于本地 composable；SVG 图表为纯展示，计算逻辑全部在 composable 内以便覆盖。


## 4. 图表计算（composable 纯函数）

- 折线图：14 点等分 X 轴，`y = 基线 - (count / max(count,1)) * 绘图区高度`；输出 `points` 串、面积 `path`、末点坐标与标签；全 0 时贴基线。
- 环图：三段 `stroke-dasharray = [seg, C-seg]`，`stroke-dashoffset` 依次累加，`C = 2πr`；`total = 0` 时只画底环。

---


