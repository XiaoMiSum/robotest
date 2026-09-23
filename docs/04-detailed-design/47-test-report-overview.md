# 软件测试平台——（总览分册）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的 **接口测试报告**进行详细设计，定义报告生成、分享、批量删除、状态同步、清理的业务逻辑与前端行为，为开发实现提供完整依据。报告的数据表（`api_report`）与报告接口（列表、详情、分享、删除）在《API 测试基础设施详细设计说明书》中定义，本文档不重复描述。Mock 服务相关设计见《Mock 服务详细设计说明书》（`docs/04-detailed-design/01-readme.md`）。

### 1.2 范围

覆盖 SRS 3.5（接口测试报告）与概要设计对应模块：

- **接口测试报告**：报告列表与详情、分享链接、批量删除、状态刷新、报告清理；
- **全局资产**：项目级可复用组件资产库（前置处理器、后置处理器、验证器、提取器）的管理与复制（表结构与接口见《API 测试基础设施详细设计说明书》2.1.5 与 3.5–3.6）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.5）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/01-readme.md`）
- 《Mock 服务详细设计说明书》（`docs/04-detailed-design/01-readme.md`）

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 报告表（api_report）

报告表（`api_report`）的完整字段定义、索引与说明见《API 测试基础设施详细设计说明书》2.1.4，此处不重复描述。本文档涉及的关键字段：

| 字段 | 说明 |
| ---- | ---- |
| id | 报告主键 |
| project_id | 归属项目 |
| execution_record_id | 关联执行记录（api_execution_record.id）。场景报告 1:1；套件报告对应多条执行记录，此字段为空 |
| report_type | 报告粒度：`scene`（场景报告，单场景）/ `suite`（套件报告，定时任务含立即执行聚合多场景） |
| external_id | 外部关联 ID，随 `report_type`：`suite` = 任务 ID；`scene` = 场景 ID |
| name | 报告名称（场景报告 = 场景名 + 执行时间戳；套件报告 = 任务名 + 执行时间戳，生成时固化） |
| execution_mode | 执行方式：platform（平台内执行） |
| status | success / failed / partial（场景报告）；套件报告按整体判定 |
| source | 报告来源：scene / schedule |
| summary | 结果汇总（场景报告 `{total, passed, failed, skipped, duration_ms}`；套件报告含场景级汇总，见 2.3） |
| result | 结果明细数据集（场景数据集 / 套件数据集，字段结构见 2.3） |
| ryze_snapshot | 执行时的 Ryze 结果树序列化快照 |
| share_token | 分享链接令牌 |
| share_expires_at | 分享链接过期时间 |
| share_user_id | 生成分享链接的用户（分享者），用于分享记录展示与复制文本 |

**结果数据集**：`api_report.result` 按 `report_type` 存储场景/套件两种数据集，字段结构见 **2.3 结果数据集模型**（含步骤状态枚举、验证器明细、提取器结果结构）。

**索引**：见《API 测试基础设施详细设计说明书》2.1.4（`idx_report_type_external`、`idx_report_project_created`、`idx_report_share_token` UNIQUE、`idx_report_share_user`）。

> `idx_report_share_token` 支撑分享访问按 token 查询（见 4.2.2）。

#### 2.1.2 全局资产表（global_asset）

全局资产表（`global_asset`）的字段定义见《API 测试基础设施详细设计说明书》2.1.5。

### 2.2 错误码引用

| 错误码 | 常量名 | 说明 | 定义位置 |
| ------ | ------ | ---- | -------- |
| **7009** | API_SHARE_EXPIRED | 分享链接无效或已过期 | 《API 测试基础设施详细设计说明书》2.2 |

> 权限不足（非执行者本人且非项目维护者）返回 HTTP 403，不占用业务错误码号段。

### 2.3 结果数据集模型

`api_report.result`（JSONB）按 `report_type` 存储两类数据集，均由执行引擎基于 **Ryze 结果树**（`io.github.xiaomisum.ryze`，见 `D:\Github\ryze\docs\developer\result.md`）构建：`SampleResult` → 步骤，`AssertionResult` → 验证器，`ExtractorResult` → 提取器。`result` 本身为平台结构化视图，**不包含** ryze 快照（快照独立存于 `api_report.ryze_snapshot`）。

#### 2.3.1 场景数据集（report_type = 'scene'）

单个场景执行结果的平面化结构：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| sceneId | UUID | 场景 ID（external_id） |
| sceneName | String | 场景名称快照 |
| status | String | success / failed / partial / cancelled |
| summary | Object | 见下文场景汇总 |
| environmentName | String/null | 环境名称快照 |
| executedAt | String | 执行时间（ISO-8601 UTC） |
| steps | Array | 步骤明细（见步骤元素） |
| preprocessors | Array | 场景前置处理器执行明细（元素形状同步骤元素，渲染规则复用步骤明细；无处理器时为空数组） |
| postprocessors | Array | 场景后置处理器执行明细（元素形状同步骤元素；无处理器时为空数组） |

**场景汇总** `summary`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| total | int | 启用步骤总数 |
| passed | int | 通过数（所有验证器通过） |
| failed | int | 失败数（任一验证器失败） |
| skipped | int | 跳过数（禁用/后续跳过） |
| durationMs | long | 执行耗时 |

**步骤元素**（`steps[]`，对应 ryze `SampleResult`）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| stepId | String | 步骤 ID（平台步骤 ID） |
| name | String | 步骤名称 |
| type | String | 协议类型（HTTP / JDBC / Redis / WebSocket / …，取请求元数据） |
| status | String | passed / failed / skipped / not_executed |
| durationMs | long | 步骤耗时 |
| request | Object | 请求快照（Real*Request 按协议 getter 序列化，如 url/method/query/headers/body/format） |
| response | Object | 响应快照（Real*Response 按协议 getter 序列化，如 status/headers/body/format/bodyBytes 截断） |
| assertions | Array | 验证器明细（AssertionResult） |
| extractors | Array | 提取器明细（ExtractorResult） |
| errorMessage | String/null | 异常/失败信息 |

**验证器明细**（`assertions[]`）：`{field, rule, expected, actual, status, message}`——验证字段、规则（`==`/`contains` 等）、期望值（已求值）、实际值、状态（passed/failed/skipped）、失败消息。

**提取器明细**（`extractors[]`）：`{refName, field, value, defaultValue|boolean, message}`——变量名、提取表达式（JSONPath/正则）、提取值、是否走默认值分支、失败信息。

#### 2.3.2 套件数据集（report_type = 'suite'）

定时任务（含立即执行）执行多个场景聚合生成，内嵌多个场景形成「场景 → 步骤」两级：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| reportType | String | 固定 `suite` |
| taskId | UUID | 关联任务 ID（external_id） |
| taskName | String | 任务名称快照（报告名称来源） |
| source | String | 报告来源：schedule（定时任务含立即执行） |
| status | String | success / failed / partial——任一场景 failed 则整体 failed |
| summary | Object | 见下文套件汇总 |
| environmentName | String/null | 环境名称快照（任务绑定环境） |
| triggeredAt | String | 触发时间（ISO-8601 UTC） |
| scenes | Array | 场景明细数组，每项即 **2.3.1 场景数据集** |
| preprocessors | Array | 环境前置处理器执行明细（任务绑定环境处理器，挂顶层 suite 执行一次；元素形状同步骤元素） |
| postprocessors | Array | 环境后置处理器执行明细（任务绑定环境处理器，挂顶层 suite 执行一次；元素形状同步骤元素） |

**套件汇总** `summary`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| totalScenes | int | 执行场景总数 |
| passedScenes | int | 通过场景数 |
| failedScenes | int | 失败场景数 |
| totalSteps | int | 全部场景启用步骤总数 |
| passedSteps | int | 通过步骤总数 |
| failedSteps | int | 失败步骤总数 |
| skippedSteps | int | 跳过步骤总数 |
| durationMs | long | 一次触发总耗时 |

> 套件数据集（report_type='suite'）通过 `external_id`（任务 ID）与共享 `report_id` 关联各场景执行记录；报告列表一行一报告（名称 = 任务名 + 执行时间戳）。

---



报告基础接口（报告列表、详情、生成分享链接、访问分享报告、删除）见《API 测试基础设施详细设计说明书》3.4，本文档不重复描述。本文档补充列表筛选参数、批量删除与状态刷新接口。


### 4.4 权限设计

报告按项目隔离，权限规则：

| 操作 | 权限 |
| ---- | ---- |
| 查看 / 分享 / 刷新状态 | 执行者本人或项目维护者 |
| 删除 / 批量删除 | 项目维护者 |

**403 处理**：非执行者本人且非项目维护者访问报告接口返回 HTTP 403；前端展示 403 页面「无权限访问该报告」。


## 6. 实施说明

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `47-test-report-overview.md` | 前言、1. 引言、2. 数据设计、4.4 权限设计、6. 实施说明、3. 接口详细设计 |
| 报告列表 | `48-test-report-list.md` | 3.1 报告列表筛选参数、3.2 批量删除报告、3.3 刷新报告状态、4.3 报告清理 |
| 详情与生成 | `49-test-report-detail.md` | 4.1 报告生成、4.5 场景执行记录弹窗查看报告、5.1 报告查看页、5.2 执行记录弹窗查看报告 |
| 分享 | `50-test-report-share.md` | 4.2 分享机制 |
