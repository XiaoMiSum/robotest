# 软件测试平台——（分册：遗漏测试点分析）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `62-ai-review-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.3 遗漏测试点分析

- **路径**：`POST /api/project/ai/cases/missing-points`（同步，`missing_point_analysis`）
- **请求体**：

```json
{
  "keywords": ["登录", "验证码"],
  "text": "直接粘贴的需求文本，可空",
  "requirementIds": ["0198…"]
}
```

三种输入（keywords / text / requirementIds）至少一项非空。

- **权限**：项目成员即可（附录 A 覆盖度分析无额外角色限定）。
- **响应**：

```json
{
  "semanticDegraded": false,
  "points": [
    {
      "title": "短信验证码超时后重新发送",
      "description": "需求提及验证码有效期5分钟，现有用例未覆盖超时重发场景",
      "suggestedModulePath": "登录模块/验证码登录",
      "relatedCaseTitles": ["验证码登录成功", "验证码错误提示"]
    }
  ]
}
```

- **说明**：分析范围限当前项目；不自动创建任何用例；「一键转生成」由前端将勾选的 points 拼接为需求文本，跳转脑图页并携带至《智能用例生成》3.2.1 入口。勾选点可能归属不同模块：面板「转用例生成」时要求用户选择一个目标文档（默认预选勾选点中出现次数最多的 `suggestedModulePath` 所对应文档；路径无法匹配到现有文档时不预选），全部勾选点文本拼接后透传至该文档脑图页。
- **前端预填**：面板打开时调用文档关联查询（《智能用例生成》3.1.6 `GET /api/project/documents/:docId/requirements`，`:docId` 为当前脑图文档）自动带入关联条目至 `requirementIds`，作为默认上下文，用户可临时改选；加载失败提示但不阻断输入。


### 4.3 遗漏测试点分析（关键词版 → 语义升级）

两阶段检索 + LLM 比对：

1. **需求输入归一**：keywords / text / 需求条目内容合并为需求描述块；text 与条目内容超预算时截断（同生成类裁剪规则）；
2. **候选用例检索**：
   - 关键词模式（梯队二 / 降级态）：按 keywords（text 场景由 LLM 先抽取 ≤ 10 个关键词，一次同步调用）对 case 节点标题 `ILIKE` 匹配，每词取前 30 条；
   - 语义模式（梯队三，`semanticSearch = available`）：需求描述块整体向量化 → ai_case_embedding TopK（K = `missingPoint.topK`，默认 100，基础设施 2.2 配置键；`project_id` 前置过滤）；
3. **LLM 比对**：输入 = 需求描述块 + 候选用例（标题 + 模块路径清单）→ 输出遗漏点数组（结构校验：title ≤ 200、suggestedModulePath 须为输入中出现过的模块路径或空、points ≤ 30）；候选集大、输出较长，该调用读超时按功能级覆盖为 60s（网关同步调用默认 15s 不足，覆盖机制同《智能用例生成》3.3.1 优先级推荐的功能级覆盖先例）；
4. `relatedCaseTitles` 由 LLM 标注后与候选清单比对过滤（防幻觉）。


