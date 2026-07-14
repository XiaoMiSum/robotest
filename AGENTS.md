# AGENTS.md — AI 辅助开发约定（总则）

> 本文件为 AI 编程助手（及人类开发者）的快速参考指南。  
> 详细规范请查阅 `docs/spec/` 目录下的对应文档。  
> 端专属约定见 `web/AGENTS.md` 与 `server/AGENTS.md`。

---

## 1. 项目定位

**软件测试平台** — 前后端分离单体仓库（`web/` + `server/`）。  
五大业务域：**系统管理**（用户/空间/角色）、**空间管理**（成员/项目）、**功能测试**（用例/评审/计划）、**接口测试**（暂不提供）、**缺陷管理**。

---

## 2. 技术栈

- **前端**：Vue 3.5 + TypeScript 6.x（strict）+ Vite 8 + Element Plus + Pinia
- **后端**：Spring Boot 4.x + Java 21 + MyBatis-Plus + Spring Security
- **协作**：Yjs CRDT（WebSocket 实时协同）

---

## 3. 环境与命令

### 本地开发

```bash
# 前端（端口 5173）
cd web && pnpm install && pnpm run dev

# 后端（端口 8080，dev profile）
cd server && mvn spring-boot:run -Pdev

# 一键启动（同时前后端）
bash scripts/dev.sh
```

### 构建

```bash
# 前端构建 → web/dist/
cd web && pnpm run build

# 后端构建 → server/target/*.jar（prod profile）
cd server && mvn package -Pprod

# 前端构建物合并到后端静态资源（用于一体化部署）
bash scripts/deploy-merged.sh
```

### 验证与质量门禁

| 端   | 命令                                                                          |
| --- | --------------------------------------------------------------------------- |
| 前端  | `pnpm run lint && pnpm run typecheck && pnpm run test:unit -- --coverage`   |
| 后端  | `mvn checkstyle:check && mvn test`                                          |
| 全量  | `pnpm run lint && pnpm run typecheck && pnpm run test:unit`<br>`mvn verify` |

**提交前逐项核对：**

- [ ] lint / typecheck / test 全部通过
- [ ] 覆盖率达标（C8）
- [ ] 无 `any`（C1）、无业务逻辑在 Controller（C2）
- [ ] 所有异常使用 `BusinessException`（C3）
- [ ] 上下文头未出现在 URL 中（C4）
- [ ] 若涉及数据库，包含迁移说明并确认无物理外键（C5）
- [ ] 注释只写 why（C6）
- [ ] 提交信息符合格式（C7）

> 环境变量：`web/.env.development` / `web/.env.production` 配置 `VITE_API_BASE_URL` 与 `VITE_WS_BASE_URL`；`.env.local` 可覆盖（已 gitignore）。

---

## 4. 共享约定

| 编号  | 规则                                                    | 检查方式      |
| --- | ----------------------------------------------------- | --------- |
| C4  | 上下文标识（如 workspaceId）**禁止**出现在 URL 或请求体中，仅通过请求头传递      | 代码审查      |
| C5  | 数据库每表必须有 `id`（自增或雪花）、`created_at`、`updated_at`，禁止物理外键 | 数据库审查     |
| C7  | Git 提交格式：`<type>(<scope>): <description>`，一个提交只做一件事   | 审查 squash |

> 端专属约定（C1 前端类型安全、C2 Controller 职责、C3 异常规范、C6 注释规范、C8 覆盖率）及编码示例见各端 `AGENTS.md`。
> 详细规范索引：`docs/spec/overview.md`、`docs/spec/backend.md`、`docs/spec/frontend.md`、`docs/spec/api.md`、`docs/spec/security.md`、`docs/spec/database.md`、`docs/spec/deploy.md`、`docs/spec/quality.md`、`docs/spec/workflow.md`。

---

## 5. 边界与约束

- **目录隔离**：前端只改 `web/`，后端只改 `server/`，禁止越界修改另一端的代码（端专属边界见各端 `AGENTS.md`）
- **依赖管控**：尽量避免新增外部依赖，确有必要时需经团队讨论

---

## 6. AI 任务执行流程

当被指派开发任务时，严格遵循以下步骤：

1. **理解**  
   - 阅读相关 `docs/spec/` 规范（优先），再阅读 `docs/需求/`、`docs/概要/`、`docs/架构/`、`docs/详细设计/`、`docs/交互设计/` 中对应的业务文档。  
   - 确认需求涉及的前端/后端范围、数据模型、API 变更。

2. **探查**  
   - 查看相邻层级的现有代码（同模块的 Controller / Service / Repository，或同页面的 components / services / stores）。  
   - 确认命名模式、导入路径、类型定义，保持风格一致。

3. **方案**  
   - 确定需新建/修改哪些文件（Controller、Service、Repository、DTO、Entity、页面、组件、API、类型等）。  
   - 若涉及数据库，提供 DDL 变更（需符合 C5）；若涉及 API，先确定 URL、方法、请求/响应结构。  
   - 评估是否需要新增依赖（**尽量避免**，除非确有必要并经过讨论）。

4. **编码**  
   - 严格遵守端内约定（C1–C3、C6、C8），详见各端 `AGENTS.md`。
   - 所有跨域上下文（workspace）通过请求头传递（C4）。

5. **验证**  
   - 运行对应端的 lint、类型检查、单元测试（参见第 3 节命令）。  
   - 确保覆盖率满足要求（C8）。  
   - 手动测试关键路径（使用 `curl` 或前端界面，参考 `docs/spec/deploy.md` 中的示例）。

6. **自检**  
   - 逐条核对核心约定（C1–C8），确认未引入违规。  
   - 确认 API 文档（SpringDoc）是否需要更新（后端变更时）。

7. **交付**  
   - 将变更提交至对应的 `feature/*` 或 `fix/*` 分支（提交格式符合 C7）。  
   - 向用户说明改动内容、测试结果，**不主动合并或 push**（待用户确认后发起 PR）。

---

## 7. 参考文档优先级

当信息冲突时，以更高优先级为准：

1. **`docs/spec/*.md`** — 工程规范（最高，不可违背）  
2. **`docs/架构/*.md`** — 架构设计与技术选型依据  
3. **`docs/详细设计/*.md`** + **`docs/概要/*.md`** — 业务逻辑与数据流依据  
4. **`docs/需求/*.md`** — 功能性需求来源  
5. **`docs/交互设计/*.md`** — 前端页面行为参考  
6. **同层邻接代码** — 实现细节风格的参考（次于文档）

> 若规范未覆盖，参考同模块已实现的类似功能。

---

## 8. 沟通与协作

- 所有 API 变更应先后端后前端，通过 OpenAPI 文档同步（`springdoc-openapi`）。  
- 跨端消息格式（WebSocket）需前后端共同确认 handler 匹配。  
- 若遇不确定性，优先查阅 `docs/spec/` 或向用户提问，不要臆断。  
- 分支策略：`main` ← `develop` ← `feature/*` / `fix/*` / `hotfix/*` / `release/*`，所有合并走 PR。

---
