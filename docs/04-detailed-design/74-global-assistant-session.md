# 软件测试平台——（分册：会话与消息）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.1 会话管理

| 接口 | 路径 | 说明 |
| ---- | ---- | ---- |
| 会话列表 | `GET /api/workspace/ai/conversations` | 当前用户 + 当前空间，按 last_active_at 降序，**游标分页**（见下），返回 `{ "items": [{id, title, lastActiveAt}], "nextCursor": "…" }` |
| 新建会话 | `POST /api/workspace/ai/conversations` | 空会话，title = "新会话"，首条消息后自动更名 |
| 删除会话 | `DELETE /api/workspace/ai/conversations/:id` | 逻辑删除（级联逻辑删除消息） |
| 清空会话 | `DELETE /api/workspace/ai/conversations` | 清空当前用户当前空间全部会话 |
| 消息历史 | `GET /api/workspace/ai/conversations/:id/messages` | 按时间升序全量返回（role=tool 的消息前端渲染为工具调用卡片） |

**会话列表分页规则**：不设总量上限，游标分页滚动获取——参数 `cursor`（不透明游标，可空表示首页）+ `size`（默认 20，上限 50）；排序与游标锚点为 `(last_active_at DESC, id DESC)`（id 决胜，UUID v7 时序性保证稳定），实现为键集查询 `WHERE (last_active_at, id) < (:cursorTime, :cursorId)`，命中既有索引 `idx_conv_user_ws`（2.1.1）。`nextCursor` 为空表示无更多。选择键集而非页码/偏移：排序键 `last_active_at` 随会话活跃动态前移，偏移分页在滚动加载过程中会产生重复与漏项；键集分页仅可能漏掉「加载期间被顶到列表头部的旧会话」，该场景由前端本地置顶补偿（见 5.2），无一致性问题。

会话归属校验：非本人会话，或会话 `workspace_id` 与 `X-Active-Workspace` 不一致时，一律按会话不存在处理，返回 3001（资源不存在段，不暴露存在性）。


### 3.2 发送消息（SSE）

- **路径**：`POST /api/workspace/ai/conversations/:id/messages`（`text/event-stream`，`assistant_chat`）
- **请求体**：

```json
{
  "content": "帮我在项目X创建一个P1缺陷，标题为登录超时",
  "pageContext": {
    "projectId": "0198…",
    "documentId": "0198…",
    "selectedNodeId": "0198…"
  },
  "modelId": null
}
```

`pageContext` 可空；脑图编辑页发送时由前端上下文桥自动注入。`modelId` 可选：用户在助手输入区选择的对话模型标识（交互式功能通用约定，缺省或失效回退系统默认模型，见基础设施 3.1 / 4.11）；同一会话内允许逐条消息切换模型，Function Calling 循环内的多次上游调用使用同一模型。

- **SSE 事件**（在基础设施统一帧格式上扩展事件类型）：

| event | data | 说明 |
| ---- | ---- | ---- |
| delta | `{"content": "…"}` | 回复文本增量 |
| tool_call | `{"toolName": "query_bugs", "summary": "查询未关闭的致命缺陷"}` | 只读工具执行通知（前端渲染过程卡片） |
| confirm_required | `{"confirmToken": "…", "toolName": "create_bug", "preview": {…}, "expiresAt": "…"}` | 写操作确认请求，**本轮 SSE 随即以 done 结束** |
| minder_commands | `{"commands": […], "documentId": "…"}` | 对话式编辑翻译结果（DSL），交前端预览执行 |
| done | `{"messageId": "…"}` | 本轮回复完成 |
| error | `{"code": 6002, "message": "…"}` | 失败 |

> 扩展事件类型遵循基础设施 3.1 的自定义帧约定：`useAiStream()` 对未识别事件原样透传，由 `useAssistantStream.ts` 负责解析（见 5.1）。


