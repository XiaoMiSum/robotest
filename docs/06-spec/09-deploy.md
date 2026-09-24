# 软件测试平台——构建与部署规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 适用范围

本文定义本地构建、环境配置、分离部署、合并部署和发布检查。具体质量门禁由 `07-quality.md` 定义，具体 CI 编排不得在本文重复维护另一套规则。

## 2. 当前工具和端口

| 项目 | 当前事实来源 |
| --- | --- |
| 前端包管理器 | `web/package.json`、`web/pnpm-lock.yaml` |
| 后端构建 | `server/pom.xml` |
| 前端开发端口 | `5173`，见 `web/vite.config.ts` |
| 后端默认端口 | `58080`，见 `application.yaml` |
| API 代理 | `/api`、`/ws`，见 `web/vite.config.ts` |
| 数据库 | PostgreSQL 14+ 为优先正式方案 |
| 一键开发 | `scripts/dev.sh` |

文档、脚本和配置中的端口必须一致。修改端口时必须同步前端代理、启动脚本、部署配置和接口文档。

## 3. 本地开发

```bash
# 安装前端依赖并启动
cd web
pnpm install --frozen-lockfile
pnpm run dev

# 启动后端
cd ../server
mvn spring-boot:run -Pdev

# 或使用一键脚本
bash scripts/dev.sh
```

`dev` profile 是当前本地开发入口。生产配置不得使用本地默认凭据。

## 4. 构建

### 4.1 前端

```bash
cd web
pnpm install --frozen-lockfile
pnpm run lint
pnpm run typecheck
pnpm run test:unit
pnpm run build
```

产物：

```text
web/dist/
```

### 4.2 后端

```bash
cd server
mvn clean verify
mvn package -Pprod
```

产物：

```text
server/target/robotest-server.jar
```

`mvn verify` 只执行当前 `server/pom.xml` 已配置的编译和测试生命周期；本项目不将 SpotBugs、ArchUnit 和 JaCoCo 作为当前强制门禁。依赖漏洞、Secret 和许可证扫描由 CI 任务单独执行。

### 4.3 合并部署

项目脚本：

```bash
bash scripts/deploy-merged.sh
```

该脚本会：

1. 构建前端；
2. 清理 `server/src/main/resources/static/`；
3. 复制 `web/dist/`；
4. 校验 `static/index.html`；
5. 调用后端构建脚本。

当前 Maven `pom.xml` 只声明了 `dev` 和 `prod` profile。合并脚本使用的 `merged` profile 必须先在构建配置中实现并验证；在实现前不得将该流程标记为可发布能力。

## 5. 环境配置

| 环境 | 前端配置 | 后端配置 | 数据库 |
| --- | --- | --- | --- |
| dev | `web/.env.development` | `application.yaml` + 环境变量 | 本地 PostgreSQL |
| test | 测试环境变量 | 测试环境变量 | 测试 PostgreSQL |
| prod | `web/.env.production` | 生产环境变量 | 生产 PostgreSQL |

当前仓库实际存在的前端环境文件为：

```text
web/.env.development
web/.env.production
```

测试和生产配置优先通过环境变量或密钥管理服务注入，不提交真实密钥、密码或 Token。

### 5.1 必要环境变量

```text
PORT
DATASOURCE_URL
DATASOURCE_USERNAME
DATASOURCE_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
JWT_SECRET_KEY
PASSWORD_SECRET
AI_SECRET_KEY
ENV_SECRET_KEY
```

生产环境缺少关键密钥时必须启动失败，不能回退到仓库中的默认值。开发环境默认值也不能用于生产。

## 6. 部署方案

| 方案 | 产物 | 适用场景 |
| --- | --- | --- |
| 分离部署 | `dist-deploy/web/` + `robotest-server.jar` | CDN、前后端独立扩缩容 |
| 合并部署 | 含 `static/` 的 jar | 中小规模、单进程交付 |

### 6.1 分离部署

```bash
bash scripts/deploy-separate.sh
```

发布流水线不得使用 `--skip-checks` 或 `--skip-tests`。这些参数只允许用于本地临时构建，不能生成可发布制品。

Nginx 至少需要代理 `/api/` 和 `/ws/`，并保留 WebSocket Upgrade、连接超时和请求体限制。SSE 路径还必须关闭代理缓冲：

```nginx
location /api/ {
    proxy_pass http://backend:58080;
    proxy_buffering off;
    proxy_read_timeout 3600s;
    client_max_body_size 10m;
}

location /ws/ {
    proxy_pass http://backend:58080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

生产 TLS、来源白名单、真实 IP、安全响应头和 token 参数脱敏由部署环境配置。

### 6.2 合并部署

合并部署前必须确认：

- `static/index.html` 存在；
- 静态资源没有旧版本残留；
- 后端 profile 已实现；
- SPA 路由可以回退到 `index.html`；
- `/api`、`/ws` 不会被静态资源处理器拦截。

## 7. 数据库发布顺序

1. 备份数据库并记录当前版本。
2. 执行向前兼容迁移。
3. 部署应用。
4. 执行健康检查和核心接口验证。
5. 观察日志、数据库负载和错误率。
6. 必要时按预案回滚应用和迁移。

数据库迁移规范见 `06-database.md`。不得直接对已迁移环境重复执行全量 `schema.sql`。

## 8. CI 编排

CI 至少按以下顺序执行：

```text
Checkout
→ 前端安装
→ 前端 lint/typecheck/test/build
→ 后端 verify
→ OpenAPI 契约检查
→ 敏感信息和依赖扫描
→ 构建制品
→ 测试环境部署
→ 发布后验证
```

覆盖率、静态分析和安全扫描是否阻断，以 `07-quality.md` 和实际 CI 配置为准。

## 9. 发布后检查

- [ ] 应用健康检查通过
- [ ] 登录、刷新 Token 和退出正常
- [ ] workspace/project 上下文隔离正常
- [ ] 管理端越权请求被拒绝
- [ ] 关键 API 响应使用 `Result`
- [ ] 分页使用 `pageNo/pageSize` 和 `list`
- [ ] WebSocket 连接和实时消息正常
- [ ] 日志中没有密码、Token 或 SQL 敏感参数
- [ ] 数据库迁移和备份状态正常

## 10. 参考

- 质量门禁：`docs/06-spec/07-quality.md`
- 安全：`docs/06-spec/10-security.md`
- 数据库迁移：`docs/06-spec/06-database.md`
- 实际脚本：`scripts/`

---

**文档结束**
