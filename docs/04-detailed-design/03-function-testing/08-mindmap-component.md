# 软件测试平台——脑图组件详细设计

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 概述

> 本文档为组件级设计文档（非归档文件，原位更新），章节按组件设计组织（概述 → 技术选型 → 组件架构 → 数据模型 → 各功能模块），与详细设计的统一骨架（引言 → 数据设计 → 接口详细设计 → 业务逻辑设计 → 前端设计 → 实施说明）不一致，属既有格式，沿用不改。

脑图组件是功能测试模块的核心组件，用于测试用例的可视化编辑、评审与执行跟踪。采用 **kityminder-core** 作为脑图渲染引擎，通过 **Yjs** 实现多人实时协作。按业务场景拆分为三个独立组件；公共能力分两处沉淀：`web/src/minder/` 承载引擎加载、编辑内核、节点适配与 Yjs 同步等与 UI 无关的核心逻辑，`web/src/components/project/functional-testing/minder/` 承载右键菜单壳、导航器与三组件共用结构样式，业务编排则下沉到 `web/src/composables/project/functional-testing/`：

- **CaseMindMap（编辑）**：节点增删改、复制/剪切/粘贴、类型标记、优先级标记、布局模板与文档关联需求，节点变更经 WebSocket 文本帧持久化到 `test_case_node` 表，并经 Yjs 多人实时同步。
- **ReviewMindMap（评审）**：基于快照的只读脑图，提供评审标记（通过/不通过/待评审）、评论抽屉与用例移除，数据写入 `test_review_record` 表并更新 `test_review_node_snapshot.last_mark`。
- **PlanMindMap（计划）**：基于快照的只读脑图，提供执行结果标记（通过/失败/阻塞/待执行）与用例移除，数据写入 `test_plan_execution_record` 表并更新 `test_plan_node_snapshot.last_result`。

---

## 2. 技术选型

| 组件    | 技术                                            | 说明                            |
| ----- | --------------------------------------------- | ----------------------------- |
| 脑图核心  | kityminder-core                               | 基于 SVG 的脑图渲染引擎，提供节点树、布局模板、快捷键与只读模式；编辑内核（原位编辑/撤销重做/键盘导流）自研于 `web/src/minder/` |
| 数据绑定  | kityminder-core 的 `importJson` / `exportJson` | JSON 格式数据中间层（后端嵌套节点树经 `adapter.ts` 转换）  |
| 实时协作  | Yjs + y-websocket                             | 编辑模式下增量同步（分片模型见第 11 节），评审/计划模式不建立连接 |
| 前端框架  | Vue 3 + Composition API                       | 封装为 Vue 组件与本地 composable，管理生命周期和交互   |
| UI 增强 | Element Plus（按钮、下拉、抽屉、对话框）                  | 工具栏按钮与排版下拉、评论抽屉、移除确认；右键菜单为 teleport 到 body 的自定义容器 |

---

## 3. 组件架构

```
web/src/components/project/functional-testing/
├── case/CaseMindMap.vue    编辑组件（props: docId）
│     ├── 编辑工具栏（撤销重做、节点增删/编辑/删除、排版模板与整理布局、类型/优先级标记、关联需求）
│     ├── 编辑内核 KMEditor（原位编辑/撤销重做，见 3.1）
│     ├── Yjs 实时协作 + WebSocket 持久化（文本帧落库）
│     ├── 协作状态指示器（在线用户头像）与断线提示横幅
│     └── 编辑右键菜单（节点操作/复制剪切粘贴/标记类型/标记等级/删除）
├── review/ReviewMindMap.vue  评审组件（props: reviewId、documentId、removable）
│     ├── 评审工具栏（通过/不通过/待评审、评论、移除用例）
│     ├── 快照只读画布（minder.disable()）
│     ├── 评论抽屉（评审记录列表）
│     └── 评审右键菜单（标记评审结果/添加评论/从评审中移除）
├── plan/PlanMindMap.vue    计划组件（props: planId、documentId、removable）
│     ├── 执行工具栏（通过/失败/阻塞/待执行、移除用例）
│     ├── 快照只读画布（minder.disable()）
│     └── 执行右键菜单（标记执行结果/从计划中移除）
└── minder/                 三组件共用的 Vue 层基座
      ├── MinderContextMenu.vue  右键菜单壳（teleport + 菜单样式，菜单项经 slot 传入）
      ├── MinderNavigator.vue    导航器（缩放条/定位根节点/抓手/缩略图/全屏，三组件共用）
      └── minder-base.scss       共享结构样式（容器/工具栏/画布）

web/src/minder/             自研脑图核心（与 UI 无关）
      ├── loader.ts          引擎加载：经典 script 标签加载 kity/kityminder-core 并注册徽标模块
      ├── useMinderInstance.ts  实例骨架：容器/加载态/实例引用、竞态令牌、销毁、选中状态维护
      ├── useContextMenu.ts     右键菜单状态：定位、空格唤醒、全局关闭监听（mousedown/Esc/wheel）
      ├── fsm.ts             状态机：normal ⇄ input
      ├── receiver.ts        contenteditable 键盘接收器（IME 支持）
      ├── input.ts           原位内联编辑（定位/提交/取消/跟随重定位、双击编辑）
      ├── jumping.ts         键盘导流：F2 编辑、Tab/Enter 新建、空格唤醒菜单、Ctrl+C/X/V、Ctrl+Z/Y
      ├── history.ts         JSON-Patch diff + applyPatches 增量撤销重做
      ├── badges.ts          自定义渲染模块：类型/优先级（左侧）与评审/执行标记（右侧）彩色徽标
      ├── clipboard.ts       应用内节点剪贴板（复制/剪切/粘贴，跨文档可用）
      ├── adapter.ts         后端节点树 ⇄ kityminder JSON 映射、UUID v7
      ├── yjsSync.ts         Yjs 分片模型 ⇄ 画布双向同步（发布/重建/远端增量应用）
      ├── editor.ts          编辑内核入口 KMEditor（仅 CaseMindMap 使用）
      └── types.ts           kity/kityminder 全局与实例类型声明

web/src/composables/project/functional-testing/
├── mindmap/useMindmapInit.ts          编辑模式初始化编排（取数/建内核/事件接线/卸载）
├── mindmap/useMindmapNodeOps.ts       节点操作（标记/编辑/增删/剪贴板/撤销重做）
├── mindmap/useMindmapLayout.ts        排版模板与整理布局
├── mindmap/useMindmapPersistence.ts   JSON 文本帧落库（节点差分 + 布局整体 upsert）
├── mindmap/useMindmapYjs.ts           Yjs 连接、在线用户、远端回放接线
├── mindmap/useMindmapRequirementLink.ts 文档关联需求
├── review/useReviewMindmapOps.ts      评审加载、标记、评论、移除用例
└── plan/usePlanMindmapOps.ts          计划加载、执行标记、移除用例
```

拆分原则：三组件仅共享"实例骨架 + 引擎加载 + 右键菜单壳 + 结构样式"这类与业务无关的能力；数据获取、工具栏、标记提交等业务逻辑各自独立（编辑走 `useMindmap*` 系列，评审/计划各走专属 ops composable），互不感知，避免单组件内 `mode` 分支蔓延。

### 3.1 编辑内核关键机制

- **原位编辑**：contenteditable 接收器平时隐藏且只接收键盘；进入 input 态时隐藏节点自身 SVG 文本，接收器按 `getRenderBox('TextRenderer', 'screen')` 换算到容器坐标（含半行距补偿）叠合到节点文本上，支持 IME、跟随视图缩放/平移重定位。Enter/blur 提交（走 `execCommand('text')` → `contentchange` → Yjs 同步 + 落库），Esc 取消；空文本或与原文一致视为无操作，不产生撤销步骤与落库；符合既定交互规范：原位编辑、禁止弹窗。
- **双击编辑**：双击节点或 F2/工具栏进入原位编辑，光标定位到文本末端（不全选）；普通打字不触发编辑（键盘接收器吞掉输入字符）。
- **自由拖拽摆放与布局持久化**：拖拽命中 drop/排序目标时换父/调序；未命中时 core 保留 `layout_*_offset` 偏移，节点停留在拖放位置（自由摆放）。模板名与全部节点偏移经 `update_layout` 帧整体 upsert 到 `test_case_document.layout` JSON 列，加载时回填偏移并应用模板；工具栏"整理布局"（core `resetlayout`，亦有 core 原生快捷键 Ctrl+Shift+L）可一键清除全部偏移恢复自动排版。
- **撤销重做**：core 无 Undo/Redo 命令，由 history.ts 实现：`contentchange` 时与上一快照 diff 生成反向 JSON-Patch 入栈（上限 100），undo/redo 调 core 原生 `minder.applyPatches()` 增量回放、仅局部重渲染；回放自动触发 `contentchange`，现有同步/落库管道零改动生效（patchLock 门闩防止回放再入栈）。
- **协同隔离**：远端 Yjs 变更回放期间（applyingRemote）不入本地撤销栈，避免撤销掉他人的编辑；review/plan 只读模式不启用编辑内核。
- **WS 心跳保活**：y-websocket 硬编码 30 秒假死检测（30 秒无入站消息即断开重连），而服务端二进制帧仅转发给房间内其他成员，单人编辑时客户端永远收不到消息，会陷入每 ~30 秒一轮的断连重连循环。修复分两端：服务端收到二进制帧后向发送者回 3 字节空 awareness 帧 `{0x01, 0x01, 0x00}`（客户端解码零副作用），借客户端每 ≤15 秒的 awareness 心跳实现回帧保活；前端截流的文本帧（业务确认帧）不经过 y-websocket 原生 onmessage，需手动刷新 `wsLastMessageReceived`（lib0 的 getUnixTime 即 Date.now，毫秒）。
- **离开文档确认**：处于文档中时切换文档（`ProjectModuleTree` 经 `useProjectModuleTree` 弹二次确认，取消后需 `setCurrentKey` 回退 el-tree 抢先高亮）、离开页面（TestCasePage `onBeforeRouteLeave` 守卫）、切换功能测试页子页面（子页面经 `activeMenu` + v-if 切换不走路由，`useFunctionalTesting` 在菜单 select 时调 TestCasePage expose 的 `confirmLeave()` 拦截，取消后需 `updateActiveIndex` 回退 el-menu 抢先高亮）均弹窗二次确认；文档内容实时自动保存，弹窗定位为防误触打断编辑。

---

## 4. 数据模型与模式映射

### 4.1 节点类型定义与彩色徽标视觉规范

节点类型与优先级直接以彩色徽标标注在节点文本左侧（badges.ts 自定义渲染模块绘制，kity 圆角色块 + 白色文字），三种模式（edit/review/plan）均可见：

| 类型值            | 含义          | 徽标文案 | 徽标颜色           |
| -------------- | ----------- | ---- | -------------- |
| `normal`       | 普通节点（无特殊标记） | 不显示  | —              |
| `precondition` | 前置条件        | 前置   | 蓝 `#409EFF`   |
| `step`         | 执行步骤        | 步骤   | 绿 `#67C23A`   |
| `expected`     | 预期结果        | 预期   | 橙 `#E6A23C`   |
| `case`         | 测试用例        | 用例   | 紫 `#A464FF`，另显示优先级徽标 |

优先级徽标文字直显 `P0`-`P3`：

| 优先级  | 徽标颜色         |
| ---- | ------------ |
| `P0` | 红 `#F56C6C` |
| `P1` | 橙 `#E6A23C` |
| `P2` | 蓝 `#409EFF` |
| `P3` | 灰 `#909399` |

实现约束：不借用 core 内置 priority/resource 渲染（前者只能显示数字 1-9，后者颜色随机不可控），注册时以同名模块覆盖禁用内置 PriorityModule（其对 `'P0'` 字符串判真会渲染隐形占位图标）；落库字段格式不变（仍为 `'P0'-'P3'` 与原 type 枚举）；徽标模块须在 `new Minder` 之前注册（`Module.register` 是全局模块池）。

### 4.2 数据来源与节点结构

文档的脑图数据以嵌套的根节点对象形式从后端获取。根节点是创建文档时自动生成的默认节点，其 `children` 数组包含用户在脑图中创建的所有子节点，每个子节点可继续嵌套。

**后端接口响应格式**（`GET /api/project/documents/:docId/nodes`）：

```json
{
  "node": {
    "id": 1,
    "parentId": null,
    "type": "normal",
    "title": "登录用例集",
    "priority": null,
    "sortOrder": 0,
    "version": 1,
    "children": [
      {
        "id": 2,
        "parentId": 1,
        "type": "case",
        "title": "邮箱登录",
        "priority": "P1",
        "sortOrder": 0,
        "version": 1,
        "children": [
          {
            "id": 3,
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
  "layout": { "...": "..." }
}
```

评审快照树（`GET /api/project/reviews/:id/modules?documentId=`）和计划快照树（`GET /api/project/plans/:id/modules?documentId=`）的节点部分采用相同结构，额外包含 `originalNodeId`、`isAssociated`、`lastMark` / `lastResult`、`lastReviewerId`、`lastReviewedAt` 等快照特有字段；评论不挂在节点上，按节点经记录接口查询（见第 9 节）。

### 4.3 kityminder 节点数据结构（统一模型）

```json
{
  "data": {
    "id": "node-uuid",
    "text": "登录用例集",
    "type": "case",
    "priority": "P2",
    "layout_default_offset": { "x": 40, "y": -12 }
  },
  "children": [...]
}
```

各模式写入节点 `data` 的字段：

| 模式         | 字段                                                                                                       |
| ---------- | -------------------------------------------------------------------------------------------------------- |
| edit       | `id`、`text`、`type`、`priority`，以及自由拖拽产生的 `layout_*_offset`（键名由 core 按模板决定）                              |
| review     | `id`、`originalNodeId`、`text`、`type`、`priority`、`isAssociated`、`lastMark`、`reviewStatus`（由 `lastMark` 派生）、`relatedBugIds`（恒为空数组，见第 8 节） |
| plan       | `id`、`originalNodeId`、`text`、`type`、`priority`、`isAssociated`、`lastResult`、`executionStatus`（由 `lastResult` 派生）、`relatedBugIds`（恒为空数组） |

评论、评审人、评审时间等明细不写入节点 `data`，由评审记录接口按节点返回（见 9.1）。

### 4.4 三种模式的数据来源与操作目标

| 模式         | 数据来源                                                            | 可编辑字段                            | 操作目标表                                                                |
| ---------- | --------------------------------------------------------------- | -------------------------------- | -------------------------------------------------------------------- |
| **edit**   | `GET /api/project/documents/:docId/nodes` 返回的嵌套根节点与布局 JSON    | 全部字段（type, priority, text 等）     | `test_case_node`（通过 WebSocket 文本帧异步持久化）                             |
| **review** | `GET /api/project/reviews/:id/modules?documentId=` 返回的快照节点树     | `lastMark`（标记）                   | `test_review_node_snapshot`（更新 last_mark），`test_review_record`（新增标记/评论记录） |
| **plan**   | `GET /api/project/plans/:id/modules?documentId=` 返回的快照节点树       | `lastResult`（执行结果）               | `test_plan_node_snapshot`（更新 last_result），`test_plan_execution_record`（新增记录） |

在评审/计划模式下，`type`、`priority`、`text` 等原始用例字段为只读，来自快照表，不可通过脑图修改。评审/计划模式下的状态字段由后端在加载快照树时填充，前端提交标记后即时更新本地节点并刷新高亮。三个组件均另支持"移除用例"（评审/计划），由详情页传入 `removable` 控制，改写规划用例列表后重刷快照：`PUT /api/project/reviews/:id/cases`、`PUT /api/project/plans/:id/cases`。

---

## 5. 工具栏设计

工具栏位于脑图画布顶部，卡片顶栏形态（与画布以分隔线相接、不随画布滚动），集中呈现该模式下的核心操作，各组件拥有独立的工具栏：编辑工具栏单行左对齐、按功能域分组（分隔线相隔）、超宽时横向滚动不换行；评审/计划工具栏只有单个按钮组，水平居中、超宽时换行。

### 5.1 编辑（CaseMindMap）

| 分组   | 按钮                | 功能                | 快捷键             | 说明                                       |
| ---- | ----------------- | ----------------- | --------------- | ---------------------------------------- |
| 撤销重做 | 撤销 / 重做           | JSON-Patch 增量撤销重做  | Ctrl+Z / Ctrl+Y（或 Ctrl+Shift+Z） | 无可撤销/重做内容时按钮置灰                          |
| 节点操作 | 添加子节点（下级）         | 新增子节点，默认名称"分支主题"  | Tab             |                                          |
|      | 添加兄弟节点（同级）        | 新增兄弟节点，默认名称"分支主题" | Enter           |                                          |
|      | 编辑内容              | 选中节点原位编辑文本        | F2 / 双击        | 光标定位到文本末端，不全选                           |
|      | 删除节点              | 删除选中节点            | Delete          |                                          |
| 排版   | 布局模板下拉            | 切换 core 原生 6 模板：思维导图(default，缺省)/右侧分布(right)/组织结构(structure)/目录(filetree)/鱼骨图(fish-bone)/天盘(tianpan) | — | 走 `execCommand('template')`，可撤销，随文档持久化；按钮不回显模板名，当前模板经悬浮提示给出 |
|      | 整理布局              | 清除全部手动拖拽偏移，恢复自动排版 | Ctrl+Shift+L（core 原生） | 走 core `resetlayout`，按钮悬浮提示"清除手动拖拽的节点偏移，恢复自动排版" |
| 标记   | 用例 / 前置 / 步骤 / 预期 | 标记节点类型（色点 + 文案，选中态高亮） | —               | 标记为测试用例时无优先级则默认 P2；另有"取消标记"按钮恢复普通节点      |
|      | P0 / P1 / P2 / P3 | 设置优先级并自动标记为测试用例   | —               | 选中态以优先级色块高亮                              |
| 关联   | 关联需求               | 打开需求选择器，保存文档关联需求  | —               | 行末右靠，仅持 `requirement:view` 权限可见         |

缩放、定位、抓手、全屏不再占用工具栏，统一由导航器提供（见 5.4）。

**标记联动规则**：

- **标记为测试用例**：`type` 设为 `case`；若 `priority` 为空，自动设为 `"P2"`。
- **标记优先级（P0-P3）**：设置 `priority` 为所选值；若 `type` 不为 `case`，自动改为 `case`。
- **标记为前置条件/步骤/预期结果**：`type` 设为对应值；`priority` 同时清除（非用例节点不保留优先级）。
- **取消标记**：`type` 恢复 `normal` 并清除 `priority`。

### 5.2 评审（ReviewMindMap）

| 按钮         | 功能                                               | 说明                                   |
| ---------- | ------------------------------------------------ | ------------------------------------ |
| ✅ 通过       | 标记通过，提交至 `POST /api/project/reviews/:id/records` | 仅 case 节点可用（其他节点提示"仅用例节点可标记评审结果"），高亮当前评审结果 |
| ❌ 不通过      | 标记不通过，同上                                         | 同上                                   |
| ❓ 待评审      | 显式重置为待评审（`mark: 'pending'`，落库 last_mark 为 NULL）  | 任意节点可用，高亮"当前无标记"态                    |
| 💬 评论      | 打开评论抽屉，查看/添加评论（见第 9 节）                             | 需先选中节点，否则提示"请先选中一个节点"                |
| 🗑 移除用例    | 从评审中移除选中用例，走 `PUT /api/project/reviews/:id/cases` | 仅 `removable`（评审未完成）且选中已关联 case 节点时可用，执行前弹确认框 |

缩放与抓手由导航器提供（见 5.4，命令均 enableReadOnly，只读态可用）。

### 5.3 计划（PlanMindMap）

| 按钮         | 功能                                             | 说明                                   |
| ---------- | ---------------------------------------------- | ------------------------------------ |
| ✅ 通过       | 标记通过，提交至 `POST /api/project/plans/:id/records` | 仅 case 节点可用（其他节点提示"仅关联用例节点可标记执行结果"），高亮当前执行结果 |
| ❌ 失败       | 标记失败，同上                                        | 同上                                   |
| ❓ 阻塞       | 标记阻塞，同上                                        |                                      |
| 🔄 待执行     | 重置为待执行，同上                                      |                                      |
| 🗑 移除用例    | 从计划中移除选中用例，走 `PUT /api/project/plans/:id/cases` | 仅 `removable`（计划可调整用例）且选中已关联 case 节点时可用，执行前弹确认框 |

激活态按执行结果语义色高亮：通过 success、失败 danger、阻塞 `--color-blocked` 深红（EP 无内建阻塞档，覆盖按钮变量实现，避免与「失败」红撞色）、待执行 info。

缩放与抓手由导航器提供（见 5.4）。

### 5.4 导航器（MinderNavigator）

悬浮于画布左下角，三种模式共用（所接命令均 enableReadOnly，review/plan 只读态同样可用）；切换文档重建 minder 实例时随组件重建。

| 功能     | 实现                                                                 |
| ------ | ------------------------------------------------------------------ |
| 缩放条    | 放大/缩小按钮接 `zoomin`/`zoomout` 命令（kityminder-core 实际注册名，无连字符），滑条指示器监听 `zoom` 事件，按 core `zoom` 选项档位（默认 10/20/50/100/200）归一化映射位置（到达上下限时按钮置灰），点击原点走 `zoom` 命令回到 100% |
| 定位根节点  | `execCommand('camera', root, 600)` 平滑移动视野到根节点                        |
| 抓手     | core 原生 `hand` 命令，监听 `statuschange` 事件同步激活态高亮                        |
| 缩略图    | kity.Paper 缩略渲染（节点盒 + 连线路径 + 红框可视区域），监听 `layout`/`layoutallfinish`/`viewchange` 增量更新；点击/拖动缩略图移动画布视野；可开关，状态记忆于 localStorage（键 `minder-navigator-open`） |
| 全屏     | 原生 Fullscreen API 对脑图容器全屏（保留工具栏），零新增依赖                             |

---

## 6. 右键菜单设计

右键菜单仅在选中节点时展示（右键节点时先选中再弹出，空白处右键不弹菜单）；编辑模式下也可对选中节点按空格键唤醒，菜单定位到节点下方。关闭时机：点击菜单项、点击菜单外任意位置、按 Esc、滚轮滚动（视图变化后菜单会错位）；鼠标划过离开菜单不关闭。

### 6.1 编辑模式

- 新建下级节点（Tab）
- 新建同级节点（Enter）
- ──────────
- 复制（Ctrl+C）/ 剪切（Ctrl+X）/ 粘贴（Ctrl+V）：应用内节点剪贴板，跨文档可粘贴；无内容时"粘贴"置灰，根节点剪切被拒绝并提示"根节点不能剪切"
- ──────────
- 类型（chip 行）：用例 / 前置 / 步骤 / 预期 / 取消标记
- 等级（chip 行）：P0 / P1 / P2 / P3
- ──────────
- 删除节点（Delete）

编辑入口为双击、F2 或工具栏"编辑内容"，右键菜单不提供编辑项。

### 6.2 评审模式

- 标记评审结果 ▸ 通过 / 不通过 / 待评审
- 添加评论
- ──────────
- 从评审中移除（仅 `removable` 且选中已关联 case 节点时显示）

### 6.3 计划模式

- 标记执行结果 ▸ 通过 / 失败 / 阻塞 / 待执行
- ──────────
- 从计划中移除（仅 `removable` 且选中已关联 case 节点时显示）

菜单中没有"查看详情"项；快照只读（`minder.disable()`）只限制画布编辑，标记、评论与移除均照常可用。

---

## 7. 评审状态与执行状态展示

### 7.1 节点徽标

评审与执行回显徽标由 `badges.ts` 的右侧渲染器绘制（读取节点 `data.lastMark` / `data.lastResult`，与左侧类型/优先级徽标区分），数据由后端在快照树中填充：

- 评审标记：`✓ 通过`（绿 `#67C23A`）、`✕ 不通过`（红 `#F56C6C`）
- 执行结果：`✓ 通过`（绿 `#67C23A`）、`✕ 失败`（红 `#F56C6C`）、`⚠ 阻塞`（深红 `#C45656`，同 `docs/05-interaction-design/03-visual-design.md` 第 7 节 `--color-blocked`，与失败红以深浅 + 符号 + 文案区分）

待评审 / 待执行是默认态，不渲染徽标以免满屏噪音；编辑模式节点无 `lastMark` / `lastResult`，自然不渲染。

### 7.2 明细查看

画布节点不提供悬停 Tooltip。评审标记与评论明细在评论抽屉中按记录查看（见 9.1），执行进度与结果明细在评审/计划详情页的进度区查看。

---

## 8. 关联 Bug 标签与跳转

脑图画布当前不渲染关联 Bug 链接标签，节点 `data` 中的 `relatedBugIds` 由 `adapter.ts` 恒置为空数组，无 `foreignObject` 扩展模板、无节点内点击跳转。

组件层预留跳转能力：`ReviewMindMap` / `PlanMindMap` 通过 `defineExpose` 暴露 `openBug(bugId)`，实现为 `window.open('/workspace/projects/bugs/' + bugId, '_blank')` 在新标签页打开缺陷详情页（对应 `useReviewDetail` / `usePlanDetail` 的 expose 契约，当前无画布调用方）。缺陷关联信息在缺陷管理与详情页查看。

---

## 9. 评论功能（评审模式）

### 9.1 评论抽屉

- 打开方式：工具栏"💬评论"按钮或右键"添加评论"；需先选中节点，否则提示"请先选中一个节点"。
- 抽屉标题为"评审记录"（宽 380），打开时按当前节点拉取 `GET /api/project/reviews/:id/nodes/:nodeId/records`，同时展示标记与评论两类记录：作者、操作标签（标记通过 / 标记不通过 / 重置待评审）、评论内容、时间；无记录时显示空态"暂无评论或标记记录"。
- 底部为多行输入框（500 字上限、实时字数），Enter 发送，内容为空时发送按钮禁用。
- 提交经 `POST /api/project/reviews/:id/records`（`operationType='comment'`），成功后清空输入并重新拉取该节点记录列表刷新展示。

### 9.2 评论同步

评论不走 Yjs：打开抽屉与每次提交后均重新拉取该节点的记录列表，多用户先后打开即可看到彼此的评论与标记。

---

## 10. 初始化加载流程

### 10.1 编辑模式加载

1. 用户点击模块树中的文档节点（`ProjectModuleTree`），页面切换 `selectedDocId`。
2. 切换文档时先冲刷未落库的持久化、销毁旧 Yjs 连接与编辑内核实例，再调用 `GET /api/project/documents/:docId/nodes` 获取嵌套的根节点对象和布局 JSON（`{ template, offsets }`）。
3. 前端将嵌套的根节点对象经 `caseNodeToKm` 转换为 kityminder JSON（根节点作为画布中心节点，其 children 递归展开），并按 offsets 把各节点的 `layout_*_offset` 偏移回填进节点 data。
4. 加载引擎（script 标签加载 kity/kityminder-core 并注册徽标模块）后创建 `KMEditor`（`enableKeyReceiver: false`），`importJson` 渲染脑图，应用布局 JSON 中的模板（缺省 `default` 思维导图）与节点偏移，主题 `fresh-blue`。
5. 绑定 `selectionchange` / `contentchange` 事件并建立持久化基线，下一帧建立 WebSocket 连接加入文档房间（`/ws/documents/{docId}?token=`）。

### 10.2 评审模式加载

1. 用户进入评审详情页并在左侧快照树选择文档（页面级空态提示"请在左侧选择一个文档"）。
2. 前端调用 `GET /api/project/reviews/:id/modules?documentId=`，获取裁剪后的快照树（嵌套根节点数组）；不传 `documentId` 时后端返回多文档多根，组件仅取首个。
3. 经 `reviewNodeToKm` 转换为 kityminder JSON 渲染只读脑图（模板 `default`，主题 `fresh-green`），随后 `minder.disable()` 禁用画布编辑；快照为空时渲染根节点"空快照"。
4. 工具栏为评审操作按钮（见 5.2）。
5. 不建立 WebSocket 连接，标记和评论通过 HTTP 提交。

### 10.3 计划模式加载

1. 用户进入计划详情页并在左侧快照树选择文档（同 10.2 的页面级空态）。
2. 前端调用 `GET /api/project/plans/:id/modules?documentId=`，获取裁剪后的快照树（嵌套根节点数组）。
3. 经 `planNodeToKm` 转换为 kityminder JSON 渲染只读脑图（模板 `default`，主题 `fresh-purple`），随后 `minder.disable()`；快照为空时渲染根节点"空快照"。
4. 工具栏为执行操作按钮（见 5.3）。
5. 不建立 WebSocket 连接，执行结果通过 HTTP 提交。

### 10.4 状态分支

| 状态        | 表现                                                              |
| --------- | --------------------------------------------------------------- |
| 加载中       | 容器级 `v-loading` 遮罩；初始化带竞态令牌，切换/卸载期间的过期结果直接丢弃                 |
| 加载失败      | `ElMessage.error` 提示（默认"加载脑图失败"），画布保持空                      |
| 未选择文档/快照  | 页面级 `el-empty` 占位（"请在左侧模块树中选择一个文档" / "请在左侧选择一个文档"），不渲染脑图       |
| 空快照       | 渲染根节点文案"空快照"                                                    |
| WS 断线重连   | 编辑组件顶部横幅"连接已断开，正在重连..."，在线用户头像隐藏；恢复 connected 后横幅消失并冲刷落库      |
| 命令不可用     | 撤销/重做无历史时置灰；剪贴板为空时"粘贴"菜单项置灰；移除用例按钮在非 `removable` 或非已关联 case 时置灰 |
| 提交失败      | 标记、评论、移除、需求关联等操作失败均 `ElMessage.error` 提示，成功给 `ElMessage.success` |

---

## 11. 实时协作设计（基于 Yjs）

### 11.1 协作数据模型

- 使用单个 Y.Doc 存储文档状态，`ydoc.getMap('mindmap')` **按节点分片**存储，保证 CRDT 合并与网络传输的粒度为单个节点：
  - `n:<nodeId>`：节点数据快照（kityminder `node.data` 全量字段：text/type/priority/expandState/自由拖拽偏移等）；
  - `order`：嵌套 `Y.Map<父节点id, Y.Array<子节点id>>`，维护各父节点下的兄弟顺序；
  - `root` / `template` / `theme`：根节点 id 与布局模板标量键。
- 本地 `contentchange` 后，将画布 `exportJson()` 结果与 Yjs 当前分片状态做树级 diff，**仅写入变化的分片**（单 `transact` 原子提交），网络只传输增量而非全量 JSON。

### 11.2 冲突处理与远端回放

- CRDT 合并粒度 = 节点分片：不同节点的并发编辑互不覆盖；同一节点字段的并发修改以最后写入胜出；同一父节点下的并发结构变更由 `Y.Array` 位置合并算法收敛。
- 远端事务经 `observeDeep` 通知后，从分片重建目标 JSON，与本地画布树**按节点 id 对齐 diff**，翻译为内核原语增量应用：结构先摘除（`removeNode`）后新增（`createNode`，含子树递归插入，按 index 定位），再写属性（`setData` / 删除 data 键），模板与主题变化走 `useTemplate` / `useTheme`，最后一次 `refresh` 统一收尾；全程不整树重建、保留未受影响节点的 DOM 与选中态；diff 无法对齐（如根节点不一致）或应用异常时，兜底回退 `importJson` 全量重建。回放期间 `applyingRemote` 门闩置位（不入撤销栈、不触发落库），结束后以画布实况重置持久化基线，避免把远端改动重复提交。
- 内核无原子 move 原语，移动类结构变更以「摘除 + 重插」表达，仅重建被移动子树的 DOM。

### 11.3 协作感知

- 由 `y-websocket` 的 `WebsocketProvider` 连接 `ws(s)://<host>/ws/documents`，房间名自动拼接为 `/ws/documents/{docId}`；浏览器 WebSocket 无法携带 Authorization 头，token 经查询参数供后端握手拦截器校验。
- 服务端广播 Yjs 更新：可写二进制帧（sync step2 / update）广播前校验 `case:edit` 权限，只读帧（sync step1、awareness）直接转发；服务端返回的 JSON 文本错误帧由前端解析并 `ElMessage.error` 展示。
- Awareness 只交换用户信息（名称、颜色），用于画布右上角的在线用户头像（仅显示其他用户，头像描边取该用户颜色）；**不在画布上渲染他人光标或选中态**。
- `status` 事件驱动断线横幅与在线头像显隐（见 10.4），重连成功后立即冲刷一次持久化。

### 11.4 编辑模式保存

编辑模式下不通过 HTTP 接口手动保存。Yjs 二进制帧仅做实时协同转发、不落库；节点增删改经同一 WebSocket 连接的 JSON 文本帧（`add_node`/`update_attrs`/`move_node`/`delete_node`/`update_layout`）提交，落库请求以 400ms 防抖合并，卸载、切换文档与重连时立即冲刷；后端 `DocumentPersistenceHandler` 与数据库现有节点差异化比对后更新 `test_case_node` 表，并整体 upsert `test_case_document.layout` JSON 列。远端变更由发起方负责落库，接收端仅对齐本地 diff 基线，不重复提交；服务端回推的错误帧（`{ type: 'error', message }`）经 `ElMessage.error` 展示。

---

## 12. 与后端交互总结

| 场景             | 模式     | 接口                                                        |
| -------------- | ------ | --------------------------------------------------------- |
| 加载编辑数据         | edit   | `GET /api/project/documents/:docId/nodes`                  |
| 实时协同 + 节点落库    | edit   | WebSocket `/ws/documents/{docId}`（文本帧 `add_node` / `update_attrs` / `move_node` / `delete_node` / `update_layout`） |
| 文档关联需求查询/保存    | edit   | `GET` / `PUT` `/api/project/documents/:docId/requirements` |
| 加载评审快照树        | review | `GET /api/project/reviews/:id/modules?documentId=`          |
| 提交评审标记/评论      | review | `POST /api/project/reviews/:id/records`                     |
| 查询节点评审记录       | review | `GET /api/project/reviews/:id/nodes/:nodeId/records`        |
| 移除评审用例         | review | `PUT /api/project/reviews/:id/cases`                        |
| 加载计划快照树        | plan   | `GET /api/project/plans/:id/modules?documentId=`            |
| 提交执行结果         | plan   | `POST /api/project/plans/:id/records`                       |
| 移除计划用例         | plan   | `PUT /api/project/plans/:id/cases`                          |

---

## 13. Vue 组件代码骨架

> 拆分后各组件模板独立，无 `mode` 分支；下方骨架为三组件模板要点的合并示意，标注了各段所属组件。

```vue
<template>
  <div v-loading="loading" class="mindmap-container">
    <!-- 断线横幅（CaseMindMap） -->
    <div v-if="!isConnected" class="mindmap-disconnect-banner">连接已断开，正在重连...</div>

    <!-- 编辑工具栏（CaseMindMap）：单行左对齐，分组以分隔线相隔，行末命令组右靠 -->
    <div class="mindmap-toolbar">
      <div class="toolbar-group">
        <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
          <el-button text :disabled="!canUndo" @click="undo"><el-icon><RefreshLeft /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
          <el-button text :disabled="!canRedo" @click="redo"><el-icon><RefreshRight /></el-icon></el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-tooltip content="添加子节点 (Tab)" placement="bottom">
          <el-button text @click="addChild"><span>下级</span></el-button>
        </el-tooltip>
        <el-tooltip content="添加兄弟节点 (Enter)" placement="bottom">
          <el-button text @click="addSibling"><span>同级</span></el-button>
        </el-tooltip>
        <el-tooltip content="编辑内容 (双击节点/F2)" placement="bottom">
          <el-button text @click="editSelectedText" />
        </el-tooltip>
        <el-tooltip content="删除 (Delete)" placement="bottom">
          <el-button text @click="deleteNode" />
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-dropdown @command="switchTemplate">
          <!-- 模板名不回显占宽，仅经悬浮提示给出 -->
          <el-button text />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="t in templates" :key="t.name" :command="t.name">{{ t.label }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-tooltip content="清除手动拖拽的节点偏移，恢复自动排版" placement="bottom">
          <el-button text @click="tidyLayout" />
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-button text :class="['type-btn', { 'is-selected': selectedType === 'case' }]" @click="markAs('case')">用例</el-button>
        <el-button text @click="markAs('precondition')">前置</el-button>
        <el-button text @click="markAs('step')">步骤</el-button>
        <el-button text @click="markAs('expected')">预期</el-button>
        <el-tooltip content="取消标记，恢复普通节点"><el-button text @click="clearMark" /></el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-button v-for="p in ['P0', 'P1', 'P2', 'P3']" :key="p" text
          :class="['priority-btn', { 'is-selected': selectedPriority === p }]" @click="markPriority(p)">{{ p }}</el-button>
      </div>
      <div v-if="canManageRequirements" class="toolbar-group toolbar-group--end">
        <el-tooltip content="关联需求" placement="bottom">
          <el-button text @click="openRequirementSelector" />
        </el-tooltip>
      </div>
    </div>

    <!-- 评审工具栏（ReviewMindMap）：单组居中 -->
    <div class="mindmap-toolbar mindmap-toolbar--center">
      <el-button-group>
        <el-button :type="reviewResult === 'pass' ? 'success' : ''" @click="markReview('pass')">✅通过</el-button>
        <el-button :type="reviewResult === 'fail' ? 'danger' : ''" @click="markReview('fail')">❌不通过</el-button>
        <el-button :type="reviewResult === null ? 'info' : ''" @click="markReview(null)">❓待评审</el-button>
      </el-button-group>
      <el-button @click="openComments">💬评论</el-button>
      <el-button v-if="removable" :disabled="!canRemove" @click="removeSelectedCase">🗑移除用例</el-button>
    </div>

    <!-- 计划工具栏（PlanMindMap）：单组居中 -->
    <div class="mindmap-toolbar mindmap-toolbar--center">
      <el-button-group>
        <el-button :type="execResult === 'pass' ? 'success' : ''" @click="markExecution('pass')">✅通过</el-button>
        <el-button :type="execResult === 'fail' ? 'danger' : ''" @click="markExecution('fail')">❌失败</el-button>
        <el-button :class="execResult === 'block' ? 'exec-block-active' : ''" @click="markExecution('block')">❓阻塞</el-button>
        <el-button :type="execResult === 'untested' ? 'info' : ''" @click="markExecution('untested')">🔄待执行</el-button>
      </el-button-group>
      <el-button v-if="removable" :disabled="!canRemove" @click="removeSelectedCase">🗑移除用例</el-button>
    </div>

    <!-- 脑图画布（编辑模式下内核向容器注入 .km-receiver 接收器，双击节点进入编辑） -->
    <div ref="containerRef" class="minder-canvas" @contextmenu.prevent="onContextMenu" />

    <!-- 在线用户头像（CaseMindMap，画布右上角） -->
    <div v-if="onlineUsers.length" class="online-users">
      <el-avatar v-for="user in onlineUsers" :key="user.id" :size="24" :style="{ border: `2px solid ${user.color}` }" />
    </div>

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏，三模式共用 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 右键菜单壳：teleport 到 body，菜单项由各模式组件经 slot 传入 -->
    <MinderContextMenu v-if="menuVisible" :x="menuPos.x" :y="menuPos.y" @close="closeContextMenu">
      <!-- CaseMindMap：新建下级/同级 → 复制/剪切/粘贴 → 类型 chip 行 → 等级 chip 行 → 删除节点 -->
      <!-- ReviewMindMap：标记评审结果 ▸ 通过/不通过/待评审 → 添加评论 → 从评审中移除 -->
      <!-- PlanMindMap：标记执行结果 ▸ 通过/失败/阻塞/待执行 → 从计划中移除 -->
    </MinderContextMenu>

    <!-- 评论抽屉（ReviewMindMap）：评审记录列表 + 输入框 -->
    <el-drawer v-model="commentVisible" title="评审记录" :size="380" class="comment-drawer">
      <div class="comment-list">
        <div v-for="r in records" :key="r.id" class="comment-item">
          <strong>{{ r.reviewerName }}</strong>
          <el-tag v-if="r.operationType === 'mark'">{{ r.mark === 'pass' ? '标记通过' : r.mark === 'fail' ? '标记不通过' : '重置待评审' }}</el-tag>
          <p v-if="r.comment">{{ r.comment }}</p>
          <small>{{ formatDateTime(r.createdAt) }}</small>
        </div>
        <el-empty v-if="!records.length" description="暂无评论或标记记录" :image-size="48" />
      </div>
      <el-input v-model="newComment" type="textarea" :rows="2" maxlength="500" show-word-limit
        placeholder="输入评论，Enter 发送" @keydown.enter.prevent="addCommentFn" />
      <el-button type="primary" size="small" :disabled="!newComment.trim()" @click="addCommentFn">发送</el-button>
    </el-drawer>
  </div>
</template>
```

---

## 14. 实施要点

1. **组件选用**：页面按业务直接使用对应组件并传入 ID（CaseMindMap→`docId`；ReviewMindMap→`reviewId` + `documentId` + `removable`；PlanMindMap→`planId` + `documentId` + `removable`），各组件加载各自数据源；评审/计划组件通过 `marked` / `removed` 事件通知详情页刷新进度，并 `defineExpose` 暴露 `reload`（详情页同步快照后刷新画布）与 `openBug`。
2. **数据加载**：编辑模式调用文档节点接口获取嵌套根节点对象；评审/计划模式调用 `modules` 快照树接口（`documentId` 限定单文档，缺省时只取首个根）。前端转换为 kityminder JSON 后渲染。
3. **数据隔离**：快照模式（review/plan）完全不建立 WebSocket/Yjs 连接；编辑模式独占 `/ws/documents/{docId}` 房间，服务端对可写帧校验 `case:edit` 权限。
4. **权限口径**：画布组件不设 `editable` prop；"移除用例"按钮与菜单项由 `removable`（详情页按评审/计划状态传入）控制，"关联需求"按钮挂 `requirement:view` 权限；标记、评论、移除接口的操作权限校验由后端兜底。
5. **kityminder 扩展**：节点状态徽标由 `badges.ts` 以 `Module.register` 自定义渲染模块实现（写法参照 core 自身 priority 模块），左侧绘制类型/优先级、右侧绘制评审/执行标记，须在 `new Minder` 之前注册（loader 内统一完成）；画布不渲染 Bug 链接标签（见第 8 节）。
6. **工具栏状态响应**：监听 kityminder 的 `selectionchange` 事件，更新类型/优先级与标记按钮高亮；撤销/重做按钮置灰态由 history 的 onChange 回调驱动（`KMEditor` 的 `onHistoryChange`）。
7. **右键菜单**：`useContextMenu` 统一管理展示与关闭时机（仅选中节点时展示，编辑模式下空格键亦可唤醒），菜单项由各模式组件模板静态给出（slot），不设模式分支。
8. **抓手模式**：接 core 原生 `hand` 命令（enableReadOnly，只读态可用），由导航器切换并监听 `statuschange` 同步激活态。
9. **原位编辑接入**：编辑内核创建 minder 时传 `enableKeyReceiver: false` 禁用 core 内置接收器，由 receiver.ts 的 contenteditable 元素统一接管键盘；命令执行后 `fire('receiverfocus')` 保证快捷键持续可用。
10. **WebSocket 持久化**：同一连接上的 JSON 文本帧（`add_node` / `update_attrs` / `move_node` / `delete_node` / `update_layout`）为持久化通路，后端 `DocumentPersistenceHandler` 与数据库现有节点差异化比对后更新 `test_case_node` 表，并整体 upsert `test_case_document.layout` JSON 列；Yjs 二进制帧只做实时转发、不落库。布局帧结构为 `{ type: 'update_layout', payload: { template, offsets } }`，节点帧为 `{ type, payload: { data } }`。
11. **Bug 链接跳转**：组件暴露的 `openBug(bugId)` 以新窗口打开缺陷详情页（路径 `/workspace/projects/bugs/:bugId`）；画布当前不渲染链接标签。

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 按实现对齐组件架构与目录、工具栏/右键菜单项、快照树与评审记录接口路径、徽标与状态分支、Yjs 协作感知及实施要点 |

---

**文档结束**
