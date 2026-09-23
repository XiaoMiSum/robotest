# 软件测试平台——（总览分册）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册为《》按功能模块拆分后的总览分册，承载前言、引言、数据设计与公共约定等非模块内容；各功能模块正文见本目录对应分册，原章节编号保持不变，分册-章节对照表见文末。

---

## 1. 引言

### 1.1 编写目的

本文档对 AI 能力域中**围绕脑图编辑器的 AI 功能**进行详细设计：用例子树生成、步骤补全、优先级推荐、轻量需求池，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.4（智能测试用例生成），含配套的需求池实体模块。公共基础（AI 网关、SSE 帧格式、限流审计、错误码 6001–6012）见《AI 基础设施详细设计说明书》，本文档不重复。

核心机制约束（概要 AD-3 / 4.2）：**AI 产出进入脑图一律经前端编辑内核挂载**，复用协同广播、diff 持久化与撤销链路，后端不提供批量写节点接口。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》（3.4）
- 《软件测试平台概要设计说明书》（4.8、4.9）
- 《AI 基础设施详细设计说明书》
- 《脑图组件详细设计》（`docs/详细设计/脑图组件详细设计.md`）
- 《项目工作区详细设计说明书》（归档，节点模型与 WS 协议）

---


## 2. 数据设计

### 2.1 数据库表设计

新表遵循平台规范（同基础设施文档 2.1）：`id` 为 UUID v7（应用层生成）、`created_at`、`updated_at`、`is_deleted`，禁止物理外键（C5）；索引遵循 C9。

#### 2.1.1 需求池条目表（requirement_pool_item）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 条目 ID |
| project_id | UUID | NOT NULL | 归属项目 |
| title | VARCHAR(200) | NOT NULL | 条目标题 |
| content | TEXT | NOT NULL | 需求文本（Markdown，长度上限见 `requirementContentMaxLength` 配置键） |
| source_url | VARCHAR(500) | NULL | 来源 URL（仅记录出处，平台不抓取） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'active' | 条目状态：active / archived（默认 active） |
| ai_generated | BOOLEAN | NOT NULL DEFAULT FALSE | AI 拆分产生的条目标识（仅用于展示徽标，不影响业务规则） |
| created_by | UUID | NOT NULL | 创建人（编辑/删除/归档权限判定依据） |
| updated_by | UUID | NOT NULL | 最后更新人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_rpi_project_id` (project_id)

> 标题关键字检索用 `title ILIKE '%kw%'`（项目内条目量级小，不建全文索引）；条目不建向量索引（AD-5）。`ai_generated` 仅用于渲染，不作独立查询条件，不建索引。

#### 2.1.2 文档需求关联表（requirement_document_rel）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| document_id | UUID | NOT NULL | 脑图文档（test_case_module，type=document） |
| requirement_id | UUID | NOT NULL | 需求池条目 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`uk_requirement_document_rel` UNIQUE (document_id, requirement_id) WHERE is_deleted = false，`idx_requirement_document_rel_requirement_id` (requirement_id)

#### 2.1.3 既有表变更（AI 标识字段）

AI 标识是节点数据的一部分（SRS 3.4.1 业务规则），随快照继承：

| 表 | 变更 | 说明 |
| ---- | ---- | ---- |
| test_case_node | `ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE` | AI 生成标识 |
| test_review_node_snapshot | 同上 | 评审快照继承 |
| test_plan_node_snapshot | 同上 | 计划快照继承 |

不新增索引（该字段仅用于渲染，不作为独立查询条件）。快照创建逻辑（评审/计划的既有拷贝 SQL）同步增加该列的复制。

### 2.2 结构化输出数据结构（AI 生成用例树）

用例子树生成、步骤补全共用同一输出结构（网关侧按下述规则校验，见基础设施文档 4.4）：

```json
{
  "nodes": [
    {
      "type": "case",
      "title": "邮箱登录成功",
      "priority": "P1",
      "children": [
        { "type": "precondition", "title": "用户已注册且处于登录页", "children": [] },
        { "type": "step", "title": "输入正确邮箱和密码，点击登录", "children": [] },
        { "type": "expected", "title": "跳转到首页，显示用户昵称", "children": [] }
      ]
    }
  ]
}
```

**校验规则**（Bean Validation + 自定义结构断言）：

- `type` ∈ `case / normal / precondition / step / expected`（`@InEnum`，与 V1.0 节点模型一致）；
- `title` 非空且 ≤ 200 字符；超长在 Schema 校验**前**的宽容规整步骤中截断（与基础设施 4.4 的剥离 think 段/代码围栏同层处理），截断计入 warnings，不触发校验失败与带错重试；
- `priority` 仅允许出现在 `case` 节点，∈ P0–P3；非 case 节点出现 priority 视为校验失败；
- 父子合法性：`precondition / step / expected` 只能是 `case` 的直接子节点且自身无子节点；`case` 下不得再嵌套 `case`（与既有编辑器约束一致）；`normal` 可嵌套 `normal / case`；
- 树深度 ≤ 5、单次节点总数 ≤ 200，超限校验失败（防失控输出）；
- 步骤补全场景额外约束：`nodes` 仅允许 `precondition / step / expected` 类型的扁平数组。

---



通用约定、SSE 帧格式、错误码见《AI 基础设施详细设计说明书》3.1 / 3.6。本章接口均为项目级（`/api/project/**`，头 `Authorization` + `X-Active-Workspace` + `X-Active-Project`）。


### 4.4 脑图操作指令集（DSL）完整定义

#### 4.4.1 结构

```typescript
interface MinderCommand {
  selector: {
    types?: NodeType[]        // case/normal/precondition/step/expected
    priorities?: Priority[]   // P0-P3，仅对 case 生效
    keyword?: string          // 标题包含（忽略大小写）
    subtreeRootTitle?: string // 以标题引用限定子树范围（默认全文档），解析规则见下
    aiGenerated?: boolean     // 按 AI 标识筛选
  }
  action:
    | { type: 'mark_type'; params: { nodeType: NodeType } }
    | { type: 'mark_priority'; params: { priority: Priority } }
    | { type: 'highlight'; params: {} }
    | { type: 'move'; params: { targetParentTitle: string } }  // 仅文档内，标题引用
    | { type: 'add_child'; params: { nodes: AiNodeTree[] } }   // 结构同 2.2
}
```

- selector 各条件为 AND 关系；`commands` 数组按序执行，上限 10 条；
- **节点引用解析规则**：LLM 不接触节点 ID（翻译上下文仅含骨架统计与一级标题清单），`subtreeRootTitle` / `targetParentTitle` 由 `dslRunner.ts` 在当前树内按标题精确匹配（忽略首尾空白）解析；指令含「当前/选中节点」语义时 LLM 输出保留值 `@selected`——该保留值仅允许作为 `subtreeRootTitle` / `targetParentTitle` 的取值出现（提示词模板中声明此约定，后端结构校验对其显式放行，不当作真实标题），前端替换为请求随附的 `selectedNodeId` 对应节点；
- **解析中止粒度**：全部标题引用（含 `@selected`）在**预览阶段一次性解析**：唯一命中则使用；任一命令出现零命中、多义或 `@selected` 无对应选中节点，则**整批不进入预览**、直接提示改写（前端本地等同 `clarification` 分支），不做部分执行；
- 当前版本不支持跨文档操作：标题引用只在当前文档树内解析，跨文档语义的指令由 LLM 按歧义处理（`ambiguous = true`）。

#### 4.4.2 分工与执行

- **LLM 只做翻译**（`dsl_translation`，同步调用 + 结构校验：action 枚举、selector 字段类型、优先级枚举、`add_child.nodes` 套用 2.2 结构断言、标题引用字段放行保留值 `@selected`）；后端翻译时附带文档骨架上下文（模块下节点类型/优先级分布统计与一级标题清单，不传全量节点，控制 token）；
- **前端确定性执行**：`dslRunner.ts` 遍历当前 minder 树计算命中集合 → 弹出**影响范围预览**（命中数量 + 节点标题清单，可展开；含「将跳过」清单，见下）→ 用户确认 → 经编辑内核批量执行（单撤销组，同 4.2）；`add_child` 新增节点写 `aiGenerated: true`；
- **mark_type 合法性联动**：执行前逐节点校验类型变更的父子合法性（2.2 约束）：变更导致自身或子孙节点关系非法的节点（如把带 step 子节点的 case 改为 step、把父节点为 case 的节点标为 case）跳过执行，在预览弹窗中单列「将跳过」清单及原因；case 改为非用例类型时复用编辑内核既有联动清除优先级；DSL 批量 `mark_type` 标记为 case **不触发** 4.3 的优先级推荐（推荐仅由手工单节点标记触发）；
- **move 合法性联动**：执行前对每个命中节点校验移动后的父子合法性（2.2 约束）并做环检测（目标父节点不得位于被移动节点自身子树内），非法者跳过并进「将跳过」清单；
- **add_child 多目标语义**：selector 命中多个节点时，`params.nodes` 为**每个**命中节点各挂载一份独立副本（节点 ID 各异，均写 `aiGenerated: true`）；命中节点为 precondition / step / expected（自身不得有子节点）或挂载后违反 2.2 父子约束的跳过并进「将跳过」清单；
- **highlight 语义**：本地临时视觉态——仅当前用户视图内高亮呈现（样式复用搜索命中态），不写节点数据、不广播、不产生撤销历史，刷新或执行下一次 DSL 后清除；其余 action 均为编辑操作，参与单撤销组；
- 命中为空 → 提示"未找到匹配节点"；`ambiguous` → 展示 `clarification` 要求改写。


### 4.5 AI 标识（aiGenerated）全链路

- **协议**：WS `add_node` / `update_attrs` 帧的节点属性集扩展 `aiGenerated`（布尔，缺省 false）；后端 diff 持久化写入 `test_case_node.ai_generated`；Yjs 协同帧为二进制透传，无需变更；
- **渲染**：`badges.ts` 徽标体系新增「AI」徽标（注册规则同既有类型徽标，读取节点 data 的 `aiGenerated`）；
- **移除**：节点标记面板（右键菜单标记域）增加「移除 AI 标识」操作，仅当 `aiGenerated = true` 时显示；执行 `update_attrs` 置 false；**前端任何入口不提供置 true 的操作**（仅挂载执行器写入），移除后不可恢复（撤销操作除外——撤销属于编辑历史回退，不视为"重新添加"）；服务端对 `update_attrs` 不校验 `aiGenerated` 的变更方向——绕过前端置 true 属编辑权限内的低危伪造（不影响任何业务规则），接受该风险，不做后端拦截；
- **复制/粘贴**：`clipboard.ts` 既有实现复制节点全部 data，`aiGenerated` 随之保留到副本（SRS 数据继承规则），无需改动，补充单测断言；
- **快照继承**：评审/计划创建快照的字段拷贝清单加入 `ai_generated`。


### 5.1 文件与组件

| 文件 | 说明 |
| ---- | ---- |
| `pages/project/RequirementPoolPage.vue` | 需求池管理页（列表 + 状态筛选 + 关键字搜索 + 新建/编辑抽屉 + 归档/取消归档 + **AI 拆分入口**，MarkdownEditor 复用）；项目工作区侧边菜单新增入口，**不受 AI 开关控制** |
| `components/project/RequirementSplitDialog.vue` | AI 文档拆分对话框（US-AI-019）：文本域粘贴文档 → [AI 拆分]（SSE 消费 `useAiStream()`）→ 模块分组预览（逐条编辑/删除/勾选 + 全选）→ [批量入库] 调 3.1.7 批量接口；见 5.2 |
| `components/project/RequirementSelector.vue` | 条目选取器弹窗（多选 + 关键字过滤，**仅展示 active 条目**），供各 AI 入口复用 |
| `components/project/minder/ai/AiGeneratePanel.vue` | 「AI 生成用例」/「AI 补全用例」抽屉（右侧滑出约 640px、透明遮罩不压暗画布，常驻挂载、关闭仅隐藏）：需求文本域 / 条目选择 / 操作行内进度条（flex:1 占位、与按钮同行左侧，完成态不显示）；模式文案由 `aiPanelModes.ts` 配置驱动——生成模式标题「AI 生成用例」、按钮 [开始生成]/[重新生成]/[确认挂载]，补全模式标题「AI 补全用例」、按钮 [开始补全]/[重新补全]/[确认追加]；done 后操作行主按钮消失，底部提供 [重新生成]（次要按钮样式，无主按钮底色）[查看预览]（主按钮，位于 [重新生成] 右侧），不再内嵌预览树；watch `docId` 切换文档时断开 SSE 并重置 |
| `components/project/minder/ai/aiPanelModes.ts` | 面板模式配置表（generate / complete）：标题、主按钮文案（开始/重新）、底部确认按钮、placeholder、`buildBody` 请求体映射、SSE 路径、`inputOptional` 等；`AiGeneratePanel` 按 mode 读取，保证两模式 UI 文案与请求构造一致 |
| `components/project/minder/ai/AiPreviewDialog.vue` | 独立预览弹窗（宽 70% × 高 80%（视口））：弹窗内创建 kityminder 只读实例渲染生成节点树快照，可勾选节点按模式区分（生成=仅用例节点、点击随用例级联内部结构；补全=全部生成节点、逐项独立取舍），底部「已勾选 N/M 个用例/项」[确认挂载] [关闭]；两窗并存、预览置顶 |
| `components/project/minder/ai/aiPreviewRender.ts` | 预览脑图渲染支撑：`AiPreviewNode[]` → kityminder `importJson` 结构转换；勾选框渲染器注册（仿 `badges.ts` 的 `defineBadgeRenderer`，读取节点 `data.aiSelected` 绘制 ☑/☐）；仅预览弹窗内实例可见 |
| `components/project/minder/ai/aiMount.ts` | 挂载执行器（4.2）：`AiPreviewNode` 携带 `aiSelected` 与 `aiSelectable`；`buildPreviewTree`（生成=仅 case 子树默认勾选，补全=全部节点默认勾选）/ `filterCheckedTree` 按勾选状态过滤 |
| `components/project/minder/ai/dslRunner.ts` | DSL 标题引用解析、命中计算与合法性过滤（4.4.2，纯函数便于单测）；highlight 视觉态与预览弹窗由调用方组件维护 |
| `components/project/minder/badges.ts` | 扩展 AI 徽标 |
| `services/project.ts` / `types/index.ts` | 3.1–3.3 接口封装与类型（无 `any`） |


### 5.2 交互要点

- 脑图工具栏新增「AI 生成用例」按钮（`stores/ai.ts` 的 `aiEnabled` 控制显隐）；右键菜单在 case 节点上显示「AI 补全步骤」；
- 生成/补全为交互式功能：`AiGeneratePanel` 内嵌公共组件 `AiModelSelect`（对话模型选择器，基础设施 5.1 / `docs/交互设计/README.md` §2.8），所选 `modelId` 随 3.2 请求提交；
- 全部 SSE 消费走基础设施的 `useAiStream()`（支持取消按钮、超 10 秒未见首帧提示可取消重试）；
- 预览-确认阶段：生成抽屉保持打开（右侧滑出，透明遮罩）；done 后点击 [查看预览] 打开独立预览弹窗（两窗并存、预览置顶）；预览弹窗内为 kityminder 只读实例渲染的本地快照（`buildPreviewTree`，仅生成节点树，文档既有数据不并入预览），不落库、不产生撤销历史；确认挂载成功 → 关闭预览弹窗与生成抽屉；未确认挂载（仅关闭预览弹窗）→ 生成抽屉保留「完成」态、快照可重新预览；
- 会话保持：`AiGeneratePanel` 在 `CaseMindMap` 中常驻挂载（generate / complete 双实例，各自 `resetToken` 信号）；关闭抽屉仅 `visible=false`，不触发 `stop()`；`handleClose` 仅关闭抽屉与预览弹窗；切换文档（watch `docId`）时 `controller.cancel()` + 全量重置；补全实例在目标节点变化（`resetToken` 自增）时重置，生成实例不随目标变化重置；
- 挂载确认时执行目标节点存在性校验；预览弹窗勾选状态（`aiSelected`）随预览弹窗销毁即丢弃，仅勾选过滤结果进入挂载执行器；
- DSL 执行预览用独立确认弹窗（命中数量醒目 + 清单折叠 + 「将跳过」清单及原因），确认后关闭并聚焦首个受影响节点；
- **AI 拆分（US-AI-019）**：`RequirementSplitDialog` 为独立对话框（非抽屉），入口在需求池页工具栏，与新建/编辑共用 `canEdit`（条目创建人或项目管理权限）控制显隐；SSE 消费 `useAiStream()`（3.2.3），预览按模块分组渲染，流式期间行内进度占位 + [停止]；逐条编辑/删除/勾选均为本地状态，关闭对话框即丢弃；[批量入库] 将勾选条目按「模块名 · 需求点标题」拼接标题后调 3.1.7 批量接口，成功后关闭对话框并刷新列表；拆分结果不入库（需求→用例闭环不在本版本）。

---


## 6. 测试设计（C8）

### 6.1 前端单元测试

- `dslRunner.ts`：selector 组合命中、空命中、标题引用解析（唯一命中/零命中/多义/`@selected` 替换/解析失败整批中止）、mark_type 非法变更跳过与优先级清除联动、move 非法移动与环检测跳过、add_child 多目标挂载与非法目标跳过、执行为单撤销组；
- `aiPanelModes.ts`：generate / complete 两模式配置字段齐全（标题/主按钮/确认按钮/placeholder/buildBody/SSE 路径/inputOptional/countLabel），模式缺字段编译期暴露；`buildBody` 请求体构造正确；
- `aiMount.ts`：勾选过滤规则（`aiSelected` 父子联动、生成模式仅 case 子树默认勾选、补全模式 selectAll 全节点默认勾选且逐项取舍）、aiGenerated 写入、目标节点缺失分支（生成重选与补全不可挂载两种场景）；
- `badges.ts`：AI 徽标注册与移除后消失；`clipboard` 断言 aiGenerated 随复制保留。

### 6.2 后端单元测试

- 需求池 Service：创建/更新的长度校验分支、非创建人且无项目管理权限的 2001 分支、archived 条目更新被拒的 2001 分支、归档/取消归档分支、删除条目联动解除文档关联、requireByIds 过滤 archived 条目；
- 批量创建（3.1.7）：items 空列表拒绝、单条长度超限拒绝、aiGenerated 透传落库；
- 文档关联：`:docId` 跨项目归属校验的 3001 分支、全量设置的差量增删；
- 结构化输出校验：2.2 断言（父子合法性、深度 ≤ 5、总数 ≤ 200、非 case 节点带 priority 失败、title 超长截断计入 warnings）；
- 拆分输出校验（3.2.3）：modules 为空/超 50 失败、module 超长截断、items 为空/超 50 失败、title/content 超长截断计入 warnings、整体结构不合规返回 error 帧；
- DSL 翻译结构校验：action / selector 枚举断言、`add_child.nodes` 套用 2.2 断言、`@selected` 保留值放行。

---


## 7. 实施说明

- **数据库迁移**：遵循脚本版本化约定（基础设施文档第 6 章）：2.1.1 / 2.1.2 两张新表 DDL 与 2.1.3 的三条 `ALTER TABLE … ADD COLUMN ai_generated`（默认 false，存量数据零影响）均写入 `v1.1.sql`——`v1.sql`（V1.0 基线）内容保持不变，不改动其中的建表语句；首次建库按 `v1.sql` → `v1.1.sql` 顺序执行后自动包含该列；
- **增量修订**：需求池条目新增 `status` 列（默认 `'active'`）属未发布阶段的表结构修订，直接同步至 `v1.1.sql` 的 `requirement_pool_item` 建表语句（存量开发库按 3.1.5 归档态限制手工 `ALTER TABLE requirement_pool_item ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active'`），不另立增量脚本；
- **增量修订（US-AI-019）**：`ai_generated` 列（默认 false）随 2.1.1 建表语句直接同步至 `v1.1.sql`（存量开发库手工 `ALTER TABLE requirement_pool_item ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE`），不另立增量脚本；
- **实施梯队**：子树生成 / 补全属梯队一（此时需求条目选取降级为仅手动输入）；需求池属梯队二；优先级推荐、对话式脑图编辑（DSL）属梯队四；AI 拆分（US-AI-019）随需求池梯队二一并交付；
- **依赖**：无新增前后端依赖。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `58-ai-case-generation-overview.md` | 前言、1. 引言、2. 数据设计、4.4 脑图操作指令集、4.5 AI 标识、5.1 文件与组件、5.2 交互要点、6. 测试设计（C8）、7. 实施说明、3. 接口详细设计 |
| 轻量需求池 | `59-ai-case-generation-pool.md` | 3.1 需求池接口 |
| AI 生成用例 | `60-ai-case-generation-ai-gen.md` | 3.2 AI 生成类接口、4.1 生成-预览-挂载总链路、4.2 挂载执行器、4.3 优先级推荐、4.6 生成类 Prompt 上下文组装 |
| 同步建议 | `61-ai-case-generation-sync.md` | 3.3 同步建议类接口 |
