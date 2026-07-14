# server/AGENTS.md — 后端约定

> 本文件适用于 `server/` 目录。总则见根目录 `AGENTS.md`，全局共享约定以总则为准。

---

## 技术栈

- **框架**：Spring Boot 4.x + Java 21
- **数据访问**：MyBatis-Plus
- **安全**：Spring Security（JWT 双令牌 + RBAC）
- **协作**：WebSocket（Yjs CRDT）

## 环境命令

```bash
# 开发（端口 8080，dev profile）
mvn spring-boot:run -Pdev

# 构建 → target/*.jar（prod profile）
mvn package -Pprod

# 质量门禁
mvn checkstyle:check && mvn test
```

## 架构

分层：**Controller → Service → Repository**

- `controller/{admin,workspace,project}/`：仅路由 + 参数校验（`@Valid`），无业务逻辑
- `service/impl/`：业务逻辑，`@Transactional`，抛出 `BusinessException`
- `repository/`：JPA / MyBatis-Plus 数据访问
- `model/entity/` ↔ `model/dto/request|response/`
- `security/`：JWT 双令牌 + RBAC
- `websocket/`：Yjs CRDT 协作
- `common/`：`ApiResponse<T>` / `PageResult<T>`

**核心子系统**：认证授权 → 上下文（workspace）隔离 → 评审/计划快照 → WebSocket 协作  
（详见 `docs/spec/security.md`、`docs/spec/api.md`、`docs/架构/`、`docs/详细设计/`）

> 详细分层职责参见 `docs/spec/backend.md`。

## 核心约定

| 编号  | 规则                                                    | 检查方式      |
| --- | ----------------------------------------------------- | --------- |
| C2  | Controller 不允许包含业务逻辑，只能路由+校验                        | 代码审查      |
| C3  | 所有业务异常必须抛出 `BusinessException(code, msg)`，不抛原始异常      | 代码审查      |
| C5  | 数据库每表必须有 `id`（自增或雪花）、`created_at`、`updated_at`，禁止物理外键 | 数据库审查     |
| C8  | 单测覆盖率 ≥ 70%                                            | CI        |

## 边界

- 只修改 `server/` 目录下的文件，不碰 `web/` 代码
- Controller 仅负责路由与参数校验，业务逻辑在 Service 层（C2）
- 上下文标识（如 workspaceId）仅通过请求头 `X-Active-Workspace` 传递（C4），不出现在 URL 或请求体中
- 避免新增外部依赖，确有必要时需经团队讨论
