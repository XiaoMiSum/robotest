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

- 当前活动 workspace 只能通过 `X-Active-Workspace` 传递。
- 当前活动 project 只能通过 `X-Active-Project` 传递。
- 活动上下文 ID 不得出现在 URL 或请求体中。
- URL 中的 `{id}` 只有在表示被操作资源本身时才允许，例如 `/api/admin/users/{id}`。
- 服务端必须校验请求头中的上下文与当前用户的权限关系，不能信任前端已校验的假设。
- 缺少或非法上下文返回统一业务错误，不降级为全局数据查询。

示例：

```http
GET /api/project/bugs?pageNo=1&pageSize=20
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

- HTTP 状态码表达传输层和资源层语义。
- `Result.code` 表达项目业务结果。
- 认证失败、权限失败、业务校验失败和系统错误必须使用统一错误码注册表。
- 不允许在不同 Controller 中自行定义同义错误码。
- 当前项目中存在 HTTP 状态码与业务码并行的历史行为，新增或修改接口前必须明确选择并通过契约测试锁定。

## 5. 分页协议

### 5.1 请求

```http
GET /api/admin/users?pageNo=1&pageSize=20
```

| 参数 | 类型 | 规则 |
| --- | --- | --- |
| `pageNo` | `number` | 从 `1` 开始 |
| `pageSize` | `number` | 默认 `20`，最大 `100` |

服务端必须校验页码和页大小，不能将未经校验的分页参数直接拼入 SQL。

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
- 请求体不得携带当前活动 workspace/project 上下文。
- 密码、Token、密钥和加密字段禁止出现在响应 DTO 中。
- 文件上传必须声明大小、扩展名、内容类型、存储位置和病毒扫描策略。
- 导入外部 URL 的接口必须执行 SSRF 防护，详见 `10-security.md`。

## 8. OpenAPI 契约

- 后端使用 SpringDoc 生成 OpenAPI JSON。
- Controller、DTO 和错误码必须提供足够的 OpenAPI 描述。
- 前端类型由 OpenAPI 基线生成，不手工维护重复的跨端类型。
- API 变更必须同步更新 OpenAPI 基线、前端生成类型、接口测试和详细设计。
- 提交前执行 `web` 的 `pnpm run contract:gen` 和契约一致性检查。

## 9. WebSocket 与 Yjs

### 9.1 连接与鉴权

连接路径：

```text
/ws/documents/{docId}
```

浏览器 WebSocket 不能稳定设置自定义 `Authorization` Header，因此当前允许通过查询参数传递 Token：

```text
/ws/documents/{docId}?token=<token>
```

这是对普通 HTTP Header 认证的明确例外，必须：

- 只允许短时访问 Token，禁止在日志中记录完整 Token。
- 服务端校验 Token 和用户对文档的访问权限。
- 生产环境限制允许的 Origin，禁止默认使用 `*`。
- 连接断开、权限撤销和 Token 失效后的行为必须可预期。

### 9.2 帧类型

| 帧类型 | 用途 | 服务端行为 |
| --- | --- | --- |
| Yjs 二进制帧 | Sync、Update、Awareness | 先校验可写权限，再按房间转发，不解析业务内容 |
| JSON 文本帧 | 节点、布局等持久化操作 | 先校验权限，再广播并持久化 |
| JSON 错误帧 | 持久化或权限失败 | 返回稳定错误码和可展示消息 |

### 9.3 JSON 文本帧

当前项目使用以下业务操作格式：

```json
{
  "type": "add_node",
  "payload": {
    "data": {
      "id": "node-uuid",
      "parentId": null,
      "title": "新节点",
      "type": "normal",
      "priority": null,
      "aiGenerated": false,
      "sortOrder": 0
    }
  }
}
```

允许的文本操作至少包括：

- `add_node`
- `update_attrs`
- `move_node`
- `delete_node`
- `update_layout`

协议版本、幂等键、消息大小、错误码、重连和顺序策略必须在协议版本中明确。客户端不得通过文本帧绕过 Yjs 的实时同步和权限控制。

## 10. API 变更检查清单

- [ ] URL、方法和请求头符合本文
- [ ] 响应使用 `Result<T>`
- [ ] 分页使用 `pageNo/pageSize` 和 `list/total`
- [ ] 错误码为 10 位且已登记
- [ ] 活动上下文只通过请求头传递
- [ ] OpenAPI、前端类型和接口测试已同步
- [ ] WebSocket 协议、鉴权和权限已同步
- [ ] 详细设计和安全影响已评估

## 11. 参考

- 框架响应、异常和分页实现：`docs/06-spec/11-migoo-framework.md`
- 安全基线：`docs/06-spec/10-security.md`
- 数据库和分页查询：`docs/06-spec/06-database.md`、`docs/06-spec/04-backend.md`

---

**文档结束**
