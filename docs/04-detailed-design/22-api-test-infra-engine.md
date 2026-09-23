# 软件测试平台——（分册：执行引擎）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.2 执行引擎接口

#### 3.2.1 触发场景执行

- **路径**：`POST /api/project/scenes/:sceneId/execute`
- **说明**：触发单个场景或组合执行。支持单场景执行、批量执行。
- **请求体**：

```json
{
  "environmentId": "018f...",
  "executionMode": "platform",
  "sceneIds": ["018f..."],
  "variableOverrides": { "base_url": "https://staging.example.com" }
}
```

- `environmentId`：目标环境 ID（可选，缺省使用项目默认环境）。
- `executionMode`：`platform`（平台内执行）。
- `sceneIds`：批量执行时传入多个场景 ID；单场景执行时传入单个 ID 或通过路径参数指定。
- `variableOverrides`：运行时变量覆盖（可选）。
- **响应**：

```json
{
  "executionHistoryId": "018f...",
  "status": "pending"
}
```

#### 3.2.2 查询执行状态

- **路径**：`GET /api/project/executions/:executionId`
- **响应**：

```json
{
  "id": "018f...",
  "sceneId": "018f...",
  "sceneName": "登录接口测试",
  "status": "running",
  "executionMode": "platform",
  "triggerType": "manual",
  "progress": 60,
  "executedAt": "2026-08-17T10:30:00Z",
  "durationMs": null
}
```

#### 3.2.3 取消执行

- **路径**：`POST /api/project/executions/:executionId/cancel`
- **说明**：取消进行中的执行任务。已执行的步骤结果保留，标记为 cancelled。
- **响应**：`{ "success": true }`


### 4.1 执行引擎与格式转换

执行引擎是接口测试的核心基础设施，基于 Ryze 框架构建。

#### 4.1.1 执行模式

| 模式 | 说明 | 资源消耗 |
| ---- | ---- | ---- |
| 平台内执行 | 调试请求与场景执行由平台执行引擎在服务端执行，格式转换后交 Ryze 引擎运行 | 消耗平台执行引擎资源 |

#### 4.1.2 格式转换机制

平台以自有字段模型存储全部接口测试数据，不持久化 Ryze 文档格式。Ryze 标准 JSON 仅在执行时由平台实时解析生成。

**平台模型 → Ryze TestSuite 映射**：

| 平台模型 | Ryze 标准 JSON |
| -------- | -------------- |
| 场景 | TestSuite（顶层集合） |
| 场景参数 | variables |
| 环境 HTTP 配置（多个） | configelements（testclass: http，挂载到 root testsuite） |
| 环境数据源（多个） | configelements（testclass: jdbc，挂载到 root testsuite） |
| 全局前置/后置处理器 | preprocessors / postprocessors |
| 场景步骤（http 取样器） | children（testclass: http） |
| 场景步骤（jdbc 取样器） | children（testclass: jdbc） |
| 步骤级处理器 | 步骤级 preprocessors / postprocessors |
| 步骤级验证器 | validators |
| 步骤级提取器 | extractors |
| 请求头、请求体、Query 参数 | config 对应字段 |

**环境配置 → configelements 转换规则**：

环境中的 HTTP 配置和数据源在执行时转为 Ryze configelements，挂载到 root testsuite 级别，由 Ryze 框架按 `ref` 自动处理继承与覆盖。

| 环境配置 | Ryze configelement（testclass） | 挂载字段 |
| -------- | ------------------------------- | -------- |
| 环境主表 `http_configs`（HTTP 配置 JSONB 列） | `http`（元件 `HTTPDefaults`，KW 含 `http`/`http_defaults`/`https`） | configelements 数组 |
| 环境主表 `data_sources`（数据源 JSONB 列） | `jdbc`（元件 `JDBCDatasource`，KW 含 `jdbc`/`jdbc_datasource`/`jdbc_data_source`） | configelements 数组 |

> 配置元件结构与 Ryze 引擎反序列化契约一致：顶层 `testclass` + `ref_name`（引用名，缺省时引擎按默认键 `__http_configure_element_default_ref_name__`/`__jdbc_configure_element_default_ref_name__` 注册），协议键放 `config` 对象内（HTTP：`base_url`/`headers`/`cookie`/`query`/`path` 等；JDBC：`driver`/`url`/`username`/`password`/`max_active` 等）。环境模型中 `refName` 直译为 `ref_name`，`baseUrl` 直译为 `base_url`，`maxPoolSize` 映射为 `max_active`（`connectionProperties` 本版仅留存环境 JSONB，不映射执行）。

**示例**：

```json
{
  "title": "测试场景",
  "configelements": [
    { "testclass": "http", "ref_name": "internal-api", "config": { "base_url": "https://api.internal.com", "headers": { "Authorization": "${token}" } } },
    { "testclass": "http", "ref_name": "pay-third", "config": { "base_url": "https://pay.third.com", "headers": {} } },
    { "testclass": "jdbc", "ref_name": "staging-db", "config": { "driver": "com.mysql.cj.jdbc.Driver", "url": "jdbc:mysql://staging-db:3306/test" } }
  ],
  "children": [...]
}
```

**步骤级 request_config 与 configelements 的关系**：

步骤的 `request_config` 保存步骤自身的差异配置（http 步骤：`method`/`url`/`headers`/`params`/`body`/`base_url`）。http 取样器 `config` 通过 `ref` 引用环境默认 HTTP 配置的 `ref_name`（步骤未显式指定时使用 `isDefault = true` 的配置，环境内未设默认取第一条，见《环境管理详细设计说明书》2.1.2），由 Ryze 引擎将环境配置元件与步骤配置合并执行：

- `url` 为相对路径时映射为 Ryze `config.path`，`base_url` 由环境配置元件经 `ref` 继承；
- `url` 为绝对地址或步骤显式配置 `base_url` 时写入 `config.base_url`（步骤级覆盖环境值）；
- 步骤无需重复配置环境中已有的值，配置了也没关系——Ryze 合并遵循最低层级优先（步骤级 > 环境级）。

> http 处理器（前置/后置）同理：其 `config.ref` 引用环境 http 配置的 `refName`，取代合并写法；处理器 `config` 仅含 Ryze 配置键，平台 overlay 键保存于元素顶层或实体列。

**多场景组合执行的层级映射**：

Ryze TestSuite 支持多层嵌套（项目级 → 模块级 → 用例级），子级集合自动继承父级的变量、配置元件与处理器。

| 平台组合执行 | Ryze TestSuite |
| ------------ | -------------- |
| 执行任务（批量） | 顶层 TestSuite（项目级） |
| 共享变量 | variables（顶层） |
| 共享配置元件 | configelements（顶层） |
| 场景 A | TestSuite（模块级子集合） |
| 场景 A 的步骤 | children（testclass: http/jdbc） |
| 场景 B | TestSuite（模块级子集合） |

> **定时任务（含立即执行）即按此模型实现**：测试计划任务把圈选出的全部场景组织为一个顶层 TestSuite，**环境相关内容全部挂载顶层、只取任务绑定环境**（顶层 `variables`=任务绑定环境变量、`preprocessors`/`postprocessors`=任务绑定环境前置/后置处理器、`configelements`=任务绑定环境的 HTTP 配置与数据源），各场景为子 TestSuite，其 `variables`=场景变量（不含环境）、`children`=场景启用步骤、`pre/postprocessors`=场景处理器（不含环境），场景自身关联环境**不参与构建**（定时任务以任务绑定环境为唯一执行环境）），**一次 `Ryze.start` 运行**。环境处理器顶层挂载后每次任务执行一次（而非每场景一次）。**顶层 suite 携带 `id` = 任务 ID（taskId），各场景子 suite 携带 `id` = 场景 ID（sceneId）**（suite 元素 `id` 映射到 `TestSuiteResult.id`，供结果树/快照直接定位），场景子 Suite 另携带 `metadata: {sceneId, taskId}`，执行引擎将元素 metadata 复制到对应 `TestSuiteResult` 节点，平台据此从单一大 suite 结果树按 `sceneId` 反查各场景结果并关联逐场景执行记录（执行/记录/报告详述见《定时任务详细设计说明书》4.3）。

**配置继承与优先级**（遵循 Ryze 原生语义）：

子级集合自动继承父级的变量、配置元件与处理器，同名配置项子级覆盖父级。执行时的合并优先级（从低到高）：

```
环境默认配置 < 顶层组合配置 < 场景级配置 < 步骤级配置
```

**格式转换失败处理**：

转换失败（平台模型存在 Ryze 无法表达的配置）时，执行引擎拒绝执行并返回错误码 7003（`API_FORMAT_CONVERT_FAILED`），不产生部分执行结果。错误信息包含具体失败原因与定位信息（如不支持的处理器类型、缺失的必填字段等）。

#### 4.1.3 资源池与并发调度

- 执行任务统一纳入资源池管理，最大并发数由系统配置项控制（默认 5）。
- 超出并发数的任务排队等待，队列长度可配置（默认 100），超出队列长度时返回错误码 7001（`API_EXECUTOR_BUSY`）。
- 定时触发的场景执行与手动执行统一排队。
- 组合执行作为一个整体任务入队，内部各场景按 Ryze 引擎串行或并行执行（由场景设置中的执行模式配置）。
- 执行超时按请求级「响应超时」配置控制，超时任务标记为超时失败（错误码 7002），记录错误信息。

#### 4.1.4 执行结果收集

Ryze 引擎执行完成后，平台收集执行结果并转换为平台自有格式：

1. **步骤级结果**：每个步骤的请求/响应快照、耗时、验证器结果、提取器结果。
2. **结果汇总**：总步骤数、通过数、失败数、跳过数、总耗时。
3. **Ryze 快照**：执行时生成的完整 Ryze 结果树 JSON，保存至 `api_report.ryze_snapshot`，用于结果回溯与转换问题定位。
4. **报告生成**：
   - **场景报告**：场景执行完成后将结果写入 `api_report`（`report_type='scene'`、`result`=场景数据集），同时更新 `api_execution_record` 状态与 `report_id`；
   - **套件报告**：测试计划任务（含立即执行）以一个顶层 TestSuite 一次运行，执行后由调度器侧从该大 suite 的 `TestSuiteResult` 树按场景子 suite 的 `metadata.sceneId` 递归抽取各场景步骤结果，聚合写入 `api_report`（`report_type='suite'`、`result`=套件数据集），并将套件报告 ID 回写本套件内各场景执行记录共享的 `report_id`（执行模型见《定时任务详细设计说明书》4.3，数据集结构见《测试报告详细设计说明书》2.3）。


### 5.1 执行状态轮询

场景执行触发后，前端通过轮询（2 秒间隔）查询执行状态，直到状态变为终态（success/failed/cancelled/timeout）：

```
触发执行 → 获得 executionHistoryId
  ↓ 轮询 GET /api/project/executions/:id
  status = pending/running → 继续轮询
  status = success → 跳转报告详情
  status = failed/cancelled/timeout → 展示错误信息
```


### 6.2 Ryze 依赖引入

在 `server/pom.xml` 中引入 Ryze 框架 Maven 依赖，版本锁定。Ryze 依赖 Java 21+，与平台技术栈一致。


### 6.3 执行引擎线程池配置

执行引擎线程池参数通过 `application.yml` 配置化：

```yaml
api-test:
  executor:
    max-concurrency: 5
    queue-capacity: 100
    thread-name-prefix: api-test-executor-
```


