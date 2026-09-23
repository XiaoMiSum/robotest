# 软件测试平台——（分册：变量与 HTTP 配置）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `36-environment-management-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.3 环境变量管理

环境变量**随环境整体聚合提交**：变量列表嵌入环境配置中，通过环境的创建/更新接口（3.1.3 / 3.1.4）整体提交，写入主表 `variables` JSONB 列；环境详情接口（3.1.2）随环境配置返回完整变量列表（变量值明文返回）。**原批量替换（`PUT /environments/:id/variables`）、批量导入（`.../variables/import`）、导出（`.../variables/export`）独立接口均已移除**，批量替换/导入/导出统一复用环境的创建/更新（3.1.3 / 3.1.4）与导入（3.1.10）/导出（3.1.9）能力。保留「从执行结果添加变量」接口（见 3.3.1）。

`variables` 数组元素结构：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | 是 | 变量名（仅字母/数字/下划线且不得重名） |
| value | TEXT | 否 | 变量值（明文存取、明文展示） |
| description | VARCHAR(500) | 否 | 变量描述 |

#### 3.3.1 从执行结果添加变量

- **路径**：`POST /api/project/environments/:id/variables`
- **请求体**：

```json
{
  "name": "orderNo",
  "value": "SO-20260817",
  "description": "从执行结果添加",
  "sourceStepId": "018f...",
  "sourceReportId": "018f..."
}
```

- **说明**：用于「从执行结果快速添加」：单步骤调试结果浮层或报告详情选中响应片段后写入环境变量；实现为在环境主表 `variables` JSONB 数组中追加元素；`source_step_id` / `source_report_id` 记录来源便于追溯。
- **校验**：变量名重名时返回校验错误（提示「变量已存在」）。


### 3.4 HTTP 配置与数据源管理

HTTP 配置与数据源**随环境整体聚合提交**：通过环境的创建/更新接口（3.1.3 / 3.1.4）一次性整体提交，分别写入主表 `http_configs` / `data_sources` JSONB 列；环境详情接口（3.1.2）随环境配置返回完整 HTTP 配置与数据源列表（数据源脱敏）。**不再提供独立的 HTTP 配置 / 数据源新增、更新、删除接口**；面板内的增删改在提交时整体写入。连接测试改为请求体传入完整配置（见 3.1.7 / 3.1.8）。

`http_configs` 数组元素结构：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | 是 | 配置名称 |
| refName | VARCHAR(100) | 是 | 引用名称（对应 Ryze `refName`） |
| baseUrl | VARCHAR(2000) | 是 | Base URL |
| headers | JSON | 否 | 请求头 `[{key, value, enabled}]`（空 key/value 行过滤、`enabled=false` 行剔除） |
| isDefault | BOOLEAN | 否 | 是否默认（同一环境内至多一个；为 true 时清除其余 HTTP 配置的 `isDefault`，缺省 false） |

`data_sources` 数组元素结构：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | 是 | 数据源名称 |
| refName | VARCHAR(100) | 是 | 引用名称（对应 Ryze `refName`） |
| driver | VARCHAR(100) | 是 | JDBC 驱动类名；Redis 数据源以 `-` 占位驱动满足必填，连接测试按 `redis://` 协议识别 |
| url | VARCHAR(500) | 是 | JDBC 连接 URL / Redis `redis://[password@]host:port/db` |
| connectionProperties | JSON | 否 | 附加连接参数（缺省 `{}`） |
| maxPoolSize | INT | 否 | 连接池最大连接数（为空默认 5） |
| isDefault | BOOLEAN | 否 | 是否默认（同一环境内至多一个；为 true 时清除其余数据源的 `isDefault`，缺省 false） |

请求体中的 `headers` 复用 3.1.3 中的 header 结构。

---


### 4.1 变量解析优先级

环境变量与场景变量、步骤级变量、步骤提取器变量、运行时覆盖同名时，按从低到高优先级取值（与 `docs/详细设计/README.md` 4.1 一致）：

```
内置函数 < 环境变量 < 场景变量 < 步骤级变量 < 步骤提取器变量 < 运行时覆盖
```

环境变量作为次低优先级，仅高于内置函数；场景级、步骤级同名变量依次覆盖环境变量（见需求 3.5「参数优先级」规则）。


### 4.2 数据源连接池

数据源连接采用 HikariCP 连接池，每个环境每个数据源独立连接池。连接池参数：
- `maximumPoolSize`：由数据源聚合 JSONB 的 `maxPoolSize` 字段控制（默认 5）。
- `minimumIdle`：1。
- `connectionTimeout`：10000ms。
- `idleTimeout`：600000ms（10 分钟）。

Redis 数据源不建立 HikariCP 连接池（`max_pool_size` 对其无意义）：连接测试按需创建 RESP 直连并在用毕关闭；Redis 取样器执行属多协议扩展预留（见需求「多协议扩展」），届时再评估长连接复用策略。

---


### 6.1 数据源连接池

连接池设计见 4.2，实现注意点：
- 数据源连接失败抛出 `BusinessException`（错误码 7403），不影响环境保存与删除。
- 连接池按「环境 + 数据源」维度缓存，环境更新或数据源删除后释放对应连接池。


### 6.2 敏感数据加密

- 环境变量值明文存储、明文展示（无敏感值概念，`type`/加密链路已移除，执行快照不再解密）。
- 数据源密码直接写入连接 URL，不在表单中独立存储。


