# 软件测试平台——migoo 框架集成规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 适用范围

本规范记录项目对 `migoo-spring-boot-starter` 的项目级约束。框架的通用 API 目录以官方文档和当前锁定版本为准，本文件不复制完整框架手册。

| 项目 | 约定 |
| --- | --- |
| 框架 | `migoo-spring-boot-starter` |
| 版本 | `1.3.18`，由 Maven BOM 管理 |
| Java | 21 |
| 数据访问 | `migoo-spring-boot-starter-mybatis` |
| 认证 | `migoo-spring-boot-starter-security` |
| WebSocket | `migoo-spring-boot-starter-websocket` |
| Redis | 用于缓存、会话和分布式 WebSocket；是否启用以配置为准 |

版本升级必须同时检查响应、异常、分页、主键、WebSocket 和安全配置，不允许只修改依赖版本。

## 2. 统一响应和错误

### 2.1 Result

Controller 统一返回 `Result<T>`：

```java
return Result.ok(data);
return Result.ok();
return Result.error(ErrorCodeConstants.SOME_ERROR);
```

响应字段由 `05-api.md` 定义：

```text
Result<T> = { code: number, msg: string, data: T }
```

### 2.2 ErrorCode

错误码使用 10 位数字，并由项目统一登记：

```java
public static final ErrorCode USER_NOT_FOUND =
        ErrorCode.of(1000003001, "用户不存在");
```

不得在业务代码中临时创建同义错误码，也不得使用 `int` 自定义一套平行错误码体系。

### 2.3 业务异常

业务异常统一通过框架工具抛出：

```java
if (user == null) {
    throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
}
```

需要参数替换时使用框架占位符：

```java
throw ServiceExceptionUtil.get(ErrorCodeConstants.SOME_ERROR, argument);
```

框架运行时异常类型以当前 `1.3.18` 版本为准，项目文档统一称为“业务异常”，不再要求自行构造 `BusinessException(int, String)`。

## 3. 分页

### 3.1 PageParam

项目接口统一使用 `PageParam` 的 `pageNo/pageSize`：

```java
public class UserPageReqParam extends PageParam {
    private String name;
    private Integer status;
}
```

项目层接口默认使用：

| 参数 | 默认值 | 最大值 |
| --- | --- | --- |
| `pageNo` | `1` | — |
| `pageSize` | `20` | `100` |

如果框架版本默认值不同，必须在项目适配层统一覆盖，不能让不同 Controller 产生不同默认值。

### 3.2 PageResult

统一使用：

```text
PageResult<T> = { list: T[], total: number }
```

```java
PageResult<SysUser> page = userMapper.selectPage(pageParam, wrapper);
```

禁止在项目 DTO 中另行定义 `records`、`items` 等平行分页字段。

## 4. MyBatis-Plus 数据层

### 4.1 Entity 基类

业务 Entity 统一继承框架的 UUID 基类：

```java
@TableName("sys_user")
public class SysUser extends BaseUuidDO<SysUser> {
    private String username;
}
```

- UUID 使用框架默认生成策略。
- 不在项目中强制 UUID v7。
- `createdAt`、`updatedAt`、`isDeleted` 由基类和字段处理器统一管理。
- 禁止在 Entity 中加入业务方法或跨层查询逻辑。

### 4.2 Mapper 基类

所有 Mapper 继承 `BaseMapperX<T>`：

```java
@Mapper
public interface SysUserMapper extends BaseMapperX<SysUser> {
}
```

框架提供的基础查询、批量操作和分页能力优先复用，具体方法签名以 1.3.18 实际 API 为准。

### 4.3 Lambda Wrapper

条件构造使用 `LambdaQueryWrapperX` / `LambdaUpdateWrapperX`：

```java
LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<SysUser>()
        .eqIfPresent(SysUser::getStatus, status)
        .likeIfPresent(SysUser::getName, name);
```

`xxxIfPresent` 只表示跳过 `null` 条件；业务默认值、空字符串语义和权限条件仍由 Service 明确决定。

### 4.4 加密和 JSON 字段

敏感字段和结构化字段使用框架提供的 TypeHandler。密码必须使用项目配置的 PasswordEncoder，不得用字段加密替代密码哈希。

- 加密密钥通过环境变量或密钥管理服务注入。
- 禁止将密钥写入代码、文档或默认配置。
- TypeHandler 的字段类型、加密算法和密钥轮换方式必须与安全规范一致。

## 5. MapStruct 转换器

### 5.1 存放位置

所有 Entity、DTO、VO 之间的 MapStruct 转换器统一放在：

```text
server/src/main/java/io/github/xiaomisum/robotest/model/convert/
```

按业务域命名：

```text
UserConvertMapper.java
RoleConvertMapper.java
BugConvertMapper.java
WorkspaceConvertMapper.java
```

现有位于其他目录的转换器应通过独立迁移任务迁移；本规范不授权在同一次改动中无计划地移动大量文件。

### 5.2 使用原则

```java
@Mapper
public interface UserConvertMapper {
    UserRespDTO toRespDTO(SysUser entity);
}
```

- 纯字段映射使用 MapStruct。
- 业务字段、权限字段、审计字段和需要额外查询的关联对象由 Service 补充。
- 不得在 Service 中为纯字段逐项 setter 拷贝。
- 转换器实现类不得包含业务判断、数据库查询或权限逻辑。
- 不得混用静态 `INSTANCE`、Spring 注入和手工拷贝三种方式；项目迁移时统一选择并记录。

## 6. 认证与授权

### 6.1 LoginUser

项目 `LoginUser` 继承框架的 `AuthUserDetails`，并提供项目所需的工作空间权限字段。

### 6.2 UserDetailsBridge

项目实现 `UserDetailsBridge`，统一提供用户名和用户 ID 的加载逻辑。业务 Controller 不自行解析 Token。

### 6.3 请求上下文

- HTTP 上下文 Header 由 `05-api.md` 定义。
- workspace 角色通过项目的 `WorkspaceRoleInterceptor` 注入权限。
- `X-User-Id` 等框架 Header 只有在可信网关覆盖并且外部请求无法伪造时才可使用。
- 资源级权限必须在 Service 或专用 Guard 中再次校验。

## 7. WebSocket

### 7.1 连接配置

```yaml
migoo:
  websocket:
    enabled: true
    distributed: true
    endpoints:
      - /ws/documents/*
    token-header: Authorization
    token-prefix: "Bearer "
```

生产环境必须配置允许的 Origin，不得依赖 `*`。浏览器 WebSocket 查询参数 Token 的例外和安全要求见 `05-api.md`、`10-security.md`。

### 7.2 Handler 约束

- Handler 只处理连接生命周期、帧转发和消息分发。
- 文档成员权限在加入房间时校验。
- 写入操作在持久化前再次校验权限，防止连接期间权限被撤销。
- Yjs 二进制帧不由业务 Handler 解析。
- JSON 业务操作必须进行大小、类型和权限校验。

## 8. 工具类

| 工具 | 用途 |
| --- | --- |
| `JsonUtils` | JSON 序列化和反序列化 |
| `CollectionUtils` | 集合转换、过滤和分组 |
| `LocalDateTimeUtils` | 项目统一时间处理 |
| `ServiceExceptionUtil` | 统一抛出业务异常 |
| `PageParam` / `PageResult` | 统一分页输入和输出 |

工具类使用前必须确认当前框架版本的实际 API，禁止凭记忆复制其他版本示例。

## 9. 框架变更检查清单

- [ ] Maven BOM 和实际依赖版本已核对
- [ ] `Result`、`ErrorCode`、`PageParam`、`PageResult` 示例可编译
- [ ] `BaseUuidDO` 和 UUID 策略与数据库规范一致
- [ ] MapStruct 转换器位于 `model/convert/`
- [ ] 认证、上下文和 WebSocket 配置已核对
- [ ] Redis/distributed 配置与实际部署模式一致
- [ ] 官方文档、版本变更记录和项目代码已同步

## 10. 参考

- HTTP 契约：`docs/06-spec/05-api.md`
- 后端分层和 C11：`docs/06-spec/04-backend.md`
- 数据库：`docs/06-spec/06-database.md`
- 安全：`docs/06-spec/10-security.md`
- 官方文档：<https://xiaomisum.github.io/springboot-migoo-framework/>

---

**文档结束**
