# 软件测试平台——审计查询详细设计说明书

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台的**审计查询**进行详细设计，定义审计日志的分页查询、统计聚合接口与数据结构，为开发实现提供完整依据。对应《系统管理重构方案》§3.1.2「审计 → Decorator + Observer 消费」的消费端落点。

### 1.2 范围

- **审计写入（注解路径）**：使用框架 `@AuditLog(action = "<operation>:<entityType>")` 标注业务方法（当前实现 4 处，均位于接口测试环境 Service 层），方法或类执行成功/失败后由框架 `AuditLogAspect` 发布 `AuditLogEvent`（入参已由框架脱敏），工程 `AuditLogEventListener` 接收并组装 `sys_audit_log` 记录，经共享 `AuditLogWriter` 以 REQUIRES_NEW 独立事务写入（组装口径见 4.3）；监听器与登录审计共用写入设施。
- **审计写入（登录路径）**：登录成功后写入 `operation='LOGIN'` 记录（含登录 IP），供数据概览活跃统计与后续审计页消费（见 4.4）。
- **审计查询**：分页查询 `GET /api/admin/audit-logs`（支持 `operation` 过滤，用于按登录/业务操作区分）、聚合统计 `GET /api/admin/audit-logs/aggregate` 两个端点。

### 1.3 参考资料

- 《系统管理重构方案》（§3.1.2「审计 → Decorator + Observer 消费」）
- 《基础设施与框架层重构方案》

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| entityType | 审计实体类型（如 Bug/TestPlan/User），取自 `@AuditLog(action = "<operation>:<entityType>")` 的后半段 |
| AuditLogEvent | 框架在 `@AuditLog` 标注方法执行成功或失败后发布的事件，字段为 `operator`（`id(username)`）、`clientIp`、`path`（路由模板）、`action`、`success`、`errorMessage`、`params`（脱敏后的入参 JSON 数组，无入参时 `null`） |

---

## 2. 数据设计

无新表、无 DDL 变更。复用 `sys_audit_log`（`server/src/main/resources/db/schema.sql:82`），既有索引 `idx_sys_audit_log_operator` / `idx_sys_audit_log_entity` / `idx_sys_audit_log_created` 覆盖 entity 过滤与 `created_at` 排序等主要查询条件，满足 C9（单表索引 3 个 ≤ 5）。

**operation 取值**：`CREATE` / `UPDATE` / `DELETE`（`@AuditLog` 注解路径）+ `LOGIN`（登录路径）。`LOGIN` 记录结构：`entity_type='User'`、`entity_id=operator_id=用户 ID`、`operator_name=登录用户名`、`request_ip=登录 IP`、`changes={}`；`created_at` 落在今日/近 14 日的 `LOGIN` 记录即数据概览「今日活跃 / 近 14 日活跃用户」的统计源（口径见 `docs/04-detailed-design/01-system-management/07-system-management-dashboard.md` §1 字段口径表）。

登录审计与 14 日聚合均以 `created_at` 为窗口，既有 `idx_sys_audit_log_created` 覆盖，**不新增索引**。

### 2.1 权限点种子

系统管理权限点 `audit:*`（模块=审计日志，顶层模块=系统管理，scope=global）的 DDL 种子如下（已随 `server/src/main/resources/db/schema.sql` §17.2 落地）：

```sql
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000021', 'audit',      '审计日志',    NULL, '审计日志', '系统管理', 'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000022', 'audit:view', '查看审计日志', 'audit', '审计日志', '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
```

> 全局系统管理权限（scope=global），不依赖工作空间上下文（C4），审计记录为全平台级，不含 workspace 隔离维度。

---

## 3. 接口详细设计

### 3.1 通用约定

- 鉴权：JWT + `@PreAuthorize("hasAuthority('audit:view')")`。
- 响应结构沿用平台通用 `Result<T>`，以下请求/响应示例仅展示 `data` 字段。
- 分页参数沿用通用 `PageParam`（pageNo/pageSize）。

### 3.2 审计日志分页查询

**接口**：`GET /api/admin/audit-logs`
**方法**：GET

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| pageNo | Integer | 否 | 页码，从 1 开始，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 20，最大 100 |
| operatorName | String | 否 | 操作人名称模糊过滤 |
| entityType | String | 否 | 实体类型精确过滤 |
| operation | String | 否 | 操作类型精确过滤（如 `LOGIN` / `CREATE` / `UPDATE` / `DELETE`） |
| beginTime | LocalDate | 否 | 起始日期过滤（created_at >= 当日 00:00:00） |
| endTime | LocalDate | 否 | 结束日期过滤（created_at <= 当日 23:59:59） |

**响应示例（data）**

```json
{
  "list": [
    {
      "id": "b0000000-0000-0000-0000-000000000001",
      "operatorId": "a0000000-0000-0000-0000-000000000001",
      "operatorName": "admin",
      "operation": "UPDATE",
      "entityType": "User",
      "entityId": "a0000000-0000-0000-0000-000000000002",
      "changes": {
        "params": [
          "a0000000-0000-0000-0000-000000000002",
          { "username": "admin", "password": "******" }
        ]
      },
      "requestIp": "10.0.0.1",
      "createdAt": "2026-09-13T10:00:00Z"
    }
  ],
  "total": 128
}
```

`changes` 为 JSON 对象而非字符串：注解路径存 `{"params": [...]}`（框架已脱敏的入参 JSON 数组，无入参时为 `{}`），登录路径存 `{}`（组装口径见 4.3、4.4）。

### 3.3 审计统计聚合

**接口**：`GET /api/admin/audit-logs/aggregate`
**方法**：GET

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| entityType | String | 是 | 按实体类型聚合 |
| from | LocalDate | 是 | 统计起始日期（含） |

**响应示例（data）**

```json
{
  "2026-09-10": 12,
  "2026-09-11": 20,
  "2026-09-12": 35,
  "2026-09-13": 28
}
```

按 `created_at` 按日分组统计操作次数，返回日期 → 次数的有序映射（Key 为 `yyyy-MM-dd` 字符串）。

---

## 4. 业务逻辑设计

### 4.1 AuditQueryService 端口

```java
public interface AuditQueryService {           // 查询/聚合端口
    PageResult<AuditLogRespDTO> page(String operatorName, String entityType,
            String operation, LocalDate beginTime, LocalDate endTime,
            Integer pageNo, Integer pageSize);  // operation 为可选过滤（LOGIN/CREATE/UPDATE/DELETE）
    Map<String, Long> aggregate(String entityType, LocalDate from);
}
```

### 4.2 AuditRecordedEvent（Observer 消费）

`AuditLogWriter` 在 `sys_audit_log` 插入成功后（REQUIRES_NEW 独立事务内）发布 Spring 事件 `AuditRecordedEvent`，供后续增量订阅方（如审计报表、通知推送）消费。当前仅发布事件、无订阅方，不改变既有写入语义。

```java
public record AuditRecordedEvent(UUID auditLogId, String entityType,
        UUID entityId, String operation, UUID operatorId) {}
```

### 4.3 AuditLogEventListener（注解路径写入）

**职责**：监听框架 `AuditLogEvent`（`@AuditLog` 标注的方法或类执行后由框架 `AuditLogAspect` 发布，成功与失败均发布），组装 `AuditLog` 实体并经 `AuditLogWriter` 落库，写入失败仅告警不外抛，不影响业务返回。

**组装口径**：

| 字段 | 来源 |
| ---- | ---- |
| `operation` | `action` 按 `:` 切分的前半段（如 `CREATE` / `UPDATE` / `DELETE`） |
| `entity_type` | `action` 按 `:` 切分的后半段（如 `User` / `Project`） |
| `operator_id` / `operator_name` | `operator` 形如 `id(username)`，解析出 UUID 与用户名；`anonymous` 不落库 |
| `entity_id` | `params` JSON 数组中首个 UUID 形态元素（业务方法的 UUID 形参，与原切面「第一个 UUID 参数」一致）；无则为 `NULL` |
| `changes` | `{"params": [...]}`（事件 `params` 的 JSON 数组）；`params` 为 `null` 时存 `{}`。参数名不保留，为已知行为变化 |
| `request_ip` | 事件 `clientIp` |
| `created_at` | 事件发生时刻 |

**护栏**：仅 `event.success() == true` 的事件落库（`sys_audit_log` 无成功/失败列，失败记录不入库）；`operator` 不匹配 `id(username)`（如 `anonymous`）或 `action` 不含 `:` 的事件跳过不落库；写入在 `AuditLogWriter` 的 REQUIRES_NEW 独立事务中执行；入参脱敏由框架切面在发布事件前完成（`@Sensitive` 掩码 + 敏感关键词字段 `******`）。`sys_audit_log` 无 DDL 变更。

### 4.4 登录审计写入

**动机**：数据概览需要「今日活跃 / 近 14 日活跃用户」统计，且需记录登录 IP；登录发生在鉴权之前，`SecurityContextHolder` 中无 `LoginUser`，`@AuditLog` 注解路径无法推断 operator，故登录走显式写入路径，并与注解路径共享写入设施。

**共享组件抽取**（`framework/audit/`）：

```java
/** 审计写入器：REQUIRES_NEW 独立事务写入 + 成功后发布 AuditRecordedEvent，失败仅告警不外抛 */
@Component
public class AuditLogWriter {
    void write(AuditLog record);   // AuditLogEventListener 注解路径与登录路径共用
}

/** 客户端 IP 解析：X-Forwarded-For 首段 → X-Real-IP → remoteAddr */
@Component
public class ClientIpResolver {
    String resolve(HttpServletRequest request);
}
```

**写入时机与语义**：

1. `AuthController.login` 认证成功后，顺序委托 `LoginAuditService.recordLogin(userId, username, request)`（Controller 只做两次委托，业务与容错在 Service，C2）。
2. `recordLogin` 组装 `AuditLog`：`operation='LOGIN'`、`entity_type='User'`、`entity_id=operator_id=用户 ID`、`operator_name=登录用户名`、`request_ip=ClientIpResolver.resolve(...)`、`changes={}`，经 `AuditLogWriter.write` 写入。
3. **登录审计失败不得影响登录**：`AuditLogWriter` 捕获异常仅 `log.warn`（与注解路径语义一致）。
4. `POST /api/auth/refresh` 不记录；同一用户当日多次登录各记 1 条（「人次」口径）。

**查询侧影响**：分页查询支持 `operation` 过滤（3.2、4.1），登录记录可通过 `operation=LOGIN&entityType=User` 检索；响应结构与 `requestIp` 字段不变。

---

## 5. 实施说明

- `service/admin/audit/`：`AuditQueryService`（查询端口）、`AuditQueryServiceImpl`（实现）、`LoginAuditService` / `LoginAuditServiceImpl`（登录审计组装与容错，4.4）。
- `controller/admin/AuditLogController`：仅路由与参数校验，两个端点均 `@PreAuthorize("hasAuthority('audit:view')")`（C2）。
- `framework/audit/`：`AuditLogEventListener`（监听框架 `AuditLogEvent`，按 4.3 组装并经 `AuditLogWriter` 落库）、`AuditLogWriter`（REQUIRES_NEW 写入 + 成功后发布 `AuditRecordedEvent`）、`ClientIpResolver`、`AuditRecordedEvent`；业务方法统一使用框架 `@AuditLog(action = "<operation>:<entityType>")`（当前实现 4 处，`recordParams` 默认开启）。
- `repository/admin/AuditLogMapper`：分页条件查询（含 `operation` 过滤）、按 `entityType`+起始日期的按日聚合，以及数据概览消费的登录人次 / 近 14 日去重统计。
- `db/schema.sql`：包含 `audit:*` 权限种子（2.1），`sys_audit_log` 无 DDL 变更。
- 前端现状见第 6 章。

---

## 6. 前端设计

**当前平台前端无审计页面**，本分册不定义任何前端页面结构与交互：

- `web/src/router/` 无审计路由、菜单项（`meta.menu`）与面包屑；
- `web/src/pages/`、`web/src/composables/`、`web/src/stores/` 下无审计相关页面组件、组合式逻辑与状态；
- `web/src/services/` 未封装审计接口调用；前端仅在 `web/src/types/generated/contract.d.ts` 中保留 OpenAPI 自动生成的契约类型（`/api/admin/audit-logs` 的 `getAuditLogPage` 与 `/api/admin/audit-logs/aggregate` 的 `aggregate`）。

审计查询当前仅以 3.2、3.3 的后端 API 形式提供；管理端审计页属后续增量，实现前本文不描述其页面结构、功能点与权限挂载。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 与实现对齐：修正权限种子 DDL、响应示例与事件发布归属，移除失效文档引用，补充前端现状说明。 |
