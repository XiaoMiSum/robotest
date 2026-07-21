# migoo 框架集成规范

> 本项目基于 **migoo-spring-boot-starter**（v1.3.17）构建，BOM 通过 `migoo-framework-dependencies` 统一管理版本。  
> 框架文档：https://xiaomisum.github.io/springboot-migoo-framework/  
> 版本发布：https://github.com/xiaomisum/migoo-framework/releases

---

## 1. Starter 清单

| Starter | 用途 | 关键类 |
|---------|------|--------|
| `migoo-spring-boot-starter-common` | 统一响应体、分页模型、异常体系、校验注解、工具类 | `Result`、`ErrorCode`、`ServiceExceptionUtil`、`PageParam`、`PageResult`、`JsonUtils`、`BeanUtils`、`CollectionUtils` |
| `migoo-spring-boot-starter-web` | Web MVC 增强、XSS 过滤、全局异常处理、API 错误日志 | `ApiErrorLogFrameworkService` |
| `migoo-spring-boot-starter-security` | JWT 双令牌认证 + RBAC 授权 | `AuthUserDetails`、`UserDetailsBridge`、`AuthUserDetailsFetcher`、`JwtTokenProvider` |
| `migoo-spring-boot-starter-mybatis` | MyBatis-Plus 增强、UUID 主键、自动填充、加密存储 | `BaseUuidDO`、`BaseMapperX`、`LambdaQueryWrapperX`、`EncryptTypeHandler` |
| `migoo-spring-boot-starter-websocket` | WebSocket 连接管理、Token 认证、会话管理、分布式广播 | `MiGooWebSocketHandler`、`WebSocketSessionManager`、`WebSocketAuthInterceptor` |
| `migoo-spring-boot-starter-redis` | Redis 自动配置（分布式 WebSocket 依赖） | 声明但未直接使用 |

---

## 2. 统一响应（common）

### 2.1 Result

Controller 统一返回 `Result<T>`，框架自动包装响应结构。

| 方法 | 说明 |
|------|------|
| `Result.ok()` | 成功（无数据） |
| `Result.ok(data)` | 成功（带数据） |
| `Result.error(ErrorCode)` | 错误响应（自动映射 HTTP 状态码） |

```java
// 成功
return Result.ok(userVO);

// 分页
return Result.ok(userMapper.selectPage(reqParam));

// 错误
return Result.error(ErrorCodeConstants.USER_NOT_FOUND);
```

### 2.2 ErrorCode

错误码定义，10 位数字，分四段 `类型(1) / 系统(3) / 模块(3) / 错误编号(3)`。

```java
// 定义
public static final ErrorCode USER_NOT_FOUND = ErrorCode.of(1000003001, "用户不存在");

// 预置错误码（GlobalErrorCodeConstants）
// SUCCESS=200, BAD_REQUEST=400, UNAUTHORIZED=401, FORBIDDEN=403, NOT_FOUND=404, INTERNAL_SERVER_ERROR=500
```

---

## 3. 异常处理（common）

### 3.1 抛出业务异常

使用 `ServiceExceptionUtil.get(ErrorCode)` 抛出 `BusinessException`，框架全局异常处理器自动捕获并返回 `Result` 响应。

```java
// 基础用法
SysUser user = userMapper.selectById(userId);
if (user == null) {
    throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
}

// 带参数（{0} 占位符替换）
throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND, userId);
```

### 3.2 自定义参数校验注解

配合 `@Valid` 在 Controller 自动校验。

| 注解 | 说明 |
|------|------|
| `@Mobile` | 手机号（11 位，1 开头） |
| `@Email` | 邮箱 |
| `@Password` | 密码（8-32 位，含字母+数字+特殊字符） |
| `@InEnum` | 枚举值校验 |

```java
public class UserCreateReqBody {
    @Mobile
    private String mobile;

    @Email
    private String email;

    @Password
    private String password;

    @InEnum(UserStatusEnum.class)
    private Integer status;
}
```

---

## 4. 分页（common）

### 4.1 PageParam

分页请求参数基类，字段 `pageNo`（默认 1）和 `pageSize`（默认 10，最大 100）。

```java
// 方式一：匿名子类
new PageParam() {{ setPageNo(pageNo); setPageSize(pageSize); }}

// 方式二：请求 DTO 继承
public class UserPageReqParam extends PageParam {
    private String name;
    private Integer status;
}
```

### 4.2 PageResult

分页响应封装，字段 `list`（数据列表）和 `total`（总条数）。

```java
PageResult<SysUser> page = userMapper.selectPage(reqParam, wrapper);
// page.getList()  -> 数据列表
// page.getTotal() -> 总条数
```

---

## 5. 数据层（mybatis）

### 5.1 实体基类

| 基类 | 主键类型 | 说明 |
|------|---------|------|
| `BaseUuidDO<T>` | `UUID` | 项目统一使用，自动生成有序 UUID |
| `BaseAutoIncDO<ID, T>` | `Long` | 自增主键（本项目未使用） |

`BaseDO` 提供字段：`createdAt`、`updatedAt`、`isDeleted`（逻辑删除），由 `DefaultFieldHandler` 自动填充。

```java
// ✅ 正确：项目统一继承 BaseUuidDO
@TableName("sys_user")
public class SysUser extends BaseUuidDO<SysUser> {
    private String username;
    // 无需声明 id / createdAt / updatedAt / isDeleted
}

// ❌ 错误：不要手动声明 @TableId 或 id 字段
public class SysUser extends BaseDO {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
}
```

### 5.2 Mapper 基类

所有 Mapper 继承 `BaseMapperX<T>`，扩展 MyBatis-Plus 原生方法。

| 方法 | 说明 |
|------|------|
| `selectOne(SFunction, value)` | 单字段精确查询 |
| `selectList(SFunction, value)` | 单字段列表查询 |
| `selectCount(SFunction, value)` | 单字段计数 |
| `selectPage(PageParam, Wrapper)` | 分页查询 |
| `insertBatch(list)` | 批量插入 |
| `updateBatch(list)` | 批量更新 |

```java
@Mapper
public interface SysUserMapper extends BaseMapperX<SysUser> {
    // 单字段查询（框架内置）
    SysUser user = userMapper.selectOne(SysUser::getUsername, username);

    // 计数
    long count = userMapper.selectCount(SysUser::getStatus, 1);

    // 自定义分页
    default PageResult<SysUser> selectPage(UserPageReqParam reqParam) {
        return selectPage(reqParam, new LambdaQueryWrapperX<SysUser>()
                .likeIfPresent(SysUser::getName, reqParam.getName())
                .eqIfPresent(SysUser::getStatus, reqParam.getStatus()));
    }
}
```

### 5.3 LambdaQueryWrapperX

扩展 MyBatis-Plus `LambdaQueryWrapper`，新增 `xxxIfPresent` 方法，`null` 值自动跳过条件。

```java
new LambdaQueryWrapperX<SysUser>()
    .eqIfPresent(SysUser::getStatus, status)       // status 为 null 时跳过
    .likeIfPresent(SysUser::getName, name)          // name 为 null 时跳过
    .betweenIfPresent(SysUser::getCreatedAt, start, end) // 边界为 null 时退化
    .orderByDesc(SysUser::getId);
```

### 5.4 加密字段存储

使用 `@TableField(typeHandler = EncryptTypeHandler.class)` 注解，存取时自动 AES 加解密。

```java
@TableName("sys_user")
public class SysUser extends BaseUuidDO<SysUser> {
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String mobile; // 存储时 AES 加密，读取时自动解密
}
```

加密密钥配置：`mybatis-plus.encryptor.password` 或 JVM 参数 `-Dmybatis-plus.encryptor.password=xxx`。

### 5.5 JSON 字段存储

| TypeHandler | 存储格式 | 适用类型 |
|-------------|---------|---------|
| `JsonLongSetTypeHandler` | JSON 数组 | `Set<Long>` |
| `StringListTypeHandler` | 逗号分隔 | `List<String>` |

```java
@TableName("sys_role")
public class SysRole extends BaseUuidDO<SysRole> {
    @TableField(typeHandler = JsonLongSetTypeHandler.class)
    private Set<Long> permissionIds;
}
```

### 5.6 自动注册组件

框架自动注册，无需手动配置：

| 组件 | 说明 |
|------|------|
| `@MapperScan` | 扫描 `xyz.migoo.framework.**` 下的 Mapper |
| `PaginationInnerInterceptor` | 分页插件 |
| `UTCLocalDateTimeHandler` | 全局 LocalDateTime UTC 时区处理 |
| `DefaultFieldHandler` | 自动填充 createdAt / updatedAt / isDeleted |

### 5.7 配置

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: assign_uuid           # UUID 主键自动生成
      logic-delete-field: isDeleted
      logic-delete-value: true
      logic-not-delete-value: false
```

---

## 6. 认证授权（security）

### 6.1 LoginUser

继承 `AuthUserDetails<LoginUser, String>`，提供 `id`/`username`/`password`/`name`/`enabled`。

```java
public class LoginUser extends AuthUserDetails<LoginUser, String> {
    private String name;
    private List<GrantedAuthority> workspaceAuthorities; // 工作空间级权限
}
```

### 6.2 UserDetailsBridge

实现 `UserDetailsBridge` 接口，提供 `loadUserByUsername` 和 `loadUserById`。

```java
@Component
public class UserDetailsBridgeImpl implements UserDetailsBridge<LoginUser> {
    @Override
    public LoginUser loadUserByUsername(String username) {
        SysUser user = userMapper.selectByUsername(username);
        return user == null ? null : toLoginUser(user);
    }
}
```

### 6.3 AuthUserDetailsFetcher

注入用于认证和 Token 刷新。

```java
@Resource
private AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher;

// 登录
LoginResult<LoginUser> result = authUserDetailsFetcher.authenticate(username, password);

// 刷新 Token
LoginResult<LoginUser> result = authUserDetailsFetcher.refreshToken(refreshToken);
```

### 6.4 JwtTokenProvider

仅在特殊场景（如邀请链接加入）手动创建 Token。

```java
@Resource
private JwtTokenProvider jwtTokenProvider;

String accessToken = jwtTokenProvider.createAccessToken(loginUser);
String refreshToken = jwtTokenProvider.createRefreshToken(loginUser);
```

### 6.5 配置

```yaml
migoo:
  security:
    mode: jwt
    jwt:
      secret-key: ${JWT_SECRET_KEY}
      access-token-expire-timespan: 24     # 小时
      refresh-token-expire-timespan: 168   # 小时
      header-name: Authorization
      user-id-header: X-User-Id
      permit-all-urls: /api/auth/login, /api/auth/refresh, /api/invitations/public/**
```

---

## 7. Web 层（web）

### 7.1 XSS 过滤

默认开启（`migoo.xss.enable: true`），无需额外配置。

### 7.2 API 错误日志

实现 `ApiErrorLogFrameworkService` 接口（当前为空实现占位）。

```java
@Component
public class ApiErrorLogFrameworkServiceImpl implements ApiErrorLogFrameworkService {
    @Override
    public void createApiErrorLog(ApiErrorLog apiErrorLog) {
        // 可接入日志系统或告警
    }
}
```

---

## 8. WebSocket（websocket）

### 8.1 依赖

```xml
<dependency>
    <groupId>xyz.migoo.springboot</groupId>
    <artifactId>migoo-spring-boot-starter-websocket</artifactId>
</dependency>
```

### 8.2 配置

```yaml
migoo:
  websocket:
    enabled: true                      # 是否启用，默认 true
    distributed: false                 # 是否启用分布式模式，默认 false
    endpoint: /ws                      # WebSocket 端点路径，默认 /ws
    allowed-origins: "*"               # 允许的来源，默认 *
    token-header: Authorization        # Token Header 名称
    token-prefix: "Bearer "            # Token 前缀
    max-session-timeout: 1800000       # 最大会话超时（毫秒），默认 30 分钟
```

### 8.3 核心组件

| 组件 | 说明 |
|------|------|
| `WebSocketSessionManager` | 会话管理器接口，提供 `sendToUser`、`broadcast`、`getOnlineUserCount` |
| `LocalWebSocketSessionManager` | 单机会话管理器（默认） |
| `DistributedWebSocketSessionManager` | 分布式会话管理器（Redis Pub/Sub） |
| `MiGooWebSocketHandler` | 消息处理器基类，提供 `getUserId`、`getUserDetails`、`sendMessage` |
| `WebSocketAuthInterceptor` | Token 认证拦截器，复用 `AuthUserDetailsFetcher.verifyToken()` |

### 8.4 实现业务 Handler

继承 `MiGooWebSocketHandler`，重写 `handleTextMessage`：

```java
@Component
public class DocumentWebSocketHandler extends MiGooWebSocketHandler {

    public DocumentWebSocketHandler(WebSocketSessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getUserId(session);
        AuthUserDetails<?, ?> user = getUserDetails(session);

        // 处理消息
        sendMessage(session, "收到: " + message.getPayload());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        super.afterConnectionEstablished(session);
        broadcast("用户 " + getUserId(session) + " 已上线");
    }
}
```

### 8.5 Token 认证

WebSocket 握手时通过 `token` 查询参数或 `Authorization` Header 验证 JWT：

```javascript
// 客户端连接
const socket = new WebSocket('ws://localhost:8080/ws/documents/{docId}?token=jwt-token');
```

框架自动调用 `AuthUserDetailsFetcher.verifyToken()` 验证，将用户信息存入 session attributes：

```java
// 从 session 获取用户信息
AuthUserDetails<?, ?> user = (AuthUserDetails<?, ?>) session.getAttributes().get("USER_DETAILS");
String userId = (String) session.getAttributes().get("USER_ID");
```

### 8.6 会话管理

```java
@Resource
private WebSocketSessionManager sessionManager;

// 发送消息给指定用户
sessionManager.sendToUser(userId, message);

// 广播给所有在线用户
sessionManager.broadcast("系统通知");

// 获取在线用户数
int count = sessionManager.getOnlineUserCount();
```

---

## 9. 对象转换（MapStruct）

项目使用 MapStruct 作为 Entity ↔ DTO/VO 的转换工具，替代手动 setter 和 `BeanUtils.toBean`。

### 9.1 依赖

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <scope>provided</scope>
</dependency>
```

### 9.2 定义转换接口

```java
@Mapper(componentModel = "spring")
public interface UserConvertMapper {
    UserConvertMapper INSTANCE = Mappers.getMapper(UserConvertMapper.class);

    // Entity → VO
    UserVO toVO(SysUser entity);
    List<UserVO> toVOList(List<SysUser> entities);

    // DTO → Entity
    SysUser toEntity(UserCreateReqDTO dto);
}
```

### 9.3 使用方式

```java
// 方式一：静态调用
UserVO vo = UserConvertMapper.INSTANCE.toVO(user);

// 方式二：注入使用（推荐，便于单元测试 mock）
@Resource
private UserConvertMapper userConvertMapper;

UserVO vo = userConvertMapper.toVO(user);
```

### 9.4 字段映射规则

MapStruct 自动按名称匹配同名字段，以下场景需要手动映射：

| 场景 | 处理方式 |
|------|---------|
| 字段名不同 | `@Mapping(source = "username", target = "name")` |
| 忽略字段 | `@Mapping(target = "password", ignore = true)` |
| UUID → String | `@Mapping(source = "id", target = "id", qualifiedByName = "uuidToString")` |
| 嵌套对象 | `@Mapping(source = "creator", target = "creatorInfo")` |

```java
@Mapper(componentModel = "spring")
public interface UserConvertMapper {

    @Named("uuidToString")
    default String uuidToString(UUID id) {
        return id == null ? null : id.toString();
    }

    @Mapping(source = "username", target = "name")
    @Mapping(target = "password", ignore = true)
    UserVO toVO(SysUser entity);
}
```

### 9.5 命名约定

| 接口 | 位置 | 命名 |
|------|------|------|
| Entity → VO | `service/convert/` | `{Entity}ConvertMapper` |
| DTO → Entity | `service/convert/` | `{Entity}ConvertMapper`（同一接口） |

---

## 10. 工具类速查（common）

### 10.1 JsonUtils — JSON 序列化

```java
// 对象 → JSON
String json = JsonUtils.toJsonString(object);

// JSON → 对象
UserVO user = JsonUtils.parseObject(json, UserVO.class);

// JSON → 列表
List<UserVO> list = JsonUtils.parseArray(json, UserVO.class);

// JSON → 泛型（复杂类型）
List<String> perms = JsonUtils.parseObject(json, new TypeReference<List<String>>() {});
```

### 10.2 CollectionUtils — 集合操作

```java
// 类型转换
List<UserVO> voList = CollectionUtils.convertList(doList, BeanUtils::toBean);

// 按 ID 映射
Map<Long, UserDO> map = CollectionUtils.convertMap(list, UserDO::getId);

// 过滤
List<UserDO> filtered = CollectionUtils.filterList(list, u -> u.getStatus() == 1);
```

### 10.3 LocalDateTimeUtils — 日期时间

```java
// 获取今天
LocalDateTime today = LocalDateTimeUtils.getToday();

// 区间判断
boolean between = LocalDateTimeUtils.isBetween(time, start, end);

// 日期差
long days = LocalDateTimeUtils.between(start, end);
```

### 10.4 RSA — 加解密签名

```java
// 签名
String sign = RSA.sign(content, privateKey);

// 验签
boolean ok = RSA.verify(content, sign, publicKey);
```

### 10.5 加密存储

```java
// 加密
String encrypted = EncryptTypeHandler.encrypt("敏感数据");

// 解密（框架自动处理，无需手动调用）
```
