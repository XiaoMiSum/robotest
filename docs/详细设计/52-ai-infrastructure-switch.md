# 软件测试平台——（分册：能力开关）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `51-ai-infrastructure-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.2 能力开关接口

#### 3.2.1 查询 AI 可用性

- **路径**：`GET /api/workspace/ai/status`
- **说明**：前端进入业务布局后调用并缓存，据此显隐全部 AI 入口；**全局能力，不依赖 `X-Active-Workspace` 上下文**（无工作空间时同样返回），AI 配置变更后由用户刷新页面感知。
- **响应**：

```json
{
  "enabled": true,
  "semanticSearch": "available",
  "chatModels": [
    { "id": "018f...", "name": "DeepSeek-V3", "isDefault": true },
    { "id": "018e...", "name": "GPT-4o", "isDefault": false }
  ]
}
```

- `enabled`：AI 总开关（配置存在且 `enabled = true`）；`false` 时前端隐藏全部 AI 入口。
- `semanticSearch`：语义检索状态，`available` / `degraded`（向量重建中，降级关键词匹配）/ `unavailable`（Embedding 未配置）。状态计算见 4.10。
- `chatModels`：当前已启用的对话模型清单（仅 `id` / 显示名 / 是否默认，**不下发地址、模型名与密钥等配置细节**），供交互式功能的模型选择器渲染；`enabled = false` 时不返回。清单为空数组时视同 AI 不可用（无可用对话模型）。


### 4.10 能力开关与降级状态计算

`GET /api/workspace/ai/status` 的状态由 `AiConfigService` 计算并缓存（30 秒 TTL）。状态为**全局计算**：AI 配置、对话模型、Embedding 任务均不归属具体工作空间，故本接口不读取 `X-Active-Workspace` 上下文，无工作空间时同样返回全局开关状态（前端在 `/workspaces` 空间列表页据此保持悬浮入口可见）：

| 条件 | enabled | semanticSearch |
| ---- | ---- | ---- |
| 无有效配置 或 `enabled=false` 或 `AI_SECRET_KEY` 缺失 或 无已启用对话模型 | false | —（不返回） |
| 已启用，Embedding 组未配置 | true | unavailable |
| 已启用，存在进行中的 `embedding_rebuild` 任务 | true | degraded |
| 已启用，最近一次 `embedding_rebuild` 任务为 `failed` / `cancelled`（向量数据不完整） | true | degraded |
| 已启用，Embedding 组配置完整、无进行中重建任务且最近一次重建非 `failed` / `cancelled` | true | available |

- 语义检索类接口（查重、聚类、语义匹配）在 `degraded`/`unavailable` 状态下自动切换关键词模式，响应中附 `"semanticDegraded": true`（业务码 6010 语义，随正常数据返回），前端明示降级；
- 保存配置时若 Embedding 模型或维度变更：自动创建 `embedding_rebuild` 任务（type=embedding_rebuild，target 为空，逐项目分批重建），任务完成前维持 `degraded`。重建任务 `failed` / `cancelled`（含 AI 总开关关闭的联动取消，见 4.6）时向量数据不完整，维持 `degraded` 直至管理员经 3.3.5 重试成功。重建任务的执行逻辑（列定义变更、分批向量化）见《缺陷智能分析与向量检索详细设计说明书》。


