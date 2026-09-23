# 软件测试平台——（分册：调度执行）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `44-scheduled-task-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 4.1 定时调度器

定时调度器基于 JVM 内的 ScheduledExecutorService 实现，精度为分钟级：**调度流程**：
1. 应用启动时加载所有 `enabled = true` 的定时任务。
2. 按 `cron_expression` 计算下次执行时间，注册到调度器。
3. 到达执行时间时，检查上一次执行是否结束（`lastExecutionStatus != running`）。
4. 未结束 → 跳过本次触发（记录 skipped），不重复触发。
5. 已结束或首次执行 → 触发执行，更新 `lastExecutionStatus = running`。
6. 执行完成 → 更新状态、写入执行记录、计算下次执行时间并注册。

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

**预设常用表达式**：

| 名称 | 表达式 | 说明 |
| ---- | ------ | ---- |
| 每小时 | `0 * * * *` | 每小时整点 |
| 每天凌晨 | `0 2 * * *` | 每天 02:00 |
| 每周一 | `0 2 * * 1` | 每周一 02:00 |
| 每月1号 | `0 2 1 * *` | 每月1号 02:00 |
| 工作日 | `0 2 * * 1-5` | 周一至周五 02:00 |


### 4.3 测试计划任务执行（场景批量执行）

`task_type = scene_execute` 的任务触发时，按执行范围基于**实时数据**圈选场景并**组织为一个顶层 TestSuite 一次执行**：

| 执行方式（execution_scope） | 圈选规则 |
| ---- | ---- |
| `all` | 目标项目下全部可执行场景（已发布状态且至少 1 个启用步骤） |
| `modules` | `module_ids` 中指定模块及其子模块下的场景（仅已发布状态） |
| `scenes` | `scene_ids` 中指定的具体场景（仅已发布状态） |

- 圈选结果执行前重新查询（实时数据），已删除/草稿/不可执行的场景跳过并在执行记录中给出提示。
- 圈选出的全部可执行场景组织为**一个顶层 TestSuite**：**环境相关内容全部挂载顶层、只取任务绑定环境（`environment_id`）**——顶层 `variables` 挂任务绑定环境变量、`preprocessors`/`postprocessors` 挂任务绑定环境前置/后置处理器、`configelements` 挂任务绑定环境的 HTTP 配置与数据源；各场景作为顶层 TestSuite 的**子 TestSuite**（子 suite 的 `variables` = 该场景变量（不含环境）、`children` = 该场景启用步骤、`pre/postprocessors` = 该场景处理器（不含环境））。**场景自身关联环境不参与构建**（定时任务以任务绑定环境为唯一执行环境），环境处理器顶层挂载后每次任务执行一次（而非每场景一次）。**顶层 suite 携带 `id` = 任务 ID（taskId）**；**场景子 suite 携带 `id` = 场景 ID（sceneId）**（`id` 供结果树/快照直接定位所属任务与场景），并携带 `metadata: {sceneId, taskId}` 用于执行结果按场景反查。整个任务**一次提交执行引擎、一次 `Ryze.start` 运行**（层级映射与配置继承见《API 测试基础设施详细设计说明书》4.1.2）。
- 每次触发**生成一份套件报告**（`report_type = 'suite'`，`source = 'schedule'`），报告名称为「任务名 + 执行时间戳」，明细按「场景 → 步骤」两级（套件数据集结构见《测试报告详细设计说明书》2.3.2）。区别于场景页 [运行] 的 `source = scene`（场景报告，不进报告列表）。执行后从单个大 suite 的 `TestSuiteResult` 树按场景子 suite 的 `metadata.sceneId` 递归抽取各场景步骤结果，作为套件报告的 `result.scenes[]` 与各场景执行记录的状态来源。
- 执行完成后汇总各场景结果，任一个场景失败则任务整体判 failed，套件报告状态置 failed（整体判定规则见《测试报告详细设计说明书》4.1）。
- 场景执行记录逐场景落库（`api_execution_record`，一场景一条，`execution_record.scene_id` 单值，`report_id` 共享本套件报告 ID）；任务执行记录（`api_scheduled_task_execution.report_id`）关联该套件报告。
- **顶层执行异常（整包构建/启动失败）**：若 `Ryze.start` 返回的顶层 `TestSuiteResult` 携带 `throwable`（引擎兜底捕获而非抛出），本次触发**不生成套件报告**（无 `api_report` 行）、**不写 `api_execution_record`**（无逐场景结果）；仅落一条 `status = failed` 的 `api_scheduled_task_execution`（`error_message` = 异常摘要，按执行记录 2000 长度截断），任务最近执行状态置 `failed`。
- 不提供取消与超时：任务执行等待大 suite 完成后一次性汇总（无单场景级 cancel/timeout 语义）。
- 被圈选场景/模块的删除保护见 4.2。


### 4.4 接口同步任务执行

`task_type = import_swagger` 的任务触发时，直接拉取 `openapi_url` 指定的 OpenAPI/Swagger JSON 文件并增量更新接口定义：

- 复用接口导入引擎（`ApiInterfaceService.importUrl`，含 `ImportSourceFetcher` 拉取与 SSRF 防护）。
- 导入策略同 3.2 定时导入（接口路径+方法匹配，手动修改过的接口不被覆盖）。
- 执行记录关联 `api_import_record` 并回显导入结果（新增/更新/失败数）。
- 保存任务时校验 `openapi_url` 可达性与格式合法性（复用导入引擎的校验能力）。

---


### 6.1 调度器线程池

定时调度器使用独立的 ScheduledExecutorService，线程数 2（一个用于调度，一个用于执行）：

```yaml
api-test:
  scheduler:
    pool-size: 2
    thread-name-prefix: api-test-scheduler-
```

---

**文档结束**

