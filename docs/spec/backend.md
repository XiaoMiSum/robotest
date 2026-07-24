# 工程规范 — 后端

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. 技术栈锁定

| 技术                         | 版本     | 说明                                 |
| -------------------------- | ------ | ---------------------------------- |
| Java                       | 21     | LTS 版本                             |
| migoo-springboot-framework | 1.3.16 | 最新版本                               |
| Spring Boot                | 4.x    | 由migoo-springboot-framework提供      |
| MyBatis-Plus               | 最新     | 复杂查询，由migoo-springboot-framework提供 |
| Spring Security            | 最新     | 认证授权，由migoo-springboot-framework提供 |
| SpringDoc                  | 最新     | OpenAPI 文档                         |
| MySQL                      | 8.0+   | 关系型数据库                             |
| Redis                      | 7+     | 缓存与消息订阅                            |
| JUnit 5                    | 最新     | 单元测试                               |
| Mockito                    | 最新     | 测试 Mock                            |
| Checkstyle                 | 最新     | 代码风格检查                             |
| SpotBugs                   | 最新     | Bug 模式检查                           |
| Maven                      | 3.9+   | 构建工具                               |

---

## 2. 分层架构

```
Controller → Service(接口) → ServiceImpl(实现) → Repository
    │             │                 │                │
 DTO 校验     业务逻辑编排         事务管理         数据访问
 参数转换      权限校验            领域逻辑         SQL/JPQL
 路由映射      跨服务调用
```

**依赖方向**（严格单向）：

```
Controller → Service(接口) → ServiceImpl → Repository → Entity
       ↓                      ↓
    DTO / VO              Entity / DTO
```

---

## 3. 各层职责

### 3.1 Controller

```java
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<UserVO>> list(UserQueryDTO query) {
        return ApiResponse.success(userService.list(query));
    }

    @PostMapping
    public ApiResponse<UserVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return ApiResponse.success(userService.create(dto));
    }
}
```

- 路径：`/api/{模块}/{资源}`
- 参数校验使用 `@Valid` + DTO 注解
- 返回 `ApiResponse<T>`，异常由全局处理器统一处理
- **禁止**在 Controller 中写业务逻辑

### 3.2 Service

```java
public interface UserService {
    PageResult<UserVO> list(UserQueryDTO query);
    UserVO create(UserCreateDTO dto);
    UserVO update(Long id, UserUpdateDTO dto);
    void delete(Long id);
}
```

- 接口定义业务契约
- 实现类标注 `@Transactional`
- 多个 Repository 写操作使用事务
- 权限校验在 Service 层完成

### 3.3 Repository

```java
// MyBatis-Plus 复杂查询
public interface UserMapper extends BaseMapperX<User> {
    Page<UserVO> queryPage(Page<User> page, @Param("query") UserQueryDTO query);
}
```

- 复杂统计、多表关联使用 MyBatis-Plus
- **禁止**在 Repository 层写业务判断

---

## 4. 命名规范

| 要素           | 规范                             | 示例                  |
| ------------ | ------------------------------ | ------------------- |
| Controller   | `XxxController`                | `UserController`    |
| Service 接口   | `XxxService`                   | `UserService`       |
| Service 实现   | `XxxServiceImpl`               | `UserServiceImpl`   |
| Repository   | `XxxRepository` / `XxxMapper`  | `UserRepository`    |
| Entity       | `Xxx`（与表名对应）                   | `User`              |
| DTO Request  | `XxxCreateDTO` / `XxxQueryDTO` | `UserCreateDTO`     |
| DTO Response | `XxxVO` / `XxxDTO`             | `UserVO`            |
| 异常类          | `XxxException`                 | `BusinessException` |
| 配置类          | `XxxConfig`                    | `SecurityConfig`    |
| 工具类          | `XxxUtils`                     | `TreeUtils`         |

---

## 5. DTO / Entity 规范

### Entity

```java
@Table(name = "user")
@Data
public class User {
    private Long id;

    private String username;

    private String passwordHash;

    private UserStatus status;
}
```

### Request DTO

```java
@Data
public class UserCreateDTO {
    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotEmpty
    private List<Long> roleIds;
}
```

### Response VO

```java
@Data
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String status;
    private List<RoleVO> roles;
    private List<WorkspaceBriefVO> workspaces;
    private LocalDateTime createdAt;
}
```

**命名转换**：Jackson 自动转换 `SNAKE_CASE` ↔ camelCase。

---

## 6. API 响应格式

### 统一响应体

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }
}
```

### 分页响应

```java
@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
}
```

---

## 7. 异常处理

### 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        return ApiResponse.error(1001, "参数校验失败");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ApiResponse.error(5000, "服务器内部错误");
    }
}
```

### 业务异常

```java
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { ... }
}

// 使用方式
if (userRepository.existsByUsername(dto.getUsername())) {
    throw new BusinessException(1002, "用户名已存在");
}
```

---

**文档结束**
