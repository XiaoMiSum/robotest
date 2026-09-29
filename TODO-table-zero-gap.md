# 待办：表格与父容器左右间距为零

> 目标：将示例 HTML 中"表格与父容器左右间距为 0"的设计写入前端规范（UI-SC-10），并修改已有 Vue 页面对齐。

---

## 一、规范更新

### 1. 新增规则 UI-SC-10

- [ ] **`docs/00-spec/50-ui/03-scroll-container.md`**
  - 新增 `### 3.10 UI-SC-10：表格与父容器左右间距为零`
    - 规则正文：表格作为卡片或容器主内容时，左右贴合容器边缘，不留 padding/margin 间距；内容对齐通过单元格 padding 实现，不通过容器 padding 实现
    - 结构要求：容器（card）无 padding；工具栏 / 分页条各自独立 padding + 分割线；`overflow: hidden` 裁剪表格圆角
    - 正例代码（SCSS + Vue 模板片段）
  - 审查清单表（§4）新增 UI-SC-10 行
  - 规则登记段（§1.5）更新 `UI-SC-01～09` → `UI-SC-01～10`
  - 文末修改记录追加一行

### 2. 登记册同步

- [ ] **`docs/00-spec/00-governance/01-overview.md`**
  - 规则登记册表 `UI-SC-01～09` → `UI-SC-01～10`，主规范指向 `03-scroll-container.md`
  - 文末修改记录追加一行

### 3. 索引同步

- [ ] **`docs/00-spec/50-ui/00-readme.md`**
  - 确认 `03-scroll-container.md` 职责描述是否需补充"表格容器间距"，无需则跳过

### 4. 文档验证

- [ ] 运行 `node scripts/validate.mjs --docs`（Windows）确认无断链、元信息、规则编号错误

---

## 二、代码修改（12 个文件）

> 统一策略：`:deep(.el-card__body) { padding: 0; overflow: hidden; }` + 工具栏/分页独立 padding + 分割线

### 模式 A：el-card 无 #header，工具栏在 body 内

- [ ] **`web/src/pages/project/bug/BugListPage.vue`** — el-card > el-table + 分页（无工具栏，仅 table + pagination）
- [ ] **`web/src/pages/project/functional-testing/PlanListPage.vue`** — el-card > 工具栏 div + el-table + 分页 div
- [ ] **`web/src/pages/project/functional-testing/ReviewListPage.vue`** — el-card > 工具栏 div + el-table + 分页 div
- [ ] **`web/src/pages/project/functional-testing/RequirementPoolPage.vue`** — el-card > 工具栏 div + el-table + 分页 div
- [ ] **`web/src/components/admin/AiStatisticsTab.vue`** — el-card > el-table（无 header、无工具栏、无分页）

### 模式 B：el-card 有 #header slot，body 内为 el-table + 分页

- [ ] **`web/src/pages/project/api-testing/schedule/SchedulesPage.vue`** — #header(工具栏) > body: el-table + 分页
- [ ] **`web/src/pages/project/api-testing/mock/MocksPage.vue`** — #header(工具栏) > body: el-table + 分页
- [ ] **`web/src/pages/project/api-testing/report/ReportsPage.vue`** — #header(工具栏) > body: el-table + 分页
- [ ] **`web/src/pages/project/bug/BugDetailPage.vue`** — #header(标题"附件") > body: el-table
- [ ] **`web/src/components/admin/AiChatModelTable.vue`** — #header(标题 + 按钮) > body: el-table

### 模式 C：已有 `:deep(.el-card__body)` flex 覆盖，补充 padding: 0

- [ ] **`web/src/pages/project/api-testing/scene/ScenariosPage.vue`** — 已有 flex 覆盖，补充 `padding: 0; overflow: hidden`
- [ ] **`web/src/pages/project/api-testing/interface/InterfacesPage.vue`** — 已有 flex 覆盖，补充 `padding: 0; overflow: hidden`

### 不修改的文件

- `UserListPage.vue`、`MemberListPanel.vue`、`InvitationListPanel.vue` — 已使用自定义容器实现零间距
- `DashboardPage.vue`、`WorkspaceListPage.vue`、`WorkspaceDetailPage.vue` — 已使用自定义 `<section>` 容器
- `el-dialog` / `el-drawer` 内的表格 — 对话框内不需零间距
- 原生 `<table>`（key-value 表等） — 不在本次范围

---

## 三、验证

- [ ] 前端验证：`node scripts/validate.mjs --frontend`（lint + typecheck + test）
- [ ] 文档验证：`node scripts/validate.mjs --docs`
- [ ] 浏览器手动验收：逐页检查表格左右是否贴合卡片边缘

---

## 四、提交

- [ ] 规范更新单独提交：`📝 docs(ui): 新增 UI-SC-10 表格与父容器零间距规范`
- [ ] 代码修改按模块分批提交（functional-testing / api-testing / admin / bug）
