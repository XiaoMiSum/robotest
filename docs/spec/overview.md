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
| 前端工程规范      | `docs/spec/frontend.md` | Vue3 + TS + 组件 + 路由 + 状态管理 + 样式 |
| 后端工程规范      | `docs/spec/backend.md`  | 分层架构 + DTO/Entity + 异常 + 响应格式   |
| API 设计规范    | `docs/spec/api.md`      | URL + 方法 + 分页 + 错误码 + WebSocket |
| 数据库规范       | `docs/spec/database.md` | 命名 + 表设计 + 索引 + 字段映射            |
| 质量保障规范      | `docs/spec/quality.md`  | 代码检查 + 测试策略 + 质量红线              |
| Git 与开发流程规范 | `docs/spec/workflow.md` | 分支模型 + 提交规范 + PR 规范             |
| 构建与部署规范     | `docs/spec/deploy.md`   | 构建流程 + 环境配置 + 部署 + CI           |
| 安全规范        | `docs/spec/security.md` | 认证 + 数据安全 + 防攻击 + 日志审计          |

---

## 2. 项目结构

### 2.1 仓库目录结构

```
software-testing-platform/
├── .gitignore
├── README.md
├── AGENTS.md                  # AI 辅助开发约定
│
├── web/                       # 前端 SPA (Vue3 + Element Plus)
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── .eslintrc.cjs
│   ├── .prettierrc
│   ├── .env
│   ├── .env.development
│   ├── .env.production
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/            # index.ts / admin.ts / business.ts
│       ├── layouts/           # AdminLayout / WorkspaceLayout / ProjectLayout
│       ├── pages/             # admin/ / workspace/ / project/
│       ├── components/        # common/ / admin/ / workspace/ / project/
│       ├── stores/            # auth.ts / workspace.ts / project.ts
│       ├── composables/       # useAuth / useWebSocket / usePermission
│       ├── services/          # request.ts / admin.ts / workspace.ts / project.ts
│       ├── types/             # api.ts / admin.ts / workspace.ts / project.ts
│       ├── constants/         # permission.ts / status.ts / error.ts
│       ├── ws/                # index.ts / handlers.ts
│       ├── utils/             # format.ts / validate.ts / tree.ts
│       └── assets/styles/     # variables.scss / global.scss
│
├── server/                    # 后端服务 (Spring Boot)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/platform/
│       │   ├── PlatformApplication.java
│       │   ├── config/        # Security / WebSocket / Cors / WebMvc / Jackson
│       │   ├── controller/    # admin/ / workspace/ / project/
│       │   ├── service/       # (interfaces)
│       │   ├── service/impl/  # (implementations)
│       │   ├── repository/
│       │   ├── model/entity/
│       │   ├── model/dto/request/
│       │   ├── model/dto/response/
│       │   ├── security/      # JwtProvider / JwtAuthFilter / AccessContext
│       │   ├── websocket/     # DocumentHandler / YjsDecoder / RoomManager
│       │   ├── interceptor/   # WorkspaceContext / ProjectContext
│       │   ├── exception/     # GlobalHandler / BusinessException / ErrorCode
│       │   └── common/        # ApiResponse / PageResult
│       ├── main/resources/    # application-{profile}.yml
│       └── test/
│
├── scripts/                   # 构建与部署脚本
│   ├── build-frontend.sh
│   ├── build-backend.sh
│   ├── dev.sh
│   ├── deploy-separate.sh
│   └── deploy-merged.sh
│
└── docs/
    ├── spec/                  # ← 工程规范拆分目录
    │   ├── overview.md
    │   ├── frontend.md
    │   ├── backend.md
    │   ├── api.md
    │   ├── database.md
    │   ├── quality.md
    │   ├── workflow.md
    │   ├── deploy.md
    │   └── security.md
    ├── 工程规范说明书.md        # 索引文件（指向 spec/）
    └── (其他设计文档)
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
| 后端 Java 包     | 全小写                    | `com.platform.service.admin` |
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
| 工程规范说明书 | Tech Lead | 定期评审更新       |

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
- 《软件测试平台功能测试模块详细设计说明书》

---

**文档结束**
