# 工程规范 — 数据库

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. 命名规范

| 要素   | 规范             | 示例                                |
| ---- | -------------- | --------------------------------- |
| 数据库名 | `swt_platform` | `swt_platform_dev`                |
| 表名   | snake_case，单数  | `test_case_module`                |
| 字段名  | snake_case     | `workspace_id`                    |
| 主键   | `id`           | `id binary(16) PK`（UUID v7，应用层生成） |
| 外键   | `{表名}_id`      | `project_id`                      |
| 索引   | `idx_{字段名}`    | `idx_project_id`                  |
| 唯一约束 | `uk_{字段名}`     | `uk_name`                         |

---

## 2. 表设计规范

**强制约定**：

- 每张表必须包含 `id`（PK, UUID v7）、`created_at`、`updated_at`、`is_deleted`（tinyint, default 0）四个字段。
- 逻辑删除统一使用 `is_deleted`（tinyint, default 0），物理删除需在详细设计中明确说明。
- 字符集：`utf8mb4`，排序规则：`utf8mb4_unicode_ci`。
- 外键在逻辑层面确保，数据库层不建物理外键（由应用层保证）。
- 主键统一使用 UUID v7（`binary(16)` 存储），应用层生成，不依赖数据库自增。
  - MySQL：`INSERT INTO t (id, ...) VALUES (UUID_TO_BIN(UUID()), ...)`；查询 `BIN_TO_UUID(id)`。
  - PostgreSQL：`id` 列类型为 `uuid`，应用层直接传入 UUID 字符串。
  - Java 依赖：`com.fasterxml.uuid:java-uuid-generator`（JUG），使用 `UUIDGenerator.generateTimeBasedEpoch()`。
  - TypeScript 依赖：`uuid` v9+，使用 `import { v7 as uuidv7 } from 'uuid'`。
- JSON 字段使用 `json` 类型（MySQL 5.7+ / 8.0+）。

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

---

## 3. 索引规范

- 主键默认索引。
- 所有外键字段必须建索引。
- 频繁查询条件字段建索引。
- 联合索引将区分度高的字段放在左侧。
- 避免过多索引（单表不超过 5 个）。

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
