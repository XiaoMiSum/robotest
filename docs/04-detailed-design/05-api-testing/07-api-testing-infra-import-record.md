# 软件测试平台——导入记录

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 导入记录接口

导入记录（表 `api_import_record`）由接口导入执行时由服务端写入，用于留痕每次导入的来源、结果与错误明细。导入接口与弹窗交互详见 `docs/04-detailed-design/05-api-testing/10-interface-management-import.md`。

**当前实现现状**：后端未提供导入记录的独立查询接口，前端也未提供导入记录查询页面。记录在导入完成时仅以 `importHistoryId` 形式随导入结果返回，另在定时任务执行记录响应中以 `importSummary` 间接暴露（见 1.3）。

### 1.1 记录写入

- **写入时机**：导入执行接口在事务内完成 upsert 后写入一条记录，同时写入若干条 `api_import_mapping` 映射（映射规则见 `docs/04-detailed-design/05-api-testing/10-interface-management-import.md` 第 6 节）。
  - `POST /api/project/interfaces/import/url` — Swagger URL 导入，权限 `api-interface:edit`
  - `POST /api/project/interfaces/import/parsed` — cURL 解析结果导入，权限 `api-interface:edit`
  - `POST /api/project/interfaces/import/preview` — 仅解析预览，权限 `api-interface:view`，**不写入记录**
- **前置失败不产生记录**：URL 拉取失败、格式无法识别、内容解析失败在写入前即整体中断，分别返回 10 位错误码 `1000017012`（URL 不可达）、`1000017010`（格式不支持）、`1000017011`（解析失败），此时不落记录。
- **字段取值**：

| 字段 | 取值 |
| ---- | ---- |
| `import_type` | `url`（Swagger URL 导入）、`curl`（cURL 粘贴导入）；实体注释中的 `file_swagger` / `file_postman` / `file_har` / `file_jmeter` 为预留取值，当前实现不产生 |
| `source_name` | URL 导入为请求的 URL 原文；cURL 导入固定为 `cURL 粘贴导入` |
| `status` | `success`：无失败条目；`partial`：存在失败条目且至少一条成功；`failed`：全部条目失败 |
| `summary` | `{created, updated, failed}` 三个计数键，不含 `skipped` |
| `error_details` | `[{source, message}]`，`source` 为操作名（summary → operationId → `METHOD path` 逐级降级），`message` 为异常信息 |
| `created_by` | 导入人用户 ID |

- **记录 ID 回传**：写入后记录主键作为 `importHistoryId` 返回给前端，前端类型 `ApiInterfaceImportResult`（`web/src/types/project/api-testing/apitest.ts`）与响应 `importHistoryId` / `summary` / `errors` 一一对应。

### 1.2 结果反馈（前端）

导入入口为接口工作区顶部右侧「导入」按钮（`web/src/pages/project/api-testing/interface/InterfaceWorkspace.vue`），前端未给该按钮挂权限码，接口管理页签按 `api-interface:view` 显隐，实际编辑权限由后端 `api-interface:edit` 校验。

弹窗 `web/src/pages/project/api-testing/interface/ImportDialog.vue` 的行为：

- **来源模式**：`Swagger URL` / `cURL` 单选切换，切换后清空已解析预览与本地解析结果；弹窗每次打开重置全部输入与结果。
- **解析预览**：URL 模式「解析预览」调用 `POST /api/project/interfaces/import/preview`；cURL 模式「本地解析」由前端 `parseCurlImport` 完成，不经过后端。预览表格展示名称、方法、路径与动作标签（`create`→新建 / `update`→覆盖更新 / `skip`→跳过），汇总键为 `toCreate` / `toUpdate` / `toSkip`；URL 为空或 cURL 未解析到接口时以警告消息阻断。
- **执行导入**：调用对应导入接口并以 `loading` 防重复提交；成功后提示摘要 `新建 x · 更新 y · 失败 z`。
- **结果分支**：`errors` 为空 → 关闭弹窗；`errors` 非空 → 保留弹窗并展示「失败明细」列表（`source：message`），同时提示部分失败条数；两种分支均向列表页 emit `imported` 以刷新列表。
- **失败反馈**：请求异常以错误消息提示，不关闭弹窗。

### 1.3 记录查询（未实现）

- 后端**不存在** `GET /api/project/import-records` 一类的导入记录查询端点，控制器层无对应路由；前端路由与菜单中也没有导入记录页面或入口。本分册不声明任何导入记录分页查询接口。
- **间接读取途径**：定时任务执行记录分页 `GET /api/project/scheduled-tasks/{id}/executions` 的响应项在 `importRecordId` 之外回填 `importSummary`（取自记录表 `summary` 字段）；`scene_execute` 类型执行无 `importRecordId`，两字段为 `null`。前端类型 `ApiScheduleExecutionItem`（`web/src/types/project/api-testing/report.ts`）已声明这两个字段，但 `web/src/pages/project/api-testing/schedule/SchedulesPage.vue` 的执行记录抽屉当前只展示触发时间、触发方式、状态、耗时与失败原因，**未展示导入汇总**。

### 1.4 数据表（api_import_record）

与 `server/src/main/resources/db/schema.sql` 保持一致（UUID 主键、逻辑删除、无物理外键）：

```sql
CREATE TABLE api_import_record (
    id              UUID         PRIMARY KEY,
    project_id      UUID         NOT NULL,
    import_type     VARCHAR(20)  NOT NULL,
    source_name     VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    summary         JSONB        NULL,
    error_details   JSONB        NULL,
    created_by      UUID         NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_irecord_project ON api_import_record(project_id, created_at DESC);
```

- `status` 默认值 `pending` 为占位；当前写入路径只产生 `success` / `partial` / `failed`，不存在停留在 `pending` 的记录。
- `project_id`、`created_by` 为逻辑外键，经 `idx_irecord_project` 支撑按项目倒序的记录翻页（供后续查询接口使用）。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 按实现对齐：删除未实现的 `GET /api/project/import-records` 查询接口，补全记录写入时机与字段取值、导入结果反馈流程、间接查询途径及与 schema.sql 一致的表结构 |
