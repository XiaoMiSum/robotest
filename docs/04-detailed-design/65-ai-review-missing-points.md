# 软件测试平台——遗漏分析

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 遗漏分析（覆盖确认阶段作业）

- **定位**：需求工作流 `coverage`（覆盖确认）阶段的阶段作业，type = `missing_point_analysis`，异步执行（`ai_analysis_task` 执行形态，见基础设施 2.1.3）；产出为 gap（遗漏点）提案，处置完毕即满足该阶段出口证据之一（总览 2.3.2）。
- **发起**：`POST /api/project/requirements/:id/stage-jobs`（《需求工作流》4.1；`extraText` 可选，附加需求文本仅本次作业使用）；阶段推进进入 coverage 时按总览 2.3.3 尽力自动发起；进度/取消/重试复用异步任务通用接口（55）。
- **权限**：阶段操作权限 + 项目成员即可（SRS 3.5.3 无额外角色限定）；AI 未启用发起返回 1000013001。
- **同步端点移除**：`POST /api/project/ai/cases/missing-points` 不再存在——分析入口唯一收敛到需求工作流工作台，脑图用例模块页的「遗漏分析」抽屉（MissingPointsPanel）随之下线，「转用例生成」改为遗漏点提案的 `to_case` 处置（《需求工作流》5.5）。
- **产出物化**：作业 success 后同事务将结果逐点物化为 gap 提案（`stage = coverage`，`task_id` 关联作业）：同 `(stage, kind)` 既有 pending / expired gap 提案置 `superseded`；与已 rejected / accepted 提案指纹（规范化 title 的 SHA-256）重复的点跳过产出（总览 2.3.3 规则 5 / 2.1.3）。

## 2. 作业执行

候选用例范围 + LLM 比对：

1. **需求输入归一**：条目标题 + 内容与 `extraText` 合并为需求描述块；超预算时截断（同生成类裁剪规则，见《AI 生成用例》5）；
2. **候选用例获取**：范围 = 该条目**血缘用例**（artifact_type = case，generated / manual 均计）∪ **血缘文档**（artifact_type = document）下的全部 case 节点——手动创建的用例经血缘文档或手动关联纳入范围；组装模块路径（目录树 + 文档节点 → `AiModuleTreeSupport.buildModulePaths`），候选清单按 token 预算截断（超出 `CANDIDATE_TOKEN_BUDGET` 静默丢弃后续）；血缘为空时候选为空集，按「全部未覆盖」处理，产出上限不变；
3. **LLM 比对**：输入 = 需求描述块 + 候选用例（标题 + 模块路径清单）→ 输出遗漏点数组（结构校验：title ≤ 200、`suggestedModulePath` 须为输入中出现过的模块路径或空、points ≤ 30）；候选集大、输出较长，该调用读超时按功能级覆盖为 300s（覆盖机制同《AI 生成用例》优先级推荐的功能级覆盖先例）；异步任务执行不设端到端时限，但网关单次调用超时仍按此功能级配置；
4. `relatedCaseTitles` 由 LLM 标注后与候选清单比对过滤（防幻觉，剔除不存在的标题）；
5. **提案 payload**：`{ title, description, suggestedModulePath, relatedCaseTitles }`（结构见总览 2.2.2）。

## 3. 遗漏点处置

遗漏点提案的处置为三选一，接口见《需求工作流》5.5（`POST /api/project/requirements/proposals/:id/dispose`）：

| 处置 | 行为 |
| ---- | ---- |
| 转为用例（`to_case`） | 与 `case_generation` 作业相同的落位参数（`targetMode` / `documentId`）发起新的用例设计作业，成功后提案置 `accepted`（`resolution = to_case`）；`text` 默认由前端拼接该点 `title + description`，用户可编辑 |
| 标记不覆盖（`wontfix`） | `reason` 必填，提案置 `rejected`（`resolution = wontfix`），留痕 |
| 忽略 | 前端本地隐藏，提案保持 pending（保留待处理），仍计入出口证据；确需通过时经阶段 `skip` 留痕绕过 |

- 处置权限 = 阶段操作权限；`to_case` 另需 `case_generation` 作业权限（目标文档编辑权限）；
- gap 提案**无独立的采纳落库动作**（不创建用例节点），`to_case` 的落库发生在后续用例生成作业的提案采纳链路（《AI 生成用例》2）。

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-09-23 | 初版起草 |
| V1.0 | 2026-09-30 | 功能名称统一改为「遗漏分析」 |
| V1.0 | 2026-09-30 | 请求体新增 `modelId`，模型选择作用于本次分析 |
| V1.0 | 2026-09-30 | 候选检索改为文档级全量获取，移除关键词检索与语义升级路径 |
| V1.0 | 2026-09-30 | 比对调用读超时 60s 提升至 300s |
| V1.0 | 2026-10-01 | 改为覆盖确认阶段作业（异步，发起收敛至需求工作流），同步端点与脑图遗漏分析抽屉下线，产出改为 gap 提案，候选范围改为条目血缘用例 ∪ 血缘文档用例 |
