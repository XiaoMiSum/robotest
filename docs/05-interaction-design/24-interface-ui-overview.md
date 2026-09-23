# 软件测试平台——接口管理交互设计总览

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

> 本文档为接口测试域交互设计分册：接口管理、接口定义编辑的前端交互设计。（修订说明：第 3 章接口定义编辑器按 MeterSphere 参考重设计——多 Tab 并存、请求 Tab 带数量徽标、独立响应区，最终仅保留单条响应定义；请求体对齐快速调试编辑器，新增接口级验证器、提取器配置 Tab，移除编辑器 card 头部与状态开关。）
> 导航框架与色彩体系见 `docs/05-interaction-design/02-global-navigation.md`、`docs/05-interaction-design/03-visual-design.md`。
> 快速调试与导入功能见 `docs/05-interaction-design/01-readme.md`。
> 交互原型：`docs/05-interaction-design/接口编辑器原型.html`。

---

## 1. 概述

### 1.1 模块范围

本文档覆盖接口测试业务域（承接 SRS 3.2 接口管理）的两大能力：

1. **接口管理列表**——接口资产的集中管理：模块树组织、列表检索、批量操作、引用关系查看；
2. **接口定义编辑**——接口定义的完整维护：基本信息、请求参数模型（请求头/Query/REST/请求体/认证）、响应示例、提取器与验证器。

快速调试（SRS 3.1）与导入功能见 `docs/05-interaction-design/01-readme.md`。

接口定义是测试场景与 Mock 服务的数据底座，本域产出的接口资产被 `docs/05-interaction-design/01-readme.md`、`docs/05-interaction-design/01-readme.md`、`docs/05-interaction-design/01-readme.md`、`docs/05-interaction-design/01-readme.md` 引用。

### 1.2 侧边栏菜单与路由

项目模式顶部菜单在既有「功能测试」「缺陷管理」之外**新增「接口测试」入口**（增量见 `docs/05-interaction-design/02-global-navigation.md` 3.3）。接口测试页面内部采用侧边栏组织子模块：

```
┌──────────────────────────────────┐
│  接口测试（顶部菜单入口）          │
│  ─────────────────────────       │
│  快速调试     /workspace/projects/api-testing?tab=debug        ← 见《快速调试交互设计》
│  接口管理     /workspace/projects/api-testing?tab=interfaces   ← 本文档第 2、3 章
│  Mock 服务    /workspace/projects/api-testing?tab=mocks
│  测试场景     /workspace/projects/api-testing?tab=scenes
│  接口测试报告 /workspace/projects/api-testing?tab=reports
│  定时任务     /workspace/projects/api-testing?tab=schedules
│  ═════════════════════════════
│  项目设置（平台级 · 按业务域过滤）
│   ├ 环境管理           /workspace/projects/settings/environments
│   └ 全局资产           /workspace/projects/settings/assets
└──────────────────────────────────┘
```

> Mock 服务、测试场景、报告、定时任务及快速调试、导入功能的交互设计见接口测试域其余交互设计文档；「项目设置」分组（环境管理、全局资产）见对应分册与《项目设置交互设计》，本文档不重复描述。

### 1.3 业务关系

- **接口定义**是核心资产：由模块树组织，承载协议、方法、路径与完整请求参数模型，供测试场景复制/链接引用、Mock 服务继承。
- **引用保护**：被场景/Mock 引用的接口禁止删除，需先解除引用（错误码 7103）。
- 变量引用统一 `${变量名}` 语法，支持内置函数（见 `docs/05-interaction-design/01-readme.md` 第 3 章引用帮助）。
- 变量解析优先级（低→高）：**内置函数 < 环境变量 < 场景参数 < 步骤级变量 < 步骤提取器变量 < 运行时覆盖**（见 `docs/04-detailed-design/01-readme.md` 4.1）。
- 快速调试（验证入口）与导入（资产来源）见 `docs/05-interaction-design/01-readme.md`。

---


## 4. 通用交互模式

本部分涉及的通用交互模式遵循 `docs/05-interaction-design/01-readme.md` 第 4 章的约定：

- **弹窗类型**：表单弹窗用于复制接口命名；确认弹窗用于删除模块/接口、批量删除、离开未保存页面；引用弹窗用于查看引用关系；帮助弹窗用于变量引用帮助；
- **加载与错误状态**：页面/详情首次加载骨架屏，筛选/刷新/翻页时表格细进度条；保存/提交按钮 loading + 禁用；校验错误表单内红字定位首个错误项；业务错误码（7101/7102/7103）以中文文案 Toast 或弹窗提示；乐观锁冲突（409）提示「接口已被他人修改，请刷新后重试」；
- **键盘快捷键**：Ctrl+S 保存、Esc 取消行内编辑、Enter 行内编辑确认、Alt+↑/↓ 键值对辅助排序；
- **拖拽约定**：模块树节点同级/跨级拖拽排序，请求头/参数键值对行内排序。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `24-interface-ui-overview.md` | 前言、1. 概述、4. 通用交互模式 |
| 接口管理列表页 | `25-interface-ui-list.md` | 2. 接口管理列表页 |
| 接口定义编辑器 | `26-interface-ui-editor.md` | 3. 接口定义编辑器 |
