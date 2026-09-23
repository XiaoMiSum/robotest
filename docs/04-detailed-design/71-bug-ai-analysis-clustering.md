# 软件测试平台——聚类分析

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.3 缺陷聚类分析

#### 3.3.1 发起聚类任务

- **路径**：`POST /api/project/ai/bugs/clustering`
- **响应**：`{ "taskId": "0198…" }`
- **约束**：同项目同时仅一个进行中任务（6005）；范围为当前项目全部未关闭缺陷（active/resolved/rejected）；缺陷数为 0 时直接返回 6012（目标对象状态不允许该 AI 操作）提示无可分析数据。

#### 3.3.2 查询聚类结果

- **路径**：`GET /api/project/ai/bugs/clustering/latest`
- **响应**：最近一次任务（含 status/progress/result，result 结构见 2.3）；无任务返回 `null`。手动刷新 = 重新发起 3.3.1（新任务成功后覆盖展示）。


### 4.3 聚类分析任务

任务执行步骤（`bug_clustering`，进度按阶段推进）：

1. **取数**（10%）：项目内未关闭缺陷 + 对应向量；无向量的缺陷现场补建（失败则计入 `unclustered`）；
2. **聚类**（40%）：贪心增量聚类——按创建时间遍历，与已有簇中心余弦相似度 ≥ `clustering.similarityThreshold`（settings 键，默认 0.82）则并入并更新中心（**归一化向量求均值后再归一化**，保持模长为 1、阈值语义稳定），否则新建簇；单缺陷簇归入 `unclustered`；确定性算法（固定遍历序），结果可复现，不依赖 LLM；
3. **归纳**（40% → 90%）：按簇大小降序仅对前 `clustering.maxLabeledClusters`（settings 键，默认 30）个簇执行 LLM 归纳——取代表样本（离中心最近 5 条的标题+截断描述）归纳 `label` 与 `rootCause`（每簇一次调用，批间更新进度并检查取消标记），防止碎簇导致调用量失控；超出上限或 LLM 失败的簇保留 `label = "未命名主题 N"` 且快照 `labeled = false`（2.3），前端明示「标签生成失败」；
4. **落库**（100%）：组装 2.3 结构 + severity/module 维度 SQL 聚合，写 `result`。

聚类为只读洞察，不修改缺陷数据；结果附 `generatedAt` 供前端展示时效。

**降级模式（semanticSearch 非 available）**：当语义检索不可用（Embedding 未配置或全量重建进行中，见基础设施 4.10）时，聚类退化为**关键词归纳**，保证分析结果仍可产出（`docs/05-interaction-design/01-readme.md` §4.2 降级提示条明示该模式）：

1. **取数**（10%）：项目内未关闭缺陷，仅取标题（供分词），不加载/补建向量；
2. **聚类**（40%）：贪心增量聚类（确定性算法，固定遍历序，结果可复现，不依赖 LLM）——标题分词取词集：中文（CJK 汉字）按单字切分（单字是中文最小语义单元，整句切分会使重叠系数恒为 0），非中文字母数字按整词保留（≥2 字符，过滤单字噪声）（`AiTextUtils#tokenizeKeywordsForClustering`，与查重降级的整词口径分离避免互相污染）；按创建时间遍历，与已有簇词集的重叠系数 `|A∩B| / min(|A|,|B|)` ≥ `clustering.keywordSimilarityThreshold`（settings 键，默认 0.2）则并入（簇词集取并集更新），否则新建簇；单缺陷簇归入 `unclustered`；
3. **归纳**（40% → 90%）：同向量模式——按簇大小降序仅对前 `clustering.maxLabeledClusters` 个簇执行 LLM 归纳，代表样本取与簇词集重叠系数最高的 5 条标题（tie-break 按缺陷 ID 升序保证确定性）；LLM 失败保留 `label = "未命名主题 N"` 且快照 `labeled = false`（2.3），前端明示「标签生成失败」；
4. **落库**（100%）：快照结构（2.3）不变，前端依据全局 `semanticSearch` 状态（基础设施 4.10）显示降级提示条，不依赖快照字段。

降级模式为纯本地计算（零额外 LLM 调用，仅归纳沿用既有调用），同样只读不修改缺陷数据；阈值与向量模式分离（`clustering.similarityThreshold` 为余弦相似度，重叠系数与其语义不可互换）。


