# 软件测试平台——审计查询详细设计说明书

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台的**审计查询**进行详细设计，定义审计日志的分页查询、统计聚合接口与数据结构，为开发实现提供完整依据。对应 `docs/重构方案/01-系统管理重构方案.md` §3.1.2「审计 → Decorator + Observer 消费」的消费端落点。

### 1.2 范围

- **审计写入（注解路径）**：沿用既有 `@AuditOperation` + `AuditLogAspect`（Decorator 样板），护栏不变：REQUIRES_NEW 独立事务、脱敏、首个 UUID 参数推断 entityId 语义不变；仅将「组装后写入 + 发布事件」抽取为共享 `AuditLogWriter`，切面与登录审计共用（见 4.3）。
- **审计写入（登录路径）**：登录成功后写入 `operation='LOGIN'` 记录（含登录 IP），供数据概览活跃统计与后续审计页消费（见 4.3）。
- **审计查询**：分页查询 `GET /api/admin/audit-logs`（新增 `operation` 过滤，用于按登录/业务操作区分）、聚合统计 `GET /api/admin/audit-logs/aggregate` 两个端点。

### 1.3 参考资料

- 《系统管理重构方案》（`docs/重构方案/01-系统管理重构方案.md`，§3.1.2）
- 《基础设施与框架层重构方案》（`docs/重构方案/07-基础设施与框架层重构方案.md`）

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| entityType | 审计实体类型（如 Bug/TestPlan/AiConfig），对应 `@AuditOperation.entityType` |

---

## 2. 数据设计

无新表、无 DDL 变更。复用 `sys_audit_log`（`server/src/main/resources/db/schema.sql:87`），既有索引 `idx_sys_audit_log_operator` / `idx_sys_audit_log_entity` / `idx_sys_audit_log_created` 已覆盖查询条件（operator/entity 过滤 + created_at 排序），满足 C9（单表索引 3 个 ≤ 5）。

**operation 取值**：`CREATE` / `UPDATE` / `DELETE`（注解路径）+ `LOGIN`（登录路径增量）。`LOGIN` 记录结构：`entity_type='User'`、`entity_id=operator_id=用户 ID`、`operator_name=登录用户名`、`request_ip=登录 IP`、`changes={}`；`created_at` 落在今日/近 14 日的 `LOGIN` 记录即数据概览「今日活跃 / 近 14 日活跃用户」的统计源（口径见 `docs/04-detailed-design/01-readme.md` §3.2）。

登录审计与 14 日聚合均以 `created_at` 为窗口，既有 `idx_sys_audit_log_created` 覆盖，**不新增索引**。

### 2.1 权限点（新增种子）

新增系统管理权限点 `audit:*`（模块=审计日志，scope=global），DDL 种子如下（随 schema.sql 补充 INSERT）：

```sql
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000021', 'audit',      '审计日志',    NULL, '审计日志', 'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000022', 'audit:view', '查看审计日志', 'audit', '审计日志', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
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
| pageNo | Integer | 是 | 页码（默认 1） |
| pageSize | Integer | 是 | 每页条数（默认 20） |
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
      "id": "b0000000000000000000000001",
      "operatorId": "a0000000000000000000000001",
      "operatorName": "admin",
      "operation": "UPDATE",
      "entityType": "AiConfig",
      "entityId": "a0000000000000000000000002",
      "changes": "{\"apiKey\":\"***\"}",
      "requestIp": "10.0.0.1",
      "createdAt": "2026-09-13T10:00:00"
    }
  ],
  "total": 128
}
```

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
public interface AuditQueryService {           // 新 Port：查询/聚合
    PageResult<AuditLogRespDTO> page(String operatorName, String entityType,
            String operation, LocalDate beginTime, LocalDate endTime,
            Integer pageNo, Integer pageSize);  // operation 为新增可选过滤
    Map<String, Long> aggregate(String entityType, LocalDate from);
}
```

### 4.2 AuditRecordedEvent（Observer 消费）

`AuditLogAspect` 在写入 `sys_audit_log` 成功后发布 Spring 事件 `AuditRecordedEvent`，供后续增量订阅方（如审计报表、通知推送）消费。本切片仅发布事件、无订阅方，不改变既有 Decorator 写入语义。

```java
public record AuditRecordedEvent(UUID auditLogId, String entityType,
        UUID entityId, String operation, UUID operatorId) {}
```

### 4.3 登录审计写入

**动机**：数据概览需要「今日活跃 / 近 14 日活跃用户」统计，且需记录登录用户 IP；登录发生在鉴权之前，`SecurityContextHolder` 中无 `LoginUser`，注解切面路径无法推断 operator，故新增显式登录写入路径，并与切面共享写入设施（「结合审计功能重新设计」的落点）。

**共享组件抽取**（`framework/audit/`）：

```java
/** 审计写入器：REQUIRES_NEW 独立事务写入 + 成功后发布 AuditRecordedEvent，失败仅告警不外抛 */
@Component
public class AuditLogWriter {
    void write(AuditLog record);   // AuditLogAspect 注解路径与登录路径共用
}

/** 客户端 IP 解析：X-Forwarded-For 首段 → X-Real-IP → remoteAddr（原 AuditLogAspect#getClientIp 下沉共享） */
@Component
public class ClientIpResolver {
    String resolve(HttpServletRequest request);
}
```

**写入时机与语义**：

1. `AuthController.login` 认证成功后，顺序委托 `LoginAuditService.recordLogin(userId, username, request)`（Controller 只做两次委托，业务与容错在 Service，C2）。
2. `recordLogin` 组装 `AuditLog`：`operation='LOGIN'`、`entity_type='User'`、`entity_id=operator_id=用户 ID`、`operator_name=登录用户名`、`request_ip=ClientIpResolver.resolve(...)`、`changes={}`，经 `AuditLogWriter.write` 写入。
3. **登录审计失败不得影响登录**：`AuditLogWriter` 捕获异常仅 `log.warn`（与切面既有语义一致）。
4. `POST /api/auth/refresh` 不记录；同一用户当日多次登录各记 1 条（「人次」口径）。

**查询侧影响**：分页查询新增 `operation` 过滤（3.2、4.1），登录记录可通过 `operation=LOGIN&entityType=User` 检索；响应结构与 `requestIp` 字段不变。

---

## 5. 实施说明

- 新增 `service/admin/audit/`：`AuditQueryService`（接口）、`AuditQueryServiceImpl`（实现）。
- 新增 `controller/admin/AuditLogController`（仅路由 + `@Valid`，C2）。
- `AuditLogAspect` 追加 `ApplicationEventPublisher.publishEvent(...)`（写入不变量，失败不影响主事务）；当前版本将写入 + 事件发布下沉至共享 `AuditLogWriter`，切面行为不变。
- 本版本新增：`framework/audit/ClientIpResolver`、`LoginAuditService`（recordLogin 组装与容错）、`AuditLogController/AuditQueryService/AuditLogMapper` 增加 `operation` 过滤参数。
- `schema.sql` 追加 `audit:*` 权限种子（2.1）。
- 前端管理端审计页属后续增量，本切片不涉及。