# 软件测试平台——（总览分册）

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

> 本文档为接口测试域交互设计分册：快速调试、导入功能的前端交互设计。
> 导航框架与色彩体系见 `docs/05-interaction-design/02-global-navigation.md`、`docs/05-interaction-design/03-visual-design.md`。
> 接口管理与接口定义编辑见 `docs/05-interaction-design/01-readme.md`。

---

## 1. 概述

### 1.1 模块范围

本文档覆盖接口测试业务域（承接 SRS 3.1 快速调试、3.2 接口管理导入能力）的两大能力：

1. **快速调试**——单请求调试：请求构造、服务端/本地执行、响应查看、调试记录管理、保存为接口定义；
2. **导入功能**——Swagger URL、Swagger 文件、cURL、HAR 四种来源的解析导入与预览确认。

接口管理列表与接口定义编辑见 `docs/05-interaction-design/01-readme.md`。

接口定义是测试场景与 Mock 服务的数据底座，本域产出的接口资产被 `docs/05-interaction-design/01-readme.md`、`docs/05-interaction-design/01-readme.md` 引用。

### 1.2 侧边栏菜单与路由

项目模式顶部菜单在既有「功能测试」「缺陷管理」之外**新增「接口测试」入口**（增量见 `docs/05-interaction-design/02-global-navigation.md` 3.3）。接口测试页面内部采用侧边栏组织子模块：

```
┌──────────────────────────────────┐
│  接口测试（顶部菜单入口）          │
│  ─────────────────────────       │
│  快速调试     /workspace/projects/api-testing?tab=debug        ← 本文档第 2 章
│  接口管理     /workspace/projects/api-testing?tab=interfaces   ← 见《接口管理交互设计》
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

> Mock 服务、测试场景、报告、定时任务及接口管理、接口定义编辑的交互设计见接口测试域其余交互设计文档；「项目设置」分组（环境管理、全局资产）见对应分册与《项目设置交互设计》，本文档不重复描述。

### 1.3 业务关系

- **快速调试**是验证入口：调试请求可一键**保存为接口定义**（新建或归属已有接口定义），调试记录仅服务端执行持久化。
- **导入**是资产来源：Swagger URL 与 cURL 命令解析后经**预览确认**写入接口定义，按 `path+method` 去重增量更新。
- 变量引用统一 `${变量名}` 语法，支持内置函数（见 `docs/05-interaction-design/01-readme.md` 第 3 章引用帮助）。
- 接口定义的列表管理与编辑维护见 `docs/05-interaction-design/01-readme.md`。

---


## 4. 通用交互模式

本部分涉及的通用交互模式遵循 `docs/05-interaction-design/01-readme.md` 第 4 章的约定：

- **弹窗类型**：表单弹窗用于保存为接口定义、cURL 导入；确认弹窗用于删除调试记录；导入弹窗（含预览确认与进度）用于导入接口；帮助弹窗用于内置函数帮助；
- **加载与错误状态**：发送/本地执行按钮 loading + 禁用，响应查看区加载中占位；导入解析/导入中弹窗内进度条且禁止关闭；校验错误弹窗内红字提示；业务错误码（7010/7011/7012/7013）以中文文案提示；并发队列满弹窗提示「并发队列已满，请稍后重试」；
- **键盘快捷键**：Ctrl+Enter 发送请求、Ctrl+Shift+H 切换历史记录、Ctrl+V 粘贴 cURL、Esc 关闭弹窗；
- **拖拽约定**：请求头/参数键值对行内上下拖拽调整顺序（快速调试页无模块树拖拽）。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `27-quick-debug-ui-overview.md` | 前言、1. 概述、4. 通用交互模式 |
| 快速调试页 | `28-quick-debug-ui-page.md` | 2. 快速调试页 |
| 导入功能 | `29-quick-debug-ui-import.md` | 3. 导入功能 |
