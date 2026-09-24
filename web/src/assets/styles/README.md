# Web 样式例外登记（CODE-008）

本登记覆盖 `web/src` 下的 Vue、SCSS 和 CSS 源码；`web/demos` 是演示资源，不纳入应用源码门禁。规范依据为 `docs/00-spec/10-engineering/01-frontend.md` §8 和 `docs/00-spec/50-ui/01-frontend-design.md` §6.2。

## 审计结论

- 初始扫描发现 101 处强制优先级文本命中；其中全局 Element Plus 桥接和局部组件覆盖均可在普通层叠或组件 CSS 变量中表达，已清理。
- 当前一方源码不再包含强制优先级声明。第三方包自身的声明不属于本目录的可编辑范围，不通过本地规则继续放大。
- 应用入口在 `web/index.html` 为 `body` 标记 `data-robotest-theme`。Element Plus 设计令牌桥接层统一挂在该标记下，以覆盖 `#app` 外的 Dialog、Drawer、Dropdown 等 Teleport 节点，同时不污染其他文档内容。
- 组件样式默认使用 `scoped`；需要命中子组件子树时使用带业务根节点的 `:deep()`，不使用无前缀的全局选择器。

## 登记的全局例外

| 位置 | 作用域 | 保留原因 | 约束 |
| --- | --- | --- | --- |
| `src/App.vue` | 文档级 reset、选区/焦点基线、Vue 过渡类 | 这些规则必须覆盖 Teleport 到 `body` 的节点，且属于应用入口基线 | 只能在此处维护入口级规则；新增规则须先更新本表和测试白名单 |
| `src/assets/styles/variables.scss` | `body[data-robotest-theme]` 下的 Element Plus 设计令牌桥接 | Dialog、Dropdown、Tooltip 等组件库节点不在 `#app` 内；该层是统一设计令牌而非页面覆盖 | 只能使用普通层叠或 CSS 变量；禁止加入新的强制优先级声明 |
| `src/components/project/functional-testing/case/CaseSelector.vue` | `.case-selector-dialog` | Element Plus Dialog 根节点 Teleport 到 `body`，不会带组件 scopeId | 选择器必须带该业务根类，不得改成裸 `.el-dialog` 覆盖 |
| `src/components/project/functional-testing/minder/ai/AiPreviewDialog.vue` | `.ai-preview-dialog` | 同上，且预览弹窗需要独立的可伸缩 body 布局 | 选择器必须带该业务根类 |
| `src/components/project/functional-testing/review/ReviewMindMap.vue` | `.comment-drawer .el-drawer__body` | Drawer Teleport 到 `body`，`scoped` 无法命中其子树 | 保留 `:global()` 仅限该抽屉类名和原因说明 |

## 变更约束

1. 新组件样式不得新增全局 `<style>`；组件库覆盖优先写在组件根类的 CSS 变量或局部 `:deep()` 中。
2. 必须使用强制优先级时，先确认组件库内部层叠无法通过 CSS 变量或局部选择器表达，再在源码注释和本表登记精确选择器、作用域、原因及规范引用。
3. `stylePolicy.spec.ts` 会阻止未登记的无 scoped 样式，并检查一方样式源码不重新引入强制优先级声明。

## 验证

```bash
pnpm exec vitest run src/assets/styles/stylePolicy.spec.ts
pnpm run build
```
