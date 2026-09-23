# 软件测试平台——（分册：执行顺序推荐）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.4 执行顺序推荐

#### 3.4.1 计算推荐

- **路径**：`POST /api/project/ai/plans/:id/order-recommend`
- **响应**：`{ "taskId": "0198…", "result": { …2.2.3 结构… } }`（同步计算，立即返回结果）
- **校验**：仅计划负责人或计划执行人（附录 A，其余角色 2001）；计划需已关联快照（6012）。重复计算覆盖旧记录。

#### 3.4.2 查询推荐结果

- **路径**：`GET /api/project/ai/plans/:id/order-recommend`
- **响应**：`{ "stale": false, "result": { … } }`；`stale = true` 表示计划快照在计算后重新同步过，需重算（见 4.4）；权限同 3.4.1，其余角色返回 2001。

#### 3.4.3 生成推荐理由

- **路径**：`POST /api/project/ai/plans/:id/order-reason`（同步，`plan_order_reason`）
- **请求体**：`{ "snapshotNodeId": "0198…" }`
- **响应**：`{ "reason": "该用例历史关联5个缺陷且属于缺陷密度最高的支付模块，建议优先执行" }`
- **处理**：LLM 基于该条 factors 数据生成文字理由并回填 result 对应 item（缓存复用，重复请求直接返回已生成理由）；LLM 不参与排序。


### 4.4 执行顺序推荐评分（确定性计算）

**评分模型**（权重取 `settings` 键 `planOrder.weights`，默认 `{w1:0.5, w2:0.3, w3:0.2}`）：

```
score(case) = w1 · norm(relatedBugCount) + w2 · priorityWeight + w3 · norm(moduleBugDensity)
```

| 因子 | 计算 |
| ---- | ---- |
| relatedBugCount | `bug.related_case_id = 快照节点.original_node_id`（计划快照节点表既有字段）的未删除缺陷数（SQL 聚合） |
| priorityWeight | P0=1.0 / P1=0.75 / P2=0.5 / P3=0.25 / 无=0.25 |
| moduleBugDensity | 快照节点所属文档对应模块的缺陷数 ÷ 该模块用例数；分子 = `bug.module_id` 属该模块（含子孙模块）的未删除缺陷数，分母 = 该模块（含子孙模块）下现势未删除 `type=case` 节点数；分子分母均取**现势口径**，同一次计算内一次性查询取数，保证结果可复现 |

- `norm()` 为项目内 min-max 归一化（全 0 时取 0）；纯 SQL + 内存计算，结果确定可复现；
- 同分并列按 priorityWeight、relatedBugCount 依次决胜，仍并列按快照 sort_order；
- **失效判定**：读取时比较 `result.planSyncedAt` 与 `test_plan.snapshot_synced_at`（2.1 增列；计算时将当时的列值——含 NULL——记入 planSyncedAt），二者不相等返回 `stale: true`，前端提示重算；不做自动重算；
- **脑图标注**：计划详情脑图以徽标渲染推荐序号（badges.ts 扩展序号徽标，数据来自 result.items 的 snapshotNodeId → order 映射，仅前端渲染态，不写入节点数据）。


