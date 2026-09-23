# 软件测试平台——（分册：用例规划智能推荐）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.5 用例规划智能推荐

- **路径**：`POST /api/project/ai/cases/plan-recommend`（同步，`case_plan_recommendation`）
- **权限**：项目成员即可（附录 A 覆盖度分析无额外角色限定）。
- **请求体**：`{ "text": "需求文本，可空", "requirementIds": [], "excludeCaseNodeIds": [] }`（text / requirementIds 至少一项非空；excludeCaseNodeIds 为当前评审/计划已纳入的用例节点 ID，用于排除重复推荐）
- **响应**：

```json
{
  "semanticDegraded": false,
  "items": [
    {
      "caseNodeId": "0198…",
      "title": "支付失败后订单状态回滚",
      "modulePath": "订单模块/支付流程",
      "matchType": "semantic",
      "score": 0.81,
      "reason": "所选需求涉及支付回调逻辑，该用例覆盖回调失败分支"
    }
  ]
}
```

- `matchType` ∈ `semantic`（语义匹配，降级态为关键词匹配亦归此值）；结果上限 50 条按 score 降序，排除 `excludeCaseNodeIds`；
- 「加入评审/计划」由前端将勾选的 `caseNodeId` 集合解析为所属文档，与既有已选合并去重后预选进「调整用例」关联流程（评审走 `GET/PUT /api/project/reviews/:id/cases`，计划走 `GET/PUT /api/project/plans/:id/cases`），最终关联以用户在既有流程中的确认为准。


### 4.5 用例规划推荐检索

1. **输入归一**：需求条目（标题定界，按选取顺序）与需求文本按条目拆分为**检索块列表**（每块独立参与检索，同 4.3 截断规则）；同时拼接为需求描述块（供理由生成 LLM 输入）；
2. **语义匹配**（可用时）：检索块列表**单次批量向量化**（一次 Embedding 调用，避免逐块多次外部调用）→ 逐块对 ai_case_embedding 独立 TopK（每块 K = `planRecommend.topK` 默认 50）→ **按 nodeId 合并去重、保留最高相似度** → 阈值过滤（`planRecommend.similarityThreshold` 默认 0.7，均为基础设施 2.2 配置键），`matchType = semantic`，score = 相似度。按块独立检索保证多需求条目场景下每个所选需求都有独立召回机会，避免合并单向量语义稀释导致偏科；降级态改为**按块分别 LLM 抽取关键词（每块 ≤ 10 个）合并去重** + 标题 ILIKE（score = 0.6，代码内置常量，仅作展示排序用）；`semanticSearch = unavailable` 或调用异常自动降级并置 `semanticDegraded = true`；
3. **排除已规划**：过滤 `excludeCaseNodeIds`（前端传入当前评审/计划已纳入用例节点 ID），截断 50 条按 score 降序；
4. **理由生成**：一次 LLM 调用为全部结果批量生成一句话 reason（输入需求描述块 + 用例标题清单，输出与输入等长的 reason 数组，长度不匹配时该字段整体置空——理由缺失不影响清单可用；最多 50 条输出较长，读超时功能级覆盖为 60s，同 4.3）。

---


