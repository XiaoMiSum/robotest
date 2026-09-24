# 框架集成规范

**文档版本**：V1.1
**日期**：2026-09-24
**状态**：已发布

---

## 1. 适用范围

本文定义采用 `migoo-spring-boot-starter` 时的组件选择、版本管理、框架适配边界和升级验证要求。

本文只规定如何选择、配置、验证和约束框架能力，不复制完整的组件 API 手册。API 速查见 [`17-migoo-api-reference.md`](17-migoo-api-reference.md)；官方 API、配置项和示例以官方文档及当前锁定版本为准。

业务模型、业务事件、业务权限和持久化规则由对应的详细设计文档定义。

## 2. 组件和版本基线

| 能力 | 约定 |
| --- | --- |
| 框架 | `migoo-spring-boot-starter` |
| 版本 | `1.3.18`，由 Maven BOM 管理 |
| Java | 21 |
| 数据访问 | `migoo-spring-boot-starter-mybatis` |
| Web | `migoo-spring-boot-starter-web` |
| 认证 | `migoo-spring-boot-starter-security` |
| WebSocket | `migoo-spring-boot-starter-websocket` |
| Redis | 按需使用 Redis 能力 |
| 消息队列 | 按需使用 MQ 能力 |

框架版本升级必须同时验证响应、异常、分页、数据访问、认证、Web、实时通信、Redis 和消息队列能力，不允许只修改依赖版本。

## 3. 官方组件使用手册

以下 URL 来自 migoo 官方文档站。新增组件或升级版本时，应先核对对应页面和发布说明。

| 组件 | 官方使用手册 | 主要内容 |
| --- | --- | --- |
| 总览 | [MiGoo Spring Boot Framework](https://xiaomisum.github.io/springboot-migoo-framework/) | 框架简介、BOM、组件清单和快速开始 |
| Common | [Common 文档](https://xiaomisum.github.io/springboot-migoo-framework/common.html) | `Result`、`ErrorCode`、异常、分页、校验和工具类 |
| Web | [Web 文档](https://xiaomisum.github.io/springboot-migoo-framework/web.html) | 全局异常、响应封装、TraceId、i18n、CORS 和请求体缓存 |
| Security | [Security 文档](https://xiaomisum.github.io/springboot-migoo-framework/security.html) | JWT、OAuth2、用户加载、Token 刷新和 TOTP |
| WebSocket | [WebSocket 文档](https://xiaomisum.github.io/springboot-migoo-framework/websocket.html) | 连接管理、Token 认证、房间、会话和分布式模式 |
| MyBatis | [MyBatis 文档](https://xiaomisum.github.io/springboot-migoo-framework/mybatis.html) | MyBatis-Plus 增强、实体基类、Mapper、Wrapper、分页和类型处理器 |
| Redis | [Redis 文档](https://xiaomisum.github.io/springboot-migoo-framework/redis.html) | Redis 连接、`RedisKit`、Key 模板、过期策略和分布式模式 |
| MQ | [MQ 文档](https://xiaomisum.github.io/springboot-migoo-framework/mq.html) | Stream、Pub/Sub、ACK、重试、死信和幂等消费 |
| 发布说明 | [GitHub Releases](https://github.com/XiaoMiSum/springboot-migoo-framework/releases) | 版本变更、兼容性和迁移注意事项 |

官方文档是组件 API 的参考来源；本文只规定如何选择、配置、验证和约束这些能力。

## 4. 框架适配边界

### 4.1 Common

工程复用框架的 `Result`、`ErrorCode`、业务异常工具、分页和通用工具，不创建平行的响应包装或异常体系。

适配要求：

- 响应字段和 HTTP 状态遵循 `05-api.md`；
- 错误码统一登记在 `ErrorCodeConstants`，使用 10 位编号；
- 业务异常统一通过 `ServiceExceptionUtil.get(ErrorCodeConstants.X)` 抛出；
- 分页统一使用 `pageNo/pageSize`，对外使用 `list/total`；
- 参数校验、i18n 和工具类优先复用框架能力。

### 4.2 Web

Web 组件负责全局异常、响应处理、TraceId、请求上下文、i18n、CORS 和请求体缓存等框架级能力。本文只补充业务错误码、DTO 校验、安全响应头、审计脱敏和 SSE/文件/实时通信等特殊响应适配。

生产环境不得依赖宽松默认配置，尤其是 CORS、请求体缓存和敏感日志配置。

### 4.3 MyBatis

业务实体使用框架公共基类，Mapper 使用 `BaseMapperX` 或其工程扩展；复杂和可复用查询下沉 Mapper，简单动态过滤可以由 Service 组合。

适配要求：

- `BaseUuidDO` 的 UUID 策略由框架默认实现决定，不在通用规范中强制 UUID v4/v7；
- `LambdaQueryWrapperX` 和 `LambdaUpdateWrapperX` 按 `04-backend.md` 的 C11 规则使用；
- 查询结果只用于校验，不作为整行 `updateById` 载体；
- TypeHandler 的类型、方言、密钥、迁移和日志脱敏必须经过验证；
- Entity 不承载跨层业务方法。

### 4.4 MapStruct

MapStruct 不属于 migoo Starter 的通用 API。统一要求：

- 转换器放在 `model/convert/`；
- 使用 Spring Bean 注入，不使用静态 `INSTANCE`；
- 纯字段映射使用 MapStruct，业务判断、权限和额外查询留在 Service；
- 转换器输入输出 DTO 变更必须有编译和转换测试。

### 4.5 Security

Security 组件提供 JWT、用户加载、Token 校验和角色权限等框架能力。接入方负责认证模式、用户加载适配、密钥和有效期配置、业务错误码、资源级 Guard 以及登录和权限审计。

框架认证成功不等于业务授权成功。管理域、业务域和实时资源必须在服务端重新校验角色、成员关系和资源归属。通用边界见 `10-security.md`。

### 4.6 WebSocket

WebSocket 组件提供连接管理、Token 认证、会话、房间和分布式广播能力。接入要求：

- 端点、Handler、会话和房间策略以当前运行配置为准；
- Handler 只处理连接生命周期、帧分发和房间广播；
- 房间/主题在加入时校验，写入操作在广播或持久化前再次校验；
- JSON 和二进制消息分别定义大小、编码、频率和权限；
- 生产使用短时一次性 Ticket、Origin 白名单和日志脱敏；
- 分布式模式明确 Redis 依赖、顺序和故障降级。

通用协议见 `15-realtime-protocol.md`，接入设计见 `docs/04-detailed-design/78-realtime-websocket.md`；业务事件不在本文定义。

### 4.7 Redis

Redis 能力用于缓存、限流、会话、短期 Ticket 和分布式协作。接入方必须集中登记 Key 命名、TTL、值类型、失效/重建策略、锁保护、敏感值处理和 Redis 不可用时的降级行为。

### 4.8 MQ

接入方按可靠性选择 Stream 或 Pub/Sub：Stream 用于可靠投递、ACK、重试、死信和幂等消费；Pub/Sub 用于广播，不作为唯一业务事实源。消息模型、Channel、幂等键、重试和告警必须集中登记。

## 5. 组件选择和配置原则

- 只引入实际使用的 Starter；
- 依赖版本由 Maven BOM 统一管理；
- 生产配置不依赖开发默认值；
- Redis、MQ、分布式 WebSocket 和 OAuth2 等能力按部署需求启用；
- 组件配置必须与官方文档和当前运行配置一致；
- 偏离官方默认值的配置必须记录原因、影响和回滚方式；
- 框架升级必须执行编译、测试、配置启动和关键链路验证。

## 6. 框架变更检查清单

- [ ] BOM、Starter 和 Java 版本已核对
- [ ] Common、Web、Security、MyBatis、Redis、MQ 和 WebSocket 官方文档已查阅
- [ ] `Result`、错误码和分页契约与通用规范一致
- [ ] Entity、Mapper、Wrapper 和 TypeHandler 用法已验证
- [ ] MapStruct 路径、Spring Bean 注入和转换测试已验证
- [ ] 认证模式、密钥和资源授权已验证
- [ ] Web CORS、TraceId、请求体和日志配置已验证
- [ ] WebSocket 端点、Ticket、Origin、Room 和分布式配置已验证
- [ ] Redis Key、TTL、锁和降级策略已验证
- [ ] MQ 消息、重试、死信和幂等策略已验证
- [ ] 官方发布说明和工程变更记录已同步

## 7. 参考

- API 速查：[`17-migoo-api-reference.md`](17-migoo-api-reference.md)
- HTTP 契约：`docs/06-spec/05-api.md`
- 通用实时协议：`docs/06-spec/15-realtime-protocol.md`
- 后端分层：`docs/06-spec/04-backend.md`
- 数据库：`docs/06-spec/06-database.md`
- 安全：`docs/06-spec/10-security.md`
- 官方总览：<https://xiaomisum.github.io/springboot-migoo-framework/>
- 官方发布说明：<https://github.com/XiaoMiSum/springboot-migoo-framework/releases>

---

**文档结束**
