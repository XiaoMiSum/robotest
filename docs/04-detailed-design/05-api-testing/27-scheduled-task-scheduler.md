# 软件测试平台——调度执行

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 定时调度器

定时调度器基于 JVM 内的 `ScheduledThreadPoolExecutor`（`ApiTestTaskScheduler`）实现，触发时刻由 Spring `CronExpression` 计算，精度为分钟级；任务注册表为实例内存态，不持久化。

**调度流程**：

1. 应用就绪（`ApplicationReadyEvent`）时加载全部 `enabled = true` 的定时任务。
2. 按 `cron_expression` 计算下次执行时间，注册为一次性延迟触发。
3. 到达执行时间时从库中重读任务：任务已删除或已停用 → 本次不执行、也不再续订；上一次执行未结束（`last_execution_status = running`）→ 写入一条 `status = skipped` 的执行记录（`error_message` = 「上一次执行尚未结束，本次触发跳过」），任务状态保持 `running`，不重复触发。
4. 已结束或首次执行 → 触发执行，执行入口先置 `last_execution_status = running`（`last_execution_at` 同步刷新）。
5. 执行完成 → 更新 `last_execution_status`（`success` / `failed`）、写入执行记录，随后在 `finally` 中取消旧注册并按**完成时刻**重算下次执行时间续订；执行期间错过的触发点不补跑。
6. 任务创建、更新、启停、删除后由 `ApiScheduleServiceImpl` 回调 `onTaskChanged` 取消原注册并按最新配置重注册（任务已删除或停用则不注册），因此 Cron 修改与启停即时生效。

**Cron 解析与下次执行时间**：

- 输入为 5 位表达式，经 `CronSupport` 在表达式前统一补上秒位 `0` 后交由 Spring `CronExpression` 解析；支持 `*`、`-`、`,`、`/`、`?`、`L`、`W`、`#` 及英文月 / 星期名，秒位恒为 0，故触发精度到分钟。
- 触发时刻按**服务器本地时钟**计算；星期取值 0–7，0 与 7 均为周日（1 = 周一 … 6 = 周六）。
- 解析失败返回 `null`：创建 / 更新任务时抛 `1000017502`（`API_SCHEDULED_TASK_CRON_INVALID`）；调度器注册阶段解析失败则跳过该任务，不产生触发。
- 下次执行时间由 `next(now)` 计算：任务列表返回 3 次（`nextExecutions`，仅启用任务有值，停用任务返回空、前端展示 `-`）、Cron 校验返回 5 次、创建响应返回 1 次（`nextExecutionAt`）；对外统一换算为 UTC 钟面返回，前端按浏览器时区还原展示。

**Cron 表达式格式**（5 位，精度到分钟）：

```
┌───── 分钟（0-59）
│ ┌───── 小时（0-23）
│ │ ┌───── 日（1-31）
│ │ │ ┌───── 月（1-12）
│ │ │ │ ┌───── 星期（0-7，0和7均为周日）
│ │ │ │ │
* * * * *
```

**预设常用表达式**（前端预设下拉，描述与 `CronSupport` 预设表一致）：

| 名称 | 表达式 | 说明 |
| ---- | ------ | ---- |
| 每小时 | `0 * * * *` | 每小时整点 |
| 每天凌晨 | `0 2 * * *` | 每天 02:00 |
| 每周一 | `0 2 * * 1` | 每周一 02:00 |
| 每月1号 | `0 2 1 * *` | 每月1号 02:00 |
| 工作日 | `0 2 * * 1-5` | 周一至周五 02:00 |

**触发方式**：

| `trigger_type` | 入口 | 行为 |
| ---- | ---- | ---- |
| `scheduled` | Cron 到点（调度器触发） | 按上述调度流程执行；任务 `running` 时只记 `skipped` |
| `manual` | 调度页 [立即执行]（接口见《任务管理》分册 `docs/04-detailed-design/05-api-testing/26-scheduled-task-management.md` 1.6） | 上一次执行未结束直接返回 `1000017504`；测试计划任务入池异步执行（响应 `status = running`，`executionId` = 任务 ID），接口同步任务在请求线程同步执行（`executionId` = 导入记录 ID） |

**执行状态口径**：

- 任务 `last_execution_status`：`NULL`（未执行过）→ `running` → `success` / `failed`；测试计划任务有任一场景失败、或本次未圈选到可执行场景，均判 `failed`。
- 执行记录 `status`：`success` / `failed` / `skipped`，`trigger_type` 为 `scheduled` / `manual`；失败原因 `error_message` 一律按 2000 字符截断。
- 进程在执行过程中异常退出时 `running` 会残留：此后定时触发只记 `skipped`、[立即执行] 返回 `1000017504`，当前实现无自动复位机制。
- 多实例部署时各实例独立注册与触发，仅在触发时刻按库中 `running` 状态判断是否跳过，注册表随进程结束失效，不提供跨实例调度协调。


## 2. 测试计划任务执行（场景批量执行）

`task_type = scene_execute` 的任务触发时，按执行范围基于**实时数据**圈选场景并**组织为一个顶层 TestSuite 一次执行**：

| 执行方式（execution_scope） | 圈选规则 |
| ---- | ---- |
| `all` | 目标项目下全部可执行场景（已发布状态且至少 1 个启用步骤） |
| `modules` | `module_ids` 中指定模块及其子模块下的场景（仅已发布状态） |
| `scenes` | `scene_ids` 中指定的具体场景（仅已发布状态） |

**执行流程**：

1. 置 `last_execution_status = running`。
2. 按 `execution_scope` 实时圈选场景：`all` 取项目全部场景；`modules` 先按模块树展开全部子模块再取场景；`scenes` 取指定场景并校验归属项目。已删除场景直接从圈选结果中消失（不产生记录）。
3. 逐场景过滤：非 `published`（草稿）→ 写一条 `skipped` 记录「场景「×」为草稿状态，已跳过」；无启用步骤（不可执行）→ 写一条 `skipped` 记录「场景「×」不可执行（无启用步骤），已跳过」。
4. 过滤后无可用场景即结束本次触发：圈选结果为空时再补一条 `skipped` 记录「未圈选到可执行场景，本次触发未执行」；两种情况均将任务状态置 `failed`，不组装、不执行。
5. 组装顶层 suite 后**一次提交执行引擎、一次 `Ryze.start` 运行**（`startSuite` 同步等待，无超时）。
6. 执行完成后从结果树按场景子 suite 的 `metadata.sceneId` 递归抽取各场景步骤结果，生成套件报告与执行记录，最后更新任务状态。

**套件组装**：

- 环境相关内容全部挂载顶层、只取任务绑定环境（`environment_id`）——顶层 `variables` 挂任务绑定环境变量、`preprocessors` / `postprocessors` 挂任务绑定环境前置 / 后置处理器、`configelements` 挂任务绑定环境的 HTTP 配置与数据源；各场景作为顶层 TestSuite 的**子 TestSuite**（子 suite 的 `variables` = 该场景变量（不含环境）、`children` = 该场景启用步骤、`preprocessors` / `postprocessors` = 该场景处理器（不含环境））。
- **场景自身关联环境不参与构建**（定时任务以任务绑定环境为唯一执行环境），环境处理器顶层挂载后每次任务执行一次（而非每场景一次）。
- **顶层 suite 携带 `id` = 任务 ID（taskId）、`title` = 任务名**；**场景子 suite 携带 `id` = 场景 ID（sceneId）、`title` = 场景名**（`id` 供结果树 / 快照直接定位所属任务与场景），并携带 `metadata: {sceneId, taskId}` 用于执行结果按场景反查。
- 场景与步骤到 Ryze 套件的字段映射见《执行引擎详细设计说明书》（`docs/04-detailed-design/05-api-testing/03-api-testing-infra-engine.md`）2.1；环境 / 场景 / 步骤配置的继承与合并见《测试场景详细设计说明书》（`docs/04-detailed-design/05-api-testing/12-test-scenario-overview.md`）2.4。

**报告与落库**：

- 每次触发**生成一份套件报告**（`report_type = 'suite'`，`source = 'schedule'`，`external_id` = 任务 ID），报告名称为「任务名 + 执行时间戳」，明细按「场景 → 步骤」两级（套件数据集结构见《测试报告详细设计说明书》（`docs/04-detailed-design/05-api-testing/28-test-report-overview.md`）2.3.2）。区别于场景页 [运行] 的 `source = scene`（场景报告，不进报告列表）。
- 执行完成后汇总各场景结果，任一个场景失败则任务整体判 `failed`、套件报告状态置 `failed`（整体判定规则见《测试报告详细设计说明书》2.3.2）。
- 场景执行记录逐场景落库（`api_execution_record`，一场景一条，`execution_record.scene_id` 单值，`environment_id` = 任务绑定环境，`source = 'schedule'`，`report_id` 共享本套件报告 ID，`trigger_type` 按 `scheduled` / `manual` 落）。
- 执行完成后为每个圈选场景各写一条任务执行记录（`api_scheduled_task_execution`，`status = success`，`error_message` = 「场景「×」执行完成」，`report_id` 共享本套件报告 ID，`duration_ms` 为本次触发总耗时），任务最终状态按场景结果判 `success` / `failed`。

**失败处理**：

- `startSuite` 抛出异常（提交失败 / 执行池拒绝 / 中断）→ 写 1 条 `status = failed` 的任务执行记录（`error_message` = 异常摘要、按 2000 截断，`duration_ms = 0`），任务状态置 `failed`；**不生成套件报告、不写 `api_execution_record`**。
- 顶层执行异常（整包构建 / 启动失败）：若 `Ryze.start` 返回的顶层 `TestSuiteResult` 携带 `throwable`（引擎兜底捕获而非抛出）→ 写 1 条 `status = failed` 的任务执行记录（含实际耗时），任务状态置 `failed`；同样**不生成套件报告（无 `api_report` 行）、不写 `api_execution_record`**（无逐场景结果）。
- 结果树中缺某场景子 suite（结果缺失）→ 该场景以占位明细（`status = failed`、步骤为空）计入套件报告并计入失败场景数，但不写该场景的 `api_execution_record`。
- 手动触发时异常继续上抛供前端统一提示；定时触发仅留痕（`ScheduledTaskRunner` 兜底捕获并写失败记录）。

**约束**：

- 不提供取消与超时：任务执行等待大 suite 完成后一次性汇总（无单场景级 cancel / timeout 语义）。
- 被圈选场景 / 模块的删除保护见《任务管理》分册（`docs/04-detailed-design/05-api-testing/26-scheduled-task-management.md`）2. 删除保护。


## 3. 接口同步任务执行

`task_type = import_swagger` 的任务触发时，直接拉取 `openapi_url` 指定的 OpenAPI/Swagger JSON 文件并增量更新接口定义：

**执行流程**：置 `running` → `ApiInterfaceImportService.importUrl(projectId, 执行者, openapiUrl, null)`（`ImportSourceFetcher` 拉取与 SSRF / 端口策略校验 → 解析 → 增量导入，整体事务）→ 写 1 条 `status = success` 的任务执行记录（`import_record_id` 关联本次导入记录，`report_id` 为空，`duration_ms` 为本次耗时）→ 任务状态置 `success`，返回导入记录 ID 与结果摘要。

- 复用接口导入引擎（`ApiInterfaceImportService.importUrl`，含 `ImportSourceFetcher` 拉取与 SSRF 防护）。
- 导入策略见《导入》分册（`docs/04-detailed-design/05-api-testing/10-interface-management-import.md`）6. 增量导入策略：先按导入映射（`api_import_mapping`）匹配，再按「接口路径 + 方法」匹配，命中即覆盖更新源中提供的字段并递增 `changeVersion`，未命中则创建，源中不存在的既有接口保留不删除。
- 执行记录关联 `api_import_record` 并回显导入结果（新增 / 更新 / 失败数）。
- 保存任务时校验 `openapi_url`：仅允许 http/https 协议，通过 SSRF / 端口策略校验并可达（HTTP 2xx，复用 `ImportSourceFetcher`）；文档格式合法性在执行时由解析器校验。
- **失败处理**：拉取 / 解析 / 导入抛出异常 → 写 1 条 `status = failed` 的任务执行记录（`error_message` = 异常摘要、按 2000 截断，`duration_ms = 0`），任务状态置 `failed`，不产生 `report_id`；手动触发时异常继续上抛供前端提示，定时触发仅留痕。


---


## 4. 调度器线程池

调度器线程池大小为可配置项（`server/src/main/resources/application.yaml`）：

```yaml
robotest:
  api-test:
    scheduler:
      # 定时任务调度线程池大小
      pool-size: ${SCHEDULER_POOL_SIZE:2}
```

- 调度器按 `pool-size`（默认 2，代码中按最小 1 取整）创建 `ScheduledThreadPoolExecutor`：线程为守护线程，线程名固定 `api-test-scheduler`（无序号后缀，未提供线程名前缀配置项），并开启 `setRemoveOnCancelPolicy(true)`（已取消的注册及时出队）。
- 定时触发的任务回调在该池线程上**同步执行并阻塞等待完成**（大 suite 实际运行在共享执行池，线程池配置见《执行引擎详细设计说明书》（`docs/04-detailed-design/05-api-testing/03-api-testing-infra-engine.md`）2.2），执行结束后才续订下一次；因此同实例可同时进行的定时触发数受 `pool-size` 限制，超出的触发在调度队列中排队、延迟执行。
- 手动触发不占用调度线程：测试计划任务提交到 `ScheduledTaskRunner` 的独立固定线程池（2 线程、守护线程、线程名 `api-test-task-tracker`）异步执行；接口同步任务在请求线程同步执行。
- 应用关闭时（`@PreDestroy`）对调度线程池与上述执行线程池执行 `shutdownNow()`。
- 同级还声明 `execution-timeout-minutes`（默认 30）与 `poll-interval-ms`（默认 5000）两个历史遗留配置项，当前实现未读取——大 suite 为同步等待，不轮询、不超时。


---


**文档结束**

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐实现：修正章节自引用与交叉引用，补全 Cron 解析与下次执行时间口径、执行与失败处理流程、状态口径及线程池配置 |
