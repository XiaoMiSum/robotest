# 工程规范 — 数据库

**文档版本**：V1.1
**日期**：2026-07-31
**状态**：已发布

---

## 1. 命名规范

| 要素   | 规范             | 示例                                |
| ---- | -------------- | --------------------------------- |
| 数据库名 | `swt_platform` | `swt_platform_dev`                |
| 表名   | `{域前缀}_{业务名}`，snake_case，单数 | `ws_workspace`、`test_case_module` |
| 字段名  | snake_case     | `workspace_id`                    |
| 主键   | `id`           | `id binary(16) PK`（UUID v7，应用层生成） |
| 关联字段（逻辑外键） | `{表名}_id`      | `project_id`                      |
| 索引   | `idx_{表名}_{字段}`  | `idx_ws_project_workspace_id`     |
| 唯一约束 | `uk_{表名}_{字段}`   | `uk_ws_workspace_name`            |

**表名域前缀**：所有表必须带业务域前缀，按业务域归位，禁止裸表名：

| 业务域     | 前缀           | 示例表                                              |
| --------- | ------------- | ------------------------------------------------- |
| 系统管理     | `sys_`        | `sys_user`、`sys_role`、`sys_audit_log`            |
| 工作空间/项目 | `ws_`         | `ws_workspace`、`ws_user`、`ws_invitation`、`ws_project` |
| 功能测试-用例 | `test_case_`  | `test_case_module`、`test_case_node`               |
| 功能测试-计划 | `test_plan_`  | `test_plan`、`test_plan_node_snapshot`             |
| 功能测试-评审 | `test_review_`| `test_review`、`test_review_record`                |
| 缺陷管理     | `bug_`        | `bug`、`bug_log`、`bug_attachment`（域根表 `bug` 保留领域词） |
| 需求池      | `requirement_`| `requirement_pool_item`、`requirement_document_rel`|
| AI 能力     | `ai_`         | `ai_config`、`ai_case_embedding`、`ai_bug_embedding` |

> 例外：缺陷域根表 `bug` 允许保留单数领域词（子表仍用 `bug_` 前缀）；其余任何表不得以裸业务词命名。

---

## 2. 表设计规范

**强制约定**：

- 每张表必须包含 `id`（PK, UUID v7）、`created_at`、`updated_at`、`is_deleted`（tinyint, default 0）四个字段。
- 逻辑删除统一使用 `is_deleted`（tinyint, default 0），物理删除需在详细设计中明确说明。
- 字符集：`utf8mb4`，排序规则：`utf8mb4_unicode_ci`。
- **禁止使用外键**：任何表不得定义 `FOREIGN KEY` 物理约束；表间关联一律通过关联字段（`{表名}_id`）表达，引用完整性由应用层（Service 层）保证，级联删除/更新由业务代码显式处理。
- 主键统一使用 UUID v7（`binary(16)` 存储），应用层生成，不依赖数据库自增。
  - MySQL：`INSERT INTO t (id, ...) VALUES (UUID_TO_BIN(UUID()), ...)`；查询 `BIN_TO_UUID(id)`。
  - PostgreSQL：`id` 列类型为 `uuid`，应用层直接传入 UUID 字符串。
  - Java 依赖：`com.fasterxml.uuid:java-uuid-generator`（JUG），使用 `UUIDGenerator.generateTimeBasedEpoch()`。
  - TypeScript 依赖：`uuid` v9+，使用 `import { v7 as uuidv7 } from 'uuid'`。
- JSON 字段使用 `json` 类型（MySQL 5.7+ / 8.0+）。
- 语义向量字段使用 pgvector 的 `vector(n)` 类型（仅 PostgreSQL，需启用扩展 `CREATE EXTENSION vector`，随迁移脚本执行）；向量表与业务表一对一独立建表（如 `ai_bug_embedding`、`ai_case_embedding`），不在业务表上直接加向量列。

**字段类型选择**：

| 类型           | 使用场景  | 示例                                 |
| ------------ | ----- | ---------------------------------- |
| `binary(16)` | 主键    | `id`                               |
| `varchar(n)` | 短文本   | `name varchar(100)`                |
| `text`       | 长文本   | `description text`                 |
| `json`       | 结构化数据 | `steps json`                       |
| `enum(...)`  | 有限状态  | `status enum('active','disabled')` |
| `datetime`   | 时间戳   | `created_at datetime`              |
| `tinyint(1)` | 布尔值   | `is_deleted tinyint(1)`            |
| `vector(n)`  | 语义向量（pgvector） | `embedding vector(1536)`  |

---

## 3. 索引规范

- 主键默认索引。
- 所有关联字段（逻辑外键）必须建索引。
- 频繁查询条件字段建索引。
- 联合索引将区分度高的字段放在左侧。
- 避免过多索引（单表不超过 5 个）。
- 向量列使用 HNSW 索引，距离算子统一余弦：`USING hnsw (embedding vector_cosine_ops)`；向量索引不计入上述单表 5 个的常规索引上限。
- 向量相似度查询必须先按业务归属字段（如 `project_id`）过滤再做近邻检索，防止跨项目数据泄漏。

---

## 4. 字段映射规则

| 数据库             | Java           | TypeScript     |
| --------------- | -------------- | -------------- |
| `id`            | `UUID`         | `string`（UUID） |
| `workspace_id`  | `workspaceId`  | `workspaceId`  |
| `created_at`    | `createdAt`    | `createdAt`    |
| `updated_at`    | `updatedAt`    | `updatedAt`    |
| `is_deleted`    | `isDeleted`    | `isDeleted`    |
| `password_hash` | `passwordHash` | 不暴露给前端         |

---

**文档结束**
