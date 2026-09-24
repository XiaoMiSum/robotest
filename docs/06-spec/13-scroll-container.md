# 软件测试平台——前端滚动容器与滚动条规范

**文档版本**：V1.0  
**日期**：2026-09-24  
**状态**：已发布

---

## 1. 引言

### 1.1 编写目的

本规范定义前端页面中分区滚动、滚动条视觉隐藏、表格内部滚动和响应式降级的统一实现方式，避免多个子组件同时产生外层滚动条导致页面结构割裂。

本规范只约束滚动容器的布局职责与视觉表现，不改变组件的数据流、状态管理或业务逻辑。

### 1.2 适用范围

适用于 `web/` 下的 Vue 页面与组件，重点包括：

- 左右分栏、卡片分栏、标签页内容区等多区域布局。
- 原生滚动容器与 Element Plus 表格、滚动条组件。
- 需要固定表头、固定分页或固定操作区的长内容区域。
- 桌面端内部滚动与移动端页面滚动的响应式切换。

### 1.3 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 滚动容器 | 实际接收鼠标滚轮、触摸滑动或键盘滚动事件的元素。 |
| 滚动拥有者 | 负责管理某一区域纵向或横向滚动的唯一容器。 |
| 视觉滚动条 | 浏览器或组件渲染出来的滚动条 UI，不等同于滚动能力本身。 |
| 内部滚动 | 滚动发生在页面布局的子区域内部，页面外层不随内容滚动。 |
| 外层滚动 | 页面或布局容器作为滚动拥有者，子组件随页面一起移动。 |

### 1.4 与现有规范的关系

本规范补充 `docs/06-spec/03-frontend.md` 第 7 节的样式约定，并与 `docs/05-interaction-design/system-management/06-system-management-ui-role.md` 中“表头固定、内容区内部滚动、外层不产生纵向滚动条”的交互要求一致。

---

## 2. 总体原则

1. **先确定滚动拥有者，再处理滚动条样式。** 一个内容区域应尽量只由一个容器负责滚动。
2. **隐藏视觉滚动条不等于关闭滚动。** 必须保留 `overflow: auto`、`overflow-y: auto` 或组件自身的滚动能力。
3. **先建立高度约束，再启用内部滚动。** Flex/Grid 子项必须处理 `min-height: 0` 或同等约束，避免内容撑开父容器。
4. **表格优先使用组件原生滚动机制。** 不得为了隐藏滚动条而替换固定表头、固定列或分页结构。
5. **样式影响范围应与业务区域一致。** 禁止通过 `*`、`html`、`body` 或全局宽泛选择器隐藏所有滚动条。
6. **可发现性不能完全依赖滚动条。** 长列表应通过区域边界、固定控件、悬停/聚焦反馈或渐隐提示提供内容仍有剩余的信号。

---

## 3. 规范条目

### 3.1 SC1：建立可计算的高度链

需要内部滚动的区域必须处于明确的视口高度链路中。Flex/Grid 布局中的可伸缩子项必须设置 `min-height: 0`，并在页面或面板层级设置 `height: 100%`、`max-height` 或等效约束。

```scss
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.page__content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
```

**禁止**：仅设置 `overflow: auto` 而不给容器或其父级建立高度约束；这种情况下内容通常会继续撑开页面，不会产生预期的内部滚动。

### 3.2 SC2：一个区域只设置一个滚动拥有者

父容器和子容器不得无序地同时设置 `overflow: auto`。外层容器可以负责裁剪（`overflow: hidden`），但实际滚动应由最内层的内容容器或表格组件负责。

```scss
.table-pane {
  overflow: hidden;
}

.table-pane__body {
  height: 100%;
  min-height: 0;
}
```

**禁止**：外层面板和 `el-table` 同时设置纵向滚动，造成双滚动条、滚轮事件争抢或分页区域被推出视口。

### 3.3 SC3：隐藏滚动条时保留滚动能力

隐藏滚动条只能作用于滚动条视觉层，不能使用 `overflow: hidden` 代替滚动。原生长滚动容器应同时使用跨浏览器的隐藏声明：

```scss
.scroll-region {
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.scroll-region::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
```

说明：`overflow-y: auto` 保留滚轮、触摸和键盘滚动；其余声明分别覆盖 Firefox、旧版 Edge/IE 和 WebKit 内核浏览器。

### 3.4 SC4：限定隐藏范围

滚动条隐藏样式必须绑定到明确的页面或组件类名，禁止使用全局通配选择器，也禁止为了单个页面修改全局滚动条策略。

```scss
/* ✅ 只影响角色树 */
.role-tree {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.role-tree::-webkit-scrollbar {
  display: none;
}
```

```scss
/* ❌ 影响整个应用 */
* {
  scrollbar-width: none;
}

*::-webkit-scrollbar {
  display: none;
}
```

### 3.5 SC5：Element Plus 表格保留内部滚动

使用 `el-table` 时，应保留表格的固定表头、固定列和内部表体滚动能力。需要隐藏视觉条时，只针对表格内部的纵向 Element Plus 滚动条，不替换表格组件。

```vue
<el-table height="100%" class="data-table" :data="rows" />
```

```scss
.data-table {
  :deep(.el-scrollbar__bar.is-vertical) {
    display: none;
  }
}
```

**要求**：

- 保留 `height="100%"` 或等效的固定高度配置。
- 纵向滚动条可以隐藏；横向滚动条默认保留，避免用户无法访问被固定列遮挡的内容。
- 不得给表格外包容器再增加一层 `overflow: auto`。

### 3.6 SC6：固定控件与滚动表体分离

表头、筛选栏、分页条和操作栏属于固定控件，不得随表体内容滚动。固定控件使用 `flex-shrink: 0`，滚动表体使用 `flex: 1` 和 `min-height: 0`。

```scss
.pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.pane__toolbar,
.pane__pager {
  flex-shrink: 0;
}

.pane__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
```

### 3.7 SC7：提供滚动可发现性

完全隐藏滚动条时，区域仍须提供至少一种内容剩余提示：

- 区域边界、固定表头或分页条明确表明内容范围。
- 长列表支持键盘聚焦并可使用键盘滚动。
- 交互要求较高时，鼠标移入或键盘聚焦后显示细滚动条。
- 不得同时隐藏所有滚动提示、移除焦点样式并限制为鼠标交互。

```vue
<section class="scroll-region" tabindex="0" aria-label="内容列表">
  <!-- 内容 -->
</section>
```

### 3.8 SC8：响应式区域切换滚动策略

桌面端可以在已建立高度约束的面板内部滚动；移动端若布局改为上下排列或高度不再固定，应恢复页面级滚动，不要强行隐藏所有页面滚动提示。

```scss
@media (max-width: 768px) {
  .page {
    height: auto;
  }
}
```

### 3.9 SC9：不引入 JavaScript 滚动控制

仅为了隐藏滚动条或保持原生滚动能力，不得新增 JavaScript 滚轮监听、手写 `scrollTop` 同步或第三方滚动库。只有在存在业务级滚动同步、锚点定位或虚拟列表等明确需求时，才另行设计交互方案。

---

## 4. 系统管理角色管理页参考实现

角色管理页采用“左右分栏 + 右侧标签页 + 表格内部滚动”的结构：

```text
AdminLayout 内容区
└── RolePage（桌面端固定高度）
    ├── RoleTreePanel
    │   └── 角色树：原生 overflow-y: auto，隐藏纵向滚动条
    └── RoleDetail
        ├── 权限点 Tab
        │   └── PermissionTable：el-table 内部滚动，隐藏纵向滚动条
        └── 关联用户 Tab
            └── RoleUsersTable：el-table 内部滚动，分页固定底部
```

对应实现要求：

1. `RolePage` 负责页面高度链路和左右栏等高，不新增外层滚动。
2. `RoleTreePanel` 只负责角色树区域的原生纵向滚动和视觉隐藏。
3. `PermissionTable`、`RoleUsersTable` 保留 `el-table` 的固定表头与内部滚动，不在外层增加第二层滚动。
4. 关联用户分页继续固定在表格底部。
5. 桌面端隐藏纵向滚动条；响应式布局恢复页面级滚动。
6. 横向滚动条不隐藏，保证窄屏下仍可访问表格全部列。

---

## 5. 审查清单

| 编号 | 检查项 | 通过标准 |
| ---- | ---- | -------- |
| SC1 | 高度约束 | 内部滚动区域存在完整高度链路，Flex/Grid 子项处理 `min-height: 0`。 |
| SC2 | 滚动拥有者 | 同一内容区域没有父子双重纵向滚动。 |
| SC3 | 滚动能力 | 隐藏滚动条后仍保留 `overflow: auto` 或组件内部滚动。 |
| SC4 | 样式范围 | 没有使用全局通配选择器隐藏滚动条。 |
| SC5 | 表格滚动 | `el-table` 保留固定表头、内部滚动和横向访问能力。 |
| SC6 | 固定控件 | 表头、工具栏、分页条不随表体滚动。 |
| SC7 | 可发现性 | 长内容提供边界、焦点、悬停反馈或其他剩余提示。 |
| SC8 | 响应式 | 移动端高度变化时恢复页面级滚动。 |
| SC9 | 实现方式 | 未为隐藏滚动条引入 JavaScript 或新增依赖。 |

---

**文档结束**
