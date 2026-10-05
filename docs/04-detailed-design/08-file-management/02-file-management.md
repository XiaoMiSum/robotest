# 软件测试平台——文件管理模块详细设计

**文档版本**：V1.0
**日期**：2026-10-04
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

引入对象存储（SeaweedFS）与**泛化附件资源**（`file_resource`），建设文件管理模块，统一承载：

1. 需求导入源文件的存储与回看下载（需求管理详设 3.8 / SRS 来源附件）；
2. 需求详情（含图片）等业务内容的图片引用；
3. 既有缺陷附件（`bug_attachment`，本地磁盘）及其下载/删除的迁移改造；
4. 文件治理页面（系统管理 → 文件管理）与 docker-compose 全家桶发行。

### 1.2 背景与依据

- 实施计划「计划外独立任务登记——文件管理模块改造」（`docs/06-implementation-plan/02-ai-requirement-development-plan.md` §1.2）：
  引入对象存储、设计**不挂工作空间 / 项目**的泛化附件资源（使用方以访问 URL 关联）、以 docker-compose 全家桶发行；随 WP-2.0 实施。
- 需求侧已预留的引用字段：`requirement.source_file_id`、`requirement_split_record.source_file_id`（当前恒为 NULL，由本模块承载）。
- 缺陷附件现状：本地磁盘 `robotest.upload.dir`（默认 `./uploads/bug`）+ `bug_attachment.storage_path` 相对路径，须迁移至对象存储。
- 安全规范（`docs/00-spec/40-security/01-security.md`）：文件类型不得仅按扩展名判断，须文件头内容嗅探；附件下载必须重新校验。
- 数据规范（`docs/00-spec/20-contracts/02-database.md`）：C5（全字段、逻辑删除、UUID 默认策略、无物理外键）、C9（索引）、§5 迁移规范（版本化迁移 + 全量 schema.sql 双轨）。

### 1.3 术语

| 术语 | 含义 |
| ---- | ---- |
| 泛化附件资源 | `file_resource` 行，不挂工作空间 / 项目 / 任何业务实体，仅由使用方以 ID 或访问 URL 关联 |
| objectKey | 对象存储 bucket 内的对象键，由服务端生成，不含任何用户可控路径成分 |
| presigned URL | S3 服务端签名的临时访问地址，时效内可直连对象存储，过期失效 |
| 平台下载接口 | `/api/files/{id}/download`，经平台登录鉴权后由后端代理读取对象流 |

---

## 2. 数据设计

### 2.1 file_resource 表（泛化附件资源）

```sql
CREATE TABLE file_resource (
    id            UUID         PRIMARY KEY,
    object_key    VARCHAR(500) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NULL,
    file_size     BIGINT       NOT NULL,
    uploader_id   UUID         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_file_resource_uploader ON file_resource (uploader_id);
CREATE INDEX idx_file_resource_created ON file_resource (created_at);

COMMENT ON TABLE file_resource IS '泛化附件资源：不挂工作空间/项目，使用方以 ID 或访问 URL 关联（文件管理详设 2.1）';
COMMENT ON COLUMN file_resource.object_key IS '对象存储对象键（服务端生成 objects/{uuid}{ext}，不含用户可控路径）';
COMMENT ON COLUMN file_resource.uploader_id IS '上传者（sys_user.id，逻辑外键）';
```

- **C5**：全字段齐备、UUID 主键走框架 `BaseUuidDO` 默认策略、`is_deleted` 逻辑删除、无物理外键。
- **刻意不含的字段**：`workspace_id` / `project_id` / `biz_type`——登记明确「不挂工作空间 / 项目」，业务归属由使用方自行维护（如 `bug_attachment.file_resource_id`）。
- **C9**：`uploader_id`（逻辑外键 + 上传者筛选）、`created_at`（列表排序）建索引，共 2 个。

### 2.2 bug_attachment 补列

```sql
ALTER TABLE bug_attachment ADD COLUMN file_resource_id UUID NULL;
CREATE INDEX idx_bug_attachment_file_resource ON bug_attachment (file_resource_id);
COMMENT ON COLUMN bug_attachment.file_resource_id IS '关联 file_resource.id（文件管理详设 4.3）；存量行由应用启动回填，NULL 期间兼容本地 storage_path';
```

- 新列**可空**：旧应用不感知新列（兼容窗口），存量行由 §7.2 启动回填补齐；回填完成后代码只读 `file_resource_id`。
- `storage_path` 保留为历史列并 `ALTER COLUMN storage_path DROP NOT NULL`（新行不再写入该列；基线 `schema.sql` 同步为允许 NULL），回滚时旧代码仍可读本地路径（§7.3）。
- `file_resource_id` 为逻辑外键，按 C9 建索引（反查文件被谁引用的治理场景）。

### 2.3 权限点与角色授权

`sys_permission` 新增（系统管理顶模块，编号顺延 a000…031–033）：

| code | 名称 | parent | scope |
| ---- | ---- | ---- | ---- |
| `file` | 文件管理 | NULL | global |
| `file:view` | 查看文件 | `file` | global |
| `file:delete` | 删除文件 | `file` | global |

系统管理员角色（`b0000000-0000-0000-0000-000000000001`）`permissions` JSONB 追加 `"file","file:view","file:delete"`。

- 管理页与列表/删除接口以 `@PreAuthorize("hasAnyAuthority('file:view' …)")` 收口（同 `AdminUserController` 模式）。
- 上传 / 下载 / 换签为**登录即可**的通用能力，不设权限点（任何业务成员都会用到，归属与宿主权限由使用方把关）。

### 2.4 迁移与基线（双轨）

| 载体 | 内容 |
| ---- | ---- |
| `server/src/main/resources/db/schema.sql` | 全量基线同步（新建库初始化，含 §2.1–2.3 全部 DDL 与种子） |
| `server/src/main/resources/db/migration/V20261004180000__file_management.sql` | 存量库增量（同 DDL），按 runbook §10 手工执行：备份 → 执行 → 回滚方案核对 → 发应用 |

部署顺序：**先执行 DDL，后发应用**（新列可空，反序会导致新代码读列失败）。

---

## 3. 存储设计（SeaweedFS 对象存储）

### 3.1 依赖与配置

- 引擎：**SeaweedFS**（S3 兼容对象存储，Apache-2.0，活跃维护）——MinIO 社区版已停止维护（CE 镜像停发、GitHub 仓库归档，
  官方镜像不可再获取），且 `seaweedfs/seaweedfs` 新官方镜像命名空间在 Docker Hub 尚未就绪，发行镜像固定为其官方发布仓库
  `chrislusf/seaweedfs` 的版本标签（§8.1）。
- 客户端：**`io.minio:minio` SDK**（经讨论引入的外部依赖，登记于实施计划 §2.5）作为 **S3 协议客户端**使用；
  上传 / 下载 / 删除 / presigned 签名全部经 SDK 标准 S3 操作，不自实现对象存储 HTTP 调用，引擎可在任意 S3 兼容服务间切换。
- 配置项（`application.yaml` `robotest.s3` 段，环境变量可覆盖）：

| 配置 | 环境变量 | 默认值 | 说明 |
| ---- | ---- | ---- | ---- |
| `endpoint` | `S3_ENDPOINT` | `http://localhost:9000` | S3 API 地址（服务端访问，compose 内为 `http://seaweed:9000`） |
| `public-endpoint` | `S3_PUBLIC_ENDPOINT` | 空 = 同 `endpoint` | presigned 签名地址（浏览器可达；compose 内配 `http://localhost:9000`——内部网地址签名后浏览器无法直连） |
| `access-key` | `S3_ACCESS_KEY` | `robotest` | dev 默认值；prod 必须显式配置 |
| `secret-key` | `S3_SECRET_KEY` | `robotest-dev-secret` | 同上，禁止真实凭据入库 |
| `bucket` | `S3_BUCKET` | `robotest` | 对象桶 |
| `presign-ttl-seconds` | `S3_PRESIGN_TTL` | `900`（15 分钟） | presigned URL 时效 |

- 凭据经根 `.env` 同源注入两侧：应用读 `S3_*`，SeaweedFS 进程读 `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`（compose 映射，§8.1）。

### 3.2 对象键与桶保证

- 对象键：`objects/{uuid}{ext}`——`uuid` 为服务端生成，`ext` 取自原始文件名扩展名（白名单校验后小写；无扩展名则省略）。
  文件名原样存 `file_name` 列，**永不进入对象键**（防路径注入）。
- bucket 懒确保：首次存储操作时 `bucketExists → createBucket`（单次标记），失败抛 `FILE_UPLOAD_FAILED`；
  不在应用启动期强依赖对象存储（本地开发未起 SeaweedFS 时应用可正常启动，仅文件能力不可用）。

### 3.3 访问模型（presigned + 平台接口并存）

| 通道 | 用途 | 约束 |
| ---- | ---- | ---- |
| presigned 临时 URL（§4.4） | 详情页 `<img>` 渲染、内嵌预览——`<img>` 无法携带 `Authorization` 头，必须直连签名地址 | 登录换签、15 分钟时效、过期即失效 |
| 平台下载接口（§4.3） | 下载按钮（`fetch` 带鉴权 → blob 保存）、回看原文 | 登录鉴权 + 使用方宿主页面权限前提 |

**使用方关联约定**：

- 结构化引用存 `file_resource.id`（`source_file_id` / `file_resource_id`）；
- 内容内嵌（Markdown 图片等）存**稳定下载路径** `/api/files/{id}/download`，渲染时由前端鉴权换取（blob 或换签 presigned）；
  **禁止**把 presigned 签名 URL 持久化进任何业务内容（过期即裂图）。

---

## 4. 接口设计

统一响应 `Result`（`docs/00-spec/20-contracts/01-api.md`）；本模块**不含任何工作空间 / 项目上下文头**（C4：无上下文可传）。

### 4.1 接口总表

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| POST | `/api/files` | 上传（multipart 字段 `file`） | 登录 |
| GET | `/api/files` | 分页列表（`pageNo/pageSize/fileName` 模糊） | `file:view` |
| GET | `/api/files/{id}/download` | 平台代理下载（对象流） | 登录 |
| GET | `/api/files/{id}/access-url` | 换取 presigned 临时 URL | 登录 |
| DELETE | `/api/files/{id}` | 删除（逻辑删行 + 删对象存储对象） | `file:delete` |

缺陷附件既有 4 接口（`POST/GET /api/project/bugs/{id}/attachments`、`GET /api/project/bugs/attachments/{attachmentId}/download`、`DELETE …`）**路径与报文不变**，仅内部实现改走本模块（§4.3）。

### 4.2 报文示例

**上传** `POST /api/files`（`multipart/form-data`，字段 `file`，≤ 20MB，§5.2）：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "7f3a…",
    "fileName": "SRS-登录模块.md",
    "fileSize": 18432,
    "contentType": "text/markdown",
    "downloadUrl": "/api/files/7f3a…/download",
    "createdAt": "2026-10-04 12:00:00"
  }
}
```

**列表** `GET /api/files?pageNo=1&pageSize=20&fileName=SRS`：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 1,
    "list": [
      {
        "id": "7f3a…",
        "fileName": "SRS-登录模块.md",
        "fileSize": 18432,
        "contentType": "text/markdown",
        "uploaderId": "u1…",
        "uploaderName": "张三",
        "createdAt": "2026-10-04 12:00:00"
      }
    ]
  }
}
```

**换取 presigned** `GET /api/files/{id}/access-url`：

```json
{ "code": 0, "msg": "success", "data": { "url": "http://localhost:9000/robotest/objects/…?X-Amz-…", "expiresIn": 900 } }
```

**平台下载** `GET /api/files/{id}/download` → 二进制流：

```text
HTTP/1.1 200 OK
Content-Type: <file_resource.content_type，未知则 application/octet-stream>
Content-Length: <file_size>
Content-Disposition: attachment; filename*=UTF-8''<URL 编码后的原始文件名>
```

### 4.3 缺陷附件的内部改造（路径不变）

```text
上传：validateBug → 缺陷侧既有校验（10MB、白名单+文件头嗅探，错误码契约不变）
      → FileResourceService.upload（模块级 20MB 与白名单再校验、落对象存储、写 file_resource）
      → 写 bug_attachment（补 file_resource_id；file_name/uploader 等业务列不变；storage_path 不再写入）→ bug_log
下载：bug_attachment → file_resource_id → 模块读取对象字节；行内为 NULL（回填前）→ 兼容读本地 storage_path
删除：保持既有语义（缺陷详设 1.12）——逻辑删 bug_attachment 行 + bug_log，对象与 file_resource 保留供审计，
      孤儿资源由文件管理页统一治理（§5.3）
```

- 校验分工：缺陷侧保留既有校验与错误码（10MB 上限、白名单与文件头嗅探，`BUG_ATTACHMENT_*` 契约不变）；
  模块入口统一再执行白名单与头嗅探（复用 `AttachmentFileValidator`，错误码 §6，覆盖直接使用模块的调用方）。
- 响应 DTO（`BugAttachmentRespDTO` / `BugAttachmentDownloadRespDTO`）不变 → **前端上传/列表/下载/删除零改动**，仅回归。

### 4.4 使用方接入（WP-2.2 起）

上传响应的 `downloadUrl` 即登记所指「访问 URL」；需求导入落库时将该 URL 对应的资源 ID 写入
`requirement.source_file_id` / `requirement_split_record.source_file_id`，详情回看走 §4.4 两通道。

---

## 5. 权限与安全

### 5.1 鉴权口径

- 上传 / 下载 / 换签：`/api/**` 默认登录鉴权（SecurityConfig，未进 permit-all）；
- 列表 / 删除：`file:view` / `file:delete` 权限点（§2.3）；
- **泛化资源无宿主归属可校验**（登记明确不挂上下文），安全边界为：登录态 + 128 位不可猜测 UUID + presigned 短时效 + 管理页权限点；
  使用方（需求详情、缺陷详情）在已通过**宿主实体权限**的页面内才发起换签 / 下载，宿主权限由各业务模块自己的接口把关。

### 5.2 文件校验与大小

| 层 | 规则 |
| ---- | ---- |
| servlet multipart | `max-file-size: 20MB`、`max-request-size: 21MB`（由现 10/10MB 上调，容纳 SRS 20MB 导入；预留 multipart 边界开销） |
| 模块级（FileResourceService） | 空文件拒绝（`FILE_EMPTY`）、> 20MB 拒绝（`FILE_SIZE_EXCEEDED`）、扩展名白名单 + 文件头嗅探（复用 `AttachmentFileValidator`，排除 html/svg/js/可执行体） |
| 缺陷附件（业务层） | 保留 10MB 上限（`MAX_FILE_SIZE` 常量，随 multipart 上调后仍由业务层强制） |

### 5.3 其他

- 对象键服务端生成，原始文件名只落库不进路径（防路径注入）；
- 下载 `Content-Disposition` 对文件名做 URL 编码，`Content-Type` 取库内记录（防内容嗅探逃逸）；
- 删除为行逻辑删 + 对象物理删（管理页治理语义：真正回收空间）；被引用文件删除属管理员治理职责（泛化资源无引用约束，§1.3 设计取舍）。

---

## 6. 错误码

**号段：1000018021–1000018039**（文件管理模块专用；需求 1000018001–014、AI 1000018101+、追溯 1000018151+ 互不重叠）。

| 错误码 | 常量 | 说明 |
| ---- | ---- | ---- |
| 1000018021 | `FILE_NOT_FOUND` | 文件不存在或已删除 |
| 1000018022 | `FILE_EMPTY` | 空文件 |
| 1000018023 | `FILE_SIZE_EXCEEDED` | 超过模块 20MB 上限 |
| 1000018024 | `FILE_TYPE_NOT_ALLOWED` | 类型不在白名单或文件头不一致 |
| 1000018025 | `FILE_UPLOAD_FAILED` | 上传失败（含对象存储不可用） |
| 1000018026 | `FILE_DOWNLOAD_FAILED` | 下载失败 |
| 1000018027 | `FILE_ACCESS_URL_FAILED` | presigned URL 签发失败 |
| 1000018028 | `FILE_DELETE_FAILED` | 删除失败 |

缺陷附件既有错误码（`BUG_ATTACHMENT_*`）不变，前端契约不受影响。

---

## 7. 存量迁移与发布

### 7.1 DDL 执行

按 runbook §10：备份 → 执行 `V20261004180000__file_management.sql` → 核对回滚方案 → 发应用。新列可空，DDL 可先行于应用（兼容窗口）。

### 7.2 本地存量文件回填（应用启动，幂等）

```text
FileResourceBackfillRunner（ApplicationRunner，事务外逐行）：
  SELECT bug_attachment WHERE file_resource_id IS NULL AND is_deleted = FALSE AND storage_path 非空
  → 逐行：本地文件存在？
      是 → SDK 上传对象存储 → INSERT file_resource → UPDATE bug_attachment.file_resource_id
      否 → 记 WARN 跳过（文件已丢失，管理页可见该行仍指向 NULL，走本地兼容下载报 FILE_NOT_FOUND）
  → 对象存储不可达 → 整体本轮跳过 + WARN，下次启动重试（回填完成前下载/删除走本地兼容路径）
```

- 幂等：以 `file_resource_id IS NULL` 为唯一驱动，重复执行无副作用；
- 不阻塞启动：回填失败只告警，不影响应用就绪。

### 7.3 回滚与影响评估

| 项 | 方案 |
| ---- | ---- |
| 回滚 | 应用回退上一版本即可：旧代码只读 `storage_path`，本地文件在回填期间**不删除**（删除动作在回填后才可能触发对象侧删除）；`file_resource` 表与新列保留，无破坏性 |
| 部署顺序 | DDL → 应用；回滚时应用 →（可选）不回滚 DDL（列可空无副作用） |
| 锁影响 | `ALTER TABLE … ADD COLUMN`（空表/小表秒级，无长事务风险）、`CREATE INDEX` 非 CONCURRENTLY（开发期数据量小；存量大表上线前按 §5 评估改 CONCURRENTLY） |
| 租户隔离 | 泛化资源本体无租户字段（设计取舍 §5.1），管理页 `global` scope；索引/逻辑删除检查见 §2 |

---

## 8. docker-compose 全家桶发行

### 8.1 服务拓扑

| 服务 | 镜像 / 构建 | 宿主端口 → 容器 | 卷 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| `postgres` | `pgvector/pgvector:pg14` | `5433 → 5432` | `pgdata` | 初始化挂 `schema.sql`（空卷首启自动建基线）；健康检查 `pg_isready` |
| `redis` | `redis:7-alpine` | `6380 → 6379` | `redisdata` | 会话与 WS 分布式；健康检查 `redis-cli ping` |
| `seaweed` | `chrislusf/seaweedfs:<固定版本>` | `9000 → 9000`（S3 API）、`9001 → 8888`（filer 控制台） | `weeddata` | `server -s3` 单进程（master/volume/filer/S3 网关）；凭据经根 `.env` 的 `S3_*` 以 `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` 注入；健康检查 master `:9333/cluster/status` |
| `server` | `build ./server`（多阶段） | `58080 → 58080` | — | `SPRING_PROFILES_ACTIVE=prod`，数据源/Redis/S3/密钥全量环境变量注入；`depends_on` 健康服务 |
| `web` | `build ./web`（多阶段） | `8081 → 80` | — | nginx 托管 dist + 反代 `server:58080` |

- 宿主端口刻意避开本地开发占用（本地 PG 5432 / Redis 6379），`server` 沿用 58080 与 `web/vite.config.ts` 代理一致，支持「compose 中间件 + 本地前端」混合调试。
- **Redis 为必选服务**：应用强依赖（`spring.data.redis`），不含则 server 无法启动。
- **存储引擎与镜像来源**：MinIO 社区版已停止维护、官方镜像不可获取；SeaweedFS 新官方命名空间（`seaweedfs/seaweedfs`）在 Docker Hub 尚未就绪，
  故固定其官方发布仓库 `chrislusf/seaweedfs` 的版本标签（二者内容同源），不使用 `latest` 漂移标签。

### 8.2 构建与配置

| 文件 | 职责 |
| ---- | ---- |
| `docker-compose.yml`（仓库根） | 服务编排、健康检查、卷与网络 |
| `server/Dockerfile` | 多阶段：`maven` 构建（`-DskipTests -Pprod`）→ `eclipse-temurin:21-jre` 运行 |
| `web/Dockerfile` | 多阶段：`node` + pnpm 构建 dist → `nginx` 托管 |
| `docker/web/nginx.conf` | 以 `scripts/nginx.conf.example` 为基线（日志脱敏 map、WS Upgrade、SPA 回退），补：`client_max_body_size 21m`、`/api/` 段 `proxy_buffering off`（SSE 流式）、upstream 指 `server:58080` |
| `.env.example`（仓库根） | compose 环境变量模板（数据源、Redis、S3 凭据、`ENV_SECRET_KEY` / `JWT_SECRET_KEY` / `PASSWORD_SECRET`），复制为 `.env` 使用；真实凭据禁止入库 |
| `server/.dockerignore`、`web/.dockerignore` | 排除 `target/`、`node_modules/`、`.git` 等构建噪音 |

### 8.3 使用与文档同步

```bash
cp .env.example .env   # 按需修改密钥与端口
docker compose up -d --build
# 首启：postgres 空卷自动执行 schema.sql；server 起动后懒确保 bucket 并回填存量附件
```

- runbook（`docs/00-spec/30-quality-delivery/04-deployment-runbook.md`）设「docker compose 全家桶」小节；根 `AGENTS.md` 环境命令同步。

---

## 9. 文件管理页（前端）

- 路由：`/admin/files`，`web/src/pages/admin/FileListPage.vue`，admin 菜单 `meta.menu` 注册（文件管理，icon `FolderOpened`），`file:view` 控显隐；
- 交互：

| 元素 | 行为 |
| ---- | ---- |
| 搜索框 | 文件名模糊过滤，回车/按钮触发 |
| 表格 | 文件名、大小（可读格式）、类型、上传者、上传时间；分页 20/页 |
| 下载 | `fetch`（带 `Authorization`）→ blob 触发浏览器保存 |
| 复制链接 | `GET /api/files/{id}/access-url` 换取 presigned 临时地址并写入剪贴板，提示有效时长（访问 URL 走通验收入口） |
| 删除 | 确认弹窗（提示不可恢复）→ `DELETE /api/files/{id}` → 刷新 |
| 空态 / 加载 / 错误 | 空态引导、表格 loading、错误按 10 位码提示（C1 无 `any`） |

- 权限：菜单与页面按钮按 `file:view` / `file:delete` 显隐；无权限用户不注册该菜单项。

---

## 10. 修改记录

| 版本 | 日期 | 说明 | 作者 |
| ---- | ---- | ---- | ---- |
| V1.0 | 2026-10-04 | 随 WP-2.0 补建：泛化附件资源与 MinIO 存储、缺陷附件迁移、文件管理页、docker-compose 全家桶 | AI |
| V1.0 | 2026-10-04 | 编码前探查修正：`storage_path` 放宽可空（历史列不再写入）、presigned 补 `public-endpoint`（签名地址须浏览器可达）、缺陷附件删除保持既有语义（对象保留供审计，孤儿由管理页治理）与校验分工（缺陷侧错误码契约不变） | AI |
| V1.0 | 2026-10-05 | 交互补：文件管理页增加「复制临时链接」（presigned 换签写剪贴板），承接「访问 URL 走通」验收入口 |
| V1.0 | 2026-10-05 | 存储引擎定标 MinIO → SeaweedFS：§3 引擎与配置中性化（`robotest.s3.*` / `S3_*`、默认凭据 `robotest`、实现类改名 `S3StorageService`，SDK 与调用逻辑不变）、§8.1 服务/镜像/端口定标（`chrislusf/seaweedfs:<固定版本>`，9001 映射 filer 控制台 8888）、runbook 章节顺延联动（§9 → §10） | AI |
