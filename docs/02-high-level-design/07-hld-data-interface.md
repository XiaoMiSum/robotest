# 软件测试平台——数据与接口设计

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 数据设计

### 1.1 核心实体关系图

    User ──── UserRole ──── Role (type: system / workspace, scope: global / workspace, full_access: bool)
    User ──── UserWorkspace (workspaceRole: UUID → sys_role.id) ──── Workspace
    Workspace ──── Project ──── TestCaseModule
                     ├── TestPlan ─── PlanModuleSnapshot
                     │                PlanNodeSnapshot
                     │                PlanExecutionRecord
                     ├── TestReview ─── ReviewModuleSnapshot
                     │                  ReviewNodeSnapshot
                     │                  ReviewRecord
                     ├── Bug
                     ├── ApiInterface ─── ApiInterfaceFollow
                     │                    ApiInterfaceChangeLog
                     │                    MockDefinition ─── MockAccessLog
                     ├── ApiScene ─── ApiExecutionRecord ─── ApiReport
                     │                 ApiChangeHistory
                     ├── ApiEnvironment ─── ApiDatasource / ApiEnvironmentVariable / ApiProcessor
                     ├── ApiScheduledTask ─── ApiScheduledTaskExecution
                     ├── ApiComponent（公共组件）
                     └── ApiDebugRecord / ApiImportRecord

### 1.2 核心数据对象概要

| 对象                   | 描述                                  |
| -------------------- | ----------------------------------- |
| User                 | 账号、邮箱、密码哈希、状态                       |
| Role                 | 名称、类型（system/workspace）、作用域（global/workspace）、全量权限标记、权限点列表、是否系统预置 |
| UserRole             | 用户与角色多对多关联                          |
| Workspace            | 名称、描述、状态                            |
| UserWorkspace        | 用户-工作空间关联，含 `workspaceRole` 和加入时间   |
| Project              | 名称、描述、状态、所属工作空间                     |
| TestCaseModule       | 模块树节点（目录/文档）                        |
| TestCaseNode         | 脑图节点（用例/普通），树形结构                    |
| TestPlan             | 计划基本信息                              |
| PlanModuleSnapshot   | 计划-模块快照                             |
| PlanNodeSnapshot     | 计划-节点快照，冗余最后执行结果                    |
| PlanExecutionRecord  | 执行记录                                |
| TestReview           | 评审基本信息（发起人、参与者）                     |
| ReviewModuleSnapshot | 评审-模块快照                             |
| ReviewNodeSnapshot   | 评审-节点快照，冗余最后评审标记                    |
| ReviewRecord         | 评审记录（标记/评论）                         |
| Bug                  | 缺陷信息，状态流转                           |
| ApiInterface         | 接口定义（协议、方法、路径、参数、响应示例），场景与 Mock 的资产底座 |
| ApiInterfaceFollow   | 接口关注关系，支撑「我关注的」视图              |
| ApiInterfaceChangeLog | 接口变更历史，含变更序号                      |
| MockDefinition       | Mock 规则（匹配条件、响应定义、启停、命中统计）      |
| MockAccessLog        | Mock 访问流水，支撑命中统计与审计                |
| ApiScene             | 测试场景（名称、模块、优先级、参数与步骤树载体）        |
| ApiEnvironment       | 环境（默认配置、数据源、全局变量、全局前后置处理器）      |
| ApiComponent         | 公共组件资产（处理器 / 验证器 / 提取器）           |
| ApiDebugRecord       | 服务端调试请求快照与执行结果                     |
| ApiExecutionRecord   | 场景执行历史，与报告关联                       |
| ApiReport            | 场景 / 套件报告结果快照，含分享令牌与有效期           |
| ApiScheduledTask     | 定时任务（类型、Cron、启用状态、绑定对象）           |
| ApiScheduledTaskExecution | 任务触发记录，关联套件报告或导入结果           |
| ApiChangeHistory     | 接口与场景的变更历史（共享追溯载体）                |
| ApiImportRecord      | 导入结果统计与失败明细                        |

### 1.3 数据隔离与生命周期

业务数据通过所属项目 → 工作空间间接隔离，查询时强制附加上下文条件。管理端无此限制。

---

## 2. 接口设计概要

### 2.1 接口风格与约定

* 协议：HTTPS，数据格式 JSON。
* 认证：请求头 `Authorization: Bearer <token>`。
* 接口分类：
  * 管理接口：`/api/admin`，需要系统角色。
  * 工作空间级业务接口：`/api/workspace`，需头 `X-Active-Workspace`。
  * 项目内业务接口：`/api/project`，需头 `X-Active-Project`。
* 上下文传递：工作空间和项目 ID 通过请求头传递，不在 URL 中暴露。
* 通用响应格式：`{ "code": 200, "message": "success", "data": {...} }`

### 2.2 认证与初始化接口

* **初始化状态**：`GET /api/auth/init/status`，返回 `{ initialized: boolean }`，无需认证。
* **初始化设置**：`POST /api/auth/init/setup`，提交管理员密码创建 admin 账号，无需认证。
* **登录**：`POST /api/auth/login`，返回双令牌及用户信息。
* **刷新令牌**：`POST /api/auth/refresh`，通过刷新令牌延长会话。
* **修改密码**：`POST /api/auth/change-password`，需认证。

### 2.3 管理端接口概要

* **用户管理**：CRUD，支持多系统角色分配，状态启用/禁用/锁定。
* **数据概览**：平台指标统计聚合（无独立权限点，可访问管理端即可读取）。
* **工作空间管理**：创建、归档/重新启用、成员管理（含设置空间管理员）。
* **系统角色管理**：CRUD，权限配置（仅系统角色）。
* **权限点树**：获取管理端权限树（按模块分组）。

### 2.4 业务端接口概要

* **我的工作空间**：查看归属列表、切换活跃空间、编辑信息。
* **工作空间成员管理**：邀请、移除、设置空间管理员。
* **项目管理**：CRUD、归档。
* **测试用例**：模块树、脑图节点、用例详情。
* **测试评审**：发起、快照树、模块快照树、评审记录、同步、调整用例。
* **测试计划**：创建、快照树、模块快照树、执行记录、同步、调整用例。
* **缺陷管理**：CRUD、状态流转。
* **接口测试**：调试执行与调试记录、接口定义与导入、Mock 规则与命中统计、测试场景与执行、执行状态、报告与分享、定时任务与执行记录、环境与数据源、公共组件；报告分享访问为免登录公开入口。概要设计见 `docs/02-high-level-design/05-api-testing/02-hld-api-overview.md` 第 5 节。

详细端点定义参见《API 详细设计文档》。

### 2.5 WebSocket 接口概要

* 端点：`/ws/documents/:docId`
* 认证：连接时通过查询参数传递 Token。
* 消息基于 JSON，支持节点增删改移、布局更新。
* 广播至同一文档的在线用户。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-24 | 初始版本 |
| V1.0 | 2026-10-01 | 数据对象与业务端接口概要补入接口测试模块 |
