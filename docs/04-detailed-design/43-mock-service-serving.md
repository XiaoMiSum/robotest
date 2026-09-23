# 软件测试平台——访问与匹配引擎

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. Mock 访问（免登录）

### 1.1 Mock 响应服务

- **路径**：`{MOCK_BASE_URL}/{path}`（与真实接口同构）
- **方法**：匹配的 HTTP 方法
- **说明**：
  - 不要求平台登录态。
  - 路径支持 `*` 通配符（如 `/api/users/*` 匹配 `/api/users/1`、`/api/users/2`）。
  - 按「请求方法 + 路径 + 匹配条件」顺序取第一条命中规则；同路径同方法多条规则按 `priority` 升序匹配。
  - 无命中时返回 404。
  - 支持响应延迟（`delay_ms`）。
  - 支持变量引用（环境变量、内置函数）动态生成响应内容。
  - 命中后更新 `hit_count` 与 `last_hit_at`，写入 `mock_access_log`。
  - Mock 限流：单路径 QPS 上限可配置（超限返回 429）。

---


## 2. Mock 匹配引擎

Mock 请求处理流程：

```
收到请求
  ↓ 按 method + path 查询启用的 Mock 列表（idx_mock_path_method，路径支持 `*` 通配符）
  ↓ 按 priority 升序排序
  ↓ 逐条检查 match_rules
  ↓ 命中 → 生成响应（变量解析 → 延迟等待 → 返回）
  ↓ 未命中 → 返回 404
```

**匹配规则类型**：

| type | 匹配逻辑 | 示例 |
| ---- | -------- | ---- |
| header | 请求头包含指定 key 且 value 匹配 | `{ "type": "header", "name": "X-Request-Id", "value": ".*" }` |
| param | Query 参数或 REST 路径参数匹配 | `{ "type": "param", "name": "page", "value": "1" }` |
| body | 请求体 JSON 字段匹配（JSONPath） | `{ "type": "body", "name": "$.username", "value": "admin" }` |

> `value` 支持普通值或正则表达式（如 `.*`），与交互设计「值/表达式」单字段输入一致。

**匹配规则为空时**：仅按 method + path 匹配，命中即返回。

**优先级匹配**：同路径同方法存在多条规则时，按 `priority` 升序逐条匹配，取第一条命中规则；`priority` 相同时按创建时间先后排序。

**跟随 API 模式**：Mock 自身未配置响应（`response_body` 为空）且 `follow_api = true` 时，使用关联接口定义的 `response_example` 作为响应。

---


## 3. Mock 服务实现

Mock 服务内嵌于平台应用进程，通过 Spring MVC 的 `/**` 通配路由或独立的 Netty 服务拦截请求。匹配逻辑在 Filter/Interceptor 层实现，优先级高于平台业务路由。

**路由优先级**：
1. 平台业务路由（`/api/**`）。
2. Mock 路由（排除 `/api/**`、`/ws/**`、静态资源）。
3. 未匹配 → 404。

> Mock 服务端口配置见《API 测试基础设施详细设计说明书》6.4（`api-test.mock.port`，为空时复用主端口）。


## 4. Mock 变量解析

Mock 响应体中的变量引用在返回时实时解析：
- `${uuid()}` → 生成唯一 ID。
- `${timestamp()}` → 当前时间戳。
- `${env:VAR}` → 从环境变量读取。
- 未定义变量 → 保留原始 `${...}` 文本。

---

**文档结束**

