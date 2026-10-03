# 软件测试平台——数据概览

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 数据概览统计

**接口**：`GET /api/admin/dashboard/stats`  
**方法**：GET  
**请求参数**：无（页面不提供筛选条件与时间范围选择，统计窗口由服务端固定：今日 / 近 7 日 / 近 14 日）  
**权限**：后端 `@PreAuthorize("hasAnyAuthority('user:view', 'workspace:view', 'role:view')")`，持任一系统管理查看权限即可读；前端路由守卫为 `requiresAdmin`，菜单项不挂权限码，对所有可进入 `/admin` 的用户可见

**响应示例（data）**

```json
{
  "generatedAt": "2026-09-23T01:00:00Z",
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
| `generatedAt` | 统计生成时间（服务端按 UTC 下发，序列化为带 `Z` 的 ISO-8601；前端 `formatDateTime` 按浏览器时区展示） |
| `users.total` | 未删除用户按 `status` 分组的 `active` / `disabled` / `locked` 三项计数之和（即 `enabled + disabled + locked`） |
| `users.weekNew` | 近 7 日（`created_at >= now - 7d`）创建的用户数；KPI 文案「较上周 +N」 |
| `users.enabled/disabled/locked` | 按 `status` 分组计数；三者之和 = `total` |
| `workspaces.total/active/dissolved` | `ws_workspace` 按 `status` 分组计数，`total = active + dissolved`（平台空间状态机为 active/dissolved，无归档态；页面统一展示为「活跃 / 归档」） |
| `projects.total` | `ws_project` 未删除总数（全工作空间） |
| `projects.weekNew` | 近 7 日创建的项目数 |
| `activity.todayLogins` | **今日登录人次**：`sys_audit_log` 中 `operation='LOGIN'` 且 `created_at` 落在今日的记录数（一次成功登录 = 1 人次）。登录审计尚无记录的环境恒为 0 |
| `activeUsersDaily` | 近 14 个自然日（含今日）**按日去重**登录用户数（`COUNT(DISTINCT operator_id)`，`operation='LOGIN'`），日期升序；无记录的日期补 `count = 0`，恒返回 14 项 |

> 口径备注：`todayLogins` 为「人次」（登录次数累加），`activeUsersDaily` 为「人数」（按日去重），与交互设计 `docs/05-interaction-design/01-system-management/03-system-management-ui-dashboard.md` 1.2 / 1.3 对应。
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
   - `AuditLogMapper`：`long countLoginsBetween(LocalDateTime begin, LocalDateTime end)`、`List<Map<String,Object>> countDistinctLoginsByDay(LocalDateTime from)`（按 `TO_CHAR(created_at,'YYYY-MM-DD')` 分组，模式沿用既有 `aggregateByDay`）。
2. Mapper 统计方法以 `default` 方法封装 Wrapper/`@Select`，Service 不直接构造 Wrapper（`docs/00-spec/10-engineering/02-backend.md` §9）。
3. `total` 由分组计数求和得出（`users.total = enabled + disabled + locked`、`workspaces.total = active + dissolved`），不另发全表计数。
4. `activeUsersDaily` 组装：生成 `today-13 … today` 的 14 个日期，以查询结果填充、缺日补 0，保证恒 14 项、日期升序；`generatedAt` 取服务端当前时刻并换算为 UTC 下发。
5. 只读接口，无事务注解；任何单表查询异常向上抛出，由全局异常处理器统一返回。


## 3. 数据概览与状态扩展的文件分层

| 层 | 文件 | 职责 |
| -- | ---- | ---- |
| 类型 | `web/src/types/admin.ts` | `UserStatus = 'active' \| 'disabled' \| 'locked'`；`DashboardStats` 及嵌套类型（`DashboardUsersStats` / `DashboardWorkspacesStats` / `DashboardProjectsStats` / `DashboardActivityStats` / `DailyActiveUsers`）；`AdminWorkspace.createdByName?: string \| null` |
| 服务 | `web/src/services/admin.ts` | `fetchDashboardStats(): Promise<DashboardStats>`（`GET /admin/dashboard/stats`，axios baseURL 为 `/api`）；`fetchWorkspaces` 供空间表格分页调用 |
| 组合式 | `web/src/composables/admin/useDashboard.ts` | 页面状态与 services 调用下沉：加载/刷新/切页/重试、两接口独立容错与过期请求丢弃、KPI 脚注文案组装、折线图坐标点与环图 `stroke-dasharray` 纯函数（`buildLineChart` / `buildDonutSegments`，可单测，C8）；每页条数常量 `DASHBOARD_WORKSPACE_PAGE_SIZE = 8` |
| 页面 | `web/src/pages/admin/DashboardPage.vue` | 按 `docs/05-interaction-design/01-system-management/03-system-management-ui-dashboard.md` §1 渲染 page-head（标题 / 副标题 / 刷新）/ 错误横幅 / KPI×4 / 双图表 / 空间表格（SVG 内联模板） |
| 页面 | `web/src/pages/admin/UserListPage.vue` | 状态三态标签、筛选项、行操作按钮矩阵（`docs/05-interaction-design/01-system-management/04-system-management-ui-user.md` 1.1 / 1.2） |
| 页面 | `web/src/pages/admin/WorkspaceListPage.vue` | 状态 / 筛选 / 展示文案与空间管理一致；列表响应中的 `createdByName` 由数据概览表格消费 |
| 路由 | `web/src/router/index.ts` | `path: 'dashboard'`、`name: 'AdminDashboard'`、`meta.title` 与 `meta.menu.label` 均为「数据概览」、`menu.icon: 'Odometer'`、`menu.order: 1`、无 `section`、无 `permission` / `permissionAny`；`/admin` 默认重定向至 `/admin/dashboard` |
| 测试 | `web/src/pages/admin/DashboardPage.spec.ts`、`web/src/composables/admin/useDashboard.spec.ts` | 错误边界（双区块错误横幅 + 重试）与 KPI / 图表 / 分页 / 竞态 / 容错单测 |

> 分层约束（web/AGENTS.md）：组件不直接 import services，状态与 services 调用置于本地 composable；SVG 图表为纯展示，计算逻辑全部在 composable 内以便覆盖。


## 4. 图表计算（composable 纯函数）

- 折线图（`buildLineChart`）：viewBox `640×210`，绘图区 X `20 → 605`、顶 `20`、基线 `170`，14 点等分 X 轴，`y = 基线 - (count / max(count,1)) * (基线 - 顶)`；输出 `points` 串、面积 `path`、末点坐标与末点数值标签，X 轴取首 / 中 / 末三个日期标签（直接截取 `MM-dd`，不走 Date 解析以免时区偏移）；全 0 时贴基线且末点标注 `0`；`activeUsersDaily` 为空数组时返回空几何，页面维持空态不抛异常；末点标签 Y 取 `max(lastY - 12, 12)`，贴顶时不再上移。
- 环图（`buildDonutSegments`）：半径 `46`、环宽 `14`，`C = 2πr`；三段依次为启用（`var(--color-success)`）/ 停用（`var(--color-info)`）/ 锁定（`var(--color-danger)`），`stroke-dasharray = [seg, C-seg]`（`seg = value / total * C`），`stroke-dashoffset` 自首段 `0` 起依次累加负偏移；`total = 0` 时各段弧长为 0，只画灰底环；环心展示用户总数与「全部用户」，图例为「启用 / 停用 / 锁定 + 数值」。

---


## 5. 前端页面与状态分支

### 5.1 路由与菜单

| 项 | 实现值 |
| -- | ------ |
| 路径 / 名称 | `/admin/dashboard`、`name: 'AdminDashboard'` |
| `meta.title` | 数据概览（`document.title` 为「数据概览 - RoboTest」） |
| 菜单 | `label: 数据概览`、`icon: Odometer`、`order: 1`、无 `section`（侧栏置顶、无分组标题）、无 `permission` / `permissionAny` |
| 守卫 | `requiresAuth`（未登录跳 `/login?redirect=…`）+ `requiresAdmin`（权限列表未加载时先 `loadPermissions()`；无系统角色且无系统权限时重定向 `/`） |
| 默认入口 | `/admin` 空路径重定向至 `/admin/dashboard` |

### 5.2 页面结构

```
┌────────────────────────────────────────────────────────────┐
│ 数据概览                                      [刷新]        │
│ 平台整体运行状态 · 数据截至 2026-09-23 09:00                 │
├────────────────────────────────────────────────────────────┤
│ ⚠ {统计错误消息}                                [重试]       │ ← 仅统计接口失败时出现
│ ┌ 用户总数 ──┐ ┌ 工作空间 ┐ ┌ 项目总数 ──┐ ┌ 今日活跃 ─┐   │ ← 4 列栅格
│ │ 128 人      │ │ 16 个     │ │ 54 个       │ │ 63 人次    │   │   （≤1200px 2 列、≤640px 1 列）
│ │较上周 +6    │ │活跃14·归档2│ │近 7 日新增 +3│ │（无脚注）  │   │
│ │· 启用 108   │ │           │ │             │ │            │   │
│ └─────────────┘ └───────────┘ └─────────────┘ └────────────┘   │
│ ┌── 近 14 日活跃用户（2fr）──┐ ┌─ 用户状态分布（1fr）─┐       │ ← ≤1100px 时单列堆叠
│ │ 面积折线图 + 末点数值标签    │ │ 环图 + 竖排图例         │       │
│ └────────────────────────────┘ └────────────────────────┘       │
│ ⚠ {空间错误消息}                                [重试]           │ ← 仅空间接口失败时出现
│ ┌ 最近创建的空间            [查看全部 →] ┐                      │
│ │ 空间名称│创建人│成员│项目│状态│创建时间    │                      │
│ │ 共 16 个空间              [1] [2] [›]   │                      │
│ └────────────────────────────────────────┘                      │
└────────────────────────────────────────────────────────────┘
```

- 页头：标题「数据概览」+ 副标题（有数据时为「平台整体运行状态 · 数据截至 {generatedAt}」）+「刷新」按钮（`loading` 时按钮转圈）。
- 折线图面板：标题「近 14 日活跃用户」、副标题「按日去重登录用户」、图例「活跃用户」；4 条水平网格线（基线加深）。
- 环图面板：标题「用户状态分布」、图例「按用户数」，环心为用户总数 +「全部用户」。
- 空间表格：标题「最近创建的空间」+「查看全部 →」跳转 `/admin/workspaces`；列依次为空间名称 / 创建人 / 成员 / 项目 / 状态 / 创建时间；创建人空值显示 `—`；状态列仅 `active` 显示「活跃」（绿点），其余显示「归档」（灰点）；时间列 `formatDateTime` 输出 `YYYY-MM-DD HH:mm`。

### 5.3 概览卡片（KPI×4）

| 卡片 | 标签 / 图标 | 主值 | 单位 | 脚注 |
| ---- | ----------- | ---- | ---- | ---- |
| `users` | 用户总数 / `User` | `users.total` | 人 | 「较上周 +{weekNew} · 启用 {enabled}」，增量片段单独渲染 |
| `workspaces` | 工作空间 / `OfficeBuilding` | `workspaces.total` | 个 | 「活跃 {active} · 归档 {dissolved}」 |
| `projects` | 项目总数 / `Folder` | `projects.total` | 个 | 「近 7 日新增 +{weekNew}」，增量片段单独渲染 |
| `activity` | 今日活跃 / `Lightning` | `activity.todayLogins` | 人次 | 无脚注（「接口调用 · 失败率」按需求裁剪） |

- 增量片段带 `delta` 标记：文案非 `+0` 时加 `kpi__delta--up`（绿色），`+0` 为中性灰。
- 卡片为纯展示，不可点击、无跳转。

### 5.4 数据加载与错误处理

- 进入页面（`onMounted`）自动加载；统计接口与空间表格经 `Promise.allSettled` 并发拉取，**两块独立容错**——一块失败只影响本区块，已成功的区块保留展示。
- 空间表格复用 `GET /admin/workspaces`，仅传 `pageNo` 与 `pageSize = 8`，无关键词 / 状态筛选；创建时间倒序由后端排序保证。
- 「刷新」：两块数据一并重拉且表格回到第 1 页；页码切换按目标页重拉；页脚总数 ≤ 8 时只显示「共 N 个空间」，超过 1 页才渲染分页器（`prev, pager, next`）。
- 失败反馈：`ElMessage.error` Toast + 页面内 `role="alert"` 错误横幅（统计失败 → `dashboardError`，空间失败 → `workspaceError`），横幅内「重试」重新加载；非 Error 异常分别兜底为「加载数据概览失败」「加载最近空间失败」。
- 竞态保护：以 `requestSequence` 丢弃晚到的旧请求结果与错误（连续刷新 / 切页 / 离开页面后不回填），组件卸载时结束 loading。

### 5.5 状态分支

| 状态 | 表现 |
| ---- | ---- |
| 加载中 | 页面 `v-loading` 遮罩、刷新按钮 `loading`；数据到达前 KPI 为 0、图表为空态几何 |
| 正常 | 按 5.2 / 5.3 渲染，副标题拼接「数据截至 {generatedAt}」 |
| 统计接口失败 | 顶部错误横幅 + Toast；空间表格正常展示，统计区块维持空态（KPI 0、图表空态） |
| 空间接口失败 | 表格区错误横幅 + Toast，表格与页脚隐藏（`v-if="!workspaceError || workspaceList.length"`）；统计与图表正常展示 |
| 空数据 | KPI 显示 0；折线贴基线、末点标注 `0`；环图灰底 + 环心 `0`、图例各项为 0；表格空态「暂无工作空间」 |
| 无权限 | 无系统角色且无系统权限时由守卫重定向 `/`；接口侧由后端 `@PreAuthorize` 返回 403，走上述失败分支 |
| 未登录 | 全局守卫跳转 `/login?redirect=…` |
| 页面无缓存 | 不入 Pinia，切换菜单后再次进入重新加载 |

### 5.6 筛选与时间范围

- 页面**无筛选条件、无时间范围选择器、无导出报表按钮**。
- 时间窗口全部由服务端固定：今日（`todayLogins`）、近 7 日（`users.weekNew` / `projects.weekNew`）、近 14 个自然日含今日（`activeUsersDaily`，恒 14 项）；前端不裁剪、不二次聚合。
- 唯一可变的请求参数为空间表格的 `pageNo` / `pageSize`（固定 8）。

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 对齐数据概览实现：补全接口权限与路由菜单口径、页面结构与 KPI 卡片、加载/空态/错误态与筛选时间范围说明，修正统计口径表述与交互设计引用章节号 |
