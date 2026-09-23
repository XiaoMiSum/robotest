# 软件测试平台——（分册：对话式脑图编辑）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 4.3 对话式脑图编辑

- 触发条件：`pageContext.documentId` 非空且用户消息为编辑意图（LLM 决策调用 `translate_minder_command`）；无文档上下文时 LLM 告知"请在脑图编辑页使用该能力"；
- 后端校验用户对该文档的编辑权限（无权则工具返回错误文本）；
- 翻译复用 `dsl_translation` 的提示词模板、文档骨架上下文与结构校验链路，但作为 `assistant_chat` 会话内的一次工具调用，审计与限流仅按 assistant_chat 记一次，不另计 dsl_translation；回填 LLM 的 tool 消息内容为翻译结果摘要（命令条数与动作类型清单，不含全量 JSON，控制 token），循环继续以生成收尾答复（如"已为你准备好编辑预览，请确认"）；
- 翻译结果经 `minder_commands` 帧下发，**前端复用 DSL 执行链路**（命中预览 → 确认 → 编辑内核批量执行 → 单撤销组，新增节点带 AI 标识）；执行/取消结果由前端追加一条本地提示消息（不回传后端，编辑效果本身经协同通道同步）；
- 指令歧义时 LLM 直接以文本追问（`ambiguous` 结果转化为澄清问题），不做模糊执行。


### 4.4 页面上下文桥（前端）

- `stores/assistantContext.ts`：脑图编辑页 `onMounted/onUnmounted` 注册/注销当前 `{projectId, documentId, selectedNodeId}`，选中节点变化时更新；
- 助手面板发送消息时读取该 store 注入 `pageContext`；非脑图页仅注入当前 projectId（若在项目内）；
- system 提示中声明上下文含义（"用户当前正在编辑文档 X，选中节点 Y"），供 LLM 消歧（如"当前用例"指代）。


