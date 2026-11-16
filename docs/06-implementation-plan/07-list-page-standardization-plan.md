# 软件测试平台——列表页样板与推广计划

**文档版本**：V1.0
**日期**：2026-11-15
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

以缺陷列表为样板，先完成设计基线、实现与人工验收，再提炼公共展示能力，分批推广至其他列表页。页面行为由交互设计定义，本计划记录实施路径、依赖与验收门禁。

### 1.2 范围

涉及交互设计、缺陷列表及后续列表模块的前端实现。沿用 Vue、Element Plus 与现有设计令牌，无新增依赖、API 或数据库变更。列显隐配置及记忆能力作为独立需求评估。

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
| F3-3a 环境管理 | F3-2 验收通过、F3-3a 方案已确认 | `api-testing/environment/EnvironmentPage.vue`、`useEnvironmentPage.ts`、`useEnvironmentPage.spec.ts`、`12-environment-ui-page.md` | 保持左侧列表+右侧详情主交互；环境名称可达性，列表失败 `ElMessage.error` 且无数据错误占位重试、有数据保留列表与选中/搜索并提供可见重试反馈；不改详情编辑器核心行为 | 实现与前端门禁完成，待用户人工验收 |
| F3-3b 函数管理 | F3-3a 验收通过、F3-3b 方案确认 | `api-testing/function/FunctionPage.vue`、必要子组件/spec 与交互文档 | 列表/名称可达性与加载失败反馈最小对齐 | 待启动 |
| F3-3c 公共组件 | F3-3b 验收通过、F3-3c 方案确认 | `api-testing/component/ComponentPage.vue`、必要子组件/spec 与交互文档 | 列表/名称可达性与滚动加载失败反馈最小对齐 | 待启动 |

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
- 当前记录：缺陷样板、评审与计划列表已验收通过；需求列表实现及门禁完成待用户验收，用例页本批仅评估。F1（报告、Mock、定时任务、系统用户/空间/文件 6 个标准分页表格页）已按确认方案实施并通过前端门禁，待用户人工验收；文件列表无独立交互文档，按样板行为实施。F2（接口管理、场景管理右侧列表区）已验收通过。F3 已按用户确认进入 **目标 B**（立项后按子批次编码）：范围与顺序为 `F3-0 → F3-1 项目卡片网格 → F3-2 成员管理 → F3-3 环境/函数/公共组件`，`DebugPage` 调试工作台排除本轮实现（仅评估结论），纳入页交互变更采取**文档先行**，E/F1 与 F3 可并行推进并各自独立验收；F3-0 文档立项已完成，F3-1、F3-2 已人工验收通过，F3-3a 文件级方案已确认并完成实现与前端门禁，当前待用户人工验收 F3-3a；验收通过后推进 F3-3b（函数管理）。

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
| 人工验收 | 待用户验收：环境名称悬停/键盘聚焦可见全文且选中正常；无数据时列表失败显示错误占位可重试；有数据时搜索/重载失败保留列表与选中/搜索上下文并提供可见错误反馈与重试；新建/编辑/复制/删除/排序/设为默认/导入导出及右侧详情编辑器行为无回归 |

- `e9b2dfc2`：📝 docs(frontend): 明确环境管理F3-3a交互口径。
- `14b9e1c1`：✨ feat(frontend): 对齐环境列表名称可达性与加载反馈。

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
