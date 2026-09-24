# 工程规范 — 总览

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. 总则

### 1.1 编写目的

定义软件测试平台的工程实施规范，覆盖项目结构、开发流程、编码风格、质量保障、构建部署等全链路工程实践，确保团队协作一致性、代码可维护性和交付质量。

### 1.2 适用范围

适用于所有参与本平台开发的工程师（前端、后端、全栈），是代码审查和 CI 门禁的判定依据。

### 1.3 规范优先级

1. **IDE / 构建工具报错** > 代码审查意见
2. 自动化规则（ESLint / Checkstyle） > 本文档约定
3. 本文档约定 > 个人习惯
4. 如有冲突，以 Tech Lead 裁定为准

### 1.4 文档索引

| 文档          | 位置                      | 说明                              |
| ----------- | ----------------------- | ------------------------------- |
| 前端工程规范      | `docs/06-spec/03-frontend.md` | Vue3 + TS + 组件 + 路由 + 状态管理 + 样式 |
| 后端工程规范      | `docs/06-spec/04-backend.md`  | 分层架构 + DTO/Entity + 异常 + 响应格式   |
| API 设计规范    | `docs/06-spec/05-api.md`      | URL + 方法 + 分页 + 错误码 + WebSocket |
| 数据库规范       | `docs/06-spec/06-database.md` | 命名 + 表设计 + 索引 + 字段映射            |
| 质量保障规范      | `docs/06-spec/07-quality.md`  | 代码检查 + 测试策略 + 质量红线              |
| Git 与开发流程规范 | `docs/06-spec/08-workflow.md` | 分支模型 + 提交规范 + PR 规范             |
| 构建与部署规范     | `docs/06-spec/09-deploy.md`   | 构建流程 + 环境配置 + 部署 + CI           |
| 安全规范        | `docs/06-spec/10-security.md` | 认证 + 数据安全 + 防攻击 + 日志审计          |
| migoo 框架集成规范 | `docs/06-spec/11-migoo-framework.md` | Starter 清单 + 响应/实体/分页/转换约定 |
| 任务执行模板      | `docs/06-spec/12-task-template.md` | AI 任务八步流程（理解→评估→探查→方案→编码→验证→自检→交付） |
| 前端滚动容器规范   | `docs/06-spec/13-scroll-container.md` | 内部滚动、滚动条视觉隐藏、表格滚动与响应式约束 |

---

## 2. 项目结构

### 2.1 仓库目录结构

```
robotest/
├── .gitignore
├── AGENTS.md                  # AI 辅助开发约定（总则）
│
├── web/                       # 前端 SPA (Vue3 + Element Plus)
│   ├── package.json
│   ├── pnpm-workspace.yaml
│   ├── vite.config.ts
│   ├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
│   ├── index.html
│   ├── eslint.config.mjs      # ESLint flat config（含 no-restricted-imports 分层门禁）
│   ├── .prettierrc
│   ├── .env.development
│   ├── .env.production
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/            # index.ts（懒加载 + meta 守卫 admin/business）
│       ├── layouts/           # AdminLayout / BusinessLayout
│       ├── pages/             # admin/ auth/ workspace/ project/
│       ├── components/        # common/ admin/ assistant/ project/{api-testing,functional-testing,bug}
│       ├── stores/            # auth.ts / nav.ts / ai.ts / apiTestingUi.ts / assistantContext.ts
│       ├── composables/       # admin/ ai/ assistant/ auth/ project/
│       ├── services/          # index.ts / admin.ts / workspace.ts / project.ts / ai.ts + project/
│       ├── types/             # index.ts / admin.ts / workspace.ts / ai.ts / common.ts + project/
│       ├── minder/            # 自研脑图组件（SVG/Canvas + Yjs，ai/ 智能编辑）
│       ├── utils/             # format.ts
│       └── assets/styles/     # variables.scss / global.scss
│
├── server/                    # 后端服务 (Spring Boot)
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/github/xiaomisum/robotest/
│       │   ├── RobotestServer.java
│       │   ├── controller/    # admin/ apitest/ project/ workspace/
│       │   ├── service/       # admin/ ai/ apitest/ domain/ project/ websocket/ workspace/（接口+实现同包）
│       │   ├── repository/    # JPA / MyBatis-Plus 数据访问
│       │   ├── model/
│       │   │   ├── entity/            # 数据库映射（继承 BaseUuidDO）
│       │   │   └── dto/request|response/  # 按域分子目录
│       │   └── framework/     # 基础框架层（与业务无关）
│       │       ├── audit/ config/ convert/ interceptor/ security/
│       │       ├── common/    # Constants / ErrorCodeConstants
│       │       ├── mock/      # Mock 服务
│       │       ├── task/      # 定时任务
│       │       └── util/
│       ├── main/resources/    # application.yaml / db/ i18n/ ai/ logback-spring.xml
│       └── test/
│
├── scripts/                   # 构建与部署脚本
│   ├── build-frontend.sh
│   ├── build-backend.sh
│   ├── dev.sh
│   ├── deploy-separate.sh
│   ├── deploy-merged.sh
│   ├── validate.sh            # 提交前质量验证（提交格式 + lint + typecheck + test）
│   └── nginx.conf.example
│
└── docs/
    ├── AGENTS.md              # 文档管理约定
    ├── 06-spec/               # ← 工程规范（持续更新，不参与版本管理）
    │   ├── 01-readme.md       # 规范索引
    │   ├── 02-overview.md / 03-frontend.md / 04-backend.md / 05-api.md
    │   ├── 06-database.md / 07-quality.md / 08-workflow.md / 09-deploy.md
    │   └── 10-security.md / 11-migoo-framework.md / 12-task-template.md / 13-scroll-container.md
    ├── 01-requirements/ 02-high-level-design/ 03-architecture/ 04-detailed-design/ 05-interaction-design/
    │                                                 # 业务设计文档（各含 01-readme 索引）
    └── 07-archive/            # 归档基线（只读）
```

**核心原则**：

- 前端 `web/`、后端 `server/` 为两个独立项目，各自拥有 `package.json` / `pom.xml`，根目录不设 workspace。
- 跨端操作（构建、部署、一键启动）统一放在 `scripts/` 下。
- `docs/` 存放所有设计文档，与代码仓库同步。

### 2.2 文件命名规范

| 范畴            | 规范                     | 示例                           |
| ------------- | ---------------------- | ---------------------------- |
| Vue 组件        | PascalCase，多词组合        | `UserList.vue`               |
| TypeScript 文件 | camelCase              | `useAuth.ts`                 |
| 页面目录          | kebab-case，与路由 path 一致 | `pages/admin/users/`         |
| 后端 Java 类     | PascalCase             | `UserController.java`        |
| 后端 Java 包     | 全小写                    | `io.github.xiaomisum.robotest.service.admin` |
| 后端资源文件        | kebab-case             | `application-dev.yml`        |
| 数据库表          | snake_case             | `test_case_module`           |
| 脚本文件          | kebab-case             | `build-frontend.sh`          |

### 2.3 目录职责边界

| 目录             | 职责                  | 禁止行为         |
| -------------- | ------------------- | ------------ |
| `pages/`       | 页面级组件，布局+数据编排       | 不可包含通用 UI 逻辑 |
| `components/`  | 通用 UI 组件，纯展示 + emit | 不可直接调 API    |
| `composables/` | 组合式逻辑复用             | 不可包含 UI 渲染   |
| `services/`    | API 请求封装            | 不可处理 UI 状态   |
| `stores/`      | 全局状态管理              | 不可直接发请求      |
| `types/`       | 类型定义                | 不可包含运行时逻辑    |
| `utils/`       | 纯函数工具               | 不可有副作用       |
| `config/`      | 配置类                 | 不可包含业务逻辑     |
| `controller/`  | 路由 + 参数校验           | 不可包含业务逻辑     |
| `service/`     | 业务逻辑编排              | 不可直接操作数据库    |
| `repository/`  | 数据访问                | 不可包含业务判断     |
| `entity/`      | 数据库映射               | 不可包含业务方法     |
| `dto/`         | 数据传输                | 不可包含业务方法     |

---

## 3. 文档规范

### 3.1 文档类型与维护

| 文档      | 维护者       | 同步策略         |
| ------- | --------- | ------------ |
| 需求规格说明书 | PM / 业务分析 | 需求变更时更新      |
| 概要设计说明书 | 架构师       | 架构调整时更新      |
| 详细设计说明书 | 开发工程师     | 接口/数据结构变更时同步 |
| 页面交互设计  | 前端 / UX   | UI 变更时同步     |
| 工程规范（`docs/06-spec/`） | Tech Lead | 定期评审更新       |

文档格式：Markdown，存放在 `docs/` 目录，与代码仓库同步管理。

### 3.2 API 文档

- 后端使用 **SpringDoc (OpenAPI 3)** 自动生成，无需手动维护独立的 API 文档。
- Controller 类和方法添加 `@Operation`、`@Schema` 注解以提供描述信息。
- 前后端通过 OpenAPI JSON 契约对齐类型定义。

### 3.3 代码注释

- **不要求**每个方法都有注释，但核心业务逻辑、复杂算法、非常规处理的代码必须有注释说明「为什么」。
- 禁止逐行写废话注释（`// 设置用户名`）。
- API 接口的注释通过 SpringDoc 注解提供，不在代码中写 JavaDoc 重复描述。

---

## 4. 附录

### 4.1 工具链版本锁定

```
Node.js >= 20 LTS
pnpm >= 8
Java >= 21 (Temurin / OpenJDK)
Maven >= 3.9
MySQL >= 8.0
Redis >= 7
```

### 4.2 常用命令速查

```bash
# 本地开发
cd web && pnpm run dev          # 前端 (端口 5173)
cd server && mvn spring-boot:run -Pdev  # 后端 (端口 8080)
bash scripts/dev.sh            # 一键启动

# 构建
cd web && pnpm run build
cd server && mvn package -Pprod

# 代码检查
cd web && pnpm run lint && pnpm run typecheck
cd server && mvn verify

# 测试
cd web && pnpm run test:unit -- --coverage
cd server && mvn test
```

### 4.3 参考文档

- [MiGoo Spring Boot 框架文档](https://xiaomisum.github.io/springboot-migoo-framework/)
- 《软件测试平台需求规格说明书》
- 《软件测试平台概要设计说明书》
- 《软件测试平台项目仓库框架与技术架构设计》
- 《软件测试平台系统管理模块详细设计说明书》
- 《软件测试平台空间管理业务模块详细设计说明书》
- 《软件测试平台项目模块详细设计说明书》

---

**文档结束**
