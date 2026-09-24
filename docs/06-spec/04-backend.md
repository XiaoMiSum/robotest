# 软件测试平台——后端工程规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 技术栈与版本来源

| 能力 | 技术 | 版本来源 |
| --- | --- | --- |
| 运行时 | Java 21 | `server/pom.xml` |
| 框架 | migoo 1.3.18 / Spring Boot 4.x | Maven BOM、`server/pom.xml` |
| 数据访问 | MyBatis-Plus / `migoo-spring-boot-starter-mybatis` | Maven BOM、`server/pom.xml` |
| 安全 | Spring Security / migoo security starter | Maven BOM、`server/pom.xml` |
| API 文档 | SpringDoc | `server/pom.xml` |
| 数据库 | PostgreSQL 14+ | `server/src/main/resources/db/`、运行配置 |
| 测试 | JUnit 5、Spring Boot Test、Mockito | `server/pom.xml` |

本文不维护“最新”版本。依赖升级必须同步 Maven BOM、配置、测试和 OpenAPI 契约。

## 2. 分层架构

```text
Controller → Service → Mapper / 外部适配器
     │           │             │
路由/校验    业务编排/事务    数据访问/查询封装
```

依赖方向必须单向：

- Controller 不直接依赖 Mapper。
- Service 不直接操作 HTTP、Vue 或 UI 状态。
- Mapper 只负责数据访问和查询条件封装，不负责权限或业务状态判断。
- Entity 不包含跨 Service 的业务方法。
- DTO 不暴露 Entity 的可变内部结构。

框架响应、异常和分页的唯一契约分别见：

- `docs/06-spec/05-api.md`
- `docs/06-spec/11-migoo-framework.md`

## 3. 各层职责

### 3.1 Controller

Controller 只负责：

- 路由和方法映射；
- DTO 绑定和 `@Valid` 校验；
- 读取认证用户和活动上下文；
- 调用 Service；
- 使用 `Result<T>` 包装返回值。

```java
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    public Result<PageResult<UserRespDTO>> list(
            @Valid UserQueryReqDTO query) {
        return Result.ok(adminUserService.list(query));
    }
}
```

禁止在 Controller 中：

- 直接调用 Mapper；
- 判断角色、状态或业务流转；
- 组装复杂聚合 DTO；
- 手动拼接 SQL 或错误消息。

### 3.2 Service

Service 负责：

- 业务规则和状态流转；
- 权限和资源归属校验；
- 事务边界；
- 多个 Mapper 的协调；
- Entity 到响应 DTO 的组装；
- 领域异常的触发。

Service 必须使用统一资源 Guard 校验资源归属和租户边界，不能只依赖前端路由或上下文 Header 的存在。

### 3.3 Mapper

Mapper 负责：

- 单表和关联数据访问；
- 复杂查询 SQL；
- 动态查询条件封装；
- 分页、批量和更新操作；
- 返回影响行数，供并发和幂等判断使用。

复杂、复用性查询和统计 Wrapper 构造必须封装在 Mapper 的 `default` 方法或专用查询对象中。Service 可以组合简单的动态业务过滤条件，但不得把权限判断、状态流转或复杂 SQL 下放到 Wrapper。Controller 和组装器不得构造 Wrapper。

## 4. 命名规范

| 要素 | 规范 | 示例 |
| --- | --- | --- |
| Controller | `XxxController` | `AdminUserController` |
| Service | `XxxService` | `AdminUserService` |
| Service 实现 | `XxxServiceImpl` | `AdminUserServiceImpl` |
| Mapper | `XxxMapper` | `SysUserMapper` |
| Entity | 与表或领域资源对应 | `SysUser` |
| 请求 DTO | `XxxCreateReqDTO`、`XxxUpdateReqDTO`、`XxxQueryReqDTO` | `UserCreateReqDTO` |
| 响应 DTO | `XxxRespDTO`、`XxxDetailRespDTO` | `UserRespDTO` |
| 错误码 | `ErrorCodeConstants` | `USER_NOT_FOUND` |
| 配置 | `XxxConfig` | `SecurityConfig` |
| 转换器 | `XxxConvertMapper` | `UserConvertMapper` |

## 5. DTO、Entity 与转换

### 5.1 Entity

Entity 使用 MyBatis-Plus 注解和 migoo 基类：

```java
@TableName("sys_user")
public class SysUser extends BaseUuidDO<SysUser> {
    private String username;
    private String passwordHash;
}
```

- 统一使用框架默认 UUID 策略。
- 不手动声明 `id`、`createdAt`、`updatedAt` 和 `isDeleted`。
- Entity 不返回给 Controller。
- Entity 不添加权限判断、状态流转或远程调用。

### 5.2 DTO

- 请求 DTO 只声明接口输入，不复用 Entity。
- 响应 DTO 只声明允许暴露的字段。
- 密码、Token、密钥、加密字段和内部审计字段禁止进入响应。
- 列表 DTO 和详情 DTO 可以拆分，避免无意义地暴露内部关联对象。

### 5.3 MapStruct

转换器统一放在：

```text
server/src/main/java/io/github/xiaomisum/robotest/model/convert/
```

规则：

- 纯字段一对一映射使用 MapStruct。
- 聚合、统计、树结构、动态权限和需要额外查询的字段由 Service 组装。
- 转换器不得查询数据库或判断业务权限。
- 项目统一使用 Spring Bean 注入，不使用静态 `INSTANCE`。
- 转换器实现使用 `@Mapper(componentModel = "spring")`，由 Service 注入。

```java
@Mapper(componentModel = "spring")
public interface UserConvertMapper {
    UserRespDTO toRespDTO(SysUser entity);
}
```

现有静态调用需要通过独立代码迁移任务改为统一方式。

## 6. 响应、异常与分页

本项目不再维护第二套 `ApiResponse`、`PageResult` 或全局异常处理器。

- Controller 返回 `Result<T>`。
- 业务异常使用 `ServiceExceptionUtil.get(ErrorCodeConstants.X)`。
- 分页使用 `pageNo/pageSize`，返回 `PageResult<T>.list/total`。
- 错误码使用 10 位数字并集中登记。
- 响应字段为 `code`、`msg`、`data`。

完整定义见 `05-api.md` 和 `11-migoo-framework.md`。

## 7. 事务、并发和幂等

### 7.1 事务

- 事务边界放在 Service 公共业务方法。
- 多个写操作需要原子性时使用 `@Transactional`。
- 异步任务、消息处理和 WebSocket 持久化必须明确事务边界。
- 事务中禁止执行不可控的远程调用或长时间阻塞操作。

### 7.2 并发

- 可并发修改的资源使用版本号、更新时间或条件更新。
- 更新影响行数为 `0` 时必须按冲突、未找到或幂等成功处理，不能静默忽略。
- 批量操作必须定义部分失败策略。

### 7.3 幂等

创建、状态流转、导入、异步执行和 WebSocket 文本操作必须说明幂等键或重复请求行为。

## 8. 数据更新规范（C11）

核心原则：只更新调用方实际提交的字段。

### 8.1 常规部分更新

查询结果只用于存在性、权限和状态校验，不得直接作为 `updateById` 载体：

```java
SysUser existing = sysUserMapper.selectById(id);
if (existing == null) {
    throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
}

SysUser update = new SysUser();
update.setId(id);
if (StringUtils.hasText(reqDTO.getName())) {
    update.setName(reqDTO.getName());
}
sysUserMapper.updateById(update);
```

### 8.2 显式置空

需要将字段设置为 `NULL` 时，使用 `LambdaUpdateWrapperX` 显式 `.set(field, null)`，不能依赖 `updateById` 的非空字段策略。

### 8.3 测试要求

- 使用 `ArgumentCaptor` 验证更新载荷。
- 验证未提交字段没有被覆盖。
- 验证显式置空和乐观锁影响行数。
- 验证权限失败、并发冲突和重复请求。

## 9. 查询封装规范

### 9.1 查询分层

- Service 可以根据请求参数组合简单的动态过滤条件；
- 复杂、复用性查询、统计查询和固定数据访问意图必须封装在 Mapper `default` 方法或专用 Query Object 中；
- Controller、Assembler 和 DTO 不得构造 Wrapper；
- 权限和业务状态判断始终在 Service 或 Guard 中完成。

示例：

```java
public interface ResourceMapper extends BaseMapperX<Resource> {
    default PageResult<Resource> findPage(PageParam pageParam, UUID resourceScopeId,
                                           String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<Resource>()
                .eq(Resource::getScopeId, resourceScopeId)
                .eqIfPresent(Resource::getStatus, status)
                .orderByDesc(Resource::getCreatedAt));
    }
}
```

Service 负责业务判断，Mapper 负责可复用的数据访问意图。

### 9.2 命名

| 操作 | 命名 |
| --- | --- |
| 分页 | `findPage` |
| 列表 | `findBy{Field}` / `listBy{Field}` |
| 计数 | `count{Condition}` |
| 单条 | `findBy{Field}` |
| 状态更新 | `{action}ById` |
| 删除 | `deleteBy{Condition}` |

更新和删除方法优先返回 `int`。

## 10. 审查清单

- [ ] Controller 无业务逻辑
- [ ] Service 使用统一异常和权限 Guard
- [ ] Mapper 封装 Wrapper 和复杂查询
- [ ] 响应、分页和错误码符合 `05-api.md`
- [ ] Entity 使用框架基类和默认 UUID 策略
- [ ] MapStruct 转换器位于 `model/convert/`
- [ ] C11 部分更新和显式置空有测试
- [ ] 事务、并发和幂等策略明确

## 11. 参考

- API 契约：`docs/06-spec/05-api.md`
- 数据库：`docs/06-spec/06-database.md`
- migoo 框架：`docs/06-spec/11-migoo-framework.md`
- 安全：`docs/06-spec/10-security.md`

---

**文档结束**
