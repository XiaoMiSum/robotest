# 软件测试平台——AI评审与测试计划辅助详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对 AI 能力域中的**评审辅助与测试计划辅助功能**进行详细设计：评审一键检查、评审摘要生成、遗漏测试点分析、执行顺序推荐、用例规划智能推荐，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.5（AI 辅助评审与覆盖度分析）与 3.8（测试计划与风险评估）。依赖的向量基建（ai_case_embedding 表、写入与重建机制）见《缺陷智能分析与向量检索详细设计说明书》；异步任务框架、SSE 帧格式、错误码见《AI 基础设施详细设计说明书》。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》（3.5、3.8、6.1 附录 A）
- 《软件测试平台概要设计说明书》（3.2、4.11、4.12）
- 《AI 基础设施详细设计说明书》
- 《项目工作区详细设计说明书》（归档，评审/计划快照模型）

---


## 2. 数据设计

### 2.1 数据库表变更

**无新增表**。全部结果持久化复用 `ai_analysis_task.result`（JSONB）快照；涉及两处既有结构变更：

1. `ai_analysis_task.type` 枚举扩展一项：

| type | 说明 | target_id |
| ---- | ---- | ---- |
| plan_order_recommend | 执行顺序推荐结果快照（同步计算，创建即终态） | 计划 ID |

（该枚举扩展同步回补至《AI 基础设施详细设计说明书》2.1.3。）

2. `test_plan` 表增列 `snapshot_synced_at`（TIMESTAMP，NULL，默认空）：记录计划快照最近一次同步成功时间，由既有的计划同步接口（`POST /api/project/plans/:id/sync`）在同步事务内写入当前时间；创建计划关联快照时同样写入。该列仅服务于 4.4 失效判定，不建索引（仅按主键单行读取）。

### 2.2 任务结果结构定义（ai_analysis_task.result）

#### 2.2.1 评审检查（type=review_check，target=评审 ID）

```json
{
  "checkedCaseCount": 120,
  "totalCaseCount": 200,
  "skippedBatches": 0,
  "items": [
    {
      "snapshotNodeId": "0198…",
      "dimension": "missing_precondition",
      "suggestion": "该用例缺少前置条件，建议补充账号状态与入口页面"
    }
  ]
}
```

`dimension` ∈ `missing_precondition`（缺前置）/ `vague_step`（步骤笼统）/ `missing_expected`（缺预期）/ `priority_conflict`（相似用例优先级冲突）。`skippedBatches` 为重试后仍失败被跳过的批次数（见 4.1），前端非 0 时提示「部分用例未完成检查」。分批执行中**每批完成即累计写入** items 与 checkedCaseCount——任务被取消时已产出部分仍可查看（SRS 3.5.1；基础设施 3.5.2 已为 review_check 定义取消保留豁免）。

#### 2.2.2 评审摘要（type=review_summary，target=评审 ID）

```json
{
  "statistics": {
    "totalCases": 200, "passCount": 150, "failCount": 30, "pendingCount": 20,
    "passRate": 75.0,
    "dimensionDist": { "missing_precondition": 3, "vague_step": 8 },
    "failByDocument": [ { "documentName": "登录用例集", "failCount": 12 } ]
  },
  "summaryMarkdown": "## 评审总结\n……"
}
```

statistics 由 SQL 精确计算（不依赖 LLM）；重复生成覆盖本记录（同一评审仅保留最新一条 success 记录，旧记录逻辑删除）。

#### 2.2.3 执行顺序推荐（type=plan_order_recommend，target=计划 ID）

```json
{
  "planSyncedAt": "2026-07-30T09:00:00Z",
  "weights": { "w1": 0.5, "w2": 0.3, "w3": 0.2 },
  "items": [
    {
      "snapshotNodeId": "0198…",
      "order": 1,
      "score": 0.87,
      "factors": { "relatedBugCount": 5, "priorityWeight": 1.0, "moduleBugDensity": 0.42 },
      "reason": null
    }
  ]
}
```

`planSyncedAt` 记录计算时刻 `test_plan.snapshot_synced_at` 的值（2.1 增列），用于失效判定（见 4.4）；`reason` 按需生成后回填。

---



均为项目级接口。


### 3.6 错误码补充

本文档新增一个 AI 段错误码（已回补至基础设施 3.6 总表）：

| 错误码 | 说明 | HTTP 状态 |
| ---- | ---- | ---- |
| 6012 | 目标对象状态不允许该 AI 操作（评审已完成时发起检查、评审未「已完成」生成摘要、计划未关联快照发起顺序推荐；基础设施总表语义另含缺陷聚类的「项目无可分析缺陷」场景，见《缺陷智能分析与向量检索详细设计说明书》3.3.1） | 409 |

> 不复用 6006——其语义限定为「**任务**不存在或任务状态不允许」，本码面向评审/计划等业务对象状态校验。其余错误沿用基础设施 3.6（2001 无权限、6005 同类任务进行中等）。

---


### 5.1 文件与组件

| 文件 | 说明 |
| ---- | ---- |
| `components/project/ReviewAiCheckPanel.vue` | 评审详情「AI 检查」抽屉（640px、透明遮罩不压暗画布、点击空白关闭）：发起按钮（仅发起人，待评审/评审中可发起，已完成只读展示历史结果）、批次进度条（宽 4px）、建议列表（维度过滤、点击定位高亮）、取消任务、底部操作行（右侧对齐） |
| `components/project/ReviewAiSummary.vue` | 评审摘要抽屉（640px、透明遮罩不压暗画布、点击空白关闭）：操作行（右侧对齐，`AiModelSelect` + 复制 + 生成/停止）+ statistics 卡片区（即时渲染）+ 流式 Markdown 总结（MarkdownView 复用） |
| `components/project/MissingPointsPanel.vue` | 用例模块页「遗漏测试点分析」抽屉（640px、常驻挂载、关闭仅隐藏，透明遮罩 4.2）：三态输入（关键词/文本/条目选择器复用 RequirementSelector）+ 操作行（右侧对齐，分析中虚假进度条占位 + [取消] + [开始分析]）+ 结果清单（勾选）+「转用例生成」按钮（含目标文档选择，规则见 3.3）；**打开时自动带入当前文档关联条目**（同《智能用例生成》3.1.6，见 3.3 前端预填）；关闭抽屉仅隐藏不 abort，watch `docId` 切换文档时中断分析并重置（`docs/05-interaction-design/01-readme.md` §4.2 会话保持） |
| `components/project/PlanOrderRecommend.vue` | 计划详情「执行顺序推荐」标签页：按指数排序列表（分值、因子明细展开、按需生成理由）+ stale 重算提示 + 脑图序号徽标联动 |
| `components/project/CasePlanRecommendDialog.vue` | 用例规划推荐抽屉（评审/计划共用，640px、透明遮罩不压暗画布、点击空白关闭）：需求池（多选，[+ 选择需求] 选取）+ 需求文本输入 + 操作行（右侧对齐，推荐中虚假进度条占位 + [取消] + [开始推荐]）+ 结果勾选清单 +「加入评审/计划」（携带勾选用例进入既有关联流程） |
| `services/project.ts` / `types/index.ts` | 3.1–3.5 接口封装与类型 |


### 5.2 交互要点

- 检查/摘要入口仅评审发起人可见（前端按当前用户 = initiator 判断，后端强校验兜底）；顺序推荐入口仅计划负责人/执行人可见；
- 摘要生成为交互式功能：`ReviewAiSummary` 的生成/重新生成入口旁内嵌公共组件 `AiModelSelect`（对话模型选择器，基础设施 5.1 / `docs/05-interaction-design/01-readme.md` §2.8），所选 `modelId` 随 3.2.1 请求提交；其余功能固定默认模型，不展示选择器；
- 检查任务进行中允许离开页面，返回后面板轮询恢复展示（任务状态即真相源）；
- 遗漏分析「转用例生成」：勾选 points 拼接为需求文本（title + description 列表），按 3.3 说明的目标文档选择规则确定跳转目标，路由跳转至该文档脑图页并透传文本预填生成面板；
- 遗漏分析（3.3）与用例规划推荐（3.5）为同步长调用（后端 LLM 读超时功能级放宽至 60s，见 4.3/4.5），`services/ai.ts` 中这两个接口的请求超时单独配置为 70s（默认超时不足会先于后端中断），调用期间面板展示持续加载态并提供取消（abort）按钮；
- 用例规划推荐「加入评审/计划」：评审详情页将勾选 `caseNodeId` 经 `getCaseDetail` 解析所属文档，与 `getReviewPlannedCases` 已选合并去重后预选进 CaseSelector；计划详情页同逻辑（`getPlanPlannedCases`）。打开抽屉前取当前已纳入用例节点 ID 集作为 `excludeCaseNodeIds` 传入推荐接口。
- 顺序推荐序号徽标与列表视图双向联动（点击列表项脑图定位；`semanticDegraded` / stale 状态均以顶部提示条呈现）。


### 5.3 单元测试点（C8）

- 评分归一化与决胜规则纯函数（后端单测：全 0 边界、并列决胜）；
- 检查结果幻觉过滤（后端：不存在的 snapshotNodeId 被剔除）；
- 前端：遗漏点转生成的文本拼接与目标文档预选规则（3.3）、推荐列表因子展开渲染、stale 提示分支。

---


## 6. 实施说明

- **数据库迁移**：无新表；`test_plan` 增列 `snapshot_synced_at`（见 2.1，ALTER 语句写入 `v1.1.sql`，遵循基础设施文档第 6 章脚本版本化约定；存量行保持 NULL，无回填）；其余依赖基础设施四表与向量表已就绪；
- **实施梯队**：评审摘要属梯队一；一键检查、遗漏分析（关键词版）属梯队二；遗漏分析语义升级、顺序推荐、用例规划推荐属梯队三；
- **依赖**：无新增依赖。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `62-ai-review-overview.md` | 前言、1. 引言、2. 数据设计、3.6 错误码补充、5.1 文件与组件、5.2 交互要点、5.3 单元测试点、6. 实施说明、3. 接口详细设计 |
| 评审一键检查 | `63-ai-review-one-click-check.md` | 3.1 评审一键检查、4.1 评审检查任务 |
| 评审摘要 | `64-ai-review-summary.md` | 3.2 评审摘要、4.2 评审摘要生成 |
| 遗漏测试点分析 | `65-ai-review-missing-points.md` | 3.3 遗漏测试点分析、4.3 遗漏测试点分析 |
| 执行顺序推荐 | `66-ai-review-execution-order.md` | 3.4 执行顺序推荐、4.4 执行顺序推荐评分 |
| 用例规划智能推荐 | `67-ai-review-case-plan.md` | 3.5 用例规划智能推荐、4.5 用例规划推荐检索 |
