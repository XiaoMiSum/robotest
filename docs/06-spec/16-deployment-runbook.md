# 项目部署 Runbook

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 文档定位

本文是 RoboTest 当前仓库的部署操作手册，记录实际命令、端口、脚本、配置入口和已知限制。

通用部署要求见 `09-deploy.md`。当本文与脚本或配置不一致时，以实际代码和配置为当前事实，并按规范冲突流程登记差异。

## 2. 当前环境基线

| 项目 | 当前值 | 事实来源 |
| --- | --- | --- |
| 前端开发端口 | `5173` | `web/vite.config.ts` |
| 后端默认端口 | `58080` | `server/src/main/resources/application.yaml` |
| 前端 API 代理 | `/api` | `web/vite.config.ts` |
| 前端 WebSocket 代理 | `/ws` | `web/vite.config.ts` |
| 前端包管理器 | pnpm | `web/package.json`、`web/pnpm-lock.yaml` |
| 后端构建 | Maven | `server/pom.xml` |
| 正式数据库 | PostgreSQL 14+ | `server/src/main/resources/db/schema.sql` |
| Redis | Redis 7+ | `server/pom.xml`、运行配置 |
| 后端框架 | migoo `1.3.18` | `server/pom.xml` |

`PORT` 可以覆盖后端端口，但本地默认值为 `58080`。修改端口时必须同步 Vite 代理、启动脚本、Nginx 和环境配置。

## 3. 本地开发

### 3.1 一键启动

```bash
bash scripts/dev.sh
```

脚本会：

1. 启动后端 `mvn spring-boot:run -Pdev`；
2. 启动前端 `pnpm run dev`；
3. 在退出时清理后端进程。

### 3.2 分端启动

```bash
# 终端 1：后端
cd server
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run -Pdev

# 终端 2：前端
cd web
pnpm install --frozen-lockfile
pnpm run dev
```

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

当前 Maven 配置不将 SpotBugs、ArchUnit 和 JaCoCo 作为强制门禁。依赖漏洞、Secret 和许可证扫描由 CI 任务负责。

## 5. 分离部署

```bash
bash scripts/deploy-separate.sh
```

脚本产物：

```text
dist-deploy/
├── web/
├── robotest-server.jar
└── nginx.conf.example
```

发布模式不得使用：

```text
--skip-checks
--skip-tests
```

这些参数只允许本地临时构建使用。

## 6. 合并部署

```bash
bash scripts/deploy-merged.sh
```

脚本会：

1. 调用前端构建；
2. 清理 `server/src/main/resources/static/`；
3. 复制 `web/dist/`；
4. 校验 `static/index.html`；
5. 调用后端构建脚本。

### 6.1 当前限制

当前 `server/pom.xml` 只声明 `dev` 和 `prod` profile，尚未声明 `merged` profile。

在 `merged` profile 实现并验证前：

- 可以执行脚本做本地验证；
- 不得将合并部署标记为生产可发布能力；
- 不得把 `mvn package -Pmerged` 视为有效构建约束。

## 7. 环境变量

### 7.1 本地后端环境文件

后端本地环境文件为：

```text
server/.env
```

- 文件采用 Java Properties 格式，只使用 `KEY=value`，不写 `export`。
- `server/.env` 已被 Git 忽略，只能保存本机凭据和本地密钥，不得提交。
- `server/.env.example` 是可提交模板，变量清单以模板为准。
- Spring Boot 启动时优先加载工作目录下的 `.env`；从 `server/` 启动可直接读取该文件。
- AI、ENV、JWT、PASSWORD 必须使用不同密钥域；本地值也不得复用于测试或生产环境。

本地启动继续显式指定 profile：

```bash
cd server
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run -Pdev
```

### 7.2 关键变量

```text
SPRING_PROFILES_ACTIVE
PORT
DATABASE_DRIVER
DATASOURCE_URL
DATASOURCE_USERNAME
DATASOURCE_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_USER
REDIS_PASSWORD
UPLOAD_DIR
AI_SECRET_KEY
ENV_SECRET_KEY
IMPORT_URL_POLICY
MOCK_ACCESS_ENABLED
MOCK_PORT
MOCK_BASE_URL
MOCK_PATH_QPS
SCHEDULER_POOL_SIZE
TASK_EXECUTION_TIMEOUT_MINUTES
TASK_POLL_INTERVAL_MS
DEBUG_STATUS
JWT_SECRET_KEY
PASSWORD_SECRET
```

### 7.3 测试与生产环境

生产环境必须设置：

```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar server/target/robotest-server.jar
```

`application-prod.yaml` 中的数据源、Redis 和安全密钥没有开发默认值，缺少变量时应用必须启动失败。

当前前端环境文件：

```text
web/.env.development
web/.env.production
```

test/prod 的后端配置优先通过部署环境变量或密钥管理服务注入，不依赖本地 `.env` 或仓库中新增未验证的 profile 文件。

## 8. Nginx 参考配置

### 8.1 API 和 SSE

```nginx
location /api/ {
    proxy_pass http://backend:58080;
    proxy_buffering off;
    proxy_read_timeout 3600s;
    client_max_body_size 10m;
}
```

SSE 接口必须关闭代理缓冲，并配置足够的读取超时。

### 8.2 WebSocket

```nginx
location /ws/ {
    proxy_pass http://backend:58080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

生产环境还必须配置：

- TLS；
- Origin 白名单；
- 真实 IP Header；
- 请求体大小；
- 安全响应头；
- 连接和业务 Token 日志脱敏。

## 9. 数据库发布

当前只有：

```text
server/src/main/resources/db/schema.sql
```

该文件是初始化基线，不应直接用于已迁移环境。

在版本化迁移工具接入前，发布前必须：

1. 备份数据库；
2. 记录当前 schema 版本；
3. 手工执行并审查增量 SQL；
4. 验证回滚或恢复方案；
5. 发布应用；
6. 验证核心表和权限。

## 10. 发布前检查

```bash
# 文档和契约检查
cd web
pnpm run contract:gen

# 统一验证脚本
cd ../..
bash scripts/validate.sh --all
```

发布前人工确认：

- [ ] 后端端口和前端代理一致；
- [ ] 生产密钥已注入且无默认值；
- [ ] PostgreSQL 可连接；
- [ ] Redis 可连接；
- [ ] 数据库迁移已备份并可恢复；
- [ ] 前端构建产物存在；
- [ ] 后端 jar 存在且版本可追溯；
- [ ] 合并部署 profile 状态已确认；
- [ ] SSE 和 WebSocket 代理已验证。

## 11. 发布后 smoke test

```text
1. GET /api/health
2. 登录并获取 Token
3. 刷新 Token
4. 获取当前 workspace 列表
5. 切换 workspace
6. 访问 project 页面
7. 验证管理端权限
8. 建立实时连接并收发消息
9. 检查日志和审计
10. 检查数据库迁移结果
```

接口路径、认证方式和业务断言以当前 OpenAPI 和对应业务设计为准。

## 12. 故障排查

| 现象 | 优先检查 |
| --- | --- |
| 前端 502 | Vite 代理、后端端口、进程状态 |
| WebSocket 连接失败 | `/ws` 代理、Upgrade Header、Origin、Ticket |
| SSE 不流式返回 | `proxy_buffering`、读取超时 |
| 登录失败 | Key 注入、Token 配置、Redis、数据库 |
| 权限异常 | workspace/project Header、服务端 Guard、角色 |
| 数据库连接失败 | `DATASOURCE_*`、网络、迁移状态 |
| 附件上传 413 | Nginx `client_max_body_size`、后端上传限制 |
| 合并部署 404 | `static/index.html`、SPA fallback、`/api` 和 `/ws` 排除 |

## 13. 已知限制

- `merged` Maven profile 尚未建立；
- 版本化数据库迁移尚未建立；
- CI、覆盖率、Secret 扫描和分支保护尚未全部接入；
- 端口和脚本注释仍需持续与配置同步；
- SQL 日志配置仍需单独整改。

## 14. 参考

- 通用部署规范：`docs/06-spec/09-deploy.md`
- 质量门禁：`docs/06-spec/07-quality.md`
- 安全：`docs/06-spec/10-security.md`
- 数据库：`docs/06-spec/06-database.md`
- 通用实时协议：`docs/06-spec/15-realtime-protocol.md`
- 项目脚本：`scripts/`

---

**文档结束**
