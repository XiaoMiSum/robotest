# 软件测试平台——（分册：同步建议）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.3 同步建议类接口

#### 3.3.1 优先级推荐

- **路径**：`POST /api/project/ai/cases/priority-recommend`
- **请求体**：`{ "title": "支付失败重试", "ancestorTitles": ["订单模块", "支付流程"] }`
- **响应**：`{ "priority": "P0", "source": "rule" }` 或 `{ "priority": "P2", "source": "llm" }`
- **处理**：关键词规则命中直接返回（不经 LLM、不计限流）；未命中发起 LLM 同步调用（超时 5s，功能级覆盖网关同步调用默认读超时 15s——推荐场景时效价值高于成功率），失败返回 `{ "priority": null }`——前端静默忽略，不提示错误（非侵入原则）。

#### 3.3.2 脑图指令翻译（DSL）

- **路径**：`POST /api/project/ai/minder/dsl-translate`
- **请求体**：`{ "documentId", "instruction": "把所有P2以下的登录相关用例标为高亮", "selectedNodeId": null }`
- **响应**：

```json
{
  "commands": [
    { "selector": { "types": ["case"], "priorities": ["P3"], "keyword": "登录" },
      "action": { "type": "highlight", "params": {} } }
  ],
  "ambiguous": false,
  "clarification": null
}
```

- `ambiguous = true` 时 `commands` 为空，`clarification` 为需用户改写的原因说明（LLM 声明歧义或输出未过校验时统一走此分支，不做模糊执行）。

---


