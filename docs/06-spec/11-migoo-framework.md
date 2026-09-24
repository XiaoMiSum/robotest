# 软件测试平台——migoo 框架集成规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 适用范围

本文定义项目使用 `migoo-spring-boot-starter` 时的集成边界、组件选择原则、版本管理方式和项目级约束。

本文不复制完整框架 API 手册。组件 API、配置项和示例以官方组件文档及当前锁定的框架版本为准；业务模型、业务事件和业务权限由对应的详细设计文档定义。

| 项目 | 约定 |
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

## 2. 官方组件使用手册

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

官方文档是组件 API 的参考来源；本文只规定项目如何选择、配置、验证和约束这些能力。

## 3. Common：响应、错误和分页

### 3.1 统一响应

Controller 使用框架提供的 `Result<T>`：

```java
return Result.ok(data);
return Result.ok();
return Result.error(ErrorCodeConstants.SOME_ERROR);
```

项目 HTTP 响应契约由 `05-api.md` 统一定义，本文不另行定义字段名称。

### 3.2 错误码和业务异常

项目错误码统一登记，业务异常通过框架工具抛出：

```java
throw ServiceExceptionUtil.get(ErrorCodeConstants.SOME_ERROR);
```

要求：

- 错误码使用项目统一的 10 位编号规则；
- 同一语义不得创建多个错误码；
- 错误消息不得包含 Token、密码、SQL 或内部堆栈；
- 参数化错误消息使用框架支持的占位符机制；
- 运行时异常类型以当前框架版本为准，不自行创建平行异常体系。

### 3.3 分页

项目分页使用 `PageParam` 和 `PageResult`：

```java
PageResult<ResourceDTO> result = resourceMapper.selectPage(pageParam);
```

项目统一使用 `pageNo/pageSize`，响应字段由 `05-api.md` 定义。框架默认值与项目默认值不一致时，必须在项目适配层统一覆盖。

## 4. Web：统一异常和请求处理

Web 组件负责框架级 Web 能力，项目不重复实现同类全局基础设施：

- 全局异常处理；
- 统一响应处理；
- TraceId 和请求上下文；
- 国际化消息；
- CORS 和请求体缓存。

项目只补充：

- API 业务错误码；
- DTO 校验规则；
- 安全响应头和环境策略；
- 业务审计和日志脱敏；
- SSE、文件和实时通信等特殊响应适配。

生产环境不得依赖 Web 组件的宽松默认配置，尤其是 CORS、请求体缓存和敏感日志配置。

## 5. MyBatis：数据访问

### 5.1 Entity 和 Mapper

项目数据访问遵循以下边界：

- Entity 使用框架提供的基类获得公共字段和主键能力；
- Mapper 继承 `BaseMapperX`；
- 复杂查询和可复用查询封装在 Mapper；
- 简单动态过滤可以由 Service 组合；
- Entity 不承载跨层业务方法。

示例只表达框架用法，不绑定具体业务表：

```java
@TableName("resource")
public class Resource extends BaseUuidDO<Resource> {
    private String name;
}

@Mapper
public interface ResourceMapper extends BaseMapperX<Resource> {
}
```

### 5.2 Wrapper、分页和类型处理器

按需使用：

- `LambdaQueryWrapperX`；
- `LambdaUpdateWrapperX`；
- `PageParam` / `PageResult`；
- 框架提供的加密、JSON 和列表 TypeHandler。

使用 TypeHandler 前必须确认：

- 字段类型和数据库方言匹配；
- 密钥来源和轮换方式符合安全规范；
- 历史数据读取和迁移策略明确；
- 敏感字段不会被日志或 DTO 暴露。

### 5.3 MapStruct

MapStruct 不属于 migoo Starter 的通用 API。项目统一约定：

- 转换器放在项目约定的 `model/convert/`；
- 使用 Spring Bean 注入；
- 纯字段映射使用 MapStruct；
- 业务判断、权限和额外查询留在 Service；
- 不得混用静态 `INSTANCE`、Spring 注入和手工 setter 拷贝。

## 6. Security：认证和授权

Security 组件提供 JWT、OAuth2、用户加载、Token 校验和 TOTP 等能力。项目负责：

- 选择认证模式；
- 实现框架要求的用户加载适配；
- 定义项目错误码；
- 配置 Token 生命周期和密钥；
- 实施资源级授权；
- 记录登录、权限和安全审计事件。

通用认证和授权边界见 `10-security.md`，前端 Token 使用见 `03-frontend.md`。

可信网关可以提供框架所需的用户 Header，但必须保证外部请求无法伪造该 Header。业务权限不能仅依赖框架登录状态，必须在服务端资源边界再次校验。

## 7. WebSocket：框架能力接入

WebSocket 组件提供连接管理、Token 认证、会话管理、房间和分布式广播能力。

项目接入要求：

- 端点、Handler 和会话策略以当前运行配置为准；
- 业务 Handler 只处理连接生命周期、帧转发和消息分发；
- 房间/主题权限在加入时校验；
- 写入操作在广播或持久化前再次校验；
- JSON 和二进制消息分别定义大小、编码、频率和权限；
- 生产环境配置 Origin 白名单；
- 分布式模式必须明确 Redis 依赖和多实例行为。

通用实时协议见 `15-realtime-protocol.md`。具体业务事件、Payload、持久化和协作语义由对应详细设计定义，不在本文规定。

## 8. Redis：缓存和分布式能力

Redis 组件提供连接配置、`RedisKit`、Key 模板、过期策略和分布式模式。

项目使用要求：

- Key 命名、TTL 和值类型集中定义；
- 缓存、限流、会话和分布式广播明确用途；
- 缓存失效和重建策略有文档；
- 分布式锁必须设置唯一持有者标识和释放保护；
- 敏感值不得以明文形式写入 Redis；
- Redis 不可用时的降级和失败行为必须明确。

## 9. MQ：消息传递

MQ 组件提供 Stream 和 Pub/Sub 两种模式：

- Stream 用于可靠投递、ACK、重试、死信和幂等消费；
- Pub/Sub 用于广播，不提供可靠消费保证。

项目使用要求：

- 消息模型和 Channel 命名集中管理；
- 明确消息幂等键和重复消费策略；
- 明确重试次数、死信和告警；
- Listener 构造和配置遵循当前框架版本；
- 不使用已废弃 API 绕过框架拦截器；
- 消息生产失败和消费失败必须有可观测日志。

## 10. 组件选择和配置原则

- 只引入实际使用的 Starter；
- 依赖版本由 Maven BOM 统一管理；
- 生产配置不依赖开发默认值；
- Redis、MQ、分布式 WebSocket 和 OAuth2 等能力按部署需求启用；
- 组件配置必须与官方文档和当前运行配置一致；
- 任何偏离官方默认值的配置都要记录原因、影响和回滚方式；
- 框架升级必须执行编译、测试、配置启动和关键链路验证。

## 11. 框架变更检查清单

- [ ] BOM、Starter 和 Java 版本已核对
- [ ] Common、Web、Security、MyBatis、Redis、MQ 和 WebSocket 文档已查阅
- [ ] `Result`、错误码和分页契约与项目规范一致
- [ ] Entity、Mapper、Wrapper 和 TypeHandler 用法已验证
- [ ] 认证模式、密钥和资源授权已验证
- [ ] Web CORS、TraceId、请求体和日志配置已验证
- [ ] WebSocket 端点、Origin、Room 和分布式配置已验证
- [ ] Redis Key、TTL、锁和降级策略已验证
- [ ] MQ 消息、重试、死信和幂等策略已验证
- [ ] 官方发布说明和项目变更记录已同步

## 12. 参考

- HTTP 契约：`docs/06-spec/05-api.md`
- 通用实时协议：`docs/06-spec/15-realtime-protocol.md`
- 后端分层：`docs/06-spec/04-backend.md`
- 数据库：`docs/06-spec/06-database.md`
- 安全：`docs/06-spec/10-security.md`
- 官方总览：<https://xiaomisum.github.io/springboot-migoo-framework/>
- 官方发布说明：<https://github.com/XiaoMiSum/springboot-migoo-framework/releases>

---

**文档结束**
