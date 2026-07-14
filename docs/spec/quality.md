# 工程规范 — 质量保障

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. 代码检查

| 层面   | 工具                         | 触发时机             |
| ---- | -------------------------- | ---------------- |
| 前端格式 | Prettier                   | 提交前（lint-staged） |
| 前端质量 | ESLint + Vue ESLint Plugin | 提交前 + CI         |
| 后端格式 | Checkstyle（Google 风格）      | 提交前 + CI         |
| 后端质量 | SpotBugs                   | CI               |
| 类型检查 | TypeScript `tsc --noEmit`  | 提交前 + CI         |

**配置文件位置**：

- 前端：`web/.eslintrc.cjs`、`web/.prettierrc`
- 后端：`server/checkstyle.xml`（在 pom.xml 中配置）

**CI 门禁规则**：

- ESLint / Checkstyle：error 级别不通过则构建失败
- TypeScript 编译：严格模式，不通过则构建失败
- 单元测试：覆盖率不低于 70%，不达标记为构建不稳定

---

## 2. 测试策略

| 层级     | 工具                   | 目标覆盖率 | 内容            |
| ------ | -------------------- | ----- | ------------- |
| 单元测试   | Vitest / JUnit 5     | ≥ 70% | Service 层核心逻辑 |
| 组件测试   | Vue Test Utils       | 关键组件  | 列表渲染、表单提交     |
| API 测试 | MockMvc / Postman    | 所有端点  | 正常 + 异常场景     |
| E2E 测试 | Playwright / Cypress | 核心流程  | 登录、空间切换、脑图编辑  |

### 2.1 后端单元测试

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void createUser_shouldThrow_whenUsernameExists() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);
        assertThrows(BusinessException.class, () -> {
            userService.create(new UserCreateDTO().setUsername("existing"));
        });
    }
}
```

### 2.2 前端单元测试

```typescript
describe('UserList', () => {
  it('renders user list', () => {
    const wrapper = mount(UserList, { props: { users: mockUsers } })
    expect(wrapper.findAll('.user-card')).toHaveLength(3)
  })
})
```

### 2.3 测试文件命名

- 后端：`XxxTest.java`（与测试类同名）
- 前端：`Xxx.spec.ts`（与被测文件在同一目录）

---

## 3. 质量红线

| 红线               | 处理方式         |
| ---------------- | ------------ |
| 引入 `any` 类型      | 代码审查打回       |
| 跳过测试             | 代码审查打回       |
| 硬编码敏感信息（密钥、密码）   | 立即修复，审查者连带责任 |
| 覆盖率低于 70%        | CI 构建不稳定标记   |
| 存在 SpotBugs 高危问题 | CI 构建失败      |

---

**文档结束**
