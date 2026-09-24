# server/AGENTS.md — 后端约定

> 本文件适用于 `server/` 目录。总则见根目录 `AGENTS.md`，全局共享约定以总则为准。

---

## 技术栈

- **框架**：Spring Boot 4.x + Java 21
- **数据访问**：MyBatis-Plus
- **安全**：Spring Security（JWT 双令牌 + RBAC）
- **协作**：WebSocket（Yjs CRDT）

## 环境命令

```bash
# 开发（端口 58080，dev profile）
mvn spring-boot:run -Pdev

# 构建 → target/*.jar（prod profile）
mvn package -Pprod

# 质量门禁
mvn test
```

## 架构

分层：**Controller → Service → Repository**

- `controller/{admin,apitest,project,workspace}/`：仅路由 + 参数校验（`@Valid`），无业务逻辑
- `service/{admin,ai,apitest,domain,project,websocket,workspace}/`：接口 + 实现同包，按业务域分组
- `service/websocket/`：业务 WebSocket 处理（DocumentHandler、DocumentPersistenceHandler）
- `repository/`：MyBatis-Plus Mapper 数据访问
- `model/entity/` ↔ `model/dto/request|response/`
- `model/convert/`：MapStruct 对象转换
- `framework/`：基础框架层（与业务无关）
  - `audit/`：审计注解 + AOP 切面
  - `common/`：`Constants` / `ErrorCodeConstants`
  - `config/`：Spring MVC 配置
  - `interceptor/`：工作空间角色权限拦截器
  - `security/`：JWT 双令牌 + RBAC（LoginUser、UserDetailsBridgeImpl）
  - `websocket/`：已移除（房间管理由框架 `migoo-spring-boot-starter-websocket` 原生支持）

**核心子系统**：认证授权 → 上下文（workspace）隔离 → 评审/计划快照 → WebSocket 协作  
（详见 `docs/06-spec/10-security.md`、`docs/06-spec/05-api.md`、`docs/03-architecture/`、`docs/04-detailed-design/`）

> 详细分层职责参见 `docs/06-spec/04-backend.md`。

## 核心约定

| 编号  | 规则                                                    | 检查方式      |
| --- | ----------------------------------------------------- | --------- |
| C10 | 优先使用 migoo 框架提供的基础功能（验证注解、工具类等），禁止重复造轮子（后端）     | 代码审查      |
| C2  | Controller 不允许包含业务逻辑，只能路由+校验                        | 代码审查      |
| C3  | 业务异常统一通过 migoo `ServiceExceptionUtil.get(ErrorCode)` 抛出，使用 10 位错误码 | 代码审查      |
| C5  | 数据库每表必须有 `id`、`created_at`、`updated_at`、`is_deleted`（逻辑删除），UUID 使用框架默认策略，禁止物理外键 | 数据库审查     |
| C8  | 单测覆盖率 ≥ 70%                                            | CI        |
| C11 | 更新数据只更新调用方实际传入的字段：查询仅做校验，禁止整行查询结果作 `updateById` 载体；显式置 null 用 `LambdaUpdateWrapperX` | 代码审查      |

> 编号以根目录 `AGENTS.md` 总则为准，本表只列后端专属与共享条目在后端的落地口径；C4（上下文头）、C7（提交格式）见总则。

## 边界

- 只修改 `server/` 目录下的文件，不碰 `web/` 代码
- Controller 仅负责路由与参数校验，业务逻辑在 Service 层（C2）
- 上下文标识（如 workspaceId/projectId）仅通过请求头 `X-Active-Workspace` / `X-Active-Project` 传递（C4），不出现在活动上下文 URL 或请求体中；资源自身 ID 按 API 规范处理
- 避免新增外部依赖，确有必要时需经团队讨论

## 框架集成（migoo-spring-boot-starter v1.3.18）

> 完整规范见 `docs/06-spec/11-migoo-framework.md`，框架文档：https://xiaomisum.github.io/springboot-migoo-framework/

### 响应与异常

```java
return Result.ok(data);                    // 成功
return Result.error(ErrorCodeConstants.X); // 错误
throw ServiceExceptionUtil.get(ErrorCodeConstants.X); // 业务异常
```

### 实体与 Mapper

```java
// 实体：继承 BaseUuidDO（UUID 主键，自动填充 createdAt/updatedAt/isDeleted）
public class SysUser extends BaseUuidDO<SysUser> {
    private String username;
}

// Mapper：继承 BaseMapperX（selectOne/selectCount/selectPage/insertBatch/updateBatch）
public interface SysUserMapper extends BaseMapperX<SysUser> {}

// Wrapper：使用 LambdaQueryWrapperX（xxxIfPresent 自动跳过 null）
new LambdaQueryWrapperX<SysUser>()
    .likeIfPresent(SysUser::getName, name)
    .eqIfPresent(SysUser::getStatus, status);
```

### 数据更新（部分更新原则，C11）

> 完整规范见 `docs/06-spec/04-backend.md` 第 8 节。查询仅用于校验，更新载体只携带 `id` + 本次变更字段。

```java
// ✅ 部分更新：新建载体，NOT_NULL 策略自动忽略未设置字段
SysUser update = new SysUser();
update.setId(id);
update.setName(reqDTO.getName());
userMapper.updateById(update);

// ✅ 需要清空列：必须走 wrapper 显式置 null（updateById 会静默忽略 null）
userMapper.update(null, new LambdaUpdateWrapperX<SysUser>()
    .eq(SysUser::getId, id)
    .set(SysUser::getLastActiveWorkspaceId, null));

// ❌ 禁止：整行查询结果作载体，全列 UPDATE 覆盖并发变更
SysUser user = userMapper.selectById(id);
user.setName(reqDTO.getName());
userMapper.updateById(user);
```

### 分页

```java
// 请求 DTO 继承 PageParam（pageNo/pageSize）
// Service 返回 PageResult<T>（list/total）
PageResult<SysUser> page = userMapper.selectPage(
    new PageParam() {{ setPageNo(1); setPageSize(20); }}, wrapper);
```

### 对象转换（MapStruct）

```java
// 转换器统一放在 model/convert/，使用 Spring Bean 注入
@Mapper(componentModel = "spring")
public interface UserConvertMapper {
    UserRespDTO toRespDTO(SysUser entity);
    List<UserRespDTO> toRespDTOList(List<SysUser> entities);
    SysUser toEntity(UserCreateReqDTO dto);
}

// Service 中注入后使用
@Resource
private UserConvertMapper userConvertMapper;

UserRespDTO dto = userConvertMapper.toRespDTO(user);
```

### 工具类速查

| 类 | 用途 |
|----|------|
| `JsonUtils` | `toJsonString(obj)` / `parseObject(json, Class)` / `parseObject(json, TypeReference)` |
| `CollectionUtils` | `convertList` / `convertMap` / `filterList` |
| `ServiceExceptionUtil` | `get(ErrorCode)` / `get(ErrorCode, args...)` — 抛出业务异常 |

### 验证注解（`xyz.migoo.framework.common.validation`）

| 注解 | 用途 | 示例 |
|------|------|------|
| `@Password` | 密码强度校验（大小写+数字+特殊字符） | `@Password private String password;` |
| `@Email` | 邮箱格式校验 | `@Email private String email;` |
| `@Mobile` | 手机号格式校验 | `@Mobile private String phone;` |
| `@InEnum` | 枚举值范围校验 | `@InEnum(StatusEnum.class) private Integer status;` |
