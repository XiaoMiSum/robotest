# 软件测试平台——API 设计规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 适用范围与权威性

本规范定义 HTTP API、响应体、分页、错误码、上下文请求头和实时通信的项目级契约。

- HTTP 响应、分页和错误码以本文为唯一事实源。
- migoo 框架的类名和配置细节引用 `11-migoo-framework.md`。
- 业务接口的具体字段、权限和业务规则在对应的详细设计文档中定义。
- 当前实现与本文不一致时，必须暂停并由用户确认，不得静默修改契约。

## 2. URL 与上下文

### 2.1 基础路径

| 能力 | 基础路径 | 典型请求头 |
| --- | --- | --- |
| 认证 | `/api/auth` | `Authorization` |
| 系统管理 | `/api/admin` | `Authorization` |
| 我的空间 | `/api/workspaces` | `Authorization` |
| 空间上下文 | `/api/workspace` | `Authorization`、`X-Active-Workspace` |
| 项目上下文 | `/api/project` | `Authorization`、`X-Active-Workspace`、`X-Active-Project` |
| 公共接口 | `/api/public`、`/api/workspace/invitations` | 按接口定义 |

实际新增资源前应先检查相邻 Controller，禁止仅为方便而新增平行基础路径。

### 2.2 上下文规则

- 当前活动 workspace 和 project 的通用传递入口分别是 `X-Active-Workspace` 和 `X-Active-Project`。
- 具体业务是否允许在专用路由或请求体中携带目标上下文 ID，由对应业务详细设计明确；本文不授予业务例外。
- URL 中的 `{id}` 只有在表示被操作资源本身时才属于通用资源 ID，例如 `/api/resources/{id}`。
- 服务端必须校验请求头中的上下文与当前用户的权限关系，不能信任前端已校验的假设。
- 缺少或非法上下文返回统一业务错误，不降级为全局数据查询。

示例：

```http
GET /api/resources?pageNo=1&pageSize=20
Authorization: Bearer <access-token>
X-Active-Workspace: <workspace-uuid>
X-Active-Project: <project-uuid>
```

## 3. HTTP 方法

| 方法 | 用途 | 约束 |
| --- | --- | --- |
| `GET` | 查询资源、列表和统计 | 不得产生业务写入 |
| `POST` | 创建资源或执行明确动作 | 动作接口需定义幂等策略 |
| `PUT` | 更新资源的约定字段集合 | 项目现有接口可采用部分字段更新，但必须在 OpenAPI 中明确 |
| `PATCH` | 局部状态或局部字段更新 | 必须定义可更新字段 |
| `DELETE` | 删除或逻辑删除资源 | 默认使用逻辑删除 |

资源名使用复数名词；动作型接口可以使用动词路径，但必须保持同一业务域内的一致性。

## 4. 统一响应体

所有 JSON API 使用 migoo 的 `Result<T>`：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `number` | 业务码；成功为 `200` |
| `msg` | `string` | 可展示或可国际化的结果消息 |
| `data` | `T` | 成功数据；无数据时可为空 |

### 4.1 适用例外

以下响应不强制使用 `Result<T>`，但必须在 OpenAPI 和接口文档中明确声明：

- SSE 流式响应；
- 文件下载和二进制响应；
- WebSocket 帧；
- Mock 服务的协议专用响应。

这些例外不得扩展为普通 JSON API 随意返回字符串或未包装对象。

### 4.2 HTTP 状态与业务码

当前项目采用框架兼容模式：

- `Result.code` 表达项目业务结果，成功为 `200`；
- 失败使用 10 位业务错误码；
- HTTP 状态码保留框架和传输层语义，不单独承担业务成功判断；
- 当前历史接口可能返回 HTTP 200 + 业务失败码，前端必须同时兼容 HTTP 错误和业务错误；
- 新增或修改接口必须通过契约测试锁定具体行为，不得在 Controller 中自行创造第三套映射。

## 5. 分页协议

### 5.1 请求

```http
GET /api/resources?pageNo=1&pageSize=20
```

| 参数 | 类型 | 规则 |
| --- | --- | --- |
| `pageNo` | `number` | 从 `1` 开始 |
| `pageSize` | `number` | 默认 `20`，最大 `100` |

服务端必须校验页码和页大小，不能将未经校验的分页参数直接拼入 SQL。普通资源使用本页协议；游标分页接口必须单独使用 `cursor/size`，并返回 `items/nextCursor`，不得伪装成 `PageResult<T>`。

### 5.2 响应

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [],
    "total": 0
  }
}
```

统一使用 `PageResult<T>`：

```text
PageResult<T> = { list: T[], total: number }
```

前端不得使用 `records`、`items` 等其他字段名替代 `list`。

## 6. 错误码

错误码统一采用 10 位数字，按项目和模块集中登记。错误码定义在 `server` 的 `ErrorCodeConstants` 中，本文维护号段原则和对外使用规则。

```java
ErrorCode.of(1000003001, "用户不存在");
```

### 6.1 号段要求

- 同一错误含义只能有一个错误码。
- 新错误码必须归属明确的业务域。
- 详细设计文档只能申请号段，不能私自复用已有编号。
- 错误消息不得包含密码、Token、SQL 或内部堆栈信息。
- 废弃错误码保留兼容说明，不能立即复用。

### 6.2 错误响应

```json
{
  "code": 1000003001,
  "msg": "用户不存在",
  "data": null
}
```

参数校验、权限、资源不存在、冲突、系统错误和限流错误必须可区分。

## 7. 请求校验与敏感字段

- Controller 使用 DTO 和框架校验注解完成输入校验。
- 活动上下文、目标资源 ID 和专用业务参数的放置方式由业务详细设计定义；服务端必须校验其归属和权限。
- 密码、Token、密钥和加密字段禁止出现在响应 DTO 中。
- 文件上传必须声明大小、扩展名、内容类型、存储位置和病毒扫描策略。
- 导入外部 URL 的接口必须执行 SSRF 防护，详见 `10-security.md`。

## 8. OpenAPI 契约

- 后端使用 SpringDoc 生成 OpenAPI JSON。
- Controller、DTO 和错误码必须提供足够的 OpenAPI 描述。
- 前端类型由 OpenAPI 基线生成，不手工维护重复的跨端类型。
- API 变更必须同步更新 OpenAPI 基线、前端生成类型、接口测试和详细设计。
- 提交前执行 `web` 的 `pnpm run contract:gen` 和契约一致性检查。

## 9. WebSocket 与实时协议

WebSocket 连接鉴权、通用帧格式、错误帧和连接生命周期统一引用：

```text
docs/06-spec/15-realtime-protocol.md
```

本文只约束实时接口与 HTTP API 的边界：

- 实时协议必须使用 10 位业务错误码或已登记的协议错误码；
- 连接和业务资源权限由实时协议和服务端 Guard 双重校验；
- 实时协议不得重新定义 `Result`、分页或数据库主键规范；
- 具体业务事件和 Payload 由对应详细设计定义。

## 10. API 变更检查清单

- [ ] URL、方法和请求头符合本文
- [ ] 响应使用 `Result<T>`
- [ ] 分页使用 `pageNo/pageSize` 和 `list/total`
- [ ] 错误码为 10 位且已登记
- [ ] 上下文边界符合业务详细设计且服务端完成归属校验
- [ ] OpenAPI、前端类型和接口测试已同步
- [ ] WebSocket 协议、鉴权和权限已同步
- [ ] 详细设计和安全影响已评估

## 11. 参考

- 框架响应、异常和分页实现：`docs/06-spec/11-migoo-framework.md`
- 通用实时协议：`docs/06-spec/15-realtime-protocol.md`
- 安全基线：`docs/06-spec/10-security.md`
- 数据库和分页查询：`docs/06-spec/06-database.md`、`docs/06-spec/04-backend.md`

---

**文档结束**
