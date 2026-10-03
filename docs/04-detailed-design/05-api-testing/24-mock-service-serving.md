# 软件测试平台——访问与匹配引擎

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. Mock 访问（免登录）

### 1.1 Mock 响应服务

- **路径**：`{base-url}{path}`（与真实接口同构，`path` 以 `/` 开头，如 `http://localhost:18080/api/users/1`；`base-url` 口径见 1.3）
- **方法**：匹配的 HTTP 方法
- **说明**：
  - 不要求平台登录态。
  - 路径支持 `*` 通配符（如 `/api/users/*` 匹配 `/api/users/1`、`/api/users/2`）。
  - 按「请求方法 → 路径 → 匹配条件」逐条召回并检查，取第一条命中规则；同路径同方法的多条规则按 `priority` 升序、创建时间早者优先匹配（见第 2 章）。
  - 未命中或处理异常时不短路，请求放行至平台请求链路，由后续链路返回最终响应，Mock 层不主动构造错误响应。
  - 支持响应延迟（`delay_ms`，见 1.2）。
  - 支持变量引用（环境变量、内置函数）动态生成响应内容（见第 4 章）。
  - 命中后异步更新 `hit_count` 与 `last_hit_at`，并异步写入 `api_mock_access_log`。
  - Mock 限流：单路径 QPS 上限可配置（`robotest.api-test.mock.path-qps`，缺省 50，`<= 0` 关闭），委托框架 `RateLimiter` 以 `mock:{path}` 为键做固定窗口 1 秒计数；仅命中规则的请求参与计数，超限返回 429、`text/plain` 纯文本 `mock rate limit exceeded`，同时记入访问日志但不计命中。
  - Mock 访问链路不产生 10 位业务错误码：未命中放行、超限 429 均按框架全局错误码口径处理。

### 1.2 响应渲染

- **状态码**：取 `response_status`，缺省 200。
- **响应头**：`response_headers` 原样输出；缺少 `Content-Type` 时按 `response_body_type` 补缺省值（`json` → `application/json`、`xml` → `application/xml`、`text` → `text/plain`、`binary` → `application/octet-stream`）。
- **响应体**：按第 4 章规则完成 `${...}` 变量替换后以 UTF-8 写出；无独立模板引擎，模板能力即占位符替换。
- **延迟**：写出前按 `delay_ms` 阻塞等待，缺省 0，上限 60000 毫秒（超出按 60000 毫秒执行）；耗时统计包含延迟时间。

### 1.3 对外访问地址与展示

- **地址构成**：`robotest.api-test.mock.base-url` + 该 Mock 定义的 `path`。`base-url` 缺省 `http://localhost:18080`（环境变量 `MOCK_BASE_URL`）；留空时按 `port`（`MOCK_PORT`，缺省 18080）、再按 `server.port` 推导 `http://localhost:{port}`，值末尾的 `/` 自动去除。
- **控制台展示**：「Mock 访问地址」对话框（`web/src/pages/project/api-testing/mock/MockAddressDialog.vue`，宽 520px，标题「Mock 访问地址」）展示：
  - 说明文案「以下地址可免登录直接访问 Mock 响应」；
  - HTTP 方法标签（GET 为成功色、其余为信息色）与完整访问地址（等宽代码块，可换行显示）；
  - 可选「需要携带的请求头」分区：按 `key` / `value` 逐行展示，未配置请求头时不渲染该分区；
  - 底部「关闭」「复制地址」按钮：复制走剪贴板 API，成功提示「已复制到剪贴板」，失败提示「复制失败，请手动复制」。

---

## 2. Mock 匹配引擎

Mock 请求处理流程：

```
收到请求
  ↓ 按 method + path 召回启用的 Mock（idx_api_mock_path_method）
    先取路径完全相等组，再取路径含 * 的通配组；组内按 priority 升序、created_at 升序
  ↓ 逐条检查 match_rules（全部命中才算命中该规则）
  ↓ 命中 → 构建响应（变量解析 → 延迟等待 → 写出）
  ↓ 未命中 → 放行平台链路
```

**召回范围**：免登录访问不区分项目，按「方法 + 路径」在全部启用定义中召回；仅 `enabled = true` 的定义参与匹配。

**匹配规则类型**：

| type | 匹配逻辑 | 示例 |
| ---- | -------- | ---- |
| header | 请求头包含指定 key 且 value 匹配（头名大小写不敏感） | `{ "type": "header", "name": "X-Request-Id", "value": ".*" }` |
| param | Query 参数匹配（取首个值） | `{ "type": "param", "name": "page", "value": "1" }` |
| body | 请求体 JSON 字段匹配（JSONPath 子集 `$.a.b[0].c`） | `{ "type": "body", "name": "$.username", "value": "admin" }` |

> `value` 支持普通值或正则表达式（如 `.*`），与交互设计「值/表达式」单字段输入一致；按整串正则匹配，正则非法时退化为字符串相等比较；未配置或未知的 `type` 视为不命中。
> 请求体仅在候选规则包含 `body` 类型时才解析缓存，其余请求不读取请求体。

**匹配规则为空时**：仅按 method + path 匹配，命中即返回。

**优先级匹配**：同路径同方法存在多条规则时，按 `priority` 升序逐条匹配，取第一条命中规则；`priority` 相同时创建时间早者优先。路径完全相等的规则整体先于含 `*` 的通配规则参与匹配（通配组无论 `priority` 多小都排在其后）。

**跟随 API 模式**：Mock 自身未配置响应体（`response_body` 为空）且 `follow_api = true` 时，使用关联接口定义的响应示例作为响应；示例中存在的 `status` / `headers` / `body` 一并采用，示例缺失或为空时维持 Mock 自身配置（状态码缺省 200）。

---


## 3. Mock 服务实现

Mock 服务内嵌于平台应用进程，以 Servlet Filter（`MockAccessFilter`）实现：注册于 `/*`，顺序为 `HIGHEST_PRECEDENCE + 100`，先于安全过滤链；命中规则时短路直接写出响应，未命中或处理异常时放行平台请求链路。不使用独立 Netty 服务，也不依赖 Spring MVC 通配路由。

**接入模式**：

1. **独立端口模式（默认）**：`port` 缺省 18080，通过 Tomcat 附加 Connector 监听；仅该端口上的请求参与 Mock 匹配，主端口请求不受影响。
2. **主端口复用模式**：`port` 置空时，按 `excluded-prefixes` 排除业务与静态前缀（缺省 `/api`、`/ws`、`/index.html`、`/assets`），其余路径参与匹配。
3. **总开关**：`access-enabled = false` 时过滤器不装配，仅保留管理端 CRUD。

**未命中处理**：过滤器放行，最终响应由后续安全 / MVC 链路决定，Mock 层不主动返回 404。

**配置项**（前缀 `robotest.api-test.mock`）：

| 配置项 | 缺省值 | 说明 |
| ------ | ------ | ---- |
| `access-enabled` | `true` | 免登录 Mock 访问总开关 |
| `port` | `18080`（`MOCK_PORT`） | 独立监听端口；置空则复用主端口并按 `excluded-prefixes` 排除业务路由 |
| `base-url` | `http://localhost:18080`（`MOCK_BASE_URL`） | 对外地址展示用基础地址，留空按端口推导 |
| `path-qps` | `50`（`MOCK_PATH_QPS`） | 单路径 QPS 上限，`<= 0` 关闭限流 |
| `excluded-prefixes` | `/api,/ws,/index.html,/assets` | 主端口复用模式下不参与 Mock 匹配的路径前缀 |

> 完整配置说明见《API 测试基础设施详细设计说明书》2.6（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）。

**访问日志口径**：命中与限流拒绝均异步写入 `api_mock_access_log`，记录方法、路径、请求头（落库前脱敏）、请求体（仅当候选规则包含 `body` 类型）、响应状态与响应体（截断至 4096 字符）、耗时与客户端 IP（优先取 `X-Forwarded-For` 首段，否则取远端地址）。

---


## 4. Mock 变量解析

Mock 响应体中的变量引用在响应构建时实时解析：

- `${uuid()}` → 生成唯一 ID。
- `${timestamp()}` → 当前时间戳（毫秒）。
- `${env:VAR}` → 从环境变量读取；变量缺失时保留原始 `${...}` 文本。
- 未定义变量 → 保留原始 `${...}` 文本。
- 解析范围仅限 Mock 自身配置的响应体；跟随 API 回退的接口示例按原文返回。管理端「调试」预览复用同一构建逻辑。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐实现：修正召回与优先级规则、未命中放行口径、响应渲染与延迟上限、限流与日志缺省值、对外地址展示、配置前缀与引用章节、表名与索引名 |

---

**文档结束**
