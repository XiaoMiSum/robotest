# 框架 API 参考

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 文档定位

本文是采用 migoo 框架时的 API 速查摘要，帮助开发者定位当前版本可用的组件入口、典型类和适配边界。

本文不是官方 API 手册，也不替代锁定版本的官方文档。组件页面 URL、版本和发布说明见 [`11-migoo-framework.md`](11-migoo-framework.md) 2. 官方文档与本文不一致时，以官方文档和实际依赖版本为准，并在通用规范中记录差异。

## 2. 使用边界

- 本文只说明“框架提供什么入口”和“如何适配”；
- 工程的响应字段、错误码、分页参数、权限和业务异常规则分别以 `05-api.md`、`10-security.md` 和 `04-backend.md` 为准；
- 业务实体、DTO、事件、持久化和事务边界由详细设计定义；
- 不得通过复制官方示例创建第二套 Result、异常体系或全局处理器。

## 3. Common API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/common.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| 成功响应 | `Result.ok(data)` / `Result.ok()` | 字段和 HTTP 状态遵循 `05-api.md` |
| 错误响应 | `Result.error(ErrorCode)` | 错误码登记在工程 `ErrorCodeConstants` |
| 业务异常 | `ServiceExceptionUtil.get(ErrorCode, args...)` | 只用于工程业务错误，不创建平行异常体系 |
| 分页请求 | `PageParam` | 统一 `pageNo/pageSize` |
| 分页结果 | `PageResult<T>` | 对外使用 `list/total` |
| 参数校验 | migoo validation 注解 | 组合工程 DTO 校验和安全规则 |
| 工具 | `JsonUtils`、`CollectionUtils` | 仅用于无业务副作用的通用转换 |

### 3.1 Result

```java
return Result.ok(data);
return Result.error(ErrorCodeConstants.SOME_ERROR);
```

`Result` 的外层字段名称、错误响应中的 `data: null` 和特殊响应例外由 `05-api.md` 定义。SSE、文件、二进制和 WebSocket 帧不机械套用普通 JSON `Result`。

### 3.2 ServiceExceptionUtil

```java
throw ServiceExceptionUtil.get(ErrorCodeConstants.SOME_ERROR);
throw ServiceExceptionUtil.get(ErrorCodeConstants.SOME_ERROR, argument);
```

异常消息参数按照官方组件支持的方式传入；不得把 Token、密码、SQL、内部堆栈或敏感配置拼入消息。

### 3.3 分页

```java
PageResult<ResourceDTO> result = resourceMapper.selectPage(pageParam, wrapper);
```

`PageParam` 和 `PageResult` 是框架能力入口；工程默认值、最大值、游标分页和响应字段由 API 规范统一覆盖。

## 4. Web API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/web.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| 全局异常 | 框架 Web 异常处理器 | 统一转换为工程 `Result` 和 `ErrorCodeConstants` |
| 请求上下文 | TraceId / request context | 业务审计和日志引用同一 traceId |
| 国际化 | `i18n/messages` | 业务错误使用资源键，不硬编码环境语言 |
| CORS | Web CORS 配置 | 生产使用明确 Origin 白名单 |
| 请求体 | Web 请求体缓存/解析能力 | 仅在有审计或性能依据时启用，敏感字段不缓存 |

工程不在 Controller 中重复实现全局异常、响应包装或跨域基础设施；业务 DTO 校验、业务审计和特殊响应适配仍由工程层负责。

## 5. MyBatis API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/mybatis.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| 实体基类 | `BaseUuidDO<T>` | 提供 UUID、创建/更新时间和逻辑删除公共字段 |
| Mapper 基类 | `BaseMapperX<T>` | 提供采用的查询、分页和批量方法 |
| 查询 Wrapper | `LambdaQueryWrapperX<T>` | 简单动态条件可在 Service 组合 |
| 更新 Wrapper | `LambdaUpdateWrapperX<T>` | 需要显式清空字段或部分更新时使用 |
| 分页 | `PageParam` / `PageResult` | 对外契约以 `05-api.md` 为准 |
| TypeHandler | JSON、加密、列表等 Handler | 使用前核对类型、方言、密钥和迁移策略 |

### 5.1 Entity 和 Mapper 适配

```java
@TableName("resource")
public class Resource extends BaseUuidDO<Resource> {
    private String name;
}

@Mapper
public interface ResourceMapper extends BaseMapperX<Resource> {
}
```

实体不承载跨层业务方法；复杂或可复用查询下沉 Mapper，简单动态过滤可以由 Service 组合，具体边界遵循 `04-backend.md`。

### 5.2 部分更新

查询结果只用于校验，不得作为包含全部字段的 `updateById` 载体。更新载体只设置本次请求实际变更的字段；需要显式写入 `null` 时使用 `LambdaUpdateWrapperX`。该规则是工程约束，不是框架默认行为。

### 5.3 Wrapper 和 TypeHandler

- `likeIfPresent`、`eqIfPresent` 等便捷方法只适用于简单条件；
- 复杂联表、聚合、锁和复用查询应封装为 Mapper 方法；
- TypeHandler 不得把明文密钥写入日志、DTO 或异常；
- JSONB、加密字段和历史数据迁移必须有读写验证。

## 6. MapStruct 接入适配

MapStruct 不是 migoo Starter 的通用 API，接入约定如下：

- 转换器统一放在 `model/convert/`；
- 使用 `@Mapper(componentModel = "spring")` 并通过 Spring Bean 注入；
- 纯字段映射使用 MapStruct，业务判断、权限和额外查询留在 Service；
- 不混用 `Mappers.getMapper()`、静态 `INSTANCE`、Spring 注入和手工 setter 拷贝；
- 转换器输入输出 DTO 变更必须有编译和转换测试。

## 7. Security API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/security.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| JWT | 框架 Token Provider / Security 配置 | 工程定义密钥、有效期、请求头和错误码 |
| 用户加载 | UserDetails / 用户桥接适配 | 从用户服务加载，不把外部 Header 当可信身份 |
| 角色权限 | Security 角色/权限能力 | 资源级 Guard 再次校验作用域权限 |
| Token 刷新 | 框架刷新能力 | 按 `10-security.md` 和系统管理详细设计执行 |
| 审计 | AOP/审计服务 | 记录操作者、资源、结果和脱敏变更 |

框架认证成功不等于业务授权成功。各业务域和实时资源均必须在服务端校验角色与归属关系。

## 8. WebSocket API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/websocket.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| Handler 基类 | `MiGooWebSocketHandler` | 业务 Handler 继承并实现连接/消息边界 |
| 会话管理 | `WebSocketSessionManager` | 会话属性只保存必要的脱敏身份和资源范围 |
| 房间 | 框架房间/会话 API | 房间由服务端按 Ticket 绑定资源生成 |
| Token 认证 | 框架握手拦截能力 | 迁移期可兼容旧 Token，目标使用一次性 Ticket |
| 分布式模式 | 框架 Redis 分布式能力 | 明确 Redis 依赖、顺序、故障降级和日志脱敏 |

通用消息信封、错误码、生命周期和安全要求见 `15-realtime-protocol.md`；接入 Ticket、Origin、前端适配器和实现差距见 `docs/04-detailed-design/78-realtime-websocket.md`。业务事件不得在本文档中定义。

## 9. Redis API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/redis.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| Key 模板 | `RedisKit` / Key 模板 | Key 命名、TTL 和值类型集中登记 |
| 缓存 | 框架缓存能力 | 明确失效、重建和空值策略 |
| 分布式锁 | 框架锁能力 | 必须有持有者标识、超时和释放保护 |
| 限流/会话 | Redis 数据结构 | 敏感值不明文存储，故障策略明确 |
| 分布式广播 | Redis Pub/Sub 或框架能力 | 多实例顺序和降级行为写入运行手册 |

## 10. MQ API 摘要

官方组件：<https://xiaomisum.github.io/springboot-migoo-framework/mq.html>。

| 能力 | 典型入口 | 接入适配 |
| --- | --- | --- |
| Stream | 可靠投递、ACK、重试和死信 | 定义消息幂等键、重试上限和告警 |
| Pub/Sub | 广播和即时通知 | 不承诺可靠投递，不作为唯一业务事实源 |
| Listener | 框架消息监听器 | 依赖版本、事务边界和失败处理必须显式配置 |
| Channel | 业务消息通道 | 命名、权限、版本和兼容策略集中登记 |

## 11. 版本和迁移检查

升级 migoo BOM 或任一 Starter 前：

1. 核对官方发布说明和迁移指南；
2. 编译并运行 Common/Web/Security/MyBatis/WebSocket/Redis/MQ 相关测试；
3. 验证 Result、异常、分页、认证、资源 Guard、房间和分布式行为；
4. 检查配置属性名称、默认行为、日志脱敏和数据库方言；
5. 更新本参考、框架集成规范、部署 Runbook 和回滚说明。

## 12. 参考

- 框架集成策略：[`11-migoo-framework.md`](11-migoo-framework.md)
- 官方总览：<https://xiaomisum.github.io/springboot-migoo-framework/>
- 官方发布说明：<https://github.com/XiaoMiSum/springboot-migoo-framework/releases>
- API 契约：`docs/00-spec/05-api.md`
- 后端分层：`docs/00-spec/04-backend.md`
- 安全规范：`docs/00-spec/10-security.md`
- 实时协议：`docs/00-spec/15-realtime-protocol.md`

---

**文档结束**
