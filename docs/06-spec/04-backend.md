# 工程规范 — 后端

**文档版本**：V1.0
**日期**：2026-07-29
**状态**：已发布

---

## 1. 技术栈锁定

| 技术                         | 版本     | 说明                                 |
| -------------------------- | ------ | ---------------------------------- |
| Java                       | 21     | LTS 版本                             |
| migoo-springboot-framework | 1.3.18 | 最新版本                               |
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

### 5.3 对象转换（MapStruct）

**核心原则：所有 Entity → DTO / Response 的转换必须使用 MapStruct Converter，禁止在 Service 中手动 `new DTO()` + setter 逐字段拷贝。**

#### 存放位置

转换器定义在 `framework/convert/` 包下，按业务模块命名：

```
framework/convert/
  ├── UserConvertMapper.java         # 用户模块
  ├── RoleConvertMapper.java         # 角色模块
  ├── BugConvertMapper.java          # 缺陷模块
  └── WorkspaceMemberConvertMapper.java
```

Entity 按业务域分入子包：

```
model/entity/
  ├── admin/          SysUser, SysRole, SysUserRole, SysPermission, AuditLog
  ├── workspace/      Workspace, WorkspaceUser, WorkspaceInvitation, Project
  ├── tcase/          TestCaseModule, TestCaseNode, TestCaseDocumentLayout
  ├── plan/           TestPlan, TestPlanModuleSnapshot, TestPlanNodeSnapshot, TestPlanExecutionRecord
  ├── review/         TestReview, TestReviewModuleSnapshot, TestReviewNodeSnapshot, TestReviewRecord
  └── bug/            Bug, BugAttachment, BugLog
```

#### 基本模式

```java
@Mapper
public interface UserConvertMapper {

    UserConvertMapper INSTANCE = Mappers.getMapper(UserConvertMapper.class);

    @Mapping(target = "roles", ignore = true)
    UserRespDTO toRespDTO(SysUser user);

    default UserInfo toUserInfo(SysUser user) {
        if (user == null) return null;
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setName(user.getUsername());
        return info;
    }
}
```

- 接口标注 `@Mapper`，`INSTANCE` 通过 `Mappers.getMapper()` 获取
- 源-目标字段名一致时自动映射，不一致时用 `@Mapping` 显式声明
- `@Mapping(target = "xxx", ignore = true)` 跳过需服务层手工赋值的字段（如 UserInfo 需查 SysUser 表）
- 复杂逻辑（如组合多个源、构造嵌套对象）用 `default` 方法实现
- 字段名、类型均一致时无需任何注解

#### 使用示例

```java
// ✅ 正确：Service 中使用 Converter
BugListRespDTO dto = BugConvertMapper.INSTANCE.toListRespDTO(bug);
dto.setReporter(BugConvertMapper.INSTANCE.toUserInfo(userMapper.selectById(bug.getReporterId())));
```

```java
// ❌ 错误：在 Service 中手写逐字段拷贝
BugListRespDTO dto = new BugListRespDTO();
dto.setId(bug.getId());
dto.setTitle(bug.getTitle());
dto.setSeverity(bug.getSeverity());
// ... 十几行重复字段拷贝
```

#### 双向转换

- **Entity → Response DTO**：使用 MapStruct Converter
- **Request DTO → Entity**：MapStruct 处理纯字段映射，业务/上下文字段在 Service 中手工赋值
- **List 批量转换**：`recentLogs.stream().map(BugConvertMapper.INSTANCE::toLogRespDTO).collect(...)`

DTO→Entity 的 `toEntity()` 方法定义在 Converter 中，配合 `@Mapping(target = "...", ignore = true)` 跳过主键、审计、状态等由 Service 赋值的字段：

```java
@Mapper
public interface TestPlanConvertMapper {
    TestPlanConvertMapper INSTANCE = Mappers.getMapper(TestPlanConvertMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TestPlan toEntity(TestPlanCreateReqDTO dto);
}
```

Service 中使用：

```java
// ✅ 正确：Converter 处理纯字段映射，业务字段在 Service 中赋值
TestPlan plan = TestPlanConvertMapper.INSTANCE.toEntity(reqDTO);
plan.setProjectId(projectId);
plan.setStatus(Constants.Status.NEW);
testPlanMapper.insert(plan);
```

```java
// ❌ 错误：在 Service 中手动 new Entity() + setter 逐字段拷贝
TestPlan plan = new TestPlan();
plan.setProjectId(projectId);
plan.setName(reqDTO.getName());
plan.setDescription(reqDTO.getDescription());
// ... 十几行重复字段拷贝
plan.setStatus(Constants.Status.NEW);
testPlanMapper.insert(plan);
```

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

## 8. 数据更新规范

**核心原则：更新数据时，只更新前端（调用方）实际传入的字段。**

`selectById` 等查询仅用于校验（存在性、权限、状态），**禁止**将整行查询结果作为 `updateById` 的更新载体——那会生成全列 UPDATE，把未传字段用查询时刻的旧值重写，造成并发丢失更新。

### 8.1 常规部分更新（模式 A）

更新载体为新建实体，只携带 `id` + 本次传入的字段（MyBatis-Plus 默认 NOT_NULL 字段策略会自动忽略 null 字段）：

```java
// ✅ 正确：查询仅做校验，载体只携带变更字段
SysUser user = userMapper.selectById(id);
if (user == null) {
    throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
}
SysUser update = new SysUser();
update.setId(id);
if (StringUtils.hasText(reqDTO.getName())) {
    update.setName(reqDTO.getName());
}
userMapper.updateById(update);
```

```java
// ❌ 错误：整行查询结果作载体，全列 UPDATE 覆盖并发变更
SysUser user = userMapper.selectById(id);
user.setName(reqDTO.getName());
userMapper.updateById(user);
```

### 8.2 需要显式置 null 的更新（模式 B）

`updateById` 的 NOT_NULL 策略会静默忽略 null 字段，需要清空列时必须使用 `LambdaUpdateWrapperX` 显式 `.set(..., null)`：

```java
// ✅ 正确：显式置 null 走 wrapper
bugMapper.update(null, new LambdaUpdateWrapperX<Bug>()
        .eq(Bug::getId, bugId)
        .set(Bug::getStatus, Constants.BugStatus.ACTIVE)
        .set(Bug::getResolution, null)
        .set(Bug::getResolvedBy, null));
```

### 8.3 单元测试要求

- 模式 A：用 `ArgumentCaptor` 捕获 `updateById` 载体，断言只携带预期字段；同时 `verify(mapper, never())` 验证未发生意外更新
- 模式 B：先通过 `TableInfoHelper.initTableInfo` 注册实体列信息，再捕获 wrapper，断言 `getSqlSet()` 包含目标列（含置 null 列）、`getParamNameValuePairs()` 包含目标值

---

## 9. 查询封装规范

**核心原则：`LambdaQueryWrapperX` / `LambdaUpdateWrapperX` 必须在 Mapper 的 `default` 方法中封装，禁止在 Service 中直接构造 Wrapper。**

### 9.1 做法

在 Mapper 接口中定义 `default` 方法，将 Wrapper 构造和 MyBatis-Plus 调用统一封装：

```java
public interface BugMapper extends BaseMapperX<Bug> {

    default PageResult<Bug> findPage(PageParam pageParam, UUID projectId,
                                      String status, String severity) {
        LambdaQueryWrapperX<Bug> wrapper = new LambdaQueryWrapperX<Bug>()
                .eq(Bug::getProjectId, projectId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(Bug::getStatus, status);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(Bug::getSeverity, severity);
        }
        wrapper.orderByDesc(Bug::getCreatedAt);
        return this.selectPage(pageParam, wrapper);
    }

    default int resolveById(UUID id, UUID userId, String resolution, UUID duplicateOfBugId) {
        return this.update(null, new LambdaUpdateWrapperX<Bug>()
                .eq(Bug::getId, id)
                .set(Bug::getStatus, Constants.BugStatus.RESOLVED)
                .set(Bug::getResolvedBy, userId)
                .set(Bug::getResolution, resolution)
                .set(Bug::getDuplicateOfBugId, duplicateOfBugId));
    }
}
```

### 9.2 使用

Service 中调用 Mapper 封装方法，不再出现任何 Wrapper：

```java
// ✅ 正确：Service 委托给 Mapper.default 方法
PageResult<Bug> page = bugMapper.findPage(pageParam, projectId, status, severity);
bugMapper.resolveById(id, userId, resolution, duplicateOfBugId);
```

```java
// ❌ 错误：Service 中直接构造 Wrapper
LambdaQueryWrapperX<Bug> wrapper = new LambdaQueryWrapperX<>()
        .eq(Bug::getProjectId, projectId);
if (...) { wrapper.eq(...); }
bugMapper.selectPage(pageParam, wrapper);
```

### 9.3 命名约定

| 操作 | 命名模式 | 示例 |
|------|----------|------|
| 分页查询 | `findPage` / `find{PageName}Page` | `findPage(PageParam, UUID, ...)` |
| 列表查询 | `findBy{字段}` / `listBy{字段}` | `findByProjectId(UUID)` / `listByDocumentId(UUID)` |
| 计数 | `count{条件}` | `countOpenBugs(UUID projectId)` |
| 单条查询 | `findBy{字段}` | `findByNameAndParent(UUID projectId, UUID parentId, String name)` |
| 更新（Wrapper） | `{操作}By{条件}` | `resolveById(UUID, UUID, String, UUID)` / `reopenById(UUID, int)` / `updateSortOrder(UUID, int)` |
| 删除（Wrapper） | `deleteBy{条件}` | `deleteByUserIdAndRoleId(UUID, UUID)` |

### 9.4 注意事项

- `default` 方法中通过 `this` 调用 Mapper 自身的方法（`selectPage`、`selectList`、`selectCount`、`update`、`delete` 等）
- 含 `null` parentId 的场景用 `isNull()` 而非 `eq(null)`：`wrapper.isNull(Entity::getParentId)`
- 更新操作返回 `int`（影响行数），与 MyBatis-Plus 原生返回类型一致
- Service 仍负责业务校验、组装 DTO、事务管理——只将 Wrapper 构造下沉到 Mapper

---

**文档结束**
