# 安全规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 适用范围

本规范定义平台身份认证、授权、租户隔离、密钥、输入安全、实时通信和审计要求。

- 认证和授权必须在服务端执行，前端路由守卫不能作为安全边界。
- 当前实现与本规范不一致时，按 `docs/00-spec/00-governance/01-overview.md` 的冲突流程暂停并由用户确认。
- 框架具体实现引用 `docs/00-spec/10-engineering/03-migoo-framework.md`，公共 API 契约引用 `docs/00-spec/20-contracts/01-api.md`。

## 2. 认证

### 2.1 HTTP 认证

- 非公开接口使用 `Authorization: Bearer <access-token>`。
- Access Token 短期有效，Refresh Token 通过专用 Header 传递。
- 登录、刷新、退出和匿名接口必须明确列入接口白名单。
- Token 不得出现在 URL、请求体、普通日志或错误响应中。
- 账号禁用、密码重置和明确的角色撤销操作必须有 Token 失效策略。

当前前端使用 `sessionStorage` 保存 Access/Refresh Token。存储方式变更必须经过 XSS 风险评估和用户确认。

### 2.2 WebSocket 认证

WebSocket 目标使用短时、一次性的连接 Ticket：

```text
HTTP API → 申请 ticket
WebSocket handshake → 提交 ticket
```

Ticket 必须绑定用户、连接范围和有效期，并在使用后失效。查询参数 Access Token 仅作为迁移兼容，不是新客户端推荐方案。

无论采用哪种方式，都必须：

- 限制生产 Origin 白名单；
- 对代理、网关和访问日志中的凭证脱敏；
- 校验连接和订阅权限；
- 对可写消息执行独立的业务权限校验。

### 2.3 Token 撤销（登出与踢出）

- 退出登录必须在服务端撤销当前会话的 Access Token 与 Refresh Token，撤销后两者的后续校验均返回 401；
- 账号禁用/锁定、密码重置（自助或管理员）必须撤销该用户已签发的全部 Token，已登录会话即时失效；撤销按 Token 签发时间生效，用户重新登录后不受影响；
- 撤销经 migoo 框架 `UserDetailsBridge` 撤销钩子实现，存储走框架 `StateStore`（Redis）：单 Token 黑名单 `security:token:blacklist:{token}`、用户级签发截止 `security:user:revoked-before:{userId}`（值为撤销时刻，签发时间早于该值的 Token 一律拒绝），TTL 统一 7 天（不短于 Refresh Token 上限有效期）；
- 撤销在每次请求的 Token 校验阶段生效，与 JWT 无状态校验互补；框架可观测信号（`TokenRevokedEvent`、`migoo.security.token.revoked` 指标）默认接入；
- Redis 不可用时撤销检查按失败开放放行（经 6.1 同一失败开放口径），写入失败仅记 WARN。

## 3. 授权和作用域隔离

### 3.1 管理域

管理域接口必须在服务端要求有效管理权限或明确权限码，不能仅依赖前端路由元信息。

### 3.2 租户与资源作用域

- 作用域信息随请求传递，客户端不能通过参数扩大访问范围。
- 服务端必须验证用户与作用域的成员关系、资源归属和角色有效性。
- 找不到成员、角色或归属关系时必须 fail-closed，返回无权限或作用域非法。
- 业务 Service 必须使用统一 Guard 校验资源级权限；不能假设拦截器已经自动追加所有查询条件。
- 任何跨作用域的资源读取、写入和导出都必须有越权测试。

### 3.3 角色变更

当前阶段不新增角色变更后的全量 Token 立即撤销机制；权限按框架现有的重新加载行为处理。密码重置、账号禁用等安全事件的即时失效按 2.3 的签发截止机制执行。

## 4. 密钥和配置

### 4.1 生产要求

以下密钥必须由环境变量或密钥管理服务注入：

```text
DATASOURCE_PASSWORD
REDIS_PASSWORD
JWT_SECRET_KEY
PASSWORD_SECRET
AI_SECRET_KEY
ENV_SECRET_KEY
```

生产环境缺少密钥、密钥长度不足或多个安全域复用同一密钥时，应用必须启动失败，不能回退到仓库默认值。

### 4.2 密钥域和轮换

- JWT、AI、环境变量加密和密码服务密钥必须使用不同密钥域。
- 密钥轮换必须有版本、有效期、灰度和回滚方案。
- 已进入代码、提交记录、日志或工单系统的密钥必须立即轮换。
- 开发示例值不能用于测试和生产环境。

## 5. 数据安全

- 密码使用统一配置的 PasswordEncoder；禁止明文、可逆加密或自定义弱哈希。
- 密码、Token、API Key、Cookie、Authorization、连接串和加密密钥不得进入日志、审计详情或响应 DTO。
- 附件和导出文件必须校验大小、类型、存储路径和访问权限。
- 用户输入必须在服务端校验；前端校验只改善体验。
- HTML/Markdown 等富文本输出必须经过与上下文匹配的编码或清洗。
- 敏感数据导出必须记录操作者、范围、时间和结果。

## 6. 常见攻击防护

### 6.1 登录和公共接口限流

登录、刷新、邀请、密码重置和公开报告接口必须配置可验证的限流：

- 登录至少按 IP 和账号标识组合限制；
- 失败计数和成功后的清理规则必须明确；
- 代理来源 IP 只能信任受控网关写入的 Header；
- 失败响应不得泄露账号是否存在；
- 限流窗口与阈值必须显式声明且集中可审计；计数存储、开关与降级行为必须可配置。

**实施口径（可验证）**：统一使用 migoo 框架（v1.4.0）限流能力，不再保留自定义限流实现：

- **IP 维度（尝试计数）**：各接口在 Controller 方法标注框架 `@RateLimit(type = IP, limit, window)`，由 `RateLimitAspect` 经 `RateLimiter` → `StateStore`（Redis，多实例共享）按固定窗口计数，统计键 `类名#方法名:客户端IP`；窗口/阈值在注解上声明，总开关 `migoo.web.rate-limit.enabled` 可配置；客户端 IP 经框架 `ServletUtils.getClientIP()` 解析（X-Forwarded-For 首段 → X-Real-IP → 来源地址，其可信性依赖受控网关覆盖写入转发头）；
- **账号维度（登录失败计数）**：框架「登录失败锁定」`migoo.security.login-lock.*`（默认启用：连续失败 5 次锁定 10 分钟、滑动窗口 15 分钟内 5 次锁定、递增时长策略并行生效取最严；`failure-window` 空闲 30 分钟清零，认证成功清零连续计数），按账号标识计数，登录前拦截已锁定账号；

各场景默认窗口 / 阈值（计数均按端点独立）：

| 场景 | scope | 维度 | 计数模式 | 默认窗口 / 阈值 |
| --- | --- | --- | --- | --- |
| 登录 | `IP 维度` + `账号维度` | 客户端 IP、账号标识 | IP 尝试计数；账号失败锁定 | 300 秒 10 次；账号连续失败 5 次锁 10 分钟 |
| 刷新令牌 | `IP 维度` | 客户端 IP | 尝试计数 | 60 秒 / 30 次 |
| 邀请公开接口（verify、check-email、join 各端点独立计数） | `IP 维度` | 客户端 IP | 尝试计数 | 60 秒 / 20 次 |
| 报告分享免登录查看 | `IP 维度` | 客户端 IP | 尝试计数 | 60 秒 / 60 次 |
| 系统初始化 setup | `IP 维度` | 客户端 IP | 尝试计数 | 600 秒 / 5 次 |
| 密码设置（自助改密、管理员重置，各端点独立计数） | `IP 维度` | 客户端 IP | 尝试计数 | 300 秒 / 10 次 |

- 超限统一返回框架全局错误码 429（`TOO_MANY_REQUESTS`，i18n 消息 `common.too.many.requests`），登录账号维度锁定命中返回 423（`ACCOUNT_LOCKED`）；不区分触发键，不泄露账号是否存在；认证失败（含账号不存在）计入账号失败锁定，避免枚举差异；
- 成功登录清零账号连续失败计数，IP 维度为固定窗口尝试计数、窗口内不清理；
- 客户端 IP 解析结果的可信性依赖受控网关覆盖写入转发头；
- 计数与锁定状态经框架 `StateStore`（Redis）存储，应用注册失败开放装饰器：Redis 不可用时放行并记 WARN（限流、登录失败锁定与 Token 撤销检查同一口径），避免缓存故障阻断认证链路；是否改为失败关闭见待确认 DEC-015。

### 6.2 CSRF 与 CORS

适用边界已定案（backlog SEC-011）：**Bearer Header 场景默认不启用 CSRF；公共接口使用限流、Origin、一次性 Token 和审计；未来出现 Cookie 场景再启用 CSRF。**

- **CSRF**：认证仅依赖 `Authorization` Bearer Header，不依赖浏览器自动携带的认证 Cookie，因此全站默认不启用 CSRF——服务端安全链显式关闭（migoo 框架 `MiGooWebSecurityFilterChainConfiguration`），属决策而非疏漏；业务接口不得改用 Cookie 承载凭证。
- **Cookie 场景**：若未来引入 Cookie 会话或双提交 Cookie，必须先更新本文，启用 CSRF 防护（同步令牌或双重提交）并重新评估 CORS 与同源约束，之后才可编码。
- **CORS**：前端开发经 Vite 代理、生产为前后端同源部署，服务端不注册任何 CORS 配置，跨源响应默认不开放；确有跨源需求时必须使用明确 Origin 白名单，禁止生产使用 `*` Origin。
- **公共接口**（登录、邀请、公开分享、WebSocket）：限流按 6.1 执行，Origin 约束按上述 CORS 白名单口径执行，一次性 Token 采用 DEC-007 方案 B（实现状态见第 9 节），并保留操作审计；WebSocket Origin 白名单已随 SEC-005 取消。

### 6.3 SSRF 和文件上传

- 外部 URL 拉取必须阻止环回、内网、链路本地和云元数据地址。
- URL 协议、端口、DNS 解析结果和重定向目标都要校验。
- 上传文件不能仅依赖扩展名判断类型。
- 附件下载必须重新校验资源归属，不接受用户提供的任意本地路径。

### 6.4 SQL、日志和依赖

- 禁止字符串拼接用户输入生成 SQL。
- 生产环境不得使用会输出 SQL 参数的 `StdOutImpl` 或高敏感级别日志。
- 日志必须脱敏嵌套 DTO、请求 Header、查询参数和异常对象。
- 依赖漏洞、许可证和密钥扫描属于发布前检查项。

## 7. WebSocket 安全

- 握手阶段校验 Ticket、用户身份、连接范围和 Origin。
- 加入房间或订阅主题后仍需在可写操作前校验业务权限。
- 通用 JSON 帧和二进制帧必须分别定义大小、类型、频率和权限。
- 写操作必须先鉴权、再广播或持久化；不能先广播后再拒绝。
- 错误帧返回稳定错误码，不把数据库异常原文直接发送给客户端。
- 消息大小、频率、顺序、重连和版本兼容规则见 `docs/00-spec/20-contracts/03-realtime-protocol.md`。

## 8. 审计和日志

### 8.1 审计范围

以下操作必须审计：

- 创建、修改、删除和状态流转；
- 密码重置、账号禁用和角色变更；
- 作用域成员、权限和资源归属变更；
- 导入、导出、附件下载和 AI 写操作；
- 管理员访问和公共 Token 使用。

### 8.2 审计内容

审计记录至少包含：

```text
operatorId / operatorName
timestamp
requestId
ip / userAgent
operation
entityType / entityId
result
changes
```

### 8.3 脱敏和保留

- 审计内容采用字段白名单或递归脱敏。
- 至少过滤 `password`、`newPassword`、`token`、`secret`、`apiKey`、`authorization`、`cookie`、`credential` 和连接信息。
- 日志和审计不得记录密码、Token 或完整请求体。
- 保留期限、存储位置、访问权限和防篡改方案必须由部署环境明确。
- 审计写入失败是否阻断业务，必须按操作风险分别定义。

## 9. 当前实现整改清单

以下项目需要在代码/配置层单独整改，完成前不得对外宣称已满足：

- [x] 生产配置移除可预测默认密钥，并实现 fail-fast
- [x] 管理端 API 增加服务端角色/权限校验
- [x] 作用域上下文校验改为 fail-closed
- [x] WebSocket 可写帧转发前校验编辑权限
- [x] 登录及公共接口增加可验证限流
- [ ] 退出登录与账号禁用/密码重置的服务端 Token 撤销（见 2.3）
- [ ] 审计覆盖敏感操作并递归脱敏
- [x] 生产关闭 SQL 参数输出和过高日志级别
- [x] 修复审计和日志中的敏感字段泄露
- ~~配置 WebSocket Origin 白名单~~（已取消：backlog SEC-005，2026-09-24 确认不需要）
- [ ] 落地一次性 WebSocket Ticket（DEC-007 已定案方案 B：一次性、短时、单文档；当前仍为 URL Token，实现前 URL Token 继续在网关日志脱敏，见 SEC-013）

## 10. 参考

- API：`docs/00-spec/20-contracts/01-api.md`
- 通用实时协议：`docs/00-spec/20-contracts/03-realtime-protocol.md`
- migoo 安全能力：`docs/00-spec/10-engineering/03-migoo-framework.md`
- 质量门禁：`docs/00-spec/30-quality-delivery/01-quality.md`
- 部署密钥：`docs/00-spec/30-quality-delivery/03-deploy.md`、`docs/00-spec/30-quality-delivery/04-deployment-runbook.md`

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-09-28 | 新增 2.3 Token 撤销；6.1 限流实施口径改为 migoo 框架 `@RateLimit` + 登录失败锁定，超限错误码改用框架 429/423；3.3 与 2.3 撤销口径对齐；登记 Token 撤销整改项 |

---

**文档结束**
