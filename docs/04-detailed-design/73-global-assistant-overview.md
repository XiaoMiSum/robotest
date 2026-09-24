# 软件测试平台——全局智能助手详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对 AI 能力域中的**全局智能助手（ChatBot）**进行详细设计：会话管理、自然语言查询（只读工具）、快捷操作执行（写工具 + 确认机制）、对话式脑图编辑、平台使用指引，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.7。助手为**工作空间级**能力（查询范围 = 当前工作空间内用户可见数据）；对话式脑图编辑的 DSL 定义与前端执行器复用《智能用例生成与脑图智能编辑详细设计说明书》4.4；网关、SSE、限流审计见《AI 基础设施详细设计说明书》。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》（3.7、4.2）
- 《软件测试平台概要设计说明书》（4.10、8）
- 《AI 基础设施详细设计说明书》
- 《智能用例生成与脑图智能编辑详细设计说明书》（DSL 与挂载执行器）

---


## 2. 数据设计

### 2.1 数据库表设计

全部新表遵循平台规范：`id`（框架默认 UUID 策略）、`created_at`、`updated_at`、`is_deleted`，禁止物理外键（C5）；索引遵循 C9。

#### 2.1.1 助手会话表（ai_conversation）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 会话 ID |
| user_id | UUID | NOT NULL | 归属用户（仅本人可见） |
| workspace_id | UUID | NOT NULL | 归属工作空间（跨空间隔离） |
| title | VARCHAR(100) | NOT NULL | 会话标题（首条用户消息前 30 字自动生成） |
| last_active_at | TIMESTAMP | NOT NULL | 最后活跃时间（列表排序） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_conv_user_ws` (user_id, workspace_id, last_active_at DESC)

#### 2.1.2 助手消息表（ai_message）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 消息 ID |
| conversation_id | UUID | NOT NULL | 所属会话 |
| role | VARCHAR(10) | NOT NULL | user / assistant / tool |
| content | TEXT | NULL | 文本内容（tool 消息为工具执行结果 JSON 文本） |
| tool_calls | JSONB | NULL | assistant 消息发起的工具调用载荷（name/arguments/callId 数组） |
| tool_call_id | VARCHAR(64) | NULL | tool 消息对应的调用 ID |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_msg_conversation_id` (conversation_id)

> 会话与消息内容仅本人可见；审计权限用户只能经 `ai_invocation_log` 查看调用元数据（SRS 4.2）。保留期清理按 `conversationRetentionDays` 执行（同基础设施审计清理任务）。

### 2.2 写操作确认令牌（Redis，不落库）

```
key:   ai:confirm:{token}          # token = UUID
value: { userId, workspaceId, conversationId, assistantMessageId, toolCallId, toolName, arguments, createdAt }
TTL:   assistantConfirmTimeoutSeconds（默认 300 秒）
```

- 一次性消费：approve / cancel / 超时任一发生即删除；令牌不存在或已消费返回 1000013011（基础设施 3.6 增补）；
- `assistantMessageId` / `toolCallId` 标识令牌对应的 assistant 消息与工具调用，approve / cancel 落库 tool 消息时据此回填 `tool_call_id`，保证会话历史回填 LLM 时消息序列完整；
- `workspaceId` 供确认时的空间上下文校验（见 3.3.1）。

---



均为工作空间级接口（`/api/workspace/ai/**`，头 `Authorization` + `X-Active-Workspace`）。


### 2.3 文件与组件

| 文件 | 说明 |
| ---- | ---- |
| `components/assistant/AssistantFab.vue` | 右下角悬浮按钮（BusinessLayout 挂载，`aiEnabled` 控制显隐；管理端布局不挂载；`aiEnabled` 为全局开关，未选择工作空间时按钮仍显示） |
| `components/assistant/AssistantPanel.vue` | 对话面板（抽屉式）：会话列表侧栏 + 消息流 + 输入区；支持最小化保持会话 |
| `components/assistant/MessageItem.vue` | 消息渲染：Markdown（链接白名单过滤）、工具过程卡片、确认卡片（操作明细表格 + 确认/取消 + 倒计时）、DSL 预览执行入口 |
| `components/assistant/useAssistantStream.ts` | 基于 `useAiStream()` 扩展多事件解析（tool_call / confirm_required / minder_commands） |
| `components/assistant/useConversationList.ts` | 会话列表游标分页组合式函数（触底加载、`nextCursor` 终止、id 去重、本地置顶、空间切换重置，见 2.4/2.5） |
| `stores/assistantContext.ts` | 页面上下文桥（4.4） |
| `services/workspace.ts` / `types/index.ts` | 3.1–3.3 接口封装与类型 |


### 2.4 交互要点

- 面板随工作空间切换重置会话列表（会话按空间隔离）；流式回复期间输入框禁用并显示停止按钮（中断即断开 SSE，服务端取消上游）；
- **未选择工作空间**（如 `/workspaces` 空间列表页）：`aiEnabled` 为全局开关（`GET /api/workspace/ai/status` 不依赖工作空间上下文），悬浮按钮仍显示；打开面板时无会话上下文，渲染「请先选择工作空间」引导态，会话列表与输入区禁用，不发起会话/消息请求；
- **模型选择器**：输入区工具条内嵌公共组件 `AiModelSelect`（对话模型选择器，基础设施 2.3 / `docs/05-interaction-design/01-readme.md` §2.8），所选 `modelId` 随 3.2 发送消息请求提交；切换即时生效于后续消息，不影响历史消息；
- **会话列表滚动加载**：侧栏滚动至底部自动携带 `nextCursor` 拉取下一页追加渲染（加载中显示骨架条，`nextCursor` 为空后不再触发）；面板打开与空间切换时从首页重载；发送消息、新建会话时前端**本地置顶**对应会话（不重拉列表），与键集分页的漂移补偿约定一致（3.1）；追加渲染按会话 id 去重防御；
- 确认卡片倒计时（expiresAt）归零后置为"已超时"不可操作；approve 后卡片状态更新为执行结果 + 跳转链接；
- `minder_commands` 收到时若用户已离开对应文档页，提示"请回到文档后重试"，丢弃指令（不缓存执行）；
- 消息历史按会话惰性拉取（打开会话时一次性获取该会话全量消息），50 条以上启用虚拟滚动（复用 Element Plus 虚拟化原语）。


### 2.5 单元测试点（C8）

- `useAssistantStream.ts` 多事件解析与异常帧容错；
- 会话列表游标分页组合式函数：滚动触底加载、`nextCursor` 终止、id 去重、本地置顶、空间切换重置；
- 链接白名单过滤纯函数（站内/站外/伪协议用例）；
- 确认卡片状态机（待确认/倒计时归零/已执行/已取消）渲染分支；
- 后端：令牌一次性消费与归属/空间校验、超时悬空 tool_calls 的补偿 tool 消息合成、循环上限终止、白名单过滤后的工具清单组装、会话列表键集分页（游标编解码、同 `last_active_at` 并列时 id 决胜、非法游标按首页处理）。

---


## 3. 实施说明

- **数据库迁移**：新增 2.1 两张表（DDL 写入 `v1.1.sql`，遵循《AI 基础设施详细设计说明书》第 6 章脚本版本化约定）；
- **实施梯队**：使用指引属梯队二（只读首发）；自然语言查询属梯队三；快捷操作（写工具）与对话式脑图编辑属梯队四；
- **依赖**：无新增依赖（确认令牌用既有 Redis；知识片段为静态资源）。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `73-global-assistant-overview.md` | 前言、1. 引言、2. 数据设计、2.3 文件与组件、2.4 交互要点、2.5 单元测试点、6. 实施说明、3. 接口详细设计 |
| 会话与消息 | `74-global-assistant-session.md` | 3.1 会话管理、3.2 发送消息 |
| 工具调用与写确认 | `75-global-assistant-tool.md` | 3.3 写操作确认、4.1 工具注册表、4.2 Function Calling 执行循环、4.6 回复链接安全 |
| 对话式脑图编辑 | `76-global-assistant-mindmap.md` | 4.3 对话式脑图编辑、4.4 页面上下文桥 |
| 使用指引知识库 | `77-global-assistant-knowledge-base.md` | 4.5 平台使用指引知识库 |
