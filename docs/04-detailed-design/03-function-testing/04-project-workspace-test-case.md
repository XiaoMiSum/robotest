# 软件测试平台——测试用例管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 测试用例管理接口

### 1.1 获取模块树

- **路径**：`GET /api/project/modules`
- **参数**：`assetType`（可选，`testcase` / `interface` / `scene`）。用例页必须携带 `assetType=testcase`，后端仅在该类型下把用例文档节点合并进模块树。
- **响应**：直接返回树形数组，每个节点包含 id、parentId、type（`directory` 目录 / `document` 文档）、name、sortOrder、createdAt、children。
- **校验**：登录、活动上下文与当前项目成员身份；免 RBAC 权限码校验。

### 1.2 创建模块目录与用例文档

- **创建目录**：`POST /api/project/modules`
  - **请求体**：`{ "parentId": null, "name": "新目录" }`（`parentId` 为 null 表示根层级）。
  - **校验**：`parentId` 存在且属于同项目；同父节点下名称唯一；名称 1-100 字符非空。
  - **响应**：创建后的目录节点信息，状态码 201。
- **创建文档**：`POST /api/project/testcases`
  - **请求体**：`{ "moduleId": "…", "name": "登录用例集" }`（`moduleId` 为 null 表示挂根层级）。
  - **校验**：`moduleId` 存在且属于同项目；同模块下文档名称唯一。
  - **处理**：事务中插入 `ft_case_document` 记录（`sort_order=0`，默认 `layout = { "template": "right", "offsets": {} }`），并自动创建默认根节点（见第 2 节）。
  - **响应**：创建后的文档信息，状态码 201。
- **校验**：登录、活动上下文与当前项目成员身份；免 RBAC 权限码校验。

### 1.3 更新/删除模块目录与用例文档

- **更新目录**：`PUT /api/project/modules/:id`
  - 重命名：请求体含 `name` 时校验同父节点下名称唯一（排除自身）。
  - 拖拽移动：`targetIndex` 非空时视为移动操作，`parentId` 为目标父目录（null 表示根层级），`targetIndex` 为目标层级下的位置下标（负数被拒，超出范围按边界收敛）。
  - 移动校验：目标父存在且属于同项目；禁止移到自身或其子孙目录下（循环引用）。
  - 移动处理：按 `targetIndex` 插入后重写目标层级的 `sort_order`（自 0 连续编号）。
- **更新文档**：`PUT /api/project/testcases/:id`
  - 重命名：请求体含 `name` 时校验同模块下文档名称唯一（排除自身）。
  - 拖拽移动：`targetIndex` 非空时视为移动操作，`moduleId` 为目标目录（null 表示根层级），校验目标模块存在且属于同项目，插入后重排同层级 `sort_order`。
  - 布局：`layout` 非空时整体覆盖文档布局 JSON。
- **删除目录**：`DELETE /api/project/modules/:id` — 目录必须为空（无子模块且无用例文档），且未被测试计划任务引用，否则拒绝。
- **删除文档**：`DELETE /api/project/testcases/:id` — 级联删除该文档下所有用例节点，布局随文档记录一并删除。
- **前端**：模块树 el-tree 开启 draggable；目录可任意排序/嵌套，文档仅允许放入目录内部或与文档同级排序（不允许停靠在目录行上下相邻处）；拖拽提交后整树重载并恢复当前文档高亮，成功提示「移动成功」，失败提示错误信息后以服务端结果回滚展示。
- **校验**：更新/删除均只校验登录、活动上下文与当前项目成员身份，免 RBAC 权限码校验。

### 1.4 获取文档全量节点

- **路径**：`GET /api/project/documents/:docId/nodes`

- **说明**：返回文档的完整脑图结构。`node` 为文档的根节点对象（嵌套树形结构），`layout` 为布局信息（`template` 模板名 + `offsets` 节点自由摆放偏移）。根节点是创建文档时自动生成的默认节点，其 `children` 包含用户在脑图中创建的所有子节点。

- **响应**：
  
  ```json
  {
  "node": {
    "id": 1,
    "documentId": 10,
    "parentId": null,
    "type": "normal",
    "title": "登录用例集",
    "priority": null,
    "sortOrder": 0,
    "version": 1,
    "children": [
      {
        "id": 2,
        "documentId": 10,
        "parentId": 1,
        "type": "case",
        "title": "邮箱登录",
        "priority": "P1",
        "sortOrder": 0,
        "version": 1,
        "children": [
          {
            "id": 3,
            "documentId": 10,
            "parentId": 2,
            "type": "precondition",
            "title": "用户已注册且处于登录页",
            "priority": null,
            "sortOrder": 0,
            "version": 1,
            "children": []
          },
          {
            "id": 4,
            "documentId": 10,
            "parentId": 2,
            "type": "step",
            "title": "输入邮箱和密码",
            "priority": null,
            "sortOrder": 1,
            "version": 1,
            "children": []
          },
          {
            "id": 5,
            "documentId": 10,
            "parentId": 2,
            "type": "expected",
            "title": "成功跳转到首页",
            "priority": null,
            "sortOrder": 2,
            "version": 1,
            "children": []
          }
        ]
      }
    ]
  },
  "layout": { "template": "right", "offsets": {} }
  }
  ```

- **字段说明**：`node` 为嵌套的根节点对象，每个节点包含 id、documentId、parentId、type、title、priority、sortOrder、version、aiGenerated 与 children 数组；根节点的 parentId 为 null，叶子节点的 children 为空数组。节点类型 `type` 取值 `normal`（普通）/ `case`（用例）/ `precondition`（前置）/ `step`（步骤）/ `expected`（预期），仅 `case` 节点可带 `priority`（P0-P3）。
- **校验**：免 RBAC 权限码校验，服务层校验项目成员身份。

### 1.5 获取用例详情

- **路径**：`GET /api/project/cases/:caseId`
- **响应**：以该节点为根的子树，字段同 1.4（id、type、title、priority、children 等完整属性），供缺陷关联用例等场景读取。

### 1.6 维护用例关联需求

用例节点与需求条目在存储层为多对多关联，供测试计划 / 测试评审按需求反查关联用例。前端「选择需求」选取器默认多选；测试用例场景收敛为单选交互，单用例最多关联一项需求，确认时仍以 `requirementIds` 数组全量覆盖提交，空数组表示清空关联。

- **查询**：`GET /api/project/cases/:caseId/requirements`
  - **响应**：`[{ "id": "…", "code": "REQ-12", "title": "登录验证码" }]`，按需求编号升序；无用例关联时返回空数组。
  - **校验**：登录、活动上下文与当前项目成员身份；免 RBAC 权限码校验。
- **保存**：`PUT /api/project/cases/:caseId/requirements`
  - **请求体**：`{ "requirementIds": ["…", "…"] }`，全量覆盖该用例节点的需求关联；空数组表示清空关联。
  - **校验**：用例节点属于当前项目且存在，否则返回 1000011022；节点类型必须为 `case`，否则返回 1000011022；`requirementIds` 去重后必须全部属于当前项目，否则返回 1000018001。
  - **处理**：事务内按 `case_node_id` 与目标集合差量对账 `ft_case_requirement`（取消的逻辑删除、新增的批量插入）；重复提交结果幂等。
  - **响应**：保存后的需求摘要列表（结构同查询）。
  - **校验**：登录、活动上下文与当前项目成员身份；免 RBAC 权限码校验。

## 2. 文档创建与默认根节点

创建用例文档（`POST /api/project/testcases`）时，事务中插入 `ft_case_document` 记录，并在 `ft_case_node` 表中创建默认根节点（title 与文档名称相同，type='normal'，sort_order=0，parent_id 为 NULL，version=1）。此根节点作为文档脑图的唯一顶层节点，用户可在其下添加子节点并标记为不同用例类型。获取文档节点树时，该根节点作为顶层节点返回，其 children 包含所有用户创建的节点；同模块下文档名称唯一，重复创建被拒绝。


## 3. 脑图实时协作

通过 WebSocket 连接 `ws(s)://<host>/ws/documents/{docId}?token=<accessToken>`（y-websocket 将房间名 `docId` 拼接到 `/ws/documents` 尾部，浏览器无法带 Authorization 头，token 走查询参数），服务端握手时校验 token 与项目成员身份后加入文档房间。连接上承载两类帧：

- **二进制帧（Yjs sync/awareness 协议）**：负责多人实时同步与在线状态，不落库；可写帧（sync step2 / update）广播前校验当前用户为文档成员，只读帧（sync step1、awareness）直接转发；服务端回发空 awareness 帧保活。
- **文本帧（JSON 操作协议）**：`{ "type": "...", "payload": {...} }`，`type` 取值 `add_node`、`move_node`、`update_attrs`、`delete_node`（payload 为 `{ "data": { ... } }`）与 `update_layout`（payload 即布局对象）。可写类型先广播给房间内其他用户，再由持久化处理器写库；每次写操作前重查文档成员身份，被拒时向发送者回 `{ "type": "error", "code": "...", "message": "非文档成员，无法编辑" }` 错误帧，不广播不落库。

节点更新与移动使用 `version` 字段乐观锁（`update_attrs` / `move_node` 携带期望版本，版本不符则丢弃并记日志），布局 upsert 到 `ft_case_document.layout` JSON 列。前端脑图数据以嵌套树形结构渲染，内容变更经 400ms 防抖后比对快照差量，递归遍历节点树生成上述操作帧，经同一连接提交；远端协同回放期间不重复提交，切换文档或离开页面时立即冲刷未提交的变更。

### 3.1 用例管理页（功能测试 - 测试用例）

**路由**：`/workspace/projects/functional-testing`（name=`FunctionalTesting`，title=功能测试，菜单不按权限码过滤）。页面内左侧子菜单（el-menu）项为 需求列表 / 测试用例 / 测试评审 / 测试计划（需求列表为首位子项并常驻展示），默认选中「测试用例」，切换经 `?tab=cases` 同步；测试用例子页面即左右结构：左侧模块树 + 右侧脑图编辑器。前端调用 `GET /api/project/modules?assetType=testcase` 加载模块树，选中文档后调用 `GET /api/project/documents/:docId/nodes` 获取嵌套的根节点对象和布局信息，`node.children` 递归渲染为脑图节点树；外部跳转（如缺陷关联用例）经 `?documentId=` 直达文档，消费后即清除参数，文档不存在时停留在空态。

**页面布局**：

```
┌────────┬───────────────┬───────────────────────────────────────┐
│功能测试 │ 模块树         │ 工具栏：[↶][↷]│[下级][同级][✎][🗑]│[模板▾][🧹]│
│ 子菜单  │[搜索目录/文档] │ [用例][前置][步骤][预期][⊗]│[P0][P1][P2][P3]   [🔗]│
│ ○需求列表│[＋新建 ▾]      │ ┌───────────────────────────────────┐ │
│ ○测试用例│ 📁功能        │ │            脑图画布                │ │
│ ○测试评审│  📁登录       │ │   ┌─────┐    （在线用户头像 右上）   │ │
│ ○测试计划│   📄用例集     │ │   │根节点│                          │ │
│        │  📁支付       │ │   └──┬──┘                           │ │
│        │   📄流程      │ │  ┌────┴────┐                        │ │
│        │              │ │  │ 子节点树  │                        │ │
│        │              │ │              [导航器]（缩放/抓手/缩略图 左下）│
│        │              │ └───────────────────────────────────┘ │
└────────┴───────────────┴───────────────────────────────────────┘
```

未选中文档时右侧为 `el-empty`（「请在左侧模块树中选择一个文档」），编辑区不设文档标题栏。

**模块树交互说明**：

| 操作     | 触发方式                    | 反馈                                                       |
| ------ | ----------------------- | -------------------------------------------------------- |
| 展开/折叠 | 点击目录行（仅展开）/ 点击节点箭头切换      | 展开/收起子节点，文档模式下点击已展开目录不收起，便于连续浏览                     |
| 搜索过滤   | 顶部搜索框输入关键字（可清空）          | 按名称大小写不敏感过滤，命中节点自动展开祖先链，清空关键字还原                      |
| 选中文档   | 点击文档节点                  | 已打开其他文档时先弹「确定离开当前文档，切换到其他文档吗？」二次确认，确认后右侧加载脑图编辑器   |
| 新建目录   | 顶部[＋新建]下拉或目录行悬停[＋]下拉     | 弹窗输入名称（非空校验），成功提示并整树重载                                |
| 新建文档   | 同上（下拉中的「新建文档」）          | 同上；创建后自动创建默认根节点（根节点作为文档脑图的唯一顶层节点）                     |
| 重命名     | 行悬停 ✎ 按钮                | 弹窗输入新名称（预填原名、非空校验），成功提示「已重命名」并重载                     |
| 移动      | 拖拽节点到目标位置               | 目录可任意排序/嵌套，文档仅能放入目录内部或与文档同级排序；提交后整树重载并恢复当前文档高亮        |
| 删除      | 行悬停 🗑 按钮               | 二次确认弹窗；目录须为空才能删除；文档级联删除所有节点；删除当前打开文档时清空编辑区            |
| 定时刷新   | 每 60 秒自动                | 静默刷新模块树（不显示 loading、失败不提示），保证多人协同下左侧树最终一致              |

**脑图编辑器交互说明**：

| 操作     | 触发方式                          | 反馈                                          |
| ------ | ----------------------------- | ------------------------------------------- |
| 新建下级节点  | Tab 键、工具栏[下级]或右键菜单            | 在选中节点下插入携默认文本「分支主题」的子节点并选中，需双击/F2 进入编辑          |
| 新建同级节点 | Enter 键、工具栏[同级]或右键菜单          | 在同层级后插入默认文本节点并选中（根节点的兄弟请求降级为子节点）                  |
| 编辑文本   | 双击节点、F2 或工具栏[✎]              | 节点文本变为原位可编辑输入框，Enter 提交、Esc 取消、失焦提交             |
| 标记类型   | 工具栏类型按钮或右键菜单类型组              | 节点 type 更新为 用例/前置/步骤/预期 并刷新色标；标记为用例且无优先级时默认 P2，标记为非用例时清除优先级；[⊗] 取消标记恢复普通节点 |
| 标记优先级  | 工具栏 P0-P3 按钮或右键菜单等级组        | 节点自动标记为用例，优先级色标更新                                |
| 删除节点   | Delete 键或右键菜单[删除节点]          | 立即删除并选中相邻节点，无确认弹窗，可通过撤销恢复                        |
| 移动节点   | 拖拽                             | 命中放置/排序目标时高亮提示并换父/调序；未命中时节点停留在拖放位置（自由摆放偏移随布局持久化） |
| 缩放画布   | 导航器缩放条（＋/－、回到 100%）、Ctrl+滚轮、Ctrl+= / Ctrl+- | 画布按档位缩放；普通滚轮为画布平移                                 |
| 抓手模式   | 导航器 ✋ 按钮                     | 切换画布拖拽（视野平移）模式                                   |
| 撤销/重做  | Ctrl+Z / Ctrl+Y（或 Ctrl+Shift+Z）、工具栏[↶][↷] | 远端协同回放不入本地撤销栈，栈空时按钮置灰                            |
| 复制/剪切/粘贴 | Ctrl+C / Ctrl+X / Ctrl+V、右键菜单 | 剪贴板走应用内实现，根节点不能剪切（菜单入口置灰并提示）                     |
| 排版模板   | 工具栏模板下拉（思维导图/右侧分布/组织结构/目录/鱼骨图/天盘） | 切换模板后自动触发同步与落库；[🧹] 整理布局清除手动偏移恢复自动排版               |

**右键菜单**：仅在选中节点时弹出（空白处右键不弹），亦可按空格键在选中节点下方唤醒；包含 新建下级/同级、复制/剪切/粘贴、类型组、等级组、删除节点；点击菜单外、按 Esc 或滚轮滚动即关闭。

**工具栏与导航器**：工具栏为单行常驻（超出宽度横向滚动），功能组依次为 撤销/重做 ｜ 节点操作 ｜ 排版模板与整理布局 ｜ 类型标记 ｜ 优先级；非全屏「编辑」下拉中的节点操作与删除入口、全屏态对应按钮均仅在选中节点时激活，未选中时置灰，撤销/重做仍按历史可用性置灰；类型标记入口（用例 / 前置 / 步骤 / 预期 / 取消标记）与优先级入口（P0–P3）同样仅在选中节点时激活，未选中时置灰；右键菜单等级组同步置灰。AI 优化与[🔗 关联需求]由用例节点悬浮窗承接，工具栏不再提供这两项入口；工具栏最右侧另设「AI测试用例」按钮（全屏 / 非全屏均常驻），点击经「选择需求」单选弹窗打开 AI 生成面板，交互见 `docs/05-interaction-design/07-ai-capability/07-assisted-features-ui.md` 2.5；悬浮窗两项入口水平并排、均为透明背景文字按钮，无卡片底色、边框与阴影；AI 优化使用成功色并以周期性闪烁强调，关联需求使用主色调。关联查询与保存均免 RBAC 权限码校验，仅校验项目成员身份。导航器悬浮于画布左下角，含缩放条、抓手、定位根节点、缩略图、全屏。

**协同状态与保存**：右上角在线用户头像来自 Yjs awareness；连接断开时顶部显示「连接已断开，正在重连...」横幅，重连成功后自动补发未提交变更。节点与布局变更自动保存（无手动保存按钮），服务端错误帧经消息提示展示（缺省文案「文档保存失败」，文档成员校验失败为「非文档成员，无法编辑」）。

**状态分支**：模块树加载中显示 loading、空态为「暂无模块，点击[新建]创建」、失败经消息提示；脑图加载中显示 loading、加载失败经消息提示「加载脑图失败」；未选文档显示空态占位；非文档成员时写操作被服务端拒绝（文档成员可执行读写操作）。

**离开确认**：处于文档中时，离开页面（路由守卫）、切换功能测试子菜单、切换文档三处均弹二次确认（「确定离开当前文档吗？」/「确定离开当前文档，切换到其他文档吗？」），取消则保持原文档并回退菜单或树的抢先高亮；文档内容实时自动保存，弹窗定位为防误触打断编辑。

**脑图组件模式**：按业务场景拆分为三个独立组件——CaseMindMap（编辑模式，本页使用，节点增删改实时写库）、ReviewMindMap（评审模式，基于快照只读）、PlanMindMap（计划模式，基于快照只读），三者共用 `minder/` 基座与导航器、右键菜单壳。详细交互参见 `docs/04-detailed-design/03-function-testing/08-mindmap-component.md`。

## 4. 数据设计（用例关联需求）

```sql
CREATE TABLE ft_case_requirement (
    id             uuid PRIMARY KEY,
    project_id     uuid NOT NULL,
    requirement_id uuid NOT NULL,
    case_node_id   uuid NOT NULL,
    document_id    uuid NOT NULL,
    is_deleted     boolean NOT NULL DEFAULT FALSE,
    created_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ft_case_requirement ON ft_case_requirement (requirement_id, case_node_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_ft_case_requirement_req ON ft_case_requirement (requirement_id);
CREATE INDEX idx_ft_case_requirement_case ON ft_case_requirement (case_node_id);
CREATE INDEX idx_ft_case_requirement_project ON ft_case_requirement (project_id);
CREATE INDEX idx_ft_case_requirement_document ON ft_case_requirement (document_id);
```

`document_id` 冗余用例节点所属文档，供计划 / 评审创建时按 `documentId → caseIds` 分组，避免反查节点表；无物理外键。

存量数据库升级时，先执行本节建表及索引语句，再执行以下增列语句；全新环境使用 `server/src/main/resources/db/schema.sql`。历史计划、评审的需求关联为空，不反推或修改已有用例快照。

```sql
ALTER TABLE ft_plan ADD COLUMN IF NOT EXISTS requirement_ids jsonb;
ALTER TABLE ft_review ADD COLUMN IF NOT EXISTS requirement_ids jsonb;
COMMENT ON COLUMN ft_plan.requirement_ids IS '关联需求 ID 集合（jsonb 数组，创建时写入）';
COMMENT ON COLUMN ft_review.requirement_ids IS '关联需求 ID 集合（jsonb 数组，创建时写入）';
```

回滚应用版本时可保留新增表和列；若确认无需保留关联数据，再删除两列与 `ft_case_requirement` 表。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐前端实现：修正路由与权限口径、模块/文档接口拆分、模块树与脑图交互表、协同保存与状态分支 |
| V1.0 | 2026-10-18 | 功能测试域表名统一 `ft_` 前缀：`test_case_document` / `test_case_node` → `ft_case_document` / `ft_case_node` |
| V1.0 | 2026-11-13 | 功能测试子菜单对齐实际实现：子项顺序改为 需求列表 / 测试用例 / 测试评审 / 测试计划，「需求池」更名为「需求列表」并居首位 |
| V1.0 | 2026-11-15 | 脑图「关联需求」改为用例节点级关联：新增 1.6 关联需求接口与 `ft_case_requirement` 数据设计，计划 / 评审创建按需求反查关联用例 |
| V1.0 | 2026-11-18 | 移除用例管理 RBAC 权限码口径：接口校验统一为登录、活动上下文与项目成员身份，WebSocket 写帧只校验文档成员关系，菜单与工具栏入口不再按权限码显隐 |
| V1.0 | 2026-11-18 | 移除脑图工具栏 AI 补全、级别推荐与关联需求入口，三项业务功能统一由用例节点悬浮窗承接 |
| V1.0 | 2026-11-18 | 用例补全与级别推荐合并为「AI 优化」，悬浮窗承接 AI 优化与关联需求两项业务功能 |
| V1.0 | 2026-11-19 | 脑图节点操作文案改为「新建下级节点 / 新建同级节点」，编辑下拉与全屏按钮按选中态置灰；用例节点悬浮窗改为透明背景文字按钮 |
| V1.0 | 2026-11-19 | 类型标记与优先级入口按选中态激活，右键菜单等级组同步置灰 |
| V1.0 | 2026-11-19 | 悬浮窗两入口改为水平并排，AI 优化增加绿色闪烁强调、关联需求改用主色调 |
| V1.0 | 2026-11-19 | 测试用例「关联需求」选取器收敛为单选（选取器默认多选，评审 / 计划保持多选）：接口仍按 requirementIds 数组全量覆盖，前端至多提交一项 |
| V1.0 | 2026-11-19 | 工具栏最右侧新增「AI测试用例」按钮：单选需求后发起 mindmap_case_generation 任务，提案脑图式勾选插入当前文档根节点下，任务详情只读不审核 |
