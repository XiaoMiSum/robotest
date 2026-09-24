# 软件测试平台——项目仓库框架与技术架构设计

**文档版本**：V1.1
**日期**：2026-09-24
**状态**：已发布

---

## 1. 设计目标

本设计定义项目的仓库结构、技术边界、开发运行模式、构建部署方式和质量保障入口。

架构设计只描述系统边界和技术关系；具体 API、数据库、业务流程和页面行为分别由对应的需求、详细设计和交互设计文档定义。

## 2. 仓库整体结构

前端与后端作为同一仓库中的两个独立项目，各自管理依赖和构建流程，跨端操作通过根目录 `scripts/` 协调。

```text
robotest/
├── web/                         # 前端 SPA
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── vite.config.ts
│   ├── tsconfig*.json
│   └── src/
│       ├── router/              # 路由和导航元信息
│       ├── layouts/             # 页面布局
│       ├── pages/               # 页面级编排
│       ├── components/          # 可复用 UI 组件
│       ├── composables/         # 组合式逻辑
│       ├── services/            # HTTP/SSE/实时请求适配
│       ├── stores/              # Pinia 全局状态
│       ├── types/               # TypeScript 类型
│       └── assets/              # 样式和静态资源
│
├── server/                      # 后端服务
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/github/xiaomisum/robotest/
│       │   ├── RobotestServer.java
│       │   ├── controller/      # 路由、参数校验和响应包装
│       │   ├── service/         # 业务编排和事务
│       │   ├── repository/      # Mapper 和数据访问
│       │   ├── model/           # Entity、DTO、转换器
│       │   └── framework/       # 安全、配置、审计和公共适配
│       ├── main/resources/
│       │   ├── application.yaml
│       │   └── db/              # 初始化基线和迁移脚本
│       └── test/
│
├── scripts/                     # 构建、启动和部署脚本
├── docs/                        # 需求、设计、规范和交互文档
└── AGENTS.md                    # AI 开发总约定
```

目录名称和实现细节以当前仓库为准；本树只表达职责边界，不作为完整文件清单。

## 3. 技术选型

| 层次 | 技术 | 约束和来源 |
| --- | --- | --- |
| 前端框架 | Vue 3.5 + TypeScript strict | 版本以 `web/package.json` 和锁文件为准 |
| 前端构建 | Vite | 版本以 `web/package.json` 和锁文件为准 |
| UI | Element Plus | 以依赖锁定版本为准 |
| 状态管理 | Pinia | 以依赖锁定版本为准 |
| HTTP | Axios | 统一请求拦截器和响应解包 |
| 实时通信 | WebSocket；协作场景可采用 Yjs | 通用协议见 `docs/00-spec/20-contracts/03-realtime-protocol.md` |
| 后端运行时 | Java 21 + Spring Boot 4.x | 版本由 Maven BOM 和 `server/pom.xml` 管理 |
| 后端框架 | migoo `1.3.18` | 组件手册见 `docs/00-spec/10-engineering/03-migoo-framework.md` |
| 数据访问 | MyBatis-Plus + migoo MyBatis Starter | 复杂查询按后端规范封装 |
| 认证授权 | Spring Security + migoo Security Starter | 服务端执行最终授权 |
| 数据库 | PostgreSQL 14+ | 优先正式方案，MySQL 仅保留兼容说明 |
| 缓存/消息 | Redis；按需启用 MQ | 组件能力按实际部署需要选择 |
| API 文档 | SpringDoc OpenAPI | 前后端契约通过 OpenAPI 同步 |
| 包管理 | pnpm + Maven | 依赖版本分别由锁文件和 BOM 管理 |

## 4. 开发运行模式

### 4.1 本地开发

- 前端开发端口：`5173`。
- 后端默认端口：`58080`。
- 前端 `/api` 和 `/ws` 代理到后端运行端口。
- 实际启动命令和环境变量参考 `docs/00-spec/30-quality-delivery/04-deployment-runbook.md`。
- 端口、代理和后端配置必须保持一致。

### 4.2 前后端边界

- 前端页面负责用户交互和数据编排。
- 后端 Controller 负责路由、校验和响应包装。
- Service 负责业务规则、权限和事务。
- Mapper 负责数据访问和查询意图。
- Entity 不跨层暴露，敏感字段不进入响应 DTO。
- 跨端 API 以 OpenAPI 和 `Result` 契约为准。

## 5. 构建和部署

通用要求见：

```text
docs/00-spec/30-quality-delivery/03-deploy.md
```

当前项目的实际命令、端口、脚本、环境变量和发布步骤见：

```text
docs/00-spec/30-quality-delivery/04-deployment-runbook.md
```

支持两种部署形态：

| 方案 | 说明 |
| --- | --- |
| 分离部署 | 前端静态资源与后端服务独立发布 |
| 合并部署 | 前端静态资源随后端制品发布 |

数据库迁移必须版本化、可追踪并提供恢复方案。当前全量初始化脚本不能代替生产迁移流程。

## 6. 实时通信架构

平台使用 WebSocket 作为实时通信传输层。通用连接、房间/主题、消息信封、错误、生命周期和安全要求见：

```text
docs/00-spec/20-contracts/03-realtime-protocol.md
```

协作算法、业务事件、Payload、持久化和冲突处理由对应业务详细设计定义，不在架构文档中重复规定。

实时部署是否启用分布式模式由 Redis、实例数量和部署拓扑决定，并通过 Runbook 配置。

## 7. 环境配置管理

| 环境 | 前端 | 后端 | 数据服务 |
| --- | --- | --- | --- |
| dev | `.env.development` | 本地配置和环境变量 | 本地 PostgreSQL/Redis |
| test | 测试环境变量 | 测试环境变量 | 测试 PostgreSQL/Redis |
| prod | `.env.production` | 生产环境变量和密钥服务 | 生产 PostgreSQL/Redis |

敏感配置不得提交到仓库。生产环境缺少密钥、数据库或 Redis 配置时必须启动失败。

## 8. 质量保障

质量门禁由 `docs/00-spec/30-quality-delivery/01-quality.md` 统一定义，部署流程由 `docs/00-spec/30-quality-delivery/03-deploy.md` 和 Runbook 维护。

当前质量基线包括：

- 前端格式、ESLint、TypeScript 和 Vitest；
- 后端 Maven 编译和 JUnit 测试；
- OpenAPI 契约检查；
- Secret、依赖和许可证扫描；
- 核心路径的集成、权限和发布验证。

本项目当前不将 SpotBugs、ArchUnit 和 JaCoCo 作为强制门禁；未来如重新引入，必须更新规范、配置和 CI 验收标准。

## 9. Git 分支和协作文档

- 生产主分支：`master`。
- 日常集成分支：`develop`。
- 功能分支：`feature/*`。
- 缺陷分支：`fix/*`。
- 发布分支：`release/*`。
- 紧急修复分支：`hotfix/*`。

详细协作规则见 `docs/00-spec/30-quality-delivery/02-workflow.md`。

## 10. 参考

- 工程规范索引：`docs/00-spec/00-readme.md`
- 规范总览：`docs/00-spec/00-governance/01-overview.md`
- 前端规范：`docs/00-spec/10-engineering/01-frontend.md`
- 后端规范：`docs/00-spec/10-engineering/02-backend.md`
- API 契约：`docs/00-spec/20-contracts/01-api.md`
- 通用实时协议：`docs/00-spec/20-contracts/03-realtime-protocol.md`
- 通用部署规范：`docs/00-spec/30-quality-delivery/03-deploy.md`
- 项目部署 Runbook：`docs/00-spec/30-quality-delivery/04-deployment-runbook.md`
- migoo 组件手册：`docs/00-spec/10-engineering/03-migoo-framework.md`

---

**文档结束**
