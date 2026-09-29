# 样式与组件覆盖例外登记

**文档版本**：V1.0
**日期**：2026-09-29
**状态**：起草中

---

## 1. 用途

本文件是 `docs/00-spec/50-ui/01-frontend-design.md` UI-DS-09 与 `docs/00-spec/10-engineering/01-frontend.md` 第 8 节的例外登记唯一入口：所有"用自定义 CSS 覆盖 Element Plus 组件内部结构或默认视觉行为"的保留项必须在此登记。

无例外登记的覆盖视为违规，代码审查应拒绝合并。

## 2. 登记规则

| 项 | 要求 |
| --- | --- |
| 作用域 | 覆盖必须挂在业务根类下（如 `.my-page :deep(.el-table)`），禁止无前缀全局选择器 |
| 原因 | 必须写明组件默认样式/配置属性/插槽/CSS 变量为何不满足 |
| 最小化 | 选择器限定到最小范围，不重写组件结构，只调整允许差异的视觉值 |
| 数量基线 | 存量数量只减不增；新增条目须先经开发者确认（UI-DS-09 中断确认） |
| 全局样式例外 | 无 `scoped` 的样式、`!important` 与 Teleport 根节点例外另行登记在 `web/src/assets/styles/README.md`（CODE-008），本文件不重复登记 |

## 3. 例外清单

### 3.1 存量批量例外：`:deep()` 命中 Element Plus 内部类

| 编号 | 范围 | 数量基线 | 保留原因 | 约束 |
| --- | --- | --- | --- | --- |
| EX-DS-001 | `web/src/**/*.vue` 中 `:deep()` 选择器命中 `.el-*`、`el-table`、`el-dialog`、`el-drawer` 等组件内部类 | 161 处选择器，53 个文件（2026-09-29 扫描） | 历史存量：早期页面在组件配置属性与 CSS 变量普及前直接微调内部样式；一次性重写风险大于收益 | 限定业务根类作用域；只允许视觉值调整，禁止结构性重写；新代码不得扩大该基线，重构触及文件时同步收敛 |

> 该批量条目覆盖的具体选择器以源码为准，不在本文件逐一罗列，避免双份事实源。收敛进度以第 4 节扫描命令复测。
>
> 成员管理页（`web/src/pages/workspace/MemberListPage.vue`）的表格与分页 Element Plus 覆盖统一由分栏容器 `.member-page__split` 下发（共 4 处），该页两张卡片组件不各自登记 `:deep(.el-*)`。此后超出 161 处 / 53 文件的新增一律拒绝，除非再次经中断确认（UI-DS-09）。

### 3.2 单项例外

暂无。新增单项例外按以下模板登记：

```markdown
| 编号 | 位置 | 选择器 | 保留原因 | 约束 | 登记日期 |
| --- | --- | --- | --- | --- | --- |
| EX-DS-0NN | web/src/.../Xxx.vue | `.xxx :deep(.el-yyy)` | 组件 props/插槽/CSS 变量不满足的具体说明 | 作用域与最小选择器约束 | YYYY-MM-DD |
```

## 4. 审查证据（扫描基线）

以下命令输出应与 3.1 基线一致；**超出基线即视为违规，审查必须拒绝**。

```bash
# 组件内部类覆盖数量与文件数
grep -rn ":deep(" web/src --include='*.vue' \
  | grep -cE ":deep\([^)]*(\.el-|el-table|el-dialog|el-drawer)"
grep -rl ":deep(" web/src --include='*.vue' | while read f; do \
  grep -qE ":deep\([^)]*(\.el-|el-table|el-dialog|el-drawer)" "$f" && echo "$f"; \
done | wc -l

# 强制优先级声明（目标为 0）
grep -rn "!important" web/src --include='*.vue' --include='*.scss' --include='*.css' | wc -l

# 已登记的全局样式例外由单测守护
cd web && pnpm exec vitest run src/assets/styles/stylePolicy.spec.ts
```

2026-09-29 基线结果：组件内部类覆盖 161 处 / 53 文件；`!important` 0 处；`stylePolicy.spec.ts` 通过。

## 5. 收敛要求

1. 新页面和新组件默认走 UI-DS-09 检查清单，不产生新的未登记覆盖。
2. 存量文件因功能改动被编辑时，顺带评估其 `:deep(.el-*)` 是否可降级为 props、插槽或 `--el-*` 变量，并更新本文件基线数。
3. 本文件基线数下调时同步更新第 4 节的日期与结果。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-24 | 初始版本 |
| V1.0 | 2026-09-29 | 复测扫描基线为 167 处 / 54 文件，纳入成员管理页经 UI-DS-09 中断确认的 2 处页签微调与 2 处存量漂移 |
| V1.0 | 2026-09-29 | 成员管理页双卡片重构后复测基线为 161 处 / 53 文件，该页覆盖收敛为 4 处并由分栏容器统一下发 |

---

**文档结束**
