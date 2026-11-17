# 软件测试平台——列表页样板与推广计划

**文档版本**：V1.0
**日期**：2026-11-15
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

以缺陷列表为样板，先完成设计基线、实现与人工验收，再提炼公共展示能力，分批推广至其他列表页。页面行为由交互设计定义，本计划记录实施路径、依赖与验收门禁。

### 1.2 范围

涉及交互设计、缺陷列表及后续列表模块的前端实现。沿用 Vue、Element Plus 与现有设计令牌，无新增依赖、API 或数据库变更。列显隐配置及记忆能力作为独立子批次（见 5.16），偏好存浏览器 localStorage，不涉及后端存储。

### 1.3 基线文档

| 文档 | 用途 |
| ---- | ---- |
| `docs/00-spec/50-ui/01-frontend-design.md` | 视觉、可访问性与组件默认样式约束 |
| `docs/00-spec/50-ui/02-page-development.md` | 页面编排、展示组件职责与错误反馈 |
| `docs/00-spec/50-ui/03-scroll-container.md` | 高度链、固定表头与唯一滚动拥有者 |
| `docs/01-requirements/04-bug-management/02-srs-bug-management.md` | 查询、字段、分页和状态流转需求 |
| `docs/02-high-level-design/04-bug-management/02-hld-bug-management.md` | 双视图共用筛选与模块边界 |
| `docs/04-detailed-design/04-bug-management/02-project-workspace-bug.md` | 字段与接口契约 |
| `docs/05-interaction-design/03-visual-design.md` | 设计令牌与公共视觉基线 |
| `docs/05-interaction-design/04-bug-management/02-workspace-ui-bug.md` | 样板页布局及状态验收依据 |

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 样板页 | 经用户人工验收、作为后续列表展示基线的缺陷列表页 |
| 推广批次 | 在自身业务文档约束内复用样板展示能力的一组页面 |

## 2. 开发方案

### 2.1 理解与一致性评估

- 任务类型：前端视觉与交互重构、配套文档维护。
- 起始分支：`ui/redesign`；交付分支：`feature/list-page-standardization`。
- 需求保持缺陷查询、分页、状态流转及双视图筛选契约。
- 交互文档原固定 13 字符、详细设计及实现原固定 18 字符，与按列宽省略的目标存在差异；用户于 2026-11-15 确认以目标为准，同步修订两处设计。
- 用户同时确认连续工作区、静态卡片悬停反馈收敛，以及先样板验收再推广的实施边界。

### 2.2 实施方式

1. 先维护视觉、缺陷交互和详细设计基线，并通过文档门禁。
2. 探查缺陷页面、页面级 composable、公共分页、邻近列表与既有测试；输出精确文件变更方案，确认后编码。
3. 在缺陷模块内落实展示改造，优先使用组件 props、插槽和公开 CSS 变量，展示组件通过 props / emits 与页面交互。
4. 样板验收后对比评审与计划列表，按实际重复程度提取公共布局、工具栏或状态展示能力；公共能力不承载业务请求。
5. 推广批次各自核对模块设计；出现新增行为或冲突时先确认并更新对应文档。

### 2.3 探查记录与已确认文件方案

已查看缺陷列表、`useBugList`、`bugStatus`、公共 `ListPagination`、评审与计划列表，以及缺陷页面与 composable 的现有测试。当前分页组件已提供结果总数、档位、分页事件和换行布局，可直接复用。缺陷请求失败已有消息提示，但缺少页面内错误状态；页面测试目前主要检查源码结构，交互回归需覆盖实际行为。

缺陷列表和详情当前将「一般」映射为蓝色、「轻微」映射为灰色，激活状态使用蓝色，与视觉基线的语义色要求不一致；实现时统一到既有视觉基线，并同步消费方类型。业务状态转换保持现有契约。

以下路径相对于 `web/src/`，作为用户已确认并完成实施的样板批次编码方案：

| 文件 | 操作 | 职责 |
| ---- | ---- | ---- |
| `pages/project/bug/BugListPage.vue` | 修改 | 连续工作区、状态区域、分页与列表 / 看板编排 |
| `components/project/bug/BugListToolbar.vue` | 新建 | 快捷过滤、伸缩搜索、更多筛选、视图切换与提交操作，通过 props / emits 连接页面 |
| `components/project/bug/BugListTable.vue` | 新建 | 表格、列宽省略与全文提示、字段层级及行操作事件 |
| `composables/project/bug/useBugList.ts` | 修改 | 暴露首次加载与刷新失败状态、筛选判定和重试能力，保留查询与分页语义 |
| `composables/project/bug/bugStatus.ts` | 修改 | 集中维护类型明确的缺陷语义色映射，供列表和详情共用 |
| `composables/project/bug/useBugDetail.ts` | 修改 | 复用缺陷配色映射 |
| `components/project/bug/BugSidebar.vue` | 修改 | 对齐共享配色类型，支持轻微等级的 success 标签 |
| `pages/project/bug/BugListPage.spec.ts` | 修改 | 调整旧结构断言，验证空态与错误恢复入口等页面行为 |
| `composables/project/bug/useBugList.spec.ts` | 修改 | 验证失败保留数据、重试恢复、筛选分页及并发响应有效性 |

样板使用自有布局容器配合 Element Plus 默认组件，去掉缺陷页重复静态卡片后自然消除其全局悬停阴影影响；全局卡片覆盖的收敛放在公共能力批次，先清点使用方再单独验证。测试按行为必要性补充，布局与全文提示通过实屏及键盘验收，不以源码字符串检查替代视觉验收。

## 3. 开发计划

### 3.1 首批推广文件级方案（已确认并实施）

**理解**：本批为前端展示重构，分支沿用 `feature/list-page-standardization`。评审与计划保留页头、关联信息副行、进度、通过率、业务状态、创建弹窗及行操作，接入已验收样板的标题全文和加载反馈。无新增依赖、API、数据库或部署变更。

**一致性评估**：用户已确认推广标题按列宽省略与全文提示、首次失败 / 刷新失败恢复、普通空态 / 筛选空态区分；对应行为已写入评审和计划交互设计 1.1 节。查询仍为 status / keyword / pageNo / pageSize，响应仍为 list / total，上下文通过请求头传递。行操作状态及权限沿用模块设计。

**探查**：两页已有相同的页头、卡片高度链、筛选栏和 `ListPagination`，列表请求及业务操作位于页面；领域展示计算分别封装在 reviewListPresentation 与 planListPresentation。现有公共组件目录无列表工作区、标题全文或加载反馈组件。计划页测试主要为源码断言，评审页缺少独立页面交互测试；已有领域展示函数测试可继续复用。公共分页 change 事件在修改页容量时已将页码重置为 1。

**公共能力边界**：提炼插槽式工作区、标题入口及加载反馈三项纯展示能力。查询条件、请求序号、数据、错误、路由、权限与业务操作由各页面持有；表格组件仅接收数据及权限结果、发出操作事件。工具栏使用工作区插槽组合各页的 Element Plus 控件，不引入字段配置驱动的通用表单。

以下路径相对于 `web/src/`，为已确认并实施的文件范围：

| 文件 | 操作 | 变更说明 |
| ---- | ---- | ---- |
| `components/common/ListWorkspace.vue` | 新建 | 工具栏、反馈区、表体与分页插槽；维护连续工作区高度链、静态容器及窄屏布局，不接收业务查询 |
| `components/common/ListTitleLink.vue` | 新建 | 标题按列宽省略、悬停 / 聚焦全文提示及可见键盘焦点；通过事件交由页面导航 |
| `components/common/ListLoadFeedback.vue` | 新建 | 以明确展示模式区分首次错误、刷新错误、普通空态、筛选空态和加载反馈；文案由调用方提供，发出重试 / 重置 / 创建事件 |
| `components/project/functional-testing/review/ReviewListTable.vue` | 新建 | 评审表格展示与行事件，复用 reviewListPresentation 和公共标题入口 |
| `components/project/functional-testing/plan/PlanListTable.vue` | 新建 | 计划表格展示与行事件，接收完成 / 恢复权限结果，复用 planListPresentation 和公共标题入口 |
| `pages/project/functional-testing/ReviewListPage.vue` | 修改 | 编排工作区、工具栏、表格、反馈及分页；管理加载状态、重试和旧请求保护 |
| `pages/project/functional-testing/PlanListPage.vue` | 修改 | 同上，保留计划操作权限与创建流程 |
| `pages/project/functional-testing/ReviewListPage.spec.ts` | 新建 | 验证筛选分页、错误恢复、空态入口及重新发起 / 删除事件流程 |
| `pages/project/functional-testing/PlanListPage.spec.ts` | 修改 | 将源码断言改为交互测试，覆盖错误恢复、分页、空态、状态与权限双条件及操作事件 |
| `components/common/ListTitleLink.spec.ts` | 新建 | 验证标题完整可访问、键盘触发与事件转发；实际省略及 tooltip 布局由实屏验收 |

**实施与验收**：先完成公共展示组件并接入评审列表，再接入计划列表；各逻辑单元通过前端门禁后独立提交。错误序列测试需验证旧响应 / 旧错误不能覆盖新请求、刷新失败数据与总数保留、重试使用当前条件及页码。布局检查覆盖长标题、副行、窄屏换行、固定分页、操作列及键盘路径；两页均由用户人工验收后进入下一批。

### 3.2 批次与依赖

| 批次 | 依赖 | 工作包 | 验收重点 | 状态 |
| ---- | ---- | ---- | ---- | ---- |
| A 设计基线 | 用户确认目标 | 视觉与缺陷设计同步、登记计划、代码探查与编码方案 | 设计差异闭合、方案确认 | 已完成 |
| B 缺陷样板 | A 通过 | 缺陷列表布局、标题、字段层级、状态反馈与相关验证 | 现有查询和操作可用、长标题完整可查、滚动与窄屏稳定 | 已完成 |
| C 样板验收 | B 门禁通过 | 实屏检查、用户人工验收、反馈修复 | 用户确认样板视觉与关键路径 | 已完成 |
| D 首批推广 | C 人工验收通过 | 评审、计划列表；提炼实际公共能力 | 模块业务一致、公共组件无业务耦合 | 已完成 |
| E 用例及需求 | D 通过 | 用例列表区域、需求列表 | 树形及脑图等专用布局保留职责边界 | 需求列表实现及门禁完成，待用户验收；用例页本批仅评估 |
| F1 接口测试与管理端（标准分页表格） | E 通过 | 报告、Mock、定时任务、系统用户/空间/文件列表页 | 保留各页业务筛选、批量与行操作；统一标题可达性、加载与刷新失败恢复 | 实现与前端门禁完成，待用户人工验收 |
| F2 接口测试与管理端（模块树分栏） | 用户确认方案（F1 人工验收仍待完成） | 接口管理、场景管理右侧列表区 | 不改模块树与页签主交互，右侧列表局部对齐样板 | 已验收通过 |
| F3-0 专用布局立项 | F2 验收通过、用户确认目标 B 及默认范围 | F3 子批次范围、非目标、文档先行与验收口径落地 | `07-list-page-standardization-plan.md` 明确拆分与节奏；后续子批次按需先更交互文档 | 已完成（计划文档已立项） |
| F3-1 项目卡片网格 | F3-0 立项完成、F3-1 方案已确认 | `ProjectListPage.vue`、`ProjectCard.vue`、`useProjectListPage.ts`、对应 spec 与 `06-space-ui-project.md` | 保持卡片网格+滚动追加主交互；名称悬停/键盘聚焦可见全文，首屏/刷新/追加失败 `ElMessage.error` 且保留已加载数据与分页信息可重试（追加失败重试追加） | 已验收通过 |
| F3-2 成员管理（双表） | F3-1 验收通过、F3-2 方案已确认 | `MemberListPage.vue`、`MemberListPanel.vue`、`InvitationListPanel.vue`、`useMemberList.ts`、`useInvitationList.ts`、对应 spec 与 `05-space-ui-member.md` | 保持双卡片/双分页/权限与邀请业务行为；姓名/邮箱与邀请预览可达性，失败 `ElMessage.error` 且无数据空态重试、有数据保留列表与分页并提供可见重试反馈 | 已验收通过 |
| F3-3a 环境管理 | F3-2 验收通过、F3-3a 方案已确认 | `api-testing/environment/EnvironmentPage.vue`、`useEnvironmentPage.ts`、`useEnvironmentPage.spec.ts`、`12-environment-ui-page.md` | 保持左侧列表+右侧详情主交互；环境名称可达性，列表失败 `ElMessage.error` 且无数据错误占位重试、有数据保留列表与选中/搜索并提供可见重试反馈；不改详情编辑器核心行为 | 已验收通过 |
| F3-3b 函数管理 | F3-3a 验收通过、F3-3b 方案已确认 | `api-testing/function/FunctionPage.vue`、`useFunctionPage.ts`、`useFunctionPage.spec.ts`、`25-project-settings-ui-function.md` | 保持左侧列表+右侧详情/编辑主交互；函数名称可达性，列表失败 `ElMessage.error` 且无数据错误占位重试、有数据保留列表与搜索/标签/选中并提供可见重试反馈；不改编辑/启停/删除/助手核心行为 | 已验收通过 |
| F3-3c 公共组件 | F3-3b 验收通过、F3-3c 方案已确认 | `api-testing/component/ComponentPage.vue`、`useComponentPage.ts`、`useComponentPage.spec.ts`、`22-global-asset-ui.md` | 保持左侧列表+右侧详情与滚动加载主交互；组件名称可达性，列表失败 `ElMessage.error` 且无数据错误占位重试、有数据保留列表/搜索/类型页签/选中/滚动窗口并提供可见重试反馈；不改详情/编辑/复制/启停/删除/引入核心行为 | 已验收通过 |

每批文档变更执行 `bash scripts/validate-docs.sh`；前端变更执行 `bash scripts/validate-web.sh`，检查 lint、类型、单元测试、覆盖率及契约。测试聚焦筛选、分页、请求失败恢复、状态流转等关键行为，视觉细节通过实屏验收。

样板检查包含 1280、1440、1920 像素窗口宽度、浏览器缩放、长标题、空数据、筛选无结果、首次请求失败、刷新失败、键盘操作与列表 / 看板切换。每批单独提交并记录用户验收结果，人工验收未通过不得推广。

## 4. 风险与应对

| 风险 | 应对 |
| ---- | ---- |
| 工具栏换行破坏表格高度链 | 按剩余空间计算布局，检查固定表头、分页及表体滚动 |
| 全局卡片样式影响其他模块 | 清点悬停样式及使用方，优先局部落实，公共改动单独回归 |
| 展示拆分改变筛选或事件行为 | 保留页面级状态所有权，验证快捷过滤优先级及分页条件保留 |
| 公共抽象过早扩大复杂度 | 样板验收后依据至少两个实际使用场景提炼 |
| 各模块布局差异造成机械套用 | 每批独立核对业务文档，编辑器、看板及树形工作区按职责适配 |

变更按独立逻辑单元提交，可通过对应提交回退；不主动合并或推送。

## 5. 计划维护

- 每批完成后更新状态、验证结果、提交记录与人工验收结果。
- 文件级编码方案在实施前确认；新增业务能力独立评估。
- 当前记录：缺陷样板、评审与计划列表已验收通过；需求列表实现及门禁完成待用户验收，用例页本批仅评估。F1（报告、Mock、定时任务、系统用户/空间/文件 6 个标准分页表格页）已按确认方案实施并通过前端门禁，待用户人工验收；文件列表无独立交互文档，按样板行为实施。F2（接口管理、场景管理右侧列表区）已验收通过。F3 已按用户确认进入 **目标 B**（立项后按子批次编码）：范围与顺序为 `F3-0 → F3-1 项目卡片网格 → F3-2 成员管理 → F3-3 环境/函数/公共组件`，`DebugPage` 调试工作台排除本轮实现（仅评估结论），纳入页交互变更采取**文档先行**，E/F1 与 F3 可并行推进并各自独立验收；F3-0 文档立项已完成，F3-1、F3-2、F3-3a、F3-3b、F3-3c 已全部人工验收通过，F3 子批次全部完成（详见 5.14 汇总）；`DebugPage` 仅保留评估结论不纳入实现。E（需求列表）与 F1 仍待用户人工验收。用例页独立子批次已完成实施并人工验收通过（详见 5.15）：共享模块树三页全量统一、脑图加载失败最小对齐。

### 5.1 样板验证记录

| 验证项 | 结果与范围 |
| ---- | ---- |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过；lint、typecheck、无 any 检查及契约一致性通过；180 个测试文件、3572 条测试通过 |
| 覆盖率 | statements 87.26%、branches 88.09%、functions 74.34%、lines 87.26%，均达到门禁要求 |
| 生产构建 | `pnpm run build` 通过，构建器提示部分 chunk 超过 500 kB |
| 差异检查 | `git diff --check` 通过 |
| 浏览器组件检查 | 真实页面组件配合模拟数据，检查快捷筛选、翻页失败保留旧数据、重试、筛选空态重置、列表 / 看板切换、键盘焦点及标题全文提示 |
| 布局检查 | 模拟内容宽度 1040 / 1200 / 1680 / 480px 及 CSS 缩放 125% / 150%，页面容器无横向溢出、分页保持在容器内、表体可滚动；已修复操作列溢出及键盘聚焦不显示全文问题 |
| 人工验收 | 用户明确回复「已通过」，C 阶段验收门禁通过；未提供逐项验收明细。代理浏览器检查仍限于模拟数据组件预览，不作为真实后端业务链路的验证记录 |

### 5.2 提交与后续门禁

- `8caba22c`：📝 docs(frontend): 明确缺陷列表样板基线与推广计划。
- `18619eb7`：✨ feat(frontend): 打磨缺陷列表样板与错误恢复体验。
- C 阶段已通过，D 阶段评估评审、计划列表的公共能力与文件级方案。
- 评审与计划交互文档保留页头、关联信息副行、业务状态与行操作，并明确已确认的样板交互；文件级编码方案见 3.1，已获确认并完成实施。

### 5.3 首批推广验证与交付记录

| 验证项 | 结果与范围 |
| ---- | ---- |
| 公共组件与评审列表 | `bash scripts/validate-web.sh` 全部通过，182 个测试文件、3580 条测试通过；类型检查发现的可选 loading 参数问题已修复并重新通过完整门禁 |
| 计划列表与最终门禁 | `bash scripts/validate-web.sh` 全部通过，182 个测试文件、3590 条测试通过；lint、typecheck、无 any 检查与契约一致性通过 |
| 最终覆盖率 | statements 87.58%、branches 88.13%、functions 74.40%、lines 87.58%，均达到门禁要求 |
| 行为回归 | 两页首次失败重试、普通空态创建、筛选无结果重置、翻页失败保留数据及总数、旧请求隔离、卸载后错误隔离；评审重新发起 / 删除、计划完成 / 恢复 / 删除的确认流程及计划状态与权限组合 |
| 实屏范围 | 两页真实组件配合浏览器内模拟数据；内容宽度 1040 / 1200 / 1680 / 480px 均无页面与操作列溢出，分页保持在容器内、表体可滚动；标题聚焦可见且显示全文；翻页失败保留 20 行并展示错误与重试 |
| 计划额外检查 | 筛选无结果重置恢复列表；CSS 缩放 125% / 150% 下分页和容器稳定 |
| 生产构建及差异 | `pnpm run build` 与 `git diff --check` 通过；构建提示部分 chunk 超过 500 kB |
| 人工验收 | 用户明确回复「通过」，评审与计划列表人工验收门禁通过，未提供逐项验收明细。代理检查仍为模拟数据组件预览，不作为真实后端业务链路验证记录 |

- `f5e45956`：📝 docs(frontend): 明确评审计划列表交互与推广方案。
- `1a48fa5f`：✨ feat(frontend): 提炼列表展示组件并推广至评审列表。
- `5a387d7b`：✨ feat(frontend): 推广计划列表样板与权限交互验证。
- 用户验收重点：长标题、关联信息副行、筛选分页、创建与行操作、计划操作权限、窄屏及缩放体验。D 阶段人工验收通过后再进入 E 阶段。

### 5.4 用例与需求适用性评估

- 用例交互设计及 `TestCasePage` 均为模块树与脑图编辑器，没有独立分页列表；当前可评估范围为专用工作区布局，不宜直接套用分页工作区。是否本批只完成用例适用性评估、将树与脑图改造独立处理，待用户确认。
- 需求列表已有骨架屏、页面错误及重试、普通空态与筛选空态；查询需保留 1 秒防抖、筛选与页码记忆、权限及 AI 入口规则。已有请求序号保护及失败保留数据实现，可在现有逻辑上完善。
- 需求交互设计明确首屏骨架与刷新整卡遮罩；公共工作区默认仅遮罩表体，两者存在差异。实现保留需求现有 `el-card` 加载布局，复用 `ListTitleLink`、`ListLoadFeedback` 等展示能力。
- 需求标题支持键盘聚焦查看全文，筛选或翻页刷新失败保留已加载数据与分页信息并可重试；关键词失焦立即查询与防抖、筛选记忆、权限及 AI 入口需协同保持。用例页无独立分页列表，本批不做列表改造。

### 5.5 需求列表实施与验证记录

| 验证项 | 结果与范围 |
| ---- | ---- |
| 交互文档 | `docs/05-interaction-design/03-function-testing/07-requirement-ui.md` 已补充关键词失焦立即查询、标题键盘聚焦查看全文、刷新失败保留已加载数据与分页信息并可重试 |
| 前端实现 | 保留首屏骨架与整卡刷新遮罩；标题改用 `ListTitleLink`；刷新失败在卡片内展示可重试反馈并保留当前数据；关键词失焦立即查询；加载失败统一消息提示并失效卸载后请求 |
| 自动化验证 | `bash scripts/validate-web.sh` 全部通过（182 个测试文件、3593 条测试通过；覆盖率 statements 87.6%、branches 88.13%、functions 74.41%、lines 87.6%；lint、typecheck、无 any、契约检查通过） |
| 用例页 | 维持模块树与脑图专用工作区，本批仅完成适用性评估，无代码改造 |
| 人工验收 | 需求列表待用户验收（重点：关键词失焦/防抖/回车/清空、标题键盘聚焦全文、首屏错误重试、刷新失败保留数据与重试、筛选分页记忆、权限与 AI/导入入口、窄屏与滚动） |

- `a1e351f7`：📝 docs(frontend): 明确需求列表交互与E阶段实施范围。
- `19dc2767`：✨ feat(frontend): 优化需求列表标题可达性与刷新反馈。
- `f58fe971`：📝 docs(frontend): 回填需求列表实施验证记录。

### 5.6 F1 实施与验证记录

| 验证项 | 结果与范围 |
| ---- | ---- |
| 交互文档 | `11e8808e` 同步报告、Mock、定时任务、系统用户、系统空间交互文档（文件列表无独立交互文档，按样板行为实施） |
| 前端实现 | 报告/Mock/定时任务/系统用户/系统空间/文件列表统一标题键盘可达性、首屏错误与刷新失败保留已加载数据及分页信息可重试；定时任务 composable 补充 `hasLoaded`/`loadError` 与请求失效保护；保留各页筛选、分页、批量与行操作语义 |
| 定向验证 | `pnpm vitest run`：`ReportsPage.spec.ts`、`MocksPage.spec.ts`、`SchedulesPage.spec.ts`、`useSchedules.spec.ts`、`UserListPage.spec.ts`、`WorkspaceListPage.spec.ts`、`FileListPage.spec.ts` 通过 |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（含 lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | F1 各页面待用户人工验收（重点：标题悬浮/键盘聚焦全文、首屏失败重试、筛选/翻页刷新失败保留数据与重试、筛选分页/批量/行操作与权限行为、窄屏与滚动） |

- `03e3ec27`：✨ feat(frontend): 优化接口报告列表标题与刷新反馈。
- `84f47693`：✨ feat(frontend): 优化Mock列表标题可达性与刷新恢复。
- `25e87c8c`：✨ feat(frontend): 优化定时任务列表加载反馈与标题可达性。
- `e7261d88`：🐛 fix(frontend): 修正定时任务Cron预设字段引用。
- `8f505c9a`：✨ feat(frontend): 优化系统用户列表标题与刷新反馈。
- `6bc8c57d`：✨ feat(frontend): 优化系统空间列表标题与刷新反馈。
- `ee899265`：✨ feat(frontend): 优化文件列表标题与刷新反馈。

### 5.7 F2 实施与验证记录

| 验证项 | 结果与范围 |
| ---- | ---- |
| 交互文档同步 | 同步 `docs/05-interaction-design/05-api-testing/03-interface-ui-list.md`、`09-scenario-ui-list.md`：接口/场景名称支持键盘聚焦查看全文（场景徽标并列保留）；右侧列表首屏/刷新失败与无匹配结果反馈对齐样板，失败保留已加载数据与分页信息可重试；保留模块树、页签条、编辑器/导入/调试等跨页行为 |
| 前端实现 | `InterfacesPage` 接入请求失效保护、`ListTitleLink`/`ListLoadFeedback` 与首屏/刷新失败恢复，保留模块树、筛选 segmented/条件、`onActivated`+KeepAlive、`defineExpose`、批量/导入/调试与行操作；`ScenariosPage` 接入同类反馈与请求保护，名称与 GitLab 来源/源状态徽标并列，保留仓库更新条、筛选/视图/来源、关注/执行/复制/删除/批量与模块移动行为 |
| 定向验证 | `pnpm vitest run src/pages/project/api-testing/interface/InterfacesPage.spec.ts src/pages/project/api-testing/scene/ScenariosPage.spec.ts` 通过（InterfacesPage 3 条、ScenariosPage 9 条） |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」；F2（接口/场景右侧列表）人工验收门禁通过，未提供逐项验收明细 |

- `94eb87c2`：📝 docs(frontend): 明确接口场景列表F2交互口径。
- `730cdeec`：✨ feat(frontend): 优化接口列表标题与刷新反馈。
- `25f108d5`：✨ feat(frontend): 优化场景列表标题与刷新反馈。

### 5.8 F3 立项与子批次方案

**用户确认（2026-11-16）**：目标 B；范围与顺序 `F3-0 → F3-1 → F3-2 → F3-3`；F3-3 顺序 `环境(EnvironmentPage) → 函数(FunctionPage) → 公共组件(ComponentPage)`，逐个子批次单独验收；`DebugPage` 调试工作台排除本轮 F3 实现（仅保留评估结论）；纳入页涉及名称可达性/首屏与刷新失败保留数据可重试等样板行为时**先更新对应交互设计文档再实现**；E/F1 与 F3 可并行推进并各自独立验收。

| 子批次 | 范围（相对 `web/src/`） | 交互文档（先行核对/同步） | 对齐目标 | 非目标/约束 |
| ---- | ---- | ---- | ---- | ---- |
| F3-0 | `docs/06-implementation-plan/07-list-page-standardization-plan.md`（必要时补充评估记录） | 本计划文档 | 落地子批次、非目标、节奏与验收口径 | 不改业务页面代码 |
| F3-1 | `pages/workspace/ProjectListPage.vue`，必要时 `composables/workspace/useProjectListPage.ts` 及相关 spec | `docs/05-interaction-design/02-space-management/06-space-ui-project.md` | 卡片名称可达性（等价可聚焦全文）、首屏/刷新/追加失败反馈与空态区分、请求失效与保留已加载数据可重试 | 不改变卡片网格+滚动追加主交互、权限与 CRUD 语义；不引入分页表格 |
| F3-2 | `pages/workspace/MemberListPage.vue`、`components/workspace/MemberListPanel.vue`、`InvitationListPanel.vue`、相关 composables/spec | `docs/05-interaction-design/02-space-management/05-space-ui-member.md` | 成员/邀请表内名称等可达性与加载/失败重试反馈局部对齐 | 保持双卡片布局、各自分页/筛选、邀请与成员管理业务行为及既有文案规则 |
| F3-3a | `pages/project/api-testing/environment/EnvironmentPage.vue`、`EnvironmentDetailPanel.vue` 等必要文件/spec | `docs/05-interaction-design/05-api-testing/12-environment-ui-page.md`（及 11/13 视影响） | 左侧列表/名称可达性与列表加载失败反馈最小对齐 | 不改详情编辑器核心行为（新建态、未保存、连接测试、导入导出等） |
| F3-3b | `pages/project/api-testing/function/FunctionPage.vue`、必要子组件/spec | `docs/05-interaction-design/05-api-testing/25-project-settings-ui-function.md`（及 24 视影响） | 左侧列表/名称可达性与加载失败反馈最小对齐 | 不改函数编辑/启停/助手等核心行为 |
| F3-3c | `pages/project/api-testing/component/ComponentPage.vue`、必要子组件/spec | `docs/05-interaction-design/05-api-testing/22-global-asset-ui.md` | 左侧列表/名称可达性与滚动加载失败反馈最小对齐 | 保持窗口滚动加载与详情编辑行为 |

**非目标（本轮 F3）**：`api-testing/debug/DebugPage.vue` 及调试工作台主流程；场景/接口/报告等编辑器与详情/分享页；新增依赖、API 或数据库变更；机械套用 `ListWorkspace`/分页表格到专用布局。

**子批次节奏**：每批先确认方案 →（需时）先改交互文档并 `validate-docs` 提交 → 输出文件级方案并确认 → 实现与定向验证 → `bash scripts/validate-web.sh` 与 `git diff --check` → 提交 → 回填计划并 `validate-docs` 提交 → 请求人工验收。验收通过后进入下一子批次。

### 5.9 F3-1 项目卡片网格实施与验证记录

| 项目 | 结果与范围 |
| ---- | ---- |
| 交互文档先行 | `docs/05-interaction-design/02-space-management/06-space-ui-project.md` 明确项目名称可达性（悬停/键盘聚焦查看全文、活跃名称激活进入、归档名称仅查看）及首屏/刷新/追加失败的 `ElMessage.error`、已加载数据与分页信息保留、追加失败重试追加等口径 |
| 前端实现 | `ProjectCard.vue` 将名称改为 `el-tooltip`（hover/focus）+ 可聚焦按钮入口，活跃名称激活进入且避免重复触发，归档名称可聚焦查看全文不进入；`useProjectListPage.ts` 统一首屏/刷新/追加失败消息提示并保留状态，追加失败后 `retry()` 优先重试追加；`ProjectListPage.vue` 保持卡片网格、滚动追加、筛选/搜索、CRUD/权限与错误横幅重试行为 |
| 定向验证 | `cd web && pnpm vitest run src/pages/workspace/ProjectListPage.spec.ts src/composables/workspace/useProjectListPage.spec.ts` 通过（2 个文件，12 条测试） |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」（F3-1 项目卡片网格） |

- `cd58e521`：📝 docs(frontend): 明确项目列表F3-1交互口径。
- `3a628a5e`：✨ feat(frontend): 对齐项目列表名称可达性与加载反馈。

### 5.10 F3-2 成员管理实施与验证记录

| 项目 | 结果与范围 |
| ---- | ---- |
| 交互文档先行 | `docs/05-interaction-design/02-space-management/05-space-ui-member.md` 明确成员姓名/邮箱与邀请链接预览的悬停/键盘聚焦可达性，以及列表失败在无数据时空态重试、有数据时保留列表与分页并提供卡片内可见重试反馈的口径 |
| 前端实现 | `MemberListPanel.vue`、`InvitationListPanel.vue` 为可截断标识补充 `el-tooltip`（hover/focus）与可聚焦入口；在 `loadError` 且已有列表数据时展示卡片内错误反馈与“重新加载”并保留表格数据/分页；`useMemberList.ts`、`useInvitationList.ts` 逻辑保持（失败提示、请求失效保护、成功/失败状态流转与业务行为不变） |
| 定向验证 | `cd web && pnpm vitest run src/pages/workspace/MemberListPage.spec.ts src/composables/workspace/useMemberList.spec.ts src/composables/workspace/useInvitationList.spec.ts` 通过（3 个文件，40 条测试） |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」（F3-2 成员管理） |

- `b464f386`：📝 docs(frontend): 明确成员管理F3-2交互口径。
- `d06e5826`：✨ feat(frontend): 对齐成员管理名称可达性与加载反馈。

### 5.11 F3-3a 环境管理实施与验证记录

| 项目 | 结果与范围 |
| ---- | ---- |
| 交互文档先行 | `docs/05-interaction-design/05-api-testing/12-environment-ui-page.md` 明确环境名称可达性（悬停/键盘聚焦查看全文，点击列表项仍选中），以及列表加载失败在无已加载数据时错误占位重试、有已加载数据时保留列表与搜索/选中并提供可见重试反馈的口径 |
| 前端实现 | `EnvironmentPage.vue` 为列表名称补充 `el-tooltip`（hover/focus）与可聚焦入口；列表区按有/无已加载数据区分失败渲染：无数据保留错误占位+重试，有数据保留列表并显示错误反馈条+重新加载；`useEnvironmentPage.ts` 保持失败提示与状态流转（不清空已加载数据） |
| 定向验证 | `cd web && pnpm vitest run src/composables/project/api-testing/environment/useEnvironmentPage.spec.ts` 通过（1 个文件，79 条测试） |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」（F3-3a 环境管理） |

- `e9b2dfc2`：📝 docs(frontend): 明确环境管理F3-3a交互口径。
- `14b9e1c1`：✨ feat(frontend): 对齐环境列表名称可达性与加载反馈。

### 5.12 F3-3b 函数管理实施与验证记录

| 项目 | 结果与范围 |
| ---- | ---- |
| 交互文档先行 | `docs/05-interaction-design/05-api-testing/25-project-settings-ui-function.md` 明确函数名称可达性（悬停/键盘聚焦查看全文，点击列表项仍选中），以及列表加载失败在无已加载数据时错误占位重试、有已加载数据时保留列表与搜索/标签/选中并提供可见重试反馈的口径 |
| 前端实现 | `FunctionPage.vue` 为列表函数名补充 `el-tooltip`（hover/focus）与可聚焦入口；列表区按有/无已加载数据区分失败渲染：无数据保留错误占位+重试，有数据保留列表并显示错误反馈条+重新加载；`useFunctionPage.ts` 保持失败提示与状态流转（不清空已加载数据） |
| 定向验证 | `cd web && pnpm vitest run src/composables/project/api-testing/function/useFunctionPage.spec.ts` 通过（1 个文件，98 条测试） |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」（F3-3b 函数管理） |

- `7858019e`：📝 docs(frontend): 明确函数管理F3-3b交互口径。
- `e43c990c`：✨ feat(frontend): 对齐函数列表名称可达性与加载反馈。

### 5.13 F3-3c 公共组件实施与验证记录

| 项目 | 结果与范围 |
| ---- | ---- |
| 交互文档先行 | `docs/05-interaction-design/05-api-testing/22-global-asset-ui.md` 明确组件名称可达性（悬停/键盘聚焦查看全文，点击列表项仍选中），以及列表加载失败在无已加载数据时错误占位重试、有已加载数据时保留列表与搜索/类型页签/选中/滚动窗口并提供可见重试反馈、滚动加载失败沿用首尾行内重试的口径 |
| 前端实现 | `ComponentPage.vue` 为组件名称补充 `el-tooltip`（hover/focus）与可聚焦入口；列表区按有/无已加载数据区分失败渲染：无数据保留错误占位+重试，有数据保留列表并显示错误反馈条+重新加载，滚动首尾提示位与右栏面板保持不变；`useComponentPage.ts` 保持失败提示、不清空已加载数据与 `requestSequence` 失效保护（本次未改业务逻辑） |
| 定向验证 | `cd web && pnpm vitest run src/composables/project/api-testing/component/useComponentPage.spec.ts` 通过（1 个文件，41 条测试） |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（lint、typecheck、单元测试与覆盖率、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」（F3-3c 公共组件） |

- `df2cf333`：📝 docs(frontend): 明确公共组件F3-3c交互口径。
- `d91c1834`：✨ feat(frontend): 对齐公共组件列表名称可达性与加载反馈。

### 5.14 F3 专用布局子批次汇总

| 子批次 | 目标页 | 交互文档先行 | 定向验证 | 人工验收 |
| ---- | ---- | ---- | ---- | ---- |
| F3-0 立项 | 计划文档（无页面代码） | 本计划文档 | `validate-docs` 通过 | —（立项由用户确认目标 B） |
| F3-1 项目卡片网格 | `ProjectListPage.vue` / `ProjectCard.vue` | `06-space-ui-project.md` | 2 文件 12 测试通过 | 已验收通过 |
| F3-2 成员管理（双表） | `MemberListPage.vue` 及成员/邀请面板 | `05-space-ui-member.md` | 相关 spec 通过 | 已验收通过 |
| F3-3a 环境管理 | `EnvironmentPage.vue` | `12-environment-ui-page.md` | 1 文件 79 测试通过 | 已验收通过 |
| F3-3b 函数管理 | `FunctionPage.vue` | `25-project-settings-ui-function.md` | 1 文件 98 测试通过 | 已验收通过 |
| F3-3c 公共组件 | `ComponentPage.vue` | `22-global-asset-ui.md` | 1 文件 41 测试通过 | 已验收通过 |

**F3 结论**：专用布局（卡片网格、双表、接口测试主从/滚动列表）按各页既有主交互保留，未机械套用分页表格；样板行为仅在名称可达性（截断时悬停/键盘聚焦可见全文、焦点清晰、点击仍选中）与列表加载失败反馈（统一 `ElMessage.error`、无数据错误占位重试、有数据保留列表与上下文并提供可见重试、滚动失败沿用首尾行内重试）两处对齐，均采取交互文档先行。`DebugPage` 调试工作台按立项结论排除本轮 F3 实现，仅保留评估结论。各子批次均单独完成“方案确认 → 文档先行 → 实现与定向验证 → 前端门禁 → 提交 → 回填 → 人工验收”，逐批独立通过。所有前端变更经 `bash scripts/validate-web.sh` 与 `git diff --check` 通过；提交均在 `feature/list-page-standardization` 分支，未 push / 未合并 / 未提 PR。

**后续事项**：E（需求列表）与 F1（报告/Mock/定时任务/系统用户/空间/文件 6 页）仍待用户人工验收；上述各批与 F3 相互独立，可各自验收收尾。用例页经用户确认独立启动实施（见 5.15）。

### 5.15 用例页专用工作区实施记录

**用户确认**：用例页从“仅评估”推进到独立实施子批次；两项关键决策——① 共享模块树（`ProjectModuleTree`）按**全量统一**策略改动，接口管理/场景管理/测试用例三页统一生效（前两页需回归）；② 脑图编辑器加载失败**纳入最小对齐**（错误占位 + 重试，不改编辑/Yjs 协同核心）。

| 项目 | 结果与范围 |
| ---- | ---- |
| 评估结论 | 用例页 `TestCasePage.vue` 为纯编排页：左侧共享模块树（文档模式）+ 右侧 Yjs 脑图编辑器，非列表型页面；可对标点为模块树节点名称可达性、模块树加载失败反馈、脑图加载失败反馈；发现接口文档已要求“模块树拉取失败错误占位”但共享组件未实现，本次闭合该偏差 |
| 交互文档先行 | `04-workspace-ui-test-case.md` 补模块树名称可达性、加载失败（无数据错误占位重试/有数据保留树+可见重试/静默轮询不提示）与脑图加载失败错误占位+重试口径；`03-interface-ui-list.md`、`09-scenario-ui-list.md` 补共享模块树名称可达性与加载失败口径（接口原已有失败口径） |
| 前端实现 | `useProjectModuleTree` 增 `loadError`（失败置位不清空已加载树、成功清除、静默轮询成功自愈且失败静默）；`ProjectModuleTree.vue` 树节点名 `el-tooltip`+`tabindex` 可达性 + 无数据错误占位/有数据反馈条两种失败分态渲染（三页统一生效）；`useMindmapInit` 增 `loadError`（失败置位、成功清除、保留统一消息提示）、`CaseMindMap.vue` 编辑区失败占位 + [重试] 调用 `initMinder()` 且不展示误导空白画布；新增 `useProjectModuleTree.spec.ts`，补 `useMindmapInit.spec.ts`、`CaseMindMap.spec.ts` 用例，并回归接口/场景页 |
| 定向验证 | `pnpm vitest run` 覆盖 `useProjectModuleTree.spec.ts`（4）、`useMindmapInit.spec.ts`（34）、`CaseMindMap.spec.ts`（4）、`InterfacesPage.spec.ts`（3）、`ScenariosPage.spec.ts`（9），共 5 文件 54 测试通过 |
| 前端门禁 | `bash scripts/validate-web.sh` 全部通过（185 文件测试与覆盖率、lint、typecheck、无 any、契约一致性）；`git diff --check` 通过 |
| 人工验收 | 用户明确回复「验收通过」；覆盖模块树名称悬停/键盘聚焦可见全文且展开/选中/拖拽/重命名/删除正常、模块树加载失败分态正确且静默轮询不打扰、脑图加载失败错误占位+重试且编辑/协同/AI/关联需求无回归、接口/场景页模块树无回归。F4 独立子批次验收门禁通过，未提供逐项验收明细 |

- `c1a1047d`：📝 docs(frontend): 明确用例页及模块树F4交互口径。
- `41041f3a`：✨ feat(frontend): 对齐用例页模块树与脑图名称可达性及加载反馈。
- `8d1a9b12`：📝 docs(frontend): 回填用例页F4实施与验证记录。

### 5.16 列显隐配置与记忆能力立项与方案

**用户确认**：从 UI 设计优化建议 1（列显隐配置与记忆）启动；三项决策——① 覆盖 12 个标准列表页；② 偏好存浏览器 `localStorage`，按页面标识 + 用户标识隔离；③ 入口为工具栏 [列设置] 下拉勾选面板。交互口径见 `docs/05-interaction-design/04-table-column-settings.md`（先行新建）。

**一致性评估**：需求/交互/详细设计均无列显隐行为定义，计划文档 1.2 节仅标注“作为独立需求评估”，属文档未覆盖的新需求，按总则 6.2 先补交互文档再实现。

**范围清单（12 页）**

| 域 | 页面 | 列载体 |
| ---- | ---- | ---- |
| 缺陷（样板） | 缺陷列表 | `BugListTable.vue` |
| 功能测试 | 需求 / 评审 / 计划 | `RequirementListPage.vue` / `ReviewListTable.vue` / `PlanListTable.vue` |
| 接口测试 | 报告 / Mock / 定时任务 / 接口 / 场景 | `ReportsPage.vue` / `MocksPage.vue` / `SchedulesPage.vue` / `InterfacesPage.vue` / `ScenariosPage.vue` |
| 系统管理 | 用户 / 空间 / 文件 | `UserListPage.vue` / `WorkspaceListPage.vue` / `FileListPage.vue` |

**非目标**：专用布局页（项目卡片网格、成员双表、环境/函数/公共组件）、用例脑图工作区、`DebugPage`、弹窗选择器、详情/分享页内表格、概览与 AI 配置表格；不改数据查询/分页/筛选/排序/批量/行操作，不引入后端偏好存储与新依赖。

**技术方案**：新增公共 `useTableColumns.ts`（列可见性状态、`isVisible`/`toggle`/`selectAll`/`reset`、localStorage 读写与合法性校验、用户维度隔离、存储异常静默降级）与 `TableColumnSetting.vue`（工具栏按钮 + `el-popover` 勾选面板，含全选/恢复默认、不可隐藏列禁用勾选、键盘可达）；各页定义列元数据并给可隐藏列加 `v-if="isVisible(key)"`，选择列/操作列强制保留。

**推进方式**：按域分 4 个子批次，每批“复用公共能力 + 该域页面 + spec + 定向验证 + 门禁 + 提交 + 回填 + 人工验收”：① 公共能力 + 缺陷样板 → ② 功能测试域 → ③ 接口测试域 → ④ 系统管理域。全部完成后统一验收。

**实施结果（4 子批次全部完成）**

| 子批次 | 范围 | 交互文档先行 | 定向验证 | 提交 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | 公共能力 + 缺陷样板 | `04-table-column-settings.md` | `useTableColumns.spec.ts`（8）、`TableColumnSetting.spec.ts`（2）、`BugListPage.spec.ts`（3） | `5730c005` |
| 2 | 功能测试域（需求 / 评审 / 计划） | 沿用公共文档 | `useTableColumns.spec.ts`、`ReviewListPage.spec.ts`、`PlanListPage.spec.ts`（共 33 用例通过） | `113f81e1` |
| 3 | 接口测试域（报告 / Mock / 定时任务 / 接口 / 场景） | 沿用公共文档 | 五页 spec 定向通过（18 用例，含 F2 接口/场景模块树与列表回归） | `68a19b04` |
| 4 | 系统管理域（用户 / 空间 / 文件） | 沿用公共文档 | 三页 spec 定向通过（5 用例） | `0c459fc5` |

公共能力 `useTableColumns.ts`（列可见性状态、localStorage 读写与合法性校验、页面标识 + 用户标识隔离、异常静默降级）与 `TableColumnSetting.vue`（工具栏按钮 + 勾选面板、全选 / 恢复默认、不可隐藏列禁用、键盘可达）在子批次 1 落地；getUserId 后续改为可选并默认从 localStorage 与 auth store 同源读取，使各页接入不依赖 Pinia 实例、spec 无需补 auth mock。12 个标准列表页逐页定义列元数据并为可隐藏业务列加 `v-if`，选择列 / 收藏列 / 固定操作列作为 pinned 恒定展示。SchedulesPage 执行记录抽屉内表格、InterfacesPage / ScenariosPage 模块树与 F2 列表反馈均未改动。

| 项目 | 状态 |
| ---- | ---- |
| 交互文档先行 | 已完成（新建 `04-table-column-settings.md` 并登记索引，`validate-docs` 通过） |
| 公共能力实现 | 已完成（`useTableColumns.ts` + `TableColumnSetting.vue` + 各自 spec，含用户隔离 / pinned / 异常降级用例） |
| 各域页面接入 | 已完成（4 子批次，12 页全部接入） |
| 定向验证 / 前端门禁 | 已完成（各批定向用例通过；每批 `bash scripts/validate-web.sh` 全绿、187 测试文件、`git diff --check` 通过） |
| 人工验收 | 用户明确回复「验收通过」；12 页统一人工验收门禁通过，未提供逐项验收明细，未提供截图或录屏 |

- `520f9fdd`：📝 docs(frontend): 明确列显隐配置与记忆能力交互口径。
- `5730c005`：✨ feat(frontend): 实现列显隐配置公共能力并接入缺陷样板页。
- `113f81e1`：✨ feat(frontend): 列显隐配置接入功能测试域列表页。
- `68a19b04`：✨ feat(frontend): 列显隐配置接入接口测试域列表页。
- `0c459fc5`：✨ feat(frontend): 列显隐配置接入系统管理域列表页。

### 5.17 列表加载反馈口径统一立项与文档先行

**用户确认**：从 UI 设计优化建议 3（加载反馈口径统一）启动。探查发现全项目加载反馈模式多样（首屏骨架、整卡遮罩、表体遮罩、`ListLoadFeedback` 占位并存），5.4 节记录的需求页与样板页差异只是全局缺统一口径的缩影；用户确认先定全局口径文档、再分页渐近迁移，本批次只做文档、不改代码。

**一致性评估**：`01-frontend-design.md` UI-DS-04/05 只定义"状态必须完整""反馈及时且可解释"原则，未定义标准列表页的具体加载状态模型与遮罩范围，属文档未覆盖的口径缺口，按文档规范先补口径文档再迁移实现。

**口径要点**：新建平台级文档 `docs/05-interaction-design/05-list-loading-feedback.md`，定义 6 状态模型（首屏加载 / 刷新加载 / 首屏失败 / 刷新失败 / 普通空态 / 筛选空态）、统一口径与遮罩范围。核心口径以样板 `ListWorkspace` 为准：首屏骨架屏，刷新只遮罩表体、保留数据与分页、工具栏与分页条不遮罩；首屏失败错误+重试不显空态，刷新失败页内错误条+重试保留数据。

**待迁移项**：需求列表页（`RequirementListPage.vue`）刷新使用整卡遮罩，与统一口径不一致，登记为待迁移项，后续独立子批次迁移为表体遮罩并同步其交互设计 `docs/05-interaction-design/03-function-testing/07-requirement-ui.md` §2.1.3；迁移前该页与文档口径的不一致为已知中间态。

**迁移实施（批次 2）**：`v-loading` 从 `<el-card>` 移至 `<el-table>`，遮罩只覆盖表格数据区，工具栏、刷新错误条与分页条保持可见可交互；`<el-card>` 保留 `:aria-busy`，表格高度链与滚动拥有者不变。交互设计 §2.1.3 先行同步为表体遮罩口径。

| 项目 | 状态 |
| ---- | ---- |
| 交互文档先行 | 已完成（新建 `05-list-loading-feedback.md` 并登记索引；需求页 §2.1.3 同步表体遮罩口径） |
| 文档校验 | 已完成（`bash scripts/validate-docs.sh` 通过） |
| 代码迁移 | 已完成（需求页 `v-loading` 移至 `el-table`，表体遮罩对齐口径） |
| 定向验证 / 前端门禁 | 已完成（`bash scripts/validate-web.sh` 全绿、`git diff --check` 通过） |
| 人工验收 | 用户明确回复「验收通过」；刷新仅表体遮罩、工具栏与分页条可用、首屏骨架与刷新失败反馈不变，未提供逐项明细与截图 |

- `5a0f18e0`：📝 docs(frontend): 建立列表加载反馈统一口径文档并立项建议3。
- `c1d03e35`：✨ feat(frontend): 需求列表刷新遮罩由整卡迁移为表体对齐加载口径。
- `67e288b8`：📝 docs(frontend): 回填需求页遮罩迁移提交记录。
- `0575cb42`：📝 docs(frontend): 记录需求页加载反馈迁移验收通过并收尾建议3。

### 5.18 DebugPage 标准化立项与评估结论

**用户确认**：从 UI 设计优化建议 4（DebugPage 标准化）启动；范围取**最小对齐**——对齐 F3 样板行为中尚未覆盖的名称可达性，加载反馈已具备的不改，标签栏键盘可达（tabindex/role=tab/方向键切换）不在本轮。按「评估 → 文档先行 → 实现 → 验收」独立子批次推进。

**评估结论**（补齐立项时「仅保留评估结论」缺失的正文）：`DebugPage.vue` 调试工作台为专用布局页，非列表型页面，F3 立项非目标已明确保留其主流程与布局、不套用 `ListWorkspace`/分页表格。对照 F3 样板两处行为：① 列表加载失败反馈——`DebugHistoryView.vue` 已完整具备（`ElMessage.error` 统一提示、无数据错误占位重试、失败保留已加载记录与上下文、pendingPage 断点续传、空态区分），**已对齐无需改**；② 名称可达性——调试标签名 `debug-tab__label` 与历史记录名 `history__item-name` 均为省略号截断且无 tooltip/title，**为唯一待对齐点**。结论：本轮仅补这两处名称的可达性。

**一致性评估**：名称可达性口径已在 `docs/05-interaction-design/05-api-testing/03-interface-ui-list.md` 等列表页定义，但快速调试页交互设计 `docs/05-interaction-design/05-api-testing/06-quick-debug-ui-page.md` 未覆盖；属文档未覆盖的新行为，按文档规范先更新交互设计再实现。

**方案**：交互设计 06 补两处名称可达性口径（先行）；`DebugHistoryView.vue` 历史记录名包 `el-tooltip`（位于可聚焦 button 内，hover/focus 触发、焦点清晰、点击恢复/双击重命名行为不变）；`DebugPage.vue` 标签名位于不可聚焦的标签 div 内，仅包 hover `el-tooltip` 查看全文、不引入 tabindex 或键盘切换机制，点击/双击重命名行为不变；复用现有名称可达性样板写法，不改加载反馈、Tab 结构、请求/响应主流程，不新增依赖。

**实施记录（批次 2）**：`DebugHistoryView.vue` 历史记录名包 `el-tooltip`（hover + focus、`trigger-keys=[]`、focus-visible 轮廓），点击恢复/双击重命名事件仍绑在 button；`DebugPage.vue` 标签名包 hover-only `el-tooltip`（无 tabindex/键盘切换），切换与双击重命名事件仍在父级 div。两者复用 `ListTitleLink` 样板写法，element-plus tooltip 以 `OnlyChild` 透传属性、无额外 DOM wrapper，布局与事件链不变。新增 `DebugPage.spec.ts` 并扩充 `DebugHistoryView.spec.ts` 源码断言。

| 项目 | 状态 |
| ---- | ---- |
| 评估结论 | 已完成（补齐 5.18） |
| 交互文档先行 | 已完成（更新 `06-quick-debug-ui-page.md` 两处名称可达性口径并补建修改记录，`validate-docs` 通过） |
| 代码实现 | 已完成（历史记录名 hover+focus、标签名 hover-only，两项 tooltip） |
| 定向验证 / 前端门禁 | 已完成（两处 spec 7 项断言通过；`bash scripts/validate-web.sh` 全绿；typecheck、lint、无 any、覆盖率、契约检查通过；`git -c core.whitespace=cr-at-eol diff --check` 通过——`DebugHistoryView.vue` 为仓库唯一 CRLF 文件，历史沿用） |
| 人工验收 | 用户授权后续任务免逐项验收并按推荐执行；标签名悬停、历史记录名悬停与键盘聚焦、点击恢复/双击重命名/切换标签行为、tab 布局已随门禁交付，未见异常反馈 |

- `3c5bb18c`：📝 docs(frontend): 补齐 DebugPage 评估结论并先行同步名称可达性口径。
- `68b01c66`：📝 docs(frontend): 收窄 DebugPage 标签名为悬停可达并同步方案口径。
- `31c18680`：✨ feat(frontend): DebugPage 标签名与历史记录名补截断可达性。

### 5.19 减少动效偏好全局兜底（建议 7）

**用户确认**：从 UI 设计优化建议 7（减少动效偏好）启动，用户授权后续任务免逐项验收、按推荐执行。

**一致性评估**：`docs/00-spec/50-ui/01-frontend-design.md` UI-DS-08 与 §5.3 已规定"遵循用户的减少动效偏好"，属已有原则的实现落地，非新增行为，不需新建交互文档。探查发现 4 个页面有局部 `prefers-reduced-motion` 规则，但 54 个文件使用动画/transition 且无全局兜底，未适配页面在用户开启减少动效时仍会播放过渡。

**方案**：在全局桥接层 `web/src/assets/styles/variables.scss` 末尾新增顶层 `@media (prefers-reduced-motion: reduce)`，于 `body[data-robotest-theme]` 作用域内统一将 `transition-duration` / `animation-duration` 收紧到近似 instantaneous（`0.01ms`），并把 `animation-iteration-count` 归 1、`scroll-behavior` 改为 auto。该作用域覆盖 Teleport 到 body 的 Dialog/Drawer 节点；4 处已有的更精细局部规则保持优先，不受影响。不新增依赖、不改组件逻辑。

**实施记录**：`variables.scss` 末尾新增顶层 `@media (prefers-reduced-motion: reduce)`，`body[data-robotest-theme]` 作用域内统一收紧 `animation-duration`/`transition-duration` 至 `0.01ms`、`animation-iteration-count` 归 1、`scroll-behavior` 改 auto。因组件 scoped 过渡特异性高于普通全局规则，该块内使用 `!important` 属无障碍强制要求，已按 CODE-008 变更约束在 `style/README.md` 登记，并在 `stylePolicy.spec.ts` 精确放行（仅此块、其余位置仍禁止）且新增块存在性断言。4 处已有的更精细局部 `prefers-reduced-motion` 规则保持优先。

| 项目 | 状态 |
| ---- | ---- |
| 规范依据 | 已有（UI-DS-08 / §5.3），无需新建交互文档 |
| 全局兜底实现 | 已完成（`variables.scss` 顶层媒体查询 + CODE-008 登记 + stylePolicy 精确例外） |
| 定向验证 / 前端门禁 | 已完成（`stylePolicy.spec.ts` 7 项通过；`validate-web.sh` 全绿；`pnpm run build` 成功且产物 CSS 含兜底规则） |
| 验收 | 用户授权免逐项验收，按推荐执行 |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-11-15 | 建立缺陷列表样板、验收门禁与分批推广计划 |
| V1.0 | 2026-11-15 | 回填设计与样板完成状态、验证范围、提交记录及待人工验收事项 |
| V1.0 | 2026-11-15 | 记录用户样板验收通过，首批推广进入一致性评估并登记待确认的交互口径 |
| V1.0 | 2026-11-15 | 记录首批推广交互确认，补充公共展示能力边界、文件级方案及验证门禁 |
| V1.0 | 2026-11-15 | 回填评审与计划实现状态、最终门禁及实屏范围、提交记录和人工验收事项 |
| V1.0 | 2026-11-15 | 记录评审与计划人工验收通过，登记用例与需求的适用性及交互差异 |
| V1.0 | 2026-11-16 | 回填需求列表实施验证记录，明确用例页本批仅评估 |
| V1.0 | 2026-11-16 | 记录 F 阶段一致性评估与 F1 范围确认，同步 F1 相关交互文档 |
| V1.0 | 2026-11-16 | 回填 F1 标准分页表格列表实施、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 登记 F2 实施启动与接口/场景交互文档同步进展 |
| V1.0 | 2026-11-16 | 回填 F2 接口/场景右侧列表实现、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 记录 F2 人工验收通过 |
| V1.0 | 2026-11-16 | F3 立项：确认目标 B 与子批次范围/非目标/节奏，启动 F3-0 文档落地 |
| V1.0 | 2026-11-16 | 回填 F3-1 项目列表文档先行、实现、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 记录 F3-1 项目卡片网格人工验收通过 |
| V1.0 | 2026-11-16 | 回填 F3-2 成员管理文档先行、实现、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 记录 F3-2 成员管理人工验收通过 |
| V1.0 | 2026-11-16 | 回填 F3-3a 环境管理文档先行、实现、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 记录 F3-3a 环境管理人工验收通过 |
| V1.0 | 2026-11-16 | 回填 F3-3b 函数管理文档先行、实现、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 记录 F3-3b 函数管理人工验收通过 |
| V1.0 | 2026-11-16 | 回填 F3-3c 公共组件文档先行、实现、验证、提交与待人工验收记录 |
| V1.0 | 2026-11-16 | 记录 F3-3c 公共组件人工验收通过，新增 F3 专用布局子批次汇总 |
| V1.0 | 2026-11-16 | 用户确认用例页独立实施，新增评估与方案章节并先行同步三份交互文档口径 |
| V1.0 | 2026-11-16 | 回填用例页 F4 共享模块树与脑图加载反馈实现、定向验证、前端门禁与提交记录 |
| V1.0 | 2026-11-16 | 记录用例页 F4 人工验收通过，用例页独立子批次收尾 |
| V1.0 | 2026-11-16 | 列显隐配置与记忆能力立项：确认覆盖 12 页、localStorage 记忆、工具栏勾选面板，新建公共交互文档 |
| V1.0 | 2026-11-17 | 回填列显隐配置 4 子批次实施、验证与提交记录，12 页全部接入完成，待统一人工验收 |
| V1.0 | 2026-11-17 | 记录列显隐配置 12 页人工验收通过，该需求收尾 |
| V1.0 | 2026-11-17 | 列表加载反馈口径统一立项：新建平台级口径文档并登记索引，需求页整卡遮罩登记为待迁移项，本批次只做文档 |
| V1.0 | 2026-11-17 | 回填需求页加载反馈迁移：刷新遮罩由整卡改为表体，同步交互设计 §2.1.3，待前端门禁与人工验收 |
| V1.0 | 2026-11-17 | 记录需求页加载反馈迁移人工验收通过，建议 3 收尾 |
| V1.0 | 2026-11-17 | DebugPage 标准化立项：补齐 F3 遗留的评估结论正文，确认最小对齐范围与文档先行口径 |
| V1.0 | 2026-11-17 | 收窄标签名可达性为悬停查看全文（不引入 tabindex 与键盘切换），历史记录名保持 hover/focus |
| V1.0 | 2026-11-17 | 回填 DebugPage 名称可达性实现、定向断言、前端门禁与提交记录，待人工验收 |
| V1.0 | 2026-11-17 | 用户授权后续任务免逐项验收并按推荐执行，建议 4 据此收尾 |
| V1.0 | 2026-11-17 | 减少动效偏好全局兜底立项（建议 7），登记规范依据与实现方案 |
| V1.0 | 2026-11-17 | 回填建议 7 全局兜底实现、CODE-008 精确例外登记与前端门禁/构建验证 |
