# 软件测试平台——质量保障规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 质量门禁原则

质量门禁分为三类：

| 类型 | 含义 | 失败行为 |
| --- | --- | --- |
| Blocking | 编译、类型、安全或核心行为错误 | 禁止合并 |
| Advisory | 风险提示或非核心质量问题 | 记录并限期处理 |
| Informational | 趋势、覆盖率和文档提示 | 不阻断当前合并 |

本文定义目标门禁。只有在仓库脚本、CI 或测试配置中实际配置后，才能将目标描述为“已强制执行”。

## 2. 代码检查

| 范围 | 工具 | 要求 | 当前状态 |
| --- | --- | --- | --- |
| 前端格式 | Prettier | 统一格式 | 以项目脚本为准 |
| 前端质量 | ESLint + Vue ESLint | Blocking | `pnpm run lint` |
| 前端类型 | TypeScript / vue-tsc | Blocking | `pnpm run typecheck` |
| 前端测试 | Vitest | Blocking | `pnpm run test:unit` |
| 后端编译 | Maven Compiler | Blocking | `mvn test` / `mvn verify` |
| 后端测试 | JUnit 5 / Spring Boot Test | Blocking | `mvn test` |
| 依赖与安全扫描 | 依赖漏洞、Secret、许可证扫描 | 目标门禁 | 以实际 CI 配置为准 |

`web/eslint.config.mjs` 中的层级门禁是前端架构检查的一部分，不能被普通代码审查替代。

本项目当前不将 SpotBugs、ArchUnit 和 JaCoCo 作为强制门禁；相关代码通过编译、单元/集成测试、依赖扫描、契约检查和人工审查保障。若未来重新引入，必须单独记录原因、配置和验收标准。

## 3. 测试策略

| 层级 | 目标 | 主要内容 | 要求 |
| --- | --- | --- | --- |
| 单元测试 | 核心业务逻辑 | Service、composable、纯函数、数据组装 | 必须覆盖正常和异常分支 |
| 组件测试 | UI 行为 | 渲染、交互、加载、空态、错误态 | 关键组件必须覆盖 |
| API 测试 | 契约和授权 | MockMvc/OpenAPI、响应、分页、错误码、越权 | 变更接口必须覆盖 |
| 契约测试 | 前后端一致性 | OpenAPI JSON 与前端生成类型 | 契约基线存在时必须执行 |
| E2E | 核心流程 | 登录、空间切换、项目编辑、协作 | 核心流程按发布风险执行 |
| 安全测试 | 认证和隔离 | Token、workspace、project、WebSocket、越权 | 高风险变更必须执行 |
| 性能测试 | 关键路径 | 分页、列表、协作和导入导出 | 有容量风险时执行 |

测试工具必须锁定在项目依赖或 CI 环境中。Postman、Cypress、Playwright 等候选工具不能同时作为“强制工具”而不说明实际选择。

## 4. 覆盖率

- 核心模块覆盖率目标为 **不低于 70%（C8）**。
- 覆盖率范围必须明确：全仓库、核心模块或变更模块。
- 新增或修改的核心逻辑不得通过删除测试、排除目录或降低阈值绕过门禁。
- 当前如果尚未配置 Vitest/JUnit 覆盖率阈值，必须在报告中标记为“未配置”，不能写成“已通过”。
- 覆盖率报告应随 CI 或验证产物保存。

## 5. 前端测试要求

- 纯函数、composable、Store 和请求适配器使用单元测试。
- 组件测试必须覆盖加载、空数据、错误、权限不足和提交中状态。
- API service 测试必须断言 `Result` 解包、业务错误和 HTTP 错误。
- 时间处理测试必须覆盖 UTC、带时区、无时区和非法输入。
- 滚动和响应式行为按 `13-scroll-container.md` 进行浏览器验收。

示例：

```ts
it('加载失败时保留可重试状态', async () => {
  const wrapper = mount(UserList)
  await wrapper.find('[data-test="retry"]').trigger('click')
  expect(wrapper.text()).toContain('加载失败')
})
```

## 6. 后端测试要求

- Service 测试必须覆盖权限、状态流转、事务和异常分支。
- Controller 测试必须验证参数校验、响应包装和授权边界。
- Mapper 测试必须验证 Wrapper 条件、分页、影响行数和逻辑删除。
- C11 必须验证更新载荷、显式置空和并发冲突。
- 跨 workspace/project 的资源访问必须有越权测试。
- WebSocket 测试必须覆盖连接鉴权、房间权限、编辑权限和错误帧。

示例中的类型、类名和构造方式必须与当前代码一致；无法编译的伪代码必须明确标注。

## 7. 契约和安全检查

API 变更必须验证：

- `Result.code/msg/data`；
- `pageNo/pageSize` 和 `list/total`；
- 10 位错误码；
- 认证、匿名接口和上下文 Header；
- OpenAPI 与前端生成类型；
- SSE、文件和 WebSocket 的特殊响应。

安全变更必须验证：

- 管理端接口不能只依赖前端路由守卫；
- workspace/project 越权访问被拒绝；
- Token、密码、API Key 和连接信息不出现在日志；
- 生产环境没有默认密钥；
- 外部 URL、文件上传和 WebSocket Origin 受到限制。

## 8. 本地验证命令

```bash
# 前端
cd web
pnpm run lint
pnpm run typecheck
pnpm run test:unit

# 后端
cd server
mvn test
mvn verify

# 项目统一脚本
bash scripts/validate.sh --all
```

脚本当前实际行为以 `scripts/validate.sh` 为准。若脚本尚未覆盖覆盖率、静态分析或安全扫描，应补充实现或降低文档中的门禁表述。

## 9. 质量红线

以下情况不得合并：

- 编译、类型检查或核心测试失败；
- 引入前端 `any` 或无理由的类型逃逸；
- Controller 出现业务逻辑；
- 业务异常绕过统一错误码；
- 上下文 ID 出现在 URL 或请求体；
- 数据库出现物理外键或缺少 C5 字段；
- 覆盖率低于已配置的核心模块阈值；
- 生产配置提交可预测密钥；
- 认证、授权、workspace/project 隔离或 WebSocket 权限存在已知高风险绕过；
- 敏感信息进入日志、审计或错误响应。

## 10. 缺陷和例外

- 测试失败不得通过删除断言、跳过测试或扩大排除范围解决。
- 紧急修复可以先走 hotfix 流程，但仍必须完成最小回归和风险评估。
- 质量例外必须记录范围、原因、批准人、失效时间和补救计划。
- 质量门禁的例外不能由个人口头决定。

## 11. 参考

- API 契约：`docs/06-spec/05-api.md`
- 安全基线：`docs/06-spec/10-security.md`
- 构建与 CI：`docs/06-spec/09-deploy.md`
- AI 任务流程：`docs/06-spec/12-task-template.md`

---

**文档结束**
