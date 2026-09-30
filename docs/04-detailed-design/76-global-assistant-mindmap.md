# 软件测试平台——对话式脑图编辑

**文档版本**：V1.0
**日期**：2026-09-30
**状态**：起草中

---

## 1. 对话式脑图编辑

- 触发条件：`pageContext.documentId` 非空且用户消息为编辑意图（LLM 决策调用 `translate_minder_command`）；无文档上下文时 LLM 告知"请在脑图编辑页使用该能力"；
- 后端校验用户对该文档的编辑权限（无权则工具返回错误文本）；
- 翻译复用 `dsl_translation` 的提示词模板、文档骨架上下文与结构校验链路，但作为 `assistant_chat` 会话内的一次工具调用，审计与限流仅按 assistant_chat 记一次，不另计 dsl_translation；回填 LLM 的 tool 消息内容为翻译结果摘要（命令条数与动作类型清单，不含全量 JSON，控制 token），循环继续以生成收尾答复（如"已为你准备好编辑预览，请确认"）；
- 翻译结果经 `minder_commands` 帧下发，**前端复用 DSL 执行链路**（命中预览 → 确认 → 编辑内核批量执行 → 单撤销组，新增节点带 AI 标识）；执行/取消结果由前端追加一条本地提示消息（不回传后端，编辑效果本身经协同通道同步）；
- 指令歧义时 LLM 直接以文本追问（`ambiguous` 结果转化为澄清问题），不做模糊执行。


## 2. 页面上下文桥（前端）

- `stores/assistantContext.ts`：脑图编辑页 `onMounted/onUnmounted` 注册/注销当前 `{projectId, documentId, selectedNodeId}`，选中节点变化时更新；
- 助手面板发送消息时读取该 store 注入 `pageContext`；非脑图页仅注入当前 projectId（若在项目内）；
- system 提示中声明上下文含义（"用户当前正在编辑文档 X，选中节点 Y"），供 LLM 消歧（如"当前用例"指代）。


## 3. AI 辅助文档创建

- 触发条件：用户消息包含"新建/增加文档"意图且不在脑图编辑页（`pageContext.documentId` 为空），LLM 调用 `create_document` 写工具；若目标模块不存在，LLM 可先调用 `create_module` 创建模块；
- `create_document` 工具参数：`projectId`（LLM 从 pageContext 获取）、`moduleName`（按名称在项目模块树中匹配，可选）、`documentName`（必填）、`caseNodes`（初始用例节点数组，每项含 `title` 与 `priority`，可选）；
- 执行逻辑：复用 `TestCaseDocumentService.createTestCase` 创建文档与根节点；若 `caseNodes` 非空，在根节点下逐条插入 `TestCaseNode`（type=case, aiGenerated=true, version=1）；节点直接写入数据库，不经过 Yjs 通道——新文档无活跃 Yjs 会话，用户首次打开时前端从 REST API 加载完整节点树（见 `57-mindmap-component.md`），Yjs 连接建立后从画布状态初始化；
- 工具结果返回 `{ documentId, documentName, routePath, createdNodes }`，LLM 据此生成回复并附带文档跳转链接；
- 用户打开文档后，可继续通过对话式脑图编辑（`translate_minder_command`）补充步骤、前置条件等详细内容——`create_document` 的 `caseNodes` 仅支持标题与优先级，不承载步骤等嵌套结构；
- 与 AD-3（AI 产出经前端编辑内核挂载）的关系：AD-3 约束的是**已打开文档的脑图节点编辑**，`create_document` 是**文档初始创建**，与 `TestCaseDocumentService.createTestCase` 本身的后端直写文档+根节点一致，不违反 AD-3。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-23 | 初始版本 |
| V1.0 | 2026-09-30 | 新增第 3 节「AI 辅助文档创建」 |


