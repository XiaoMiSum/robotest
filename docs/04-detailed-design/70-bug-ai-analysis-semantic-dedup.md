# 软件测试平台——语义查重

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 缺陷语义查重

- **路径**：`POST /api/project/ai/bugs/dedup`（同步检索，`bug_dedup`）
- **请求体**：`{ "title": "登录超时", "reproSteps": "……可空", "excludeBugId": null }`（编辑既有缺陷时排除自身）
- **响应**：

```json
{
  "semanticDegraded": false,
  "items": [
    {
      "bugId": "0198…",
      "title": "登录接口响应超过30秒后前端报网关超时",
      "status": "active",
      "assigneeName": "李四",
      "similarity": 0.87
    }
  ]
}
```

- **检索规则**：Top-K = `dedup.topK`（默认 5），相似度阈值 = `dedup.similarityThreshold`（默认 0.75）——二者为 `ai_config.settings` 配置键（登记见基础设施 2.2）；范围 = 当前项目 + `status != 'closed'` + `is_deleted = false`；
- **降级模式**：标题分词（按空格/标点切分取长度 ≥ 2 的词）`title ILIKE` OR 匹配，按命中词数排序取前 `dedup.topK` 条，`similarity` 为 `null`（该字段可空，前端降级态不展示相似度徽标）；
- 查重不阻断提交；确认重复的后续流转复用 V1.0 resolution=duplicate 机制，本功能不新增状态。


## 2. 语义查重执行

- 链路：输入拼接（同 2.2 索引对象口径）→ Embedding（10s 超时）→ pgvector 检索 → 阈值过滤 → 组卡片数据；
- 检索 SQL（余弦距离 `<=>`，相似度 = 1 − 距离）：

```sql
SELECT b.id, b.title, b.status, b.assignee_id,
       1 - (e.embedding <=> :queryVec) AS similarity
FROM ai_bug_embedding e
JOIN bug b ON b.id = e.bug_id
WHERE e.project_id = :projectId
  AND e.is_deleted = false
  AND b.is_deleted = false
  AND b.status <> 'closed'
  AND (:excludeBugId IS NULL OR b.id <> :excludeBugId)
ORDER BY e.embedding <=> :queryVec
LIMIT :topK
```

阈值过滤在应用层执行（HNSW 先取 TopK 再过滤，保证索引命中）；`project_id` 前置过滤为强制（数据库规范）；JOIN 与状态条件对 HNSW 属**后过滤**，理论上可能使返回数不足 TopK——依赖 pgvector ≥ 0.8 的迭代索引扫描（`hnsw.iterative_scan`）或适当调大 `hnsw.ef_search` 缓解；千级规模影响可忽略，实施时在部署说明中注明 pgvector 版本要求；
- Embedding 调用失败或降级状态 → 自动切关键词模式并置 `semanticDegraded: true`；
- 性能：检索段（不含向量化）目标 500ms 内——千级向量 + HNSW + 项目过滤余量充足。


